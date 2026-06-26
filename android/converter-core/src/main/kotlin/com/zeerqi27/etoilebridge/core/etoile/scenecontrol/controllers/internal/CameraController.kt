package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.internal

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.ConstantChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.const
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.Controller
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.ICameraController
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.IPositionController

/**
 * Assets/Scripts/Gameplay/Scenecontrol/Controllers/Internal/CameraController.cs
 */
class CameraController : Controller(), ICameraController, IPositionController {
    override var active: ValueChannel = 1f.const()

    override var enableCameraModule: Boolean = false
    override var fieldOfView: ValueChannel = ConstantChannel(0f)
        set(value) {
            field = value
            enableCameraModule = true
        }
    override var tiltFactor: ValueChannel = ConstantChannel(1f)
        set(value) {
            field = value
            enableCameraModule = true
        }

    override var enablePositionModule: Boolean = false
    override var translationX: ValueChannel = ConstantChannel(0f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var translationY: ValueChannel = ConstantChannel(0f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var translationZ: ValueChannel = ConstantChannel(0f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var rotationX: ValueChannel = ConstantChannel(0f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var rotationY: ValueChannel = ConstantChannel(0f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var rotationZ: ValueChannel = ConstantChannel(0f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var scaleX: ValueChannel = ConstantChannel(1f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var scaleY: ValueChannel = ConstantChannel(1f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var scaleZ: ValueChannel = ConstantChannel(1f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var serializedType: String = "camera"

}