package com.zeerqi27.etoilebridge.core

import com.charleskorn.kaml.decodeFromStream
import com.charleskorn.kaml.encodeToStream
import com.zeerqi27.etoilebridge.core.etoile.ArcpkgEntryType
import com.zeerqi27.etoilebridge.core.etoile.ChartEntry
import com.zeerqi27.etoilebridge.core.etoile.EtoileYaml
import com.zeerqi27.etoilebridge.core.etoile.ImportInformationEntry
import com.zeerqi27.etoilebridge.core.etoile.PackInformation
import com.zeerqi27.etoilebridge.core.etoile.ProjectInformation
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.builtins.ListSerializer

class ArcpkgBundleMerger {
    fun scan(input: File): ArcpkgBundleScanResult {
        val files = collectArcpkgFiles(input)
        val warnings = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val reports = mutableListOf<ArcpkgSourceReport>()
        val levels = mutableListOf<ArcpkgLevelEntry>()
        val packs = mutableListOf<ArcpkgPackEntry>()

        files.forEach { file ->
            val result = runCatching { readArcpkgEntries(file) }
            val entries = result.getOrNull()
            val levelEntries = entries?.levels.orEmpty()
            val packEntries = entries?.packs.orEmpty()
            if (result.isSuccess) {
                val firstPack = packEntries.firstOrNull()
                reports += ArcpkgSourceReport(
                    sourceFile = file,
                    readable = true,
                    levelCount = levelEntries.size,
                    packEntryCount = packEntries.size,
                    packName = firstPack?.packName,
                    packImagePath = firstPack?.imagePath,
                    packImageExists = firstPack?.packImageExists == true,
                    packLevelIdentifierCount = firstPack?.levelIdentifiers.orEmpty().size,
                    packMatchesIndexLevels = firstPack?.matchesIndexLevels,
                )
                levels += levelEntries
                packs += packEntries
                logs += "Read ${levelEntries.size} level entries and ${packEntries.size} pack entries from ${file.name}"
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to parse arcpkg."
                reports += ArcpkgSourceReport(file, readable = false, failureReason = message)
                warnings += "Bad arcpkg skipped: ${file.name}: $message"
            }
        }

        return ArcpkgBundleScanResult(
            sourceFiles = reports,
            levelEntries = levels,
            packEntries = packs,
            warnings = warnings,
            logs = logs,
        )
    }

    fun scanExistingPack(basePack: File, addInput: File? = null): ExistingPackEditScanResult {
        val warnings = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val baseEntries = runCatching { readArcpkgEntries(basePack) }.getOrElse { error ->
            return ExistingPackEditScanResult(
                basePackFile = basePack,
                basePackEntry = null,
                existingLevels = emptyList(),
                addedLevels = emptyList(),
                sourceFiles = listOf(ArcpkgSourceReport(basePack, readable = false, failureReason = error.message)),
                warnings = listOf("Existing pack read failed: ${basePack.name}: ${error.message}"),
                logs = logs,
            )
        }
        if (baseEntries.packs.isEmpty()) {
            warnings += "Existing arcpkg does not contain a type: pack entry."
        }
        if (baseEntries.packs.size > 1) {
            warnings += "Multiple type: pack entries found in existing pack; using the first."
        }

        val addedScan = addInput
            ?.takeIf { it.exists() }
            ?.let { scan(it) }
        addedScan?.sourceFiles
            ?.filter { it.packEntryCount > 0 }
            ?.forEach { warnings += "Ignored pack entry from added source: ${it.sourceFile.name}" }

        val renamedConflictCount = estimateAddedConflictCount(baseEntries, addedScan)
        if (renamedConflictCount > 0) {
            warnings += "Potential added level directory/identifier conflicts: $renamedConflictCount"
        }

        logs += "Read existing pack: ${baseEntries.levels.size} levels, ${baseEntries.packs.size} pack entries."
        addedScan?.let {
            logs += "Read added input: ${it.levelEntries.size} levels, ${it.packEntries.size} ignored pack entries."
        }

        return ExistingPackEditScanResult(
            basePackFile = basePack,
            basePackEntry = baseEntries.packs.firstOrNull(),
            existingLevels = baseEntries.levels,
            addedLevels = addedScan?.levelEntries.orEmpty(),
            sourceFiles = addedScan?.sourceFiles.orEmpty(),
            warnings = warnings + addedScan?.warnings.orEmpty(),
            logs = logs + addedScan?.logs.orEmpty(),
            renamedConflictCount = renamedConflictCount,
        )
    }

    fun merge(
        input: File,
        outputFile: File,
        publisherId: String = "etoilebridge",
    ): BundleConvertResult = merge(input, outputFile, BundleOptions(publisherId = publisherId))

    fun merge(
        input: File,
        outputFile: File,
        options: BundleOptions,
    ): BundleConvertResult {
        val warnings = mutableListOf<String>()
        val logs = mutableListOf<String>()
        return try {
            val files = collectArcpkgFiles(input)
            if (files.isEmpty()) {
                return BundleConvertResult.Failed("No .arcpkg files found.", workspaceDir = input.takeIf { it.isDirectory })
            }
            val sourceScan = scan(input)
            warnings += sourceScan.warnings
            outputFile.parentFile?.mkdirs()
            ZipOutputStream(outputFile.outputStream()).use { out ->
                val usedZipEntries = mutableSetOf<String>()
                val usedDirectories = mutableSetOf<String>()
                val usedIdentifiers = mutableSetOf<String>()
                val levelIndex = mutableListOf<ImportInformationEntry>()

                files.forEach { file ->
                    runCatching {
                        mergeOne(file, out, levelIndex, usedZipEntries, usedDirectories, usedIdentifiers, options, warnings)
                    }.onFailure { error ->
                        warnings += "Bad arcpkg skipped: ${file.name}: ${error.message}"
                    }
                }
                if (levelIndex.isEmpty()) {
                    return BundleConvertResult.Failed("No valid level entries found.", warnings = warnings, logs = logs)
                }

                val packEntry = writePackEntry(
                    out = out,
                    usedZipEntries = usedZipEntries,
                    usedDirectories = usedDirectories,
                    usedIdentifiers = usedIdentifiers,
                    levelIndex = levelIndex,
                    options = options,
                    outputFile = outputFile,
                    sourceImage = sourceScan.packImageCandidate,
                    sourcePackName = sourceScan.packNameCandidate,
                    warnings = warnings,
                )

                out.putNextEntry(ZipEntry("index.yml"))
                EtoileYaml.encodeToStream(listOf(packEntry) + levelIndex, out)
                out.closeEntry()
                logs += "Merged ${levelIndex.size} level entries."
                logs += "Generated pack entry: ${packEntry.identifier}"
            }
            val validation = BundleOutputValidator().validateBundleArcpkg(outputFile)
            warnings += validation.warnings
            logs += validation.summaryLines()
            logs += validation.logs
            if (!validation.valid) {
                return BundleConvertResult.Failed(
                    message = "Bundle output validation failed.",
                    warnings = warnings + validation.errors,
                    logs = logs,
                    workspaceDir = input.takeIf { it.isDirectory },
                )
            }
            BundleConvertResult.Success(outputFile, convertedCount = validation.levelEntryCount, skippedCount = 0, warnings = warnings, logs = logs)
        } catch (error: Exception) {
            BundleConvertResult.Failed(error.message ?: "Merge failed.", error, warnings, logs, input.takeIf { it.isDirectory })
        }
    }

    fun editExistingPack(
        basePack: File,
        addInput: File?,
        outputFile: File,
        options: BundleOptions,
    ): BundleConvertResult {
        val warnings = mutableListOf<String>()
        val logs = mutableListOf<String>()
        return try {
            val baseRead = readArcpkgEntries(basePack)
            val basePackEntry = baseRead.packs.firstOrNull()
                ?: return BundleConvertResult.Failed(
                    "Existing arcpkg does not contain a type: pack entry.",
                    workspaceDir = addInput?.takeIf { it.isDirectory },
                )
            if (baseRead.packs.size > 1) {
                warnings += "Multiple type: pack entries found in existing pack; using the first."
            }
            if (baseRead.levels.isEmpty()) {
                return BundleConvertResult.Failed("Existing pack has no level entries.", warnings = warnings)
            }

            val addFiles = addInput
                ?.takeIf { it.exists() }
                ?.let { collectArcpkgFiles(it) }
                .orEmpty()

            outputFile.parentFile?.mkdirs()
            ZipOutputStream(outputFile.outputStream()).use { out ->
                val usedZipEntries = mutableSetOf<String>()
                val usedDirectories = mutableSetOf(basePackEntry.directory)
                val usedIdentifiers = mutableSetOf(basePackEntry.identifier)
                val levelIndex = mutableListOf<ImportInformationEntry>()

                ZipFile(basePack).use { zip ->
                    baseRead.levels.forEach { level ->
                        val entry = ImportInformationEntry(
                            directory = level.directory,
                            identifier = level.identifier,
                            settingsFile = level.settingsFile,
                            version = level.version,
                            type = ArcpkgEntryType.LEVEL,
                        )
                        usedDirectories += entry.directory
                        usedIdentifiers += entry.identifier
                        copyDirectory(
                            zip = zip,
                            sourceDirectory = entry.directory,
                            targetDirectory = entry.directory,
                            settingsFile = entry.settingsFile,
                            override = options.overrideFor(basePack, entry),
                            out = out,
                            usedZipEntries = usedZipEntries,
                        )
                        levelIndex += entry
                    }
                }

                addFiles.forEach { file ->
                    runCatching {
                        val entries = readArcpkgEntries(file)
                        if (entries.packs.isNotEmpty()) {
                            warnings += "Ignored pack entry from added source: ${file.name}"
                        }
                        mergeOne(file, out, levelIndex, usedZipEntries, usedDirectories, usedIdentifiers, options, warnings)
                    }.onFailure { error ->
                        warnings += "Bad added arcpkg skipped: ${file.name}: ${error.message}"
                    }
                }

                ZipFile(basePack).use { zip ->
                    copyExistingPackDirectory(
                        zip = zip,
                        packEntry = basePackEntry,
                        levelIdentifiers = levelIndex.map { it.identifier },
                        options = options,
                        out = out,
                        usedZipEntries = usedZipEntries,
                        warnings = warnings,
                    )
                }

                out.putNextEntry(ZipEntry("index.yml"))
                val packIndex = ImportInformationEntry(
                    directory = basePackEntry.directory,
                    identifier = basePackEntry.identifier,
                    settingsFile = basePackEntry.settingsFile,
                    version = basePackEntry.version,
                    type = ArcpkgEntryType.PACK,
                )
                EtoileYaml.encodeToStream(listOf(packIndex) + levelIndex, out)
                out.closeEntry()
                logs += "Rebuilt existing pack with ${baseRead.levels.size} existing levels and ${levelIndex.size - baseRead.levels.size} added levels."
            }

            val validation = BundleOutputValidator().validateBundleArcpkg(outputFile)
            warnings += validation.warnings
            logs += validation.summaryLines()
            logs += validation.logs
            if (!validation.valid) {
                return BundleConvertResult.Failed(
                    message = "Bundle output validation failed.",
                    warnings = warnings + validation.errors,
                    logs = logs,
                    workspaceDir = addInput?.takeIf { it.isDirectory },
                )
            }
            BundleConvertResult.Success(outputFile, convertedCount = validation.levelEntryCount, skippedCount = 0, warnings = warnings, logs = logs)
        } catch (error: Exception) {
            BundleConvertResult.Failed(error.message ?: "Existing pack edit failed.", error, warnings, logs, addInput?.takeIf { it.isDirectory })
        }
    }

    private fun writePackEntry(
        out: ZipOutputStream,
        usedZipEntries: MutableSet<String>,
        usedDirectories: MutableSet<String>,
        usedIdentifiers: MutableSet<String>,
        levelIndex: List<ImportInformationEntry>,
        options: BundleOptions,
        outputFile: File,
        sourceImage: ArcpkgPackImageCandidate?,
        sourcePackName: String?,
        warnings: MutableList<String>,
    ): ImportInformationEntry {
        val packId = (options.packId ?: outputFile.nameWithoutExtension).safeId("EtoileBridgePack")
        val packName = options.packName?.takeIf { it.isNotBlank() }
            ?: sourcePackName?.takeIf { it.isNotBlank() }
            ?: outputFile.nameWithoutExtension
        val packDirectory = uniqueName(packId, usedDirectories)
        if (packDirectory != packId) {
            warnings += "Pack directory conflict: $packId renamed to $packDirectory"
        }
        val identifierBase = "${options.publisherId.ifBlank { "etoilebridge" }}.$packId.pack"
        val packIdentifier = uniqueName(identifierBase, usedIdentifiers)
        if (packIdentifier != identifierBase) {
            warnings += "Pack identifier conflict: $identifierBase renamed to $packIdentifier"
        }

        val packInfo = PackInformation(
            imagePath = PACK_IMAGE_NAME,
            levelIdentifiers = levelIndex.map { it.identifier },
            packName = packName,
        )
        putTextEntry(out, usedZipEntries, "$packDirectory/pack.yml") {
            EtoileYaml.encodeToStream(packInfo, it)
        }
        writePackImage(out, usedZipEntries, "$packDirectory/$PACK_IMAGE_NAME", options.packImageFile, sourceImage, warnings)
        return ImportInformationEntry(
            directory = packDirectory,
            identifier = packIdentifier,
            settingsFile = "pack.yml",
            version = 0,
            type = ArcpkgEntryType.PACK,
        )
    }

    private fun putTextEntry(
        out: ZipOutputStream,
        usedZipEntries: MutableSet<String>,
        name: String,
        write: (ZipOutputStream) -> Unit,
    ) {
        require(usedZipEntries.add(name)) { "Zip entry conflict: $name" }
        out.putNextEntry(ZipEntry(name))
        write(out)
        out.closeEntry()
    }

    private fun writePackImage(
        out: ZipOutputStream,
        usedZipEntries: MutableSet<String>,
        targetName: String,
        packImageFile: File?,
        sourceImage: ArcpkgPackImageCandidate?,
        warnings: MutableList<String>,
    ) {
        require(usedZipEntries.add(targetName)) { "Zip entry conflict: $targetName" }
        out.putNextEntry(ZipEntry(targetName))
        when {
            packImageFile?.isFile == true -> packImageFile.inputStream().use { it.copyTo(out) }
            sourceImage != null -> ZipFile(sourceImage.sourceFile).use { zip ->
                val entry = zip.getEntry(sourceImage.zipEntryPath)
                    ?: error("Source pack image not found: ${sourceImage.zipEntryPath}")
                zip.getInputStream(entry).use { it.copyTo(out) }
            }
            else -> {
                warnings += "Pack image not recognized; generated placeholder pack.png."
                out.write(TRANSPARENT_PNG)
            }
        }
        out.closeEntry()
    }

    private fun mergeOne(
        file: File,
        out: ZipOutputStream,
        outputIndex: MutableList<ImportInformationEntry>,
        usedZipEntries: MutableSet<String>,
        usedDirectories: MutableSet<String>,
        usedIdentifiers: MutableSet<String>,
        options: BundleOptions,
        warnings: MutableList<String>,
    ) {
        ZipFile(file).use { zip ->
            val levels = readIndexEntries(zip)
                .filter { it.type == ArcpkgEntryType.LEVEL }
            levels.forEach { entry ->
                val override = options.overrideFor(file, entry)
                val enabled = override?.enabled ?: true
                if (!enabled) {
                    warnings += "Skipped disabled level: ${entry.identifier.ifBlank { entry.directory }}"
                    return@forEach
                }
                val newDirectory = uniqueName(entry.directory, usedDirectories)
                if (newDirectory != entry.directory) {
                    warnings += "Directory conflict: ${entry.directory} renamed to $newDirectory"
                }
                val identifierBase = override?.levelId?.takeIf { it.isNotBlank() }
                    ?.let { "${options.publisherId.ifBlank { "etoilebridge" }}.${it.safeId(entry.directory)}" }
                    ?: entry.identifier.ifBlank { "${options.publisherId}.${entry.directory}" }
                val newIdentifier = uniqueName(identifierBase, usedIdentifiers)
                if (newIdentifier != identifierBase) {
                    warnings += "Identifier conflict: $identifierBase renamed to $newIdentifier"
                }

                copyDirectory(zip, entry.directory, newDirectory, entry.settingsFile, override, out, usedZipEntries)
                outputIndex += entry.copy(directory = newDirectory, identifier = newIdentifier)
            }
        }
    }

    private fun copyDirectory(
        zip: ZipFile,
        sourceDirectory: String,
        targetDirectory: String,
        settingsFile: String,
        override: BundleEntryOverride?,
        out: ZipOutputStream,
        usedZipEntries: MutableSet<String>,
    ) {
        val prefix = sourceDirectory.trimEnd('/') + "/"
        val targetPrefix = targetDirectory.trimEnd('/') + "/"
        val entries = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.startsWith(prefix) }
            .toList()
        require(entries.isNotEmpty()) { "No files found for directory $sourceDirectory" }
        entries.forEach { entry ->
            val relative = entry.name.removePrefix(prefix)
            val targetName = targetPrefix + relative
            require(usedZipEntries.add(targetName)) { "Zip entry conflict: $targetName" }
            out.putNextEntry(ZipEntry(targetName))
            if (relative == settingsFile && override != null && override.hasProjectMetadataOverride()) {
                zip.getInputStream(entry).use { input ->
                    val bytes = rewriteProjectInformation(input, override).toByteArray(Charsets.UTF_8)
                    out.write(bytes)
                }
            } else {
                zip.getInputStream(entry).use { input -> input.copyTo(out) }
            }
            out.closeEntry()
        }
    }

    private fun copyExistingPackDirectory(
        zip: ZipFile,
        packEntry: ArcpkgPackEntry,
        levelIdentifiers: List<String>,
        options: BundleOptions,
        out: ZipOutputStream,
        usedZipEntries: MutableSet<String>,
        warnings: MutableList<String>,
    ) {
        val packInfo = readPackInformation(zip, packEntry)
        val imagePath = packInfo?.imagePath?.takeIf { it.isNotBlank() }
            ?: packEntry.imagePath?.takeIf { it.isNotBlank() }
            ?: PACK_IMAGE_NAME
        val packName = options.packName?.takeIf { it.isNotBlank() }
            ?: packInfo?.packName?.takeIf { it.isNotBlank() }
            ?: packEntry.packName?.takeIf { it.isNotBlank() }
            ?: packEntry.directory
        val prefix = packEntry.directory.trimEnd('/') + "/"
        val entries = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.startsWith(prefix) }
            .toList()
        require(entries.isNotEmpty()) { "No files found for pack directory ${packEntry.directory}" }

        var wroteSettings = false
        var wroteImage = false
        entries.forEach { sourceEntry ->
            val relative = sourceEntry.name.removePrefix(prefix)
            val targetName = prefix + relative
            require(usedZipEntries.add(targetName)) { "Zip entry conflict: $targetName" }
            out.putNextEntry(ZipEntry(targetName))
            when {
                relative == packEntry.settingsFile -> {
                    wroteSettings = true
                    EtoileYaml.encodeToStream(
                        PackInformation(
                            imagePath = imagePath,
                            levelIdentifiers = levelIdentifiers,
                            packName = packName,
                        ),
                        out,
                    )
                }
                relative == imagePath && options.packImageFile?.isFile == true -> {
                    wroteImage = true
                    options.packImageFile.inputStream().use { it.copyTo(out) }
                }
                else -> {
                    if (relative == imagePath) wroteImage = true
                    zip.getInputStream(sourceEntry).use { input -> input.copyTo(out) }
                }
            }
            out.closeEntry()
        }

        if (!wroteSettings) {
            putTextEntry(out, usedZipEntries, "$prefix${packEntry.settingsFile}") {
                EtoileYaml.encodeToStream(
                    PackInformation(
                        imagePath = imagePath,
                        levelIdentifiers = levelIdentifiers,
                        packName = packName,
                    ),
                    it,
                )
            }
        }
        if (!wroteImage) {
            val targetName = prefix + imagePath
            require(usedZipEntries.add(targetName)) { "Zip entry conflict: $targetName" }
            out.putNextEntry(ZipEntry(targetName))
            if (options.packImageFile?.isFile == true) {
                options.packImageFile.inputStream().use { it.copyTo(out) }
            } else {
                warnings += "Pack image not found in existing pack; generated placeholder ${imagePath}."
                out.write(TRANSPARENT_PNG)
            }
            out.closeEntry()
        }
    }

    private fun readPackInformation(zip: ZipFile, entry: ArcpkgPackEntry): PackInformation? {
        val settingsPath = "${entry.directory.trimEnd('/')}/${entry.settingsFile}"
        val zipEntry = zip.getEntry(settingsPath) ?: return null
        return runCatching {
            zip.getInputStream(zipEntry).use { EtoileYaml.decodeFromStream(PackInformation.serializer(), it) }
        }.getOrNull()
    }

    private fun estimateAddedConflictCount(
        baseEntries: ArcpkgReadResult,
        addedScan: ArcpkgBundleScanResult?,
    ): Int {
        if (addedScan == null) return 0
        val usedDirectories = mutableSetOf<String>()
        val usedIdentifiers = mutableSetOf<String>()
        baseEntries.packs.firstOrNull()?.let {
            usedDirectories += it.directory
            usedIdentifiers += it.identifier
        }
        baseEntries.levels.forEach {
            usedDirectories += it.directory
            usedIdentifiers += it.identifier
        }
        var conflicts = 0
        addedScan.levelEntries.forEach { level ->
            if (!usedDirectories.add(level.directory)) conflicts++
            if (!usedIdentifiers.add(level.identifier)) conflicts++
        }
        return conflicts
    }

    private fun readArcpkgEntries(file: File): ArcpkgReadResult =
        ZipFile(file).use { zip ->
            val index = readIndexEntries(zip)
            val levels = index.filter { it.type == ArcpkgEntryType.LEVEL }
                .map { readLevelEntry(file, zip, it) }
            val levelIdentifiers = levels.map { it.identifier }.toSet()
            val packs = index.filter { it.type == ArcpkgEntryType.PACK }
                .map { entry -> readPackEntry(file, zip, entry, levelIdentifiers) }
            ArcpkgReadResult(levels, packs)
        }

    private fun readLevelEntry(
        file: File,
        zip: ZipFile,
        entry: ImportInformationEntry,
    ): ArcpkgLevelEntry {
        val settingsPath = "${entry.directory.trimEnd('/')}/${entry.settingsFile}"
        val warnings = mutableListOf<String>()
        val zipEntry = zip.getEntry(settingsPath)
        if (zipEntry == null) {
            return ArcpkgLevelEntry(
                key = levelKey(file, entry),
                sourceFile = file,
                directory = entry.directory,
                identifier = entry.identifier,
                settingsFile = entry.settingsFile,
                version = entry.version,
                warnings = listOf("Level settings file not found: $settingsPath"),
                failureReason = "Level settings file not found.",
            )
        }

        val projectText = zip.getInputStream(zipEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val parsedCharts = readProjectCharts(projectText, warnings)
        val title = parsedCharts.firstNotNullOfOrNull { it.title.takeIf { value -> value.isNotBlank() } }
        val artist = parsedCharts.firstNotNullOfOrNull { it.composer.takeIf { value -> value.isNotBlank() } }
        val charts = parsedCharts.mapIndexed { index, chart ->
            chart.toBundleChartEntry(index)
        }
        return ArcpkgLevelEntry(
            key = levelKey(file, entry),
            sourceFile = file,
            directory = entry.directory,
            identifier = entry.identifier,
            settingsFile = entry.settingsFile,
            version = entry.version,
            title = title,
            artist = artist,
            difficultySummary = charts.mapNotNull { it.difficulty?.takeIf { value -> value.isNotBlank() } }
                .joinToString(" · "),
            charts = charts,
            warnings = warnings,
            failureReason = if (charts.isEmpty()) "Unable to read ArcCreate charts." else null,
        )
    }

    private fun readProjectCharts(projectText: String, warnings: MutableList<String>): List<ProjectChartMetadata> {
        val strict = runCatching {
            EtoileYaml.decodeFromStream(
                ProjectInformation.serializer(),
                projectText.byteInputStream(Charsets.UTF_8),
            )
        }
        strict.getOrNull()?.let { project ->
            return project.charts.map { it.toProjectChartMetadata() }
        }

        warnings += "Project metadata parsed with compatible fallback: ${strict.exceptionOrNull()?.message ?: "unknown error"}"
        return parseProjectChartsFallback(projectText)
    }

    private fun ChartEntry.toProjectChartMetadata(): ProjectChartMetadata =
        ProjectChartMetadata(
            chartPath = chartPath,
            title = title,
            composer = composer,
            charter = charter,
            illustrator = illustrator,
            difficulty = difficulty,
            chartConstant = chartConstant,
        )

    private fun parseProjectChartsFallback(projectText: String): List<ProjectChartMetadata> {
        val charts = mutableListOf<MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        projectText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("- chartPath:") -> {
                    current = mutableMapOf("chartPath" to line.substringAfter(":").trimYamlScalar())
                    charts += requireNotNull(current)
                }
                current != null && line.contains(":") && !line.startsWith("- ") -> {
                    val key = line.substringBefore(":").trim()
                    val value = line.substringAfter(":").trimYamlScalar()
                    if (key in FALLBACK_PROJECT_CHART_KEYS) {
                        requireNotNull(current)[key] = value
                    }
                }
            }
        }
        return charts.map { map ->
            ProjectChartMetadata(
                chartPath = map["chartPath"].orEmpty(),
                title = map["title"].orEmpty(),
                composer = map["composer"].orEmpty(),
                charter = map["charter"],
                illustrator = map["illustrator"],
                difficulty = map["difficulty"].orEmpty(),
                chartConstant = map["chartConstant"]?.toFloatOrNull(),
            )
        }.filter { it.chartPath.isNotBlank() }
    }

    private fun ProjectChartMetadata.toBundleChartEntry(index: Int): BundleChartEntry {
        val ratingClass = chartPath.substringBeforeLast('.', chartPath)
            .toIntOrNull()
            ?.takeIf { it in 0..4 }
            ?: index
        return BundleChartEntry(
            ratingClass = ratingClass,
            chartPath = chartPath,
            difficulty = difficulty,
            chartConstant = chartConstant,
            charter = charter,
            illustrator = illustrator,
            enabled = true,
            canConvert = chartPath.isNotBlank(),
            failureReason = if (chartPath.isBlank()) "Missing chartPath." else null,
        )
    }

    private fun readPackEntry(
        file: File,
        zip: ZipFile,
        entry: ImportInformationEntry,
        indexLevelIdentifiers: Set<String>,
    ): ArcpkgPackEntry {
        val settingsPath = "${entry.directory.trimEnd('/')}/${entry.settingsFile}"
        val packInfo = zip.getEntry(settingsPath)?.let { zipEntry ->
            runCatching {
                zip.getInputStream(zipEntry).use { EtoileYaml.decodeFromStream(PackInformation.serializer(), it) }
            }.getOrNull()
        }
        val imagePath = packInfo?.imagePath
        val imageZipPath = imagePath?.takeIf { it.isNotBlank() }?.let { "${entry.directory.trimEnd('/')}/$it" }
        val imageExists = imageZipPath?.let { zip.getEntry(it) != null } == true
        val identifiers = packInfo?.levelIdentifiers.orEmpty()
        val matches = packInfo?.let { identifiers.toSet() == indexLevelIdentifiers }
        return ArcpkgPackEntry(
            sourceFile = file,
            directory = entry.directory,
            identifier = entry.identifier,
            settingsFile = entry.settingsFile,
            version = entry.version,
            packName = packInfo?.packName,
            imagePath = imagePath,
            packImageZipPath = imageZipPath,
            packImageExists = imageExists,
            levelIdentifiers = identifiers,
            matchesIndexLevels = matches,
        )
    }

    private fun readIndexEntries(zip: ZipFile): List<ImportInformationEntry> {
        val index = zip.getEntry("index.yml") ?: zip.getEntry("index.yaml")
            ?: error("index.yml not found")
        return zip.getInputStream(index).use {
            EtoileYaml.decodeFromStream(ListSerializer(ImportInformationEntry.serializer()), it)
        }
    }

    private fun collectArcpkgFiles(input: File): List<File> =
        when {
            input.isFile && input.extension.equals("arcpkg", ignoreCase = true) -> listOf(input)
            input.isDirectory -> input.walkTopDown()
                .filter { it.isFile && it.extension.equals("arcpkg", ignoreCase = true) }
                .toList()
                .sortedBy { it.name.lowercase() }
            else -> emptyList()
        }

    private fun uniqueName(base: String, used: MutableSet<String>): String {
        if (used.add(base)) return base
        var index = 2
        while (true) {
            val candidate = "${base}_$index"
            if (used.add(candidate)) return candidate
            index++
        }
    }

    private fun String.safeId(fallback: String): String =
        replace(Regex("""[^\w.-]+"""), "_")
            .trim('_', '.', '-')
            .ifBlank { fallback }

    private fun BundleOptions.overrideFor(file: File, entry: ImportInformationEntry): BundleEntryOverride? {
        val key = levelKey(file, entry)
        return entryOverrides[key]
            ?: entryOverrides[entry.identifier]
            ?: entryOverrides[entry.directory]
    }

    private fun levelKey(file: File, entry: ImportInformationEntry): String =
        "${file.canonicalPathSafe()}#${entry.directory}#${entry.identifier}"

    private fun File.canonicalPathSafe(): String =
        runCatching { canonicalPath }.getOrElse { absolutePath }

    private fun String.trimYamlScalar(): String =
        trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")

    private fun BundleEntryOverride.hasProjectMetadataOverride(): Boolean =
        !title.isNullOrBlank() ||
            !artist.isNullOrBlank() ||
            chartOverrides.values.any { chart ->
                listOf(chart.charter, chart.illustrator, chart.difficulty).any { !it.isNullOrBlank() } ||
                    chart.chartConstant != null
            }

    private fun rewriteProjectInformation(input: InputStream, override: BundleEntryOverride): String {
        val project = EtoileYaml.decodeFromStream(ProjectInformation.serializer(), input)
        val rewritten = project.copy(
            charts = project.charts.mapIndexed { index, chart ->
                val chartOverride = override.chartOverrides[chart.bundleChartKey(index)]
                chart.copy(
                    title = override.title?.takeIf { it.isNotBlank() } ?: chart.title,
                    composer = override.artist?.takeIf { it.isNotBlank() } ?: chart.composer,
                    charter = chartOverride?.charter?.takeIf { it.isNotBlank() } ?: chart.charter,
                    illustrator = chartOverride?.illustrator?.takeIf { it.isNotBlank() } ?: chart.illustrator,
                    difficulty = chartOverride?.difficulty?.takeIf { it.isNotBlank() } ?: chart.difficulty,
                    chartConstant = chartOverride?.chartConstant ?: chart.chartConstant,
                )
            }
        )
        return java.io.ByteArrayOutputStream().use { buffer ->
            EtoileYaml.encodeToStream(ProjectInformation.serializer(), rewritten, buffer)
            buffer.toString(Charsets.UTF_8.name())
        }
    }

    private fun ChartEntry.bundleChartKey(index: Int): Int =
        chartPath.substringBeforeLast('.', chartPath)
            .toIntOrNull()
            ?.takeIf { it in 0..4 }
            ?: index

    private data class ArcpkgReadResult(
        val levels: List<ArcpkgLevelEntry>,
        val packs: List<ArcpkgPackEntry>,
    )

    private data class ProjectChartMetadata(
        val chartPath: String,
        val title: String = "",
        val composer: String = "",
        val charter: String? = null,
        val illustrator: String? = null,
        val difficulty: String = "",
        val chartConstant: Float? = null,
    )

    companion object {
        private const val PACK_IMAGE_NAME = "pack.png"
        private val FALLBACK_PROJECT_CHART_KEYS = setOf(
            "title",
            "composer",
            "charter",
            "illustrator",
            "difficulty",
            "chartConstant",
        )
        private val TRANSPARENT_PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(), 0x89.toByte(),
            0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
            0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
            0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
    }
}
