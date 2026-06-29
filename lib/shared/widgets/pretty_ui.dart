import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/material.dart';

import '../../app/app_spacing.dart';

class PrettyColors {
  const PrettyColors._();

  static const page = Color(0xFFF5FBFF);
  static const panel = Color(0xFFFFFFFF);
  static const panelSoft = Color(0xFFFAFDFF);
  static const border = Color(0xFFDCEEF8);
  static const borderStrong = Color(0xFF9DD7EE);
  static const primary = Color(0xFF168FE8);
  static const primaryDark = Color(0xFF075A95);
  static const primarySoft = Color(0xFFE1F4FF);
  static const primarySofter = Color(0xFFF0FAFF);
  static const cyan = Color(0xFF4BC7E9);
  static const pink = Color(0xFFE95BE1);
  static const text = Color(0xFF102033);
  static const muted = Color(0xFF647487);
  static const faint = Color(0xFF95AABE);
  static const success = Color(0xFF2D8F65);
  static const warning = Color(0xFFB17000);
  static const danger = Color(0xFFC9446B);
}

class PrettyRadii {
  const PrettyRadii._();

  static const double card = 24;
  static const double cardLarge = 28;
  static const double control = 18;
  static const double pill = 999;
}

class PrettyCard extends StatefulWidget {
  const PrettyCard({
    required this.child,
    super.key,
    this.title,
    this.icon,
    this.trailing,
    this.padding = const EdgeInsets.all(20),
    this.margin,
    this.compact = false,
    this.emphasized = false,
  });

  final String? title;
  final IconData? icon;
  final Widget? trailing;
  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry? margin;
  final bool compact;
  final bool emphasized;

  @override
  State<PrettyCard> createState() => _PrettyCardState();
}

class _PrettyCardState extends State<PrettyCard> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final radius = BorderRadius.circular(
      widget.compact ? PrettyRadii.control : PrettyRadii.card,
    );
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOutCubic,
      builder: (context, value, child) {
        return Transform.translate(
          offset: Offset(0, (1 - value) * 10),
          child: Transform.scale(
            scale: 0.992 + value * 0.008,
            alignment: Alignment.topCenter,
            child: child,
          ),
        );
      },
      child: MouseRegion(
        onEnter: (_) => setState(() => _hovered = true),
        onExit: (_) => setState(() => _hovered = false),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeOutCubic,
          margin: widget.margin,
          decoration: BoxDecoration(
            color: widget.emphasized
                ? scheme.surface
                : scheme.surface.withValues(alpha: 0.92),
            borderRadius: radius,
            border: Border.all(
              color: _hovered
                  ? scheme.outline.withValues(alpha: 0.48)
                  : scheme.outlineVariant.withValues(alpha: 0.72),
            ),
            boxShadow: [
              BoxShadow(
                color: scheme.primary.withValues(
                  alpha: _hovered ? 0.13 : 0.075,
                ),
                blurRadius: _hovered ? 34 : 24,
                offset: Offset(0, _hovered ? 15 : 10),
              ),
            ],
          ),
          child: ClipRRect(
            borderRadius: radius,
            child: Material(
              color: Colors.transparent,
              child: Padding(
                padding: widget.padding,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (widget.title != null || widget.trailing != null) ...[
                      Row(
                        children: [
                          if (widget.icon != null) ...[
                            Icon(
                              widget.icon,
                              size: 21,
                              color: scheme.onSurface,
                            ),
                            const SizedBox(width: 10),
                          ],
                          if (widget.title != null)
                            Expanded(
                              child: Text(
                                widget.title!,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: Theme.of(context).textTheme.titleMedium
                                    ?.copyWith(
                                      fontWeight: FontWeight.w800,
                                      color: scheme.onSurface,
                                    ),
                              ),
                            )
                          else
                            const Spacer(),
                          if (widget.trailing != null)
                            Flexible(
                              fit: FlexFit.loose,
                              child: Align(
                                alignment: Alignment.centerRight,
                                child: widget.trailing!,
                              ),
                            ),
                        ],
                      ),
                      const SizedBox(height: 16),
                    ],
                    widget.child,
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

class PrettyButton extends StatelessWidget {
  const PrettyButton({
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
    final scheme = Theme.of(context).colorScheme;
    final foreground = primary ? scheme.onPrimary : scheme.primary;
    final background = primary
        ? scheme.primary
        : scheme.secondaryContainer.withValues(alpha: 0.72);
    return AnimatedOpacity(
      duration: const Duration(milliseconds: 160),
      opacity: onPressed == null ? 0.46 : 1,
      child: DecoratedBox(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(PrettyRadii.pill),
          gradient: primary
              ? LinearGradient(colors: [scheme.primary, scheme.tertiary])
              : null,
        ),
        child: Material(
          color: primary ? Colors.transparent : background,
          borderRadius: BorderRadius.circular(PrettyRadii.pill),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onPressed,
            child: Padding(
              padding: EdgeInsets.symmetric(
                horizontal: compact ? 16 : 22,
                vertical: compact ? 11 : 14,
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(icon, color: foreground, size: compact ? 18 : 20),
                  const SizedBox(width: 9),
                  Flexible(
                    child: Text(
                      label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.labelLarge?.copyWith(
                        color: foreground,
                        fontWeight: FontWeight.w800,
                      ),
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

class PrettyGlassButton extends StatefulWidget {
  const PrettyGlassButton({
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
  State<PrettyGlassButton> createState() => _PrettyGlassButtonState();
}

class _PrettyGlassButtonState extends State<PrettyGlassButton> {
  bool _hovered = false;
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final enabled = widget.onPressed != null;
    final micaBase = Color.lerp(
      Colors.white,
      widget.primary ? scheme.primaryContainer : scheme.secondaryContainer,
      widget.primary ? 0.34 : 0.22,
    )!;
    final fill = !enabled
        ? scheme.surfaceContainerHighest.withValues(alpha: 0.32)
        : _pressed
        ? Color.lerp(
            micaBase,
            scheme.primaryContainer,
            0.22,
          )!.withValues(alpha: widget.primary ? 0.66 : 0.48)
        : _hovered
        ? micaBase.withValues(alpha: widget.primary ? 0.62 : 0.46)
        : micaBase.withValues(alpha: widget.primary ? 0.50 : 0.36);
    final borderColor = !enabled
        ? scheme.outlineVariant.withValues(alpha: 0.42)
        : (widget.primary ? scheme.primary : scheme.outline).withValues(
            alpha: _hovered ? 0.38 : 0.22,
          );
    final foreground = !enabled
        ? scheme.onSurfaceVariant.withValues(alpha: 0.54)
        : widget.primary
        ? scheme.primary
        : scheme.onSecondaryContainer;
    final radius = BorderRadius.circular(widget.compact ? 16 : 18);

    return MouseRegion(
      cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() {
        _hovered = false;
        _pressed = false;
      }),
      child: AnimatedScale(
        duration: const Duration(milliseconds: 120),
        curve: Curves.easeOutCubic,
        scale: _pressed ? 0.985 : (_hovered && enabled ? 1.012 : 1),
        child: Opacity(
          opacity: enabled ? 1 : 0.58,
          child: ClipRRect(
            borderRadius: radius,
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 22, sigmaY: 22),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 170),
                curve: Curves.easeOutCubic,
                decoration: BoxDecoration(
                  color: fill,
                  borderRadius: radius,
                  border: Border.all(color: borderColor, width: 1.1),
                  boxShadow: [
                    BoxShadow(
                      color: scheme.primary.withValues(
                        alpha: enabled ? (_hovered ? 0.14 : 0.075) : 0.025,
                      ),
                      blurRadius: _hovered ? 26 : 18,
                      offset: Offset(0, _hovered ? 9 : 6),
                    ),
                    BoxShadow(
                      color: Colors.white.withValues(alpha: 0.40),
                      blurRadius: 12,
                      offset: const Offset(-1.5, -1.5),
                    ),
                  ],
                ),
                child: Stack(
                  children: [
                    Positioned.fill(
                      child: Padding(
                        padding: const EdgeInsets.all(1),
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            borderRadius: radius,
                            border: Border.all(
                              color: Colors.white.withValues(
                                alpha: enabled
                                    ? (_hovered ? 0.72 : 0.54)
                                    : 0.28,
                              ),
                              width: 0.8,
                            ),
                          ),
                        ),
                      ),
                    ),
                    Material(
                      color: Colors.transparent,
                      child: InkWell(
                        onTap: widget.onPressed,
                        onHighlightChanged: (value) {
                          if (!enabled) return;
                          setState(() => _pressed = value);
                        },
                        child: Padding(
                          padding: EdgeInsets.symmetric(
                            horizontal: widget.compact ? 16 : 22,
                            vertical: widget.compact ? 11 : 14,
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                widget.icon,
                                color: foreground,
                                size: widget.compact ? 18 : 20,
                              ),
                              const SizedBox(width: 9),
                              Flexible(
                                child: Text(
                                  widget.label,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: Theme.of(context).textTheme.labelLarge
                                      ?.copyWith(
                                        color: foreground,
                                        fontWeight: FontWeight.w800,
                                      ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
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
    final scheme = Theme.of(context).colorScheme;
    final colors = switch (tone) {
      PrettyPillTone.primary => (
        bg: scheme.primaryContainer,
        fg: scheme.onPrimaryContainer,
      ),
      PrettyPillTone.success => (
        bg: const Color(0xFFE2F7EE),
        fg: PrettyColors.success,
      ),
      PrettyPillTone.warning => (
        bg: const Color(0xFFFFF1D8),
        fg: PrettyColors.warning,
      ),
      PrettyPillTone.danger => (
        bg: const Color(0xFFFFE3EC),
        fg: PrettyColors.danger,
      ),
      PrettyPillTone.neutral => (
        bg: scheme.surfaceContainerHighest.withValues(alpha: 0.62),
        fg: scheme.onSurfaceVariant,
      ),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
      decoration: BoxDecoration(
        color: colors.bg,
        borderRadius: BorderRadius.circular(PrettyRadii.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 16, color: colors.fg),
            const SizedBox(width: 6),
          ],
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelMedium?.copyWith(
                color: colors.fg,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

enum PrettyPillTone { neutral, primary, success, warning, danger }

class PrettyExpandableSection extends StatefulWidget {
  const PrettyExpandableSection({
    required this.title,
    required this.child,
    super.key,
    this.icon,
    this.subtitle,
    this.trailing,
    this.initiallyExpanded = false,
    this.padding = const EdgeInsets.fromLTRB(14, 12, 14, 14),
  });

  final String title;
  final IconData? icon;
  final String? subtitle;
  final Widget? trailing;
  final Widget child;
  final bool initiallyExpanded;
  final EdgeInsetsGeometry padding;

  @override
  State<PrettyExpandableSection> createState() =>
      _PrettyExpandableSectionState();
}

class _PrettyExpandableSectionState extends State<PrettyExpandableSection>
    with SingleTickerProviderStateMixin {
  late bool _expanded = widget.initiallyExpanded;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOutCubic,
      decoration: BoxDecoration(
        color: _expanded
            ? scheme.primaryContainer.withValues(alpha: 0.12)
            : Colors.transparent,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: _expanded
              ? scheme.outlineVariant.withValues(alpha: 0.52)
              : Colors.transparent,
        ),
      ),
      child: Column(
        children: [
          Material(
            color: Colors.transparent,
            borderRadius: BorderRadius.circular(18),
            child: InkWell(
              borderRadius: BorderRadius.circular(18),
              onTap: () => setState(() => _expanded = !_expanded),
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 12,
                ),
                child: Row(
                  children: [
                    if (widget.icon != null) ...[
                      Icon(widget.icon, size: 18, color: scheme.onSurface),
                      const SizedBox(width: 9),
                    ],
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            widget.title,
                            style: Theme.of(context).textTheme.titleSmall
                                ?.copyWith(
                                  color: scheme.onSurface,
                                  fontWeight: FontWeight.w800,
                                ),
                          ),
                          if (widget.subtitle != null) ...[
                            const SizedBox(height: 2),
                            Text(
                              widget.subtitle!,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: Theme.of(context).textTheme.bodySmall
                                  ?.copyWith(
                                    color: scheme.onSurfaceVariant,
                                    fontWeight: FontWeight.w600,
                                  ),
                            ),
                          ],
                        ],
                      ),
                    ),
                    if (widget.trailing != null) ...[
                      const SizedBox(width: 8),
                      widget.trailing!,
                    ],
                    const SizedBox(width: 8),
                    AnimatedRotation(
                      turns: _expanded ? 0.5 : 0,
                      duration: const Duration(milliseconds: 220),
                      curve: Curves.easeOutCubic,
                      child: Icon(
                        Icons.keyboard_arrow_down_rounded,
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          ClipRect(
            child: AnimatedSize(
              duration: const Duration(milliseconds: 260),
              curve: Curves.easeInOutCubic,
              alignment: Alignment.topCenter,
              child: _expanded
                  ? Padding(
                      padding: widget.padding,
                      child: AnimatedOpacity(
                        duration: const Duration(milliseconds: 180),
                        opacity: _expanded ? 1 : 0,
                        child: widget.child,
                      ),
                    )
                  : const SizedBox(width: double.infinity),
            ),
          ),
        ],
      ),
    );
  }
}

class DashedEmptyBox extends StatelessWidget {
  const DashedEmptyBox({
    required this.icon,
    required this.title,
    required this.subtitle,
    super.key,
    this.minHeight = 150,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final double minHeight;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return CustomPaint(
      painter: _DashedBorderPainter(),
      child: Container(
        constraints: BoxConstraints(minHeight: minHeight),
        width: double.infinity,
        alignment: Alignment.center,
        padding: const EdgeInsets.all(AppSpacing.lg),
        decoration: BoxDecoration(
          color: scheme.primaryContainer.withValues(alpha: 0.18),
          borderRadius: BorderRadius.circular(22),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              color: scheme.onSurfaceVariant.withValues(alpha: 0.7),
              size: 34,
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              title,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                color: scheme.onSurfaceVariant,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              subtitle,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: scheme.onSurfaceVariant.withValues(alpha: 0.72),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DashedBorderPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = PrettyColors.borderStrong
      ..strokeWidth = 1.2
      ..style = PaintingStyle.stroke;
    final radius = BorderRadius.circular(22).toRRect(Offset.zero & size);
    final path = Path()..addRRect(radius);
    for (final metric in path.computeMetrics()) {
      var distance = 0.0;
      const dash = 7.0;
      const gap = 5.0;
      while (distance < metric.length) {
        final next = math.min(distance + dash, metric.length);
        canvas.drawPath(metric.extractPath(distance, next), paint);
        distance = next + gap;
      }
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
