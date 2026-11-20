class ArcCreateAppearanceOptions {
  const ArcCreateAppearanceOptions({
    this.side = 'LIGHT',
    this.note = 'INHERIT',
    this.particle = 'INHERIT',
    this.accent = 'INHERIT',
    this.track = 'INHERIT',
    this.singleLine = 'NONE',
  });

  final String side;
  final String note;
  final String particle;
  final String accent;
  final String track;
  final String singleLine;

  Map<String, Object?> toJson() => {
    'side': side,
    'note': note,
    'particle': particle,
    'accent': accent,
    'track': track,
    'singleLine': singleLine,
  };

  ArcCreateAppearanceOptions copyWith({
    String? side,
    String? note,
    String? particle,
    String? accent,
    String? track,
    String? singleLine,
  }) {
    return ArcCreateAppearanceOptions(
      side: side ?? this.side,
      note: note ?? this.note,
      particle: particle ?? this.particle,
      accent: accent ?? this.accent,
      track: track ?? this.track,
      singleLine: singleLine ?? this.singleLine,
    );
  }
}

class PreprocessOptions {
  const PreprocessOptions({this.deleteDesignantLine = true});

  const PreprocessOptions.disabled() : deleteDesignantLine = false;

  final bool deleteDesignantLine;

  Map<String, Object?> toJson() => {'deleteDesignantLine': deleteDesignantLine};
}

class ConversionOptionChoice {
  const ConversionOptionChoice(this.value, this.labelKey, {this.shortLabelKey});

  final String value;
  final String labelKey;
  final String? shortLabelKey;
}

const sideAppearanceChoices = [
  ConversionOptionChoice('LIGHT', 'appearance.option.light'),
  ConversionOptionChoice('CONFLICT', 'appearance.option.conflict'),
  ConversionOptionChoice('COLORLESS', 'appearance.option.colorless'),
];

const noteAppearanceChoices = [
  ConversionOptionChoice('INHERIT', 'appearance.option.inherit'),
  ConversionOptionChoice('LIGHT', 'appearance.option.light'),
  ConversionOptionChoice('CONFLICT', 'appearance.option.conflict'),
];

const particleAppearanceChoices = [
  ConversionOptionChoice('INHERIT', 'appearance.option.inherit'),
  ConversionOptionChoice('LIGHT', 'appearance.option.light'),
  ConversionOptionChoice('CONFLICT', 'appearance.option.conflict'),
  ConversionOptionChoice('MIRAI_LIGHT', 'appearance.option.miraiLight'),
  ConversionOptionChoice('MIRAI_CONFLICT', 'appearance.option.miraiConflict'),
  ConversionOptionChoice('COLORLESS', 'appearance.option.colorless'),
];

const accentAppearanceChoices = [
  ConversionOptionChoice('INHERIT', 'appearance.option.inherit'),
  ConversionOptionChoice('LIGHT', 'appearance.option.light'),
  ConversionOptionChoice('CONFLICT', 'appearance.option.conflict'),
  ConversionOptionChoice('DYNAMIX', 'appearance.option.dynamix'),
  ConversionOptionChoice('COLORLESS', 'appearance.option.colorless'),
];

const trackAppearanceChoices = [
  ConversionOptionChoice('INHERIT', 'appearance.option.inherit'),
  ConversionOptionChoice('LIGHT', 'appearance.option.light'),
  ConversionOptionChoice('CONFLICT', 'appearance.option.conflict'),
  ConversionOptionChoice('BLACK', 'appearance.option.black'),
  ConversionOptionChoice('NIJUUSEI', 'appearance.option.nijuusei'),
  ConversionOptionChoice('REI', 'appearance.option.rei'),
  ConversionOptionChoice('DARK_VS', 'appearance.option.darkVs'),
  ConversionOptionChoice('TEMPEST', 'appearance.option.tempest'),
  ConversionOptionChoice('FINALE', 'appearance.option.finale'),
  ConversionOptionChoice('PENTIMENT', 'appearance.option.pentiment'),
  ConversionOptionChoice('ARCANA', 'appearance.option.arcana'),
  ConversionOptionChoice('COLORLESS', 'appearance.option.colorless'),
];

const singleLineAppearanceChoices = [
  ConversionOptionChoice('NONE', 'appearance.option.none'),
  ConversionOptionChoice('LIGHT', 'appearance.option.light'),
  ConversionOptionChoice('CONFLICT', 'appearance.option.conflict'),
  ConversionOptionChoice('NEO', 'appearance.option.neo'),
];
