package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.internal

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.const
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.common.SpriteController

class CriticalLineController(id: Int) : SpriteController("critline$id") {
    override var active: ValueChannel = if (id in 1..4) 1f.const() else 0f.const()

    override var translationX: ValueChannel = (-2.37f * id + 5.925f).const()
        set(value) {
            field = value
            enablePositionModule = true
        }

    override var translationY: ValueChannel = 54f.const()
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var rotationX: ValueChannel = 180f.const()
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var scaleX: ValueChannel = 239f.const()
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var scaleY: ValueChannel = 3.7f.const()
        set(value) {
            field = value
            enablePositionModule = true
        }
}