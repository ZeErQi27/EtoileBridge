import 'dart:async';
import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;

import '../../app/app_state.dart';
import '../../app/routes.dart';
import '../../app/safe_layout.dart';
import '../../app/safe_action.dart';
import '../../core/i18n/app_strings.dart';
import '../../core/models/conversion_options.dart';
import '../../core/models/operation_models.dart';
import '../../core/models/single_song_models.dart';
import '../../shared/widgets/conversion_options_controls.dart';
import '../../shared/widgets/audio_preview.dart';
import '../../shared/widgets/info_row.dart';
import '../../shared/widgets/log_panel.dart';
import '../../shared/widgets/pretty_ui.dart';
import '../../shared/layout/app_layout_tokens.dart';
import 'single_song_state.dart';

class SingleSongPage extends StatefulWidget {
  const SingleSongPage({super.key});

  @override
  State<SingleSongPage> createState() => _SingleSongPageState();
}

class _SingleSongPageState extends State<SingleSongPage> {
  final _saveKey = GlobalKey();
  bool _saveHighlighted = false;
  Timer? _highlightTimer;

  @override
  void dispose() {
    _highlightTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final page = state.singleSong;
    final busy = _isBusy(page);

    return SingleChildScrollView(
      padding: AppLayoutTokens.pagePadding(context),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(
            maxWidth: AppLayoutTokens.pageContentMaxWidth,
          ),
          child: _FadeIn(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const _SingleHeader(),
                const SizedBox(height: 24),
                _ResponsiveDashboard(
                  enableThreeColumns: page.scan != null,
                  left: [
                    _InputWorkbenchCard(state: state, page: page, busy: busy),
                    _StatusWorkbenchCard(
                      page: page,
                      busy: busy,
                      onContinue: page.scan == null ? null : _jumpToSaveSection,
                    ),
                    if (page.scan != null)
                      _SaveWorkbenchCard(
                        key: _saveKey,
                        state: state,
                        page: page,
                        busy: busy,
                        highlighted: _saveHighlighted,
                      ),
                  ],
                  middle: [
                    _SongChartInfoWorkbenchCard(
                      state: state,
                      page: page,
                      onChanged: _refresh,
                    ),
                    _AppearanceWorkbenchCard(state: state, page: page),
                    _PreprocessWorkbenchCard(state: state, page: page),
                  ],
                  right: [
                    _ResourcePreviewWorkbenchCard(state: state, page: page),
                    _AffMappingWorkbenchCard(scan: page.scan),
                    if (page.scan == null)
                      _SaveWorkbenchCard(
                        key: _saveKey,
                        state: state,
                        page: page,
                        busy: busy,
                        highlighted: _saveHighlighted,
                      ),
                    if (page.scan != null)
                      _DiagnosticsWorkbenchCard(page: page),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _jumpToSaveSection() async {
    final targetContext = _saveKey.currentContext;
    if (targetContext != null) {
      await Scrollable.ensureVisible(
        targetContext,
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOutCubic,
        alignment: 0.18,
      );
    }
    if (!mounted) return;
    setState(() => _saveHighlighted = true);
    _highlightTimer?.cancel();
    _highlightTimer = Timer(const Duration(milliseconds: 520), () {
      if (mounted) setState(() => _saveHighlighted = false);
    });
  }

  void _refresh() {
    if (mounted) setState(() {});
  }
}

class _SingleHeader extends StatelessWidget {
  const _SingleHeader();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          context.t('app.title'),
          style: Theme.of(context).textTheme.displaySmall?.copyWith(
            color: PrettyColors.text,
            fontWeight: FontWeight.w900,
            letterSpacing: 0,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          context.t('page.singleSong'),
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: PrettyColors.muted,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }
}

class _ResponsiveDashboard extends StatelessWidget {
  const _ResponsiveDashboard({
    required this.left,
    required this.right,
    this.middle = const [],
    this.enableThreeColumns = false,
  });

  final List<Widget> left;
  final List<Widget> middle;
  final List<Widget> right;
  final bool enableThreeColumns;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth;
        final threeColumn =
            enableThreeColumns &&
            middle.isNotEmpty &&
            width >= AppLayoutTokens.threeColumnBreakpoint;
        final twoColumn = width >= AppLayoutTokens.twoColumnBreakpoint;
        if (threeColumn) {
          return AppWorkspaceLayout(left: left, middle: middle, right: right);
        }
        if (!twoColumn) {
          return AppWorkspaceColumn(children: [...left, ...middle, ...right]);
        }
        return AppWorkspaceLayout(left: left, middle: middle, right: right);
      },
    );
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
    return PrettyCard(
      title: context.t('input'),
      icon: Icons.folder_open_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              PrettyGlassButton(
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
              PrettyGlassButton(
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
              PrettyGlassButton(
                label: context.t('scan'),
                icon: Icons.refresh_rounded,
                onPressed: busy || input == null
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
          const SizedBox(height: 18),
          Text(
            context.t('currentInput'),
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: PrettyColors.muted,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 10),
          if (input == null)
            DashedEmptyBox(
              icon: Icons.folder_open_rounded,
              title: context.t('single.input.emptyTitle'),
              subtitle: context.t('single.input.emptySubtitle'),
            )
          else
            _SelectedInputBox(path: input, state: state),
          const SizedBox(height: 8),
          TextButton.icon(
            onPressed: input == null
                ? null
                : () => safeAction(
                    context,
                    id: 'single.openLocation',
                    label: context.t('openLocation'),
                    page: AppPageId.singleSong,
                    action: () => state.platform.openLocation.openLocation(
                      FileSystemEntity.isDirectorySync(input)
                          ? input
                          : p.dirname(input),
                    ),
                  ),
            icon: const Icon(Icons.folder_rounded, size: 18),
            label: Text(context.t('openLocation')),
          ),
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
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: PrettyColors.primarySofter.withValues(alpha: 0.55),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: PrettyColors.borderStrong),
      ),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: PrettyColors.primarySoft,
              borderRadius: BorderRadius.circular(16),
            ),
            child: const Icon(
              Icons.insert_drive_file_rounded,
              color: PrettyColors.primary,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    color: PrettyColors.text,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 4),
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

class _StatusWorkbenchCard extends StatelessWidget {
  const _StatusWorkbenchCard({
    required this.page,
    required this.busy,
    required this.onContinue,
  });

  final SingleSongState page;
  final bool busy;
  final Future<void> Function()? onContinue;

  @override
  Widget build(BuildContext context) {
    final tone = _phaseTone(page.phase);
    final statusTitle = _statusTitle(context, page);
    final statusDescription = _statusDescription(context, page);
    return PrettyCard(
      title: context.t('status'),
      icon: Icons.info_outline_rounded,
      trailing: PrettyPill(
        label: context.t(page.phase.i18nKey),
        icon: _phaseIcon(page.phase),
        tone: tone,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AnimatedSwitcher(
            duration: const Duration(milliseconds: 240),
            child: Container(
              key: ValueKey(page.phase),
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: _statusBoxColor(tone),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: _statusBorderColor(tone)),
              ),
              child: Row(
                children: [
                  Icon(
                    _phaseIcon(page.phase),
                    color: _statusAccentColor(tone),
                    size: 24,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      statusTitle,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        color: PrettyColors.text,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Text(
            statusDescription,
            style: Theme.of(
              context,
            ).textTheme.bodyMedium?.copyWith(color: PrettyColors.muted),
          ),
          if (busy) ...[
            const SizedBox(height: 14),
            ClipRRect(
              borderRadius: BorderRadius.circular(999),
              child: LinearProgressIndicator(
                minHeight: 8,
                backgroundColor: PrettyColors.primarySofter,
                color: PrettyColors.primary,
              ),
            ),
          ],
          if (onContinue != null) ...[
            const SizedBox(height: 12),
            TextButton.icon(
              onPressed: () => safeAction(
                context,
                id: 'single.continue.save',
                label: context.t('continue'),
                page: AppPageId.singleSong,
                action: onContinue!,
              ),
              icon: const Icon(Icons.south_rounded),
              label: Text(context.t('continue')),
            ),
          ],
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
          _ChartSelectionPanel(state: state, page: page, onChanged: onChanged),
          const SizedBox(height: 12),
          _SongChartSummaryStrip(edit: edit, chart: current),
          const SizedBox(height: 14),
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
              _PrettyTextField(
                label: 'bpmText',
                identity: edit,
                value: edit.bpmText,
                onChanged: (value) {
                  edit.bpmText = value;
                  state.markSingleChartEdited('metadata.bpmText');
                  onChanged();
                },
              ),
              _PrettyTextField(
                label: 'baseBpm',
                identity: edit,
                value: edit.bpmBase,
                onChanged: (value) {
                  edit.bpmBase = value;
                  state.markSingleChartEdited('metadata.baseBpm');
                  onChanged();
                },
              ),
              _PrettyTextField(
                label: context.t('difficulty'),
                identity: current,
                value: current.difficulty,
                onChanged: (value) {
                  current.difficulty = value;
                  state.markSingleChartEdited('chart.difficulty');
                  onChanged();
                },
              ),
              _PrettyTextField(
                label: context.t('chartConstant'),
                identity: current,
                value: current.chartConstant,
                onChanged: (value) {
                  current.chartConstant = value;
                  state.markSingleChartEdited('chart.constant');
                  onChanged();
                },
              ),
              _PrettyTextField(
                label: context.t('charter'),
                identity: current,
                value: current.charter,
                onChanged: (value) {
                  current.charter = value;
                  state.markSingleChartEdited('chart.charter');
                  onChanged();
                },
              ),
              _PrettyTextField(
                label: context.t('illustrator'),
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
          const SizedBox(height: 14),
          _AdvancedEditorSection(
            title: context.t('advanced'),
            children: [
              _MetricsGrid(
                compact: true,
                items: [
                  _Metric('songId', edit.levelId),
                  _Metric(context.t('selectedAff'), current.affName),
                  _Metric(context.t('chartPath'), current.chartPath),
                  _Metric(
                    context.t('source'),
                    sourceKindLabel(
                      page.scan?.sourceKind ?? SingleSourceKind.unknown,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
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
                  _PrettyTextField(
                    label: 'AFF',
                    identity: current,
                    value: current.affName ?? current.affPath ?? '-',
                    enabled: false,
                    onChanged: (_) {},
                  ),
                  _PrettyTextField(
                    label: 'chartPath',
                    identity: current,
                    value: current.chartPath ?? '-',
                    enabled: false,
                    onChanged: (_) {},
                  ),
                ],
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
  const _AppearanceWorkbenchCard({required this.state, required this.page});

  final AppState state;
  final SingleSongState page;

  @override
  Widget build(BuildContext context) {
    final value = page.appearanceOptions;
    return PrettyCard(
      title: context.t('appearance'),
      icon: Icons.auto_awesome_rounded,
      compact: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _SideSegmentedControl(
            label: context.t('appearance.side'),
            value: value.side,
            onChanged: (side) =>
                _update(context, value.copyWith(side: side), 'side', side),
          ),
          const SizedBox(height: 14),
          _FieldGrid(
            minWidth: 180,
            children: [
              _AppearanceDropdown(
                label: context.t('appearance.note'),
                value: value.note,
                choices: noteAppearanceChoices,
                onChanged: (note) =>
                    _update(context, value.copyWith(note: note), 'note', note),
              ),
              _AppearanceDropdown(
                label: context.t('appearance.particle'),
                value: value.particle,
                choices: particleAppearanceChoices,
                onChanged: (particle) => _update(
                  context,
                  value.copyWith(particle: particle),
                  'particle',
                  particle,
                ),
              ),
              _AppearanceDropdown(
                label: context.t('appearance.accent'),
                value: value.accent,
                choices: accentAppearanceChoices,
                onChanged: (accent) => _update(
                  context,
                  value.copyWith(accent: accent),
                  'accent',
                  accent,
                ),
              ),
              _AppearanceDropdown(
                label: context.t('appearance.track'),
                value: value.track,
                choices: trackAppearanceChoices,
                onChanged: (track) => _update(
                  context,
                  value.copyWith(track: track),
                  'track',
                  track,
                ),
              ),
              _AppearanceDropdown(
                label: context.t('appearance.singleLine'),
                value: value.singleLine,
                choices: singleLineAppearanceChoices,
                onChanged: (singleLine) => _update(
                  context,
                  value.copyWith(singleLine: singleLine),
                  'singleLine',
                  singleLine,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  void _update(
    BuildContext context,
    ArcCreateAppearanceOptions next,
    String field,
    String option,
  ) {
    safeAction(
      context,
      id: 'single.appearance.$field',
      label: option,
      page: AppPageId.singleSong,
      action: () => state.updateSingleAppearance(next),
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
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: Theme.of(context).textTheme.labelLarge?.copyWith(
            color: PrettyColors.muted,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 8),
        LayoutBuilder(
          builder: (context, constraints) {
            final compact = constraints.maxWidth < 360;
            return Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final choice in sideAppearanceChoices)
                  _SideChoiceButton(
                    label: context.t(choice.labelKey),
                    selected: selected == choice.value,
                    compact: compact,
                    onPressed: selected == choice.value
                        ? null
                        : () => onChanged(choice.value),
                  ),
              ],
            );
          },
        ),
      ],
    );
  }
}

class _SideChoiceButton extends StatelessWidget {
  const _SideChoiceButton({
    required this.label,
    required this.selected,
    required this.compact,
    required this.onPressed,
  });

  final String label;
  final bool selected;
  final bool compact;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final foreground = selected ? Colors.white : PrettyColors.primaryDark;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOutCubic,
      constraints: BoxConstraints(minWidth: compact ? 96 : 120),
      decoration: BoxDecoration(
        color: selected
            ? PrettyColors.primary
            : PrettyColors.primarySofter.withValues(alpha: 0.56),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: selected ? PrettyColors.primary : PrettyColors.borderStrong,
        ),
        boxShadow: selected
            ? [
                BoxShadow(
                  color: PrettyColors.primary.withValues(alpha: 0.24),
                  blurRadius: 16,
                  offset: const Offset(0, 8),
                ),
              ]
            : null,
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(18),
          onTap: onPressed,
          child: Padding(
            padding: EdgeInsets.symmetric(
              horizontal: compact ? 12 : 16,
              vertical: 11,
            ),
            child: Center(
              child: Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: foreground,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _PreprocessWorkbenchCard extends StatelessWidget {
  const _PreprocessWorkbenchCard({required this.state, required this.page});

  final AppState state;
  final SingleSongState page;

  @override
  Widget build(BuildContext context) {
    return PrettyCard(
      title: context.t('preprocess'),
      icon: Icons.tune_rounded,
      compact: true,
      child: PreprocessOptionsPanel(
        value: page.preprocessOptions,
        onChanged: state.updateSinglePreprocess,
        pageId: AppPageId.singleSong,
        actionPrefix: 'single.preprocess',
      ),
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
      decoration: InputDecoration(
        labelText: label,
        filled: true,
        fillColor: PrettyColors.primarySofter.withValues(alpha: 0.48),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: PrettyColors.border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: PrettyColors.border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: PrettyColors.primary),
        ),
      ),
      borderRadius: BorderRadius.circular(16),
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
    return PrettyCard(
      title: context.t('resources'),
      icon: Icons.image_rounded,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = SafeLayout.widthFromConstraints(constraints, context);
          final columns = width >= 760 ? 3 : (width >= 430 ? 2 : 1);
          const gap = 12.0;
          final itemWidth = ((width - gap * (columns - 1)) / columns).clamp(
            168.0,
            360.0,
          );
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
              warning: effective.warnings.isEmpty
                  ? null
                  : effective.warnings.join('\n'),
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
          return Wrap(
            spacing: gap,
            runSpacing: gap,
            children: [
              for (final resource in resources)
                SizedBox(
                  width: itemWidth,
                  height: 262,
                  child: _ResourceTile(spec: resource),
                ),
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
    this.warning,
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
  final String? warning;
  final String? actionLabel;
  final VoidCallback? onAction;
  final AudioConversionCallback? onAudioConvert;

  String get heroTag => 'single-song-resource-$id';
}

class _ResourceTile extends StatelessWidget {
  const _ResourceTile({required this.spec});

  final _ResourceSpec spec;

  @override
  Widget build(BuildContext context) {
    final resource = spec.resource;
    final file = resource?.path == null ? null : File(resource!.path!);
    final exists = file != null && file.existsSync();
    final hasResource = resource != null;
    final hasMissingReference = spec.missingReference?.isNotEmpty == true;
    return Hero(
      tag: spec.heroTag,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(18),
          onTap: hasResource
              ? () => _showResourceDetails(context, spec, resource)
              : null,
          child: Ink(
            decoration: BoxDecoration(
              color: PrettyColors.panelSoft,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(color: PrettyColors.border),
            ),
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(spec.icon, color: PrettyColors.text, size: 20),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        spec.label,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleSmall?.copyWith(
                          color: PrettyColors.text,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                    PrettyPill(
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
                  ],
                ),
                const SizedBox(height: 12),
                Expanded(
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(16),
                    child: _ResourcePreviewBody(
                      spec: spec,
                      resource: resource,
                      file: file,
                      exists: exists,
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                PrettyPill(
                  label: context.t(spec.sourceKey),
                  tone: hasMissingReference
                      ? PrettyPillTone.warning
                      : PrettyPillTone.neutral,
                ),
                const SizedBox(height: 6),
                Text(
                  resource?.name ??
                      spec.missingReference ??
                      context.t('resource.waiting'),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(
                    context,
                  ).textTheme.bodySmall?.copyWith(color: PrettyColors.muted),
                ),
                SizedBox(
                  height: 34,
                  child: spec.onAction == null
                      ? TextButton.icon(
                          style: TextButton.styleFrom(
                            padding: EdgeInsets.zero,
                            visualDensity: VisualDensity.compact,
                            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                          ),
                          onPressed: hasResource
                              ? () => _showResourceDetails(
                                  context,
                                  spec,
                                  resource,
                                )
                              : null,
                          icon: const Icon(
                            Icons.info_outline_rounded,
                            size: 17,
                          ),
                          label: Text(context.t('resource.details')),
                        )
                      : TextButton.icon(
                          style: TextButton.styleFrom(
                            padding: EdgeInsets.zero,
                            visualDensity: VisualDensity.compact,
                            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                          ),
                          onPressed: spec.onAction,
                          icon: const Icon(
                            Icons.add_photo_alternate_rounded,
                            size: 17,
                          ),
                          label: Text(spec.actionLabel!),
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

class _ResourcePreviewBody extends StatelessWidget {
  const _ResourcePreviewBody({
    required this.spec,
    required this.resource,
    required this.file,
    required this.exists,
  });

  final _ResourceSpec spec;
  final ResourceInfo? resource;
  final File? file;
  final bool exists;

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
                fit: BoxFit.cover,
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
      _ResourceKind.songlist => _SonglistMiniPreview(
        resource: current,
        scan: spec.scan,
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
        color: const Color(0xFFFFF4DD).withValues(alpha: 0.72),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: PrettyColors.warning.withValues(alpha: 0.42)),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(
            Icons.image_not_supported_rounded,
            color: PrettyColors.warning,
            size: 28,
          ),
          const SizedBox(height: 8),
          Text(
            reference,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: PrettyColors.text,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            context.t('resource.externalBgHint'),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
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

class _WaitingResource extends StatelessWidget {
  const _WaitingResource({required this.icon});

  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: PrettyColors.primarySofter.withValues(alpha: 0.34),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: PrettyColors.borderStrong,
          style: BorderStyle.solid,
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: PrettyColors.faint, size: 24),
          const SizedBox(height: 5),
          Text(
            context.t('resource.waiting'),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: PrettyColors.muted,
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
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: PrettyColors.primarySofter.withValues(alpha: 0.44),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, color: PrettyColors.primary, size: 28),
          const SizedBox(height: 8),
          Text(
            title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: PrettyColors.text,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 3),
          Text(
            subtitle,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(
              context,
            ).textTheme.bodySmall?.copyWith(color: PrettyColors.muted),
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

class _SonglistMiniPreview extends StatelessWidget {
  const _SonglistMiniPreview({required this.resource, required this.scan});

  final ResourceInfo resource;
  final SingleSongScanResult? scan;

  @override
  Widget build(BuildContext context) {
    final lines = <String>[
      if (scan?.songId?.isNotEmpty ?? false) scan!.songId!,
      if (scan?.title?.isNotEmpty ?? false) scan!.title!,
      '${context.t('chartCount')}: ${scan?.charts.length ?? 0}',
      sourceKindLabel(scan?.sourceKind ?? SingleSourceKind.unknown),
    ];
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: PrettyColors.primarySofter.withValues(alpha: 0.44),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Row(
            children: [
              const Icon(
                Icons.description_rounded,
                color: PrettyColors.primary,
                size: 22,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  resource.source == 'fallback'
                      ? context.t('resource.fallback')
                      : (resource.name ?? context.t('songlist')),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelMedium?.copyWith(
                    color: PrettyColors.primaryDark,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          for (final line in lines.take(3))
            Text(
              line,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(
                context,
              ).textTheme.bodySmall?.copyWith(color: PrettyColors.muted),
            ),
        ],
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

String _fileFormat(String? value) {
  if (value == null || value.trim().isEmpty) return '-';
  final extension = p.extension(value).replaceFirst('.', '').toUpperCase();
  return extension.isEmpty ? '-' : extension;
}

class _AffMappingWorkbenchCard extends StatelessWidget {
  const _AffMappingWorkbenchCard({required this.scan});

  final SingleSongScanResult? scan;

  @override
  Widget build(BuildContext context) {
    final files = scan?.affFiles ?? const <AffInfo>[];
    final adopted = files.where((aff) => aff.adopted).length;
    final ignored = files.length - adopted;
    return PrettyCard(
      title: context.t('affMapping'),
      icon: Icons.link_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: _AffCount(
                  label: context.t('adoptedAff'),
                  value: adopted,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: _AffCount(
                  label: context.t('ignoredAff'),
                  value: ignored,
                ),
              ),
            ],
          ),
          if (files.isNotEmpty) ...[
            const SizedBox(height: 14),
            for (final aff in files.take(6)) _AffLine(aff: aff, scan: scan),
          ],
        ],
      ),
    );
  }
}

class _AffCount extends StatelessWidget {
  const _AffCount({required this.label, required this.value});

  final String label;
  final int value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: PrettyColors.primarySofter.withValues(alpha: 0.52),
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: Theme.of(
              context,
            ).textTheme.labelMedium?.copyWith(color: PrettyColors.muted),
          ),
          const SizedBox(height: 4),
          Text(
            '$value',
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
              color: PrettyColors.text,
              fontWeight: FontWeight.w900,
            ),
          ),
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
    required this.highlighted,
    super.key,
  });

  final AppState state;
  final SingleSongState page;
  final bool busy;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    final hasScan = page.scan != null && page.edit != null;
    final canSave = hasScan && _hasAdoptedChart(page);
    final saved = page.saveResult != null;
    final failed = page.phase == OperationPhase.failed && hasScan;
    final outputPath = page.saveResult?.outputPath;
    final outputName = outputPath == null || outputPath.isEmpty
        ? _suggestedOutput(page.edit, page.scan)
        : p.basename(outputPath);
    final outputDir = outputPath == null || outputPath.isEmpty
        ? page.lastSaveDirectory
        : p.dirname(outputPath);
    final outputSize =
        page.saveResult?.sizeBytes ??
        (outputPath == null || outputPath.isEmpty
            ? null
            : _fileLength(outputPath));

    return AnimatedScale(
      duration: const Duration(milliseconds: 220),
      scale: highlighted ? 1.012 : 1,
      child: Stack(
        children: [
          PrettyCard(
            title: context.t('saveAndExport'),
            icon: Icons.download_rounded,
            emphasized: highlighted,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                AnimatedContainer(
                  duration: const Duration(milliseconds: 220),
                  curve: Curves.easeOutCubic,
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: saved
                        ? const Color(0xFFEAF8F1)
                        : failed
                        ? const Color(0xFFFFEEF3)
                        : PrettyColors.primarySofter.withValues(alpha: 0.62),
                    borderRadius: BorderRadius.circular(18),
                    border: Border.all(
                      color: saved
                          ? PrettyColors.success.withValues(alpha: 0.35)
                          : failed
                          ? PrettyColors.danger.withValues(alpha: 0.28)
                          : PrettyColors.border,
                    ),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        saved
                            ? Icons.check_circle_rounded
                            : failed
                            ? Icons.error_rounded
                            : Icons.download_done_rounded,
                        color: saved
                            ? PrettyColors.success
                            : failed
                            ? PrettyColors.danger
                            : PrettyColors.primary,
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              saved
                                  ? context.t('saveComplete')
                                  : failed
                                  ? context.t('saveFailed')
                                  : canSave
                                  ? context.t('readyToSave')
                                  : hasScan
                                  ? context.t('noChartSelected')
                                  : context.t('saveUnavailable'),
                              style: Theme.of(context).textTheme.titleSmall
                                  ?.copyWith(
                                    color: PrettyColors.text,
                                    fontWeight: FontWeight.w900,
                                  ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              failed
                                  ? (page.error ?? context.t('saveFailed'))
                                  : canSave
                                  ? context.t('saveHint')
                                  : hasScan
                                  ? context.t('chartSelectionHint')
                                  : context.t('saveDisabledHint'),
                              style: Theme.of(context).textTheme.bodySmall
                                  ?.copyWith(color: PrettyColors.muted),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: PrettyColors.panelSoft,
                    borderRadius: BorderRadius.circular(18),
                    border: Border.all(color: PrettyColors.border),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Container(
                            width: 42,
                            height: 42,
                            decoration: BoxDecoration(
                              color: PrettyColors.primarySoft,
                              borderRadius: BorderRadius.circular(14),
                            ),
                            child: const Icon(
                              Icons.inventory_2_rounded,
                              color: PrettyColors.primary,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  outputName,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: Theme.of(context).textTheme.titleSmall
                                      ?.copyWith(
                                        color: PrettyColors.text,
                                        fontWeight: FontWeight.w900,
                                      ),
                                ),
                                const SizedBox(height: 3),
                                Text(
                                  outputSize == null
                                      ? context.t('sizePending')
                                      : _formatBytes(outputSize),
                                  style: Theme.of(context).textTheme.bodySmall
                                      ?.copyWith(color: PrettyColors.muted),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Text(
                        context.t('outputLocation'),
                        style: Theme.of(context).textTheme.labelSmall?.copyWith(
                          color: PrettyColors.muted,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 10,
                        ),
                        decoration: BoxDecoration(
                          color: PrettyColors.primarySofter.withValues(
                            alpha: 0.5,
                          ),
                          borderRadius: BorderRadius.circular(13),
                          border: Border.all(color: PrettyColors.border),
                        ),
                        child: Text(
                          outputPath ?? outputDir ?? '-',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(
                                color: PrettyColors.muted,
                                fontWeight: FontWeight.w700,
                              ),
                        ),
                      ),
                      const SizedBox(height: 10),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          OutlinedButton.icon(
                            onPressed: outputPath == null
                                ? null
                                : () => safeAction(
                                    context,
                                    id: 'single.save.copyPath',
                                    label: outputPath,
                                    page: AppPageId.singleSong,
                                    action: () async {
                                      await Clipboard.setData(
                                        ClipboardData(text: outputPath),
                                      );
                                      if (!context.mounted) return;
                                      ScaffoldMessenger.of(
                                        context,
                                      ).showSnackBar(
                                        SnackBar(
                                          content: Text(
                                            context.t('pathCopied'),
                                          ),
                                          behavior: SnackBarBehavior.floating,
                                          duration: const Duration(
                                            milliseconds: 1400,
                                          ),
                                        ),
                                      );
                                    },
                                  ),
                            icon: const Icon(Icons.copy_rounded, size: 18),
                            label: Text(context.t('copyPath')),
                          ),
                          OutlinedButton.icon(
                            onPressed: outputPath == null || !Platform.isWindows
                                ? null
                                : () => safeAction(
                                    context,
                                    id: 'single.save.openFolder',
                                    label: outputPath,
                                    page: AppPageId.singleSong,
                                    action: () async {
                                      final ok = await state
                                          .platform
                                          .openLocation
                                          .openLocation(outputPath);
                                      if (!context.mounted || ok) return;
                                      ScaffoldMessenger.of(
                                        context,
                                      ).showSnackBar(
                                        SnackBar(
                                          content: Text(
                                            context.t('openLocationFailed'),
                                          ),
                                          behavior: SnackBarBehavior.floating,
                                        ),
                                      );
                                    },
                                  ),
                            icon: const Icon(
                              Icons.folder_open_rounded,
                              size: 18,
                            ),
                            label: Text(context.t('openFolder')),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    style: FilledButton.styleFrom(
                      backgroundColor: PrettyColors.primary,
                      foregroundColor: Colors.white,
                      disabledBackgroundColor: PrettyColors.primarySoft,
                      disabledForegroundColor: PrettyColors.faint,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(18),
                      ),
                    ),
                    onPressed: busy || !canSave
                        ? null
                        : () => safeAction(
                            context,
                            id: 'single.save.start',
                            label: context.t('saveArcpkg'),
                            page: AppPageId.singleSong,
                            action: state.saveSingleSong,
                          ),
                    icon: Icon(
                      saved ? Icons.sync_rounded : Icons.save_alt_rounded,
                    ),
                    label: Text(
                      saved
                          ? context.t('exportAgain')
                          : context.t('saveArcpkg'),
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                Text(
                  saved
                      ? context.t('saveAgainHint')
                      : context.t('saveOverwriteHint'),
                  style: Theme.of(
                    context,
                  ).textTheme.bodySmall?.copyWith(color: PrettyColors.muted),
                ),
              ],
            ),
          ),
          Positioned.fill(
            child: IgnorePointer(
              child: ClipRRect(
                borderRadius: BorderRadius.circular(PrettyRadii.card),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  curve: Curves.easeOutCubic,
                  color: highlighted
                      ? Colors.black.withValues(alpha: 0.16)
                      : Colors.transparent,
                ),
              ),
            ),
          ),
        ],
      ),
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
    if (charts.length == 1) {
      return Wrap(
        spacing: 10,
        runSpacing: 8,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          PrettyPill(
            label: _chartChipLabel(charts.single),
            icon: Icons.check_rounded,
            tone: PrettyPillTone.primary,
          ),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 360),
            child: Text(
              context.t('singleChartHint'),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(
                context,
              ).textTheme.bodySmall?.copyWith(color: PrettyColors.muted),
            ),
          ),
        ],
      );
    }

    final selectedCount = charts.where((chart) => chart.adopted).length;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: PrettyColors.primarySofter.withValues(alpha: 0.38),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: PrettyColors.border),
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
                    color: PrettyColors.text,
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
            style: Theme.of(
              context,
            ).textTheme.bodySmall?.copyWith(color: PrettyColors.muted),
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
          ? PrettyColors.primarySoft.withValues(alpha: 0.58)
          : PrettyColors.panel,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
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
                  color: PrettyColors.text,
                  fontWeight: FontWeight.w800,
                ),
              );
              final notes = Text(
                '${context.t('notesCount')}: ${_notesCountForChart(page.scan, chart)}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(
                  context,
                ).textTheme.bodySmall?.copyWith(color: PrettyColors.muted),
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
          PrettyExpandableSection(
            title: context.t('missingFields'),
            icon: Icons.rule_rounded,
            child: Align(
              alignment: Alignment.centerLeft,
              child: SelectableText(
                page.scanDiagnostics.isEmpty
                    ? context.t('diagnosticsUnavailable')
                    : page.scanDiagnostics.join('\n'),
              ),
            ),
          ),
          PrettyExpandableSection(
            title: context.t('logs'),
            icon: Icons.article_rounded,
            child: LogPanel(logs: page.logs),
          ),
          InfoRow(
            label: context.t('rawScanJson'),
            value: page.scanRawJsonPath ?? '-',
          ),
          InfoRow(
            label: context.t('scanDiagnosticsPath'),
            value: page.scanDiagnosticsPath ?? '-',
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
        color: PrettyColors.panelSoft,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            metric.label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: PrettyColors.muted,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 5),
          SelectableText(
            _orDash(metric.value),
            maxLines: 2,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: PrettyColors.text,
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
  const _FieldGrid({required this.children, this.minWidth = 190});

  final List<Widget> children;
  final double minWidth;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = SafeLayout.widthFromConstraints(constraints, context);
        final columns = width >= 640 ? 2 : 1;
        const gap = 10.0;
        final itemWidth = ((width - gap * (columns - 1)) / columns)
            .clamp(math.min(minWidth, width), 620.0)
            .toDouble();
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
    this.enabled = true,
  });

  final String label;
  final String value;
  final ValueChanged<String> onChanged;
  final Object? identity;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      key: ValueKey(
        '$label-${identity == null ? 'default' : identityHashCode(identity)}',
      ),
      initialValue: value,
      enabled: enabled,
      decoration: InputDecoration(labelText: label),
      onChanged: onChanged,
    );
  }
}

class _FadeIn extends StatelessWidget {
  const _FadeIn({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: const Duration(milliseconds: 280),
      curve: Curves.easeOutCubic,
      builder: (context, value, child) => Opacity(
        opacity: value,
        child: Transform.translate(
          offset: Offset(0, (1 - value) * 10),
          child: child,
        ),
      ),
      child: child,
    );
  }
}

void _showResourceDetails(
  BuildContext context,
  _ResourceSpec spec,
  ResourceInfo resource,
) {
  Navigator.of(context).push(
    PageRouteBuilder<void>(
      opaque: false,
      barrierDismissible: true,
      barrierColor: Colors.black.withValues(alpha: 0.22),
      transitionDuration: const Duration(milliseconds: 360),
      reverseTransitionDuration: const Duration(milliseconds: 260),
      pageBuilder: (context, animation, secondaryAnimation) {
        return _ResourceDetailOverlay(spec: spec, resource: resource);
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
  const _ResourceDetailOverlay({required this.spec, required this.resource});

  final _ResourceSpec spec;
  final ResourceInfo resource;

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
                  tag: spec.heroTag,
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
                                Icon(
                                  spec.icon,
                                  color: PrettyColors.primary,
                                  size: 24,
                                ),
                                const SizedBox(width: 10),
                                Expanded(
                                  child: Text(
                                    spec.label,
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
                                  label: _resourceStatusLabel(
                                    context,
                                    resource,
                                  ),
                                  tone: resource.source == 'fallback'
                                      ? PrettyPillTone.warning
                                      : PrettyPillTone.primary,
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
                                    _ResourceDetailPreview(
                                      spec: spec,
                                      resource: resource,
                                    ),
                                    const SizedBox(height: 16),
                                    _ResourceDetailInfo(resource: resource),
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

class _ResourceDetailPreview extends StatelessWidget {
  const _ResourceDetailPreview({required this.spec, required this.resource});

  final _ResourceSpec spec;
  final ResourceInfo resource;

  @override
  Widget build(BuildContext context) {
    final file = resource.path == null ? null : File(resource.path!);
    final exists = file != null && file.existsSync();
    return switch (spec.kind) {
      _ResourceKind.image => ClipRRect(
        borderRadius: BorderRadius.circular(22),
        child: Container(
          constraints: const BoxConstraints(maxHeight: 460),
          color: PrettyColors.primarySofter.withValues(alpha: 0.42),
          child: exists
              ? Image.file(
                  file,
                  fit: BoxFit.contain,
                  errorBuilder: (context, error, stackTrace) =>
                      _LargeEmptyPreview(
                        icon: spec.icon,
                        label: context.t('previewFailed'),
                      ),
                )
              : _LargeEmptyPreview(
                  icon: spec.icon,
                  label: context.t('previewFailed'),
                ),
        ),
      ),
      _ResourceKind.audio => SizedBox(
        height: 240,
        child: AudioPreviewPanel(
          resource: resource,
          onConvertToCompatibleOgg: spec.onAudioConvert,
        ),
      ),
      _ResourceKind.songlist => _SonglistDetailPreview(
        resource: resource,
        scan: spec.scan,
      ),
    };
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
        color: PrettyColors.primarySofter.withValues(alpha: 0.52),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: PrettyColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            context.t('resource.structurePreview'),
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: PrettyColors.primaryDark,
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

class _ResourceDetailInfo extends StatelessWidget {
  const _ResourceDetailInfo({required this.resource});

  final ResourceInfo resource;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        InfoRow(label: context.t('source'), value: resource.source ?? '-'),
        InfoRow(
          label: context.t('size'),
          value: _formatBytes(resource.sizeBytes),
        ),
        InfoRow(
          label: context.t('resource.format'),
          value: _fileFormat(resource.name ?? resource.path),
        ),
        if (resource.width != null || resource.height != null)
          InfoRow(
            label: context.t('dimensions'),
            value: '${resource.width ?? '-'} x ${resource.height ?? '-'}',
          ),
        InfoRow(label: context.t('detailsPath'), value: resource.path ?? '-'),
      ],
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

Color _statusBoxColor(PrettyPillTone tone) => switch (tone) {
  PrettyPillTone.success => const Color(0xFFEAF8F2),
  PrettyPillTone.warning => const Color(0xFFFFF7E8),
  PrettyPillTone.danger => const Color(0xFFFFEEF3),
  PrettyPillTone.primary => PrettyColors.primarySofter,
  PrettyPillTone.neutral => PrettyColors.panelSoft,
};

Color _statusBorderColor(PrettyPillTone tone) => switch (tone) {
  PrettyPillTone.success => PrettyColors.success.withValues(alpha: 0.22),
  PrettyPillTone.warning => PrettyColors.warning.withValues(alpha: 0.24),
  PrettyPillTone.danger => PrettyColors.danger.withValues(alpha: 0.24),
  PrettyPillTone.primary => PrettyColors.borderStrong,
  PrettyPillTone.neutral => PrettyColors.border,
};

Color _statusAccentColor(PrettyPillTone tone) => switch (tone) {
  PrettyPillTone.success => PrettyColors.success,
  PrettyPillTone.warning => PrettyColors.warning,
  PrettyPillTone.danger => PrettyColors.danger,
  PrettyPillTone.primary => PrettyColors.primary,
  PrettyPillTone.neutral => PrettyColors.faint,
};

String _statusTitle(BuildContext context, SingleSongState page) {
  return switch (page.phase) {
    OperationPhase.idle => context.t('phase.idle'),
    OperationPhase.scanning => context.t('scan'),
    OperationPhase.scanned || OperationPhase.ready => context.t('convertible'),
    OperationPhase.saving => context.t('saveArcpkg'),
    OperationPhase.saved => context.t('saveComplete'),
    OperationPhase.failed => page.error ?? context.t('scanFailed'),
  };
}

String _statusDescription(BuildContext context, SingleSongState page) {
  return switch (page.phase) {
    OperationPhase.idle => context.t('single.status.idleMessage'),
    OperationPhase.scanning => context.t('scan'),
    OperationPhase.scanned ||
    OperationPhase.ready => context.t('single.status.readyMessage'),
    OperationPhase.saving => context.t('single.status.savingMessage'),
    OperationPhase.saved => context.t('single.status.savedMessage'),
    OperationPhase.failed =>
      page.error ?? context.t('single.status.failedMessage'),
  };
}

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
