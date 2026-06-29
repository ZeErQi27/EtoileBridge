package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.Union
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.io.ScenecontrolSerialization

/**
 * Assets/Scripts/Gameplay/Scenecontrol/Channels/MathChannels/ConstantChannel.cs
 */
class ConstantChannel(private var value: Float) : ValueChannel() {
    override fun valueAt(timing: Long): Float = value

    override fun serializeProperties(serialization: ScenecontrolSerialization): List<Union> = listOf(Union(value))

    override fun getChildrenChannels(): Iterable<ValueChannel> = emptyList()
}

fun Float.const(): ConstantChannel {
    return ConstantChannel(this)
}