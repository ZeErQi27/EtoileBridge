export type PreviewPoint = {
  x: number
  y: number
}

export type PreviewBounds = {
  left: number
  top: number
  right: number
  bottom: number
}

export type RectTransformSnapshot = {
  name: string
  fileId: number
  anchorMin: PreviewPoint
  anchorMax: PreviewPoint
  anchoredPosition: PreviewPoint
  sizeDelta: PreviewPoint
  pivot: PreviewPoint
  localScale?: PreviewPoint
  source: string
}

export type ResultRectSnapshot = {
  name: string
  fileId: number
  rect: PreviewBounds
  spritePath?: string
  source: string
}

export type ResultLayoutSnapshot = {
  profileName: string
  referenceWidth: number
  referenceHeight: number
  logicalWidth: number
  logicalHeight: number
  canvasMatch: number
  characterParent: RectTransformSnapshot
  characterParentFinalAnchoredPosition: PreviewPoint
  characterImage: RectTransformSnapshot
  characterImageHeightBase: number
  backgroundArrowRect: ResultRectSnapshot
  clearGlowRect: ResultRectSnapshot
  jacketRect: ResultRectSnapshot
  resultPanelRect: ResultRectSnapshot
  judgementHighlightRect: ResultRectSnapshot
  bottomScoreRect: ResultRectSnapshot
  playRetryRect: ResultRectSnapshot
  playRetryHighlightRect: ResultRectSnapshot
}

export type CharacterPreviewPlacement = {
  width: number
  height: number
  offsetX: number
  offsetY: number
  visibleBounds: PreviewBounds
  intersectsCanvas: boolean
  pivotX: number
  pivotY: number
  logicalDrawRect: PreviewBounds
  logicalPivot: PreviewPoint
  logicalVisibleBounds: PreviewBounds
  displayDrawRect: PreviewBounds
  displayScale: number
  debugInfo: string
}

export type ResultAssetLayerKey =
  | 'backgroundArrow'
  | 'clearGlow'
  | 'jacketBackground'
  | 'scoreFrame'
  | 'judgementTable'
  | 'judgementHighlight'
  | 'playRetryBackground'
  | 'playRetryFrame'

export type ResultPreviewLayer =
  | { kind: 'asset'; key: ResultAssetLayerKey; rect: PreviewBounds; className?: string }
  | { kind: 'block'; rect: PreviewBounds; className: string; radius?: number }

type ResolvedRect = {
  left: number
  bottom: number
  right: number
  top: number
  pivotX: number
  pivotY: number
  localWidth: number
  localHeight: number
  pivotFractionX: number
  pivotFractionY: number
  scaleX: number
  scaleY: number
}

const resultUnity = 'Assets/Scenes/Result.unity'
const resultScreen = 'Assets/Scripts/Selection/Interface/ResultScreen.cs'

export const RESULT_REFERENCE_WIDTH = 1920
export const RESULT_REFERENCE_HEIGHT = 1080
export const RESULT_LOGICAL_WIDTH = 1920
export const RESULT_LOGICAL_HEIGHT = 1080
export const RESULT_CANVAS_MATCH_WIDTH_OR_HEIGHT = 0.75
export const CHARACTER_PARENT_X = -640.3182
export const CHARACTER_PARENT_Y = -365
export const CHARACTER_IMAGE_HEIGHT = 2048

const root = resolvedRoot(RESULT_REFERENCE_WIDTH, RESULT_REFERENCE_HEIGHT)

const backgroundArrow = rectTransform({
  name: 'BackgroundArrow',
  fileId: 285783021,
  anchorMin: { x: 0, y: 0 },
  anchorMax: { x: 1, y: 1 },
  anchoredPosition: { x: 354.68176, y: 0 },
  sizeDelta: { x: 0, y: 0 },
  pivot: { x: 0.5, y: 0.5 },
  source: `${resultUnity}:1205-1224`
})

const jacketParent = rectTransform({
  name: 'Jacket',
  fileId: 1148361366,
  anchorMin: { x: 0.5, y: 1 },
  anchorMax: { x: 0.5, y: 1 },
  anchoredPosition: { x: 0, y: -541 },
  sizeDelta: { x: 100, y: 100 },
  pivot: { x: 0.5, y: 0.5 },
  localScale: { x: 1.3001714, y: 1.3001714 },
  source: `${resultUnity}:5101-5125`
})

const jacketFrame = rectTransform({
  name: 'JacketFrame',
  fileId: 1349891945,
  anchorMin: { x: 0.5, y: 0.5 },
  anchorMax: { x: 0.5, y: 0.5 },
  anchoredPosition: { x: 0, y: -0.000091552734 },
  sizeDelta: { x: 800, y: 800 },
  pivot: { x: 0.5, y: 0.5 },
  source: `${resultUnity}:5689-5709`
})

const scoreFrame = rectTransform({
  name: 'ScoreFrame',
  fileId: 1720625761,
  anchorMin: { x: 0.5, y: 0 },
  anchorMax: { x: 0.5, y: 0 },
  anchoredPosition: { x: 0, y: 439.99908 },
  sizeDelta: { x: 100, y: 100 },
  pivot: { x: 0.5, y: 0.5 },
  source: `${resultUnity}:6941-6969`
})

const scoreBackground = rectTransform({
  name: 'Background',
  fileId: 1381619424,
  anchorMin: { x: 0.5, y: 0 },
  anchorMax: { x: 0.5, y: 0 },
  anchoredPosition: { x: 20, y: -490 },
  sizeDelta: { x: 1105, y: 270 },
  pivot: { x: 0.5, y: 0 },
  source: `${resultUnity}:5768-5786`
})

const clearResult = rectTransform({
  name: 'ClearResult',
  fileId: 1304582804,
  anchorMin: { x: 0.5, y: 0.5 },
  anchorMax: { x: 0.5, y: 0.5 },
  anchoredPosition: { x: 0, y: -335 },
  sizeDelta: { x: 700, y: 182.7 },
  pivot: { x: 0.5, y: 0.5 },
  localScale: { x: 1.7003375, y: 1.7003375 },
  source: `${resultUnity}:5597-5617`
})

const judgementFrame = rectTransform({
  name: 'JudgementFrame',
  fileId: 1604620936,
  anchorMin: { x: 0.56, y: 0 },
  anchorMax: { x: 0.95, y: 1 },
  anchoredPosition: { x: 137.50003, y: 40 },
  sizeDelta: { x: -183, y: -560 },
  pivot: { x: 0.5, y: 0.5 },
  source: `${resultUnity}:6707-6727 + defaultPosition line 6744`
})

const judgementTable = rectTransform({
  name: 'JudgementTable',
  fileId: 639175305,
  anchorMin: { x: 0, y: 0 },
  anchorMax: { x: 0, y: 0 },
  anchoredPosition: { x: 0, y: 0 },
  sizeDelta: { x: 0, y: 0 },
  pivot: { x: 0.5, y: 0.5 },
  source: `${resultUnity}:2977-3003`
})

const judgementHighlight = rectTransform({
  name: 'JudgementTableHighlight',
  fileId: 1253284157,
  anchorMin: { x: 0, y: 0 },
  anchorMax: { x: 0.916, y: 1 },
  anchoredPosition: { x: 0, y: 0 },
  sizeDelta: { x: 0, y: 0 },
  pivot: { x: 0.5, y: 0.5 },
  source: `${resultUnity}:5519-5538`
})

const playRetryTable = rectTransform({
  name: 'PlayRetryTable',
  fileId: 1410225590,
  anchorMin: { x: 0.27, y: -0.2 },
  anchorMax: { x: 1, y: -0.2 },
  anchoredPosition: { x: 0, y: 0 },
  sizeDelta: { x: 0, y: 0 },
  pivot: { x: 1, y: 0.5 },
  source: `${resultUnity}:5952-5976`
})

const playRetryHighlight = rectTransform({
  name: 'Highlights',
  fileId: 470976325,
  anchorMin: { x: 0.55, y: -0.015 },
  anchorMax: { x: 1, y: 1.02 },
  anchoredPosition: { x: 8, y: 0 },
  sizeDelta: { x: 8, y: 0 },
  pivot: { x: 1, y: 0.5 },
  source: `${resultUnity}:2511-2530`
})

const characterParent = rectTransform({
  name: 'CharacterParent',
  fileId: 1519955448,
  anchorMin: { x: 0.5, y: 0.5 },
  anchorMax: { x: 0.5, y: 0.5 },
  anchoredPosition: { x: -340.31818, y: -365 },
  sizeDelta: { x: 0, y: 0 },
  pivot: { x: 0.5, y: 0.5 },
  source: `${resultUnity}:6487-6507`
})

const characterImage = rectTransform({
  name: 'Character',
  fileId: 597877295,
  anchorMin: { x: 0.5, y: 0.5 },
  anchorMax: { x: 0.5, y: 0.5 },
  anchoredPosition: { x: 0, y: 0 },
  sizeDelta: { x: 1152, y: 2048 },
  pivot: { x: 0.5, y: 0.5 },
  source: `${resultUnity}:2897-2916; ${resultScreen}:157-163`
})

const jacketResolved = resolveRect(jacketParent, root)
const scoreResolved = resolveRect(scoreFrame, root)
const judgementResolved = resolveRect(judgementFrame, root)
const judgementTableResolved = resolveRect(judgementTable, judgementResolved)
const playRetryResolved = resolveRect(playRetryTable, judgementTableResolved)

export const androidPortedResultLayout: ResultLayoutSnapshot = {
  profileName: 'AndroidPortedResultLayout',
  referenceWidth: RESULT_REFERENCE_WIDTH,
  referenceHeight: RESULT_REFERENCE_HEIGHT,
  logicalWidth: RESULT_LOGICAL_WIDTH,
  logicalHeight: RESULT_LOGICAL_HEIGHT,
  canvasMatch: RESULT_CANVAS_MATCH_WIDTH_OR_HEIGHT,
  characterParent,
  characterParentFinalAnchoredPosition: { x: CHARACTER_PARENT_X, y: CHARACTER_PARENT_Y },
  characterImage,
  characterImageHeightBase: CHARACTER_IMAGE_HEIGHT,
  backgroundArrowRect: rectSnapshot('BackgroundArrow', backgroundArrow, root, 'Assets/Textures/Result/Background Arrow.png'),
  clearGlowRect: rectSnapshot('ClearResult', clearResult, scoreResolved, 'Assets/Textures/Result/Clear Glow.png'),
  jacketRect: rectSnapshot('JacketFrame', jacketFrame, jacketResolved, 'Assets/Textures/Result/Jacket Background.png'),
  resultPanelRect: resolvedRectSnapshot('JudgementTable', judgementTable, judgementResolved, 'Assets/Textures/Result/Judgement Table.png'),
  judgementHighlightRect: rectSnapshot('JudgementTableHighlight', judgementHighlight, judgementResolved, 'Assets/Textures/Result/Judgement Table Highlight.png'),
  bottomScoreRect: rectSnapshot('ScoreBackground', scoreBackground, scoreResolved, 'Assets/Textures/Result/Score Frame.png'),
  playRetryRect: rectSnapshot('PlayRetryTable', playRetryTable, judgementTableResolved, 'Assets/Textures/Result/Play Retry Background.png'),
  playRetryHighlightRect: rectSnapshot('PlayRetryHighlight', playRetryHighlight, playRetryResolved, 'Assets/Textures/Result/Play Retry Frame.png')
}

export const resultPreviewBackLayers: ResultPreviewLayer[] = [
  { kind: 'block', className: 'resultBaseBlock', rect: bounds(0, 0, RESULT_LOGICAL_WIDTH, RESULT_LOGICAL_HEIGHT), radius: 0 },
  { kind: 'asset', key: 'backgroundArrow', rect: androidPortedResultLayout.backgroundArrowRect.rect, className: 'resultBackdropLayer' },
  { kind: 'asset', key: 'clearGlow', rect: androidPortedResultLayout.clearGlowRect.rect },
  { kind: 'block', className: 'resultAccentBlock', rect: bounds(0, 0, 520, RESULT_LOGICAL_HEIGHT), radius: 0 }
]

export const resultPreviewFrontLayers: ResultPreviewLayer[] = [
  { kind: 'asset', key: 'jacketBackground', rect: androidPortedResultLayout.jacketRect.rect },
  { kind: 'block', className: 'resultJacketPlaceholder', rect: inset(androidPortedResultLayout.jacketRect.rect, 0.25), radius: 10 },
  { kind: 'asset', key: 'scoreFrame', rect: androidPortedResultLayout.bottomScoreRect.rect },
  { kind: 'asset', key: 'judgementTable', rect: androidPortedResultLayout.resultPanelRect.rect },
  { kind: 'asset', key: 'judgementHighlight', rect: androidPortedResultLayout.judgementHighlightRect.rect },
  { kind: 'asset', key: 'playRetryBackground', rect: androidPortedResultLayout.playRetryRect.rect },
  { kind: 'asset', key: 'playRetryFrame', rect: androidPortedResultLayout.playRetryHighlightRect.rect }
]

export function mapCharacterToResultCanvas(
  image: { width?: number; height?: number } | undefined,
  x: number,
  y: number,
  scale: number,
  layout = androidPortedResultLayout
): CharacterPreviewPlacement {
  const safeImageHeight = Math.max(1, image?.height ?? 1)
  const imageAspect = Math.max(1, image?.width ?? 1) / safeImageHeight
  const imageTransform = withLocalScale(
    withSizeDelta(
      withAnchoredPosition(layout.characterImage, { x, y }),
      { x: layout.characterImageHeightBase * imageAspect, y: layout.characterImageHeightBase }
    ),
    scale
  )
  const referenceRoot = resolvedRoot(layout.referenceWidth, layout.referenceHeight)
  const parent = resolveRect(withAnchoredPosition(layout.characterParent, layout.characterParentFinalAnchoredPosition), referenceRoot)
  const imageRect = resolveRect(imageTransform, parent)
  const drawRect = toPreviewBounds(imageRect, layout.referenceHeight)
  const pivot = toPreviewPoint(imageRect, layout.referenceHeight)
  const canvas = bounds(0, 0, layout.logicalWidth, layout.logicalHeight)
  const visibleBounds = intersection(drawRect, canvas)
  const intersectsCanvas = widthOf(visibleBounds) > 0 && heightOf(visibleBounds) > 0
  return {
    width: widthOf(drawRect),
    height: heightOf(drawRect),
    offsetX: drawRect.left,
    offsetY: drawRect.top,
    visibleBounds,
    intersectsCanvas,
    pivotX: pivot.x,
    pivotY: pivot.y,
    logicalDrawRect: drawRect,
    logicalPivot: pivot,
    logicalVisibleBounds: visibleBounds,
    displayDrawRect: drawRect,
    displayScale: 1,
    debugInfo: [
      `profile=${layout.profileName}`,
      `source=${image?.width ?? 0}x${image?.height ?? 0}`,
      `logical=${format1(layout.logicalWidth)}x${format1(layout.logicalHeight)}`,
      `parent=${format1(layout.characterParentFinalAnchoredPosition.x)},${format1(layout.characterParentFinalAnchoredPosition.y)}`,
      `draw=${format1(widthOf(drawRect))}x${format1(heightOf(drawRect))}`,
      `offset=${format1(drawRect.left)},${format1(drawRect.top)}`,
      `pivot=${format1(pivot.x)},${format1(pivot.y)}`,
      `visible=${format1(visibleBounds.left)},${format1(visibleBounds.top)},${format1(visibleBounds.right)},${format1(visibleBounds.bottom)}`,
      `intersects=${intersectsCanvas}`
    ].join('; ')
  }
}

export function rectCss(rect: PreviewBounds, layout = androidPortedResultLayout): Record<string, string> {
  return {
    left: `${(rect.left / layout.logicalWidth) * 100}%`,
    top: `${(rect.top / layout.logicalHeight) * 100}%`,
    width: `${(widthOf(rect) / layout.logicalWidth) * 100}%`,
    height: `${(heightOf(rect) / layout.logicalHeight) * 100}%`
  }
}

export function widthOf(rect: PreviewBounds): number {
  return Math.max(0, rect.right - rect.left)
}

export function heightOf(rect: PreviewBounds): number {
  return Math.max(0, rect.bottom - rect.top)
}

function rectTransform(value: RectTransformSnapshot): RectTransformSnapshot {
  return { ...value, localScale: value.localScale ?? { x: 1, y: 1 } }
}

function withAnchoredPosition(transform: RectTransformSnapshot, anchoredPosition: PreviewPoint): RectTransformSnapshot {
  return { ...transform, anchoredPosition }
}

function withSizeDelta(transform: RectTransformSnapshot, sizeDelta: PreviewPoint): RectTransformSnapshot {
  return { ...transform, sizeDelta }
}

function withLocalScale(transform: RectTransformSnapshot, scale: number): RectTransformSnapshot {
  return { ...transform, localScale: { x: scale, y: scale } }
}

function rectSnapshot(name: string, transform: RectTransformSnapshot, parent: ResolvedRect, spritePath?: string): ResultRectSnapshot {
  return {
    name,
    fileId: transform.fileId,
    rect: toPreviewBounds(resolveRect(transform, parent), RESULT_REFERENCE_HEIGHT),
    spritePath,
    source: transform.source
  }
}

function resolvedRectSnapshot(name: string, transform: RectTransformSnapshot, resolved: ResolvedRect, spritePath?: string): ResultRectSnapshot {
  return {
    name,
    fileId: transform.fileId,
    rect: toPreviewBounds(resolved, RESULT_REFERENCE_HEIGHT),
    spritePath,
    source: transform.source
  }
}

function resolvedRoot(width: number, height: number): ResolvedRect {
  return {
    left: 0,
    bottom: 0,
    right: width,
    top: height,
    pivotX: 0,
    pivotY: 0,
    localWidth: width,
    localHeight: height,
    pivotFractionX: 0,
    pivotFractionY: 0,
    scaleX: 1,
    scaleY: 1
  }
}

function resolveRect(transform: RectTransformSnapshot, parent: ResolvedRect): ResolvedRect {
  const localScale = transform.localScale ?? { x: 1, y: 1 }
  const localWidth = parent.localWidth * (transform.anchorMax.x - transform.anchorMin.x) + transform.sizeDelta.x
  const localHeight = parent.localHeight * (transform.anchorMax.y - transform.anchorMin.y) + transform.sizeDelta.y
  const anchorReferenceX =
    parent.localWidth * transform.anchorMin.x +
    parent.localWidth * (transform.anchorMax.x - transform.anchorMin.x) * transform.pivot.x +
    transform.anchoredPosition.x -
    parent.localWidth * parent.pivotFractionX
  const anchorReferenceY =
    parent.localHeight * transform.anchorMin.y +
    parent.localHeight * (transform.anchorMax.y - transform.anchorMin.y) * transform.pivot.y +
    transform.anchoredPosition.y -
    parent.localHeight * parent.pivotFractionY
  const pivotWorldX = parent.pivotX + anchorReferenceX * parent.scaleX
  const pivotWorldY = parent.pivotY + anchorReferenceY * parent.scaleY
  const worldScaleX = parent.scaleX * localScale.x
  const worldScaleY = parent.scaleY * localScale.y
  const renderedWidth = localWidth * worldScaleX
  const renderedHeight = localHeight * worldScaleY
  const left = pivotWorldX - renderedWidth * transform.pivot.x
  const bottom = pivotWorldY - renderedHeight * transform.pivot.y
  return {
    left,
    bottom,
    right: left + renderedWidth,
    top: bottom + renderedHeight,
    pivotX: pivotWorldX,
    pivotY: pivotWorldY,
    localWidth,
    localHeight,
    pivotFractionX: transform.pivot.x,
    pivotFractionY: transform.pivot.y,
    scaleX: worldScaleX,
    scaleY: worldScaleY
  }
}

function toPreviewBounds(rect: ResolvedRect, referenceHeight: number): PreviewBounds {
  return {
    left: rect.left,
    top: referenceHeight - rect.top,
    right: rect.right,
    bottom: referenceHeight - rect.bottom
  }
}

function toPreviewPoint(rect: ResolvedRect, referenceHeight: number): PreviewPoint {
  return { x: rect.pivotX, y: referenceHeight - rect.pivotY }
}

function bounds(left: number, top: number, right: number, bottom: number): PreviewBounds {
  return { left, top, right, bottom }
}

function inset(rect: PreviewBounds, fraction: number): PreviewBounds {
  const dx = widthOf(rect) * fraction
  const dy = heightOf(rect) * fraction
  return {
    left: rect.left + dx,
    top: rect.top + dy,
    right: rect.right - dx,
    bottom: rect.bottom - dy
  }
}

function intersection(a: PreviewBounds, b: PreviewBounds): PreviewBounds {
  return {
    left: Math.max(a.left, b.left),
    top: Math.max(a.top, b.top),
    right: Math.min(a.right, b.right),
    bottom: Math.min(a.bottom, b.bottom)
  }
}

function format1(value: number): string {
  return value.toFixed(1)
}
