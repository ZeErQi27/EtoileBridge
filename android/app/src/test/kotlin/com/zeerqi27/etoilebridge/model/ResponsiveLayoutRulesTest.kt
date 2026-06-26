package com.zeerqi27.etoilebridge.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResponsiveLayoutRulesTest {
    @Test
    fun widthBreakpointsMapToLayoutModes() {
        assertEquals(UiLayoutMode.Compact, ResponsiveLayoutRules.modeForWidth(599))
        assertEquals(UiLayoutMode.Medium, ResponsiveLayoutRules.modeForWidth(600))
        assertEquals(UiLayoutMode.Medium, ResponsiveLayoutRules.modeForWidth(839))
        assertEquals(UiLayoutMode.Expanded, ResponsiveLayoutRules.modeForWidth(840))
    }

    @Test
    fun compactUsesSingleColumnAndExpandedUsesTwoColumns() {
        assertFalse(ResponsiveLayoutRules.useTwoColumns(412))
        assertTrue(ResponsiveLayoutRules.useTwoColumns(700))
        assertTrue(ResponsiveLayoutRules.useTwoColumns(1024))
    }

    @Test
    fun actionButtonsWrapInsteadOfRelyingOnHorizontalScrolling() {
        assertEquals(2, ResponsiveLayoutRules.maxActionsPerRow(412))
        assertEquals(3, ResponsiveLayoutRules.maxActionsPerRow(700))
        assertEquals(6, ResponsiveLayoutRules.maxActionsPerRow(1024))
        assertTrue(ResponsiveLayoutRules.usesWrappingActionRows(412))
    }

    @Test
    fun sharedScrollViewportDoesNotUseRoundedPageMask() {
        val sourceFile = listOf(
            File("src/main/kotlin/com/zeerqi27/etoilebridge/ui/AdaptiveLayout.kt"),
            File("app/src/main/kotlin/com/zeerqi27/etoilebridge/ui/AdaptiveLayout.kt"),
        ).first { it.isFile }
        val source = sourceFile.readText()
        assertTrue(source.contains("fun ScrollContentViewport"))
        assertFalse(source.contains("fun RoundedContentViewport"))
        assertFalse(source.contains("clip(RoundedCornerShape"))
    }
}
