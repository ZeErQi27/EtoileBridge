package com.zeerqi27.etoilebridge.electron.worker

import com.zeerqi27.etoilebridge.core.ConvertInput
import com.zeerqi27.etoilebridge.core.ConvertOptions
import com.zeerqi27.etoilebridge.core.ConvertResult
import com.zeerqi27.etoilebridge.core.EtoileBridgeConverter
import com.zeerqi27.etoilebridge.core.AppearanceOptions
import com.zeerqi27.etoilebridge.core.ArcpkgBundleMerger
import com.zeerqi27.etoilebridge.core.ArcpkgBundleScanResult
import com.zeerqi27.etoilebridge.core.ArcpkgLevelEntry
import com.zeerqi27.etoilebridge.core.ArcpkgPackImageCandidate
import com.zeerqi27.etoilebridge.core.ArcpkgSourceReport
import com.zeerqi27.etoilebridge.core.ArcCreateAccent
import com.zeerqi27.etoilebridge.core.ArcCreateProjectReader
import com.zeerqi27.etoilebridge.core.ArcCreateParticle
import com.zeerqi27.etoilebridge.core.ArcCreateSingleLine
import com.zeerqi27.etoilebridge.core.ArcCreateTrack
import com.zeerqi27.etoilebridge.core.BundleChartEntry
import com.zeerqi27.etoilebridge.core.BundleChartOverride
import com.zeerqi27.etoilebridge.core.BundleConvertResult
import com.zeerqi27.etoilebridge.core.BundleEntry
import com.zeerqi27.etoilebridge.core.BundleEntryOverride
import com.zeerqi27.etoilebridge.core.BundleInput
import com.zeerqi27.etoilebridge.core.BundleOptions
import com.zeerqi27.etoilebridge.core.BundleOutputValidator
import com.zeerqi27.etoilebridge.core.BundleScanResult
import com.zeerqi27.etoilebridge.core.BundleValidationReport
import com.zeerqi27.etoilebridge.core.CharacterPackageBuilder
import com.zeerqi27.etoilebridge.core.CharacterPackageInput
import com.zeerqi27.etoilebridge.core.CharacterPackageLoader
import com.zeerqi27.etoilebridge.core.CharacterPackageOptions
import com.zeerqi27.etoilebridge.core.CharacterPackageResult
import com.zeerqi27.etoilebridge.core.CharacterValidationReport
import com.zeerqi27.etoilebridge.core.ExistingPackEditScanResult
import com.zeerqi27.etoilebridge.core.ImageDimensionReader
import com.zeerqi27.etoilebridge.core.InputKind
import com.zeerqi27.etoilebridge.core.InputScanner
import com.zeerqi27.etoilebridge.core.ManualMetadata
import com.zeerqi27.etoilebridge.core.ManualChartOverrides
import com.zeerqi27.etoilebridge.core.ManualDifficultyMetadata
import com.zeerqi27.etoilebridge.core.ManualResourceOverrides
import com.zeerqi27.etoilebridge.core.MetadataResolution
import com.zeerqi27.etoilebridge.core.MetadataResolver
import com.zeerqi27.etoilebridge.core.PackageOptions
import com.zeerqi27.etoilebridge.core.PacklistParser
import com.zeerqi27.etoilebridge.core.PackBundleConverter
import com.zeerqi27.etoilebridge.core.PackBundleScanner
import com.zeerqi27.etoilebridge.core.ResourceResolver
import com.zeerqi27.etoilebridge.core.ScannedInput
import com.zeerqi27.etoilebridge.core.Songlist
import com.zeerqi27.etoilebridge.core.SonglistParser
import com.zeerqi27.etoilebridge.core.SonglistSong
import com.zeerqi27.etoilebridge.core.splitCharacterIdentifier
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

private val JsonOut = Json {
    prettyPrint = false
    explicitNulls = false
}

@Serializable
private data class WorkerEnvelope(
    val ok: Boolean,
    val data: JsonElement? = null,
    val error: String? = null,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
)

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

@Serializable
data class PackScanJson(
    val mode: String,
    val sourcePath: String? = null,
    val basePackPath: String? = null,
    val addWorkspacePath: String? = null,
    val workspacePath: String? = null,
    val publisherId: String? = null,
    val packName: String? = null,
    val packId: String? = null,
    val packIdentifier: String? = null,
    val packDirectory: String? = null,
    val packImage: ResourceJson? = null,
    val entries: List<PackLevelJson> = emptyList(),
    val sourceReports: List<PackSourceReportJson> = emptyList(),
    val existingLevelCount: Int = 0,
    val addedLevelCount: Int = 0,
    val finalLevelCount: Int = entries.size,
    val renamedConflictCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
)

@Serializable
data class PackLevelJson(
    val key: String,
    val sourceFile: String? = null,
    val directory: String? = null,
    val identifier: String? = null,
    val songId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val levelId: String? = null,
    val difficultySummary: String = "",
    val chartCount: Int = 0,
    val resourceStatus: String = "",
    val jacket: ResourceJson? = null,
    val background: ResourceJson? = null,
    val enabled: Boolean = true,
    val canConvert: Boolean = true,
    val charts: List<PackChartJson> = emptyList(),
    val warnings: List<String> = emptyList(),
    val failureReason: String? = null,
)

@Serializable
data class PackChartJson(
    val ratingClass: Int,
    val chartPath: String? = null,
    val difficulty: String? = null,
    val chartConstant: Float? = null,
    val charter: String? = null,
    val illustrator: String? = null,
    val enabled: Boolean = true,
    val canConvert: Boolean = true,
    val warnings: List<String> = emptyList(),
    val failureReason: String? = null,
)

@Serializable
data class PackSourceReportJson(
    val sourceFile: String,
    val readable: Boolean,
    val levelCount: Int = 0,
    val packEntryCount: Int = 0,
    val packName: String? = null,
    val packImagePath: String? = null,
    val packImageExists: Boolean = false,
    val packLevelIdentifierCount: Int = 0,
    val packMatchesIndexLevels: Boolean? = null,
    val failureReason: String? = null,
)

@Serializable
data class PackSaveRequestJson(
    val mode: String,
    val publisherId: String = "etoilebridge",
    val outputFileName: String? = null,
    val packName: String? = null,
    val packId: String? = null,
    val packIdentifier: String? = null,
    val packImagePath: String? = null,
    val entries: List<PackEntryEditJson> = emptyList(),
    val appearance: AppearanceJson = AppearanceJson(),
    val preprocess: PreprocessJson = PreprocessJson(),
)

@Serializable
data class PackEntryEditJson(
    val key: String,
    val enabled: Boolean = true,
    val title: String? = null,
    val artist: String? = null,
    val levelId: String? = null,
    val charts: List<PackChartEditJson> = emptyList(),
)

@Serializable
data class PackChartEditJson(
    val ratingClass: Int,
    val enabled: Boolean = true,
    val difficulty: String? = null,
    val chartConstant: Float? = null,
    val charter: String? = null,
    val illustrator: String? = null,
)

@Serializable
data class PackConvertJson(
    val outputPath: String,
    val sizeBytes: Long,
    val convertedCount: Int,
    val skippedCount: Int,
    val workspaceCleaned: Boolean = false,
    val validation: PackValidationJson? = null,
)

@Serializable
data class PackValidationJson(
    val valid: Boolean,
    val packEntryCount: Int,
    val levelEntryCount: Int,
    val packName: String? = null,
    val packIdentifier: String? = null,
    val packImageExists: Boolean = false,
    val levelIdentifiersMatch: Boolean = false,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
)

private data class ParsedPackIdentifier(
    val publisherId: String?,
    val packId: String?,
    val warning: String? = null,
)

@Serializable
data class CharacterScanJson(
    val sourcePath: String,
    val sourceKind: String,
    val inputType: String,
    val workspacePath: String,
    val publisherId: String = "etoilebridge",
    val characterId: String = "character",
    val directory: String = "character",
    val identifier: String = "etoilebridge.character",
    val outputFileName: String = "etoilebridge.character.arcpkg",
    val defaultName: String = "Character",
    val zhCnName: String? = null,
    val imagePath: String? = null,
    val iconPath: String? = null,
    val image: ResourceJson? = null,
    val icon: ResourceJson? = null,
    val imageHasAlpha: Boolean? = null,
    val x: Float = 300f,
    val y: Float = 100f,
    val scale: Float = 0.7f,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
)

@Serializable
data class CharacterSaveRequestJson(
    val publisherId: String = "etoilebridge",
    val characterId: String = "character",
    val directory: String? = null,
    val outputFileName: String? = null,
    val defaultName: String = "Character",
    val zhCnName: String? = null,
    val imagePath: String? = null,
    val iconPath: String? = null,
    val imageFileName: String? = null,
    val iconFileName: String? = null,
    val x: Float = 300f,
    val y: Float = 100f,
    val scale: Float = 0.7f,
)

@Serializable
data class CharacterConvertJson(
    val outputPath: String,
    val sizeBytes: Long,
    val identifier: String,
    val directory: String,
    val workspaceCleaned: Boolean = false,
    val validation: CharacterValidationJson,
)

@Serializable
data class CharacterValidationJson(
    val valid: Boolean,
    val characterEntryCount: Int,
    val identifier: String? = null,
    val directory: String? = null,
    val defaultName: String? = null,
    val imageExists: Boolean = false,
    val iconExists: Boolean = false,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
)

@Serializable
data class IconCropRequestJson(
    val imagePath: String,
    val outputPath: String,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val cropSize: Float = 0.5f,
    val outputSize: Int = 256,
)

@Serializable
data class CharacterIconJson(
    val iconPath: String,
    val icon: ResourceJson,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
)

private data class CommandResult(
    val data: JsonElement,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
)

fun main(args: Array<String>) {
    val result = runCatching {
        val parsed = parseArgs(args.toList())
        when (parsed.command) {
            "smoke-test" -> CommandResult(
                JsonOut.encodeToJsonElement(mapOf("status" to "ok")),
                logs = listOf("converter-worker smoke ok"),
            )
            "scan-single" -> scanSingle(parsed.options)
            "convert-single" -> convertSingle(parsed.options)
            "scan-official-pack" -> scanOfficialPack(parsed.options)
            "scan-arcpkg-bundle" -> scanArcpkgBundle(parsed.options)
            "scan-existing-pack" -> scanExistingPack(parsed.options)
            "save-official-pack" -> saveOfficialPack(parsed.options)
            "save-arcpkg-bundle" -> saveArcpkgBundle(parsed.options)
            "save-existing-pack" -> saveExistingPack(parsed.options)
            "scan-character-image" -> scanCharacterImage(parsed.options)
            "scan-character-arcpkg" -> scanCharacterArcpkg(parsed.options)
            "generate-character-icon" -> generateCharacterIcon(parsed.options)
            "save-character-package" -> saveCharacterPackage(parsed.options)
            else -> error("Unknown command: ${parsed.command}")
        }
    }

    val envelope = result.fold(
        onSuccess = { value ->
            WorkerEnvelope(
                ok = true,
                data = value.data,
                warnings = value.warnings,
                logs = value.logs,
            )
        },
        onFailure = { WorkerEnvelope(ok = false, error = it.message ?: it.javaClass.simpleName) },
    )
    val output = JsonOut.encodeToString(envelope)
    System.out.write(output.toByteArray(Charsets.UTF_8))
    System.out.write('\n'.code)
}

private data class ParsedArgs(val command: String, val options: Map<String, String>)

private fun parseArgs(args: List<String>): ParsedArgs {
    require(args.isNotEmpty()) { "Missing command." }
    val options = mutableMapOf<String, String>()
    var index = 1
    while (index < args.size) {
        val key = args[index]
        require(key.startsWith("--")) { "Invalid argument: $key" }
        val value = args.getOrNull(index + 1) ?: error("Missing value for $key")
        options[key.removePrefix("--")] = value
        index += 2
    }
    return ParsedArgs(args.first(), options)
}

private fun scanCharacterImage(options: Map<String, String>): CommandResult {
    val source = options.requiredFile("source")
    val session = options.requiredPath("session")
    val inputRoot = prepareInputWorkspace(source, session.resolve("input"))
    val image = inputRoot.walkTopDown()
        .firstOrNull { it.isFile && it.extension.lowercase(Locale.ROOT) in setOf("png", "jpg", "jpeg", "webp") }
        ?: error("Character image not found after import.")
    val characterId = image.nameWithoutExtension.safeUiId("character")
    val defaultName = source.nameWithoutExtension.ifBlank { characterId }
    val warnings = mutableListOf<String>()
    val scan = CharacterScanJson(
        sourcePath = source.absolutePath,
        sourceKind = "image",
        inputType = source.inputTypeLabel(),
        workspacePath = inputRoot.absolutePath,
        characterId = characterId,
        directory = characterId,
        identifier = "etoilebridge.$characterId",
        outputFileName = "etoilebridge.$characterId.arcpkg",
        defaultName = defaultName,
        imagePath = image.name,
        iconPath = null,
        image = image.toResourceJson("Imported character image"),
        icon = null,
        imageHasAlpha = image.hasAlphaChannel(),
        warnings = warnings,
        logs = listOf("Imported character image: ${source.absolutePath}", "Workspace: ${inputRoot.absolutePath}"),
    )
    return CommandResult(JsonOut.encodeToJsonElement(scan), warnings, scan.logs)
}

private fun scanCharacterArcpkg(options: Map<String, String>): CommandResult {
    val source = options.requiredFile("source")
    val session = options.requiredPath("session")
    val inputRoot = prepareInputWorkspace(source, session.resolve("input"))
    val loaded = CharacterPackageLoader().loadExistingCharacterPackage(inputRoot)
    val warnings = (loaded.warnings + loaded.errors).toMutableList()
    val entry = loaded.entry
    val character = loaded.character
    val (publisherId, characterId) = splitCharacterIdentifier(entry?.identifier.orEmpty(), "etoilebridge", entry?.directory ?: "character")
    val defaultName = character?.name?.get("default") ?: characterId
    val zhCnName = character?.name?.get("zh-cn") ?: character?.name?.get("zh-CN")
    val image = loaded.imageFile
    val icon = loaded.iconFile
    val scan = CharacterScanJson(
        sourcePath = source.absolutePath,
        sourceKind = "arcpkg",
        inputType = source.inputTypeLabel(),
        workspacePath = inputRoot.absolutePath,
        publisherId = publisherId,
        characterId = characterId.safeUiId("character"),
        directory = (entry?.directory ?: characterId).safeUiId("character"),
        identifier = entry?.identifier ?: "$publisherId.$characterId",
        outputFileName = "$publisherId.${characterId.safeUiId("character")}.arcpkg",
        defaultName = defaultName,
        zhCnName = zhCnName,
        imagePath = character?.imagePath ?: image?.name,
        iconPath = character?.iconPath ?: icon?.name,
        image = image.toResourceJson("Character image"),
        icon = icon.toResourceJson("Character icon"),
        imageHasAlpha = image.hasAlphaChannel(),
        x = character?.x ?: 300f,
        y = character?.y ?: 100f,
        scale = character?.scale ?: 0.7f,
        warnings = warnings,
        logs = listOf("Loaded character arcpkg: ${source.absolutePath}", "Workspace: ${inputRoot.absolutePath}"),
    )
    return CommandResult(JsonOut.encodeToJsonElement(scan), warnings, scan.logs)
}

private fun generateCharacterIcon(options: Map<String, String>): CommandResult {
    val request = options.iconCropRequest()
    val source = File(request.imagePath).takeIf { it.isFile } ?: error("Character image not found.")
    val output = File(request.outputPath)
    output.parentFile?.mkdirs()
    val image = ImageIO.read(source) ?: error("Unable to decode character image.")
    val minSide = minOf(image.width, image.height)
    val cropSide = (minSide * request.cropSize.coerceIn(0.05f, 1f)).toInt().coerceIn(1, minSide)
    val centerX = (image.width * request.centerX.coerceIn(0f, 1f)).toInt()
    val centerY = (image.height * request.centerY.coerceIn(0f, 1f)).toInt()
    val left = (centerX - cropSide / 2).coerceIn(0, image.width - cropSide)
    val top = (centerY - cropSide / 2).coerceIn(0, image.height - cropSide)
    val crop = image.getSubimage(left, top, cropSide, cropSide)
    val result = BufferedImage(request.outputSize, request.outputSize, BufferedImage.TYPE_INT_ARGB)
    val graphics = result.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.drawImage(crop, 0, 0, request.outputSize, request.outputSize, null)
    } finally {
        graphics.dispose()
    }
    ImageIO.write(result, "png", output)
    val json = CharacterIconJson(
        iconPath = output.absolutePath,
        icon = output.toResourceJson("Generated character icon") ?: error("Generated icon missing."),
        logs = listOf("Generated character icon: ${output.absolutePath}"),
    )
    return CommandResult(JsonOut.encodeToJsonElement(json), json.warnings, json.logs)
}

private fun saveCharacterPackage(options: Map<String, String>): CommandResult {
    val output = options.requiredFile("output")
    val request = options.characterRequest()
    val image = request.imagePath?.let(::File)?.takeIf { it.isFile } ?: error("Character image is missing.")
    val icon = request.iconPath?.let(::File)?.takeIf { it.isFile } ?: error("Character icon is missing.")
    val result = CharacterPackageBuilder().build(
        CharacterPackageInput(
            imageFile = image,
            iconFile = icon,
            outputFile = output,
            options = CharacterPackageOptions(
                publisherId = request.publisherId,
                characterId = request.characterId,
                directory = request.directory,
                defaultName = request.defaultName,
                zhCnName = request.zhCnName?.takeIf { it.isNotBlank() },
                imageFileName = request.imageFileName?.takeIf { it.isNotBlank() } ?: image.name,
                iconFileName = request.iconFileName?.takeIf { it.isNotBlank() } ?: icon.name,
                x = request.x,
                y = request.y,
                scale = request.scale,
            ),
        ),
    )
    return when (result) {
        is CharacterPackageResult.Success -> {
            val warnings = result.warnings + result.validation.warnings
            val logs = result.logs + result.validation.summaryLines() + result.validation.logs
            CommandResult(
                data = JsonOut.encodeToJsonElement(
                    CharacterConvertJson(
                        outputPath = result.outputFile.absolutePath,
                        sizeBytes = result.outputFile.length(),
                        identifier = result.identifier,
                        directory = result.directory,
                        validation = result.validation.toJson(),
                    ),
                ),
                warnings = warnings,
                logs = logs,
            )
        }
        is CharacterPackageResult.Failed -> error(result.message)
    }
}

private fun scanSingle(options: Map<String, String>): CommandResult {
    val source = options.requiredFile("source")
    val session = options.requiredPath("session")
    val inputRoot = prepareInputWorkspace(source, session.resolve("input"))
    findProjectFile(inputRoot)?.let { projectFile ->
        return scanArcCreateProject(source, inputRoot, projectFile)
    }
    val scan = InputScanner().scan(inputRoot)
    val warnings = scan.ignoredAffFiles
        .map { "Ignored non-standard AFF file: ${it.relativeToOrSelf(inputRoot)}" }
        .toMutableList()
    val rootSonglistFile = scan.songlistFile
    val rootSonglist = rootSonglistFile?.parseSonglistOrNull(warnings)
    val target = resolveSingleScanTarget(scan, rootSonglist)
        ?: return scanJson(
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
    val firstDifficulty = resolvedSong?.difficulties?.firstOrNull()
    val difficultySummary = metadata?.difficulties?.joinToString { diff ->
        val constant = diff.chartConstant?.let { "%.1f".format(it) }.orEmpty()
        "${diff.ratingClass}:${diff.difficulty ?: "?"}${constant.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}"
    }
    val charts = metadata?.difficulties.orEmpty().map { diff ->
        ChartJson(
            ratingClass = diff.ratingClass,
            difficulty = diff.difficulty,
            chartConstant = diff.chartConstant,
            rating = diff.rating,
            ratingPlus = diff.ratingPlus,
            charter = diff.chartDesigner,
            illustrator = diff.jacketDesigner,
            alias = diff.alias,
            affPath = target.affFiles[diff.ratingClass]?.absolutePath,
            affName = target.affFiles[diff.ratingClass]?.name,
        )
    }.ifEmpty {
        target.affFiles.toSortedMap().map { (ratingClass, file) ->
            ChartJson(ratingClass = ratingClass, affPath = file.absolutePath, affName = file.name)
        }
    }

    return scanJson(
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
                AffJson(ratingClass, file.absolutePath, file.name, adopted = true)
            },
            warnings = warnings,
            logs = listOf("Scanned ${source.absolutePath}", "Workspace: ${inputRoot.absolutePath}"),
        ),
        warnings,
    )
}

private fun scanArcCreateProject(source: File, inputRoot: File, projectFile: File): CommandResult {
    val warnings = mutableListOf<String>()
    val project = runCatching {
        ArcCreateProjectReader.read(projectFile)
    }.getOrElse { error("project.arcproj parse failed: ${it.message ?: it.javaClass.simpleName}") }
    val projectDir = projectFile.parentFile ?: inputRoot
    val charts = project.charts.mapIndexed { index, chart ->
        val chartFile = projectDir.resolve(chart.chartPath).takeIf { it.isFile }
        val ratingClass = chart.chartPath.substringBeforeLast('.').toIntOrNull() ?: index
        ChartJson(
            ratingClass = ratingClass,
            difficulty = chart.difficulty,
            chartConstant = chart.chartConstant,
            charter = chart.charter,
            illustrator = chart.illustrator,
            alias = chart.alias,
            affPath = chartFile?.absolutePath,
            affName = chartFile?.name ?: chart.chartPath,
        )
    }
    val first = project.charts.firstOrNull()
    val firstBaseDir = projectDir
    val difficultySummary = charts.joinToString { chart ->
        val constant = chart.chartConstant?.let { "%.1f".format(it) }.orEmpty()
        "${chart.ratingClass}:${chart.difficulty ?: "?"}${constant.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}"
    }
    val sourceKind = if (source.extension.equals("arcpkg", ignoreCase = true)) "arccreate-arcpkg" else "arccreate-project"
    return scanJson(
        SingleScanJson(
            sourcePath = source.absolutePath,
            sourceKind = sourceKind,
            inputType = source.inputTypeLabel(),
            workspacePath = inputRoot.absolutePath,
            songId = projectDir.name,
            title = first?.title,
            artist = first?.composer,
            bpmText = first?.bpmText,
            bpmBase = first?.baseBpm,
            difficulty = difficultySummary.takeIf { it.isNotBlank() },
            charts = charts,
            audio = first?.audioPath?.let { firstBaseDir.resolve(it) }.toResourceJson("ArcCreate audio"),
            jacket = first?.jacketPath?.let { firstBaseDir.resolve(it) }.toResourceJson("ArcCreate jacket"),
            background = first?.backgroundPath?.let { firstBaseDir.resolve(it) }.toResourceJson("ArcCreate background"),
            affFiles = charts.mapNotNull { chart ->
                val path = chart.affPath ?: return@mapNotNull null
                AffJson(chart.ratingClass, path, chart.affName ?: File(path).name, adopted = true)
            },
            warnings = warnings,
            logs = listOf("Scanned ArcCreate project ${projectFile.absolutePath}", "Workspace: ${inputRoot.absolutePath}"),
        ),
        warnings,
    )
}

private fun convertSingle(options: Map<String, String>): CommandResult {
    val workspace = options.requiredFile("workspace")
    val output = options.requiredFile("output")
    val request = options.jsonOption("request-json")?.let { JsonOut.decodeFromString<ConvertRequestJson>(it) }
        ?: ConvertRequestJson(
            publisherId = options["publisher-id"] ?: "etoilebridge",
            levelId = options["level-id"],
            title = options["title"],
            artist = options["artist"],
        )
    output.parentFile?.mkdirs()
    val result = EtoileBridgeConverter.convert(
        input = ConvertInput(
            workspaceDir = workspace,
            outputFile = output,
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
                        alias = chart.alias?.takeIf { it.isNotBlank() },
                        difficulty = chart.difficulty?.takeIf { it.isNotBlank() },
                        chartConstant = chart.chartConstant,
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
            appearanceOptions = AppearanceOptions(
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
        is ConvertResult.Success -> CommandResult(
            data = JsonOut.encodeToJsonElement(
                ConvertJson(result.outputFile.absolutePath, result.songId, result.outputFile.length()),
            ),
            logs = listOf("Saved ${result.outputFile.absolutePath}"),
        )
        is ConvertResult.NeedMetadata -> error("Metadata is incomplete: ${result.missingMetadata.requiredFields.joinToString()}")
        is ConvertResult.UnsupportedPackStructure -> error(result.message)
        is ConvertResult.Failed -> error(result.message)
    }
}

private fun scanOfficialPack(options: Map<String, String>): CommandResult {
    val source = options.requiredFile("source")
    val session = options.requiredPath("session")
    val inputRoot = prepareInputWorkspace(source, session.resolve("input"))
    val scan = PackBundleScanner().scanOfficialPack(inputRoot)
    val json = scan.toPackScanJson(
        mode = "official",
        sourcePath = source.absolutePath,
        workspacePath = inputRoot.absolutePath,
        previewRoot = session.resolve("preview").toFile(),
    )
    return CommandResult(
        data = JsonOut.encodeToJsonElement(json),
        warnings = json.warnings,
        logs = json.logs,
    )
}

private fun scanArcpkgBundle(options: Map<String, String>): CommandResult {
    val session = options.requiredPath("session")
    val sources = options.sourceFiles()
    val inputRoot = prepareArcpkgInputWorkspace(sources, session.resolve("input"))
    val scan = ArcpkgBundleMerger().scan(inputRoot)
    val json = scan.toPackScanJson(
        mode = "bundle",
        sourcePath = sources.joinToString(File.pathSeparator) { it.absolutePath },
        workspacePath = inputRoot.absolutePath,
        previewRoot = session.resolve("preview").toFile(),
    )
    return CommandResult(JsonOut.encodeToJsonElement(json), json.warnings, json.logs)
}

private fun scanExistingPack(options: Map<String, String>): CommandResult {
    val basePack = options.requiredFile("base")
    val session = options.requiredPath("session")
    val addSources = options.sourceFiles(optional = true)
    val addInput = addSources.takeIf { it.isNotEmpty() }?.let {
        prepareArcpkgInputWorkspace(it, session.resolve("add-input"))
    }
    val scan = ArcpkgBundleMerger().scanExistingPack(basePack, addInput)
    val json = scan.toPackScanJson(
        basePackPath = basePack.absolutePath,
        addWorkspacePath = addInput?.absolutePath,
        previewRoot = session.resolve("preview").toFile(),
    )
    return CommandResult(JsonOut.encodeToJsonElement(json), json.warnings, json.logs)
}

private fun saveOfficialPack(options: Map<String, String>): CommandResult {
    val workspace = options.requiredFile("workspace")
    val output = options.requiredFile("output")
    val request = options.packRequest()
    val result = PackBundleConverter().convertOfficialPack(
        BundleInput(
            workspaceDir = workspace,
            outputFile = output,
            options = request.toBundleOptions(output),
        ),
    )
    return packConvertResult(result, output)
}

private fun saveArcpkgBundle(options: Map<String, String>): CommandResult {
    val workspace = options.requiredFile("workspace")
    val output = options.requiredFile("output")
    val request = options.packRequest()
    val result = ArcpkgBundleMerger().merge(
        input = workspace,
        outputFile = output,
        options = request.toBundleOptions(output),
    )
    return packConvertResult(result, output)
}

private fun saveExistingPack(options: Map<String, String>): CommandResult {
    val basePack = options.requiredFile("base")
    val output = options.requiredFile("output")
    val addInput = options["add-workspace"]?.let(::File)?.takeIf { it.exists() }
    val request = options.packRequest()
    val result = ArcpkgBundleMerger().editExistingPack(
        basePack = basePack,
        addInput = addInput,
        outputFile = output,
        options = request.toBundleOptions(output),
    )
    return packConvertResult(result, output)
}

private fun packConvertResult(result: BundleConvertResult, requestedOutput: File): CommandResult =
    when (result) {
        is BundleConvertResult.Success -> {
            val validation = BundleOutputValidator().validateBundleArcpkg(result.outputFile)
            val warnings = result.warnings + validation.warnings
            val logs = result.logs + validation.summaryLines() + validation.logs
            CommandResult(
                data = JsonOut.encodeToJsonElement(
                    PackConvertJson(
                        outputPath = result.outputFile.absolutePath,
                        sizeBytes = result.outputFile.length(),
                        convertedCount = result.convertedCount,
                        skippedCount = result.skippedCount,
                        validation = validation.toJson(),
                    ),
                ),
                warnings = warnings,
                logs = logs,
            )
        }
        is BundleConvertResult.Failed -> error(result.message)
    }

private fun BundleScanResult.toPackScanJson(
    mode: String,
    sourcePath: String,
    workspacePath: String,
    previewRoot: File,
): PackScanJson =
    PackScanJson(
        mode = mode,
        sourcePath = sourcePath,
        workspacePath = workspacePath,
        packName = packNameCandidate,
        packId = packIdCandidate,
        packImage = packImageFile.toResourceJson("Detected pack image"),
        entries = entries.map { it.toPackLevelJson() },
        existingLevelCount = 0,
        addedLevelCount = entries.size,
        finalLevelCount = entries.size,
        warnings = warnings,
        logs = logs,
    )

private fun ArcpkgBundleScanResult.toPackScanJson(
    mode: String,
    sourcePath: String,
    workspacePath: String,
    previewRoot: File,
): PackScanJson {
    val imageFile = packImageCandidate?.extractPackImage(previewRoot)
    val firstPack = packEntries.firstOrNull()
    val parsed = parsePackIdentifier(firstPack?.identifier)
    return PackScanJson(
        mode = mode,
        sourcePath = sourcePath,
        workspacePath = workspacePath,
        publisherId = parsed.publisherId,
        packName = packNameCandidate,
        packId = parsed.packId ?: firstPack?.directory,
        packIdentifier = firstPack?.identifier,
        packDirectory = firstPack?.directory,
        packImage = imageFile.toResourceJson("Source pack image"),
        entries = levelEntries.map { it.toPackLevelJson(previewRoot) },
        sourceReports = sourceFiles.map { it.toJson() },
        existingLevelCount = 0,
        addedLevelCount = levelEntries.size,
        finalLevelCount = levelEntries.size,
        warnings = warnings,
        logs = logs,
    )
}

private fun ExistingPackEditScanResult.toPackScanJson(
    basePackPath: String,
    addWorkspacePath: String?,
    previewRoot: File,
): PackScanJson {
    val imageFile = packImageCandidate?.extractPackImage(previewRoot)
    val parsed = parsePackIdentifier(basePackEntry?.identifier)
    val parseWarnings = listOfNotNull(parsed.warning)
    return PackScanJson(
        mode = "existing",
        sourcePath = basePackPath,
        basePackPath = basePackPath,
        addWorkspacePath = addWorkspacePath,
        publisherId = parsed.publisherId,
        packName = packNameCandidate,
        packId = parsed.packId ?: packIdCandidate,
        packIdentifier = basePackEntry?.identifier,
        packDirectory = basePackEntry?.directory,
        packImage = imageFile.toResourceJson("Existing pack image"),
        entries = (existingLevels + addedLevels).map { it.toPackLevelJson(previewRoot) },
        sourceReports = sourceFiles.map { it.toJson() },
        existingLevelCount = existingLevelCount,
        addedLevelCount = addedLevelCount,
        finalLevelCount = finalLevelCount,
        renamedConflictCount = renamedConflictCount,
        warnings = warnings + parseWarnings,
        logs = logs,
    )
}

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

private fun BundleEntry.toPackLevelJson(): PackLevelJson =
    PackLevelJson(
        key = key,
        songId = songId,
        title = title,
        artist = artist,
        levelId = songId,
        difficultySummary = difficultySummary,
        chartCount = charts.size,
        resourceStatus = resourceStatus(),
        jacket = jacketFile.toResourceJson("Level jacket"),
        background = backgroundFile.toResourceJson("Level background"),
        enabled = canConvert,
        canConvert = canConvert,
        charts = charts.map { it.toJson() },
        warnings = warnings,
        failureReason = failureReason,
    )

private fun ArcpkgLevelEntry.toPackLevelJson(previewRoot: File): PackLevelJson {
    val jacketFile = extractArcpkgLevelPreviewImage(previewRoot, "jacketPath")
    val backgroundFile = extractArcpkgLevelPreviewImage(previewRoot, "backgroundPath")
    return PackLevelJson(
        key = key,
        sourceFile = sourceFile.absolutePath,
        directory = directory,
        identifier = identifier,
        songId = directory,
        title = title,
        artist = artist,
        levelId = identifier.substringAfterLast('.'),
        difficultySummary = difficultySummary,
        chartCount = charts.size,
        resourceStatus = if (failureReason == null) "project.arcproj ok" else "metadata warning",
        jacket = jacketFile.toResourceJson("ArcCreate level jacket"),
        background = backgroundFile.toResourceJson("ArcCreate level background"),
        enabled = failureReason == null,
        canConvert = failureReason == null,
        charts = charts.map { it.toJson() },
        warnings = warnings,
        failureReason = failureReason,
    )
}

private fun BundleChartEntry.toJson(): PackChartJson =
    PackChartJson(
        ratingClass = ratingClass,
        chartPath = chartPath,
        difficulty = difficulty,
        chartConstant = chartConstant,
        charter = charter,
        illustrator = illustrator,
        enabled = enabled,
        canConvert = canConvert,
        warnings = warnings,
        failureReason = failureReason,
    )

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

private fun String.trimYamlScalar(): String =
    trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .trim()

private fun zipPath(directory: String, childPath: String): String =
    listOf(directory.trim('/').replace('\\', '/'), childPath.trim('/').replace('\\', '/'))
        .filter { it.isNotBlank() }
        .joinToString("/")

private fun ArcpkgSourceReport.toJson(): PackSourceReportJson =
    PackSourceReportJson(
        sourceFile = sourceFile.absolutePath,
        readable = readable,
        levelCount = levelCount,
        packEntryCount = packEntryCount,
        packName = packName,
        packImagePath = packImagePath,
        packImageExists = packImageExists,
        packLevelIdentifierCount = packLevelIdentifierCount,
        packMatchesIndexLevels = packMatchesIndexLevels,
        failureReason = failureReason,
    )

private fun BundleEntry.resourceStatus(): String {
    val missing = buildList {
        if (audioFile == null) add("audio")
        if (jacketFile == null) add("jacket")
        if (charts.isEmpty()) add("charts")
    }
    return if (missing.isEmpty()) "ok" else "missing ${missing.joinToString()}"
}

private fun PackSaveRequestJson.toBundleOptions(output: File): BundleOptions =
    BundleOptions(
        publisherId = publisherId.ifBlank { "etoilebridge" },
        outputFileName = outputFileName?.takeIf { it.isNotBlank() } ?: output.name,
        packName = packName?.takeIf { it.isNotBlank() },
        packId = packId?.takeIf { it.isNotBlank() },
        packIdentifier = packIdentifier?.takeIf { it.isNotBlank() },
        packImageFile = packImagePath?.toExistingFileOrNull(),
        includeOnlyConvertible = true,
        convertOptions = ConvertOptions(
            enableDeleteDesignantLine = preprocess.deleteDesignantLine,
            enableFixZeroDurationArcTap = preprocess.fixZeroDurationArcTap,
            enableFixReversedArcTime = preprocess.fixReversedArcTime,
            enableExpandArcResolution = preprocess.expandArcResolution,
            keepWorkspaceOnFailure = true,
            cleanWorkspaceOnSuccess = true,
        ),
        appearanceOptions = AppearanceOptions(
            particle = appearance.particle.toEnumOrDefault(ArcCreateParticle.INHERIT),
            accent = appearance.accent.toEnumOrDefault(ArcCreateAccent.INHERIT),
            track = appearance.track.toEnumOrDefault(ArcCreateTrack.INHERIT),
            singleLine = appearance.singleLine.toEnumOrDefault(ArcCreateSingleLine.NONE),
        ),
        entryOverrides = entries.associate { entry ->
            entry.key to BundleEntryOverride(
                enabled = entry.enabled,
                title = entry.title?.takeIf { it.isNotBlank() },
                artist = entry.artist?.takeIf { it.isNotBlank() },
                levelId = entry.levelId?.takeIf { it.isNotBlank() },
                chartOverrides = entry.charts.associate { chart ->
                    chart.ratingClass to BundleChartOverride(
                        enabled = chart.enabled,
                        difficulty = chart.difficulty?.takeIf { it.isNotBlank() },
                        chartConstant = chart.chartConstant,
                        charter = chart.charter?.takeIf { it.isNotBlank() },
                        illustrator = chart.illustrator?.takeIf { it.isNotBlank() },
                    )
                },
            )
        },
    )

private fun BundleValidationReport.toJson(): PackValidationJson =
    PackValidationJson(
        valid = valid,
        packEntryCount = packEntryCount,
        levelEntryCount = levelEntryCount,
        packName = packName,
        packIdentifier = packIdentifier,
        packImageExists = packImageExists,
        levelIdentifiersMatch = levelIdentifiersMatch,
        errors = errors,
        warnings = warnings,
        logs = logs,
    )

private fun scanJson(scan: SingleScanJson, warnings: List<String>): CommandResult =
    CommandResult(
        data = JsonOut.encodeToJsonElement(scan),
        warnings = warnings,
        logs = scan.logs,
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
            val songId = requestedFromSonglist ?: scanned.candidateSongIds.singleOrNull() ?: scanned.candidateSongIds.firstOrNull()
            val songDir = songId?.let { scanned.findSongDirectory(it)?.second } ?: return null
            SingleScanTarget(songId, songDir, InputScanner.findAffFiles(songDir))
        }
        InputKind.Unknown -> SingleScanTarget(requestedFromSonglist, scanned.workspaceDir, scanned.rootAffFiles)
            .takeIf { it.affFiles.isNotEmpty() }
    }
}

private fun prepareInputWorkspace(source: File, workspace: Path): File {
    if (workspace.exists()) workspace.toFile().deleteRecursively()
    workspace.createDirectories()
    return when {
        source.isDirectory -> {
            copyDirectory(source.toPath(), workspace)
            workspace.toFile()
        }
        source.extension.equals("zip", ignoreCase = true) || source.extension.equals("arcpkg", ignoreCase = true) -> {
            unzip(source, workspace)
            workspace.toFile()
        }
        else -> {
            Files.copy(source.toPath(), workspace.resolve(source.name), StandardCopyOption.REPLACE_EXISTING)
            workspace.toFile()
        }
    }
}

private fun prepareArcpkgInputWorkspace(sources: List<File>, workspace: Path): File {
    if (workspace.exists()) workspace.toFile().deleteRecursively()
    workspace.createDirectories()
    val usedNames = mutableSetOf<String>()
    sources.flatMap { source ->
        when {
            source.isFile && source.extension.equals("arcpkg", ignoreCase = true) -> listOf(source)
            source.isDirectory -> source.walkTopDown()
                .filter { it.isFile && it.extension.equals("arcpkg", ignoreCase = true) }
                .toList()
                .sortedBy { it.name.lowercase(Locale.ROOT) }
            else -> emptyList()
        }
    }.forEach { file ->
        val targetName = uniqueFileName(file.name, usedNames)
        Files.copy(file.toPath(), workspace.resolve(targetName), StandardCopyOption.REPLACE_EXISTING)
    }
    return workspace.toFile()
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

private fun unzip(source: File, target: Path) {
    ZipFile(source).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            val out = target.resolve(entry.name).normalize()
            require(out.startsWith(target.normalize())) { "Unsafe zip entry: ${entry.name}" }
            if (entry.isDirectory) {
                out.createDirectories()
            } else {
                out.parent?.createDirectories()
                zip.getInputStream(entry).use { input ->
                    Files.copy(input, out, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}

private fun copyDirectory(source: Path, target: Path) {
    Files.walk(source).use { stream ->
        stream.forEach { path ->
            val relative = source.relativize(path)
            val out = target.resolve(relative).normalize()
            require(out.startsWith(target.normalize())) { "Refusing to copy outside workspace." }
            if (path.isDirectory()) {
                out.createDirectories()
            } else {
                out.parent?.createDirectories()
                Files.copy(path, out, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

private fun findProjectFile(root: File): File? =
    root.walkTopDown().firstOrNull { it.isFile && it.name == "project.arcproj" }

private fun Map<String, String>.requiredFile(key: String): File =
    File(this[key] ?: error("Missing --$key"))

private fun Map<String, String>.requiredPath(key: String): Path =
    File(this[key] ?: error("Missing --$key")).toPath()

private fun Map<String, String>.sourceFiles(optional: Boolean = false): List<File> {
    val encoded = jsonOption("sources-json")
    if (!encoded.isNullOrBlank()) {
        return JsonOut.decodeFromString<List<String>>(encoded).map(::File)
    }
    val source = this["source"]?.let(::File)
    if (source != null) return listOf(source)
    if (optional) return emptyList()
    error("Missing --source or --sources-json")
}

private fun Map<String, String>.packRequest(): PackSaveRequestJson =
    jsonOption("request-json")?.let { JsonOut.decodeFromString<PackSaveRequestJson>(it) }
        ?: PackSaveRequestJson(mode = this["mode"] ?: "bundle")

private fun Map<String, String>.characterRequest(): CharacterSaveRequestJson =
    jsonOption("request-json")?.let { JsonOut.decodeFromString<CharacterSaveRequestJson>(it) }
        ?: CharacterSaveRequestJson()

private fun Map<String, String>.iconCropRequest(): IconCropRequestJson =
    jsonOption("request-json")?.let { JsonOut.decodeFromString<IconCropRequestJson>(it) }
        ?: error("Missing --request-json")

private fun Map<String, String>.jsonOption(name: String): String? =
    this[name] ?: this["$name-file"]?.let { File(it).readText(Charsets.UTF_8).removePrefix("\uFEFF") }

private fun File.parseSonglistOrNull(warnings: MutableList<String>): Songlist? =
    runCatching { SonglistParser().parse(this) }
        .onFailure { warnings += "Songlist/slst parse failed for $name: ${it.message}" }
        .getOrNull()

private fun ScannedInput.findSongDirectory(songId: String): Pair<String, File>? {
    songDirectories[songId]?.let { return songId to it }
    return songDirectories.entries.firstOrNull { it.key.equals(songId, ignoreCase = true) }
        ?.let { it.key to it.value }
}

private fun SonglistSong.displayTitle(): String? =
    titleLocalized["en"] ?: titleLocalized["ja"] ?: titleLocalized["zh-Hans"] ?: titleLocalized["zh-Hant"] ?: titleLocalized.values.firstOrNull() ?: title

private fun File.relativeToOrSelf(base: File): String =
    runCatching { relativeTo(base).path }.getOrElse { absolutePath }

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

private fun CharacterValidationReport.toJson(): CharacterValidationJson =
    CharacterValidationJson(
        valid = valid,
        characterEntryCount = characterEntryCount,
        identifier = identifier,
        directory = directory,
        defaultName = defaultName,
        imageExists = imageExists,
        iconExists = iconExists,
        errors = errors,
        warnings = warnings,
        logs = logs,
    )

private fun File?.hasAlphaChannel(): Boolean? {
    val file = this?.takeIf { it.isFile } ?: return null
    return runCatching {
        ImageIO.read(file)?.colorModel?.hasAlpha()
    }.getOrNull()
}

private fun String.safeUiId(fallback: String): String =
    replace(Regex("""[^\w.-]+"""), "_")
        .trim('.', '_', '-')
        .ifBlank { fallback }

private fun File.inputTypeLabel(): String =
    when {
        isDirectory -> "Folder"
        extension.equals("zip", ignoreCase = true) -> "ZIP"
        else -> "File"
    }

private fun String.toExistingFileOrNull(): File? =
    takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }

private inline fun <reified T : Enum<T>> String.toEnumOrDefault(default: T): T =
    runCatching { enumValueOf<T>(uppercase()) }.getOrDefault(default)
