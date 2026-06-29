import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import '../core/i18n/app_strings.dart';
import '../features/character_editor/character_editor_page.dart';
import '../features/pack_editor/pack_editor_page.dart';
import '../features/single_song/single_song_page.dart';
import '../shared/widgets/app_shell.dart';
import 'android_dynamic_colors.dart';
import 'app_state.dart';
import 'theme.dart';

class EtoileBridgeApp extends StatelessWidget {
  const EtoileBridgeApp({required this.state, super.key});

  final AppState state;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<ColorScheme?>(
      future: AndroidDynamicColors.lightScheme(),
      builder: (context, snapshot) => AppScope(
        state: state,
        child: AnimatedBuilder(
          animation: state,
          builder: (context, _) {
            return MaterialApp(
              title: 'EtoileBridge',
              debugShowCheckedModeBanner: false,
              theme: EtoileTheme.light(dynamicColorScheme: snapshot.data),
              locale: state.locale,
              supportedLocales: AppStrings.supportedLocales,
              localizationsDelegates: const [
                GlobalMaterialLocalizations.delegate,
                GlobalCupertinoLocalizations.delegate,
                GlobalWidgetsLocalizations.delegate,
              ],
              home: defaultTargetPlatform == TargetPlatform.windows
                  ? const ExcludeSemantics(
                      child: AppShell(
                        pages: [
                          SingleSongPage(),
                          PackEditorPage(),
                          CharacterEditorPage(),
                        ],
                      ),
                    )
                  : const AppShell(
                      pages: [
                        SingleSongPage(),
                        PackEditorPage(),
                        CharacterEditorPage(),
                      ],
                    ),
            );
          },
        ),
      ),
    );
  }
}
