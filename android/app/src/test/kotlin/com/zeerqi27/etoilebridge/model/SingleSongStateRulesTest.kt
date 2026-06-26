package com.zeerqi27.etoilebridge.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingleSongStateRulesTest {
    @Test
    fun missingMetadataShowsEditorEntry() {
        val state = UiConvertState(
            missingMetadata = UiMissingMetadata(
                reason = "missing",
                requiredFields = listOf("title"),
                optionalFields = emptyList(),
                candidateSongIds = emptyList(),
                affFiles = emptyList(),
                resourceFiles = emptyList(),
            ),
        )

        assertTrue(SingleSongStateRules.showMetadataEditorEntry(state))
    }

    @Test
    fun android9UsesSaveAsAsPrimaryExport() {
        assertFalse(SingleSongStateRules.showDownloadsButton(28))
    }

    @Test
    fun android10AndAboveCanShowDownloadsExport() {
        assertTrue(SingleSongStateRules.showDownloadsButton(29))
        assertTrue(SingleSongStateRules.showDownloadsButton(35))
    }

    @Test
    fun editedMetadataCanEnableConversionWhenStateIsReady() {
        val state = UiConvertState(
            canConvert = true,
            metadataDraft = UiMetadataDraft(songId = "afterdark", title = "Afterdark"),
        )

        assertTrue(SingleSongStateRules.canStartConversion(state))
    }

    @Test
    fun manualAffMappingCanEnableConversionWhenStateIsReady() {
        val state = UiConvertState(
            canConvert = true,
            affMappings = listOf(
                UiAffMappingItem(
                    filePath = "2_no_smoothness.aff",
                    fileName = "2_no_smoothness.aff",
                    detectedRatingClass = null,
                    mappedRatingClass = 2,
                    adopted = true,
                    manual = true,
                    conflict = false,
                ),
            ),
        )

        assertTrue(SingleSongStateRules.canStartConversion(state))
    }

    @Test
    fun packStructureDisablesConversion() {
        val state = UiConvertState(
            canConvert = true,
            unsupportedPackStructure = true,
            candidateSongIds = listOf("a", "b"),
        )

        assertTrue(SingleSongStateRules.showPackBlocked(state))
        assertFalse(SingleSongStateRules.canStartConversion(state))
    }

    @Test
    fun scanningStateDisablesConversionAndReportsScanningPhase() {
        val state = UiConvertState(canConvert = true, isScanning = true)

        assertFalse(SingleSongStateRules.canStartConversion(state, busy = state.isScanning))
        assertEquals(UiOperationPhase.Scanning, UiFeedbackStateRules.singlePhase(state))
        assertEquals(null, UiFeedbackStateRules.singleContinueTarget(state))
    }

    @Test
    fun convertingStateDisablesSaveAndReportsConvertingPhase() {
        val state = UiConvertState(
            pendingOutputFile = java.io.File("song.arcpkg"),
            canSave = true,
            isConverting = true,
        )

        assertFalse(SingleSongStateRules.canSaveAs(state, busy = state.isConverting))
        assertEquals(UiOperationPhase.Converting, UiFeedbackStateRules.singlePhase(state))
    }

    @Test
    fun readySingleSongExposesConvertSaveScrollTarget() {
        val state = UiConvertState(canConvert = true)

        assertEquals(UiScrollTarget.SingleConvertSave, UiFeedbackStateRules.singleContinueTarget(state))
    }

    @Test
    fun resourceStateSurvivesExpandedCollapsedUiStateChanges() {
        val resources = UiResourceStatus(
            audioFileName = "base.ogg",
            jacketFileName = "1080_base.jpg",
            backgroundFileName = "bg.jpg",
        )
        val state = UiConvertState(resourceStatus = resources)

        assertEquals(resources, state.resourceStatus)
        assertEquals(resources, state.copy().resourceStatus)
    }

    @Test
    fun metadataEditorStateDoesNotExposeRatingOrRatingPlus() {
        val fields = UiDifficultyDraft::class.java.declaredFields.map { it.name }.toSet()

        assertFalse("rating" in fields)
        assertFalse("ratingPlus" in fields)
        assertTrue("difficulty" in fields)
        assertTrue("chartConstant" in fields)
    }
}
