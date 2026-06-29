package com.zeerqi27.etoile_bridge

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
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
import java.io.BufferedInputStream
import java.io.File
import java.nio.charset.Charset
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

private const val TAG = "EtoileAndroidSingle"

class AndroidSingleSongBridge(private val context: Context) {
    fun smokeTest(): String = "android converter channel ready"

    fun cacheRootPath(): String = cacheRoot().absolutePath

    fun scanSingle(args: Map<*, *>): Map<String, Any?> =
        runEnvelope("android.scanSingle") {
            val source = args.stringArg("source") ?: error("Missing source URI.")
            val requestedSession = args.stringArg("session")
            val session = sessionDir(requestedSession)
            val prepared = prepareInputWorkspace(source, session.resolve("input"))
            val scan = scanPreparedWorkspace(
                sourceRef = source,
                inputType = prepared.inputType,
                inputRoot = prepared.inputRoot,
            )
            val logs = scan.logs + listOf(
                "android.scanSingle.success",
                "Android session: ${session.absolutePath}",
            )
            EnvelopeResult(scan.data, scan.warnings, logs)
        }

    fun saveSingle(args: Map<*, *>): Map<String, Any?> =
        runEnvelope("android.saveSingle") {
            val workspace = args.stringArg("workspace")?.let(::File)
                ?: error("Missing workspace path.")
            val outputUri = args.stringArg("output")?.let(Uri::parse)
                ?: error("Missing output URI.")
            val request = args["request"] as? Map<*, *> ?: emptyMap<Any, Any?>()
            require(workspace.isDirectory) { "Workspace not found: ${workspace.absolutePath}" }

            val outputDir = workspace.parentFile?.resolve("output")
                ?: context.cacheDir.resolve("etoilebridge-flutter-output")
            outputDir.mkdirs()
            val tempOutput = outputDir.resolve(
                "single-${System.currentTimeMillis()}-${UUID.randomUUID()}.arcpkg",
            )
            val convert = convertSingle(workspace, tempOutput, request)
            if (!convert.outputFile.isFile || convert.outputFile.length() <= 0L) {
                error("converter-core did not produce a non-empty arcpkg.")
            }
            val save = writeFileToUri(convert.outputFile, outputUri)
            EnvelopeResult(
                data = mapOf(
                    "outputPath" to outputUri.toString(),
                    "displayName" to save.displayName,
                    "songId" to convert.songId,
                    "sizeBytes" to save.expectedBytes,
                    "queriedSizeBytes" to save.queriedBytes,
                ),
                warnings = convert.warnings,
                logs = convert.logs + listOf(
                    "android.saveSingle.success",
                    "Saved via SAF: ${save.displayName ?: outputUri}",
                    "Verified saved bytes: ${save.expectedBytes}",
                ),
            )
        }

    fun cleanOldSessions() {
        val root = cacheRoot()
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        root.listFiles()
            ?.filter { it.isDirectory && it.lastModified() < cutoff }
            .orEmpty()
            .forEach { it.deleteRecursively() }
    }

    private fun scanPreparedWorkspace(
        sourceRef: String,
        inputType: String,
        inputRoot: File,
    ): ScanPayload {
        val scan = InputScanner().scan(inputRoot)
        val warnings = scan.ignoredAffFiles
            .map { "Ignored non-standard AFF file: ${it.relativeToOrSelf(inputRoot)}" }
            .toMutableList()
        val rootSonglistFile = scan.songlistFile
        val rootSonglist = rootSonglistFile.parseSonglistOrNull(warnings)
        val target = resolveSingleScanTarget(scan, rootSonglist)
            ?: return ScanPayload(
                data = mapOf(
                    "sourcePath" to sourceRef,
                    "sourceKind" to "official-song",
                    "inputType" to inputType,
                    "workspacePath" to inputRoot.absolutePath,
                    "songlist" to scan.songlistFile.toResourceMap("Detected songlist"),
                    "packlist" to scan.packlistFile.toResourceMap("Detected packlist"),
                    "warnings" to warnings,
                    "logs" to listOf("Scanned $sourceRef into ${inputRoot.absolutePath}"),
                ),
                warnings = warnings,
                logs = listOf("Scanned $sourceRef into ${inputRoot.absolutePath}"),
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
            }.onFailure { error ->
                warnings += "Resource scan warning: ${error.message ?: error.javaClass.simpleName}"
            }.getOrNull()
        }
        val resolvedByRating = resolvedSong?.difficulties
            ?.associateBy { it.metadata.ratingClass }
            .orEmpty()
        val firstDifficulty = resolvedSong?.difficulties?.firstOrNull()
        val charts = metadata?.let { songMetadata ->
            songMetadata.difficulties.map { diff ->
                val resolved = resolvedByRating[diff.ratingClass]
                val bgReference = diff.bg ?: diff.bgInverse ?: songMetadata.bg ?: songMetadata.bgInverse
                val bgMissing = bgReference != null && !resolved?.backgroundFile.matchesResourceReference(bgReference)
                mapOf(
                    "ratingClass" to diff.ratingClass,
                    "difficulty" to diff.difficulty,
                    "chartConstant" to diff.chartConstant,
                    "rating" to diff.rating,
                    "ratingPlus" to diff.ratingPlus,
                    "charter" to diff.chartDesigner,
                    "illustrator" to diff.jacketDesigner,
                    "affPath" to target.affFiles[diff.ratingClass]?.absolutePath,
                    "affName" to target.affFiles[diff.ratingClass]?.name,
                    "audio" to resolved?.audioFile.toResourceMap(
                        if (diff.audioOverride) "Chart audio override" else "Detected audio",
                    ),
                    "jacket" to resolved?.jacketFile.toResourceMap(
                        if (diff.jacketOverride) "Chart jacket override" else "Detected jacket",
                    ),
                    "background" to resolved?.backgroundFile.toResourceMap(
                        if (!diff.bg.isNullOrBlank() || !diff.bgInverse.isNullOrBlank()) {
                            "Chart background"
                        } else {
                            "Detected background"
                        },
                    ),
                    "audioOverride" to diff.audioOverride,
                    "jacketOverride" to diff.jacketOverride,
                    "bg" to bgReference,
                    "bgOverride" to (!diff.bg.isNullOrBlank() || !diff.bgInverse.isNullOrBlank()),
                    "missingBackgroundReference" to bgMissing,
                    "resourceWarnings" to if (bgMissing) {
                        listOf("Background reference not found: $bgReference")
                    } else {
                        emptyList<String>()
                    },
                )
            }
        }.orEmpty().ifEmpty {
            target.affFiles.toSortedMap().map { (ratingClass, file) ->
                mapOf(
                    "ratingClass" to ratingClass,
                    "affPath" to file.absolutePath,
                    "affName" to file.name,
                )
            }
        }
        val difficultySummary = metadata?.difficulties?.joinToString { diff ->
            val constant = diff.chartConstant?.let { "%.1f".format(Locale.ROOT, it) }.orEmpty()
            "${diff.ratingClass}:${diff.difficulty ?: "?"}${constant.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}"
        }
        val logs = listOf("Scanned $sourceRef", "Workspace: ${inputRoot.absolutePath}")
        return ScanPayload(
            data = mapOf(
                "sourcePath" to sourceRef,
                "sourceKind" to "official-song",
                "inputType" to inputType,
                "workspacePath" to inputRoot.absolutePath,
                "songId" to (metadata?.songId ?: target.songId ?: fallbackSong?.id ?: target.songDir.name),
                "title" to (metadata?.title ?: fallbackSong?.displayTitle()),
                "artist" to (metadata?.artist ?: fallbackSong?.artist ?: fallbackSong?.composer),
                "bpmText" to (metadata?.bpmText ?: fallbackSong?.bpmText),
                "bpmBase" to (metadata?.bpmBase ?: fallbackSong?.bpmBase),
                "difficulty" to difficultySummary,
                "charts" to charts,
                "audio" to firstDifficulty?.audioFile.toResourceMap("Detected audio"),
                "jacket" to firstDifficulty?.jacketFile.toResourceMap("Detected jacket"),
                "background" to firstDifficulty?.backgroundFile.toResourceMap("Detected background"),
                "songlist" to songlistFile.toResourceMap("Detected songlist"),
                "packlist" to packlistFile.toResourceMap("Detected packlist"),
                "affFiles" to target.affFiles.toSortedMap().map { (ratingClass, file) ->
                    mapOf(
                        "ratingClass" to ratingClass,
                        "path" to file.absolutePath,
                        "name" to file.name,
                        "sizeBytes" to file.length(),
                        "adopted" to true,
                    )
                },
                "warnings" to warnings,
                "logs" to logs,
            ),
            warnings = warnings,
            logs = logs,
        )
    }

    private fun convertSingle(
        workspace: File,
        output: File,
        request: Map<*, *>,
    ): ConvertPayload {
        output.parentFile?.mkdirs()
        val charts = request.listArg("charts")
        val resources = request.mapArg("resources")
        val appearance = request.mapArg("appearance")
        val preprocess = request.mapArg("preprocess")
        val result = EtoileBridgeConverter.convert(
            input = ConvertInput(
                workspaceDir = workspace,
                outputFile = output,
                manualMetadata = ManualMetadata(
                    songId = request.stringArg("levelId")?.takeIf { it.isNotBlank() },
                    title = request.stringArg("title")?.takeIf { it.isNotBlank() },
                    artist = request.stringArg("artist")?.takeIf { it.isNotBlank() },
                    bpmText = request.stringArg("bpmText")?.takeIf { it.isNotBlank() },
                    bpmBase = request.floatArg("bpmBase"),
                    difficulties = charts.map { chart ->
                        ManualDifficultyMetadata(
                            ratingClass = chart.intArg("ratingClass") ?: 0,
                            chartDesigner = chart.stringArg("charter")?.takeIf { it.isNotBlank() },
                            jacketDesigner = chart.stringArg("illustrator")?.takeIf { it.isNotBlank() },
                            difficulty = chart.stringArg("difficulty")?.takeIf { it.isNotBlank() },
                            chartConstant = chart.floatArg("chartConstant"),
                            bg = chart.stringArg("externalBackgroundStem")?.takeIf { it.isNotBlank() },
                        )
                    },
                ),
                resourceOverrides = ManualResourceOverrides(
                    audioFile = resources.stringArg("audioPath").toExistingFileOrNull(),
                    jacketFile = resources.stringArg("jacketPath").toExistingFileOrNull(),
                    backgroundFile = resources.stringArg("backgroundPath").toExistingFileOrNull(),
                    songlistFile = resources.stringArg("songlistPath").toExistingFileOrNull(),
                    packlistFile = resources.stringArg("packlistPath").toExistingFileOrNull(),
                ),
                chartOverrides = ManualChartOverrides(
                    adoptedAffByRatingClass = charts
                        .filter { it.boolArg("adopted") != false && !it.stringArg("affPath").isNullOrBlank() }
                        .associate { (it.intArg("ratingClass") ?: 0) to File(it.stringArg("affPath")!!) },
                    ignoredAffFiles = charts
                        .filter { it.boolArg("adopted") == false && !it.stringArg("affPath").isNullOrBlank() }
                        .map { File(it.stringArg("affPath")!!) }
                        .toSet(),
                ),
                packageOptions = PackageOptions(
                    publisherId = request.stringArg("publisherId")?.takeIf { it.isNotBlank() } ?: "etoilebridge",
                    levelId = request.stringArg("levelId")?.takeIf { it.isNotBlank() },
                ),
                appearanceOptions = AppearanceOptions(
                    side = appearance.stringArg("side").toNullableEnumUnlessInherit<ArcCreateSide>(),
                    note = appearance.stringArg("note").toEnumOrDefault(ArcCreateNote.INHERIT),
                    particle = appearance.stringArg("particle").toEnumOrDefault(ArcCreateParticle.INHERIT),
                    accent = appearance.stringArg("accent").toEnumOrDefault(ArcCreateAccent.INHERIT),
                    track = appearance.stringArg("track").toEnumOrDefault(ArcCreateTrack.INHERIT),
                    singleLine = appearance.stringArg("singleLine").toEnumOrDefault(ArcCreateSingleLine.NONE),
                ),
            ),
            options = ConvertOptions(
                enableDeleteDesignantLine = preprocess.boolArg("deleteDesignantLine") != false,
                enableFixZeroDurationArcTap = preprocess.boolArg("fixZeroDurationArcTap") != false,
                enableFixReversedArcTime = preprocess.boolArg("fixReversedArcTime") != false,
                enableExpandArcResolution = preprocess.boolArg("expandArcResolution") != false,
                keepWorkspaceOnFailure = true,
                cleanWorkspaceOnSuccess = true,
            ),
        )
        return when (result) {
            is ConvertResult.Success -> ConvertPayload(
                outputFile = result.outputFile,
                songId = result.songId,
                warnings = result.warnings,
                logs = result.logs + "Packed to cache: ${result.outputFile.absolutePath}",
            )
            is ConvertResult.NeedMetadata -> error(
                "Metadata is incomplete: ${result.missingMetadata.requiredFields.joinToString()}",
            )
            is ConvertResult.UnsupportedPackStructure -> error(result.message)
            is ConvertResult.Failed -> error(result.message)
        }
    }

    private fun prepareInputWorkspace(sourceRef: String, inputDir: File): PreparedInput {
        inputDir.deleteRecursively()
        inputDir.mkdirs()
        val uri = runCatching { Uri.parse(sourceRef) }.getOrNull()
        if (uri != null && uri.scheme == "content") {
            return if (DocumentsContract.isTreeUri(uri)) {
                copyTree(uri, inputDir)
                PreparedInput(normalizeArchiveRoot(inputDir), "Folder")
            } else {
                val displayName = displayName(uri)
                val safeName = displayName.sanitizeFileName()
                val sourceFile = inputDir.parentFile
                    ?.resolve("source")
                    ?.resolve(safeName)
                    ?: inputDir.resolve(safeName)
                copyUriToFile(uri, sourceFile)
                sourceFile.prepareFileInput(inputDir)
            }
        }
        val file = File(sourceRef)
        return when {
            file.isDirectory -> {
                copyLocalDirectory(file, inputDir)
                PreparedInput(normalizeArchiveRoot(inputDir), "Folder")
            }
            file.isFile -> file.prepareFileInput(inputDir)
            else -> error("Input not found: $sourceRef")
        }
    }

    private fun File.prepareFileInput(inputDir: File): PreparedInput {
        val lower = name.lowercase(Locale.ROOT)
        return if (lower.endsWith(".zip") || lower.endsWith(".arcpkg")) {
            extractZip(this, inputDir)
            PreparedInput(normalizeArchiveRoot(inputDir), "ZIP")
        } else {
            val out = inputDir.resolve(name)
            if (canonicalPath != out.canonicalPath) copyTo(out, overwrite = true)
            PreparedInput(inputDir, "File")
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

    private fun copyTree(treeUri: Uri, targetDir: File) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Unable to open selected folder.")
        require(root.isDirectory) { "Selected URI is not a directory." }
        copyDocumentDirectory(root, targetDir)
    }

    private fun copyDocumentDirectory(sourceDir: DocumentFile, targetDir: File) {
        targetDir.mkdirs()
        sourceDir.listFiles().forEach { child ->
            val safeName = child.name?.takeIf { it.isNotBlank() } ?: return@forEach
            val target = targetDir.resolve(safeName.sanitizeFileName())
            when {
                child.isDirectory -> copyDocumentDirectory(child, target)
                child.isFile -> {
                    target.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Unable to read ${child.name ?: child.uri}")
                }
            }
        }
    }

    private fun copyUriToFile(sourceUri: Uri, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open input URI.")
    }

    private fun extractZip(zipFile: File, targetDir: File) {
        var lastError: Throwable? = null
        for (charset in listOf(Charsets.UTF_8, Charset.forName("GBK"), Charset.forName("CP936"), Charset.forName("CP437")).distinctBy { it.name() }) {
            targetDir.deleteRecursively()
            targetDir.mkdirs()
            val result = runCatching { extractZipWithCharset(zipFile, targetDir, charset) }
            if (result.isSuccess) return
            lastError = result.exceptionOrNull()
        }
        targetDir.deleteRecursively()
        throw IllegalStateException("ZIP extract failed: ${lastError?.message}", lastError)
    }

    private fun extractZipWithCharset(zipFile: File, targetDir: File, charset: Charset) {
        val targetCanonical = targetDir.canonicalFile
        ZipInputStream(BufferedInputStream(zipFile.inputStream()), charset).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = targetDir.resolve(entry.name).canonicalFile
                if (!output.path.startsWith(targetCanonical.path + File.separator) && output != targetCanonical) {
                    error("Blocked unsafe zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }

    private fun copyLocalDirectory(source: File, target: File) {
        source.walkTopDown().forEach { file ->
            val relative = file.relativeTo(source)
            val out = target.resolve(relative.path).canonicalFile
            val targetCanonical = target.canonicalFile
            if (!out.path.startsWith(targetCanonical.path + File.separator) && out != targetCanonical) {
                error("Refusing to copy outside workspace.")
            }
            if (file.isDirectory) out.mkdirs() else {
                out.parentFile?.mkdirs()
                file.copyTo(out, overwrite = true)
            }
        }
    }

    private fun writeFileToUri(sourceFile: File, targetUri: Uri): AndroidSaveTarget {
        val expected = sourceFile.length()
        val written = context.contentResolver.openOutputStream(targetUri, "w")?.use { output ->
            sourceFile.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    total += read
                }
                output.flush()
                total
            }
        } ?: error("Unable to open output URI for writing.")
        if (written != expected) {
            error("Saved file size mismatch: expected $expected bytes, wrote $written bytes.")
        }
        val queried = querySize(targetUri)
        if (queried != null && queried >= 0L && queried != expected) {
            error("Saved file size mismatch: expected $expected bytes, queried $queried bytes.")
        }
        return AndroidSaveTarget(displayName(targetUri), expected, queried)
    }

    private fun sessionDir(requested: String?): File {
        val root = cacheRoot()
        val requestedFile = requested?.takeIf { it.isNotBlank() }?.let(::File)
        val session = requestedFile
            ?.takeIf { isInside(root, it) || it.canonicalFile == root.canonicalFile }
            ?: root.resolve("single-session-${System.currentTimeMillis()}-${UUID.randomUUID()}")
        session.mkdirs()
        return session
    }

    private fun cacheRoot(): File =
        context.cacheDir.resolve("EtoileBridgeFlutter").resolve("cache").apply { mkdirs() }

    private fun isInside(root: File, child: File): Boolean {
        val rootPath = root.canonicalFile.toPath()
        val childPath = child.canonicalFile.toPath()
        return childPath.startsWith(rootPath)
    }

    private fun displayName(uri: Uri): String {
        queryColumn(uri, OpenableColumns.DISPLAY_NAME) { cursor, index ->
            cursor.getString(index)
        }?.let { return it }
        val treeName = runCatching {
            DocumentsContract.getTreeDocumentId(uri)
                .substringAfterLast(':')
                .takeIf { it.isNotBlank() }
        }.getOrNull()
        return treeName ?: uri.lastPathSegment ?: "selected-input"
    }

    private fun querySize(uri: Uri): Long? =
        queryColumn(uri, OpenableColumns.SIZE) { cursor, index ->
            if (cursor.isNull(index)) null else cursor.getLong(index)
        }

    private fun <T> queryColumn(
        uri: Uri,
        column: String,
        read: (android.database.Cursor, Int) -> T?,
    ): T? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val index = cursor.getColumnIndex(column)
                    if (index < 0) null else read(cursor, index)
                }
        }.getOrNull()

    private fun runEnvelope(actionId: String, block: () -> EnvelopeResult): Map<String, Any?> =
        try {
            Log.i(TAG, "$actionId.start")
            val result = block()
            Log.i(TAG, "$actionId.success")
            mapOf(
                "ok" to true,
                "data" to result.data,
                "warnings" to result.warnings,
                "logs" to result.logs,
            )
        } catch (error: Throwable) {
            Log.e(TAG, "$actionId.error", error)
            mapOf(
                "ok" to false,
                "error" to (error.message ?: error.javaClass.simpleName),
                "logs" to listOf("$actionId.error", error.stackTraceToString()),
            )
        }
}

private data class PreparedInput(val inputRoot: File, val inputType: String)

private data class ScanPayload(
    val data: Map<String, Any?>,
    val warnings: List<String>,
    val logs: List<String>,
)

private data class ConvertPayload(
    val outputFile: File,
    val songId: String?,
    val warnings: List<String>,
    val logs: List<String>,
)

private data class EnvelopeResult(
    val data: Map<String, Any?>,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
)

private data class AndroidSaveTarget(
    val displayName: String?,
    val expectedBytes: Long,
    val queriedBytes: Long?,
)

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

private fun File?.toResourceMap(source: String): Map<String, Any?>? {
    val file = this?.takeIf { it.isFile } ?: return null
    val dimension = ImageDimensionReader.read(file)
    return mapOf(
        "path" to file.absolutePath,
        "name" to file.name,
        "source" to source,
        "sizeBytes" to file.length(),
        "width" to dimension?.width,
        "height" to dimension?.height,
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

private fun String.sanitizeFileName(): String =
    replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "selected-input" }

private fun String?.toExistingFileOrNull(): File? =
    takeIf { !it.isNullOrBlank() }?.let(::File)?.takeIf { it.isFile }

private fun Map<*, *>.stringArg(key: String): String? =
    this[key]?.toString()

private fun Map<*, *>.boolArg(key: String): Boolean? =
    when (val value = this[key]) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull()
        else -> null
    }

private fun Map<*, *>.intArg(key: String): Int? =
    when (val value = this[key]) {
        is Int -> value
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

private fun Map<*, *>.floatArg(key: String): Float? =
    when (val value = this[key]) {
        is Float -> value
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull()
        else -> null
    }

private fun Map<*, *>.mapArg(key: String): Map<*, *> =
    this[key] as? Map<*, *> ?: emptyMap<Any, Any?>()

private fun Map<*, *>.listArg(key: String): List<Map<*, *>> =
    (this[key] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    runCatching { enumValueOf<T>(this?.uppercase(Locale.ROOT).orEmpty()) }.getOrDefault(default)

private inline fun <reified T : Enum<T>> String?.toNullableEnumUnlessInherit(): T? =
    this?.takeIf { it.isNotBlank() && !it.equals("INHERIT", ignoreCase = true) }
        ?.let { value -> runCatching { enumValueOf<T>(value.uppercase(Locale.ROOT)) }.getOrNull() }
