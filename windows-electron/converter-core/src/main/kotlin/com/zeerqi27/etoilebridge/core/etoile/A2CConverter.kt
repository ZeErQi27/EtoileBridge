package com.zeerqi27.etoilebridge.core.etoile

import com.tairitsu.compose.Chart
import com.tairitsu.compose.EventFilter
import com.tairitsu.compose.TimingGroupSpecialEffectFilter
import com.tairitsu.compose.filter.ShimFilter
import com.tairitsu.compose.parser.ArcaeaChartParser

class A2CConverter : ArcaeaChartParser() {
    override val globalEffectFilter: TimingGroupSpecialEffectFilter = ShimFilter.A2C
    override val globalEventFilter: EventFilter = ShimFilter.A2C

    companion object {
        val Instance: A2CConverter by lazy { A2CConverter() }
        fun parse(content: String): Chart = Instance.parse(content)
    }
}
