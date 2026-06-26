package com.zeerqi27.etoilebridge.core.etoile.scenecontrol

import com.tairitsu.compose.Scenecontrol
import com.tairitsu.compose.TimingGroup
import com.zeerqi27.etoilebridge.core.etoile.EtoileJson
import com.zeerqi27.etoilebridge.core.etoile.EtoileJsonMinified
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.Scene
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.io.ScenecontrolSerialization
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.io.SerializedUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

class ScenecontrolService(
    val scenecontrols: List<Scenecontrol>,
    val timingGroups: List<TimingGroup>,
    private val ratingClass: Int,
) {
    private val referencedControllers: MutableList<ISceneController> = mutableListOf()

    var scene: Scene = Scene(this)
    val context: Context = Context(this)

    private fun processScenecontrol(scenecontrol: Scenecontrol) {
        val handler = ScenecontrolHandler.fromScenecontrolType(scenecontrol.type, scene)
        handler?.execute(scenecontrol)
    }

    fun addReferencedController(controller: ISceneController) {
        if (!referencedControllers.contains(controller)) {
            referencedControllers.add(controller)
        }
    }

    fun serialize(): List<SerializedUnit>? {
        scenecontrols.forEach {
            processScenecontrol(it)
        }

        val serialization = ScenecontrolSerialization()
        if (referencedControllers.isEmpty()) {
            ScenecontrolHandler.clearCache()
            scene.clearNoteGroupControllerCache()
            return null
        }

        referencedControllers.forEach {
            serialization.addUnitAndGetId(it)
        }

        // clear cache for next serialization
        ScenecontrolHandler.clearCache()
        scene.clearNoteGroupControllerCache()

        return serialization.getResult()
    }

    /**
     * Assets/Scripts/Gameplay/Scenecontrol/ScenecontrolService.cs#Export()
     */
    fun export(): String? {
        val rst = serialize() ?: return null

        return EtoileJsonMinified.encodeToString(rst)
    }
    fun exportPrettified(): String? {
        val rst = serialize() ?: return null

        return EtoileJson.encodeToString(rst)
    }
}