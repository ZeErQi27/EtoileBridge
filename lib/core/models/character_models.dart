import 'single_song_models.dart';

enum CharacterInputKind { image, arcpkg, unknown }

class CharacterScanResult {
  const CharacterScanResult({
    required this.sourcePath,
    required this.sourceKind,
    required this.inputType,
    required this.workspacePath,
    required this.publisherId,
    required this.characterId,
    required this.directory,
    required this.identifier,
    required this.outputFileName,
    required this.defaultName,
    this.zhCnName,
    this.imagePath,
    this.iconPath,
    this.image,
    this.icon,
    this.imageHasAlpha,
    this.x = 300,
    this.y = 100,
    this.scale = 0.7,
    this.warnings = const [],
    this.logs = const [],
    this.raw = const {},
  });

  final String sourcePath;
  final CharacterInputKind sourceKind;
  final String inputType;
  final String workspacePath;
  final String publisherId;
  final String characterId;
  final String directory;
  final String identifier;
  final String outputFileName;
  final String defaultName;
  final String? zhCnName;
  final String? imagePath;
  final String? iconPath;
  final ResourceInfo? image;
  final ResourceInfo? icon;
  final bool? imageHasAlpha;
  final double x;
  final double y;
  final double scale;
  final List<String> warnings;
  final List<String> logs;
  final Map<String, Object?> raw;

  factory CharacterScanResult.fromJson(Object? value) {
    final json = _map(value);
    return CharacterScanResult(
      sourcePath: json['sourcePath'] as String? ?? '',
      sourceKind: _characterInputKind(json['sourceKind'] as String?),
      inputType: json['inputType'] as String? ?? '',
      workspacePath: json['workspacePath'] as String? ?? '',
      publisherId: json['publisherId'] as String? ?? 'etoilebridge',
      characterId: json['characterId'] as String? ?? 'character',
      directory: json['directory'] as String? ?? 'character',
      identifier: json['identifier'] as String? ?? 'etoilebridge.character',
      outputFileName:
          json['outputFileName'] as String? ?? 'etoilebridge.character.arcpkg',
      defaultName: json['defaultName'] as String? ?? 'Character',
      zhCnName: json['zhCnName'] as String?,
      imagePath: json['imagePath'] as String?,
      iconPath: json['iconPath'] as String?,
      image: json['image'] == null
          ? null
          : ResourceInfo.fromJson(json['image']),
      icon: json['icon'] == null ? null : ResourceInfo.fromJson(json['icon']),
      imageHasAlpha: json['imageHasAlpha'] as bool?,
      x: _double(json['x']) ?? 300,
      y: _double(json['y']) ?? 100,
      scale: _double(json['scale']) ?? 0.7,
      warnings: _stringList(json['warnings']),
      logs: _stringList(json['logs']),
      raw: json,
    );
  }
}

class CharacterEditState {
  CharacterEditState({
    this.publisherId = 'etoilebridge',
    this.characterId = 'character',
    this.directory = 'character',
    this.outputFileName = 'etoilebridge.character.arcpkg',
    this.defaultName = 'Character',
    this.zhCnName = '',
    this.imageFileName,
    this.iconFileName,
    this.imagePath,
    this.iconPath,
    this.x = 300,
    this.y = 100,
    this.scale = 0.7,
    this.cropCenterX = 0.5,
    this.cropCenterY = 0.5,
    this.cropSize = 0.5,
  });

  String publisherId;
  String characterId;
  String directory;
  String outputFileName;
  String defaultName;
  String zhCnName;
  String? imageFileName;
  String? iconFileName;
  String? imagePath;
  String? iconPath;
  double x;
  double y;
  double scale;
  double cropCenterX;
  double cropCenterY;
  double cropSize;

  factory CharacterEditState.fromScan(CharacterScanResult scan) {
    return CharacterEditState(
      publisherId: scan.publisherId,
      characterId: scan.characterId,
      directory: scan.directory,
      outputFileName: scan.outputFileName,
      defaultName: scan.defaultName,
      zhCnName: scan.zhCnName ?? '',
      imageFileName: scan.image?.name ?? _fileName(scan.imagePath),
      iconFileName: scan.icon?.name ?? _fileName(scan.iconPath),
      imagePath: scan.image?.path,
      iconPath: scan.icon?.path,
      x: scan.x,
      y: scan.y,
      scale: scan.scale == 0 ? 1 : scan.scale,
    );
  }

  String get identifier => '$publisherId.$characterId';

  Map<String, Object?> toSaveJson({String? iconPathOverride}) => {
    'publisherId': publisherId,
    'characterId': characterId,
    'directory': directory,
    'outputFileName': outputFileName,
    'defaultName': defaultName,
    if (zhCnName.trim().isNotEmpty) 'zhCnName': zhCnName,
    'imagePath': imagePath,
    'iconPath': iconPathOverride ?? iconPath,
    if (imageFileName != null) 'imageFileName': imageFileName,
    if (iconFileName != null) 'iconFileName': iconFileName,
    'x': x,
    'y': y,
    'scale': scale,
  };
}

class CharacterIconRequest {
  const CharacterIconRequest({
    required this.imagePath,
    required this.outputPath,
    required this.centerX,
    required this.centerY,
    required this.cropSize,
    this.outputSize = 256,
  });

  final String imagePath;
  final String outputPath;
  final double centerX;
  final double centerY;
  final double cropSize;
  final int outputSize;

  Map<String, Object?> toJson() => {
    'imagePath': imagePath,
    'outputPath': outputPath,
    'centerX': centerX,
    'centerY': centerY,
    'cropSize': cropSize,
    'outputSize': outputSize,
  };
}

class CharacterIconResult {
  const CharacterIconResult({
    required this.iconPath,
    required this.icon,
    this.warnings = const [],
    this.logs = const [],
  });

  final String iconPath;
  final ResourceInfo icon;
  final List<String> warnings;
  final List<String> logs;

  factory CharacterIconResult.fromJson(Object? value) {
    final json = _map(value);
    return CharacterIconResult(
      iconPath: json['iconPath'] as String? ?? '',
      icon: ResourceInfo.fromJson(json['icon']),
      warnings: _stringList(json['warnings']),
      logs: _stringList(json['logs']),
    );
  }
}

class CharacterSaveRequest {
  const CharacterSaveRequest({
    required this.scan,
    required this.edit,
    required this.outputPath,
    required this.iconPath,
  });

  final CharacterScanResult scan;
  final CharacterEditState edit;
  final String outputPath;
  final String iconPath;

  Map<String, Object?> toWorkerRequestJson() =>
      edit.toSaveJson(iconPathOverride: iconPath);
}

class CharacterSaveResult {
  const CharacterSaveResult({
    required this.outputPath,
    required this.identifier,
    required this.directory,
    required this.validation,
    this.sizeBytes,
    this.displayName,
    this.queriedSizeBytes,
  });

  final String outputPath;
  final String identifier;
  final String directory;
  final int? sizeBytes;
  final String? displayName;
  final int? queriedSizeBytes;
  final CharacterValidationResult validation;

  factory CharacterSaveResult.fromJson(Object? value) {
    final json = _map(value);
    return CharacterSaveResult(
      outputPath: json['outputPath'] as String? ?? '',
      identifier: json['identifier'] as String? ?? '',
      directory: json['directory'] as String? ?? '',
      sizeBytes: _int(json['sizeBytes']),
      displayName: json['displayName'] as String?,
      queriedSizeBytes: _int(json['queriedSizeBytes']),
      validation: CharacterValidationResult.fromJson(json['validation']),
    );
  }
}

class CharacterValidationResult {
  const CharacterValidationResult({
    required this.valid,
    this.characterEntryCount = 0,
    this.identifier,
    this.directory,
    this.defaultName,
    this.imageExists = false,
    this.iconExists = false,
    this.errors = const [],
    this.warnings = const [],
    this.logs = const [],
  });

  final bool valid;
  final int characterEntryCount;
  final String? identifier;
  final String? directory;
  final String? defaultName;
  final bool imageExists;
  final bool iconExists;
  final List<String> errors;
  final List<String> warnings;
  final List<String> logs;

  factory CharacterValidationResult.fromJson(Object? value) {
    final json = _map(value);
    return CharacterValidationResult(
      valid: json['valid'] != false,
      characterEntryCount: _int(json['characterEntryCount']) ?? 0,
      identifier: json['identifier'] as String?,
      directory: json['directory'] as String?,
      defaultName: json['defaultName'] as String?,
      imageExists: json['imageExists'] == true,
      iconExists: json['iconExists'] == true,
      errors: _stringList(json['errors']),
      warnings: _stringList(json['warnings']),
      logs: _stringList(json['logs']),
    );
  }
}

CharacterInputKind _characterInputKind(String? value) =>
    switch (value?.toLowerCase()) {
      'image' => CharacterInputKind.image,
      'arcpkg' => CharacterInputKind.arcpkg,
      _ => CharacterInputKind.unknown,
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

String? _fileName(String? path) {
  if (path == null || path.trim().isEmpty) return null;
  return path.split(RegExp(r'[\\/]')).last;
}
