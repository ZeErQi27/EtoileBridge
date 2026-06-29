import 'dart:io';

import 'package:etoile_bridge/app/app_state.dart';
import 'package:etoile_bridge/core/models/character_models.dart';
import 'package:etoile_bridge/core/models/conversion_options.dart';
import 'package:etoile_bridge/core/models/pack_models.dart';
import 'package:etoile_bridge/core/models/single_song_models.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;

const _runLocalSmokeEnv = 'ETOILEBRIDGE_FLUTTER_LOCAL_SMOKE';
const _localOutputDirEnv = 'ETOILEBRIDGE_FLUTTER_LOCAL_OUTPUT_DIR';
const _slstBugDir = r'E:\ArcpkgAPP\samples\歌曲压缩包bug修复\歌曲压缩包bug修复2号';
const _packBundleSample =
    r'E:\ArcpkgAPP\samples\曲包\成功的arccreate曲包示例\DJC.arcpkg';
const _officialPackSample =
    r'E:\ArcpkgAPP\samples\曲包\arcaea曲包示例\vividstasis.zip';
const _caiSeDeHeiZip = r'E:\ArcpkgAPP\samples\test_song\彩色的黑.zip';
const _caiSeDeHeiFolder = r'E:\ArcpkgAPP\samples\test_song';
const _unicodeZip =
    r'E:\ArcpkgAPP\samples\歌曲压缩包bug修复\Essence of Twilight BYD版.zip';
const _multiAffZip =
    r'E:\ArcpkgAPP\samples\歌曲压缩包示例\Echoes of Memoria (含 FTR 12 子供向).zip';

const _characterPngSample =
    r'E:\ArcpkgAPP\samples\搭档包\ArcCreate游戏内搭档对比\otto.png';
const _characterArcpkgSample = r'E:\ArcpkgAPP\samples\搭档包\zeerqi27.otto.arcpkg';
const _characterWideArcpkgSample =
    r'E:\ArcpkgAPP\samples\搭档包\zeerqi27.奈龙.arcpkg';

void main() {
  final shouldRun =
      Platform.environment[_runLocalSmokeEnv] == '1' && Platform.isWindows;
  final skipReason = shouldRun
      ? false
      : 'Set $_runLocalSmokeEnv=1 on Windows to run local worker smoke tests.';

  test('local Windows worker scans and saves 彩色的黑.zip', () async {
    final sample = File(_caiSeDeHeiZip);
    expect(sample.existsSync(), isTrue, reason: sample.path);

    final state = AppState.bootstrap();
    await state.scanSingleSong(sample.path);

    final scan = state.singleSong.scan;
    final edit = state.singleSong.edit;
    expect(scan, isNotNull);
    expect(edit, isNotNull);
    expect(scan!.songId, 'CaiSeDeHei');
    expect(scan.title, '彩色的黑');
    expect(scan.artist, '吉克隽逸');
    expect(scan.bpmText, '105');
    expect(scan.bpmBase, 105);
    expect(scan.sourceKind, SingleSourceKind.officialSong);
    expect(scan.inputType, SingleInputType.zip);
    expect(scan.difficulty, '4:Eternal 10 (10.0)');
    expect(scan.charts.single.rating, 10);
    expect(scan.charts.single.ratingPlus, isFalse);
    expect(scan.charts.single.chartPath, isNotEmpty);
    expect(scan.charts.single.affPath, isNotEmpty);
    expect(scan.audio?.name, 'base.ogg');
    expect(scan.jacket?.name, '1080_base.jpg');
    expect(scan.background?.name, 'kaguya.jpg');
    expect(scan.affFiles.map((aff) => aff.name), contains('4.aff'));
    expect(state.singleSong.scanRawJsonPath, isNotNull);
    expect(state.singleSong.scanDiagnosticsPath, isNotNull);
    expect(File(state.singleSong.scanRawJsonPath!).existsSync(), isTrue);
    expect(File(state.singleSong.scanDiagnosticsPath!).existsSync(), isTrue);
    expect(state.singleSong.scanDiagnostics.join('\n'), contains('songId'));
    expect(state.singleSong.scanDiagnostics.join('\n'), contains('version'));

    final outDir = await _smokeOutputDir('single');
    final outputPath = p.join(outDir.path, 'CaiSeDeHei.arcpkg');
    edit!.title = '彩色的黑 Flutter 回归';
    edit.charts.single.charter = '◈Flutter Charter◈';
    final save = await state.platform.workerBridge.saveSingle(
      SingleSongSaveRequest(scan: scan, edit: edit, outputPath: outputPath),
    );
    expect(save.ok, isTrue, reason: save.error);
    expect(File(outputPath).existsSync(), isTrue);
    expect(File(outputPath).lengthSync(), greaterThan(0));
    // ignore: avoid_print
    print('single smoke output: $outputPath');
    // ignore: avoid_print
    print('single raw scan JSON: ${state.singleSong.scanRawJsonPath}');
    // ignore: avoid_print
    print('single scan diagnostics: ${state.singleSong.scanDiagnosticsPath}');
  }, skip: skipReason);

  test('local Windows worker scans and saves a single-song folder', () async {
    final sample = Directory(_caiSeDeHeiFolder);
    expect(sample.existsSync(), isTrue, reason: sample.path);

    final state = AppState.bootstrap();
    await state.scanSingleSong(sample.path);
    final scan = state.singleSong.scan;
    final edit = state.singleSong.edit;
    expect(scan, isNotNull);
    expect(edit, isNotNull);
    expect(scan!.songId, isNotEmpty);
    expect(scan.inputType, SingleInputType.folder);
    expect(scan.charts, isNotEmpty);
    expect(scan.affFiles, isNotEmpty);

    final outDir = await _smokeOutputDir('single-folder');
    final outputPath = p.join(outDir.path, 'CaiSeDeHei-folder.arcpkg');
    final save = await state.platform.workerBridge.saveSingle(
      SingleSongSaveRequest(scan: scan, edit: edit!, outputPath: outputPath),
    );
    expect(save.ok, isTrue, reason: save.error);
    expect(File(outputPath).existsSync(), isTrue);
    expect(File(outputPath).lengthSync(), greaterThan(0));
    // ignore: avoid_print
    print('single folder smoke output: $outputPath (${scan.songId})');
  }, skip: skipReason);

  test(
    'local Windows worker applies side and appearance skin fields',
    () async {
      final conflictSample = File(_caiSeDeHeiZip);
      final lightSample = Directory(_caiSeDeHeiFolder);
      expect(conflictSample.existsSync(), isTrue, reason: conflictSample.path);
      expect(lightSample.existsSync(), isTrue, reason: lightSample.path);

      final conflictState = AppState.bootstrap();
      await conflictState.scanSingleSong(conflictSample.path);
      expect(conflictState.singleSong.appearanceOptions.side, 'CONFLICT');
      final conflictScan = conflictState.singleSong.scan;
      final conflictEdit = conflictState.singleSong.edit;
      expect(conflictScan, isNotNull);
      expect(conflictEdit, isNotNull);

      final outDir = await _smokeOutputDir('single-appearance');
      final conflictToLightPath = p.join(
        outDir.path,
        'conflict-to-light.arcpkg',
      );
      final conflictToLight = await conflictState.platform.workerBridge
          .saveSingle(
            SingleSongSaveRequest(
              scan: conflictScan!,
              edit: conflictEdit!,
              outputPath: conflictToLightPath,
              appearance: const ArcCreateAppearanceOptions(
                side: 'LIGHT',
                note: 'LIGHT',
                particle: 'LIGHT',
                accent: 'DYNAMIX',
                track: 'TEMPEST',
                singleLine: 'NEO',
              ),
            ),
          );
      expect(conflictToLight.ok, isTrue, reason: conflictToLight.error);
      final conflictToLightProject = await _readProjectArcproj(
        File(conflictToLightPath),
      );
      expect(conflictToLightProject, contains('side: "light"'));
      expect(conflictToLightProject, contains('note: "light"'));
      expect(conflictToLightProject, contains('particle: "light"'));
      expect(conflictToLightProject, contains('accent: "dynamix"'));
      expect(conflictToLightProject, contains('track: "tempestissimo"'));
      expect(conflictToLightProject, contains('singleLine: "neo"'));

      final lightState = AppState.bootstrap();
      await lightState.scanSingleSong(lightSample.path);
      expect(lightState.singleSong.appearanceOptions.side, 'LIGHT');
      final lightScan = lightState.singleSong.scan;
      final lightEdit = lightState.singleSong.edit;
      expect(lightScan, isNotNull);
      expect(lightEdit, isNotNull);

      final lightToConflictPath = p.join(
        outDir.path,
        'light-to-conflict.arcpkg',
      );
      final lightToConflict = await lightState.platform.workerBridge.saveSingle(
        SingleSongSaveRequest(
          scan: lightScan!,
          edit: lightEdit!,
          outputPath: lightToConflictPath,
          appearance: const ArcCreateAppearanceOptions(side: 'CONFLICT'),
        ),
      );
      expect(lightToConflict.ok, isTrue, reason: lightToConflict.error);
      final lightToConflictProject = await _readProjectArcproj(
        File(lightToConflictPath),
      );
      expect(lightToConflictProject, contains('side: "conflict"'));
      // ignore: avoid_print
      print(
        'appearance smoke outputs: $conflictToLightPath / $lightToConflictPath',
      );
    },
    skip: skipReason,
  );

  test(
    'local Windows worker preserves Unicode metadata during save',
    () async {
      final sample = File(_unicodeZip);
      expect(sample.existsSync(), isTrue, reason: sample.path);

      final state = AppState.bootstrap();
      await state.scanSingleSong(sample.path);
      final scan = state.singleSong.scan;
      final edit = state.singleSong.edit;
      expect(scan, isNotNull);
      expect(edit, isNotNull);

      final originalUnicode = [
        scan?.artist,
        scan?.charts.map((chart) => chart.charter).join('\n'),
        edit?.charts.map((chart) => chart.charter).join('\n'),
      ].join('\n');
      expect(originalUnicode, contains('◈Twiℓight Yūgen◈'));

      final outDir = await _smokeOutputDir('unicode');
      final outputPath = p.join(outDir.path, 'unicode.arcpkg');
      final save = await state.platform.workerBridge.saveSingle(
        SingleSongSaveRequest(scan: scan!, edit: edit!, outputPath: outputPath),
      );
      expect(save.ok, isTrue, reason: save.error);
      expect(File(outputPath).existsSync(), isTrue);
      expect(File(outputPath).lengthSync(), greaterThan(0));
      // ignore: avoid_print
      print('unicode smoke output: $outputPath');
    },
    skip: skipReason,
  );
  test('local Windows worker recovers SLST fallback samples', () async {
    final fallbackSamples = [File(p.join(_slstBugDir, 'Chaos.zip'))];
    for (final sample in fallbackSamples) {
      expect(sample.existsSync(), isTrue, reason: sample.path);
      final state = AppState.bootstrap();
      await state.scanSingleSong(sample.path);
      final scan = state.singleSong.scan;
      expect(scan, isNotNull, reason: sample.path);
      expect(scan!.songId, isNotNull, reason: sample.path);
      expect(scan.title, isNotNull, reason: sample.path);
      expect(scan.artist, isNotNull, reason: sample.path);
      expect(scan.charts, isNotEmpty, reason: sample.path);
      expect(
        [
          ...scan.warnings,
          ...state.singleSong.logs.map((log) => log.message),
        ].join('\n').toLowerCase(),
        contains('fallback'),
        reason: sample.path,
      );
      // ignore: avoid_print
      print('${p.basename(sample.path)} => ${scan.songId} / ${scan.title}');
    }

    final questionRatingSample = File(p.join(_slstBugDir, '7th Avenue.zip'));
    expect(questionRatingSample.existsSync(), isTrue);
    final state = AppState.bootstrap();
    await state.scanSingleSong(questionRatingSample.path);
    final scan = state.singleSong.scan;
    expect(scan, isNotNull, reason: questionRatingSample.path);
    expect(scan!.charts, isNotEmpty);
    expect(scan.charts.any((chart) => chart.isQuestionRating), isTrue);
    expect(
      [
        ...scan.warnings,
        ...state.singleSong.logs.map((log) => log.message),
      ].join('\n').toLowerCase(),
      isNot(contains('metadata with fallback')),
    );
  }, skip: skipReason);

  test('local Windows worker scans a multi AFF single-song sample', () async {
    final sample = File(_multiAffZip);
    expect(sample.existsSync(), isTrue, reason: sample.path);

    final state = AppState.bootstrap();
    await state.scanSingleSong(sample.path);
    final scan = state.singleSong.scan;
    final edit = state.singleSong.edit;
    expect(scan, isNotNull, reason: sample.path);
    expect(edit, isNotNull, reason: sample.path);
    expect(scan!.affFiles.length, greaterThan(1), reason: sample.path);
    expect(edit!.charts.length, greaterThan(1), reason: sample.path);

    edit.charts.last.adopted = false;
    final saveCharts = edit.toSaveJson()['charts']! as Iterable<Object?>;
    expect(
      saveCharts.whereType<Map<String, Object?>>().last['adopted'],
      isFalse,
    );
    // ignore: avoid_print
    print(
      'multi AFF single smoke: ${p.basename(sample.path)} => '
      '${edit.charts.length} charts, ${scan.affFiles.length} aff files',
    );
  }, skip: skipReason);

  test('local Windows worker applies single preprocess toggles', () async {
    final sample = File(_caiSeDeHeiZip);
    expect(sample.existsSync(), isTrue, reason: sample.path);

    final state = AppState.bootstrap();
    await state.scanSingleSong(sample.path);
    final scan = state.singleSong.scan;
    final edit = state.singleSong.edit;
    expect(scan, isNotNull);
    expect(edit, isNotNull);
    final affPath = edit!.charts.single.affPath;
    expect(affPath, isNotNull);
    _injectPreprocessFixtures(File(affPath!));

    final outDir = await _smokeOutputDir('single-preprocess');
    final disabledPath = p.join(outDir.path, 'preprocess-disabled.arcpkg');
    final disabledSave = await state.platform.workerBridge.saveSingle(
      SingleSongSaveRequest(
        scan: scan!,
        edit: edit,
        outputPath: disabledPath,
        preprocess: const PreprocessOptions(
          deleteDesignantLine: false,
          fixZeroDurationArcTap: false,
          fixReversedArcTime: false,
          expandArcResolution: false,
        ),
      ),
    );
    expect(disabledSave.ok, isFalse, reason: disabledSave.logs.join('\n'));
    expect(disabledSave.error ?? '', isNotEmpty);

    final enabledPath = p.join(outDir.path, 'preprocess-enabled.arcpkg');
    final enabledSave = await state.platform.workerBridge.saveSingle(
      SingleSongSaveRequest(scan: scan, edit: edit, outputPath: enabledPath),
    );
    expect(enabledSave.ok, isTrue, reason: enabledSave.error);
    expect(File(enabledPath).existsSync(), isTrue);
    expect(File(enabledPath).lengthSync(), greaterThan(0));
    // ignore: avoid_print
    print('single preprocess smoke output: $enabledPath');
  }, skip: skipReason);

  test('local Windows worker scans and saves an arcpkg bundle pack', () async {
    final sample = File(_packBundleSample);
    expect(sample.existsSync(), isTrue, reason: sample.path);

    final state = AppState.bootstrap();
    state.setPackMode(PackEditorMode.bundle);
    await state.scanPack([sample.path]);
    final scan = state.packEditor.scan;
    expect(scan, isNotNull);
    expect(scan!.entries, isNotEmpty);

    final outDir = await _smokeOutputDir('pack');
    final outputPath = p.join(outDir.path, 'flutter-pack-smoke.arcpkg');
    final save = await state.platform.workerBridge.savePack(
      PackSaveRequest(
        scan: scan,
        outputPath: outputPath,
        packName: state.packEditor.packName,
        packId: state.packEditor.packId,
        entries: state.packEditor.entries,
      ),
    );
    expect(save.ok, isTrue, reason: save.error);
    expect(File(outputPath).existsSync(), isTrue);
    expect(File(outputPath).lengthSync(), greaterThan(0));
    // ignore: avoid_print
    print('pack smoke output: $outputPath');
  }, skip: skipReason);

  test('local Windows worker applies pack preprocess toggles', () async {
    final sample = File(_officialPackSample);
    expect(sample.existsSync(), isTrue, reason: sample.path);

    final state = AppState.bootstrap();
    state.setPackMode(PackEditorMode.official);
    await state.scanPack([sample.path]);
    final scan = state.packEditor.scan;
    expect(scan, isNotNull);
    final scanResult = scan!;

    final outDir = await _smokeOutputDir('pack-preprocess');
    final disabledPath = p.join(outDir.path, 'pack-preprocess-disabled.arcpkg');
    final disabledSave = await state.platform.workerBridge.savePack(
      PackSaveRequest(
        scan: scanResult,
        outputPath: disabledPath,
        packName: state.packEditor.packName,
        packId: state.packEditor.packId,
        entries: state.packEditor.entries,
        preprocess: const PreprocessOptions(
          deleteDesignantLine: false,
          fixZeroDurationArcTap: false,
          fixReversedArcTime: false,
          expandArcResolution: false,
        ),
      ),
    );
    expect(disabledSave.ok, isTrue, reason: disabledSave.error);
    final disabledLogs = disabledSave.logs.join('\n');
    expect(disabledLogs, contains('Preprocessed'));
    expect(disabledLogs, contains('designant=0'));
    expect(disabledLogs, contains('zeroArcTap=0'));
    expect(disabledLogs, contains('reversedArc=0'));
    expect(disabledLogs, contains('arcresolution=0'));

    final enabledState = AppState.bootstrap();
    enabledState.setPackMode(PackEditorMode.official);
    await enabledState.scanPack([sample.path]);
    final enabledScan = enabledState.packEditor.scan;
    expect(enabledScan, isNotNull);
    final enabledPath = p.join(outDir.path, 'pack-preprocess-enabled.arcpkg');
    final enabledSave = await enabledState.platform.workerBridge.savePack(
      PackSaveRequest(
        scan: enabledScan!,
        outputPath: enabledPath,
        packName: enabledState.packEditor.packName,
        packId: enabledState.packEditor.packId,
        entries: enabledState.packEditor.entries,
      ),
    );
    expect(enabledSave.ok, isTrue, reason: enabledSave.error);
    expect(enabledSave.logs.join('\n'), contains('Preprocessed'));
    expect(File(enabledPath).existsSync(), isTrue);
    expect(File(enabledPath).lengthSync(), greaterThan(0));
    // ignore: avoid_print
    print('pack preprocess smoke output: $enabledPath');
  }, skip: skipReason);

  test('local Windows worker scans and saves an official pack zip', () async {
    final sample = File(_officialPackSample);
    expect(sample.existsSync(), isTrue, reason: sample.path);

    final state = AppState.bootstrap();
    state.setPackMode(PackEditorMode.official);
    await state.scanPack([sample.path]);
    final scan = state.packEditor.scan;
    expect(scan, isNotNull);
    expect(scan!.entries, isNotEmpty);

    final outDir = await _smokeOutputDir('pack-official');
    final outputPath = p.join(
      outDir.path,
      'flutter-official-pack-smoke.arcpkg',
    );
    final save = await state.platform.workerBridge.savePack(
      PackSaveRequest(
        scan: scan,
        outputPath: outputPath,
        packName: state.packEditor.packName,
        packId: state.packEditor.packId,
        entries: state.packEditor.entries,
      ),
    );
    expect(save.ok, isTrue, reason: save.error);
    expect(File(outputPath).existsSync(), isTrue);
    expect(File(outputPath).lengthSync(), greaterThan(0));
    // ignore: avoid_print
    print('official pack smoke output: $outputPath');
  }, skip: skipReason);

  test('local Windows worker scans and saves an existing pack', () async {
    final sample = File(_packBundleSample);
    expect(sample.existsSync(), isTrue, reason: sample.path);

    final state = AppState.bootstrap();
    state.setPackMode(PackEditorMode.existing);
    await state.scanPack([sample.path]);
    final scan = state.packEditor.scan;
    expect(scan, isNotNull);
    expect(scan!.entries, isNotEmpty);

    final outDir = await _smokeOutputDir('pack-existing');
    final outputPath = p.join(
      outDir.path,
      'flutter-existing-pack-smoke.arcpkg',
    );
    final save = await state.platform.workerBridge.savePack(
      PackSaveRequest(
        scan: scan,
        outputPath: outputPath,
        packName: state.packEditor.packName,
        packId: state.packEditor.packId,
        entries: state.packEditor.entries,
      ),
    );
    expect(save.ok, isTrue, reason: save.error);
    expect(File(outputPath).existsSync(), isTrue);
    expect(File(outputPath).lengthSync(), greaterThan(0));
    // ignore: avoid_print
    print('existing pack smoke output: $outputPath');
  }, skip: skipReason);

  test(
    'local Windows worker imports a character image and saves arcpkg',
    () async {
      final sample = File(_characterPngSample);
      expect(sample.existsSync(), isTrue, reason: sample.path);

      final state = AppState.bootstrap();
      await state.scanCharacterImage(sample.path);
      final scan = state.characterEditor.scan;
      final edit = state.characterEditor.edit;
      expect(scan, isNotNull);
      expect(edit, isNotNull);
      expect(scan!.image?.path, isNotNull);
      expect(scan.image?.name, 'otto.png');

      final outDir = await _smokeOutputDir('character-image');
      final iconPath = p.join(outDir.path, 'otto-icon.png');
      final icon = await state.platform.workerBridge.generateCharacterIcon(
        CharacterIconRequest(
          imagePath: edit!.imagePath!,
          outputPath: iconPath,
          centerX: edit.cropCenterX,
          centerY: edit.cropCenterY,
          cropSize: edit.cropSize,
        ),
      );
      expect(icon.ok, isTrue, reason: icon.error);
      expect(File(iconPath).existsSync(), isTrue);

      final outputPath = p.join(outDir.path, 'otto-character.arcpkg');
      edit.defaultName = 'Otto Flutter';
      final save = await state.platform.workerBridge.saveCharacter(
        CharacterSaveRequest(
          scan: scan,
          edit: edit,
          outputPath: outputPath,
          iconPath: icon.data!.iconPath,
        ),
      );
      expect(save.ok, isTrue, reason: save.error);
      expect(File(outputPath).existsSync(), isTrue);
      expect(File(outputPath).lengthSync(), greaterThan(0));
      // ignore: avoid_print
      print('character image smoke output: $outputPath');
    },
    skip: skipReason,
  );

  test(
    'local Windows worker imports existing character arcpkg and saves',
    () async {
      final sample = File(_characterArcpkgSample);
      expect(sample.existsSync(), isTrue, reason: sample.path);

      final state = AppState.bootstrap();
      await state.scanCharacterPackage(sample.path);
      final scan = state.characterEditor.scan;
      final edit = state.characterEditor.edit;
      expect(scan, isNotNull);
      expect(edit, isNotNull);
      expect(scan!.image?.path, isNotNull);
      expect(scan.icon?.path, isNotNull);

      final outDir = await _smokeOutputDir('character-existing');
      final iconPath = p.join(outDir.path, 'existing-character-icon.png');
      final icon = await state.platform.workerBridge.generateCharacterIcon(
        CharacterIconRequest(
          imagePath: edit!.imagePath!,
          outputPath: iconPath,
          centerX: edit.cropCenterX,
          centerY: edit.cropCenterY,
          cropSize: edit.cropSize,
        ),
      );
      expect(icon.ok, isTrue, reason: icon.error);

      final outputPath = p.join(outDir.path, 'existing-character-smoke.arcpkg');
      edit.defaultName = '${edit.defaultName} Flutter';
      final save = await state.platform.workerBridge.saveCharacter(
        CharacterSaveRequest(
          scan: scan,
          edit: edit,
          outputPath: outputPath,
          iconPath: icon.data!.iconPath,
        ),
      );
      expect(save.ok, isTrue, reason: save.error);
      expect(File(outputPath).existsSync(), isTrue);
      expect(File(outputPath).lengthSync(), greaterThan(0));
      final verifyState = AppState.bootstrap();
      await verifyState.scanCharacterPackage(outputPath);
      expect(verifyState.characterEditor.scan?.identifier, scan.identifier);
      expect(verifyState.characterEditor.edit?.defaultName, edit.defaultName);
      // ignore: avoid_print
      print('character existing smoke output: $outputPath');
    },
    skip: skipReason,
  );

  test(
    'local Windows worker imports wide character arcpkg and saves',
    () async {
      final sample = File(_characterWideArcpkgSample);
      expect(sample.existsSync(), isTrue, reason: sample.path);

      final state = AppState.bootstrap();
      await state.scanCharacterPackage(sample.path);
      final scan = state.characterEditor.scan;
      final edit = state.characterEditor.edit;
      expect(scan, isNotNull);
      expect(edit, isNotNull);
      expect(scan!.image?.path, isNotNull);
      expect((scan.image?.width ?? 0) > 0, isTrue);
      expect((scan.image?.height ?? 0) > 0, isTrue);

      final outDir = await _smokeOutputDir('character-wide-existing');
      final iconPath = p.join(outDir.path, 'wide-character-icon.png');
      final icon = await state.platform.workerBridge.generateCharacterIcon(
        CharacterIconRequest(
          imagePath: edit!.imagePath!,
          outputPath: iconPath,
          centerX: edit.cropCenterX,
          centerY: edit.cropCenterY,
          cropSize: edit.cropSize,
        ),
      );
      expect(icon.ok, isTrue, reason: icon.error);

      final outputPath = p.join(outDir.path, 'wide-character-smoke.arcpkg');
      final save = await state.platform.workerBridge.saveCharacter(
        CharacterSaveRequest(
          scan: scan,
          edit: edit,
          outputPath: outputPath,
          iconPath: icon.data!.iconPath,
        ),
      );
      expect(save.ok, isTrue, reason: save.error);
      expect(File(outputPath).existsSync(), isTrue);
      expect(File(outputPath).lengthSync(), greaterThan(0));
      final verifyState = AppState.bootstrap();
      await verifyState.scanCharacterPackage(outputPath);
      expect(verifyState.characterEditor.scan?.identifier, scan.identifier);
      expect(verifyState.characterEditor.edit?.defaultName, edit.defaultName);
      // ignore: avoid_print
      print('character wide existing smoke output: $outputPath');
    },
    skip: skipReason,
  );
}

Future<Directory> _smokeOutputDir(String name) async {
  final base = Platform.environment[_localOutputDirEnv];
  if (base == null || base.trim().isEmpty) {
    final dir = await Directory.systemTemp.createTemp(
      'etoilebridge-flutter-$name-smoke-',
    );
    addTearDown(() async {
      if (await dir.exists()) await dir.delete(recursive: true);
    });
    return dir;
  }
  final dir = Directory(p.join(base, name));
  if (!await dir.exists()) await dir.create(recursive: true);
  return dir;
}

void _injectPreprocessFixtures(File affFile) {
  expect(affFile.existsSync(), isTrue, reason: affFile.path);
  final original = affFile.readAsStringSync();
  affFile.writeAsStringSync('''
$original
designant();
arc(1000,1000,0.00,1.00,s,0.00,1.00,0,none,true)[arctap(1000)];
arc(2400,1800,0.00,1.00,s,0.00,1.00,0,none,true);
timinggroup(arcresolution=24){
arc(3000,3600,0.00,1.00,s,0.00,1.00,0,none,true);
};
''');
}

Future<String> _readProjectArcproj(File arcpkg) async {
  expect(arcpkg.existsSync(), isTrue, reason: arcpkg.path);
  final script = r'''
$Path = $env:ETOILEBRIDGE_TEST_ARCPKG_PATH
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
try {
  $entry = $zip.Entries | Where-Object { $_.FullName -like '*/project.arcproj' } | Select-Object -First 1
  if ($null -eq $entry) { throw "project.arcproj not found" }
  $reader = [System.IO.StreamReader]::new($entry.Open(), [System.Text.Encoding]::UTF8)
  try { $reader.ReadToEnd() } finally { $reader.Dispose() }
} finally {
  $zip.Dispose()
}
''';
  final result = await Process.run(
    'powershell',
    ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', script],
    environment: {'ETOILEBRIDGE_TEST_ARCPKG_PATH': arcpkg.path},
  );
  expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
  return result.stdout.toString();
}
