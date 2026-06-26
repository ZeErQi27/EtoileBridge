package com.zeerqi27.etoilebridge.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterPreviewCoordinateMapperTest {
    @Test
    fun previewCanvasUsesArcCreateResultScreenReference() {
        val snapshot = ArcCreateResultLayoutSnapshots.ResultScreen

        assertEquals(1920f, CharacterPreviewCoordinateMapper.RESULT_REFERENCE_WIDTH)
        assertEquals(1080f, CharacterPreviewCoordinateMapper.RESULT_REFERENCE_HEIGHT)
        assertEquals(1920f, CharacterPreviewCoordinateMapper.RESULT_LOGICAL_WIDTH)
        assertEquals(1080f, CharacterPreviewCoordinateMapper.RESULT_LOGICAL_HEIGHT)
        assertEquals(0.75f, CharacterPreviewCoordinateMapper.RESULT_CANVAS_MATCH_WIDTH_OR_HEIGHT)
        assertEquals(1920f, snapshot.referenceWidth)
        assertEquals(1080f, snapshot.referenceHeight)
        assertEquals(1920f, snapshot.logicalWidth)
        assertEquals(1080f, snapshot.logicalHeight)
        assertEquals(0.75f, snapshot.canvasMatch)
    }

    @Test
    fun resultScreenLayoutSnapshotUsesArcCreateRectTransforms() {
        val snapshot = ArcCreateResultLayoutSnapshots.ResultScreen

        assertEquals(1519955448, snapshot.characterParent.fileId)
        assertEquals(597877295, snapshot.characterImage.fileId)
        assertEquals(0.5f, snapshot.characterParent.anchorMin.x)
        assertEquals(0.5f, snapshot.characterParent.anchorMin.y)
        assertEquals(-340.31818f, snapshot.characterParent.anchoredPosition.x, absoluteTolerance = 0.001f)
        assertEquals(-365f, snapshot.characterParent.anchoredPosition.y)
        assertEquals(-640.3182f, snapshot.characterParentFinalAnchoredPosition.x, absoluteTolerance = 0.001f)
        assertEquals(-365f, snapshot.characterParentFinalAnchoredPosition.y)
        assertEquals(0.5f, snapshot.characterImage.pivot.x)
        assertEquals(0.5f, snapshot.characterImage.pivot.y)
        assertEquals(2048f, snapshot.characterImageHeightBase)
        assertTrue(snapshot.characterParent.source.contains("Result.unity:6487-6507"))
        assertTrue(snapshot.characterImage.source.contains("ResultScreen.cs:157-163"))
    }

    @Test
    fun resultReferenceRectsComeFromArcCreateSprites() {
        val snapshot = ArcCreateResultLayoutSnapshots.ResultScreen

        assertTrue(snapshot.backgroundArrowRect.spritePath!!.endsWith("Background Arrow.png"))
        assertTrue(snapshot.jacketRect.spritePath!!.endsWith("Jacket Background.png"))
        assertTrue(snapshot.resultPanelRect.spritePath!!.endsWith("Judgement Table.png"))
        assertTrue(snapshot.judgementHighlightRect.spritePath!!.endsWith("Judgement Table Highlight.png"))
        assertTrue(snapshot.bottomScoreRect.spritePath!!.endsWith("Score Frame.png"))
        assertTrue(snapshot.playRetryRect.spritePath!!.endsWith("Play Retry Background.png"))
        assertTrue(snapshot.playRetryHighlightRect.spritePath!!.endsWith("Play Retry Frame.png"))
        assertTrue(snapshot.jacketRect.rect.width > 900f)
        assertTrue(snapshot.resultPanelRect.rect.width > 0f)
        assertTrue(snapshot.playRetryRect.source.contains("Result.unity:5952-5976"))
    }

    @Test
    fun xAndYFollowArcCreateAnchoredPositionDirection() {
        val base = CharacterPreviewCoordinateMapper.map(400f, 300f, 1024, 2048, 0f, 0f, 1f)
        val right = CharacterPreviewCoordinateMapper.map(400f, 300f, 1024, 2048, 100f, 0f, 1f)
        val up = CharacterPreviewCoordinateMapper.map(400f, 300f, 1024, 2048, 0f, 100f, 1f)

        assertTrue(right.pivotX > base.pivotX)
        assertTrue(up.pivotY < base.pivotY)
    }

    @Test
    fun scaleChangesSizeButKeepsPivotStable() {
        val scales = listOf(0.3f, 0.42f, 0.5f, 0.62f, 0.85f, 1f, 1.5f)
        val placements = scales.map {
            CharacterPreviewCoordinateMapper.map(400f, 300f, 8400, 3400, -300f, 200f, it)
        }

        placements.zipWithNext().forEach { (previous, next) ->
            assertTrue(next.width > previous.width)
            assertTrue(next.height > previous.height)
            assertEquals(previous.pivotX, next.pivotX, absoluteTolerance = 0.001f)
            assertEquals(previous.pivotY, next.pivotY, absoluteTolerance = 0.001f)
        }
    }

    @Test
    fun largeWideCharacterUsesOriginalAspectAndCanIntersectCanvas() {
        val placement = CharacterPreviewCoordinateMapper.mapLogical(8400, 3400, -300f, 200f, 0.85f)

        assertEquals(8400f / 3400f, placement.width / placement.height, absoluteTolerance = 0.001f)
        assertTrue(placement.width > CharacterPreviewCoordinateMapper.RESULT_LOGICAL_WIDTH)
        assertTrue(placement.intersectsCanvas)
        assertTrue(placement.visibleBounds.width > 0f)
        assertTrue(placement.visibleBounds.height > 0f)
    }

    @Test
    fun nailongUsesArcCreateResultScreenCoordinates() {
        val placement = CharacterPreviewCoordinateMapper.mapLogical(8400, 3400, -300f, 200f, 0.85f)

        assertEquals(19.6818f, placement.logicalPivot.x, absoluteTolerance = 0.01f)
        assertEquals(705f, placement.logicalPivot.y, absoluteTolerance = 0.01f)
        assertEquals(4300.8f, placement.logicalDrawRect.width, absoluteTolerance = 0.1f)
        assertEquals(1740.8f, placement.logicalDrawRect.height, absoluteTolerance = 0.1f)
        assertEquals(-2130.7183f, placement.logicalDrawRect.left, absoluteTolerance = 0.1f)
        assertEquals(-165.4f, placement.logicalDrawRect.top, absoluteTolerance = 0.1f)
    }

    @Test
    fun phoneAndTabletUseTheSameLogicalPlacement() {
        val logical = CharacterPreviewCoordinateMapper.mapLogical(8400, 3400, -300f, 200f, 0.85f)
        val phone = CharacterPreviewCoordinateMapper.map(360f, 202.5f, 8400, 3400, -300f, 200f, 0.85f)
        val tablet = CharacterPreviewCoordinateMapper.map(900f, 506.25f, 8400, 3400, -300f, 200f, 0.85f)
        val phoneScale = 360f / CharacterPreviewCoordinateMapper.RESULT_LOGICAL_WIDTH
        val tabletScale = 900f / CharacterPreviewCoordinateMapper.RESULT_LOGICAL_WIDTH

        assertEquals(logical.logicalDrawRect.left, phone.logicalDrawRect.left, absoluteTolerance = 0.01f)
        assertEquals(logical.logicalDrawRect.left, tablet.logicalDrawRect.left, absoluteTolerance = 0.01f)
        assertEquals(logical.logicalPivot.x, phone.logicalPivot.x, absoluteTolerance = 0.01f)
        assertEquals(logical.logicalPivot.x, tablet.logicalPivot.x, absoluteTolerance = 0.01f)
        assertEquals(logical.offsetX * phoneScale, phone.offsetX, absoluteTolerance = 0.01f)
        assertEquals(logical.offsetY * phoneScale, phone.offsetY, absoluteTolerance = 0.01f)
        assertEquals(logical.width * phoneScale, phone.width, absoluteTolerance = 0.01f)
        assertEquals(logical.height * phoneScale, phone.height, absoluteTolerance = 0.01f)
        assertEquals(logical.offsetX * tabletScale, tablet.offsetX, absoluteTolerance = 0.01f)
        assertEquals(logical.offsetY * tabletScale, tablet.offsetY, absoluteTolerance = 0.01f)
        assertEquals(logical.width * tabletScale, tablet.width, absoluteTolerance = 0.01f)
        assertEquals(logical.height * tabletScale, tablet.height, absoluteTolerance = 0.01f)
        assertEquals(phone.displayDrawRect.left, phone.offsetX)
        assertEquals(tablet.displayDrawRect.left, tablet.offsetX)
    }

    @Test
    fun differentSourceAspectsKeepTheirRatioWithoutFittingToCanvas() {
        val tall = CharacterPreviewCoordinateMapper.map(400f, 300f, 900, 2400, 0f, 0f, 0.7f)
        val wide = CharacterPreviewCoordinateMapper.map(400f, 300f, 2400, 900, 0f, 0f, 0.7f)
        val square = CharacterPreviewCoordinateMapper.map(400f, 300f, 1200, 1200, 0f, 0f, 0.7f)

        assertEquals(900f / 2400f, tall.width / tall.height, absoluteTolerance = 0.001f)
        assertEquals(2400f / 900f, wide.width / wide.height, absoluteTolerance = 0.001f)
        assertEquals(1f, square.width / square.height, absoluteTolerance = 0.001f)
        assertTrue(wide.width > 400f, "Wide character art should not be auto-fitted into the preview canvas.")
    }

    @Test
    fun charactersMayRenderOutsideTheCanvas() {
        val placement = CharacterPreviewCoordinateMapper.map(400f, 300f, 1200, 1200, 5000f, 5000f, 1f)

        assertFalse(placement.intersectsCanvas)
        assertEquals(0f, placement.visibleBounds.width)
        assertTrue(placement.debugInfo.contains("intersects=false"))
    }

    @Test
    fun comparisonReferenceImagesAreAvailableWhenPresent() {
        val samplesRoot = System.getenv("ETOILEBRIDGE_LOCAL_SAMPLES") ?: return
        val dir = File(samplesRoot, "搭档包/ArcCreate游戏内搭档对比")
        if (!dir.isDirectory) return

        val images = dir.listFiles { file -> file.extension.equals("png", ignoreCase = true) }.orEmpty()
        assertTrue(images.size >= 6, "Expected the ArcCreate comparison image set to be present.")
        assertTrue(images.any { it.nameWithoutExtension.equals("otto", ignoreCase = true) })
    }
}
