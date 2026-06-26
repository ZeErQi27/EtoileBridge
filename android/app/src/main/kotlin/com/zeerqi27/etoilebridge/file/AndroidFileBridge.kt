package com.zeerqi27.etoilebridge.file

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

data class SaveResult(
    val location: String,
    val expectedBytes: Long,
    val writtenBytes: Long,
    val queriedBytes: Long?,
)

class DownloadsRequiresCreateDocumentException :
    IllegalStateException("Current Android version requires Save As.")

object AndroidFileBridge {
    fun canUseMediaStoreDownloads(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.Q

    fun displayName(context: Context, uri: Uri): String {
        queryDisplayName(context, uri)?.let { return it }
        val treeDocumentName = runCatching {
            DocumentsContract.getTreeDocumentId(uri)
                .substringAfterLast(':')
                .takeIf { it.isNotBlank() }
        }.getOrNull()
        return treeDocumentName ?: uri.lastPathSegment ?: "Selected input"
    }

    fun copyUriToFile(context: Context, sourceUri: Uri, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open input URI.")
    }

    fun writeFileToUri(context: Context, sourceFile: File, targetUri: Uri): SaveResult {
        val written = context.contentResolver.openOutputStream(targetUri, "w")?.use { output ->
            VerifiedFileWriter.copyFileToOutputStream(sourceFile, output)
        } ?: error("Unable to open output URI for writing.")
        val verification = VerifiedFileWriter.verifyWrittenBytes(sourceFile, written)
        val queried = querySize(context, targetUri)
        verifyQueriedSize(verification.expectedBytes, queried)
        return SaveResult(
            location = queryDisplayName(context, targetUri) ?: targetUri.toString(),
            expectedBytes = verification.expectedBytes,
            writtenBytes = verification.writtenBytes,
            queriedBytes = queried,
        )
    }

    fun saveToDownloads(context: Context, sourceFile: File, fileName: String): SaveResult {
        if (!canUseMediaStoreDownloads(Build.VERSION.SDK_INT)) {
            throw DownloadsRequiresCreateDocumentException()
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create Downloads entry.")
        try {
            val written = resolver.openOutputStream(uri, "w")?.use { output ->
                VerifiedFileWriter.copyFileToOutputStream(sourceFile, output)
            } ?: error("Unable to open Downloads output stream.")
            val verification = VerifiedFileWriter.verifyWrittenBytes(sourceFile, written)
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            val queried = querySize(context, uri)
            verifyQueriedSize(verification.expectedBytes, queried)
            return SaveResult(
                location = "${Environment.DIRECTORY_DOWNLOADS}/$fileName",
                expectedBytes = verification.expectedBytes,
                writtenBytes = verification.writtenBytes,
                queriedBytes = queried,
            )
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun verifyQueriedSize(expected: Long, queried: Long?) {
        if (queried != null && queried >= 0 && queried != expected) {
            error("Saved file size mismatch: expected $expected bytes, queried $queried bytes.")
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        queryColumn(context, uri, OpenableColumns.DISPLAY_NAME) { cursor, index ->
            cursor.getString(index)
        }

    private fun querySize(context: Context, uri: Uri): Long? =
        queryColumn(context, uri, OpenableColumns.SIZE) { cursor, index ->
            if (cursor.isNull(index)) null else cursor.getLong(index)
        }

    private fun <T> queryColumn(
        context: Context,
        uri: Uri,
        column: String,
        read: (android.database.Cursor, Int) -> T?,
    ): T? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val index = cursor.getColumnIndex(column)
                    if (index < 0) null else read(cursor, index)
                }
        }.getOrNull()
}
