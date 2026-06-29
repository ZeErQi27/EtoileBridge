import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path/path.dart' as p;

import '../core/audio/audio_processing.dart';
import '../core/logging/app_action_logger.dart';
import '../core/models/character_models.dart';
import '../core/models/conversion_options.dart';
import '../core/models/pack_models.dart';
import '../core/models/single_song_models.dart';
import '../core/platform/platform_services.dart';
import '../features/character_editor/character_editor_state.dart';
import '../features/pack_editor/pack_editor_state.dart';
import '../features/single_song/single_song_state.dart';
import 'routes.dart';

class AppState extends ChangeNotifier {
  AppState({required this.platform});

  factory AppState.bootstrap() => AppState(platform: PlatformServices.create());

  final PlatformServices platform;
  final singleSong = SingleSongState();
  final packEditor = PackEditorState();
  final characterEditor = CharacterEditorState();

  AppPageId currentPage = AppPageId.singleSong;
  Locale locale = const Locale('zh', 'CN');
  String lastCacheResult = 'not cleaned yet';
  bool settingsOpen = false;
  bool _disposed = false;

  void selectPage(AppPageId page) {
    if (page == currentPage) return;
    currentPage = page;
    _safeNotifyListeners();
  }

  void setLocale(Locale next) {
    if (locale == next) return;
    locale = next;
    _safeNotifyListeners();
  }

  void openSettings() {
    if (settingsOpen) return;
    settingsOpen = true;
    _safeNotifyListeners();
  }

  void closeSettings() {
    if (!settingsOpen) return;
    settingsOpen = false;
    _safeNotifyListeners();
  }

  Future<void> mockImportSingleSong() async {
    singleSong.mockImport();
    _safeNotifyListeners();
  }

  Future<void> pickSingleZip() async {
    final path = await platform.filePicker.pickZip();
    if (path == null) return;
    await scanSingleSong(path);
  }

  Future<void> pickSingleFolder() async {
    final path = await platform.filePicker.pickFolder();
    if (path == null) return;
    await scanSingleSong(path);
  }

  Future<void> rescanSingleSong() async {
    final path = singleSong.inputPath;
    if (path == null || path.startsWith('mock://')) return;
    await scanSingleSong(path);
  }

  Future<void> scanSingleSong(String sourcePath) async {
    AppActionLogger.write(
      phase: 'start',
      page: currentPage.name,
      id: 'single.scan.start',
      label: sourcePath,
      before: debugSummary(),
    );
    final session = await _newSingleSessionPath();
    singleSong.startScanning(sourcePath, session);
    _safeNotifyListeners();
    final result = await platform.workerBridge.scanSingle(
      sourcePath: sourcePath,
      sessionPath: session,
    );
    if (_disposed) return;
    if (result.ok && result.data != null) {
      final inferredSide = await _inferSideFromSonglist(result.data!);
      final inferredAppearance = _inferSingleAppearance(inferredSide);
      singleSong.applyScan(
        result.data!,
        warnings: [
          ...result.warnings,
          if (inferredSide == null)
            'Side could not be inferred from songlist/slst; defaulting to Light.',
        ],
        workerLogs: result.logs,
        rawJsonPath: result.rawJsonPath,
        diagnosticsPath: result.diagnosticsPath,
        diagnostics: result.diagnostics,
        inferredAppearance: inferredAppearance,
      );
      AppActionLogger.write(
        phase: 'end',
        page: currentPage.name,
        id: 'single.scan.success',
        label: result.data!.songId ?? sourcePath,
        before: debugSummary(),
        after: debugSummary(),
      );
    } else {
      singleSong.fail(result.error ?? 'scan failed', workerLogs: result.logs);
      AppActionLogger.write(
        phase: 'error',
        page: currentPage.name,
        id: 'single.scan.error',
        label: result.error ?? 'scan failed',
        before: debugSummary(),
      );
    }
    _safeNotifyListeners();
  }

  Future<void> saveSingleSong() async {
    final scan = singleSong.scan;
    final edit = singleSong.edit;
    if (scan == null || edit == null) return;
    final suggested = _safeOutputName(edit.levelId.ifBlank(scan.songId));
    final target = await platform.saveDialog.saveArcpkg(
      suggestedName: suggested,
      initialDirectory: singleSong.lastSaveDirectory,
    );
    if (target == null) return;
    AppActionLogger.write(
      phase: 'start',
      page: currentPage.name,
      id: 'single.save.start',
      label: target,
      before: debugSummary(),
    );
    singleSong.startSaving();
    _safeNotifyListeners();
    final audioPreparation = await _prepareSingleAudioForSave();
    if (_disposed) return;
    if (!audioPreparation.ok) {
      singleSong.fail(
        audioPreparation.error ?? 'Audio is not ArcCreate-compatible.',
        workerLogs: audioPreparation.logs,
      );
      AppActionLogger.write(
        phase: 'error',
        page: currentPage.name,
        id: 'single.save.audio.error',
        label: audioPreparation.error ?? 'audio preparation failed',
        before: debugSummary(),
        after: debugSummary(),
      );
      _safeNotifyListeners();
      return;
    }
    final preparedScan = singleSong.scan;
    if (preparedScan == null) {
      singleSong.fail('scan lost before save');
      _safeNotifyListeners();
      return;
    }
    final result = await platform.workerBridge.saveSingle(
      SingleSongSaveRequest(
        scan: preparedScan,
        edit: edit,
        outputPath: target,
        appearance: singleSong.appearanceOptions,
        preprocess: singleSong.preprocessOptions,
        resources: audioPreparation.resources,
      ),
    );
    if (_disposed) return;
    if (result.ok && result.data != null) {
      if (Platform.isWindows) {
        singleSong.lastSaveDirectory = p.dirname(target);
      } else if (Platform.isAndroid) {
        singleSong.lastSaveDirectory = null;
      }
      singleSong.applySave(
        result.data!,
        warnings: result.warnings,
        workerLogs: result.logs,
      );
      AppActionLogger.write(
        phase: 'end',
        page: currentPage.name,
        id: 'single.save.success',
        label: target,
        before: debugSummary(),
        after: debugSummary(),
      );
    } else {
      singleSong.fail(result.error ?? 'save failed', workerLogs: result.logs);
      AppActionLogger.write(
        phase: 'error',
        page: currentPage.name,
        id: 'single.save.error',
        label: result.error ?? 'save failed',
        before: debugSummary(),
      );
    }
    _safeNotifyListeners();
  }

  void selectSingleChart(int index) {
    final before = debugSummary();
    singleSong.selectChart(index);
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'single.chart.select',
      label: 'chart[$index]',
      before: before,
      after: debugSummary(),
    );
    _safeNotifyListeners();
  }

  void markSingleChartEdited(String field) {
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'single.chart.edit',
      label: field,
      before: debugSummary(),
      after: debugSummary(),
    );
  }

  void updateSingleAppearance(ArcCreateAppearanceOptions options) {
    final before = debugSummary();
    singleSong.appearanceOptions = options;
    singleSong.appearanceEdited = true;
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'single.appearance.update',
      label: options.toJson().toString(),
      before: before,
      after: debugSummary(),
    );
    _safeNotifyListeners();
  }

  void updateSinglePreprocess(PreprocessOptions options) {
    final before = debugSummary();
    singleSong.preprocessOptions = options;
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'single.preprocess.update',
      label: options.toJson().toString(),
      before: before,
      after: debugSummary(),
    );
    _safeNotifyListeners();
  }

  Future<ResourceInfo?> convertSingleAudioResource(
    ResourceInfo resource,
  ) async {
    final before = debugSummary();
    try {
      final replacement = await _convertSingleAudioResource(resource);
      if (replacement == null) return null;
      AppActionLogger.write(
        phase: 'end',
        page: currentPage.name,
        id: 'single.audio.convert',
        label: replacement.path ?? replacement.name ?? 'converted audio',
        before: before,
        after: debugSummary(),
      );
      _safeNotifyListeners();
      return replacement;
    } catch (error) {
      singleSong.fail(error.toString());
      AppActionLogger.write(
        phase: 'error',
        page: currentPage.name,
        id: 'single.audio.convert.error',
        label: error.toString(),
        before: before,
        after: debugSummary(),
      );
      _safeNotifyListeners();
      rethrow;
    }
  }

  Future<void> pickSingleExternalBackgroundForSelectedChart() async {
    final scan = singleSong.scan;
    final chart = singleSong.selectedChart;
    if (scan == null || chart == null) return;
    final sourcePath = await platform.filePicker.pickImage();
    if (sourcePath == null || sourcePath.trim().isEmpty) return;
    final source = File(sourcePath);
    if (!source.existsSync()) return;
    final directory = Directory(
      p.join(scan.workspacePath, 'external-backgrounds'),
    )..createSync(recursive: true);
    final extension = p.extension(source.path).toLowerCase();
    final stem = 'external_bg_${chart.ratingClass}';
    final output = File(p.join(directory.path, '$stem$extension'));
    source.copySync(output.path);
    final before = debugSummary();
    singleSong.setExternalBackgroundForSelectedChart(
      path: output.path,
      name: p.basename(output.path),
      bgStem: stem,
    );
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'single.resource.externalBackground',
      label: output.path,
      before: before,
      after: debugSummary(),
    );
    _safeNotifyListeners();
  }

  void updatePackPreprocess(PreprocessOptions options) {
    if (packEditor.mode == PackEditorMode.existing) {
      packEditor.preprocessOptions = const PreprocessOptions.disabled();
      _safeNotifyListeners();
      return;
    }
    final before = debugSummary();
    packEditor.preprocessOptions = options;
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'pack.preprocess.update',
      label: options.toJson().toString(),
      before: before,
      after: debugSummary(),
    );
    _safeNotifyListeners();
  }

  Future<void> mockImportPack() async {
    packEditor.mockImport();
    _safeNotifyListeners();
  }

  Future<void> pickOfficialPackZip() async {
    final path = await platform.filePicker.pickZip();
    if (path == null) return;
    packEditor.setMode(PackEditorMode.official);
    await scanPack([path]);
  }

  Future<void> pickOfficialPackFolder() async {
    final path = await platform.filePicker.pickFolder();
    if (path == null) return;
    packEditor.setMode(PackEditorMode.official);
    await scanPack([path]);
  }

  Future<void> pickPackBundleArcpkg() async {
    final paths = await platform.filePicker.pickMultipleArcpkg();
    if (paths.isEmpty) return;
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'pack.pickArcpkgMultiple',
      label: '${paths.length}',
      before: debugSummary(),
    );
    packEditor.setMode(PackEditorMode.bundle);
    await scanPack(paths);
  }

  Future<void> pickPackBundleFolder() async {
    final path = await platform.filePicker.pickFolder();
    if (path == null) return;
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'pack.pickArcpkgFolder',
      label: path,
      before: debugSummary(),
    );
    packEditor.setMode(PackEditorMode.bundle);
    await scanPack([path]);
  }

  Future<void> appendPackBundleArcpkg() async {
    final paths = await platform.filePicker.pickMultipleArcpkg();
    if (paths.isEmpty) return;
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'pack.input.appendArcpkg',
      label: '${paths.length}',
      before: debugSummary(),
    );
    final merged = <String>[...packEditor.inputPaths, ...paths];
    packEditor.setMode(PackEditorMode.bundle);
    await scanPack(merged);
  }

  Future<void> appendPackBundleFolder() async {
    if (packEditor.inputPaths.isEmpty) return;
    final path = await platform.filePicker.pickFolder();
    if (path == null) return;
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'pack.input.appendFolder',
      label: path,
      before: debugSummary(),
    );
    packEditor.setMode(PackEditorMode.bundle);
    await scanPack([...packEditor.inputPaths, path]);
  }

  Future<void> appendExistingPackArcpkg() async {
    if (packEditor.inputPaths.isEmpty) return;
    final paths = await platform.filePicker.pickMultipleArcpkg();
    if (paths.isEmpty) return;
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'pack.input.appendArcpkg',
      label: '${paths.length}',
      before: debugSummary(),
    );
    packEditor.setMode(PackEditorMode.existing);
    await scanPack([...packEditor.inputPaths, ...paths]);
  }

  Future<void> appendExistingPackFolder() async {
    if (packEditor.inputPaths.isEmpty) return;
    final path = await platform.filePicker.pickFolder();
    if (path == null) return;
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'pack.input.appendFolder',
      label: path,
      before: debugSummary(),
    );
    packEditor.setMode(PackEditorMode.existing);
    await scanPack([...packEditor.inputPaths, path]);
  }

  Future<void> removePackInput(int index) async {
    final paths = [...packEditor.inputPaths];
    if (index < 0 || index >= paths.length) return;
    final removed = paths.removeAt(index);
    AppActionLogger.write(
      phase: 'start',
      page: currentPage.name,
      id: 'pack.input.remove',
      label: removed,
      before: debugSummary(),
    );
    if (paths.isEmpty) {
      clearPackInput();
      return;
    }
    await scanPack(paths);
  }

  void clearPackInput() {
    AppActionLogger.write(
      phase: 'start',
      page: currentPage.name,
      id: 'pack.input.clear',
      label: packEditor.inputPath ?? '',
      before: debugSummary(),
    );
    packEditor.clearInput();
    _safeNotifyListeners();
  }

  Future<void> pickExistingPack() async {
    final path = await platform.filePicker.pickArcpkg();
    if (path == null) return;
    packEditor.setMode(PackEditorMode.existing);
    await scanPack([path]);
  }

  Future<void> scanPack(List<String> sources) async {
    AppActionLogger.write(
      phase: 'start',
      page: currentPage.name,
      id: 'pack.scan.start',
      label: sources.join('; '),
      before: debugSummary(),
    );
    final session = await _newPackSessionPath();
    packEditor.startScanning(sources, session);
    _safeNotifyListeners();
    final result = switch (packEditor.mode) {
      PackEditorMode.official => platform.workerBridge.scanPackOfficial(
        sourcePath: sources.first,
        sessionPath: session,
      ),
      PackEditorMode.bundle => platform.workerBridge.scanPackBundle(
        sourcePaths: sources,
        sessionPath: session,
      ),
      PackEditorMode.existing => platform.workerBridge.scanPackExisting(
        basePath: sources.first,
        addSourcePaths: sources.skip(1).toList(),
        sessionPath: session,
      ),
    };
    final scan = await result;
    if (_disposed) return;
    if (scan.ok && scan.data != null) {
      packEditor.applyScan(
        scan.data!,
        warnings: scan.warnings,
        workerLogs: scan.logs,
      );
      AppActionLogger.write(
        phase: 'end',
        page: currentPage.name,
        id: 'pack.scan.success',
        label: scan.data!.packId ?? scan.data!.packName ?? sources.first,
        before: debugSummary(),
        after: debugSummary(),
      );
    } else {
      packEditor.fail(scan.error ?? 'pack scan failed', workerLogs: scan.logs);
      AppActionLogger.write(
        phase: 'error',
        page: currentPage.name,
        id: 'pack.scan.error',
        label: scan.error ?? 'pack scan failed',
        before: debugSummary(),
      );
    }
    _safeNotifyListeners();
  }

  Future<void> savePack() async {
    final scan = packEditor.scan;
    if (scan == null) return;
    final target = await platform.saveDialog.saveArcpkg(
      suggestedName: _safeOutputName(packEditor.packId),
      initialDirectory: singleSong.lastSaveDirectory,
    );
    if (target == null) return;
    AppActionLogger.write(
      phase: 'start',
      page: currentPage.name,
      id: 'pack.save.start',
      label: target,
      before: debugSummary(),
    );
    packEditor.startSaving();
    _safeNotifyListeners();
    final result = await platform.workerBridge.savePack(
      PackSaveRequest(
        scan: scan,
        outputPath: target,
        packName: packEditor.packName,
        packId: packEditor.packId,
        entries: packEditor.entries,
        preprocess: packEditor.mode == PackEditorMode.existing
            ? const PreprocessOptions.disabled()
            : packEditor.preprocessOptions,
      ),
    );
    if (_disposed) return;
    if (result.ok && result.data != null) {
      if (Platform.isWindows) singleSong.lastSaveDirectory = p.dirname(target);
      packEditor.applySave(
        result.data!,
        warnings: result.warnings,
        workerLogs: result.logs,
      );
      await _cleanPackSessionIfManaged(scan);
      AppActionLogger.write(
        phase: 'end',
        page: currentPage.name,
        id: 'pack.save.success',
        label: target,
        before: debugSummary(),
        after: debugSummary(),
      );
    } else {
      packEditor.fail(
        result.error ?? 'pack save failed',
        workerLogs: result.logs,
      );
      AppActionLogger.write(
        phase: 'error',
        page: currentPage.name,
        id: 'pack.save.error',
        label: result.error ?? 'pack save failed',
        before: debugSummary(),
      );
    }
    _safeNotifyListeners();
  }

  Future<void> pickCharacterImage() async {
    final path = await platform.filePicker.pickImage();
    if (path == null) return;
    await scanCharacterImage(path);
  }

  Future<void> pickCharacterPackage() async {
    final path = await platform.filePicker.pickArcpkg();
    if (path == null) return;
    await scanCharacterPackage(path);
  }

  void clearCharacterInput() {
    AppActionLogger.write(
      phase: 'start',
      page: currentPage.name,
      id: 'character.input.clear',
      label: characterEditor.inputPath ?? '',
      before: debugSummary(),
    );
    characterEditor.clearInput();
    _safeNotifyListeners();
  }

  Future<void> scanCharacterImage(String sourcePath) async {
    AppActionLogger.write(
      phase: 'start',
      page: currentPage.name,
      id: 'character.scanImage.start',
      label: sourcePath,
      before: debugSummary(),
    );
    final session = await _newCharacterSessionPath();
    characterEditor.startScanning(sourcePath);
    _safeNotifyListeners();
    final result = await platform.workerBridge.scanCharacterImage(
      sourcePath: sourcePath,
      sessionPath: session,
    );
    if (_disposed) return;
    if (result.ok && result.data != null) {
      characterEditor.applyScan(
        result.data!,
        warnings: result.warnings,
        workerLogs: result.logs,
      );
      AppActionLogger.write(
        phase: 'end',
        page: currentPage.name,
        id: 'character.scanImage.success',
        label: result.data!.identifier,
        before: debugSummary(),
        after: debugSummary(),
      );
    } else {
      characterEditor.fail(
        result.error ?? 'character image scan failed',
        workerLogs: result.logs,
      );
      AppActionLogger.write(
        phase: 'error',
        page: currentPage.name,
        id: 'character.scanImage.error',
        label: result.error ?? 'character image scan failed',
        before: debugSummary(),
      );
    }
    _safeNotifyListeners();
  }

  Future<void> scanCharacterPackage(String sourcePath) async {
    AppActionLogger.write(
      phase: 'start',
      page: currentPage.name,
      id: 'character.scanPackage.start',
      label: sourcePath,
      before: debugSummary(),
    );
    final session = await _newCharacterSessionPath();
    characterEditor.startScanning(sourcePath);
    _safeNotifyListeners();
    final result = await platform.workerBridge.scanCharacterPackage(
      sourcePath: sourcePath,
      sessionPath: session,
    );
    if (_disposed) return;
    if (result.ok && result.data != null) {
      characterEditor.applyScan(
        result.data!,
        warnings: result.warnings,
        workerLogs: result.logs,
      );
      AppActionLogger.write(
        phase: 'end',
        page: currentPage.name,
        id: 'character.scanPackage.success',
        label: result.data!.identifier,
        before: debugSummary(),
        after: debugSummary(),
      );
    } else {
      characterEditor.fail(
        result.error ?? 'character package scan failed',
        workerLogs: result.logs,
      );
      AppActionLogger.write(
        phase: 'error',
        page: currentPage.name,
        id: 'character.scanPackage.error',
        label: result.error ?? 'character package scan failed',
        before: debugSummary(),
      );
    }
    _safeNotifyListeners();
  }

  Future<void> saveCharacter() async {
    final scan = characterEditor.scan;
    final edit = characterEditor.edit;
    if (scan == null || edit == null || edit.imagePath == null) return;
    final target = await platform.saveDialog.saveArcpkg(
      suggestedName: _safeOutputName(edit.outputFileName),
      initialDirectory: singleSong.lastSaveDirectory,
    );
    if (target == null) return;
    AppActionLogger.write(
      phase: 'start',
      page: currentPage.name,
      id: 'character.save.start',
      label: target,
      before: debugSummary(),
    );
    characterEditor.startSaving();
    _safeNotifyListeners();
    final iconResult = await _regenerateCharacterIcon(scan, edit);
    if (_disposed) return;
    if (!iconResult.ok || iconResult.data == null) {
      characterEditor.fail(
        iconResult.error ?? 'character icon generation failed',
        workerLogs: iconResult.logs,
      );
      AppActionLogger.write(
        phase: 'error',
        page: currentPage.name,
        id: 'character.save.error',
        label: iconResult.error ?? 'icon generation failed',
        before: debugSummary(),
      );
      _safeNotifyListeners();
      return;
    }
    characterEditor.applyGeneratedIcon(
      iconResult.data!,
      warnings: iconResult.warnings,
      workerLogs: iconResult.logs,
    );
    final result = await platform.workerBridge.saveCharacter(
      CharacterSaveRequest(
        scan: scan,
        edit: edit,
        outputPath: target,
        iconPath: iconResult.data!.iconPath,
      ),
    );
    if (_disposed) return;
    if (result.ok && result.data != null) {
      if (Platform.isWindows) singleSong.lastSaveDirectory = p.dirname(target);
      characterEditor.applySave(
        result.data!,
        warnings: result.warnings,
        workerLogs: result.logs,
      );
      AppActionLogger.write(
        phase: 'end',
        page: currentPage.name,
        id: 'character.save.success',
        label: target,
        before: debugSummary(),
        after: debugSummary(),
      );
    } else {
      characterEditor.fail(
        result.error ?? 'character save failed',
        workerLogs: result.logs,
      );
      AppActionLogger.write(
        phase: 'error',
        page: currentPage.name,
        id: 'character.save.error',
        label: result.error ?? 'character save failed',
        before: debugSummary(),
      );
    }
    _safeNotifyListeners();
  }

  Future<void> applyCharacterIconCrop() async {
    final scan = characterEditor.scan;
    final edit = characterEditor.edit;
    if (scan == null || edit == null || edit.imagePath == null) return;
    final before = debugSummary();
    AppActionLogger.write(
      phase: 'start',
      page: currentPage.name,
      id: 'character.icon.generate.start',
      label:
          'cx=${edit.cropCenterX}; cy=${edit.cropCenterY}; size=${edit.cropSize}',
      before: before,
    );
    characterEditor.startIconGeneration();
    _safeNotifyListeners();

    final result = await _regenerateCharacterIcon(scan, edit);
    if (_disposed) return;
    if (result.ok && result.data != null) {
      characterEditor.applyGeneratedIcon(
        result.data!,
        warnings: result.warnings,
        workerLogs: result.logs,
      );
      AppActionLogger.write(
        phase: 'end',
        page: currentPage.name,
        id: 'character.icon.generate.success',
        label: result.data!.iconPath,
        before: before,
        after: debugSummary(),
      );
    } else {
      final error = result.error ?? 'character icon generation failed';
      characterEditor.failIconGeneration(error, workerLogs: result.logs);
      AppActionLogger.write(
        phase: 'error',
        page: currentPage.name,
        id: 'character.icon.generate.error',
        label: error,
        before: before,
      );
    }
    _safeNotifyListeners();
  }

  void setPackMode(PackEditorMode mode) {
    packEditor.setMode(mode);
    _safeNotifyListeners();
  }

  void updatePackMetadata({String? packName, String? packId}) {
    if (packName != null) packEditor.packName = packName;
    if (packId != null) packEditor.packId = packId;
    _safeNotifyListeners();
  }

  void togglePackLevel(int index) {
    packEditor.selectEntry(index);
    if (packEditor.expanded.contains(index)) {
      packEditor.expanded.remove(index);
    } else {
      packEditor.expanded.add(index);
    }
    _safeNotifyListeners();
  }

  void selectPackEntry(int index) {
    packEditor.selectEntry(index);
    _safeNotifyListeners();
  }

  void setPackLevelEnabled(int index, bool enabled) {
    packEditor.setLevelEnabled(index, enabled);
    _safeNotifyListeners();
  }

  void setPackChartEnabled(int entryIndex, int chartIndex, bool enabled) {
    final before = debugSummary();
    packEditor.setChartEnabled(entryIndex, chartIndex, enabled);
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'pack.chart.toggleEnabled',
      label: 'entry[$entryIndex].chart[$chartIndex]=$enabled',
      before: before,
      after: debugSummary(),
    );
    _safeNotifyListeners();
  }

  void expandAllPackLevels() {
    packEditor.expandAll();
    _safeNotifyListeners();
  }

  void collapseAllPackLevels() {
    packEditor.collapseAll();
    _safeNotifyListeners();
  }

  void focusPackWarningEntry(int index) {
    AppActionLogger.write(
      phase: 'start',
      page: currentPage.name,
      id: 'pack.warning.jump',
      label: 'entry[$index]',
      before: debugSummary(),
    );
    packEditor.focusWarningEntry(index);
    _safeNotifyListeners();
  }

  void expandPackDiagnostics() {
    AppActionLogger.write(
      phase: 'start',
      page: currentPage.name,
      id: 'pack.warning.expandDiagnostics',
      label: packEditor.packId,
      before: debugSummary(),
    );
    _safeNotifyListeners();
  }

  void updateCharacterPosition({double? x, double? y, double? scale}) {
    characterEditor.updatePosition(x: x, y: y, scale: scale);
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'character.editPosition',
      label: 'x=${x ?? '-'}; y=${y ?? '-'}; scale=${scale ?? '-'}',
      before: debugSummary(),
      after: debugSummary(),
    );
    _safeNotifyListeners();
  }

  void updateCharacterCrop({
    double? centerX,
    double? centerY,
    double? cropSize,
  }) {
    characterEditor.updateCrop(
      centerX: centerX,
      centerY: centerY,
      cropSize: cropSize,
    );
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'character.editIconCrop',
      label:
          'cx=${centerX ?? '-'}; cy=${centerY ?? '-'}; size=${cropSize ?? '-'}',
      before: debugSummary(),
      after: debugSummary(),
    );
    _safeNotifyListeners();
  }

  void updateCharacterMetadata({
    String? publisherId,
    String? characterId,
    String? directory,
    String? defaultName,
    String? zhCnName,
    String? outputFileName,
    String? imageFileName,
    String? iconFileName,
  }) {
    characterEditor.updateMetadata(
      publisherId: publisherId,
      characterId: characterId,
      directory: directory,
      defaultName: defaultName,
      zhCnName: zhCnName,
      outputFileName: outputFileName,
      imageFileName: imageFileName,
      iconFileName: iconFileName,
    );
    AppActionLogger.write(
      phase: 'end',
      page: currentPage.name,
      id: 'character.editMetadata',
      label: [
        if (publisherId != null) 'publisherId',
        if (characterId != null) 'characterId',
        if (directory != null) 'directory',
        if (defaultName != null) 'defaultName',
        if (zhCnName != null) 'zhCnName',
        if (outputFileName != null) 'outputFileName',
        if (imageFileName != null) 'imageFileName',
        if (iconFileName != null) 'iconFileName',
      ].join(','),
      before: debugSummary(),
      after: debugSummary(),
    );
    _safeNotifyListeners();
  }

  Future<void> clearCache() async {
    final result = await platform.cache.cleanupSafe(
      activeSessionPaths: _activeManagedSessionPaths(),
    );
    if (_disposed) return;
    if (locale.languageCode == 'zh') {
      lastCacheResult =
          '释放 ${_formatBytes(result.freedBytes)}；'
          '删除 ${result.deletedFiles} 个文件 / '
          '${result.deletedDirectories} 个目录；'
          '跳过 ${result.skippedActiveSessions} 个活动会话；'
          '失败 ${result.failedEntries} 项';
    } else {
      lastCacheResult =
          'freed ${_formatBytes(result.freedBytes)}; '
          'deleted ${result.deletedFiles} files / '
          '${result.deletedDirectories} dirs; '
          'skipped ${result.skippedActiveSessions}; '
          'failed ${result.failedEntries}';
    }
    _safeNotifyListeners();
  }

  Set<String> _activeManagedSessionPaths() {
    final paths = <String>{};
    void addWorkspace(String? workspacePath) {
      if (workspacePath == null || workspacePath.isEmpty) return;
      paths.add(_sessionRootPath(workspacePath));
    }

    addWorkspace(singleSong.workspacePath);
    addWorkspace(singleSong.scan?.workspacePath);
    addWorkspace(packEditor.scan?.workspacePath);
    addWorkspace(packEditor.scan?.addWorkspacePath);
    addWorkspace(packEditor.scan?.basePackPath);
    addWorkspace(characterEditor.scan?.workspacePath);
    return paths;
  }

  String _sessionRootPath(String workspacePath) {
    var sessionPath = workspacePath;
    if ({
      'input',
      'add-input',
      'base',
      'output',
      'preview',
      'generated',
      'external-backgrounds',
    }.contains(p.basename(sessionPath))) {
      sessionPath = p.dirname(sessionPath);
    }
    return sessionPath;
  }

  Future<_SingleAudioPreparation> _prepareSingleAudioForSave() async {
    final scan = singleSong.scan;
    if (scan == null) {
      return const _SingleAudioPreparation(ok: true);
    }
    final logs = <String>[];
    String? defaultAudioOverride;

    final defaultAudio = scan.audio;
    if (defaultAudio?.path != null) {
      final prepared = await _ensureAudioResourceCompatible(
        defaultAudio!,
        preferredOutputPath: _defaultAudioConversionPath(scan, defaultAudio),
      );
      if (!prepared.ok) {
        return _SingleAudioPreparation(
          ok: false,
          error: prepared.error,
          logs: logs..addAll(prepared.logs),
        );
      }
      logs.addAll(prepared.logs);
      if (prepared.resource != null &&
          !_samePath(prepared.resource!.path, defaultAudio.path)) {
        defaultAudioOverride = prepared.resource!.path;
      }
    }

    final currentScan = singleSong.scan ?? scan;
    for (final chart in currentScan.charts.where((chart) => chart.adopted)) {
      final audio = chart.audio;
      if (chart.audioOverride != true || audio?.path == null) continue;
      final preferredPath = _chartAudioConversionPath(
        currentScan,
        chart,
        audio!,
      );
      final prepared = await _ensureAudioResourceCompatible(
        audio,
        preferredOutputPath: preferredPath,
      );
      if (!prepared.ok) {
        return _SingleAudioPreparation(
          ok: false,
          error: prepared.error,
          logs: logs..addAll(prepared.logs),
        );
      }
      logs.addAll(prepared.logs);
    }

    return _SingleAudioPreparation(
      ok: true,
      resources: SingleSongResourceOverrides(audioPath: defaultAudioOverride),
      logs: logs,
    );
  }

  Future<ResourceInfo?> _convertSingleAudioResource(
    ResourceInfo resource,
  ) async {
    final scan = singleSong.scan;
    if (scan == null || resource.path == null) return null;
    final chart = scan.charts.firstWhere(
      (chart) => _samePath(chart.audio?.path, resource.path),
      orElse: () => const ChartMetadata(ratingClass: -1),
    );
    final preferredPath = chart.ratingClass >= 0 && chart.audioOverride
        ? _chartAudioConversionPath(scan, chart, resource)
        : _defaultAudioConversionPath(scan, resource);
    final prepared = await _ensureAudioResourceCompatible(
      resource,
      preferredOutputPath: preferredPath,
      forceConvert: true,
    );
    if (!prepared.ok) {
      throw AudioProcessingException(
        prepared.error ?? 'Audio conversion failed.',
      );
    }
    return prepared.resource;
  }

  Future<_PreparedAudioResource> _ensureAudioResourceCompatible(
    ResourceInfo resource, {
    required String preferredOutputPath,
    bool forceConvert = false,
  }) async {
    final path = resource.path;
    if (path == null || path.isEmpty) {
      return const _PreparedAudioResource(ok: true);
    }
    final analysis = await AudioProcessingService.instance.analyze(path);
    final report = analysis.compatibility;
    if (!forceConvert && report.canExportDirectly) {
      return _PreparedAudioResource(
        ok: true,
        logs: ['Audio is ArcCreate-compatible: ${resource.name ?? path}'],
      );
    }
    if (!forceConvert && report.blocksExport) {
      return _PreparedAudioResource(
        ok: false,
        error: report.reason ?? 'Audio is not usable for ArcCreate export.',
        logs: ['Audio export blocked: ${resource.name ?? path}'],
      );
    }
    if (!report.canConvert && !forceConvert) {
      return _PreparedAudioResource(
        ok: false,
        error: report.reason ?? 'Audio cannot be converted.',
        logs: ['Audio cannot be converted: ${resource.name ?? path}'],
      );
    }
    try {
      final converted = await AudioProcessingService.instance
          .convertToCompatibleOgg(
            inputPath: path,
            outputPath: preferredOutputPath,
          );
      final file = File(converted.outputPath);
      final replacement = resource.copyWith(
        path: converted.outputPath,
        name: p.basename(converted.outputPath),
        source: 'converted',
        sizeBytes: file.existsSync() ? file.lengthSync() : null,
        raw: {
          ...resource.raw,
          'convertedFrom': path,
          'audioCompatibility': 'compatibleOgg',
        },
      );
      singleSong.replaceAudioResourceByPath(path, replacement);
      return _PreparedAudioResource(
        ok: true,
        resource: replacement,
        logs: [
          'Converted audio for ArcCreate: ${resource.name ?? path} -> ${replacement.name}',
        ],
      );
    } catch (error) {
      return _PreparedAudioResource(
        ok: false,
        error: error.toString(),
        logs: ['Audio conversion failed: ${resource.name ?? path}: $error'],
      );
    }
  }

  String _defaultAudioConversionPath(
    SingleSongScanResult scan,
    ResourceInfo resource,
  ) {
    final sourcePath = resource.path;
    if (sourcePath != null &&
        _isInsidePath(scan.workspacePath, sourcePath) &&
        p.extension(sourcePath).toLowerCase() == '.ogg') {
      return sourcePath;
    }
    final directory = Directory(p.join(scan.workspacePath, 'converted-audio'));
    directory.createSync(recursive: true);
    final stem = _safeFileStem(resource.name ?? sourcePath ?? 'base');
    return p.join(directory.path, '${stem}_compatible.ogg');
  }

  String _chartAudioConversionPath(
    SingleSongScanResult scan,
    ChartMetadata chart,
    ResourceInfo resource,
  ) {
    final sourcePath = resource.path;
    if (sourcePath != null && _isInsidePath(scan.workspacePath, sourcePath)) {
      final sourceDirectory = p.dirname(sourcePath);
      return p.join(sourceDirectory, '${chart.ratingClass}.ogg');
    }
    final directory = Directory(p.join(scan.workspacePath, 'converted-audio'));
    directory.createSync(recursive: true);
    return p.join(directory.path, '${chart.ratingClass}.ogg');
  }

  bool _isInsidePath(String parent, String child) {
    try {
      final normalizedParent = p.normalize(parent);
      final normalizedChild = p.normalize(child);
      return p.equals(normalizedParent, normalizedChild) ||
          p.isWithin(normalizedParent, normalizedChild);
    } catch (_) {
      return false;
    }
  }

  bool _samePath(String? left, String? right) {
    if (left == null || right == null) return false;
    return p.equals(p.normalize(left), p.normalize(right));
  }

  String _safeFileStem(String value) {
    final stem = p.basenameWithoutExtension(value).ifBlank('audio') ?? 'audio';
    return stem.replaceAll(RegExp(r'[<>:"/\\|?*\x00-\x1F]'), '_');
  }

  String debugSummary() {
    return [
      'page=${currentPage.name}',
      'locale=${locale.toLanguageTag()}',
      'settingsOpen=$settingsOpen',
      'singleInput=${singleSong.inputPath != null}',
      'packLevels=${packEditor.levels.length}',
      'packExpanded=${packEditor.expanded.length}',
      'characterInput=${characterEditor.inputPath != null}',
      'x=${characterEditor.x.toStringAsFixed(1)}',
      'y=${characterEditor.y.toStringAsFixed(1)}',
      'scale=${characterEditor.scale.toStringAsFixed(2)}',
    ].join(';');
  }

  Future<String> _newSingleSessionPath() async {
    final root = await platform.cache.cacheRoot();
    final session = Directory(
      p.join(root, 'single-session-${DateTime.now().millisecondsSinceEpoch}'),
    );
    if (!await session.exists()) await session.create(recursive: true);
    return session.path;
  }

  Future<String> _newPackSessionPath() async {
    final root = await platform.cache.cacheRoot();
    final session = Directory(
      p.join(root, 'pack-session-${DateTime.now().millisecondsSinceEpoch}'),
    );
    if (!await session.exists()) await session.create(recursive: true);
    return session.path;
  }

  Future<String> _newCharacterSessionPath() async {
    final root = await platform.cache.cacheRoot();
    final session = Directory(
      p.join(
        root,
        'character-session-${DateTime.now().millisecondsSinceEpoch}',
      ),
    );
    if (!await session.exists()) await session.create(recursive: true);
    return session.path;
  }

  Future<WorkerActionResult<CharacterIconResult>> _regenerateCharacterIcon(
    CharacterScanResult scan,
    CharacterEditState edit,
  ) async {
    final imagePath = edit.imagePath;
    if (imagePath == null || imagePath.isEmpty) {
      return const WorkerActionResult(
        ok: false,
        error: 'character image is missing',
      );
    }
    final session = p.dirname(scan.workspacePath);
    final iconName = _safeOutputName(
      edit.characterId,
    ).replaceAll(RegExp(r'\.arcpkg$', caseSensitive: false), '_icon.png');
    final outputPath = p.join(session, 'generated', iconName);
    return platform.workerBridge.generateCharacterIcon(
      CharacterIconRequest(
        imagePath: imagePath,
        outputPath: outputPath,
        centerX: edit.cropCenterX,
        centerY: edit.cropCenterY,
        cropSize: edit.cropSize,
      ),
    );
  }

  Future<void> _cleanSessionIfManaged(String workspacePath) async {
    final root = await platform.cache.cacheRoot();
    var sessionPath = p.dirname(workspacePath);
    if ({
      'input',
      'add-input',
      'base',
      'output',
      'preview',
    }.contains(p.basename(sessionPath))) {
      sessionPath = p.dirname(sessionPath);
    }
    final session = Directory(sessionPath);
    final managed =
        p.equals(session.path, root) || p.isWithin(root, session.path);
    if (managed && await session.exists()) {
      await session.delete(recursive: true);
    }
  }

  Future<void> _cleanPackSessionIfManaged(PackScanResult scan) async {
    final candidates = [
      scan.workspacePath,
      scan.addWorkspacePath,
      scan.basePackPath,
    ].whereType<String>();
    for (final workspacePath in candidates) {
      await _cleanSessionIfManaged(workspacePath);
    }
  }

  ArcCreateAppearanceOptions _inferSingleAppearance(String? side) {
    return singleSong.appearanceOptions.copyWith(side: side ?? 'LIGHT');
  }

  Future<String?> _inferSideFromSonglist(SingleSongScanResult scan) async {
    final songlistPath = scan.songlist?.path;
    if (songlistPath == null || songlistPath.isEmpty) return null;
    final file = File(songlistPath);
    if (!await file.exists()) return null;
    try {
      final content = await file.readAsString(encoding: utf8);
      final decoded = jsonDecode(_normalizeSonglistJson(content));
      final songs = _songlistSongs(decoded);
      final song = songs.firstWhere(
        (entry) =>
            scan.songId != null && _stringValue(entry['id']) == scan.songId,
        orElse: () => songs.length == 1 ? songs.single : <String, Object?>{},
      );
      return _sideCodeToAppearance(_intValue(song['side']));
    } catch (_) {
      return null;
    }
  }

  String _normalizeSonglistJson(String content) {
    final trimmed = content.trim();
    if (trimmed.isEmpty) return '{}';
    if (trimmed.startsWith('{') || trimmed.startsWith('[')) return trimmed;
    return '[$trimmed]';
  }

  List<Map<String, Object?>> _songlistSongs(Object? decoded) {
    if (decoded is List) {
      return decoded.whereType<Map>().map(_objectMap).toList();
    }
    if (decoded is Map) {
      final map = _objectMap(decoded);
      final songs = map['songs'] ?? map['songlist'];
      if (songs is List) return songs.whereType<Map>().map(_objectMap).toList();
      if (map.containsKey('id') || map.containsKey('side')) return [map];
    }
    return const [];
  }

  Map<String, Object?> _objectMap(Map value) =>
      value.map((key, value) => MapEntry(key.toString(), value));

  String? _stringValue(Object? value) => value?.toString();

  int? _intValue(Object? value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '');
  }

  String? _sideCodeToAppearance(int? side) {
    return switch (side) {
      0 => 'LIGHT',
      1 => 'CONFLICT',
      2 => 'COLORLESS',
      3 => 'LIGHT',
      _ => null,
    };
  }

  String _safeOutputName(String? raw) {
    final id = (raw == null || raw.trim().isEmpty) ? 'etoilebridge-level' : raw;
    final safe = id.replaceAll(RegExp(r'[\\/:*?"<>|]+'), '_');
    return safe.endsWith('.arcpkg') ? safe : '$safe.arcpkg';
  }

  void _safeNotifyListeners() {
    if (_disposed) return;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}

extension _BlankString on String {
  String? ifBlank(String? fallback) => trim().isEmpty ? fallback : this;
}

class _SingleAudioPreparation {
  const _SingleAudioPreparation({
    required this.ok,
    this.resources = const SingleSongResourceOverrides(),
    this.error,
    this.logs = const [],
  });

  final bool ok;
  final SingleSongResourceOverrides resources;
  final String? error;
  final List<String> logs;
}

class _PreparedAudioResource {
  const _PreparedAudioResource({
    required this.ok,
    this.resource,
    this.error,
    this.logs = const [],
  });

  final bool ok;
  final ResourceInfo? resource;
  final String? error;
  final List<String> logs;
}

String _formatBytes(int bytes) {
  if (bytes.abs() < 1024) return '$bytes B';
  if (bytes.abs() < 1024 * 1024) {
    return '${(bytes / 1024).toStringAsFixed(1)} KB';
  }
  if (bytes.abs() < 1024 * 1024 * 1024) {
    return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
  }
  return '${(bytes / 1024 / 1024 / 1024).toStringAsFixed(1)} GB';
}

class AppScope extends InheritedNotifier<AppState> {
  const AppScope({required AppState state, required super.child, super.key})
    : super(notifier: state);

  static AppState of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<AppScope>();
    assert(scope != null, 'AppScope is missing.');
    return scope!.notifier!;
  }
}
