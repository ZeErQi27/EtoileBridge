package com.zeerqi27.etoilebridge.model

import java.io.File

enum class UiCharacterInputType {
    None,
    Png,
    Arcpkg,
}

data class UiCharacterState(
    val language: UiLanguage = UiLanguage.ZhHans,
    val deviceSdkInt: Int = 0,
    val deviceRelease: String = "",
    val canUseMediaStoreDownloads: Boolean = false,
    val inputType: UiCharacterInputType = UiCharacterInputType.None,
    val inputName: String? = null,
    val workspacePath: String? = null,
    val isCopying: Boolean = false,
    val isScanning: Boolean = false,
    val isBuilding: Boolean = false,
    val isSaving: Boolean = false,
    val publisherId: String = "etoilebridge",
    val characterId: String = "character",
    val directory: String = "character",
    val outputFileName: String = "etoilebridge.character.arcpkg",
    val outputFileNameManual: Boolean = false,
    val defaultName: String = "Character",
    val zhCnName: String = "",
    val identifier: String = "etoilebridge.character",
    val sourceIdentifier: String? = null,
    val sourceDirectory: String? = null,
    val imageFileName: String? = null,
    val imageFilePath: String? = null,
    val iconFileName: String? = null,
    val iconFilePath: String? = null,
    val imageHasAlpha: Boolean? = null,
    val cropCenterX: Float = 0.5f,
    val cropCenterY: Float = 0.35f,
    val cropSize: Float = 0.35f,
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val pendingOutputFile: File? = null,
    val pendingOutputFileSize: Long? = null,
    val validationPassed: Boolean? = null,
    val validationSummary: List<String> = emptyList(),
    val validationErrors: List<String> = emptyList(),
    val saveStatus: UiSaveStatus = UiSaveStatus.NotSaved,
    val savedLocation: String? = null,
    val savedFileSize: Long? = null,
    val workspaceCleaned: Boolean = false,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null,
    val errorDetails: String? = null,
) {
    val canBuild: Boolean get() =
        !isCopying && !isScanning && !isBuilding && imageFilePath != null && iconFilePath != null &&
            publisherId.isNotBlank() && characterId.isNotBlank() && defaultName.isNotBlank()

    val canSave: Boolean get() =
        pendingOutputFile != null && validationPassed == true && !isSaving

    val canSaveDownloads: Boolean get() = canSave && canUseMediaStoreDownloads
}

object CharacterStateRules {
    fun defaultOutputFileName(publisherId: String, characterId: String): String =
        "${publisherId.ifBlank { "etoilebridge" }}.${characterId.ifBlank { "character" }}.arcpkg"

    fun inputLabel(type: UiCharacterInputType, language: UiLanguage): String =
        when (type) {
            UiCharacterInputType.None -> if (language == UiLanguage.English) "No input" else "未选择"
            UiCharacterInputType.Png -> "PNG"
            UiCharacterInputType.Arcpkg -> "character arcpkg"
        }
}
