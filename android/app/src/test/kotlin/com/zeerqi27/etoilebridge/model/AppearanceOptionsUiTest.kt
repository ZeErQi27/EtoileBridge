package com.zeerqi27.etoilebridge.model

import com.zeerqi27.etoilebridge.ui.textFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppearanceOptionsUiTest {
    @Test
    fun arcCreateAppearanceExposesSupportedSkinGroups() {
        assertTrue(UiArcCreateParticle.entries.contains(UiArcCreateParticle.MiraiLight))
        assertTrue(UiArcCreateParticle.entries.contains(UiArcCreateParticle.MiraiConflict))
        assertTrue(UiArcCreateAccent.entries.contains(UiArcCreateAccent.Dynamix))
        assertTrue(UiArcCreateTrack.entries.contains(UiArcCreateTrack.Arcana))
    }

    @Test
    fun appearanceLabelsAreLocalized() {
        val zh = textFor(UiLanguage.ZhHans)
        val en = textFor(UiLanguage.English)

        assertEquals("粒子特效", zh.particle)
        assertEquals("判定线和连击数", zh.accent)
        assertEquals("轨道", zh.track)
        assertEquals("Particle", en.particle)
        assertEquals("Accent", en.accent)
        assertEquals("Track", en.track)
    }
}
