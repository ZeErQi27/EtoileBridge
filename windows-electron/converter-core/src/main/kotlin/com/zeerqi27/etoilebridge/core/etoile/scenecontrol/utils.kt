package com.zeerqi27.etoilebridge.core.etoile.scenecontrol

import com.tairitsu.compose.Scenecontrol
import com.tairitsu.compose.TimingGroup
import com.tairitsu.compose.parser.ArcaeaChartParser

/**
 * Assets/Scripts/Gameplay/Chart/ChartService.cs#LoadChart()
 */
fun loadChart(content: String): List<TimingGroup> = ArcaeaChartParser.parse(content).let { chart ->
    listOf(chart.mainTiming) + chart.subTiming.values
}

fun extractScenecontrols(tgChart: List<TimingGroup>) = tgChart.map {
    it.getScenecontrols()
}.fold(listOf<Scenecontrol>()) { a, b ->
    val reduceResult = a + b
    reduceResult
}.let {
    // ScenecontrolService.cs#RebuildList()
    it.sortedWith { o1, o2 -> o1.time.compareTo(o2.time) }
}
