import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/app_spacing.dart';
import '../../app/app_state.dart';
import '../../app/app_version.dart';
import '../../app/safe_action.dart';
import '../../app/theme.dart';
import '../../core/i18n/app_strings.dart';
import '../../core/platform/external_url_opener.dart';
import '../../core/update/update_checker.dart';
import '../widgets/app_motion.dart';

class SettingsOverlay extends StatefulWidget {
  const SettingsOverlay({super.key, this.sourceRect});

  final Rect? sourceRect;

  @override
  State<SettingsOverlay> createState() => _SettingsOverlayState();
}

class _SettingsOverlayState extends State<SettingsOverlay>
    with SingleTickerProviderStateMixin {
  late final _controller = AnimationController(
    vsync: this,
    duration: AppMotion.overlay,
    reverseDuration: AppMotion.dismiss,
  );
  bool _closing = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_closing) return;
    if (MediaQuery.disableAnimationsOf(context)) {
      _controller.value = 1;
    } else if (_controller.isDismissed) {
      _controller.forward();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _close() async {
    if (_closing) return;
    _closing = true;
    if (!MediaQuery.disableAnimationsOf(context)) {
      await _controller.reverse();
    }
    if (mounted) AppScope.of(context).closeSettings();
  }

  void _requestClose() {
    safeAction(
      context,
      id: 'settings.close',
      label: context.t('close'),
      action: _close,
    );
  }

  KeyEventResult _onKeyEvent(FocusNode node, KeyEvent event) {
    if (event is KeyDownEvent &&
        event.logicalKey == LogicalKeyboardKey.escape) {
      _requestClose();
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    final isWindows = defaultTargetPlatform == TargetPlatform.windows;
    final scheme = context.colors;
    final media = MediaQuery.of(context);
    final compact = media.size.width < 600;
    final margin = compact ? 12.0 : 24.0;
    final safe = Rect.fromLTRB(
      media.padding.left + margin,
      media.padding.top + margin,
      media.size.width - media.padding.right - margin,
      media.size.height -
          math.max(media.padding.bottom, media.viewInsets.bottom) -
          margin,
    );
    final width = math.max(0.0, math.min(safe.width, 464.0));
    final height = math.max(0.0, math.min(safe.height, 438.0));
    final destination = Rect.fromCenter(
      center: compact
          ? Offset(safe.center.dx, safe.bottom - height / 2)
          : safe.center,
      width: width,
      height: height,
    );
    final anchor = widget.sourceRect;
    final source = anchor != null && (Offset.zero & media.size).overlaps(anchor)
        ? anchor
        : Rect.fromCenter(center: destination.center, width: 44, height: 44);

    final child = PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _requestClose();
      },
      child: AnimatedBuilder(
        animation: _controller,
        child: RepaintBoundary(
          child: Focus(
            autofocus: true,
            onKeyEvent: _onKeyEvent,
            child: SettingsPanel(onClose: _close),
          ),
        ),
        builder: (context, panel) {
          final t = AppMotion.travel.transform(_controller.value);
          final rect = Rect.lerp(source, destination, t)!;
          final reveal = const Interval(
            0.23,
            1,
            curve: AppMotion.enter,
          ).transform(_controller.value);
          final radii = BorderRadius.circular(
            AppRadius.workspaceControl +
                (AppRadius.workspaceDialog - AppRadius.workspaceControl) * t,
          );
          return Stack(
            fit: StackFit.expand,
            children: [
              ModalBarrier(
                color: scheme.scrim.withValues(
                  alpha: t * (isWindows ? 0.24 : 0.34),
                ),
                semanticsLabel: context.t('close'),
                onDismiss: _requestClose,
              ),
              Positioned.fromRect(
                rect: rect,
                child: Material(
                  elevation: 6 * t,
                  color: Color.lerp(
                    scheme.secondaryContainer,
                    scheme.surfaceContainerLow,
                    t,
                  ),
                  shape: RoundedSuperellipseBorder(borderRadius: radii),
                  clipBehavior: Clip.antiAlias,
                  child: OverflowBox(
                    alignment: Alignment.topLeft,
                    minWidth: width,
                    maxWidth: width,
                    minHeight: height,
                    maxHeight: height,
                    child: IgnorePointer(
                      ignoring: _closing || reveal < 0.70,
                      child: Opacity(opacity: reveal, child: panel),
                    ),
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
    return isWindows ? ExcludeSemantics(child: child) : child;
  }
}

class SettingsPanel extends StatefulWidget {
  const SettingsPanel({required this.onClose, super.key});

  final VoidCallback onClose;

  @override
  State<SettingsPanel> createState() => _SettingsPanelState();
}

class _SettingsPanelState extends State<SettingsPanel> {
  bool _checkingUpdates = false;
  UpdateCheckResult? _updateResult;

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final strings = context.strings;
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.xl,
        AppSpacing.lg,
        AppSpacing.xl,
        AppSpacing.md,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.settings_outlined, color: theme.colorScheme.primary),
              const SizedBox(width: AppSpacing.sm),
              Expanded(
                child: Text(
                  strings.t('settings'),
                  style: theme.textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              IconButton(
                tooltip: strings.t('close'),
                onPressed: () => safeAction(
                  context,
                  id: 'settings.close',
                  label: strings.t('close'),
                  action: widget.onClose,
                ),
                icon: const Icon(Icons.close_rounded),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          Flexible(
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    strings.t('language'),
                    style: theme.textTheme.titleMedium,
                  ),
                  const SizedBox(height: AppSpacing.xs),
                  Wrap(
                    spacing: AppSpacing.xs,
                    runSpacing: AppSpacing.xs,
                    children: [
                      ChoiceChip(
                        label: Text(strings.t('language.zh')),
                        selected: state.locale.languageCode == 'zh',
                        onSelected: (_) => safeAction(
                          context,
                          id: 'settings.language.zhCn',
                          label: strings.t('language.zh'),
                          action: () =>
                              state.setLocale(const Locale('zh', 'CN')),
                        ),
                      ),
                      ChoiceChip(
                        label: Text(strings.t('language.en')),
                        selected: state.locale.languageCode == 'en',
                        onSelected: (_) => safeAction(
                          context,
                          id: 'settings.language.enUs',
                          label: strings.t('language.en'),
                          action: () =>
                              state.setLocale(const Locale('en', 'US')),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.md),
                  _SettingsTile(
                    icon: Icons.cleaning_services_rounded,
                    title: strings.t('clearCache'),
                    subtitle:
                        '${strings.t('cache.lastResult')}: ${state.lastCacheResult}',
                    onTap: () => safeAction(
                      context,
                      id: 'settings.clearCache',
                      label: strings.t('clearCache'),
                      action: state.clearCache,
                    ),
                  ),
                  _SettingsTile(
                    icon: Icons.info_rounded,
                    title: strings.t('about'),
                    subtitle: 'EtoileBridge Flutter $appVersionName',
                    onTap: null,
                  ),
                  _SettingsTile(
                    icon: Icons.system_update_alt_rounded,
                    title: strings.t('checkUpdates'),
                    subtitle: _updateSubtitle(strings),
                    trailing: _checkingUpdates
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : null,
                    onTap: _checkingUpdates
                        ? null
                        : () => safeAction(
                            context,
                            id: 'settings.checkUpdates',
                            label: strings.t('checkUpdates'),
                            action: _checkUpdates,
                          ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton(
              onPressed: () => safeAction(
                context,
                id: 'settings.close',
                label: strings.t('close'),
                action: widget.onClose,
              ),
              child: Text(strings.t('close')),
            ),
          ),
        ],
      ),
    );
  }

  String _updateSubtitle(AppStrings strings) {
    if (_checkingUpdates) return strings.t('updates.checking');
    final result = _updateResult;
    if (result == null) return strings.t('updates.ready');
    return switch (result.status) {
      UpdateCheckStatus.updateAvailable => _format(
        strings.t('updates.available'),
        {'version': result.latestVersion ?? '-'},
      ),
      UpdateCheckStatus.upToDate => strings.t('updates.upToDate'),
      UpdateCheckStatus.rateLimited => strings.t('updates.rateLimited'),
      UpdateCheckStatus.networkError => strings.t('updates.networkError'),
      UpdateCheckStatus.unknown => strings.t('updates.unknown'),
    };
  }

  Future<void> _checkUpdates() async {
    setState(() => _checkingUpdates = true);
    final result = await const GitHubUpdateChecker().check();
    if (!mounted) return;
    setState(() {
      _checkingUpdates = false;
      _updateResult = result;
    });
    await _showUpdateResult(result);
  }

  Future<void> _showUpdateResult(UpdateCheckResult result) async {
    final strings = context.strings;
    final title = switch (result.status) {
      UpdateCheckStatus.updateAvailable => strings.t('updates.availableTitle'),
      UpdateCheckStatus.upToDate => strings.t('updates.upToDateTitle'),
      UpdateCheckStatus.rateLimited => strings.t('updates.rateLimitedTitle'),
      UpdateCheckStatus.networkError => strings.t('updates.networkErrorTitle'),
      UpdateCheckStatus.unknown => strings.t('updates.unknownTitle'),
    };
    final body = switch (result.status) {
      UpdateCheckStatus.updateAvailable =>
        '${_format(strings.t('updates.currentVersion'), {'version': result.currentVersion})}\n'
            '${_format(strings.t('updates.latestVersion'), {'version': result.latestVersion ?? '-'})}',
      UpdateCheckStatus.upToDate =>
        '${_format(strings.t('updates.currentVersion'), {'version': result.currentVersion})}\n'
            '${_format(strings.t('updates.latestVersion'), {'version': result.latestVersion ?? result.currentVersion})}',
      UpdateCheckStatus.rateLimited =>
        '${strings.t('updates.rateLimitedMessage')}\n${result.error ?? ''}',
      UpdateCheckStatus.networkError => _format(
        strings.t('updates.networkErrorMessage'),
        {'error': result.error ?? '-'},
      ),
      UpdateCheckStatus.unknown =>
        '${_format(strings.t('updates.unknownMessage'), {'error': result.error ?? '-'})}\n'
            '${_format(strings.t('updates.currentVersion'), {'version': result.currentVersion})}\n'
            '${_format(strings.t('updates.latestVersion'), {'version': result.latestVersion ?? '-'})}\n'
            '${result.error ?? ''}',
    };
    final canOpen = result.canOpenReleasePage;
    final shouldOpen = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(body.trim()),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(strings.t('close')),
          ),
          if (canOpen)
            FilledButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: Text(strings.t('updates.openReleasePage')),
            ),
        ],
      ),
    );
    if (shouldOpen == true) {
      final url = result.releaseUrl ?? githubReleasesPage;
      await openExternalUrl(url);
    }
  }

  String _format(String template, Map<String, String> values) {
    var text = template;
    for (final entry in values.entries) {
      text = text.replaceAll('{${entry.key}}', entry.value);
    }
    return text;
  }
}

class _SettingsTile extends StatelessWidget {
  const _SettingsTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
    this.trailing,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback? onTap;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      subtitle: Text(subtitle),
      trailing: trailing,
      onTap: onTap,
    );
  }
}
