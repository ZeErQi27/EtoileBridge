package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.Union
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.io.ScenecontrolSerialization

/**
 * Assets/Scripts/Gameplay/Scenecontrol/Channels/MathChannels/InverseChannel.cs
 */
class InverseChannel(private val target: ValueChannel) : ValueChannel() {
    override fun valueAt(timing: Long): Float = 1 / target.valueAt(timing)

    override fun serializeProperties(serialization: ScenecontrolSerialization): List<Union> =
        listOf(
            Union(serialization.addUnitAndGetId(target))
        )

    override fun getChildrenChannels(): Iterable<ValueChannel> = listOf(target)
}