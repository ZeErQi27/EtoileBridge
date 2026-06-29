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
import com.zeerqi27.etoilebridge.core.ArcpkgBundleMerger
import com.zeerqi27.etoilebridge.core.ArcpkgBundleScanResult
import com.zeerqi27.etoilebridge.core.ArcpkgLevelEntry
import com.zeerqi27.etoilebridge.core.ArcpkgPackImageCandidate
import com.zeerqi27.etoilebridge.core.ArcpkgSourceReport
import com.zeerqi27.etoilebridge.core.BundleChartEntry
import com.zeerqi27.etoilebridge.core.BundleChartOverride
import com.zeerqi27.etoilebridge.core.BundleConvertResult
import com.zeerqi27.etoilebridge.core.BundleEntry
import com.zeerqi27.etoilebridge.core.BundleEntryOverride
import com.zeerqi27.etoilebridge.core.BundleInput
import com.zeerqi27.etoilebridge.core.BundleOptions
import com.zeerqi27.etoilebridge.core.BundleOutputValidator
import com.zeerqi27.etoilebridge.core.BundleScanResult
import com.zeerqi27.etoilebridge.core.ConvertOptions
import com.zeerqi27.etoilebridge.core.ExistingPackEditScanResult
import com.zeerqi27.etoilebridge.core.ImageDimensionReader
import com.zeerqi27.etoilebridge.core.PackBundleConverter
import com.zeerqi27.etoilebridge.core.PackBundleScanner
import java.io.BufferedInputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

private const val PACK_TAG = "EtoileAndroidPack"

class AndroidPackBridge(private val context: Context) {
    fun scanPackOfficial(args: Map<*, *>): Map<String, Any?> =
        runEnvelope("android.scanPackOfficial") {
            val source = args.stringArg("source") ?: error("Missing source URI.")
            val session = sessionDir(args.stringArg("session"))
            val inputRoot = prepareOfficialWorkspace(source, session.resolve("input"))
            val scan = PackBundleScanner().scanOfficialPack(inputRoot)
            val data = scan.toPackMap(
                mode = "official",
                sourcePath = source,
                workspacePath = inputRoot.absolutePath,
                previewRoot = session.resolve("preview"),
            )
            PackEnvelopeResult(data = data, warnings = scan.warnings, logs = scan.logs)
        }

    fun scanPackBundle(args: Map<*, *>): Map<String, Any?> =
        runEnvelope("android.scanPackBundle") {
            val sources = args.stringListArg("sources").ifEmpty {
                args.stringArg("source")?.let(::listOf).orEmpty()
            }
            require(sources.isNotEmpty()) { "Missing arcpkg sources." }
            val session = sessionDir(args.stringArg("session"))
            val inputRoot = prepareArcpkgWorkspace(sources, session.resolve("input"))
            val scan = ArcpkgBundleMerger().scan(inputRoot)
            val data = scan.toPackMap(
                mode = "bundle",
                sourcePath = sources.joinToString(File.pathSeparator),
                workspacePath = inputRoot.absolutePath,
                previewRoot = session.resolve("preview"),
            )
            PackEnvelopeResult(data = data, warnings = scan.warnings, logs = scan.logs)
        }

    fun scanPackExisting(args: Map<*, *>): Map<String, Any?> =
        runEnvelope("android.scanPackExisting") {
            val base = args.stringArg("base") ?: error("Missing base pack URI.")
            val addSources = args.stringListArg("addSources")
            val session = sessionDir(args.stringArg("session"))
            val baseFile = copySourceToFile(base, session.resolve("base").resolve(safeDisplayName(base, "base.arcpkg")))
            val addInput = addSources.takeIf { it.isNotEmpty() }?.let {
                prepareArcpkgWorkspace(it, session.resolve("add-input"))
            }
            val scan = ArcpkgBundleMerger().scanExistingPack(baseFile, addInput)
            val data = scan.toPackMap(
                basePackPath = baseFile.absolutePath,
                addWorkspacePath = addInput?.absolutePath,
                previewRoot = session.resolve("preview"),
                originalSource = base,
            )
            PackEnvelopeResult(data = data, warnings = scan.warnings, logs = scan.logs)
        }

    fun savePack(args: Map<*, *>): Map<String, Any?> =
        runEnvelope("android.savePack") {
            val outputUri = args.stringArg("output")?.let(Uri::parse)
                ?: error("Missing output URI.")
            val request = args.mapArg("request")
            val mode = args.stringArg("mode") ?: request.stringArg("mode") ?: "bundle"
            val session = sessionDir(args.stringArg("session"))
            val tempOutput = session.resolve("output").resolve(
                "pack-${System.currentTimeMillis()}-${UUID.randomUUID()}.arcpkg",
            )
            tempOutput.parentFile?.mkdirs()
            val result = when (mode) {
                "official" -> {
                    val workspace = args.stringArg("workspace")?.let(::File)
                        ?: error("Missing official pack workspace.")
                    PackBundleConverter().convertOfficialPack(
                        BundleInput(
                            workspaceDir = workspace,
                            outputFile = tempOutput,
                            options = request.toBundleOptions(tempOutput),
                        ),
                    )
                }
                "existing" -> {
                    val basePack = args.stringArg("base")?.let(::File)
                        ?: error("Missing existing base pack path.")
                    val addInput = args.stringArg("addWorkspace")?.let(::File)?.takeIf { it.exists() }
                    ArcpkgBundleMerger().editExistingPack(
                        basePack = basePack,
                        addInput = addInput,
                        outputFile = tempOutput,
                        options = request.toBundleOptions(tempOutput),
                    )
                }
                else -> {
                    val workspace = args.stringArg("workspace")?.let(::File)
                        ?: error("Missing arcpkg bundle workspace.")
                    ArcpkgBundleMerger().merge(
                        input = workspace,
                        outputFile = tempOutput,
                        options = request.toBundleOptions(tempOutput),
                    )
                }
            }
            val success = when (result) {
                is BundleConvertResult.Success -> result
                is BundleConvertResult.Failed -> error(result.message)
            }
            if (!success.outputFile.isFile || success.outputFile.length() <= 0L) {
                error("converter-core did not produce a non-empty pack arcpkg.")
            }
            val validation = BundleOutputValidator().validateBundleArcpkg(success.outputFile)
            val save = writeFileToUri(success.outputFile, outputUri)
            PackEnvelopeResult(
                data = mapOf(
                    "outputPath" to outputUri.toString(),
                    "displayName" to save.displayName,
                    "sizeBytes" to save.expectedBytes,
                    "queriedSizeBytes" to save.queriedBytes,
                    "convertedCount" to success.convertedCount,
                    "skippedCount" to success.skippedCount,
                ),
                warnings = success.warnings + validation.warnings,
                logs = success.logs + validation.summaryLines() + validation.logs + listOf(
                    "android.savePack.success",
                    "Saved via SAF: ${save.displayName ?: outputUri}",
                    "Verified saved bytes: ${save.expectedBytes}",
                ),
            )
        }

    private fun prepareOfficialWorkspace(sourceRef: String, inputDir: File): File {
        inputDir.deleteRecursively()
        inputDir.mkdirs()
        val uri = sourceRef.contentUriOrNull()
        if (uri != null) {
            return if (DocumentsContract.isTreeUri(uri)) {
                copyTree(uri, inputDir)
                normalizeArchiveRoot(inputDir)
            } else {
                val sourceFile = copySourceToFile(
                    sourceRef,
                    inputDir.parentFile!!.resolve("source").resolve(safeDisplayName(sourceRef, "pack.zip")),
                )
                sourceFile.prepareOfficialFileInput(inputDir)
            }
        }
        val file = File(sourceRef)
        return when {
            file.isDirectory -> {
                copyLocalDirectory(file, inputDir)
                normalizeArchiveRoot(inputDir)
            }
            file.isFile -> file.prepareOfficialFileInput(inputDir)
            else -> error("Input not found: $sourceRef")
        }
    }

    private fun File.prepareOfficialFileInput(inputDir: File): File {
        val lower = name.lowercase(Locale.ROOT)
        return if (lower.endsWith(".zip") || lower.endsWith(".arcpkg")) {
            extractZip(this, inputDir)
            normalizeArchiveRoot(inputDir)
        } else {
            val out = inputDir.resolve(name)
            if (canonicalPath != out.canonicalPath) copyTo(out, overwrite = true)
            inputDir
        }
    }

    private fun prepareArcpkgWorkspace(sourceRefs: List<String>, inputDir: File): File {
        inputDir.deleteRecursively()
        inputDir.mkdirs()
        val usedNames = mutableSetOf<String>()
        sourceRefs.forEach { ref ->
            val uri = ref.contentUriOrNull()
            if (uri != null && !DocumentsContract.isTreeUri(uri)) {
                copySourceToFile(ref, inputDir.resolve(uniqueFileName(safeDisplayName(ref, "source.arcpkg"), usedNames)))
                return@forEach
            }
            val file = File(ref)
            when {
                file.isFile && file.extension.equals("arcpkg", ignoreCase = true) ->
                    file.copyTo(inputDir.resolve(uniqueFileName(file.name, usedNames)), overwrite = true)
                file.isDirectory ->
                    file.walkTopDown()
                        .filter { it.isFile && it.extension.equals("arcpkg", ignoreCase = true) }
                        .sortedBy { it.name.lowercase(Locale.ROOT) }
                        .forEach { it.copyTo(inputDir.resolve(uniqueFileName(it.name, usedNames)), overwrite = true) }
                else -> error("Unsupported arcpkg input: $ref")
            }
        }
        return inputDir
    }

    private fun normalizeArchiveRoot(archiveDir: File): File {
        var current = archiveDir
        while (true) {
            val hasRootMarkers = current.resolve("songlist").isFile ||
                current.resolve("slst").isFile ||
                current.resolve("packlist").isFile ||
                current.resolve("assets/songs").isDirectory ||
                current.listFiles()?.any { it.isDirectory && it.walkTopDown().any { child -> child.isFile && child.extension.equals("aff", ignoreCase = true) } } == true
            if (hasRootMarkers) return current
            val childDirs = current.listFiles()?.filter { it.isDirectory && it.name != "__MACOSX" }.orEmpty()
            val meaningfulFiles = current.listFiles()?.filter { it.isFile && it.name != ".DS_Store" }.orEmpty()
            if (childDirs.size != 1 || meaningfulFiles.isNotEmpty()) return current
            current = childDirs.single()
        }
    }

    private fun copyTree(treeUri: Uri, targetDir: File) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Unable to open selected folder.")
        require(root.isDirectory) { "Selected URI is not a directory." }
        copyDocumentDirectory(root, targetDir)
    }

    private fun copyDocumentDirectory(sourceDir: DocumentFile, targetDir: File) {
        targetDir.mkdirs()
        sourceDir.listFiles().forEach { child ->
            val safeName = child.name?.takeIf { it.isNotBlank() }?.sanitizeFileName() ?: return@forEach
            val target = targetDir.resolve(safeName)
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

    private fun copySourceToFile(sourceRef: String, targetFile: File): File {
        targetFile.parentFile?.mkdirs()
        val uri = sourceRef.contentUriOrNull()
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to open input URI.")
            return targetFile
        }
        File(sourceRef).copyTo(targetFile, overwrite = true)
        return targetFile
    }

    private fun extractZip(zipFile: File, targetDir: File) {
        var lastError: Throwable? = null
        for (charset in listOf(Charsets.UTF_8, Charset.forName("GBK"))) {
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

    private fun writeFileToUri(sourceFile: File, targetUri: Uri): AndroidPackSaveTarget {
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
        return AndroidPackSaveTarget(displayName(targetUri), expected, queried)
    }

    private fun sessionDir(requested: String?): File {
        val root = cacheRoot()
        val requestedFile = requested?.takeIf { it.isNotBlank() }?.let(::File)
        val session = requestedFile
            ?.takeIf { isInside(root, it) || it.canonicalFile == root.canonicalFile }
            ?: root.resolve("pack-session-${System.currentTimeMillis()}-${UUID.randomUUID()}")
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

    private fun safeDisplayName(sourceRef: String, fallback: String): String {
        val uri = sourceRef.contentUriOrNull()
        val name = uri?.let(::displayName) ?: File(sourceRef).name.ifBlank { fallback }
        return name.sanitizeFileName().ifBlank { fallback }
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

    private fun runEnvelope(actionId: String, block: () -> PackEnvelopeResult): Map<String, Any?> =
        try {
            Log.i(PACK_TAG, "$actionId.start")
            val result = block()
            Log.i(PACK_TAG, "$actionId.success")
            mapOf(
                "ok" to true,
                "data" to result.data,
                "warnings" to result.warnings,
                "logs" to result.logs,
            )
        } catch (error: Throwable) {
            Log.e(PACK_TAG, "$actionId.error", error)
            mapOf(
                "ok" to false,
                "error" to (error.message ?: error.javaClass.simpleName),
                "logs" to listOf("$actionId.error", error.stackTraceToString()),
            )
        }
}

private data class PackEnvelopeResult(
    val data: Map<String, Any?>,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
)

private data class AndroidPackSaveTarget(
    val displayName: String?,
    val expectedBytes: Long,
    val queriedBytes: Long?,
)

private data class ParsedPackIdentifier(
    val publisherId: String?,
    val packId: String?,
    val warning: String? = null,
)

private fun BundleScanResult.toPackMap(
    mode: String,
    sourcePath: String,
    workspacePath: String,
    previewRoot: File,
): Map<String, Any?> =
    mapOf(
        "mode" to mode,
        "sourcePath" to sourcePath,
        "workspacePath" to workspacePath,
        "packName" to packNameCandidate,
        "packId" to packIdCandidate,
        "packImage" to packImageFile.toResourceMap("Detected pack image"),
        "entries" to entries.map { it.toPackLevelMap() },
        "existingLevelCount" to 0,
        "addedLevelCount" to entries.size,
        "finalLevelCount" to entries.size,
        "warnings" to warnings,
        "logs" to logs,
    )

private fun ArcpkgBundleScanResult.toPackMap(
    mode: String,
    sourcePath: String,
    workspacePath: String,
    previewRoot: File,
): Map<String, Any?> {
    val imageFile = packImageCandidate?.extractPackImage(previewRoot)
    val firstPack = packEntries.firstOrNull()
    val parsed = parsePackIdentifier(firstPack?.identifier)
    return mapOf(
        "mode" to mode,
        "sourcePath" to sourcePath,
        "workspacePath" to workspacePath,
        "publisherId" to parsed.publisherId,
        "packName" to packNameCandidate,
        "packId" to (parsed.packId ?: firstPack?.directory),
        "packIdentifier" to firstPack?.identifier,
        "packDirectory" to firstPack?.directory,
        "packImage" to imageFile.toResourceMap("Source pack image"),
        "entries" to levelEntries.map { it.toPackLevelMap(previewRoot) },
        "sourceReports" to sourceFiles.map { it.toMap() },
        "existingLevelCount" to 0,
        "addedLevelCount" to levelEntries.size,
        "finalLevelCount" to levelEntries.size,
        "warnings" to warnings,
        "logs" to logs,
    )
}

private fun ExistingPackEditScanResult.toPackMap(
    basePackPath: String,
    addWorkspacePath: String?,
    previewRoot: File,
    originalSource: String,
): Map<String, Any?> {
    val imageFile = packImageCandidate?.extractPackImage(previewRoot)
    val parsed = parsePackIdentifier(basePackEntry?.identifier)
    val parseWarnings = listOfNotNull(parsed.warning)
    return mapOf(
        "mode" to "existing",
        "sourcePath" to originalSource,
        "basePackPath" to basePackPath,
        "addWorkspacePath" to addWorkspacePath,
        "publisherId" to parsed.publisherId,
        "packName" to packNameCandidate,
        "packId" to (parsed.packId ?: packIdCandidate),
        "packIdentifier" to basePackEntry?.identifier,
        "packDirectory" to basePackEntry?.directory,
        "packImage" to imageFile.toResourceMap("Existing pack image"),
        "entries" to (existingLevels + addedLevels).map { it.toPackLevelMap(previewRoot) },
        "sourceReports" to sourceFiles.map { it.toMap() },
        "existingLevelCount" to existingLevelCount,
        "addedLevelCount" to addedLevelCount,
        "finalLevelCount" to finalLevelCount,
        "renamedConflictCount" to renamedConflictCount,
        "warnings" to warnings + parseWarnings,
        "logs" to logs,
    )
}

private fun BundleEntry.toPackLevelMap(): Map<String, Any?> =
    mapOf(
        "key" to key,
        "songId" to songId,
        "title" to title,
        "artist" to artist,
        "levelId" to songId,
        "difficultySummary" to difficultySummary,
        "chartCount" to charts.size,
        "resourceStatus" to resourceStatus(),
        "jacket" to jacketFile.toResourceMap("Level jacket"),
        "background" to backgroundFile.toResourceMap("Level background"),
        "enabled" to canConvert,
        "canConvert" to canConvert,
        "charts" to charts.map { it.toMap() },
        "warnings" to warnings,
        "failureReason" to failureReason,
    )

private fun ArcpkgLevelEntry.toPackLevelMap(previewRoot: File): Map<String, Any?> {
    val jacketFile = extractArcpkgLevelPreviewImage(previewRoot, "jacketPath")
    val backgroundFile = extractArcpkgLevelPreviewImage(previewRoot, "backgroundPath")
    return mapOf(
        "key" to key,
        "sourceFile" to sourceFile.absolutePath,
        "directory" to directory,
        "identifier" to identifier,
        "songId" to directory,
        "title" to title,
        "artist" to artist,
        "levelId" to identifier.substringAfterLast('.'),
        "difficultySummary" to difficultySummary,
        "chartCount" to charts.size,
        "resourceStatus" to if (failureReason == null) "project.arcproj ok" else "metadata warning",
        "jacket" to jacketFile.toResourceMap("ArcCreate level jacket"),
        "background" to backgroundFile.toResourceMap("ArcCreate level background"),
        "enabled" to (failureReason == null),
        "canConvert" to (failureReason == null),
        "charts" to charts.map { it.toMap() },
        "warnings" to warnings,
        "failureReason" to failureReason,
    )
}

private fun BundleChartEntry.toMap(): Map<String, Any?> =
    mapOf(
        "ratingClass" to ratingClass,
        "chartPath" to chartPath,
        "difficulty" to difficulty,
        "chartConstant" to chartConstant,
        "charter" to charter,
        "illustrator" to illustrator,
        "enabled" to enabled,
        "canConvert" to canConvert,
        "warnings" to warnings,
        "failureReason" to failureReason,
    )

private fun ArcpkgSourceReport.toMap(): Map<String, Any?> =
    mapOf(
        "sourceFile" to sourceFile.absolutePath,
        "readable" to readable,
        "levelCount" to levelCount,
        "packEntryCount" to packEntryCount,
        "packName" to packName,
        "packImagePath" to packImagePath,
        "packImageExists" to packImageExists,
        "packLevelIdentifierCount" to packLevelIdentifierCount,
        "packMatchesIndexLevels" to packMatchesIndexLevels,
        "failureReason" to failureReason,
    )

private fun BundleEntry.resourceStatus(): String {
    val missing = buildList {
        if (audioFile == null) add("audio")
        if (jacketFile == null) add("jacket")
        if (charts.isEmpty()) add("charts")
    }
    return if (missing.isEmpty()) "ok" else "missing ${missing.joinToString()}"
}

private fun Map<*, *>.toBundleOptions(output: File): BundleOptions =
    BundleOptions(
        publisherId = stringArg("publisherId")?.takeIf { it.isNotBlank() } ?: "etoilebridge",
        outputFileName = stringArg("outputFileName")?.takeIf { it.isNotBlank() } ?: output.name,
        packName = stringArg("packName")?.takeIf { it.isNotBlank() },
        packId = stringArg("packId")?.takeIf { it.isNotBlank() },
        packImageFile = stringArg("packImagePath").toExistingFileOrNull(),
        includeOnlyConvertible = true,
        convertOptions = ConvertOptions(
            enableDeleteDesignantLine = mapArg("preprocess").boolArg("deleteDesignantLine") != false,
            enableFixZeroDurationArcTap = mapArg("preprocess").boolArg("fixZeroDurationArcTap") != false,
            enableFixReversedArcTime = mapArg("preprocess").boolArg("fixReversedArcTime") != false,
            enableExpandArcResolution = mapArg("preprocess").boolArg("expandArcResolution") != false,
            keepWorkspaceOnFailure = true,
            cleanWorkspaceOnSuccess = true,
        ),
        appearanceOptions = AppearanceOptions(
            side = mapArg("appearance").stringArg("side").toNullableEnumUnlessInherit<ArcCreateSide>(),
            note = mapArg("appearance").stringArg("note").toEnumOrDefault(ArcCreateNote.INHERIT),
            particle = mapArg("appearance").stringArg("particle").toEnumOrDefault(ArcCreateParticle.INHERIT),
            accent = mapArg("appearance").stringArg("accent").toEnumOrDefault(ArcCreateAccent.INHERIT),
            track = mapArg("appearance").stringArg("track").toEnumOrDefault(ArcCreateTrack.INHERIT),
            singleLine = mapArg("appearance").stringArg("singleLine").toEnumOrDefault(ArcCreateSingleLine.NONE),
        ),
        entryOverrides = listArg("entries").associate { entry ->
            val key = entry.stringArg("key").orEmpty()
            key to BundleEntryOverride(
                enabled = entry.boolArg("enabled"),
                title = entry.stringArg("title")?.takeIf { it.isNotBlank() },
                artist = entry.stringArg("artist")?.takeIf { it.isNotBlank() },
                levelId = entry.stringArg("levelId")?.takeIf { it.isNotBlank() },
                chartOverrides = entry.listArg("charts").associate { chart ->
                    (chart.intArg("ratingClass") ?: 0) to BundleChartOverride(
                        enabled = chart.boolArg("enabled"),
                        difficulty = chart.stringArg("difficulty")?.takeIf { it.isNotBlank() },
                        chartConstant = chart.floatArg("chartConstant"),
                        charter = chart.stringArg("charter")?.takeIf { it.isNotBlank() },
                        illustrator = chart.stringArg("illustrator")?.takeIf { it.isNotBlank() },
                    )
                },
            )
        }.filterKeys { it.isNotBlank() },
    )

private fun parsePackIdentifier(identifier: String?): ParsedPackIdentifier {
    val value = identifier?.trim().orEmpty()
    if (value.isBlank()) return ParsedPackIdentifier(null, null)
    if (!value.endsWith(".pack")) {
        return ParsedPackIdentifier(
            null,
            null,
            "Cannot parse existing pack identifier, keeping original identifier: $value",
        )
    }
    val body = value.removeSuffix(".pack")
    val publisherId = body.substringBefore('.', missingDelimiterValue = "")
    val packId = body.substringAfter('.', missingDelimiterValue = "")
    return if (publisherId.isNotBlank() && packId.isNotBlank()) {
        ParsedPackIdentifier(publisherId, packId)
    } else {
        ParsedPackIdentifier(
            null,
            null,
            "Cannot parse existing pack identifier, keeping original identifier: $value",
        )
    }
}

private fun ArcpkgPackImageCandidate.extractPackImage(previewRoot: File): File? =
    runCatching {
        previewRoot.mkdirs()
        val extension = zipEntryPath.substringAfterLast('.', "png").ifBlank { "png" }
        val target = previewRoot.resolve("pack-image.${extension.lowercase(Locale.ROOT)}")
        ZipFile(sourceFile).use { zip ->
            val entry = zip.getEntry(zipEntryPath) ?: return@runCatching null
            zip.getInputStream(entry).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        target.takeIf { it.isFile }
    }.getOrNull()

private fun ArcpkgLevelEntry.extractArcpkgLevelPreviewImage(previewRoot: File, projectImageKey: String): File? =
    runCatching {
        ZipFile(sourceFile).use { zip ->
            val settingsZipPath = zipPath(directory, settingsFile)
            val projectEntry = zip.getEntry(settingsZipPath) ?: return@use null
            val projectText = zip.getInputStream(projectEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val imagePath = readProjectScalar(projectText, projectImageKey) ?: return@use null
            val imageZipPath = zipPath(directory, imagePath)
            val imageEntry = zip.getEntry(imageZipPath) ?: return@use null
            val extension = File(imagePath).extension.takeIf { it.isNotBlank() } ?: "png"
            val safeName = "${key.replace(Regex("[^A-Za-z0-9_.-]+"), "_")}-$projectImageKey.$extension"
            val output = previewRoot.resolve(safeName)
            output.parentFile?.mkdirs()
            zip.getInputStream(imageEntry).use { input ->
                Files.copy(input, output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            output
        }
    }.getOrNull()

private fun readProjectScalar(projectText: String, key: String): String? =
    projectText.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$key:") }
        ?.substringAfter(":")
        ?.trimYamlScalar()
        ?.takeIf { it.isNotBlank() }

private fun zipPath(directory: String, childPath: String): String =
    listOf(directory.trim('/').replace('\\', '/'), childPath.trim('/').replace('\\', '/'))
        .filter { it.isNotBlank() }
        .joinToString("/")

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

private fun uniqueFileName(name: String, usedNames: MutableSet<String>): String {
    if (usedNames.add(name)) return name
    val stem = name.substringBeforeLast('.', name)
    val extension = name.substringAfterLast('.', "")
    var index = 2
    while (true) {
        val candidate = if (extension.isBlank()) "${stem}_$index" else "${stem}_$index.$extension"
        if (usedNames.add(candidate)) return candidate
        index++
    }
}

private fun String?.toExistingFileOrNull(): File? =
    takeIf { !it.isNullOrBlank() }?.let(::File)?.takeIf { it.isFile }

private fun String.contentUriOrNull(): Uri? =
    runCatching { Uri.parse(this) }.getOrNull()?.takeIf { it.scheme == "content" }

private fun String.sanitizeFileName(): String =
    replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "selected-input" }

private fun String.trimYamlScalar(): String =
    trim().removeSurrounding("\"").removeSurrounding("'").trim()

private fun Map<*, *>.stringArg(key: String): String? =
    this[key]?.toString()

private fun Map<*, *>.stringListArg(key: String): List<String> =
    (this[key] as? List<*>).orEmpty()
        .mapNotNull { it?.toString()?.takeIf { value -> value.isNotBlank() } }

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
