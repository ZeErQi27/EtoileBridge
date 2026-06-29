import 'dart:collection';
import 'dart:io';

import 'package:flutter/foundation.dart';

import 'app_error_logger.dart';

class AppActionLogger {
  AppActionLogger._();

  static const int _maxLines = 300;
  static final Queue<String> _lines = Queue<String>();
  static final Map<String, DateTime> _lastWrittenAt = {};

  static File? _logFile;

  static Future<void> initialize() async {
    if (kIsWeb) return;
    try {
      final directoryPath = AppErrorLogger.logDirectoryPath;
      if (directoryPath == null) return;
      _logFile = File('$directoryPath${Platform.pathSeparator}app-actions.log');
      debugPrint('Action logger initialized: ${_logFile!.path}');
      write(
        id: 'logger.init',
        label: 'Action logger initialized: ${_logFile!.path}',
        page: '-',
        phase: 'ready',
        before: '-',
      );
    } catch (error, stackTrace) {
      AppErrorLogger.record(error, stackTrace, source: 'AppActionLogger');
    }
  }

  static void write({
    required String id,
    required String label,
    required String page,
    required String phase,
    required String before,
    String? after,
    Object? error,
    StackTrace? stackTrace,
    Duration throttle = Duration.zero,
  }) {
    if (throttle > Duration.zero) {
      final now = DateTime.now();
      final last = _lastWrittenAt[id];
      if (last != null && now.difference(last) < throttle) return;
      _lastWrittenAt[id] = now;
    }

    final timestamp = DateTime.now().toIso8601String();
    final platform = kIsWeb ? 'web' : Platform.operatingSystem;
    final buffer = StringBuffer()
      ..write('$timestamp [$phase] platform=$platform')
      ..write(' page=$page')
      ..write(' id=$id')
      ..write(' label="$label"')
      ..write(' before="$before"');
    if (after != null) buffer.write(' after="$after"');
    if (error != null) buffer.write(' error="$error"');
    if (stackTrace != null) buffer.write('\n$stackTrace');

    final line = buffer.toString();
    debugPrint(line);
    _lines.addLast(line);
    while (_lines.length > _maxLines) {
      _lines.removeFirst();
    }

    final file = _logFile;
    if (file == null) return;
    try {
      file.writeAsStringSync('$line\n', mode: FileMode.append, flush: true);
    } catch (writeError, writeStackTrace) {
      AppErrorLogger.record(
        writeError,
        writeStackTrace,
        source: 'AppActionLogger.write',
      );
    }
  }

  static List<String> recentLines() => List.unmodifiable(_lines);

  static String? get logPath => _logFile?.path;
}
