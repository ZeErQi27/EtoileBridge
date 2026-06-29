import 'package:etoile_bridge/app/app_state.dart';
import 'package:etoile_bridge/app/etoile_bridge_app.dart';
import 'package:etoile_bridge/app/routes.dart';
import 'package:etoile_bridge/core/models/character_models.dart';
import 'package:etoile_bridge/core/models/pack_models.dart';
import 'package:etoile_bridge/core/models/single_song_models.dart';
import 'package:etoile_bridge/features/character_editor/character_result_assets.dart';
import 'package:etoile_bridge/features/character_editor/character_result_layout.dart';
import 'package:etoile_bridge/features/character_editor/character_result_preview_mapper.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Future<void> pumpAppAtSize(
  WidgetTester tester,
  AppState state,
  Size size,
) async {
  tester.view
    ..physicalSize = size
    ..devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  await tester.pumpWidget(EtoileBridgeApp(state: state));
  await tester.pumpAndSettle();
  expect(tester.takeException(), isNull);
}

void main() {
  test('single song worker JSON keeps Unicode metadata', () {
    final scan = SingleSongScanResult.fromJson({
      'sourcePath': r'E:\sample.zip',
      'sourceKind': 'official-song',
      'inputType': 'ZIP',
      'workspacePath': r'C:\cache\session\input',
      'songId': 'Twilight',
      'title': '夜明け☆♪',
      'artist': '◈Twiℓight Yūgen◈ / 中文 / 日本語 / 한글',
      'bpmText': '105-210',
      'bpmBase': 105,
      'version': 'mock-version',
      'packageDirectory': 'twilight',
      'projectFilePath': r'C:\cache\twilight\project.arcproj',
      'unexpectedField': 'kept in extra',
      'charts': [
        {
          'ratingClass': 4,
          'difficulty': 'Beyond 10+',
          'chartConstant': 10.7,
          'rating': 10,
          'ratingPlus': true,
          'charter': '◈Twiℓight Yūgen◈',
          'illustrator': '絵師☆',
          'alias': 'BYD',
          'chartPath': 'charts/4.aff',
          'affPath': '4.aff',
        },
      ],
      'affFiles': [
        {
          'ratingClass': 4,
          'path': '4.aff',
          'name': '4.aff',
          'sizeBytes': 123,
          'adopted': true,
        },
      ],
    });

    expect(scan.artist, contains('◈Twiℓight Yūgen◈'));
    expect(scan.artist, contains('한글'));
    expect(scan.charts.single.charter, '◈Twiℓight Yūgen◈');
    expect(scan.bpmText, '105-210');
    expect(scan.version, 'mock-version');
    expect(scan.packageDirectory, 'twilight');
    expect(scan.projectFilePath, contains('project.arcproj'));
    expect(scan.extra['unexpectedField'], 'kept in extra');
    expect(scan.charts.single.rating, 10);
    expect(scan.charts.single.ratingPlus, isTrue);
    expect(scan.charts.single.alias, 'BYD');
    expect(scan.charts.single.chartPath, 'charts/4.aff');
    expect(scan.charts.single.raw['chartPath'], 'charts/4.aff');
    expect(scan.affFiles.single.sizeBytes, 123);
  });

  testWidgets('shows the three primary pages', (tester) async {
    await pumpAppAtSize(tester, AppState.bootstrap(), const Size(1200, 800));

    expect(find.text('单曲转换'), findsWidgets);
    expect(find.text('曲包编辑'), findsWidgets);
    expect(find.text('搭档编辑'), findsWidgets);
  });

  testWidgets('keeps single song state while switching pages', (tester) async {
    final state = AppState.bootstrap();
    await pumpAppAtSize(tester, state, const Size(1200, 800));

    await state.mockImportSingleSong();
    state.selectPage(AppPageId.packEditor);
    await tester.pump();
    state.selectPage(AppPageId.singleSong);
    await tester.pumpAndSettle();

    expect(state.singleSong.inputPath, isNotNull);
    expect(find.text('CaiSeDeHei'), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  testWidgets('language switching does not clear page state', (tester) async {
    final state = AppState.bootstrap();
    await state.mockImportPack();
    await pumpAppAtSize(tester, state, const Size(1200, 800));

    state.setLocale(const Locale('en', 'US'));
    await tester.pumpAndSettle();

    expect(find.text('Pack Editor'), findsWidgets);
    expect(state.packEditor.levels, isNotEmpty);
    expect(tester.takeException(), isNull);
  });

  testWidgets('pack expansion and character sliders keep state across pages', (
    tester,
  ) async {
    final state = AppState.bootstrap();
    await state.mockImportPack();
    state.togglePackLevel(0);
    _seedCharacter(state);
    state.updateCharacterPosition(x: 120, y: -80, scale: 1.2);
    state.updateCharacterCrop(centerX: 0.25, centerY: 0.75, cropSize: 0.5);

    await pumpAppAtSize(tester, state, const Size(1200, 800));
    state.selectPage(AppPageId.characterEditor);
    await tester.pumpAndSettle();
    state.selectPage(AppPageId.packEditor);
    await tester.pumpAndSettle();

    expect(state.packEditor.expanded.contains(0), isTrue);
    expect(state.characterEditor.x, 120);
    expect(state.characterEditor.y, -80);
    expect(state.characterEditor.scale, 1.2);
    expect(state.characterEditor.cropCenterX, 0.25);
    expect(state.characterEditor.cropCenterY, 0.75);
    expect(state.characterEditor.cropSize, 0.5);
    expect(tester.takeException(), isNull);
  });

  testWidgets('character page keeps result canvas empty until image import', (
    tester,
  ) async {
    final state = AppState.bootstrap();
    state
      ..setLocale(const Locale('en', 'US'))
      ..selectPage(AppPageId.characterEditor);

    await pumpAppAtSize(tester, state, const Size(1200, 800));

    expect(
      find.text('Import an image or arcpkg to show the character position.'),
      findsOneWidget,
    );
    expect(find.text('Character Image'), findsNothing);

    _seedCharacter(state);
    state.selectPage(AppPageId.characterEditor);
    expect(state.characterEditor.hasPreviewImage, isTrue);
    expect(tester.takeException(), isNull);
  });

  testWidgets('common viewport sizes do not throw layout exceptions', (
    tester,
  ) async {
    const sizes = [
      Size(320, 640),
      Size(390, 844),
      Size(700, 900),
      Size(900, 600),
      Size(1200, 800),
      Size(1600, 900),
    ];

    for (final size in sizes) {
      final state = AppState.bootstrap();
      await state.mockImportSingleSong();
      await state.mockImportPack();
      _seedCharacter(state);
      await pumpAppAtSize(tester, state, size);

      for (final page in AppPageId.values) {
        state.selectPage(page);
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
      }

      await tester.tap(find.byIcon(Icons.settings_rounded).last);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.tap(find.text('关闭').last);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    }
  });

  testWidgets('resize sequence keeps the mounted page tree stable', (
    tester,
  ) async {
    final state = AppState.bootstrap();
    await state.mockImportSingleSong();
    await state.mockImportPack();
    _seedCharacter(state);

    await tester.pumpWidget(EtoileBridgeApp(state: state));
    const sizes = [
      Size(390, 844),
      Size(900, 600),
      Size(1600, 900),
      Size(700, 900),
      Size(320, 640),
      Size(1200, 800),
    ];

    for (final size in sizes) {
      tester.view
        ..physicalSize = size
        ..devicePixelRatio = 1;
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    }

    state.selectPage(AppPageId.packEditor);
    await tester.pumpAndSettle();
    state.setLocale(const Locale('en', 'US'));
    await tester.pumpAndSettle();
    expect(state.singleSong.inputPath, isNotNull);
    expect(state.packEditor.levels, isNotEmpty);
    expect(tester.takeException(), isNull);

    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
  });

  testWidgets('rapid mocked interactions do not throw', (tester) async {
    final state = AppState.bootstrap();
    await pumpAppAtSize(tester, state, const Size(1200, 800));

    for (var i = 0; i < 12; i++) {
      await tester.tap(find.text('曲包编辑').last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('搭档编辑').last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('单曲转换').last);
      await tester.pumpAndSettle();
    }

    await state.mockImportSingleSong();
    await tester.pumpAndSettle();
    await tester.tap(find.text('曲包编辑').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('模拟导入').first);
    await tester.pumpAndSettle();

    for (var i = 0; i < 8; i++) {
      await tester.ensureVisible(find.text('全部展开'));
      await tester.tap(find.text('全部展开'));
      await tester.pump();
      await tester.ensureVisible(find.text('全部收起'));
      await tester.tap(find.text('全部收起'));
      await tester.pump();
    }

    for (var i = 0; i < 10; i++) {
      final expandButtons = find.byIcon(Icons.expand_more_rounded);
      if (expandButtons.evaluate().isNotEmpty) {
        await tester.tap(expandButtons.first, warnIfMissed: false);
        await tester.pump();
      }
      final collapseButtons = find.byIcon(Icons.expand_less_rounded);
      if (collapseButtons.evaluate().isNotEmpty) {
        await tester.tap(collapseButtons.first, warnIfMissed: false);
        await tester.pump();
      }
    }

    await tester.tap(find.text('搭档编辑').last);
    await tester.pumpAndSettle();
    _seedCharacter(state);
    await tester.pumpAndSettle();
    final sliders = find.byType(Slider);
    final sliderCount = sliders.evaluate().length;
    for (var i = 0; i < sliderCount; i++) {
      final slider = sliders.at(i);
      await tester.ensureVisible(slider);
      await tester.pump();
      await tester.drag(slider, const Offset(80, 0));
      await tester.pump();
      await tester.drag(slider, const Offset(-40, 0));
      await tester.pump();
    }

    for (var i = 0; i < 5; i++) {
      await tester.tap(find.byIcon(Icons.settings_rounded).first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('English'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Simplified Chinese'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('关闭'));
      await tester.pumpAndSettle();
    }

    expect(tester.takeException(), isNull);
    expect(state.singleSong.inputPath, isNotNull);
    expect(state.packEditor.levels, isNotEmpty);
    expect(state.characterEditor.inputPath, isNotNull);
  });

  test('pack scan logs are deduplicated by source and message', () {
    final state = AppState.bootstrap();
    state.packEditor.applyScan(
      const PackScanResult(
        mode: PackEditorMode.existing,
        packName: 'ACW3',
        packId: 'ACW3',
        warnings: [
          'Cannot parse existing pack identifier, keeping original identifier: ACW3',
        ],
        logs: ['Read existing pack: 20 levels, 1 pack entries.'],
      ),
      workerLogs: const ['Read existing pack: 20 levels, 1 pack entries.'],
      warnings: const [
        'Cannot parse existing pack identifier, keeping original identifier: ACW3',
      ],
    );

    expect(
      state.packEditor.logs
          .where(
            (line) =>
                line.message ==
                'Read existing pack: 20 levels, 1 pack entries.',
          )
          .length,
      1,
    );
    expect(
      state.packEditor.logs
          .where(
            (line) =>
                line.message ==
                'Cannot parse existing pack identifier, keeping original identifier: ACW3',
          )
          .length,
      1,
    );
  });

  testWidgets('pack warning chip opens the warnings dialog', (tester) async {
    final state = AppState.bootstrap();
    state
      ..setLocale(const Locale('en', 'US'))
      ..selectPage(AppPageId.packEditor);
    state.packEditor.applyScan(
      const PackScanResult(
        mode: PackEditorMode.existing,
        packName: 'ACW3',
        packId: 'ACW3',
        warnings: [
          'Cannot parse existing pack identifier, keeping original identifier: ACW3',
        ],
      ),
    );

    await pumpAppAtSize(tester, state, const Size(1200, 800));

    await tester.tap(find.text('Warnings 1'));
    await tester.pumpAndSettle();

    expect(find.text('Pack warnings'), findsOneWidget);
    expect(
      find.text(
        'Cannot parse existing pack identifier, keeping original identifier: ACW3',
      ),
      findsAtLeastNWidgets(1),
    );
    expect(tester.takeException(), isNull);
  });

  test(
    'ArcCreate character preview mapper keeps logical coordinates stable',
    () {
      final base = CharacterPreviewCoordinateMapper.map(
        canvasWidth: 960,
        canvasHeight: 540,
        imageWidth: 1152,
        imageHeight: 2048,
        x: 0,
        y: 0,
        scale: 1,
      );
      final right = CharacterPreviewCoordinateMapper.map(
        canvasWidth: 960,
        canvasHeight: 540,
        imageWidth: 1152,
        imageHeight: 2048,
        x: 100,
        y: 0,
        scale: 1,
      );
      final up = CharacterPreviewCoordinateMapper.map(
        canvasWidth: 960,
        canvasHeight: 540,
        imageWidth: 1152,
        imageHeight: 2048,
        x: 0,
        y: 100,
        scale: 1,
      );
      final larger = CharacterPreviewCoordinateMapper.map(
        canvasWidth: 960,
        canvasHeight: 540,
        imageWidth: 1152,
        imageHeight: 2048,
        x: 0,
        y: 0,
        scale: 1.2,
      );

      expect(
        right.logicalDrawBounds.left,
        greaterThan(base.logicalDrawBounds.left),
      );
      expect(up.logicalDrawBounds.top, lessThan(base.logicalDrawBounds.top));
      expect(
        larger.logicalDrawBounds.width,
        greaterThan(base.logicalDrawBounds.width),
      );
      expect(base.intersectsCanvas, isTrue);
    },
  );

  test('ArcCreate result preview uses real result texture layers', () {
    expect(CharacterResultAssets.all, hasLength(8));
    expect(
      CharacterResultAssets.all,
      everyElement(startsWith('assets/arccreate_result/')),
    );

    final layers = CharacterResultLayout.allLayers;
    expect(layers.map((layer) => layer.assetPath).toSet(), {
      CharacterResultAssets.backgroundArrow,
      CharacterResultAssets.clearGlow,
      CharacterResultAssets.jacketBackground,
      CharacterResultAssets.scoreFrame,
      CharacterResultAssets.judgementTable,
      CharacterResultAssets.judgementTableHighlight,
      CharacterResultAssets.playRetryBackground,
      CharacterResultAssets.playRetryFrame,
    });
    expect(
      layers.where(
        (layer) => layer.phase == CharacterResultLayerPhase.background,
      ),
      hasLength(2),
    );
    expect(
      layers.where(
        (layer) => layer.phase == CharacterResultLayerPhase.foreground,
      ),
      hasLength(6),
    );
    for (final layer in layers) {
      expect(
        layer.logicalBounds.width,
        greaterThan(0),
        reason: '${layer.id} width should be positive',
      );
      expect(
        layer.logicalBounds.height,
        greaterThan(0),
        reason: '${layer.id} height should be positive',
      );
      expect(layer.source, isNotEmpty);
    }
  });
}

void _seedCharacter(AppState state) {
  const scan = CharacterScanResult(
    sourcePath: 'memory://character.png',
    sourceKind: CharacterInputKind.image,
    inputType: 'File',
    workspacePath: r'C:\cache\character-session\input',
    publisherId: 'etoilebridge',
    characterId: 'character',
    directory: 'character',
    identifier: 'etoilebridge.character',
    outputFileName: 'etoilebridge.character.arcpkg',
    defaultName: 'Character',
    imagePath: 'character.png',
    image: ResourceInfo(
      path: 'character.png',
      name: 'character.png',
      width: 1152,
      height: 2048,
    ),
    x: 300,
    y: 100,
    scale: 0.7,
  );
  state.characterEditor.inputPath = scan.sourcePath;
  state.characterEditor.applyScan(scan);
}
