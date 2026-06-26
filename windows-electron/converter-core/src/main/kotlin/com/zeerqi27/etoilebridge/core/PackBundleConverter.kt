package com.zeerqi27.etoilebridge.core

import java.io.File

class PackBundleConverter(
    private val scanner: PackBundleScanner = PackBundleScanner(),
    private val merger: ArcpkgBundleMerger = ArcpkgBundleMerger(),
) {
    fun convertOfficialPack(input: BundleInput): BundleConvertResult {
        val warnings = mutableListOf<String>()
        val logs = mutableListOf<String>()
        return try {
            val scan = scanner.scanOfficialPack(input.workspaceDir)
            warnings += scan.warnings
            logs += scan.logs
            val tempRoot = input.workspaceDir.resolve("etoilebridge_bundle_temp").apply {
                deleteRecursively()
                mkdirs()
            }
            val selected = scan.entries.filter { input.options.isEntryEnabled(it) }
            if (selected.isEmpty()) {
                return BundleConvertResult.Failed(
                    message = "No enabled songs selected for this pack.",
                    warnings = warnings,
                    logs = logs,
                    workspaceDir = input.workspaceDir,
                )
            }

            val singleOutputDir = tempRoot.resolve("single_arcpkg").apply { mkdirs() }
            var skipped = scan.entries.size - selected.size
            selected.forEach { entry ->
                val override = input.options.overrideFor(entry)
                val enabledRatingClasses = input.options.enabledRatingClasses(entry)
                if (enabledRatingClasses.isEmpty()) {
                    skipped++
                    warnings += "Skipped ${entry.songId}: no enabled charts."
                    return@forEach
                }
                val songWorkspace = prepareSongWorkspace(scan.projectRoot, entry, tempRoot, enabledRatingClasses)
                val result = EtoileBridgeConverter.convert(
                    input = ConvertInput(
                        workspaceDir = songWorkspace,
                        outputFile = singleOutputDir,
                        targetSongId = entry.songId,
                        manualMetadata = override?.toManualMetadata(entry),
                        packageOptions = PackageOptions(
                            publisherId = input.options.publisherId,
                            levelId = override?.levelId?.takeIf { it.isNotBlank() } ?: entry.songId,
                        ),
                        appearanceOptions = input.options.appearanceOptions,
                    ),
                    options = input.options.convertOptions.copy(
                        cleanWorkspaceOnSuccess = true,
                        keepWorkspaceOnFailure = true,
                    ),
                )
                when (result) {
                    is ConvertResult.Success -> {
                        warnings += result.warnings
                        logs += result.logs
                        logs += "Converted ${entry.songId} for bundle."
                    }
                    is ConvertResult.NeedMetadata -> {
                        skipped++
                        warnings += "Skipped ${entry.songId}: metadata is missing (${result.missingMetadata.requiredFields.joinToString()})."
                    }
                    is ConvertResult.UnsupportedPackStructure -> {
                        skipped++
                        warnings += "Skipped ${entry.songId}: unsupported nested pack structure."
                    }
                    is ConvertResult.Failed -> {
                        skipped++
                        warnings += "Skipped ${entry.songId}: ${result.message}"
                        logs += result.logs
                    }
                }
            }

            val output = fixedOutputFile(input.outputFile, input.options.outputFileName)
            val fallbackPackImage = input.options.packImageFile
                ?: scan.packImageFile
                ?: selected.firstNotNullOfOrNull { it.jacketFile }
                    ?.also { warnings += "Pack image not recognized; using the first jacket as temporary pack image: ${it.name}" }
            val mergeOptions = input.options.copy(
                packName = input.options.packName ?: scan.packNameCandidate ?: output.nameWithoutExtension,
                packId = input.options.packId ?: scan.packIdCandidate ?: output.nameWithoutExtension,
                packImageFile = fallbackPackImage,
            )
            val merge = merger.merge(singleOutputDir, output, mergeOptions)
            when (merge) {
                is BundleConvertResult.Success -> merge.copy(
                    skippedCount = skipped,
                    warnings = warnings + merge.warnings,
                    logs = logs + merge.logs,
                )
                is BundleConvertResult.Failed -> merge.copy(
                    warnings = warnings + merge.warnings,
                    logs = logs + merge.logs,
                    workspaceDir = input.workspaceDir,
                )
            }
        } catch (error: Exception) {
            BundleConvertResult.Failed(
                message = error.message ?: "Bundle conversion failed.",
                cause = error,
                warnings = warnings,
                logs = logs,
                workspaceDir = input.workspaceDir,
            )
        }
    }

    private fun prepareSongWorkspace(
        projectRoot: File,
        entry: BundleEntry,
        tempRoot: File,
        enabledRatingClasses: Set<Int>,
    ): File {
        val songWorkspace = tempRoot.resolve("song_workspaces").resolve(entry.songId.sanitizeFileName()).apply {
            deleteRecursively()
            mkdirs()
        }
        copyDirectoryContents(entry.songDir, songWorkspace)
        entry.songlistFile?.takeIf { it.isFile && !it.isDescendantOf(entry.songDir) }
            ?.copyTo(songWorkspace.resolve("songlist"), overwrite = true)
        entry.packlistFile?.takeIf { it.isFile && !it.isDescendantOf(entry.songDir) }
            ?.copyTo(songWorkspace.resolve("packlist"), overwrite = true)
        entry.backgroundFile?.takeIf { it.isFile && !it.isDescendantOf(entry.songDir) }
            ?.let { it.copyTo(songWorkspace.resolve(it.name), overwrite = true) }
        entry.jacketFile?.takeIf { it.isFile && !it.isDescendantOf(entry.songDir) }
            ?.let { it.copyTo(songWorkspace.resolve(it.name), overwrite = true) }
        copyReferencedAdditionalBackgrounds(projectRoot, entry, songWorkspace)
        (0..4)
            .filterNot { it in enabledRatingClasses }
            .forEach { ratingClass -> songWorkspace.resolve("$ratingClass.aff").delete() }
        return songWorkspace
    }

    private fun copyReferencedAdditionalBackgrounds(projectRoot: File, entry: BundleEntry, songWorkspace: File) {
        val stems = entry.backgroundFile?.let { listOf(it.nameWithoutExtension) }.orEmpty()
        stems.forEach { stem ->
            projectRoot.walkTopDown()
                .filter { it.isFile && it.nameWithoutExtension.equals(stem, ignoreCase = true) }
                .filterNot { it.path.contains("${File.separator}etoilebridge_bundle_temp${File.separator}") }
                .firstOrNull()
                ?.let {
                    val target = songWorkspace.resolve(it.name)
                    if (it.canonicalPathSafe() != target.canonicalPathSafe()) {
                        it.copyTo(target, overwrite = true)
                    }
                }
        }
    }

    private fun copyDirectoryContents(source: File, target: File) {
        source.walkTopDown().forEach { file ->
            val relative = file.relativeTo(source)
            if (relative.path.isBlank()) return@forEach
            val out = target.resolve(relative.path)
            if (file.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                file.copyTo(out, overwrite = true)
            }
        }
    }

    private fun File.isDescendantOf(parent: File): Boolean {
        val parentPath = parent.canonicalFile.toPath()
        return runCatching { canonicalFile.toPath().startsWith(parentPath) }.getOrDefault(false)
    }

    private fun File.canonicalPathSafe(): String =
        runCatching { canonicalPath }.getOrElse { absolutePath }

    private fun fixedOutputFile(requested: File, fileName: String): File =
        when {
            requested.isDirectory -> requested.resolve(fileName.ensureArcpkgExtension())
            requested.extension.isBlank() -> requested.resolve(fileName.ensureArcpkgExtension())
            else -> requested
        }

    private fun String.ensureArcpkgExtension(): String =
        if (endsWith(".arcpkg", ignoreCase = true)) this else "$this.arcpkg"

    private fun String.sanitizeFileName(): String =
        replace(Regex("""[\\/:*?"<>|]"""), "_")

    private fun BundleOptions.overrideFor(entry: BundleEntry): BundleEntryOverride? =
        entryOverrides[entry.key] ?: entryOverrides[entry.songId]

    private fun BundleOptions.isEntryEnabled(entry: BundleEntry): Boolean {
        val override = overrideFor(entry)
        val entryEnabled = override?.enabled ?: if (includeOnlyConvertible) entry.canConvert else true
        return entryEnabled && enabledRatingClasses(entry).isNotEmpty()
    }

    private fun BundleOptions.enabledRatingClasses(entry: BundleEntry): Set<Int> =
        entry.charts
            .filter { chart ->
                overrideFor(entry)
                    ?.chartOverrides
                    ?.get(chart.ratingClass)
                    ?.enabled
                    ?: chart.enabled
            }
            .map { it.ratingClass }
            .toSet()

    private fun BundleEntryOverride.toManualMetadata(entry: BundleEntry): ManualMetadata? {
        val hasMetadataOverride = !title.isNullOrBlank() ||
            !artist.isNullOrBlank() ||
            chartOverrides.values.any { chart ->
                listOf(chart.charter, chart.illustrator, chart.difficulty).any { !it.isNullOrBlank() } ||
                    chart.chartConstant != null
            }
        if (!hasMetadataOverride) return null
        val difficulties = entry.charts
            .filter { chartOverrides[it.ratingClass]?.enabled ?: it.enabled }
            .ifEmpty { entry.charts }
            .map { chart ->
                val chartOverride = chartOverrides[chart.ratingClass]
                ManualDifficultyMetadata(
                    ratingClass = chart.ratingClass,
                    chartDesigner = chartOverride?.charter ?: chart.charter,
                    jacketDesigner = chartOverride?.illustrator ?: chart.illustrator,
                    difficulty = chartOverride?.difficulty ?: chart.difficulty,
                    chartConstant = chartOverride?.chartConstant ?: chart.chartConstant,
                )
            }
        return ManualMetadata(
            songId = entry.songId,
            title = title,
            artist = artist,
            difficulties = difficulties,
        )
    }
}
