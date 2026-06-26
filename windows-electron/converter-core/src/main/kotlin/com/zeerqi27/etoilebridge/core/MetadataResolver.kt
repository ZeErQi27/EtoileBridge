package com.zeerqi27.etoilebridge.core

import java.io.File

sealed class MetadataResolution {
    data class Resolved(val metadata: ResolvedSongMetadata) : MetadataResolution()
    data class Need(val missingMetadata: MissingMetadata) : MetadataResolution()
}

data class ResolvedSongMetadata(
    val songId: String,
    val title: String,
    val artist: String,
    val bpmText: String,
    val bpmBase: Float,
    val set: String,
    val side: Int,
    val bg: String?,
    val bgInverse: String?,
    val audioPreview: Long,
    val audioPreviewEnd: Long,
    val additionalFiles: List<String>,
    val pack: PacklistPack?,
    val searchTags: String,
    val difficulties: List<ResolvedDifficultyMetadata>,
)

data class ResolvedDifficultyMetadata(
    val ratingClass: Int,
    val chartDesigner: String,
    val jacketDesigner: String,
    val alias: String? = null,
    val rating: Int?,
    val ratingPlus: Boolean,
    val difficulty: String? = null,
    val chartConstant: Float? = null,
    val jacketOverride: Boolean,
    val audioOverride: Boolean,
    val bg: String?,
    val bgInverse: String?,
    val title: String?,
    val artist: String?,
    val bpmText: String?,
    val bpmBase: Float?,
)

enum class SonglistMatchMode {
    EXACT,
    IGNORE_CASE,
    SINGLE_OBJECT_FALLBACK,
    NONE,
}

data class SonglistMatch(
    val song: SonglistSong?,
    val mode: SonglistMatchMode,
    val songRootName: String,
    val songlistId: String?,
    val warning: String? = null,
)

class MetadataResolver {
    fun resolve(
        songDir: File,
        affFiles: Map<Int, File>,
        requestedSongId: String?,
        songlist: Songlist?,
        packlist: Packlist?,
        manualMetadata: ManualMetadata?,
        warnings: MutableList<String>? = null,
    ): MetadataResolution {
        val match = matchSong(songDir, requestedSongId, songlist)
        match.warning?.let { warning ->
            if (warnings?.contains(warning) == false) warnings += warning
        }
        val song = match.song
        val manualSongId = manualMetadata?.songId ?: requestedSongId
        val automaticSongId = when (match.mode) {
            SonglistMatchMode.EXACT -> song?.id
            SonglistMatchMode.IGNORE_CASE -> requestedSongId ?: songDir.name.takeIf { it.isNotBlank() } ?: song?.id
            SonglistMatchMode.SINGLE_OBJECT_FALLBACK -> requestedSongId ?: song?.id ?: songDir.name.takeIf { it.isNotBlank() }
            SonglistMatchMode.NONE -> songDir.name.takeIf { it.isNotBlank() }
        }
        val songId = manualSongId ?: automaticSongId

        val missing = mutableListOf<String>()
        if (songlist == null && manualMetadata == null) {
            val required = mutableListOf("title", "artist", "bpm", "bpm_base", "set", "side", "difficulties")
            if (songId.isNullOrBlank()) required.add(0, "songId")
            return MetadataResolution.Need(
                MissingMetadata(
                    reason = "No songlist was found. Manual metadata is required before packing.",
                    requiredFields = required,
                )
            )
        }

        if (songId.isNullOrBlank()) missing += "songId"
        val title = manualMetadata?.title ?: song?.titleLocalized?.preferred() ?: song?.title
        val artist = manualMetadata?.artist ?: song?.artist ?: song?.composer
        val bpmText = manualMetadata?.bpmText ?: song?.bpmText
        val bpmBase = manualMetadata?.bpmBase ?: song?.bpmBase
        val set = manualMetadata?.set ?: song?.set
        val side = manualMetadata?.side ?: song?.side

        if (title.isNullOrBlank()) missing += if (manualMetadata == null) "title_localized" else "title"
        if (artist.isNullOrBlank()) missing += if (manualMetadata == null) "artist" else "composer"
        if (bpmText.isNullOrBlank() && manualMetadata == null) missing += "bpm"
        if (bpmBase == null || bpmBase <= 0f) missing += if (manualMetadata == null) "bpm_base" else "baseBpm"
        if (set == null && manualMetadata == null) missing += "set"
        if (side == null && manualMetadata == null) missing += "side"

        val difficultyMetadata = affFiles.keys.sorted().map { ratingClass ->
            resolveDifficulty(ratingClass, song, manualMetadata)
        }

        if (difficultyMetadata.isEmpty()) missing += "aff"
        difficultyMetadata.forEach { diff ->
            if (diff.difficulty.isNullOrBlank()) missing += "difficulties[${diff.ratingClass}].difficulty"
            if (diff.chartConstant == null) missing += "difficulties[${diff.ratingClass}].chartConstant"
        }

        if (missing.isNotEmpty()) {
            return MetadataResolution.Need(
                MissingMetadata(
                    reason = "Songlist is missing fields needed to generate ArcCreate metadata.",
                    requiredFields = missing.distinct(),
                )
            )
        }

        val pack = packlist?.packs?.firstOrNull { it.id == set }
        val searchTags = buildSearchTags(song)

        return MetadataResolution.Resolved(
            ResolvedSongMetadata(
                songId = songId.orEmpty(),
                title = title.orEmpty(),
                artist = artist.orEmpty(),
                bpmText = bpmText ?: bpmBase?.toString().orEmpty(),
                bpmBase = bpmBase ?: 0f,
                set = set.orEmpty(),
                side = side ?: 0,
                bg = manualMetadata?.bg ?: song?.bg,
                bgInverse = manualMetadata?.bgInverse ?: song?.bgInverse,
                audioPreview = manualMetadata?.audioPreview ?: song?.audioPreview ?: 0,
                audioPreviewEnd = manualMetadata?.audioPreviewEnd ?: song?.audioPreviewEnd ?: 5000,
                additionalFiles = song?.additionalFiles.orEmpty(),
                pack = pack,
                searchTags = searchTags,
                difficulties = difficultyMetadata,
            )
        )
    }

    fun matchSong(songDir: File, requestedSongId: String?, songlist: Songlist?): SonglistMatch {
        val songs = songlist?.songs.orEmpty().filter { it.deleted != true }
        val targetId = requestedSongId?.takeIf { it.isNotBlank() } ?: songDir.name
        val exact = songs.firstOrNull { it.id == targetId }
        if (exact != null) {
            return SonglistMatch(
                song = exact,
                mode = SonglistMatchMode.EXACT,
                songRootName = songDir.name,
                songlistId = exact.id,
            )
        }

        val ignoreCase = songs.firstOrNull { it.id?.equals(targetId, ignoreCase = true) == true }
        if (ignoreCase != null) {
            return SonglistMatch(
                song = ignoreCase,
                mode = SonglistMatchMode.IGNORE_CASE,
                songRootName = songDir.name,
                songlistId = ignoreCase.id,
                warning = "songRoot name differs from songlist id only by case: ${songDir.name} vs ${ignoreCase.id}",
            )
        }

        if (songs.size == 1) {
            val only = songs.single()
            return SonglistMatch(
                song = only,
                mode = SonglistMatchMode.SINGLE_OBJECT_FALLBACK,
                songRootName = songDir.name,
                songlistId = only.id,
                warning = "songlist id does not match song folder, using the only song object as metadata source.",
            )
        }

        return SonglistMatch(
            song = null,
            mode = SonglistMatchMode.NONE,
            songRootName = songDir.name,
            songlistId = null,
        )
    }

    private fun resolveDifficulty(
        ratingClass: Int,
        song: SonglistSong?,
        manualMetadata: ManualMetadata?,
    ): ResolvedDifficultyMetadata {
        val source = song?.difficulties?.firstOrNull { it.ratingClass == ratingClass }
        val manual = manualMetadata?.difficulties?.firstOrNull { it.ratingClass == ratingClass }
        val rating = manual?.rating ?: source?.rating
        val ratingPlus = manual?.ratingPlus ?: source?.ratingPlus ?: false
        return ResolvedDifficultyMetadata(
            ratingClass = ratingClass,
            chartDesigner = manual?.chartDesigner ?: source?.chartDesigner.orEmpty(),
            jacketDesigner = manual?.jacketDesigner ?: source?.jacketDesigner.orEmpty(),
            alias = manual?.alias,
            rating = rating,
            ratingPlus = ratingPlus,
            difficulty = manual?.difficulty ?: rating?.let { DifficultyMapper.displayName(ratingClass, it, ratingPlus) },
            chartConstant = manual?.chartConstant
                ?: source?.chartConstant
                ?: source?.ratingReal
                ?: source?.chartConstantSnake
                ?: source?.ratingRealSnake
                ?: DifficultyMapper.chartConstant(rating, ratingPlus),
            jacketOverride = source?.jacketOverride ?: false,
            audioOverride = source?.audioOverride ?: false,
            bg = manual?.bg ?: source?.bg,
            bgInverse = manual?.bgInverse ?: source?.bgInverse,
            title = manual?.title ?: source?.titleLocalized?.preferred(),
            artist = manual?.artist ?: source?.artist,
            bpmText = manual?.bpmText ?: source?.bpmText,
            bpmBase = manual?.bpmBase ?: source?.bpmBase,
        )
    }

    private fun buildSearchTags(song: SonglistSong?): String {
        if (song == null) return ""
        return song.titleLocalized.values
            .plus(listOfNotNull(song.title))
            .plus(song.searchTitle.values.flatten())
            .plus(song.searchArtist.values.flatten())
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
    }
}

internal fun Map<String, String>.preferred(): String? =
    this["en"] ?: this["ja"] ?: this["zh-Hans"] ?: this["zh-Hant"] ?: values.firstOrNull()
