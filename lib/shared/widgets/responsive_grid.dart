import 'package:flutter/material.dart';

import '../../app/app_spacing.dart';
import '../../app/safe_layout.dart';

class ResponsiveGrid extends StatelessWidget {
  const ResponsiveGrid({
    required this.children,
    this.desktopColumns = 2,
    super.key,
  });

  final List<Widget> children;
  final int desktopColumns;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = SafeLayout.widthFromConstraints(constraints, context);
        final columns = SafeLayout.gridColumns(
          width,
          desktopColumns: desktopColumns,
        );
        if (columns == 1) {
          return Column(
            children: [
              for (final child in children) ...[
                child,
                const SizedBox(height: 16),
              ],
            ],
          );
        }
        final left = <Widget>[];
        final right = <Widget>[];
        for (var i = 0; i < children.length; i++) {
          (i.isEven ? left : right).add(children[i]);
        }
        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(child: _ColumnWithGap(children: left)),
            const SizedBox(width: 16),
            Expanded(child: _ColumnWithGap(children: right)),
          ],
        );
      },
    );
  }
}

class TaskColumns extends StatelessWidget {
  const TaskColumns({
    required this.primary,
    required this.secondary,
    this.primaryFlex = 11,
    this.secondaryFlex = 7,
    this.breakpoint = 1040,
    this.gap = AppSpacing.lg,
    super.key,
  });

  final List<Widget> primary;
  final List<Widget> secondary;
  final int primaryFlex;
  final int secondaryFlex;
  final double breakpoint;
  final double gap;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = SafeLayout.widthFromConstraints(constraints, context);
        if (width < breakpoint) {
          return _ColumnWithGap(gap: gap, children: [...primary, ...secondary]);
        }
        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              flex: primaryFlex,
              child: _ColumnWithGap(gap: gap, children: primary),
            ),
            SizedBox(width: gap),
            Expanded(
              flex: secondaryFlex,
              child: _ColumnWithGap(gap: gap, children: secondary),
            ),
          ],
        );
      },
    );
  }
}

class _ColumnWithGap extends StatelessWidget {
  const _ColumnWithGap({required this.children, this.gap = 16});

  final List<Widget> children;
  final double gap;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        for (final child in children) ...[child, SizedBox(height: gap)],
      ],
    );
  }
}
