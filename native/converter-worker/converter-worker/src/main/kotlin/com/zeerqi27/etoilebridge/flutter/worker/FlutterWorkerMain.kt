package com.zeerqi27.etoilebridge.flutter.worker

import com.zeerqi27.etoilebridge.core.AppearanceOptions
import com.zeerqi27.etoilebridge.core.ArcCreateAccent
import com.zeerqi27.etoilebridge.core.ArcCreateNote
import com.zeerqi27.etoilebridge.core.ArcCreateParticle
import com.zeerqi27.etoilebridge.core.ArcCreateSide
import com.zeerqi27.etoilebridge.core.ArcCreateSingleLine
import com.zeerqi27.etoilebridge.core.ArcCreateTrack
import com.zeerqi27.etoilebridge.core.ConvertInput
import com.zeerqi27.etoilebridge.core.ConvertOptions
import com.zeerqi27.etoilebridge.core.ConvertResult
import com.zeerqi27.etoilebridge.core.EtoileBridgeConverter
import com.zeerqi27.etoilebridge.core.ImageDimensionReader
import com.zeerqi27.etoilebridge.core.InputKind
import com.zeerqi27.etoilebridge.core.InputScanner
import com.zeerqi27.etoilebridge.core.ManualChartOverrides
import com.zeerqi27.etoilebridge.core.ManualDifficultyMetadata
import com.zeerqi27.etoilebridge.core.ManualMetadata
import com.zeerqi27.etoilebridge.core.ManualResourceOverrides
import com.zeerqi27.etoilebridge.core.MetadataResolution
import com.zeerqi27.etoilebridge.core.MetadataResolver
import com.zeerqi27.etoilebridge.core.PackageOptions
import com.zeerqi27.etoilebridge.core.PacklistParser
import com.zeerqi27.etoilebridge.core.ResourceResolver
import com.zeerqi27.etoilebridge.core.ScannedInput
import com.zeerqi27.etoilebridge.core.Songlist
import com.zeerqi27.etoilebridge.core.SonglistParser
import com.zeerqi27.etoilebridge.core.SonglistSong
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.io.BufferedInputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import kotlin.system.exitProcess

private val JsonIo = Json {
    prettyPrint = false
    explicitNulls = false
    ignoreUnknownKeys = true
}

fun main(args: Array<String>) {
    val action = args.firstOrNull()
    val options = parseArgs(args.drop(1))
    val envelope = try {
        when (action) {
            "scan-single" -> scanSingle(options)
            "convert-single" -> convertSingle(options)
            "smoke-test" -> WorkerEnvelope(ok = true, data = "flutter-worker smoke ok")
            else -> WorkerEnvelope(ok = false, error = "Unsupported flutter worker action: $action")
        }
    } catch (error: Throwable) {
        WorkerEnvelope(
            ok = false,
            error = userFacingError(error),
            logs = listOf(
                "raw-error: ${error.javaClass.name}: ${error.message ?: error}",
                error.stackTraceToString(),
            ),
        )
    }
    println(JsonIo.encodeToString(envelope))
    if (!envelope.ok) exitProcess(1)
}

private fun scanSingle(options: Map<String, String>): WorkerEnvelope {
    val source = options.requiredFile("source")
    val session = options.requiredFile("session")
    val inputRoot = prepareInputWorkspace(source, session.resolve("input"))
    val scan = InputScanner().scan(inputRoot)
    val warnings = scan.ignoredAffFiles
        .map { "Ignored non-standard AFF file: ${it.relativeToOrSelf(inputRoot)}" }
        .toMutableList()
    val rootSonglistFile = scan.songlistFile
    val rootSonglist = rootSonglistFile.parseSonglistOrNull(warnings)
    val target = resolveSingleScanTarget(scan, rootSonglist)
        ?: return scanEnvelope(
            SingleScanJson(
                sourcePath = source.absolutePath,
                sourceKind = "official-song",
                inputType = source.inputTypeLabel(),
                workspacePath = inputRoot.absolutePath,
                songlist = scan.songlistFile.toResourceJson("Detected songlist"),
                packlist = scan.packlistFile.toResourceJson("Detected packlist"),
                warnings = warnings,
                logs = listOf("Scanned ${source.absolutePath} into ${inputRoot.absolutePath}"),
            ),
            warnings,
        )

    val songlistFile = InputScanner.findSonglistFile(target.songDir)
        ?: InputScanner.findSonglistFile(inputRoot)
        ?: scan.songlistFile
    val packlistFile = InputScanner.findPacklistFile(target.songDir)
        ?: scan.packlistFile
    val songlist = when (songlistFile?.canonicalFile) {
        rootSonglistFile?.canonicalFile -> rootSonglist
        null -> null
        else -> songlistFile.parseSonglistOrNull(warnings)
    }
    val packlist = packlistFile?.let { file ->
        runCatching { PacklistParser().parse(file) }
            .onFailure { warnings += "Packlist parse failed for ${file.name}: ${it.message}" }
            .getOrNull()
    }
    val metadataResolution = MetadataResolver().resolve(
        songDir = target.songDir,
        affFiles = target.affFiles,
        requestedSongId = target.songId,
        songlist = songlist,
        packlist = packlist,
        manualMetadata = null,
        warnings = warnings,
    )
    val metadata = (metadataResolution as? MetadataResolution.Resolved)?.metadata
    val fallbackSong = target.findSongIn(songlist)
    val resolvedSong = metadata?.let {
        runCatching {
            ResourceResolver().resolve(
                workspaceDir = inputRoot,
                songDir = target.songDir,
                affFiles = target.affFiles,
                metadata = it,
                warnings = warnings,
                overrides = null,
            )
        }.onFailure { warnings += "Resource scan warning: ${it.message ?: it.javaClass.simpleName}" }.getOrNull()
    }
    val resolvedByRating = resolvedSong?.difficulties
        ?.associateBy { it.metadata.ratingClass }
        .orEmpty()
    val firstDifficulty = resolvedSong?.difficulties?.firstOrNull()
    val difficultySummary = metadata?.difficulties?.joinToString { diff ->
        val constant = diff.chartConstant?.let { "%.1f".format(Locale.ROOT, it) }.orEmpty()
        "${diff.ratingClass}:${diff.difficulty ?: "?"}${constant.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}"
    }
    val charts = metadata?.let { songMetadata ->
        songMetadata.difficulties.map { diff ->
            val resolved = resolvedByRating[diff.ratingClass]
            val bgReference = diff.bg ?: diff.bgInverse ?: songMetadata.bg ?: songMetadata.bgInverse
            val bgMissing = bgReference != null && !resolved?.backgroundFile.matchesResourceReference(bgReference)
            ChartJson(
                ratingClass = diff.ratingClass,
                difficulty = diff.difficulty,
                chartConstant = diff.chartConstant,
                rating = diff.rating,
                ratingPlus = diff.ratingPlus,
                charter = diff.chartDesigner,
                illustrator = diff.jacketDesigner,
                affPath = target.affFiles[diff.ratingClass]?.absolutePath,
                affName = target.affFiles[diff.ratingClass]?.name,
                audio = resolved?.audioFile.toResourceJson(
                    if (diff.audioOverride) "Chart audio override" else "Detected audio",
                ),
                jacket = resolved?.jacketFile.toResourceJson(
                    if (diff.jacketOverride) "Chart jacket override" else "Detected jacket",
                ),
                background = resolved?.backgroundFile.toResourceJson(
                    if (!diff.bg.isNullOrBlank() || !diff.bgInverse.isNullOrBlank()) {
                        "Chart background"
                    } else {
                        "Detected background"
                    },
                ),
                audioOverride = diff.audioOverride,
                jacketOverride = diff.jacketOverride,
                bg = bgReference,
                bgOverride = !diff.bg.isNullOrBlank() || !diff.bgInverse.isNullOrBlank(),
                missingBackgroundReference = bgMissing,
                resourceWarnings = if (bgMissing) {
                    listOf("Background reference not found: $bgReference")
                } else {
                    emptyList()
                },
            )
        }
    }.orEmpty().ifEmpty {
        target.affFiles.toSortedMap().map { (ratingClass, file) ->
            ChartJson(ratingClass = ratingClass, affPath = file.absolutePath, affName = file.name)
        }
    }

    return scanEnvelope(
        SingleScanJson(
            sourcePath = source.absolutePath,
            sourceKind = "official-song",
            inputType = source.inputTypeLabel(),
            workspacePath = inputRoot.absolutePath,
            songId = metadata?.songId ?: target.songId ?: fallbackSong?.id ?: target.songDir.name,
            title = metadata?.title ?: fallbackSong?.displayTitle(),
            artist = metadata?.artist ?: fallbackSong?.artist ?: fallbackSong?.composer,
            bpmText = metadata?.bpmText ?: fallbackSong?.bpmText,
            bpmBase = metadata?.bpmBase ?: fallbackSong?.bpmBase,
            difficulty = difficultySummary,
            charts = charts,
            audio = firstDifficulty?.audioFile.toResourceJson("Detected audio"),
            jacket = firstDifficulty?.jacketFile.toResourceJson("Detected jacket"),
            background = firstDifficulty?.backgroundFile.toResourceJson("Detected background"),
            songlist = songlistFile.toResourceJson("Detected songlist"),
            packlist = packlistFile.toResourceJson("Detected packlist"),
            affFiles = target.affFiles.toSortedMap().map { (ratingClass, file) ->
                AffJson(
                    ratingClass = ratingClass,
                    path = file.absolutePath,
                    name = file.name,
                    sizeBytes = file.length(),
                    adopted = true,
                )
            },
            warnings = warnings,
            logs = listOf(
                "Scanned ${source.absolutePath}",
                "Workspace: ${inputRoot.absolutePath}",
            ),
        ),
        warnings,
    )
}

private fun convertSingle(options: Map<String, String>): WorkerEnvelope {
    val workspace = options.requiredFile("workspace")
    val output = options.requiredFile("output")
    val coreOutputDir = File(
        output.parentFile ?: File("."),
        ".etoilebridge-flutter-worker-${System.currentTimeMillis()}-${output.nameWithoutExtension}",
    )
    val request = options.jsonOption("request-json")?.let {
        JsonIo.decodeFromString(ConvertRequestJson.serializer(), it)
    } ?: ConvertRequestJson()
    output.parentFile?.mkdirs()
    coreOutputDir.mkdirs()

    val result = EtoileBridgeConverter.convert(
        input = ConvertInput(
            workspaceDir = workspace,
            outputFile = coreOutputDir,
            manualMetadata = ManualMetadata(
                songId = request.levelId?.takeIf { it.isNotBlank() },
                title = request.title?.takeIf { it.isNotBlank() },
                artist = request.artist?.takeIf { it.isNotBlank() },
                bpmText = request.bpmText?.takeIf { it.isNotBlank() },
                bpmBase = request.bpmBase,
                difficulties = request.charts.filter { it.adopted }.map { chart ->
                    ManualDifficultyMetadata(
                        ratingClass = chart.ratingClass,
                        chartDesigner = chart.charter?.takeIf { it.isNotBlank() },
                        jacketDesigner = chart.illustrator?.takeIf { it.isNotBlank() },
                        difficulty = chart.difficulty?.takeIf { it.isNotBlank() },
                        chartConstant = chart.chartConstant,
                        bg = chart.externalBackgroundStem?.takeIf { it.isNotBlank() },
                    )
                },
            ),
            resourceOverrides = ManualResourceOverrides(
                audioFile = request.resources.audioPath?.toExistingFileOrNull(),
                jacketFile = request.resources.jacketPath?.toExistingFileOrNull(),
                backgroundFile = request.resources.backgroundPath?.toExistingFileOrNull(),
                songlistFile = request.resources.songlistPath?.toExistingFileOrNull(),
                packlistFile = request.resources.packlistPath?.toExistingFileOrNull(),
            ),
            chartOverrides = ManualChartOverrides(
                adoptedAffByRatingClass = request.charts
                    .filter { it.adopted && !it.affPath.isNullOrBlank() }
                    .associate { it.ratingClass to File(it.affPath!!) },
                ignoredAffFiles = request.charts
                    .filter { !it.adopted && !it.affPath.isNullOrBlank() }
                    .map { File(it.affPath!!) }
                    .toSet(),
            ),
            packageOptions = PackageOptions(
                publisherId = request.publisherId.takeIf { it.isNotBlank() } ?: "etoilebridge",
                levelId = request.levelId?.takeIf { it.isNotBlank() },
            ),
            // Source: EtoileBridge Android ConverterViewModel.toCoreAppearanceOptions
            // and converter-core ConvertInput.AppearanceOptions. Unlike the old
            // Electron worker schema, this Flutter-owned worker passes all six
            // ArcCreate skin fields through to PackEngine.
            appearanceOptions = AppearanceOptions(
                side = request.appearance.side.toNullableSide(),
                note = request.appearance.note.toEnumOrDefault(ArcCreateNote.INHERIT),
                particle = request.appearance.particle.toEnumOrDefault(ArcCreateParticle.INHERIT),
                accent = request.appearance.accent.toEnumOrDefault(ArcCreateAccent.INHERIT),
                track = request.appearance.track.toEnumOrDefault(ArcCreateTrack.INHERIT),
                singleLine = request.appearance.singleLine.toEnumOrDefault(ArcCreateSingleLine.NONE),
            ),
        ),
        options = ConvertOptions(
            enableDeleteDesignantLine = request.preprocess.deleteDesignantLine,
            enableFixZeroDurationArcTap = request.preprocess.fixZeroDurationArcTap,
            enableFixReversedArcTime = request.preprocess.fixReversedArcTime,
            enableExpandArcResolution = request.preprocess.expandArcResolution,
            keepWorkspaceOnFailure = true,
            cleanWorkspaceOnSuccess = true,
        ),
    )

    return when (result) {
        is ConvertResult.Success -> {
            output.parentFile?.mkdirs()
            result.outputFile.copyTo(output, overwrite = true)
            coreOutputDir.deleteRecursively()
            WorkerEnvelope(
                ok = true,
                data = ConvertJson(output.absolutePath, result.songId, output.length()),
                warnings = result.warnings,
                logs = result.logs + "Saved ${output.absolutePath}",
            )
        }
        is ConvertResult.NeedMetadata -> WorkerEnvelope(
            ok = false,
            error = "Metadata is incomplete: ${result.missingMetadata.requiredFields.joinToString()}",
            warnings = result.missingMetadata.optionalFields,
        )
        is ConvertResult.UnsupportedPackStructure -> WorkerEnvelope(ok = false, error = result.message)
        is ConvertResult.Failed -> WorkerEnvelope(
            ok = false,
            error = result.message,
            warnings = result.warnings,
            logs = result.logs,
        )
    }
}

private fun parseArgs(args: List<String>): Map<String, String> {
    val map = linkedMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val key = args[index]
        if (!key.startsWith("--")) {
            index++
            continue
        }
        val value = args.getOrNull(index + 1)
        if (value != null && !value.startsWith("--")) {
            map[key.removePrefix("--")] = value
            index += 2
        } else {
            map[key.removePrefix("--")] = "true"
            index++
        }
    }
    return map
}

private fun scanEnvelope(scan: SingleScanJson, warnings: List<String>): WorkerEnvelope =
    WorkerEnvelope(
        ok = true,
        data = JsonIo.encodeToJsonElement(SingleScanJson.serializer(), scan),
        warnings = warnings,
        logs = scan.logs,
    )

private fun prepareInputWorkspace(source: File, workspace: File): File {
    workspace.deleteRecursively()
    workspace.mkdirs()
    return when {
        source.isDirectory -> {
            copyDirectory(source.toPath(), workspace.toPath())
            normalizeArchiveRoot(workspace)
        }
        source.extension.equals("zip", ignoreCase = true) ||
            source.extension.equals("arcpkg", ignoreCase = true) -> {
            extractZipCompat(source, workspace)
            normalizeArchiveRoot(workspace)
        }
        else -> {
            Files.copy(source.toPath(), workspace.resolve(source.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
            workspace
        }
    }
}

private fun extractZipCompat(source: File, target: File) {
    val charsets = listOf(
        Charsets.UTF_8,
        Charset.forName("GBK"),
        Charset.forName("CP936"),
        Charset.forName("CP437"),
    ).distinctBy { it.name() }
    val errors = mutableListOf<String>()
    for (charset in charsets) {
        target.deleteRecursively()
        target.mkdirs()
        val result = runCatching {
            extractZipWithCommons(source, target, charset)
        }
        if (result.isSuccess) return
        val error = result.exceptionOrNull()
        errors += "${charset.name()}: ${error?.javaClass?.simpleName ?: "Error"}: ${error?.message ?: error}"
    }
    target.deleteRecursively()
    throw ZipReadException(
        "Unable to read ZIP central/local entries with UTF-8, GBK/CP936, or CP437. $errors",
    )
}

private fun extractZipWithCommons(source: File, target: File, charset: Charset) {
    val targetCanonical = target.canonicalFile
    val encoding = charset.name()
    ZipArchiveInputStream(
        BufferedInputStream(source.inputStream()),
        encoding,
        true,
        true,
    ).use { zip ->
        while (true) {
            val entry = zip.nextZipEntry ?: break
            val rawName = entry.name ?: continue
            if (rawName.isBlank()) continue
            if (rawName.contains('\uFFFD')) {
                error("ZIP entry name contains replacement characters with ${charset.name()}: $rawName")
            }
            val safeName = rawName.replace('\\', '/')
            if (safeName.startsWith("__MACOSX/") ||
                safeName.endsWith("/.DS_Store") ||
                safeName == ".DS_Store") {
                continue
            }
            val output = target.resolve(safeName).canonicalFile
            if (!output.path.startsWith(targetCanonical.path + File.separator) && output != targetCanonical) {
                error("Blocked unsafe zip entry: $rawName")
            }
            if (entry.isDirectory) {
                output.mkdirs()
            } else {
                output.parentFile?.mkdirs()
                output.outputStream().use { out -> zip.copyTo(out) }
            }
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
            current.resolve("project.arcproj").isFile ||
            InputScanner.findAffFiles(current).isNotEmpty()
        if (hasRootMarkers) return current
        current.resolve("assets").resolve("songs")
            .takeIf { it.isDirectory && hasChildSongDirs(it) }
            ?.let { return it }
        val childDirs = current.listFiles()
            ?.filter { it.isDirectory && it.name != "__MACOSX" }
            .orEmpty()
        val meaningfulFiles = current.listFiles()
            ?.filter { it.isFile && it.name != ".DS_Store" }
            .orEmpty()
        if (childDirs.size != 1 || meaningfulFiles.isNotEmpty()) return current
        current = childDirs.single()
    }
}

private fun hasChildSongDirs(dir: File): Boolean =
    dir.listFiles()?.any { it.isDirectory && InputScanner.findAffFiles(it).isNotEmpty() } == true

private fun copyDirectory(source: Path, target: Path) {
    Files.walk(source).use { stream ->
        stream.forEach { path ->
            val relative = source.relativize(path)
            val out = target.resolve(relative).normalize()
            require(out.startsWith(target.normalize())) { "Refusing to copy outside workspace." }
            if (Files.isDirectory(path)) {
                Files.createDirectories(out)
            } else {
                out.parent?.let(Files::createDirectories)
                Files.copy(path, out, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

private data class SingleScanTarget(
    val songId: String?,
    val songDir: File,
    val affFiles: Map<Int, File>,
) {
    fun findSongIn(songlist: Songlist?): SonglistSong? {
        val songs = songlist?.songs.orEmpty().filter { it.deleted != true }
        val targetId = songId ?: songDir.name
        return songs.firstOrNull { it.id == targetId }
            ?: songs.firstOrNull { it.id?.equals(targetId, ignoreCase = true) == true }
            ?: songs.singleOrNull()
    }
}

private fun resolveSingleScanTarget(scanned: ScannedInput, rootSonglist: Songlist?): SingleScanTarget? {
    val requestedFromSonglist = rootSonglist?.songs.orEmpty()
        .filter { it.deleted != true }
        .mapNotNull { it.id }
        .firstOrNull { scanned.findSongDirectory(it) != null }
    return when (scanned.kind) {
        InputKind.SingleSong -> SingleScanTarget(requestedFromSonglist, scanned.workspaceDir, scanned.rootAffFiles)
        InputKind.PackFolder -> {
            val songId = requestedFromSonglist
                ?: scanned.candidateSongIds.singleOrNull()
                ?: scanned.candidateSongIds.firstOrNull()
            val songDir = songId?.let { scanned.findSongDirectory(it)?.second } ?: return null
            SingleScanTarget(songId, songDir, InputScanner.findAffFiles(songDir))
        }
        InputKind.Unknown -> SingleScanTarget(requestedFromSonglist, scanned.workspaceDir, scanned.rootAffFiles)
            .takeIf { it.affFiles.isNotEmpty() }
    }
}

private fun ScannedInput.findSongDirectory(songId: String): Pair<String, File>? {
    songDirectories[songId]?.let { return songId to it }
    return songDirectories.entries.firstOrNull { it.key.equals(songId, ignoreCase = true) }
        ?.let { it.key to it.value }
}

private fun File?.parseSonglistOrNull(warnings: MutableList<String>): Songlist? {
    val file = this ?: return null
    return runCatching { SonglistParser().parse(file) }
        .onFailure { warnings += "Songlist/slst parse failed for ${file.name}: ${it.message}" }
        .getOrNull()
}

private fun File?.toResourceJson(source: String): ResourceJson? {
    val file = this?.takeIf { it.isFile } ?: return null
    val dimension = ImageDimensionReader.read(file)
    return ResourceJson(
        path = file.absolutePath,
        name = file.name,
        source = source,
        sizeBytes = file.length(),
        width = dimension?.width,
        height = dimension?.height,
    )
}

private fun File?.matchesResourceReference(reference: String?): Boolean {
    val file = this?.takeIf { it.isFile } ?: return false
    val value = reference?.trim()?.replace('\\', '/')?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: return false
    val lower = value.lowercase(Locale.ROOT)
    val name = file.name.lowercase(Locale.ROOT)
    val stem = file.nameWithoutExtension.lowercase(Locale.ROOT)
    return lower == name || lower == stem
}

private fun SonglistSong.displayTitle(): String? =
    titleLocalized["en"]
        ?: titleLocalized["ja"]
        ?: titleLocalized["zh-Hans"]
        ?: titleLocalized["zh-Hant"]
        ?: titleLocalized.values.firstOrNull()
        ?: title

private fun File.relativeToOrSelf(base: File): String =
    runCatching { relativeTo(base).path }.getOrElse { absolutePath }

private fun File.inputTypeLabel(): String =
    when {
        isDirectory -> "Folder"
        extension.equals("zip", ignoreCase = true) || extension.equals("arcpkg", ignoreCase = true) -> "ZIP"
        else -> "File"
    }

private class ZipReadException(message: String) : IllegalStateException(message)

private fun userFacingError(error: Throwable): String {
    val text = generateSequence(error) { it.cause }
        .joinToString("\n") { "${it.javaClass.name}: ${it.message.orEmpty()}" }
    val zipProblem = error is ZipReadException ||
        text.contains("invalid CEN header", ignoreCase = true) ||
        text.contains("bad entry name", ignoreCase = true) ||
        text.contains("MALFORMED", ignoreCase = true) ||
        text.contains("zip", ignoreCase = true)
    return if (zipProblem) {
        "无法读取压缩包目录，可能是压缩包文件名编码不兼容或压缩包损坏。原始错误已写入日志。"
    } else {
        error.message ?: error.toString()
    }
}

private fun Map<String, String>.requiredFile(name: String): File =
    File(this[name] ?: error("Missing --$name"))

private fun Map<String, String>.jsonOption(name: String): String? {
    this["$name-file"]?.let { return File(it).readText(Charsets.UTF_8) }
    return this[name]
}

private fun String.toExistingFileOrNull(): File? =
    takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }

private fun String?.toNullableSide(): ArcCreateSide? {
    val value = this?.trim()?.uppercase(Locale.ROOT)
    if (value.isNullOrEmpty() || value == "AUTO" || value == "UNKNOWN" || value == "INHERIT") {
        return null
    }
    return runCatching { enumValueOf<ArcCreateSide>(value) }.getOrNull()
}

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T {
    val value = this?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return default
    return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
}

@Serializable
data class WorkerEnvelope(
    val ok: Boolean,
    val data: kotlinx.serialization.json.JsonElement? = null,
    val error: String? = null,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
) {
    constructor(
        ok: Boolean,
        data: String,
        error: String? = null,
        warnings: List<String> = emptyList(),
        logs: List<String> = emptyList(),
    ) : this(ok, JsonIo.encodeToJsonElement(kotlinx.serialization.serializer<String>(), data), error, warnings, logs)

    constructor(
        ok: Boolean,
        data: ConvertJson,
        error: String? = null,
        warnings: List<String> = emptyList(),
        logs: List<String> = emptyList(),
    ) : this(ok, JsonIo.encodeToJsonElement(ConvertJson.serializer(), data), error, warnings, logs)
}

@Serializable
data class SingleScanJson(
    val sourcePath: String,
    val sourceKind: String,
    val inputType: String,
    val workspacePath: String,
    val songId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val bpmText: String? = null,
    val bpmBase: Float? = null,
    val difficulty: String? = null,
    val charts: List<ChartJson> = emptyList(),
    val audio: ResourceJson? = null,
    val jacket: ResourceJson? = null,
    val background: ResourceJson? = null,
    val songlist: ResourceJson? = null,
    val packlist: ResourceJson? = null,
    val affFiles: List<AffJson> = emptyList(),
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
)

@Serializable
data class AffJson(
    val ratingClass: Int,
    val path: String,
    val name: String,
    val adopted: Boolean = true,
    val sizeBytes: Long? = null,
    val warning: String? = null,
)

@Serializable
data class ChartJson(
    val ratingClass: Int,
    val difficulty: String? = null,
    val chartConstant: Float? = null,
    val rating: Int? = null,
    val ratingPlus: Boolean? = null,
    val charter: String? = null,
    val illustrator: String? = null,
    val alias: String? = null,
    val affPath: String? = null,
    val affName: String? = null,
    val audio: ResourceJson? = null,
    val jacket: ResourceJson? = null,
    val background: ResourceJson? = null,
    val audioOverride: Boolean = false,
    val jacketOverride: Boolean = false,
    val bg: String? = null,
    val bgOverride: Boolean = false,
    val missingBackgroundReference: Boolean = false,
    val resourceWarnings: List<String> = emptyList(),
)

@Serializable
data class ResourceJson(
    val path: String? = null,
    val name: String? = null,
    val source: String? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class ConvertJson(
    val outputPath: String,
    val songId: String? = null,
    val sizeBytes: Long? = null,
)

@Serializable
data class ConvertRequestJson(
    val publisherId: String = "etoilebridge",
    val levelId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val bpmText: String? = null,
    val bpmBase: Float? = null,
    val charts: List<ChartEditJson> = emptyList(),
    val resources: ResourceOverridesJson = ResourceOverridesJson(),
    val appearance: AppearanceJson = AppearanceJson(),
    val preprocess: PreprocessJson = PreprocessJson(),
)

@Serializable
data class ChartEditJson(
    val ratingClass: Int,
    val difficulty: String? = null,
    val chartConstant: Float? = null,
    val charter: String? = null,
    val illustrator: String? = null,
    val alias: String? = null,
    val affPath: String? = null,
    val externalBackgroundPath: String? = null,
    val externalBackgroundName: String? = null,
    val externalBackgroundStem: String? = null,
    val adopted: Boolean = true,
)

@Serializable
data class ResourceOverridesJson(
    val audioPath: String? = null,
    val jacketPath: String? = null,
    val backgroundPath: String? = null,
    val songlistPath: String? = null,
    val packlistPath: String? = null,
)

@Serializable
data class AppearanceJson(
    val side: String = "AUTO",
    val note: String = "INHERIT",
    val particle: String = "INHERIT",
    val accent: String = "INHERIT",
    val track: String = "INHERIT",
    val singleLine: String = "NONE",
)

@Serializable
data class PreprocessJson(
    val deleteDesignantLine: Boolean = true,
    val fixZeroDurationArcTap: Boolean = true,
    val fixReversedArcTime: Boolean = true,
    val expandArcResolution: Boolean = true,
)
