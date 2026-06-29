import 'character_result_assets.dart';
import 'character_result_preview_mapper.dart';

enum CharacterResultLayerPhase { background, foreground }

class CharacterResultTextureLayer {
  const CharacterResultTextureLayer({
    required this.id,
    required this.assetPath,
    required this.logicalBounds,
    required this.phase,
    required this.source,
  });

  final String id;
  final String assetPath;
  final CharacterPreviewBounds logicalBounds;
  final CharacterResultLayerPhase phase;
  final String source;
}

class CharacterResultLayout {
  CharacterResultLayout._();

  static const profileName =
      'ArcCreateResultLayout / AndroidPortedTextureStack';
  static const referenceWidth = 1920.0;
  static const referenceHeight = 1080.0;

  static final ArcCreateResolvedRect root = ArcCreateResolvedRect.root(
    referenceWidth,
    referenceHeight,
  );

  // Result.unity:1205-1224, BackgroundArrow.
  static const backgroundArrow = ArcCreateRectTransformSnapshot(
    anchorMin: CharacterPreviewPoint(0, 0),
    anchorMax: CharacterPreviewPoint(1, 1),
    anchoredPosition: CharacterPreviewPoint(354.68176, 0),
    sizeDelta: CharacterPreviewPoint(0, 0),
    pivot: CharacterPreviewPoint(0.5, 0.5),
  );

  // Result.unity:5101-5125, Jacket.
  static const jacketParent = ArcCreateRectTransformSnapshot(
    anchorMin: CharacterPreviewPoint(0.5, 1),
    anchorMax: CharacterPreviewPoint(0.5, 1),
    anchoredPosition: CharacterPreviewPoint(0, -541),
    sizeDelta: CharacterPreviewPoint(100, 100),
    pivot: CharacterPreviewPoint(0.5, 0.5),
    localScale: CharacterPreviewPoint(1.3001714, 1.3001714),
  );

  // Result.unity:5689-5709, JacketFrame.
  static const jacketFrame = ArcCreateRectTransformSnapshot(
    anchorMin: CharacterPreviewPoint(0.5, 0.5),
    anchorMax: CharacterPreviewPoint(0.5, 0.5),
    anchoredPosition: CharacterPreviewPoint(0, -0.000091552734),
    sizeDelta: CharacterPreviewPoint(800, 800),
    pivot: CharacterPreviewPoint(0.5, 0.5),
  );

  // Result.unity:6941-6969, ScoreFrame.
  static const scoreFrameParent = ArcCreateRectTransformSnapshot(
    anchorMin: CharacterPreviewPoint(0.5, 0),
    anchorMax: CharacterPreviewPoint(0.5, 0),
    anchoredPosition: CharacterPreviewPoint(0, 439.99908),
    sizeDelta: CharacterPreviewPoint(100, 100),
    pivot: CharacterPreviewPoint(0.5, 0.5),
  );

  // Result.unity:5768-5786, ScoreFrame/Background.
  static const scoreBackground = ArcCreateRectTransformSnapshot(
    anchorMin: CharacterPreviewPoint(0.5, 0),
    anchorMax: CharacterPreviewPoint(0.5, 0),
    anchoredPosition: CharacterPreviewPoint(20, -490),
    sizeDelta: CharacterPreviewPoint(1105, 270),
    pivot: CharacterPreviewPoint(0.5, 0),
  );

  // Result.unity:5597-5617, ClearResult.
  static const clearResult = ArcCreateRectTransformSnapshot(
    anchorMin: CharacterPreviewPoint(0.5, 0.5),
    anchorMax: CharacterPreviewPoint(0.5, 0.5),
    anchoredPosition: CharacterPreviewPoint(0, -335),
    sizeDelta: CharacterPreviewPoint(700, 182.7),
    pivot: CharacterPreviewPoint(0.5, 0.5),
    localScale: CharacterPreviewPoint(1.7003375, 1.7003375),
  );

  // Result.unity:6707-6727 plus old Android default-position correction.
  static const judgementFrame = ArcCreateRectTransformSnapshot(
    anchorMin: CharacterPreviewPoint(0.56, 0),
    anchorMax: CharacterPreviewPoint(0.95, 1),
    anchoredPosition: CharacterPreviewPoint(137.50003, 40),
    sizeDelta: CharacterPreviewPoint(-183, -560),
    pivot: CharacterPreviewPoint(0.5, 0.5),
  );

  // Result.unity:2977-3003, JudgementTable.
  static const judgementTable = ArcCreateRectTransformSnapshot(
    anchorMin: CharacterPreviewPoint(0, 0),
    anchorMax: CharacterPreviewPoint(0, 0),
    anchoredPosition: CharacterPreviewPoint(0, 0),
    sizeDelta: CharacterPreviewPoint(0, 0),
    pivot: CharacterPreviewPoint(0.5, 0.5),
  );

  // Result.unity:5519-5538, JudgementTableHighlight.
  static const judgementHighlight = ArcCreateRectTransformSnapshot(
    anchorMin: CharacterPreviewPoint(0, 0),
    anchorMax: CharacterPreviewPoint(0.916, 1),
    anchoredPosition: CharacterPreviewPoint(0, 0),
    sizeDelta: CharacterPreviewPoint(0, 0),
    pivot: CharacterPreviewPoint(0.5, 0.5),
  );

  // Result.unity:2299-2304, visible PlayRetryTable texture slot.
  static const playRetryTable = ArcCreateRectTransformSnapshot(
    anchorMin: CharacterPreviewPoint(0.27, -0.2),
    anchorMax: CharacterPreviewPoint(0.93, -0.2),
    anchoredPosition: CharacterPreviewPoint(0, 0),
    sizeDelta: CharacterPreviewPoint(0, 60),
    pivot: CharacterPreviewPoint(1, 0.5),
  );

  // Result.unity:2511-2530, Highlights under PlayRetryTable.
  static const playRetryHighlight = ArcCreateRectTransformSnapshot(
    anchorMin: CharacterPreviewPoint(0.55, -0.015),
    anchorMax: CharacterPreviewPoint(1, 1.02),
    anchoredPosition: CharacterPreviewPoint(8, 0),
    sizeDelta: CharacterPreviewPoint(8, 0),
    pivot: CharacterPreviewPoint(1, 0.5),
  );

  static final ArcCreateResolvedRect _jacketResolved = jacketParent.resolve(
    root,
  );
  static final ArcCreateResolvedRect _scoreResolved = scoreFrameParent.resolve(
    root,
  );
  static final ArcCreateResolvedRect _judgementResolved = judgementFrame
      .resolve(root);
  static final ArcCreateResolvedRect _playRetryResolved = playRetryTable
      .resolve(_judgementResolved);

  static CharacterPreviewBounds _preview(ArcCreateResolvedRect rect) =>
      rect.toPreviewBounds(referenceHeight);

  static CharacterPreviewBounds get jacketBounds =>
      _preview(jacketFrame.resolve(_jacketResolved));

  static CharacterPreviewBounds get jacketInnerBounds {
    final jacket = jacketBounds;
    return CharacterPreviewBounds(
      left: jacket.left + jacket.width * 0.25,
      top: jacket.top + jacket.height * 0.25,
      right: jacket.right - jacket.width * 0.25,
      bottom: jacket.bottom - jacket.height * 0.25,
    );
  }

  static List<CharacterResultTextureLayer> get backgroundLayers => [
    CharacterResultTextureLayer(
      id: 'BackgroundArrow',
      assetPath: CharacterResultAssets.backgroundArrow,
      logicalBounds: _preview(backgroundArrow.resolve(root)),
      phase: CharacterResultLayerPhase.background,
      source: 'Result.unity:1205-1224; Android backgroundArrowRect',
    ),
    CharacterResultTextureLayer(
      id: 'ClearResult',
      assetPath: CharacterResultAssets.clearGlow,
      logicalBounds: _preview(clearResult.resolve(_scoreResolved)),
      phase: CharacterResultLayerPhase.background,
      source: 'Result.unity:5597-5617; Android clearGlowRect',
    ),
  ];

  static List<CharacterResultTextureLayer> get foregroundLayers => [
    CharacterResultTextureLayer(
      id: 'JacketFrame',
      assetPath: CharacterResultAssets.jacketBackground,
      logicalBounds: jacketBounds,
      phase: CharacterResultLayerPhase.foreground,
      source: 'Result.unity:5689-5709; Android jacketRect',
    ),
    CharacterResultTextureLayer(
      id: 'ScoreBackground',
      assetPath: CharacterResultAssets.scoreFrame,
      logicalBounds: _preview(scoreBackground.resolve(_scoreResolved)),
      phase: CharacterResultLayerPhase.foreground,
      source: 'Result.unity:5768-5786; Android bottomScoreRect',
    ),
    CharacterResultTextureLayer(
      id: 'JudgementTable',
      assetPath: CharacterResultAssets.judgementTable,
      logicalBounds: _preview(_judgementResolved),
      phase: CharacterResultLayerPhase.foreground,
      source: 'Result.unity:6707-6727; Android resultPanelRect',
    ),
    CharacterResultTextureLayer(
      id: 'JudgementTableHighlight',
      assetPath: CharacterResultAssets.judgementTableHighlight,
      logicalBounds: _preview(judgementHighlight.resolve(_judgementResolved)),
      phase: CharacterResultLayerPhase.foreground,
      source: 'Result.unity:5519-5538; Android judgementHighlightRect',
    ),
    CharacterResultTextureLayer(
      id: 'PlayRetryTable',
      assetPath: CharacterResultAssets.playRetryBackground,
      logicalBounds: _preview(_playRetryResolved),
      phase: CharacterResultLayerPhase.foreground,
      source: 'Result.unity:2299-2304; Android playRetryRect',
    ),
    CharacterResultTextureLayer(
      id: 'PlayRetryHighlight',
      assetPath: CharacterResultAssets.playRetryFrame,
      logicalBounds: _preview(playRetryHighlight.resolve(_playRetryResolved)),
      phase: CharacterResultLayerPhase.foreground,
      source: 'Result.unity:2511-2530; Android playRetryHighlightRect',
    ),
  ];

  static List<CharacterResultTextureLayer> get allLayers => [
    ...backgroundLayers,
    ...foregroundLayers,
  ];
}
