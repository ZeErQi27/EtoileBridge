import 'package:etoile_bridge/core/models/conversion_options.dart';
import 'package:etoile_bridge/core/models/difficulty_display.dart';
import 'package:etoile_bridge/core/models/pack_models.dart';
import 'package:etoile_bridge/core/models/single_song_models.dart';
import 'package:etoile_bridge/core/update/update_checker.dart';
import 'package:etoile_bridge/features/single_song/single_song_state.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('single save request serializes appearance and preprocess options', () {
    final scan = _singleScan();
    final edit = SingleSongEditState.fromScan(scan);
    final request = SingleSongSaveRequest(
      scan: scan,
      edit: edit,
      outputPath: r'C:\out\song.arcpkg',
      appearance: const ArcCreateAppearanceOptions(
        side: 'CONFLICT',
        note: 'LIGHT',
        particle: 'MIRAI_LIGHT',
        accent: 'DYNAMIX',
        track: 'TEMPEST',
        singleLine: 'NEO',
      ),
      preprocess: const PreprocessOptions(
        deleteDesignantLine: false,
        fixZeroDurationArcTap: true,
        fixReversedArcTime: false,
        expandArcResolution: true,
      ),
    );

    final json = request.toWorkerRequestJson();
    expect(json['appearance'], {
      'side': 'CONFLICT',
      'note': 'LIGHT',
      'particle': 'MIRAI_LIGHT',
      'accent': 'DYNAMIX',
      'track': 'TEMPEST',
      'singleLine': 'NEO',
    });
    expect(json['preprocess'], {
      'deleteDesignantLine': false,
      'fixZeroDurationArcTap': true,
      'fixReversedArcTime': false,
      'expandArcResolution': true,
    });
  });

  test(
    'side appearance defaults to light and exposes only concrete choices',
    () {
      expect(const ArcCreateAppearanceOptions().side, 'LIGHT');
      expect(sideAppearanceChoices.map((choice) => choice.value), [
        'LIGHT',
        'CONFLICT',
        'COLORLESS',
      ]);
      expect(
        sideAppearanceChoices.map((choice) => choice.value),
        isNot(contains('AUTO')),
      );
      expect(
        sideAppearanceChoices.map((choice) => choice.value),
        isNot(contains('INHERIT')),
      );
    },
  );

  test('update version comparison handles v prefix and prerelease tags', () {
    expect(compareVersions('v1.3.26626', '1.3.26626-dev'), greaterThan(0));
    expect(compareVersions('v1.3.26626', 'v1.3.26626'), 0);
    expect(compareVersions('v1.3.26627', 'v1.3.26626'), greaterThan(0));
    expect(compareVersions('v1.3.26625', 'v1.3.26626'), lessThan(0));
    expect(compareVersions('not-a-version', 'v1.3.26626'), isNull);
  });

  test('single scan can apply inferred side until user edits appearance', () {
    final state = SingleSongState();
    final scan = _singleScan();

    state.applyScan(
      scan,
      inferredAppearance: const ArcCreateAppearanceOptions(side: 'CONFLICT'),
    );
    expect(state.appearanceOptions.side, 'CONFLICT');
    expect(state.appearanceEdited, isFalse);

    state.appearanceEdited = true;
    state.appearanceOptions = state.appearanceOptions.copyWith(side: 'LIGHT');
    state.applyScan(
      scan,
      inferredAppearance: const ArcCreateAppearanceOptions(side: 'COLORLESS'),
    );
    expect(state.appearanceOptions.side, 'LIGHT');

    state.startScanning(r'C:\next.zip', r'C:\cache\next');
    expect(state.appearanceOptions.side, 'LIGHT');
    expect(state.appearanceEdited, isFalse);
  });

  test('selected chart resources follow audio jacket and bg overrides', () {
    final state = SingleSongState();
    state.applyScan(_multiChartScan());

    expect(state.effectiveResources.audio?.name, 'base.ogg');
    expect(state.effectiveResources.jacket?.name, 'base.jpg');
    expect(state.effectiveResources.background?.name, 'epilogue.jpg');

    state.selectChart(1);
    final resources = state.effectiveResources;
    expect(resources.audio?.name, '3.ogg');
    expect(resources.jacket?.name, '3.jpg');
    expect(resources.background?.name, 'woyouyyz.jpg');
    expect(resources.audioSourceKey, 'resource.chartOverride');
    expect(resources.jacketSourceKey, 'resource.chartOverride');
    expect(resources.backgroundSourceKey, 'resource.chartBackground');
  });

  test('missing bg reference can be replaced by external background', () {
    final state = SingleSongState();
    state.applyScan(_missingBgScan());

    expect(state.effectiveResources.background, isNull);
    expect(state.effectiveResources.missingBackgroundReference, 'epilogue');
    expect(
      state.effectiveResources.backgroundSourceKey,
      'resource.missingDefaultSide',
    );

    state.setExternalBackgroundForSelectedChart(
      path: r'C:\workspace\single\external-backgrounds\external_bg_3.png',
      name: 'external_bg_3.png',
      bgStem: 'external_bg_3',
    );
    expect(state.effectiveResources.background?.name, 'external_bg_3.png');
    expect(
      state.effectiveResources.backgroundSourceKey,
      'resource.externalImported',
    );

    final request = SingleSongSaveRequest(
      scan: state.scan!,
      edit: state.edit!,
      outputPath: r'C:\out\song.arcpkg',
    ).toWorkerRequestJson();
    final charts = request['charts']! as List<Object?>;
    final chart = charts.single as Map<String, Object?>;
    expect(chart['externalBackgroundStem'], 'external_bg_3');
  });

  test('pack save request serializes shared preprocess options', () {
    final scan = PackScanResult.fromJson({
      'mode': 'bundle',
      'workspacePath': r'C:\workspace\pack',
      'packName': 'Pack',
      'packId': 'pack',
      'entries': <Object?>[],
    });
    final request = PackSaveRequest(
      scan: scan,
      outputPath: r'C:\out\pack.arcpkg',
      packName: 'Pack',
      packId: 'pack',
      entries: const [],
      preprocess: const PreprocessOptions(
        deleteDesignantLine: true,
        fixZeroDurationArcTap: false,
        fixReversedArcTime: true,
        expandArcResolution: false,
      ),
    );

    final json = request.toWorkerRequestJson();
    expect(json['preprocess'], {
      'deleteDesignantLine': true,
      'fixZeroDurationArcTap': false,
      'fixReversedArcTime': true,
      'expandArcResolution': false,
    });
  });

  test('existing pack save request disables preprocess options', () {
    final scan = PackScanResult.fromJson({
      'mode': 'existing',
      'workspacePath': r'C:\workspace\pack',
      'packName': 'Pack',
      'packId': 'pack',
      'entries': <Object?>[],
    });
    final request = PackSaveRequest(
      scan: scan,
      outputPath: r'C:\out\pack.arcpkg',
      packName: 'Pack',
      packId: 'pack',
      entries: const [],
      preprocess: const PreprocessOptions(
        deleteDesignantLine: true,
        fixZeroDurationArcTap: true,
        fixReversedArcTime: true,
        expandArcResolution: true,
      ),
    );

    final json = request.toWorkerRequestJson();
    expect(json['preprocess'], {
      'deleteDesignantLine': false,
      'fixZeroDurationArcTap': false,
      'fixReversedArcTime': false,
      'expandArcResolution': false,
    });
  });

  test('pack save request serializes disabled chart choices', () {
    final scan = PackScanResult.fromJson({
      'mode': 'bundle',
      'workspacePath': r'C:\workspace\pack',
      'packName': 'Pack',
      'packId': 'pack',
      'entries': <Object?>[],
    });
    final request = PackSaveRequest(
      scan: scan,
      outputPath: r'C:\out\pack.arcpkg',
      packName: 'Pack',
      packId: 'pack',
      entries: [
        PackSongEntry(
          key: 'song',
          levelId: 'song',
          enabled: true,
          charts: [
            PackChartEntry(ratingClass: 2, chartPath: '2.aff', enabled: true),
            PackChartEntry(ratingClass: 3, chartPath: '3.aff', enabled: false),
          ],
        ),
      ],
    );

    final json = request.toWorkerRequestJson();
    final entries = json['entries']! as List<Object?>;
    final entry = entries.single as Map<String, Object?>;
    final charts = entry['charts']! as List<Object?>;
    expect(entry['enabled'], isTrue);
    expect((charts.first as Map<String, Object?>)['enabled'], isTrue);
    expect((charts.last as Map<String, Object?>)['enabled'], isFalse);
  });

  test('question ratings normalize without metadata fallback warning', () {
    final cases = <Object?>[0, -1, null];
    for (final rating in cases) {
      final scan = SingleSongScanResult.fromJson({
        'sourcePath': r'C:\input\question.zip',
        'inputType': 'zip',
        'workspacePath': r'C:\workspace\question',
        'songId': 'question',
        'title': 'Question',
        'artist': 'Composer',
        'charts': [
          {
            'ratingClass': 2,
            'difficulty': 'Future',
            ...rating == null
                ? const <String, Object?>{}
                : <String, Object?>{'rating': rating},
            'ratingPlus': false,
            'affPath': r'C:\workspace\question\2.aff',
            'affName': '2.aff',
          },
        ],
        'affFiles': [
          {
            'ratingClass': 2,
            'path': r'C:\workspace\question\2.aff',
            'name': '2.aff',
            'adopted': true,
          },
        ],
      });

      expect(scan.charts.single.isQuestionRating, isTrue);
      expect(scan.charts.single.difficulty, 'Future ?');
      expect(scan.charts.single.chartConstant, 0);
      expect(
        scan.warnings,
        isNot(
          contains('Recovered missing single-song metadata with fallback.'),
        ),
      );
    }
  });

  test('normal rating and rating plus keep existing display rules', () {
    final display = DifficultyDisplay.resolve(
      ratingClass: 4,
      rating: 10,
      ratingPlus: true,
    );
    expect(display.isQuestionRating, isFalse);
    expect(display.name, 'Eternal 10+');
    expect(display.chartConstant, 10.7);
  });

  test('pack chart question rating display is normalized', () {
    final chart = PackChartEntry.fromJson({
      'ratingClass': 3,
      'difficulty': 'Beyond ?',
      'chartPath': '3.aff',
    });

    expect(chart.difficulty, 'Beyond ?');
    expect(chart.chartConstant, 0);
  });
}

SingleSongScanResult _singleScan() {
  return const SingleSongScanResult(
    sourcePath: r'C:\input\song.zip',
    inputType: SingleInputType.zip,
    workspacePath: r'C:\workspace\single',
    songId: 'song',
    title: 'Song',
    artist: 'Composer',
    bpmText: '128',
    bpmBase: 128,
    charts: [
      ChartMetadata(
        ratingClass: 2,
        difficulty: 'Future 9',
        chartConstant: 9,
        affPath: r'C:\workspace\single\2.aff',
        affName: '2.aff',
      ),
    ],
    affFiles: [
      AffInfo(
        ratingClass: 2,
        path: r'C:\workspace\single\2.aff',
        name: '2.aff',
        adopted: true,
      ),
    ],
  );
}

SingleSongScanResult _multiChartScan() {
  return const SingleSongScanResult(
    sourcePath: r'C:\input\multi.zip',
    inputType: SingleInputType.zip,
    workspacePath: r'C:\workspace\single',
    songId: 'multi',
    title: 'Multi',
    artist: 'Composer',
    bpmText: '160',
    bpmBase: 160,
    audio: ResourceInfo(name: 'base.ogg', source: 'Detected audio'),
    jacket: ResourceInfo(name: 'base.jpg', source: 'Detected jacket'),
    background: ResourceInfo(
      name: 'epilogue.jpg',
      source: 'Detected background',
    ),
    charts: [
      ChartMetadata(
        ratingClass: 2,
        difficulty: 'Future 9',
        affPath: r'C:\workspace\single\2.aff',
        affName: '2.aff',
        audio: ResourceInfo(name: 'base.ogg', source: 'Detected audio'),
        jacket: ResourceInfo(name: 'base.jpg', source: 'Detected jacket'),
        background: ResourceInfo(
          name: 'epilogue.jpg',
          source: 'Detected background',
        ),
      ),
      ChartMetadata(
        ratingClass: 3,
        difficulty: 'Beyond 10',
        affPath: r'C:\workspace\single\3.aff',
        affName: '3.aff',
        audio: ResourceInfo(name: '3.ogg', source: 'Chart audio override'),
        jacket: ResourceInfo(name: '3.jpg', source: 'Chart jacket override'),
        background: ResourceInfo(
          name: 'woyouyyz.jpg',
          source: 'Chart background',
        ),
        audioOverride: true,
        jacketOverride: true,
        bgReference: 'woyouyyz',
        bgOverride: true,
      ),
    ],
    affFiles: [
      AffInfo(
        ratingClass: 2,
        path: r'C:\workspace\single\2.aff',
        name: '2.aff',
        adopted: true,
      ),
      AffInfo(
        ratingClass: 3,
        path: r'C:\workspace\single\3.aff',
        name: '3.aff',
        adopted: true,
      ),
    ],
  );
}

SingleSongScanResult _missingBgScan() {
  return const SingleSongScanResult(
    sourcePath: r'C:\input\missing-bg.zip',
    inputType: SingleInputType.zip,
    workspacePath: r'C:\workspace\single',
    songId: 'missingbg',
    title: 'Missing BG',
    artist: 'Composer',
    bpmText: '160',
    bpmBase: 160,
    audio: ResourceInfo(name: 'base.ogg', source: 'Detected audio'),
    jacket: ResourceInfo(name: 'base.jpg', source: 'Detected jacket'),
    charts: [
      ChartMetadata(
        ratingClass: 3,
        difficulty: 'Beyond 10',
        affPath: r'C:\workspace\single\3.aff',
        affName: '3.aff',
        audio: ResourceInfo(name: '3.ogg', source: 'Chart audio override'),
        jacket: ResourceInfo(name: '3.jpg', source: 'Chart jacket override'),
        audioOverride: true,
        jacketOverride: true,
        bgReference: 'epilogue',
        bgOverride: true,
        missingBackgroundReference: true,
        resourceWarnings: ['Background reference not found: epilogue'],
      ),
    ],
    affFiles: [
      AffInfo(
        ratingClass: 3,
        path: r'C:\workspace\single\3.aff',
        name: '3.aff',
        adopted: true,
      ),
    ],
  );
}
