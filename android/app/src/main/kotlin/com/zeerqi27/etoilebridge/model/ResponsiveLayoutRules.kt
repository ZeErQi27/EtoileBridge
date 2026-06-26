package com.zeerqi27.etoilebridge.model

enum class UiLayoutMode {
    Compact,
    Medium,
    Expanded,
}

object ResponsiveLayoutRules {
    const val TopContentPaddingDp = 20
    const val ContentViewportCornerDp = 32

    fun modeForWidth(widthDp: Int): UiLayoutMode =
        when {
            widthDp < 600 -> UiLayoutMode.Compact
            widthDp < 840 -> UiLayoutMode.Medium
            else -> UiLayoutMode.Expanded
        }

    fun useTwoColumns(widthDp: Int): Boolean =
        modeForWidth(widthDp) != UiLayoutMode.Compact

    fun maxActionsPerRow(widthDp: Int): Int =
        when (modeForWidth(widthDp)) {
            UiLayoutMode.Compact -> 2
            UiLayoutMode.Medium -> 3
            UiLayoutMode.Expanded -> 6
        }

    fun usesWrappingActionRows(widthDp: Int): Boolean =
        maxActionsPerRow(widthDp) < Int.MAX_VALUE
}
