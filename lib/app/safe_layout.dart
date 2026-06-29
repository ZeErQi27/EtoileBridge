import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'app_spacing.dart';
import 'breakpoints.dart';

class SafeLayout {
  const SafeLayout._();

  static const double minViewportWidth = 320;
  static const double minViewportHeight = 480;
  static const double pageHorizontalPadding = AppSpacing.lg;
  static const double pageTopPadding = AppSpacing.md;
  static const double pageBottomPadding = 80;
  static const double cardGap = AppSpacing.md;

  static double finite(
    double value, {
    required double fallback,
    double min = 1,
  }) {
    if (!value.isFinite || value < min) return fallback;
    return value;
  }

  static double widthFromConstraints(
    BoxConstraints constraints,
    BuildContext context,
  ) {
    final fallback = math.max(
      MediaQuery.sizeOf(context).width,
      minViewportWidth,
    );
    return finite(constraints.maxWidth, fallback: fallback);
  }

  static double heightOf(BuildContext context) {
    return finite(
      MediaQuery.sizeOf(context).height,
      fallback: minViewportHeight,
    );
  }

  static WindowClass classify(double width) {
    return Breakpoints.classify(finite(width, fallback: minViewportWidth));
  }

  static int gridColumns(double width, {int desktopColumns = 2}) {
    final safeWidth = finite(width, fallback: minViewportWidth);
    if (safeWidth < Breakpoints.expandedMax) return 1;
    return math.max(1, desktopColumns);
  }

  static EdgeInsets pagePadding(BuildContext context) {
    final bottomInset = finite(
      MediaQuery.paddingOf(context).bottom,
      fallback: 0,
      min: 0,
    );
    return EdgeInsets.fromLTRB(
      pageHorizontalPadding,
      pageTopPadding,
      pageHorizontalPadding,
      bottomInset + pageBottomPadding,
    );
  }

  static BoxConstraints dialogConstraints(BuildContext context) {
    final height = heightOf(context);
    final availableHeight = math.max(180.0, height - 48.0);
    return BoxConstraints(
      maxWidth: 440,
      maxHeight: availableHeight.clamp(180.0, 640.0),
    );
  }
}
