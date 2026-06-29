package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.internal

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.ValueChannel
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.math.const
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.controllers.common.SpriteController

class DarkenController : SpriteController("darken") {
    override var active: ValueChannel = 0f.const()

    override var colorR: ValueChannel = 0f.const()
        set(value) {
            field = value
            enableColorModule = true
        }
    override var colorG: ValueChannel = 0f.const()
        set(value) {
            field = value
            enableColorModule = true
        }
    override var colorB: ValueChannel = 0f.const()
        set(value) {
            field = value
            enableColorModule = true
        }
    override var colorA: ValueChannel = 255f.const()
        set(value) {
            field = value
            enableColorModule = true
        }
    override var colorV: ValueChannel = 0f.const()
        set(value) {
            field = value
            enableColorModule = true
        }

    override var scaleX: ValueChannel = 180f.const()
        set(value) {
            field = value
            enablePositionModule = true
        }
    override var scaleY: ValueChannel = 180f.const()
        set(value) {
            field = value
            enablePositionModule = true
        }
}