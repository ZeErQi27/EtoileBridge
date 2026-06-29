package com.zeerqi27.etoilebridge.core

import java.io.File

object EtoileBridgeConverter {
    fun convert(
        input: ConvertInput,
        options: ConvertOptions = ConvertOptions(),
        logger: ConvertLogger = ConvertLogger.NONE,
    ): ConvertResult {
        val log = LogCollector(logger)
        val warnings = mutableListOf<String>()

        return try {
            require(input.workspaceDir.isDirectory) { "workspaceDir does not exist or is not a directory: ${input.workspaceDir}" }

            val scanner = InputScanner()
            val scanned = scanner.scan(input.workspaceDir)
            log.log("Scanned input as ${scanned.kind}")
            scanned.ignoredAffFiles.forEach {
                warnings += "Ignored non-standard AFF file: ${it.relativeToOrSelf(input.workspaceDir)}"
            }

            if (scanned.rootAffFiles.isEmpty() && scanned.songDirectories.isEmpty() && scanned.ignoredAffFiles.isEmpty()) {
                return failed("No standard difficulty AFF files found.", null, warnings, log, input, options)
            }

            val rootSonglistFile = input.resourceOverrides?.songlistFile?.takeIf { it.isFile } ?: scanned.songlistFile
            val rootSonglist = rootSonglistFile?.let { parseSonglistOrNull(it, warnings, log) }

            val requestedSongId = input.targetSongId
                ?: input.manualMetadata?.songId
                ?: rootSonglist?.songs.orEmpty().firstOrNull { it.deleted != true }?.id
                    ?.takeIf { scanned.findSongDirectory(it) != null }
            if (scanned.kind == InputKind.PackFolder && scanned.candidateSongIds.size > 1 && input.targetSongId == null) {
                return ConvertResult.UnsupportedPackStructure(
                    message = "Detected pack structure with multiple songs. Single-song conversion does not support pack conversion yet.",
                    candidateSongIds = scanned.candidateSongIds,
                    scannedInput = scanned,
                )
            }
            val target = resolveTarget(scanned, requestedSongId)
                ?: return ConvertResult.NeedMetadata(
                    MissingMetadata(
                        reason = "Pack folder contains multiple songs. Select a target songId before converting.",
                        requiredFields = listOf("targetSongId"),
                        candidateSongIds = scanned.candidateSongIds,
                    ),
                    scanned,
                )

            val affFiles = applyChartOverrides(target.affFiles, input.chartOverrides, input.workspaceDir, warnings)
            if (affFiles.isEmpty()) {
                return failed("No standard difficulty AFF files found. Use manual AFF mapping to adopt a non-standard chart file.", null, warnings, log, input, options)
            }

            val songlistFile = selectSonglistFile(input.resourceOverrides, target.songDir, input.workspaceDir, scanned)
            val packlistFile = input.resourceOverrides?.packlistFile?.takeIf { it.isFile }
                ?: InputScanner.findPacklistFile(target.songDir)
                ?: scanned.packlistFile
            val songlist = when (songlistFile?.canonicalFile) {
                rootSonglistFile?.canonicalFile -> rootSonglist
                null -> null
                else -> parseSonglistOrNull(songlistFile, warnings, log)
            }
            val packlist = packlistFile?.let {
                log.log("Parsing packlist: ${it.name}")
                PacklistParser().parse(it)
            }

            val metadataResolution = MetadataResolver().resolve(
                songDir = target.songDir,
                affFiles = affFiles,
                requestedSongId = target.songId,
                songlist = songlist,
                packlist = packlist,
                manualMetadata = input.manualMetadata,
                warnings = warnings,
            )
            val metadata = when (metadataResolution) {
                is MetadataResolution.Need -> return ConvertResult.NeedMetadata(metadataResolution.missingMetadata, scanned)
                is MetadataResolution.Resolved -> metadataResolution.metadata
            }
            log.log("Resolved metadata for ${metadata.songId}")

            val processedDir = TempWorkspace.processedAffDir(input.workspaceDir, metadata.songId)
            val processed = Preprocessor().preprocessAffFiles(affFiles, processedDir, options, log)
            processed.values.flatMapTo(warnings) { it.warnings }
            val processedAffFiles = processed.mapValues { it.value.outputFile }

            val resolvedSong = ResourceResolver().resolve(
                workspaceDir = input.workspaceDir,
                songDir = target.songDir,
                affFiles = processedAffFiles,
                metadata = metadata,
                warnings = warnings,
                overrides = input.resourceOverrides,
            )

            val output = fixedOutputFile(input.outputFile, metadata.songId)
            PackEngine().pack(resolvedSong, output, warnings, log, input.packageOptions, input.appearanceOptions)

            if (options.cleanWorkspaceOnSuccess) {
                TempWorkspace.cleanProcessedAff(input.workspaceDir)
                log.log("Cleaned temporary processed AFF files")
            }

            ConvertResult.Success(output, metadata.songId, warnings.toList(), log.logs)
        } catch (e: Exception) {
            failed(e.message ?: "Conversion failed", e, warnings, log, input, options)
        }
    }

    private fun selectSonglistFile(
        overrides: ManualResourceOverrides?,
        songDir: File,
        workspaceDir: File,
        scanned: ScannedInput,
    ): File? =
        overrides?.songlistFile?.takeIf { it.isFile }
            ?: InputScanner.findSonglistFile(songDir)
            ?: InputScanner.findSonglistFile(workspaceDir)
            ?: scanned.songlistFile

    private fun parseSonglistOrNull(
        file: File,
        warnings: MutableList<String>,
        log: LogCollector,
    ): Songlist? =
        try {
            log.log("Parsing songlist/slst: ${file.name}")
            SonglistParser().parse(file)
        } catch (error: Exception) {
            warnings += "Songlist/slst parse failed for ${file.name}: ${error.message}"
            null
        }

    private fun applyChartOverrides(
        affFiles: Map<Int, File>,
        overrides: ManualChartOverrides?,
        workspaceDir: File,
        warnings: MutableList<String>,
    ): Map<Int, File> {
        if (overrides == null || overrides.adoptedAffByRatingClass.isEmpty()) return affFiles
        val result = affFiles.toMutableMap()
        overrides.adoptedAffByRatingClass.forEach { (ratingClass, file) ->
            if (ratingClass !in 0..4) {
                warnings += "Manual AFF mapping ignored invalid ratingClass: $ratingClass"
                return@forEach
            }
            if (!file.isFile) {
                warnings += "Manual AFF mapping file not found: ${file.relativeToOrSelf(workspaceDir)}"
                return@forEach
            }
            result[ratingClass] = file
            warnings += "Manual AFF mapping adopted ${file.relativeToOrSelf(workspaceDir)} as ${ratingClass}.aff"
        }
        return result.toSortedMap()
    }

    private fun resolveTarget(scanned: ScannedInput, requestedSongId: String?): TargetSong? {
        return when (scanned.kind) {
            InputKind.SingleSong -> TargetSong(requestedSongId, scanned.workspaceDir, scanned.rootAffFiles)
            InputKind.PackFolder -> {
                val songId = requestedSongId ?: scanned.candidateSongIds.singleOrNull() ?: return null
                val matched = scanned.findSongDirectory(songId) ?: return null
                TargetSong(matched.first, matched.second, InputScanner.findAffFiles(matched.second))
            }
            InputKind.Unknown -> TargetSong(requestedSongId, scanned.workspaceDir, scanned.rootAffFiles)
                .takeIf { it.affFiles.isNotEmpty() }
        }
    }

    private fun fixedOutputFile(requested: File, songId: String): File {
        val name = "$songId.arcpkg"
        return when {
            requested.isDirectory -> requested.resolve(name)
            requested.extension.isBlank() -> requested.resolve(name)
            requested.name == name -> requested
            else -> requested.parentFile?.resolve(name) ?: File(name)
        }
    }

    private fun failed(
        message: String,
        cause: Throwable?,
        warnings: List<String>,
        log: LogCollector,
        input: ConvertInput,
        options: ConvertOptions,
    ): ConvertResult.Failed {
        if (!options.keepWorkspaceOnFailure) {
            TempWorkspace.cleanProcessedAff(input.workspaceDir)
        }
        return ConvertResult.Failed(
            message = message,
            cause = cause,
            warnings = warnings.toList(),
            logs = log.logs,
            workspaceDir = input.workspaceDir.takeIf { options.keepWorkspaceOnFailure },
        )
    }
}

private fun File.relativeToOrSelf(base: File): String =
    runCatching { relativeTo(base).path }.getOrElse { absolutePath }

private fun ScannedInput.findSongDirectory(songId: String): Pair<String, File>? {
    songDirectories[songId]?.let { return songId to it }
    return songDirectories.entries.firstOrNull { it.key.equals(songId, ignoreCase = true) }
        ?.let { it.key to it.value }
}

private data class TargetSong(
    val songId: String?,
    val songDir: File,
    val affFiles: Map<Int, File>,
)
