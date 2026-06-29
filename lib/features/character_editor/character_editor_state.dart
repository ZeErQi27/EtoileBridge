import '../../core/models/character_models.dart';
import '../../core/models/operation_models.dart';

class CharacterEditorState {
  String? inputPath;
  CharacterScanResult? scan;
  CharacterEditState? edit;
  CharacterIconResult? generatedIcon;
  CharacterSaveResult? saveResult;
  OperationPhase phase = OperationPhase.idle;
  bool iconGenerating = false;
  bool iconCropDirty = false;
  String? iconError;
  String? lastError;
  final List<LogLine> logs = [];

  double get x => edit?.x ?? 300;
  double get y => edit?.y ?? 100;
  double get scale => edit?.scale ?? 0.7;
  double get cropCenterX => edit?.cropCenterX ?? 0.5;
  double get cropCenterY => edit?.cropCenterY ?? 0.5;
  double get cropSize => edit?.cropSize ?? 0.5;
  String? get imageName => scan?.image?.name ?? edit?.imageFileName;
  String? get iconName =>
      generatedIcon?.icon.name ?? scan?.icon?.name ?? edit?.iconFileName;
  String? get imagePath => edit?.imagePath ?? scan?.image?.path;
  String? get iconPath =>
      generatedIcon?.iconPath ?? edit?.iconPath ?? scan?.icon?.path;
  bool get hasInput => inputPath != null && inputPath!.isNotEmpty;
  bool get hasPreviewImage => imagePath != null && imagePath!.isNotEmpty;
  bool get canSave =>
      scan != null && edit != null && phase != OperationPhase.saving;

  void clearInput() {
    inputPath = null;
    scan = null;
    edit = null;
    generatedIcon = null;
    saveResult = null;
    iconGenerating = false;
    iconCropDirty = false;
    iconError = null;
    lastError = null;
    phase = OperationPhase.idle;
    _resetLogs(const []);
  }

  void startScanning(String sourcePath) {
    inputPath = sourcePath;
    scan = null;
    edit = null;
    generatedIcon = null;
    saveResult = null;
    iconGenerating = false;
    iconCropDirty = false;
    iconError = null;
    lastError = null;
    phase = OperationPhase.scanning;
    _resetLogs(['character.scan.start']);
  }

  void applyScan(
    CharacterScanResult result, {
    List<String> warnings = const [],
    List<String> workerLogs = const [],
  }) {
    scan = result;
    edit = CharacterEditState.fromScan(result);
    generatedIcon = null;
    saveResult = null;
    iconGenerating = false;
    iconCropDirty = false;
    iconError = null;
    lastError = null;
    phase = OperationPhase.ready;
    _resetLogs([
      ...result.logs,
      ...workerLogs,
      ...warnings,
      ...result.warnings,
    ]);
  }

  void startSaving() {
    lastError = null;
    phase = OperationPhase.saving;
    _appendLog('character.save.start');
  }

  void startIconGeneration() {
    iconGenerating = true;
    iconError = null;
    _appendLog('character.icon.generate.start');
  }

  void applyGeneratedIcon(
    CharacterIconResult result, {
    List<String> warnings = const [],
    List<String> workerLogs = const [],
  }) {
    iconGenerating = false;
    iconCropDirty = false;
    iconError = null;
    generatedIcon = result;
    edit?.iconPath = result.iconPath;
    edit?.iconFileName = result.icon.name;
    for (final line in [...result.logs, ...workerLogs, ...warnings]) {
      _appendLog(line);
    }
  }

  void failIconGeneration(
    String message, {
    List<String> workerLogs = const [],
  }) {
    iconGenerating = false;
    iconError = message;
    for (final line in [...workerLogs, message]) {
      _appendLog(line);
    }
  }

  void applySave(
    CharacterSaveResult result, {
    List<String> warnings = const [],
    List<String> workerLogs = const [],
  }) {
    saveResult = result;
    lastError = null;
    phase = OperationPhase.saved;
    for (final line in [
      ...workerLogs,
      ...warnings,
      ...result.validation.logs,
      ...result.validation.warnings,
      'character.save.success: ${result.outputPath}',
    ]) {
      _appendLog(line);
    }
  }

  void fail(String message, {List<String> workerLogs = const []}) {
    lastError = message;
    phase = OperationPhase.failed;
    for (final line in [...workerLogs, message]) {
      _appendLog(line);
    }
  }

  void updateMetadata({
    String? publisherId,
    String? characterId,
    String? directory,
    String? defaultName,
    String? zhCnName,
    String? outputFileName,
    String? imageFileName,
    String? iconFileName,
  }) {
    final current = edit;
    if (current == null) return;
    if (publisherId != null) current.publisherId = publisherId;
    if (characterId != null) current.characterId = characterId;
    if (directory != null) current.directory = directory;
    if (defaultName != null) current.defaultName = defaultName;
    if (zhCnName != null) current.zhCnName = zhCnName;
    if (outputFileName != null) current.outputFileName = outputFileName;
    if (imageFileName != null) current.imageFileName = imageFileName;
    if (iconFileName != null) current.iconFileName = iconFileName;
  }

  void updatePosition({double? x, double? y, double? scale}) {
    final current = edit;
    if (current == null) return;
    if (x != null) current.x = x;
    if (y != null) current.y = y;
    if (scale != null) current.scale = scale;
  }

  void updateCrop({double? centerX, double? centerY, double? cropSize}) {
    final current = edit;
    if (current == null) return;
    if (centerX != null) current.cropCenterX = centerX;
    if (centerY != null) current.cropCenterY = centerY;
    if (cropSize != null) current.cropSize = cropSize;
    iconCropDirty = true;
    iconError = null;
  }

  void _resetLogs(List<String> next) {
    logs
      ..clear()
      ..addAll(_trimLogs(next));
  }

  void _appendLog(String message) {
    if (message.trim().isEmpty) return;
    logs.add(LogLine(message));
    if (logs.length > 80) logs.removeRange(0, logs.length - 80);
  }

  List<LogLine> _trimLogs(List<String> next) {
    return next
        .where((line) => line.trim().isNotEmpty)
        .toSet()
        .take(80)
        .map(LogLine.new)
        .toList();
  }
}
