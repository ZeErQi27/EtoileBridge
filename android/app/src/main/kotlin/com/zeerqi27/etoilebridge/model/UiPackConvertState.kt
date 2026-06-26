package com.zeerqi27.etoilebridge.model

import java.io.File

enum class UiAppPage {
    SingleSong,
    PackBundle,
    Character,
}

enum class UiPackMode {
    OfficialArcaeaPack,
    ArcpkgBundle,
    ExistingPackEdit,
}

data class UiPackConvertState(
    val language: UiLanguage = UiLanguage.ZhHans,
    val deviceSdkInt: Int = 0,
    val deviceRelease: String = "",
    val canUseMediaStoreDownloads: Boolean = false,
    val mode: UiPackMode = UiPackMode.OfficialArcaeaPack,
    val inputName: String? = null,
    val workspacePath: String? = null,
    val projectRootPath: String? = null,
    val scanStatus: UiScanStatus = UiScanStatus.NotScanned,
    val isCopying: Boolean = false,
    val isScanning: Boolean = false,
    val isPacking: Boolean = false,
    val isSaving: Boolean = false,
    val publisherId: String = "etoilebridge",
    val outputFileName: String = "etoilebridge.EtoileBridgePack.arcpkg",
    val outputFileNameManual: Boolean = false,
    val packName: String = "EtoileBridge Pack",
    val packId: String = "EtoileBridgePack",
    val packImageFileName: String? = null,
    val packImageFilePath: String? = null,
    val packImageManual: Boolean = false,
    val includeOnlyConvertible: Boolean = true,
    val options: UiConvertOptions = UiConvertOptions(),
    val entries: List<UiPackEntry> = emptyList(),
    val sourceReports: List<UiArcpkgSourceReport> = emptyList(),
    val existingLevelCount: Int = 0,
    val addedLevelCount: Int = 0,
    val finalLevelCount: Int = 0,
    val renamedConflictCount: Int = 0,
    val pendingOutputFile: File? = null,
    val pendingOutputFileSize: Long? = null,
    val bundleValidationPassed: Boolean? = null,
    val bundleValidationSummary: List<String> = emptyList(),
    val bundleValidationErrors: List<String> = emptyList(),
    val saveStatus: UiSaveStatus = UiSaveStatus.NotSaved,
    val savedLocation: String? = null,
    val savedFileSize: Long? = null,
    val workspaceCleaned: Boolean = false,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null,
    val errorDetails: String? = null,
    val canScan: Boolean = false,
    val canPack: Boolean = false,
    val canSave: Boolean = false,
    val canSaveDownloads: Boolean = false,
)

data class UiPackEntry(
    val key: String,
    val songId: String,
    val title: String,
    val artist: String,
    val difficultySummary: String,
    val charts: List<UiPackChartEntry> = emptyList(),
    val levelId: String = songId,
    val originalTitle: String = title,
    val originalArtist: String = artist,
    val originalLevelId: String = levelId,
    val enabled: Boolean,
    val audio: String?,
    val jacket: String?,
    val background: String?,
    val metadataStatus: String,
    val canConvert: Boolean,
    val warningCount: Int,
    val warnings: List<String> = emptyList(),
    val failureReason: String?,
) {
    val canBeEnabled: Boolean get() = canConvert || metadataStatus.contains("metadata", ignoreCase = true)
    val metadataDraftLooksComplete: Boolean
        get() = metadataStatus.contains("metadata", ignoreCase = true) &&
            title.isNotBlank() &&
            artist.isNotBlank() &&
            charts.any { it.effectiveCanPack || (it.enabled && it.difficultyText.isNotBlank() && it.chartConstantText.toFloatOrNull() != null) }
    val effectiveCanPack: Boolean
        get() = enabled &&
            charts.any { it.effectiveCanPack || (metadataDraftLooksComplete && it.enabled) } &&
            (canConvert || metadataDraftLooksComplete)
    val identifierPreview: String get() = levelId.ifBlank { songId }
}

data class UiPackChartEntry(
    val ratingClass: Int,
    val chartPath: String,
    val difficultyText: String,
    val chartConstantText: String,
    val charter: String = "",
    val illustrator: String = "",
    val enabled: Boolean = true,
    val originalDifficultyText: String = difficultyText,
    val originalChartConstantText: String = chartConstantText,
    val originalCharter: String = charter,
    val originalIllustrator: String = illustrator,
    val originalEnabled: Boolean = enabled,
    val canConvert: Boolean = true,
    val warningCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val failureReason: String? = null,
) {
    val effectiveCanPack: Boolean
        get() = enabled && canConvert && difficultyText.isNotBlank() && chartPath.isNotBlank()
}

data class UiArcpkgSourceReport(
    val sourceName: String,
    val readable: Boolean,
    val levelCount: Int,
    val failureReason: String?,
)

object PackStateRules {
    fun canStartPacking(state: UiPackConvertState, busy: Boolean): Boolean {
        if (busy || !state.canPack || state.entries.isEmpty()) return false
        val enabled = state.entries.filter { it.enabled }
        if (enabled.isEmpty()) return false
        return if (state.includeOnlyConvertible) {
            enabled.any { it.effectiveCanPack }
        } else {
            enabled.all { it.effectiveCanPack }
        }
    }

    fun showDownloadsButton(state: UiPackConvertState): Boolean =
        state.pendingOutputFile != null && state.canUseMediaStoreDownloads

    fun showPreprocessingOptions(state: UiPackConvertState): Boolean =
        state.mode == UiPackMode.OfficialArcaeaPack

    fun canSaveValidatedOutput(state: UiPackConvertState): Boolean =
        state.pendingOutputFile != null && state.bundleValidationPassed == true
}
