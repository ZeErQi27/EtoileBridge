package com.zeerqi27.etoilebridge.core

import com.charleskorn.kaml.decodeFromStream
import com.zeerqi27.etoilebridge.core.etoile.ArcpkgEntryType
import com.zeerqi27.etoilebridge.core.etoile.EtoileYaml
import com.zeerqi27.etoilebridge.core.etoile.ImportInformationEntry
import com.zeerqi27.etoilebridge.core.etoile.PackInformation
import com.zeerqi27.etoilebridge.core.etoile.ProjectInformation
import java.io.File
import java.util.zip.ZipFile
import kotlinx.serialization.builtins.ListSerializer

class BundleOutputValidator {
    fun validateBundleArcpkg(outputFile: File): BundleValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val logs = mutableListOf<String>()

        if (!outputFile.isFile) {
            return BundleValidationReport(
                outputFile = outputFile,
                valid = false,
                errors = listOf("Output arcpkg does not exist: ${outputFile.absolutePath}"),
            )
        }

        return try {
            ZipFile(outputFile).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                names.groupingBy { it }.eachCount()
                    .filterValues { it > 1 }
                    .keys
                    .forEach { errors += "Duplicate zip entry: $it" }

                val indexEntry = zip.getEntry("index.yml") ?: zip.getEntry("index.yaml")
                if (indexEntry == null) {
                    return BundleValidationReport(outputFile, valid = false, errors = listOf("index.yml not found"))
                }

                val index = runCatching {
                    zip.getInputStream(indexEntry).use {
                        EtoileYaml.decodeFromStream(ListSerializer(ImportInformationEntry.serializer()), it)
                    }
                }.getOrElse { error ->
                    return BundleValidationReport(
                        outputFile = outputFile,
                        valid = false,
                        errors = listOf("index.yml parse failed: ${error.message}"),
                    )
                }

                val packEntries = index.filter { it.type == ArcpkgEntryType.PACK }
                val levelEntries = index.filter { it.type == ArcpkgEntryType.LEVEL }
                if (packEntries.isEmpty()) errors += "No type: pack entry found."
                if (packEntries.size > 1) errors += "Multiple type: pack entries found: ${packEntries.size}"
                if (levelEntries.isEmpty()) errors += "No type: level entries found."

                validateUniqueValues("directory", index.map { it.directory }, errors)
                validateUniqueValues("identifier", index.map { it.identifier }, errors)

                val packEntry = packEntries.firstOrNull()
                var packInfo: PackInformation? = null
                var packImageExists = false
                var levelIdentifiersMatch = false

                if (packEntry != null) {
                    validateIndexEntryBasics(packEntry, "pack", errors)
                    if (!hasDirectory(zip, packEntry.directory)) {
                        errors += "Pack directory not found: ${packEntry.directory}"
                    }
                    val packSettingsPath = "${packEntry.directory.trimEnd('/')}/${packEntry.settingsFile}"
                    val packSettings = zip.getEntry(packSettingsPath)
                    if (packSettings == null) {
                        errors += "Pack settings file not found: $packSettingsPath"
                    } else {
                        packInfo = runCatching {
                            zip.getInputStream(packSettings).use {
                                EtoileYaml.decodeFromStream(PackInformation.serializer(), it)
                            }
                        }.getOrElse { error ->
                            errors += "pack.yml parse failed: ${error.message}"
                            null
                        }
                    }
                }

                if (packInfo != null && packEntry != null) {
                    if (packInfo.packName.isBlank()) errors += "pack.yml packName is blank."
                    if (packInfo.imagePath.isBlank()) errors += "pack.yml imagePath is blank."
                    if (packInfo.levelIdentifiers.isEmpty()) errors += "pack.yml levelIdentifiers is empty."
                    val imageEntryName = "${packEntry.directory.trimEnd('/')}/${packInfo.imagePath}"
                    packImageExists = zip.getEntry(imageEntryName) != null
                    if (!packImageExists) {
                        errors += "Pack image referenced by pack.yml not found: $imageEntryName"
                    }
                    val levelIdentifiers = levelEntries.map { it.identifier }.toSet()
                    val packIdentifiers = packInfo.levelIdentifiers.toSet()
                    val missingInIndex = packIdentifiers - levelIdentifiers
                    val missingInPack = levelIdentifiers - packIdentifiers
                    levelIdentifiersMatch = missingInIndex.isEmpty() && missingInPack.isEmpty()
                    if (missingInIndex.isNotEmpty()) {
                        errors += "pack.yml references missing level identifiers: ${missingInIndex.joinToString()}"
                    }
                    if (missingInPack.isNotEmpty()) {
                        errors += "index.yml levels missing from pack.yml: ${missingInPack.joinToString()}"
                    }
                }

                levelEntries.forEach { level ->
                    validateIndexEntryBasics(level, "level", errors)
                    if (!hasDirectory(zip, level.directory)) {
                        errors += "Level directory not found: ${level.directory}"
                    }
                    val settingsPath = "${level.directory.trimEnd('/')}/${level.settingsFile}"
                    val settingsEntry = zip.getEntry(settingsPath)
                    if (settingsEntry == null) {
                        errors += "Level settings file not found: $settingsPath"
                    } else {
                        val text = runCatching {
                            zip.getInputStream(settingsEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        }.getOrElse {
                            errors += "Level settings file unreadable: $settingsPath: ${it.message}"
                            null
                        }
                        if (text != null) {
                            validateProjectCharts(settingsPath, text, errors, warnings)
                        }
                    }
                }

                logs += "Validated ${packEntries.size} pack entries and ${levelEntries.size} level entries."
                BundleValidationReport(
                    outputFile = outputFile,
                    valid = errors.isEmpty(),
                    packEntryCount = packEntries.size,
                    levelEntryCount = levelEntries.size,
                    packName = packInfo?.packName,
                    packIdentifier = packEntry?.identifier,
                    packImageExists = packImageExists,
                    levelIdentifiersMatch = levelIdentifiersMatch,
                    errors = errors,
                    warnings = warnings,
                    logs = logs,
                )
            }
        } catch (error: Exception) {
            BundleValidationReport(
                outputFile = outputFile,
                valid = false,
                errors = listOf("Bundle validation failed: ${error.message}"),
            )
        }
    }

    private fun validateIndexEntryBasics(
        entry: ImportInformationEntry,
        label: String,
        errors: MutableList<String>,
    ) {
        if (entry.directory.isBlank()) errors += "$label entry directory is blank."
        if (entry.identifier.isBlank()) errors += "$label entry identifier is blank."
        if (entry.settingsFile.isBlank()) errors += "$label entry settingsFile is blank."
    }

    private fun validateUniqueValues(label: String, values: List<String>, errors: MutableList<String>) {
        values.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
            .forEach { errors += "Duplicate $label: $it" }
    }

    private fun validateProjectCharts(
        settingsPath: String,
        text: String,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val parsed = runCatching {
            EtoileYaml.decodeFromStream(ProjectInformation.serializer(), text.byteInputStream(Charsets.UTF_8))
        }.getOrNull()
        val chartPaths = parsed?.charts?.map { it.chartPath }
            ?: Regex("""(?m)^\s*-?\s*chartPath:\s*"?([^"\r\n]+)"?\s*$""")
                .findAll(text)
                .map { it.groupValues[1].trim() }
                .toList()
                .also {
                    warnings += "project.arcproj used compatibility chart validation: $settingsPath"
                }

        if (chartPaths.isEmpty()) {
            errors += "project.arcproj charts is empty: $settingsPath"
        }
        chartPaths.filter { it.isBlank() }
            .forEach { _ -> errors += "project.arcproj has blank chartPath: $settingsPath" }
        chartPaths.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
            .forEach { errors += "Duplicate chartPath in $settingsPath: $it" }
    }

    private fun hasDirectory(zip: ZipFile, directory: String): Boolean {
        val prefix = directory.trimEnd('/') + "/"
        return zip.entries().asSequence().any { it.name == directory || it.name.startsWith(prefix) }
    }
}
