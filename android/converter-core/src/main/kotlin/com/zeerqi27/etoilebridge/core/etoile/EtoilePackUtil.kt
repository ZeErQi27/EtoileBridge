package com.zeerqi27.etoilebridge.core.etoile

import com.zeerqi27.etoilebridge.core.AppearanceOptions
import com.zeerqi27.etoilebridge.core.ArcCreateAccent
import com.zeerqi27.etoilebridge.core.ArcCreateNote
import com.zeerqi27.etoilebridge.core.ArcCreateParticle
import com.zeerqi27.etoilebridge.core.ArcCreateSide
import com.zeerqi27.etoilebridge.core.ArcCreateSingleLine
import com.zeerqi27.etoilebridge.core.ArcCreateTrack

object EtoilePackUtil {
    fun getIdentifier(songId: String): String = songId

    fun getDifficultyColor(ratingClass: Int): String = when (ratingClass) {
        0 -> "#3A6B78FF"
        1 -> "#566947FF"
        3 -> "#7C1C30FF"
        4 -> "#433455FF"
        else -> "#482B54FF"
    }

    fun getSkin(
        side: Int,
        songId: String,
        setId: String,
        bg: String?,
        appearanceOptions: AppearanceOptions = AppearanceOptions(),
    ): DifficultySkin {
        val sideStyle = appearanceOptions.side?.toSkinSideStyle() ?: when (side) {
            0 -> DifficultySkin.SideStyle.LIGHT
            1 -> DifficultySkin.SideStyle.CONFLICT
            2 -> DifficultySkin.SideStyle.COLORLESS
            3 -> DifficultySkin.SideStyle.LIGHT
            else -> DifficultySkin.SideStyle.LIGHT
        }
        val qualifiedBg = bg ?: when (sideStyle) {
            DifficultySkin.SideStyle.LIGHT -> "base_light"
            DifficultySkin.SideStyle.CONFLICT -> "base_conflict"
            DifficultySkin.SideStyle.COLORLESS -> "epilogue"
            DifficultySkin.SideStyle.LEPHON -> "base_light"
        }
        val noteStyle = appearanceOptions.note.toSkinNoteStyle()
        val particleStyle = appearanceOptions.particle.toSkinParticleStyle()
        val inferredTrackStyle = when {
            qualifiedBg.startsWith("byd_") -> sideStyle.toTrackStyle()
            songId.startsWith("alexandrite") -> DifficultySkin.TrackStyle.BLACK
            qualifiedBg in setOf("dynamix_conflict", "mirai_conflict", "lethaeus", "mirai_awakened", "saikyostronger") ->
                DifficultySkin.TrackStyle.BLACK
            !qualifiedBg.startsWith("nijuusei") || qualifiedBg != "vs_conflict" -> when {
                songId.startsWith("etherstrike") -> DifficultySkin.TrackStyle.REI
                songId.startsWith("tempestissimo") -> DifficultySkin.TrackStyle.TEMPESTISSIMO
                qualifiedBg == "finale_conflict" || qualifiedBg == "alterego" -> DifficultySkin.TrackStyle.FINALE
                qualifiedBg == "pentiment" || qualifiedBg == "apophenia" -> DifficultySkin.TrackStyle.PENTIMENT
                qualifiedBg == "arcanaeden" -> DifficultySkin.TrackStyle.ARCANA
                else -> sideStyle.toTrackStyle()
            }
            else -> DifficultySkin.TrackStyle.NONE
        }
        val inferredAccentStyle = when {
            setId == "dynamix" || songId == "alexandrite" -> DifficultySkin.AccentStyle.DYNAMIX
            else -> DifficultySkin.AccentStyle.NONE
        }
        val trackStyle = appearanceOptions.track.toSkinTrackStyle() ?: inferredTrackStyle
        val accentStyle = appearanceOptions.accent.toSkinAccentStyle() ?: inferredAccentStyle
        val singleLineStyle = appearanceOptions.singleLine.toSkinSingleLineStyle()
        return DifficultySkin(sideStyle, noteStyle, particleStyle, trackStyle, accentStyle, singleLineStyle)
    }

    private fun ArcCreateSide.toSkinSideStyle(): DifficultySkin.SideStyle =
        when (this) {
            ArcCreateSide.LIGHT -> DifficultySkin.SideStyle.LIGHT
            ArcCreateSide.CONFLICT -> DifficultySkin.SideStyle.CONFLICT
            ArcCreateSide.COLORLESS -> DifficultySkin.SideStyle.COLORLESS
        }

    private fun ArcCreateNote.toSkinNoteStyle(): DifficultySkin.NoteStyle =
        when (this) {
            ArcCreateNote.INHERIT -> DifficultySkin.NoteStyle.NONE
            ArcCreateNote.LIGHT -> DifficultySkin.NoteStyle.LIGHT
            ArcCreateNote.CONFLICT -> DifficultySkin.NoteStyle.CONFLICT
        }

    private fun ArcCreateParticle.toSkinParticleStyle(): DifficultySkin.ParticleStyle =
        when (this) {
            ArcCreateParticle.INHERIT -> DifficultySkin.ParticleStyle.NONE
            ArcCreateParticle.LIGHT -> DifficultySkin.ParticleStyle.LIGHT
            ArcCreateParticle.CONFLICT -> DifficultySkin.ParticleStyle.CONFLICT
            ArcCreateParticle.MIRAI_LIGHT -> DifficultySkin.ParticleStyle.MIRAI_LIGHT
            ArcCreateParticle.MIRAI_CONFLICT -> DifficultySkin.ParticleStyle.MIRAI_CONFLICT
            ArcCreateParticle.COLORLESS -> DifficultySkin.ParticleStyle.COLORLESS
        }

    private fun ArcCreateAccent.toSkinAccentStyle(): DifficultySkin.AccentStyle? =
        when (this) {
            ArcCreateAccent.INHERIT -> null
            ArcCreateAccent.LIGHT -> DifficultySkin.AccentStyle.LIGHT
            ArcCreateAccent.CONFLICT -> DifficultySkin.AccentStyle.CONFLICT
            ArcCreateAccent.DYNAMIX -> DifficultySkin.AccentStyle.DYNAMIX
            ArcCreateAccent.COLORLESS -> DifficultySkin.AccentStyle.COLORLESS
        }

    private fun ArcCreateTrack.toSkinTrackStyle(): DifficultySkin.TrackStyle? =
        when (this) {
            ArcCreateTrack.INHERIT -> null
            ArcCreateTrack.LIGHT -> DifficultySkin.TrackStyle.LIGHT
            ArcCreateTrack.CONFLICT -> DifficultySkin.TrackStyle.CONFLICT
            ArcCreateTrack.BLACK -> DifficultySkin.TrackStyle.BLACK
            ArcCreateTrack.NIJUUSEI -> DifficultySkin.TrackStyle.NIJUUSEI
            ArcCreateTrack.REI -> DifficultySkin.TrackStyle.REI
            ArcCreateTrack.DARK_VS -> DifficultySkin.TrackStyle.CONFLICT_VS
            ArcCreateTrack.TEMPEST -> DifficultySkin.TrackStyle.TEMPESTISSIMO
            ArcCreateTrack.FINALE -> DifficultySkin.TrackStyle.FINALE
            ArcCreateTrack.PENTIMENT -> DifficultySkin.TrackStyle.PENTIMENT
            ArcCreateTrack.ARCANA -> DifficultySkin.TrackStyle.ARCANA
            ArcCreateTrack.COLORLESS -> DifficultySkin.TrackStyle.COLORLESS
        }

    private fun ArcCreateSingleLine.toSkinSingleLineStyle(): DifficultySkin.SingleLineStyle =
        when (this) {
            ArcCreateSingleLine.NONE -> DifficultySkin.SingleLineStyle.NONE
            ArcCreateSingleLine.LIGHT -> DifficultySkin.SingleLineStyle.LIGHT
            ArcCreateSingleLine.CONFLICT -> DifficultySkin.SingleLineStyle.CONFLICT
            ArcCreateSingleLine.NEO -> DifficultySkin.SingleLineStyle.NEO
        }
}
