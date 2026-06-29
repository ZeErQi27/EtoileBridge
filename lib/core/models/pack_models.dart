import 'conversion_options.dart';
import 'difficulty_display.dart';
import 'single_song_models.dart';

enum PackEditorMode { official, bundle, existing }

extension PackEditorModeI18n on PackEditorMode {
  String get i18nKey {
    switch (this) {
      case PackEditorMode.official:
        return 'packMode.official';
      case PackEditorMode.bundle:
        return 'packMode.bundle';
      case PackEditorMode.existing:
        return 'packMode.existing';
    }
  }
}

class PackScanResult {
  const PackScanResult({
    required this.mode,
    this.sourcePath,
    this.basePackPath,
    this.addWorkspacePath,
    this.workspacePath,
    this.publisherId,
    this.packName,
    this.packId,
    this.packIdentifier,
    this.packDirectory,
    this.packImage,
    this.entries = const [],
    this.existingLevelCount = 0,
    this.addedLevelCount = 0,
    this.finalLevelCount = 0,
    this.renamedConflictCount = 0,
    this.warnings = const [],
    this.logs = const [],
    this.raw = const {},
  });

  final PackEditorMode mode;
  final String? sourcePath;
  final String? basePackPath;
  final String? addWorkspacePath;
  final String? workspacePath;
  final String? publisherId;
  final String? packName;
  final String? packId;
  final String? packIdentifier;
  final String? packDirectory;
  final ResourceInfo? packImage;
  final List<PackSongEntry> entries;
  final int existingLevelCount;
  final int addedLevelCount;
  final int finalLevelCount;
  final int renamedConflictCount;
  final List<String> warnings;
  final List<String> logs;
  final Map<String, Object?> raw;

  factory PackScanResult.fromJson(Object? value) {
    final json = _map(value);
    return PackScanResult(
      mode: _packMode(json['mode'] as String?),
      sourcePath: json['sourcePath'] as String?,
      basePackPath: json['basePackPath'] as String?,
      addWorkspacePath: json['addWorkspacePath'] as String?,
      workspacePath: json['workspacePath'] as String?,
      publisherId: json['publisherId'] as String?,
      packName: json['packName'] as String?,
      packId: json['packId'] as String?,
      packIdentifier: json['packIdentifier'] as String?,
      packDirectory: json['packDirectory'] as String?,
      packImage: json['packImage'] == null
          ? null
          : ResourceInfo.fromJson(json['packImage']),
      entries: _list(json['entries']).map(PackSongEntry.fromJson).toList(),
      existingLevelCount: _int(json['existingLevelCount']) ?? 0,
      addedLevelCount: _int(json['addedLevelCount']) ?? 0,
      finalLevelCount: _int(json['finalLevelCount']) ?? 0,
      renamedConflictCount: _int(json['renamedConflictCount']) ?? 0,
      warnings: _stringList(json['warnings']),
      logs: _stringList(json['logs']),
      raw: json,
    );
  }
}

class PackSongEntry {
  PackSongEntry({
    required this.key,
    this.sourceFile,
    this.directory,
    this.identifier,
    this.songId,
    this.title,
    this.artist,
    this.levelId,
    this.difficultySummary = '',
    this.chartCount = 0,
    this.resourceStatus = '',
    this.jacket,
    this.background,
    this.enabled = true,
    this.canConvert = true,
    this.charts = const [],
    this.warnings = const [],
    this.failureReason,
    this.raw = const {},
  });

  final String key;
  final String? sourceFile;
  final String? directory;
  final String? identifier;
  final String? songId;
  String? title;
  String? artist;
  String? levelId;
  String difficultySummary;
  int chartCount;
  String resourceStatus;
  final ResourceInfo? jacket;
  final ResourceInfo? background;
  bool enabled;
  bool canConvert;
  final List<PackChartEntry> charts;
  final List<String> warnings;
  final String? failureReason;
  final Map<String, Object?> raw;

  int get exportableChartCount {
    if (!enabled || !canConvert) return 0;
    if (charts.isEmpty) return chartCount > 0 ? chartCount : 1;
    return charts.where((chart) => chart.enabled && chart.canConvert).length;
  }

  bool get hasExportableCharts => exportableChartCount > 0;

  int get excludedChartCount =>
      charts.where((chart) => !chart.enabled || !chart.canConvert).length;

  factory PackSongEntry.fromJson(Object? value) {
    final json = _map(value);
    return PackSongEntry(
      key: json['key'] as String? ?? json['identifier'] as String? ?? '',
      sourceFile: json['sourceFile'] as String?,
      directory: json['directory'] as String?,
      identifier: json['identifier'] as String?,
      songId: json['songId'] as String?,
      title: json['title'] as String?,
      artist: json['artist'] as String?,
      levelId: json['levelId'] as String?,
      difficultySummary: json['difficultySummary'] as String? ?? '',
      chartCount: _int(json['chartCount']) ?? 0,
      resourceStatus: json['resourceStatus'] as String? ?? '',
      jacket: json['jacket'] == null
          ? null
          : ResourceInfo.fromJson(json['jacket']),
      background: json['background'] == null
          ? null
          : ResourceInfo.fromJson(json['background']),
      enabled: json['enabled'] != false,
      canConvert: json['canConvert'] != false,
      charts: _list(json['charts']).map(PackChartEntry.fromJson).toList(),
      warnings: _stringList(json['warnings']),
      failureReason: json['failureReason'] as String?,
      raw: json,
    );
  }

  Map<String, Object?> toEditJson() => {
    'key': key,
    'enabled': hasExportableCharts,
    'title': title ?? '',
    'artist': artist ?? '',
    'levelId': levelId ?? songId ?? directory ?? key,
    'charts': charts.map((chart) => chart.toEditJson()).toList(),
  };
}

class PackChartEntry {
  PackChartEntry({
    required this.ratingClass,
    this.chartPath,
    this.difficulty = '',
    this.chartConstant,
    this.charter,
    this.illustrator,
    this.enabled = true,
    this.canConvert = true,
    this.warnings = const [],
    this.failureReason,
  });

  final int ratingClass;
  final String? chartPath;
  String difficulty;
  double? chartConstant;
  String? charter;
  String? illustrator;
  bool enabled;
  final bool canConvert;
  final List<String> warnings;
  final String? failureReason;

  factory PackChartEntry.fromJson(Object? value) {
    final json = _map(value);
    final ratingClass = _int(json['ratingClass']) ?? 0;
    final difficulty = DifficultyDisplay.resolve(
      ratingClass: ratingClass,
      difficulty: json['difficulty'] as String?,
      chartConstant: _double(json['chartConstant']),
    );
    return PackChartEntry(
      ratingClass: ratingClass,
      chartPath: json['chartPath'] as String?,
      difficulty: difficulty.name,
      chartConstant: difficulty.chartConstant,
      charter: json['charter'] as String?,
      illustrator: json['illustrator'] as String?,
      enabled: json['enabled'] != false,
      canConvert: json['canConvert'] != false,
      warnings: _stringList(json['warnings']),
      failureReason: json['failureReason'] as String?,
    );
  }

  Map<String, Object?> toEditJson() => {
    'ratingClass': ratingClass,
    'enabled': enabled,
    'difficulty': difficulty,
    'chartConstant': chartConstant,
    'charter': charter ?? '',
    'illustrator': illustrator ?? '',
  };
}

class PackSaveRequest {
  const PackSaveRequest({
    required this.scan,
    required this.outputPath,
    required this.packName,
    required this.packId,
    required this.entries,
    this.appearance = const ArcCreateAppearanceOptions(),
    this.preprocess = const PreprocessOptions(),
  });

  final PackScanResult scan;
  final String outputPath;
  final String packName;
  final String packId;
  final List<PackSongEntry> entries;
  final ArcCreateAppearanceOptions appearance;
  final PreprocessOptions preprocess;

  PreprocessOptions get _effectivePreprocess =>
      scan.mode == PackEditorMode.existing
      ? const PreprocessOptions.disabled()
      : preprocess;

  Map<String, Object?> toWorkerRequestJson() => {
    'mode': scan.mode.name,
    'publisherId': scan.publisherId ?? 'etoilebridge',
    'outputFileName': outputPath,
    'packName': packName,
    'packId': packId,
    'packIdentifier': scan.packIdentifier,
    'packImagePath': scan.packImage?.path,
    'entries': entries.map((entry) => entry.toEditJson()).toList(),
    'appearance': appearance.toJson(),
    'preprocess': _effectivePreprocess.toJson(),
  };
}

class PackSaveResult {
  const PackSaveResult({
    required this.outputPath,
    this.sizeBytes,
    this.convertedCount,
    this.skippedCount,
  });

  final String outputPath;
  final int? sizeBytes;
  final int? convertedCount;
  final int? skippedCount;

  factory PackSaveResult.fromJson(Object? value) {
    final json = _map(value);
    return PackSaveResult(
      outputPath: json['outputPath'] as String? ?? '',
      sizeBytes: _int(json['sizeBytes']),
      convertedCount: _int(json['convertedCount']),
      skippedCount: _int(json['skippedCount']),
    );
  }
}

PackEditorMode _packMode(String? value) => switch (value) {
  'official' => PackEditorMode.official,
  'existing' => PackEditorMode.existing,
  _ => PackEditorMode.bundle,
};

Map<String, Object?> _map(Object? value) {
  if (value is Map<String, Object?>) return value;
  if (value is Map) return value.cast<String, Object?>();
  return const {};
}

List<Object?> _list(Object? value) =>
    value is List ? value.cast<Object?>() : const [];

List<String> _stringList(Object? value) =>
    _list(value).whereType<String>().toList(growable: false);

int? _int(Object? value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  return int.tryParse(value?.toString() ?? '');
}

double? _double(Object? value) {
  if (value is double) return value;
  if (value is num) return value.toDouble();
  return double.tryParse(value?.toString() ?? '');
}
