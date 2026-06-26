package com.zeerqi27.etoilebridge.model

import com.zeerqi27.etoilebridge.ui.operationPhaseLabel
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterStateRulesTest {
    @Test
    fun defaultOutputFileNameUsesPublisherAndCharacterId() {
        assertEquals(
            "etoilebridge.otto.arcpkg",
            CharacterStateRules.defaultOutputFileName("etoilebridge", "otto"),
        )
    }

    @Test
    fun characterBuildRequiresImageIconAndNames() {
        assertFalse(UiCharacterState().canBuild)
        assertTrue(
            UiCharacterState(
                imageFilePath = "image.png",
                iconFilePath = "icon.png",
                publisherId = "etoilebridge",
                characterId = "otto",
                defaultName = "OTTO",
            ).canBuild
        )
    }

    @Test
    fun characterSaveRequiresValidatedPendingOutput() {
        val pending = File("character.arcpkg")

        assertFalse(UiCharacterState(pendingOutputFile = pending, validationPassed = false).canSave)
        assertTrue(UiCharacterState(pendingOutputFile = pending, validationPassed = true).canSave)
    }

    @Test
    fun pageSwitchDoesNotMutateThreeIndependentStates() {
        val single = UiConvertState(inputName = "single.zip", workspacePath = "single-workspace")
        val pack = UiPackConvertState(inputName = "pack.zip", workspacePath = "pack-workspace")
        val character = UiCharacterState(inputName = "otto.png", workspacePath = "character-workspace")
        var page = UiAppPage.SingleSong

        page = UiAppPage.Character

        assertEquals(UiAppPage.Character, page)
        assertEquals("single.zip", single.inputName)
        assertEquals("pack.zip", pack.inputName)
        assertEquals("otto.png", character.inputName)
        assertEquals("character-workspace", character.workspacePath)
    }

    @Test
    fun waitingPhaseIsLocalized() {
        assertEquals("Waiting", operationPhaseLabel(UiOperationPhase.Idle, UiLanguage.English))
        assertEquals("待开始", operationPhaseLabel(UiOperationPhase.Idle, UiLanguage.ZhHans))
    }
}
