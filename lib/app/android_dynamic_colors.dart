import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

typedef AndroidColorSchemes = ({ColorScheme light, ColorScheme dark});

class AndroidDynamicColors {
  const AndroidDynamicColors._();

  static const _channel = MethodChannel('com.zeerqi27.etoile_bridge/theme');

  static Future<AndroidColorSchemes?> load() async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
      return null;
    }
    try {
      final values = await _channel.invokeMapMethod<String, Object?>(
        'dynamicColorScheme',
      );
      if (values == null || values.isEmpty) return null;
      final primary = _readColor(values, 'primary');
      if (primary == null) return null;
      final secondary = _readColor(values, 'secondary');
      final tertiary = _readColor(values, 'tertiary');
      ColorScheme scheme(Brightness brightness) {
        ColorScheme fromSeed(Color seed) =>
            ColorScheme.fromSeed(seedColor: seed, brightness: brightness);
        final base = fromSeed(primary);
        final accent2 = secondary == null ? null : fromSeed(secondary);
        final accent3 = tertiary == null ? null : fromSeed(tertiary);
        return base.copyWith(
          secondary: accent2?.primary,
          onSecondary: accent2?.onPrimary,
          secondaryContainer: accent2?.primaryContainer,
          onSecondaryContainer: accent2?.onPrimaryContainer,
          tertiary: accent3?.primary,
          onTertiary: accent3?.onPrimary,
          tertiaryContainer: accent3?.primaryContainer,
          onTertiaryContainer: accent3?.onPrimaryContainer,
        );
      }

      return (light: scheme(Brightness.light), dark: scheme(Brightness.dark));
    } on MissingPluginException {
      return null;
    } on PlatformException {
      return null;
    }
  }

  static Color? _readColor(Map<String, Object?> values, String key) {
    final value = values[key];
    if (value is int) return Color(value);
    if (value is num) return Color(value.toInt());
    return null;
  }
}
