import 'dart:io';

import 'package:path/path.dart' as p;

import '../../core/models/operation_models.dart';
import '../../core/models/conversion_options.dart';
import '../../core/models/single_song_models.dart';

class EffectiveChartResources {
  const EffectiveChartResources({
    this.audio,
    this.jacket,
    this.background,
    this.songlist,
    this.audioSourceKey = 'resource.default',
    this.jacketSourceKey = 'resource.default',
    this.backgroundSourceKey = 'resource.default',
    this.songlistSourceKey = 'resource.default',
    this.missingBackgroundReference,
    this.warnings = const [],
  });

  final ResourceInfo? audio;
  final ResourceInfo? jacket;
  final ResourceInfo? background;
  final ResourceInfo? songlist;
  final String audioSourceKey;
  final String jacketSourceKey;
  final String backgroundSourceKey;
  final String songlistSourceKey;
  final String? missingBackgroundReference;
  final List<String> warnings;
}

class SingleSongState {
  String? inputPath;
  String? workspacePath;
  OperationPhase phase = OperationPhase.idle;
  SingleSongScanResult? scan;
  SingleSongEditState? edit;
  SingleSongSaveResult? saveResult;
  String? error;
  String? lastSaveDirectory;
  String? scanRawJsonPath;
  String? scanDiagnosticsPath;
  List<String> scanDiagnostics = const [];
  int selectedChartIndex = 0;
  ArcCreateAppearanceOptions appearanceOptions =
      const ArcCreateAppearanceOptions();
  bool appearanceEdited = false;
  PreprocessOptions preprocessOptions = const PreprocessOptions();
  final List<LogLine> logs = [];

  String get songId => scan?.songId ?? edit?.levelId ?? '-';
  String get title => edit?.title ?? scan?.title ?? '-';
  String get composer => edit?.artist ?? scan?.artist ?? '-';
  String get baseBpm => edit?.bpmBase ?? scan?.bpmBase?.toString() ?? '-';
  String get bpmText => edit?.bpmText ?? scan?.bpmText ?? '-';

  List<MockResource> get resources {
    final current = scan;
    if (current == null) return const [];
    return [
      MockResource(
        label: 'audio',
        fileName: current.audio?.name ?? '-',
        statusKey: current.audio == null
            ? 'resource.missing'
            : 'resource.identified',
      ),
      MockResource(
        label: 'jacket',
        fileName: current.jacket?.name ?? '-',
        statusKey: current.jacket == null
            ? 'resource.missing'
            : 'resource.identified',
      ),
      MockResource(
        label: 'background',
        fileName: current.background?.name ?? '-',
        statusKey: current.background == null
            ? 'resource.missing'
            : 'resource.identified',
      ),
    ];
  }

  void startScanning(String source, String sessionPath) {
    inputPath = source;
    workspacePath = sessionPath;
    scan = null;
    edit = null;
    saveResult = null;
    error = null;
    scanRawJsonPath = null;
    scanDiagnosticsPath = null;
    scanDiagnostics = const [];
    selectedChartIndex = 0;
    appearanceOptions = const ArcCreateAppearanceOptions();
    appearanceEdited = false;
    phase = OperationPhase.scanning;
    logs
      ..clear()
      ..add(LogLine('scan started: $source'));
  }

  void applyScan(
    SingleSongScanResult result, {
    List<String> warnings = const [],
    List<String> workerLogs = const [],
    String? rawJsonPath,
    String? diagnosticsPath,
    List<String> diagnostics = const [],
    ArcCreateAppearanceOptions? inferredAppearance,
  }) {
    scan = result;
    edit = SingleSongEditState.fromScan(result);
    inputPath = result.sourcePath;
    workspacePath = result.workspacePath;
    scanRawJsonPath = rawJsonPath;
    scanDiagnosticsPath = diagnosticsPath;
    scanDiagnostics = diagnostics;
    selectedChartIndex = 0;
    if (!appearanceEdited && inferredAppearance != null) {
      appearanceOptions = inferredAppearance;
    }
    phase = OperationPhase.ready;
    error = null;
    logs
      ..clear()
      ..addAll(workerLogs.map((line) => LogLine(line)))
      ..addAll(result.logs.map((line) => LogLine(line)))
      ..addAll(result.warnings.map((line) => LogLine(line, isWarning: true)))
      ..addAll(warnings.map((line) => LogLine(line, isWarning: true)));
  }

  void fail(String message, {List<String> workerLogs = const []}) {
    error = message;
    phase = OperationPhase.failed;
    logs
      ..add(LogLine(message, isError: true))
      ..addAll(workerLogs.map((line) => LogLine(line)));
  }

  void startSaving() {
    phase = OperationPhase.saving;
    error = null;
    logs.add(const LogLine('save started'));
  }

  void applySave(
    SingleSongSaveResult result, {
    List<String> warnings = const [],
    List<String> workerLogs = const [],
  }) {
    saveResult = result;
    phase = OperationPhase.saved;
    error = null;
    logs
      ..add(LogLine('save success: ${result.outputPath}'))
      ..addAll(workerLogs.map((line) => LogLine(line)))
      ..addAll(warnings.map((line) => LogLine(line, isWarning: true)));
  }

  void mockImport() {
    final result = SingleSongScanResult(
      sourcePath: 'mock://samples/test_song/彩色的黑.zip',
      inputType: SingleInputType.zip,
      workspacePath: 'mock://cache/session-single-song',
      songId: 'CaiSeDeHei',
      title: '彩色的黑',
      artist: '吉克隽逸',
      bpmText: '105',
      bpmBase: 105,
      charts: const [
        ChartMetadata(
          ratingClass: 4,
          difficulty: 'Eternal 10',
          chartConstant: 10,
          charter: 'mock charter',
          illustrator: 'mock illustrator',
          affPath: '4.aff',
          affName: '4.aff',
        ),
      ],
      audio: const ResourceInfo(name: 'base.ogg', source: 'mock'),
      jacket: const ResourceInfo(name: '1080_base.jpg', source: 'mock'),
      background: const ResourceInfo(name: 'kaguya.jpg', source: 'mock'),
      affFiles: const [
        AffInfo(ratingClass: 4, path: '4.aff', name: '4.aff', adopted: true),
      ],
      logs: const ['mock scan completed'],
    );
    applyScan(result);
  }

  ChartEditState? get selectedChart {
    final charts = edit?.charts;
    if (charts == null || charts.isEmpty) return null;
    final index = selectedChartIndex.clamp(0, charts.length - 1).toInt();
    if (index != selectedChartIndex) selectedChartIndex = index;
    return charts[index];
  }

  ChartMetadata? get selectedChartMetadata {
    final selected = selectedChart;
    final all = scan?.charts;
    if (selected == null || all == null || all.isEmpty) return null;
    return all.firstWhere(
      (chart) => chart.ratingClass == selected.ratingClass,
      orElse: () => all[selectedChartIndex.clamp(0, all.length - 1).toInt()],
    );
  }

  EffectiveChartResources get effectiveResources {
    final current = scan;
    final chart = selectedChartMetadata;
    final editChart = selectedChart;
    if (current == null) return const EffectiveChartResources();
    final externalBackground = _externalBackgroundResource(editChart);
    final missingBg = chart?.missingBackgroundReference == true
        ? chart?.bgReference
        : null;
    final background =
        externalBackground ??
        (missingBg == null ? (chart?.background ?? current.background) : null);
    return EffectiveChartResources(
      audio: chart?.audio ?? current.audio,
      jacket: chart?.jacket ?? current.jacket,
      background: background,
      songlist: current.songlist,
      audioSourceKey: chart?.audioOverride == true
          ? 'resource.chartOverride'
          : 'resource.default',
      jacketSourceKey: chart?.jacketOverride == true
          ? 'resource.chartOverride'
          : 'resource.default',
      backgroundSourceKey: externalBackground != null
          ? 'resource.externalImported'
          : missingBg != null
          ? 'resource.missingDefaultSide'
          : chart?.bgOverride == true
          ? 'resource.chartBackground'
          : 'resource.default',
      songlistSourceKey: 'resource.currentChart',
      missingBackgroundReference: missingBg,
      warnings: [
        ...?chart?.resourceWarnings,
        if (missingBg != null)
          'Missing background reference "$missingBg"; default side background will be used unless an external background is selected.',
      ],
    );
  }

  void setExternalBackgroundForSelectedChart({
    required String path,
    required String name,
    required String bgStem,
  }) {
    final chart = selectedChart;
    if (chart == null) return;
    chart.externalBackgroundPath = path;
    chart.externalBackgroundName = name;
    chart.externalBackgroundStem = bgStem;
  }

  void replaceAudioResourceByPath(String oldPath, ResourceInfo replacement) {
    final current = scan;
    if (current == null) return;
    final updatedCharts = current.charts
        .map(
          (chart) => _samePath(chart.audio?.path, oldPath)
              ? chart.copyWith(audio: replacement)
              : chart,
        )
        .toList();
    scan = current.copyWith(
      audio: _samePath(current.audio?.path, oldPath)
          ? replacement
          : current.audio,
      charts: updatedCharts,
      warnings: [
        ...current.warnings,
        'Audio converted to ArcCreate-compatible OGG: ${replacement.name ?? replacement.path ?? ''}',
      ],
    );
  }

  void selectChart(int index) {
    final charts = edit?.charts;
    if (charts == null || charts.isEmpty) {
      selectedChartIndex = 0;
      return;
    }
    selectedChartIndex = index.clamp(0, charts.length - 1).toInt();
  }

  ResourceInfo? _externalBackgroundResource(ChartEditState? chart) {
    final path = chart?.externalBackgroundPath;
    if (path == null || path.isEmpty) return null;
    final file = File(path);
    return ResourceInfo(
      path: path,
      name: chart?.externalBackgroundName ?? file.uri.pathSegments.last,
      source: 'external',
      sizeBytes: file.existsSync() ? file.lengthSync() : null,
      raw: {
        'ratingClass': chart?.ratingClass,
        'bgStem': chart?.externalBackgroundStem,
      },
    );
  }
}

bool _samePath(String? left, String right) {
  if (left == null) return false;
  return p.equals(p.normalize(left), p.normalize(right));
}
