import 'dart:convert';

import 'conversion_options.dart';
import 'difficulty_display.dart';

enum SingleInputType { zip, folder, file, unknown }

enum SingleSourceKind {
  officialSong,
  officialPack,
  arccreateProject,
  arccreateArcpkg,
  unknown,
}

class WorkerActionResult<T> {
  const WorkerActionResult({
    required this.ok,
    this.data,
    this.error,
    this.warnings = const [],
    this.logs = const [],
    this.rawEnvelope = const {},
    this.rawJsonPath,
    this.diagnosticsPath,
    this.diagnostics = const [],
  });

  final bool ok;
  final T? data;
  final String? error;
  final List<String> warnings;
  final List<String> logs;
  final Map<String, Object?> rawEnvelope;
  final String? rawJsonPath;
  final String? diagnosticsPath;
  final List<String> diagnostics;

  static WorkerActionResult<T> fromEnvelope<T>(
    Map<String, Object?> json,
    T Function(Object? data) parse, {
    String? rawJsonPath,
    String? diagnosticsPath,
    List<String> diagnostics = const [],
  }) {
    return WorkerActionResult<T>(
      ok: json['ok'] == true,
      data: json['data'] == null ? null : parse(json['data']),
      error: json['error'] as String?,
      warnings: _stringList(json['warnings']),
      logs: _stringList(json['logs']),
      rawEnvelope: json,
      rawJsonPath: rawJsonPath,
      diagnosticsPath: diagnosticsPath,
      diagnostics: diagnostics,
    );
  }

  WorkerActionResult<T> copyWith({
    bool? ok,
    T? data,
    String? error,
    List<String>? warnings,
    List<String>? logs,
    Map<String, Object?>? rawEnvelope,
    String? rawJsonPath,
    String? diagnosticsPath,
    List<String>? diagnostics,
  }) {
    return WorkerActionResult<T>(
      ok: ok ?? this.ok,
      data: data ?? this.data,
      error: error ?? this.error,
      warnings: warnings ?? this.warnings,
      logs: logs ?? this.logs,
      rawEnvelope: rawEnvelope ?? this.rawEnvelope,
      rawJsonPath: rawJsonPath ?? this.rawJsonPath,
      diagnosticsPath: diagnosticsPath ?? this.diagnosticsPath,
      diagnostics: diagnostics ?? this.diagnostics,
    );
  }
}

class ResourceInfo {
  const ResourceInfo({
    this.path,
    this.name,
    this.source,
    this.sizeBytes,
    this.width,
    this.height,
    this.raw = const {},
  });

  final String? path;
  final String? name;
  final String? source;
  final int? sizeBytes;
  final int? width;
  final int? height;
  final Map<String, Object?> raw;

  factory ResourceInfo.fromJson(Object? value) {
    final json = _map(value);
    return ResourceInfo(
      path: json['path'] as String?,
      name: json['name'] as String?,
      source: json['source'] as String?,
      sizeBytes: _int(json['sizeBytes']),
      width: _int(json['width']),
      height: _int(json['height']),
      raw: json,
    );
  }

  Map<String, Object?> toJson() => {
    if (path != null) 'path': path,
    if (name != null) 'name': name,
    if (source != null) 'source': source,
    if (sizeBytes != null) 'sizeBytes': sizeBytes,
    if (width != null) 'width': width,
    if (height != null) 'height': height,
  };

  ResourceInfo copyWith({
    String? path,
    String? name,
    String? source,
    int? sizeBytes,
    int? width,
    int? height,
    Map<String, Object?>? raw,
  }) {
    return ResourceInfo(
      path: path ?? this.path,
      name: name ?? this.name,
      source: source ?? this.source,
      sizeBytes: sizeBytes ?? this.sizeBytes,
      width: width ?? this.width,
      height: height ?? this.height,
      raw: raw ?? this.raw,
    );
  }
}

class AffInfo {
  const AffInfo({
    required this.ratingClass,
    required this.path,
    required this.name,
    required this.adopted,
    this.sizeBytes,
    this.warning,
    this.raw = const {},
  });

  final int ratingClass;
  final String path;
  final String name;
  final bool adopted;
  final int? sizeBytes;
  final String? warning;
  final Map<String, Object?> raw;

  factory AffInfo.fromJson(Object? value) {
    final json = _map(value);
    return AffInfo(
      ratingClass: _int(json['ratingClass']) ?? 0,
      path: json['path'] as String? ?? '',
      name: json['name'] as String? ?? '',
      adopted: json['adopted'] != false,
      sizeBytes: _int(json['sizeBytes']),
      warning: json['warning'] as String?,
      raw: json,
    );
  }
}

class ChartMetadata {
  const ChartMetadata({
    required this.ratingClass,
    this.difficulty,
    this.chartConstant,
    this.rating,
    this.ratingPlus,
    this.charter,
    this.illustrator,
    this.alias,
    this.chartPath,
    this.affPath,
    this.affName,
    this.adopted = true,
    this.audio,
    this.jacket,
    this.background,
    this.audioOverride = false,
    this.jacketOverride = false,
    this.bgReference,
    this.bgOverride = false,
    this.missingBackgroundReference = false,
    this.resourceWarnings = const [],
    this.raw = const {},
  });

  final int ratingClass;
  final String? difficulty;
  final double? chartConstant;
  final int? rating;
  final bool? ratingPlus;
  final String? charter;
  final String? illustrator;
  final String? alias;
  final String? chartPath;
  final String? affPath;
  final String? affName;
  final bool adopted;
  final ResourceInfo? audio;
  final ResourceInfo? jacket;
  final ResourceInfo? background;
  final bool audioOverride;
  final bool jacketOverride;
  final String? bgReference;
  final bool bgOverride;
  final bool missingBackgroundReference;
  final List<String> resourceWarnings;
  final Map<String, Object?> raw;

  bool get isQuestionRating => DifficultyDisplay.isUnknownRating(
    rating: rating,
    chartConstant: chartConstant,
    difficulty: difficulty,
  );

  factory ChartMetadata.fromJson(Object? value) {
    final json = _map(value);
    final ratingClass = _int(json['ratingClass']) ?? 0;
    final rating = _int(json['rating']);
    final ratingPlus = json['ratingPlus'] as bool?;
    final difficulty = DifficultyDisplay.resolve(
      ratingClass: ratingClass,
      difficulty: json['difficulty'] as String?,
      chartConstant: _double(json['chartConstant']),
      rating: rating,
      ratingPlus: ratingPlus,
    );
    return ChartMetadata(
      ratingClass: ratingClass,
      difficulty: difficulty.name,
      chartConstant: difficulty.chartConstant,
      rating: rating,
      ratingPlus: ratingPlus,
      charter: json['charter'] as String?,
      illustrator: json['illustrator'] as String?,
      alias: json['alias'] as String?,
      chartPath: json['chartPath'] as String? ?? json['affPath'] as String?,
      affPath: json['affPath'] as String?,
      affName: json['affName'] as String?,
      adopted: json['adopted'] != false,
      audio: json['audio'] == null
          ? null
          : ResourceInfo.fromJson(json['audio']),
      jacket: json['jacket'] == null
          ? null
          : ResourceInfo.fromJson(json['jacket']),
      background: json['background'] == null
          ? null
          : ResourceInfo.fromJson(json['background']),
      audioOverride: json['audioOverride'] == true,
      jacketOverride: json['jacketOverride'] == true,
      bgReference: json['bg'] as String? ?? json['bgReference'] as String?,
      bgOverride: json['bgOverride'] == true,
      missingBackgroundReference: json['missingBackgroundReference'] == true,
      resourceWarnings: _stringList(json['resourceWarnings']),
      raw: json,
    );
  }

  Map<String, Object?> toSaveJson() => {
    'ratingClass': ratingClass,
    if (difficulty != null) 'difficulty': difficulty,
    if (chartConstant != null) 'chartConstant': chartConstant,
    if (charter != null) 'charter': charter,
    if (illustrator != null) 'illustrator': illustrator,
    if (alias != null) 'alias': alias,
    if (affPath != null) 'affPath': affPath,
    'adopted': adopted,
  };

  ChartMetadata copyWith({
    int? ratingClass,
    String? difficulty,
    double? chartConstant,
    int? rating,
    bool? ratingPlus,
    String? charter,
    String? illustrator,
    String? alias,
    String? chartPath,
    String? affPath,
    String? affName,
    bool? adopted,
    ResourceInfo? audio,
    ResourceInfo? jacket,
    ResourceInfo? background,
    bool? audioOverride,
    bool? jacketOverride,
    String? bgReference,
    bool? bgOverride,
    bool? missingBackgroundReference,
    List<String>? resourceWarnings,
    Map<String, Object?>? raw,
  }) {
    return ChartMetadata(
      ratingClass: ratingClass ?? this.ratingClass,
      difficulty: difficulty ?? this.difficulty,
      chartConstant: chartConstant ?? this.chartConstant,
      rating: rating ?? this.rating,
      ratingPlus: ratingPlus ?? this.ratingPlus,
      charter: charter ?? this.charter,
      illustrator: illustrator ?? this.illustrator,
      alias: alias ?? this.alias,
      chartPath: chartPath ?? this.chartPath,
      affPath: affPath ?? this.affPath,
      affName: affName ?? this.affName,
      adopted: adopted ?? this.adopted,
      audio: audio ?? this.audio,
      jacket: jacket ?? this.jacket,
      background: background ?? this.background,
      audioOverride: audioOverride ?? this.audioOverride,
      jacketOverride: jacketOverride ?? this.jacketOverride,
      bgReference: bgReference ?? this.bgReference,
      bgOverride: bgOverride ?? this.bgOverride,
      missingBackgroundReference:
          missingBackgroundReference ?? this.missingBackgroundReference,
      resourceWarnings: resourceWarnings ?? this.resourceWarnings,
      raw: raw ?? this.raw,
    );
  }
}

class SingleSongScanResult {
  const SingleSongScanResult({
    required this.sourcePath,
    required this.inputType,
    required this.workspacePath,
    required this.charts,
    required this.affFiles,
    this.sourceKind = SingleSourceKind.unknown,
    this.songId,
    this.title,
    this.artist,
    this.alias,
    this.bpmText,
    this.bpmBase,
    this.difficulty,
    this.version,
    this.packageDirectory,
    this.projectFilePath,
    this.audio,
    this.jacket,
    this.background,
    this.songlist,
    this.packlist,
    this.project,
    this.warnings = const [],
    this.logs = const [],
    this.raw = const {},
    this.extra = const {},
  });

  final String sourcePath;
  final SingleInputType inputType;
  final String workspacePath;
  final SingleSourceKind sourceKind;
  final String? songId;
  final String? title;
  final String? artist;
  final String? alias;
  final String? bpmText;
  final double? bpmBase;
  final String? difficulty;
  final String? version;
  final String? packageDirectory;
  final String? projectFilePath;
  final List<ChartMetadata> charts;
  final ResourceInfo? audio;
  final ResourceInfo? jacket;
  final ResourceInfo? background;
  final ResourceInfo? songlist;
  final ResourceInfo? packlist;
  final ResourceInfo? project;
  final List<AffInfo> affFiles;
  final List<String> warnings;
  final List<String> logs;
  final Map<String, Object?> raw;
  final Map<String, Object?> extra;

  factory SingleSongScanResult.fromJson(Object? value) {
    final json = _map(value);
    return SingleSongScanResult(
      sourcePath: json['sourcePath'] as String? ?? '',
      sourceKind: _sourceKind(json['sourceKind'] as String?),
      inputType: _inputType(json['inputType'] as String?),
      workspacePath: json['workspacePath'] as String? ?? '',
      songId: json['songId'] as String?,
      title: json['title'] as String?,
      artist: json['artist'] as String?,
      alias: json['alias'] as String?,
      bpmText: json['bpmText'] as String?,
      bpmBase: _double(json['bpmBase']),
      difficulty: json['difficulty'] as String?,
      version: json['version'] as String?,
      packageDirectory: json['packageDirectory'] as String?,
      projectFilePath: json['projectFilePath'] as String?,
      charts: _list(json['charts']).map(ChartMetadata.fromJson).toList(),
      audio: json['audio'] == null
          ? null
          : ResourceInfo.fromJson(json['audio']),
      jacket: json['jacket'] == null
          ? null
          : ResourceInfo.fromJson(json['jacket']),
      background: json['background'] == null
          ? null
          : ResourceInfo.fromJson(json['background']),
      songlist: json['songlist'] == null
          ? null
          : ResourceInfo.fromJson(json['songlist']),
      packlist: json['packlist'] == null
          ? null
          : ResourceInfo.fromJson(json['packlist']),
      project: json['project'] == null
          ? null
          : ResourceInfo.fromJson(json['project']),
      affFiles: _list(json['affFiles']).map(AffInfo.fromJson).toList(),
      warnings: _stringList(json['warnings']),
      logs: _stringList(json['logs']),
      raw: json,
      extra: _extraFields(json, _singleKnownKeys),
    );
  }

  SingleSongScanResult copyWith({
    String? sourcePath,
    SingleInputType? inputType,
    String? workspacePath,
    SingleSourceKind? sourceKind,
    String? songId,
    String? title,
    String? artist,
    String? alias,
    String? bpmText,
    double? bpmBase,
    String? difficulty,
    String? version,
    String? packageDirectory,
    String? projectFilePath,
    List<ChartMetadata>? charts,
    ResourceInfo? audio,
    ResourceInfo? jacket,
    ResourceInfo? background,
    ResourceInfo? songlist,
    ResourceInfo? packlist,
    ResourceInfo? project,
    List<AffInfo>? affFiles,
    List<String>? warnings,
    List<String>? logs,
    Map<String, Object?>? raw,
    Map<String, Object?>? extra,
  }) {
    return SingleSongScanResult(
      sourcePath: sourcePath ?? this.sourcePath,
      inputType: inputType ?? this.inputType,
      workspacePath: workspacePath ?? this.workspacePath,
      sourceKind: sourceKind ?? this.sourceKind,
      songId: songId ?? this.songId,
      title: title ?? this.title,
      artist: artist ?? this.artist,
      alias: alias ?? this.alias,
      bpmText: bpmText ?? this.bpmText,
      bpmBase: bpmBase ?? this.bpmBase,
      difficulty: difficulty ?? this.difficulty,
      version: version ?? this.version,
      packageDirectory: packageDirectory ?? this.packageDirectory,
      projectFilePath: projectFilePath ?? this.projectFilePath,
      charts: charts ?? this.charts,
      audio: audio ?? this.audio,
      jacket: jacket ?? this.jacket,
      background: background ?? this.background,
      songlist: songlist ?? this.songlist,
      packlist: packlist ?? this.packlist,
      project: project ?? this.project,
      affFiles: affFiles ?? this.affFiles,
      warnings: warnings ?? this.warnings,
      logs: logs ?? this.logs,
      raw: raw ?? this.raw,
      extra: extra ?? this.extra,
    );
  }
}

class SingleSongEditState {
  SingleSongEditState({
    this.publisherId = 'etoilebridge',
    this.levelId = '',
    this.title = '',
    this.artist = '',
    this.alias = '',
    this.bpmText = '',
    this.bpmBase = '',
    this.showAlias = false,
    this.charts = const [],
  });

  String publisherId;
  String levelId;
  String title;
  String artist;
  String alias;
  String bpmText;
  String bpmBase;
  bool showAlias;
  List<ChartEditState> charts;

  factory SingleSongEditState.fromScan(SingleSongScanResult scan) {
    final hasAlias =
        (scan.alias?.trim().isNotEmpty ?? false) ||
        scan.charts.any((chart) => chart.alias?.trim().isNotEmpty ?? false);
    return SingleSongEditState(
      levelId: scan.songId ?? '',
      title: scan.title ?? '',
      artist: scan.artist ?? '',
      alias: scan.alias ?? '',
      bpmText: scan.bpmText ?? '',
      bpmBase: scan.bpmBase?.toString() ?? '',
      showAlias:
          hasAlias ||
          scan.sourceKind == SingleSourceKind.arccreateProject ||
          scan.sourceKind == SingleSourceKind.arccreateArcpkg,
      charts: scan.charts
          .map(
            (chart) => ChartEditState(
              ratingClass: chart.ratingClass,
              difficulty: chart.difficulty ?? '',
              chartConstant: chart.chartConstant?.toString() ?? '',
              rating: chart.rating,
              ratingPlus: chart.ratingPlus,
              charter: chart.charter ?? '',
              illustrator: chart.illustrator ?? '',
              alias: chart.alias ?? '',
              chartPath: chart.chartPath,
              affPath: chart.affPath,
              affName: chart.affName,
              adopted: chart.adopted,
            ),
          )
          .toList(),
    );
  }

  Map<String, Object?> toSaveJson() => {
    'publisherId': publisherId,
    'levelId': levelId,
    'title': title,
    'artist': artist,
    'bpmText': bpmText,
    'bpmBase': _parseNum(bpmBase),
    'charts': charts.map((chart) => chart.toSaveJson()).toList(),
  };
}

class ChartEditState {
  ChartEditState({
    required this.ratingClass,
    this.difficulty = '',
    this.chartConstant = '',
    this.rating,
    this.ratingPlus,
    this.charter = '',
    this.illustrator = '',
    this.alias = '',
    this.chartPath,
    this.affPath,
    this.affName,
    this.adopted = true,
    this.externalBackgroundPath,
    this.externalBackgroundName,
    this.externalBackgroundStem,
  });

  final int ratingClass;
  String difficulty;
  String chartConstant;
  int? rating;
  bool? ratingPlus;
  String charter;
  String illustrator;
  String alias;
  String? chartPath;
  String? affPath;
  String? affName;
  bool adopted;
  String? externalBackgroundPath;
  String? externalBackgroundName;
  String? externalBackgroundStem;

  Map<String, Object?> toSaveJson() => {
    'ratingClass': ratingClass,
    'difficulty': difficulty,
    'chartConstant': _parseNum(chartConstant),
    'charter': charter,
    'illustrator': illustrator,
    'alias': alias,
    if (affPath != null) 'affPath': affPath,
    if (externalBackgroundPath != null)
      'externalBackgroundPath': externalBackgroundPath,
    if (externalBackgroundName != null)
      'externalBackgroundName': externalBackgroundName,
    if (externalBackgroundStem != null)
      'externalBackgroundStem': externalBackgroundStem,
    'adopted': adopted,
  };
}

class SingleSongSaveRequest {
  const SingleSongSaveRequest({
    required this.scan,
    required this.edit,
    required this.outputPath,
    this.appearance = const ArcCreateAppearanceOptions(),
    this.preprocess = const PreprocessOptions(),
    this.resources = const SingleSongResourceOverrides(),
  });

  final SingleSongScanResult scan;
  final SingleSongEditState edit;
  final String outputPath;
  final ArcCreateAppearanceOptions appearance;
  final PreprocessOptions preprocess;
  final SingleSongResourceOverrides resources;

  Map<String, Object?> toWorkerRequestJson() => {
    ...edit.toSaveJson(),
    'resources': resources.toJson(),
    'appearance': appearance.toJson(),
    'preprocess': preprocess.toJson(),
  };
}

class SingleSongResourceOverrides {
  const SingleSongResourceOverrides({
    this.audioPath,
    this.jacketPath,
    this.backgroundPath,
    this.songlistPath,
  });

  final String? audioPath;
  final String? jacketPath;
  final String? backgroundPath;
  final String? songlistPath;

  bool get isEmpty =>
      audioPath == null &&
      jacketPath == null &&
      backgroundPath == null &&
      songlistPath == null;

  Map<String, Object?> toJson() => {
    if (audioPath != null) 'audioPath': audioPath,
    if (jacketPath != null) 'jacketPath': jacketPath,
    if (backgroundPath != null) 'backgroundPath': backgroundPath,
    if (songlistPath != null) 'songlistPath': songlistPath,
  };
}

class SingleSongSaveResult {
  const SingleSongSaveResult({
    required this.outputPath,
    this.songId,
    this.sizeBytes,
  });

  final String outputPath;
  final String? songId;
  final int? sizeBytes;

  factory SingleSongSaveResult.fromJson(Object? value) {
    final json = _map(value);
    return SingleSongSaveResult(
      outputPath: json['outputPath'] as String? ?? '',
      songId: json['songId'] as String?,
      sizeBytes: _int(json['sizeBytes']),
    );
  }
}

String encodeWorkerJson(Map<String, Object?> json) => jsonEncode(json);

String prettyWorkerJson(Map<String, Object?> json) {
  const encoder = JsonEncoder.withIndent('  ');
  return encoder.convert(json);
}

String inputTypeLabel(SingleInputType type) => switch (type) {
  SingleInputType.zip => 'ZIP',
  SingleInputType.folder => 'Folder',
  SingleInputType.file => 'File',
  SingleInputType.unknown => 'Unknown',
};

String sourceKindLabel(SingleSourceKind kind) => switch (kind) {
  SingleSourceKind.officialSong => 'Official song',
  SingleSourceKind.officialPack => 'Official pack',
  SingleSourceKind.arccreateProject => 'ArcCreate project',
  SingleSourceKind.arccreateArcpkg => 'ArcCreate arcpkg',
  SingleSourceKind.unknown => 'Unknown',
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

num? _parseNum(String value) {
  if (value.trim().isEmpty) return null;
  return num.tryParse(value.trim());
}

SingleInputType _inputType(String? value) => switch (value?.toLowerCase()) {
  'zip' => SingleInputType.zip,
  'folder' => SingleInputType.folder,
  'file' => SingleInputType.file,
  _ => SingleInputType.unknown,
};

SingleSourceKind _sourceKind(String? value) => switch (value) {
  'official-song' => SingleSourceKind.officialSong,
  'official-pack' => SingleSourceKind.officialPack,
  'arccreate-project' => SingleSourceKind.arccreateProject,
  'arccreate-arcpkg' => SingleSourceKind.arccreateArcpkg,
  _ => SingleSourceKind.unknown,
};

Map<String, Object?> _extraFields(
  Map<String, Object?> json,
  Set<String> knownKeys,
) {
  final extra = <String, Object?>{};
  for (final entry in json.entries) {
    if (!knownKeys.contains(entry.key)) extra[entry.key] = entry.value;
  }
  return extra;
}

const _singleKnownKeys = {
  'sourcePath',
  'sourceKind',
  'inputType',
  'workspacePath',
  'songId',
  'title',
  'artist',
  'alias',
  'bpmText',
  'bpmBase',
  'difficulty',
  'version',
  'packageDirectory',
  'projectFilePath',
  'charts',
  'audio',
  'jacket',
  'background',
  'songlist',
  'packlist',
  'project',
  'affFiles',
  'warnings',
  'logs',
};
