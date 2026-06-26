package com.zeerqi27.etoilebridge.core.etoile.scenecontrol.channels.string

import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.Union
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.io.ISerializableUnit
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.io.ScenecontrolSerialization

abstract class StringChannel : ISerializableUnit {
    abstract fun valueAt(timing: Long): String
    abstract override fun serializeProperties(serialization: ScenecontrolSerialization): List<Union>?
}