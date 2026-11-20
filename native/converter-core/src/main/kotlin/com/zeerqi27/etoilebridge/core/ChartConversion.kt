package com.zeerqi27.etoilebridge.core

import java.io.File

fun interface ChartConverter {
    fun convert(source: File): ConvertedChart
}

data class ConvertedChart(val aff: String, val scenecontrolJson: String?)
