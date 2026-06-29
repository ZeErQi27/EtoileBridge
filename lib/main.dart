import 'dart:async';
import 'dart:ui';

import 'package:flutter/material.dart';

import 'app/app_state.dart';
import 'app/etoile_bridge_app.dart';
import 'core/logging/app_action_logger.dart';
import 'core/logging/app_error_logger.dart';

void main() {
  runZonedGuarded(
    () async {
      WidgetsFlutterBinding.ensureInitialized();
      await AppErrorLogger.initialize();
      await AppActionLogger.initialize();

      FlutterError.onError = (details) {
        FlutterError.presentError(details);
        AppErrorLogger.record(
          details.exception,
          details.stack ?? StackTrace.current,
          source: 'FlutterError',
        );
      };

      PlatformDispatcher.instance.onError = (error, stackTrace) {
        AppErrorLogger.record(error, stackTrace, source: 'PlatformDispatcher');
        return false;
      };

      runApp(EtoileBridgeApp(state: AppState.bootstrap()));
    },
    (error, stackTrace) {
      AppErrorLogger.record(error, stackTrace, source: 'runZonedGuarded');
    },
  );
}
