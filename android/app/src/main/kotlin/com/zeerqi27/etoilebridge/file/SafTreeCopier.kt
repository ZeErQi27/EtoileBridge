package com.zeerqi27.etoilebridge.file

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

class SafTreeCopier(private val context: Context) {
    fun copyTree(treeUri: Uri, targetDir: File) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Unable to open selected folder.")
        require(root.isDirectory) { "Selected URI is not a directory." }
        targetDir.deleteRecursively()
        targetDir.mkdirs()
        copyDirectory(root, targetDir)
    }

    private fun copyDirectory(sourceDir: DocumentFile, targetDir: File) {
        targetDir.mkdirs()
        sourceDir.listFiles().forEach { child ->
            val safeName = child.name?.takeIf { it.isNotBlank() } ?: return@forEach
            val target = targetDir.resolve(safeName)
            when {
                child.isDirectory -> copyDirectory(child, target)
                child.isFile -> copyFile(child, target)
            }
        }
    }

    private fun copyFile(sourceFile: DocumentFile, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        val uri = sourceFile.uri
        context.contentResolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read ${sourceFile.name ?: uri}")
    }
}
