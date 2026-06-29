import 'dart:collection';
import 'dart:io';

import 'package:flutter/foundation.dart';

class AppErrorLogger {
  AppErrorLogger._();

  static const int _maxLines = 160;
  static final Queue<String> _lines = Queue<String>();

  static File? _logFile;
  static Directory? _logDirectory;

  static Future<void> initialize() async {
    if (kIsWeb) return;
    try {
      final root = Directory.systemTemp.createTempSync(
        'etoile_bridge_flutter_',
      );
      _logDirectory = root;
      _logFile = File('${root.path}${Platform.pathSeparator}app-errors.log');
      _append('Logger initialized: ${_logFile!.path}');
    } catch (error, stackTrace) {
      debugPrint('Failed to initialize app logger: $error');
      debugPrint('$stackTrace');
    }
  }

  static void record(
    Object error,
    StackTrace stackTrace, {
    String source = 'flutter',
  }) {
    _append('[$source] $error');
    _append('$stackTrace');
  }

  static List<String> recentLines() => List.unmodifiable(_lines);

  static String? get logPath => _logFile?.path;

  static String? get logDirectoryPath => _logDirectory?.path;

  static void _append(String line) {
    final timestamp = DateTime.now().toIso8601String();
    final value = '$timestamp $line';
    debugPrint(value);
    _lines.addLast(value);
    while (_lines.length > _maxLines) {
      _lines.removeFirst();
    }
    final file = _logFile;
    if (file == null) return;
    try {
      file.writeAsStringSync('$value\n', mode: FileMode.append, flush: true);
    } catch (_) {
      // Logging must never become a second failure path.
    }
  }
}
