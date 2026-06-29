package com.zeerqi27.etoilebridge.core.etoile

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val EtoileJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

val EtoileJsonMinified = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
}

val EtoileYaml = Yaml(
    configuration = YamlConfiguration(
        allowAnchorsAndAliases = true,
        strictMode = false,
    )
)

@Serializable
enum class ArcpkgEntryType {
    @SerialName("level")
    LEVEL,

    @SerialName("pack")
    PACK,

    @SerialName("character")
    CHARACTER,
}

@Serializable
data class ImportInformationEntry(
    val directory: String,
    val identifier: String,
    val settingsFile: String,
    val version: Int = 0,
    val type: ArcpkgEntryType,
)

@Serializable
data class ProjectInformation(
    val lastOpenedChartPath: String,
    val charts: List<ChartEntry>,
)

@Serializable
data class ChartEntry(
    val chartPath: String,
    val audioPath: String,
    val jacketPath: String,
    val backgroundPath: String? = null,
    val baseBpm: Float,
    val bpmText: String,
    val syncBaseBpm: Boolean = false,
    val title: String,
    val composer: String,
    val alias: String? = null,
    val charter: String? = null,
    val illustrator: String? = null,
    val difficulty: String,
    val chartConstant: Float? = null,
    val difficultyColor: String,
    val skin: DifficultySkin? = DifficultySkin(),
    val previewStart: Long? = 0,
    val previewEnd: Long? = 5000,
    val searchTags: String? = null,
)

@Serializable
data class PackInformation(
    val imagePath: String,
    val levelIdentifiers: List<String>,
    val packName: String,
)

@Serializable
data class CharacterInformation(
    val name: Map<String, String>,
    val imagePath: String,
    val iconPath: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
)

@Serializable
data class DifficultySkin(
    val side: SideStyle = SideStyle.LIGHT,
    val note: NoteStyle = NoteStyle.NONE,
    val particle: ParticleStyle = ParticleStyle.NONE,
    val track: TrackStyle = TrackStyle.NONE,
    val accent: AccentStyle = AccentStyle.NONE,
    val singleLine: SingleLineStyle? = null,
) {
    @Serializable
    enum class SideStyle {
        @SerialName("light")
        LIGHT,

        @SerialName("conflict")
        CONFLICT,

        @SerialName("colorless")
        COLORLESS,

        @SerialName("lephon")
        LEPHON;

        fun toTrackStyle(): TrackStyle = when (this) {
            LIGHT -> TrackStyle.LIGHT
            CONFLICT -> TrackStyle.CONFLICT
            COLORLESS -> TrackStyle.COLORLESS
            LEPHON -> TrackStyle.LIGHT
        }

        fun toParticleStyle(): ParticleStyle = when (this) {
            LIGHT -> ParticleStyle.LIGHT
            CONFLICT -> ParticleStyle.CONFLICT
            COLORLESS -> ParticleStyle.COLORLESS
            LEPHON -> ParticleStyle.LIGHT
        }
    }

    @Serializable
    enum class NoteStyle {
        @SerialName("")
        NONE,

        @SerialName("light")
        LIGHT,

        @SerialName("conflict")
        CONFLICT,
    }

    @Serializable
    enum class ParticleStyle {
        @SerialName("")
        NONE,

        @SerialName("light")
        LIGHT,

        @SerialName("conflict")
        CONFLICT,

        @SerialName("colorless")
        COLORLESS,

        @SerialName("mirailight")
        MIRAI_LIGHT,

        @SerialName("miraiconflict")
        MIRAI_CONFLICT,
    }

    @Serializable
    enum class TrackStyle {
        @SerialName("")
        NONE,

        @SerialName("light")
        LIGHT,

        @SerialName("conflict")
        CONFLICT,

        @SerialName("black")
        BLACK,

        @SerialName("nijuusei")
        NIJUUSEI,

        @SerialName("rei")
        REI,

        @SerialName("conflictvs")
        CONFLICT_VS,

        @SerialName("tempestissimo")
        TEMPESTISSIMO,

        @SerialName("finale")
        FINALE,

        @SerialName("pentiment")
        PENTIMENT,

        @SerialName("arcana")
        ARCANA,

        @SerialName("colorless")
        COLORLESS,
    }

    @Serializable
    enum class AccentStyle {
        @SerialName("")
        NONE,

        @SerialName("light")
        LIGHT,

        @SerialName("conflict")
        CONFLICT,

        @SerialName("dynamix")
        DYNAMIX,

        @SerialName("colorless")
        COLORLESS,
    }

    @Serializable
    enum class SingleLineStyle {
        @SerialName("none")
        NONE,

        @SerialName("")
        NULL,

        @SerialName("light")
        LIGHT,

        @SerialName("conflict")
        CONFLICT,

        @SerialName("neo")
        NEO,
    }
}
