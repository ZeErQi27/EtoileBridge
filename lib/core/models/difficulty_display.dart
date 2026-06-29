class DifficultyDisplay {
  const DifficultyDisplay({
    required this.ratingClass,
    required this.name,
    required this.chartConstant,
    required this.isQuestionRating,
  });

  final int ratingClass;
  final String name;
  final double chartConstant;
  final bool isQuestionRating;

  static DifficultyDisplay resolve({
    required int ratingClass,
    String? difficulty,
    double? chartConstant,
    int? rating,
    bool? ratingPlus,
  }) {
    final question = isUnknownRating(
      rating: rating,
      chartConstant: chartConstant,
      difficulty: difficulty,
    );
    if (question) {
      return DifficultyDisplay(
        ratingClass: ratingClass,
        name: '${_baseDifficultyName(ratingClass, difficulty)} ?',
        chartConstant: 0,
        isQuestionRating: true,
      );
    }

    final resolvedConstant =
        chartConstant ?? _chartConstantFromRating(rating, ratingPlus) ?? 0;
    return DifficultyDisplay(
      ratingClass: ratingClass,
      name: _normalDifficultyName(
        ratingClass: ratingClass,
        difficulty: difficulty,
        rating: rating,
        ratingPlus: ratingPlus,
      ),
      chartConstant: resolvedConstant,
      isQuestionRating: false,
    );
  }

  static bool isUnknownRating({
    int? rating,
    double? chartConstant,
    String? difficulty,
  }) {
    if (_hasQuestionMarkDifficulty(difficulty)) return true;
    if (difficulty != null && _endsWithRating(difficulty)) return false;
    if (rating != null) return rating <= 0;
    return chartConstant == null || chartConstant <= 0;
  }

  static String labelForRatingClass(int ratingClass) {
    return switch (ratingClass) {
      0 => 'Past',
      1 => 'Present',
      2 => 'Future',
      3 => 'Beyond',
      4 => 'Eternal',
      _ => 'ratingClass $ratingClass',
    };
  }

  static String _normalDifficultyName({
    required int ratingClass,
    required String? difficulty,
    required int? rating,
    required bool? ratingPlus,
  }) {
    final trimmed = difficulty?.trim();
    if (trimmed != null && trimmed.isNotEmpty) {
      if (_hasQuestionMarkDifficulty(trimmed) || _endsWithRating(trimmed)) {
        return trimmed;
      }
      if (rating != null && rating > 0) {
        return '$trimmed $rating${ratingPlus == true ? '+' : ''}';
      }
      return trimmed;
    }
    final label = labelForRatingClass(ratingClass);
    if (rating == null || rating <= 0) return label;
    return '$label $rating${ratingPlus == true ? '+' : ''}';
  }

  static String _baseDifficultyName(int ratingClass, String? difficulty) {
    var base = difficulty?.trim();
    if (base == null || base.isEmpty) return labelForRatingClass(ratingClass);
    base = base.replaceFirst(RegExp(r'\s*\?\s*$'), '');
    base = base.replaceFirst(RegExp(r'\s+-?\d+(?:\.\d+)?\+?\s*$'), '');
    return base.trim().isEmpty ? labelForRatingClass(ratingClass) : base.trim();
  }

  static bool _hasQuestionMarkDifficulty(String? difficulty) {
    return difficulty?.trim().endsWith('?') == true;
  }

  static bool _endsWithRating(String difficulty) {
    return RegExp(r'\s+\d+(?:\.\d+)?\+?\s*$').hasMatch(difficulty.trim());
  }

  static double? _chartConstantFromRating(int? rating, bool? ratingPlus) {
    if (rating == null || rating <= 0) return null;
    return rating + (ratingPlus == true ? 0.7 : 0);
  }
}
