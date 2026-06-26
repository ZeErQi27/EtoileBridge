package com.zeerqi27.etoilebridge.model

data class UiConvertOptions(
    val enableDeleteDesignantLine: Boolean = true,
    val enableFixZeroDurationArcTap: Boolean = true,
    val enableFixReversedArcTime: Boolean = true,
    val enableExpandArcResolution: Boolean = true,
)
