import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../core/i18n/app_strings.dart';
import '../../core/models/single_song_models.dart';
import 'audio_preview.dart';
import 'info_row.dart';
import 'pretty_ui.dart';

enum ResourceDetailKind { image, audio, document, custom }

class ResourceDetailRow {
  const ResourceDetailRow({required this.label, required this.value});

  final String label;
  final String value;
}

void showResourceDetailOverlay(
  BuildContext context, {
  required String heroTag,
  required String title,
  required IconData icon,
  required ResourceDetailKind kind,
  ResourceInfo? resource,
  Widget? preview,
  List<ResourceDetailRow> rows = const [],
  String? statusLabel,
  PrettyPillTone statusTone = PrettyPillTone.primary,
}) {
  Navigator.of(context).push(
    PageRouteBuilder<void>(
      opaque: false,
      barrierDismissible: true,
      barrierColor: Colors.black.withValues(alpha: 0.22),
      transitionDuration: const Duration(milliseconds: 360),
      reverseTransitionDuration: const Duration(milliseconds: 260),
      pageBuilder: (context, animation, secondaryAnimation) {
        return _ResourceDetailOverlay(
          heroTag: heroTag,
          title: title,
          icon: icon,
          kind: kind,
          resource: resource,
          preview: preview,
          rows: rows,
          statusLabel: statusLabel,
          statusTone: statusTone,
        );
      },
      transitionsBuilder: (context, animation, secondaryAnimation, child) {
        final curved = CurvedAnimation(
          parent: animation,
          curve: Curves.easeOutCubic,
          reverseCurve: Curves.easeInCubic,
        );
        return FadeTransition(
          opacity: curved,
          child: ScaleTransition(
            scale: Tween<double>(begin: 0.985, end: 1).animate(curved),
            child: child,
          ),
        );
      },
    ),
  );
}

class _ResourceDetailOverlay extends StatelessWidget {
  const _ResourceDetailOverlay({
    required this.heroTag,
    required this.title,
    required this.icon,
    required this.kind,
    required this.rows,
    required this.statusTone,
    this.resource,
    this.preview,
    this.statusLabel,
  });

  final String heroTag;
  final String title;
  final IconData icon;
  final ResourceDetailKind kind;
  final ResourceInfo? resource;
  final Widget? preview;
  final List<ResourceDetailRow> rows;
  final String? statusLabel;
  final PrettyPillTone statusTone;

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.sizeOf(context);
    return Material(
      color: Colors.transparent,
      child: SafeArea(
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: () => Navigator.of(context).pop(),
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  maxWidth: math.min(920.0, size.width - 48),
                  maxHeight: math.max(260.0, size.height - 48),
                ),
                child: Hero(
                  tag: heroTag,
                  child: Material(
                    color: Colors.transparent,
                    child: GestureDetector(
                      onTap: () {},
                      child: Container(
                        decoration: BoxDecoration(
                          color: PrettyColors.panel,
                          borderRadius: BorderRadius.circular(28),
                          border: Border.all(color: PrettyColors.borderStrong),
                          boxShadow: [
                            BoxShadow(
                              color: PrettyColors.primary.withValues(
                                alpha: 0.22,
                              ),
                              blurRadius: 50,
                              offset: const Offset(0, 24),
                            ),
                          ],
                        ),
                        padding: const EdgeInsets.all(22),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Icon(icon, color: PrettyColors.primary),
                                const SizedBox(width: 10),
                                Expanded(
                                  child: Text(
                                    title,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: Theme.of(context)
                                        .textTheme
                                        .titleLarge
                                        ?.copyWith(
                                          color: PrettyColors.text,
                                          fontWeight: FontWeight.w900,
                                        ),
                                  ),
                                ),
                                PrettyPill(
                                  label:
                                      statusLabel ??
                                      _resourceStatusLabel(context, resource),
                                  tone: statusTone,
                                ),
                                const SizedBox(width: 8),
                                IconButton(
                                  tooltip: context.t('resource.closeDetails'),
                                  onPressed: () => Navigator.of(context).pop(),
                                  icon: const Icon(Icons.close_rounded),
                                ),
                              ],
                            ),
                            const SizedBox(height: 16),
                            Flexible(
                              child: SingleChildScrollView(
                                child: Column(
                                  crossAxisAlignment:
                                      CrossAxisAlignment.stretch,
                                  children: [
                                    preview ??
                                        _DefaultResourcePreview(
                                          resource: resource,
                                          kind: kind,
                                          icon: icon,
                                        ),
                                    const SizedBox(height: 16),
                                    _ResourceDetailInfo(
                                      resource: resource,
                                      rows: rows,
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _DefaultResourcePreview extends StatelessWidget {
  const _DefaultResourcePreview({
    required this.resource,
    required this.kind,
    required this.icon,
  });

  final ResourceInfo? resource;
  final ResourceDetailKind kind;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final file = resource?.path == null ? null : File(resource!.path!);
    final exists = file != null && file.existsSync();
    if (kind == ResourceDetailKind.image) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(22),
        child: Container(
          height: 360,
          constraints: const BoxConstraints(maxHeight: 460),
          color: PrettyColors.primarySofter.withValues(alpha: 0.42),
          child: exists
              ? Stack(
                  fit: StackFit.expand,
                  children: [
                    const _CheckerBackground(),
                    Image.file(
                      file,
                      fit: BoxFit.contain,
                      errorBuilder: (context, error, stackTrace) =>
                          _LargeEmptyPreview(
                            icon: icon,
                            label: context.t('previewFailed'),
                          ),
                    ),
                  ],
                )
              : _LargeEmptyPreview(
                  icon: icon,
                  label: context.t('previewFailed'),
                ),
        ),
      );
    }
    if (kind == ResourceDetailKind.audio) {
      return SizedBox(
        height: 240,
        child: resource == null
            ? _LargeEmptyPreview(
                icon: icon,
                label: context.t('resource.waveformUnavailable'),
              )
            : AudioPreviewPanel(resource: resource!),
      );
    }
    return _LargeEmptyPreview(
      icon: icon,
      label: resource?.name ?? context.t('resource.preview'),
    );
  }
}

class _ResourceDetailInfo extends StatelessWidget {
  const _ResourceDetailInfo({required this.resource, required this.rows});

  final ResourceInfo? resource;
  final List<ResourceDetailRow> rows;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final row in rows) InfoRow(label: row.label, value: row.value),
        InfoRow(label: context.t('source'), value: resource?.source ?? '-'),
        InfoRow(
          label: context.t('size'),
          value: _formatBytes(resource?.sizeBytes),
        ),
        InfoRow(
          label: context.t('resource.format'),
          value: _fileFormat(resource?.name ?? resource?.path),
        ),
        if (resource?.width != null || resource?.height != null)
          InfoRow(
            label: context.t('dimensions'),
            value: '${resource?.width ?? '-'} x ${resource?.height ?? '-'}',
          ),
        InfoRow(label: context.t('detailsPath'), value: resource?.path ?? '-'),
      ],
    );
  }
}

class _LargeEmptyPreview extends StatelessWidget {
  const _LargeEmptyPreview({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 260,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 42, color: PrettyColors.faint),
            const SizedBox(height: 10),
            Text(
              label,
              style: Theme.of(
                context,
              ).textTheme.bodyMedium?.copyWith(color: PrettyColors.muted),
            ),
          ],
        ),
      ),
    );
  }
}

class _CheckerBackground extends StatelessWidget {
  const _CheckerBackground();

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: _CheckerPainter(
        base: Theme.of(context).colorScheme.surfaceContainerHighest,
        alternate: Theme.of(
          context,
        ).colorScheme.surface.withValues(alpha: 0.85),
      ),
    );
  }
}

class _CheckerPainter extends CustomPainter {
  const _CheckerPainter({required this.base, required this.alternate});

  final Color base;
  final Color alternate;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(Offset.zero & size, Paint()..color = base);
    const cell = 16.0;
    final paint = Paint()..color = alternate;
    for (var y = 0.0; y < size.height; y += cell) {
      for (var x = 0.0; x < size.width; x += cell) {
        if (((x / cell).floor() + (y / cell).floor()).isEven) {
          canvas.drawRect(Rect.fromLTWH(x, y, cell, cell), paint);
        }
      }
    }
  }

  @override
  bool shouldRepaint(covariant _CheckerPainter oldDelegate) =>
      oldDelegate.base != base || oldDelegate.alternate != alternate;
}

String _resourceStatusLabel(BuildContext context, ResourceInfo? resource) {
  if (resource == null) return context.t('resource.missing');
  if (resource.source == 'fallback') return context.t('resource.fallback');
  return context.t('resource.identified');
}

String _formatBytes(int? bytes) {
  if (bytes == null) return '-';
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
  return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
}

String _fileFormat(String? name) {
  if (name == null || name.trim().isEmpty) return '-';
  final index = name.lastIndexOf('.');
  if (index < 0 || index == name.length - 1) return '-';
  return name.substring(index + 1).toUpperCase();
}
