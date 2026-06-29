import 'package:flutter/material.dart';

import '../../app/app_spacing.dart';

enum SurfaceCardVariant { filled, elevated, outlined }

class SurfaceCard extends StatelessWidget {
  const SurfaceCard({
    required this.title,
    required this.child,
    this.icon,
    this.trailing,
    this.subtitle,
    this.variant = SurfaceCardVariant.filled,
    this.dense = false,
    this.compact = false,
    super.key,
  });

  final String title;
  final String? subtitle;
  final IconData? icon;
  final Widget? trailing;
  final SurfaceCardVariant variant;
  final bool dense;
  final bool compact;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final radius = compact
        ? AppRadius.lg
        : dense
        ? AppRadius.lg
        : AppRadius.xl;
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(radius),
    );
    final content = Padding(
      padding: EdgeInsets.all(
        compact
            ? AppSpacing.sm
            : dense
            ? AppSpacing.md
            : AppSpacing.lg,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              if (icon != null) ...[
                Icon(icon, color: theme.colorScheme.primary),
                const SizedBox(width: AppSpacing.sm),
              ],
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    if (subtitle != null) ...[
                      const SizedBox(height: AppSpacing.xxs),
                      Text(
                        subtitle!,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
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
            ],
          ),
          SizedBox(
            height: compact
                ? AppSpacing.xs
                : dense
                ? AppSpacing.sm
                : AppSpacing.md,
          ),
          child,
        ],
      ),
    );
    return switch (variant) {
      SurfaceCardVariant.elevated => Card(
        elevation: AppElevation.level1,
        shadowColor: theme.colorScheme.shadow.withValues(alpha: 0.16),
        surfaceTintColor: Colors.transparent,
        clipBehavior: Clip.antiAlias,
        shape: shape,
        child: content,
      ),
      SurfaceCardVariant.outlined => Card.outlined(
        surfaceTintColor: Colors.transparent,
        clipBehavior: Clip.antiAlias,
        shape: shape.copyWith(
          side: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
        child: content,
      ),
      SurfaceCardVariant.filled => Card.filled(
        color: theme.colorScheme.surfaceContainerLow,
        surfaceTintColor: Colors.transparent,
        clipBehavior: Clip.antiAlias,
        shape: shape,
        child: content,
      ),
    };
  }
}

class AnimatedSurfaceCard extends StatelessWidget {
  const AnimatedSurfaceCard({
    required this.title,
    required this.child,
    this.icon,
    this.trailing,
    this.subtitle,
    this.variant = SurfaceCardVariant.filled,
    super.key,
  });

  final String title;
  final String? subtitle;
  final IconData? icon;
  final Widget? trailing;
  final SurfaceCardVariant variant;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return SurfaceCard(
      title: title,
      subtitle: subtitle,
      icon: icon,
      trailing: trailing,
      variant: variant,
      child: AnimatedSize(
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOutCubic,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [child],
        ),
      ),
    );
  }
}
