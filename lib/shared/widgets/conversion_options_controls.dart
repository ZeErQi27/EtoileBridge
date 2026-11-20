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
    final scheme = Theme.of(context).colorScheme;
    final enabled = value.deleteDesignantLine;
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
    final radius = BorderRadius.circular(enabled ? 20 : 12);
    return AnimatedContainer(
      duration: reduceMotion
          ? Duration.zero
          : const Duration(milliseconds: 180),
      curve: Curves.easeOutCubic,
      decoration: BoxDecoration(
        color: enabled ? scheme.secondaryContainer : scheme.surfaceContainerLow,
        borderRadius: radius,
        border: Border.all(
          color: enabled ? Colors.transparent : scheme.outlineVariant,
        ),
      ),
      child: Material(
        color: Colors.transparent,
        child: SwitchListTile(
          shape: RoundedRectangleBorder(borderRadius: radius),
          contentPadding: const EdgeInsets.symmetric(horizontal: 12),
          title: Text(
            context.t('preprocess.deleteDesignant'),
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: enabled ? scheme.onSecondaryContainer : scheme.onSurface,
              fontWeight: FontWeight.w700,
            ),
          ),
          value: enabled,
          onChanged: (next) => safeAction(
            context,
            id: '$actionPrefix.deleteDesignantLine',
            label: 'deleteDesignantLine=$next',
            page: pageId,
            action: () =>
                onChanged(PreprocessOptions(deleteDesignantLine: next)),
          ),
        ),
      ),
    );
  }
}
