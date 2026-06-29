import 'dart:io';

import 'package:path/path.dart' as p;

import 'platform_paths.dart';

class CacheCleanupResult {
  const CacheCleanupResult({
    required this.message,
    required this.bytesBefore,
    required this.bytesAfter,
    required this.deletedFiles,
    required this.deletedDirectories,
    required this.skippedActiveSessions,
    required this.failedEntries,
  });

  final String message;
  final int bytesBefore;
  final int bytesAfter;
  final int deletedFiles;
  final int deletedDirectories;
  final int skippedActiveSessions;
  final int failedEntries;

  int get freedBytes => bytesBefore - bytesAfter;
}

abstract interface class PlatformCacheService {
  Future<String> cacheRoot();
  Future<CacheCleanupResult> cleanupSafe({
    bool includeActiveSession = false,
    Set<String> activeSessionPaths = const {},
  });
}

class RealPlatformCacheService implements PlatformCacheService {
  RealPlatformCacheService(this.paths);

  final PlatformPaths paths;

  @override
  Future<String> cacheRoot() => paths.cacheRoot();

  @override
  Future<CacheCleanupResult> cleanupSafe({
    bool includeActiveSession = false,
    Set<String> activeSessionPaths = const {},
  }) async {
    final root = Directory(await paths.cacheRoot());
    final before = await _directorySize(root);
    var deletedFiles = 0;
    var deletedDirectories = 0;
    var skippedActiveSessions = 0;
    var failedEntries = 0;
    if (await root.exists()) {
      await for (final entity in root.list(followLinks: false)) {
        if (entity is! Directory) continue;
        final name = p.basename(entity.path);
        if (!_isManagedCacheSessionName(name)) continue;
        if (!includeActiveSession &&
            _isActiveSession(entity.path, activeSessionPaths)) {
          skippedActiveSessions++;
          continue;
        }
        try {
          final counts = await _countEntities(entity);
          await entity.delete(recursive: true);
          deletedFiles += counts.files;
          deletedDirectories += counts.directories + 1;
        } catch (_) {
          failedEntries++;
        }
      }
    }
    final after = await _directorySize(root);
    return CacheCleanupResult(
      message: 'cleaned inactive sessions',
      bytesBefore: before,
      bytesAfter: after,
      deletedFiles: deletedFiles,
      deletedDirectories: deletedDirectories,
      skippedActiveSessions: skippedActiveSessions,
      failedEntries: failedEntries,
    );
  }
}

class MockPlatformCacheService implements PlatformCacheService {
  MockPlatformCacheService(this.paths);

  final PlatformPaths paths;

  @override
  Future<String> cacheRoot() => paths.cacheRoot();

  @override
  Future<CacheCleanupResult> cleanupSafe({
    bool includeActiveSession = false,
    Set<String> activeSessionPaths = const {},
  }) async {
    return const CacheCleanupResult(
      message: 'mock cleanup',
      bytesBefore: 42 * 1024 * 1024,
      bytesAfter: 8 * 1024 * 1024,
      deletedFiles: 12,
      deletedDirectories: 3,
      skippedActiveSessions: 0,
      failedEntries: 0,
    );
  }
}

bool _isManagedCacheSessionName(String name) {
  return name.startsWith('single-session-') ||
      name.startsWith('pack-session-') ||
      name.startsWith('character-session-') ||
      name.startsWith('session-') ||
      name.startsWith('single-') ||
      name.startsWith('pack-') ||
      name.startsWith('character-');
}

bool _isActiveSession(String sessionPath, Set<String> activeSessionPaths) {
  final normalizedSession = p.normalize(sessionPath);
  for (final active in activeSessionPaths) {
    if (active.trim().isEmpty) continue;
    final normalizedActive = p.normalize(active);
    if (p.equals(normalizedSession, normalizedActive) ||
        p.isWithin(normalizedSession, normalizedActive) ||
        p.isWithin(normalizedActive, normalizedSession)) {
      return true;
    }
  }
  return false;
}

Future<({int files, int directories})> _countEntities(
  Directory directory,
) async {
  var files = 0;
  var directories = 0;
  if (!await directory.exists()) return (files: 0, directories: 0);
  await for (final entity in directory.list(
    recursive: true,
    followLinks: false,
  )) {
    if (entity is File) files++;
    if (entity is Directory) directories++;
  }
  return (files: files, directories: directories);
}

Future<int> _directorySize(Directory directory) async {
  if (!await directory.exists()) return 0;
  var total = 0;
  await for (final entity in directory.list(
    recursive: true,
    followLinks: false,
  )) {
    if (entity is File) {
      total += await entity.length();
    }
  }
  return total;
}
