import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;

import '../../app/app_state.dart';
import '../../app/routes.dart';
import '../../app/safe_action.dart';
import '../../core/i18n/app_strings.dart';
import '../../core/models/character_models.dart';
import '../../core/models/operation_models.dart';
import '../../core/models/single_song_models.dart';
import '../../shared/widgets/info_row.dart';
import '../../shared/widgets/log_panel.dart';
import '../../shared/widgets/pretty_ui.dart';
import '../../shared/widgets/resource_detail_overlay.dart';
import '../../shared/layout/app_layout_tokens.dart';
import 'character_editor_state.dart';
import 'character_result_preview_canvas.dart';
import 'character_result_preview_mapper.dart';

class CharacterEditorPage extends StatelessWidget {
  const CharacterEditorPage({super.key});

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final page = state.characterEditor;
    final busy =
        page.phase == OperationPhase.scanning ||
        page.phase == OperationPhase.saving;

    return SingleChildScrollView(
      padding: _pagePadding(context),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(
            maxWidth: AppLayoutTokens.pageContentMaxWidth,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _CharacterHeroHeader(page: page),
              const SizedBox(height: 24),
              _CharacterWorkbenchLayout(
                left: [
                  _CharacterInputCard(state: state, page: page, busy: busy),
                  _CharacterSaveCard(state: state, page: page, busy: busy),
                  _CharacterDiagnosticsCard(page: page),
                ],
                middle: [
                  _CharacterMetadataCard(state: state, page: page),
                  if (page.hasPreviewImage) _CharacterImageCard(page: page),
                ],
                right: [
                  _CharacterResultPreviewCard(state: state, page: page),
                  if (page.hasPreviewImage)
                    _CharacterIconCropCard(state: state, page: page),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _CharacterHeroHeader extends StatelessWidget {
  const _CharacterHeroHeader({required this.page});

  final CharacterEditorState page;

  @override
  Widget build(BuildContext context) {
    final pills = page.scan != null || page.phase == OperationPhase.saved
        ? Wrap(
            spacing: 8,
            runSpacing: 8,
            alignment: WrapAlignment.end,
            children: [
              PrettyPill(
                icon: Icons.person_rounded,
                label: page.edit?.identifier ?? page.scan?.identifier ?? '-',
                tone: PrettyPillTone.primary,
              ),
              PrettyPill(
                icon: _phaseIcon(page.phase),
                label: context.t(page.phase.i18nKey),
                tone: _phaseTone(page.phase),
              ),
            ],
          )
        : null;
    final title = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          context.t('app.title'),
          style: Theme.of(context).textTheme.displaySmall?.copyWith(
            fontWeight: FontWeight.w900,
            color: PrettyColors.text,
            letterSpacing: 0,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          context.t('page.characterEditor'),
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: PrettyColors.muted,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 560) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              title,
              if (pills != null) ...[const SizedBox(height: 12), pills],
            ],
          );
        }
        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(child: title),
            if (pills != null)
              Flexible(
                child: Align(alignment: Alignment.topRight, child: pills),
              ),
          ],
        );
      },
    );
  }
}

class _CharacterWorkbenchLayout extends StatelessWidget {
  const _CharacterWorkbenchLayout({
    required this.left,
    required this.middle,
    required this.right,
  });

  final List<Widget> left;
  final List<Widget> middle;
  final List<Widget> right;

  @override
  Widget build(BuildContext context) {
    return AppWorkspaceLayout(
      left: left,
      middle: middle,
      right: right,
      mediumOrder: AppWorkspaceMediumOrder.rightThenMiddle,
    );
  }
}

class _CharacterInputCard extends StatelessWidget {
  const _CharacterInputCard({
    required this.state,
    required this.page,
    required this.busy,
  });

  final AppState state;
  final CharacterEditorState page;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    final inputPath = page.inputPath;
    return PrettyCard(
      title: context.t('input'),
      icon: Icons.person_add_alt_rounded,
      trailing: PrettyPill(
        icon: _phaseIcon(page.phase),
        label: context.t(page.phase.i18nKey),
        tone: _phaseTone(page.phase),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              PrettyGlassButton(
                label: context.t('chooseImage'),
                icon: Icons.image_rounded,
                primary: true,
                onPressed: busy
                    ? null
                    : () => safeAction(
                        context,
                        id: 'character.pickImage',
                        label: context.t('chooseImage'),
                        page: AppPageId.characterEditor,
                        action: state.pickCharacterImage,
                      ),
              ),
              PrettyGlassButton(
                label: context.t('chooseArcpkg'),
                icon: Icons.inventory_2_rounded,
                onPressed: busy
                    ? null
                    : () => safeAction(
                        context,
                        id: 'character.pickPackage',
                        label: context.t('chooseArcpkg'),
                        page: AppPageId.characterEditor,
                        action: state.pickCharacterPackage,
                      ),
              ),
              if (page.hasInput)
                OutlinedButton.icon(
                  onPressed: busy
                      ? null
                      : () => safeAction(
                          context,
                          id: 'character.input.clear',
                          label: context.t('clearInputs'),
                          page: AppPageId.characterEditor,
                          action: state.clearCharacterInput,
                        ),
                  icon: const Icon(Icons.clear_all_rounded),
                  label: Text(context.t('clearInputs')),
                ),
            ],
          ),
          const SizedBox(height: 16),
          if (inputPath == null || inputPath.isEmpty)
            DashedEmptyBox(
              icon: Icons.person_search_rounded,
              title: context.t('notSelected'),
              subtitle: context.t('characterInputEmptyHint'),
              minHeight: 132,
            )
          else
            _CurrentInputPanel(page: page),
          if (page.scan != null) ...[
            const SizedBox(height: 8),
            PrettyExpandableSection(
              title: context.t('workspace'),
              icon: Icons.workspaces_rounded,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  InfoRow(
                    label: context.t('inputType'),
                    value: page.scan!.inputType.ifBlank(_sourceKindLabel(page)),
                  ),
                  InfoRow(
                    label: context.t('workspace'),
                    value: page.scan!.workspacePath,
                  ),
                ],
              ),
            ),
          ],
          if (page.lastError != null) ...[
            const SizedBox(height: 12),
            Text(
              page.lastError!,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: PrettyColors.danger,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _CurrentInputPanel extends StatelessWidget {
  const _CurrentInputPanel({required this.page});

  final CharacterEditorState page;

  @override
  Widget build(BuildContext context) {
    final path = page.inputPath ?? '';
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Theme.of(
          context,
        ).colorScheme.primaryContainer.withValues(alpha: 0.16),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: PrettyColors.borderStrong.withValues(alpha: 0.36),
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: PrettyColors.primarySoft,
              borderRadius: BorderRadius.circular(15),
            ),
            child: Icon(
              page.scan?.sourceKind == CharacterInputKind.arcpkg
                  ? Icons.inventory_2_rounded
                  : Icons.image_rounded,
              color: PrettyColors.primary,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  p.basename(path),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(
                    context,
                  ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w900),
                ),
                const SizedBox(height: 3),
                Text(
                  path,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(
                    context,
                  ).textTheme.bodySmall?.copyWith(color: PrettyColors.muted),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _CharacterMetadataCard extends StatelessWidget {
  const _CharacterMetadataCard({required this.state, required this.page});

  final AppState state;
  final CharacterEditorState page;

  @override
  Widget build(BuildContext context) {
    final edit = page.edit;
    final enabled = edit != null;
    return PrettyCard(
      title: context.t('characterInfo'),
      icon: Icons.badge_rounded,
      trailing: PrettyPill(
        icon: Icons.tag_rounded,
        label: edit?.identifier ?? '-',
        tone: enabled ? PrettyPillTone.primary : PrettyPillTone.neutral,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _FieldGrid(
            children: [
              _TextEdit(
                enabled: enabled,
                label: context.t('field.publisherId'),
                value: edit?.publisherId ?? '',
                onChanged: (value) =>
                    state.updateCharacterMetadata(publisherId: value),
              ),
              _TextEdit(
                enabled: enabled,
                label: context.t('field.characterId'),
                value: edit?.characterId ?? '',
                onChanged: (value) =>
                    state.updateCharacterMetadata(characterId: value),
              ),
              _TextEdit(
                enabled: enabled,
                label: context.t('field.directory'),
                value: edit?.directory ?? '',
                onChanged: (value) =>
                    state.updateCharacterMetadata(directory: value),
              ),
              _TextEdit(
                enabled: enabled,
                label: context.t('field.defaultName'),
                value: edit?.defaultName ?? '',
                onChanged: (value) =>
                    state.updateCharacterMetadata(defaultName: value),
              ),
              _TextEdit(
                enabled: enabled,
                label: context.t('field.zhCnName'),
                value: edit?.zhCnName ?? '',
                onChanged: (value) =>
                    state.updateCharacterMetadata(zhCnName: value),
              ),
            ],
          ),
          const SizedBox(height: 8),
          PrettyExpandableSection(
            title: context.t('advanced'),
            icon: Icons.tune_rounded,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _TextEdit(
                  enabled: enabled,
                  label: context.t('outputFile'),
                  value: edit?.outputFileName ?? '',
                  onChanged: (value) =>
                      state.updateCharacterMetadata(outputFileName: value),
                ),
                const SizedBox(height: 10),
                _TextEdit(
                  enabled: enabled,
                  label: context.t('field.imageResource'),
                  value: edit?.imageFileName ?? '',
                  onChanged: (value) =>
                      state.updateCharacterMetadata(imageFileName: value),
                ),
                const SizedBox(height: 10),
                _TextEdit(
                  enabled: enabled,
                  label: context.t('field.iconResource'),
                  value: edit?.iconFileName ?? '',
                  onChanged: (value) =>
                      state.updateCharacterMetadata(iconFileName: value),
                ),
                const SizedBox(height: 10),
                _StaticInfoTile(
                  label: context.t('identifierPreview'),
                  value: edit?.identifier ?? '-',
                ),
                _StaticInfoTile(
                  label: context.t('field.imagePath'),
                  value: page.imagePath ?? '-',
                ),
                _StaticInfoTile(
                  label: context.t('field.iconPath'),
                  value: page.iconPath ?? '-',
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _FieldGrid extends StatelessWidget {
  const _FieldGrid({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final twoColumns = constraints.maxWidth >= 560;
        if (!twoColumns) {
          return Column(
            children: [
              for (var i = 0; i < children.length; i++) ...[
                children[i],
                if (i != children.length - 1) const SizedBox(height: 10),
              ],
            ],
          );
        }
        return Wrap(
          spacing: 12,
          runSpacing: 10,
          children: [
            for (final child in children)
              SizedBox(width: (constraints.maxWidth - 12) / 2, child: child),
          ],
        );
      },
    );
  }
}

class _TextEdit extends StatelessWidget {
  const _TextEdit({
    required this.enabled,
    required this.label,
    required this.value,
    required this.onChanged,
  });

  final bool enabled;
  final String label;
  final String value;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      key: ValueKey('$label:$value'),
      enabled: enabled,
      initialValue: value,
      decoration: InputDecoration(labelText: label),
      onChanged: (next) => safeAction(
        context,
        id: 'character.editMetadata',
        label: label,
        page: AppPageId.characterEditor,
        logThrottle: const Duration(milliseconds: 420),
        action: () => onChanged(next),
      ),
    );
  }
}

class _StaticInfoTile extends StatelessWidget {
  const _StaticInfoTile({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: InfoRow(label: label, value: value),
    );
  }
}

class _CharacterImageCard extends StatelessWidget {
  const _CharacterImageCard({required this.page});

  final CharacterEditorState page;

  @override
  Widget build(BuildContext context) {
    final image = page.scan?.image;
    return PrettyCard(
      title: context.t('characterImage'),
      icon: Icons.portrait_rounded,
      trailing: PrettyPill(
        label: page.hasPreviewImage
            ? context.t('resource.identified')
            : context.t('resource.missing'),
        tone: page.hasPreviewImage
            ? PrettyPillTone.success
            : PrettyPillTone.neutral,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _ImagePreview(
            path: page.imagePath,
            label: page.imageName ?? context.t('previewPlaceholder'),
            aspectRatio: _resourceAspectRatio(image, fallback: 4 / 3),
            minHeight: 240,
            resource: image,
            heroTag: 'character-image-resource',
            detailTitle: context.t('characterImage'),
            detailRows: [
              ResourceDetailRow(
                label: context.t('field.alpha'),
                value: page.scan?.imageHasAlpha == null
                    ? '-'
                    : page.scan!.imageHasAlpha!
                    ? 'true'
                    : 'false',
              ),
              ResourceDetailRow(
                label: context.t('field.imageResource'),
                value: page.imageName ?? '-',
              ),
            ],
          ),
          const SizedBox(height: 12),
          _ResourceMeta(resource: image, fallbackPath: page.imagePath),
          if (page.scan?.imageHasAlpha != null)
            InfoRow(
              label: context.t('field.alpha'),
              value: page.scan!.imageHasAlpha! ? 'true' : 'false',
            ),
        ],
      ),
    );
  }
}

class _CharacterResultPreviewCard extends StatelessWidget {
  const _CharacterResultPreviewCard({required this.state, required this.page});

  final AppState state;
  final CharacterEditorState page;

  @override
  Widget build(BuildContext context) {
    final hasImage = page.hasPreviewImage;
    return PrettyCard(
      title: context.t('resultPreview'),
      icon: Icons.preview_rounded,
      emphasized: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ArcCreateResultPreviewCanvas(page: page),
          const SizedBox(height: 14),
          if (hasImage) ...[
            _CharacterMetricRow(page: page),
            const SizedBox(height: 8),
            _NumberSliderField(
              label: 'x',
              min: -1000,
              max: 1000,
              divisions: 2000,
              value: page.x,
              onChanged: (value) => _updatePosition(context, x: value),
            ),
            _NumberSliderField(
              label: 'y',
              min: -1000,
              max: 1000,
              divisions: 2000,
              value: page.y,
              onChanged: (value) => _updatePosition(context, y: value),
            ),
            _NumberSliderField(
              label: 'scale',
              min: 0.1,
              max: 2.0,
              divisions: 190,
              value: page.scale.clamp(0.1, 2.0).toDouble(),
              fractionDigits: 2,
              onChanged: (value) => _updatePosition(context, scale: value),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                OutlinedButton(
                  onPressed: () => _setPosition(context, 300, 100, 0.7),
                  child: Text(context.t('control.reset')),
                ),
                OutlinedButton(
                  onPressed: () => _updatePosition(context, x: 640, y: 365),
                  child: Text(context.t('control.center')),
                ),
                OutlinedButton(
                  onPressed: () => _fitHeight(context),
                  child: Text(context.t('control.fitHeight')),
                ),
                OutlinedButton(
                  onPressed: () => _fitWidth(context),
                  child: Text(context.t('control.fitWidth')),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  void _setPosition(BuildContext context, double x, double y, double scale) {
    _updatePosition(context, x: x, y: y, scale: scale);
  }

  void _fitHeight(BuildContext context) {
    final scale =
        CharacterPreviewCoordinateMapper.resultLogicalHeight /
        CharacterPreviewCoordinateMapper.characterImageHeight;
    _updatePosition(context, scale: scale);
  }

  void _fitWidth(BuildContext context) {
    final image = page.scan?.image;
    final ratio =
        (image?.width != null && image?.height != null && image!.height! > 0)
        ? image.width! / image.height!
        : 1152 / 2048;
    final scale =
        CharacterPreviewCoordinateMapper.resultLogicalWidth /
        (CharacterPreviewCoordinateMapper.characterImageHeight * ratio);
    _updatePosition(context, scale: scale);
  }

  void _updatePosition(
    BuildContext context, {
    double? x,
    double? y,
    double? scale,
  }) {
    safeAction(
      context,
      id: 'character.editPosition',
      label: 'position',
      page: AppPageId.characterEditor,
      logThrottle: const Duration(milliseconds: 250),
      action: () => state.updateCharacterPosition(x: x, y: y, scale: scale),
    );
  }
}

class _CharacterMetricRow extends StatelessWidget {
  const _CharacterMetricRow({required this.page});

  final CharacterEditorState page;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        PrettyPill(label: 'x ${page.x.toStringAsFixed(1)}'),
        PrettyPill(label: 'y ${page.y.toStringAsFixed(1)}'),
        PrettyPill(label: 'scale ${page.scale.toStringAsFixed(2)}'),
      ],
    );
  }
}

class _NumberSliderField extends StatelessWidget {
  const _NumberSliderField({
    required this.label,
    required this.value,
    required this.onChanged,
    this.min = 0,
    this.max = 1,
    this.divisions,
    this.fractionDigits = 1,
  });

  final String label;
  final double min;
  final double max;
  final int? divisions;
  final double value;
  final int fractionDigits;
  final ValueChanged<double> onChanged;

  @override
  Widget build(BuildContext context) {
    final clamped = value.clamp(min, max).toDouble();
    final labelWidget = Text(
      label,
      style: Theme.of(
        context,
      ).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w800),
    );
    final input = SizedBox(
      width: 86,
      child: TextFormField(
        key: ValueKey('$label:${value.toStringAsFixed(fractionDigits)}'),
        initialValue: value.toStringAsFixed(fractionDigits),
        textAlign: TextAlign.end,
        keyboardType: const TextInputType.numberWithOptions(
          signed: true,
          decimal: true,
        ),
        decoration: const InputDecoration(isDense: true),
        onFieldSubmitted: (next) {
          final parsed = double.tryParse(next);
          if (parsed != null) {
            onChanged(parsed.clamp(min, max).toDouble());
          }
        },
      ),
    );
    final slider = Slider(
      min: min,
      max: max,
      divisions: divisions,
      value: clamped,
      onChanged: onChanged,
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 260) {
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Expanded(child: labelWidget),
                    input,
                  ],
                ),
                slider,
              ],
            ),
          );
        }
        return Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Row(
            children: [
              SizedBox(width: 72, child: labelWidget),
              Expanded(child: slider),
              input,
            ],
          ),
        );
      },
    );
  }
}

class _CharacterIconCropCard extends StatelessWidget {
  const _CharacterIconCropCard({required this.state, required this.page});

  final AppState state;
  final CharacterEditorState page;

  @override
  Widget build(BuildContext context) {
    return PrettyCard(
      title: context.t('iconCrop'),
      icon: Icons.crop_rounded,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final wide = constraints.maxWidth >= 640;
          final editor = _InteractiveIconCropEditor(state: state, page: page);
          final controls = _IconCropControls(
            state: state,
            page: page,
            onUpdate: _updateCrop,
          );
          final preview = _IconPreviewPanel(page: page);
          if (!wide) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                editor,
                const SizedBox(height: 14),
                controls,
                const SizedBox(height: 14),
                preview,
              ],
            );
          }
          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(flex: 7, child: editor),
              const SizedBox(width: 18),
              Expanded(
                flex: 4,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [preview, const SizedBox(height: 14), controls],
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  void _updateCrop(
    BuildContext context, {
    double? centerX,
    double? centerY,
    double? cropSize,
  }) {
    safeAction(
      context,
      id: 'character.editIconCrop',
      label: context.t('iconCrop'),
      page: AppPageId.characterEditor,
      logThrottle: const Duration(milliseconds: 250),
      action: () => state.updateCharacterCrop(
        centerX: centerX,
        centerY: centerY,
        cropSize: cropSize,
      ),
    );
  }
}

class _IconCropControls extends StatelessWidget {
  const _IconCropControls({
    required this.state,
    required this.page,
    required this.onUpdate,
  });

  final AppState state;
  final CharacterEditorState page;
  final void Function(
    BuildContext context, {
    double? centerX,
    double? centerY,
    double? cropSize,
  })
  onUpdate;

  @override
  Widget build(BuildContext context) {
    final canGenerate = page.hasPreviewImage && !page.iconGenerating;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _NumberSliderField(
          label: context.t('iconCrop.centerX'),
          value: page.cropCenterX.clamp(0.0, 1.0).toDouble(),
          fractionDigits: 2,
          onChanged: (value) => onUpdate(context, centerX: value),
        ),
        _NumberSliderField(
          label: context.t('iconCrop.centerY'),
          value: page.cropCenterY.clamp(0.0, 1.0).toDouble(),
          fractionDigits: 2,
          onChanged: (value) => onUpdate(context, centerY: value),
        ),
        _NumberSliderField(
          label: context.t('iconCrop.size'),
          min: 0.05,
          value: page.cropSize.clamp(0.05, 1.0).toDouble(),
          fractionDigits: 2,
          onChanged: (value) => onUpdate(context, cropSize: value),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            FilledButton.icon(
              onPressed: canGenerate
                  ? () => safeAction(
                      context,
                      id: 'character.icon.generate',
                      label: context.t('iconCrop.apply'),
                      page: AppPageId.characterEditor,
                      action: state.applyCharacterIconCrop,
                    )
                  : null,
              icon: page.iconGenerating
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.done_rounded),
              label: Text(
                page.iconGenerating
                    ? context.t('iconCrop.generating')
                    : context.t('iconCrop.apply'),
              ),
            ),
            OutlinedButton.icon(
              onPressed: page.iconGenerating
                  ? null
                  : () => onUpdate(
                      context,
                      centerX: 0.5,
                      centerY: 0.5,
                      cropSize: 0.5,
                    ),
              icon: const Icon(Icons.restart_alt_rounded),
              label: Text(context.t('iconCrop.reset')),
            ),
          ],
        ),
        const SizedBox(height: 10),
        Text(
          context.t('iconCrop.saveHint'),
          style: Theme.of(
            context,
          ).textTheme.bodySmall?.copyWith(color: PrettyColors.muted),
        ),
        if (page.iconError != null) ...[
          const SizedBox(height: 8),
          Text(
            page.iconError!,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: PrettyColors.danger,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ],
    );
  }
}

class _InteractiveIconCropEditor extends StatefulWidget {
  const _InteractiveIconCropEditor({required this.state, required this.page});

  final AppState state;
  final CharacterEditorState page;

  @override
  State<_InteractiveIconCropEditor> createState() =>
      _InteractiveIconCropEditorState();
}

class _InteractiveIconCropEditorState
    extends State<_InteractiveIconCropEditor> {
  _CropDragMode? _mode;

  @override
  Widget build(BuildContext context) {
    final image = widget.page.scan?.image;
    final aspectRatio = _resourceAspectRatio(image, fallback: 1);
    final file = widget.page.imagePath == null
        ? null
        : File(widget.page.imagePath!);
    return AspectRatio(
      aspectRatio: aspectRatio,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(22),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final size = Size(constraints.maxWidth, constraints.maxHeight);
            final rect = _cropRect(size, widget.page, image);
            return GestureDetector(
              behavior: HitTestBehavior.opaque,
              onPanStart: (details) {
                _mode = _hitTest(details.localPosition, rect);
                if (_mode != null) {
                  _applyPointer(details.localPosition, size, image);
                }
              },
              onPanUpdate: (details) {
                if (_mode != null) {
                  _applyPointer(details.localPosition, size, image);
                }
              },
              onPanEnd: (_) => _mode = null,
              onPanCancel: () => _mode = null,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  _CheckerBackground(),
                  if (file != null && file.existsSync())
                    Image.file(
                      file,
                      fit: BoxFit.fill,
                      filterQuality: FilterQuality.medium,
                      errorBuilder: (_, _, _) => _PreviewPlaceholder(
                        label: context.t('previewFailed'),
                      ),
                    )
                  else
                    _PreviewPlaceholder(
                      label:
                          widget.page.imageName ??
                          context.t('previewPlaceholder'),
                    ),
                  CustomPaint(
                    painter: _CropOverlayPainter(
                      rect: rect,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                  ),
                  Positioned.fromRect(
                    rect: rect,
                    child: IgnorePointer(
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          border: Border.all(
                            color: Theme.of(context).colorScheme.primary,
                            width: 2,
                          ),
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: Stack(
                          children: const [
                            _CropHandle(alignment: Alignment.topLeft),
                            _CropHandle(alignment: Alignment.topRight),
                            _CropHandle(alignment: Alignment.bottomLeft),
                            _CropHandle(alignment: Alignment.bottomRight),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }

  _CropDragMode? _hitTest(Offset position, Rect rect) {
    if (!rect.inflate(12).contains(position)) return null;
    final nearHorizontal =
        (position.dx - rect.left).abs() < 18 ||
        (position.dx - rect.right).abs() < 18;
    final nearVertical =
        (position.dy - rect.top).abs() < 18 ||
        (position.dy - rect.bottom).abs() < 18;
    return nearHorizontal || nearVertical
        ? _CropDragMode.resize
        : _CropDragMode.move;
  }

  void _applyPointer(Offset position, Size size, ResourceInfo? image) {
    final page = widget.page;
    final imageWidth = (image?.width ?? size.width).toDouble();
    final imageHeight = (image?.height ?? size.height).toDouble();
    if (imageWidth <= 0 ||
        imageHeight <= 0 ||
        size.width <= 0 ||
        size.height <= 0) {
      return;
    }
    if (_mode == _CropDragMode.move) {
      _update(
        centerX: (position.dx / size.width).clamp(0.0, 1.0).toDouble(),
        centerY: (position.dy / size.height).clamp(0.0, 1.0).toDouble(),
      );
      return;
    }
    final dx =
        ((position.dx / size.width) - page.cropCenterX).abs() * imageWidth;
    final dy =
        ((position.dy / size.height) - page.cropCenterY).abs() * imageHeight;
    final minSide = math.min(imageWidth, imageHeight);
    final nextSize = ((math.max(dx, dy) * 2) / minSide)
        .clamp(0.05, 1.0)
        .toDouble();
    _update(cropSize: nextSize);
  }

  void _update({double? centerX, double? centerY, double? cropSize}) {
    widget.state.updateCharacterCrop(
      centerX: centerX,
      centerY: centerY,
      cropSize: cropSize,
    );
  }
}

enum _CropDragMode { move, resize }

class _CropHandle extends StatelessWidget {
  const _CropHandle({required this.alignment});

  final Alignment alignment;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: alignment,
      child: Container(
        width: 14,
        height: 14,
        margin: const EdgeInsets.all(5),
        decoration: BoxDecoration(
          color: Colors.white,
          shape: BoxShape.circle,
          border: Border.all(color: Theme.of(context).colorScheme.primary),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.18),
              blurRadius: 8,
            ),
          ],
        ),
      ),
    );
  }
}

class _CropOverlayPainter extends CustomPainter {
  const _CropOverlayPainter({required this.rect, required this.color});

  final Rect rect;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final outer = Path()..addRect(Offset.zero & size);
    final inner = Path()
      ..addRRect(RRect.fromRectAndRadius(rect, const Radius.circular(14)));
    final dim = Paint()..color = Colors.black.withValues(alpha: 0.18);
    canvas.drawPath(Path.combine(PathOperation.difference, outer, inner), dim);
    final thirds = Paint()
      ..color = color.withValues(alpha: 0.55)
      ..strokeWidth = 1;
    for (final ratio in const [1 / 3, 2 / 3]) {
      canvas.drawLine(
        Offset(rect.left + rect.width * ratio, rect.top),
        Offset(rect.left + rect.width * ratio, rect.bottom),
        thirds,
      );
      canvas.drawLine(
        Offset(rect.left, rect.top + rect.height * ratio),
        Offset(rect.right, rect.top + rect.height * ratio),
        thirds,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _CropOverlayPainter oldDelegate) =>
      oldDelegate.rect != rect || oldDelegate.color != color;
}

class _IconPreviewPanel extends StatelessWidget {
  const _IconPreviewPanel({required this.page});

  final CharacterEditorState page;

  @override
  Widget build(BuildContext context) {
    final statusTone = page.iconError != null
        ? PrettyPillTone.danger
        : page.iconGenerating
        ? PrettyPillTone.warning
        : page.iconCropDirty
        ? PrettyPillTone.warning
        : page.iconPath != null
        ? PrettyPillTone.success
        : PrettyPillTone.neutral;
    final statusLabel = page.iconError != null
        ? context.t('iconCrop.failed')
        : page.iconGenerating
        ? context.t('iconCrop.generating')
        : page.iconCropDirty
        ? context.t('iconCrop.dirty')
        : page.iconPath != null
        ? context.t('iconCrop.generated')
        : context.t('iconCrop.notGenerated');
    final iconResource = page.iconCropDirty
        ? null
        : page.generatedIcon?.icon ?? page.scan?.icon;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                context.t('iconPreview'),
                style: Theme.of(
                  context,
                ).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w900),
              ),
            ),
            PrettyPill(label: statusLabel, tone: statusTone),
          ],
        ),
        const SizedBox(height: 8),
        Center(
          child: Material(
            color: Colors.transparent,
            child: InkWell(
              borderRadius: BorderRadius.circular(24),
              onTap: () => showResourceDetailOverlay(
                context,
                heroTag: 'character-icon-preview',
                title: context.t('characterIcon'),
                icon: Icons.crop_rounded,
                kind: ResourceDetailKind.image,
                resource: iconResource,
                statusLabel: statusLabel,
                statusTone: statusTone,
                preview: Center(
                  child: _IconPreviewSquare(page: page, dimension: 320),
                ),
                rows: [
                  ResourceDetailRow(
                    label: context.t('iconCrop.centerX'),
                    value: page.cropCenterX.toStringAsFixed(3),
                  ),
                  ResourceDetailRow(
                    label: context.t('iconCrop.centerY'),
                    value: page.cropCenterY.toStringAsFixed(3),
                  ),
                  ResourceDetailRow(
                    label: context.t('iconCrop.size'),
                    value: page.cropSize.toStringAsFixed(3),
                  ),
                  ResourceDetailRow(
                    label: context.t('iconCrop.outputFormat'),
                    value: 'PNG 1:1',
                  ),
                ],
              ),
              child: _IconPreviewSquare(
                page: page,
                dimension: 168,
                heroTag: 'character-icon-preview',
              ),
            ),
          ),
        ),
        const SizedBox(height: 10),
        InfoRow(label: context.t('characterIcon'), value: page.iconName ?? '-'),
        InfoRow(label: context.t('iconCrop.outputFormat'), value: 'PNG 1:1'),
        if (page.generatedIcon?.icon.width != null ||
            page.generatedIcon?.icon.height != null)
          InfoRow(
            label: context.t('dimensions'),
            value:
                '${page.generatedIcon?.icon.width ?? '-'} x ${page.generatedIcon?.icon.height ?? '-'}',
          ),
      ],
    );
  }
}

class _IconPreviewSquare extends StatelessWidget {
  const _IconPreviewSquare({
    required this.page,
    required this.dimension,
    this.heroTag,
  });

  final CharacterEditorState page;
  final double dimension;
  final String? heroTag;

  @override
  Widget build(BuildContext context) {
    final appliedIconPath = page.iconCropDirty ? null : page.iconPath;
    final appliedFile = appliedIconPath == null ? null : File(appliedIconPath);
    final sourceFile = page.imagePath == null ? null : File(page.imagePath!);
    final image = page.scan?.image;
    final key = ValueKey(
      [
        page.iconPath,
        page.iconCropDirty,
        page.cropCenterX.toStringAsFixed(4),
        page.cropCenterY.toStringAsFixed(4),
        page.cropSize.toStringAsFixed(4),
        _fileStamp(appliedFile),
      ].join(':'),
    );
    Widget content = SizedBox.square(
      dimension: dimension,
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 180),
        switchInCurve: Curves.easeOutCubic,
        switchOutCurve: Curves.easeInCubic,
        child: ClipRRect(
          key: key,
          borderRadius: BorderRadius.circular(24),
          child: Stack(
            fit: StackFit.expand,
            children: [
              _CheckerBackground(),
              if (appliedFile != null && appliedFile.existsSync())
                _MemoryFileImage(file: appliedFile)
              else if (sourceFile != null && sourceFile.existsSync())
                _LiveIconCropPreview(
                  sourceFile: sourceFile,
                  page: page,
                  image: image,
                )
              else
                _PreviewPlaceholder(label: context.t('iconCrop.notGenerated')),
            ],
          ),
        ),
      ),
    );
    if (heroTag != null) {
      content = Hero(
        tag: heroTag!,
        child: Material(color: Colors.transparent, child: content),
      );
    }
    return content;
  }
}

class _MemoryFileImage extends StatelessWidget {
  const _MemoryFileImage({required this.file});

  final File file;

  @override
  Widget build(BuildContext context) {
    try {
      return Image.memory(
        file.readAsBytesSync(),
        fit: BoxFit.contain,
        filterQuality: FilterQuality.medium,
      );
    } catch (_) {
      return _PreviewPlaceholder(label: context.t('previewFailed'));
    }
  }
}

class _LiveIconCropPreview extends StatelessWidget {
  const _LiveIconCropPreview({
    required this.sourceFile,
    required this.page,
    required this.image,
  });

  final File sourceFile;
  final CharacterEditorState page;
  final ResourceInfo? image;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final previewSize = constraints.biggest.shortestSide;
        final sourceSize = _sourceImageSize(image, Size.square(previewSize));
        final crop = _iconCropPixels(page, image, fallback: sourceSize);
        if (sourceSize.width <= 0 ||
            sourceSize.height <= 0 ||
            crop.width <= 0) {
          return _PreviewPlaceholder(label: context.t('previewFailed'));
        }
        final scale = previewSize / crop.width;
        final display = Rect.fromLTWH(
          -crop.left * scale,
          -crop.top * scale,
          sourceSize.width * scale,
          sourceSize.height * scale,
        );
        return Stack(
          fit: StackFit.expand,
          children: [
            Positioned.fromRect(
              rect: display,
              child: Image.file(
                sourceFile,
                fit: BoxFit.fill,
                filterQuality: FilterQuality.medium,
              ),
            ),
          ],
        );
      },
    );
  }
}

class _CharacterSaveCard extends StatelessWidget {
  const _CharacterSaveCard({
    required this.state,
    required this.page,
    required this.busy,
  });

  final AppState state;
  final CharacterEditorState page;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    final result = page.saveResult;
    final canSave = _canSaveCharacter(page) && !busy;
    final failed =
        page.phase == OperationPhase.failed && page.lastError != null;
    return PrettyCard(
      title: context.t('saveAndExport'),
      icon: Icons.download_rounded,
      emphasized: result != null,
      trailing: PrettyPill(
        icon: result != null
            ? Icons.check_circle_rounded
            : failed
            ? Icons.error_rounded
            : canSave
            ? Icons.save_rounded
            : Icons.hourglass_empty_rounded,
        label: result != null
            ? context.t('saveComplete')
            : failed
            ? context.t('saveFailed')
            : canSave
            ? context.t('readyToSave')
            : context.t('saveUnavailable'),
        tone: result != null
            ? PrettyPillTone.success
            : failed
            ? PrettyPillTone.danger
            : canSave
            ? PrettyPillTone.primary
            : PrettyPillTone.neutral,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (result != null)
            _CharacterSaveResultPanel(result: result, state: state)
          else if (failed)
            Text(
              page.lastError!,
              style: const TextStyle(color: PrettyColors.danger),
            )
          else
            Text(
              canSave
                  ? page.edit?.outputFileName ?? context.t('saveArcpkg')
                  : context.t('character.saveDisabledHint'),
              style: Theme.of(
                context,
              ).textTheme.bodyMedium?.copyWith(color: PrettyColors.muted),
            ),
          const SizedBox(height: 14),
          FilledButton.icon(
            onPressed: canSave
                ? () => safeAction(
                    context,
                    id: 'character.save.start',
                    label: context.t('saveArcpkg'),
                    page: AppPageId.characterEditor,
                    action: state.saveCharacter,
                  )
                : null,
            icon: Icon(
              result == null ? Icons.save_rounded : Icons.sync_rounded,
            ),
            label: Text(
              result == null
                  ? context.t('saveArcpkg')
                  : context.t('exportAgain'),
            ),
          ),
        ],
      ),
    );
  }
}

class _CharacterSaveResultPanel extends StatelessWidget {
  const _CharacterSaveResultPanel({required this.result, required this.state});

  final CharacterSaveResult result;
  final AppState state;

  @override
  Widget build(BuildContext context) {
    final outputPath = result.outputPath;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Theme.of(
          context,
        ).colorScheme.primaryContainer.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(22),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.person_rounded, color: PrettyColors.primary),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  p.basename(outputPath),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(
                    context,
                  ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w900),
                ),
              ),
              Text(_formatBytes(result.sizeBytes ?? result.queriedSizeBytes)),
            ],
          ),
          const SizedBox(height: 10),
          SelectableText(outputPath, maxLines: 2),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              OutlinedButton.icon(
                onPressed: () async {
                  await Clipboard.setData(ClipboardData(text: outputPath));
                  if (!context.mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(context.t('pathCopied'))),
                  );
                },
                icon: const Icon(Icons.copy_rounded),
                label: Text(context.t('copyPath')),
              ),
              OutlinedButton.icon(
                onPressed: () =>
                    state.platform.openLocation.openLocation(outputPath),
                icon: const Icon(Icons.folder_open_rounded),
                label: Text(context.t('openFolder')),
              ),
            ],
          ),
          const SizedBox(height: 10),
          InfoRow(label: 'identifier', value: result.identifier),
          InfoRow(label: context.t('field.directory'), value: result.directory),
          InfoRow(
            label: context.t('field.validation'),
            value: result.validation.valid ? 'passed' : 'failed',
          ),
        ],
      ),
    );
  }
}

class _CharacterDiagnosticsCard extends StatelessWidget {
  const _CharacterDiagnosticsCard({required this.page});

  final CharacterEditorState page;

  @override
  Widget build(BuildContext context) {
    final warnings = page.scan?.warnings ?? const <String>[];
    return PrettyCard(
      title: context.t('logs'),
      icon: Icons.article_rounded,
      compact: true,
      trailing: PrettyPill(
        label: '${warnings.length} ${context.t('warningCount')}',
        tone: warnings.isEmpty
            ? PrettyPillTone.neutral
            : PrettyPillTone.warning,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          PrettyExpandableSection(
            title: context.t('warningCount'),
            icon: Icons.warning_amber_rounded,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (warnings.isEmpty)
                  Text(
                    '-',
                    style: Theme.of(
                      context,
                    ).textTheme.bodySmall?.copyWith(color: PrettyColors.muted),
                  )
                else
                  for (final warning in warnings)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 6),
                      child: Text(warning),
                    ),
              ],
            ),
          ),
          PrettyExpandableSection(
            title: context.t('logs'),
            icon: Icons.article_rounded,
            child: LogPanel(logs: page.logs),
          ),
        ],
      ),
    );
  }
}

class _ImagePreview extends StatelessWidget {
  const _ImagePreview({
    required this.path,
    required this.label,
    required this.aspectRatio,
    this.minHeight = 160,
    this.resource,
    this.heroTag,
    this.detailTitle,
    this.detailRows = const [],
  });

  final String? path;
  final String label;
  final double aspectRatio;
  final double minHeight;
  final ResourceInfo? resource;
  final String? heroTag;
  final String? detailTitle;
  final List<ResourceDetailRow> detailRows;

  @override
  Widget build(BuildContext context) {
    final file = path == null ? null : File(path!);
    final preview = ConstrainedBox(
      constraints: BoxConstraints(minHeight: minHeight),
      child: AspectRatio(
        aspectRatio: aspectRatio,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(22),
          child: Stack(
            fit: StackFit.expand,
            children: [
              _CheckerBackground(),
              if (file != null && file.existsSync())
                Image.file(
                  file,
                  fit: BoxFit.contain,
                  filterQuality: FilterQuality.medium,
                  errorBuilder: (_, _, _) => _PreviewPlaceholder(label: label),
                )
              else
                _PreviewPlaceholder(label: label),
            ],
          ),
        ),
      ),
    );
    if (heroTag == null) return preview;
    return Hero(
      tag: heroTag!,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(22),
          onTap: () => showResourceDetailOverlay(
            context,
            heroTag: heroTag!,
            title: detailTitle ?? label,
            icon: Icons.portrait_rounded,
            kind: ResourceDetailKind.image,
            resource: resource,
            rows: detailRows,
          ),
          child: preview,
        ),
      ),
    );
  }
}

class _CheckerBackground extends StatelessWidget {
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

class _PreviewPlaceholder extends StatelessWidget {
  const _PreviewPlaceholder({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            Icons.image_not_supported_rounded,
            color: Theme.of(context).colorScheme.outline,
          ),
          const SizedBox(height: 8),
          Text(
            label,
            textAlign: TextAlign.center,
            style: Theme.of(
              context,
            ).textTheme.bodySmall?.copyWith(color: PrettyColors.muted),
          ),
        ],
      ),
    );
  }
}

class _ResourceMeta extends StatelessWidget {
  const _ResourceMeta({required this.resource, this.fallbackPath});

  final ResourceInfo? resource;
  final String? fallbackPath;

  @override
  Widget build(BuildContext context) {
    final path = resource?.path ?? fallbackPath;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        InfoRow(
          label: context.t('field.file'),
          value: resource?.name ?? _basename(path) ?? '-',
        ),
        InfoRow(
          label: context.t('fileSize'),
          value: _formatBytes(resource?.sizeBytes ?? _fileLength(path)),
        ),
        if (resource?.width != null || resource?.height != null)
          InfoRow(
            label: context.t('imageOriginalSize'),
            value: '${resource?.width ?? '-'} x ${resource?.height ?? '-'}',
          ),
        if (path != null) InfoRow(label: context.t('path'), value: path),
      ],
    );
  }
}

Rect _cropRect(Size size, CharacterEditorState page, ResourceInfo? image) {
  final source = _sourceImageSize(image, size);
  final crop = _iconCropPixels(page, image, fallback: source);
  if (source.width <= 0 || source.height <= 0) {
    return Offset.zero & size;
  }
  return Rect.fromLTWH(
    (crop.left / source.width) * size.width,
    (crop.top / source.height) * size.height,
    (crop.width / source.width) * size.width,
    (crop.height / source.height) * size.height,
  );
}

Size _sourceImageSize(ResourceInfo? image, Size fallback) {
  return Size(
    (image?.width ?? fallback.width).toDouble(),
    (image?.height ?? fallback.height).toDouble(),
  );
}

Rect _iconCropPixels(
  CharacterEditorState page,
  ResourceInfo? image, {
  required Size fallback,
}) {
  final source = _sourceImageSize(image, fallback);
  final imageWidth = source.width;
  final imageHeight = source.height;
  if (imageWidth <= 0 || imageHeight <= 0) return Rect.zero;
  final minSide = math.min(imageWidth, imageHeight);
  final side = (minSide * page.cropSize.clamp(0.05, 1.0))
      .clamp(1.0, minSide)
      .toDouble();
  final centerX = imageWidth * page.cropCenterX.clamp(0.0, 1.0);
  final centerY = imageHeight * page.cropCenterY.clamp(0.0, 1.0);
  final maxLeft = math.max(0.0, imageWidth - side);
  final maxTop = math.max(0.0, imageHeight - side);
  final left = (centerX - side / 2).clamp(0.0, maxLeft).toDouble();
  final top = (centerY - side / 2).clamp(0.0, maxTop).toDouble();
  return Rect.fromLTWH(
    left,
    top,
    math.min(side, imageWidth - left),
    math.min(side, imageHeight - top),
  );
}

String _fileStamp(File? file) {
  if (file == null || !file.existsSync()) return 'missing';
  try {
    final stat = file.statSync();
    return '${stat.modified.millisecondsSinceEpoch}:${stat.size}';
  } catch (_) {
    return 'unknown';
  }
}

double _resourceAspectRatio(
  ResourceInfo? resource, {
  required double fallback,
}) {
  final width = resource?.width;
  final height = resource?.height;
  if (width == null || height == null || height <= 0) return fallback;
  return (width / height).clamp(0.35, 2.4).toDouble();
}

bool _canSaveCharacter(CharacterEditorState page) {
  final edit = page.edit;
  if (page.scan == null ||
      edit == null ||
      page.phase == OperationPhase.saving) {
    return false;
  }
  return page.hasPreviewImage &&
      edit.publisherId.trim().isNotEmpty &&
      edit.characterId.trim().isNotEmpty &&
      edit.defaultName.trim().isNotEmpty;
}

String _sourceKindLabel(CharacterEditorState page) {
  return switch (page.scan?.sourceKind) {
    CharacterInputKind.image => 'image',
    CharacterInputKind.arcpkg => 'arcpkg',
    CharacterInputKind.unknown || null => '-',
  };
}

IconData _phaseIcon(OperationPhase phase) => switch (phase) {
  OperationPhase.failed => Icons.error_rounded,
  OperationPhase.saved => Icons.check_circle_rounded,
  OperationPhase.saving ||
  OperationPhase.scanning => Icons.hourglass_empty_rounded,
  OperationPhase.ready || OperationPhase.scanned => Icons.verified_rounded,
  OperationPhase.idle => Icons.person_outline_rounded,
};

PrettyPillTone _phaseTone(OperationPhase phase) => switch (phase) {
  OperationPhase.failed => PrettyPillTone.danger,
  OperationPhase.saved => PrettyPillTone.success,
  OperationPhase.ready || OperationPhase.scanned => PrettyPillTone.primary,
  OperationPhase.saving || OperationPhase.scanning => PrettyPillTone.warning,
  OperationPhase.idle => PrettyPillTone.neutral,
};

EdgeInsets _pagePadding(BuildContext context) {
  return AppLayoutTokens.pagePadding(context);
}

String _formatBytes(int? bytes) {
  if (bytes == null) return '-';
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
  return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
}

int? _fileLength(String? path) {
  if (path == null || path.isEmpty) return null;
  try {
    final file = File(path);
    if (file.existsSync()) return file.lengthSync();
  } catch (_) {
    return null;
  }
  return null;
}

String? _basename(String? path) {
  if (path == null || path.isEmpty) return null;
  return p.basename(path);
}

extension on String {
  String ifBlank(String fallback) => trim().isEmpty ? fallback : this;
}
