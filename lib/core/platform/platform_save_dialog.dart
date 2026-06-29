import 'dart:io';

import 'package:flutter/services.dart';

import '../logging/app_action_logger.dart';

abstract interface class PlatformSaveDialog {
  Future<String?> saveArcpkg({
    required String suggestedName,
    String? initialDirectory,
  });
}

class RealPlatformSaveDialog implements PlatformSaveDialog {
  static const _channel = MethodChannel(
    'com.zeerqi27.etoile_bridge/file_dialogs',
  );

  @override
  Future<String?> saveArcpkg({
    required String suggestedName,
    String? initialDirectory,
  }) async {
    if (!Platform.isWindows && !Platform.isAndroid) return null;
    final name = suggestedName.endsWith('.arcpkg')
        ? suggestedName
        : '$suggestedName.arcpkg';
    if (Platform.isAndroid) {
      AppActionLogger.write(
        phase: 'start',
        page: 'platform',
        id: 'android.saveDocument.start',
        label: name,
        before: 'platform',
      );
    }
    try {
      final result = await _channel.invokeMethod<String>('saveFile', {
        'suggestedName': name,
        'initialDirectory': initialDirectory,
        'extension': 'arcpkg',
      });
      if (Platform.isAndroid) {
        AppActionLogger.write(
          phase: result == null ? 'cancel' : 'end',
          page: 'platform',
          id: result == null
              ? 'android.saveDocument.cancel'
              : 'android.saveDocument.success',
          label: result ?? name,
          before: 'platform',
        );
      }
      return result;
    } catch (error) {
      if (Platform.isAndroid) {
        AppActionLogger.write(
          phase: 'error',
          page: 'platform',
          id: 'android.saveDocument.error',
          label: error.toString(),
          before: 'platform',
        );
      }
      rethrow;
    }
  }
}

class MockPlatformSaveDialog implements PlatformSaveDialog {
  @override
  Future<String?> saveArcpkg({
    required String suggestedName,
    String? initialDirectory,
  }) async {
    return 'mock://save/$suggestedName';
  }
}
