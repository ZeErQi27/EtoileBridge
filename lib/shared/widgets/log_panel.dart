import 'package:flutter/material.dart';

import '../../core/i18n/app_strings.dart';
import '../../core/models/operation_models.dart';

class LogPanel extends StatelessWidget {
  const LogPanel({required this.logs, super.key});

  final List<LogLine> logs;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (logs.isEmpty) {
      return Text('-', style: theme.textTheme.bodyMedium);
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: logs.map((line) {
        final color = line.isError
            ? theme.colorScheme.error
            : line.isWarning
            ? Colors.orange.shade700
            : theme.colorScheme.onSurfaceVariant;
        return Padding(
          padding: const EdgeInsets.only(bottom: 6),
          child: Text(
            context.t(line.message),
            style: theme.textTheme.bodySmall?.copyWith(color: color),
          ),
        );
      }).toList(),
    );
  }
}
