import 'package:flutter/material.dart';

import '../../app/app_spacing.dart';
import '../../app/theme.dart';
import '../layout/app_layout_tokens.dart';
import 'app_motion.dart';

class PrettyCard extends StatelessWidget {
  const PrettyCard({
    required this.child,
    super.key,
    this.title,
    this.icon,
    this.trailing,
    this.padding = const EdgeInsets.all(AppLayoutTokens.cardPadding),
    this.margin,
    this.emphasized = false,
  });

  final String? title;
  final IconData? icon;
  final Widget? trailing;
  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry? margin;
  final bool emphasized;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: margin ?? EdgeInsets.zero,
      child: WorkspaceSurface(
        emphasized: emphasized,
        child: Padding(
          padding: padding,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (title != null || trailing != null) ...[
                ConstrainedBox(
                  constraints: const BoxConstraints(
                    minHeight: AppLayoutTokens.cardHeadingHeight,
                  ),
                  child: Row(
                    children: [
                      if (icon != null) ...[
                        Icon(
                          icon,
                          size: 20,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                        const SizedBox(width: 9),
                      ],
                      Expanded(
                        flex: 3,
                        child: title == null
                            ? const SizedBox.shrink()
                            : Semantics(
                                header: true,
                                child: Text(
                                  title!,
                                  style: theme.textTheme.titleMedium?.copyWith(
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                      ),
                      if (trailing != null) ...[
                        const SizedBox(width: 8),
                        Flexible(
                          flex: 2,
                          child: Align(
                            alignment: Alignment.centerRight,
                            child: trailing,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: AppLayoutTokens.cardHeadingGap),
              ],
              child,
            ],
          ),
        ),
      ),
    );
  }
}

class WorkspaceSurface extends StatelessWidget {
  const WorkspaceSurface({
    required this.child,
    super.key,
    this.radius = AppRadius.workspaceCard,
    this.emphasized = false,
  });

  final Widget child;
  final double radius;
  final bool emphasized;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final dark = colors.brightness == Brightness.dark;
    final shape = RoundedSuperellipseBorder(
      borderRadius: BorderRadius.circular(radius),
    );
    return DecoratedBox(
      decoration: ShapeDecoration(
        shape: shape,
        shadows: [
          if (!dark)
            BoxShadow(
              color: colors.surfaceContainerLowest.withValues(alpha: 0.72),
              offset: const Offset(-1, -2),
              blurRadius: 5,
              spreadRadius: -1,
            ),
          BoxShadow(
            color: colors.shadow.withValues(alpha: dark ? 0.22 : 0.065),
            offset: const Offset(0, 2),
            blurRadius: 5,
          ),
          BoxShadow(
            color: colors.shadow.withValues(alpha: dark ? 0.30 : 0.115),
            offset: const Offset(0, 8),
            blurRadius: 22,
            spreadRadius: -4,
          ),
        ],
      ),
      child: Material(
        color: emphasized
            ? Color.lerp(
                colors.surfaceContainerLow,
                colors.surfaceContainerLowest,
                0.58,
              )
            : colors.surfaceContainerLow,
        shape: shape,
        clipBehavior: Clip.antiAlias,
        child: child,
      ),
    );
  }
}

class WorkspaceAction extends StatelessWidget {
  const WorkspaceAction({
    required this.label,
    required this.icon,
    required this.onPressed,
    super.key,
    this.primary = false,
    this.compact = false,
  });

  final String label;
  final IconData icon;
  final VoidCallback? onPressed;
  final bool primary;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final style = FilledButton.styleFrom(
      minimumSize: compact ? const Size(42, 40) : null,
      backgroundColor: primary
          ? colors.primaryContainer
          : colors.surfaceContainerHigh,
      foregroundColor: primary ? colors.onPrimaryContainer : colors.onSurface,
      animationDuration: AppMotion.duration(context, AppMotion.control),
    );
    final text = Text(label, maxLines: 2, overflow: TextOverflow.ellipsis);
    final symbol = Icon(icon, size: compact ? 17 : 19);
    return FilledButton.tonalIcon(
      style: style,
      onPressed: onPressed,
      icon: symbol,
      label: text,
    );
  }
}

class PrettyPill extends StatelessWidget {
  const PrettyPill({
    required this.label,
    super.key,
    this.icon,
    this.tone = PrettyPillTone.neutral,
  });

  final String label;
  final IconData? icon;
  final PrettyPillTone tone;

  @override
  Widget build(BuildContext context) {
    final (bg, fg) = tone.colorsOf(context);
    return AnimatedContainer(
      duration: AppMotion.duration(context, AppMotion.control),
      curve: AppMotion.enter,
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: ShapeDecoration(color: bg, shape: const StadiumBorder()),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 15, color: fg),
            const SizedBox(width: 5),
          ],
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelMedium?.copyWith(
                color: fg,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

enum PrettyPillTone {
  neutral,
  primary,
  success,
  warning,
  danger;

  (Color, Color) colorsOf(BuildContext context) {
    final scheme = context.colors;
    final semantic = context.semanticColors;
    return switch (this) {
      neutral => (scheme.surfaceContainerHigh, scheme.onSurfaceVariant),
      primary => (scheme.secondaryContainer, scheme.onSecondaryContainer),
      success => (semantic.successContainer, semantic.onSuccessContainer),
      warning => (semantic.warningContainer, semantic.onWarningContainer),
      danger => (scheme.errorContainer, scheme.onErrorContainer),
    };
  }
}

class PrettyDisclosureHeader extends StatelessWidget {
  const PrettyDisclosureHeader({
    required this.title,
    required this.expanded,
    required this.progress,
    required this.onTap,
    super.key,
    this.icon,
    this.subtitle,
    this.trailing,
  });

  final String title;
  final bool expanded;
  final Animation<double> progress;
  final VoidCallback onTap;
  final IconData? icon;
  final String? subtitle;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Semantics(
      button: true,
      expanded: expanded,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: const BorderRadius.all(
            Radius.circular(AppRadius.workspaceInset),
          ),
          splashFactory: NoSplash.splashFactory,
          hoverColor: scheme.onSurface.withValues(alpha: 0.05),
          highlightColor: scheme.onSurface.withValues(alpha: 0.08),
          focusColor: scheme.primary.withValues(alpha: 0.10),
          onTap: onTap,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 44),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
              child: Row(
                children: [
                  if (icon != null) ...[
                    Icon(icon, size: 18, color: scheme.onSurfaceVariant),
                    const SizedBox(width: 9),
                  ],
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          style: theme.textTheme.titleSmall?.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        if (subtitle != null)
                          Text(
                            subtitle!,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: scheme.onSurfaceVariant,
                            ),
                          ),
                      ],
                    ),
                  ),
                  if (trailing != null)
                    Flexible(
                      child: Align(
                        alignment: Alignment.centerRight,
                        child: trailing,
                      ),
                    ),
                  const SizedBox(width: 8),
                  RotationTransition(
                    turns: progress.drive(Tween(begin: 0.0, end: 0.5)),
                    child: Icon(
                      Icons.expand_more_rounded,
                      size: 22,
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class PrettyExpandableSection extends StatefulWidget {
  const PrettyExpandableSection({
    required this.title,
    required this.child,
    super.key,
    this.icon,
    this.subtitle,
    this.trailing,
    this.initiallyExpanded = false,
    this.padding,
  });

  final String title;
  final IconData? icon;
  final String? subtitle;
  final Widget? trailing;
  final Widget child;
  final bool initiallyExpanded;
  final EdgeInsetsGeometry? padding;

  @override
  State<PrettyExpandableSection> createState() =>
      _PrettyExpandableSectionState();
}

class _PrettyExpandableSectionState extends State<PrettyExpandableSection>
    with SingleTickerProviderStateMixin {
  late bool _expanded = widget.initiallyExpanded;
  late bool _builtBody = _expanded;
  late final _reveal = AnimationController(
    vsync: this,
    duration: AppMotion.reveal,
    value: _expanded ? 1 : 0,
  );
  late final _height = _reveal.drive(CurveTween(curve: AppMotion.travel));
  late final _fade = _reveal.drive(
    CurveTween(curve: const Interval(0.16, 1, curve: AppMotion.enter)),
  );

  void _toggle() {
    setState(() {
      _expanded = !_expanded;
      _builtBody = true;
    });
    if (MediaQuery.disableAnimationsOf(context)) {
      _reveal.value = _expanded ? 1 : 0;
    } else if (_expanded) {
      _reveal.forward();
    } else {
      _reveal.reverse();
    }
  }

  @override
  void dispose() {
    _reveal.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    const radius = BorderRadius.all(Radius.circular(AppRadius.workspaceInset));
    final bodyPadding =
        widget.padding ??
        EdgeInsets.fromLTRB(
          AppSpacing.sm,
          MediaQuery.textScalerOf(context).scale(AppSpacing.sm),
          AppSpacing.sm,
          AppSpacing.sm,
        );
    return AnimatedContainer(
      duration: AppMotion.duration(context, AppMotion.reveal),
      curve: AppMotion.enter,
      decoration: BoxDecoration(
        color: _expanded ? scheme.surfaceContainer : Colors.transparent,
        borderRadius: radius,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          PrettyDisclosureHeader(
            title: widget.title,
            subtitle: widget.subtitle,
            icon: widget.icon,
            trailing: widget.trailing,
            expanded: _expanded,
            progress: _height,
            onTap: _toggle,
          ),
          if (_builtBody)
            SizeTransition(
              sizeFactor: _height,
              alignment: Alignment.topCenter,
              child: ExcludeFocus(
                excluding: !_expanded,
                child: ExcludeSemantics(
                  excluding: !_expanded,
                  child: IgnorePointer(
                    ignoring: !_expanded,
                    child: FadeTransition(
                      opacity: _fade,
                      child: Padding(padding: bodyPadding, child: widget.child),
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class WorkspaceEmptyState extends StatelessWidget {
  const WorkspaceEmptyState({
    required this.icon,
    required this.title,
    required this.subtitle,
    super.key,
  });

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Container(
      constraints: const BoxConstraints(
        minHeight: AppLayoutTokens.emptyStateMinHeight,
      ),
      width: double.infinity,
      alignment: Alignment.center,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: scheme.surfaceContainer,
        borderRadius: BorderRadius.circular(AppRadius.workspaceInset),
      ),
      child: Row(
        children: [
          DecoratedBox(
            decoration: ShapeDecoration(
              color: scheme.surfaceContainerHighest,
              shape: RoundedSuperellipseBorder(
                borderRadius: BorderRadius.circular(AppRadius.workspaceControl),
              ),
            ),
            child: SizedBox.square(
              dimension: 43,
              child: Icon(icon, color: scheme.onSurfaceVariant, size: 23),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                if (subtitle.isNotEmpty) ...[
                  const SizedBox(height: 3),
                  Text(
                    subtitle,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}
