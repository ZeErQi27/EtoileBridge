import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class AndroidDynamicColors {
  const AndroidDynamicColors._();

  static const _channel = MethodChannel('com.zeerqi27.etoile_bridge/theme');

  static Future<ColorScheme?> lightScheme() async {
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
      return ColorScheme.fromSeed(
        seedColor: primary,
        brightness: Brightness.light,
      ).copyWith(
        primary: primary,
        primaryContainer: _readColor(values, 'primaryContainer'),
        onPrimaryContainer: _readColor(values, 'onPrimaryContainer'),
        secondary: _readColor(values, 'secondary'),
        secondaryContainer: _readColor(values, 'secondaryContainer'),
        onSecondaryContainer: _readColor(values, 'onSecondaryContainer'),
        tertiary: _readColor(values, 'tertiary'),
        tertiaryContainer: _readColor(values, 'tertiaryContainer'),
        onTertiaryContainer: _readColor(values, 'onTertiaryContainer'),
        surface: _readColor(values, 'surface'),
        onSurface: _readColor(values, 'onSurface'),
        surfaceContainerLow: _readColor(values, 'surfaceContainerLow'),
        surfaceContainer: _readColor(values, 'surfaceContainer'),
        surfaceContainerHigh: _readColor(values, 'surfaceContainerHigh'),
        surfaceContainerHighest: _readColor(values, 'surfaceContainerHighest'),
        onSurfaceVariant: _readColor(values, 'onSurfaceVariant'),
        outline: _readColor(values, 'outline'),
        outlineVariant: _readColor(values, 'outlineVariant'),
      );
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
