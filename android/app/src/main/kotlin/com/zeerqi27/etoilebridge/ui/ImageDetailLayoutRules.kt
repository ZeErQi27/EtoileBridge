package com.zeerqi27.etoilebridge.ui

enum class ImageDetailLayoutMode {
    Compact,
    Medium,
    Expanded,
}

object ImageDetailLayoutRules {
    const val MediumWidthDp = 600
    const val ExpandedWidthDp = 840
    const val DetailContentScale = "Fit"
    const val PathInitiallyExpanded = false

    fun modeForWidth(widthDp: Int): ImageDetailLayoutMode =
        when {
            widthDp < MediumWidthDp -> ImageDetailLayoutMode.Compact
            widthDp < ExpandedWidthDp -> ImageDetailLayoutMode.Medium
            else -> ImageDetailLayoutMode.Expanded
        }

    fun useSideBySide(widthDp: Int): Boolean =
        modeForWidth(widthDp) == ImageDetailLayoutMode.Expanded

    fun dialogMaxWidthDp(widthDp: Int): Int =
        when (modeForWidth(widthDp)) {
            ImageDetailLayoutMode.Compact -> 560
            ImageDetailLayoutMode.Medium -> 720
            ImageDetailLayoutMode.Expanded -> 1040
        }

    fun imageMaxHeightDp(widthDp: Int): Int =
        when (modeForWidth(widthDp)) {
            ImageDetailLayoutMode.Compact -> 300
            ImageDetailLayoutMode.Medium -> 420
            ImageDetailLayoutMode.Expanded -> 520
        }
}
