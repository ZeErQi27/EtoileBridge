enum OperationPhase { idle, scanning, scanned, ready, saving, saved, failed }

extension OperationPhaseI18n on OperationPhase {
  String get i18nKey => 'phase.$name';
}

class LogLine {
  const LogLine(
    this.message, {
    this.isWarning = false,
    this.isError = false,
    this.source,
    this.scope,
    this.code,
    this.targetId,
  });

  final String message;
  final bool isWarning;
  final bool isError;
  final String? source;
  final String? scope;
  final String? code;
  final String? targetId;

  String get dedupeKey {
    final kind = isError ? 'error' : (isWarning ? 'warning' : 'log');
    return [
      kind,
      source ?? '',
      scope ?? '',
      code ?? '',
      targetId ?? '',
      message.trim(),
    ].join('\u{1f}');
  }
}

class MockResource {
  const MockResource({
    required this.label,
    required this.fileName,
    required this.statusKey,
  });

  final String label;
  final String fileName;
  final String statusKey;
}
