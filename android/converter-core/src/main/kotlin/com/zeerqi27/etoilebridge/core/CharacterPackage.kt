package com.zeerqi27.etoilebridge.core

import com.charleskorn.kaml.decodeFromStream
import com.charleskorn.kaml.encodeToStream
import com.zeerqi27.etoilebridge.core.etoile.ArcpkgEntryType
import com.zeerqi27.etoilebridge.core.etoile.CharacterInformation
import com.zeerqi27.etoilebridge.core.etoile.EtoileYaml
import com.zeerqi27.etoilebridge.core.etoile.ImportInformationEntry
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.builtins.ListSerializer

data class CharacterPackageInput(
    val imageFile: File,
    val iconFile: File,
    val outputFile: File,
    val options: CharacterPackageOptions = CharacterPackageOptions(),
)

data class CharacterPackageOptions(
    val publisherId: String = "etoilebridge",
    val characterId: String = "character",
    val directory: String? = null,
    val defaultName: String = "Character",
    val zhCnName: String? = null,
    val imageFileName: String? = null,
    val iconFileName: String? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val version: Int = 0,
)

sealed class CharacterPackageResult {
    data class Success(
        val outputFile: File,
        val identifier: String,
        val directory: String,
        val warnings: List<String>,
        val logs: List<String>,
        val validation: CharacterValidationReport,
    ) : CharacterPackageResult()

    data class Failed(
        val message: String,
        val cause: Throwable? = null,
        val warnings: List<String> = emptyList(),
        val logs: List<String> = emptyList(),
    ) : CharacterPackageResult()
}

data class CharacterPackageScanResult(
    val packages: List<CharacterPackageLoadResult>,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
)

data class CharacterPackageLoadResult(
    val sourceFile: File,
    val entry: ImportInformationEntry?,
    val character: CharacterInformation?,
    val directory: String? = entry?.directory,
    val identifier: String? = entry?.identifier,
    val settingsFile: String? = entry?.settingsFile,
    val imageFile: File? = null,
    val iconFile: File? = null,
    val imageZipPath: String? = null,
    val iconZipPath: String? = null,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
) {
    val isEditable: Boolean get() = entry != null && character != null && errors.none { it.contains("character.yml") }
}

data class CharacterValidationReport(
    val outputFile: File,
    val valid: Boolean,
    val characterEntryCount: Int = 0,
    val identifier: String? = null,
    val directory: String? = null,
    val defaultName: String? = null,
    val imageExists: Boolean = false,
    val iconExists: Boolean = false,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
) {
    fun summaryLines(): List<String> = buildList {
        add("Character validation: ${if (valid) "passed" else "failed"}")
        add("character entries: $characterEntryCount")
        identifier?.let { add("identifier: $it") }
        directory?.let { add("directory: $it") }
        defaultName?.let { add("name.default: $it") }
        add("image exists: $imageExists")
        add("icon exists: $iconExists")
    }
}

class CharacterPackageBuilder {
    fun build(input: CharacterPackageInput): CharacterPackageResult {
        val warnings = mutableListOf<String>()
        val logs = mutableListOf<String>()
        return try {
            if (!input.imageFile.isFile) return CharacterPackageResult.Failed("Character image not found: ${input.imageFile.absolutePath}")
            if (!input.iconFile.isFile) return CharacterPackageResult.Failed("Character icon not found: ${input.iconFile.absolutePath}")

            val options = input.options.normalized()
            val directory = options.directory?.safePackageId(options.characterId) ?: options.characterId.safePackageId("character")
            val identifier = "${options.publisherId.safePublisherId()}.${options.characterId.safePackageId("character")}"
            val imageName = options.imageFileName?.safeFileName("${options.characterId}.png") ?: "${options.characterId.safePackageId("character")}.png"
            val iconName = options.iconFileName?.safeFileName("${options.characterId}_icon.png") ?: "${options.characterId.safePackageId("character")}_icon.png"

            val character = CharacterInformation(
                name = buildMap {
                    put("default", options.defaultName.ifBlank { options.characterId })
                    options.zhCnName?.takeIf { it.isNotBlank() }?.let { put("zh-cn", it) }
                },
                imagePath = imageName,
                iconPath = iconName,
                x = options.x,
                y = options.y,
                scale = if (options.scale == 0f) 1f else options.scale,
            )
            val index = listOf(
                ImportInformationEntry(
                    directory = directory,
                    identifier = identifier,
                    settingsFile = CHARACTER_SETTINGS,
                    version = options.version,
                    type = ArcpkgEntryType.CHARACTER,
                )
            )

            input.outputFile.parentFile?.mkdirs()
            ZipOutputStream(input.outputFile.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("index.yml"))
                EtoileYaml.encodeToStream(ListSerializer(ImportInformationEntry.serializer()), index, zip)
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("$directory/$CHARACTER_SETTINGS"))
                EtoileYaml.encodeToStream(CharacterInformation.serializer(), character, zip)
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("$directory/$imageName"))
                input.imageFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("$directory/$iconName"))
                input.iconFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            logs += "Generated character package: ${input.outputFile.absolutePath}"
            logs += "Generated character entry: $identifier"
            val validation = CharacterPackageValidator().validateCharacterArcpkg(input.outputFile)
            warnings += validation.warnings
            logs += validation.summaryLines()
            logs += validation.logs
            if (!validation.valid) {
                return CharacterPackageResult.Failed(
                    message = "Character package validation failed.",
                    warnings = warnings + validation.errors,
                    logs = logs,
                )
            }
            CharacterPackageResult.Success(input.outputFile, identifier, directory, warnings, logs, validation)
        } catch (error: Exception) {
            CharacterPackageResult.Failed(error.message ?: "Character package build failed.", error, warnings, logs)
        }
    }

    private fun CharacterPackageOptions.normalized(): CharacterPackageOptions =
        copy(
            publisherId = publisherId.safePublisherId(),
            characterId = characterId.safePackageId("character"),
            directory = directory?.safePackageId(characterId.safePackageId("character")),
        )
}

class CharacterPackageLoader {
    fun loadExistingCharacterPackage(input: File): CharacterPackageLoadResult =
        when {
            input.isFile -> loadFromArcpkg(input)
            input.isDirectory -> loadFromDirectory(input)
            else -> CharacterPackageLoadResult(input, null, null, errors = listOf("Input does not exist."))
        }

    private fun loadFromDirectory(root: File): CharacterPackageLoadResult {
        val indexFile = root.resolve("index.yml").takeIf { it.isFile } ?: root.resolve("index.yaml").takeIf { it.isFile }
            ?: return CharacterPackageLoadResult(root, null, null, errors = listOf("index.yml not found."))
        val entries = runCatching {
            indexFile.inputStream().use { EtoileYaml.decodeFromStream(ListSerializer(ImportInformationEntry.serializer()), it) }
        }.getOrElse { return CharacterPackageLoadResult(root, null, null, errors = listOf("Unable to parse index.yml: ${it.message}")) }
        val characters = entries.filter { it.type == ArcpkgEntryType.CHARACTER }
        if (characters.isEmpty()) return CharacterPackageLoadResult(root, null, null, errors = listOf("No type: character entry found."))
        val warnings = mutableListOf<String>()
        if (characters.size > 1) warnings += "Multiple character entries found; using the first."
        val entry = characters.first()
        val directory = root.resolve(entry.directory)
        val settings = directory.resolve(entry.settingsFile)
        if (!settings.isFile) {
            return CharacterPackageLoadResult(root, entry, null, warnings = warnings, errors = listOf("Missing character.yml: ${entry.directory}/${entry.settingsFile}"))
        }
        val character = runCatching {
            settings.inputStream().use { EtoileYaml.decodeFromStream(CharacterInformation.serializer(), it) }
        }.getOrElse {
            return CharacterPackageLoadResult(root, entry, null, warnings = warnings, errors = listOf("Unable to parse character.yml: ${it.message}"))
        }
        val image = directory.resolve(character.imagePath).takeIf { it.isFile }
        val icon = directory.resolve(character.iconPath).takeIf { it.isFile }
        if (image == null) warnings += "Character image missing: ${character.imagePath}"
        if (icon == null) warnings += "Character icon missing: ${character.iconPath}"
        return CharacterPackageLoadResult(root, entry, character, imageFile = image, iconFile = icon, warnings = warnings)
    }

    private fun loadFromArcpkg(file: File): CharacterPackageLoadResult =
        ZipFile(file).use { zip ->
            val index = zip.getEntry("index.yml") ?: zip.getEntry("index.yaml")
                ?: return CharacterPackageLoadResult(file, null, null, errors = listOf("index.yml not found."))
            val entries = runCatching {
                zip.getInputStream(index).use { EtoileYaml.decodeFromStream(ListSerializer(ImportInformationEntry.serializer()), it) }
            }.getOrElse { return CharacterPackageLoadResult(file, null, null, errors = listOf("Unable to parse index.yml: ${it.message}")) }
            val characters = entries.filter { it.type == ArcpkgEntryType.CHARACTER }
            if (characters.isEmpty()) return CharacterPackageLoadResult(file, null, null, errors = listOf("No type: character entry found."))
            val warnings = mutableListOf<String>()
            if (characters.size > 1) warnings += "Multiple character entries found; using the first."
            val entry = characters.first()
            val settingsPath = "${entry.directory.trimEnd('/')}/${entry.settingsFile}"
            val settings = zip.getEntry(settingsPath)
                ?: return CharacterPackageLoadResult(file, entry, null, warnings = warnings, errors = listOf("Missing character.yml: $settingsPath"))
            val character = runCatching {
                zip.getInputStream(settings).use { EtoileYaml.decodeFromStream(CharacterInformation.serializer(), it) }
            }.getOrElse {
                return CharacterPackageLoadResult(file, entry, null, warnings = warnings, errors = listOf("Unable to parse character.yml: ${it.message}"))
            }
            val imageZipPath = "${entry.directory.trimEnd('/')}/${character.imagePath}"
            val iconZipPath = "${entry.directory.trimEnd('/')}/${character.iconPath}"
            if (zip.getEntry(imageZipPath) == null) warnings += "Character image missing: ${character.imagePath}"
            if (zip.getEntry(iconZipPath) == null) warnings += "Character icon missing: ${character.iconPath}"
            CharacterPackageLoadResult(
                sourceFile = file,
                entry = entry,
                character = character,
                imageZipPath = imageZipPath,
                iconZipPath = iconZipPath,
                warnings = warnings,
            )
        }
}

class CharacterPackageScanner {
    fun scan(input: File): CharacterPackageScanResult {
        val warnings = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val packages = collectCharacterInputs(input).map { file ->
            val result = runCatching { CharacterPackageLoader().loadExistingCharacterPackage(file) }
                .getOrElse { CharacterPackageLoadResult(file, null, null, errors = listOf(it.message ?: "Unable to load character package.")) }
            logs += "Scanned ${file.name}: ${result.identifier ?: "no character"}"
            warnings += result.warnings
            result
        }
        if (packages.isEmpty()) warnings += "No character arcpkg files found."
        return CharacterPackageScanResult(packages, warnings, logs)
    }

    private fun collectCharacterInputs(input: File): List<File> =
        when {
            input.isFile && input.extension.equals("arcpkg", ignoreCase = true) -> listOf(input)
            input.isDirectory -> input.walkTopDown()
                .filter { it.isFile && it.extension.equals("arcpkg", ignoreCase = true) }
                .toList()
                .sortedBy { it.name.lowercase() }
            else -> emptyList()
        }
}

class CharacterPackageValidator {
    fun validateCharacterArcpkg(outputFile: File): CharacterValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val logs = mutableListOf<String>()
        if (!outputFile.isFile) {
            return CharacterValidationReport(outputFile, valid = false, errors = listOf("Output file does not exist."))
        }
        var characterEntryCount = 0
        var identifier: String? = null
        var directory: String? = null
        var defaultName: String? = null
        var imageExists = false
        var iconExists = false
        runCatching {
            ZipFile(outputFile).use { zip ->
                val indexEntry = zip.getEntry("index.yml") ?: zip.getEntry("index.yaml")
                if (indexEntry == null) {
                    errors += "index.yml not found."
                    return@use
                }
                val entries = zip.getInputStream(indexEntry).use {
                    EtoileYaml.decodeFromStream(ListSerializer(ImportInformationEntry.serializer()), it)
                }
                val characters = entries.filter { it.type == ArcpkgEntryType.CHARACTER }
                characterEntryCount = characters.size
                if (characters.size != 1) errors += "Expected exactly one type: character entry, found ${characters.size}."
                val entry = characters.firstOrNull() ?: return@use
                identifier = entry.identifier
                directory = entry.directory
                if (entry.identifier.isBlank()) errors += "Character identifier is blank."
                if (entry.directory.isBlank()) errors += "Character directory is blank."
                if (entry.settingsFile.isBlank()) errors += "Character settingsFile is blank."
                val settingsPath = "${entry.directory.trimEnd('/')}/${entry.settingsFile}"
                val settings = zip.getEntry(settingsPath)
                if (settings == null) {
                    errors += "character.yml not found: $settingsPath"
                    return@use
                }
                val character = zip.getInputStream(settings).use {
                    EtoileYaml.decodeFromStream(CharacterInformation.serializer(), it)
                }
                defaultName = character.name["default"]
                if (defaultName.isNullOrBlank()) errors += "name.default is missing."
                if (character.imagePath.isBlank()) errors += "imagePath is missing."
                if (character.iconPath.isBlank()) errors += "iconPath is missing."
                imageExists = character.imagePath.isNotBlank() && zip.getEntry("${entry.directory.trimEnd('/')}/${character.imagePath}") != null
                iconExists = character.iconPath.isNotBlank() && zip.getEntry("${entry.directory.trimEnd('/')}/${character.iconPath}") != null
                if (!imageExists) errors += "imagePath target not found: ${character.imagePath}"
                if (!iconExists) errors += "iconPath target not found: ${character.iconPath}"
                if (character.scale == 0f) warnings += "scale is 0; ArcCreate imports it as 1."
                logs += "Validated character package: ${entry.identifier}"
            }
        }.onFailure { errors += it.message ?: "Unable to validate character package." }

        return CharacterValidationReport(
            outputFile = outputFile,
            valid = errors.isEmpty(),
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
    }
}

fun splitCharacterIdentifier(identifier: String, fallbackPublisher: String = "etoilebridge", fallbackCharacterId: String = "character"): Pair<String, String> {
    val parts = identifier.split('.').filter { it.isNotBlank() }
    return if (parts.size >= 2) {
        parts.dropLast(1).joinToString(".") to parts.last()
    } else {
        fallbackPublisher to fallbackCharacterId
    }
}

private fun String.safePublisherId(): String =
    split('.')
        .joinToString(".") { it.safePackageId("etoilebridge") }
        .ifBlank { "etoilebridge" }

private fun String.safePackageId(fallback: String): String =
    replace(Regex("""[^\w.-]+"""), "_")
        .trim('.', '_', '-')
        .ifBlank { fallback }

private fun String.safeFileName(fallback: String): String =
    replace(Regex("""[\\/:*?"<>|]+"""), "_")
        .trim()
        .ifBlank { fallback }

private const val CHARACTER_SETTINGS = "character.yml"
