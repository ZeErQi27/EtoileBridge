import 'package:flutter/material.dart';

import '../../app/app_spacing.dart';

enum NoticeTone { info, success, warning, error }

class StatusNotice extends StatelessWidget {
  const StatusNotice({
    required this.title,
    required this.message,
    required this.icon,
    this.tone = NoticeTone.info,
    this.trailing,
    super.key,
  });

  final String title;
  final String message;
  final IconData icon;
  final NoticeTone tone;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final (Color background, Color foreground) = switch (tone) {
      NoticeTone.success => (
        scheme.primaryContainer,
        scheme.onPrimaryContainer,
      ),
      NoticeTone.warning => (
        scheme.tertiaryContainer,
        scheme.onTertiaryContainer,
      ),
      NoticeTone.error => (scheme.errorContainer, scheme.onErrorContainer),
      NoticeTone.info => (
        scheme.secondaryContainer,
        scheme.onSecondaryContainer,
      ),
    };
    return Card.filled(
      color: background,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: foreground),
            const SizedBox(width: AppSpacing.sm),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: theme.textTheme.titleSmall?.copyWith(
                      color: foreground,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: AppSpacing.xxs),
                  Text(
                    message,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: foreground,
                    ),
                  ),
                ],
              ),
            ),
            if (trailing != null) ...[
              const SizedBox(width: AppSpacing.sm),
              IconTheme(
                data: IconThemeData(color: foreground),
                child: trailing!,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class EmptyTaskCard extends StatelessWidget {
  const EmptyTaskCard({
    required this.icon,
    required this.title,
    required this.message,
    required this.actions,
    this.footer,
    this.compact = false,
    super.key,
  });

  final IconData icon;
  final String title;
  final String message;
  final List<Widget> actions;
  final Widget? footer;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      elevation: AppElevation.level1,
      color: theme.colorScheme.surfaceContainerLowest,
      surfaceTintColor: Colors.transparent,
      shadowColor: theme.colorScheme.shadow.withValues(alpha: 0.16),
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(
          compact ? AppRadius.lg : AppRadius.xl,
        ),
      ),
      child: Padding(
        padding: EdgeInsets.all(compact ? AppSpacing.lg : AppSpacing.xxl),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              icon,
              size: compact ? 32 : 42,
              color: theme.colorScheme.primary,
            ),
            SizedBox(height: compact ? AppSpacing.md : AppSpacing.lg),
            Text(
              title,
              style:
                  (compact
                          ? theme.textTheme.titleLarge
                          : theme.textTheme.headlineSmall)
                      ?.copyWith(fontWeight: FontWeight.w700),
            ),
            SizedBox(height: compact ? AppSpacing.xs : AppSpacing.sm),
            Text(
              message,
              style:
                  (compact
                          ? theme.textTheme.bodyMedium
                          : theme.textTheme.bodyLarge)
                      ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
            SizedBox(height: compact ? AppSpacing.md : AppSpacing.xl),
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.sm,
              children: actions,
            ),
            if (footer != null) ...[
              const SizedBox(height: AppSpacing.xl),
              footer!,
            ],
          ],
        ),
      ),
    );
  }
}

class InfoPill extends StatelessWidget {
  const InfoPill({
    required this.label,
    required this.value,
    this.icon,
    super.key,
  });

  final String label;
  final String value;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InputDecorator(
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: icon == null ? null : Icon(icon),
        filled: true,
      ),
      child: Text(
        value,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: theme.textTheme.bodyMedium?.copyWith(
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class AnimatedSection extends StatelessWidget {
  const AnimatedSection({
    required this.child,
    this.duration = const Duration(milliseconds: 220),
    super.key,
  });

  final Widget child;
  final Duration duration;

  @override
  Widget build(BuildContext context) {
    final reduceMotion =
        MediaQuery.maybeOf(context)?.disableAnimations ?? false;
    return AnimatedSwitcher(
      duration: reduceMotion ? Duration.zero : duration,
      switchInCurve: Curves.easeOutCubic,
      switchOutCurve: Curves.easeInCubic,
      transitionBuilder: (child, animation) {
        return FadeTransition(
          opacity: animation,
          child: SizeTransition(sizeFactor: animation, child: child),
        );
      },
      child: child,
    );
  }
}
