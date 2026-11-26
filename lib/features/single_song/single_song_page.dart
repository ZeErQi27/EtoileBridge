import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;

import '../../app/app_spacing.dart';
import '../../app/app_state.dart';
import '../../app/routes.dart';
import '../../app/safe_layout.dart';
import '../../app/safe_action.dart';
import '../../app/theme.dart';
import '../../core/i18n/app_strings.dart';
import '../../core/models/conversion_options.dart';
import '../../core/models/operation_models.dart';
import '../../core/models/single_song_models.dart';
import '../../shared/widgets/conversion_options_controls.dart';
import '../../shared/widgets/app_motion.dart';
import '../../shared/widgets/audio_preview.dart';
import '../../shared/widgets/info_row.dart';
import '../../shared/widgets/log_panel.dart';
import '../../shared/widgets/pretty_ui.dart';
import '../../shared/widgets/resource_detail_overlay.dart';
import '../../shared/widgets/workspace_choices.dart';
import '../../shared/layout/app_layout_tokens.dart';
import 'single_song_state.dart';

class SingleSongPage extends StatefulWidget {
  const SingleSongPage({super.key});

  @override
  State<SingleSongPage> createState() => _SingleSongPageState();
}

class _SingleSongPageState extends State<SingleSongPage>
    with SingleTickerProviderStateMixin {
  bool _appearanceExpanded = false;
  late final _appearanceReveal = AnimationController(
    vsync: this,
    duration: AppMotion.reveal,
  );
  late final _appearanceHeight = _appearanceReveal.drive(
    CurveTween(curve: AppMotion.travel),
  );
  late final _appearanceFade = _appearanceReveal.drive(
    CurveTween(curve: const Interval(0.18, 1, curve: AppMotion.enter)),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (MediaQuery.disableAnimationsOf(context)) {
      _appearanceReveal.value = _appearanceExpanded ? 1 : 0;
    }
  }

  @override
  void dispose() {
    _appearanceReveal.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final page = state.singleSong;
    final busy = _isBusy(page);
    final input = AppWorkspacePanel(
      'input',
      _InputWorkbenchCard(state: state, page: page, busy: busy),
    );
    final save = AppWorkspacePanel(
      'save',
      _SaveWorkbenchCard(state: state, page: page, busy: busy),
    );
    final chart = AppWorkspacePanel(
      'chart',
      _SongChartInfoWorkbenchCard(
        state: state,
        page: page,
        onChanged: _refresh,
      ),
    );
    final appearanceValue = page.appearanceOptions;
    final resources = AppWorkspacePanel(
      'resources',
      _ResourcePreviewWorkbenchCard(state: state, page: page),
    );
    final diagnostics = page.scan == null
        ? null
        : AppWorkspacePanel(
            'diagnostics',
            _DiagnosticsWorkbenchCard(page: page),
          );
    const alignmentStretch = AppLayoutTokens.maxCardAlignmentStretch;

    void updateAppearance(
      ArcCreateAppearanceOptions next,
      String field,
      String value,
    ) {
      safeAction(
        context,
        id: 'single.appearance.$field',
        label: value,
        page: AppPageId.singleSong,
        action: () => state.updateSingleAppearance(next),
      );
    }

    return AppWorkspaceScaffold(
      title: context.t('page.singleSong'),
      child: AppWorkspaceLayout(
        bands: (columns) {
          final wide = columns == 3;
          final details = _appearanceDetails(
            _AppearanceOptionFields(
              value: appearanceValue,
              onChanged: updateAppearance,
              maxColumns: wide ? 5 : 2,
            ),
            separate: wide,
          );
          final appearance = AppWorkspacePanel(
            'appearance',
            _AppearanceWorkbenchCard(
              side: appearanceValue.side,
              onSideChanged: (side) => updateAppearance(
                appearanceValue.copyWith(side: side),
                'side',
                side,
              ),
              disclosure: PrettyDisclosureHeader(
                title: context.t('appearance.more'),
                icon: Icons.tune_rounded,
                expanded: _appearanceExpanded,
                progress: _appearanceHeight,
                onTap: _toggleAppearance,
              ),
              details: wide ? null : details,
            ),
          );
          return switch (columns) {
            3 => [
              AppWorkspaceBand([
                input,
                appearance,
                save,
              ], maxStretch: alignmentStretch),
              AppWorkspaceBand([
                AppWorkspacePanel('appearance-details', details, span: 3),
              ], gapBefore: 0),
              if (diagnostics == null)
                AppWorkspaceBand([chart.across(2), resources])
              else
                AppWorkspaceBand.stacked([
                  [chart.across(2), diagnostics.across(2)],
                  [resources],
                ], maxStretch: alignmentStretch),
            ],
            2 => [
              AppWorkspaceBand([input, save], maxStretch: alignmentStretch),
              if (diagnostics == null) ...[
                AppWorkspaceBand([chart, resources]),
                AppWorkspaceBand([appearance.across(2)]),
              ] else
                AppWorkspaceBand.stacked([
                  [chart, appearance],
                  [resources, diagnostics],
                ], maxStretch: alignmentStretch),
            ],
            _ => [
              for (final panel in [
                input,
                save,
                chart,
                resources,
                appearance,
                ?diagnostics,
              ])
                AppWorkspaceBand([panel]),
            ],
          };
        },
      ),
    );
  }

  void _toggleAppearance() {
    setState(() => _appearanceExpanded = !_appearanceExpanded);
    if (MediaQuery.disableAnimationsOf(context)) {
      _appearanceReveal.value = _appearanceExpanded ? 1 : 0;
    } else if (_appearanceExpanded) {
      _appearanceReveal.forward();
    } else {
      _appearanceReveal.reverse();
    }
  }

  Widget _appearanceDetails(Widget child, {required bool separate}) {
    final inset = separate ? AppLayoutTokens.cardPadding : AppSpacing.sm;
    final reveal = SizeTransition(
      sizeFactor: _appearanceHeight,
      alignment: Alignment.topCenter,
      child: ExcludeFocus(
        excluding: !_appearanceExpanded,
        child: ExcludeSemantics(
          excluding: !_appearanceExpanded,
          child: IgnorePointer(
            ignoring: !_appearanceExpanded,
            child: Padding(
              padding: EdgeInsets.fromLTRB(
                inset,
                MediaQuery.textScalerOf(context).scale(inset),
                inset,
                inset,
              ),
              child: child,
            ),
          ),
        ),
      ),
    );
    if (separate) {
      // The optional band owns its animated gap. At zero height the other
      // bands have exactly the same positions as if it were absent.
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizeTransition(
            sizeFactor: _appearanceHeight,
            alignment: Alignment.topCenter,
            child: const SizedBox(height: AppLayoutTokens.gridGap),
          ),
          FadeTransition(
            opacity: _appearanceFade,
            child: WorkspaceSurface(
              key: const ValueKey('single-appearance-shelf'),
              child: reveal,
            ),
          ),
        ],
      );
    }
    return FadeTransition(
      opacity: _appearanceFade,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: context.colors.surfaceContainer,
          borderRadius: BorderRadius.circular(AppRadius.workspaceInset),
        ),
        child: reveal,
      ),
    );
  }

  void _refresh() {
    if (mounted) setState(() {});
  }
}

class _InputWorkbenchCard extends StatelessWidget {
  const _InputWorkbenchCard({
    required this.state,
    required this.page,
    required this.busy,
  });

  final AppState state;
  final SingleSongState page;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    final input = page.inputPath;
    final scanning = page.phase == OperationPhase.scanning;
    final scanFailed = page.phase == OperationPhase.failed && page.scan == null;
    final phase = scanning || scanFailed
        ? page.phase
        : page.scan != null
        ? OperationPhase.ready
        : OperationPhase.idle;
    return PrettyCard(
      title: context.t('input'),
      icon: Icons.folder_open_rounded,
      trailing: PrettyPill(
        label: context.t(
          phase == OperationPhase.ready ? 'convertible' : phase.i18nKey,
        ),
        icon: _phaseIcon(phase),
        tone: _phaseTone(phase),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              WorkspaceAction(
                label: context.t('chooseZip'),
                icon: Icons.description_rounded,
                primary: true,
                onPressed: busy
                    ? null
                    : () => safeAction(
                        context,
                        id: 'single.pickZip',
                        label: context.t('chooseZip'),
                        page: AppPageId.singleSong,
                        action: state.pickSingleZip,
                      ),
              ),
              WorkspaceAction(
                label: context.t('chooseFolder'),
                icon: Icons.folder_rounded,
                onPressed: busy
                    ? null
                    : () => safeAction(
                        context,
                        id: 'single.pickFolder',
                        label: context.t('chooseFolder'),
                        page: AppPageId.singleSong,
                        action: state.pickSingleFolder,
                      ),
              ),
              if (input != null)
                WorkspaceAction(
                  label: context.t('scan'),
                  icon: Icons.refresh_rounded,
                  onPressed: busy
                      ? null
                      : () => safeAction(
                          context,
                          id: 'single.scan.start',
                          label: context.t('rescan'),
                          page: AppPageId.singleSong,
                          action: state.rescanSingleSong,
                        ),
                ),
            ],
          ),
          if (input != null) ...[
            const SizedBox(height: AppSpacing.sm),
            _SelectedInputBox(path: input, state: state),
          ],
          if (scanning) ...[
            const SizedBox(height: AppSpacing.xs),
            LinearProgressIndicator(
              minHeight: 3,
              semanticsLabel: context.t('phase.scanning'),
            ),
          ],
          if (scanFailed) ...[
            const SizedBox(height: AppSpacing.xs),
            Text(
              page.error ?? context.t('single.status.failedMessage'),
              style: Theme.of(
                context,
              ).textTheme.bodySmall?.copyWith(color: context.colors.error),
            ),
          ],
        ],
      ),
    );
  }
}

class _SelectedInputBox extends StatelessWidget {
  const _SelectedInputBox({required this.path, required this.state});

  final String path;
  final AppState state;

  @override
  Widget build(BuildContext context) {
    final name = path.startsWith('content://') ? path : p.basename(path);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.sm,
        vertical: AppSpacing.xs,
      ),
      decoration: BoxDecoration(
        color: context.colors.surfaceContainerHigh.withValues(alpha: 0.55),
        borderRadius: BorderRadius.circular(AppRadius.workspaceInset),
        border: Border.all(color: context.colors.outlineVariant),
      ),
      child: Row(
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: context.colors.primaryContainer,
              borderRadius: BorderRadius.circular(AppRadius.workspaceInset),
            ),
            child: Icon(
              Icons.insert_drive_file_rounded,
              color: context.colors.primary,
              size: 20,
            ),
          ),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    color: context.colors.onSurface,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                Tooltip(
                  message: path,
                  child: Text(
                    path,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: context.colors.onSurfaceVariant,
                    ),
                  ),
                ),
              ],
            ),
          ),
          if (Platform.isWindows)
            IconButton(
              tooltip: context.t('openLocation'),
              onPressed: () => safeAction(
                context,
                id: 'single.openLocation',
                label: context.t('openLocation'),
                page: AppPageId.singleSong,
                action: () => state.platform.openLocation.openLocation(path),
              ),
              icon: const Icon(Icons.folder_open_rounded, size: 19),
            ),
        ],
      ),
    );
  }
}

class _SongChartInfoWorkbenchCard extends StatelessWidget {
  const _SongChartInfoWorkbenchCard({
    required this.state,
    required this.page,
    required this.onChanged,
  });

  final AppState state;
  final SingleSongState page;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final edit = page.edit;
    final current = page.selectedChart;
    if (page.scan == null) {
      return PrettyCard(
        title: context.t('songChartInfo'),
        icon: Icons.edit_note_rounded,
        child: WorkspaceEmptyState(
          icon: Icons.library_music_outlined,
          title: context.t('resource.waiting'),
          subtitle: '',
        ),
      );
    }
    if (edit == null || current == null) {
      return PrettyCard(
        title: context.t('songChartInfo'),
        icon: Icons.edit_note_rounded,
        child: _MetricsGrid(
          compact: true,
          items: [
            _Metric('songId', page.songId),
            _Metric(context.t('field.title'), page.title),
            _Metric(context.t('field.composer'), page.composer),
            _Metric('bpmText', page.bpmText),
            _Metric('baseBpm', page.baseBpm),
            _Metric(context.t('difficulty'), page.scan?.difficulty ?? '-'),
          ],
        ),
      );
    }

    return PrettyCard(
      title: context.t('songChartInfo'),
      icon: Icons.edit_note_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (edit.charts.length > 1) ...[
            _ChartSelectionPanel(
              state: state,
              page: page,
              onChanged: onChanged,
            ),
            const SizedBox(height: AppSpacing.xs),
          ],
          _SongChartSummaryStrip(edit: edit, chart: current),
          const SizedBox(height: AppSpacing.sm),
          _FieldGrid(
            children: [
              _PrettyTextField(
                label: context.t('field.title'),
                identity: edit,
                value: edit.title,
                onChanged: (value) {
                  edit.title = value;
                  state.markSingleChartEdited('metadata.title');
                  onChanged();
                },
              ),
              _PrettyTextField(
                label: context.t('field.composer'),
                identity: edit,
                value: edit.artist,
                onChanged: (value) {
                  edit.artist = value;
                  state.markSingleChartEdited('metadata.artist');
                  onChanged();
                },
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          _FieldGrid(
            minWidth: 146,
            maxColumns: 4,
            children: [
              _PrettyTextField(
                label: context.t('single.field.displayBpm'),
                identity: edit,
                value: edit.bpmText,
                onChanged: (value) {
                  edit.bpmText = value;
                  state.markSingleChartEdited('metadata.bpmText');
                  onChanged();
                },
              ),
              _PrettyTextField(
                label: context.t('single.field.baseBpm'),
                identity: edit,
                value: edit.bpmBase,
                onChanged: (value) {
                  edit.bpmBase = value;
                  state.markSingleChartEdited('metadata.baseBpm');
                  onChanged();
                },
              ),
              _PrettyTextField(
                label: context.t('field.difficulty'),
                identity: current,
                value: current.difficulty,
                onChanged: (value) {
                  current.difficulty = value;
                  state.markSingleChartEdited('chart.difficulty');
                  onChanged();
                },
              ),
              _PrettyTextField(
                label: context.t('single.field.chartConstant'),
                identity: current,
                value: current.chartConstant,
                onChanged: (value) {
                  current.chartConstant = value;
                  state.markSingleChartEdited('chart.constant');
                  onChanged();
                },
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          _FieldGrid(
            children: [
              _PrettyTextField(
                label: context.t('field.charter'),
                identity: current,
                value: current.charter,
                onChanged: (value) {
                  current.charter = value;
                  state.markSingleChartEdited('chart.charter');
                  onChanged();
                },
              ),
              _PrettyTextField(
                label: context.t('field.illustrator'),
                identity: current,
                value: current.illustrator,
                onChanged: (value) {
                  current.illustrator = value;
                  state.markSingleChartEdited('chart.illustrator');
                  onChanged();
                },
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          _AdvancedEditorSection(
            title: context.t('advanced'),
            children: [
              _FieldGrid(
                children: [
                  _PrettyTextField(
                    label: context.t('field.publisherId'),
                    identity: edit,
                    value: edit.publisherId,
                    onChanged: (value) {
                      edit.publisherId = value;
                      state.markSingleChartEdited('metadata.publisherId');
                      onChanged();
                    },
                  ),
                  _PrettyTextField(
                    label: context.t('field.levelId'),
                    identity: edit,
                    value: edit.levelId,
                    onChanged: (value) {
                      edit.levelId = value;
                      state.markSingleChartEdited('metadata.levelId');
                      onChanged();
                    },
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.xs),
              InfoRow(
                label: context.t('source'),
                value: sourceKindLabel(
                  page.scan?.sourceKind ?? SingleSourceKind.unknown,
                ),
              ),
              InfoRow(
                label: context.t('chartPath'),
                value: current.chartPath ?? current.affPath ?? '-',
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _SongChartSummaryStrip extends StatelessWidget {
  const _SongChartSummaryStrip({required this.edit, required this.chart});

  final SingleSongEditState edit;
  final ChartEditState chart;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        PrettyPill(
          label: _orDash(edit.levelId),
          icon: Icons.tag_rounded,
          tone: PrettyPillTone.primary,
        ),
        PrettyPill(
          label: _chartChipLabel(chart),
          icon: Icons.checklist_rounded,
          tone: chart.adopted ? PrettyPillTone.success : PrettyPillTone.neutral,
        ),
        PrettyPill(
          label:
              chart.affName ??
              p.basename(chart.affPath ?? chart.chartPath ?? '-'),
          icon: Icons.description_rounded,
          tone: PrettyPillTone.neutral,
        ),
      ],
    );
  }
}

class _AppearanceWorkbenchCard extends StatelessWidget {
  const _AppearanceWorkbenchCard({
    required this.side,
    required this.onSideChanged,
    required this.disclosure,
    required this.details,
  });

  final String side;
  final ValueChanged<String> onSideChanged;
  final Widget disclosure;
  final Widget? details;

  @override
  Widget build(BuildContext context) {
    return PrettyCard(
      title: context.t('appearance'),
      icon: Icons.auto_awesome_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _SideSegmentedControl(
            label: context.t('appearance.side'),
            value: side,
            onChanged: onSideChanged,
          ),
          const SizedBox(height: 8),
          disclosure,
          ?details,
        ],
      ),
    );
  }
}

class _AppearanceOptionFields extends StatelessWidget {
  const _AppearanceOptionFields({
    required this.value,
    required this.onChanged,
    required this.maxColumns,
  });

  final ArcCreateAppearanceOptions value;
  final void Function(
    ArcCreateAppearanceOptions next,
    String field,
    String value,
  )
  onChanged;
  final int maxColumns;

  @override
  Widget build(BuildContext context) {
    return _FieldGrid(
      minWidth: 180,
      maxColumns: maxColumns,
      children: [
        _AppearanceDropdown(
          label: context.t('appearance.note'),
          value: value.note,
          choices: noteAppearanceChoices,
          onChanged: (note) =>
              onChanged(value.copyWith(note: note), 'note', note),
        ),
        _AppearanceDropdown(
          label: context.t('appearance.particle'),
          value: value.particle,
          choices: particleAppearanceChoices,
          onChanged: (particle) => onChanged(
            value.copyWith(particle: particle),
            'particle',
            particle,
          ),
        ),
        _AppearanceDropdown(
          label: context.t('appearance.accent'),
          value: value.accent,
          choices: accentAppearanceChoices,
          onChanged: (accent) =>
              onChanged(value.copyWith(accent: accent), 'accent', accent),
        ),
        _AppearanceDropdown(
          label: context.t('appearance.track'),
          value: value.track,
          choices: trackAppearanceChoices,
          onChanged: (track) =>
              onChanged(value.copyWith(track: track), 'track', track),
        ),
        _AppearanceDropdown(
          label: context.t('appearance.singleLine'),
          value: value.singleLine,
          choices: singleLineAppearanceChoices,
          onChanged: (singleLine) => onChanged(
            value.copyWith(singleLine: singleLine),
            'singleLine',
            singleLine,
          ),
        ),
      ],
    );
  }
}

class _SideSegmentedControl extends StatelessWidget {
  const _SideSegmentedControl({
    required this.label,
    required this.value,
    required this.onChanged,
  });

  final String label;
  final String value;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    final selected =
        sideAppearanceChoices.any((choice) => choice.value == value)
        ? value
        : sideAppearanceChoices.first.value;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(label, style: Theme.of(context).textTheme.labelLarge),
        const SizedBox(height: 7),
        WorkspaceChoices<String>(
          choices: [
            for (final choice in sideAppearanceChoices)
              WorkspaceChoice(
                choice.value,
                context.t('appearance.side.${choice.value.toLowerCase()}'),
                tooltip: context.t(choice.labelKey),
              ),
          ],
          value: selected,
          onChanged: (next) {
            if (next != selected) onChanged(next);
          },
        ),
      ],
    );
  }
}

class _AppearanceDropdown extends StatelessWidget {
  const _AppearanceDropdown({
    required this.label,
    required this.value,
    required this.choices,
    required this.onChanged,
  });

  final String label;
  final String value;
  final List<ConversionOptionChoice> choices;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<String>(
      initialValue: choices.any((choice) => choice.value == value)
          ? value
          : choices.first.value,
      isExpanded: true,
      decoration: InputDecoration(labelText: label),
      borderRadius: BorderRadius.circular(AppRadius.workspaceControl),
      items: [
        for (final choice in choices)
          DropdownMenuItem<String>(
            value: choice.value,
            child: Text(
              context.t(choice.labelKey),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
      ],
      onChanged: (next) {
        if (next != null && next != value) onChanged(next);
      },
    );
  }
}

class _ResourcePreviewWorkbenchCard extends StatelessWidget {
  const _ResourcePreviewWorkbenchCard({
    required this.state,
    required this.page,
  });

  final AppState state;
  final SingleSongState page;

  @override
  Widget build(BuildContext context) {
    final scan = page.scan;
    final effective = page.effectiveResources;
    if (scan == null) {
      return PrettyCard(
        title: context.t('resources'),
        icon: Icons.image_outlined,
        child: WorkspaceEmptyState(
          icon: Icons.perm_media_outlined,
          title: context.t('resource.waiting'),
          subtitle: '',
        ),
      );
    }
    return PrettyCard(
      title: context.t('resources'),
      icon: Icons.image_rounded,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = SafeLayout.widthFromConstraints(constraints, context);
          final columns =
              width >= 314 && MediaQuery.textScalerOf(context).scale(14) <= 17
              ? 2
              : 1;
          const gap = AppSpacing.xs;
          final itemWidth = (width - gap * (columns - 1)) / columns;
          final previewHeight = (itemWidth * 0.52).clamp(90.0, 144.0);
          final resources = [
            _ResourceSpec(
              id: 'audio',
              label: context.t('audio'),
              icon: Icons.music_note_rounded,
              resource: effective.audio,
              kind: _ResourceKind.audio,
              scan: scan,
              sourceKey: effective.audioSourceKey,
              onAudioConvert: state.convertSingleAudioResource,
            ),
            _ResourceSpec(
              id: 'jacket',
              label: context.t('jacket'),
              icon: Icons.image_rounded,
              resource: effective.jacket,
              kind: _ResourceKind.image,
              scan: scan,
              sourceKey: effective.jacketSourceKey,
            ),
            _ResourceSpec(
              id: 'background',
              label: context.t('background'),
              icon: Icons.landscape_rounded,
              resource: effective.background,
              kind: _ResourceKind.image,
              scan: scan,
              sourceKey: effective.backgroundSourceKey,
              missingReference: effective.missingBackgroundReference,
              actionLabel: effective.missingBackgroundReference == null
                  ? null
                  : context.t('resource.selectExternalBackground'),
              onAction: effective.missingBackgroundReference == null
                  ? null
                  : state.pickSingleExternalBackgroundForSelectedChart,
            ),
            _ResourceSpec(
              id: 'songlist',
              label: context.t('songlist'),
              icon: Icons.list_alt_rounded,
              resource: effective.songlist ?? _songlistResource(scan),
              kind: _ResourceKind.songlist,
              scan: scan,
              sourceKey: effective.songlistSourceKey,
            ),
          ];
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _ResourceTile(
                spec: resources.first,
                previewHeight: previewHeight,
              ),
              const SizedBox(height: gap),
              Wrap(
                spacing: gap,
                runSpacing: gap,
                children: [
                  for (final resource in resources.where(
                    (item) => item.kind == _ResourceKind.image,
                  ))
                    SizedBox(
                      width: itemWidth,
                      child: _ResourceTile(
                        spec: resource,
                        previewHeight: previewHeight,
                      ),
                    ),
                ],
              ),
              const SizedBox(height: gap),
              _SonglistResourceRow(spec: resources.last),
            ],
          );
        },
      ),
    );
  }
}

enum _ResourceKind { audio, image, songlist }

class _ResourceSpec {
  const _ResourceSpec({
    required this.id,
    required this.label,
    required this.icon,
    required this.resource,
    required this.kind,
    required this.scan,
    this.sourceKey = 'resource.default',
    this.missingReference,
    this.actionLabel,
    this.onAction,
    this.onAudioConvert,
  });

  final String id;
  final String label;
  final IconData icon;
  final ResourceInfo? resource;
  final _ResourceKind kind;
  final SingleSongScanResult? scan;
  final String sourceKey;
  final String? missingReference;
  final String? actionLabel;
  final VoidCallback? onAction;
  final AudioConversionCallback? onAudioConvert;

  String get heroTag => 'single-song-resource-$id';
}

class _ResourceTile extends StatelessWidget {
  const _ResourceTile({required this.spec, required this.previewHeight});

  final _ResourceSpec spec;
  final double previewHeight;

  @override
  Widget build(BuildContext context) {
    final resource = spec.resource;
    final file = resource?.path == null ? null : File(resource!.path!);
    final exists = file != null && file.existsSync();
    final hasResource = resource != null;
    final audio = spec.kind == _ResourceKind.audio;
    final hasMissingReference = spec.missingReference?.isNotEmpty == true;
    return Hero(
      tag: spec.heroTag,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(AppRadius.workspaceInset),
          onTap: hasResource
              ? () => _showResourceDetails(context, spec, resource)
              : null,
          child: Ink(
            decoration: BoxDecoration(
              color: context.colors.surfaceContainer,
              borderRadius: BorderRadius.circular(AppRadius.workspaceInset),
              border: Border.all(color: context.colors.outlineVariant),
            ),
            padding: const EdgeInsets.all(10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(spec.icon, color: context.colors.onSurface, size: 20),
                    const SizedBox(width: 8),
                    Expanded(
                      flex: 3,
                      child: Text(
                        spec.label,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleSmall?.copyWith(
                          color: context.colors.onSurface,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                    if (audio && hasResource) ...[
                      Flexible(
                        flex: 2,
                        child: Align(
                          alignment: AlignmentDirectional.centerEnd,
                          child: Text(
                            context.t(spec.sourceKey),
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.bodySmall
                                ?.copyWith(
                                  color: context.colors.onSurfaceVariant,
                                ),
                          ),
                        ),
                      ),
                      IconButton(
                        tooltip: context.t('resource.details'),
                        onPressed: () =>
                            _showResourceDetails(context, spec, resource),
                        icon: const Icon(Icons.open_in_full_rounded, size: 18),
                      ),
                    ] else
                      Flexible(
                        flex: 2,
                        child: Align(
                          alignment: AlignmentDirectional.centerEnd,
                          child: PrettyPill(
                            label: hasResource
                                ? _resourceStatusLabel(context, resource)
                                : hasMissingReference
                                ? context.t('resource.missingBgReference')
                                : context.t('resource.missing'),
                            tone: hasResource
                                ? (resource.source == 'fallback'
                                      ? PrettyPillTone.warning
                                      : PrettyPillTone.primary)
                                : hasMissingReference
                                ? PrettyPillTone.warning
                                : PrettyPillTone.neutral,
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 8),
                ClipRRect(
                  borderRadius: BorderRadius.circular(
                    AppRadius.workspaceControl,
                  ),
                  child: ColoredBox(
                    color: context.colors.surfaceContainerHigh,
                    child: ConstrainedBox(
                      constraints: BoxConstraints(
                        minWidth: double.infinity,
                        minHeight: audio
                            ? (hasResource ? 0 : 64)
                            : previewHeight,
                      ),
                      child: _ResourcePreviewBody(
                        spec: spec,
                        resource: resource,
                        file: file,
                        exists: exists,
                        previewHeight: previewHeight,
                      ),
                    ),
                  ),
                ),
                if (!audio && (hasResource || hasMissingReference)) ...[
                  const SizedBox(height: 6),
                  Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              resource?.name ??
                                  resource?.path ??
                                  spec.missingReference ??
                                  '-',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: Theme.of(context).textTheme.labelLarge,
                            ),
                            Text(
                              context.t(spec.sourceKey),
                              style: Theme.of(context).textTheme.bodySmall
                                  ?.copyWith(
                                    color: context.colors.onSurfaceVariant,
                                  ),
                            ),
                          ],
                        ),
                      ),
                      if (hasResource)
                        IconButton(
                          tooltip: context.t('resource.details'),
                          onPressed: () =>
                              _showResourceDetails(context, spec, resource),
                          icon: const Icon(
                            Icons.open_in_full_rounded,
                            size: 18,
                          ),
                        ),
                    ],
                  ),
                  if (spec.onAction != null)
                    TextButton.icon(
                      onPressed: spec.onAction,
                      icon: const Icon(
                        Icons.add_photo_alternate_outlined,
                        size: 18,
                      ),
                      label: Text(spec.actionLabel!),
                    ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ResourcePreviewBody extends StatelessWidget {
  const _ResourcePreviewBody({
    required this.spec,
    required this.resource,
    required this.file,
    required this.exists,
    required this.previewHeight,
  });

  final _ResourceSpec spec;
  final ResourceInfo? resource;
  final File? file;
  final bool exists;
  final double previewHeight;

  @override
  Widget build(BuildContext context) {
    final current = resource;
    if (current == null && spec.missingReference != null) {
      return _MissingBackgroundPreview(reference: spec.missingReference!);
    }
    if (current == null) return _WaitingResource(icon: spec.icon);
    return switch (spec.kind) {
      _ResourceKind.image =>
        exists
            ? Image.file(
                file!,
                height: previewHeight,
                fit: BoxFit.contain,
                errorBuilder: (context, error, stackTrace) => _FileMiniPreview(
                  icon: spec.icon,
                  title: current.name ?? context.t('previewFailed'),
                  subtitle: context.t('previewFailed'),
                ),
              )
            : _FileMiniPreview(
                icon: spec.icon,
                title: current.name ?? current.path ?? spec.label,
                subtitle: context.t('previewFailed'),
              ),
      _ResourceKind.audio => _AudioMiniPreview(
        resource: current,
        onConvert: spec.onAudioConvert,
      ),
      _ResourceKind.songlist => _FileMiniPreview(
        icon: spec.icon,
        title: spec.label,
        subtitle: context.t(spec.sourceKey),
      ),
    };
  }
}

class _MissingBackgroundPreview extends StatelessWidget {
  const _MissingBackgroundPreview({required this.reference});

  final String reference;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: context.semanticColors.warningContainer,
        borderRadius: BorderRadius.circular(AppRadius.workspaceInset),
        border: Border.all(
          color: context.semanticColors.warning.withValues(alpha: 0.42),
        ),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.image_not_supported_rounded,
            color: context.semanticColors.onWarningContainer,
            size: 28,
          ),
          const SizedBox(height: 8),
          Text(
            reference,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: context.colors.onSurface,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            context.t('resource.externalBgHint'),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: context.colors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

class _WaitingResource extends StatelessWidget {
  const _WaitingResource({required this.icon});

  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: context.colors.surfaceContainerHigh.withValues(alpha: 0.34),
        borderRadius: BorderRadius.circular(AppRadius.workspaceInset),
        border: Border.all(
          color: context.colors.outlineVariant,
          style: BorderStyle.solid,
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: context.colors.onSurfaceVariant, size: 24),
          const SizedBox(height: 5),
          Text(
            context.t('resource.waiting'),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: context.colors.onSurfaceVariant,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

class _FileMiniPreview extends StatelessWidget {
  const _FileMiniPreview({
    required this.icon,
    required this.title,
    required this.subtitle,
  });

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: context.colors.surfaceContainerHigh.withValues(alpha: 0.44),
        borderRadius: BorderRadius.circular(AppRadius.workspaceInset),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, color: context.colors.primary, size: 28),
          const SizedBox(height: 8),
          Text(
            title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: context.colors.onSurface,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 3),
          Text(
            subtitle,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: context.colors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

class _AudioMiniPreview extends StatelessWidget {
  const _AudioMiniPreview({required this.resource, this.onConvert});

  final ResourceInfo resource;
  final AudioConversionCallback? onConvert;

  @override
  Widget build(BuildContext context) {
    return AudioPreviewPanel(
      resource: resource,
      compact: true,
      onConvertToCompatibleOgg: onConvert,
    );
  }
}

class _SonglistResourceRow extends StatelessWidget {
  const _SonglistResourceRow({required this.spec});

  final _ResourceSpec spec;

  @override
  Widget build(BuildContext context) {
    final resource = spec.resource;
    return Hero(
      tag: spec.heroTag,
      child: Material(
        color: context.colors.surfaceContainer,
        shape: RoundedSuperellipseBorder(
          borderRadius: BorderRadius.circular(AppRadius.workspaceInset),
        ),
        clipBehavior: Clip.antiAlias,
        child: ListTile(
          dense: true,
          leading: Icon(spec.icon, size: 20),
          title: Text(
            spec.label,
            style: Theme.of(context).textTheme.titleSmall,
          ),
          subtitle: Text(
            resource == null
                ? context.t('resource.missing')
                : context.t(spec.sourceKey),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          trailing: resource == null
              ? null
              : Tooltip(
                  message: context.t('resource.details'),
                  child: const Icon(Icons.open_in_full_rounded, size: 18),
                ),
          onTap: resource == null
              ? null
              : () => _showResourceDetails(context, spec, resource),
        ),
      ),
    );
  }
}

ResourceInfo? _songlistResource(SingleSongScanResult? scan) {
  if (scan == null) return null;
  if (scan.songlist != null) return scan.songlist;
  final hasFallback = [
    ...scan.warnings,
    ...scan.logs,
  ].any((entry) => entry.toLowerCase().contains('fallback'));
  if (!hasFallback) return null;
  return ResourceInfo(
    name: 'metadata fallback',
    source: 'fallback',
    raw: {'fallback': true},
  );
}

String _resourceStatusLabel(BuildContext context, ResourceInfo? resource) {
  if (resource?.source == 'fallback') {
    return '${context.t('resource.identified')} / ${context.t('resource.fallback')}';
  }
  return context.t('resource.identified');
}

class _AffMappingSection extends StatelessWidget {
  const _AffMappingSection({required this.scan});

  final SingleSongScanResult? scan;

  @override
  Widget build(BuildContext context) {
    final files = scan?.affFiles ?? const <AffInfo>[];
    final adopted = files.where((aff) => aff.adopted).length;
    final ignored = files.length - adopted;
    return PrettyExpandableSection(
      title: context.t('affMapping'),
      icon: Icons.link_rounded,
      subtitle:
          '${context.t('adoptedAff')} $adopted · ${context.t('ignoredAff')} $ignored',
      child: files.isEmpty
          ? Text(context.t('diagnosticsUnavailable'))
          : Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                for (final aff in files.take(6)) _AffLine(aff: aff, scan: scan),
              ],
            ),
    );
  }
}

class _AffLine extends StatelessWidget {
  const _AffLine({required this.aff, required this.scan});

  final AffInfo aff;
  final SingleSongScanResult? scan;

  @override
  Widget build(BuildContext context) {
    final chart = _findChartForAff(scan, aff);
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Row(
        children: [
          PrettyPill(
            label: 'RC ${aff.ratingClass}',
            tone: aff.adopted ? PrettyPillTone.primary : PrettyPillTone.neutral,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              [
                    aff.name,
                    chart?.difficulty,
                    _formatBytes(aff.sizeBytes ?? _fileLength(aff.path)),
                  ]
                  .whereType<String>()
                  .where((item) => item.isNotEmpty)
                  .join(' / '),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}

class _SaveWorkbenchCard extends StatelessWidget {
  const _SaveWorkbenchCard({
    required this.state,
    required this.page,
    required this.busy,
  });

  final AppState state;
  final SingleSongState page;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    final hasScan = page.scan != null && page.edit != null;
    final canSave = hasScan && _hasAdoptedChart(page);
    final saving = page.phase == OperationPhase.saving;
    final failed = page.phase == OperationPhase.failed && hasScan;
    final output = page.saveResult?.outputPath;
    final outputPath = output == null || output.isEmpty ? null : output;
    final filename = outputPath == null
        ? _suggestedOutput(page.edit, page.scan)
        : p.basename(outputPath);
    return PrettyCard(
      title: context.t('saveAndExport'),
      icon: Icons.download_rounded,
      trailing: failed || outputPath != null
          ? PrettyPill(
              label: context.t(failed ? 'saveFailed' : 'saveComplete'),
              icon: failed ? Icons.error_outline_rounded : Icons.check_rounded,
              tone: failed ? PrettyPillTone.danger : PrettyPillTone.success,
            )
          : null,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (outputPath != null)
            _SingleExportFile(
              name: filename,
              path: outputPath,
              size: page.saveResult?.sizeBytes ?? _fileLength(outputPath),
              onOpen: Platform.isWindows
                  ? () => _openOutput(context, outputPath)
                  : null,
            )
          else if (canSave)
            Row(
              children: [
                Icon(
                  Icons.inventory_2_outlined,
                  size: 18,
                  color: context.colors.onSurfaceVariant,
                ),
                const SizedBox(width: AppSpacing.xs),
                Expanded(
                  child: Text(
                    filename,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleSmall,
                  ),
                ),
              ],
            )
          else
            Text(
              context.t(hasScan ? 'noChartSelected' : 'saveDisabledHint'),
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: context.colors.onSurfaceVariant,
              ),
            ),
          if (failed) ...[
            const SizedBox(height: AppSpacing.xs),
            Text(
              page.error ?? context.t('saveFailed'),
              style: Theme.of(
                context,
              ).textTheme.bodySmall?.copyWith(color: context.colors.error),
            ),
          ],
          const SizedBox(height: AppSpacing.sm),
          WorkspaceExportActions(
            preprocess: PreprocessOptionsPanel(
              value: page.preprocessOptions,
              onChanged: state.updateSinglePreprocess,
              pageId: AppPageId.singleSong,
              actionPrefix: 'single.preprocess',
              enabled: !busy,
            ),
            save: FilledButton.icon(
              onPressed: busy || !canSave
                  ? null
                  : () => safeAction(
                      context,
                      id: 'single.save.start',
                      label: context.t('saveArcpkg'),
                      page: AppPageId.singleSong,
                      action: state.saveSingleSong,
                    ),
              icon: saving
                  ? const SizedBox.square(
                      dimension: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Icon(
                      outputPath != null
                          ? Icons.sync_rounded
                          : Icons.save_alt_rounded,
                    ),
              label: Text(
                context.t(
                  saving
                      ? 'phase.saving'
                      : outputPath == null
                      ? 'saveArcpkg'
                      : 'exportAgain',
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _openOutput(BuildContext context, String outputPath) {
    safeAction(
      context,
      id: 'single.save.openFolder',
      label: outputPath,
      page: AppPageId.singleSong,
      action: () async {
        final ok = await state.platform.openLocation.openLocation(outputPath);
        if (!context.mounted || ok) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.t('openLocationFailed')),
            behavior: SnackBarBehavior.floating,
          ),
        );
      },
    );
  }
}

class _SingleExportFile extends StatelessWidget {
  const _SingleExportFile({
    required this.name,
    required this.path,
    required this.size,
    this.onOpen,
  });

  final String name;
  final String path;
  final int? size;
  final VoidCallback? onOpen;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Icon(
          Icons.inventory_2_outlined,
          size: 20,
          color: context.colors.primary,
        ),
        const SizedBox(width: AppSpacing.xs),
        Expanded(
          child: Tooltip(
            message: path,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Flexible(
                      child: Text(
                        name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.titleSmall,
                      ),
                    ),
                    if (size != null) ...[
                      const SizedBox(width: AppSpacing.xs),
                      Text(
                        _formatBytes(size),
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: context.colors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ],
                ),
                Text(
                  path,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: context.colors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ),
        IconButton(
          tooltip: context.t('copyPath'),
          icon: const Icon(Icons.content_copy_rounded, size: 18),
          onPressed: () => safeAction(
            context,
            id: 'single.save.copyPath',
            label: path,
            page: AppPageId.singleSong,
            action: () async {
              await Clipboard.setData(ClipboardData(text: path));
              if (!context.mounted) return;
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(context.t('pathCopied')),
                  behavior: SnackBarBehavior.floating,
                  duration: const Duration(milliseconds: 1400),
                ),
              );
            },
          ),
        ),
        if (onOpen != null)
          IconButton(
            tooltip: context.t('openFolder'),
            icon: const Icon(Icons.folder_open_rounded, size: 19),
            onPressed: onOpen,
          ),
      ],
    );
  }
}

class _AdvancedEditorSection extends StatelessWidget {
  const _AdvancedEditorSection({required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return PrettyExpandableSection(
      title: title,
      icon: Icons.tune_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: children,
      ),
    );
  }
}

class _ChartSelectionPanel extends StatelessWidget {
  const _ChartSelectionPanel({
    required this.state,
    required this.page,
    required this.onChanged,
  });

  final AppState state;
  final SingleSongState page;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final charts = page.edit?.charts ?? const <ChartEditState>[];
    if (charts.isEmpty) {
      return PrettyPill(label: context.t('notSelected'));
    }

    final selectedCount = charts.where((chart) => chart.adopted).length;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: context.colors.surfaceContainerHigh.withValues(alpha: 0.38),
        borderRadius: BorderRadius.circular(AppRadius.workspaceInset),
        border: Border.all(color: context.colors.outlineVariant),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 4,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 280),
                child: Text(
                  '${context.t('selectCharts')} ($selectedCount / ${charts.length})',
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    color: context.colors.onSurface,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
              TextButton(
                onPressed: selectedCount == charts.length
                    ? null
                    : () => safeAction(
                        context,
                        id: 'single.chart.selectAll',
                        label: context.t('selectAll'),
                        page: AppPageId.singleSong,
                        action: () {
                          for (final chart in charts) {
                            chart.adopted = true;
                          }
                          state.markSingleChartEdited('chart.adopted.all');
                          onChanged();
                        },
                      ),
                child: Text(context.t('selectAll')),
              ),
              TextButton(
                onPressed: selectedCount == 0
                    ? null
                    : () => safeAction(
                        context,
                        id: 'single.chart.clearAll',
                        label: context.t('clearSelection'),
                        page: AppPageId.singleSong,
                        action: () {
                          for (final chart in charts) {
                            chart.adopted = false;
                          }
                          state.markSingleChartEdited('chart.adopted.none');
                          onChanged();
                        },
                      ),
                child: Text(context.t('clearSelection')),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            context.t('chartSelectionHint'),
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: context.colors.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 12),
          for (var index = 0; index < charts.length; index++) ...[
            if (index > 0) const SizedBox(height: 8),
            _ChartSelectionRow(
              state: state,
              page: page,
              chart: charts[index],
              index: index,
              onChanged: onChanged,
            ),
          ],
        ],
      ),
    );
  }
}

class _ChartSelectionRow extends StatelessWidget {
  const _ChartSelectionRow({
    required this.state,
    required this.page,
    required this.chart,
    required this.index,
    required this.onChanged,
  });

  final AppState state;
  final SingleSongState page;
  final ChartEditState chart;
  final int index;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final selected = page.selectedChartIndex == index;
    return Material(
      color: selected
          ? context.colors.primaryContainer.withValues(alpha: 0.58)
          : context.colors.surfaceContainerLow,
      borderRadius: BorderRadius.circular(AppRadius.workspaceControl),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadius.workspaceControl),
        onTap: () => safeAction(
          context,
          id: 'single.chart.select',
          label: 'chart[$index]',
          page: AppPageId.singleSong,
          action: () => state.selectSingleChart(index),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final compact = constraints.maxWidth < 520;
              final checkbox = Checkbox(
                value: chart.adopted,
                visualDensity: VisualDensity.compact,
                onChanged: (value) => safeAction(
                  context,
                  id: 'single.chart.adopted',
                  label: 'chart[$index]=${value == true}',
                  page: AppPageId.singleSong,
                  action: () {
                    chart.adopted = value == true;
                    state.markSingleChartEdited('chart.adopted');
                    onChanged();
                  },
                ),
              );
              final difficulty = PrettyPill(
                label: _difficultyLabel(chart),
                tone: chart.adopted
                    ? PrettyPillTone.primary
                    : PrettyPillTone.neutral,
              );
              final affName = Text(
                chart.affName ??
                    p.basename(chart.affPath ?? chart.chartPath ?? '-'),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: context.colors.onSurface,
                  fontWeight: FontWeight.w800,
                ),
              );
              final notes = Text(
                '${context.t('notesCount')}: ${_notesCountForChart(page.scan, chart)}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: context.colors.onSurfaceVariant,
                ),
              );
              final status = PrettyPill(
                label: chart.adopted
                    ? context.t('convertible')
                    : context.t('optional'),
                tone: chart.adopted
                    ? PrettyPillTone.success
                    : PrettyPillTone.neutral,
              );
              if (compact) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        checkbox,
                        const SizedBox(width: 6),
                        Expanded(child: difficulty),
                        const SizedBox(width: 8),
                        status,
                      ],
                    ),
                    const SizedBox(height: 4),
                    affName,
                    const SizedBox(height: 2),
                    notes,
                  ],
                );
              }
              return Row(
                children: [
                  checkbox,
                  const SizedBox(width: 6),
                  SizedBox(width: 100, child: difficulty),
                  const SizedBox(width: 10),
                  Expanded(flex: 3, child: affName),
                  const SizedBox(width: 10),
                  Expanded(flex: 2, child: notes),
                  const SizedBox(width: 8),
                  status,
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

class _DiagnosticsWorkbenchCard extends StatelessWidget {
  const _DiagnosticsWorkbenchCard({required this.page});

  final SingleSongState page;

  @override
  Widget build(BuildContext context) {
    return PrettyCard(
      title: context.t('scanDiagnostics'),
      icon: Icons.fact_check_rounded,
      child: Column(
        children: [
          _AffMappingSection(scan: page.scan),
          PrettyExpandableSection(
            title: context.t('missingFields'),
            icon: Icons.rule_rounded,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SelectableText(
                  page.scanDiagnostics.isEmpty
                      ? context.t('diagnosticsUnavailable')
                      : page.scanDiagnostics.join('\n'),
                ),
                if (page.scanRawJsonPath != null)
                  InfoRow(
                    label: context.t('rawScanJson'),
                    value: page.scanRawJsonPath!,
                  ),
                if (page.scanDiagnosticsPath != null)
                  InfoRow(
                    label: context.t('scanDiagnosticsPath'),
                    value: page.scanDiagnosticsPath!,
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

class _MetricsGrid extends StatelessWidget {
  const _MetricsGrid({required this.items, this.compact = false});

  final List<_Metric> items;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = SafeLayout.widthFromConstraints(constraints, context);
        final columns = width >= 540 ? 2 : 1;
        final gap = compact ? 8.0 : 12.0;
        final itemWidth = ((width - gap * (columns - 1)) / columns)
            .clamp(math.min(140.0, width), 500.0)
            .toDouble();
        return Wrap(
          spacing: gap,
          runSpacing: gap,
          children: [
            for (final item in items)
              SizedBox(
                width: itemWidth,
                child: _MetricTile(metric: item, compact: compact),
              ),
          ],
        );
      },
    );
  }
}

class _MetricTile extends StatelessWidget {
  const _MetricTile({required this.metric, required this.compact});

  final _Metric metric;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.all(compact ? 10 : 13),
      decoration: BoxDecoration(
        color: context.colors.surfaceContainer,
        borderRadius: BorderRadius.circular(AppRadius.workspaceInset),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            metric.label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: context.colors.onSurfaceVariant,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 5),
          SelectableText(
            _orDash(metric.value),
            maxLines: 2,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: context.colors.onSurface,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class _Metric {
  const _Metric(this.label, this.value);

  final String label;
  final String? value;
}

class _FieldGrid extends StatelessWidget {
  const _FieldGrid({
    required this.children,
    this.minWidth = 184,
    this.maxColumns = 2,
  });

  final List<Widget> children;
  final double minWidth;
  final int maxColumns;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = SafeLayout.widthFromConstraints(constraints, context);
        const gap = AppSpacing.xs;
        final nominalWidth =
            minWidth *
            math.max(1, MediaQuery.textScalerOf(context).scale(14) / 14);
        final fitting = ((width + gap) / (nominalWidth + gap)).floor();
        final columns = fitting >= maxColumns
            ? maxColumns
            : fitting >= 2
            ? 2
            : 1;
        final itemWidth = (width - gap * (columns - 1)) / columns;
        return Wrap(
          spacing: gap,
          runSpacing: gap,
          children: [
            for (final child in children)
              SizedBox(width: itemWidth, child: child),
          ],
        );
      },
    );
  }
}

class _PrettyTextField extends StatelessWidget {
  const _PrettyTextField({
    required this.label,
    required this.value,
    required this.onChanged,
    this.identity,
  });

  final String label;
  final String value;
  final ValueChanged<String> onChanged;
  final Object? identity;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      key: ValueKey(
        '$label-${identity == null ? 'default' : identityHashCode(identity)}',
      ),
      initialValue: value,
      decoration: InputDecoration(labelText: label),
      onChanged: onChanged,
    );
  }
}

void _showResourceDetails(
  BuildContext context,
  _ResourceSpec spec,
  ResourceInfo resource,
) {
  showResourceDetailOverlay(
    context,
    heroTag: spec.heroTag,
    title: spec.label,
    icon: spec.icon,
    kind: switch (spec.kind) {
      _ResourceKind.image => ResourceDetailKind.image,
      _ResourceKind.audio => ResourceDetailKind.audio,
      _ResourceKind.songlist => ResourceDetailKind.document,
    },
    resource: resource,
    statusLabel: _resourceStatusLabel(context, resource),
    statusTone: resource.source == 'fallback'
        ? PrettyPillTone.warning
        : PrettyPillTone.primary,
    preview: switch (spec.kind) {
      _ResourceKind.audio => AudioPreviewPanel(
        resource: resource,
        onConvertToCompatibleOgg: spec.onAudioConvert,
      ),
      _ResourceKind.songlist => _SonglistDetailPreview(
        resource: resource,
        scan: spec.scan,
      ),
      _ResourceKind.image => null,
    },
  );
}

class _SonglistDetailPreview extends StatelessWidget {
  const _SonglistDetailPreview({required this.resource, required this.scan});

  final ResourceInfo resource;
  final SingleSongScanResult? scan;

  @override
  Widget build(BuildContext context) {
    final rows = [
      InfoRow(
        label: context.t('songlistPreview.songId'),
        value: scan?.songId ?? '-',
      ),
      InfoRow(
        label: context.t('songlistPreview.title'),
        value: scan?.title ?? '-',
      ),
      InfoRow(
        label: context.t('songlistPreview.artist'),
        value: scan?.artist ?? '-',
      ),
      InfoRow(
        label: context.t('songlistPreview.chartCount'),
        value: '${scan?.charts.length ?? 0}',
      ),
      InfoRow(
        label: context.t('songlistPreview.sourceKind'),
        value: sourceKindLabel(scan?.sourceKind ?? SingleSourceKind.unknown),
      ),
    ];
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: context.colors.surfaceContainerHigh.withValues(alpha: 0.52),
        borderRadius: BorderRadius.circular(AppRadius.workspaceInset),
        border: Border.all(color: context.colors.outlineVariant),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            context.t('resource.structurePreview'),
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: context.colors.onPrimaryContainer,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 12),
          ...rows,
          if (resource.source == 'fallback') ...[
            const SizedBox(height: 8),
            PrettyPill(
              label: context.t('resource.fallback'),
              tone: PrettyPillTone.warning,
            ),
          ],
        ],
      ),
    );
  }
}

bool _isBusy(SingleSongState page) =>
    page.phase == OperationPhase.scanning ||
    page.phase == OperationPhase.saving;

bool _hasAdoptedChart(SingleSongState page) {
  final charts = page.edit?.charts ?? const <ChartEditState>[];
  return charts.isEmpty || charts.any((chart) => chart.adopted);
}

PrettyPillTone _phaseTone(OperationPhase phase) => switch (phase) {
  OperationPhase.failed => PrettyPillTone.danger,
  OperationPhase.saved ||
  OperationPhase.ready ||
  OperationPhase.scanned => PrettyPillTone.success,
  OperationPhase.scanning || OperationPhase.saving => PrettyPillTone.primary,
  OperationPhase.idle => PrettyPillTone.neutral,
};

IconData _phaseIcon(OperationPhase phase) => switch (phase) {
  OperationPhase.failed => Icons.error_rounded,
  OperationPhase.saved ||
  OperationPhase.ready ||
  OperationPhase.scanned => Icons.check_circle_rounded,
  OperationPhase.scanning || OperationPhase.saving => Icons.sync_rounded,
  OperationPhase.idle => Icons.hourglass_empty_rounded,
};

ChartMetadata? _findChartForAff(SingleSongScanResult? scan, AffInfo aff) {
  final charts = scan?.charts ?? const <ChartMetadata>[];
  for (final chart in charts) {
    if (chart.ratingClass == aff.ratingClass) return chart;
  }
  return null;
}

String _suggestedOutput(SingleSongEditState? edit, SingleSongScanResult? scan) {
  final id = edit?.levelId.trim().isNotEmpty == true
      ? edit!.levelId.trim()
      : scan?.songId ?? 'etoilebridge-level';
  return id.endsWith('.arcpkg') ? id : '$id.arcpkg';
}

String _chartChipLabel(ChartEditState chart) {
  final parts = [
    'RC ${chart.ratingClass}',
    if (chart.difficulty.trim().isNotEmpty) chart.difficulty.trim(),
    if (chart.chartConstant.trim().isNotEmpty) chart.chartConstant.trim(),
  ];
  return parts.join(' / ');
}

String _difficultyLabel(ChartEditState chart) {
  final difficulty = chart.difficulty.trim();
  if (difficulty.isNotEmpty) return difficulty;
  return 'RC ${chart.ratingClass}';
}

String _notesCountForChart(SingleSongScanResult? scan, ChartEditState chart) {
  final rawCharts = scan?.charts ?? const <ChartMetadata>[];
  for (final raw in rawCharts) {
    final sameClass = raw.ratingClass == chart.ratingClass;
    final sameAff =
        raw.affName == chart.affName ||
        raw.affPath == chart.affPath ||
        raw.chartPath == chart.chartPath;
    if (!sameClass && !sameAff) continue;
    final value = _firstRawValue(raw.raw, const [
      'notes',
      'noteCount',
      'notesCount',
      'totalNotes',
      'objectCount',
    ]);
    if (value != null) return value;
  }
  for (final aff in scan?.affFiles ?? const <AffInfo>[]) {
    final sameClass = aff.ratingClass == chart.ratingClass;
    final sameAff =
        aff.name == chart.affName ||
        aff.path == chart.affPath ||
        aff.path == chart.chartPath;
    if (!sameClass && !sameAff) continue;
    final value = _firstRawValue(aff.raw, const [
      'notes',
      'noteCount',
      'notesCount',
      'totalNotes',
      'objectCount',
    ]);
    if (value != null) return value;
  }
  return '-';
}

String? _firstRawValue(Map<String, Object?> raw, List<String> keys) {
  for (final key in keys) {
    final value = raw[key];
    if (value == null) continue;
    final text = '$value'.trim();
    if (text.isNotEmpty) return text;
  }
  return null;
}

String _orDash(String? value) {
  final text = value?.trim();
  return text == null || text.isEmpty ? '-' : text;
}

String _formatBytes(int? bytes) {
  if (bytes == null) return '-';
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
  return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
}

int? _fileLength(String path) {
  try {
    final file = File(path);
    if (file.existsSync()) return file.lengthSync();
  } catch (_) {
    return null;
  }
  return null;
}
