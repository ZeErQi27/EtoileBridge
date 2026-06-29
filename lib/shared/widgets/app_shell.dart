import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../app/app_spacing.dart';
import '../../app/app_state.dart';
import '../../app/app_version.dart';
import '../../app/breakpoints.dart';
import '../../app/routes.dart';
import '../../app/safe_action.dart';
import '../../app/safe_layout.dart';
import '../../core/i18n/app_strings.dart';
import '../layout/app_layout_tokens.dart';
import '../dialogs/settings_dialog.dart';

class AppShell extends StatefulWidget {
  const AppShell({required this.pages, super.key});

  final List<Widget> pages;

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  final _settingsButtonKey = GlobalKey();
  Rect? _settingsAnchorRect;
  int _lastPageIndex = 0;
  int _pageTransitionDirection = 1;

  void _openSettings(BuildContext context, AppState state) {
    _settingsAnchorRect = _rectForKey(_settingsButtonKey);
    safeAction(
      context,
      id: 'settings.open',
      label: context.t('settings'),
      action: state.openSettings,
    );
  }

  Rect? _rectForKey(GlobalKey key) {
    final keyContext = key.currentContext;
    if (keyContext == null) return null;
    final renderObject = keyContext.findRenderObject();
    if (renderObject is! RenderBox || !renderObject.hasSize) return null;
    final topLeft = renderObject.localToGlobal(Offset.zero);
    return topLeft & renderObject.size;
  }

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final scheme = Theme.of(context).colorScheme;
    final width = SafeLayout.finite(
      MediaQuery.sizeOf(context).width,
      fallback: SafeLayout.minViewportWidth,
    );
    final window = SafeLayout.classify(width);
    final compact = window == WindowClass.compact;
    final desktop = _isDesktopPlatform(defaultTargetPlatform);
    final currentIndex = AppPageId.values.indexOf(state.currentPage);
    if (currentIndex != _lastPageIndex) {
      _pageTransitionDirection = currentIndex > _lastPageIndex ? 1 : -1;
      _lastPageIndex = currentIndex;
    }

    final pageStack = Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(
          maxWidth: AppLayoutTokens.pageContentMaxWidth,
        ),
        child: Stack(
          fit: StackFit.expand,
          children: [
            for (var index = 0; index < widget.pages.length; index++)
              _PersistentPageSlot(
                active: index == currentIndex,
                direction: _pageTransitionDirection,
                child: widget.pages[index],
              ),
          ],
        ),
      ),
    );

    final scaffold = compact
        ? Scaffold(
            appBar: AppBar(
              toolbarHeight: 58,
              titleSpacing: AppSpacing.md,
              title: const _AppBarTitle(compact: true),
              actions: [
                IconButton(
                  key: _settingsButtonKey,
                  icon: const Icon(Icons.settings_rounded),
                  onPressed: () => _openSettings(context, state),
                ),
                const SizedBox(width: AppSpacing.xs),
              ],
            ),
            body: SafeArea(child: pageStack),
            bottomNavigationBar: NavigationBar(
              selectedIndex: currentIndex,
              onDestinationSelected: (index) {
                final page = AppPageId.values[index];
                safeAction(
                  context,
                  id: 'nav.${page.name}',
                  label: context.t(page.i18nKey),
                  page: page,
                  action: () => state.selectPage(page),
                );
              },
              destinations: [
                for (final page in AppPageId.values)
                  NavigationDestination(
                    icon: Icon(page.icon),
                    selectedIcon: Icon(page.icon),
                    label: context.t(page.i18nKey),
                  ),
              ],
            ),
          )
        : Scaffold(
            backgroundColor: desktop
                ? Colors.transparent
                : Theme.of(context).scaffoldBackgroundColor,
            body: _DesktopMicaBackground(
              enabled: desktop,
              child: SafeArea(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    children: [
                      _PrettySidebar(
                        settingsKey: _settingsButtonKey,
                        onSettingsPressed: () => _openSettings(context, state),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: desktop
                            ? _DesktopMicaPanel(
                                radius: 28,
                                surfaceAlpha: 0.48,
                                child: pageStack,
                              )
                            : ClipRRect(
                                borderRadius: BorderRadius.circular(28),
                                child: DecoratedBox(
                                  decoration: BoxDecoration(
                                    color: scheme.surface.withValues(
                                      alpha: 0.34,
                                    ),
                                    borderRadius: BorderRadius.circular(28),
                                    border: Border.all(
                                      color: scheme.outlineVariant.withValues(
                                        alpha: 0.76,
                                      ),
                                    ),
                                  ),
                                  child: pageStack,
                                ),
                              ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );

    return _ShellOverlayHost(
      settingsAnchorRect: _settingsAnchorRect,
      child: scaffold,
    );
  }
}

class _PrettySidebar extends StatelessWidget {
  const _PrettySidebar({
    required this.settingsKey,
    required this.onSettingsPressed,
  });

  final GlobalKey settingsKey;
  final VoidCallback onSettingsPressed;

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final scheme = Theme.of(context).colorScheme;
    final content = Padding(
      padding: const EdgeInsets.fromLTRB(18, 22, 18, 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              const _AppLogo(size: 48),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      context.t('app.title'),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        color: scheme.onSurface,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    Text(
                      context.t('app.subtitle'),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 28),
          _SidebarDestinationStack(currentPage: state.currentPage),
          const Spacer(),
          _SidebarSettingsButton(
            key: settingsKey,
            onPressed: onSettingsPressed,
          ),
          const SizedBox(height: 18),
          Text(
            appVersionLabel,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(
              context,
            ).textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
          ),
        ],
      ),
    );
    if (_isDesktopPlatform(defaultTargetPlatform)) {
      return SizedBox(
        width: 248,
        child: _DesktopMicaPanel(
          radius: 28,
          surfaceAlpha: 0.54,
          child: content,
        ),
      );
    }
    return Container(
      width: 248,
      decoration: BoxDecoration(
        color: scheme.surface.withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(28),
        border: Border.all(color: scheme.outlineVariant),
        boxShadow: [
          BoxShadow(
            color: scheme.primary.withValues(alpha: 0.09),
            blurRadius: 34,
            offset: const Offset(0, 18),
          ),
        ],
      ),
      child: content,
    );
  }
}

class _SidebarDestinationStack extends StatelessWidget {
  const _SidebarDestinationStack({required this.currentPage});

  final AppPageId currentPage;

  static const double _itemHeight = 60;
  static const double _pillHeight = 52;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final index = AppPageId.values
        .indexOf(currentPage)
        .clamp(0, AppPageId.values.length - 1);
    return SizedBox(
      height: AppPageId.values.length * _itemHeight,
      child: Stack(
        children: [
          AnimatedPositioned(
            duration: const Duration(milliseconds: 310),
            curve: Curves.easeOutBack,
            left: 0,
            right: 0,
            top: index * _itemHeight,
            height: _pillHeight,
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: scheme.secondaryContainer,
                borderRadius: BorderRadius.circular(18),
                boxShadow: [
                  BoxShadow(
                    color: scheme.primary.withValues(alpha: 0.08),
                    blurRadius: 18,
                    offset: const Offset(0, 8),
                  ),
                ],
              ),
            ),
          ),
          for (var i = 0; i < AppPageId.values.length; i++)
            Positioned(
              left: 0,
              right: 0,
              top: i * _itemHeight,
              height: _pillHeight,
              child: _SidebarDestination(
                page: AppPageId.values[i],
                selected: currentPage == AppPageId.values[i],
              ),
            ),
        ],
      ),
    );
  }
}

class _SidebarDestination extends StatelessWidget {
  const _SidebarDestination({required this.page, required this.selected});

  final AppPageId page;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final scheme = Theme.of(context).colorScheme;
    final color = selected ? scheme.onSecondaryContainer : scheme.onSurface;
    return Material(
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(18),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => safeAction(
          context,
          id: 'nav.${page.name}',
          label: context.t(page.i18nKey),
          page: page,
          action: () => state.selectPage(page),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 14),
          child: TweenAnimationBuilder<Color?>(
            tween: ColorTween(begin: color, end: color),
            duration: const Duration(milliseconds: 190),
            curve: Curves.easeOutCubic,
            builder: (context, animatedColor, _) => Row(
              children: [
                Icon(page.icon, color: animatedColor, size: 23),
                const SizedBox(width: 14),
                Expanded(
                  child: Text(
                    context.t(page.i18nKey),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      color: animatedColor,
                      fontWeight: selected ? FontWeight.w900 : FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SidebarSettingsButton extends StatelessWidget {
  const _SidebarSettingsButton({required this.onPressed, super.key});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final radius = BorderRadius.circular(20);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: scheme.secondaryContainer.withValues(alpha: 0.58),
        borderRadius: radius,
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.46),
          width: 0.8,
        ),
        boxShadow: [
          BoxShadow(
            color: scheme.primary.withValues(alpha: 0.055),
            blurRadius: 18,
            offset: const Offset(0, 7),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: radius,
        child: InkWell(
          customBorder: RoundedRectangleBorder(borderRadius: radius),
          onTap: onPressed,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 62),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 15),
              child: Center(
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.settings_rounded, color: scheme.primary),
                    const SizedBox(width: 10),
                    _SettingsButtonLabel(
                      label: context.t('settings'),
                      color: scheme.onSecondaryContainer,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _SettingsButtonLabel extends StatelessWidget {
  const _SettingsButtonLabel({required this.label, required this.color});

  final String label;
  final Color color;

  static const _fontFallback = [
    'EtoileSans',
    'Noto Sans CJK SC',
    'Noto Sans SC',
    'Noto Sans',
  ];

  @override
  Widget build(BuildContext context) {
    final style = Theme.of(context).textTheme.titleSmall?.copyWith(
      color: color,
      fontFamily: 'EtoileSans',
      fontFamilyFallback: _fontFallback,
      fontSize: 16,
      fontWeight: FontWeight.w600,
      height: 1.38,
    );

    final labelRunes = label.runes.toList(growable: false);
    if (labelRunes.length == 2) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(String.fromCharCode(labelRunes[0]), style: style),
          Text(String.fromCharCode(labelRunes[1]), style: style),
        ],
      );
    }

    return Text(
      label,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: style,
    );
  }
}

class _DesktopMicaBackground extends StatelessWidget {
  const _DesktopMicaBackground({required this.enabled, required this.child});

  final bool enabled;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    if (!enabled) {
      return DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              Theme.of(context).scaffoldBackgroundColor,
              scheme.secondaryContainer.withValues(alpha: 0.28),
              scheme.tertiaryContainer.withValues(alpha: 0.16),
            ],
          ),
        ),
        child: child,
      );
    }
    final micaTint = Color.lerp(scheme.surface, scheme.primaryContainer, 0.12)!;
    return Stack(
      children: [
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(color: micaTint.withValues(alpha: 0.28)),
          ),
        ),
        child,
      ],
    );
  }
}

class _DesktopMicaPanel extends StatelessWidget {
  const _DesktopMicaPanel({
    required this.child,
    required this.radius,
    required this.surfaceAlpha,
  });

  final Widget child;
  final double radius;
  final double surfaceAlpha;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final borderRadius = BorderRadius.circular(radius);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Color.lerp(
          scheme.surface,
          scheme.primaryContainer,
          0.05,
        )!.withValues(alpha: surfaceAlpha),
        borderRadius: borderRadius,
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.52),
          width: 0.9,
        ),
        boxShadow: [
          BoxShadow(
            color: scheme.primary.withValues(alpha: 0.07),
            blurRadius: 38,
            offset: const Offset(0, 18),
          ),
        ],
      ),
      child: ClipRRect(borderRadius: borderRadius, child: child),
    );
  }
}

class _AppLogo extends StatelessWidget {
  const _AppLogo({required this.size});

  final double size;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: Image.asset(
        'assets/branding/etoilebridge_logo.png',
        fit: BoxFit.contain,
      ),
    );
  }
}

class _ShellOverlayHost extends StatelessWidget {
  const _ShellOverlayHost({required this.child, this.settingsAnchorRect});

  final Widget child;
  final Rect? settingsAnchorRect;

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    return Stack(
      children: [
        child,
        if (state.settingsOpen)
          Positioned.fill(
            child: SettingsOverlay(sourceRect: settingsAnchorRect),
          ),
      ],
    );
  }
}

bool _isDesktopPlatform(TargetPlatform platform) {
  return platform == TargetPlatform.windows ||
      platform == TargetPlatform.macOS ||
      platform == TargetPlatform.linux;
}

class _PersistentPageSlot extends StatelessWidget {
  const _PersistentPageSlot({
    required this.active,
    required this.direction,
    required this.child,
  });

  final bool active;
  final int direction;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    if (defaultTargetPlatform == TargetPlatform.windows) {
      return IgnorePointer(
        ignoring: !active,
        child: TickerMode(
          enabled: active,
          child: Offstage(
            offstage: !active,
            child: _PageEnterMotion(
              active: active,
              direction: direction,
              child: child,
            ),
          ),
        ),
      );
    }

    return IgnorePointer(
      ignoring: !active,
      child: TickerMode(
        enabled: active,
        child: AnimatedOpacity(
          opacity: active ? 1 : 0,
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeOutCubic,
          child: Offstage(offstage: !active, child: child),
        ),
      ),
    );
  }
}

class _PageEnterMotion extends StatelessWidget {
  const _PageEnterMotion({
    required this.active,
    required this.direction,
    required this.child,
  });

  final bool active;
  final int direction;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: active ? 0 : 1, end: active ? 1 : 0),
      duration: const Duration(milliseconds: 240),
      curve: Curves.easeOutCubic,
      child: child,
      builder: (context, value, child) {
        return Transform.translate(
          offset: Offset(0, (1 - value) * 18 * direction),
          child: Transform.scale(
            scale: 0.988 + value * 0.012,
            alignment: Alignment.topCenter,
            child: child,
          ),
        );
      },
    );
  }
}

class _AppBarTitle extends StatelessWidget {
  const _AppBarTitle({required this.compact});

  final bool compact;

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final theme = Theme.of(context);
    final page = state.currentPage;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(7),
          child: Image.asset(
            'assets/branding/etoilebridge_logo.png',
            width: compact ? 24 : 28,
            height: compact ? 24 : 28,
            fit: BoxFit.cover,
          ),
        ),
        const SizedBox(width: AppSpacing.sm),
        Flexible(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                context.t('app.title'),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
              if (!compact)
                Text(
                  context.t('app.subtitle'),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
            ],
          ),
        ),
        if (!compact) ...[
          const SizedBox(width: AppSpacing.md),
          Chip(
            visualDensity: VisualDensity.compact,
            avatar: Icon(page.icon, size: 16),
            label: Text(context.t(page.i18nKey)),
          ),
        ],
      ],
    );
  }
}
