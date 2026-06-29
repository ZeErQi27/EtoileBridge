import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_soloud/flutter_soloud.dart';
import 'package:path/path.dart' as p;

import '../../core/audio/audio_processing.dart';
import '../../core/i18n/app_strings.dart';
import '../../core/models/single_song_models.dart';
import 'pretty_ui.dart';

typedef AudioConversionCallback =
    Future<ResourceInfo?> Function(ResourceInfo resource);

class AudioPreviewPanel extends StatefulWidget {
  const AudioPreviewPanel({
    required this.resource,
    super.key,
    this.compact = false,
    this.onConvertToCompatibleOgg,
  });

  final ResourceInfo resource;
  final bool compact;
  final AudioConversionCallback? onConvertToCompatibleOgg;

  @override
  State<AudioPreviewPanel> createState() => _AudioPreviewPanelState();
}

class _AudioPreviewPanelState extends State<AudioPreviewPanel> {
  late Future<AudioAnalysis> _future;
  bool _converting = false;
  String? _conversionError;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  @override
  void didUpdateWidget(covariant AudioPreviewPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.resource.path != widget.resource.path ||
        oldWidget.resource.sizeBytes != widget.resource.sizeBytes) {
      _conversionError = null;
      _future = _load();
      if (oldWidget.resource.path != widget.resource.path) {
        unawaited(
          SoLoudAudioPlaybackController.instance.stopIfDifferent(
            widget.resource.path,
          ),
        );
      }
    }
  }

  Future<AudioAnalysis> _load() {
    return AudioProcessingService.instance.analyze(
      widget.resource.path,
      peakCount: widget.compact ? 96 : 220,
    );
  }

  @override
  Widget build(BuildContext context) {
    final name = widget.resource.name ?? widget.resource.path ?? '-';
    return FutureBuilder<AudioAnalysis>(
      future: _future,
      builder: (context, snapshot) {
        final loading = snapshot.connectionState == ConnectionState.waiting;
        final analysis = snapshot.data;
        final playback = SoLoudAudioPlaybackController.instance;
        return AnimatedBuilder(
          animation: playback,
          builder: (context, _) {
            final path = widget.resource.path;
            final active = playback.activePath == path;
            final playing = active && playback.isPlaying;
            final position = active ? playback.position : Duration.zero;
            final duration = active
                ? playback.duration ?? analysis?.duration
                : analysis?.duration;
            final progress = _progress(position, duration);
            final compatibility = analysis?.compatibility;
            final hasAudioPath = widget.resource.path?.isNotEmpty == true;
            return Container(
              padding: EdgeInsets.all(widget.compact ? 8 : 16),
              decoration: BoxDecoration(
                color: PrettyColors.primarySofter.withValues(alpha: 0.46),
                borderRadius: BorderRadius.circular(widget.compact ? 16 : 22),
                border: Border.all(color: PrettyColors.border),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _AudioHeader(
                    name: name,
                    resource: widget.resource,
                    analysis: analysis,
                    compatibility: compatibility,
                    compact: widget.compact,
                    playing: playing,
                    onToggle: path == null
                        ? null
                        : () => _togglePlayback(analysis),
                  ),
                  SizedBox(height: widget.compact ? 6 : 12),
                  Expanded(
                    child: _WaveformBox(
                      loading: loading,
                      peaks: analysis?.peaks ?? const [],
                      progress: progress,
                      compact: widget.compact,
                      onSeekFraction: duration == null
                          ? null
                          : (fraction) => playback.seek(
                              Duration(
                                milliseconds:
                                    (duration.inMilliseconds * fraction)
                                        .round(),
                              ),
                            ),
                    ),
                  ),
                  SizedBox(height: widget.compact ? 2 : 12),
                  _AudioSeekBar(
                    compact: widget.compact,
                    position: position,
                    duration: duration,
                    onChanged: duration == null
                        ? null
                        : (value) => playback.seek(
                            Duration(milliseconds: value.round()),
                          ),
                  ),
                  if (hasAudioPath &&
                      compatibility != null &&
                      (_shouldShowCompatibilityFooter(compatibility) ||
                          _conversionError != null)) ...[
                    SizedBox(height: widget.compact ? 4 : 8),
                    _AudioCompatibilityFooter(
                      report: compatibility,
                      compact: widget.compact,
                      converting: _converting,
                      conversionError: _conversionError,
                      onConvert: _canConvert(compatibility) ? _convert : null,
                    ),
                  ],
                  if (!widget.compact) ...[
                    const SizedBox(height: 10),
                    _AudioDetails(
                      analysis: analysis,
                      resource: widget.resource,
                    ),
                  ],
                ],
              ),
            );
          },
        );
      },
    );
  }

  bool _canConvert(AudioCompatibilityReport report) {
    return widget.onConvertToCompatibleOgg != null &&
        report.canConvert &&
        !_converting;
  }

  bool _shouldShowCompatibilityFooter(AudioCompatibilityReport report) {
    return report.status != ArcCreateAudioCompatibility.compatibleOgg ||
        (widget.onConvertToCompatibleOgg != null && report.canConvert);
  }

  Future<void> _togglePlayback(AudioAnalysis? analysis) async {
    final path = widget.resource.path;
    if (path == null) return;
    final playback = SoLoudAudioPlaybackController.instance;
    if (playback.activePath == path && playback.isPlaying) {
      await playback.pause();
    } else {
      await playback.play(path, fallbackDuration: analysis?.duration);
    }
  }

  Future<void> _convert() async {
    final callback = widget.onConvertToCompatibleOgg;
    if (callback == null || _converting) return;
    setState(() {
      _converting = true;
      _conversionError = null;
    });
    try {
      await callback(widget.resource);
      if (!mounted) return;
      AudioProcessingService.instance.invalidate(widget.resource.path ?? '');
      setState(() {
        _future = _load();
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _conversionError = error.toString();
      });
    } finally {
      if (mounted) {
        setState(() {
          _converting = false;
        });
      }
    }
  }
}

class SoLoudAudioPlaybackController extends ChangeNotifier {
  SoLoudAudioPlaybackController._();

  static final instance = SoLoudAudioPlaybackController._();

  SoLoud? _soloud;

  String? activePath;
  bool isPlaying = false;
  Duration? duration;
  Duration position = Duration.zero;
  Object? error;

  AudioSource? _source;
  SoundHandle? _handle;
  Timer? _poller;

  Future<void> play(String path, {Duration? fallbackDuration}) async {
    try {
      await _ensureEngine();
      if (activePath != path) {
        await _load(path, fallbackDuration: fallbackDuration);
      }
      if (_source == null) return;
      final soloud = _engine;
      if (_handle == null || !soloud.getIsValidVoiceHandle(_handle!)) {
        _handle = soloud.play(_source!);
      } else {
        soloud.setPause(_handle!, false);
      }
      isPlaying = true;
      error = null;
      _startPolling();
      notifyListeners();
    } catch (exception) {
      _fail(exception);
    }
  }

  Future<void> pause() async {
    try {
      final soloud = _soloud;
      if (soloud != null &&
          _handle != null &&
          soloud.getIsValidVoiceHandle(_handle!)) {
        soloud.setPause(_handle!, true);
      }
      isPlaying = false;
      notifyListeners();
    } catch (exception) {
      _fail(exception);
    }
  }

  Future<void> seek(Duration value) async {
    if (_handle == null) return;
    try {
      position = value;
      final soloud = _soloud;
      if (soloud != null && soloud.getIsValidVoiceHandle(_handle!)) {
        soloud.seek(_handle!, value);
      }
      notifyListeners();
    } catch (exception) {
      _fail(exception);
    }
  }

  Future<void> stopIfDifferent(String? path) async {
    if (activePath != null && activePath != path) {
      await stop();
    }
  }

  Future<void> stop() async {
    _poller?.cancel();
    try {
      final soloud = _soloud;
      if (soloud != null &&
          _handle != null &&
          soloud.getIsValidVoiceHandle(_handle!)) {
        await soloud.stop(_handle!);
      }
      if (soloud != null && _source != null) {
        await soloud.disposeSource(_source!);
      }
    } catch (_) {
      // Audio preview cleanup is best-effort.
    }
    activePath = null;
    isPlaying = false;
    duration = null;
    position = Duration.zero;
    error = null;
    _source = null;
    _handle = null;
    notifyListeners();
  }

  Future<void> _ensureEngine() async {
    final soloud = _engine;
    if (!soloud.isInitialized) {
      await soloud.init();
    }
  }

  Future<void> _load(String path, {Duration? fallbackDuration}) async {
    await stop();
    await _ensureEngine();
    final soloud = _engine;
    _source = await soloud.loadFile(path, mode: LoadMode.memory);
    activePath = path;
    duration = soloud.getLength(_source!);
    if (duration == Duration.zero) duration = fallbackDuration;
    position = Duration.zero;
    isPlaying = false;
    error = null;
    notifyListeners();
  }

  void _startPolling() {
    _poller?.cancel();
    _poller = Timer.periodic(const Duration(milliseconds: 180), (_) {
      _refreshPosition();
    });
  }

  void _refreshPosition() {
    final handle = _handle;
    if (handle == null) return;
    try {
      final soloud = _soloud;
      if (soloud == null || !soloud.getIsValidVoiceHandle(handle)) {
        isPlaying = false;
        notifyListeners();
        return;
      }
      position = soloud.getPosition(handle);
      isPlaying = !soloud.getPause(handle);
      if (duration != null && position >= duration!) {
        isPlaying = false;
      }
      notifyListeners();
    } catch (_) {
      // Polling can fail while the source is changing; the next tick recovers.
    }
  }

  void _fail(Object exception) {
    error = exception;
    isPlaying = false;
    _poller?.cancel();
    notifyListeners();
  }

  SoLoud get _engine => _soloud ??= SoLoud.instance;
}

class _AudioHeader extends StatelessWidget {
  const _AudioHeader({
    required this.name,
    required this.resource,
    required this.analysis,
    required this.compatibility,
    required this.compact,
    required this.playing,
    required this.onToggle,
  });

  final String name;
  final ResourceInfo resource;
  final AudioAnalysis? analysis;
  final AudioCompatibilityReport? compatibility;
  final bool compact;
  final bool playing;
  final VoidCallback? onToggle;

  @override
  Widget build(BuildContext context) {
    final duration = analysis?.duration;
    return Row(
      children: [
        IconButton.filledTonal(
          visualDensity: VisualDensity.compact,
          style: IconButton.styleFrom(
            minimumSize: Size.square(compact ? 32 : 42),
            padding: EdgeInsets.zero,
            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
          ),
          tooltip: playing ? context.t('pause') : context.t('play'),
          onPressed: onToggle,
          icon: Icon(playing ? Icons.pause_rounded : Icons.play_arrow_rounded),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                p.basename(name),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: PrettyColors.text,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                [
                  _fileFormat(name),
                  _formatBytes(analysis?.sizeBytes ?? resource.sizeBytes),
                  if (duration != null) _formatDuration(duration),
                ].where((item) => item != '-').join(' / '),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: PrettyColors.muted,
                  fontSize: compact ? 11 : null,
                ),
              ),
            ],
          ),
        ),
        if (compatibility != null) ...[
          const SizedBox(width: 8),
          _AudioCompatibilityPill(report: compatibility!),
        ],
      ],
    );
  }
}

class _AudioCompatibilityPill extends StatelessWidget {
  const _AudioCompatibilityPill({required this.report});

  final AudioCompatibilityReport report;

  @override
  Widget build(BuildContext context) {
    return PrettyPill(
      label: _compatLabel(context, report),
      tone: _compatTone(report.status),
    );
  }
}

class _AudioCompatibilityFooter extends StatelessWidget {
  const _AudioCompatibilityFooter({
    required this.report,
    required this.compact,
    required this.converting,
    required this.onConvert,
    this.conversionError,
  });

  final AudioCompatibilityReport report;
  final bool compact;
  final bool converting;
  final String? conversionError;
  final VoidCallback? onConvert;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(
            _compatReason(context, report),
            maxLines: compact ? 1 : 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: PrettyColors.muted,
              fontWeight: FontWeight.w700,
              fontSize: compact ? 11 : null,
            ),
          ),
        ),
        if (onConvert != null) ...[
          const SizedBox(width: 8),
          TextButton.icon(
            style: TextButton.styleFrom(
              visualDensity: VisualDensity.compact,
              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              padding: const EdgeInsets.symmetric(horizontal: 8),
            ),
            onPressed: converting ? null : onConvert,
            icon: converting
                ? const SizedBox(
                    width: 14,
                    height: 14,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.autorenew_rounded, size: 16),
            label: Text(
              converting
                  ? context.t('resource.audio.converting')
                  : context.t('resource.audio.convertToOgg'),
            ),
          ),
        ],
        if (conversionError != null && !compact) ...[
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              conversionError!,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: PrettyColors.danger,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ],
      ],
    );
  }
}

class _WaveformBox extends StatelessWidget {
  const _WaveformBox({
    required this.loading,
    required this.peaks,
    required this.progress,
    required this.compact,
    required this.onSeekFraction,
  });

  final bool loading;
  final List<double> peaks;
  final double progress;
  final bool compact;
  final ValueChanged<double>? onSeekFraction;

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Center(
        child: SizedBox(
          width: 22,
          height: 22,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      );
    }
    if (peaks.isEmpty) {
      return _AudioUnavailableMessage(
        message: context.t('resource.waveformUnavailable'),
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) {
        void seek(Offset localPosition) {
          final width = math.max(1.0, constraints.maxWidth);
          onSeekFraction?.call((localPosition.dx / width).clamp(0.0, 1.0));
        }

        return GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTapDown: onSeekFraction == null
              ? null
              : (details) => seek(details.localPosition),
          onHorizontalDragUpdate: onSeekFraction == null
              ? null
              : (details) => seek(details.localPosition),
          child: CustomPaint(
            painter: RealWaveformPainter(
              peaks: peaks,
              progress: progress,
              color: PrettyColors.primary,
              backgroundColor: PrettyColors.primary.withValues(alpha: 0.22),
            ),
          ),
        );
      },
    );
  }
}

class _AudioSeekBar extends StatelessWidget {
  const _AudioSeekBar({
    required this.compact,
    required this.position,
    required this.duration,
    required this.onChanged,
  });

  final bool compact;
  final Duration position;
  final Duration? duration;
  final ValueChanged<double>? onChanged;

  @override
  Widget build(BuildContext context) {
    final maxMs = math.max<double>(duration?.inMilliseconds.toDouble() ?? 1, 1);
    final positionMs = position.inMilliseconds
        .clamp(0, maxMs.toInt())
        .toDouble();
    return Row(
      children: [
        if (!compact)
          Text(
            _formatDuration(position),
            style: Theme.of(
              context,
            ).textTheme.labelSmall?.copyWith(color: PrettyColors.muted),
          ),
        Expanded(
          child: SizedBox(
            height: compact ? 24 : 38,
            child: SliderTheme(
              data: SliderTheme.of(context).copyWith(
                trackHeight: 3,
                thumbShape: const RoundSliderThumbShape(
                  enabledThumbRadius: 5,
                  disabledThumbRadius: 5,
                ),
              ),
              child: Slider(
                value: positionMs,
                onChanged: onChanged,
                min: 0,
                max: maxMs,
              ),
            ),
          ),
        ),
        if (!compact)
          Text(
            duration == null ? '--:--' : _formatDuration(duration!),
            style: Theme.of(
              context,
            ).textTheme.labelSmall?.copyWith(color: PrettyColors.muted),
          ),
      ],
    );
  }
}

class _AudioDetails extends StatelessWidget {
  const _AudioDetails({required this.analysis, required this.resource});

  final AudioAnalysis? analysis;
  final ResourceInfo resource;

  @override
  Widget build(BuildContext context) {
    final report = analysis?.compatibility;
    final rows = <String, String>{
      context.t('resource.format'): [
        report?.container,
        report?.codec,
      ].whereType<String>().where((value) => value.isNotEmpty).join(' / '),
      context.t('resource.duration'): analysis?.duration == null
          ? '-'
          : _formatDuration(analysis!.duration!),
      context.t('path'): resource.path ?? '-',
    };
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: rows.entries
          .where((entry) => entry.value.isNotEmpty)
          .map(
            (entry) => Padding(
              padding: const EdgeInsets.only(bottom: 5),
              child: Row(
                children: [
                  SizedBox(
                    width: 84,
                    child: Text(
                      entry.key,
                      style: Theme.of(context).textTheme.labelSmall?.copyWith(
                        color: PrettyColors.muted,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  Expanded(
                    child: Text(
                      entry.value,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: PrettyColors.text,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          )
          .toList(),
    );
  }
}

class _AudioUnavailableMessage extends StatelessWidget {
  const _AudioUnavailableMessage({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Text(
        message,
        textAlign: TextAlign.center,
        style: Theme.of(context).textTheme.bodySmall?.copyWith(
          color: PrettyColors.muted,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

class RealWaveformPainter extends CustomPainter {
  const RealWaveformPainter({
    required this.peaks,
    required this.progress,
    required this.color,
    required this.backgroundColor,
  });

  final List<double> peaks;
  final double progress;
  final Color color;
  final Color backgroundColor;

  @override
  void paint(Canvas canvas, Size size) {
    if (peaks.isEmpty || size.isEmpty) return;
    final count = peaks.length;
    final strokeWidth = math.max(1.8, size.width / count * 0.42);
    final playedUntil = (count * progress.clamp(0.0, 1.0)).round();
    for (var index = 0; index < count; index++) {
      final paint = Paint()
        ..color = index <= playedUntil ? color : backgroundColor
        ..strokeWidth = strokeWidth
        ..strokeCap = StrokeCap.round;
      final x = (index + 0.5) * size.width / count;
      final height = math.max(2.0, size.height * peaks[index].clamp(0.0, 1.0));
      canvas.drawLine(
        Offset(x, (size.height - height) / 2),
        Offset(x, (size.height + height) / 2),
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant RealWaveformPainter oldDelegate) {
    return oldDelegate.peaks != peaks ||
        oldDelegate.progress != progress ||
        oldDelegate.color != color ||
        oldDelegate.backgroundColor != backgroundColor;
  }
}

double _progress(Duration position, Duration? duration) {
  if (duration == null || duration.inMilliseconds <= 0) return 0;
  return (position.inMilliseconds / duration.inMilliseconds).clamp(0.0, 1.0);
}

PrettyPillTone _compatTone(ArcCreateAudioCompatibility status) {
  return switch (status) {
    ArcCreateAudioCompatibility.compatibleOgg => PrettyPillTone.success,
    ArcCreateAudioCompatibility.fakeOgg ||
    ArcCreateAudioCompatibility.unsupportedFormat => PrettyPillTone.warning,
    ArcCreateAudioCompatibility.damaged ||
    ArcCreateAudioCompatibility.unknown => PrettyPillTone.danger,
  };
}

String _compatLabel(BuildContext context, AudioCompatibilityReport report) {
  return switch (report.status) {
    ArcCreateAudioCompatibility.compatibleOgg => context.t(
      'resource.audio.compatibleOgg',
    ),
    ArcCreateAudioCompatibility.fakeOgg => context.t('resource.audio.fakeOgg'),
    ArcCreateAudioCompatibility.unsupportedFormat => context.t(
      'resource.audio.unsupportedFormat',
    ),
    ArcCreateAudioCompatibility.damaged => context.t('resource.audio.damaged'),
    ArcCreateAudioCompatibility.unknown => context.t('resource.audio.unknown'),
  };
}

String _compatReason(BuildContext context, AudioCompatibilityReport report) {
  if (report.toolMissing) return context.t('resource.audio.ffmpegMissing');
  return switch (report.status) {
    ArcCreateAudioCompatibility.compatibleOgg => context.t(
      'resource.audio.compatibleReason',
    ),
    ArcCreateAudioCompatibility.fakeOgg => context.t(
      'resource.audio.fakeOggReason',
    ),
    ArcCreateAudioCompatibility.unsupportedFormat => context.t(
      'resource.audio.unsupportedReason',
    ),
    ArcCreateAudioCompatibility.damaged => context.t(
      'resource.audio.damagedReason',
    ),
    ArcCreateAudioCompatibility.unknown =>
      report.reason ?? context.t('resource.audio.damagedReason'),
  };
}

String _fileFormat(String? value) {
  if (value == null || value.trim().isEmpty) return '-';
  final extension = p.extension(value).replaceFirst('.', '').toUpperCase();
  return extension.isEmpty ? '-' : extension;
}

String _formatBytes(int? bytes) {
  if (bytes == null) return '-';
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
  return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
}

String _formatDuration(Duration duration) {
  final minutes = duration.inMinutes.remainder(60).toString().padLeft(2, '0');
  final seconds = duration.inSeconds.remainder(60).toString().padLeft(2, '0');
  if (duration.inHours > 0) {
    return '${duration.inHours}:$minutes:$seconds';
  }
  return '$minutes:$seconds';
}
