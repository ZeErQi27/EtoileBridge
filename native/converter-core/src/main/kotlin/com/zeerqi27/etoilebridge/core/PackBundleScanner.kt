package com.zeerqi27.etoilebridge.core

import java.io.File

class PackBundleScanner {
    fun scanOfficialPack(workspaceDir: File): BundleScanResult {
        require(workspaceDir.isDirectory) { "workspaceDir must be a directory: $workspaceDir" }
        val warnings = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val projectRoot = normalizeProjectRoot(workspaceDir)
        logs += "Bundle project root: ${projectRoot.absolutePath}"

        val rootSonglistFile = findSonglistForPack(projectRoot)
        val rootPacklistFile = findPacklistForPack(projectRoot)
        val rootSonglist = rootSonglistFile?.let { parseSonglist(it, warnings) }
        val rootPacklist = rootPacklistFile?.let { parsePacklist(it, warnings) }
        val pack = rootPacklist?.packs?.firstOrNull()
        val packIdCandidate = pack?.id?.takeIf { it.isNotBlank() } ?: projectRoot.name.sanitizePackId()
        val packNameCandidate = pack?.displayName() ?: projectRoot.name
        val packImageFile = findPackImage(projectRoot, packIdCandidate)
        val songDirs = findSongDirectories(projectRoot)
        if (songDirs.isEmpty()) {
            warnings += "No song directories found in bundle."
        }

        val entries = songDirs.map { songDir ->
            scanSong(projectRoot, songDir, rootSonglistFile, rootSonglist, rootPacklistFile, rootPacklist)
        }.sortedBy { it.songId.lowercase() }

        return BundleScanResult(
            projectRoot = projectRoot,
            songlistFile = rootSonglistFile,
            packlistFile = rootPacklistFile,
            packNameCandidate = packNameCandidate,
            packIdCandidate = packIdCandidate,
            packImageFile = packImageFile,
            entries = entries,
            warnings = warnings,
            logs = logs,
        )
    }

    fun normalizeProjectRoot(workspaceDir: File): File {
        if (looksLikeBundleRoot(workspaceDir)) return workspaceDir
        val childDirs = workspaceDir.listFiles()?.filter { it.isDirectory }.orEmpty()
        val single = childDirs.singleOrNull()
        return if (single != null && looksLikeBundleRoot(single)) single else workspaceDir
    }

    private fun looksLikeBundleRoot(dir: File): Boolean =
        InputScanner.findSonglistFile(dir) != null ||
            InputScanner.findPacklistFile(dir) != null ||
            dir.resolve("assets/songs").isDirectory ||
            dir.listFiles()?.any { it.isDirectory && hasAnyAff(it) } == true

    private fun findSongDirectories(projectRoot: File): List<File> {
        val roots = buildList {
            projectRoot.resolve("assets/songs").takeIf { it.isDirectory }?.let { add(it) }
            add(projectRoot)
        }.distinctBy { it.canonicalPathSafe() }

        return roots
            .flatMap { root ->
                root.listFiles()
                    ?.filter { it.isDirectory && hasAnyAff(it) }
                    .orEmpty()
            }
            .ifEmpty {
                projectRoot.walkTopDown()
                    .filter { it.isDirectory && hasAnyAff(it) }
                    .filterNot { it.path.contains("${File.separator}ArcCreate${File.separator}") }
                    .toList()
            }
            .distinctBy { it.canonicalPathSafe() }
            .sortedBy { it.name.lowercase() }
    }

    private fun scanSong(
        projectRoot: File,
        songDir: File,
        rootSonglistFile: File?,
        rootSonglist: Songlist?,
        rootPacklistFile: File?,
        rootPacklist: Packlist?,
    ): BundleEntry {
        val warnings = mutableListOf<String>()
        val affFiles = InputScanner.findAffFiles(songDir)
        val ignoredAff = InputScanner.findIgnoredAffFiles(songDir)
        ignoredAff.forEach { warnings += "Ignored non-standard AFF file: ${it.name}" }
        if (affFiles.isEmpty()) {
            return BundleEntry(
                key = songDir.name,
                songId = songDir.name,
                songDir = songDir,
                charts = affFiles.toChartEntries(null),
                ignoredAffFiles = ignoredAff,
                canConvert = false,
                warnings = warnings,
                failureReason = "No standard difficulty AFF files found.",
            )
        }

        val songlistFile = InputScanner.findSonglistFile(songDir) ?: rootSonglistFile
        val songlist = if (songlistFile?.canonicalPathSafe() == rootSonglistFile?.canonicalPathSafe()) {
            rootSonglist
        } else {
            songlistFile?.let { parseSonglist(it, warnings) }
        }
        val packlistFile = InputScanner.findPacklistFile(songDir) ?: rootPacklistFile
        val packlist = if (packlistFile?.canonicalPathSafe() == rootPacklistFile?.canonicalPathSafe()) {
            rootPacklist
        } else {
            packlistFile?.let { parsePacklist(it, warnings) }
        }

        val metadataResolution = MetadataResolver().resolve(
            songDir = songDir,
            affFiles = affFiles,
            requestedSongId = songDir.name,
            songlist = songlist,
            packlist = packlist,
            manualMetadata = null,
            warnings = warnings,
        )

        val metadata = (metadataResolution as? MetadataResolution.Resolved)?.metadata
        val metadataStatus = if (metadata != null) BundleMetadataStatus.Resolved else BundleMetadataStatus.NeedMetadata
        val charts = if (metadata != null) {
            affFiles.toChartEntries(projectRoot, songDir, metadata, warnings)
        } else {
            affFiles.toChartEntries(null).map {
                it.copy(canConvert = false, failureReason = "Metadata is missing.")
            }
        }
        val firstResolvedDifficulty = charts
            .firstOrNull { it.canConvert }
            ?.ratingClass
            ?.let { ratingClass ->
                runCatching {
                    val diffMetadata = metadata?.difficulties?.firstOrNull { it.ratingClass == ratingClass }
                    if (metadata != null && diffMetadata != null) {
                        ResourceResolver().resolve(
                            projectRoot,
                            songDir,
                            mapOf(ratingClass to requireNotNull(affFiles[ratingClass])),
                            metadata.copy(difficulties = listOf(diffMetadata)),
                            mutableListOf(),
                        ).difficulties.firstOrNull()
                    } else {
                        null
                    }
                }.getOrNull()
            }
        val canConvert = metadata != null && charts.any { it.canConvert }
        val failureReason = when {
            metadata == null -> (metadataResolution as? MetadataResolution.Need)?.missingMetadata?.reason ?: "Metadata is missing."
            !canConvert -> "No convertible charts found."
            else -> null
        }

        return BundleEntry(
            key = metadata?.songId ?: songDir.name,
            songId = metadata?.songId ?: songDir.name,
            title = metadata?.title,
            artist = metadata?.artist,
            difficultySummary = charts.joinToString(" · ") { it.difficulty ?: DifficultyMapper.labelFor(it.ratingClass) },
            charts = charts,
            songDir = songDir,
            affFiles = affFiles.values.toList(),
            ignoredAffFiles = ignoredAff,
            audioFile = firstResolvedDifficulty?.audioFile,
            jacketFile = firstResolvedDifficulty?.jacketFile,
            backgroundFile = firstResolvedDifficulty?.backgroundFile,
            songlistFile = songlistFile,
            packlistFile = packlistFile,
            metadataStatus = metadataStatus,
            canConvert = canConvert,
            warnings = warnings,
            failureReason = failureReason,
        )
    }

    private fun hasAnyAff(dir: File): Boolean =
        InputScanner.findAffFiles(dir).isNotEmpty() || InputScanner.findIgnoredAffFiles(dir).isNotEmpty()

    private fun Map<Int, File>.toChartEntries(metadata: ResolvedSongMetadata?): List<BundleChartEntry> =
        entries.sortedBy { it.key }.map { (ratingClass, file) ->
            val diff = metadata?.difficulties?.firstOrNull { it.ratingClass == ratingClass }
            BundleChartEntry(
                ratingClass = ratingClass,
                chartPath = file.name,
                difficulty = diff?.difficulty ?: DifficultyMapper.displayName(ratingClass, diff?.rating, diff?.ratingPlus),
                chartConstant = diff?.chartConstant ?: DifficultyMapper.chartConstant(diff?.rating, diff?.ratingPlus),
                charter = diff?.chartDesigner,
                illustrator = diff?.jacketDesigner,
                affFile = file,
                canConvert = metadata != null,
                failureReason = if (metadata == null) "Metadata is missing." else null,
            )
        }

    private fun Map<Int, File>.toChartEntries(
        projectRoot: File,
        songDir: File,
        metadata: ResolvedSongMetadata,
        entryWarnings: MutableList<String>,
    ): List<BundleChartEntry> =
        entries.sortedBy { it.key }.map { (ratingClass, file) ->
            val diff = metadata.difficulties.firstOrNull { it.ratingClass == ratingClass }
            val chartWarnings = mutableListOf<String>()
            val result = runCatching {
                if (diff == null) error("Metadata for ${file.name} is missing.")
                ResourceResolver().resolve(
                    projectRoot,
                    songDir,
                    mapOf(ratingClass to file),
                    metadata.copy(difficulties = listOf(diff)),
                    chartWarnings,
                )
            }
            entryWarnings += chartWarnings
            BundleChartEntry(
                ratingClass = ratingClass,
                chartPath = file.name,
                difficulty = diff?.difficulty ?: DifficultyMapper.displayName(ratingClass, diff?.rating, diff?.ratingPlus),
                chartConstant = diff?.chartConstant ?: DifficultyMapper.chartConstant(diff?.rating, diff?.ratingPlus),
                charter = diff?.chartDesigner,
                illustrator = diff?.jacketDesigner,
                affFile = file,
                canConvert = result.isSuccess,
                warnings = chartWarnings,
                failureReason = result.exceptionOrNull()?.message,
            )
        }

    private fun findSonglistForPack(projectRoot: File): File? =
        InputScanner.findSonglistFile(projectRoot)
            ?: projectRoot.walkTopDown()
                .filter { it.isFile && it.name in songlistNames }
                .firstOrNull { runCatching { SonglistParser().parse(it).songs.size > 1 }.getOrDefault(false) }

    private fun findPacklistForPack(projectRoot: File): File? =
        InputScanner.findPacklistFile(projectRoot)
            ?: projectRoot.walkTopDown().filter { it.isFile && it.name in packlistNames }.firstOrNull()

    private fun parseSonglist(file: File, warnings: MutableList<String>): Songlist? =
        runCatching { SonglistParser().parse(file) }
            .onFailure { warnings += "Songlist/slst parse failed for ${file.name}: ${it.message}" }
            .getOrNull()

    private fun parsePacklist(file: File, warnings: MutableList<String>): Packlist? =
        runCatching { PacklistParser().parse(file) }
            .onFailure { warnings += "Packlist parse failed for ${file.name}: ${it.message}" }
            .getOrNull()

    private fun findPackImage(projectRoot: File, packId: String?): File? {
        val extensions = setOf("png", "jpg", "jpeg")
        val normalizedPackId = packId?.lowercase().orEmpty()
        val files = projectRoot.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in extensions }
            .toList()
        val packDirImages = files.filter {
            it.parentFile?.name.equals("pack", ignoreCase = true) ||
                it.relativeToOrNull(projectRoot)?.path?.split(File.separatorChar)?.any { part -> part.equals("pack", ignoreCase = true) } == true
        }
        val preferred = packDirImages.firstOrNull {
            val stem = it.nameWithoutExtension.lowercase()
            stem == "pack" ||
                stem == "select_$normalizedPackId" ||
                (normalizedPackId.isNotBlank() && stem.contains(normalizedPackId))
        }
        return preferred ?: packDirImages.firstOrNull()
            ?: files.firstOrNull { it.nameWithoutExtension.equals("pack", ignoreCase = true) }
    }

    private fun PacklistPack.displayName(): String? =
        nameLocalized["en"]
            ?: nameLocalized["zh-Hans"]
            ?: nameLocalized["ja"]
            ?: nameLocalized.values.firstOrNull()
            ?: id

    private fun String.sanitizePackId(): String =
        replace(Regex("""[^\w.-]+"""), "_")
            .trim('_', '.', '-')
            .ifBlank { "EtoileBridgePack" }

    private fun File.canonicalPathSafe(): String =
        runCatching { canonicalPath }.getOrElse { absolutePath }

    companion object {
        private val songlistNames = setOf("songlist", "songlist.json", "songlist.txt", "slst", "slst.json", "slst.txt")
        private val packlistNames = setOf("packlist", "packlist.json", "packlist.txt")
    }
}
