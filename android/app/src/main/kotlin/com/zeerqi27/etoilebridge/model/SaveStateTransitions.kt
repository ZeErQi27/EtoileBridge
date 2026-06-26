package com.zeerqi27.etoilebridge.model

object SaveStateTransitions {
    fun afterSaveFailure(
        state: UiConvertState,
        message: String,
        details: String?,
        canUseDownloads: Boolean,
        logLine: String,
    ): UiConvertState =
        state.copy(
            isSaving = false,
            saveStatus = UiSaveStatus.Failed,
            errorMessage = message,
            errorDetails = details,
            canSave = state.pendingOutputFile != null,
            canSaveDownloads = state.pendingOutputFile != null && canUseDownloads,
            logs = state.logs + logLine,
        )
}
