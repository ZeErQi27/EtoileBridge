import 'package:flutter/material.dart';

enum AppPageId { singleSong, packEditor, characterEditor }

extension AppPageInfo on AppPageId {
  IconData get icon {
    switch (this) {
      case AppPageId.singleSong:
        return Icons.music_note_rounded;
      case AppPageId.packEditor:
        return Icons.library_music_rounded;
      case AppPageId.characterEditor:
        return Icons.person_rounded;
    }
  }

  String get i18nKey {
    switch (this) {
      case AppPageId.singleSong:
        return 'page.singleSong';
      case AppPageId.packEditor:
        return 'page.packEditor';
      case AppPageId.characterEditor:
        return 'page.characterEditor';
    }
  }

  String get subtitleKey {
    switch (this) {
      case AppPageId.singleSong:
        return 'subtitle.singleSong';
      case AppPageId.packEditor:
        return 'subtitle.packEditor';
      case AppPageId.characterEditor:
        return 'subtitle.characterEditor';
    }
  }
}
