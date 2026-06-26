package com.zeerqi27.etoilebridge.core

import java.io.File

data class ConvertInput(
    val workspaceDir: File,
    val outputFile: File,
    val manualMetadata: ManualMetadata? = null,
    val targetSongId: String? = null,
    val resourceOverrides: ManualResourceOverrides? = null,
    val chartOverrides: ManualChartOverrides? = null,
    val packageOptions: PackageOptions = PackageOptions(),
    val appearanceOptions: AppearanceOptions = AppearanceOptions(),
)

data class PackageOptions(
    val directoryName: String? = null,
    val publisherId: String = "etoilebridge",
    val levelId: String? = null,
    val identifier: String? = null,
) {
    fun resolvedIdentifier(songId: String): String {
        identifier?.takeIf { it.isNotBlank() }?.let { return it }
        val publisher = publisherId.takeIf { it.isNotBlank() } ?: "etoilebridge"
        val level = levelId?.takeIf { it.isNotBlank() } ?: songId
        return "$publisher.$level"
    }
}

data class AppearanceOptions(
    val side: ArcCreateSide? = null,
    val note: ArcCreateNote = ArcCreateNote.INHERIT,
    val particle: ArcCreateParticle = ArcCreateParticle.INHERIT,
    val accent: ArcCreateAccent = ArcCreateAccent.INHERIT,
    val track: ArcCreateTrack = ArcCreateTrack.INHERIT,
    val singleLine: ArcCreateSingleLine = ArcCreateSingleLine.NONE,
)

enum class ArcCreateSide {
    LIGHT,
    CONFLICT,
    COLORLESS,
}

enum class ArcCreateNote {
    INHERIT,
    LIGHT,
    CONFLICT,
}

enum class ArcCreateParticle {
    INHERIT,
    LIGHT,
    CONFLICT,
    MIRAI_LIGHT,
    MIRAI_CONFLICT,
    COLORLESS,
}

enum class ArcCreateAccent {
    INHERIT,
    LIGHT,
    CONFLICT,
    DYNAMIX,
    COLORLESS,
}

enum class ArcCreateTrack {
    INHERIT,
    LIGHT,
    CONFLICT,
    BLACK,
    NIJUUSEI,
    REI,
    DARK_VS,
    TEMPEST,
    FINALE,
    PENTIMENT,
    ARCANA,
    COLORLESS,
}

enum class ArcCreateSingleLine {
    NONE,
    LIGHT,
    CONFLICT,
    NEO,
}

data class ManualResourceOverrides(
    val audioFile: File? = null,
    val jacketFile: File? = null,
    val backgroundFile: File? = null,
    val songlistFile: File? = null,
    val packlistFile: File? = null,
)

data class ManualChartOverrides(
    val adoptedAffByRatingClass: Map<Int, File> = emptyMap(),
    val ignoredAffFiles: Set<File> = emptySet(),
)

data class ManualMetadata(
    val songId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val bpmText: String? = null,
    val bpmBase: Float? = null,
    val set: String? = null,
    val side: Int? = null,
    val bg: String? = null,
    val bgInverse: String? = null,
    val audioPreview: Long? = null,
    val audioPreviewEnd: Long? = null,
    val difficulties: List<ManualDifficultyMetadata> = emptyList(),
)

data class ManualDifficultyMetadata(
    val ratingClass: Int,
    val chartDesigner: String? = null,
    val jacketDesigner: String? = null,
    val alias: String? = null,
    val rating: Int? = null,
    val ratingPlus: Boolean? = null,
    val difficulty: String? = null,
    val chartConstant: Float? = null,
    val title: String? = null,
    val artist: String? = null,
    val bpmText: String? = null,
    val bpmBase: Float? = null,
    val bg: String? = null,
    val bgInverse: String? = null,
)
