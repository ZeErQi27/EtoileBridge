import 'dart:io';

import 'package:flutter/services.dart';

import '../logging/app_action_logger.dart';

abstract interface class PlatformFilePicker {
  Future<String?> pickZip();
  Future<String?> pickFolder();
  Future<String?> pickImage();
  Future<String?> pickArcpkg();
  Future<List<String>> pickMultipleArcpkg();
}

class RealPlatformFilePicker implements PlatformFilePicker {
  static const _channel = MethodChannel(
    'com.zeerqi27.etoile_bridge/file_dialogs',
  );

  @override
  Future<String?> pickArcpkg() async {
    return _pickFile(['arcpkg']);
  }

  @override
  Future<String?> pickFolder() async {
    if (!Platform.isWindows && !Platform.isAndroid) return null;
    return _invokePicker(
      method: 'pickFolder',
      startId: 'android.pickFolder.start',
      successId: 'android.pickFolder.success',
      cancelId: 'android.pickFolder.cancel',
      errorId: 'android.pickFolder.error',
    );
  }

  @override
  Future<String?> pickImage() async {
    return _pickFile(['png', 'jpg', 'jpeg', 'webp']);
  }

  @override
  Future<List<String>> pickMultipleArcpkg() async {
    if (!Platform.isWindows && !Platform.isAndroid) return const [];
    if (Platform.isAndroid) {
      AppActionLogger.write(
        phase: 'start',
        page: 'platform',
        id: 'android.pickArcpkgMultiple.start',
        label: 'pickFiles',
        before: 'platform',
      );
    }
    try {
      final result = await _channel.invokeListMethod<String>('pickFiles', {
        'extensions': ['arcpkg'],
      });
      final paths = result?.where((path) => path.trim().isNotEmpty).toList();
      if (Platform.isAndroid) {
        AppActionLogger.write(
          phase: paths == null || paths.isEmpty ? 'cancel' : 'end',
          page: 'platform',
          id: paths == null || paths.isEmpty
              ? 'android.pickArcpkgMultiple.cancel'
              : 'android.pickArcpkgMultiple.success',
          label: '${paths?.length ?? 0}',
          before: 'platform',
        );
      }
      return paths ?? const [];
    } catch (error) {
      if (Platform.isAndroid) {
        AppActionLogger.write(
          phase: 'error',
          page: 'platform',
          id: 'android.pickArcpkgMultiple.error',
          label: error.toString(),
          before: 'platform',
        );
      }
      rethrow;
    }
  }

  @override
  Future<String?> pickZip() async {
    return _pickFile(['zip']);
  }

  Future<String?> _pickFile(List<String> extensions) async {
    if (!Platform.isWindows && !Platform.isAndroid) return null;
    final isZip = extensions.length == 1 && extensions.single == 'zip';
    return _invokePicker(
      method: 'pickFile',
      arguments: {'extensions': extensions},
      startId: isZip ? 'android.pickZip.start' : 'android.pickFile.start',
      successId: isZip ? 'android.pickZip.success' : 'android.pickFile.success',
      cancelId: isZip ? 'android.pickZip.cancel' : 'android.pickFile.cancel',
      errorId: isZip ? 'android.pickZip.error' : 'android.pickFile.error',
    );
  }

  Future<String?> _invokePicker({
    required String method,
    Object? arguments,
    required String startId,
    required String successId,
    required String cancelId,
    required String errorId,
  }) async {
    if (Platform.isAndroid) {
      AppActionLogger.write(
        phase: 'start',
        page: 'platform',
        id: startId,
        label: method,
        before: 'platform',
      );
    }
    try {
      final result = await _channel.invokeMethod<String>(method, arguments);
      if (Platform.isAndroid) {
        AppActionLogger.write(
          phase: result == null ? 'cancel' : 'end',
          page: 'platform',
          id: result == null ? cancelId : successId,
          label: result ?? method,
          before: 'platform',
        );
      }
      return result;
    } catch (error) {
      if (Platform.isAndroid) {
        AppActionLogger.write(
          phase: 'error',
          page: 'platform',
          id: errorId,
          label: error.toString(),
          before: 'platform',
        );
      }
      rethrow;
    }
  }
}

class MockPlatformFilePicker implements PlatformFilePicker {
  @override
  Future<String?> pickArcpkg() async => 'mock://selected/package.arcpkg';

  @override
  Future<String?> pickFolder() async => 'mock://selected/folder';

  @override
  Future<String?> pickImage() async => 'mock://selected/image.png';

  @override
  Future<List<String>> pickMultipleArcpkg() async => [
    'mock://selected/level-1.arcpkg',
    'mock://selected/level-2.arcpkg',
  ];

  @override
  Future<String?> pickZip() async => 'mock://selected/chart.zip';
}
