import 'dart:convert';
import 'dart:io';

import 'package:etoile_bridge/app/app_state.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;

const _runBatchEnv = 'ETOILEBRIDGE_OFFICIAL_BATCH_SMOKE';
const _batchRootEnv = 'ETOILEBRIDGE_OFFICIAL_BATCH_ROOT';
const _defaultBatchRoot = r'G:\我的云端硬盘\谱面\官谱格式';
const _designHakimiName = '设计哈基米.zip';

void main() {
  final shouldRun =
      Platform.environment[_runBatchEnv] == '1' && Platform.isWindows;
  final skipReason = shouldRun
      ? false
      : 'Set $_runBatchEnv=1 on Windows to scan the local official chart corpus.';

  test(
    'batch scans local official-format single-song samples',
    () async {
      final root = Directory(
        Platform.environment[_batchRootEnv]?.trim().isNotEmpty == true
            ? Platform.environment[_batchRootEnv]!
            : _defaultBatchRoot,
      );
      expect(root.existsSync(), isTrue, reason: root.path);

      final candidates = _collectCandidates(root);
      expect(
        candidates,
        isNotEmpty,
        reason: 'No .zip or official-folder candidates under ${root.path}',
      );

      final passed = <Map<String, Object?>>[];
      final failed = <Map<String, Object?>>[];

      for (final candidate in candidates) {
        final state = AppState.bootstrap();
        try {
          await state.scanSingleSong(candidate.path);
          final scan = state.singleSong.scan;
          if (scan == null) {
            failed.add(
              _failure(
                candidate,
                'scan',
                'unknown',
                state.singleSong.error ?? 'scan returned null',
              ),
            );
            continue;
          }
          final hasStructure =
              scan.charts.isNotEmpty ||
              scan.affFiles.isNotEmpty ||
              scan.audio != null ||
              scan.jacket != null ||
              scan.background != null ||
              scan.songlist != null;
          if (!hasStructure) {
            failed.add(
              _failure(
                candidate,
                'scan',
                'official-structure-missing',
                'scan succeeded without chart/resource markers',
              ),
            );
            continue;
          }
          passed.add({
            'path': candidate.path,
            'kind': candidate is File ? 'zip' : 'folder',
            'songId': scan.songId,
            'title': scan.title,
            'charts': scan.charts.length,
            'affFiles': scan.affFiles.length,
            'resources': {
              'audio': scan.audio?.name,
              'jacket': scan.jacket?.name,
              'background': scan.background?.name,
              'songlist': scan.songlist?.name,
            },
            'warnings': scan.warnings,
          });
        } catch (error, stack) {
          failed.add(
            _failure(
              candidate,
              'scan',
              _classify(error.toString()),
              '$error\n$stack',
            ),
          );
        }
      }

      final report = {
        'root': root.path,
        'total': candidates.length,
        'passed': passed.length,
        'failed': failed.length,
        'passedItems': passed,
        'failedItems': failed,
      };
      final reportDir = Directory(p.join('build', 'official-song-batch-smoke'))
        ..createSync(recursive: true);
      final reportFile = File(
        p.join(reportDir.path, 'official-song-batch-report.json'),
      );
      reportFile.writeAsStringSync(
        const JsonEncoder.withIndent('  ').convert(report),
        encoding: utf8,
      );

      // ignore: avoid_print
      print('official batch scan report: ${reportFile.absolute.path}');
      // ignore: avoid_print
      print(
        'official batch scan summary: ${passed.length}/${candidates.length} passed, ${failed.length} failed',
      );
      for (final item in failed) {
        // ignore: avoid_print
        print('FAILED [${item['type']}] ${item['path']} :: ${item['message']}');
      }

      final design = candidates
          .where((entity) => p.basename(entity.path) == _designHakimiName)
          .toList();
      if (design.isNotEmpty) {
        final designFailed = failed
            .where((item) => item['path'] == design.first.path)
            .toList();
        expect(
          designFailed,
          isEmpty,
          reason:
              '$_designHakimiName must scan after ZIP encoding compatibility fix',
        );
      }
    },
    skip: skipReason,
    timeout: const Timeout(Duration(minutes: 30)),
  );
}

List<FileSystemEntity> _collectCandidates(Directory root) {
  final zips = <FileSystemEntity>[];
  final dirs = <Directory>[];
  for (final entity in root.listSync(recursive: true, followLinks: false)) {
    final name = p.basename(entity.path);
    if (name == '__MACOSX' || name == '.DS_Store') continue;
    if (entity is File && entity.path.toLowerCase().endsWith('.zip')) {
      zips.add(entity);
      continue;
    }
    if (entity is Directory && _hasOfficialMarkers(entity)) {
      dirs.add(entity);
    }
  }
  final compactDirs = dirs.where((dir) {
    return !dirs.any(
      (parent) => parent.path != dir.path && p.isWithin(parent.path, dir.path),
    );
  });
  return [...zips, ...compactDirs].toList()
    ..sort((a, b) => a.path.toLowerCase().compareTo(b.path.toLowerCase()));
}

bool _hasOfficialMarkers(Directory dir) {
  final children = dir.listSync(followLinks: false);
  final names = children
      .map((entity) => p.basename(entity.path).toLowerCase())
      .toSet();
  if (names.any(
    (name) =>
        name == 'songlist' ||
        name == 'songlist.json' ||
        name == 'slst' ||
        name == 'slst.json',
  )) {
    return true;
  }
  if (names.contains('project.arcproj')) return true;
  return children.any(
    (entity) => entity is File && entity.path.toLowerCase().endsWith('.aff'),
  );
}

Map<String, Object?> _failure(
  FileSystemEntity entity,
  String stage,
  String type,
  String message,
) {
  return {
    'path': entity.path,
    'kind': entity is File ? 'zip' : 'folder',
    'stage': stage,
    'type': type,
    'message': message,
  };
}

String _classify(String message) {
  final lower = message.toLowerCase();
  if (lower.contains('invalid cen') ||
      lower.contains('bad entry name') ||
      lower.contains('zip') && lower.contains('encoding')) {
    return 'zip-encoding';
  }
  if (lower.contains('zip') &&
      (lower.contains('corrupt') ||
          lower.contains('malformed') ||
          lower.contains('unexpected'))) {
    return 'zip-structure-damaged';
  }
  if (lower.contains('songlist') ||
      lower.contains('metadata') ||
      lower.contains('aff')) {
    return 'official-structure-missing';
  }
  if (lower.contains('resource') ||
      lower.contains('audio') ||
      lower.contains('jacket') ||
      lower.contains('background')) {
    return 'resource-missing-but-continuable';
  }
  return 'unknown';
}
