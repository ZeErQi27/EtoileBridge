import 'dart:async';

import 'package:flutter/material.dart';

import '../core/logging/app_action_logger.dart';
import '../core/logging/app_error_logger.dart';
import 'app_state.dart';
import 'routes.dart';

Future<void> safeAction(
  BuildContext context, {
  required String id,
  required String label,
  required FutureOr<void> Function() action,
  AppPageId? page,
  Duration logThrottle = Duration.zero,
}) async {
  final state = AppScope.of(context);
  final currentPage = page ?? state.currentPage;
  final before = state.debugSummary();
  AppActionLogger.write(
    id: id,
    label: label,
    page: currentPage.name,
    phase: 'start',
    before: before,
    throttle: logThrottle,
  );
  try {
    await action();
    AppActionLogger.write(
      id: id,
      label: label,
      page: currentPage.name,
      phase: 'end',
      before: before,
      after: state.debugSummary(),
      throttle: logThrottle,
    );
  } catch (error, stackTrace) {
    AppActionLogger.write(
      id: id,
      label: label,
      page: currentPage.name,
      phase: 'error',
      before: before,
      after: state.debugSummary(),
      error: error,
      stackTrace: stackTrace,
    );
    AppErrorLogger.record(error, stackTrace, source: 'safeAction:$id');
    FlutterError.reportError(
      FlutterErrorDetails(
        exception: error,
        stack: stackTrace,
        library: 'EtoileBridge safeAction',
        context: ErrorDescription(id),
      ),
    );
  }
}
