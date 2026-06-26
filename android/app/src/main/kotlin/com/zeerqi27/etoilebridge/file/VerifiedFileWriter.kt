package com.zeerqi27.etoilebridge.file

import java.io.File
import java.io.OutputStream

data class CopyVerification(
    val expectedBytes: Long,
    val writtenBytes: Long,
)

object VerifiedFileWriter {
    fun copyFileToOutputStream(sourceFile: File, outputStream: OutputStream): Long {
        sourceFile.inputStream().use { input ->
            var total = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                outputStream.write(buffer, 0, read)
                total += read
            }
            outputStream.flush()
            return total
        }
    }

    fun verifyWrittenBytes(sourceFile: File, writtenBytes: Long): CopyVerification {
        val expected = sourceFile.length()
        if (writtenBytes != expected) {
            error("Saved byte count mismatch: expected $expected bytes, wrote $writtenBytes bytes.")
        }
        return CopyVerification(expectedBytes = expected, writtenBytes = writtenBytes)
    }
}
