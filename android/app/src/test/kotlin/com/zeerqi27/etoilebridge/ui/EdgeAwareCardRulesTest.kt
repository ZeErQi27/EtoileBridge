package com.zeerqi27.etoilebridge.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EdgeAwareCardRulesTest {
    @Test
    fun radiusNeverExceedsHalfVisibleHeight() {
        assertEquals(12f, edgeAwareRadiusPx(baseRadiusPx = 28f, visibleHeightPx = 24f))
        assertEquals(28f, edgeAwareRadiusPx(baseRadiusPx = 28f, visibleHeightPx = 100f))
        assertEquals(0f, edgeAwareRadiusPx(baseRadiusPx = 28f, visibleHeightPx = 0f))
    }

    @Test
    fun cropTopIsClampedWithoutChangingLayoutHeight() {
        assertEquals(0f, edgeAwareCropTopPx(viewportTopPx = 0f, cardTopPx = 24f, cardHeightPx = 200f))
        assertEquals(40f, edgeAwareCropTopPx(viewportTopPx = 64f, cardTopPx = 24f, cardHeightPx = 200f))
        assertEquals(200f, edgeAwareCropTopPx(viewportTopPx = 300f, cardTopPx = 24f, cardHeightPx = 200f))
    }

    @Test
    fun edgeAwareCardDoesNotUseDynamicLayoutHeightForCropping() {
        val source = projectFile("src/main/kotlin/com/zeerqi27/etoilebridge/ui/AdaptiveLayout.kt").readText()
        val body = source.substringAfter("fun EdgeAwareCard(").substringBefore("@Composable\nfun")

        assertFalse(body.contains("height(with(density) { visibleHeightPx.toDp() })"))
        assertFalse(body.contains("requiredHeight"))
        assertFalse(body.contains("heightIn(max"))
        assertFalse(body.contains("Modifier.height(visibleHeight"))
        assertFalse(body.contains("Modifier.requiredHeight(visibleHeight"))
        assertTrue(body.contains("drawWithContent"), "Edge-aware clipping should stay in the draw phase.")
    }

    @Test
    fun shapeTokensKeepLayeredCardRadiiConsistent() {
        assertTrue(EtoileShapeTokens.TopBar.value >= EtoileShapeTokens.HeroCard.value)
        assertTrue(EtoileShapeTokens.HeroCard.value >= EtoileShapeTokens.SectionCard.value)
        assertTrue(EtoileShapeTokens.SectionCard.value >= EtoileShapeTokens.InnerCard.value)
        assertTrue(EtoileShapeTokens.ImagePreview.value == EtoileShapeTokens.InnerCard.value)
    }

    @Test
    fun arcCreateResultPreviewUsesRealResultResources() {
        val resourceDir = projectFile("src/main/res/drawable-nodpi")
        val expected = listOf(
            "ac_result_background_arrow.png",
            "ac_result_jacket_background.png",
            "ac_result_score_frame.png",
            "ac_result_play_retry_background.png",
            "ac_result_play_retry_frame.png",
            "ac_result_clear_glow.png",
            "ac_result_judgement_table.png",
            "ac_result_judgement_table_highlight.png",
        )

        expected.forEach { assertTrue(File(resourceDir, it).isFile, "$it should be copied from ArcCreate result textures.") }
    }

    @Test
    fun edgeAwareCardIsNotUsedInsideCharacterPreviewInternals() {
        val source = projectFile("src/main/kotlin/com/zeerqi27/etoilebridge/ui/CharacterHomeScreen.kt").readText()
        val previewBody = source.substringAfter("private fun CharacterResultPreviewCanvas(")
        val beforeNextComposable = previewBody.substringBefore("@Composable\nprivate fun NumericSliderField")
        val cropBody = source.substringAfter("private fun CharacterIconCropCard(")
        val beforePreview = cropBody.substringBefore("@Composable\nprivate fun CharacterPositionCard")

        assertFalse(beforeNextComposable.contains("EdgeAwareCard("), "Result preview internals must not use edge-aware clipping.")
        assertFalse(beforePreview.contains("EdgeAwareCard("), "Icon crop preview internals must not use edge-aware clipping.")
    }

    @Test
    fun edgeAwareCardIsNotUsedInsidePackEntryEditors() {
        val source = projectFile("src/main/kotlin/com/zeerqi27/etoilebridge/ui/PackHomeScreen.kt").readText()
        source.functionBodies("private fun PackEntryRow(").forEach {
            assertFalse(it.contains("EdgeAwareCard("), "Pack entry rows are nested inside the level-list section and must stay static.")
        }
        source.functionBodies("private fun PackEntryEditor(").forEach {
            assertFalse(it.contains("EdgeAwareCard("), "Pack entry text fields must not use edge-aware clipping.")
        }
        source.functionBodies("private fun PackChartEditor(").forEach {
            assertFalse(it.contains("EdgeAwareCard("), "Chart cards, switches, and text fields must not use edge-aware clipping.")
        }
    }

    @Test
    fun nestedControlSectionsDoNotReceiveEdgeAwareClipping() {
        val home = projectFile("src/main/kotlin/com/zeerqi27/etoilebridge/ui/HomeScreen.kt").readText()
        val pack = projectFile("src/main/kotlin/com/zeerqi27/etoilebridge/ui/PackHomeScreen.kt").readText()
        val character = projectFile("src/main/kotlin/com/zeerqi27/etoilebridge/ui/CharacterHomeScreen.kt").readText()

        listOf(
            home to listOf("private fun ChoiceRow(", "private fun ResourceImageCard(", "private fun ImagePreviewBox(", "private fun ManualResourcePanel(", "private fun MetadataField(", "private fun DifficultyEditor(", "private fun MappingChip("),
            pack to listOf("private fun PackImageSelector(", "private fun PackEntryRow(", "private fun PackEntryEditor(", "private fun PackChartEditor("),
            character to listOf("private fun CharacterCropControls(", "private fun CharacterIconPreview(", "private fun CharacterCropPreviewCanvas(", "private fun CharacterResultPreviewCanvas(", "private fun NumericSliderField(", "private fun NumericValueField("),
        ).forEach { (source, markers) ->
            markers.forEach { marker ->
                source.functionBodies(marker).forEach {
                    assertFalse(it.contains("EdgeAwareCard("), "$marker must use static inner surfaces and controls only.")
                }
            }
        }
    }

    private fun projectFile(pathInApp: String): File {
        val fromAppModule = File(pathInApp)
        if (fromAppModule.exists()) return fromAppModule
        return File("app/$pathInApp")
    }

    private fun String.functionBodies(marker: String): List<String> {
        val bodies = mutableListOf<String>()
        var start = indexOf(marker)
        while (start >= 0) {
            val next = indexOf("\n@Composable", start + marker.length).let { if (it == -1) length else it }
            bodies += substring(start, next)
            start = indexOf(marker, start + marker.length)
        }
        return bodies
    }
}
