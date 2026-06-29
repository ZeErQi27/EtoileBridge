package com.zeerqi27.etoile_bridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.zeerqi27.etoilebridge.core.CharacterPackageBuilder
import com.zeerqi27.etoilebridge.core.CharacterPackageInput
import com.zeerqi27.etoilebridge.core.CharacterPackageLoader
import com.zeerqi27.etoilebridge.core.CharacterPackageOptions
import com.zeerqi27.etoilebridge.core.CharacterPackageResult
import com.zeerqi27.etoilebridge.core.CharacterValidationReport
import com.zeerqi27.etoilebridge.core.ImageDimensionReader
import com.zeerqi27.etoilebridge.core.splitCharacterIdentifier
import java.io.BufferedInputStream
import java.io.File
import java.nio.charset.Charset
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val CHARACTER_TAG = "EtoileAndroidCharacter"

class AndroidCharacterBridge(private val context: Context) {
    fun scanCharacterImage(args: Map<*, *>): Map<String, Any?> =
        runEnvelope("android.scanCharacterImage") {
            val source = args.stringArg("source") ?: error("Missing source URI.")
            val session = sessionDir(args.stringArg("session"))
            val inputRoot = session.resolve("input").apply {
                deleteRecursively()
                mkdirs()
            }
            val image = copySourceToFile(
                source,
                inputRoot.resolve(safeDisplayName(source, "character.png")),
            )
            val characterId = image.nameWithoutExtension.safeUiId("character")
            val scan = mapOf(
                "sourcePath" to source,
                "sourceKind" to "image",
                "inputType" to "File",
                "workspacePath" to inputRoot.absolutePath,
                "publisherId" to "etoilebridge",
                "characterId" to characterId,
                "directory" to characterId,
                "identifier" to "etoilebridge.$characterId",
                "outputFileName" to "etoilebridge.$characterId.arcpkg",
                "defaultName" to safeDisplayName(source, characterId).substringBeforeLast('.'),
                "imagePath" to image.name,
                "iconPath" to null,
                "image" to image.toResourceMap("Imported character image"),
                "icon" to null,
                "imageHasAlpha" to image.hasAlphaChannel(),
                "x" to 300f,
                "y" to 100f,
                "scale" to 0.7f,
                "warnings" to emptyList<String>(),
                "logs" to listOf(
                    "Imported character image: ${image.name}",
                    "Android session: ${session.absolutePath}",
                ),
            )
            CharacterEnvelopeResult(scan, logs = scan.stringListArg("logs"))
        }

    fun scanCharacterPackage(args: Map<*, *>): Map<String, Any?> =
        runEnvelope("android.scanCharacterPackage") {
            val source = args.stringArg("source") ?: error("Missing source URI.")
            val session = sessionDir(args.stringArg("session"))
            val sourceFile = copySourceToFile(
                source,
                session.resolve("source").resolve(safeDisplayName(source, "character.arcpkg")),
            )
            val inputRoot = session.resolve("input")
            extractZip(sourceFile, inputRoot)
            val root = normalizeCharacterRoot(inputRoot)
            val loaded = CharacterPackageLoader().loadExistingCharacterPackage(root)
            val warnings = loaded.warnings + loaded.errors
            val entry = loaded.entry
            val character = loaded.character
            val (publisherId, characterId) = splitCharacterIdentifier(
                entry?.identifier.orEmpty(),
                "etoilebridge",
                entry?.directory ?: "character",
            )
            val image = loaded.imageFile
            val icon = loaded.iconFile
            val scan = mapOf(
                "sourcePath" to source,
                "sourceKind" to "arcpkg",
                "inputType" to "ZIP",
                "workspacePath" to root.absolutePath,
                "publisherId" to publisherId,
                "characterId" to characterId.safeUiId("character"),
                "directory" to (entry?.directory ?: characterId).safeUiId("character"),
                "identifier" to (entry?.identifier ?: "$publisherId.$characterId"),
                "outputFileName" to "$publisherId.${characterId.safeUiId("character")}.arcpkg",
                "defaultName" to (character?.name?.get("default") ?: characterId),
                "zhCnName" to (character?.name?.get("zh-cn") ?: character?.name?.get("zh-CN")),
                "imagePath" to (character?.imagePath ?: image?.name),
                "iconPath" to (character?.iconPath ?: icon?.name),
                "image" to image.toResourceMap("Character image"),
                "icon" to icon.toResourceMap("Character icon"),
                "imageHasAlpha" to image.hasAlphaChannel(),
                "x" to (character?.x ?: 300f),
                "y" to (character?.y ?: 100f),
                "scale" to (character?.scale ?: 0.7f),
                "warnings" to warnings,
                "logs" to listOf(
                    "Loaded character arcpkg: ${safeDisplayName(source, sourceFile.name)}",
                    "Android session: ${session.absolutePath}",
                ),
            )
            CharacterEnvelopeResult(scan, warnings = warnings, logs = scan.stringListArg("logs"))
        }

    fun generateCharacterIcon(args: Map<*, *>): Map<String, Any?> =
        runEnvelope("android.generateCharacterIcon") {
            val image = args.stringArg("imagePath")?.let(::File)?.takeIf { it.isFile }
                ?: error("Character image not found.")
            val output = args.stringArg("outputPath")?.let(::File)
                ?: error("Missing icon output path.")
            generateIcon(
                source = image,
                output = output,
                centerX = args.floatArg("centerX") ?: 0.5f,
                centerY = args.floatArg("centerY") ?: 0.5f,
                cropSize = args.floatArg("cropSize") ?: 0.5f,
                outputSize = args.intArg("outputSize") ?: 256,
            )
            val data = mapOf(
                "iconPath" to output.absolutePath,
                "icon" to output.toResourceMap("Generated character icon"),
                "warnings" to emptyList<String>(),
                "logs" to listOf("Generated character icon: ${output.absolutePath}"),
            )
            CharacterEnvelopeResult(data, logs = data.stringListArg("logs"))
        }

    fun saveCharacter(args: Map<*, *>): Map<String, Any?> =
        runEnvelope("android.saveCharacter") {
            val outputUri = args.stringArg("output")?.let(Uri::parse)
                ?: error("Missing output URI.")
            val request = args.mapArg("request")
            val session = sessionDir(args.stringArg("session"))
            val image = request.stringArg("imagePath")?.let(::File)?.takeIf { it.isFile }
                ?: error("Character image is missing.")
            val icon = request.stringArg("iconPath")?.let(::File)?.takeIf { it.isFile }
                ?: error("Character icon is missing.")
            val tempOutput = session.resolve("output").resolve(
                "character-${System.currentTimeMillis()}-${UUID.randomUUID()}.arcpkg",
            )
            tempOutput.parentFile?.mkdirs()
            val build = CharacterPackageBuilder().build(
                CharacterPackageInput(
                    imageFile = image,
                    iconFile = icon,
                    outputFile = tempOutput,
                    options = CharacterPackageOptions(
                        publisherId = request.stringArg("publisherId") ?: "etoilebridge",
                        characterId = request.stringArg("characterId") ?: "character",
                        directory = request.stringArg("directory"),
                        defaultName = request.stringArg("defaultName") ?: "Character",
                        zhCnName = request.stringArg("zhCnName")?.takeIf { it.isNotBlank() },
                        imageFileName = request.stringArg("imageFileName") ?: image.name,
                        iconFileName = request.stringArg("iconFileName") ?: icon.name,
                        x = request.floatArg("x") ?: 300f,
                        y = request.floatArg("y") ?: 100f,
                        scale = request.floatArg("scale") ?: 0.7f,
                    ),
                ),
            )
            val success = when (build) {
                is CharacterPackageResult.Success -> build
                is CharacterPackageResult.Failed -> error(build.message)
            }
            if (!success.outputFile.isFile || success.outputFile.length() <= 0L) {
                error("converter-core did not produce a non-empty character arcpkg.")
            }
            val save = writeFileToUri(success.outputFile, outputUri)
            val data = mapOf(
                "outputPath" to outputUri.toString(),
                "displayName" to save.displayName,
                "sizeBytes" to save.expectedBytes,
                "queriedSizeBytes" to save.queriedBytes,
                "identifier" to success.identifier,
                "directory" to success.directory,
                "validation" to success.validation.toMap(),
            )
            CharacterEnvelopeResult(
                data = data,
                warnings = success.warnings + success.validation.warnings,
                logs = success.logs + success.validation.summaryLines() + success.validation.logs + listOf(
                    "android.saveCharacter.success",
                    "Saved via SAF: ${save.displayName ?: outputUri}",
                    "Verified saved bytes: ${save.expectedBytes}",
                ),
            )
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

    private fun normalizeCharacterRoot(archiveDir: File): File {
        var current = archiveDir
        while (true) {
            if (current.resolve("index.yml").isFile || current.resolve("index.yaml").isFile) return current
            val childDirs = current.listFiles()?.filter { it.isDirectory && it.name != "__MACOSX" }.orEmpty()
            val files = current.listFiles()?.filter { it.isFile && it.name != ".DS_Store" }.orEmpty()
            if (childDirs.size != 1 || files.isNotEmpty()) return current
            current = childDirs.single()
        }
    }

    private fun generateIcon(
        source: File,
        output: File,
        centerX: Float,
        centerY: Float,
        cropSize: Float,
        outputSize: Int,
    ) {
        val bitmap = BitmapFactory.decodeFile(source.absolutePath)
            ?: error("Unable to decode character image.")
        try {
            val size = (min(bitmap.width, bitmap.height) * cropSize.coerceIn(0.05f, 1f))
                .roundToInt()
                .coerceAtLeast(1)
            val cx = (bitmap.width * centerX.coerceIn(0f, 1f)).roundToInt()
            val cy = (bitmap.height * centerY.coerceIn(0f, 1f)).roundToInt()
            val left = (cx - size / 2).coerceIn(0, max(0, bitmap.width - size))
            val top = (cy - size / 2).coerceIn(0, max(0, bitmap.height - size))
            val cropped = Bitmap.createBitmap(
                bitmap,
                left,
                top,
                min(size, bitmap.width - left),
                min(size, bitmap.height - top),
            )
            try {
                val scaled = Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true)
                try {
                    output.parentFile?.mkdirs()
                    output.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally {
                    scaled.recycle()
                }
            } finally {
                cropped.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeFileToUri(sourceFile: File, targetUri: Uri): AndroidCharacterSaveTarget {
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
        return AndroidCharacterSaveTarget(displayName(targetUri), expected, queried)
    }

    private fun sessionDir(requested: String?): File {
        val root = cacheRoot()
        val requestedFile = requested?.takeIf { it.isNotBlank() }?.let(::File)
        val session = requestedFile
            ?.takeIf { isInside(root, it) || it.canonicalFile == root.canonicalFile }
            ?: root.resolve("character-session-${System.currentTimeMillis()}-${UUID.randomUUID()}")
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
        return uri.lastPathSegment ?: "selected-input"
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

    private fun runEnvelope(actionId: String, block: () -> CharacterEnvelopeResult): Map<String, Any?> =
        try {
            Log.i(CHARACTER_TAG, "$actionId.start")
            val result = block()
            Log.i(CHARACTER_TAG, "$actionId.success")
            mapOf(
                "ok" to true,
                "data" to result.data,
                "warnings" to result.warnings,
                "logs" to result.logs,
            )
        } catch (error: Throwable) {
            Log.e(CHARACTER_TAG, "$actionId.error", error)
            mapOf(
                "ok" to false,
                "error" to (error.message ?: error.javaClass.simpleName),
                "logs" to listOf("$actionId.error", error.stackTraceToString()),
            )
        }
}

private data class CharacterEnvelopeResult(
    val data: Map<String, Any?>,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
)

private data class AndroidCharacterSaveTarget(
    val displayName: String?,
    val expectedBytes: Long,
    val queriedBytes: Long?,
)

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

private fun File?.hasAlphaChannel(): Boolean? {
    val file = this?.takeIf { it.isFile } ?: return null
    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
    return runCatching { BitmapFactory.decodeFile(file.absolutePath, options)?.hasAlpha() }.getOrNull()
}

private fun CharacterValidationReport.toMap(): Map<String, Any?> =
    mapOf(
        "valid" to valid,
        "characterEntryCount" to characterEntryCount,
        "identifier" to identifier,
        "directory" to directory,
        "defaultName" to defaultName,
        "imageExists" to imageExists,
        "iconExists" to iconExists,
        "errors" to errors,
        "warnings" to warnings,
        "logs" to logs,
    )

private fun String.contentUriOrNull(): Uri? =
    runCatching { Uri.parse(this) }.getOrNull()?.takeIf { it.scheme == "content" }

private fun String.sanitizeFileName(): String =
    replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "selected-input" }

private fun String.safeUiId(fallback: String): String =
    replace(Regex("""[^\w.-]+"""), "_")
        .trim('.', '_', '-')
        .ifBlank { fallback }

private fun Map<*, *>.stringArg(key: String): String? =
    this[key]?.toString()

private fun Map<*, *>.stringListArg(key: String): List<String> =
    (this[key] as? List<*>).orEmpty()
        .mapNotNull { it?.toString()?.takeIf { value -> value.isNotBlank() } }

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
