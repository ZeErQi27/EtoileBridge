package com.zeerqi27.etoilebridge.model

enum class UiOperationPhase {
    Idle,
    Copying,
    Extracting,
    Scanning,
    Ready,
    Converting,
    Validating,
    PendingSave,
    Saving,
    Saved,
    Failed,
}

enum class UiScrollTarget {
    SingleConvertSave,
    PackConvertSave,
}

object UiFeedbackStateRules {
    fun singlePhase(state: UiConvertState): UiOperationPhase =
        when {
            state.errorMessage != null || state.scanStatus == UiScanStatus.Failed || state.saveStatus == UiSaveStatus.Failed -> UiOperationPhase.Failed
            state.isCopying -> if (state.inputType == UiInputType.Zip) UiOperationPhase.Extracting else UiOperationPhase.Copying
            state.isScanning -> UiOperationPhase.Scanning
            state.isConverting -> UiOperationPhase.Converting
            state.isSaving -> UiOperationPhase.Saving
            state.saveStatus == UiSaveStatus.Saved -> UiOperationPhase.Saved
            state.pendingOutputFile != null || state.saveStatus == UiSaveStatus.Pending -> UiOperationPhase.PendingSave
            state.canConvert -> UiOperationPhase.Ready
            else -> UiOperationPhase.Idle
        }

    fun packPhase(state: UiPackConvertState): UiOperationPhase =
        when {
            state.errorMessage != null || state.scanStatus == UiScanStatus.Failed || state.saveStatus == UiSaveStatus.Failed -> UiOperationPhase.Failed
            state.isCopying -> UiOperationPhase.Copying
            state.isScanning -> UiOperationPhase.Scanning
            state.isPacking -> UiOperationPhase.Converting
            state.bundleValidationPassed == false -> UiOperationPhase.Failed
            state.isSaving -> UiOperationPhase.Saving
            state.saveStatus == UiSaveStatus.Saved -> UiOperationPhase.Saved
            state.pendingOutputFile != null || state.saveStatus == UiSaveStatus.Pending -> {
                if (state.bundleValidationPassed == true) UiOperationPhase.PendingSave else UiOperationPhase.Validating
            }
            state.canPack -> UiOperationPhase.Ready
            else -> UiOperationPhase.Idle
        }

    fun canSaveSingle(state: UiConvertState, busy: Boolean): Boolean =
        state.pendingOutputFile != null && state.canSave && !busy

    fun canSavePack(state: UiPackConvertState, busy: Boolean): Boolean =
        state.pendingOutputFile != null && state.canSave && state.bundleValidationPassed == true && !busy

    fun singleContinueTarget(state: UiConvertState): UiScrollTarget? =
        if (singlePhase(state) in setOf(UiOperationPhase.Ready, UiOperationPhase.PendingSave, UiOperationPhase.Saved)) {
            UiScrollTarget.SingleConvertSave
        } else {
            null
        }

    fun packContinueTarget(state: UiPackConvertState): UiScrollTarget? =
        if (packPhase(state) in setOf(UiOperationPhase.Ready, UiOperationPhase.PendingSave, UiOperationPhase.Saved)) {
            UiScrollTarget.PackConvertSave
        } else {
            null
        }
}
