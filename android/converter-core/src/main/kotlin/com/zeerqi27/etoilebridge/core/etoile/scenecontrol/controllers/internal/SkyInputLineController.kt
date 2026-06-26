package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.internal

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.ConstantChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.const
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.common.SpriteController

class SkyInputLineController : SpriteController("skyinputline") {
    override var active: ValueChannel = 1f.const()

    override var translationY: ValueChannel = ConstantChannel(5.5f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var scaleX: ValueChannel = ConstantChannel(5000f)
        set(value) {
            field = value
            enablePositionModule = true
        }
}