import 'package:flutter/material.dart';

import '../../app/app_spacing.dart';

class MockPreview extends StatelessWidget {
  const MockPreview({
    required this.label,
    this.aspectRatio = 16 / 9,
    this.icon = Icons.image_rounded,
    super.key,
  });

  final String label;
  final double aspectRatio;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final safeAspectRatio = aspectRatio.isFinite && aspectRatio > 0
        ? aspectRatio
        : 16 / 9;
    return LayoutBuilder(
      builder: (context, constraints) {
        final compact = constraints.maxHeight.isFinite
            ? constraints.maxHeight < 120
            : false;
        final iconSize = compact ? 30.0 : 42.0;
        return AspectRatio(
          aspectRatio: safeAspectRatio,
          child: Card.outlined(
            color: theme.colorScheme.surfaceContainerHighest.withValues(
              alpha: 0.58,
            ),
            clipBehavior: Clip.antiAlias,
            child: Center(
              child: Padding(
                padding: const EdgeInsets.all(AppSpacing.sm),
                child: FittedBox(
                  fit: BoxFit.scaleDown,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        icon,
                        size: iconSize,
                        color: theme.colorScheme.primary,
                      ),
                      if (label.isNotEmpty) ...[
                        const SizedBox(height: AppSpacing.xs),
                        SizedBox(
                          width: 160,
                          child: Text(
                            label,
                            textAlign: TextAlign.center,
                            maxLines: compact ? 1 : 2,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.bodyMedium,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
