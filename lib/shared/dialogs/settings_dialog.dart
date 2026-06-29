import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/app_spacing.dart';
import '../../app/app_state.dart';
import '../../app/app_version.dart';
import '../../app/safe_action.dart';
import '../../app/safe_layout.dart';
import '../../core/i18n/app_strings.dart';
import '../../core/platform/external_url_opener.dart';
import '../../core/update/update_checker.dart';

class SettingsOverlay extends StatefulWidget {
  const SettingsOverlay({super.key, this.sourceRect});

  final Rect? sourceRect;

  @override
  State<SettingsOverlay> createState() => _SettingsOverlayState();
}

class _SettingsOverlayState extends State<SettingsOverlay>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _animation;
  bool _closing = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 280),
      reverseDuration: const Duration(milliseconds: 220),
    );
    _animation = CurvedAnimation(
      parent: _controller,
      curve: Curves.easeOutCubic,
      reverseCurve: Curves.easeInCubic,
    );
    _controller.forward();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _close(BuildContext context) async {
    if (_closing) return;
    _closing = true;
    await _controller.reverse();
    if (!context.mounted) return;
    AppScope.of(context).closeSettings();
  }

  KeyEventResult _onKeyEvent(FocusNode node, KeyEvent event) {
    if (event is KeyDownEvent &&
        event.logicalKey == LogicalKeyboardKey.escape) {
      safeAction(
        context,
        id: 'settings.close',
        label: context.t('close'),
        action: () => _close(context),
      );
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    final isWindows = defaultTargetPlatform == TargetPlatform.windows;
    final child = Stack(
      children: [
        AnimatedBuilder(
          animation: _animation,
          builder: (context, _) {
            return ModalBarrier(
              color: isWindows
                  ? Colors.transparent
                  : Colors.black.withValues(alpha: 0.28 * _animation.value),
              dismissible: true,
              onDismiss: () => safeAction(
                context,
                id: 'settings.close',
                label: context.t('close'),
                action: () => _close(context),
              ),
            );
          },
        ),
        Focus(
          autofocus: true,
          onKeyEvent: _onKeyEvent,
          child: AnimatedBuilder(
            animation: _animation,
            builder: (context, child) {
              final screen = MediaQuery.sizeOf(context);
              final source = widget.sourceRect;
              final sourceCenter =
                  source?.center ?? Offset(screen.width / 2, screen.height / 2);
              final targetCenter = Offset(screen.width / 2, screen.height / 2);
              final offset = Offset.lerp(
                sourceCenter - targetCenter,
                Offset.zero,
                _animation.value,
              )!;
              final minSide = source == null
                  ? 88.0
                  : source.size.shortestSide.clamp(44.0, 96.0).toDouble();
              final scaleStart = (minSide / 520).clamp(0.08, 0.22).toDouble();
              final scale = scaleStart + (1 - scaleStart) * _animation.value;
              return Center(
                child: Transform.translate(
                  offset: offset,
                  child: Transform.scale(
                    scale: scale,
                    child: Opacity(
                      opacity: (0.35 + _animation.value * 0.65)
                          .clamp(0, 1)
                          .toDouble(),
                      child: child,
                    ),
                  ),
                ),
              );
            },
            child: Padding(
              padding: const EdgeInsets.all(AppSpacing.lg),
              child: RepaintBoundary(
                child: Dialog(
                  child: ConstrainedBox(
                    constraints: SafeLayout.dialogConstraints(context),
                    child: SettingsPanel(onClose: () => _close(context)),
                  ),
                ),
              ),
            ),
          ),
        ),
      ],
    );

    if (isWindows) {
      return ExcludeSemantics(child: child);
    }
    return child;
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
              Icon(Icons.settings_rounded, color: theme.colorScheme.primary),
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
                  const Divider(),
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
                    onTap: () => safeAction(
                      context,
                      id: 'settings.about',
                      label: strings.t('about'),
                      action: () {},
                    ),
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
