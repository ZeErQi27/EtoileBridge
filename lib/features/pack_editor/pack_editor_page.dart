// ignore_for_file: unused_element

import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;

import '../../app/app_spacing.dart';
import '../../app/app_state.dart';
import '../../app/routes.dart';
import '../../app/safe_action.dart';
import '../../core/i18n/app_strings.dart';
import '../../core/models/operation_models.dart';
import '../../core/models/pack_models.dart';
import '../../core/models/single_song_models.dart';
import '../../shared/layout/app_layout_tokens.dart';
import '../../shared/cards/surface_card.dart';
import '../../shared/previews/mock_preview.dart';
import '../../shared/widgets/conversion_options_controls.dart';
import '../../shared/widgets/info_row.dart';
import '../../shared/widgets/log_panel.dart';
import '../../shared/widgets/md3_components.dart';
import '../../shared/widgets/pretty_ui.dart';
import '../../shared/widgets/resource_detail_overlay.dart';
import 'pack_editor_state.dart';

class PackEditorPage extends StatelessWidget {
  const PackEditorPage({super.key});

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final page = state.packEditor;
    final busy =
        page.phase == OperationPhase.scanning ||
        page.phase == OperationPhase.saving;
    return SingleChildScrollView(
      padding: AppLayoutTokens.pagePadding(context),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(
            maxWidth: AppLayoutTokens.pageContentMaxWidth,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _PackHeroHeader(page: page),
              const SizedBox(height: 24),
              _PackWorkbenchLayout(
                left: [
                  _PackInputWorkbenchCard(state: state, page: page, busy: busy),
                  _PackSaveWorkbenchCard(state: state, page: page, busy: busy),
                  if (page.mode != PackEditorMode.existing)
                    _PackPreprocessWorkbenchCard(state: state, page: page),
                ],
                middle: [
                  _PackMetadataWorkbenchCard(state: state, page: page),
                  _PackLevelWorkbenchCard(state: state, page: page),
                ],
                right: [
                  _PackCoverWorkbenchCard(page: page),
                  _SelectedPackEntryWorkbenchCard(page: page),
                  _PackDiagnosticsWorkbenchCard(page: page),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PackHeroHeader extends StatelessWidget {
  const _PackHeroHeader({required this.page});

  final PackEditorState page;

  @override
  Widget build(BuildContext context) {
    final warnings = _packWarningItems(page);
    final scan = page.scan;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Column(
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
                context.t('page.packEditor'),
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: PrettyColors.muted,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
        if (scan != null || warnings.isNotEmpty)
          Wrap(
            spacing: 8,
            runSpacing: 8,
            alignment: WrapAlignment.end,
            children: [
              PrettyPill(
                icon: Icons.inventory_2_rounded,
                label: page.packId,
                tone: PrettyPillTone.primary,
              ),
              PrettyPill(
                icon: Icons.queue_music_rounded,
                label: '${page.entryCount} ${context.t('levelList')}',
                tone: PrettyPillTone.neutral,
              ),
              if (warnings.isNotEmpty)
                ActionChip(
                  avatar: const Icon(Icons.warning_amber_rounded, size: 18),
                  label: Text(
                    '${context.t('warningCount')} ${warnings.length}',
                  ),
                  onPressed: () => _openPackWarningsDialog(context, page),
                ),
            ],
          ),
      ],
    );
  }
}

class _PackWorkbenchLayout extends StatelessWidget {
  const _PackWorkbenchLayout({
    required this.left,
    required this.middle,
    required this.right,
  });

  final List<Widget> left;
  final List<Widget> middle;
  final List<Widget> right;

  @override
  Widget build(BuildContext context) {
    return AppWorkspaceLayout(left: left, middle: middle, right: right);
  }
}

class _PackColumn extends StatelessWidget {
  const _PackColumn({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var i = 0; i < children.length; i++) ...[
          children[i],
          if (i != children.length - 1) const SizedBox(height: 20),
        ],
      ],
    );
  }
}

class _PackInputWorkbenchCard extends StatelessWidget {
  const _PackInputWorkbenchCard({
    required this.state,
    required this.page,
    required this.busy,
  });

  final AppState state;
  final PackEditorState page;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    return PrettyCard(
      title: context.t('input'),
      icon: Icons.folder_open_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: PackEditorMode.values
                .map(
                  (mode) => _PackModeButton(
                    mode: mode,
                    selected: page.mode == mode,
                    onPressed: busy
                        ? null
                        : () => safeAction(
                            context,
                            id: 'pack.mode.change',
                            label: mode.name,
                            page: AppPageId.packEditor,
                            action: () => state.setPackMode(mode),
                          ),
                  ),
                )
                .toList(),
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: _packInputActions(context, state, page, busy),
          ),
          const SizedBox(height: 18),
          Text(
            context.t('currentInput'),
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: PrettyColors.muted,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 8),
          if (page.inputPaths.isEmpty)
            DashedEmptyBox(
              icon: Icons.inventory_2_outlined,
              title: context.t('notSelected'),
              subtitle: context.t('subtitle.packEditor'),
              minHeight: 138,
            )
          else
            _PackInputList(state: state, paths: page.inputPaths, busy: busy),
          if (kDebugMode) ...[
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                onPressed: busy
                    ? null
                    : () => safeAction(
                        context,
                        id: 'pack.mockImport',
                        label: context.t('mockAction'),
                        page: AppPageId.packEditor,
                        action: state.mockImportPack,
                      ),
                icon: const Icon(Icons.science_rounded),
                label: Text(context.t('mockAction')),
              ),
            ),
          ],
        ],
      ),
    );
  }

  List<Widget> _packInputActions(
    BuildContext context,
    AppState state,
    PackEditorState page,
    bool busy,
  ) {
    switch (page.mode) {
      case PackEditorMode.official:
        return [
          PrettyGlassButton(
            primary: true,
            icon: Icons.archive_rounded,
            label: context.t('chooseZip'),
            onPressed: busy
                ? null
                : () => safeAction(
                    context,
                    id: 'pack.pickOfficialZip',
                    label: context.t('chooseZip'),
                    page: AppPageId.packEditor,
                    action: state.pickOfficialPackZip,
                  ),
          ),
          PrettyGlassButton(
            icon: Icons.folder_rounded,
            label: context.t('chooseFolder'),
            onPressed: busy
                ? null
                : () => safeAction(
                    context,
                    id: 'pack.pickOfficialFolder',
                    label: context.t('chooseFolder'),
                    page: AppPageId.packEditor,
                    action: state.pickOfficialPackFolder,
                  ),
          ),
        ];
      case PackEditorMode.bundle:
        return [
          PrettyGlassButton(
            primary: true,
            icon: Icons.library_add_rounded,
            label: context.t('chooseArcpkg'),
            onPressed: busy
                ? null
                : () => safeAction(
                    context,
                    id: 'pack.pickArcpkgMultiple',
                    label: context.t('chooseArcpkg'),
                    page: AppPageId.packEditor,
                    action: state.pickPackBundleArcpkg,
                  ),
          ),
          PrettyGlassButton(
            icon: Icons.add_rounded,
            label: context.t('addArcpkg'),
            onPressed: busy || page.inputPaths.isEmpty
                ? null
                : () => safeAction(
                    context,
                    id: 'pack.input.appendArcpkg',
                    label: context.t('addArcpkg'),
                    page: AppPageId.packEditor,
                    action: state.appendPackBundleArcpkg,
                  ),
          ),
          PrettyGlassButton(
            icon: Icons.create_new_folder_rounded,
            label: page.inputPaths.isEmpty
                ? context.t('chooseFolder')
                : context.t('addFolder'),
            onPressed: busy
                ? null
                : () => safeAction(
                    context,
                    id: page.inputPaths.isEmpty
                        ? 'pack.pickArcpkgFolder'
                        : 'pack.input.appendFolder',
                    label: context.t('chooseFolder'),
                    page: AppPageId.packEditor,
                    action: page.inputPaths.isEmpty
                        ? state.pickPackBundleFolder
                        : state.appendPackBundleFolder,
                  ),
          ),
        ];
      case PackEditorMode.existing:
        return [
          PrettyGlassButton(
            primary: true,
            icon: Icons.edit_document,
            label: context.t('chooseArcpkg'),
            onPressed: busy
                ? null
                : () => safeAction(
                    context,
                    id: 'pack.pickExisting',
                    label: context.t('chooseArcpkg'),
                    page: AppPageId.packEditor,
                    action: state.pickExistingPack,
                  ),
          ),
          PrettyGlassButton(
            icon: Icons.library_add_rounded,
            label: context.t('addArcpkg'),
            onPressed: busy || page.inputPaths.isEmpty
                ? null
                : () => safeAction(
                    context,
                    id: 'pack.input.appendArcpkg',
                    label: context.t('addArcpkg'),
                    page: AppPageId.packEditor,
                    action: state.appendExistingPackArcpkg,
                  ),
          ),
          PrettyGlassButton(
            icon: Icons.create_new_folder_rounded,
            label: context.t('addFolder'),
            onPressed: busy || page.inputPaths.isEmpty
                ? null
                : () => safeAction(
                    context,
                    id: 'pack.input.appendFolder',
                    label: context.t('addFolder'),
                    page: AppPageId.packEditor,
                    action: state.appendExistingPackFolder,
                  ),
          ),
        ];
    }
  }
}

class _PackModeButton extends StatelessWidget {
  const _PackModeButton({
    required this.mode,
    required this.selected,
    required this.onPressed,
  });

  final PackEditorMode mode;
  final bool selected;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final icon = switch (mode) {
      PackEditorMode.official => Icons.inventory_2_rounded,
      PackEditorMode.bundle => Icons.all_inbox_rounded,
      PackEditorMode.existing => Icons.edit_document,
    };
    return Material(
      color: selected
          ? scheme.primaryContainer.withValues(alpha: 0.68)
          : scheme.surfaceContainerHighest.withValues(alpha: 0.46),
      borderRadius: BorderRadius.circular(PrettyRadii.control),
      child: InkWell(
        borderRadius: BorderRadius.circular(PrettyRadii.control),
        onTap: onPressed,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 48, maxWidth: 230),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 13),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(icon, size: 20, color: scheme.primary),
                const SizedBox(width: 9),
                Flexible(
                  child: Text(
                    context.t('packMode.${mode.name}'),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      color: selected
                          ? scheme.onPrimaryContainer
                          : scheme.onSurface,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _PackInputList extends StatelessWidget {
  const _PackInputList({
    required this.state,
    required this.paths,
    required this.busy,
  });

  final AppState state;
  final List<String> paths;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    final visible = paths.take(5).toList();
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(
          context,
        ).colorScheme.primaryContainer.withValues(alpha: 0.16),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: PrettyColors.border),
      ),
      child: Column(
        children: [
          for (var i = 0; i < visible.length; i++)
            ListTile(
              dense: true,
              leading: const Icon(Icons.insert_drive_file_rounded),
              title: Text(
                p.basename(visible[i]),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              subtitle: Text(
                visible[i],
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              trailing: IconButton(
                tooltip: context.t('clearInputs'),
                onPressed: busy
                    ? null
                    : () => safeAction(
                        context,
                        id: 'pack.input.remove',
                        label: visible[i],
                        page: AppPageId.packEditor,
                        action: () => state.removePackInput(i),
                      ),
                icon: const Icon(Icons.close_rounded),
              ),
            ),
          if (paths.length > visible.length)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(
                '+ ${paths.length - visible.length}',
                style: Theme.of(context).textTheme.labelMedium,
              ),
            ),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton.icon(
              onPressed: busy
                  ? null
                  : () => safeAction(
                      context,
                      id: 'pack.input.clear',
                      label: 'pack',
                      page: AppPageId.packEditor,
                      action: state.clearPackInput,
                    ),
              icon: const Icon(Icons.delete_sweep_rounded),
              label: Text(context.t('clearInputs')),
            ),
          ),
        ],
      ),
    );
  }
}

class _PackMetadataWorkbenchCard extends StatelessWidget {
  const _PackMetadataWorkbenchCard({required this.state, required this.page});

  final AppState state;
  final PackEditorState page;

  @override
  Widget build(BuildContext context) {
    final scan = page.scan;
    final warnings = _packWarningItems(page);
    return PrettyCard(
      title: context.t('packSettings'),
      icon: Icons.tune_rounded,
      trailing: warnings.isEmpty
          ? PrettyPill(
              icon: Icons.verified_rounded,
              label: context.t('readyToSave'),
              tone: PrettyPillTone.success,
            )
          : ActionChip(
              avatar: const Icon(Icons.warning_amber_rounded, size: 18),
              label: Text('${warnings.length}'),
              onPressed: () => _openPackWarningsDialog(context, page),
            ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              _PackMetricTile(
                label: 'identifier',
                value:
                    scan?.packIdentifier ?? 'etoilebridge.${page.packId}.pack',
              ),
              _PackMetricTile(
                label: 'directory',
                value: scan?.packDirectory ?? page.packId,
              ),
              _PackMetricTile(
                label: context.t('levelList'),
                value: '${page.entryCount}',
              ),
              _PackMetricTile(
                label: context.t('packCover'),
                value: scan?.packImage == null
                    ? context.t('resource.missing')
                    : context.t('resource.identified'),
              ),
            ],
          ),
          const SizedBox(height: 16),
          TextFormField(
            key: ValueKey('pack-name-${page.packName}'),
            initialValue: page.packName,
            decoration: const InputDecoration(labelText: 'packName'),
            onChanged: (value) => state.updatePackMetadata(packName: value),
          ),
          const SizedBox(height: 10),
          TextFormField(
            key: ValueKey('pack-id-${page.packId}'),
            initialValue: page.packId,
            decoration: const InputDecoration(labelText: 'packId'),
            onChanged: (value) => state.updatePackMetadata(packId: value),
          ),
          if (scan != null) ...[
            const SizedBox(height: 14),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                PrettyPill(
                  label:
                      '${context.t('existingLevels')}: ${scan.existingLevelCount}',
                ),
                PrettyPill(
                  label: '${context.t('addedLevels')}: ${scan.addedLevelCount}',
                ),
                PrettyPill(
                  label: '${context.t('finalLevels')}: ${scan.finalLevelCount}',
                ),
                if (scan.renamedConflictCount > 0)
                  PrettyPill(
                    icon: Icons.drive_file_rename_outline_rounded,
                    label: '${scan.renamedConflictCount}',
                    tone: PrettyPillTone.warning,
                  ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _PackMetricTile extends StatelessWidget {
  const _PackMetricTile({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: const BoxConstraints(minWidth: 150, maxWidth: 250),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Theme.of(
            context,
          ).colorScheme.primaryContainer.withValues(alpha: 0.14),
          borderRadius: BorderRadius.circular(18),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: PrettyColors.muted,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              value,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(
                context,
              ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w900),
            ),
          ],
        ),
      ),
    );
  }
}

class _PackPreprocessWorkbenchCard extends StatelessWidget {
  const _PackPreprocessWorkbenchCard({required this.state, required this.page});

  final AppState state;
  final PackEditorState page;

  @override
  Widget build(BuildContext context) {
    return PrettyCard(
      title: context.t('preprocess'),
      icon: Icons.auto_fix_high_rounded,
      compact: true,
      child: PreprocessOptionsPanel(
        value: page.preprocessOptions,
        onChanged: state.updatePackPreprocess,
        pageId: AppPageId.packEditor,
        actionPrefix: 'pack.preprocess',
      ),
    );
  }
}

class _PackSaveWorkbenchCard extends StatelessWidget {
  const _PackSaveWorkbenchCard({
    required this.state,
    required this.page,
    required this.busy,
  });

  final AppState state;
  final PackEditorState page;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    final result = page.saveResult;
    final enabledEntries = page.exportableEntryCount;
    final enabledCharts = page.exportableChartCount;
    final canSave = page.scan != null && enabledCharts > 0 && !busy;
    final failed = page.phase == OperationPhase.failed && page.error != null;
    return PrettyCard(
      title: context.t('saveAndExport'),
      icon: Icons.download_rounded,
      emphasized: result != null,
      trailing: PrettyPill(
        icon: result != null
            ? Icons.check_circle_rounded
            : failed
            ? Icons.error_rounded
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
            _PackSaveResultPanel(result: result, state: state)
          else if (failed)
            Text(
              page.error!,
              style: const TextStyle(color: PrettyColors.danger),
            )
          else
            Text(
              canSave
                  ? '$enabledEntries ${context.t('levelList')} / $enabledCharts ${context.t('charts')}'
                  : page.scan != null && enabledCharts == 0
                  ? context.t('noExportableCharts')
                  : context.t('saveDisabledHint'),
              style: Theme.of(
                context,
              ).textTheme.bodyMedium?.copyWith(color: PrettyColors.muted),
            ),
          const SizedBox(height: 14),
          FilledButton.icon(
            onPressed: canSave
                ? () => safeAction(
                    context,
                    id: 'pack.save',
                    label: page.packId,
                    page: AppPageId.packEditor,
                    action: state.savePack,
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

class _PackSaveResultPanel extends StatelessWidget {
  const _PackSaveResultPanel({required this.result, required this.state});

  final PackSaveResult result;
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
              const Icon(
                Icons.inventory_2_rounded,
                color: PrettyColors.primary,
              ),
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
              Text(_formatBytes(result.sizeBytes)),
            ],
          ),
          const SizedBox(height: 10),
          SelectableText(
            outputPath,
            maxLines: 2,
            style: Theme.of(context).textTheme.bodySmall,
          ),
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
        ],
      ),
    );
  }
}

class _PackCoverWorkbenchCard extends StatelessWidget {
  const _PackCoverWorkbenchCard({required this.page});

  final PackEditorState page;

  @override
  Widget build(BuildContext context) {
    final cover = page.scan?.packImage;
    return PrettyCard(
      title: context.t('packCover'),
      icon: Icons.image_rounded,
      trailing: PrettyPill(
        label: cover == null
            ? context.t('resource.missing')
            : context.t('resource.identified'),
        tone: cover == null ? PrettyPillTone.neutral : PrettyPillTone.success,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _PackImagePreview(
            label: context.t('packCover'),
            resource: cover,
            aspectRatio: 3 / 4,
            fit: BoxFit.contain,
            heroTag: 'pack-cover-resource',
            detailTitle: context.t('packCover'),
          ),
          if (cover != null) ...[
            const SizedBox(height: 10),
            InfoRow(
              label: context.t('path'),
              value: cover.name ?? cover.path ?? '-',
            ),
            InfoRow(
              label: context.t('size'),
              value: _formatBytes(cover.sizeBytes),
            ),
          ],
        ],
      ),
    );
  }
}

class _SelectedPackEntryWorkbenchCard extends StatelessWidget {
  const _SelectedPackEntryWorkbenchCard({required this.page});

  final PackEditorState page;

  @override
  Widget build(BuildContext context) {
    final index = page.selectedEntryIndex ?? (page.entryCount > 0 ? 0 : null);
    final entry = index == null || page.entries.isEmpty
        ? null
        : page.entries[index];
    final mock = index == null || page.levels.isEmpty
        ? null
        : page.levels[index];
    final title = entry?.title ?? mock?.title;
    return PrettyCard(
      title: context.t('selectedSong'),
      icon: Icons.music_note_rounded,
      child: title == null
          ? DashedEmptyBox(
              icon: Icons.queue_music_rounded,
              title: context.t('notSelected'),
              subtitle: context.t('levelList'),
              minHeight: 140,
            )
          : Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (entry != null)
                  _PackImagePreview(
                    label: entry.title ?? '',
                    resource: entry.jacket,
                    aspectRatio: 1,
                    fit: BoxFit.contain,
                    heroTag: 'pack-selected-jacket-${entry.identifier}',
                    detailTitle: entry.title ?? context.t('selectedSong'),
                  ),
                const SizedBox(height: 12),
                Text(
                  title,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  entry?.artist ?? mock?.composer ?? '-',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(
                    context,
                  ).textTheme.bodyMedium?.copyWith(color: PrettyColors.muted),
                ),
                const SizedBox(height: 12),
                InfoRow(
                  label: 'identifier',
                  value: entry?.identifier ?? mock?.identifier ?? '-',
                ),
                InfoRow(
                  label: context.t('charts'),
                  value: '${entry?.charts.length ?? mock?.chartCount ?? 0}',
                ),
                if (entry != null && entry.charts.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      for (final chart in entry.charts)
                        PrettyPill(
                          label:
                              [
                                    'RC ${chart.ratingClass}',
                                    chart.difficulty,
                                    chart.chartConstant?.toString(),
                                  ]
                                  .where(
                                    (text) => text != null && text.isNotEmpty,
                                  )
                                  .join(' / '),
                          tone: chart.canConvert
                              ? chart.enabled
                                    ? PrettyPillTone.success
                                    : PrettyPillTone.neutral
                              : PrettyPillTone.warning,
                        ),
                    ],
                  ),
                  if (entry.excludedChartCount > 0) ...[
                    const SizedBox(height: 8),
                    PrettyPill(
                      icon: Icons.remove_circle_outline_rounded,
                      label:
                          '${entry.excludedChartCount} ${context.t('chartExcluded')}',
                      tone: PrettyPillTone.warning,
                    ),
                  ],
                ],
              ],
            ),
    );
  }
}

class _PackDiagnosticsWorkbenchCard extends StatelessWidget {
  const _PackDiagnosticsWorkbenchCard({required this.page});

  final PackEditorState page;

  @override
  Widget build(BuildContext context) {
    final warnings = _packWarningItems(page);
    return PrettyCard(
      title: context.t('scanDiagnostics'),
      icon: Icons.fact_check_rounded,
      compact: true,
      trailing: warnings.isEmpty
          ? null
          : ActionChip(
              visualDensity: VisualDensity.compact,
              avatar: const Icon(Icons.warning_amber_rounded, size: 18),
              label: Text('${warnings.length}'),
              onPressed: () => _openPackWarningsDialog(context, page),
            ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          PrettyExpandableSection(
            title: context.t('warningCount'),
            icon: Icons.warning_amber_rounded,
            child: warnings.isEmpty
                ? Align(
                    alignment: Alignment.centerLeft,
                    child: Text(context.t('diagnosticsUnavailable')),
                  )
                : Column(
                    children: [
                      for (final warning in warnings.take(8))
                        ListTile(
                          dense: true,
                          contentPadding: EdgeInsets.zero,
                          leading: const Icon(Icons.warning_amber_rounded),
                          title: Text(warning.message),
                          subtitle: Text(warning.targetTitle ?? warning.scope),
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

class _PackLevelWorkbenchCard extends StatelessWidget {
  const _PackLevelWorkbenchCard({required this.state, required this.page});

  final AppState state;
  final PackEditorState page;

  @override
  Widget build(BuildContext context) {
    return PrettyCard(
      title: context.t('levelList'),
      icon: Icons.queue_music_rounded,
      trailing: page.entryCount == 0
          ? null
          : Wrap(
              spacing: 8,
              children: [
                TextButton(
                  onPressed: state.expandAllPackLevels,
                  child: Text(context.t('expandAll')),
                ),
                TextButton(
                  onPressed: state.collapseAllPackLevels,
                  child: Text(context.t('collapseAll')),
                ),
              ],
            ),
      child: page.entryCount == 0
          ? DashedEmptyBox(
              icon: Icons.queue_music_rounded,
              title: context.t('notSelected'),
              subtitle: context.t('subtitle.packEditor'),
              minHeight: 170,
            )
          : Column(
              children: [
                for (var i = 0; i < page.entryCount; i++)
                  _PackLevelPrettyRow(index: i),
              ],
            ),
    );
  }
}

class _PackLevelPrettyRow extends StatelessWidget {
  const _PackLevelPrettyRow({required this.index});

  final int index;

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final page = state.packEditor;
    final entry = page.entries.isNotEmpty ? page.entries[index] : null;
    final level = entry == null ? page.levels[index] : null;
    final selected = page.selectedEntryIndex == index;
    final expanded = page.expanded.contains(index);
    final highlighted = page.highlightedWarningEntryIndex == index;
    final title = entry?.title ?? level?.title ?? '-';
    final composer = entry?.artist ?? level?.composer ?? '-';
    final identifier =
        entry?.identifier ?? entry?.levelId ?? level?.identifier ?? '-';
    final chartCount = entry?.charts.length ?? level?.chartCount ?? 0;
    final exportableChartCount =
        entry?.exportableChartCount ?? level?.chartCount ?? 0;
    final enabled = entry?.enabled ?? level?.enabled ?? true;
    final scheme = Theme.of(context).colorScheme;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 180),
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: highlighted
            ? scheme.tertiaryContainer.withValues(alpha: 0.45)
            : selected
            ? scheme.primaryContainer.withValues(alpha: 0.28)
            : scheme.surfaceContainerHighest.withValues(alpha: 0.22),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: selected
              ? scheme.primary.withValues(alpha: 0.42)
              : PrettyColors.border,
        ),
      ),
      child: Column(
        children: [
          InkWell(
            borderRadius: BorderRadius.circular(22),
            onTap: () => state.selectPackEntry(index),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  _PackThumb(
                    resource: entry?.jacket,
                    label: title,
                    heroTag: 'pack-list-jacket-$index',
                    detailTitle: title,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.titleSmall
                              ?.copyWith(fontWeight: FontWeight.w900),
                        ),
                        const SizedBox(height: 3),
                        Text(
                          composer,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(color: PrettyColors.muted),
                        ),
                        const SizedBox(height: 6),
                        Wrap(
                          spacing: 6,
                          runSpacing: 6,
                          children: [
                            PrettyPill(
                              label:
                                  '${context.t('charts')} $exportableChartCount/$chartCount',
                              tone: PrettyPillTone.neutral,
                            ),
                            if ((entry?.warnings.length ?? 0) > 0)
                              PrettyPill(
                                icon: Icons.warning_amber_rounded,
                                label: '${entry!.warnings.length}',
                                tone: PrettyPillTone.warning,
                              ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  Checkbox(
                    value: enabled,
                    onChanged: (value) => safeAction(
                      context,
                      id: 'pack.entry.toggleEnabled',
                      label: 'level[$index].enabled',
                      page: AppPageId.packEditor,
                      action: () =>
                          state.setPackLevelEnabled(index, value ?? true),
                    ),
                  ),
                  IconButton(
                    onPressed: () => safeAction(
                      context,
                      id: expanded
                          ? 'pack.entry.collapse'
                          : 'pack.entry.expand',
                      label: 'level[$index]',
                      page: AppPageId.packEditor,
                      action: () => state.togglePackLevel(index),
                    ),
                    icon: Icon(
                      expanded
                          ? Icons.expand_less_rounded
                          : Icons.expand_more_rounded,
                    ),
                  ),
                ],
              ),
            ),
          ),
          AnimatedSize(
            duration: const Duration(milliseconds: 180),
            curve: Curves.easeOutCubic,
            child: expanded
                ? Padding(
                    padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
                    child: Column(
                      children: [
                        TextFormField(
                          key: ValueKey('pack-pretty-title-$index-$title'),
                          initialValue: title,
                          decoration: InputDecoration(
                            labelText: context.t('field.title'),
                          ),
                          onChanged: (value) {
                            if (entry != null) entry.title = value;
                          },
                        ),
                        const SizedBox(height: 8),
                        TextFormField(
                          key: ValueKey(
                            'pack-pretty-composer-$index-$composer',
                          ),
                          initialValue: composer,
                          decoration: InputDecoration(
                            labelText: context.t('field.composer'),
                          ),
                          onChanged: (value) {
                            if (entry != null) entry.artist = value;
                          },
                        ),
                        const SizedBox(height: 10),
                        Align(
                          alignment: Alignment.centerLeft,
                          child: Text(
                            identifier,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.bodySmall
                                ?.copyWith(color: PrettyColors.muted),
                          ),
                        ),
                        if (entry != null)
                          for (
                            var chartIndex = 0;
                            chartIndex < entry.charts.length;
                            chartIndex++
                          )
                            _PackChartToggleRow(
                              entryIndex: index,
                              chartIndex: chartIndex,
                              chart: entry.charts[chartIndex],
                            ),
                        if (entry != null && entry.excludedChartCount > 0)
                          Padding(
                            padding: const EdgeInsets.only(top: 8),
                            child: Align(
                              alignment: Alignment.centerLeft,
                              child: Text(
                                context.t('excludedChartHint'),
                                style: Theme.of(context).textTheme.bodySmall
                                    ?.copyWith(color: PrettyColors.warning),
                              ),
                            ),
                          ),
                      ],
                    ),
                  )
                : const SizedBox.shrink(),
          ),
        ],
      ),
    );
  }
}

class _PackChartToggleRow extends StatelessWidget {
  const _PackChartToggleRow({
    required this.entryIndex,
    required this.chartIndex,
    required this.chart,
  });

  final int entryIndex;
  final int chartIndex;
  final PackChartEntry chart;

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final scheme = Theme.of(context).colorScheme;
    return Container(
      margin: const EdgeInsets.only(top: 8),
      decoration: BoxDecoration(
        color: chart.enabled
            ? scheme.surfaceContainerHighest.withValues(alpha: 0.32)
            : scheme.errorContainer.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: PrettyColors.border),
      ),
      child: CheckboxListTile(
        dense: true,
        contentPadding: const EdgeInsets.symmetric(horizontal: 10),
        value: chart.enabled && chart.canConvert,
        onChanged: chart.canConvert
            ? (value) => safeAction(
                context,
                id: 'pack.chart.toggleEnabled',
                label: 'entry[$entryIndex].chart[$chartIndex]',
                page: AppPageId.packEditor,
                action: () => state.setPackChartEnabled(
                  entryIndex,
                  chartIndex,
                  value ?? true,
                ),
              )
            : null,
        controlAffinity: ListTileControlAffinity.leading,
        title: Text(
          chart.difficulty.isEmpty
              ? context.t('field.difficulty')
              : chart.difficulty,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        subtitle: Text(
          [
            'RC ${chart.ratingClass}',
            chart.chartConstant?.toString(),
            chart.chartPath == null ? null : p.basename(chart.chartPath!),
          ].whereType<String>().where((text) => text.isNotEmpty).join(' / '),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        secondary: PrettyPill(
          label: !chart.canConvert
              ? context.t('optional')
              : chart.enabled
              ? context.t('convertible')
              : context.t('chartExcluded'),
          tone: !chart.canConvert
              ? PrettyPillTone.warning
              : chart.enabled
              ? PrettyPillTone.success
              : PrettyPillTone.neutral,
        ),
      ),
    );
  }
}

class _PackThumb extends StatelessWidget {
  const _PackThumb({
    required this.resource,
    required this.label,
    this.heroTag,
    this.detailTitle,
  });

  final ResourceInfo? resource;
  final String label;
  final String? heroTag;
  final String? detailTitle;

  @override
  Widget build(BuildContext context) {
    final path = resource?.path;
    final exists = path != null && File(path).existsSync();
    final child = ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: SizedBox(
        width: 64,
        height: 64,
        child: ColoredBox(
          color: Theme.of(
            context,
          ).colorScheme.primaryContainer.withValues(alpha: 0.18),
          child: exists
              ? Image.file(File(path), fit: BoxFit.cover)
              : Icon(
                  Icons.music_note_rounded,
                  color: Theme.of(context).colorScheme.primary,
                ),
        ),
      ),
    );
    if (resource == null || heroTag == null) return child;
    return Hero(
      tag: heroTag!,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: () => showResourceDetailOverlay(
            context,
            heroTag: heroTag!,
            title: detailTitle ?? label,
            icon: Icons.image_rounded,
            kind: ResourceDetailKind.image,
            resource: resource,
            rows: [
              ResourceDetailRow(label: context.t('field.file'), value: label),
            ],
          ),
          child: child,
        ),
      ),
    );
  }
}

class _PackSummaryCard extends StatelessWidget {
  const _PackSummaryCard({required this.page});

  final PackEditorState page;

  @override
  Widget build(BuildContext context) {
    final scan = page.scan;
    final warnings = _packWarningItems(page);
    final warningCount = warnings.length;
    return SurfaceCard(
      title: page.packName,
      subtitle: scan?.packIdentifier ?? 'etoilebridge.${page.packId}.pack',
      icon: Icons.inventory_2_rounded,
      variant: SurfaceCardVariant.filled,
      compact: true,
      trailing: Chip(
        visualDensity: VisualDensity.compact,
        avatar: const Icon(Icons.queue_music_rounded, size: 18),
        label: Text('${page.entryCount}'),
      ),
      child: Wrap(
        spacing: AppSpacing.xs,
        runSpacing: AppSpacing.xs,
        children: [
          _PackSummaryChip(icon: Icons.tag_rounded, label: page.packId),
          _PackSummaryChip(
            icon: Icons.folder_rounded,
            label: scan?.packDirectory ?? page.packId,
          ),
          _PackSummaryChip(
            icon: scan?.packImage == null
                ? Icons.image_not_supported_rounded
                : Icons.image_rounded,
            label: context.t('packCover'),
          ),
          if (warningCount > 0)
            _PackSummaryChip(
              icon: Icons.warning_amber_rounded,
              label: '${context.t('warningCount')} $warningCount',
              onPressed: () => _openPackWarningsDialog(context, page),
            ),
        ],
      ),
    );
  }
}

class _PackSummaryChip extends StatelessWidget {
  const _PackSummaryChip({
    required this.icon,
    required this.label,
    this.onPressed,
  });

  final IconData icon;
  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    if (onPressed == null) {
      return Chip(
        visualDensity: VisualDensity.compact,
        avatar: Icon(icon, size: 18),
        label: Text(label),
      );
    }
    return ActionChip(
      visualDensity: VisualDensity.compact,
      avatar: Icon(icon, size: 18),
      label: Text(label),
      onPressed: onPressed,
    );
  }
}

class _PackWarningItem {
  const _PackWarningItem({
    required this.scope,
    required this.message,
    this.entryIndex,
    this.targetId,
    this.targetTitle,
  });

  final String scope;
  final String message;
  final int? entryIndex;
  final String? targetId;
  final String? targetTitle;
}

List<_PackWarningItem> _packWarningItems(PackEditorState page) {
  final items = <_PackWarningItem>[];
  final seen = <String>{};
  void add(_PackWarningItem item) {
    final key = [
      item.scope,
      item.entryIndex?.toString() ?? '',
      item.targetId ?? '',
      item.message.trim(),
    ].join('\u{1f}');
    if (item.message.trim().isNotEmpty && seen.add(key)) {
      items.add(item);
    }
  }

  for (final warning in page.scan?.warnings ?? const <String>[]) {
    add(_PackWarningItem(scope: 'pack', message: warning));
  }
  for (var i = 0; i < page.entries.length; i++) {
    final entry = page.entries[i];
    for (final warning in entry.warnings) {
      add(
        _PackWarningItem(
          scope: 'song',
          message: warning,
          entryIndex: i,
          targetId: entry.identifier ?? entry.levelId,
          targetTitle: entry.title,
        ),
      );
    }
  }
  return items;
}

void _openPackWarningsDialog(BuildContext context, PackEditorState page) {
  final warnings = _packWarningItems(page);
  safeAction(
    context,
    id: 'pack.warning.open',
    label: '${warnings.length}',
    page: AppPageId.packEditor,
    action: () => _showPackWarningsDialog(context, page, warnings),
  );
}

void _showPackWarningsDialog(
  BuildContext context,
  PackEditorState page,
  List<_PackWarningItem> warnings,
) {
  final state = AppScope.of(context);
  showDialog<void>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: Text(context.t('packWarnings')),
      content: SizedBox(
        width: 560,
        child: warnings.isEmpty
            ? Text(context.t('diagnosticsUnavailable'))
            : ListView.separated(
                shrinkWrap: true,
                itemCount: warnings.length,
                separatorBuilder: (context, index) => const Divider(height: 1),
                itemBuilder: (context, index) {
                  final warning = warnings[index];
                  final isSongWarning = warning.entryIndex != null;
                  return ListTile(
                    dense: true,
                    leading: Icon(
                      isSongWarning
                          ? Icons.music_note_rounded
                          : Icons.inventory_2_rounded,
                    ),
                    title: Text(warning.message),
                    subtitle: Text(
                      [
                            context.t(
                              isSongWarning
                                  ? 'warningScopeSong'
                                  : 'warningScopePack',
                            ),
                            warning.targetTitle,
                            warning.targetId,
                          ]
                          .whereType<String>()
                          .where((text) => text.isNotEmpty)
                          .join(' / '),
                    ),
                    trailing: isSongWarning
                        ? TextButton(
                            onPressed: () {
                              Navigator.of(dialogContext).pop();
                              state.focusPackWarningEntry(warning.entryIndex!);
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                  content: Text(context.t('jumpToEntry')),
                                ),
                              );
                            },
                            child: Text(context.t('jumpToEntry')),
                          )
                        : TextButton(
                            onPressed: () {
                              Navigator.of(dialogContext).pop();
                              state.expandPackDiagnostics();
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                  content: Text(context.t('expandDiagnostics')),
                                ),
                              );
                            },
                            child: Text(context.t('expandDiagnostics')),
                          ),
                  );
                },
              ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(dialogContext).pop(),
          child: Text(context.t('close')),
        ),
      ],
    ),
  );
}

class _PackInputCard extends StatelessWidget {
  const _PackInputCard({
    required this.state,
    required this.page,
    required this.busy,
  });

  final AppState state;
  final PackEditorState page;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    return SurfaceCard(
      title: context.t('input'),
      icon: Icons.inventory_2_rounded,
      variant: SurfaceCardVariant.elevated,
      dense: page.scan != null,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: SegmentedButton<PackEditorMode>(
              selected: {page.mode},
              onSelectionChanged: (next) {
                final mode = next.first;
                safeAction(
                  context,
                  id: 'pack.mode.${mode.name}',
                  label: context.t(mode.i18nKey),
                  page: AppPageId.packEditor,
                  action: () => state.setPackMode(mode),
                );
              },
              segments: [
                for (final mode in PackEditorMode.values)
                  ButtonSegment<PackEditorMode>(
                    value: mode,
                    label: Text(context.t(mode.i18nKey)),
                  ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.sm,
            children: _packInputActions(context, state, page, busy),
          ),
          const SizedBox(height: AppSpacing.sm),
          InfoRow(
            label: context.t('currentInput'),
            value: page.inputPath ?? context.t('notSelected'),
          ),
          if (page.inputPaths.length > 1) ...[
            const SizedBox(height: AppSpacing.sm),
            Wrap(
              spacing: AppSpacing.xs,
              runSpacing: AppSpacing.xs,
              children: [
                Chip(
                  visualDensity: VisualDensity.compact,
                  avatar: const Icon(Icons.inventory_2_rounded, size: 18),
                  label: Text('${page.inputPaths.length} arcpkg'),
                ),
                TextButton.icon(
                  onPressed: busy
                      ? null
                      : () => safeAction(
                          context,
                          id: 'pack.input.clear',
                          label: context.t('clearInputs'),
                          page: AppPageId.packEditor,
                          action: state.clearPackInput,
                        ),
                  icon: const Icon(Icons.clear_all_rounded),
                  label: Text(context.t('clearInputs')),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.xs),
            for (var i = 0; i < page.inputPaths.length; i++)
              ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.inventory_2_rounded),
                title: Text(
                  p.basename(page.inputPaths[i]),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                subtitle: Text(
                  page.inputPaths[i],
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                trailing: IconButton(
                  onPressed: busy
                      ? null
                      : () => safeAction(
                          context,
                          id: 'pack.input.remove',
                          label: page.inputPaths[i],
                          page: AppPageId.packEditor,
                          action: () => state.removePackInput(i),
                        ),
                  icon: const Icon(Icons.close_rounded),
                ),
              ),
          ],
        ],
      ),
    );
  }
}

class _PackSettingsCard extends StatelessWidget {
  const _PackSettingsCard({required this.state, required this.page});

  final AppState state;
  final PackEditorState page;

  @override
  Widget build(BuildContext context) {
    return SurfaceCard(
      title: context.t('packSettings'),
      icon: Icons.settings_applications_rounded,
      variant: SurfaceCardVariant.filled,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _PackMetadataGrid(state: state, page: page),
          const SizedBox(height: AppSpacing.sm),
          InfoRow(
            label: 'identifier',
            value:
                page.scan?.packIdentifier ?? 'etoilebridge.${page.packId}.pack',
          ),
          InfoRow(
            label: 'directory',
            value: page.scan?.packDirectory ?? page.packId,
          ),
          const SizedBox(height: AppSpacing.md),
          Text(
            context.t('preprocess'),
            style: Theme.of(
              context,
            ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: AppSpacing.sm),
          PreprocessOptionsPanel(
            value: page.preprocessOptions,
            onChanged: state.updatePackPreprocess,
            pageId: AppPageId.packEditor,
            actionPrefix: 'pack.preprocess',
          ),
        ],
      ),
    );
  }
}

class _PackMetadataGrid extends StatelessWidget {
  const _PackMetadataGrid({required this.state, required this.page});

  final AppState state;
  final PackEditorState page;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final twoColumns = constraints.maxWidth >= 560;
        final fields = [
          TextFormField(
            key: ValueKey('packName-${identityHashCode(page.scan)}'),
            initialValue: page.packName,
            decoration: const InputDecoration(labelText: 'packName'),
            onChanged: (value) => state.updatePackMetadata(packName: value),
          ),
          TextFormField(
            key: ValueKey('packId-${identityHashCode(page.scan)}'),
            initialValue: page.packId,
            decoration: const InputDecoration(labelText: 'packId'),
            onChanged: (value) => state.updatePackMetadata(packId: value),
          ),
        ];
        if (!twoColumns) {
          return Column(
            children: [
              for (final field in fields) ...[
                field,
                const SizedBox(height: AppSpacing.sm),
              ],
            ],
          );
        }
        return Row(
          children: [
            Expanded(child: fields[0]),
            const SizedBox(width: AppSpacing.sm),
            Expanded(child: fields[1]),
          ],
        );
      },
    );
  }
}

class _PackSaveCard extends StatelessWidget {
  const _PackSaveCard({
    required this.state,
    required this.page,
    required this.busy,
  });

  final AppState state;
  final PackEditorState page;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    return SurfaceCard(
      title: context.t('saveArcpkg'),
      icon: Icons.save_rounded,
      variant: page.saveResult == null
          ? SurfaceCardVariant.outlined
          : SurfaceCardVariant.filled,
      dense: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InfoRow(
            label: context.t('outputFile'),
            value: '${page.packId}.arcpkg',
          ),
          if (page.saveResult != null) ...[
            InfoRow(
              label: context.t('savedPath'),
              value: page.saveResult!.outputPath,
            ),
            InfoRow(
              label: context.t('fileSize'),
              value: _formatBytes(page.saveResult!.sizeBytes),
            ),
          ],
          const SizedBox(height: AppSpacing.md),
          FilledButton.icon(
            onPressed: busy || page.scan == null
                ? null
                : () => safeAction(
                    context,
                    id: 'pack.save.start',
                    label: context.t('saveArcpkg'),
                    page: AppPageId.packEditor,
                    action: state.savePack,
                  ),
            icon: const Icon(Icons.save_rounded),
            label: Text(context.t('saveArcpkg')),
          ),
        ],
      ),
    );
  }
}

class _PackCoverPreviewCard extends StatelessWidget {
  const _PackCoverPreviewCard({required this.page});

  final PackEditorState page;

  @override
  Widget build(BuildContext context) {
    return SurfaceCard(
      title: context.t('packCover'),
      subtitle: page.scan?.packImage?.name ?? context.t('notSelected'),
      icon: Icons.image_rounded,
      variant: SurfaceCardVariant.elevated,
      compact: page.scan == null,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _PackImagePreview(
            label: context.t('packCover'),
            resource: page.scan?.packImage,
            aspectRatio: 16 / 9,
          ),
          if (page.scan?.packImage != null) ...[
            const SizedBox(height: AppSpacing.sm),
            InfoRow(
              label: context.t('fileSize'),
              value: _formatBytes(page.scan!.packImage!.sizeBytes),
            ),
          ],
        ],
      ),
    );
  }
}

class _SelectedPackEntryCard extends StatelessWidget {
  const _SelectedPackEntryCard({required this.page});

  final PackEditorState page;

  @override
  Widget build(BuildContext context) {
    final index = page.expanded.isNotEmpty
        ? page.expanded.first
        : (page.entryCount > 0 ? 0 : -1);
    final entry = index >= 0 && page.entries.isNotEmpty
        ? page.entries[index]
        : null;
    final mock = index >= 0 && page.entries.isEmpty && page.levels.isNotEmpty
        ? page.levels[index]
        : null;
    return SurfaceCard(
      title: context.t('selectedSong'),
      subtitle: entry?.title ?? mock?.title ?? context.t('notSelected'),
      icon: Icons.music_note_rounded,
      variant: SurfaceCardVariant.outlined,
      compact: true,
      child: entry == null && mock == null
          ? EmptyTaskCard(
              icon: Icons.queue_music_rounded,
              title: context.t('levelList'),
              message: context.t('notSelected'),
              compact: true,
              actions: const [],
            )
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (entry?.jacket != null)
                  _PackImagePreview(
                    label: '',
                    resource: entry?.jacket,
                    aspectRatio: 1,
                    fit: BoxFit.cover,
                  ),
                if (entry?.jacket != null)
                  const SizedBox(height: AppSpacing.sm),
                InfoRow(
                  label: context.t('field.title'),
                  value: entry?.title ?? mock?.title ?? '-',
                ),
                InfoRow(
                  label: context.t('field.composer'),
                  value: entry?.artist ?? mock?.composer ?? '-',
                ),
                InfoRow(
                  label: context.t('identifierPreview'),
                  value: entry?.identifier ?? mock?.identifier ?? '-',
                ),
                InfoRow(
                  label: context.t('chartCount'),
                  value: '${entry?.charts.length ?? mock?.chartCount ?? 0}',
                ),
                if ((entry?.warnings.length ?? 0) > 0)
                  Padding(
                    padding: const EdgeInsets.only(top: AppSpacing.xs),
                    child: ActionChip(
                      visualDensity: VisualDensity.compact,
                      avatar: const Icon(Icons.warning_amber_rounded, size: 18),
                      label: Text('${entry!.warnings.length}'),
                      onPressed: () => _openPackWarningsDialog(context, page),
                    ),
                  ),
              ],
            ),
    );
  }
}

class _PackLevelListCard extends StatelessWidget {
  const _PackLevelListCard({required this.state, required this.page});

  final AppState state;
  final PackEditorState page;

  @override
  Widget build(BuildContext context) {
    return SurfaceCard(
      title: context.t('levelList'),
      subtitle: page.entryCount == 0
          ? context.t('notSelected')
          : '${context.t('chartCount')} ${page.entryCount}',
      icon: Icons.queue_music_rounded,
      variant: SurfaceCardVariant.elevated,
      trailing: page.entryCount == 0
          ? null
          : Wrap(
              spacing: 8,
              children: [
                TextButton(
                  onPressed: () => safeAction(
                    context,
                    id: 'pack.expandAll',
                    label: context.t('expandAll'),
                    page: AppPageId.packEditor,
                    action: state.expandAllPackLevels,
                  ),
                  child: Text(context.t('expandAll')),
                ),
                TextButton(
                  onPressed: () => safeAction(
                    context,
                    id: 'pack.collapseAll',
                    label: context.t('collapseAll'),
                    page: AppPageId.packEditor,
                    action: state.collapseAllPackLevels,
                  ),
                  child: Text(context.t('collapseAll')),
                ),
              ],
            ),
      child: AnimatedSize(
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOutCubic,
        child: Column(
          children: [
            if (page.entryCount == 0)
              EmptyTaskCard(
                icon: Icons.queue_music_rounded,
                title: context.t('levelList'),
                message: context.t('notSelected'),
                compact: true,
                actions: [
                  FilledButton.tonalIcon(
                    onPressed: () => safeAction(
                      context,
                      id: 'pack.pickOfficial',
                      label: context.t('chooseFolder'),
                      page: AppPageId.packEditor,
                      action: state.pickOfficialPackFolder,
                    ),
                    icon: const Icon(Icons.folder_rounded),
                    label: Text(context.t('chooseFolder')),
                  ),
                ],
              )
            else
              for (var i = 0; i < page.entryCount; i++) _PackLevelRow(index: i),
          ],
        ),
      ),
    );
  }
}

class _FeatureHeader extends StatelessWidget {
  const _FeatureHeader({required this.title, required this.subtitle});

  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: theme.textTheme.headlineSmall?.copyWith(
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: AppSpacing.xs),
        Text(
          subtitle,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

List<Widget> _packInputActions(
  BuildContext context,
  AppState state,
  PackEditorState page,
  bool busy,
) {
  final actions = switch (page.mode) {
    PackEditorMode.official => [
      FilledButton.icon(
        onPressed: busy
            ? null
            : () => safeAction(
                context,
                id: 'pack.pickOfficialZip',
                label: context.t('chooseZip'),
                page: AppPageId.packEditor,
                action: state.pickOfficialPackZip,
              ),
        icon: const Icon(Icons.archive_rounded),
        label: Text(context.t('chooseZip')),
      ),
      OutlinedButton.icon(
        onPressed: busy
            ? null
            : () => safeAction(
                context,
                id: 'pack.pickOfficialFolder',
                label: context.t('chooseFolder'),
                page: AppPageId.packEditor,
                action: state.pickOfficialPackFolder,
              ),
        icon: const Icon(Icons.folder_rounded),
        label: Text(context.t('chooseFolder')),
      ),
    ],
    PackEditorMode.bundle => [
      FilledButton.icon(
        onPressed: busy
            ? null
            : () => safeAction(
                context,
                id: 'pack.pickArcpkgMultiple',
                label: context.t('chooseArcpkg'),
                page: AppPageId.packEditor,
                action: state.pickPackBundleArcpkg,
              ),
        icon: const Icon(Icons.inventory_2_rounded),
        label: Text(context.t('chooseArcpkg')),
      ),
    ],
    PackEditorMode.existing => [
      FilledButton.icon(
        onPressed: busy
            ? null
            : () => safeAction(
                context,
                id: 'pack.pickExisting',
                label: context.t('chooseArcpkg'),
                page: AppPageId.packEditor,
                action: state.pickExistingPack,
              ),
        icon: const Icon(Icons.edit_document),
        label: Text(context.t('chooseArcpkg')),
      ),
    ],
  };
  return [
    ...actions,
    TextButton.icon(
      onPressed: busy
          ? null
          : () => safeAction(
              context,
              id: 'pack.mockImport',
              label: context.t('mockAction'),
              page: AppPageId.packEditor,
              action: state.mockImportPack,
            ),
      icon: const Icon(Icons.auto_awesome_rounded),
      label: Text(context.t('mockAction')),
    ),
  ];
}

class _PackImagePreview extends StatelessWidget {
  const _PackImagePreview({
    required this.label,
    required this.resource,
    required this.aspectRatio,
    this.fit = BoxFit.contain,
    this.heroTag,
    this.detailTitle,
  });

  final String label;
  final ResourceInfo? resource;
  final double aspectRatio;
  final BoxFit fit;
  final String? heroTag;
  final String? detailTitle;

  @override
  Widget build(BuildContext context) {
    final path = resource?.path;
    final file = path == null ? null : File(path);
    final exists = file != null && file.existsSync();
    final theme = Theme.of(context);
    final preview = Card.filled(
      color: theme.colorScheme.surfaceContainer,
      clipBehavior: Clip.antiAlias,
      child: AspectRatio(
        aspectRatio: aspectRatio,
        child: exists
            ? Image.file(
                file,
                fit: fit,
                errorBuilder: (context, error, stackTrace) =>
                    _PackImageEmpty(label: label),
              )
            : _PackImageEmpty(label: label),
      ),
    );
    if (resource == null || heroTag == null) return preview;
    return Hero(
      tag: heroTag!,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: () => showResourceDetailOverlay(
            context,
            heroTag: heroTag!,
            title: detailTitle ?? label,
            icon: Icons.image_rounded,
            kind: ResourceDetailKind.image,
            resource: resource,
            rows: [
              ResourceDetailRow(label: context.t('field.file'), value: label),
            ],
          ),
          child: preview,
        ),
      ),
    );
  }
}

class _PackImageEmpty extends StatelessWidget {
  const _PackImageEmpty({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme.onSurfaceVariant;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.image_not_supported_rounded, color: color),
          if (label.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(label, style: TextStyle(color: color)),
          ],
        ],
      ),
    );
  }
}

class _PackLevelRow extends StatelessWidget {
  const _PackLevelRow({required this.index});

  final int index;

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final page = state.packEditor;
    final entry = page.entries.isNotEmpty ? page.entries[index] : null;
    final level = entry == null ? page.levels[index] : null;
    final expanded = page.expanded.contains(index);
    final title = entry?.title ?? level?.title ?? '-';
    final composer = entry?.artist ?? level?.composer ?? '-';
    final identifier =
        entry?.identifier ?? entry?.levelId ?? level?.identifier ?? '-';
    final chartCount = entry?.charts.length ?? level?.chartCount ?? 0;
    final enabled = entry?.enabled ?? level?.enabled ?? true;
    final highlighted = page.highlightedWarningEntryIndex == index;
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: Card.outlined(
        color: highlighted
            ? Theme.of(
                context,
              ).colorScheme.tertiaryContainer.withValues(alpha: 0.55)
            : null,
        clipBehavior: Clip.antiAlias,
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.sm),
          child: Column(
            children: [
              Row(
                children: [
                  SizedBox.square(
                    dimension: 76,
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(16),
                      child: entry == null
                          ? const MockPreview(label: '', aspectRatio: 1)
                          : _PackImagePreview(
                              label: '',
                              resource: entry.jacket,
                              aspectRatio: 1,
                              fit: BoxFit.cover,
                            ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        Text(
                          composer,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        Text(
                          identifier,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        Wrap(
                          spacing: AppSpacing.xs,
                          crossAxisAlignment: WrapCrossAlignment.center,
                          children: [
                            Chip(
                              visualDensity: VisualDensity.compact,
                              label: Text('${context.t('charts')} $chartCount'),
                            ),
                            if ((entry?.warnings.length ?? 0) > 0)
                              ActionChip(
                                visualDensity: VisualDensity.compact,
                                avatar: const Icon(
                                  Icons.warning_amber_rounded,
                                  size: 18,
                                ),
                                label: Text('${entry!.warnings.length}'),
                                onPressed: () =>
                                    _openPackWarningsDialog(context, page),
                              ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  Checkbox(
                    value: enabled,
                    onChanged: (value) => safeAction(
                      context,
                      id: 'pack.entry.toggleEnabled',
                      label: 'level[$index].enabled',
                      page: AppPageId.packEditor,
                      action: () =>
                          state.setPackLevelEnabled(index, value ?? true),
                    ),
                  ),
                  IconButton(
                    onPressed: () => safeAction(
                      context,
                      id: expanded
                          ? 'pack.entry.collapse'
                          : 'pack.entry.expand',
                      label: 'level[$index]',
                      page: AppPageId.packEditor,
                      action: () => state.togglePackLevel(index),
                    ),
                    icon: Icon(
                      expanded
                          ? Icons.expand_less_rounded
                          : Icons.expand_more_rounded,
                    ),
                  ),
                ],
              ),
              AnimatedSize(
                duration: const Duration(milliseconds: 180),
                curve: Curves.easeOutCubic,
                child: expanded
                    ? Column(
                        children: [
                          const SizedBox(height: 12),
                          TextFormField(
                            key: ValueKey('pack-title-$index-$title'),
                            initialValue: title,
                            decoration: InputDecoration(
                              labelText: context.t('field.title'),
                            ),
                            onChanged: (value) {
                              if (entry != null) entry.title = value;
                            },
                          ),
                          const SizedBox(height: 8),
                          TextFormField(
                            key: ValueKey('pack-composer-$index-$composer'),
                            initialValue: composer,
                            decoration: InputDecoration(
                              labelText: context.t('field.composer'),
                            ),
                            onChanged: (value) {
                              if (entry != null) entry.artist = value;
                            },
                          ),
                          const SizedBox(height: 8),
                          for (final chart
                              in entry?.charts ?? const <PackChartEntry>[])
                            Padding(
                              padding: const EdgeInsets.only(bottom: 8),
                              child: ListTile(
                                dense: true,
                                leading: CircleAvatar(
                                  child: Text('${chart.ratingClass}'),
                                ),
                                title: Text(
                                  chart.difficulty.isEmpty
                                      ? context.t('field.difficulty')
                                      : chart.difficulty,
                                ),
                                subtitle: Text(
                                  [
                                        chart.chartConstant?.toString(),
                                        chart.charter,
                                        chart.chartPath == null
                                            ? null
                                            : p.basename(chart.chartPath!),
                                      ]
                                      .whereType<String>()
                                      .where((text) => text.isNotEmpty)
                                      .join(' / '),
                                ),
                              ),
                            ),
                        ],
                      )
                    : const SizedBox.shrink(),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

String _formatBytes(int? bytes) {
  if (bytes == null) return '-';
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
  return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
}
