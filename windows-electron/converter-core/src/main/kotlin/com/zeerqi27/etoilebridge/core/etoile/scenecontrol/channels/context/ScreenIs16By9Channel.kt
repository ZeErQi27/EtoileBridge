package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.context

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.Union
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.io.ScenecontrolSerialization

class ScreenIs16By9Channel : ValueChannel() {
    override fun valueAt(timing: Long): Float = 0f

    override fun serializeProperties(serialization: ScenecontrolSerialization): List<Union>? = null

    override fun getChildrenChannels(): Iterable<ValueChannel> = listOf()
}