import 'dart:io';

abstract interface class PlatformOpenLocation {
  Future<bool> openLocation(String path);
}

class RealPlatformOpenLocation implements PlatformOpenLocation {
  @override
  Future<bool> openLocation(String path) async {
    if (path.isEmpty) return false;
    if (Platform.isWindows) {
      final type = await FileSystemEntity.type(path);
      if (type == FileSystemEntityType.notFound) return false;
      if (type == FileSystemEntityType.directory) {
        final result = await Process.run('explorer.exe', [path]);
        return result.exitCode == 0;
      }
      final result = await Process.run('explorer.exe', ['/select,', path]);
      return result.exitCode == 0;
    }
    return false;
  }
}

class MockPlatformOpenLocation implements PlatformOpenLocation {
  @override
  Future<bool> openLocation(String path) async => path.isNotEmpty;
}
