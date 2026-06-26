package com.zeerqi27.etoilebridge.cli

import com.zeerqi27.etoilebridge.core.ConvertInput
import com.zeerqi27.etoilebridge.core.ConvertLogger
import com.zeerqi27.etoilebridge.core.ConvertOptions
import com.zeerqi27.etoilebridge.core.ConvertResult
import com.zeerqi27.etoilebridge.core.EtoileBridgeConverter
import com.zeerqi27.etoilebridge.core.ArcpkgBundleMerger
import com.zeerqi27.etoilebridge.core.BundleOutputValidator
import com.zeerqi27.etoilebridge.core.BundleConvertResult
import com.zeerqi27.etoilebridge.core.BundleInput
import com.zeerqi27.etoilebridge.core.BundleOptions
import com.zeerqi27.etoilebridge.core.CharacterPackageScanner
import com.zeerqi27.etoilebridge.core.PackBundleConverter
import com.zeerqi27.etoilebridge.core.PackBundleScanner
import com.zeerqi27.etoilebridge.core.InputKind
import com.zeerqi27.etoilebridge.core.InputScanner
import com.zeerqi27.etoilebridge.core.MetadataResolution
import com.zeerqi27.etoilebridge.core.MetadataResolver
import com.zeerqi27.etoilebridge.core.MissingRequiredResourceException
import com.zeerqi27.etoilebridge.core.PacklistParser
import com.zeerqi27.etoilebridge.core.ResourceResolver
import com.zeerqi27.etoilebridge.core.ResolvedDifficultyMetadata
import com.zeerqi27.etoilebridge.core.ResolvedSongMetadata
import com.zeerqi27.etoilebridge.core.ScannedInput
import com.zeerqi27.etoilebridge.core.Songlist
import com.zeerqi27.etoilebridge.core.SonglistParser
import com.zeerqi27.etoilebridge.core.SonglistSong
import java.io.File
import java.io.PrintStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess

enum class CliMode {
    ConvertSingle,
    ScanOnly,
    ScanPack,
    ScanArcpkgPack,
    MergeArcpkg,
    EditPackArcpkg,
    ConvertPack,
    ScanCharacter,
}

data class CliArguments(
    val inputDir: File,
    val outputDir: File? = null,
    val basePack: File? = null,
    val addInput: File? = null,
    val songId: String? = null,
    val mode: CliMode = CliMode.ConvertSingle,
    val publisherId: String = "etoilebridge",
    val validateOutput: Boolean = false,
    val keepTemp: Boolean = false,
    val enableDeleteDesignantLine: Boolean = true,
    val enableFixZeroDurationArcTap: Boolean = true,
    val enableFixReversedArcTime: Boolean = true,
    val enableExpandArcResolution: Boolean = true,
)

private data class PackScanCliReport(
    val source: String,
    val extract: String,
    val projectRoot: String,
    val packName: String?,
    val packId: String?,
    val packImage: String?,
    val songCount: Int,
    val convertible: Int,
    val needsMetadata: Int,
    val failed: Int,
    val warnings: List<String>,
    val entries: List<com.zeerqi27.etoilebridge.core.BundleEntry>,
)

object CliArgsParser {
    fun parse(args: Array<String>): CliArguments {
        var input: String? = null
        var output: String? = null
        var base: String? = null
        var add: String? = null
        var songId: String? = null
        var mode = CliMode.ConvertSingle
        var publisherId = "etoilebridge"
        var validateOutput = false
        var keepTemp = false
        var enableDeleteDesignantLine = true
        var enableFixZeroDurationArcTap = true
        var enableFixReversedArcTime = true
        var enableExpandArcResolution = true

        var index = 0
        while (index < args.size) {
            val token = args[index]
            val split = token.splitOption()
            val option = split?.first ?: token
            val inlineValue = split?.second

            when (option) {
                "--input" -> input = inlineValue ?: args.valueAfter(index, option).also { index++ }
                "--output" -> output = inlineValue ?: args.valueAfter(index, option).also { index++ }
                "--base" -> base = inlineValue ?: args.valueAfter(index, option).also { index++ }
                "--add" -> add = inlineValue ?: args.valueAfter(index, option).also { index++ }
                "--song-id" -> songId = inlineValue ?: args.valueAfter(index, option).also { index++ }
                "--publisher-id" -> publisherId = inlineValue ?: args.valueAfter(index, option).also { index++ }
                "--keep-temp" -> keepTemp = expectNoInlineValue(option, inlineValue)
                "--scan-only" -> {
                    expectNoInlineValue(option, inlineValue)
                    mode = CliMode.ScanOnly
                }
                "--scan-pack" -> {
                    expectNoInlineValue(option, inlineValue)
                    mode = CliMode.ScanPack
                }
                "--scan-arcpkg-pack" -> {
                    expectNoInlineValue(option, inlineValue)
                    mode = CliMode.ScanArcpkgPack
                }
                "--merge-arcpkg" -> {
                    expectNoInlineValue(option, inlineValue)
                    mode = CliMode.MergeArcpkg
                }
                "--edit-pack-arcpkg" -> {
                    expectNoInlineValue(option, inlineValue)
                    mode = CliMode.EditPackArcpkg
                }
                "--convert-pack" -> {
                    expectNoInlineValue(option, inlineValue)
                    mode = CliMode.ConvertPack
                }
                "--scan-character" -> {
                    expectNoInlineValue(option, inlineValue)
                    mode = CliMode.ScanCharacter
                }
                "--validate-output" -> validateOutput = expectNoInlineValue(option, inlineValue)
                "--disable-delete-designant" -> enableDeleteDesignantLine = !expectNoInlineValue(option, inlineValue)
                "--disable-fix-zero-arctap" -> enableFixZeroDurationArcTap = !expectNoInlineValue(option, inlineValue)
                "--disable-fix-reversed-arc" -> enableFixReversedArcTime = !expectNoInlineValue(option, inlineValue)
                "--disable-arcresolution" -> enableExpandArcResolution = !expectNoInlineValue(option, inlineValue)
                "--help", "-h" -> throw UsageRequested()
                else -> throw IllegalArgumentException("Unknown option: $token")
            }
            index++
        }

        if (mode in setOf(CliMode.ConvertSingle, CliMode.MergeArcpkg, CliMode.EditPackArcpkg, CliMode.ConvertPack) && output == null) {
            throw IllegalArgumentException("Missing required option: --output")
        }
        if (mode == CliMode.EditPackArcpkg && base == null && input == null) {
            throw IllegalArgumentException("Missing required option: --base")
        }

        return CliArguments(
            inputDir = File(input ?: base ?: throw IllegalArgumentException("Missing required option: --input")),
            outputDir = output?.let(::File),
            basePack = base?.let(::File),
            addInput = add?.let(::File),
            songId = songId?.takeIf { it.isNotBlank() },
            mode = mode,
            publisherId = publisherId.ifBlank { "etoilebridge" },
            validateOutput = validateOutput,
            keepTemp = keepTemp,
            enableDeleteDesignantLine = enableDeleteDesignantLine,
            enableFixZeroDurationArcTap = enableFixZeroDurationArcTap,
            enableFixReversedArcTime = enableFixReversedArcTime,
            enableExpandArcResolution = enableExpandArcResolution,
        )
    }

    private fun String.splitOption(): Pair<String, String>? {
        if (!startsWith("--")) return null
        val equalsIndex = indexOf('=')
        return if (equalsIndex == -1) null else substring(0, equalsIndex) to substring(equalsIndex + 1)
    }

    private fun Array<String>.valueAfter(index: Int, option: String): String {
        val value = getOrNull(index + 1)
            ?: throw IllegalArgumentException("Missing value for $option")
        if (value.startsWith("--")) {
            throw IllegalArgumentException("Missing value for $option")
        }
        return value
    }

    private fun expectNoInlineValue(option: String, value: String?): Boolean {
        require(value == null) { "$option does not take a value" }
        return true
    }
}

class UsageRequested : IllegalArgumentException("Usage requested")

fun main(args: Array<String>) {
    val exitCode = runCli(args, System.out, System.err)
    if (exitCode != 0) exitProcess(exitCode)
}

fun runCli(
    args: Array<String>,
    out: PrintStream = System.out,
    err: PrintStream = System.err,
): Int {
    val parsed = try {
        CliArgsParser.parse(args)
    } catch (_: UsageRequested) {
        out.println(usageText())
        return 0
    } catch (e: IllegalArgumentException) {
        err.println(e.message)
        err.println()
        err.println(usageText())
        return 2
    }

    when (parsed.mode) {
        CliMode.ScanOnly -> return runScanOnly(parsed.inputDir, parsed.songId, out, err)
        CliMode.ScanPack -> return runScanPack(parsed.inputDir, out, err)
        CliMode.ScanArcpkgPack -> return runScanArcpkgPack(parsed.inputDir, out, err)
        CliMode.MergeArcpkg -> return runMergeArcpkg(parsed, out, err)
        CliMode.EditPackArcpkg -> return runEditPackArcpkg(parsed, out, err)
        CliMode.ConvertPack -> return runConvertPack(parsed, out, err)
        CliMode.ScanCharacter -> return runScanCharacter(parsed.inputDir, out, err)
        CliMode.ConvertSingle -> Unit
    }

    if (!parsed.inputDir.isDirectory) {
        err.println("Input is not a directory: ${parsed.inputDir.absolutePath}")
        return 2
    }
    val outputDir = requireNotNull(parsed.outputDir)
    if (outputDir.exists() && !outputDir.isDirectory) {
        err.println("Output is not a directory: ${outputDir.absolutePath}")
        return 2
    }
    outputDir.mkdirs()

    val result = EtoileBridgeConverter.convert(
        input = ConvertInput(
            workspaceDir = parsed.inputDir,
            outputFile = outputDir,
            targetSongId = parsed.songId,
        ),
        options = ConvertOptions(
            enableDeleteDesignantLine = parsed.enableDeleteDesignantLine,
            enableFixZeroDurationArcTap = parsed.enableFixZeroDurationArcTap,
            enableFixReversedArcTime = parsed.enableFixReversedArcTime,
            enableExpandArcResolution = parsed.enableExpandArcResolution,
            keepWorkspaceOnFailure = parsed.keepTemp,
            cleanWorkspaceOnSuccess = true,
        ),
        logger = ConvertLogger.NONE,
    )

    return when (result) {
        is ConvertResult.Success -> {
            printSuccess(result, out)
            0
        }
        is ConvertResult.NeedMetadata -> {
            printNeedMetadata(result, out)
            1
        }
        is ConvertResult.UnsupportedPackStructure -> {
            printUnsupportedPack(result, err)
            1
        }
        is ConvertResult.Failed -> {
            printFailed(result, parsed.inputDir, err)
            1
        }
    }
}

private fun runScanOnly(input: File, songId: String?, out: PrintStream, err: PrintStream): Int {
    if (!input.exists()) {
        err.println("Input does not exist: ${input.absolutePath}")
        return 2
    }
    val targets = when {
        input.isFile -> listOf(input)
        input.isDirectory -> input.listFiles()
            ?.filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(input)
        else -> emptyList()
    }
    val scanRoot = File("build/tmp/scan-only").absoluteFile.apply { mkdirs() }
    val reports = targets.map { target -> scanTarget(target, songId, scanRoot) }
    printScanReportsTable(reports, out)
    return 0
}

private fun runScanPack(input: File, out: PrintStream, err: PrintStream): Int {
    if (!input.exists()) {
        err.println("Input does not exist: ${input.absolutePath}")
        return 2
    }
    val targets = collectZipOrDirectoryTargets(input)
    val scanRoot = File("build/tmp/scan-pack").absoluteFile.apply { mkdirs() }
    out.println("zip/file\textract\tprojectRoot\tpackName\tpackId\tpackImage\tsongCount\tconvertible\tneedsMetadata\tfailed\twarnings")
    targets.forEach { target ->
        val report = runCatching {
            val root = if (target.isFile) {
                val archiveDir = scanRoot.resolve(target.nameWithoutExtension.sanitizeFileName()).apply {
                    deleteRecursively()
                    mkdirs()
                }
                extractZip(target, archiveDir)
                archiveDir
            } else {
                target
            }
            val scan = PackBundleScanner().scanOfficialPack(root)
            val needsMetadata = scan.entries.count { it.metadataStatus == com.zeerqi27.etoilebridge.core.BundleMetadataStatus.NeedMetadata }
            val failed = scan.entries.count { !it.canConvert && it.metadataStatus != com.zeerqi27.etoilebridge.core.BundleMetadataStatus.NeedMetadata }
            PackScanCliReport(
                source = target.name,
                extract = if (target.isFile) "extracted" else "not needed",
                projectRoot = scan.projectRoot.absolutePath,
                packName = scan.packNameCandidate,
                packId = scan.packIdCandidate,
                packImage = scan.packImageFile?.name,
                songCount = scan.entries.size,
                convertible = scan.convertibleEntries.size,
                needsMetadata = needsMetadata,
                failed = failed,
                warnings = (scan.warnings + scan.entries.flatMap { it.warnings }).distinct(),
                entries = scan.entries,
            )
        }.getOrElse { error ->
            PackScanCliReport(
                source = target.name,
                extract = "failed",
                projectRoot = "",
                packName = null,
                packId = null,
                packImage = null,
                songCount = 0,
                convertible = 0,
                needsMetadata = 0,
                failed = 0,
                warnings = listOf(error.message ?: "scan failed"),
                entries = emptyList(),
            )
        }
        out.println(
            listOf(
                report.source,
                report.extract,
                report.projectRoot,
                report.packName.orEmpty(),
                report.packId.orEmpty(),
                report.packImage ?: "-",
                report.songCount,
                report.convertible,
                report.needsMetadata,
                report.failed,
                report.warnings.joinToString(" | "),
            ).joinToString("\t")
        )
        report.entries.forEach { entry ->
            out.println(
                "  - ${entry.songId}\t${entry.title.orEmpty()}\t${entry.artist.orEmpty()}\t" +
                    "aff=${entry.affFiles.joinToString { it.name }}\t" +
                    "audio=${entry.audioFile?.name ?: "-"}\tjacket=${entry.jacketFile?.name ?: "-"}\t" +
                    "background=${entry.backgroundFile?.name ?: "-"}\tcanConvert=${entry.canConvert}\t" +
                    "reason=${entry.failureReason.orEmpty()}"
            )
        }
    }
    return 0
}

private fun runScanArcpkgPack(input: File, out: PrintStream, err: PrintStream): Int {
    if (!input.exists()) {
        err.println("Input does not exist: ${input.absolutePath}")
        return 2
    }
    val scan = ArcpkgBundleMerger().scan(input)
    out.println("source\treadable\tlevelCount\tpackEntry\tpackName\tpackImage\tpackImageExists\tpackLevelIdentifiers\tpackMatchesIndexLevels\tfailure")
    scan.sourceFiles.forEach { report ->
        out.println(
            listOf(
                report.sourceFile.name,
                report.readable,
                report.levelCount,
                report.packEntryCount,
                report.packName.orEmpty(),
                report.packImagePath.orEmpty(),
                report.packImageExists,
                report.packLevelIdentifierCount,
                report.packMatchesIndexLevels?.toString().orEmpty(),
                report.failureReason.orEmpty(),
            ).joinToString("\t")
        )
    }
    out.println()
    out.println("pack source\tdirectory\tidentifier\tsettingsFile\tpackName\timagePath\timageExists\tlevelIdentifiers\tmatchesIndexLevels")
    scan.packEntries.forEach { pack ->
        out.println(
            listOf(
                pack.sourceFile.name,
                pack.directory,
                pack.identifier,
                pack.settingsFile,
                pack.packName.orEmpty(),
                pack.imagePath.orEmpty(),
                pack.packImageExists,
                pack.levelIdentifiers.size,
                pack.matchesIndexLevels?.toString().orEmpty(),
            ).joinToString("\t")
        )
    }
    out.println()
    out.println("level source\tdirectory\tidentifier\tsettingsFile\tversion\ttitle\tcomposer\tcharts\tchartPaths\tdifficulties\tchartConstants\tcharters\tfailure")
    scan.levelEntries.forEach { level ->
        out.println(
            listOf(
                level.sourceFile.name,
                level.directory,
                level.identifier,
                level.settingsFile,
                level.version,
                level.title.orEmpty(),
                level.artist.orEmpty(),
                level.charts.size,
                level.charts.joinToString(" | ") { it.chartPath },
                level.charts.joinToString(" | ") { it.difficulty.orEmpty() },
                level.charts.joinToString(" | ") { it.chartConstant?.toString().orEmpty() },
                level.charts.joinToString(" | ") { it.charter.orEmpty() },
                level.failureReason.orEmpty(),
            ).joinToString("\t")
        )
    }
    if (scan.warnings.isNotEmpty()) {
        out.println()
        out.println("warnings:")
        scan.warnings.forEach { out.println("- $it") }
    }
    return 0
}

private fun runScanCharacter(input: File, out: PrintStream, err: PrintStream): Int {
    if (!input.exists()) {
        err.println("Input does not exist: ${input.absolutePath}")
        return 2
    }
    val scan = CharacterPackageScanner().scan(input)
    out.println("source\teditable\tdirectory\tidentifier\tsettingsFile\tname.default\tname.zh-cn\timagePath\ticonPath\tx\ty\tscale\timageFound\ticonFound\twarnings\terrors")
    scan.packages.forEach { item ->
        val character = item.character
        out.println(
            listOf(
                item.sourceFile.name,
                item.isEditable,
                item.directory.orEmpty(),
                item.identifier.orEmpty(),
                item.settingsFile.orEmpty(),
                character?.name?.get("default").orEmpty(),
                character?.name?.get("zh-cn").orEmpty(),
                character?.imagePath.orEmpty(),
                character?.iconPath.orEmpty(),
                character?.x?.toString().orEmpty(),
                character?.y?.toString().orEmpty(),
                character?.scale?.toString().orEmpty(),
                item.imageFile != null || item.imageZipPath != null,
                item.iconFile != null || item.iconZipPath != null,
                item.warnings.joinToString(" | "),
                item.errors.joinToString(" | "),
            ).joinToString("\t")
        )
    }
    if (scan.warnings.isNotEmpty()) {
        out.println()
        out.println("warnings:")
        scan.warnings.forEach { out.println("- $it") }
    }
    return if (scan.packages.any { it.isEditable }) 0 else 1
}

private fun runMergeArcpkg(parsed: CliArguments, out: PrintStream, err: PrintStream): Int {
    val output = resolveBundleOutputFile(
        requireNotNull(parsed.outputDir),
        defaultBundleOutputFileName(parsed.publisherId, parsed.inputDir.nameWithoutExtension),
    )
    val result = ArcpkgBundleMerger().merge(parsed.inputDir, output, parsed.publisherId)
    return when (result) {
        is BundleConvertResult.Success -> {
            out.println("Merged arcpkg bundle: ${result.outputFile.absolutePath}")
            out.println("Converted entries: ${result.convertedCount}")
            if (parsed.validateOutput) printBundleValidation(result.outputFile, out)
            if (result.warnings.isNotEmpty()) {
                out.println("Warnings:")
                result.warnings.forEach { out.println("- $it") }
            }
            0
        }
        is BundleConvertResult.Failed -> {
            err.println("Merge failed: ${result.message}")
            result.cause?.printStackTrace(err)
            if (result.warnings.isNotEmpty()) {
                err.println("Warnings:")
                result.warnings.forEach { err.println("- $it") }
            }
            1
        }
    }
}

private fun runEditPackArcpkg(parsed: CliArguments, out: PrintStream, err: PrintStream): Int {
    val base = parsed.basePack ?: parsed.inputDir
    if (!base.exists()) {
        err.println("Base pack does not exist: ${base.absolutePath}")
        return 2
    }
    val addInput = parsed.addInput
    if (addInput != null && !addInput.exists()) {
        err.println("Added input does not exist: ${addInput.absolutePath}")
        return 2
    }
    val output = resolveBundleOutputFile(
        requireNotNull(parsed.outputDir),
        defaultBundleOutputFileName(parsed.publisherId, base.nameWithoutExtension),
    )
    val scan = ArcpkgBundleMerger().scanExistingPack(base, addInput)
    out.println("Existing pack levels: ${scan.existingLevelCount}")
    out.println("Added levels: ${scan.addedLevelCount}")
    out.println("Final levels: ${scan.finalLevelCount}")
    out.println("Renamed conflicts: ${scan.renamedConflictCount}")
    val result = ArcpkgBundleMerger().editExistingPack(
        basePack = base,
        addInput = addInput,
        outputFile = output,
        options = BundleOptions(
            publisherId = parsed.publisherId,
            outputFileName = output.name,
        ),
    )
    return when (result) {
        is BundleConvertResult.Success -> {
            out.println("Edited pack arcpkg: ${result.outputFile.absolutePath}")
            out.println("Level entries: ${result.convertedCount}")
            if (parsed.validateOutput) printBundleValidation(result.outputFile, out)
            val warnings = scan.warnings + result.warnings
            if (warnings.isNotEmpty()) {
                out.println("Warnings:")
                warnings.distinct().forEach { out.println("- $it") }
            }
            0
        }
        is BundleConvertResult.Failed -> {
            err.println("Edit pack failed: ${result.message}")
            result.cause?.printStackTrace(err)
            val warnings = scan.warnings + result.warnings
            if (warnings.isNotEmpty()) {
                err.println("Warnings:")
                warnings.distinct().forEach { err.println("- $it") }
            }
            1
        }
    }
}

private fun runConvertPack(parsed: CliArguments, out: PrintStream, err: PrintStream): Int {
    if (!parsed.inputDir.exists()) {
        err.println("Input does not exist: ${parsed.inputDir.absolutePath}")
        return 2
    }
    val workRoot = File("build/tmp/convert-pack").absoluteFile.apply { mkdirs() }
    val inputRoot = try {
        if (parsed.inputDir.isFile && parsed.inputDir.extension.equals("zip", ignoreCase = true)) {
            val archiveDir = workRoot.resolve(parsed.inputDir.nameWithoutExtension.sanitizeFileName()).apply {
                deleteRecursively()
                mkdirs()
            }
            extractZip(parsed.inputDir, archiveDir)
            archiveDir
        } else {
            parsed.inputDir
        }
    } catch (error: Exception) {
        err.println("Pack input preparation failed: ${error.message}")
        return 1
    }
    val output = resolveBundleOutputFile(
        requireNotNull(parsed.outputDir),
        defaultBundleOutputFileName(parsed.publisherId, inputRoot.name),
    )
    val result = PackBundleConverter().convertOfficialPack(
        BundleInput(
            workspaceDir = inputRoot,
            outputFile = output,
            options = BundleOptions(
                publisherId = parsed.publisherId,
                outputFileName = output.name,
                convertOptions = ConvertOptions(
                    enableDeleteDesignantLine = parsed.enableDeleteDesignantLine,
                    enableFixZeroDurationArcTap = parsed.enableFixZeroDurationArcTap,
                    enableFixReversedArcTime = parsed.enableFixReversedArcTime,
                    enableExpandArcResolution = parsed.enableExpandArcResolution,
                    keepWorkspaceOnFailure = parsed.keepTemp,
                    cleanWorkspaceOnSuccess = true,
                ),
            ),
        )
    )
    return when (result) {
        is BundleConvertResult.Success -> {
            out.println("Converted official pack: ${result.outputFile.absolutePath}")
            out.println("Converted entries: ${result.convertedCount}")
            out.println("Skipped entries: ${result.skippedCount}")
            if (parsed.validateOutput) printBundleValidation(result.outputFile, out)
            if (result.warnings.isNotEmpty()) {
                out.println("Warnings:")
                result.warnings.forEach { out.println("- $it") }
            }
            0
        }
        is BundleConvertResult.Failed -> {
            err.println("Pack conversion failed: ${result.message}")
            result.cause?.printStackTrace(err)
            if (result.warnings.isNotEmpty()) {
                err.println("Warnings:")
                result.warnings.forEach { err.println("- $it") }
            }
            1
        }
    }
}

private fun printBundleValidation(outputFile: File, out: PrintStream) {
    val report = BundleOutputValidator().validateBundleArcpkg(outputFile)
    out.println("validator: ${if (report.valid) "passed" else "failed"}")
    report.summaryLines().forEach { out.println(it) }
    if (report.errors.isNotEmpty()) {
        out.println("validator errors:")
        report.errors.forEach { out.println("- $it") }
    }
    if (report.warnings.isNotEmpty()) {
        out.println("validator warnings:")
        report.warnings.forEach { out.println("- $it") }
    }
}

private fun collectZipOrDirectoryTargets(input: File): List<File> =
    when {
        input.isFile -> listOf(input)
        input.isDirectory -> input.listFiles()
            ?.filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(input)
        else -> emptyList()
    }

private fun resolveBundleOutputFile(output: File, defaultFileName: String = "etoilebridge.EtoileBridgePack.arcpkg"): File =
    when {
        output.isDirectory -> output.resolve(defaultFileName)
        output.extension.isBlank() -> output.resolve(defaultFileName)
        else -> output
    }

private fun defaultBundleOutputFileName(publisherId: String, packId: String): String =
    "${publisherId.safeId("etoilebridge")}.${packId.safeId("EtoileBridgePack")}.arcpkg"
        .sanitizeFileName()

private fun scanTarget(target: File, songId: String?, scanRoot: File): ScanReport {
    if (target.isFile && !target.extension.equals("zip", ignoreCase = true)) {
        return ScanReport(
            sourceName = target.name,
            extractStatus = if (target.extension.lowercase() in setOf("rar", "7z")) {
                "unsupported archive"
            } else {
                "not a zip"
            },
            canConvert = false,
            failureReason = "Only zip archives are supported.",
        )
    }

    return try {
        val inputRoot = if (target.isFile) {
            val archiveDir = scanRoot.resolve(target.nameWithoutExtension.sanitizeFileName()).apply {
                deleteRecursively()
                mkdirs()
            }
            extractZip(target, archiveDir)
            archiveDir
        } else {
            target
        }
        val projectRoot = normalizeArchiveRoot(inputRoot)
        val scan = scanProject(projectRoot, songId)
        scan.copy(
            sourceName = target.name,
            extractStatus = if (target.isFile) "extracted" else "not needed",
            inputRoot = inputRoot.absolutePath,
            projectRoot = projectRoot.absolutePath,
        )
    } catch (error: Exception) {
        ScanReport(
            sourceName = target.name,
            extractStatus = "failed",
            canConvert = false,
            failureReason = "ZIP extract failed: ${error.message}",
        )
    }
}

private fun scanProject(projectRoot: File, requestedSongId: String?): ScanReport {
    val warnings = mutableListOf<String>()
    return try {
        val scanned = InputScanner().scan(projectRoot)
        val rootSonglist = parseSonglistForScan(scanned.songlistFile, projectRoot)
        val target = resolveTarget(scanned, rootSonglist.songlist, requestedSongId)
        val ignoredAff = scanned.ignoredAffFiles.map { it.relativeToOrAbsolute(projectRoot) }.sorted()
        ignoredAff.forEach { warnings += "Ignored non-standard AFF file: $it" }
        val selectedSonglist = if (target != null) {
            val selected = selectSonglistFile(target.songDir, projectRoot, scanned)
            if (selected?.canonicalFile == rootSonglist.file?.canonicalFile) rootSonglist else parseSonglistForScan(selected, projectRoot)
        } else {
            rootSonglist
        }
        selectedSonglist.error?.let { warnings += it }
        val songlist = selectedSonglist.songlist

        if (target == null) {
            return ScanReport(
                kind = scanned.kind.name,
                songId = requestedSongId ?: songlist?.songs?.firstOrNull()?.id,
                songlistPresent = selectedSonglist.file != null,
                songlistPath = selectedSonglist.path,
                songlistParseResult = selectedSonglist.status,
                packlistPresent = scanned.packlistFile != null,
                affFiles = scanned.affPaths(projectRoot),
                ignoredAffFiles = ignoredAff,
                warnings = warnings,
                canConvert = false,
                failureReason = if (scanned.candidateSongIds.size > 1) {
                    "Detected pack structure with multiple songs; single-song conversion does not support pack conversion yet."
                } else {
                    "Input contains no standard AFF; manual AFF mapping is required."
                },
            )
        }

        val packlist = scanned.packlistFile?.let { PacklistParser().parse(it) }
        val resolver = MetadataResolver()
        val match = resolver.matchSong(target.songDir, target.songId, songlist)
        val metadata = resolver.resolve(
            songDir = target.songDir,
            affFiles = target.affFiles,
            requestedSongId = target.songId,
            songlist = songlist,
            packlist = packlist,
            manualMetadata = null,
            warnings = warnings,
        )
        when (metadata) {
            is MetadataResolution.Need -> {
                val scannedResources = resolveResourcesForScan(projectRoot, target, warnings)
                ScanReport(
                    kind = scanned.kind.name,
                    songRoot = target.songDir.absolutePath,
                    songId = target.songId ?: songlist?.songs?.firstOrNull()?.id,
                    songlistPresent = selectedSonglist.file != null,
                    songlistPath = selectedSonglist.path,
                    songlistParseResult = selectedSonglist.status,
                    songRootName = target.songDir.name,
                    songlistId = match.songlistId,
                    matchMode = match.mode.name,
                    parsedTitle = match.song.parsedTitle(),
                    parsedArtist = match.song.parsedArtist(),
                    parsedBpm = match.song?.bpmText,
                    parsedBpmBase = match.song?.bpmBase?.toString(),
                    parsedDifficulties = match.song.parsedDifficulties(),
                    packlistPresent = scanned.packlistFile != null,
                    needMetadata = true,
                    affFiles = scanned.affPaths(projectRoot),
                    adoptedAffFiles = target.affFiles.values.map { it.relativeToOrAbsolute(projectRoot) }.sorted(),
                    ignoredAffFiles = ignoredAff,
                    audioFiles = scannedResources.audioFiles,
                    jacketFiles = scannedResources.jacketFiles,
                    backgroundFiles = scannedResources.backgroundFiles,
                    warnings = warnings,
                    canConvert = false,
                    failureReason = "NeedMetadata: ${metadata.missingMetadata.requiredFields.joinToString()}",
                )
            }
            is MetadataResolution.Resolved -> {
                val adopted = target.affFiles.values.map { it.relativeToOrAbsolute(projectRoot) }.sorted()
                val resolved = try {
                    ResourceResolver().resolve(
                        workspaceDir = projectRoot,
                        songDir = target.songDir,
                        affFiles = target.affFiles,
                        metadata = metadata.metadata,
                        warnings = warnings,
                    )
                } catch (error: MissingRequiredResourceException) {
                    return ScanReport(
                        kind = scanned.kind.name,
                        songRoot = target.songDir.absolutePath,
                        songId = metadata.metadata.songId,
                        songlistPresent = selectedSonglist.file != null,
                        songlistPath = selectedSonglist.path,
                        songlistParseResult = selectedSonglist.status,
                        songRootName = target.songDir.name,
                        songlistId = match.songlistId,
                        matchMode = match.mode.name,
                        parsedTitle = match.song.parsedTitle(),
                        parsedArtist = match.song.parsedArtist(),
                        parsedBpm = match.song?.bpmText,
                        parsedBpmBase = match.song?.bpmBase?.toString(),
                        parsedDifficulties = match.song.parsedDifficulties(),
                        packlistPresent = scanned.packlistFile != null,
                        needMetadata = false,
                        affFiles = scanned.affPaths(projectRoot),
                        adoptedAffFiles = adopted,
                        ignoredAffFiles = ignoredAff,
                        warnings = warnings,
                        canConvert = false,
                        failureReason = error.message ?: "Required resource missing.",
                    )
                }
                val audio = resolved.difficulties.map { it.audioFile.relativeToOrAbsolute(projectRoot) }.distinct()
                val jacket = resolved.difficulties.mapNotNull { it.jacketFile?.relativeToOrAbsolute(projectRoot) }.distinct()
                val background = resolved.difficulties.mapNotNull { it.backgroundFile?.relativeToOrAbsolute(projectRoot) }.distinct()
                if (background.isEmpty() && warnings.none { it.contains("Background not recognized") }) {
                    warnings += "Background not recognized."
                }
                ScanReport(
                    kind = scanned.kind.name,
                    songRoot = target.songDir.absolutePath,
                    songId = metadata.metadata.songId,
                    songlistPresent = selectedSonglist.file != null,
                    songlistPath = selectedSonglist.path,
                    songlistParseResult = selectedSonglist.status,
                    songRootName = target.songDir.name,
                    songlistId = match.songlistId,
                    matchMode = match.mode.name,
                    parsedTitle = match.song.parsedTitle(),
                    parsedArtist = match.song.parsedArtist(),
                    parsedBpm = match.song?.bpmText,
                    parsedBpmBase = match.song?.bpmBase?.toString(),
                    parsedDifficulties = match.song.parsedDifficulties(),
                    packlistPresent = scanned.packlistFile != null,
                    needMetadata = false,
                    affFiles = scanned.affPaths(projectRoot),
                    adoptedAffFiles = adopted,
                    ignoredAffFiles = ignoredAff,
                    audioFiles = audio,
                    jacketFiles = jacket,
                    backgroundFiles = background,
                    warnings = warnings,
                    canConvert = true,
                )
            }
        }
    } catch (error: MissingRequiredResourceException) {
        ScanReport(
            warnings = warnings,
            canConvert = false,
            failureReason = error.message ?: "Required resource missing.",
        )
    } catch (error: Exception) {
        ScanReport(
            warnings = warnings,
            canConvert = false,
            failureReason = "Scan failed: ${error.message}",
        )
    }
}

private fun resolveResourcesForScan(
    projectRoot: File,
    target: TargetSong,
    warnings: MutableList<String>,
): ScanResources {
    val metadata = ResolvedSongMetadata(
        songId = target.songId ?: target.songDir.name,
        title = "",
        artist = "",
        bpmText = "",
        bpmBase = 0f,
        set = "",
        side = 0,
        bg = null,
        bgInverse = null,
        audioPreview = 0,
        audioPreviewEnd = 0,
        additionalFiles = emptyList(),
        pack = null,
        searchTags = "",
        difficulties = target.affFiles.keys.sorted().map { ratingClass ->
            ResolvedDifficultyMetadata(
                ratingClass = ratingClass,
                chartDesigner = "",
                jacketDesigner = "",
                rating = null,
                ratingPlus = false,
                jacketOverride = false,
                audioOverride = false,
                bg = null,
                bgInverse = null,
                title = null,
                artist = null,
                bpmText = null,
                bpmBase = null,
            )
        },
    )
    return try {
        val resolved = ResourceResolver().resolve(
            workspaceDir = projectRoot,
            songDir = target.songDir,
            affFiles = target.affFiles,
            metadata = metadata,
            warnings = warnings,
        )
        ScanResources(
            audioFiles = resolved.difficulties.map { it.audioFile.relativeToOrAbsolute(projectRoot) }.distinct(),
            jacketFiles = resolved.difficulties.mapNotNull { it.jacketFile?.relativeToOrAbsolute(projectRoot) }.distinct(),
            backgroundFiles = resolved.difficulties.mapNotNull { it.backgroundFile?.relativeToOrAbsolute(projectRoot) }.distinct(),
        )
    } catch (error: MissingRequiredResourceException) {
        warnings += error.message ?: "Required resource missing."
        ScanResources()
    }
}

private fun selectSonglistFile(songDir: File, projectRoot: File, scanned: ScannedInput): File? =
    InputScanner.findSonglistFile(songDir)
        ?: InputScanner.findSonglistFile(projectRoot)
        ?: scanned.songlistFile

private fun parseSonglistForScan(file: File?, projectRoot: File): SonglistScan {
    if (file == null) return SonglistScan()
    return try {
        val songlist = SonglistParser().parse(file)
        SonglistScan(
            file = file,
            path = file.relativeToOrAbsolute(projectRoot),
            songlist = songlist,
            status = "parsed:${songlist.songs.size}",
        )
    } catch (error: Exception) {
        SonglistScan(
            file = file,
            path = file.relativeToOrAbsolute(projectRoot),
            songlist = null,
            status = "failed",
            error = "Songlist/slst parse failed for ${file.relativeToOrAbsolute(projectRoot)}: ${error.message}",
        )
    }
}

private fun resolveTarget(scanned: ScannedInput, songlist: Songlist?, requestedSongId: String?): TargetSong? {
    val requested = requestedSongId?.takeIf { it.isNotBlank() }
    val songlistSongId = songlist?.songs.orEmpty()
        .firstOrNull { it.deleted != true && (requested == null || it.id == requested || it.id?.equals(requested, ignoreCase = true) == true) }
        ?.id
        ?.takeIf { scanned.findSongDirectory(it) != null }
    return when (scanned.kind) {
        InputKind.SingleSong -> TargetSong(
            requested ?: songlist?.songs?.firstOrNull { it.deleted != true }?.id,
            scanned.workspaceDir,
            scanned.rootAffFiles,
        )
        InputKind.PackFolder -> {
            val songId = requested ?: songlistSongId ?: scanned.candidateSongIds.singleOrNull() ?: return null
            val matched = scanned.findSongDirectory(songId) ?: return null
            TargetSong(matched.first, matched.second, InputScanner.findAffFiles(matched.second))
        }
        InputKind.Unknown -> TargetSong(
            requested ?: songlist?.songs?.firstOrNull { it.deleted != true }?.id,
            scanned.workspaceDir,
            scanned.rootAffFiles,
        ).takeIf { it.affFiles.isNotEmpty() }
    }
}

private fun extractZip(zipFile: File, targetDir: File) {
    val charsets = listOf(StandardCharsets.UTF_8, Charset.forName("GBK"))
    var lastError: Exception? = null
    for (charset in charsets) {
        targetDir.deleteRecursively()
        targetDir.mkdirs()
        try {
            extractZipWithCharset(zipFile, targetDir, charset)
            return
        } catch (error: Exception) {
            lastError = error
        }
    }
    throw lastError ?: IllegalStateException("Unable to extract zip.")
}

private fun extractZipWithCharset(zipFile: File, targetDir: File, charset: Charset) {
    val canonicalRoot = targetDir.canonicalFile
    ZipInputStream(zipFile.inputStream().buffered(), charset).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val output = targetDir.resolve(entry.name)
            val canonicalOutput = output.canonicalFile
            val insideRoot = canonicalOutput == canonicalRoot ||
                canonicalOutput.path.startsWith(canonicalRoot.path + File.separator)
            require(insideRoot) { "Unsafe zip entry path: ${entry.name}" }
            if (entry.isDirectory) {
                canonicalOutput.mkdirs()
            } else {
                canonicalOutput.parentFile?.mkdirs()
                canonicalOutput.outputStream().use { zip.copyTo(it) }
            }
            zip.closeEntry()
        }
    }
}

private fun normalizeArchiveRoot(archiveDir: File): File {
    var current = archiveDir
    while (true) {
        val hasRootMarkers = current.resolve("songlist").isFile ||
            current.resolve("songlist.json").isFile ||
            current.resolve("songlist.txt").isFile ||
            current.resolve("slst").isFile ||
            current.resolve("slst.json").isFile ||
            current.resolve("slst.txt").isFile ||
            current.resolve("packlist").isFile ||
            current.resolve("packlist.json").isFile ||
            current.resolve("packlist.txt").isFile ||
            InputScanner.findAffFiles(current).isNotEmpty()
        if (hasRootMarkers) return current
        current.resolve("assets").resolve("songs")
            .takeIf { it.isDirectory && hasChildSongDirs(it) }
            ?.let { return it }
        val childDirs = current.listFiles()?.filter { it.isDirectory && it.name != "__MACOSX" }.orEmpty()
        val meaningfulFiles = current.listFiles()
            ?.filter { it.isFile && it.name != ".DS_Store" }
            .orEmpty()
        if (childDirs.size != 1 || meaningfulFiles.isNotEmpty()) return current
        current = childDirs.single()
    }
}

private fun hasChildSongDirs(dir: File): Boolean =
    dir.listFiles()?.any { it.isDirectory && InputScanner.findAffFiles(it).isNotEmpty() } == true

private fun printSuccess(result: ConvertResult.Success, out: PrintStream) {
    out.println("Success")
    out.println("outputFile: ${result.outputFile.absolutePath}")
    out.println("songId: ${result.songId}")
    printList("warnings", result.warnings, out)
    printList("logs", result.logs, out)
}

private fun printNeedMetadata(result: ConvertResult.NeedMetadata, out: PrintStream) {
    val scanned = result.scannedInput
    out.println("NeedMetadata")
    out.println("reason: ${result.missingMetadata.reason}")
    printList("requiredFields", result.missingMetadata.requiredFields, out)
    printList("optionalFields", result.missingMetadata.optionalFields, out)
    printList("candidateSongIds", result.missingMetadata.candidateSongIds, out)
    printList("affFiles", scanned.affPaths(scanned.workspaceDir), out)
    printList("resources", scanned.resourcePaths(), out)
}

private fun printUnsupportedPack(result: ConvertResult.UnsupportedPackStructure, err: PrintStream) {
    err.println("UnsupportedPackStructure")
    err.println("message: ${result.message}")
    printList("candidateSongIds", result.candidateSongIds, err)
}

private fun printFailed(result: ConvertResult.Failed, fallbackWorkspaceDir: File, err: PrintStream) {
    err.println("Failed")
    err.println("message: ${result.message}")
    result.cause?.printStackTrace(err)
    printList("warnings", result.warnings, err)
    printList("logs", result.logs, err)
    err.println("workspaceDir: ${result.workspaceDir?.absolutePath ?: fallbackWorkspaceDir.absolutePath}")
}

private fun printScanReport(report: ScanReport, out: PrintStream) {
    out.println("zipFile: ${report.sourceName}")
    out.println("extract: ${report.extractStatus}")
    out.println("inputRoot: ${report.inputRoot ?: "(none)"}")
    out.println("projectRoot: ${report.projectRoot ?: "(none)"}")
    out.println("kind: ${report.kind ?: "(unknown)"}")
    out.println("songRoot: ${report.songRoot ?: "(none)"}")
    out.println("songId: ${report.songId ?: "(unknown)"}")
    out.println("songlistPath: ${report.songlistPath ?: "(none)"}")
    out.println("songlistParse: ${report.songlistParseResult}")
    out.println("songRootName: ${report.songRootName ?: "(none)"}")
    out.println("songlistId: ${report.songlistId ?: "(none)"}")
    out.println("matchMode: ${report.matchMode}")
    out.println("parsedTitle: ${report.parsedTitle ?: "(none)"}")
    out.println("parsedArtist: ${report.parsedArtist ?: "(none)"}")
    out.println("parsedBpm: ${report.parsedBpm ?: "(none)"}")
    out.println("parsedBpmBase: ${report.parsedBpmBase ?: "(none)"}")
    printList("parsedDifficulties", report.parsedDifficulties, out)
    printList("affFiles", report.affFiles, out)
    printList("adoptedAffFiles", report.adoptedAffFiles, out)
    printList("ignoredAffFiles", report.ignoredAffFiles, out)
    printList("audio", report.audioFiles, out)
    printList("jacket", report.jacketFiles, out)
    printList("background", report.backgroundFiles, out)
    printList("warnings", report.warnings, out)
    out.println("canConvert: ${report.canConvert}")
    out.println("failureReason: ${report.failureReason ?: "(none)"}")
}

private fun printScanReportsTable(reports: List<ScanReport>, out: PrintStream) {
    val headers = listOf(
        "zipFile",
        "extract",
        "songId",
        "projectRoot",
        "songRoot",
        "adoptedAff",
        "ignoredAff",
        "audio",
        "jacket",
        "background",
        "songlist",
        "songlistPath",
        "songlistParse",
        "songRootName",
        "songlistId",
        "matchMode",
        "parsedTitle",
        "parsedArtist",
        "parsedBpm",
        "parsedBpmBase",
        "parsedDifficulties",
        "packlist",
        "needMetadata",
        "canConvert",
        "warnings",
        "failureReason",
    )
    out.println(headers.joinToString(prefix = "| ", separator = " | ", postfix = " |"))
    out.println(headers.joinToString(prefix = "| ", separator = " | ", postfix = " |") { "---" })
    reports.forEach { report ->
        val row = listOf(
            report.sourceName,
            report.extractStatus,
            report.songId ?: "(unknown)",
            report.projectRoot ?: "(none)",
            report.songRoot ?: "(none)",
            report.adoptedAffFiles.tableList(),
            report.ignoredAffFiles.tableList(),
            report.audioFiles.tableList(),
            report.jacketFiles.tableList(),
            report.backgroundFiles.tableList(),
            report.songlistPresent.toString(),
            report.songlistPath ?: "(none)",
            report.songlistParseResult,
            report.songRootName ?: "(none)",
            report.songlistId ?: "(none)",
            report.matchMode,
            report.parsedTitle ?: "(none)",
            report.parsedArtist ?: "(none)",
            report.parsedBpm ?: "(none)",
            report.parsedBpmBase ?: "(none)",
            report.parsedDifficulties.tableList(),
            report.packlistPresent.toString(),
            report.needMetadata.toString(),
            report.canConvert.toString(),
            report.warnings.tableList(),
            report.failureReason ?: "(none)",
        )
        out.println(row.joinToString(prefix = "| ", separator = " | ", postfix = " |") { it.tableCell() })
    }
}

private fun printList(name: String, values: List<String>, stream: PrintStream) {
    stream.println("$name:")
    if (values.isEmpty()) {
        stream.println("  (none)")
    } else {
        values.forEach { stream.println("  - $it") }
    }
}

private fun List<String>.tableList(): String =
    if (isEmpty()) "(none)" else joinToString("<br>")

private fun String.tableCell(): String =
    replace("|", "\\|")
        .replace("\r", " ")
        .replace("\n", "<br>")

private fun ScannedInput.affPaths(base: File): List<String> {
    val root = rootAffFiles.values.map { it.relativeToOrAbsolute(base) }
    val child = songDirectories.values
        .flatMap { songDir -> InputScanner.findAffFiles(songDir).values }
        .map { it.relativeToOrAbsolute(base) }
    return (root + child).distinct().sorted()
}

private fun ScannedInput.resourcePaths(): List<String> {
    val resourceExtensions = setOf("ogg", "wav", "png", "jpg", "jpeg", "json", "yml", "yaml")
    return workspaceDir.walkTopDown()
        .filter { it.isFile }
        .filter { file ->
            val name = file.name.lowercase()
            val extension = file.extension.lowercase()
            name.endsWith(".sc.json") || extension in resourceExtensions
        }
        .map { it.relativeToOrAbsolute(workspaceDir) }
        .toList()
        .sorted()
}

private fun ScannedInput.findSongDirectory(songId: String): Pair<String, File>? {
    songDirectories[songId]?.let { return songId to it }
    return songDirectories.entries.firstOrNull { it.key.equals(songId, ignoreCase = true) }
        ?.let { it.key to it.value }
}

private fun SonglistSong?.parsedTitle(): String? =
    this?.titleLocalized?.let { titles ->
        titles["en"] ?: titles["ja"] ?: titles["zh-Hans"] ?: titles["zh-Hant"] ?: titles.values.firstOrNull()
    } ?: this?.title

private fun SonglistSong?.parsedArtist(): String? =
    this?.artist ?: this?.composer

private fun SonglistSong?.parsedDifficulties(): List<String> =
    this?.difficulties.orEmpty().map { difficulty ->
        val ratingClass = difficulty.ratingClass?.toString() ?: "?"
        val rating = difficulty.rating?.toString() ?: "?"
        "$ratingClass:$rating${if (difficulty.ratingPlus == true) "+" else ""}"
    }

private fun File.relativeToOrAbsolute(base: File): String =
    runCatching { relativeTo(base).path }.getOrElse { absolutePath }

private fun String.sanitizeFileName(): String =
    replace(Regex("""[\\/:*?"<>|]"""), "_")

private fun String.safeId(fallback: String): String =
    replace(Regex("""[^\w.-]+"""), "_")
        .trim('_', '.', '-')
        .ifBlank { fallback }

private data class TargetSong(
    val songId: String?,
    val songDir: File,
    val affFiles: Map<Int, File>,
)

private data class ScanReport(
    val sourceName: String = "",
    val extractStatus: String = "",
    val inputRoot: String? = null,
    val projectRoot: String? = null,
    val kind: String? = null,
    val songRoot: String? = null,
    val songId: String? = null,
    val songlistPresent: Boolean = false,
    val songlistPath: String? = null,
    val songlistParseResult: String = "none",
    val songRootName: String? = null,
    val songlistId: String? = null,
    val matchMode: String = "none",
    val parsedTitle: String? = null,
    val parsedArtist: String? = null,
    val parsedBpm: String? = null,
    val parsedBpmBase: String? = null,
    val parsedDifficulties: List<String> = emptyList(),
    val packlistPresent: Boolean = false,
    val needMetadata: Boolean = false,
    val affFiles: List<String> = emptyList(),
    val adoptedAffFiles: List<String> = emptyList(),
    val ignoredAffFiles: List<String> = emptyList(),
    val audioFiles: List<String> = emptyList(),
    val jacketFiles: List<String> = emptyList(),
    val backgroundFiles: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val canConvert: Boolean = false,
    val failureReason: String? = null,
)

private data class SonglistScan(
    val file: File? = null,
    val path: String? = null,
    val songlist: Songlist? = null,
    val status: String = "none",
    val error: String? = null,
)

private data class ScanResources(
    val audioFiles: List<String> = emptyList(),
    val jacketFiles: List<String> = emptyList(),
    val backgroundFiles: List<String> = emptyList(),
)

private fun usageText(): String = """
    Usage:
      java -jar converter-cli.jar --input "D:\sample_song" --output "D:\output"
      .\gradlew :converter-cli:run --args='--input "D:\sample_song" --output "D:\output"'
      .\gradlew :converter-cli:run --args='--scan-only --input "D:\sample.zip"'
      .\gradlew :converter-cli:run --args='--scan-pack --input "D:\arcaea_pack_or_zips"'
      .\gradlew :converter-cli:run --args='--scan-arcpkg-pack --input "D:\arccreate_pack_or_dir"'
      .\gradlew :converter-cli:run --args='--scan-character --input "D:\character_arcpkg_or_dir"'
      .\gradlew :converter-cli:run --args='--convert-pack --input "D:\arcaea_pack.zip" --output "D:\out" --validate-output'
      .\gradlew :converter-cli:run --args='--merge-arcpkg --input "D:\arcpkgs" --output "D:\out" --publisher-id "etoilebridge"'
      .\gradlew :converter-cli:run --args='--edit-pack-arcpkg --base "D:\base_pack.arcpkg" --add "D:\more_arcpkgs" --output "D:\out" --validate-output'

    Options:
      --input <path>                    Input song folder, pack folder, zip, or directory of zips.
      --output <path>                   Output directory for songId.arcpkg. Not required with --scan-only.
      --song-id <id>                    Target songId when a pack folder contains multiple songs.
      --scan-only                       Scan input and print detected roots/resources without packing.
      --scan-pack                       Scan Arcaea official-format pack zips/folders.
      --scan-arcpkg-pack                Scan ArcCreate .arcpkg packs or directories.
      --scan-character                  Scan ArcCreate character .arcpkg files or directories.
      --convert-pack                    Convert an Arcaea official-format pack zip/folder into a bundle arcpkg.
      --merge-arcpkg                    Merge multiple .arcpkg files into one bundle.
      --edit-pack-arcpkg                Rebuild an existing pack arcpkg and append level arcpkg files from --add.
      --base <path>                     Base existing pack arcpkg for --edit-pack-arcpkg.
      --add <path>                      File or folder of arcpkg files to append with --edit-pack-arcpkg.
      --publisher-id <id>               Publisher prefix for merge defaults. Default: etoilebridge.
      --validate-output                 Validate generated bundle arcpkg and print a report.
      --keep-temp                       Keep temporary processed files on failure.
      --disable-delete-designant        Disable deleting lines containing designant.
      --disable-fix-zero-arctap         Disable 0ms arc+arctap fix.
      --disable-fix-reversed-arc        Disable reversed arc time fix.
      --disable-arcresolution           Disable timinggroup arcresolution expansion.
      --help                            Print this help.
""".trimIndent()
