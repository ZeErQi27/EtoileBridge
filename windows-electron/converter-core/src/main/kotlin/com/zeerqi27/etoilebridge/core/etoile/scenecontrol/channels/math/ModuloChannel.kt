package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.Union
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.io.ScenecontrolSerialization

/**
 * Assets/Scripts/Gameplay/Scenecontrol/Channels/MathChannels/ModuloChannel.cs
 */
class ModuloChannel(private val a: ValueChannel, private val b: ValueChannel) : ValueChannel() {
    override fun valueAt(timing: Long): Float = a.valueAt(timing) % b.valueAt(timing)

    override fun serializeProperties(serialization: ScenecontrolSerialization): List<Union> =
        listOf(
            Union(serialization.addUnitAndGetId(a)),
            Union(serialization.addUnitAndGetId(b))
        )

    override fun getChildrenChannels(): Iterable<ValueChannel> = listOf(a, b)
}