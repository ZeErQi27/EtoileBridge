package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.internal

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.ConstantChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.const
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.PartSide
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.common.SpriteController

class EdgeExtraController(side: PartSide) : SpriteController("edgeextra${side.char.uppercase()}") {
    override var active: ValueChannel = 0f.const()

    override var translationX: ValueChannel = ConstantChannel(-side.value * 7.33f)
        set(value) {
            field = value
            enablePositionModule = true
        }
}
