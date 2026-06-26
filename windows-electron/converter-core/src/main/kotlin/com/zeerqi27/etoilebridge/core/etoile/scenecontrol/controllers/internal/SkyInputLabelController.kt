package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.internal

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.ConstantChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.const
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.common.SpriteController

class SkyInputLabelController : SpriteController("skyinputlabel") {
    override var active: ValueChannel = 1f.const()

    override var translationX: ValueChannel = ConstantChannel(-7.1f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var translationY: ValueChannel = ConstantChannel(5.65f)
        set(value) {
            field = value
            enablePositionModule = true
        }
}