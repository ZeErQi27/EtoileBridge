package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.internal

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.ConstantChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.const
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.PartSide
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.common.SpriteController

class LaneExtraController(side: PartSide) : SpriteController("extra${side.char.uppercase()}") {
    override var active: ValueChannel = 0f.const()

    override var translationX: ValueChannel = ConstantChannel(-side.value * 5.96f)
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var scaleX: ValueChannel = 239f.const()
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var scaleY: ValueChannel = 15.35f.const()
        set(value) {
            field = value
            enablePositionModule = true
        }
}
