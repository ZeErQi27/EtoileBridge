package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.internal

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.ConstantChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.const
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.PartSide
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.common.SpriteController

class SingleLineController(side: PartSide) : SpriteController("singleline${side.char}") {
    override var active: ValueChannel = 0f.const()

    override var translationX: ValueChannel = ConstantChannel(-side.value * 4f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var translationY: ValueChannel = ConstantChannel(7.15f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var translationZ: ValueChannel = ConstantChannel(50f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var rotationX: ValueChannel = ConstantChannel(-side.value * 145f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var rotationY: ValueChannel = ConstantChannel(90f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var rotationZ: ValueChannel = ConstantChannel(-90f)
        set(value) {
            field = value
            enablePositionModule = true
        }
}
