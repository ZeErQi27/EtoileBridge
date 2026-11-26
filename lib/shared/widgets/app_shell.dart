import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/physics.dart';

import '../../app/app_spacing.dart';
import '../../app/app_state.dart';
import '../../app/app_version.dart';
import '../../app/breakpoints.dart';
import '../../app/routes.dart';
import '../../app/safe_action.dart';
import '../../core/i18n/app_strings.dart';
import '../dialogs/settings_dialog.dart';
import 'app_motion.dart';

class AppShell extends StatefulWidget {
  const AppShell({required this.pages, super.key});

  final List<Widget> pages;

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  final _toolbarSettingsKey = GlobalKey();
  final _railSettingsKey = GlobalKey();
  Rect? _settingsAnchor;

  void _openSettings(BuildContext context, AppState state, GlobalKey key) {
    final anchor = key.currentContext?.findRenderObject();
    _settingsAnchor = anchor is RenderBox && anchor.hasSize
        ? anchor.localToGlobal(Offset.zero) & anchor.size
        : null;
    safeAction(
      context,
      id: 'settings.open',
      label: context.t('settings'),
      action: state.openSettings,
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final scheme = Theme.of(context).colorScheme;
    final width = MediaQuery.sizeOf(context).width;
    final compact = width < Breakpoints.compactMax;
    final extended = width >= Breakpoints.railExtendedMinWidth;
    final acrylic = defaultTargetPlatform == TargetPlatform.windows;
    final dark = scheme.brightness == Brightness.dark;
    final selected = state.currentPage.index;

    return Stack(
      fit: StackFit.expand,
      children: [
        ExcludeFocus(
          excluding: state.settingsOpen,
          child: Scaffold(
            backgroundColor: acrylic ? Colors.transparent : scheme.surface,
            appBar: compact
                ? AppBar(
                    toolbarHeight: 56,
                    titleSpacing: 16,
                    title: const _Brand(extended: true),
                    actions: [
                      IconButton(
                        key: _toolbarSettingsKey,
                        tooltip: context.t('settings'),
                        icon: const Icon(Icons.settings_outlined),
                        onPressed: () =>
                            _openSettings(context, state, _toolbarSettingsKey),
                      ),
                      const SizedBox(width: 8),
                    ],
                  )
                : null,
            // Keep the same body subtree when navigation changes form.
            body: ColoredBox(
              color: acrylic
                  ? scheme.surface.withValues(alpha: dark ? 0.93 : 0.80)
                  : scheme.surface,
              child: SafeArea(
                bottom: !compact,
                child: Row(
                  children: [
                    if (!compact)
                      _WorkspaceRail(
                        extended: extended,
                        acrylic: acrylic,
                        selected: selected,
                        settingsKey: _railSettingsKey,
                        onSettings: () =>
                            _openSettings(context, state, _railSettingsKey),
                      ),
                    Expanded(
                      key: const ValueKey('workspace'),
                      child: AppPageViewport(
                        index: selected,
                        pages: widget.pages,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            bottomNavigationBar: compact
                ? NavigationBar(
                    height: 68,
                    selectedIndex: selected,
                    animationDuration: AppMotion.duration(
                      context,
                      AppMotion.reveal,
                    ),
                    onDestinationSelected: (index) =>
                        _selectPage(context, AppPageId.values[index]),
                    destinations: [
                      for (final page in AppPageId.values)
                        NavigationDestination(
                          key: ValueKey('nav.${page.name}'),
                          icon: Icon(_unselectedIcon(page)),
                          selectedIcon: Icon(page.icon),
                          label: context.t(page.i18nKey),
                        ),
                    ],
                  )
                : null,
          ),
        ),
        if (state.settingsOpen) SettingsOverlay(sourceRect: _settingsAnchor),
      ],
    );
  }
}

class _WorkspaceRail extends StatelessWidget {
  const _WorkspaceRail({
    required this.extended,
    required this.acrylic,
    required this.selected,
    required this.settingsKey,
    required this.onSettings,
  });

  final bool extended;
  final bool acrylic;
  final int selected;
  final GlobalKey settingsKey;
  final VoidCallback onSettings;

  static final _compactVersionLabel = appVersionLabel.replaceFirstMapped(
    RegExp(r'^(v\d+\.\d+\.\d+)(.+)$'),
    (match) => '${match[1]}\n${match[2]}',
  );

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final surface = Color.lerp(
      scheme.surface,
      scheme.surfaceContainerHighest,
      0.54,
    )!;
    final settings = context.t('settings');
    final controlsInset = EdgeInsets.symmetric(horizontal: extended ? 12 : 10);
    return Container(
      width: extended ? Breakpoints.railExtendedWidth : Breakpoints.railWidth,
      decoration: BoxDecoration(
        color: acrylic ? surface.withValues(alpha: 0.90) : surface,
        border: Border(
          right: BorderSide(color: scheme.onSurface.withValues(alpha: 0.075)),
        ),
      ),
      padding: const EdgeInsets.only(top: 20, bottom: 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: EdgeInsets.symmetric(horizontal: extended ? 20 : 10),
            child: _Brand(extended: extended),
          ),
          const SizedBox(height: 24),
          Padding(
            padding: controlsInset,
            child: _RailDestinations(extended: extended, selected: selected),
          ),
          const Spacer(),
          Padding(
            padding: controlsInset,
            child: _RailAction(
              key: settingsKey,
              extended: extended,
              icon: Icons.settings_outlined,
              label: settings,
              onTap: onSettings,
            ),
          ),
          const SizedBox(height: 8),
          Padding(
            padding: extended
                ? controlsInset
                : const EdgeInsets.symmetric(horizontal: AppSpacing.xxs),
            child: Tooltip(
              message: appVersionLabel,
              child: Text(
                extended ? appVersionLabel : _compactVersionLabel,
                maxLines: extended ? 1 : 2,
                overflow: TextOverflow.ellipsis,
                textAlign: extended ? TextAlign.start : TextAlign.center,
                style: theme.textTheme.labelSmall?.copyWith(
                  color: scheme.onSurfaceVariant,
                  fontSize: extended ? null : 10,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _RailDestinations extends StatefulWidget {
  const _RailDestinations({required this.extended, required this.selected});

  final bool extended;
  final int selected;

  @override
  State<_RailDestinations> createState() => _RailDestinationsState();
}

class _RailDestinationsState extends State<_RailDestinations>
    with SingleTickerProviderStateMixin {
  static const double _step = 52;
  static const double _height = 46;
  static final _spring = SpringDescription.withDampingRatio(
    mass: 1,
    stiffness: 450,
    ratio: 0.85,
  );

  late final _indicator = AnimationController.unbounded(
    vsync: this,
    value: _target,
  );

  double get _target => widget.selected * _step;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (MediaQuery.disableAnimationsOf(context)) _indicator.value = _target;
  }

  @override
  void didUpdateWidget(covariant _RailDestinations oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.selected == oldWidget.selected) return;
    if (MediaQuery.disableAnimationsOf(context)) {
      _indicator.value = _target;
    } else {
      _indicator.animateWith(
        SpringSimulation(
          _spring,
          _indicator.value,
          _target,
          _indicator.velocity,
        ),
      );
    }
  }

  @override
  void dispose() {
    _indicator.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return SizedBox(
      height: (AppPageId.values.length - 1) * _step + _height,
      child: Stack(
        children: [
          Positioned(
            left: 0,
            right: 0,
            top: 0,
            height: _height,
            child: AnimatedBuilder(
              animation: _indicator,
              builder: (context, child) => Transform.translate(
                offset: Offset(0, _indicator.value),
                child: child,
              ),
              child: DecoratedBox(
                key: const ValueKey('rail-selection'),
                decoration: ShapeDecoration(
                  shape: RoundedSuperellipseBorder(
                    borderRadius: BorderRadius.circular(
                      AppRadius.workspaceControl,
                    ),
                  ),
                  color: Color.lerp(
                    scheme.surfaceContainerHighest,
                    scheme.onSurface,
                    0.06,
                  ),
                ),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: Container(
                    width: 3,
                    height: 18,
                    decoration: BoxDecoration(
                      color: scheme.primary,
                      borderRadius: BorderRadius.circular(3),
                    ),
                  ),
                ),
              ),
            ),
          ),
          for (final page in AppPageId.values)
            Positioned(
              top: page.index * _step,
              left: 0,
              right: 0,
              height: _height,
              child: _RailAction(
                key: ValueKey('nav.${page.name}'),
                extended: widget.extended,
                selected: page.index == widget.selected,
                icon: page.index == widget.selected
                    ? page.icon
                    : _unselectedIcon(page),
                label: context.t(page.i18nKey),
                onTap: () => _selectPage(context, page),
              ),
            ),
        ],
      ),
    );
  }
}

class _RailAction extends StatelessWidget {
  const _RailAction({
    required this.extended,
    required this.icon,
    required this.label,
    required this.onTap,
    this.selected,
    super.key,
  });

  final bool extended;
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool? selected;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final color = selected == true ? scheme.onSurface : scheme.onSurfaceVariant;
    final shape = RoundedSuperellipseBorder(
      borderRadius: BorderRadius.circular(AppRadius.workspaceControl),
    );
    final button = Semantics(
      button: true,
      selected: selected,
      label: extended ? null : label,
      child: Material(
        color: Colors.transparent,
        clipBehavior: Clip.antiAlias,
        shape: shape,
        child: InkWell(
          onTap: onTap,
          customBorder: shape,
          overlayColor: WidgetStateProperty.resolveWith((states) {
            if (states.contains(WidgetState.pressed) ||
                states.contains(WidgetState.focused)) {
              return color.withValues(alpha: 0.12);
            }
            if (states.contains(WidgetState.hovered)) {
              return color.withValues(alpha: 0.08);
            }
            return null;
          }),
          child: SizedBox(
            height: 48,
            child: TweenAnimationBuilder<Color?>(
              tween: ColorTween(end: color),
              duration: AppMotion.duration(context, AppMotion.control),
              builder: (context, value, _) => extended
                  ? Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 14),
                      child: Row(
                        children: [
                          Icon(icon, size: 22, color: value),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              label,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: theme.textTheme.labelLarge?.copyWith(
                                color: value,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                      ),
                    )
                  : Center(child: Icon(icon, size: 24, color: value)),
            ),
          ),
        ),
      ),
    );
    return extended && selected != null
        ? button
        : Tooltip(message: label, child: button);
  }
}

class _Brand extends StatelessWidget {
  const _Brand({required this.extended});

  final bool extended;

  @override
  Widget build(BuildContext context) {
    final logo = Image.asset(
      'assets/branding/etoilebridge_logo.png',
      width: 30,
      height: 30,
      fit: BoxFit.contain,
    );
    if (!extended) return Center(child: logo);
    return Row(
      children: [
        logo,
        const SizedBox(width: 10),
        Flexible(
          child: Text(
            context.t('app.title'),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(
              context,
            ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
        ),
      ],
    );
  }
}

IconData _unselectedIcon(AppPageId page) => switch (page) {
  AppPageId.singleSong => Icons.music_note_outlined,
  AppPageId.packEditor => Icons.library_music_outlined,
  AppPageId.characterEditor => Icons.person_outline_rounded,
};

void _selectPage(BuildContext context, AppPageId page) {
  final state = AppScope.of(context);
  if (state.currentPage == page) return;
  safeAction(
    context,
    id: 'nav.${page.name}',
    label: context.t(page.i18nKey),
    page: page,
    action: () => state.selectPage(page),
  );
}
