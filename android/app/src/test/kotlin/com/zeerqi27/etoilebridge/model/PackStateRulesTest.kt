package com.zeerqi27.etoilebridge.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackStateRulesTest {
    @Test
    fun partialConvertibleEntriesCanStartPacking() {
        val state = UiPackConvertState(
            canPack = true,
            includeOnlyConvertible = true,
            entries = listOf(
                packEntry("ok", canConvert = true),
                packEntry("bad", canConvert = false, metadataStatus = "Need metadata", failureReason = "missing"),
            ),
        )

        assertTrue(PackStateRules.canStartPacking(state, busy = false))
    }

    @Test
    fun partialConvertibleEntriesCannotStartPackingWhenIncludeOnlyConvertibleIsOff() {
        val state = UiPackConvertState(
            canPack = true,
            includeOnlyConvertible = false,
            entries = listOf(
                packEntry("ok", canConvert = true),
                packEntry("bad", canConvert = false, metadataStatus = "Need metadata", failureReason = "missing"),
            ),
        )

        assertFalse(PackStateRules.canStartPacking(state, busy = false))
    }

    @Test
    fun allInvalidEntriesCannotStartPacking() {
        val state = UiPackConvertState(
            canPack = false,
            entries = listOf(packEntry("bad", canConvert = false, metadataStatus = "Need metadata", failureReason = "missing")),
        )

        assertFalse(PackStateRules.canStartPacking(state, busy = false))
    }

    @Test
    fun downloadsButtonRequiresPendingFileAndSdkSupport() {
        assertFalse(PackStateRules.showDownloadsButton(UiPackConvertState(canUseMediaStoreDownloads = true)))
        assertTrue(
            PackStateRules.showDownloadsButton(
                UiPackConvertState(
                    canUseMediaStoreDownloads = true,
                    pendingOutputFile = File("pack.arcpkg"),
                )
            )
        )
    }

    @Test
    fun preprocessingOptionsOnlyShowForOfficialPackMode() {
        assertTrue(PackStateRules.showPreprocessingOptions(UiPackConvertState(mode = UiPackMode.OfficialArcaeaPack)))
        assertFalse(PackStateRules.showPreprocessingOptions(UiPackConvertState(mode = UiPackMode.ArcpkgBundle)))
        assertFalse(PackStateRules.showPreprocessingOptions(UiPackConvertState(mode = UiPackMode.ExistingPackEdit)))
    }

    @Test
    fun saveRequiresValidatedOutput() {
        val file = File("pack.arcpkg")
        assertFalse(PackStateRules.canSaveValidatedOutput(UiPackConvertState(pendingOutputFile = file, bundleValidationPassed = false)))
        assertTrue(PackStateRules.canSaveValidatedOutput(UiPackConvertState(pendingOutputFile = file, bundleValidationPassed = true)))
    }

    @Test
    fun validatorControlsPackSaveFeedbackState() {
        val file = File("pack.arcpkg")

        assertFalse(
            UiFeedbackStateRules.canSavePack(
                UiPackConvertState(pendingOutputFile = file, canSave = true, bundleValidationPassed = false),
                busy = false,
            )
        )
        assertTrue(
            UiFeedbackStateRules.canSavePack(
                UiPackConvertState(pendingOutputFile = file, canSave = true, bundleValidationPassed = true),
                busy = false,
            )
        )
        assertFalse(
            UiFeedbackStateRules.canSavePack(
                UiPackConvertState(pendingOutputFile = file, canSave = true, bundleValidationPassed = true),
                busy = true,
            )
        )
    }

    @Test
    fun packPhasesExposeValidationAndPendingSaveStates() {
        val file = File("pack.arcpkg")

        assertEquals(UiOperationPhase.Validating, UiFeedbackStateRules.packPhase(UiPackConvertState(pendingOutputFile = file)))
        assertEquals(
            UiOperationPhase.PendingSave,
            UiFeedbackStateRules.packPhase(UiPackConvertState(pendingOutputFile = file, bundleValidationPassed = true)),
        )
        assertEquals(
            UiOperationPhase.Failed,
            UiFeedbackStateRules.packPhase(UiPackConvertState(pendingOutputFile = file, bundleValidationPassed = false)),
        )
    }

    @Test
    fun readyPackExposesPackSaveScrollTarget() {
        val state = UiPackConvertState(canPack = true, entries = listOf(packEntry("ok")))

        assertEquals(UiScrollTarget.PackConvertSave, UiFeedbackStateRules.packContinueTarget(state))
    }

    @Test
    fun failedPackDoesNotExposeContinueScrollTarget() {
        val state = UiPackConvertState(errorMessage = "bad input")

        assertEquals(null, UiFeedbackStateRules.packContinueTarget(state))
    }

    @Test
    fun defaultPackOutputNameUsesPublisherAndPackIdPrefix() {
        val state = UiPackConvertState()

        assertTrue(state.outputFileName.startsWith("etoilebridge."))
        assertTrue(state.outputFileName.endsWith(".arcpkg"))
    }

    @Test
    fun noEnabledEntriesCannotStartPacking() {
        val state = UiPackConvertState(
            canPack = true,
            entries = listOf(packEntry("ok", enabled = false, canConvert = true)),
        )

        assertFalse(PackStateRules.canStartPacking(state, busy = false))
    }

    @Test
    fun skippedInvalidEntryDoesNotBlockPackingWhenIncludeOnlyConvertibleIsOff() {
        val state = UiPackConvertState(
            canPack = true,
            includeOnlyConvertible = false,
            entries = listOf(
                packEntry("ok", enabled = true, canConvert = true),
                packEntry("bad", enabled = false, canConvert = false, failureReason = "missing audio"),
            ),
        )

        assertTrue(PackStateRules.canStartPacking(state, busy = false))
    }

    @Test
    fun metadataDraftCanEnableNeedMetadataEntry() {
        val state = UiPackConvertState(
            canPack = true,
            entries = listOf(
                packEntry(
                    "needs_meta",
                    enabled = true,
                    canConvert = false,
                    metadataStatus = "Need metadata",
                ).copy(
                    title = "Title",
                    artist = "Composer",
                    charts = listOf(
                        UiPackChartEntry(
                            ratingClass = 2,
                            chartPath = "2.aff",
                            difficultyText = "Future 9",
                            chartConstantText = "9.5",
                            canConvert = false,
                        )
                    ),
                )
            ),
        )

        assertTrue(PackStateRules.canStartPacking(state, busy = false))
    }

    @Test
    fun pageSwitchDoesNotMutateIndependentStates() {
        val single = UiConvertState(inputName = "single.zip", workspacePath = "single-workspace")
        val pack = UiPackConvertState(inputName = "pack.zip", workspacePath = "pack-workspace")
        var page = UiAppPage.SingleSong

        page = UiAppPage.PackBundle

        assertEquals(UiAppPage.PackBundle, page)
        assertEquals("single.zip", single.inputName)
        assertEquals("single-workspace", single.workspacePath)
        assertEquals("pack.zip", pack.inputName)
        assertEquals("pack-workspace", pack.workspacePath)
    }

    private fun packEntry(
        songId: String,
        enabled: Boolean = true,
        canConvert: Boolean = true,
        metadataStatus: String = "OK",
        failureReason: String? = null,
    ): UiPackEntry =
        UiPackEntry(
            key = songId,
            songId = songId,
            title = "",
            artist = "",
            difficultySummary = "",
            charts = listOf(
                UiPackChartEntry(
                    ratingClass = 2,
                    chartPath = "2.aff",
                    difficultyText = "Future 9",
                    chartConstantText = "9.5",
                    canConvert = canConvert,
                )
            ),
            enabled = enabled,
            audio = null,
            jacket = null,
            background = null,
            metadataStatus = metadataStatus,
            canConvert = canConvert,
            warningCount = 0,
            failureReason = failureReason,
        )
}
