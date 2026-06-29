import 'dart:io';
import 'dart:typed_data';

import 'package:etoile_bridge/core/audio/audio_processing.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  late Directory tempDir;
  final service = AudioProcessingService.instance;

  setUp(() {
    tempDir = Directory.systemTemp.createTempSync('etoile_audio_test_');
    service.debugForceHeaderProbeForTesting = true;
  });

  tearDown(() {
    service.debugForceHeaderProbeForTesting = false;
    if (tempDir.existsSync()) {
      tempDir.deleteSync(recursive: true);
    }
  });

  test(
    'Ogg Vorbis header stays exportable when ffprobe is unavailable',
    () async {
      final file = File('${tempDir.path}/base.ogg')
        ..writeAsBytesSync(
          Uint8List.fromList([
            ...'OggS'.codeUnits,
            ...List<int>.filled(24, 0),
            0x01,
            ...'vorbis'.codeUnits,
            ...List<int>.filled(64, 0),
          ]),
        );
      service.invalidate(file.path);

      final analysis = await service.analyze(file.path, peakCount: 8);
      final report = analysis.compatibility;

      expect(report.status, ArcCreateAudioCompatibility.compatibleOgg);
      expect(report.container, 'ogg');
      expect(report.codec, 'vorbis');
      expect(report.canExportDirectly, isTrue);
      expect(report.blocksExport, isFalse);
      expect(report.toolMissing, isFalse);
    },
  );

  test(
    'fake ogg is detected from header when ffprobe is unavailable',
    () async {
      final file = File('${tempDir.path}/fake.ogg')
        ..writeAsBytesSync(
          Uint8List.fromList([...'ID3'.codeUnits, ...List<int>.filled(64, 0)]),
        );
      service.invalidate(file.path);

      final analysis = await service.analyze(file.path, peakCount: 8);
      final report = analysis.compatibility;

      expect(report.status, ArcCreateAudioCompatibility.fakeOgg);
      expect(report.container, 'mp3');
      expect(report.canExportDirectly, isFalse);
    },
  );

  test('non-ogg audio is marked unsupported but convertible', () async {
    final file = File('${tempDir.path}/audio.wav')
      ..writeAsBytesSync(
        Uint8List.fromList([
          ...'RIFF'.codeUnits,
          0,
          0,
          0,
          0,
          ...'WAVE'.codeUnits,
          ...List<int>.filled(64, 0),
        ]),
      );
    service.invalidate(file.path);

    final analysis = await service.analyze(file.path, peakCount: 8);
    final report = analysis.compatibility;

    expect(report.status, ArcCreateAudioCompatibility.unsupportedFormat);
    expect(report.container, 'wav');
    expect(report.canExportDirectly, isFalse);
    expect(report.canConvert, isTrue);
  });
}
