import 'package:flutter/material.dart';

import '../shared/widgets/app_motion.dart';
import 'app_spacing.dart';

class EtoileTheme {
  static const Color seed = Color(0xFF246BDA);
  static const String fontFamily = 'EtoileSans';

  static ThemeData build(Brightness brightness, {ColorScheme? dynamicScheme}) {
    final scheme = dynamicScheme ?? _brandScheme(brightness);
    final dark = brightness == Brightness.dark;
    final typography = Typography.material2021(
      platform: TargetPlatform.android,
    );
    final textTheme = _typeset(
      (dark ? typography.white : typography.black).apply(
        bodyColor: scheme.onSurface,
        displayColor: scheme.onSurface,
        fontFamily: fontFamily,
      ),
    );
    final shape = WidgetStateProperty.resolveWith<OutlinedBorder>((states) {
      return RoundedSuperellipseBorder(
        borderRadius: BorderRadius.circular(
          states.contains(WidgetState.pressed)
              ? AppRadius.workspacePressed
              : AppRadius.workspaceControl,
        ),
      );
    });
    final filled = FilledButton.styleFrom(
      minimumSize: const Size(48, 44),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
      textStyle: textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w600),
      animationDuration: AppMotion.control,
    ).copyWith(shape: shape);

    return ThemeData(
      useMaterial3: true,
      fontFamily: fontFamily,
      colorScheme: scheme,
      scaffoldBackgroundColor: scheme.surface,
      textTheme: textTheme,
      extensions: [EtoileSemanticColors.forBrightness(brightness)],
      appBarTheme: AppBarTheme(
        centerTitle: false,
        elevation: AppElevation.level0,
        scrolledUnderElevation: AppElevation.level0,
        backgroundColor: scheme.surface,
        foregroundColor: scheme.onSurface,
        titleTextStyle: textTheme.titleLarge?.copyWith(
          color: scheme.onSurface,
          fontWeight: FontWeight.w700,
        ),
      ),
      cardTheme: CardThemeData(
        elevation: AppElevation.level0,
        color: scheme.surfaceContainerLow,
        surfaceTintColor: Colors.transparent,
        margin: EdgeInsets.zero,
        clipBehavior: Clip.antiAlias,
        shape: RoundedSuperellipseBorder(
          borderRadius: BorderRadius.circular(AppRadius.workspaceCard),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(style: filled),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: filled.copyWith(
          side: WidgetStateProperty.resolveWith((states) {
            return BorderSide(
              color: states.contains(WidgetState.disabled)
                  ? scheme.outlineVariant.withValues(alpha: 0.55)
                  : scheme.outlineVariant,
            );
          }),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          minimumSize: const Size(48, 40),
          animationDuration: AppMotion.control,
        ).copyWith(shape: shape),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: IconButton.styleFrom(
          minimumSize: const Size(40, 40),
          animationDuration: AppMotion.control,
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: scheme.surfaceContainer,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md,
          vertical: 13,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.workspaceControl),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.workspaceControl),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.workspaceControl),
          borderSide: BorderSide(color: scheme.primary, width: 2),
        ),
      ),
      chipTheme: ChipThemeData(
        side: BorderSide(color: scheme.outlineVariant),
        backgroundColor: scheme.surfaceContainerHigh,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.workspaceControl),
        ),
      ),
      navigationRailTheme: NavigationRailThemeData(
        backgroundColor: scheme.surface,
        indicatorColor: scheme.secondaryContainer,
        selectedIconTheme: IconThemeData(color: scheme.onSecondaryContainer),
        selectedLabelTextStyle: textTheme.labelLarge?.copyWith(
          color: scheme.onSecondaryContainer,
          fontWeight: FontWeight.w700,
        ),
        unselectedIconTheme: IconThemeData(color: scheme.onSurfaceVariant),
        unselectedLabelTextStyle: textTheme.labelLarge?.copyWith(
          color: scheme.onSurfaceVariant,
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: scheme.surfaceContainer,
        indicatorColor: scheme.secondaryContainer,
        iconTheme: WidgetStateProperty.resolveWith(
          (states) => IconThemeData(
            size: 22,
            color: states.contains(WidgetState.selected)
                ? scheme.onSecondaryContainer
                : scheme.onSurfaceVariant,
          ),
        ),
        labelTextStyle: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return textTheme.labelMedium?.copyWith(
            fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
            color: selected ? scheme.onSurface : scheme.onSurfaceVariant,
          );
        }),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: scheme.surfaceContainerLow,
        surfaceTintColor: Colors.transparent,
        shape: RoundedSuperellipseBorder(
          borderRadius: BorderRadius.circular(AppRadius.workspaceDialog),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: scheme.inverseSurface,
        contentTextStyle: textTheme.bodyMedium?.copyWith(
          color: scheme.onInverseSurface,
        ),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.workspaceControl),
        ),
      ),
      dividerTheme: DividerThemeData(
        color: scheme.outlineVariant,
        space: 1,
        thickness: 1,
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(
        color: scheme.primary,
        linearTrackColor: scheme.surfaceContainerHighest,
      ),
      listTileTheme: ListTileThemeData(
        iconColor: scheme.onSurfaceVariant,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.workspaceInset),
        ),
        contentPadding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md,
          vertical: AppSpacing.xs,
        ),
      ),
    );
  }

  static ColorScheme _brandScheme(Brightness brightness) {
    final base = ColorScheme.fromSeed(seedColor: seed, brightness: brightness);
    return brightness == Brightness.dark
        ? base.copyWith(
            surface: const Color(0xFF19191E),
            surfaceContainerLowest: const Color(0xFF141418),
            surfaceContainerLow: const Color(0xFF232329),
            surfaceContainer: const Color(0xFF2B2B32),
            surfaceContainerHigh: const Color(0xFF33333B),
            surfaceContainerHighest: const Color(0xFF3E3D46),
          )
        : base.copyWith(
            primary: const Color(0xFF0064DA),
            surface: const Color(0xFFEDECEF),
            surfaceContainerLowest: const Color(0xFFFFFFFF),
            surfaceContainerLow: const Color(0xFFFBFAFC),
            surfaceContainer: const Color(0xFFF0EFF2),
            surfaceContainerHigh: const Color(0xFFEAE9ED),
            surfaceContainerHighest: const Color(0xFFE1E0E6),
            onSurface: const Color(0xFF1B1C23),
            onSurfaceVariant: const Color(0xFF4D505B),
            outlineVariant: const Color(0xFFD6D4DD),
          );
  }

  static TextTheme _typeset(TextTheme theme) {
    TextStyle? spaced(TextStyle? style) => style?.copyWith(letterSpacing: 0);
    return theme.copyWith(
      displayLarge: spaced(theme.displayLarge),
      displayMedium: spaced(theme.displayMedium),
      displaySmall: spaced(theme.displaySmall),
      headlineLarge: spaced(theme.headlineLarge),
      headlineMedium: spaced(theme.headlineMedium),
      headlineSmall: spaced(theme.headlineSmall),
      titleLarge: spaced(theme.titleLarge),
      titleMedium: spaced(theme.titleMedium),
      titleSmall: spaced(theme.titleSmall),
      bodyLarge: spaced(theme.bodyLarge),
      bodyMedium: spaced(theme.bodyMedium),
      bodySmall: spaced(theme.bodySmall),
      labelLarge: spaced(theme.labelLarge),
      labelMedium: spaced(theme.labelMedium),
      labelSmall: spaced(theme.labelSmall),
    );
  }
}

extension EtoileThemeContext on BuildContext {
  ColorScheme get colors => Theme.of(this).colorScheme;

  EtoileSemanticColors get semanticColors =>
      Theme.of(this).extension<EtoileSemanticColors>() ??
      EtoileSemanticColors.forBrightness(colors.brightness);
}

@immutable
class EtoileSemanticColors extends ThemeExtension<EtoileSemanticColors> {
  const EtoileSemanticColors({
    required this.success,
    required this.onSuccess,
    required this.successContainer,
    required this.onSuccessContainer,
    required this.warning,
    required this.onWarning,
    required this.warningContainer,
    required this.onWarningContainer,
  });

  final Color success;
  final Color onSuccess;
  final Color successContainer;
  final Color onSuccessContainer;
  final Color warning;
  final Color onWarning;
  final Color warningContainer;
  final Color onWarningContainer;

  static EtoileSemanticColors forBrightness(Brightness brightness) =>
      brightness == Brightness.dark
      ? const EtoileSemanticColors(
          success: Color(0xFF98D6A7),
          onSuccess: Color(0xFF00391B),
          successContainer: Color(0xFF204A31),
          onSuccessContainer: Color(0xFFC5EED0),
          warning: Color(0xFFFFCF83),
          onWarning: Color(0xFF432C00),
          warningContainer: Color(0xFF503916),
          onWarningContainer: Color(0xFFFFDFAB),
        )
      : const EtoileSemanticColors(
          success: Color(0xFF24663D),
          onSuccess: Color(0xFFFFFFFF),
          successContainer: Color(0xFFDAF1E0),
          onSuccessContainer: Color(0xFF17482B),
          warning: Color(0xFF7A4D00),
          onWarning: Color(0xFFFFFFFF),
          warningContainer: Color(0xFFFFEBCB),
          onWarningContainer: Color(0xFF603A00),
        );

  @override
  EtoileSemanticColors copyWith({
    Color? success,
    Color? onSuccess,
    Color? successContainer,
    Color? onSuccessContainer,
    Color? warning,
    Color? onWarning,
    Color? warningContainer,
    Color? onWarningContainer,
  }) {
    return EtoileSemanticColors(
      success: success ?? this.success,
      onSuccess: onSuccess ?? this.onSuccess,
      successContainer: successContainer ?? this.successContainer,
      onSuccessContainer: onSuccessContainer ?? this.onSuccessContainer,
      warning: warning ?? this.warning,
      onWarning: onWarning ?? this.onWarning,
      warningContainer: warningContainer ?? this.warningContainer,
      onWarningContainer: onWarningContainer ?? this.onWarningContainer,
    );
  }

  @override
  EtoileSemanticColors lerp(
    ThemeExtension<EtoileSemanticColors>? other,
    double t,
  ) {
    if (other is! EtoileSemanticColors) return this;
    return EtoileSemanticColors(
      success: Color.lerp(success, other.success, t)!,
      onSuccess: Color.lerp(onSuccess, other.onSuccess, t)!,
      successContainer: Color.lerp(
        successContainer,
        other.successContainer,
        t,
      )!,
      onSuccessContainer: Color.lerp(
        onSuccessContainer,
        other.onSuccessContainer,
        t,
      )!,
      warning: Color.lerp(warning, other.warning, t)!,
      onWarning: Color.lerp(onWarning, other.onWarning, t)!,
      warningContainer: Color.lerp(
        warningContainer,
        other.warningContainer,
        t,
      )!,
      onWarningContainer: Color.lerp(
        onWarningContainer,
        other.onWarningContainer,
        t,
      )!,
    );
  }
}
