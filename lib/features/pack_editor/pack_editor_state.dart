import '../../core/models/conversion_options.dart';
import '../../core/models/operation_models.dart';
import '../../core/models/pack_models.dart';

class PackLevelMock {
  PackLevelMock({
    required this.title,
    required this.composer,
    required this.identifier,
    required this.chartCount,
    this.enabled = true,
  });

  final String title;
  final String composer;
  final String identifier;
  final int chartCount;
  bool enabled;
}

class PackEditorState {
  PackEditorMode mode = PackEditorMode.official;
  String? inputPath;
  List<String> inputPaths = const [];
  String packName = '';
  String packId = '';
  OperationPhase phase = OperationPhase.idle;
  bool compactByDefault = true;
  PackScanResult? scan;
  PackSaveResult? saveResult;
  String? error;
  final Set<int> expanded = {};
  final List<PackLevelMock> levels = [];
  final List<PackSongEntry> entries = [];
  final List<LogLine> logs = [];
  PreprocessOptions preprocessOptions = const PreprocessOptions();
  int? highlightedWarningEntryIndex;
  int? selectedEntryIndex;

  void mockImport() {
    inputPath = 'mock://samples/pack/vividstasis.zip';
    inputPaths = [inputPath!];
    packName = 'vivid/stasis';
    packId = 'vividstasis';
    phase = OperationPhase.ready;
    scan = null;
    saveResult = null;
    error = null;
    levels
      ..clear()
      ..addAll(
        List.generate(
          7,
          (index) => PackLevelMock(
            title: 'Mock Level ${index + 1}',
            composer: 'ArcCreate Composer',
            identifier: 'etoilebridge.mock${index + 1}',
            chartCount: index.isEven ? 1 : 3,
          ),
        ),
      );
    expanded
      ..clear()
      ..addAll(
        levels.length <= 5 ? Iterable.generate(levels.length) : const [],
      );
    logs
      ..clear()
      ..add(const LogLine('mock.pack.log1'));
  }

  void setMode(PackEditorMode nextMode) {
    if (mode == nextMode) return;
    mode = nextMode;
    preprocessOptions = nextMode == PackEditorMode.existing
        ? const PreprocessOptions.disabled()
        : const PreprocessOptions();
  }

  void clearInput() {
    inputPath = null;
    inputPaths = const [];
    packName = '';
    packId = '';
    phase = OperationPhase.idle;
    scan = null;
    saveResult = null;
    error = null;
    expanded.clear();
    highlightedWarningEntryIndex = null;
    selectedEntryIndex = null;
    entries.clear();
    levels.clear();
    logs.clear();
  }

  void startScanning(List<String> sources, String sessionPath) {
    inputPaths = sources;
    inputPath = sources.join('; ');
    packName = '';
    packId = '';
    phase = OperationPhase.scanning;
    scan = null;
    saveResult = null;
    error = null;
    entries.clear();
    levels.clear();
    expanded.clear();
    highlightedWarningEntryIndex = null;
    selectedEntryIndex = null;
    logs
      ..clear()
      ..add(LogLine('pack scan started: ${sources.join(', ')}'));
    if (mode == PackEditorMode.existing) {
      preprocessOptions = const PreprocessOptions.disabled();
    }
  }

  void applyScan(
    PackScanResult result, {
    List<String> warnings = const [],
    List<String> workerLogs = const [],
  }) {
    scan = result;
    inputPath = result.sourcePath ?? result.basePackPath ?? inputPath;
    packName = result.packName ?? result.packId ?? '';
    packId = result.packId ?? result.packDirectory ?? '';
    entries
      ..clear()
      ..addAll(result.entries);
    expanded
      ..clear()
      ..addAll(
        entries.length <= 5 ? Iterable.generate(entries.length) : const [],
      );
    highlightedWarningEntryIndex = null;
    selectedEntryIndex = entries.isEmpty ? null : 0;
    phase = OperationPhase.ready;
    error = null;
    _replaceLogs([
      ...workerLogs.map(
        (line) => LogLine(line, source: 'worker', scope: 'pack'),
      ),
      ...result.logs.map(
        (line) => LogLine(line, source: 'worker', scope: 'pack'),
      ),
      ...result.warnings.map(
        (line) =>
            LogLine(line, isWarning: true, source: 'warning', scope: 'pack'),
      ),
      ...warnings.map(
        (line) =>
            LogLine(line, isWarning: true, source: 'warning', scope: 'pack'),
      ),
    ]);
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
    logs.add(const LogLine('pack save started'));
  }

  void applySave(
    PackSaveResult result, {
    List<String> warnings = const [],
    List<String> workerLogs = const [],
  }) {
    saveResult = result;
    phase = OperationPhase.saved;
    error = null;
    _appendLogs([
      LogLine('pack save success: ${result.outputPath}', source: 'state'),
      ...workerLogs.map(
        (line) => LogLine(line, source: 'worker', scope: 'pack'),
      ),
      ...warnings.map(
        (line) =>
            LogLine(line, isWarning: true, source: 'warning', scope: 'pack'),
      ),
    ]);
  }

  void expandAll() {
    expanded
      ..clear()
      ..addAll(Iterable.generate(entryCount));
  }

  void collapseAll() {
    expanded.clear();
  }

  void setLevelEnabled(int index, bool enabled) {
    if (entries.isNotEmpty) {
      entries[index].enabled = enabled;
      if (entries[index].charts.isNotEmpty) {
        for (final chart in entries[index].charts) {
          chart.enabled = enabled && chart.canConvert;
        }
      }
    } else {
      levels[index].enabled = enabled;
    }
  }

  void setChartEnabled(int entryIndex, int chartIndex, bool enabled) {
    if (entryIndex < 0 || entryIndex >= entries.length) return;
    final entry = entries[entryIndex];
    if (chartIndex < 0 || chartIndex >= entry.charts.length) return;
    final chart = entry.charts[chartIndex];
    chart.enabled = enabled && chart.canConvert;
    entry.enabled = entry.charts.any(
      (chart) => chart.enabled && chart.canConvert,
    );
  }

  void focusWarningEntry(int index) {
    if (index < 0 || index >= entries.length) return;
    expanded.add(index);
    highlightedWarningEntryIndex = index;
    selectedEntryIndex = index;
  }

  void selectEntry(int index) {
    if (index < 0 || index >= entryCount) return;
    selectedEntryIndex = index;
  }

  void clearWarningHighlight() {
    highlightedWarningEntryIndex = null;
  }

  int get entryCount => entries.isNotEmpty ? entries.length : levels.length;

  int get exportableEntryCount => entries.isEmpty
      ? levels.where((level) => level.enabled).length
      : entries.where((entry) => entry.hasExportableCharts).length;

  int get exportableChartCount => entries.isEmpty
      ? levels
            .where((level) => level.enabled)
            .fold<int>(0, (sum, level) => sum + level.chartCount)
      : entries.fold<int>(0, (sum, entry) => sum + entry.exportableChartCount);

  int get excludedChartCount =>
      entries.fold<int>(0, (sum, entry) => sum + entry.excludedChartCount);

  void _replaceLogs(Iterable<LogLine> next) {
    logs
      ..clear()
      ..addAll(_dedupeLogs(next).take(120));
  }

  void _appendLogs(Iterable<LogLine> next) {
    final merged = _dedupeLogs([...logs, ...next]).toList();
    logs
      ..clear()
      ..addAll(
        merged.length > 120 ? merged.sublist(merged.length - 120) : merged,
      );
  }

  List<LogLine> _dedupeLogs(Iterable<LogLine> next) {
    final seen = <String>{};
    final result = <LogLine>[];
    for (final line in next) {
      if (line.message.trim().isEmpty) continue;
      final key = line.dedupeKey;
      if (seen.add(key)) result.add(line);
    }
    return result;
  }
}
