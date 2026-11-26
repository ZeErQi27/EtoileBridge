import 'package:etoile_bridge/app/app_spacing.dart';
import 'package:etoile_bridge/app/app_state.dart';
import 'package:etoile_bridge/app/breakpoints.dart';
import 'package:etoile_bridge/app/etoile_bridge_app.dart';
import 'package:etoile_bridge/app/routes.dart';
import 'package:etoile_bridge/core/models/character_models.dart';
import 'package:etoile_bridge/core/models/pack_models.dart';
import 'package:etoile_bridge/core/models/single_song_models.dart';
import 'package:etoile_bridge/features/character_editor/character_result_assets.dart';
import 'package:etoile_bridge/features/character_editor/character_result_layout.dart';
import 'package:etoile_bridge/features/character_editor/character_result_preview_mapper.dart';
import 'package:etoile_bridge/shared/layout/app_layout_tokens.dart';
import 'package:etoile_bridge/shared/widgets/app_motion.dart';
import 'package:etoile_bridge/shared/widgets/pretty_ui.dart';
import 'package:etoile_bridge/shared/widgets/workspace_choices.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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

Finder workspaceCard(String title) => find.byWidgetPredicate(
  (widget) => widget is PrettyCard && widget.title == title,
);

Finder destination(AppPageId page) => find.byKey(ValueKey('nav.${page.name}'));

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

    for (final page in AppPageId.values) {
      expect(destination(page), findsOneWidget);
    }
  });

  testWidgets('a resized single song keeps import and export compact', (
    tester,
  ) async {
    const size = Size(1267, 715);
    final state = AppState.bootstrap();
    await state.mockImportSingleSong();
    await pumpAppAtSize(tester, state, size);

    final input = workspaceCard('输入');
    final save = workspaceCard('转换与保存');
    final saveButton = find.descendant(
      of: save,
      matching: find.text('保存 arcpkg'),
    );
    final preprocess = find.descendant(
      of: save,
      matching: find.byType(SwitchListTile),
    );
    final title = find.widgetWithText(TextFormField, '标题');
    final artist = find.widgetWithText(TextFormField, '曲师');

    expect(
      tester.getTopLeft(find.byType(AppPageViewport)).dx,
      closeTo(Breakpoints.railWidth, 0.01),
    );
    expect(tester.getTopLeft(input).dy, tester.getTopLeft(save).dy);
    expect(tester.getBottomRight(input).dy, tester.getBottomRight(save).dy);
    expect(tester.getBottomRight(saveButton).dy, lessThan(size.height));
    expect(tester.getTopLeft(workspaceCard('资源')).dy, lessThan(size.height));
    expect(tester.getTopLeft(title).dy, tester.getTopLeft(artist).dy);
    expect(workspaceCard('预处理选项'), findsNothing);
    expect(find.byTooltip('复制路径'), findsNothing);
    expect(preprocess, findsOneWidget);

    final initialValue = state.singleSong.preprocessOptions.deleteDesignantLine;
    await tester.tap(preprocess);
    await tester.pumpAndSettle();
    expect(
      state.singleSong.preprocessOptions.deleteDesignantLine,
      !initialValue,
    );

    tester.view.physicalSize = const Size(1920, 1080);
    await tester.pumpAndSettle();
    final chart = tester.getRect(workspaceCard('歌曲与谱面信息'));
    final resources = tester.getRect(workspaceCard('资源'));
    final diagnostics = tester.getRect(workspaceCard('扫描诊断'));
    expect(tester.getRect(save).bottom, tester.getRect(input).bottom);
    expect(
      tester.getRect(save).bottom,
      tester.getRect(workspaceCard('ArcCreate 外观')).bottom,
    );
    expect(resources.top, closeTo(chart.top, 0.01));
    expect(resources.bottom, closeTo(diagnostics.bottom, 0.01));
    expect(
      diagnostics.top,
      closeTo(chart.bottom + AppLayoutTokens.gridGap, 0.01),
    );
    expect(diagnostics.left, closeTo(chart.left, 0.01));
    expect(diagnostics.right, closeTo(chart.right, 0.01));
    final bpm = find.widgetWithText(TextFormField, '显示 BPM');
    final constant = find.widgetWithText(TextFormField, '谱面定数');
    expect(tester.getTopLeft(bpm).dy, tester.getTopLeft(constant).dy);
  });

  testWidgets('wide appearance disclosure leaves all three cards in place', (
    tester,
  ) async {
    final state = AppState.bootstrap();
    await state.mockImportSingleSong();
    await pumpAppAtSize(tester, state, const Size(1920, 1080));

    final header = find.widgetWithText(InkWell, '更多外观选项');
    final shelf = find.byKey(const ValueKey('single-appearance-shelf'));
    final chart = workspaceCard('歌曲与谱面信息');
    List<Rect> row() => [
      for (final title in ['输入', 'ArcCreate 外观', '转换与保存'])
        tester.getRect(workspaceCard(title)),
    ];

    final anchored = row();
    final collapsedY = tester.getTopLeft(chart).dy;
    expect(tester.getSize(shelf).height, 0);
    expect(
      collapsedY,
      closeTo(anchored.first.bottom + AppLayoutTokens.gridGap, 0.01),
    );

    Future<void> checkFrames({required bool opening}) async {
      await tester.tap(header);
      await tester.pump();
      var previousY = tester.getTopLeft(chart).dy;
      for (var ms = 0; ms < AppMotion.reveal.inMilliseconds; ms += 16) {
        await tester.pump(const Duration(milliseconds: 16));
        expect(row(), anchored);
        final y = tester.getTopLeft(chart).dy;
        expect(
          y,
          opening
              ? greaterThanOrEqualTo(previousY)
              : lessThanOrEqualTo(previousY),
        );
        previousY = y;
      }
    }

    await checkFrames(opening: true);
    expect(tester.getTopLeft(chart).dy, greaterThan(collapsedY));
    final options = find.descendant(
      of: shelf,
      matching: find.byType(DropdownButtonFormField<String>),
    );
    expect(options, findsNWidgets(5));
    for (var i = 1; i < 5; i++) {
      expect(
        tester.getTopLeft(options.at(i)).dy,
        tester.getTopLeft(options.first).dy,
      );
    }
    await tester.tap(options.first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Light / 光芒侧').last);
    await tester.pumpAndSettle();
    expect(state.singleSong.appearanceOptions.note, 'LIGHT');

    await checkFrames(opening: false);
    expect(tester.getSize(shelf).height, 0);
    expect(tester.getTopLeft(chart).dy, collapsedY);

    await tester.tap(header);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 80));
    final interruptedY = tester.getTopLeft(chart).dy;
    await tester.tap(header);
    await tester.pump();
    expect(row(), anchored);
    expect(tester.getTopLeft(chart).dy, closeTo(interruptedY, 0.01));
    await tester.pumpAndSettle();
    expect(tester.getTopLeft(chart).dy, collapsedY);

    await tester.tap(header);
    tester.view.physicalSize = const Size(1267, 768);
    await tester.pumpAndSettle();
    expect(shelf, findsNothing);
    final appearance = workspaceCard('ArcCreate 外观');
    final inlineOptions = find.descendant(
      of: appearance,
      matching: find.byType(DropdownButtonFormField<String>),
    );
    expect(inlineOptions, findsNWidgets(5));
    expect(
      tester.getRect(appearance).contains(tester.getCenter(inlineOptions.last)),
      isTrue,
    );
    expect(state.singleSong.appearanceOptions.note, 'LIGHT');
    await tester.ensureVisible(inlineOptions.first);
    await tester.pumpAndSettle();
    expect(
      find
          .descendant(
            of: inlineOptions.first,
            matching: find.text('Light / 光芒侧'),
          )
          .hitTestable(),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('workspace stops stretching tall neighbors without a jump', (
    tester,
  ) async {
    tester.view
      ..physicalSize = const Size(1200, 800)
      ..devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    const key = ValueKey('natural-height');
    final short = find.byKey(key);
    Future<void> layout(double height) => tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: AppWorkspaceLayout(
          bands: (_) => [
            AppWorkspaceBand([
              const AppWorkspacePanel('short', SizedBox(key: key, height: 100)),
              AppWorkspacePanel('long', SizedBox(height: height)),
            ], maxStretch: 120),
          ],
        ),
      ),
    );

    await layout(219);
    expect(tester.getSize(short).height, 219);
    await layout(221);
    expect(tester.getSize(short).height, closeTo(220, 1));
    await layout(341);
    expect(tester.getSize(short).height, 100);
  });

  testWidgets('segment contrast follows the plate and honors reduced motion', (
    tester,
  ) async {
    await pumpAppAtSize(tester, AppState.bootstrap(), const Size(1920, 1080));
    final plate = find.byKey(const ValueKey('workspace-choice-indicator'));
    final light = find.text('光芒侧');
    final colorless = find.text('消色侧');
    final colors = Theme.of(tester.element(light)).colorScheme;
    Color? colorOf(Finder label) => tester.widget<Text>(label).style!.color;

    expect(tester.getRect(plate).contains(tester.getCenter(light)), isTrue);
    await tester.tap(colorless);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 40));
    expect(
      tester.getRect(plate).contains(tester.getCenter(colorless)),
      isFalse,
    );
    expect(colorOf(colorless), colors.onSurfaceVariant);
    await tester.pumpAndSettle();
    expect(tester.getRect(plate).contains(tester.getCenter(colorless)), isTrue);
    expect(colorOf(colorless), colors.onPrimary);

    tester.platformDispatcher.accessibilityFeaturesTestValue =
        const FakeAccessibilityFeatures(disableAnimations: true);
    addTearDown(tester.platformDispatcher.clearAccessibilityFeaturesTestValue);
    await tester.pump();
    await tester.tap(light);
    await tester.pump();
    expect(tester.getRect(plate).contains(tester.getCenter(light)), isTrue);
    expect(colorOf(light), colors.onPrimary);
  });

  testWidgets('pack song content stays mounted until its fold closes', (
    tester,
  ) async {
    final state = AppState.bootstrap()..selectPage(AppPageId.packEditor);
    await state.mockImportPack();
    await pumpAppAtSize(tester, state, const Size(1366, 768));

    final open = find.byTooltip('展开曲目').first;
    final close = find.byTooltip('收起曲目').first;
    final editor = find.byKey(const ValueKey('pack-pretty-title'));
    await tester.ensureVisible(open);
    await tester.tap(open);
    await tester.pumpAndSettle();
    final fieldState = tester.state<FormFieldState<String>>(editor);
    final body = find.ancestor(
      of: editor,
      matching: find.byType(SizeTransition),
    );
    final height = tester.getSize(body).height;

    await tester.tap(close);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 155));
    expect(tester.getSize(body).height, inExclusiveRange(0, height));
    expect(tester.state<FormFieldState<String>>(editor), same(fieldState));
    await tester.tap(open);
    await tester.pumpAndSettle();
    expect(tester.state<FormFieldState<String>>(editor), same(fieldState));
    await tester.tap(close);
    await tester.pumpAndSettle();
    expect(editor, findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('expanded metadata labels clear their header and stay editable', (
    tester,
  ) async {
    final state = AppState.bootstrap();
    await state.mockImportSingleSong();
    await pumpAppAtSize(tester, state, const Size(1920, 1080));

    final header = find.widgetWithText(InkWell, '高级信息');
    await tester.tap(header);
    await tester.pumpAndSettle();

    final publisher = find.widgetWithText(TextFormField, '发布者 ID');
    for (final label in ['发布者 ID', '单曲 ID']) {
      expect(
        tester.getTopLeft(find.text(label)).dy,
        greaterThan(tester.getBottomLeft(header).dy),
      );
    }
    expect(
      tester.getRect(workspaceCard('资源')).bottom,
      closeTo(tester.getRect(workspaceCard('扫描诊断')).bottom, 0.01),
    );

    await tester.enterText(publisher, 'newpublisher');
    await tester.pump();
    expect(state.singleSong.edit!.publisherId, 'newpublisher');
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'desktop workspace responds to wheel and scrollbar drag',
    (tester) async {
      final state = AppState.bootstrap();
      await state.mockImportSingleSong();
      await pumpAppAtSize(tester, state, const Size(1267, 500));

      final scrollbar = tester.widget<Scrollbar>(find.byType(Scrollbar));
      final controller = scrollbar.controller!;
      final viewport = find.byWidgetPredicate(
        (widget) =>
            widget is SingleChildScrollView && widget.controller == controller,
      );
      expect(scrollbar.thumbVisibility, isTrue);
      expect(controller.position.maxScrollExtent, greaterThan(0));
      await tester.sendEventToBinding(
        PointerScrollEvent(
          position: tester.getCenter(workspaceCard('输入')),
          scrollDelta: const Offset(0, 240),
        ),
      );
      await tester.pumpAndSettle();
      expect(controller.offset, greaterThan(0));

      controller.jumpTo(0);
      await tester.pumpAndSettle();
      await tester.dragFrom(
        tester.getTopRight(viewport) + const Offset(-4, 32),
        const Offset(0, 150),
        kind: PointerDeviceKind.mouse,
      );
      await tester.pumpAndSettle();
      final offset = controller.offset;
      expect(offset, greaterThan(0));

      state.selectPage(AppPageId.packEditor);
      await tester.pumpAndSettle();
      final pack = tester.widget<Scrollbar>(find.byType(Scrollbar));
      expect(pack.controller, isNot(same(controller)));
      state.selectPage(AppPageId.singleSong);
      await tester.pumpAndSettle();
      expect(controller.offset, offset);
      expect(tester.takeException(), isNull);
    },
    variant: TargetPlatformVariant.only(TargetPlatform.windows),
  );

  testWidgets('pack modes stay in one row and editing the name keeps focus', (
    tester,
  ) async {
    final state = AppState.bootstrap()..selectPage(AppPageId.packEditor);
    await pumpAppAtSize(tester, state, const Size(320, 640));
    final group = find.byKey(const ValueKey('pack-mode'));
    final official = find.descendant(of: group, matching: find.text('官方曲包'));
    final bundle = find.descendant(of: group, matching: find.text('合并打包'));
    final existing = find.descendant(of: group, matching: find.text('编辑曲包'));
    expect(tester.getCenter(official).dy, tester.getCenter(bundle).dy);
    expect(tester.getCenter(bundle).dy, tester.getCenter(existing).dy);
    expect(
      tester.getCenter(official).dx,
      lessThan(tester.getCenter(existing).dx),
    );
    await tester.tap(existing);
    await tester.pumpAndSettle();
    expect(
      tester.widget<WorkspaceChoices<PackEditorMode>>(group).value,
      PackEditorMode.existing,
    );
    expect(find.text('可以保存'), findsNothing);

    final nameField = find.widgetWithText(TextFormField, '曲包名称');
    await tester.ensureVisible(nameField);
    await tester.enterText(nameField, 'Draft');
    await tester.pump();
    final textInput = find.descendant(
      of: nameField,
      matching: find.byType(EditableText),
    );
    expect(tester.widget<EditableText>(textInput).focusNode.hasFocus, isTrue);
    tester.testTextInput.enterText('Draft Pack');
    await tester.pump();
    expect(state.packEditor.packName, 'Draft Pack');

    final editor = tester.state<EditableTextState>(textInput);
    for (final size in [
      const Size(1920, 1080),
      const Size(1366, 768),
      const Size(390, 844),
    ]) {
      tester.view.physicalSize = size;
      await tester.pump();
      expect(tester.state<EditableTextState>(textInput), same(editor));
      expect(editor.widget.controller.text, 'Draft Pack');
    }
  });

  testWidgets('pack bands share widths, edges, gutters and card corners', (
    tester,
  ) async {
    final state = AppState.bootstrap()..selectPage(AppPageId.packEditor);
    await pumpAppAtSize(tester, state, const Size(1920, 1080));
    Rect card(String title) => tester.getRect(workspaceCard(title));
    void aligned(Rect a, Rect b) {
      expect(b.top, closeTo(a.top, 0.01));
      expect(b.bottom, closeTo(a.bottom, 0.01));
    }

    final input = card('输入');
    final metadata = card('曲包设置');
    final cover = card('曲包封面');
    final save = card('转换与保存');
    final preprocess = find.descendant(
      of: workspaceCard('转换与保存'),
      matching: find.byType(SwitchListTile),
    );
    final diagnostics = card('扫描诊断');
    final list = card('曲目列表');
    final selected = card('选中曲目');
    const gap = AppLayoutTokens.gridGap;

    aligned(input, metadata);
    aligned(input, cover);
    expect(save.top, closeTo(diagnostics.top, 0.01));
    aligned(list, selected);
    expect(metadata.width, closeTo(input.width, 0.01));
    expect(cover.width, closeTo(input.width, 0.01));
    expect(save.left, closeTo(input.left, 0.01));
    expect(save.right, closeTo(metadata.right, 0.01));
    expect(workspaceCard('预处理选项'), findsNothing);
    expect(preprocess, findsOneWidget);
    expect(diagnostics.left, closeTo(cover.left, 0.01));
    expect(metadata.left - input.right, closeTo(gap, 0.01));
    expect(save.top - input.bottom, closeTo(gap, 0.01));
    expect(list.width, closeTo(input.width * 2 + gap, 0.01));
    expect(selected.left, closeTo(cover.left, 0.01));
    expect(
      tester
          .widgetList<WorkspaceSurface>(find.byType(WorkspaceSurface))
          .map((surface) => surface.radius)
          .toSet(),
      {AppRadius.workspaceCard},
    );

    state.setPackMode(PackEditorMode.existing);
    await tester.pumpAndSettle();
    final wideSave = card('转换与保存');
    expect(wideSave.right, closeTo(metadata.right, 0.01));
    expect(wideSave.top, closeTo(card('扫描诊断').top, 0.01));
    expect(preprocess, findsNothing);

    tester.view.physicalSize = const Size(1366, 768);
    await tester.pumpAndSettle();
    aligned(card('输入'), card('曲包设置'));
    expect(card('转换与保存').top, closeTo(card('曲包封面').top, 0.01));
    expect(card('曲包设置').width, closeTo(card('输入').width, 0.01));
  });

  testWidgets('workspace titles and first cards align on all three pages', (
    tester,
  ) async {
    final state = AppState.bootstrap();
    await pumpAppAtSize(tester, state, const Size(1920, 1080));
    final title = find.byKey(const ValueKey('workspace-title'));
    final firstCard = find.byType(PrettyCard).first;
    for (final size in [
      const Size(1920, 1080),
      const Size(1366, 768),
      const Size(390, 844),
    ]) {
      tester.view.physicalSize = size;
      state.selectPage(AppPageId.singleSong);
      await tester.pumpAndSettle();
      final titleOrigin = tester.getTopLeft(title);
      final cardOrigin = tester.getTopLeft(firstCard);
      final cardWidth = tester.getSize(firstCard).width;
      for (final page in [AppPageId.packEditor, AppPageId.characterEditor]) {
        state.selectPage(page);
        await tester.pumpAndSettle();
        expect(tester.getTopLeft(title), titleOrigin);
        expect(tester.getTopLeft(firstCard), cardOrigin);
        expect(tester.getSize(firstCard).width, cardWidth);
      }
    }
  });

  testWidgets(
    'sidebar and content animate and can be retargeted before settling',
    (tester) async {
      final state = AppState.bootstrap();
      await pumpAppAtSize(tester, state, const Size(1366, 768));
      final indicator = find.byKey(const ValueKey('rail-selection'));
      final initialY = tester.getTopLeft(indicator).dy;
      final firstPage = find.byWidgetPredicate(
        (widget) => widget is Offstage && widget.key == const ValueKey<int>(0),
      );

      state.selectPage(AppPageId.packEditor);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 55));
      expect(
        tester.getTopLeft(indicator).dy,
        inExclusiveRange(initialY, initialY + 52),
      );
      expect(tester.widget<Offstage>(firstPage).offstage, isFalse);

      state.selectPage(AppPageId.characterEditor);
      await tester.pump();
      state.selectPage(AppPageId.singleSong);
      await tester.pumpAndSettle();
      expect(tester.getTopLeft(indicator).dy, closeTo(initialY, 0.1));
      expect(find.byKey(const ValueKey('workspace-title')), findsOneWidget);
      expect(state.currentPage, AppPageId.singleSong);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'reduced motion switches immediately and resize keeps scrollable state',
    (tester) async {
      tester.platformDispatcher.accessibilityFeaturesTestValue =
          const FakeAccessibilityFeatures(disableAnimations: true);
      addTearDown(
        tester.platformDispatcher.clearAccessibilityFeaturesTestValue,
      );
      final state = AppState.bootstrap();
      await pumpAppAtSize(tester, state, const Size(1366, 768));
      state.selectPage(AppPageId.packEditor);
      await tester.pump();
      expect(find.byKey(const ValueKey('workspace-title')), findsOneWidget);
      final viewport = tester.element(find.byType(AppPageViewport));
      final scrollView = find.byType(SingleChildScrollView).first;
      final scrollElement = tester.element(scrollView);
      tester.view.physicalSize = const Size(390, 844);
      await tester.pumpAndSettle();
      expect(tester.element(find.byType(AppPageViewport)), same(viewport));
      expect(tester.element(scrollView), same(scrollElement));
      expect(state.currentPage, AppPageId.packEditor);
    },
  );

  testWidgets(
    'Android wallpaper theme follows system brightness without resetting the page',
    (tester) async {
      const channel = MethodChannel('com.zeerqi27.etoile_bridge/theme');
      final messenger = tester.binding.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => {'primary': 0xFF725687, 'secondary': 0xFF49675B},
      );
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
      addTearDown(tester.platformDispatcher.clearPlatformBrightnessTestValue);
      final state = AppState.bootstrap()..selectPage(AppPageId.characterEditor);
      await pumpAppAtSize(tester, state, const Size(390, 844));
      final title = find.byKey(const ValueKey('workspace-title'));
      final light = Theme.of(tester.element(title)).colorScheme;
      expect(light.brightness, Brightness.light);
      tester.platformDispatcher.platformBrightnessTestValue = Brightness.dark;
      await tester.pumpAndSettle();
      final dark = Theme.of(tester.element(title)).colorScheme;
      expect(dark.brightness, Brightness.dark);
      expect(dark.primary, isNot(light.primary));
      expect(dark.secondary, isNot(dark.primary));
      expect(state.currentPage, AppPageId.characterEditor);
      expect(tester.takeException(), isNull);
    },
    variant: TargetPlatformVariant.only(TargetPlatform.android),
  );

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

    expect(find.byTooltip('Pack Editor'), findsOneWidget);
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

      await tester.tap(find.byTooltip('设置').first);
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
      await tester.tap(destination(AppPageId.packEditor));
      await tester.pumpAndSettle();
      await tester.tap(destination(AppPageId.characterEditor));
      await tester.pumpAndSettle();
      await tester.tap(destination(AppPageId.singleSong));
      await tester.pumpAndSettle();
    }

    await state.mockImportSingleSong();
    await tester.pumpAndSettle();
    await tester.tap(destination(AppPageId.packEditor));
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

    await tester.tap(destination(AppPageId.characterEditor));
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
      await tester.tap(find.byTooltip('设置').first);
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
