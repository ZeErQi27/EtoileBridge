package com.zeerqi27.etoilebridge.core

import java.io.File

data class BundleOptions(
    val publisherId: String = "etoilebridge",
    val outputFileName: String = "etoilebridge.EtoileBridgePack.arcpkg",
    val packName: String? = null,
    val packId: String? = null,
    val packIdentifier: String? = null,
    val packImageFile: File? = null,
    val includeOnlyConvertible: Boolean = true,
    val convertOptions: ConvertOptions = ConvertOptions(),
    val appearanceOptions: AppearanceOptions = AppearanceOptions(),
    val entryOverrides: Map<String, BundleEntryOverride> = emptyMap(),
)

data class BundleEntryOverride(
    val enabled: Boolean? = null,
    val title: String? = null,
    val artist: String? = null,
    val levelId: String? = null,
    val chartOverrides: Map<Int, BundleChartOverride> = emptyMap(),
)

data class BundleChartOverride(
    val enabled: Boolean? = null,
    val difficulty: String? = null,
    val chartConstant: Float? = null,
    val charter: String? = null,
    val illustrator: String? = null,
)

data class BundleInput(
    val workspaceDir: File,
    val outputFile: File,
    val options: BundleOptions = BundleOptions(),
)

data class BundleScanResult(
    val projectRoot: File,
    val songlistFile: File?,
    val packlistFile: File?,
    val packNameCandidate: String? = null,
    val packIdCandidate: String? = null,
    val packImageFile: File? = null,
    val entries: List<BundleEntry>,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
) {
    val convertibleEntries: List<BundleEntry> get() = entries.filter { it.canConvert }
    val canConvertAny: Boolean get() = convertibleEntries.isNotEmpty()
}

data class BundleEntry(
    val key: String,
    val songId: String,
    val title: String? = null,
    val artist: String? = null,
    val difficultySummary: String = "",
    val charts: List<BundleChartEntry> = emptyList(),
    val songDir: File,
    val affFiles: List<File> = emptyList(),
    val ignoredAffFiles: List<File> = emptyList(),
    val audioFile: File? = null,
    val jacketFile: File? = null,
    val backgroundFile: File? = null,
    val songlistFile: File? = null,
    val packlistFile: File? = null,
    val metadataStatus: BundleMetadataStatus = BundleMetadataStatus.Unknown,
    val canConvert: Boolean = false,
    val warnings: List<String> = emptyList(),
    val failureReason: String? = null,
)

data class BundleChartEntry(
    val ratingClass: Int,
    val chartPath: String,
    val difficulty: String? = null,
    val chartConstant: Float? = null,
    val charter: String? = null,
    val illustrator: String? = null,
    val affFile: File? = null,
    val enabled: Boolean = true,
    val canConvert: Boolean = true,
    val warnings: List<String> = emptyList(),
    val failureReason: String? = null,
)

enum class BundleMetadataStatus {
    Resolved,
    NeedMetadata,
    Unknown,
}

sealed class BundleConvertResult {
    data class Success(
        val outputFile: File,
        val convertedCount: Int,
        val skippedCount: Int,
        val warnings: List<String>,
        val logs: List<String>,
    ) : BundleConvertResult()

    data class Failed(
        val message: String,
        val cause: Throwable? = null,
        val warnings: List<String> = emptyList(),
        val logs: List<String> = emptyList(),
        val workspaceDir: File? = null,
    ) : BundleConvertResult()
}

data class BundleValidationReport(
    val outputFile: File,
    val valid: Boolean,
    val packEntryCount: Int = 0,
    val levelEntryCount: Int = 0,
    val packName: String? = null,
    val packIdentifier: String? = null,
    val packImageExists: Boolean = false,
    val levelIdentifiersMatch: Boolean = false,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
) {
    fun summaryLines(): List<String> =
        buildList {
            add("Bundle validation: ${if (valid) "passed" else "failed"}")
            add("pack entries: $packEntryCount")
            add("level entries: $levelEntryCount")
            packName?.let { add("packName: $it") }
            packIdentifier?.let { add("pack identifier: $it") }
            add("pack image exists: $packImageExists")
            add("levelIdentifiers match: $levelIdentifiersMatch")
        }
}

data class ArcpkgBundleScanResult(
    val sourceFiles: List<ArcpkgSourceReport>,
    val levelEntries: List<ArcpkgLevelEntry>,
    val packEntries: List<ArcpkgPackEntry> = emptyList(),
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
) {
    val validLevelCount: Int get() = levelEntries.size
    val packNameCandidate: String? get() = packEntries.firstNotNullOfOrNull { it.packName }
    val packImageCandidate: ArcpkgPackImageCandidate? get() =
        packEntries.firstOrNull { it.packImageExists && it.packImageZipPath != null }
            ?.let { ArcpkgPackImageCandidate(it.sourceFile, requireNotNull(it.packImageZipPath)) }
}

data class ArcpkgSourceReport(
    val sourceFile: File,
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

data class ArcpkgLevelEntry(
    val key: String,
    val sourceFile: File,
    val directory: String,
    val identifier: String,
    val settingsFile: String,
    val version: Int = 0,
    val title: String? = null,
    val artist: String? = null,
    val difficultySummary: String = "",
    val charts: List<BundleChartEntry> = emptyList(),
    val warnings: List<String> = emptyList(),
    val failureReason: String? = null,
)

data class ArcpkgPackEntry(
    val sourceFile: File,
    val directory: String,
    val identifier: String,
    val settingsFile: String,
    val version: Int = 0,
    val packName: String? = null,
    val imagePath: String? = null,
    val packImageZipPath: String? = null,
    val packImageExists: Boolean = false,
    val levelIdentifiers: List<String> = emptyList(),
    val matchesIndexLevels: Boolean? = null,
)

data class ArcpkgPackImageCandidate(
    val sourceFile: File,
    val zipEntryPath: String,
)

data class ExistingPackEditScanResult(
    val basePackFile: File,
    val basePackEntry: ArcpkgPackEntry?,
    val existingLevels: List<ArcpkgLevelEntry>,
    val addedLevels: List<ArcpkgLevelEntry>,
    val sourceFiles: List<ArcpkgSourceReport>,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
    val renamedConflictCount: Int = 0,
) {
    val existingLevelCount: Int get() = existingLevels.size
    val addedLevelCount: Int get() = addedLevels.size
    val finalLevelCount: Int get() = existingLevelCount + addedLevelCount
    val canRebuild: Boolean get() = basePackEntry != null && existingLevels.isNotEmpty()
    val packNameCandidate: String? get() = basePackEntry?.packName
    val packIdCandidate: String? get() =
        basePackEntry?.identifier
            ?.removeSuffix(".pack")
            ?.substringAfterLast('.')
            ?: basePackEntry?.directory
    val packImageCandidate: ArcpkgPackImageCandidate? get() =
        basePackEntry
            ?.takeIf { it.packImageExists && it.packImageZipPath != null }
            ?.let { ArcpkgPackImageCandidate(it.sourceFile, requireNotNull(it.packImageZipPath)) }
}
