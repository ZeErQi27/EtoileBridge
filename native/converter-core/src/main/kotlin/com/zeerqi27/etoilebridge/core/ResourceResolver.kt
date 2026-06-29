package com.zeerqi27.etoilebridge.core

import java.io.File

data class ResolvedSong(
    val songDir: File,
    val metadata: ResolvedSongMetadata,
    val difficulties: List<ResolvedDifficulty>,
    val additionalFiles: List<File>,
)

data class ResolvedDifficulty(
    val metadata: ResolvedDifficultyMetadata,
    val affFile: File,
    val audioFile: File,
    val jacketFile: File?,
    val backgroundFile: File?,
)

class ResourceResolver {
    fun resolve(
        workspaceDir: File,
        songDir: File,
        affFiles: Map<Int, File>,
        metadata: ResolvedSongMetadata,
        warnings: MutableList<String>,
        overrides: ManualResourceOverrides? = null,
    ): ResolvedSong {
        val difficulties = metadata.difficulties.map { diff ->
            val aff = affFiles[diff.ratingClass]
                ?: throw MissingRequiredResourceException("Missing ${diff.ratingClass}.aff")
            val audio = overrides?.audioFile?.takeIf { it.isFile } ?: resolveAudio(songDir, diff)
                ?: throw MissingRequiredResourceException("Missing audio for ${metadata.songId}")
            val jacket = overrides?.jacketFile?.takeIf { it.isFile }
                ?: resolveJacket(workspaceDir, songDir, metadata, diff, warnings)
            if (jacket == null) {
                warnings += "Jacket image not found for ${metadata.songId}; ArcCreate metadata will reference an empty jacket path."
            }
            val background = overrides?.backgroundFile?.takeIf { it.isFile }
                ?: resolveBackground(workspaceDir, songDir, metadata, diff, warnings)
            if (background == null) {
                warnings += "Background not recognized. You can select one manually."
            }
            ResolvedDifficulty(diff, aff, audio, jacket, background)
        }

        val additional = metadata.additionalFiles.mapNotNull { path ->
            val file = workspaceDir.resolve(path).takeIf { it.isFile }
                ?: songDir.resolve(path).takeIf { it.isFile }
            if (file == null) warnings += "Additional file not found: $path"
            file
        }

        return ResolvedSong(songDir, metadata, difficulties, additional)
    }

    private fun resolveAudio(songDir: File, diff: ResolvedDifficultyMetadata): File? {
        if (diff.audioOverride) {
            findFirstExisting(songDir, "${diff.ratingClass}.ogg", "${diff.ratingClass}.wav")?.let { return it }
        }
        return findFirstExisting(songDir, "base.ogg", "song.ogg")
            ?: uniqueByExtensions(songDir, setOf("ogg"))
            ?: uniqueByExtensions(songDir, setOf("wav"))
    }

    private fun resolveJacket(
        workspaceDir: File,
        songDir: File,
        song: ResolvedSongMetadata,
        diff: ResolvedDifficultyMetadata,
        warnings: MutableList<String>,
    ): File? {
        if (diff.jacketOverride) {
            findFirstExisting(songDir, *ratingClassJacketCandidates(diff.ratingClass).toTypedArray())?.let { return it }
            warnings += "Jacket override image not found for ratingClass ${diff.ratingClass}; falling back to common jacket candidates."
        }
        val excludedBackgroundNames = explicitBackgroundNames(workspaceDir, songDir, song, diff)
        return findFirstExisting(songDir, *jacketCandidates(diff.ratingClass).toTypedArray())
            ?: uniqueImageByRatio(songDir, ImageRatio.Square, excludedBackgroundNames)
            ?: uniqueByExtensions(songDir, imageExtensions, excludedBackgroundNames)
    }

    private fun resolveBackground(
        workspaceDir: File,
        songDir: File,
        song: ResolvedSongMetadata,
        diff: ResolvedDifficultyMetadata,
        warnings: MutableList<String>,
    ): File? {
        val searchDirs = listOf(songDir, workspaceDir).distinctBy { it.canonicalFile }
        val namedCandidates = listOfNotNull(diff.bg, song.bg, song.bgInverse, diff.bgInverse).distinct()
        for (candidate in namedCandidates) {
            val file = findByStemOrName(searchDirs, candidate)
            if (file != null) return file
            warnings += "Background specified by songlist not found: $candidate"
        }
        return findFirstExisting(
            songDir,
            "bg.png",
            "bg.jpg",
            "bg.jpeg",
            "background.png",
            "background.jpg",
            "background.jpeg",
            "base_light.png",
            "base_light.jpg",
            "base_light.jpeg",
            "base_conflict.png",
            "base_conflict.jpg",
            "base_conflict.jpeg",
        )
            ?: uniqueImageByRatio(songDir, ImageRatio.FourToThree, jacketLikeNames(diff.ratingClass))
            ?: findFirstExisting(
                workspaceDir,
                "bg.png",
                "bg.jpg",
                "bg.jpeg",
                "background.png",
                "background.jpg",
                "background.jpeg",
                "base_light.png",
                "base_light.jpg",
                "base_light.jpeg",
                "base_conflict.png",
                "base_conflict.jpg",
                "base_conflict.jpeg",
            )
            ?: uniqueImageByRatio(workspaceDir, ImageRatio.FourToThree, jacketLikeNames(diff.ratingClass))
            ?: uniqueByExtensions(workspaceDir, imageExtensions, jacketLikeNames(diff.ratingClass))
    }

    private fun ratingClassJacketCandidates(ratingClass: Int): List<String> =
        listOf(
            "1080_$ratingClass.png",
            "1080_$ratingClass.jpg",
            "1080_$ratingClass.jpeg",
            "$ratingClass.png",
            "$ratingClass.jpg",
            "$ratingClass.jpeg",
        )

    private fun jacketCandidates(ratingClass: Int): List<String> =
        ratingClassJacketCandidates(ratingClass) + listOf(
            "jacket.png",
            "jacket.jpg",
            "jacket.jpeg",
            "1080_base.png",
            "1080_base.jpg",
            "1080_base.jpeg",
            "base.jpg",
            "base.png",
            "base.jpeg",
            "base_256.png",
            "base_256.jpg",
            "base_256.jpeg",
            "1080_base_256.png",
            "1080_base_256.jpg",
            "1080_base_256.jpeg",
        )

    private fun findFirstExisting(dir: File, vararg names: String): File? =
        names.firstNotNullOfOrNull { name -> dir.resolve(name).takeIf { it.isFile } }

    private fun findByStemOrName(dir: File, nameOrStem: String): File? {
        val direct = dir.resolve(nameOrStem).takeIf { it.isFile }
        if (direct != null) return direct
        return findFirstExisting(
            dir,
            "$nameOrStem.jpg",
            "$nameOrStem.png",
            "$nameOrStem.jpeg",
        )
    }

    private fun findByStemOrName(dirs: List<File>, nameOrStem: String): File? =
        dirs.firstNotNullOfOrNull { findByStemOrName(it, nameOrStem) }
            ?: dirs.firstNotNullOfOrNull { findByStemOrNameRecursive(it, nameOrStem) }

    private fun findByStemOrNameRecursive(dir: File, nameOrStem: String): File? =
        dir.walkTopDown()
            .filter { it.isFile }
            .filter { it.extension.lowercase() in imageExtensions }
            .firstOrNull { file ->
                file.name.equals(nameOrStem, ignoreCase = true) ||
                    file.nameWithoutExtension.equals(nameOrStem, ignoreCase = true)
            }

    private fun explicitBackgroundNames(
        workspaceDir: File,
        songDir: File,
        song: ResolvedSongMetadata,
        diff: ResolvedDifficultyMetadata,
    ): Set<String> =
        listOfNotNull(diff.bg, song.bg, diff.bgInverse, song.bgInverse)
            .mapNotNull { findByStemOrName(listOf(songDir, workspaceDir), it)?.name }
            .toSet()

    private fun uniqueImageByRatio(
        dir: File,
        ratio: ImageRatio,
        excludedNames: Set<String>,
    ): File? {
        val files = imageFiles(dir)
            .filter { it.name !in excludedNames }
            .filter { file ->
                val dimension = ImageDimensionReader.read(file)
                dimension != null && ratio.matches(dimension)
            }
        return files.singleOrNull()
    }

    private fun uniqueByExtensions(
        dir: File,
        extensions: Set<String>,
        excludedNames: Set<String> = emptySet(),
    ): File? {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name !in excludedNames && it.extension.lowercase() in extensions }
            .orEmpty()
        return files.singleOrNull()
    }

    private fun imageFiles(dir: File): List<File> =
        dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in imageExtensions }
            .orEmpty()

    private fun jacketLikeNames(ratingClass: Int): Set<String> =
        jacketCandidates(ratingClass).toSet()

    private enum class ImageRatio {
        Square,
        FourToThree;

        fun matches(dimension: ImageDimension): Boolean =
            when (this) {
                Square -> dimension.width == dimension.height
                FourToThree -> dimension.width * 3 == dimension.height * 4
            }
    }

    companion object {
        private val imageExtensions = setOf("png", "jpg", "jpeg")
    }
}

class MissingRequiredResourceException(message: String) : RuntimeException(message)
