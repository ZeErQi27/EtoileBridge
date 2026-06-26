package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.internal

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.ConstantChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.const
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.common.SpriteController

class DivideLineController(id: Int) : SpriteController("divline$id${id + 1}") {
    override var active: ValueChannel = if (id in 1..3) 1f.const() else 0f.const()

    override var translationX: ValueChannel = (-2.38f * id + 4.76f).const()
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var scaleX: ValueChannel = ConstantChannel(0.5587685f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var scaleY: ValueChannel = ConstantChannel(12.43724f)
        set(value) {
            field = value
            enablePositionModule = true
        }
}