import 'package:flutter/material.dart';

import '../../app/theme.dart';
import 'app_motion.dart';

class WorkspaceChoice<T> {
  const WorkspaceChoice(this.value, this.label, {this.icon, this.tooltip});

  final T value;
  final String label;
  final IconData? icon;
  final String? tooltip;
}

class WorkspaceChoices<T> extends StatelessWidget {
  const WorkspaceChoices({
    required this.choices,
    required this.value,
    required this.onChanged,
    super.key,
  });

  final List<WorkspaceChoice<T>> choices;
  final T value;
  final ValueChanged<T>? onChanged;

  @override
  Widget build(BuildContext context) {
    if (choices.isEmpty) return const SizedBox.shrink();
    final colors = context.colors;
    final dark = colors.brightness == Brightness.dark;
    final selected = choices.indexWhere((choice) => choice.value == value);
    final enabled = onChanged != null;
    final travelSign = Directionality.of(context) == TextDirection.ltr
        ? 1.0
        : -1.0;
    const gap = 4.0;
    final fill = enabled ? colors.primary : colors.surfaceContainerHighest;
    final labelStyle = Theme.of(
      context,
    ).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w600);
    return DecoratedBox(
      decoration: ShapeDecoration(
        color: colors.surfaceContainerHigh,
        shape: StadiumBorder(
          side: BorderSide(
            color: colors.onSurface.withValues(alpha: dark ? 0.08 : 0.045),
          ),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(4),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final cell =
                (constraints.maxWidth - (choices.length - 1) * gap) /
                choices.length;
            final showIcons =
                cell >= 103 && MediaQuery.textScalerOf(context).scale(14) <= 16;
            return TweenAnimationBuilder<double>(
              tween: Tween(end: selected.toDouble()),
              duration: AppMotion.duration(context, AppMotion.reveal),
              curve: AppMotion.travel,
              builder: (context, position, plate) {
                final indexAt = position.clamp(0.0, choices.length - 1.0);
                Color foregroundAt(int index) {
                  if (!enabled) return colors.onSurface.withValues(alpha: 0.38);
                  if (selected < 0) return colors.onSurfaceVariant;
                  final distance = (indexAt - index).abs();
                  // Let the contrast change as the moving pill reaches the
                  // label, so text never switches ahead of the background.
                  final coverage = ((0.52 - distance) / 0.26).clamp(0.0, 1.0);
                  return Color.lerp(
                    colors.onSurfaceVariant,
                    colors.onPrimary,
                    Curves.easeOutCubic.transform(coverage),
                  )!;
                }

                return Stack(
                  clipBehavior: Clip.none,
                  children: [
                    if (selected != -1)
                      Positioned.fill(
                        child: IgnorePointer(
                          child: Align(
                            alignment: AlignmentDirectional.centerStart,
                            child: Transform.translate(
                              offset: Offset(
                                indexAt * (cell + gap) * travelSign,
                                0,
                              ),
                              child: SizedBox(
                                width: cell,
                                height: double.infinity,
                                child: plate,
                              ),
                            ),
                          ),
                        ),
                      ),
                    Row(
                      children: [
                        for (final (index, choice) in choices.indexed) ...[
                          if (index > 0) const SizedBox(width: gap),
                          Expanded(
                            child: Tooltip(
                              message: choice.tooltip ?? choice.label,
                              child: Semantics(
                                button: true,
                                selected: index == selected,
                                child: Material(
                                  type: MaterialType.transparency,
                                  child: InkWell(
                                    customBorder: const StadiumBorder(),
                                    splashFactory: NoSplash.splashFactory,
                                    onTap: enabled
                                        ? () => onChanged!(choice.value)
                                        : null,
                                    child: ConstrainedBox(
                                      constraints: const BoxConstraints(
                                        minHeight: 42,
                                      ),
                                      child: Padding(
                                        padding: const EdgeInsets.symmetric(
                                          horizontal: 4,
                                          vertical: 7,
                                        ),
                                        child: Row(
                                          mainAxisAlignment:
                                              MainAxisAlignment.center,
                                          children: [
                                            if (showIcons &&
                                                choice.icon != null) ...[
                                              Icon(
                                                choice.icon,
                                                size: 17,
                                                color: foregroundAt(index),
                                              ),
                                              const SizedBox(width: 5),
                                            ],
                                            Flexible(
                                              child: Text(
                                                choice.label,
                                                textAlign: TextAlign.center,
                                                maxLines: 2,
                                                overflow: TextOverflow.ellipsis,
                                                style: labelStyle?.copyWith(
                                                  color: foregroundAt(index),
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
                            ),
                          ),
                        ],
                      ],
                    ),
                  ],
                );
              },
              child: DecoratedBox(
                key: const ValueKey('workspace-choice-indicator'),
                decoration: ShapeDecoration(
                  color: fill,
                  shape: const StadiumBorder(),
                  shadows: enabled
                      ? [
                          BoxShadow(
                            color: fill.withValues(alpha: dark ? 0.24 : 0.30),
                            blurRadius: 8,
                            offset: const Offset(0, 3),
                            spreadRadius: -2,
                          ),
                        ]
                      : null,
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}
