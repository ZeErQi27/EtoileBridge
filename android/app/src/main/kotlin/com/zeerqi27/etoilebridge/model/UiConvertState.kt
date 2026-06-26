package com.zeerqi27.etoilebridge.model

import java.io.File

enum class UiInputType {
    None,
    Folder,
    Zip,
}

enum class UiExtractStatus {
    NotExtracted,
    Extracting,
    Extracted,
    Failed,
}

enum class UiScanStatus {
    NotScanned,
    Scanning,
    Scanned,
    Failed,
}

enum class UiSaveStatus {
    NotSaved,
    Pending,
    Saving,
    Saved,
    Failed,
    Canceled,
}

enum class UiLanguage {
    ZhHans,
    English,
}

enum class UiArcCreateSide {
    Light,
    Conflict,
    Colorless,
}

enum class UiArcCreateNote {
    Inherit,
    Light,
    Conflict,
}

enum class UiArcCreateParticle {
    Inherit,
    Light,
    Conflict,
    MiraiLight,
    MiraiConflict,
    Colorless,
}

enum class UiArcCreateAccent {
    Inherit,
    Light,
    Conflict,
    Dynamix,
    Colorless,
}

enum class UiArcCreateTrack {
    Inherit,
    Light,
    Conflict,
    Black,
    Nijuusei,
    Rei,
    DarkVs,
    Tempest,
    Finale,
    Pentiment,
    Arcana,
    Colorless,
}

enum class UiArcCreateSingleLine {
    None,
    Light,
    Conflict,
    Neo,
}

data class UiConvertState(
    val language: UiLanguage = UiLanguage.ZhHans,
    val deviceSdkInt: Int = 0,
    val deviceRelease: String = "",
    val canUseMediaStoreDownloads: Boolean = false,
    val inputType: UiInputType = UiInputType.None,
    val inputName: String? = null,
    val workspacePath: String? = null,
    val projectRootPath: String? = null,
    val songRootPath: String? = null,
    val extractStatus: UiExtractStatus = UiExtractStatus.NotExtracted,
    val scanStatus: UiScanStatus = UiScanStatus.NotScanned,
    val songId: String? = null,
    val affDifficulties: List<String> = emptyList(),
    val adoptedAffFiles: List<String> = emptyList(),
    val ignoredAffFiles: List<String> = emptyList(),
    val affMappings: List<UiAffMappingItem> = emptyList(),
    val resourceStatus: UiResourceStatus = UiResourceStatus(),
    val outputFileName: String? = null,
    val pendingOutputFile: File? = null,
    val pendingOutputFileSize: Long? = null,
    val saveStatus: UiSaveStatus = UiSaveStatus.NotSaved,
    val savedMethod: String? = null,
    val savedFileName: String? = null,
    val savedLocation: String? = null,
    val savedFileSize: Long? = null,
    val workspaceCleaned: Boolean = false,
    val manualResources: UiManualResources = UiManualResources(),
    val options: UiConvertOptions = UiConvertOptions(),
    val appearanceOptions: UiAppearanceOptions = UiAppearanceOptions(),
    val missingMetadata: UiMissingMetadata? = null,
    val metadataDraft: UiMetadataDraft = UiMetadataDraft(),
    val unsupportedPackStructure: Boolean = false,
    val unsupportedPackMessage: String? = null,
    val candidateSongIds: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null,
    val errorDetails: String? = null,
    val isCopying: Boolean = false,
    val isScanning: Boolean = false,
    val isConverting: Boolean = false,
    val isSaving: Boolean = false,
    val canScan: Boolean = false,
    val canConvert: Boolean = false,
    val canSave: Boolean = false,
    val canSaveDownloads: Boolean = false,
)

data class UiResourceStatus(
    val audioFileName: String? = null,
    val audioFilePath: String? = null,
    val jacketFileName: String? = null,
    val jacketFilePath: String? = null,
    val backgroundFileName: String? = null,
    val backgroundFilePath: String? = null,
    val audioManual: Boolean = false,
    val jacketManual: Boolean = false,
    val backgroundManual: Boolean = false,
    val resourceFiles: List<String> = emptyList(),
)

data class UiManualResources(
    val audioFileName: String? = null,
    val jacketFileName: String? = null,
    val backgroundFileName: String? = null,
    val songlistFileName: String? = null,
    val packlistFileName: String? = null,
)

enum class ManualResourceKind {
    Audio,
    Jacket,
    Background,
    Songlist,
    Packlist,
}

data class UiMissingMetadata(
    val reason: String,
    val requiredFields: List<String>,
    val optionalFields: List<String>,
    val candidateSongIds: List<String>,
    val affFiles: List<String>,
    val resourceFiles: List<String>,
)

data class UiMetadataDraft(
    val songId: String = "",
    val title: String = "",
    val artist: String = "",
    val bpmText: String = "",
    val baseBpm: String = "",
    val side: String = "0",
    val publisherId: String = "etoilebridge",
    val levelId: String = "",
    val identifierOverride: String = "",
    val difficulties: List<UiDifficultyDraft> = emptyList(),
)

data class UiAppearanceOptions(
    val side: UiArcCreateSide = UiArcCreateSide.Light,
    val sideInferredFromLephon: Boolean = false,
    val note: UiArcCreateNote = UiArcCreateNote.Inherit,
    val particle: UiArcCreateParticle = UiArcCreateParticle.Inherit,
    val accent: UiArcCreateAccent = UiArcCreateAccent.Inherit,
    val track: UiArcCreateTrack = UiArcCreateTrack.Inherit,
    val singleLine: UiArcCreateSingleLine = UiArcCreateSingleLine.None,
)

data class UiDifficultyDraft(
    val ratingClass: Int,
    val affFileName: String,
    val difficulty: String = "",
    val chartConstant: String = "",
    val chartDesigner: String = "",
    val jacketDesigner: String = "",
)

data class UiAffMappingItem(
    val filePath: String,
    val fileName: String,
    val detectedRatingClass: Int?,
    val mappedRatingClass: Int?,
    val adopted: Boolean,
    val manual: Boolean,
    val conflict: Boolean,
)
