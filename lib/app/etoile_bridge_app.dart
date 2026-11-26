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

class EtoileBridgeApp extends StatefulWidget {
  const EtoileBridgeApp({required this.state, super.key});

  final AppState state;

  @override
  State<EtoileBridgeApp> createState() => _EtoileBridgeAppState();
}

class _EtoileBridgeAppState extends State<EtoileBridgeApp>
    with WidgetsBindingObserver {
  late Future<AndroidColorSchemes?> _dynamicColors;

  @override
  void initState() {
    super.initState();
    _dynamicColors = AndroidDynamicColors.load();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed &&
        defaultTargetPlatform == TargetPlatform.android) {
      setState(() => _dynamicColors = AndroidDynamicColors.load());
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    return FutureBuilder<AndroidColorSchemes?>(
      future: _dynamicColors,
      builder: (context, snapshot) {
        final light = EtoileTheme.build(
          Brightness.light,
          dynamicScheme: snapshot.data?.light,
        );
        final dark = EtoileTheme.build(
          Brightness.dark,
          dynamicScheme: snapshot.data?.dark,
        );
        return AppScope(
          state: state,
          child: AnimatedBuilder(
            animation: state,
            builder: (context, _) {
              return MaterialApp(
                title: 'EtoileBridge',
                debugShowCheckedModeBanner: false,
                theme: light,
                darkTheme: dark,
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
        );
      },
    );
  }
}
