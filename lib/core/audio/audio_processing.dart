import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:path/path.dart' as p;

enum ArcCreateAudioCompatibility {
  compatibleOgg,
  fakeOgg,
  unsupportedFormat,
  damaged,
  unknown,
}

class AudioCompatibilityReport {
  const AudioCompatibilityReport({
    required this.status,
    this.extension,
    this.container,
    this.codec,
    this.reason,
    this.toolMissing = false,
  });

  final ArcCreateAudioCompatibility status;
  final String? extension;
  final String? container;
  final String? codec;
  final String? reason;
  final bool toolMissing;

  bool get canExportDirectly =>
      status == ArcCreateAudioCompatibility.compatibleOgg;

  bool get canConvert =>
      !toolMissing &&
      (status == ArcCreateAudioCompatibility.fakeOgg ||
          status == ArcCreateAudioCompatibility.unsupportedFormat);

  bool get blocksExport =>
      status == ArcCreateAudioCompatibility.damaged ||
      status == ArcCreateAudioCompatibility.unknown ||
      (toolMissing && !canExportDirectly);
}

class AudioAnalysis {
  const AudioAnalysis({
    required this.path,
    required this.peaks,
    required this.compatibility,
    this.duration,
    this.sizeBytes,
  });

  final String path;
  final List<double> peaks;
  final Duration? duration;
  final int? sizeBytes;
  final AudioCompatibilityReport compatibility;
}

class AudioConversionResult {
  const AudioConversionResult({
    required this.outputPath,
    required this.analysis,
  });

  final String outputPath;
  final AudioAnalysis analysis;
}

class AudioProcessingException implements Exception {
  AudioProcessingException(this.message, {this.details});

  final String message;
  final String? details;

  @override
  String toString() => details == null ? message : '$message: $details';
}

class AudioProcessingService {
  AudioProcessingService._();

  static final instance = AudioProcessingService._();

  final _analysisCache = <String, Future<AudioAnalysis>>{};
  final _toolCache = <String, Future<String?>>{};

  Future<AudioAnalysis> analyze(String? path, {int peakCount = 160}) async {
    if (path == null || path.trim().isEmpty) {
      return AudioAnalysis(
        path: '',
        peaks: const [],
        compatibility: const AudioCompatibilityReport(
          status: ArcCreateAudioCompatibility.unknown,
          reason: 'Missing audio path.',
        ),
      );
    }
    final key = await _cacheKey(path, peakCount);
    return _analysisCache.putIfAbsent(key, () => _analyze(path, peakCount));
  }

  void invalidate(String path) {
    _analysisCache.removeWhere((key, _) => key.startsWith('$path|'));
  }

  Future<AudioConversionResult> convertToCompatibleOgg({
    required String inputPath,
    required String outputPath,
  }) async {
    final ffmpeg = await _resolveTool('ffmpeg');
    if (ffmpeg == null) {
      throw AudioProcessingException('ffmpeg was not found.');
    }
    final output = File(outputPath);
    output.parent.createSync(recursive: true);
    final samePath = p.equals(p.normalize(inputPath), p.normalize(outputPath));
    final actualOutput = samePath
        ? p.join(
            output.parent.path,
            '${p.basenameWithoutExtension(output.path)}.etoilebridge-converting.ogg',
          )
        : output.path;
    final result = await Process.run(ffmpeg, [
      '-y',
      '-hide_banner',
      '-v',
      'error',
      '-i',
      inputPath,
      '-vn',
      '-map_metadata',
      '-1',
      '-acodec',
      'libvorbis',
      '-q:a',
      '5',
      actualOutput,
    ], stderrEncoding: utf8);
    if (result.exitCode != 0) {
      throw AudioProcessingException(
        'Audio conversion failed.',
        details: (result.stderr as String?)?.trim(),
      );
    }
    if (samePath) {
      File(actualOutput).copySync(output.path);
      File(actualOutput).deleteSync();
    }
    invalidate(output.path);
    final analysis = await analyze(output.path, peakCount: 192);
    if (!analysis.compatibility.canExportDirectly) {
      throw AudioProcessingException(
        'Converted audio is still not ArcCreate-compatible.',
        details: analysis.compatibility.reason,
      );
    }
    return AudioConversionResult(outputPath: output.path, analysis: analysis);
  }

  Future<AudioAnalysis> _analyze(String path, int peakCount) async {
    final file = File(path);
    if (!await file.exists()) {
      return AudioAnalysis(
        path: path,
        peaks: const [],
        compatibility: AudioCompatibilityReport(
          status: ArcCreateAudioCompatibility.damaged,
          extension: _extension(path),
          reason: 'Audio file does not exist.',
        ),
      );
    }
    final stat = await file.stat();
    final probe = await _probe(path);
    final compatibility = _compatibility(path, probe);
    final peaks = compatibility.status == ArcCreateAudioCompatibility.damaged
        ? const <double>[]
        : await _extractPeaks(path, peakCount);
    return AudioAnalysis(
      path: path,
      peaks: peaks,
      duration: probe.duration,
      sizeBytes: stat.size,
      compatibility: compatibility,
    );
  }

  Future<_ProbeResult> _probe(String path) async {
    final ffprobe = await _resolveTool('ffprobe');
    if (ffprobe == null) {
      return const _ProbeResult(toolMissing: true);
    }
    try {
      final result = await Process.run(
        ffprobe,
        [
          '-v',
          'error',
          '-show_entries',
          'format=format_name,duration:stream=codec_name,codec_type,sample_rate,channels',
          '-of',
          'json',
          path,
        ],
        stdoutEncoding: utf8,
        stderrEncoding: utf8,
      ).timeout(const Duration(seconds: 12));
      if (result.exitCode != 0) {
        return _ProbeResult(error: (result.stderr as String?)?.trim());
      }
      final decoded =
          jsonDecode(result.stdout as String) as Map<String, Object?>;
      final streams = decoded['streams'] as List<Object?>? ?? const [];
      final audioStream = streams
          .whereType<Map<String, Object?>>()
          .where((stream) => stream['codec_type'] == 'audio')
          .cast<Map<String, Object?>>()
          .firstOrNull;
      final format = decoded['format'] as Map<String, Object?>?;
      final durationText = format?['duration']?.toString();
      return _ProbeResult(
        container: format?['format_name']?.toString(),
        codec: audioStream?['codec_name']?.toString(),
        duration: _durationFromSeconds(durationText),
        hasAudio: audioStream != null,
      );
    } catch (error) {
      return _ProbeResult(error: error.toString());
    }
  }

  AudioCompatibilityReport _compatibility(String path, _ProbeResult probe) {
    final extension = _extension(path);
    if (probe.toolMissing) {
      final looksOgg = _looksLikeOgg(path);
      return AudioCompatibilityReport(
        status: looksOgg
            ? ArcCreateAudioCompatibility.unknown
            : ArcCreateAudioCompatibility.unknown,
        extension: extension,
        reason:
            'ffprobe is not available, so the real container and codec could not be checked.',
        toolMissing: true,
      );
    }
    if (probe.error != null || !probe.hasAudio) {
      return AudioCompatibilityReport(
        status: ArcCreateAudioCompatibility.damaged,
        extension: extension,
        container: probe.container,
        codec: probe.codec,
        reason: probe.error ?? 'No audio stream was found.',
      );
    }
    final container = (probe.container ?? '').toLowerCase();
    final codec = (probe.codec ?? '').toLowerCase();
    final isOggContainer =
        container.split(',').contains('ogg') || container.contains('ogg');
    if (extension == 'ogg' && isOggContainer && codec == 'vorbis') {
      return AudioCompatibilityReport(
        status: ArcCreateAudioCompatibility.compatibleOgg,
        extension: extension,
        container: probe.container,
        codec: probe.codec,
        reason: 'Ogg Vorbis is compatible with ArcCreate.',
      );
    }
    if (extension == 'ogg') {
      return AudioCompatibilityReport(
        status: ArcCreateAudioCompatibility.fakeOgg,
        extension: extension,
        container: probe.container,
        codec: probe.codec,
        reason: isOggContainer
            ? 'This .ogg file is not Vorbis; ArcCreate expects Ogg Vorbis.'
            : 'The extension is .ogg, but the real container is not OGG.',
      );
    }
    return AudioCompatibilityReport(
      status: ArcCreateAudioCompatibility.unsupportedFormat,
      extension: extension,
      container: probe.container,
      codec: probe.codec,
      reason:
          'ArcCreate expects Ogg Vorbis. This file must be converted before export.',
    );
  }

  Future<List<double>> _extractPeaks(String path, int peakCount) async {
    final ffmpeg = await _resolveTool('ffmpeg');
    if (ffmpeg == null) return const [];
    try {
      final result = await Process.run(
        ffmpeg,
        [
          '-hide_banner',
          '-v',
          'error',
          '-i',
          path,
          '-vn',
          '-ac',
          '1',
          '-ar',
          '8000',
          '-f',
          's16le',
          'pipe:1',
        ],
        stdoutEncoding: null,
        stderrEncoding: utf8,
      ).timeout(const Duration(seconds: 18));
      if (result.exitCode != 0) return const [];
      final bytes = result.stdout is Uint8List
          ? result.stdout as Uint8List
          : Uint8List.fromList((result.stdout as List).cast<int>());
      return _peaksFromPcm(bytes, peakCount);
    } catch (_) {
      return const [];
    }
  }

  List<double> _peaksFromPcm(Uint8List bytes, int peakCount) {
    final sampleCount = bytes.length ~/ 2;
    if (sampleCount <= 0) return const [];
    final data = ByteData.sublistView(bytes);
    final bucketSize = math.max(1, sampleCount ~/ peakCount);
    final peaks = <double>[];
    var maxPeak = 0.0;
    for (var start = 0; start < sampleCount; start += bucketSize) {
      var peak = 0.0;
      final end = math.min(sampleCount, start + bucketSize);
      for (var i = start; i < end; i++) {
        final value = data.getInt16(i * 2, Endian.little).abs() / 32768.0;
        if (value > peak) peak = value;
      }
      peaks.add(peak);
      if (peak > maxPeak) maxPeak = peak;
    }
    if (maxPeak <= 0) return peaks;
    return peaks.map((peak) => (peak / maxPeak).clamp(0.04, 1.0)).toList();
  }

  Future<String?> _resolveTool(String name) {
    return _toolCache.putIfAbsent(name, () async {
      final executableName = Platform.isWindows ? '$name.exe' : name;
      final known = <String>[
        if (Platform.isWindows)
          'E:\\ffmpeg-8.0-full_build\\bin\\$executableName',
        if (Platform.isWindows) 'E:\\ffmpeg\\bin\\$executableName',
      ];
      for (final path in known) {
        if (File(path).existsSync()) return path;
      }
      final locator = Platform.isWindows ? 'where' : 'which';
      try {
        final result = await Process.run(
          locator,
          [name],
          stdoutEncoding: utf8,
          stderrEncoding: utf8,
          runInShell: true,
        ).timeout(const Duration(seconds: 4));
        if (result.exitCode == 0) {
          final lines = (result.stdout as String)
              .split(RegExp(r'\r?\n'))
              .map((line) => line.trim())
              .where((line) => line.isNotEmpty);
          for (final line in lines) {
            if (File(line).existsSync()) return line;
          }
        }
      } catch (_) {
        // Fall through to null; UI will expose conversion/check limitations.
      }
      return null;
    });
  }

  Future<String> _cacheKey(String path, int peakCount) async {
    final file = File(path);
    try {
      final stat = await file.stat();
      return '$path|${stat.modified.millisecondsSinceEpoch}|${stat.size}|$peakCount';
    } catch (_) {
      return '$path|missing|$peakCount';
    }
  }
}

class _ProbeResult {
  const _ProbeResult({
    this.container,
    this.codec,
    this.duration,
    this.hasAudio = false,
    this.toolMissing = false,
    this.error,
  });

  final String? container;
  final String? codec;
  final Duration? duration;
  final bool hasAudio;
  final bool toolMissing;
  final String? error;
}

extension _FirstOrNull<T> on Iterable<T> {
  T? get firstOrNull {
    final iterator = this.iterator;
    if (!iterator.moveNext()) return null;
    return iterator.current;
  }
}

Duration? _durationFromSeconds(String? value) {
  if (value == null) return null;
  final seconds = double.tryParse(value);
  if (seconds == null || !seconds.isFinite || seconds <= 0) return null;
  return Duration(milliseconds: (seconds * 1000).round());
}

String _extension(String path) =>
    p.extension(path).replaceFirst('.', '').toLowerCase();

bool _looksLikeOgg(String path) {
  try {
    final file = File(path);
    if (!file.existsSync()) return false;
    final bytes = file.openSync()..setPositionSync(0);
    final header = bytes.readSync(4);
    bytes.closeSync();
    return header.length == 4 && String.fromCharCodes(header) == 'OggS';
  } catch (_) {
    return false;
  }
}
