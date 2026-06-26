package com.zeerqi27.etoilebridge.model

object SingleSongStateRules {
    fun showMetadataEditorEntry(state: UiConvertState): Boolean =
        !state.unsupportedPackStructure && (state.missingMetadata != null || state.metadataDraft.songId.isNotBlank())

    fun canStartConversion(state: UiConvertState, busy: Boolean = false): Boolean =
        state.canConvert && !state.unsupportedPackStructure && !busy

    fun canSaveAs(state: UiConvertState, busy: Boolean = false): Boolean =
        state.pendingOutputFile != null && state.canSave && !busy

    fun showDownloadsButton(sdkInt: Int): Boolean = sdkInt >= 29

    fun showPackBlocked(state: UiConvertState): Boolean =
        state.unsupportedPackStructure
}
