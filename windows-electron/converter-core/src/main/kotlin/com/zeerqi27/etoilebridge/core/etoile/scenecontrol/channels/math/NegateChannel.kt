package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.Union
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.io.ScenecontrolSerialization

/**
 * Assets/Scripts/Gameplay/Scenecontrol/Channels/MathChannels/NegateChannel.cs
 */
class NegateChannel(private val target: ValueChannel) : ValueChannel() {
    override fun valueAt(timing: Long): Float = -target.valueAt(timing)

    override fun serializeProperties(serialization: ScenecontrolSerialization): List<Union> =
        listOf(Union(serialization.addUnitAndGetId(target)))

    override fun getChildrenChannels(): Iterable<ValueChannel> = listOf(target)
}