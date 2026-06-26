package com.zeerqi27.etoilebridge.core.etoile.scenecontrol

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.context.ScreenIs16By9Channel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.ConstantChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.io.ISerializableUnit
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.io.ScenecontrolSerialization

/**
 * Assets/Scripts/Gameplay/Scenecontrol/Context.cs
 */
class Context(private val scenecontrolService: ScenecontrolService) : ISerializableUnit, ISceneController {

    var laneFrom: ValueChannel = ConstantChannel(1f)
        set(value) {
            field = value
            scenecontrolService.addReferencedController(this)
        }

    var laneTo: ValueChannel = ConstantChannel(4f)
        set(value) {
            field = value
            scenecontrolService.addReferencedController(this)
        }

    companion object {
        val is16By9 = ScreenIs16By9Channel()
    }

    override var serializedType: String = "context"

    override fun serializeProperties(serialization: ScenecontrolSerialization): List<Union> =
        listOf(
            Union(serialization.addUnitAndGetId(laneFrom)),
            Union(serialization.addUnitAndGetId(laneTo))
        )
}