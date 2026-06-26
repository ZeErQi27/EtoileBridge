package com.zeerqi27.etoilebridge.file

import com.zeerqi27.etoilebridge.model.SaveStateTransitions
import com.zeerqi27.etoilebridge.model.UiConvertState
import com.zeerqi27.etoilebridge.model.UiSaveStatus
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.io.path.createTempFile

class VerifiedFileWriterTest {
    @Test
    fun copyFileToOutputStreamCountsWrittenBytes() {
        val file = createTempFile().toFile()
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        val output = ByteArrayOutputStream()

        val bytes = VerifiedFileWriter.copyFileToOutputStream(file, output)

        assertEquals(5, bytes)
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), output.toByteArray().toList())
    }

    @Test
    fun verifyWrittenBytesFailsOnMismatch() {
        val file = createTempFile().toFile()
        file.writeBytes(byteArrayOf(1, 2, 3))

        assertFailsWith<IllegalStateException> {
            VerifiedFileWriter.verifyWrittenBytes(file, writtenBytes = 0)
        }
    }

    @Test
    fun canUseMediaStoreDownloadsMatchesAndroidVersionBoundary() {
        assertFalse(AndroidFileBridge.canUseMediaStoreDownloads(28))
        assertTrue(AndroidFileBridge.canUseMediaStoreDownloads(29))
        assertTrue(AndroidFileBridge.canUseMediaStoreDownloads(30))
        assertTrue(AndroidFileBridge.canUseMediaStoreDownloads(35))
    }

    @Test
    fun saveFailureKeepsPendingOutputState() {
        val pending = File("pending.arcpkg")
        val state = UiConvertState(
            pendingOutputFile = pending,
            canSave = false,
            canSaveDownloads = false,
            isSaving = true,
        )

        val failed = SaveStateTransitions.afterSaveFailure(
            state = state,
            message = "保存失败，文件仍保留在待保存状态",
            details = "details",
            canUseDownloads = true,
            logLine = "failed",
        )

        assertNotNull(failed.pendingOutputFile)
        assertEquals(UiSaveStatus.Failed, failed.saveStatus)
        assertEquals("保存失败，文件仍保留在待保存状态", failed.errorMessage)
        assertTrue(failed.canSave)
        assertTrue(failed.canSaveDownloads)
        assertFalse(failed.isSaving)
    }
}
