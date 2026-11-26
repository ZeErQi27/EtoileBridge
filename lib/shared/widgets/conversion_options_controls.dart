import 'package:flutter/material.dart';

import '../../app/app_spacing.dart';
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
    this.enabled = true,
    super.key,
  });

  final PreprocessOptions value;
  final ValueChanged<PreprocessOptions> onChanged;
  final AppPageId pageId;
  final String actionPrefix;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final shape = RoundedSuperellipseBorder(
      borderRadius: BorderRadius.circular(AppRadius.workspaceControl),
    );
    return Tooltip(
      message: context.t('preprocess'),
      child: Material(
        color: scheme.surfaceContainer,
        shape: shape,
        child: SwitchListTile(
          dense: true,
          shape: shape,
          contentPadding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm),
          title: Text(
            context.t('preprocess.deleteDesignant'),
            style: Theme.of(context).textTheme.labelLarge,
          ),
          value: value.deleteDesignantLine,
          onChanged: enabled
              ? (next) => safeAction(
                  context,
                  id: '$actionPrefix.deleteDesignantLine',
                  label: 'deleteDesignantLine=$next',
                  page: pageId,
                  action: () =>
                      onChanged(PreprocessOptions(deleteDesignantLine: next)),
                )
              : null,
        ),
      ),
    );
  }
}

class WorkspaceExportActions extends StatelessWidget {
  const WorkspaceExportActions({
    required this.save,
    this.preprocess,
    super.key,
  });

  final Widget save;
  final Widget? preprocess;

  @override
  Widget build(BuildContext context) {
    final option = preprocess;
    if (option == null) return SizedBox(width: double.infinity, child: save);

    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth;
        final inline =
            width >= 418 && MediaQuery.textScalerOf(context).scale(14) <= 17;
        final saveWidth = inline ? (width * 0.42).clamp(164.0, 220.0) : width;
        return Flex(
          direction: inline ? Axis.horizontal : Axis.vertical,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            SizedBox(
              width: inline
                  ? (width - saveWidth - AppSpacing.sm).clamp(0.0, 300.0)
                  : width,
              child: option,
            ),
            SizedBox(
              width: inline ? AppSpacing.sm : 0,
              height: inline ? 0 : AppSpacing.xs,
            ),
            SizedBox(width: saveWidth, child: save),
          ],
        );
      },
    );
  }
}
