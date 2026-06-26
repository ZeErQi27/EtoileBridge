package com.zeerqi27.etoilebridge.core

import java.io.File

sealed class ConvertResult {
    data class Success(
        val outputFile: File,
        val songId: String,
        val warnings: List<String>,
        val logs: List<String>,
    ) : ConvertResult()

    data class NeedMetadata(
        val missingMetadata: MissingMetadata,
        val scannedInput: ScannedInput,
    ) : ConvertResult()

    data class UnsupportedPackStructure(
        val message: String,
        val candidateSongIds: List<String>,
        val scannedInput: ScannedInput,
    ) : ConvertResult()

    data class Failed(
        val message: String,
        val cause: Throwable?,
        val warnings: List<String>,
        val logs: List<String>,
        val workspaceDir: File?,
    ) : ConvertResult()
}
