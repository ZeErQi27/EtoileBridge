import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;

import '../logging/app_action_logger.dart';
import '../models/character_models.dart';
import '../models/difficulty_display.dart';
import '../models/pack_models.dart';
import '../models/single_song_models.dart';
import 'platform_paths.dart';

abstract interface class PlatformWorkerBridge {
  Future<String> smokeTest();
  Future<WorkerActionResult<SingleSongScanResult>> scanSingle({
    required String sourcePath,
    required String sessionPath,
  });
  Future<WorkerActionResult<SingleSongSaveResult>> saveSingle(
    SingleSongSaveRequest request,
  );
  Future<WorkerActionResult<PackScanResult>> scanPackOfficial({
    required String sourcePath,
    required String sessionPath,
  });
  Future<WorkerActionResult<PackScanResult>> scanPackBundle({
    required List<String> sourcePaths,
    required String sessionPath,
  });
  Future<WorkerActionResult<PackScanResult>> scanPackExisting({
    required String basePath,
    required List<String> addSourcePaths,
    required String sessionPath,
  });
  Future<WorkerActionResult<PackSaveResult>> savePack(PackSaveRequest request);
  Future<WorkerActionResult<CharacterScanResult>> scanCharacterImage({
    required String sourcePath,
    required String sessionPath,
  });
  Future<WorkerActionResult<CharacterScanResult>> scanCharacterPackage({
    required String sourcePath,
    required String sessionPath,
  });
  Future<WorkerActionResult<CharacterIconResult>> generateCharacterIcon(
    CharacterIconRequest request,
  );
  Future<WorkerActionResult<CharacterSaveResult>> saveCharacter(
    CharacterSaveRequest request,
  );
}

PlatformWorkerBridge createPlatformWorkerBridge(PlatformPaths paths) {
  if (Platform.isWindows) return WindowsJvmWorkerBridge();
  if (Platform.isAndroid) return AndroidMethodChannelWorkerBridge();
  return MockPlatformWorkerBridge();
}

class WindowsJvmWorkerBridge implements PlatformWorkerBridge {
  static const _mainClass =
      'com.zeerqi27.etoilebridge.electron.worker.WorkerMainKt';
  static const _flutterMainClass =
      'com.zeerqi27.etoilebridge.flutter.worker.FlutterWorkerMainKt';

  @override
  Future<String> smokeTest() async {
    final result = await _runWorker(['smoke-test']);
    if (result.ok) return 'converter-worker smoke ok';
    throw StateError(result.error ?? 'converter-worker smoke failed');
  }

  @override
  Future<WorkerActionResult<SingleSongScanResult>> scanSingle({
    required String sourcePath,
    required String sessionPath,
  }) async {
    final result = await _runFlutterWorker([
      'scan-single',
      '--source',
      sourcePath,
      '--session',
      sessionPath,
    ]);
    var parsed = WorkerActionResult.fromEnvelope(
      result.envelope,
      SingleSongScanResult.fromJson,
    );
    if (_needsArchiveRootRetry(parsed.data)) {
      final retrySource = await _nestedSingleArchiveRoot(parsed.data!);
      if (retrySource != null) {
        AppActionLogger.write(
          phase: 'start',
          page: 'platform',
          id: 'single.fallback.metadata',
          label: 'retry nested archive root: $retrySource',
          before: 'windows-flutter-worker',
        );
        final retrySession = '$sessionPath-normalized';
        final retry = await _runFlutterWorker([
          'scan-single',
          '--source',
          retrySource,
          '--session',
          retrySession,
        ]);
        final retryParsed = WorkerActionResult.fromEnvelope(
          retry.envelope,
          SingleSongScanResult.fromJson,
        );
        if (_singleScanScore(retryParsed.data) >
            _singleScanScore(parsed.data)) {
          parsed = retryParsed.copyWith(
            warnings: [
              ...retryParsed.warnings,
              'Retried scan inside nested archive root: $retrySource',
            ],
            logs: [...retryParsed.logs, ...parsed.logs],
          );
        }
      }
    }
    parsed = await _repairSingleScanWithFallback(parsed);
    return _attachSingleScanDiagnostics(
      parsed,
      envelope: parsed.rawEnvelope.isEmpty
          ? result.envelope
          : parsed.rawEnvelope,
      sourcePath: sourcePath,
      sessionPath: sessionPath,
      action: 'scan-single',
    );
  }

  @override
  Future<WorkerActionResult<SingleSongSaveResult>> saveSingle(
    SingleSongSaveRequest request,
  ) async {
    final tempOutput = _tempOutputFor(request.outputPath);
    final tempFile = File(tempOutput);
    if (await tempFile.exists()) await tempFile.delete();
    final result = await _runFlutterWorker([
      'convert-single',
      '--workspace',
      request.scan.workspacePath,
      '--output',
      tempOutput,
      '--request-json',
      encodeWorkerJson(request.toWorkerRequestJson()),
    ]);
    final parsed = WorkerActionResult.fromEnvelope(
      result.envelope,
      SingleSongSaveResult.fromJson,
    );
    if (!parsed.ok) {
      if (await tempFile.exists()) await tempFile.delete();
      return parsed;
    }
    if (!await tempFile.exists() || await tempFile.length() <= 0) {
      if (await tempFile.exists()) await tempFile.delete();
      return WorkerActionResult(
        ok: false,
        error: 'converter worker did not produce a non-empty arcpkg',
        warnings: parsed.warnings,
        logs: parsed.logs,
      );
    }
    final target = File(request.outputPath);
    if (!await target.parent.exists()) {
      await target.parent.create(recursive: true);
    }
    if (await target.exists()) {
      await target.delete();
    }
    await tempFile.rename(request.outputPath);
    final size = await target.length();
    return WorkerActionResult(
      ok: true,
      data: SingleSongSaveResult(
        outputPath: request.outputPath,
        songId: parsed.data?.songId,
        sizeBytes: size,
      ),
      warnings: parsed.warnings,
      logs: parsed.logs,
    );
  }

  @override
  Future<WorkerActionResult<PackScanResult>> scanPackOfficial({
    required String sourcePath,
    required String sessionPath,
  }) async {
    final result = await _runWorker([
      'scan-official-pack',
      '--source',
      sourcePath,
      '--session',
      sessionPath,
    ]);
    return WorkerActionResult.fromEnvelope(
      result.envelope,
      PackScanResult.fromJson,
    );
  }

  @override
  Future<WorkerActionResult<PackScanResult>> scanPackBundle({
    required List<String> sourcePaths,
    required String sessionPath,
  }) async {
    final result = await _runWorker([
      'scan-arcpkg-bundle',
      '--sources-json',
      jsonEncode(sourcePaths),
      '--session',
      sessionPath,
    ]);
    return WorkerActionResult.fromEnvelope(
      result.envelope,
      PackScanResult.fromJson,
    );
  }

  @override
  Future<WorkerActionResult<PackScanResult>> scanPackExisting({
    required String basePath,
    required List<String> addSourcePaths,
    required String sessionPath,
  }) async {
    final args = [
      'scan-existing-pack',
      '--base',
      basePath,
      '--session',
      sessionPath,
    ];
    if (addSourcePaths.isNotEmpty) {
      args
        ..add('--sources-json')
        ..add(jsonEncode(addSourcePaths));
    }
    final result = await _runWorker(args);
    return WorkerActionResult.fromEnvelope(
      result.envelope,
      PackScanResult.fromJson,
    );
  }

  @override
  Future<WorkerActionResult<PackSaveResult>> savePack(
    PackSaveRequest request,
  ) async {
    final tempOutput = _tempOutputFor(request.outputPath);
    final tempFile = File(tempOutput);
    if (await tempFile.exists()) await tempFile.delete();
    final args = switch (request.scan.mode) {
      PackEditorMode.official => [
        'save-official-pack',
        '--workspace',
        request.scan.workspacePath ?? '',
        '--output',
        tempOutput,
        '--request-json',
        jsonEncode(
          _electronWorkerCompatibleRequest(request.toWorkerRequestJson()),
        ),
      ],
      PackEditorMode.existing => [
        'save-existing-pack',
        '--base',
        request.scan.basePackPath ?? '',
        if (request.scan.addWorkspacePath != null) ...[
          '--add-workspace',
          request.scan.addWorkspacePath!,
        ],
        '--output',
        tempOutput,
        '--request-json',
        jsonEncode(
          _electronWorkerCompatibleRequest(request.toWorkerRequestJson()),
        ),
      ],
      PackEditorMode.bundle => [
        'save-arcpkg-bundle',
        '--workspace',
        request.scan.workspacePath ?? '',
        '--output',
        tempOutput,
        '--request-json',
        jsonEncode(
          _electronWorkerCompatibleRequest(request.toWorkerRequestJson()),
        ),
      ],
    };
    final result = await _runWorker(args);
    final parsed = WorkerActionResult.fromEnvelope(
      result.envelope,
      PackSaveResult.fromJson,
    );
    if (!parsed.ok) {
      if (await tempFile.exists()) await tempFile.delete();
      return parsed;
    }
    if (!await tempFile.exists() || await tempFile.length() <= 0) {
      if (await tempFile.exists()) await tempFile.delete();
      return WorkerActionResult(
        ok: false,
        error: 'converter worker did not produce a non-empty pack arcpkg',
        warnings: parsed.warnings,
        logs: parsed.logs,
      );
    }
    final target = File(request.outputPath);
    if (!await target.parent.exists()) {
      await target.parent.create(recursive: true);
    }
    if (await target.exists()) await target.delete();
    await tempFile.rename(request.outputPath);
    final size = await target.length();
    return WorkerActionResult(
      ok: true,
      data: PackSaveResult(
        outputPath: request.outputPath,
        sizeBytes: size,
        convertedCount: parsed.data?.convertedCount,
        skippedCount: parsed.data?.skippedCount,
      ),
      warnings: parsed.warnings,
      logs: parsed.logs,
    );
  }

  @override
  Future<WorkerActionResult<CharacterScanResult>> scanCharacterImage({
    required String sourcePath,
    required String sessionPath,
  }) async {
    final result = await _runWorker([
      'scan-character-image',
      '--source',
      sourcePath,
      '--session',
      sessionPath,
    ]);
    return WorkerActionResult.fromEnvelope(
      result.envelope,
      CharacterScanResult.fromJson,
    );
  }

  @override
  Future<WorkerActionResult<CharacterScanResult>> scanCharacterPackage({
    required String sourcePath,
    required String sessionPath,
  }) async {
    final result = await _runWorker([
      'scan-character-arcpkg',
      '--source',
      sourcePath,
      '--session',
      sessionPath,
    ]);
    return WorkerActionResult.fromEnvelope(
      result.envelope,
      CharacterScanResult.fromJson,
    );
  }

  @override
  Future<WorkerActionResult<CharacterIconResult>> generateCharacterIcon(
    CharacterIconRequest request,
  ) async {
    final result = await _runWorker([
      'generate-character-icon',
      '--request-json',
      encodeWorkerJson(request.toJson()),
    ]);
    return WorkerActionResult.fromEnvelope(
      result.envelope,
      CharacterIconResult.fromJson,
    );
  }

  @override
  Future<WorkerActionResult<CharacterSaveResult>> saveCharacter(
    CharacterSaveRequest request,
  ) async {
    final tempOutput = _tempOutputFor(request.outputPath);
    final tempFile = File(tempOutput);
    if (await tempFile.exists()) await tempFile.delete();
    final result = await _runWorker([
      'save-character-package',
      '--output',
      tempOutput,
      '--request-json',
      encodeWorkerJson(request.toWorkerRequestJson()),
    ]);
    final parsed = WorkerActionResult.fromEnvelope(
      result.envelope,
      CharacterSaveResult.fromJson,
    );
    if (!parsed.ok) {
      if (await tempFile.exists()) await tempFile.delete();
      return parsed;
    }
    if (!await tempFile.exists() || await tempFile.length() <= 0) {
      if (await tempFile.exists()) await tempFile.delete();
      return WorkerActionResult(
        ok: false,
        error: 'converter worker did not produce a non-empty character arcpkg',
        warnings: parsed.warnings,
        logs: parsed.logs,
      );
    }
    final target = File(request.outputPath);
    if (!await target.parent.exists()) {
      await target.parent.create(recursive: true);
    }
    if (await target.exists()) await target.delete();
    await tempFile.rename(request.outputPath);
    final size = await target.length();
    final data = parsed.data;
    return WorkerActionResult(
      ok: true,
      data: data == null
          ? null
          : CharacterSaveResult(
              outputPath: request.outputPath,
              identifier: data.identifier,
              directory: data.directory,
              sizeBytes: size,
              validation: data.validation,
            ),
      warnings: parsed.warnings,
      logs: parsed.logs,
    );
  }

  Future<_RawWorkerResult> _runWorker(List<String> args) async {
    final resolved = await _resolveWorker();
    return _runResolvedWorker(resolved, _mainClass, args);
  }

  Future<_RawWorkerResult> _runFlutterWorker(List<String> args) async {
    final resolved = await _resolveFlutterWorker();
    return _runResolvedWorker(resolved, _flutterMainClass, args);
  }

  Future<_RawWorkerResult> _runResolvedWorker(
    _ResolvedWorker resolved,
    String mainClass,
    List<String> args,
  ) async {
    final materialized = await _materializeJsonArgs([
      '-Dfile.encoding=UTF-8',
      '-Dsun.stdout.encoding=UTF-8',
      '-Dsun.stderr.encoding=UTF-8',
      '-cp',
      resolved.classpath,
      mainClass,
      ...args,
    ]);
    try {
      final process = await Process.start(
        resolved.javaPath,
        materialized.args,
        workingDirectory: resolved.workingDirectory,
        runInShell: false,
        mode: ProcessStartMode.normal,
      );
      final stdoutFuture = process.stdout.transform(utf8.decoder).join();
      final stderrFuture = process.stderr.transform(utf8.decoder).join();
      final exitCode = await process.exitCode.timeout(
        const Duration(minutes: 3),
      );
      final stdout = await stdoutFuture;
      final stderr = await stderrFuture;
      if (exitCode != 0) {
        return _RawWorkerResult.error(
          'converter worker failed with exit code $exitCode',
          logs: [stderr.trim()].where((line) => line.isNotEmpty).toList(),
        );
      }
      final payload = _jsonPayload(stdout);
      try {
        final envelope = jsonDecode(payload) as Map<String, Object?>;
        final logs = [
          ..._stringList(envelope['logs']),
          if (stderr.trim().isNotEmpty) stderr.trim(),
        ];
        return _RawWorkerResult({...envelope, 'logs': logs});
      } catch (error) {
        return _RawWorkerResult.error(
          'converter worker returned invalid JSON: $error',
          logs: [stdout, stderr].where((line) => line.isNotEmpty).toList(),
        );
      }
    } on TimeoutException {
      return _RawWorkerResult.error('converter worker timed out');
    } finally {
      await materialized.cleanup();
    }
  }

  Future<_ResolvedWorker> _resolveFlutterWorker() async {
    final javaName = Platform.isWindows ? 'java.exe' : 'java';
    final javaCandidates = [
      _envJava('ETOILEBRIDGE_JAVA_HOME', javaName),
      _portableChildPath('runtime/bin/$javaName'),
      _envJava('JAVA_HOME', javaName),
      _findOnPath(javaName),
      _siblingPath('EtoileBridgeElectron', 'build/runtime/bin/$javaName'),
      _siblingPath(
        'EtoileBridgeElectron',
        'dist/win-unpacked/resources/runtime/bin/$javaName',
      ),
    ].whereType<String>();
    final javaPath = javaCandidates.firstWhere(
      (candidate) => File(candidate).existsSync(),
      orElse: () => '',
    );
    if (javaPath.isEmpty) {
      throw StateError(
        'Java runtime not found. Set ETOILEBRIDGE_JAVA_HOME or JAVA_HOME.',
      );
    }

    final libCandidates = [
      Platform.environment['ETOILEBRIDGE_FLUTTER_WORKER_LIB_DIR'],
      _portableChildPath('native/flutter-converter-worker/lib'),
      _projectChildPath(
        'native/converter-worker/converter-worker/build/install/converter-worker/lib',
      ),
    ].whereType<String>();
    final libDir = libCandidates.firstWhere(
      (candidate) =>
          Directory(candidate).existsSync() &&
          Directory(candidate).listSync().whereType<File>().any(
            (file) => file.path.endsWith('.jar'),
          ),
      orElse: () => '',
    );
    if (libDir.isEmpty) {
      throw StateError(
        'Flutter-owned converter worker classpath not found. Run '
        'E:\\ArcpkgAPP\\EtoileBridgeElectron\\gradlew.bat -p '
        'E:\\ArcpkgAPP\\EtoileBridgeFlutter\\native\\converter-worker '
        ':converter-worker:installDist.',
      );
    }
    final jars =
        Directory(libDir)
            .listSync()
            .whereType<File>()
            .where((file) => file.path.endsWith('.jar'))
            .map((file) => file.path)
            .toList()
          ..sort();
    return _ResolvedWorker(
      javaPath: javaPath,
      classpath: jars.join(Platform.isWindows ? ';' : ':'),
      workingDirectory:
          _projectRootCandidate() ??
          _portableRootCandidate() ??
          Directory.current.path,
    );
  }

  Future<_ResolvedWorker> _resolveWorker() async {
    final javaName = Platform.isWindows ? 'java.exe' : 'java';
    final javaCandidates = [
      _envJava('ETOILEBRIDGE_JAVA_HOME', javaName),
      _portableChildPath('runtime/bin/$javaName'),
      _envJava('JAVA_HOME', javaName),
      _findOnPath(javaName),
      _siblingPath('EtoileBridgeElectron', 'build/runtime/bin/$javaName'),
      _siblingPath(
        'EtoileBridgeElectron',
        'dist/win-unpacked/resources/runtime/bin/$javaName',
      ),
    ].whereType<String>();
    final javaPath = javaCandidates.firstWhere(
      (candidate) => File(candidate).existsSync(),
      orElse: () => '',
    );
    if (javaPath.isEmpty) {
      throw StateError(
        'Java runtime not found. Set ETOILEBRIDGE_JAVA_HOME or JAVA_HOME.',
      );
    }

    final libCandidates = [
      Platform.environment['ETOILEBRIDGE_WORKER_LIB_DIR'],
      _portableChildPath('native/converter-worker/lib'),
      _siblingPath(
        'EtoileBridgeElectron',
        'converter-worker/build/install/converter-worker/lib',
      ),
      _siblingPath(
        'EtoileBridgeElectron',
        'dist/win-unpacked/resources/converter-worker/lib',
      ),
    ].whereType<String>();
    final libDir = libCandidates.firstWhere(
      (candidate) =>
          Directory(candidate).existsSync() &&
          Directory(candidate).listSync().whereType<File>().any(
            (file) => file.path.endsWith('.jar'),
          ),
      orElse: () => '',
    );
    if (libDir.isEmpty) {
      throw StateError(
        'converter worker classpath not found. Build Electron worker first.',
      );
    }
    final jars =
        Directory(libDir)
            .listSync()
            .whereType<File>()
            .where((file) => file.path.endsWith('.jar'))
            .map((file) => file.path)
            .toList()
          ..sort();
    return _ResolvedWorker(
      javaPath: javaPath,
      classpath: jars.join(Platform.isWindows ? ';' : ':'),
      workingDirectory:
          _projectRootCandidate() ??
          _portableRootCandidate() ??
          Directory.current.path,
    );
  }

  Future<_MaterializedArgs> _materializeJsonArgs(List<String> args) async {
    final jsonFlags = {'--request-json', '--sources-json'};
    Directory? tempDir;
    final nextArgs = [...args];
    for (var index = 0; index < nextArgs.length - 1; index++) {
      final flag = nextArgs[index];
      if (!jsonFlags.contains(flag)) continue;
      tempDir ??= await Directory.systemTemp.createTemp(
        'etoilebridge-flutter-worker-args-',
      );
      final file = File(
        p.join(tempDir.path, '${flag.substring(2)}-$index.json'),
      );
      await file.writeAsString(nextArgs[index + 1], encoding: utf8);
      nextArgs[index] = '$flag-file';
      nextArgs[index + 1] = file.path;
    }
    return _MaterializedArgs(nextArgs, () async {
      if (tempDir != null && await tempDir.exists()) {
        await tempDir.delete(recursive: true);
      }
    });
  }

  String _tempOutputFor(String outputPath) {
    final dir = p.dirname(outputPath);
    final base = p.basename(outputPath);
    return p.join(
      dir,
      '$base.tmp-${DateTime.now().microsecondsSinceEpoch}.arcpkg',
    );
  }

  Map<String, Object?> _electronWorkerCompatibleRequest(
    Map<String, Object?> request,
  ) {
    final next = Map<String, Object?>.from(request);
    final appearance = next['appearance'];
    if (appearance is Map) {
      final safeAppearance = Map<String, Object?>.from(appearance);
      // Electron's current worker JSON schema does not expose Side/Note and
      // rejects unknown keys. Keep Windows saves compatible until the external
      // worker is upgraded.
      safeAppearance.remove('side');
      safeAppearance.remove('note');
      next['appearance'] = safeAppearance;
    }
    return next;
  }

  String? _envJava(String key, String javaName) {
    final home = Platform.environment[key];
    if (home == null || home.isEmpty) return null;
    return p.join(home, 'bin', javaName);
  }

  String? _findOnPath(String executable) {
    final pathValue = Platform.environment['PATH'] ?? '';
    for (final entry in pathValue.split(Platform.isWindows ? ';' : ':')) {
      if (entry.isEmpty) continue;
      final candidate = p.join(entry, executable);
      if (File(candidate).existsSync()) return candidate;
    }
    return null;
  }

  String? _siblingPath(String sibling, String child) {
    final root = _projectRootCandidate();
    if (root == null) return null;
    return p.normalize(
      p.joinAll([p.dirname(root), sibling, ...child.split('/')]),
    );
  }

  String? _projectChildPath(String child) {
    final root = _projectRootCandidate();
    if (root == null) return null;
    return p.normalize(p.joinAll([root, ...child.split('/')]));
  }

  String? _portableChildPath(String child) {
    final root = _portableRootCandidate();
    if (root == null) return null;
    return p.normalize(p.joinAll([root, ...child.split('/')]));
  }

  String? _portableRootCandidate() {
    if (!Platform.isWindows) return null;
    final executable = Platform.resolvedExecutable;
    if (executable.isEmpty) return null;
    final dir = p.dirname(executable);
    final exeName = p.basename(executable).toLowerCase();
    if (exeName == 'etoile_bridge.exe' ||
        File(p.join(dir, 'etoile_bridge.exe')).existsSync()) {
      return dir;
    }
    return null;
  }

  String? _projectRootCandidate() {
    final starts = [
      Directory.current.path,
      p.dirname(Platform.resolvedExecutable),
    ];
    for (final start in starts) {
      var dir = Directory(start);
      for (var i = 0; i < 8; i++) {
        if (File(p.join(dir.path, 'pubspec.yaml')).existsSync()) {
          return dir.path;
        }
        final parent = dir.parent;
        if (parent.path == dir.path) break;
        dir = parent;
      }
    }
    return null;
  }
}

bool _needsArchiveRootRetry(SingleSongScanResult? scan) {
  if (scan == null || !scan.workspacePath.isNotEmpty) return false;
  final hasMetadata = !_isBlank(scan.songId) || !_isBlank(scan.title);
  return !hasMetadata && scan.charts.isEmpty && scan.affFiles.isEmpty;
}

int _singleScanScore(SingleSongScanResult? scan) {
  if (scan == null) return 0;
  var score = 0;
  if (!_isBlank(scan.songId)) score += 3;
  if (!_isBlank(scan.title)) score += 3;
  if (!_isBlank(scan.artist)) score += 2;
  score += scan.charts.length * 2;
  score += scan.affFiles.length;
  if (scan.audio != null) score++;
  if (scan.jacket != null) score++;
  if (scan.background != null) score++;
  return score;
}

Future<String?> _nestedSingleArchiveRoot(SingleSongScanResult scan) async {
  final workspace = Directory(scan.workspacePath);
  if (!await workspace.exists()) return null;
  final children = await workspace
      .list()
      .where((entity) => entity is Directory)
      .cast<Directory>()
      .toList();
  if (children.length != 1) return null;
  final child = children.single;
  if (await _containsSingleSongClues(child)) return child.path;
  return null;
}

Future<bool> _containsSingleSongClues(Directory root) async {
  await for (final entity in root.list(recursive: true, followLinks: false)) {
    if (entity is! File) continue;
    final name = p.basename(entity.path).toLowerCase();
    if (name.endsWith('.aff') ||
        name == 'songlist' ||
        name == 'slst' ||
        name == 'project.arcproj') {
      return true;
    }
  }
  return false;
}

Future<WorkerActionResult<SingleSongScanResult>> _repairSingleScanWithFallback(
  WorkerActionResult<SingleSongScanResult> parsed,
) async {
  final scan = parsed.data;
  if (scan == null) return parsed;
  final warnings = [...parsed.warnings, ...scan.warnings];
  final logs = [...parsed.logs];
  var changed = false;
  var repaired = scan;

  final song = await _readSonglistSongObject(scan);
  if (song != null) {
    final title =
        _localizedString(song['title_localized']) ??
        _stringValue(song['title']);
    final artist =
        _stringValue(song['artist']) ?? _stringValue(song['composer']);
    final songId = _stringValue(song['id']);
    final bpmText = _stringValue(song['bpm']);
    final bpmBase = _doubleValue(song['bpm_base']);
    final version = _stringValue(song['version']);
    final charts = _mergeChartsFromSonglist(scan, song);
    final bgName = _stringValue(song['bg']);
    final audio =
        scan.audio ?? await _findResource(scan.workspacePath, _audioMatcher);
    final jacket =
        scan.jacket ?? await _findResource(scan.workspacePath, _jacketMatcher);
    final background =
        scan.background ??
        (bgName == null
            ? null
            : await _findResource(
                scan.workspacePath,
                (file) =>
                    p.basenameWithoutExtension(file.path).toLowerCase() ==
                    bgName.toLowerCase(),
              ));
    repaired = scan.copyWith(
      songId: _firstNonBlank(scan.songId, songId),
      title: _firstNonBlank(scan.title, title),
      artist: _firstNonBlank(scan.artist, artist),
      bpmText: _firstNonBlank(scan.bpmText, bpmText),
      bpmBase: scan.bpmBase ?? bpmBase,
      version: _firstNonBlank(scan.version, version),
      charts: charts,
      difficulty: _firstNonBlank(scan.difficulty, _difficultySummary(charts)),
      audio: audio,
      jacket: jacket,
      background: background,
      warnings: warnings,
      logs: scan.logs,
      extra: {...scan.extra, 'metadataFallback': 'songlist'},
    );
    changed = _singleScanScore(repaired) > _singleScanScore(scan);
  }

  if (changed) {
    final message = 'Recovered missing single-song metadata with fallback.';
    final nextWarnings = {...warnings, message}.toList();
    AppActionLogger.write(
      phase: 'end',
      page: 'platform',
      id: 'single.fallback.metadata',
      label: repaired.songId ?? repaired.sourcePath,
      before: 'worker-result',
      after: message,
    );
    return parsed.copyWith(
      data: repaired.copyWith(warnings: nextWarnings),
      warnings: nextWarnings,
      logs: [...logs, message],
    );
  }
  return parsed;
}

Future<Map<String, Object?>?> _readSonglistSongObject(
  SingleSongScanResult scan,
) async {
  final file = await _songlistFile(scan);
  if (file == null || !await file.exists()) return null;
  final text = await file.readAsString(encoding: utf8);
  final decoded = _decodeSonglistJson(text);
  final songs = _songObjects(decoded);
  if (songs.isEmpty) return null;
  final wanted = <String>{
    if (!_isBlank(scan.songId)) scan.songId!.toLowerCase(),
    p.basenameWithoutExtension(scan.sourcePath).toLowerCase(),
    p.basename(scan.sourcePath).toLowerCase(),
  };
  return songs.firstWhere((song) {
    final id = _stringValue(song['id'])?.toLowerCase();
    return id != null && wanted.contains(id);
  }, orElse: () => songs.first);
}

Future<File?> _songlistFile(SingleSongScanResult scan) async {
  final path = scan.songlist?.path;
  if (path != null && await File(path).exists()) return File(path);
  final workspace = Directory(scan.workspacePath);
  if (!await workspace.exists()) return null;
  await for (final entity in workspace.list(
    recursive: true,
    followLinks: false,
  )) {
    if (entity is! File) continue;
    final name = p.basename(entity.path).toLowerCase();
    if (name == 'songlist' || name == 'slst') return entity;
  }
  return null;
}

Object? _decodeSonglistJson(String text) {
  try {
    return jsonDecode(text);
  } catch (_) {
    final objectText = _firstBalancedJsonObject(text);
    if (objectText == null) return null;
    try {
      return jsonDecode(objectText);
    } catch (_) {
      return null;
    }
  }
}

String? _firstBalancedJsonObject(String text) {
  final start = text.indexOf('{');
  if (start < 0) return null;
  var depth = 0;
  var inString = false;
  var escaped = false;
  for (var index = start; index < text.length; index++) {
    final code = text.codeUnitAt(index);
    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (code == 0x5C) {
        escaped = true;
      } else if (code == 0x22) {
        inString = false;
      }
      continue;
    }
    if (code == 0x22) {
      inString = true;
    } else if (code == 0x7B) {
      depth++;
    } else if (code == 0x7D) {
      depth--;
      if (depth == 0) return text.substring(start, index + 1);
    }
  }
  return null;
}

List<Map<String, Object?>> _songObjects(Object? decoded) {
  if (decoded is Map) {
    final map = decoded.cast<String, Object?>();
    final songs = map['songs'];
    if (songs is List) {
      return songs
          .whereType<Map>()
          .map((item) => item.cast<String, Object?>())
          .toList();
    }
    if (map.containsKey('id')) return [map];
  }
  if (decoded is List) {
    return decoded
        .whereType<Map>()
        .map((item) => item.cast<String, Object?>())
        .toList();
  }
  return const [];
}

List<ChartMetadata> _mergeChartsFromSonglist(
  SingleSongScanResult scan,
  Map<String, Object?> song,
) {
  final existing = {for (final chart in scan.charts) chart.ratingClass: chart};
  final difficulties = song['difficulties'];
  final next = <ChartMetadata>[];
  if (difficulties is List) {
    for (final value in difficulties.whereType<Map>()) {
      final diff = value.cast<String, Object?>();
      final ratingClass = _intValue(diff['ratingClass']);
      if (ratingClass == null) continue;
      final current = existing.remove(ratingClass);
      final rating = _intValue(diff['rating']);
      final ratingPlus = diff['ratingPlus'] is bool
          ? diff['ratingPlus'] as bool
          : null;
      final aff = scan.affFiles
          .where((item) => item.ratingClass == ratingClass)
          .firstOrNull;
      if (current == null && aff == null) continue;
      next.add(
        (current ?? ChartMetadata(ratingClass: ratingClass)).copyWith(
          difficulty: _firstNonBlank(
            current?.difficulty,
            _difficultyLabel(ratingClass, rating, ratingPlus),
          ),
          chartConstant:
              current?.chartConstant ??
              DifficultyDisplay.resolve(
                ratingClass: ratingClass,
                rating: rating,
                ratingPlus: ratingPlus,
              ).chartConstant,
          rating: current?.rating ?? rating,
          ratingPlus: current?.ratingPlus ?? ratingPlus,
          charter: _firstNonBlank(
            current?.charter,
            _stringValue(diff['chartDesigner']),
          ),
          illustrator: _firstNonBlank(
            current?.illustrator,
            _stringValue(diff['jacketDesigner']),
          ),
          affPath: _firstNonBlank(current?.affPath, aff?.path),
          affName: _firstNonBlank(current?.affName, aff?.name),
          chartPath: _firstNonBlank(current?.chartPath, aff?.path),
        ),
      );
    }
  }
  next.addAll(existing.values);
  next.sort((a, b) => a.ratingClass.compareTo(b.ratingClass));
  return next.isEmpty ? scan.charts : next;
}

Future<ResourceInfo?> _findResource(
  String workspacePath,
  bool Function(File file) matches,
) async {
  final workspace = Directory(workspacePath);
  if (!await workspace.exists()) return null;
  await for (final entity in workspace.list(
    recursive: true,
    followLinks: false,
  )) {
    if (entity is! File || !matches(entity)) continue;
    return ResourceInfo(
      path: entity.path,
      name: p.basename(entity.path),
      source: 'fallback',
      sizeBytes: await entity.length(),
    );
  }
  return null;
}

bool _audioMatcher(File file) {
  final name = p.basename(file.path).toLowerCase();
  return name.endsWith('.ogg') ||
      name.endsWith('.mp3') ||
      name.endsWith('.wav') ||
      name.endsWith('.flac') ||
      name.endsWith('.m4a');
}

bool _jacketMatcher(File file) {
  final name = p.basename(file.path).toLowerCase();
  if (!(name.endsWith('.jpg') ||
      name.endsWith('.jpeg') ||
      name.endsWith('.png') ||
      name.endsWith('.webp'))) {
    return false;
  }
  return name == 'base.jpg' ||
      name == 'base.png' ||
      name.contains('jacket') ||
      name.contains('1080_base');
}

String? _localizedString(Object? value) {
  if (value is Map) {
    final map = value.cast<String, Object?>();
    return _stringValue(map['en']) ??
        _stringValue(map['ja']) ??
        _stringValue(map['zh-Hans']) ??
        _stringValue(map['zh-Hant']) ??
        map.values.map(_stringValue).whereType<String>().firstOrNull;
  }
  return _stringValue(value);
}

String? _stringValue(Object? value) {
  final text = value?.toString().trim();
  return text == null || text.isEmpty ? null : text;
}

int? _intValue(Object? value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  return int.tryParse(value?.toString() ?? '');
}

double? _doubleValue(Object? value) {
  if (value is double) return value;
  if (value is num) return value.toDouble();
  return double.tryParse(value?.toString() ?? '');
}

String? _firstNonBlank(String? first, String? second) =>
    _isBlank(first) ? second : first;

bool _isBlank(String? value) => value == null || value.trim().isEmpty;

String _difficultyLabel(int ratingClass, int? rating, bool? ratingPlus) {
  return DifficultyDisplay.resolve(
    ratingClass: ratingClass,
    rating: rating,
    ratingPlus: ratingPlus,
  ).name;
}

String? _difficultySummary(List<ChartMetadata> charts) {
  if (charts.isEmpty) return null;
  return charts
      .map((chart) {
        final display = DifficultyDisplay.resolve(
          ratingClass: chart.ratingClass,
          difficulty: chart.difficulty,
          chartConstant: chart.chartConstant,
          rating: chart.rating,
          ratingPlus: chart.ratingPlus,
        );
        final rating = display.isQuestionRating
            ? ' (?)'
            : ' (${display.chartConstant.toStringAsFixed(1)})';
        return '${chart.ratingClass}:${display.name}$rating';
      })
      .join(', ');
}

Future<WorkerActionResult<SingleSongScanResult>> _attachSingleScanDiagnostics(
  WorkerActionResult<SingleSongScanResult> parsed, {
  required Map<String, Object?> envelope,
  required String sourcePath,
  required String sessionPath,
  required String action,
}) async {
  String? rawJsonPath;
  String? diagnosticsPath;
  var diagnostics = <String>[];
  try {
    final debugDir = Directory(
      p.join(Directory.systemTemp.path, 'etoilebridge-flutter-scan-debug'),
    );
    if (!await debugDir.exists()) await debugDir.create(recursive: true);
    final scanId =
        '${p.basename(sessionPath)}-${DateTime.now().millisecondsSinceEpoch}';
    rawJsonPath = p.join(debugDir.path, '$scanId-scan-raw.json');
    diagnosticsPath = p.join(debugDir.path, '$scanId-scan-diagnostics.log');
    await File(
      rawJsonPath,
    ).writeAsString(prettyWorkerJson(envelope), encoding: utf8);
    diagnostics = _buildSingleScanDiagnostics(
      envelope: envelope,
      scan: parsed.data,
      sourcePath: sourcePath,
      action: action,
      rawJsonPath: rawJsonPath,
      diagnosticsPath: diagnosticsPath,
    );
    await File(
      diagnosticsPath,
    ).writeAsString(diagnostics.join('\n'), encoding: utf8);
  } catch (error, stack) {
    diagnostics = [
      ...diagnostics,
      'diagnostics write error: $error',
      stack.toString(),
    ];
  }
  return parsed.copyWith(
    rawJsonPath: rawJsonPath,
    diagnosticsPath: diagnosticsPath,
    diagnostics: diagnostics,
  );
}

List<String> _buildSingleScanDiagnostics({
  required Map<String, Object?> envelope,
  required SingleSongScanResult? scan,
  required String sourcePath,
  required String action,
  required String? rawJsonPath,
  required String? diagnosticsPath,
}) {
  final data = _diagnosticMap(envelope['data']);
  final lines = <String>[
    'scan source path: $sourcePath',
    'worker action: $action',
    'worker ok: ${envelope['ok']}',
    'raw JSON path: ${rawJsonPath ?? '-'}',
    'diagnostics path: ${diagnosticsPath ?? '-'}',
    '',
    '[basic]',
  ];
  void field(
    String name,
    Object? modelValue,
    String uiSurface, {
    String? rawKey,
  }) {
    final key = rawKey ?? name;
    final workerHas = _diagnosticHasValue(data, key);
    final modelHas = _diagnosticHasModelValue(modelValue);
    final reason = workerHas
        ? (modelHas ? 'OK' : 'DART_MODEL_MISSING')
        : 'WORKER_NOT_RETURNED';
    lines.add(
      '$reason $name: worker=${_diagnosticPreview(data[key])}; '
      'model=${_diagnosticPreview(modelValue)}; ui=$uiSurface',
    );
  }

  field('songId', scan?.songId, 'overview, editor');
  field('title', scan?.title, 'overview, editor');
  field('composer', scan?.artist, 'overview, editor', rawKey: 'artist');
  field('alias', scan?.alias, 'editor when source supports alias');
  field('baseBpm', scan?.bpmBase, 'overview, editor', rawKey: 'bpmBase');
  field('bpmText', scan?.bpmText, 'overview, editor');
  field('version', scan?.version, 'diagnostics');
  field(
    'source type',
    scan == null ? null : sourceKindLabel(scan.sourceKind),
    'input, diagnostics',
    rawKey: 'sourceKind',
  );
  field(
    'package directory',
    scan?.packageDirectory,
    'diagnostics',
    rawKey: 'packageDirectory',
  );
  field(
    'project file path',
    scan?.projectFilePath,
    'diagnostics',
    rawKey: 'projectFilePath',
  );
  field('songlist path', scan?.songlist?.path, 'resources', rawKey: 'songlist');

  lines.addAll(['', '[resources]']);
  _resourceDiagnostics(lines, 'audio', data['audio'], scan?.audio);
  _resourceDiagnostics(lines, 'jacket', data['jacket'], scan?.jacket);
  _resourceDiagnostics(
    lines,
    'background',
    data['background'],
    scan?.background,
  );
  _resourceDiagnostics(lines, 'songlist', data['songlist'], scan?.songlist);
  _resourceDiagnostics(lines, 'packlist', data['packlist'], scan?.packlist);
  _resourceDiagnostics(lines, 'project', data['project'], scan?.project);

  final rawCharts = _diagnosticList(data['charts']);
  lines.addAll(['', '[charts]', 'chart count: ${scan?.charts.length ?? 0}']);
  for (var index = 0; index < (scan?.charts.length ?? 0); index++) {
    final chart = scan!.charts[index];
    final raw = index < rawCharts.length
        ? _diagnosticMap(rawCharts[index])
        : const <String, Object?>{};
    lines.add('chart[$index]');
    lines.add(
      '  ratingClass: worker=${raw['ratingClass']}; model=${chart.ratingClass}; ui=chart editor',
    );
    lines.add(
      '  difficulty: worker=${raw['difficulty']}; model=${chart.difficulty}; ui=chart editor',
    );
    lines.add(
      '  difficulty number/rating: worker=${raw['rating']}; model=${chart.rating}; ui=chart editor',
    );
    lines.add(
      '  ratingPlus: worker=${raw['ratingPlus']}; model=${chart.ratingPlus}; ui=chart editor',
    );
    lines.add(
      '  chartConstant: worker=${raw['chartConstant']}; model=${chart.chartConstant}; ui=chart editor',
    );
    lines.add(
      '  charter: worker=${_diagnosticPreview(raw['charter'])}; model=${_diagnosticPreview(chart.charter)}; ui=chart editor',
    );
    lines.add(
      '  illustrator: worker=${_diagnosticPreview(raw['illustrator'])}; model=${_diagnosticPreview(chart.illustrator)}; ui=chart editor',
    );
    lines.add(
      '  alias: worker=${_diagnosticPreview(raw['alias'])}; model=${_diagnosticPreview(chart.alias)}; ui=conditional chart editor',
    );
    lines.add(
      '  chartPath: worker=${_diagnosticPreview(raw['chartPath'] ?? raw['affPath'])}; model=${_diagnosticPreview(chart.chartPath)}; ui=chart editor',
    );
  }

  lines.addAll(['', '[aff]']);
  final rawAff = _diagnosticList(data['affFiles']);
  lines.add('aff file count: ${scan?.affFiles.length ?? 0}');
  for (var index = 0; index < (scan?.affFiles.length ?? 0); index++) {
    final aff = scan!.affFiles[index];
    final raw = index < rawAff.length
        ? _diagnosticMap(rawAff[index])
        : const <String, Object?>{};
    lines.add(
      'aff[$index]: name=${aff.name}; ratingClass=${aff.ratingClass}; '
      'adopted=${aff.adopted}; path=${aff.path}; rawPath=${raw['path']}',
    );
    if (aff.warning != null) lines.add('  warning=${aff.warning}');
  }
  lines.add(
    'missing aff warnings: ${scan?.warnings.where((line) => line.toLowerCase().contains('aff')).join(' | ') ?? '-'}',
  );

  lines.addAll(['', '[output/save]']);
  lines.add('output filename: computed by UI/save dialog from levelId');
  lines.add('output identifier: computed as publisherId.levelId in UI');
  lines.add('project.arcproj output path: generated by worker during save');
  lines.add('arcpkg target path: selected at save time');

  lines.addAll(['', '[warnings]', ...?scan?.warnings]);
  if (parsedError(envelope) != null) {
    lines.addAll(['', '[worker error]', parsedError(envelope)!]);
  }
  return lines;
}

void _resourceDiagnostics(
  List<String> lines,
  String name,
  Object? rawValue,
  ResourceInfo? resource,
) {
  final raw = _diagnosticMap(rawValue);
  if (rawValue == null && resource == null) {
    lines.add('WORKER_NOT_RETURNED $name');
    return;
  }
  lines.add(
    '$name: path=${_diagnosticPreview(resource?.path)}; '
    'name=${_diagnosticPreview(resource?.name)}; '
    'size=${resource?.sizeBytes ?? '-'}; '
    'dimensions=${resource?.width ?? '-'}x${resource?.height ?? '-'}; '
    'workerKeys=${raw.keys.join(',')}',
  );
}

String? parsedError(Map<String, Object?> envelope) =>
    envelope['error'] as String?;

Map<String, Object?> _diagnosticMap(Object? value) {
  if (value is Map<String, Object?>) return value;
  if (value is Map) return value.cast<String, Object?>();
  return const {};
}

List<Object?> _diagnosticList(Object? value) =>
    value is List ? value.cast<Object?>() : const [];

bool _diagnosticHasValue(Map<String, Object?> data, String key) {
  if (!data.containsKey(key)) return false;
  return _diagnosticHasModelValue(data[key]);
}

bool _diagnosticHasModelValue(Object? value) {
  if (value == null) return false;
  if (value is String) return value.trim().isNotEmpty;
  if (value is Iterable) return value.isNotEmpty;
  if (value is Map) return value.isNotEmpty;
  return true;
}

String _diagnosticPreview(Object? value) {
  if (value == null) return '-';
  final text = value.toString();
  return text.length > 160 ? '${text.substring(0, 160)}...' : text;
}

class AndroidMethodChannelWorkerBridge implements PlatformWorkerBridge {
  static const _channel = MethodChannel('com.zeerqi27.etoile_bridge/converter');

  @override
  Future<String> smokeTest() async {
    final result = await _channel.invokeMethod<String>('smokeTest');
    return result ?? 'android converter channel ready';
  }

  @override
  Future<WorkerActionResult<SingleSongScanResult>> scanSingle({
    required String sourcePath,
    required String sessionPath,
  }) async {
    AppActionLogger.write(
      phase: 'start',
      page: 'platform',
      id: 'android.scanSingle.start',
      label: sourcePath,
      before: 'platform',
    );
    try {
      final result = await _channel.invokeMapMethod<String, Object?>(
        'scanSingle',
        {'source': sourcePath, 'session': sessionPath},
      );
      if (result == null) {
        AppActionLogger.write(
          phase: 'error',
          page: 'platform',
          id: 'android.scanSingle.error',
          label: 'empty result',
          before: 'platform',
        );
        return const WorkerActionResult(
          ok: false,
          error: 'Android scan returned no result.',
        );
      }
      var parsed = WorkerActionResult.fromEnvelope(
        result,
        SingleSongScanResult.fromJson,
      );
      parsed = await _repairSingleScanWithFallback(parsed);
      AppActionLogger.write(
        phase: parsed.ok ? 'end' : 'error',
        page: 'platform',
        id: parsed.ok
            ? 'android.scanSingle.success'
            : 'android.scanSingle.error',
        label: parsed.data?.songId ?? parsed.error ?? sourcePath,
        before: 'platform',
      );
      return _attachSingleScanDiagnostics(
        parsed,
        envelope: result,
        sourcePath: sourcePath,
        sessionPath: sessionPath,
        action: 'android.scanSingle',
      );
    } on MissingPluginException {
      AppActionLogger.write(
        phase: 'error',
        page: 'platform',
        id: 'android.scanSingle.error',
        label: 'missing plugin',
        before: 'platform',
      );
      return const WorkerActionResult(
        ok: false,
        error: 'Android converter channel is not implemented yet.',
      );
    } catch (error) {
      AppActionLogger.write(
        phase: 'error',
        page: 'platform',
        id: 'android.scanSingle.error',
        label: error.toString(),
        before: 'platform',
      );
      rethrow;
    }
  }

  @override
  Future<WorkerActionResult<SingleSongSaveResult>> saveSingle(
    SingleSongSaveRequest request,
  ) async {
    AppActionLogger.write(
      phase: 'start',
      page: 'platform',
      id: 'android.saveSingle.start',
      label: request.outputPath,
      before: 'platform',
    );
    try {
      final result = await _channel
          .invokeMapMethod<String, Object?>('saveSingle', {
            'workspace': request.scan.workspacePath,
            'output': request.outputPath,
            'request': request.toWorkerRequestJson(),
          });
      if (result == null) {
        AppActionLogger.write(
          phase: 'error',
          page: 'platform',
          id: 'android.saveSingle.error',
          label: 'empty result',
          before: 'platform',
        );
        return const WorkerActionResult(
          ok: false,
          error: 'Android save returned no result.',
        );
      }
      final parsed = WorkerActionResult.fromEnvelope(
        result,
        SingleSongSaveResult.fromJson,
      );
      AppActionLogger.write(
        phase: parsed.ok ? 'end' : 'error',
        page: 'platform',
        id: parsed.ok
            ? 'android.saveSingle.success'
            : 'android.saveSingle.error',
        label: parsed.data?.outputPath ?? parsed.error ?? request.outputPath,
        before: 'platform',
      );
      return parsed;
    } on MissingPluginException {
      AppActionLogger.write(
        phase: 'error',
        page: 'platform',
        id: 'android.saveSingle.error',
        label: 'missing plugin',
        before: 'platform',
      );
      return const WorkerActionResult(
        ok: false,
        error: 'Android converter channel is not implemented yet.',
      );
    } catch (error) {
      AppActionLogger.write(
        phase: 'error',
        page: 'platform',
        id: 'android.saveSingle.error',
        label: error.toString(),
        before: 'platform',
      );
      rethrow;
    }
  }

  @override
  Future<WorkerActionResult<PackScanResult>> scanPackOfficial({
    required String sourcePath,
    required String sessionPath,
  }) async {
    AppActionLogger.write(
      phase: 'start',
      page: 'platform',
      id: 'android.scanPackOfficial.start',
      label: sourcePath,
      before: 'platform',
    );
    try {
      final result = await _channel.invokeMapMethod<String, Object?>(
        'scanPackOfficial',
        {'source': sourcePath, 'session': sessionPath},
      );
      return _androidPackScanResult(
        result,
        successId: 'android.scanPackOfficial.success',
        errorId: 'android.scanPackOfficial.error',
        fallbackLabel: sourcePath,
      );
    } on MissingPluginException {
      return const WorkerActionResult(
        ok: false,
        error: 'Android pack converter channel is not available.',
      );
    }
  }

  @override
  Future<WorkerActionResult<PackScanResult>> scanPackBundle({
    required List<String> sourcePaths,
    required String sessionPath,
  }) async {
    AppActionLogger.write(
      phase: 'start',
      page: 'platform',
      id: 'android.scanPackBundle.start',
      label: '${sourcePaths.length}',
      before: 'platform',
    );
    try {
      final result = await _channel.invokeMapMethod<String, Object?>(
        'scanPackBundle',
        {'sources': sourcePaths, 'session': sessionPath},
      );
      return _androidPackScanResult(
        result,
        successId: 'android.scanPackBundle.success',
        errorId: 'android.scanPackBundle.error',
        fallbackLabel: '${sourcePaths.length} arcpkg',
      );
    } on MissingPluginException {
      return const WorkerActionResult(
        ok: false,
        error: 'Android pack converter channel is not available.',
      );
    }
  }

  @override
  Future<WorkerActionResult<PackScanResult>> scanPackExisting({
    required String basePath,
    required List<String> addSourcePaths,
    required String sessionPath,
  }) async {
    AppActionLogger.write(
      phase: 'start',
      page: 'platform',
      id: 'android.scanPackExisting.start',
      label: basePath,
      before: 'platform',
    );
    try {
      final result = await _channel.invokeMapMethod<String, Object?>(
        'scanPackExisting',
        {
          'base': basePath,
          'addSources': addSourcePaths,
          'session': sessionPath,
        },
      );
      return _androidPackScanResult(
        result,
        successId: 'android.scanPackExisting.success',
        errorId: 'android.scanPackExisting.error',
        fallbackLabel: basePath,
      );
    } on MissingPluginException {
      return const WorkerActionResult(
        ok: false,
        error: 'Android pack converter channel is not available.',
      );
    }
  }

  @override
  Future<WorkerActionResult<PackSaveResult>> savePack(
    PackSaveRequest request,
  ) async {
    AppActionLogger.write(
      phase: 'start',
      page: 'platform',
      id: 'android.savePack.start',
      label: request.outputPath,
      before: 'platform',
    );
    try {
      final result = await _channel
          .invokeMapMethod<String, Object?>('savePack', {
            'mode': request.scan.mode.name,
            'workspace': request.scan.workspacePath,
            'base': request.scan.basePackPath,
            'addWorkspace': request.scan.addWorkspacePath,
            'session': _packSessionFor(request.scan),
            'output': request.outputPath,
            'request': request.toWorkerRequestJson(),
          });
      if (result == null) {
        AppActionLogger.write(
          phase: 'error',
          page: 'platform',
          id: 'android.savePack.error',
          label: 'empty result',
          before: 'platform',
        );
        return const WorkerActionResult(
          ok: false,
          error: 'Android pack save returned no result.',
        );
      }
      final parsed = WorkerActionResult.fromEnvelope(
        result,
        PackSaveResult.fromJson,
      );
      AppActionLogger.write(
        phase: parsed.ok ? 'end' : 'error',
        page: 'platform',
        id: parsed.ok ? 'android.savePack.success' : 'android.savePack.error',
        label: parsed.data?.outputPath ?? parsed.error ?? request.outputPath,
        before: 'platform',
      );
      return parsed;
    } on MissingPluginException {
      return const WorkerActionResult(
        ok: false,
        error: 'Android pack converter channel is not available.',
      );
    }
  }

  @override
  Future<WorkerActionResult<CharacterScanResult>> scanCharacterImage({
    required String sourcePath,
    required String sessionPath,
  }) async {
    AppActionLogger.write(
      phase: 'start',
      page: 'platform',
      id: 'android.scanCharacterImage.start',
      label: sourcePath,
      before: 'platform',
    );
    try {
      final result = await _channel.invokeMapMethod<String, Object?>(
        'scanCharacterImage',
        {'source': sourcePath, 'session': sessionPath},
      );
      return _androidCharacterScanResult(
        result,
        successId: 'android.scanCharacterImage.success',
        errorId: 'android.scanCharacterImage.error',
        fallbackLabel: sourcePath,
      );
    } on MissingPluginException {
      return const WorkerActionResult(
        ok: false,
        error: 'Android character converter channel is not available.',
      );
    }
  }

  @override
  Future<WorkerActionResult<CharacterScanResult>> scanCharacterPackage({
    required String sourcePath,
    required String sessionPath,
  }) async {
    AppActionLogger.write(
      phase: 'start',
      page: 'platform',
      id: 'android.scanCharacterPackage.start',
      label: sourcePath,
      before: 'platform',
    );
    try {
      final result = await _channel.invokeMapMethod<String, Object?>(
        'scanCharacterPackage',
        {'source': sourcePath, 'session': sessionPath},
      );
      return _androidCharacterScanResult(
        result,
        successId: 'android.scanCharacterPackage.success',
        errorId: 'android.scanCharacterPackage.error',
        fallbackLabel: sourcePath,
      );
    } on MissingPluginException {
      return const WorkerActionResult(
        ok: false,
        error: 'Android character converter channel is not available.',
      );
    }
  }

  @override
  Future<WorkerActionResult<CharacterIconResult>> generateCharacterIcon(
    CharacterIconRequest request,
  ) async {
    try {
      final result = await _channel.invokeMapMethod<String, Object?>(
        'generateCharacterIcon',
        request.toJson(),
      );
      if (result == null) {
        return const WorkerActionResult(
          ok: false,
          error: 'Android character icon generation returned no result.',
        );
      }
      return WorkerActionResult.fromEnvelope(
        result,
        CharacterIconResult.fromJson,
      );
    } on MissingPluginException {
      return const WorkerActionResult(
        ok: false,
        error: 'Android character converter channel is not available.',
      );
    }
  }

  @override
  Future<WorkerActionResult<CharacterSaveResult>> saveCharacter(
    CharacterSaveRequest request,
  ) async {
    AppActionLogger.write(
      phase: 'start',
      page: 'platform',
      id: 'android.saveCharacter.start',
      label: request.outputPath,
      before: 'platform',
    );
    try {
      final result = await _channel
          .invokeMapMethod<String, Object?>('saveCharacter', {
            'session': _characterSessionFor(request.scan),
            'output': request.outputPath,
            'request': request.toWorkerRequestJson(),
          });
      if (result == null) {
        AppActionLogger.write(
          phase: 'error',
          page: 'platform',
          id: 'android.saveCharacter.error',
          label: 'empty result',
          before: 'platform',
        );
        return const WorkerActionResult(
          ok: false,
          error: 'Android character save returned no result.',
        );
      }
      final parsed = WorkerActionResult.fromEnvelope(
        result,
        CharacterSaveResult.fromJson,
      );
      AppActionLogger.write(
        phase: parsed.ok ? 'end' : 'error',
        page: 'platform',
        id: parsed.ok
            ? 'android.saveCharacter.success'
            : 'android.saveCharacter.error',
        label: parsed.data?.outputPath ?? parsed.error ?? request.outputPath,
        before: 'platform',
      );
      return parsed;
    } on MissingPluginException {
      return const WorkerActionResult(
        ok: false,
        error: 'Android character converter channel is not available.',
      );
    }
  }

  WorkerActionResult<PackScanResult> _androidPackScanResult(
    Map<String, Object?>? result, {
    required String successId,
    required String errorId,
    required String fallbackLabel,
  }) {
    if (result == null) {
      AppActionLogger.write(
        phase: 'error',
        page: 'platform',
        id: errorId,
        label: 'empty result',
        before: 'platform',
      );
      return const WorkerActionResult(
        ok: false,
        error: 'Android pack scan returned no result.',
      );
    }
    final parsed = WorkerActionResult.fromEnvelope(
      result,
      PackScanResult.fromJson,
    );
    AppActionLogger.write(
      phase: parsed.ok ? 'end' : 'error',
      page: 'platform',
      id: parsed.ok ? successId : errorId,
      label:
          parsed.data?.packId ??
          parsed.data?.packName ??
          parsed.error ??
          fallbackLabel,
      before: 'platform',
    );
    return parsed;
  }

  WorkerActionResult<CharacterScanResult> _androidCharacterScanResult(
    Map<String, Object?>? result, {
    required String successId,
    required String errorId,
    required String fallbackLabel,
  }) {
    if (result == null) {
      AppActionLogger.write(
        phase: 'error',
        page: 'platform',
        id: errorId,
        label: 'empty result',
        before: 'platform',
      );
      return const WorkerActionResult(
        ok: false,
        error: 'Android character scan returned no result.',
      );
    }
    final parsed = WorkerActionResult.fromEnvelope(
      result,
      CharacterScanResult.fromJson,
    );
    AppActionLogger.write(
      phase: parsed.ok ? 'end' : 'error',
      page: 'platform',
      id: parsed.ok ? successId : errorId,
      label:
          parsed.data?.identifier ??
          parsed.data?.defaultName ??
          parsed.error ??
          fallbackLabel,
      before: 'platform',
    );
    return parsed;
  }

  String? _packSessionFor(PackScanResult scan) {
    final path =
        scan.workspacePath ?? scan.addWorkspacePath ?? scan.basePackPath;
    if (path == null || path.isEmpty) return null;
    final normalized = p.normalize(path);
    if (normalized.endsWith('${p.separator}input') ||
        normalized.endsWith('${p.separator}add-input')) {
      return p.dirname(normalized);
    }
    if (p.basename(p.dirname(normalized)) == 'base') {
      return p.dirname(p.dirname(normalized));
    }
    return p.dirname(normalized);
  }

  String? _characterSessionFor(CharacterScanResult scan) {
    final workspace = scan.workspacePath;
    if (workspace.isEmpty) return null;
    final normalized = p.normalize(workspace);
    if (normalized.endsWith('${p.separator}input')) {
      return p.dirname(normalized);
    }
    return p.dirname(normalized);
  }
}

class MockPlatformWorkerBridge implements PlatformWorkerBridge {
  @override
  Future<String> smokeTest() async => 'mock worker bridge ready';

  @override
  Future<WorkerActionResult<SingleSongScanResult>> scanSingle({
    required String sourcePath,
    required String sessionPath,
  }) async {
    return const WorkerActionResult(
      ok: false,
      error: 'mock worker does not scan real inputs',
    );
  }

  @override
  Future<WorkerActionResult<SingleSongSaveResult>> saveSingle(
    SingleSongSaveRequest request,
  ) async {
    return const WorkerActionResult(
      ok: false,
      error: 'mock worker does not save real outputs',
    );
  }

  @override
  Future<WorkerActionResult<PackScanResult>> scanPackOfficial({
    required String sourcePath,
    required String sessionPath,
  }) async {
    return const WorkerActionResult(
      ok: false,
      error: 'mock worker does not scan real packs',
    );
  }

  @override
  Future<WorkerActionResult<PackScanResult>> scanPackBundle({
    required List<String> sourcePaths,
    required String sessionPath,
  }) async {
    return const WorkerActionResult(
      ok: false,
      error: 'mock worker does not scan real packs',
    );
  }

  @override
  Future<WorkerActionResult<PackScanResult>> scanPackExisting({
    required String basePath,
    required List<String> addSourcePaths,
    required String sessionPath,
  }) async {
    return const WorkerActionResult(
      ok: false,
      error: 'mock worker does not scan real packs',
    );
  }

  @override
  Future<WorkerActionResult<PackSaveResult>> savePack(
    PackSaveRequest request,
  ) async {
    return const WorkerActionResult(
      ok: false,
      error: 'mock worker does not save real packs',
    );
  }

  @override
  Future<WorkerActionResult<CharacterScanResult>> scanCharacterImage({
    required String sourcePath,
    required String sessionPath,
  }) async {
    return const WorkerActionResult(
      ok: false,
      error: 'mock worker does not scan real characters',
    );
  }

  @override
  Future<WorkerActionResult<CharacterScanResult>> scanCharacterPackage({
    required String sourcePath,
    required String sessionPath,
  }) async {
    return const WorkerActionResult(
      ok: false,
      error: 'mock worker does not scan real characters',
    );
  }

  @override
  Future<WorkerActionResult<CharacterIconResult>> generateCharacterIcon(
    CharacterIconRequest request,
  ) async {
    return const WorkerActionResult(
      ok: false,
      error: 'mock worker does not generate real character icons',
    );
  }

  @override
  Future<WorkerActionResult<CharacterSaveResult>> saveCharacter(
    CharacterSaveRequest request,
  ) async {
    return const WorkerActionResult(
      ok: false,
      error: 'mock worker does not save real characters',
    );
  }
}

class _ResolvedWorker {
  const _ResolvedWorker({
    required this.javaPath,
    required this.classpath,
    required this.workingDirectory,
  });

  final String javaPath;
  final String classpath;
  final String workingDirectory;
}

class _MaterializedArgs {
  const _MaterializedArgs(this.args, this.cleanup);

  final List<String> args;
  final Future<void> Function() cleanup;
}

class _RawWorkerResult {
  const _RawWorkerResult(this.envelope);

  factory _RawWorkerResult.error(String error, {List<String> logs = const []}) {
    return _RawWorkerResult({'ok': false, 'error': error, 'logs': logs});
  }

  final Map<String, Object?> envelope;

  bool get ok => envelope['ok'] == true;
  String? get error => envelope['error'] as String?;
}

String _jsonPayload(String stdout) {
  final trimmed = stdout.trim();
  final start = trimmed.indexOf('{');
  return start >= 0 ? trimmed.substring(start) : trimmed;
}

List<String> _stringList(Object? value) =>
    value is List ? value.whereType<String>().toList() : const [];
