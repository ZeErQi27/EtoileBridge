import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

class AppLayoutTokens {
  const AppLayoutTokens._();

  static const double pageContentMaxWidth = 1880;
  static const double gridGap = 16;
  static const double cardPadding = 18;
  static const double cardHeadingHeight = 32;
  static const double cardHeadingGap = 12;
  static const double emptyStateMinHeight = 76;
  static const double threeColumnBreakpoint = 1310;
  static const double twoColumnBreakpoint = 840;
  static const double maxCardAlignmentStretch = 120;

  static EdgeInsets pagePadding(double width) {
    final inset = width < 640 ? 16.0 : (width < 1100 ? 22.0 : 26.0);
    return EdgeInsets.fromLTRB(inset, width < 640 ? 16 : 26, inset, 36);
  }
}

class AppWorkspaceScaffold extends StatefulWidget {
  const AppWorkspaceScaffold({
    required this.title,
    required this.child,
    this.actions = const [],
    super.key,
  });

  final String title;
  final List<Widget> actions;
  final Widget child;

  @override
  State<AppWorkspaceScaffold> createState() => _AppWorkspaceScaffoldState();
}

class _AppWorkspaceScaffoldState extends State<AppWorkspaceScaffold> {
  final _scrollController = ScrollController();

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final scrollView = LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        controller: _scrollController,
        primary: false,
        child: Align(
          alignment: Alignment.topCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: AppLayoutTokens.pageContentMaxWidth,
            ),
            child: Padding(
              padding: AppLayoutTokens.pagePadding(constraints.maxWidth),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _WorkspaceHeading(
                    title: widget.title,
                    actions: widget.actions,
                  ),
                  const SizedBox(height: AppLayoutTokens.gridGap),
                  widget.child,
                ],
              ),
            ),
          ),
        ),
      ),
    );
    switch (Theme.of(context).platform) {
      case TargetPlatform.linux:
      case TargetPlatform.macOS:
      case TargetPlatform.windows:
        return ScrollConfiguration(
          behavior: ScrollConfiguration.of(context).copyWith(scrollbars: false),
          child: Scrollbar(
            controller: _scrollController,
            thumbVisibility: true,
            interactive: true,
            child: scrollView,
          ),
        );
      case TargetPlatform.android:
      case TargetPlatform.fuchsia:
      case TargetPlatform.iOS:
        return scrollView;
    }
  }
}

class _WorkspaceHeading extends StatelessWidget {
  const _WorkspaceHeading({required this.title, required this.actions});

  final String title;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final heading = Semantics(
      header: true,
      child: Text(
        title,
        key: const ValueKey('workspace-title'),
        style: theme.textTheme.headlineMedium?.copyWith(
          color: theme.colorScheme.onSurface,
          fontWeight: FontWeight.w600,
          letterSpacing: 0,
        ),
      ),
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        final inline = constraints.maxWidth >= 610;
        final chips = Wrap(
          spacing: 8,
          runSpacing: 6,
          alignment: inline ? WrapAlignment.end : WrapAlignment.start,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: actions,
        );
        return ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 44),
          child: inline
              ? Row(
                  children: [
                    Expanded(child: heading),
                    if (actions.isNotEmpty) ...[
                      const SizedBox(width: 14),
                      Flexible(
                        child: Align(
                          alignment: Alignment.centerRight,
                          child: chips,
                        ),
                      ),
                    ],
                  ],
                )
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    heading,
                    if (actions.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      chips,
                    ],
                  ],
                ),
        );
      },
    );
  }
}

class AppWorkspacePanel {
  const AppWorkspacePanel(this.id, this.child, {this.span = 1});

  final String id;
  final Widget child;
  final int span;

  AppWorkspacePanel across(int columns) =>
      AppWorkspacePanel(id, child, span: columns);
}

class AppWorkspaceBand {
  AppWorkspaceBand(
    List<AppWorkspacePanel> panels, {
    bool matchHeight = true,
    double maxStretch = double.infinity,
    this.gapBefore = AppLayoutTokens.gridGap,
  }) : maxStretch = matchHeight ? maxStretch : 0,
       lanes = [
         for (final panel in panels) [panel],
       ];

  const AppWorkspaceBand.stacked(this.lanes, {this.maxStretch = 0})
    : gapBefore = AppLayoutTokens.gridGap;

  final List<List<AppWorkspacePanel>> lanes;
  final double maxStretch;
  final double gapBefore;
}

class AppWorkspaceLayout extends StatelessWidget {
  const AppWorkspaceLayout({required this.bands, super.key});

  final List<AppWorkspaceBand> Function(int columns) bands;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth;
        final columns = width >= AppLayoutTokens.threeColumnBreakpoint
            ? 3
            : width >= AppLayoutTokens.twoColumnBreakpoint
            ? 2
            : 1;
        final rows = bands(columns);
        assert(
          rows.every(
            (row) =>
                row.lanes.isNotEmpty &&
                row.lanes.every(
                  (lane) =>
                      lane.isNotEmpty &&
                      lane.every((panel) => panel.span == lane.first.span),
                ) &&
                row.lanes.fold(0, (sum, lane) => sum + lane.first.span) ==
                    columns,
          ),
        );
        return _WorkspaceGrid(
          columns: columns,
          rows: [
            for (final row in rows)
              (
                lanes: [
                  for (final lane in row.lanes)
                    (span: lane.first.span, count: lane.length),
                ],
                maxStretch: row.maxStretch,
                gapBefore: row.gapBefore,
              ),
          ],
          direction: Directionality.of(context),
          children: [
            for (final row in rows)
              for (final lane in row.lanes)
                for (final panel in lane)
                  KeyedSubtree(key: ValueKey(panel.id), child: panel.child),
          ],
        );
      },
    );
  }
}

typedef _WorkspaceLaneGeometry = ({int span, int count});
typedef _WorkspaceRowGeometry = ({
  List<_WorkspaceLaneGeometry> lanes,
  double maxStretch,
  double gapBefore,
});

class _WorkspaceGrid extends MultiChildRenderObjectWidget {
  const _WorkspaceGrid({
    required this.columns,
    required this.rows,
    required this.direction,
    required super.children,
  });

  final int columns;
  final List<_WorkspaceRowGeometry> rows;
  final TextDirection direction;

  @override
  _RenderWorkspaceGrid createRenderObject(BuildContext context) =>
      _RenderWorkspaceGrid(columns, rows, direction);

  @override
  void updateRenderObject(
    BuildContext context,
    _RenderWorkspaceGrid renderObject,
  ) {
    renderObject.configure(columns, rows, direction);
  }
}

class _WorkspaceCellData extends ContainerBoxParentData<RenderBox> {}

class _RenderWorkspaceGrid extends RenderBox
    with
        ContainerRenderObjectMixin<RenderBox, _WorkspaceCellData>,
        RenderBoxContainerDefaultsMixin<RenderBox, _WorkspaceCellData> {
  _RenderWorkspaceGrid(this._columns, this._rows, this._direction);

  int _columns;
  List<_WorkspaceRowGeometry> _rows;
  TextDirection _direction;

  void configure(
    int columns,
    List<_WorkspaceRowGeometry> rows,
    TextDirection direction,
  ) {
    if (_columns == columns &&
        _direction == direction &&
        _rows.length == rows.length) {
      var unchanged = true;
      for (final (index, row) in rows.indexed) {
        final previous = _rows[index];
        if (row.maxStretch != previous.maxStretch ||
            row.gapBefore != previous.gapBefore ||
            !listEquals(row.lanes, previous.lanes)) {
          unchanged = false;
          break;
        }
      }
      if (unchanged) return;
    }
    _columns = columns;
    _rows = rows;
    _direction = direction;
    markNeedsLayout();
  }

  @override
  void setupParentData(RenderBox child) {
    if (child.parentData is! _WorkspaceCellData) {
      child.parentData = _WorkspaceCellData();
    }
  }

  @override
  void performLayout() {
    const gap = AppLayoutTokens.gridGap;
    final width = constraints.maxWidth;
    final track = (width - gap * (_columns - 1)) / _columns;
    RenderBox? next = firstChild;
    double top = 0;
    for (final (index, row) in _rows.indexed) {
      if (index > 0) top += row.gapBefore;
      final alignEnds = row.maxStretch > 0 && row.lanes.length > 1;
      final tails = <({RenderBox box, double bottom})>[];
      double leading = 0;
      double height = 0;
      // Measure at each card's actual width; layout-only widgets cannot
      // produce intrinsic heights before their children are built.
      for (final lane in row.lanes) {
        final cellWidth = lane.span * (track + gap) - gap;
        final left = _direction == TextDirection.ltr
            ? leading
            : width - leading - cellWidth;
        double laneTop = top;
        for (var i = 0; i < lane.count; i++) {
          if (i > 0) laneTop += gap;
          final cell = next!;
          cell.layout(
            BoxConstraints.tightFor(width: cellWidth),
            parentUsesSize: true,
          );
          final data = cell.parentData! as _WorkspaceCellData;
          data.offset = Offset(left, laneTop);
          laneTop += cell.size.height;
          next = data.nextSibling;
          if (alignEnds && i == lane.count - 1) {
            tails.add((box: cell, bottom: laneTop - top));
          }
        }
        leading += cellWidth + gap;
        height = math.max(height, laneTop - top);
      }
      if (alignEnds) {
        final shortest = tails.map((tail) => tail.bottom).reduce(math.min);
        final difference = height - shortest;
        // Ease out of alignment as a disclosure grows. A hard cutoff makes
        // the neighboring surfaces jump when their height catches up again.
        final release = difference <= row.maxStretch
            ? 1.0
            : 1.0 -
                  Curves.easeInOut.transform(
                    ((difference - row.maxStretch) / row.maxStretch).clamp(
                      0,
                      1,
                    ),
                  );
        for (final (:box, :bottom) in tails) {
          final extra = math.min(height - bottom, row.maxStretch) * release;
          if (extra > 0) {
            // Keep vertical layout loose so a changing child can request a
            // fresh measurement instead of relaying out at a stale height.
            box.layout(
              BoxConstraints(
                minWidth: box.size.width,
                maxWidth: box.size.width,
                minHeight: box.size.height + extra,
              ),
              parentUsesSize: true,
            );
          }
        }
      }
      top += height;
    }
    size = constraints.constrain(Size(width, top));
  }

  @override
  void paint(PaintingContext context, Offset offset) =>
      defaultPaint(context, offset);

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) =>
      defaultHitTestChildren(result, position: position);
}
