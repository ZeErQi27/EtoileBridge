package com.zeerqi27.etoilebridge.file

import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

class ZipArchiveExtractor {
    fun extract(zipFile: File, targetDir: File): File {
        val lowerName = zipFile.name.lowercase()
        if (lowerName.endsWith(".rar") || lowerName.endsWith(".7z")) {
            error("Unsupported archive format. Please convert it to zip first.")
        }
        if (!lowerName.endsWith(".zip")) {
            error("Only zip archives are supported.")
        }

        var lastError: Throwable? = null
        for (charset in listOf(Charsets.UTF_8, Charset.forName("GBK"))) {
            targetDir.deleteRecursively()
            targetDir.mkdirs()
            val result = runCatching { extractWithCharset(zipFile, targetDir, charset) }
            if (result.isSuccess) return targetDir
            lastError = result.exceptionOrNull()
        }
        targetDir.deleteRecursively()
        throw IllegalStateException("ZIP extract failed: ${lastError?.message}", lastError)
    }

    private fun extractWithCharset(zipFile: File, targetDir: File, charset: Charset) {
        val targetCanonical = targetDir.canonicalFile
        ZipInputStream(zipFile.inputStream().buffered(), charset).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = targetDir.resolve(entry.name).canonicalFile
                if (!output.path.startsWith(targetCanonical.path + File.separator) && output != targetCanonical) {
                    error("Blocked unsafe zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }
}
