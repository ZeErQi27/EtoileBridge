import 'package:flutter/material.dart';

class AppLayoutTokens {
  const AppLayoutTokens._();

  static const double pageContentMaxWidth = 1760;
  static const double pageHorizontalPadding = 42;
  static const double pageHorizontalPaddingCompact = 18;
  static const double pageTopPadding = 30;
  static const double pageTopPaddingCompact = 20;
  static const double pageBottomPadding = 80;

  static const double columnGap = 24;
  static const double columnGapMedium = 22;
  static const double rowGap = 20;

  static const double leftColumnWidth = 400;
  static const double leftColumnWidthMedium = 410;
  static const double middleColumnWidth = 500;
  static const double rightColumnWidth = 430;

  static const double threeColumnBreakpoint = 1420;
  static const double twoColumnBreakpoint = 980;

  static EdgeInsets pagePadding(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    final compact = width < 700;
    final bottomInset = MediaQuery.paddingOf(context).bottom;
    return EdgeInsets.fromLTRB(
      compact ? pageHorizontalPaddingCompact : pageHorizontalPadding,
      compact ? pageTopPaddingCompact : pageTopPadding,
      compact ? pageHorizontalPaddingCompact : pageHorizontalPadding,
      bottomInset + pageBottomPadding,
    );
  }
}

class AppWorkspaceLayout extends StatelessWidget {
  const AppWorkspaceLayout({
    super.key,
    required this.left,
    required this.middle,
    required this.right,
    this.mediumOrder = AppWorkspaceMediumOrder.middleThenRight,
  });

  final List<Widget> left;
  final List<Widget> middle;
  final List<Widget> right;
  final AppWorkspaceMediumOrder mediumOrder;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth >= AppLayoutTokens.threeColumnBreakpoint) {
          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                width: AppLayoutTokens.leftColumnWidth,
                child: AppWorkspaceColumn(children: left),
              ),
              const SizedBox(width: AppLayoutTokens.columnGap),
              SizedBox(
                width: AppLayoutTokens.middleColumnWidth,
                child: AppWorkspaceColumn(children: middle),
              ),
              const SizedBox(width: AppLayoutTokens.columnGap),
              Expanded(child: AppWorkspaceColumn(children: right)),
            ],
          );
        }
        if (constraints.maxWidth >= AppLayoutTokens.twoColumnBreakpoint) {
          final remaining = switch (mediumOrder) {
            AppWorkspaceMediumOrder.middleThenRight => [...middle, ...right],
            AppWorkspaceMediumOrder.rightThenMiddle => [...right, ...middle],
          };
          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                width: AppLayoutTokens.leftColumnWidthMedium,
                child: AppWorkspaceColumn(children: left),
              ),
              const SizedBox(width: AppLayoutTokens.columnGapMedium),
              Expanded(child: AppWorkspaceColumn(children: remaining)),
            ],
          );
        }
        return AppWorkspaceColumn(children: [...left, ...middle, ...right]);
      },
    );
  }
}

enum AppWorkspaceMediumOrder { middleThenRight, rightThenMiddle }

class AppWorkspaceColumn extends StatelessWidget {
  const AppWorkspaceColumn({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var i = 0; i < children.length; i++) ...[
          children[i],
          if (i != children.length - 1)
            const SizedBox(height: AppLayoutTokens.rowGap),
        ],
      ],
    );
  }
}
