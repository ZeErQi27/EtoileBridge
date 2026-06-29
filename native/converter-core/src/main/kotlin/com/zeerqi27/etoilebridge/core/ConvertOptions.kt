package com.zeerqi27.etoilebridge.core

data class ConvertOptions(
    val enableDeleteDesignantLine: Boolean = true,
    val enableFixZeroDurationArcTap: Boolean = true,
    val enableFixReversedArcTime: Boolean = true,
    val enableExpandArcResolution: Boolean = true,
    val keepWorkspaceOnFailure: Boolean = true,
    val cleanWorkspaceOnSuccess: Boolean = true,
)
