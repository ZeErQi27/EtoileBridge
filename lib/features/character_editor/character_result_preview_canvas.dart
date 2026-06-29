import 'dart:io';

import 'package:flutter/material.dart';

import '../../core/i18n/app_strings.dart';
import '../../shared/widgets/info_row.dart';
import '../../shared/widgets/pretty_ui.dart';
import 'character_editor_state.dart';
import 'character_result_assets.dart';
import 'character_result_layout.dart';
import 'character_result_preview_mapper.dart';

class ArcCreateResultPreviewCanvas extends StatelessWidget {
  const ArcCreateResultPreviewCanvas({required this.page, super.key});

  final CharacterEditorState page;

  @override
  Widget build(BuildContext context) {
    final imagePath = page.imagePath;
    final hasPreviewImage = page.hasPreviewImage;
    final imageWidth = (page.scan?.image?.width ?? 1152).toDouble();
    final imageHeight = (page.scan?.image?.height ?? 2048).toDouble();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AspectRatio(
          aspectRatio:
              CharacterPreviewCoordinateMapper.resultLogicalWidth /
              CharacterPreviewCoordinateMapper.resultLogicalHeight,
          child: LayoutBuilder(
            builder: (context, constraints) {
              final width = constraints.maxWidth.isFinite
                  ? constraints.maxWidth
                  : CharacterPreviewCoordinateMapper.resultLogicalWidth;
              final height = constraints.maxHeight.isFinite
                  ? constraints.maxHeight
                  : CharacterPreviewCoordinateMapper.resultLogicalHeight;
              final placement = hasPreviewImage
                  ? CharacterPreviewCoordinateMapper.map(
                      canvasWidth: width,
                      canvasHeight: height,
                      imageWidth: imageWidth,
                      imageHeight: imageHeight,
                      x: page.x,
                      y: page.y,
                      scale: page.scale,
                    )
                  : null;
              final transform =
                  CharacterPreviewCoordinateMapper.displayTransform(
                    canvasWidth: width,
                    canvasHeight: height,
                  );
              return ClipRRect(
                borderRadius: BorderRadius.circular(18),
                child: ColoredBox(
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                  child: Stack(
                    clipBehavior: Clip.hardEdge,
                    children: [
                      _ResultBaseSurface(transform: transform),
                      for (final layer
                          in CharacterResultLayout.backgroundLayers)
                        _TextureLayer(layer: layer, transform: transform),
                      _ResultLeftAccent(transform: transform),
                      if (hasPreviewImage && imagePath != null)
                        Positioned.fromRect(
                          rect: _toRect(placement!.displayDrawBounds),
                          child: Image.file(
                            File(imagePath),
                            fit: BoxFit.fill,
                            filterQuality: FilterQuality.medium,
                            errorBuilder: (context, error, stackTrace) =>
                                _ImagePlaceholder(
                                  label: context.t('previewFailed'),
                                ),
                          ),
                        ),
                      for (final layer
                          in CharacterResultLayout.foregroundLayers)
                        _TextureLayer(layer: layer, transform: transform),
                      _JacketSlotFill(transform: transform),
                    ],
                  ),
                ),
              );
            },
          ),
        ),
        const SizedBox(height: 8),
        if (!hasPreviewImage) ...[
          Text(
            context.t('characterPreviewEmptyHint'),
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 4),
        ],
        _PreviewDebugInfo(
          page: page,
          imageWidth: imageWidth,
          imageHeight: imageHeight,
        ),
      ],
    );
  }
}

class _TextureLayer extends StatelessWidget {
  const _TextureLayer({required this.layer, required this.transform});

  final CharacterResultTextureLayer layer;
  final CharacterResultDisplayTransform transform;

  @override
  Widget build(BuildContext context) {
    final displayBounds = transform.mapBounds(layer.logicalBounds);
    if (displayBounds.width <= 0 || displayBounds.height <= 0) {
      return const SizedBox.shrink();
    }
    return Positioned.fromRect(
      rect: _toRect(displayBounds),
      child: Image.asset(
        layer.assetPath,
        fit: BoxFit.fill,
        filterQuality: FilterQuality.medium,
        errorBuilder: (context, error, stackTrace) {
          return _AssetLoadError(label: layer.id);
        },
      ),
    );
  }
}

class _ResultBaseSurface extends StatelessWidget {
  const _ResultBaseSurface({required this.transform});

  final CharacterResultDisplayTransform transform;

  @override
  Widget build(BuildContext context) {
    return Positioned.fromRect(
      rect: Rect.fromLTWH(
        transform.contentLeft,
        transform.contentTop,
        transform.contentWidth,
        transform.contentHeight,
      ),
      child: const ColoredBox(color: Color(0xFF171B2C)),
    );
  }
}

class _ResultLeftAccent extends StatelessWidget {
  const _ResultLeftAccent({required this.transform});

  final CharacterResultDisplayTransform transform;

  @override
  Widget build(BuildContext context) {
    return Positioned.fromRect(
      rect: Rect.fromLTWH(
        transform.contentLeft,
        transform.contentTop,
        520 * transform.displayScale,
        transform.contentHeight,
      ),
      child: ColoredBox(
        color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.20),
      ),
    );
  }
}

class _JacketSlotFill extends StatelessWidget {
  const _JacketSlotFill({required this.transform});

  final CharacterResultDisplayTransform transform;

  @override
  Widget build(BuildContext context) {
    final displayBounds = transform.mapBounds(
      CharacterResultLayout.jacketInnerBounds,
    );
    if (displayBounds.width <= 0 || displayBounds.height <= 0) {
      return const SizedBox.shrink();
    }
    return Positioned.fromRect(
      rect: _toRect(displayBounds),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface.withValues(alpha: 0.38),
          borderRadius: BorderRadius.circular(10 * transform.displayScale),
        ),
      ),
    );
  }
}

class _PreviewDebugInfo extends StatelessWidget {
  const _PreviewDebugInfo({
    required this.page,
    required this.imageWidth,
    required this.imageHeight,
  });

  final CharacterEditorState page;
  final double imageWidth;
  final double imageHeight;

  @override
  Widget build(BuildContext context) {
    final hasPreviewImage = page.hasPreviewImage;
    final placement = hasPreviewImage
        ? CharacterPreviewCoordinateMapper.map(
            canvasWidth: CharacterPreviewCoordinateMapper.resultLogicalWidth,
            canvasHeight: CharacterPreviewCoordinateMapper.resultLogicalHeight,
            imageWidth: imageWidth,
            imageHeight: imageHeight,
            x: page.x,
            y: page.y,
            scale: page.scale,
          )
        : null;
    return PrettyExpandableSection(
      title: context.t('previewDebugInfo'),
      icon: Icons.bug_report_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          InfoRow(
            label: context.t('imageOriginalSize'),
            value: hasPreviewImage
                ? '${imageWidth.toStringAsFixed(0)} x '
                      '${imageHeight.toStringAsFixed(0)}'
                : '-',
          ),
          InfoRow(label: context.t('logicalCanvasSize'), value: '1920 x 1080'),
          if (placement != null) ...[
            InfoRow(
              label: context.t('displayScale'),
              value: placement.displayScale.toStringAsFixed(4),
            ),
            InfoRow(
              label: 'source x / y / scale',
              value:
                  'x=${page.x.toStringAsFixed(1)}, '
                  'y=${page.y.toStringAsFixed(1)}, '
                  'scale=${page.scale.toStringAsFixed(3)}',
            ),
            InfoRow(
              label: context.t('partnerPivot'),
              value:
                  'x=${placement.pivotLogical.x.toStringAsFixed(1)}, '
                  'y=${placement.pivotLogical.y.toStringAsFixed(1)}',
            ),
            InfoRow(
              label: context.t('partnerDrawRect'),
              value: placement.logicalDrawBounds.format(),
            ),
            InfoRow(
              label: context.t('visibleRect'),
              value: placement.visibleBounds?.format() ?? '-',
            ),
            InfoRow(
              label: context.t('intersectsCanvas'),
              value: placement.intersectsCanvas ? 'true' : 'false',
            ),
          ] else
            InfoRow(label: context.t('partnerDrawRect'), value: '-'),
          InfoRow(
            label: context.t('layoutProfile'),
            value:
                placement?.layoutProfileName ??
                CharacterResultLayout.profileName,
          ),
          InfoRow(
            label: 'result assets',
            value: CharacterResultAssets.all
                .map((asset) => asset.split('/').last)
                .join(', '),
          ),
          const InfoRow(label: 'debug overlay', value: 'false'),
          InfoRow(
            label: 'Result.unity nodes',
            value:
                'BackgroundArrow, ClearResult, JacketFrame, ScoreFrame, '
                'JudgementFrame, PlayRetryTable, CharacterParent',
          ),
          InfoRow(
            label: context.t('referenceSources'),
            value:
                'ArcCreate ResultScreen.cs / Result.unity; Android CharacterPreviewCoordinateMapper.kt',
          ),
        ],
      ),
    );
  }
}

class _ImagePlaceholder extends StatelessWidget {
  const _ImagePlaceholder({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainerHigh.withValues(alpha: 0.85),
        border: Border.all(color: colorScheme.outlineVariant),
      ),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.person_rounded, color: colorScheme.onSurfaceVariant),
            const SizedBox(height: 4),
            Text(
              label,
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AssetLoadError extends StatelessWidget {
  const _AssetLoadError({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.errorContainer,
      ),
      child: Center(
        child: Text(
          label,
          style: Theme.of(context).textTheme.labelSmall?.copyWith(
            color: Theme.of(context).colorScheme.onErrorContainer,
          ),
        ),
      ),
    );
  }
}

Rect _toRect(CharacterPreviewBounds bounds) {
  return Rect.fromLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom);
}
