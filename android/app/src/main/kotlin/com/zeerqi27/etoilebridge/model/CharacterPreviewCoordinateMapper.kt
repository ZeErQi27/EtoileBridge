package com.zeerqi27.etoilebridge.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class PreviewPoint(
    val x: Float,
    val y: Float,
)

data class PreviewBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = max(0f, right - left)
    val height: Float get() = max(0f, bottom - top)
}

data class ArcCreateRectTransformSnapshot(
    val name: String,
    val fileId: Long,
    val anchorMin: PreviewPoint,
    val anchorMax: PreviewPoint,
    val anchoredPosition: PreviewPoint,
    val sizeDelta: PreviewPoint,
    val pivot: PreviewPoint,
    val localScale: PreviewPoint = PreviewPoint(1f, 1f),
    val source: String,
) {
    fun withAnchoredPosition(position: PreviewPoint) = copy(anchoredPosition = position)
    fun withSizeDelta(size: PreviewPoint) = copy(sizeDelta = size)
    fun withLocalScale(scale: Float) = copy(localScale = PreviewPoint(scale, scale))
}

data class ArcCreateResultRectSnapshot(
    val name: String,
    val fileId: Long,
    val rect: PreviewBounds,
    val spritePath: String? = null,
    val source: String,
)

data class ArcCreateResultLayoutSnapshot(
    val referenceWidth: Float,
    val referenceHeight: Float,
    val logicalWidth: Float,
    val logicalHeight: Float,
    val canvasMatch: Float,
    val characterParent: ArcCreateRectTransformSnapshot,
    val characterParentFinalAnchoredPosition: PreviewPoint,
    val characterImage: ArcCreateRectTransformSnapshot,
    val characterImageHeightBase: Float,
    val backgroundArrowRect: ArcCreateResultRectSnapshot,
    val clearGlowRect: ArcCreateResultRectSnapshot,
    val jacketRect: ArcCreateResultRectSnapshot,
    val resultPanelRect: ArcCreateResultRectSnapshot,
    val judgementHighlightRect: ArcCreateResultRectSnapshot,
    val bottomScoreRect: ArcCreateResultRectSnapshot,
    val playRetryRect: ArcCreateResultRectSnapshot,
    val playRetryHighlightRect: ArcCreateResultRectSnapshot,
)

data class CharacterPreviewPlacement(
    val width: Float,
    val height: Float,
    val offsetX: Float,
    val offsetY: Float,
    val visibleBounds: PreviewBounds,
    val intersectsCanvas: Boolean,
    val pivotX: Float,
    val pivotY: Float,
    val debugInfo: String,
    val logicalDrawRect: PreviewBounds,
    val logicalPivot: PreviewPoint,
    val logicalVisibleBounds: PreviewBounds,
    val displayDrawRect: PreviewBounds,
    val displayScale: Float,
)

object ArcCreateResultLayoutSnapshots {
    private const val RESULT_UNITY = "Assets/Scenes/Result.unity"
    private const val RESULT_SCREEN = "Assets/Scripts/Selection/Interface/ResultScreen.cs"

    val ResultScreen: ArcCreateResultLayoutSnapshot by lazy {
        val referenceWidth = 1920f
        val referenceHeight = 1080f
        val logicalWidth = referenceWidth
        val logicalHeight = referenceHeight
        val root = ResolvedRect.root(referenceWidth, referenceHeight)
        val backgroundArrow = ArcCreateRectTransformSnapshot(
            name = "BackgroundArrow",
            fileId = 285783021,
            anchorMin = PreviewPoint(0f, 0f),
            anchorMax = PreviewPoint(1f, 1f),
            anchoredPosition = PreviewPoint(354.68176f, 0f),
            sizeDelta = PreviewPoint(0f, 0f),
            pivot = PreviewPoint(0.5f, 0.5f),
            source = "$RESULT_UNITY:1205-1224",
        )
        val jacketParent = ArcCreateRectTransformSnapshot(
            name = "Jacket",
            fileId = 1148361366,
            anchorMin = PreviewPoint(0.5f, 1f),
            anchorMax = PreviewPoint(0.5f, 1f),
            anchoredPosition = PreviewPoint(0f, -541f),
            sizeDelta = PreviewPoint(100f, 100f),
            pivot = PreviewPoint(0.5f, 0.5f),
            localScale = PreviewPoint(1.3001714f, 1.3001714f),
            source = "$RESULT_UNITY:5101-5125",
        )
        val jacketFrame = ArcCreateRectTransformSnapshot(
            name = "JacketFrame",
            fileId = 1349891945,
            anchorMin = PreviewPoint(0.5f, 0.5f),
            anchorMax = PreviewPoint(0.5f, 0.5f),
            anchoredPosition = PreviewPoint(0f, -0.000091552734f),
            sizeDelta = PreviewPoint(800f, 800f),
            pivot = PreviewPoint(0.5f, 0.5f),
            source = "$RESULT_UNITY:5689-5709",
        )
        val scoreFrame = ArcCreateRectTransformSnapshot(
            name = "ScoreFrame",
            fileId = 1720625761,
            anchorMin = PreviewPoint(0.5f, 0f),
            anchorMax = PreviewPoint(0.5f, 0f),
            anchoredPosition = PreviewPoint(0f, 439.99908f),
            sizeDelta = PreviewPoint(100f, 100f),
            pivot = PreviewPoint(0.5f, 0.5f),
            source = "$RESULT_UNITY:6941-6969",
        )
        val scoreBackground = ArcCreateRectTransformSnapshot(
            name = "Background",
            fileId = 1381619424,
            anchorMin = PreviewPoint(0.5f, 0f),
            anchorMax = PreviewPoint(0.5f, 0f),
            anchoredPosition = PreviewPoint(20f, -490f),
            sizeDelta = PreviewPoint(1105f, 270f),
            pivot = PreviewPoint(0.5f, 0f),
            source = "$RESULT_UNITY:5768-5786",
        )
        val clearResult = ArcCreateRectTransformSnapshot(
            name = "ClearResult",
            fileId = 1304582804,
            anchorMin = PreviewPoint(0.5f, 0.5f),
            anchorMax = PreviewPoint(0.5f, 0.5f),
            anchoredPosition = PreviewPoint(0f, -335f),
            sizeDelta = PreviewPoint(700f, 182.7f),
            pivot = PreviewPoint(0.5f, 0.5f),
            localScale = PreviewPoint(1.7003375f, 1.7003375f),
            source = "$RESULT_UNITY:5597-5617",
        )
        val judgementFrame = ArcCreateRectTransformSnapshot(
            name = "JudgementFrame",
            fileId = 1604620936,
            anchorMin = PreviewPoint(0.56f, 0f),
            anchorMax = PreviewPoint(0.95f, 1f),
            anchoredPosition = PreviewPoint(137.50003f, 40f),
            sizeDelta = PreviewPoint(-183f, -560f),
            pivot = PreviewPoint(0.5f, 0.5f),
            source = "$RESULT_UNITY:6707-6727 + defaultPosition line 6744",
        )
        val judgementTable = ArcCreateRectTransformSnapshot(
            name = "JudgementTable",
            fileId = 639175305,
            anchorMin = PreviewPoint(0f, 0f),
            anchorMax = PreviewPoint(0f, 0f),
            anchoredPosition = PreviewPoint(0f, 0f),
            sizeDelta = PreviewPoint(0f, 0f),
            pivot = PreviewPoint(0.5f, 0.5f),
            source = "$RESULT_UNITY:2977-3003",
        )
        val judgementHighlight = ArcCreateRectTransformSnapshot(
            name = "JudgementTableHighlight",
            fileId = 1253284157,
            anchorMin = PreviewPoint(0f, 0f),
            anchorMax = PreviewPoint(0.916f, 1f),
            anchoredPosition = PreviewPoint(0f, 0f),
            sizeDelta = PreviewPoint(0f, 0f),
            pivot = PreviewPoint(0.5f, 0.5f),
            source = "$RESULT_UNITY:5519-5538",
        )
        val playRetryTable = ArcCreateRectTransformSnapshot(
            name = "PlayRetryTable",
            fileId = 1410225590,
            anchorMin = PreviewPoint(0.27f, -0.2f),
            anchorMax = PreviewPoint(1f, -0.2f),
            anchoredPosition = PreviewPoint(0f, 0f),
            sizeDelta = PreviewPoint(0f, 0f),
            pivot = PreviewPoint(1f, 0.5f),
            source = "$RESULT_UNITY:5952-5976",
        )
        val playRetryHighlight = ArcCreateRectTransformSnapshot(
            name = "Highlights",
            fileId = 470976325,
            anchorMin = PreviewPoint(0.55f, -0.015f),
            anchorMax = PreviewPoint(1f, 1.02f),
            anchoredPosition = PreviewPoint(8f, 0f),
            sizeDelta = PreviewPoint(8f, 0f),
            pivot = PreviewPoint(1f, 0.5f),
            source = "$RESULT_UNITY:2511-2530",
        )
        val characterParent = ArcCreateRectTransformSnapshot(
            name = "CharacterParent",
            fileId = 1519955448,
            anchorMin = PreviewPoint(0.5f, 0.5f),
            anchorMax = PreviewPoint(0.5f, 0.5f),
            anchoredPosition = PreviewPoint(-340.31818f, -365f),
            sizeDelta = PreviewPoint(0f, 0f),
            pivot = PreviewPoint(0.5f, 0.5f),
            source = "$RESULT_UNITY:6487-6507",
        )
        val characterImage = ArcCreateRectTransformSnapshot(
            name = "Character",
            fileId = 597877295,
            anchorMin = PreviewPoint(0.5f, 0.5f),
            anchorMax = PreviewPoint(0.5f, 0.5f),
            anchoredPosition = PreviewPoint(0f, 0f),
            sizeDelta = PreviewPoint(1152f, 2048f),
            pivot = PreviewPoint(0.5f, 0.5f),
            source = "$RESULT_UNITY:2897-2916; $RESULT_SCREEN:157-163",
        )
        fun rectSnapshot(
            name: String,
            transform: ArcCreateRectTransformSnapshot,
            parent: ResolvedRect,
            spritePath: String?,
        ) = ArcCreateResultRectSnapshot(
            name = name,
            fileId = transform.fileId,
            rect = transform.resolve(parent).toPreviewBounds(referenceHeight),
            spritePath = spritePath,
            source = transform.source,
        )
        fun resolvedRectSnapshot(
            name: String,
            transform: ArcCreateRectTransformSnapshot,
            resolved: ResolvedRect,
            spritePath: String?,
        ) = ArcCreateResultRectSnapshot(
            name = name,
            fileId = transform.fileId,
            rect = resolved.toPreviewBounds(referenceHeight),
            spritePath = spritePath,
            source = transform.source,
        )

        val jacketResolved = jacketParent.resolve(root)
        val scoreResolved = scoreFrame.resolve(root)
        val judgementResolved = judgementFrame.resolve(root)
        val judgementTableResolved = judgementTable.resolve(judgementResolved)
        val playRetryResolved = playRetryTable.resolve(judgementTableResolved)
        ArcCreateResultLayoutSnapshot(
            referenceWidth = referenceWidth,
            referenceHeight = referenceHeight,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
            canvasMatch = 0.75f,
            characterParent = characterParent,
            characterParentFinalAnchoredPosition = PreviewPoint(-640.3182f, -365f),
            characterImage = characterImage,
            characterImageHeightBase = 2048f,
            backgroundArrowRect = rectSnapshot(
                name = "BackgroundArrow",
                transform = backgroundArrow,
                parent = root,
                spritePath = "Assets/Textures/Result/Background Arrow.png",
            ),
            clearGlowRect = rectSnapshot(
                name = "ClearResult",
                transform = clearResult,
                parent = scoreResolved,
                spritePath = "Assets/Textures/Result/Clear Glow.png",
            ),
            jacketRect = rectSnapshot(
                name = "JacketFrame",
                transform = jacketFrame,
                parent = jacketResolved,
                spritePath = "Assets/Textures/Result/Jacket Background.png",
            ),
            resultPanelRect = resolvedRectSnapshot(
                name = "JudgementTable",
                transform = judgementTable,
                resolved = judgementResolved,
                spritePath = "Assets/Textures/Result/Judgement Table.png",
            ),
            judgementHighlightRect = rectSnapshot(
                name = "JudgementTableHighlight",
                transform = judgementHighlight,
                parent = judgementResolved,
                spritePath = "Assets/Textures/Result/Judgement Table Highlight.png",
            ),
            bottomScoreRect = rectSnapshot(
                name = "ScoreBackground",
                transform = scoreBackground,
                parent = scoreResolved,
                spritePath = "Assets/Textures/Result/Score Frame.png",
            ),
            playRetryRect = rectSnapshot(
                name = "PlayRetryTable",
                transform = playRetryTable,
                parent = judgementTableResolved,
                spritePath = "Assets/Textures/Result/Play Retry Background.png",
            ),
            playRetryHighlightRect = rectSnapshot(
                name = "PlayRetryHighlight",
                transform = playRetryHighlight,
                parent = playRetryResolved,
                spritePath = "Assets/Textures/Result/Play Retry Frame.png",
            ),
        )
    }
}

object CharacterPreviewCoordinateMapper {
    const val RESULT_REFERENCE_WIDTH = 1920f
    const val RESULT_REFERENCE_HEIGHT = 1080f
    const val RESULT_LOGICAL_WIDTH = 1920f
    const val RESULT_LOGICAL_HEIGHT = 1080f
    const val RESULT_CANVAS_MATCH_WIDTH_OR_HEIGHT = 0.75f
    const val CHARACTER_PARENT_X = -640.3182f
    const val CHARACTER_PARENT_Y = -365f
    const val CHARACTER_IMAGE_HEIGHT = 2048f

    fun map(
        canvasWidth: Float,
        canvasHeight: Float,
        imageWidth: Int,
        imageHeight: Int,
        x: Float,
        y: Float,
        scale: Float,
        layoutSnapshot: ArcCreateResultLayoutSnapshot = ArcCreateResultLayoutSnapshots.ResultScreen,
    ): CharacterPreviewPlacement {
        val logical = mapLogical(imageWidth, imageHeight, x, y, scale, layoutSnapshot)
        val displayScale = min(canvasWidth / layoutSnapshot.logicalWidth, canvasHeight / layoutSnapshot.logicalHeight)
        val safeDisplayScale = max(0.0001f, displayScale)
        val contentWidth = layoutSnapshot.logicalWidth * safeDisplayScale
        val contentHeight = layoutSnapshot.logicalHeight * safeDisplayScale
        val displayLeft = (canvasWidth - contentWidth) / 2f
        val displayTop = (canvasHeight - contentHeight) / 2f
        return logical.toDisplayPlacement(safeDisplayScale, displayLeft, displayTop)
    }

    fun mapLogical(
        imageWidth: Int,
        imageHeight: Int,
        x: Float,
        y: Float,
        scale: Float,
        layoutSnapshot: ArcCreateResultLayoutSnapshot = ArcCreateResultLayoutSnapshots.ResultScreen,
    ): CharacterPreviewPlacement {
        val safeImageHeight = max(1, imageHeight).toFloat()
        val imageAspect = max(1, imageWidth).toFloat() / safeImageHeight
        val imageTransform = layoutSnapshot.characterImage
            .withAnchoredPosition(PreviewPoint(x, y))
            .withSizeDelta(PreviewPoint(layoutSnapshot.characterImageHeightBase * imageAspect, layoutSnapshot.characterImageHeightBase))
            .withLocalScale(scale)
        val referenceRoot = ResolvedRect.root(layoutSnapshot.referenceWidth, layoutSnapshot.referenceHeight)
        val parent = layoutSnapshot.characterParent
            .withAnchoredPosition(layoutSnapshot.characterParentFinalAnchoredPosition)
            .resolve(referenceRoot)
        val image = imageTransform.resolve(parent)
        val drawRect = image.toPreviewBounds(layoutSnapshot.referenceHeight)
        val pivot = image.toPreviewPoint(layoutSnapshot.referenceHeight)
        val canvas = PreviewBounds(0f, 0f, layoutSnapshot.logicalWidth, layoutSnapshot.logicalHeight)
        val visibleBounds = drawRect.intersection(canvas)
        val intersects = visibleBounds.width > 0f && visibleBounds.height > 0f
        return CharacterPreviewPlacement(
            width = drawRect.width,
            height = drawRect.height,
            offsetX = drawRect.left,
            offsetY = drawRect.top,
            visibleBounds = visibleBounds,
            intersectsCanvas = intersects,
            pivotX = pivot.x,
            pivotY = pivot.y,
            logicalDrawRect = drawRect,
            logicalPivot = pivot,
            logicalVisibleBounds = visibleBounds,
            displayDrawRect = drawRect,
            displayScale = 1f,
            debugInfo = buildString {
                append("source=${imageWidth}x$imageHeight; ")
                append("reference=${layoutSnapshot.referenceWidth.format1()}x${layoutSnapshot.referenceHeight.format1()}; ")
                append("logical=${layoutSnapshot.logicalWidth.format1()}x${layoutSnapshot.logicalHeight.format1()}; ")
                append("parent=${layoutSnapshot.characterParentFinalAnchoredPosition.x.format1()},${layoutSnapshot.characterParentFinalAnchoredPosition.y.format1()}; ")
                append("draw=${drawRect.width.format1()}x${drawRect.height.format1()}; ")
                append("offset=${drawRect.left.format1()},${drawRect.top.format1()}; ")
                append("pivot=${pivot.x.format1()},${pivot.y.format1()}; ")
                append("visible=${visibleBounds.left.format1()},${visibleBounds.top.format1()},${visibleBounds.right.format1()},${visibleBounds.bottom.format1()}; ")
                append("intersects=$intersects")
            },
        )
    }

    fun canvasScaleFactor(canvasWidth: Float, canvasHeight: Float): Float {
        val safeWidth = max(1f, canvasWidth)
        val safeHeight = max(1f, canvasHeight)
        val widthScale = safeWidth / RESULT_REFERENCE_WIDTH
        val heightScale = safeHeight / RESULT_REFERENCE_HEIGHT
        val match = RESULT_CANVAS_MATCH_WIDTH_OR_HEIGHT.coerceIn(0f, 1f)
        return (widthScale.toDouble().pow(1.0 - match.toDouble()) *
            heightScale.toDouble().pow(match.toDouble())).toFloat()
    }
}

private data class ResolvedRect(
    val left: Float,
    val bottom: Float,
    val right: Float,
    val top: Float,
    val pivotX: Float,
    val pivotY: Float,
    val localWidth: Float,
    val localHeight: Float,
    val pivotFractionX: Float,
    val pivotFractionY: Float,
    val scaleX: Float,
    val scaleY: Float,
) {
    val width: Float get() = max(0f, right - left)
    val height: Float get() = max(0f, top - bottom)

    fun toPreviewBounds(referenceHeight: Float) = PreviewBounds(
        left = left,
        top = referenceHeight - top,
        right = right,
        bottom = referenceHeight - bottom,
    )

    fun toPreviewPoint(referenceHeight: Float) = PreviewPoint(
        x = pivotX,
        y = referenceHeight - pivotY,
    )

    companion object {
        fun root(width: Float, height: Float) = ResolvedRect(
            left = 0f,
            bottom = 0f,
            right = width,
            top = height,
            pivotX = 0f,
            pivotY = 0f,
            localWidth = width,
            localHeight = height,
            pivotFractionX = 0f,
            pivotFractionY = 0f,
            scaleX = 1f,
            scaleY = 1f,
        )
    }
}

private fun ArcCreateRectTransformSnapshot.resolve(parent: ResolvedRect): ResolvedRect {
    val localWidth = parent.localWidth * (anchorMax.x - anchorMin.x) + sizeDelta.x
    val localHeight = parent.localHeight * (anchorMax.y - anchorMin.y) + sizeDelta.y
    val anchorReferenceX = parent.localWidth * anchorMin.x +
        parent.localWidth * (anchorMax.x - anchorMin.x) * pivot.x +
        anchoredPosition.x -
        parent.localWidth * parent.pivotFractionX
    val anchorReferenceY = parent.localHeight * anchorMin.y +
        parent.localHeight * (anchorMax.y - anchorMin.y) * pivot.y +
        anchoredPosition.y -
        parent.localHeight * parent.pivotFractionY
    val pivotWorldX = parent.pivotX + (anchorReferenceX * parent.scaleX)
    val pivotWorldY = parent.pivotY + (anchorReferenceY * parent.scaleY)
    val worldScaleX = parent.scaleX * localScale.x
    val worldScaleY = parent.scaleY * localScale.y
    val renderedWidth = localWidth * worldScaleX
    val renderedHeight = localHeight * worldScaleY
    val left = pivotWorldX - renderedWidth * pivot.x
    val bottom = pivotWorldY - renderedHeight * pivot.y
    return ResolvedRect(
        left = left,
        bottom = bottom,
        right = left + renderedWidth,
        top = bottom + renderedHeight,
        pivotX = pivotWorldX,
        pivotY = pivotWorldY,
        localWidth = localWidth,
        localHeight = localHeight,
        pivotFractionX = pivot.x,
        pivotFractionY = pivot.y,
        scaleX = worldScaleX,
        scaleY = worldScaleY,
    )
}

private fun PreviewBounds.intersection(other: PreviewBounds) = PreviewBounds(
    left = max(left, other.left),
    top = max(top, other.top),
    right = min(right, other.right),
    bottom = min(bottom, other.bottom),
)

private fun CharacterPreviewPlacement.toDisplayPlacement(
    displayScale: Float,
    displayLeft: Float,
    displayTop: Float,
): CharacterPreviewPlacement {
    fun PreviewBounds.scale() = PreviewBounds(
        left = displayLeft + left * displayScale,
        top = displayTop + top * displayScale,
        right = displayLeft + right * displayScale,
        bottom = displayTop + bottom * displayScale,
    )
    val displayDrawRect = logicalDrawRect.scale()
    val displayVisibleBounds = logicalVisibleBounds.scale()
    return copy(
        width = displayDrawRect.width,
        height = displayDrawRect.height,
        offsetX = displayDrawRect.left,
        offsetY = displayDrawRect.top,
        visibleBounds = displayVisibleBounds,
        pivotX = displayLeft + logicalPivot.x * displayScale,
        pivotY = displayTop + logicalPivot.y * displayScale,
        displayDrawRect = displayDrawRect,
        displayScale = displayScale,
        debugInfo = "$debugInfo; displayScale=${displayScale.format1()}",
    )
}

private fun Float.format1(): String = "%.1f".format(this)
