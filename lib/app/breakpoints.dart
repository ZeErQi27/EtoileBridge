enum WindowClass { compact, medium, expanded, large, extraLarge }

class Breakpoints {
  static const double compactMax = 600;
  static const double mediumMax = 840;
  static const double expandedMax = 1200;
  static const double largeMax = 1600;
  static const double contentMaxWidth = 1560;
  static const double readableMaxWidth = 1180;
  static const double railWidth = 88;
  static const double railExtendedWidth = 224;

  static WindowClass classify(double width) {
    if (!width.isFinite || width <= 0) return WindowClass.compact;
    if (width < compactMax) return WindowClass.compact;
    if (width < mediumMax) return WindowClass.medium;
    if (width < expandedMax) return WindowClass.expanded;
    if (width < largeMax) return WindowClass.large;
    return WindowClass.extraLarge;
  }
}
