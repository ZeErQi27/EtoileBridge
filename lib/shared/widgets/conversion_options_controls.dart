import 'package:flutter/material.dart';

import '../../app/routes.dart';
import '../../app/safe_action.dart';
import '../../core/i18n/app_strings.dart';
import '../../core/models/conversion_options.dart';

class PreprocessOptionsPanel extends StatelessWidget {
  const PreprocessOptionsPanel({
    required this.value,
    required this.onChanged,
    required this.pageId,
    required this.actionPrefix,
    super.key,
  });

  final PreprocessOptions value;
  final ValueChanged<PreprocessOptions> onChanged;
  final AppPageId pageId;
  final String actionPrefix;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        _PreprocessToggle(
          label: context.t('preprocess.deleteDesignant'),
          value: value.deleteDesignantLine,
          onChanged: (enabled) => _update(
            context,
            'deleteDesignantLine',
            value.copyWith(deleteDesignantLine: enabled),
          ),
        ),
        _PreprocessToggle(
          label: context.t('preprocess.fixZeroArcTap'),
          value: value.fixZeroDurationArcTap,
          onChanged: (enabled) => _update(
            context,
            'fixZeroDurationArcTap',
            value.copyWith(fixZeroDurationArcTap: enabled),
          ),
        ),
        _PreprocessToggle(
          label: context.t('preprocess.fixReversedArc'),
          value: value.fixReversedArcTime,
          onChanged: (enabled) => _update(
            context,
            'fixReversedArcTime',
            value.copyWith(fixReversedArcTime: enabled),
          ),
        ),
        _PreprocessToggle(
          label: context.t('preprocess.expandTiminggroup'),
          value: value.expandArcResolution,
          onChanged: (enabled) => _update(
            context,
            'expandArcResolution',
            value.copyWith(expandArcResolution: enabled),
          ),
        ),
      ],
    );
  }

  void _update(BuildContext context, String field, PreprocessOptions next) {
    safeAction(
      context,
      id: '$actionPrefix.$field',
      label: '$field=${next.toJson()[field]}',
      page: pageId,
      action: () => onChanged(next),
    );
  }
}

class _PreprocessToggle extends StatelessWidget {
  const _PreprocessToggle({
    required this.label,
    required this.value,
    required this.onChanged,
  });

  final String label;
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Material(
        color: scheme.secondaryContainer.withValues(alpha: 0.36),
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: () => onChanged(!value),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: Row(
              children: [
                Checkbox(
                  value: value,
                  visualDensity: VisualDensity.compact,
                  onChanged: (enabled) => onChanged(enabled == true),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    label,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: scheme.onSurface,
                      fontWeight: FontWeight.w700,
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
