import 'dart:math' as math;

class CharacterPreviewPoint {
  const CharacterPreviewPoint(this.x, this.y);

  final double x;
  final double y;

  CharacterPreviewPoint operator +(CharacterPreviewPoint other) =>
      CharacterPreviewPoint(x + other.x, y + other.y);

  CharacterPreviewPoint operator -(CharacterPreviewPoint other) =>
      CharacterPreviewPoint(x - other.x, y - other.y);

  CharacterPreviewPoint operator *(double value) =>
      CharacterPreviewPoint(x * value, y * value);
}

class CharacterPreviewBounds {
  const CharacterPreviewBounds({
    required this.left,
    required this.top,
    required this.right,
    required this.bottom,
  });

  final double left;
  final double top;
  final double right;
  final double bottom;

  double get width => right - left;
  double get height => bottom - top;
  bool get isEmpty => width <= 0 || height <= 0;

  CharacterPreviewBounds scaleAndTranslate({
    required double scale,
    required double dx,
    required double dy,
  }) {
    return CharacterPreviewBounds(
      left: dx + left * scale,
      top: dy + top * scale,
      right: dx + right * scale,
      bottom: dy + bottom * scale,
    );
  }

  CharacterPreviewBounds? intersect(CharacterPreviewBounds other) {
    final nextLeft = math.max(left, other.left);
    final nextTop = math.max(top, other.top);
    final nextRight = math.min(right, other.right);
    final nextBottom = math.min(bottom, other.bottom);
    if (nextRight <= nextLeft || nextBottom <= nextTop) {
      return null;
    }
    return CharacterPreviewBounds(
      left: nextLeft,
      top: nextTop,
      right: nextRight,
      bottom: nextBottom,
    );
  }

  String format() =>
      'left=${left.toStringAsFixed(1)}, top=${top.toStringAsFixed(1)}, '
      'right=${right.toStringAsFixed(1)}, bottom=${bottom.toStringAsFixed(1)}, '
      'w=${width.toStringAsFixed(1)}, h=${height.toStringAsFixed(1)}';
}

class ArcCreateRectTransformSnapshot {
  const ArcCreateRectTransformSnapshot({
    required this.anchorMin,
    required this.anchorMax,
    required this.anchoredPosition,
    required this.sizeDelta,
    required this.pivot,
    this.localScale = const CharacterPreviewPoint(1, 1),
  });

  final CharacterPreviewPoint anchorMin;
  final CharacterPreviewPoint anchorMax;
  final CharacterPreviewPoint anchoredPosition;
  final CharacterPreviewPoint sizeDelta;
  final CharacterPreviewPoint pivot;
  final CharacterPreviewPoint localScale;

  ArcCreateRectTransformSnapshot withAnchoredPosition(
    CharacterPreviewPoint value,
  ) {
    return ArcCreateRectTransformSnapshot(
      anchorMin: anchorMin,
      anchorMax: anchorMax,
      anchoredPosition: value,
      sizeDelta: sizeDelta,
      pivot: pivot,
      localScale: localScale,
    );
  }

  ArcCreateRectTransformSnapshot withSizeDelta(CharacterPreviewPoint value) {
    return ArcCreateRectTransformSnapshot(
      anchorMin: anchorMin,
      anchorMax: anchorMax,
      anchoredPosition: anchoredPosition,
      sizeDelta: value,
      pivot: pivot,
      localScale: localScale,
    );
  }

  ArcCreateRectTransformSnapshot withLocalScale(double value) {
    return ArcCreateRectTransformSnapshot(
      anchorMin: anchorMin,
      anchorMax: anchorMax,
      anchoredPosition: anchoredPosition,
      sizeDelta: sizeDelta,
      pivot: pivot,
      localScale: CharacterPreviewPoint(value, value),
    );
  }

  ArcCreateResolvedRect resolve(ArcCreateResolvedRect parent) {
    // Ported from old Android CharacterPreviewCoordinateMapper.kt.
    // This resolves Unity RectTransform anchors through nested parents,
    // including parent pivot, local size, and local scale. The earlier
    // simplified Flutter resolver drifted on nested Result.unity nodes.
    final parentLocalWidth = parent.resolvedLocalWidth;
    final parentLocalHeight = parent.resolvedLocalHeight;
    final parentPivotX = parent.resolvedPivotX;
    final parentPivotY = parent.resolvedPivotY;
    final localWidth =
        parentLocalWidth * (anchorMax.x - anchorMin.x) + sizeDelta.x;
    final localHeight =
        parentLocalHeight * (anchorMax.y - anchorMin.y) + sizeDelta.y;
    final anchorReferenceX =
        parentLocalWidth * anchorMin.x +
        parentLocalWidth * (anchorMax.x - anchorMin.x) * pivot.x +
        anchoredPosition.x -
        parentLocalWidth * parent.pivotFractionX;
    final anchorReferenceY =
        parentLocalHeight * anchorMin.y +
        parentLocalHeight * (anchorMax.y - anchorMin.y) * pivot.y +
        anchoredPosition.y -
        parentLocalHeight * parent.pivotFractionY;
    final pivotWorld = CharacterPreviewPoint(
      parentPivotX + anchorReferenceX * parent.scaleX,
      parentPivotY + anchorReferenceY * parent.scaleY,
    );
    final worldScaleX = parent.scaleX * localScale.x;
    final worldScaleY = parent.scaleY * localScale.y;
    final renderedWidth = localWidth * worldScaleX;
    final renderedHeight = localHeight * worldScaleY;
    return ArcCreateResolvedRect(
      left: pivotWorld.x - renderedWidth * pivot.x,
      right: pivotWorld.x + renderedWidth * (1 - pivot.x),
      bottom: pivotWorld.y - renderedHeight * pivot.y,
      top: pivotWorld.y + renderedHeight * (1 - pivot.y),
      pivotX: pivotWorld.x,
      pivotY: pivotWorld.y,
      localWidth: localWidth,
      localHeight: localHeight,
      pivotFractionX: pivot.x,
      pivotFractionY: pivot.y,
      scaleX: worldScaleX,
      scaleY: worldScaleY,
    );
  }
}

class ArcCreateResultRectSnapshot {
  const ArcCreateResultRectSnapshot({required this.transform, this.parent});

  final ArcCreateRectTransformSnapshot transform;
  final ArcCreateRectTransformSnapshot? parent;

  CharacterPreviewBounds resolvePreviewBounds({
    required ArcCreateResolvedRect root,
    required double referenceHeight,
  }) {
    final parentRect = parent?.resolve(root) ?? root;
    return transform.resolve(parentRect).toPreviewBounds(referenceHeight);
  }
}

class ArcCreateResultLayoutSnapshot {
  const ArcCreateResultLayoutSnapshot({
    required this.referenceWidth,
    required this.referenceHeight,
    required this.characterParent,
    required this.characterImage,
    required this.characterParentFinalAnchoredPosition,
    required this.backgroundArrowRect,
    required this.jacketBackgroundRect,
    required this.scoreFrameRect,
    required this.judgementTableRect,
    required this.judgementHighlightRect,
    required this.playRetryBackgroundRect,
    required this.playRetryFrameRect,
    required this.clearGlowRect,
    required this.profileName,
  });

  final double referenceWidth;
  final double referenceHeight;
  final ArcCreateRectTransformSnapshot characterParent;
  final ArcCreateRectTransformSnapshot characterImage;
  final CharacterPreviewPoint characterParentFinalAnchoredPosition;
  final ArcCreateResultRectSnapshot backgroundArrowRect;
  final ArcCreateResultRectSnapshot jacketBackgroundRect;
  final ArcCreateResultRectSnapshot scoreFrameRect;
  final ArcCreateResultRectSnapshot judgementTableRect;
  final ArcCreateResultRectSnapshot judgementHighlightRect;
  final ArcCreateResultRectSnapshot playRetryBackgroundRect;
  final ArcCreateResultRectSnapshot playRetryFrameRect;
  final ArcCreateResultRectSnapshot clearGlowRect;
  final String profileName;

  ArcCreateResolvedRect get root => ArcCreateResolvedRect(
    left: 0,
    right: referenceWidth,
    bottom: 0,
    top: referenceHeight,
    pivotX: 0,
    pivotY: 0,
    localWidth: referenceWidth,
    localHeight: referenceHeight,
    pivotFractionX: 0,
    pivotFractionY: 0,
    scaleX: 1,
    scaleY: 1,
  );
}

class ArcCreateResultLayoutSnapshots {
  const ArcCreateResultLayoutSnapshots._();

  static const resultScreen = ArcCreateResultLayoutSnapshot(
    // ArcCreate Result.unity CanvasScaler:
    // ReferenceResolution = 1920 x 1080, MatchWidthOrHeight = 0.75.
    referenceWidth: 1920,
    referenceHeight: 1080,
    // ArcCreate Result.unity CharacterParent raw anchoredPosition is
    // x=-340.31818, y=-365. The old Android EtoileBridge mapper uses
    // the final in-game result position x=-640.3182, y=-365 after
    // ArcCreate result scene positioning/animation settles.
    characterParent: ArcCreateRectTransformSnapshot(
      anchorMin: CharacterPreviewPoint(0.5, 0.5),
      anchorMax: CharacterPreviewPoint(0.5, 0.5),
      anchoredPosition: CharacterPreviewPoint(-640.3182, -365),
      sizeDelta: CharacterPreviewPoint(0, 0),
      pivot: CharacterPreviewPoint(0.5, 0.5),
    ),
    // ArcCreate ResultScreen.cs: SetSizeWithCurrentAnchors vertical=2048,
    // horizontal=2048 * imageAspect, pivot is Sprite.Create(0.5, 0.5).
    characterImage: ArcCreateRectTransformSnapshot(
      anchorMin: CharacterPreviewPoint(0.5, 0.5),
      anchorMax: CharacterPreviewPoint(0.5, 0.5),
      anchoredPosition: CharacterPreviewPoint(0, 0),
      sizeDelta: CharacterPreviewPoint(1152, 2048),
      pivot: CharacterPreviewPoint(0.5, 0.5),
    ),
    characterParentFinalAnchoredPosition: CharacterPreviewPoint(
      -640.3182,
      -365,
    ),
    backgroundArrowRect: ArcCreateResultRectSnapshot(
      transform: ArcCreateRectTransformSnapshot(
        anchorMin: CharacterPreviewPoint(0, 0),
        anchorMax: CharacterPreviewPoint(1, 1),
        anchoredPosition: CharacterPreviewPoint(354.68176, 0),
        sizeDelta: CharacterPreviewPoint(0, 0),
        pivot: CharacterPreviewPoint(0.5, 0.5),
      ),
    ),
    jacketBackgroundRect: ArcCreateResultRectSnapshot(
      transform: ArcCreateRectTransformSnapshot(
        anchorMin: CharacterPreviewPoint(0.5, 1),
        anchorMax: CharacterPreviewPoint(0.5, 1),
        anchoredPosition: CharacterPreviewPoint(0, -541),
        sizeDelta: CharacterPreviewPoint(100, 100),
        pivot: CharacterPreviewPoint(0.5, 0.5),
        localScale: CharacterPreviewPoint(1.3001714, 1.3001714),
      ),
      parent: ArcCreateRectTransformSnapshot(
        anchorMin: CharacterPreviewPoint(0, 0),
        anchorMax: CharacterPreviewPoint(1, 1),
        anchoredPosition: CharacterPreviewPoint(0, 0),
        sizeDelta: CharacterPreviewPoint(0, 0),
        pivot: CharacterPreviewPoint(0.5, 0.5),
      ),
    ),
    scoreFrameRect: ArcCreateResultRectSnapshot(
      transform: ArcCreateRectTransformSnapshot(
        anchorMin: CharacterPreviewPoint(0.5, 0),
        anchorMax: CharacterPreviewPoint(0.5, 0),
        anchoredPosition: CharacterPreviewPoint(0, 439.99908),
        sizeDelta: CharacterPreviewPoint(100, 100),
        pivot: CharacterPreviewPoint(0.5, 0.5),
      ),
    ),
    clearGlowRect: ArcCreateResultRectSnapshot(
      transform: ArcCreateRectTransformSnapshot(
        anchorMin: CharacterPreviewPoint(0.5, 0.5),
        anchorMax: CharacterPreviewPoint(0.5, 0.5),
        anchoredPosition: CharacterPreviewPoint(0, -335),
        sizeDelta: CharacterPreviewPoint(700, 182.7),
        pivot: CharacterPreviewPoint(0.5, 0.5),
        localScale: CharacterPreviewPoint(1.7003375, 1.7003375),
      ),
      parent: ArcCreateRectTransformSnapshot(
        anchorMin: CharacterPreviewPoint(0.5, 0),
        anchorMax: CharacterPreviewPoint(0.5, 0),
        anchoredPosition: CharacterPreviewPoint(0, 439.99908),
        sizeDelta: CharacterPreviewPoint(100, 100),
        pivot: CharacterPreviewPoint(0.5, 0.5),
      ),
    ),
    judgementTableRect: ArcCreateResultRectSnapshot(
      transform: ArcCreateRectTransformSnapshot(
        anchorMin: CharacterPreviewPoint(0.5, 0.5),
        anchorMax: CharacterPreviewPoint(0.5, 0.5),
        anchoredPosition: CharacterPreviewPoint(0, 0),
        sizeDelta: CharacterPreviewPoint(100, 100),
        pivot: CharacterPreviewPoint(0.5, 0.5),
      ),
      parent: ArcCreateRectTransformSnapshot(
        anchorMin: CharacterPreviewPoint(0.56, 0),
        anchorMax: CharacterPreviewPoint(0.95, 1),
        anchoredPosition: CharacterPreviewPoint(337.5, 40),
        sizeDelta: CharacterPreviewPoint(-183, -560),
        pivot: CharacterPreviewPoint(0.5, 0.5),
      ),
    ),
    judgementHighlightRect: ArcCreateResultRectSnapshot(
      transform: ArcCreateRectTransformSnapshot(
        anchorMin: CharacterPreviewPoint(0, 0),
        anchorMax: CharacterPreviewPoint(0.916, 1),
        anchoredPosition: CharacterPreviewPoint(0, 0),
        sizeDelta: CharacterPreviewPoint(0, 0),
        pivot: CharacterPreviewPoint(0.5, 0.5),
      ),
      parent: ArcCreateRectTransformSnapshot(
        anchorMin: CharacterPreviewPoint(0.56, 0),
        anchorMax: CharacterPreviewPoint(0.95, 1),
        anchoredPosition: CharacterPreviewPoint(337.5, 40),
        sizeDelta: CharacterPreviewPoint(-183, -560),
        pivot: CharacterPreviewPoint(0.5, 0.5),
      ),
    ),
    playRetryBackgroundRect: ArcCreateResultRectSnapshot(
      transform: ArcCreateRectTransformSnapshot(
        anchorMin: CharacterPreviewPoint(0.27, -0.2),
        anchorMax: CharacterPreviewPoint(1, -0.2),
        anchoredPosition: CharacterPreviewPoint(0, 0),
        sizeDelta: CharacterPreviewPoint(0, 120),
        pivot: CharacterPreviewPoint(1, 0.5),
      ),
    ),
    playRetryFrameRect: ArcCreateResultRectSnapshot(
      transform: ArcCreateRectTransformSnapshot(
        anchorMin: CharacterPreviewPoint(0.27, -0.2),
        anchorMax: CharacterPreviewPoint(1, -0.2),
        anchoredPosition: CharacterPreviewPoint(0, 0),
        sizeDelta: CharacterPreviewPoint(0, 120),
        pivot: CharacterPreviewPoint(1, 0.5),
      ),
    ),
    profileName: 'ArcCreateResultLayout / AndroidPortedResultLayout',
  );
}

class CharacterPreviewPlacement {
  const CharacterPreviewPlacement({
    required this.imageLogicalWidth,
    required this.imageLogicalHeight,
    required this.logicalDrawBounds,
    required this.visibleBounds,
    required this.displayDrawBounds,
    required this.displayVisibleBounds,
    required this.displayScale,
    required this.contentLeft,
    required this.contentTop,
    required this.intersectsCanvas,
    required this.pivotLogical,
    required this.layoutProfileName,
  });

  final double imageLogicalWidth;
  final double imageLogicalHeight;
  final CharacterPreviewBounds logicalDrawBounds;
  final CharacterPreviewBounds? visibleBounds;
  final CharacterPreviewBounds displayDrawBounds;
  final CharacterPreviewBounds? displayVisibleBounds;
  final double displayScale;
  final double contentLeft;
  final double contentTop;
  final bool intersectsCanvas;
  final CharacterPreviewPoint pivotLogical;
  final String layoutProfileName;
}

class CharacterResultDisplayTransform {
  const CharacterResultDisplayTransform({
    required this.displayScale,
    required this.contentLeft,
    required this.contentTop,
    required this.contentWidth,
    required this.contentHeight,
  });

  final double displayScale;
  final double contentLeft;
  final double contentTop;
  final double contentWidth;
  final double contentHeight;

  CharacterPreviewBounds mapBounds(CharacterPreviewBounds bounds) {
    return bounds.scaleAndTranslate(
      scale: displayScale,
      dx: contentLeft,
      dy: contentTop,
    );
  }
}

class CharacterPreviewCoordinateMapper {
  const CharacterPreviewCoordinateMapper._();

  static const resultLogicalWidth = 1920.0;
  static const resultLogicalHeight = 1080.0;
  static const characterImageHeight = 2048.0;

  static CharacterResultDisplayTransform displayTransform({
    required double canvasWidth,
    required double canvasHeight,
    ArcCreateResultLayoutSnapshot layout =
        ArcCreateResultLayoutSnapshots.resultScreen,
  }) {
    final safeWidth = canvasWidth.isFinite && canvasWidth > 0
        ? canvasWidth
        : 1.0;
    final safeHeight = canvasHeight.isFinite && canvasHeight > 0
        ? canvasHeight
        : 1.0;
    final scale = math.min(
      safeWidth / layout.referenceWidth,
      safeHeight / layout.referenceHeight,
    );
    final contentWidth = layout.referenceWidth * scale;
    final contentHeight = layout.referenceHeight * scale;
    return CharacterResultDisplayTransform(
      displayScale: scale,
      contentLeft: (safeWidth - contentWidth) / 2,
      contentTop: (safeHeight - contentHeight) / 2,
      contentWidth: contentWidth,
      contentHeight: contentHeight,
    );
  }

  static CharacterPreviewPlacement map({
    required double canvasWidth,
    required double canvasHeight,
    required double imageWidth,
    required double imageHeight,
    required double x,
    required double y,
    required double scale,
    ArcCreateResultLayoutSnapshot layout =
        ArcCreateResultLayoutSnapshots.resultScreen,
  }) {
    final transform = displayTransform(
      canvasWidth: canvasWidth,
      canvasHeight: canvasHeight,
      layout: layout,
    );
    final logical = mapLogical(
      imageWidth: imageWidth,
      imageHeight: imageHeight,
      x: x,
      y: y,
      scale: scale,
      layout: layout,
    );
    return CharacterPreviewPlacement(
      imageLogicalWidth: logical.imageLogicalWidth,
      imageLogicalHeight: logical.imageLogicalHeight,
      logicalDrawBounds: logical.logicalDrawBounds,
      visibleBounds: logical.visibleBounds,
      displayDrawBounds: transform.mapBounds(logical.logicalDrawBounds),
      displayVisibleBounds: logical.visibleBounds == null
          ? null
          : transform.mapBounds(logical.visibleBounds!),
      displayScale: transform.displayScale,
      contentLeft: transform.contentLeft,
      contentTop: transform.contentTop,
      intersectsCanvas: logical.intersectsCanvas,
      pivotLogical: logical.pivotLogical,
      layoutProfileName: layout.profileName,
    );
  }

  static CharacterPreviewPlacement mapLogical({
    required double imageWidth,
    required double imageHeight,
    required double x,
    required double y,
    required double scale,
    ArcCreateResultLayoutSnapshot layout =
        ArcCreateResultLayoutSnapshots.resultScreen,
  }) {
    final safeImageWidth = imageWidth.isFinite && imageWidth > 0
        ? imageWidth
        : 1.0;
    final safeImageHeight = imageHeight.isFinite && imageHeight > 0
        ? imageHeight
        : 1.0;
    final safeScale = scale.isFinite && scale > 0 ? scale : 1.0;
    final aspect = safeImageWidth / safeImageHeight;
    final logicalWidth = characterImageHeight * aspect;
    final logicalHeight = characterImageHeight;

    final parentTransform = layout.characterParent.withAnchoredPosition(
      layout.characterParentFinalAnchoredPosition,
    );
    final parentRect = parentTransform.resolve(layout.root);
    final imageTransform = layout.characterImage
        .withAnchoredPosition(CharacterPreviewPoint(x, y))
        .withSizeDelta(CharacterPreviewPoint(logicalWidth, logicalHeight))
        .withLocalScale(safeScale);
    final imageRect = imageTransform.resolve(parentRect);
    final previewBounds = imageRect.toPreviewBounds(layout.referenceHeight);
    final canvasBounds = CharacterPreviewBounds(
      left: 0,
      top: 0,
      right: layout.referenceWidth,
      bottom: layout.referenceHeight,
    );
    final visible = previewBounds.intersect(canvasBounds);
    final pivot = CharacterPreviewPoint(
      parentRect.left + parentRect.width * 0.5 + x,
      layout.referenceHeight -
          (parentRect.bottom + parentRect.height * 0.5 + y),
    );
    return CharacterPreviewPlacement(
      imageLogicalWidth: logicalWidth,
      imageLogicalHeight: logicalHeight,
      logicalDrawBounds: previewBounds,
      visibleBounds: visible,
      displayDrawBounds: previewBounds,
      displayVisibleBounds: visible,
      displayScale: 1,
      contentLeft: 0,
      contentTop: 0,
      intersectsCanvas: visible != null && !visible.isEmpty,
      pivotLogical: pivot,
      layoutProfileName: layout.profileName,
    );
  }
}

class ArcCreateResolvedRect {
  const ArcCreateResolvedRect({
    required this.left,
    required this.right,
    required this.bottom,
    required this.top,
    this.pivotX,
    this.pivotY,
    this.localWidth,
    this.localHeight,
    this.pivotFractionX = 0,
    this.pivotFractionY = 0,
    this.scaleX = 1,
    this.scaleY = 1,
  });

  factory ArcCreateResolvedRect.root(double width, double height) {
    return ArcCreateResolvedRect(
      left: 0,
      right: width,
      bottom: 0,
      top: height,
      pivotX: 0,
      pivotY: 0,
      localWidth: width,
      localHeight: height,
      pivotFractionX: 0,
      pivotFractionY: 0,
      scaleX: 1,
      scaleY: 1,
    );
  }

  final double left;
  final double right;
  final double bottom;
  final double top;
  final double? pivotX;
  final double? pivotY;
  final double? localWidth;
  final double? localHeight;
  final double pivotFractionX;
  final double pivotFractionY;
  final double scaleX;
  final double scaleY;

  double get width => right - left;
  double get height => top - bottom;
  double get resolvedPivotX => pivotX ?? left;
  double get resolvedPivotY => pivotY ?? bottom;
  double get resolvedLocalWidth => localWidth ?? width;
  double get resolvedLocalHeight => localHeight ?? height;

  CharacterPreviewBounds toPreviewBounds(double referenceHeight) {
    return CharacterPreviewBounds(
      left: left,
      top: referenceHeight - top,
      right: right,
      bottom: referenceHeight - bottom,
    );
  }
}
