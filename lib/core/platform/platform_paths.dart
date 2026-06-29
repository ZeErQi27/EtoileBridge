import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;

abstract interface class PlatformPaths {
  Future<String> cacheRoot();
}

class RealPlatformPaths implements PlatformPaths {
  static const MethodChannel _converterChannel = MethodChannel(
    'com.zeerqi27.etoile_bridge/converter',
  );

  @override
  Future<String> cacheRoot() async {
    if (Platform.isAndroid) {
      final root = await _converterChannel.invokeMethod<String>('cacheRoot');
      if (root == null || root.isEmpty) {
        throw StateError('Android cache root is unavailable.');
      }
      final directory = Directory(root);
      if (!await directory.exists()) {
        await directory.create(recursive: true);
      }
      return directory.path;
    }

    final base = Platform.isWindows
        ? (Platform.environment['LOCALAPPDATA'] ??
              Platform.environment['APPDATA'] ??
              Directory.systemTemp.path)
        : Directory.systemTemp.path;
    final root = Directory(p.join(base, 'EtoileBridgeFlutter', 'cache'));
    if (!await root.exists()) {
      await root.create(recursive: true);
    }
    return root.path;
  }
}

class MockPlatformPaths implements PlatformPaths {
  @override
  Future<String> cacheRoot() async =>
      'mock://app-cache/EtoileBridgeFlutter/cache';
}
