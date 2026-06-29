package com.zeerqi27.etoilebridge.core

import java.io.File

enum class InputKind {
    SingleSong,
    PackFolder,
    Unknown,
}

data class ScannedInput(
    val workspaceDir: File,
    val kind: InputKind,
    val songlistFile: File?,
    val packlistFile: File?,
    val rootAffFiles: Map<Int, File>,
    val songDirectories: Map<String, File>,
    val ignoredAffFiles: List<File> = emptyList(),
) {
    val candidateSongIds: List<String> = songDirectories.keys.sorted()
}

class InputScanner {
    fun scan(workspaceDir: File): ScannedInput {
        require(workspaceDir.isDirectory) { "workspaceDir must be a directory: $workspaceDir" }

        val songlistFile = findSonglistFile(workspaceDir)
        val packlistFile = findPacklistFile(workspaceDir)
        val rootAffFiles = findAffFiles(workspaceDir)
        val rootIgnoredAffFiles = findIgnoredAffFiles(workspaceDir)

        if (rootAffFiles.isNotEmpty() || rootIgnoredAffFiles.isNotEmpty()) {
            return ScannedInput(
                workspaceDir = workspaceDir,
                kind = InputKind.SingleSong,
                songlistFile = songlistFile,
                packlistFile = packlistFile,
                rootAffFiles = rootAffFiles,
                songDirectories = emptyMap(),
                ignoredAffFiles = rootIgnoredAffFiles,
            )
        }

        val childSongDirs = workspaceDir.listFiles()
            ?.filter { it.isDirectory && (findAffFiles(it).isNotEmpty() || findIgnoredAffFiles(it).isNotEmpty()) }
            ?.associateBy { it.name }
            .orEmpty()
        val childIgnoredAffFiles = childSongDirs.values.flatMap { findIgnoredAffFiles(it) }

        val kind = if (childSongDirs.isNotEmpty()) {
            InputKind.PackFolder
        } else {
            InputKind.Unknown
        }

        return ScannedInput(
            workspaceDir = workspaceDir,
            kind = kind,
            songlistFile = songlistFile,
            packlistFile = packlistFile,
            rootAffFiles = rootAffFiles,
            songDirectories = childSongDirs,
            ignoredAffFiles = rootIgnoredAffFiles + childIgnoredAffFiles,
        )
    }

    companion object {
        fun findAffFiles(songDir: File): Map<Int, File> =
            (0..4).mapNotNull { ratingClass ->
                songDir.resolve("$ratingClass.aff").takeIf { it.isFile }?.let { ratingClass to it }
            }.toMap()

        fun findIgnoredAffFiles(songDir: File): List<File> {
            val standardNames = (0..4).map { "$it.aff" }.toSet()
            return songDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("aff", ignoreCase = true) && it.name !in standardNames }
                .orEmpty()
                .sortedBy { it.name }
        }

        fun findSonglistFile(dir: File): File? =
            listOf("songlist", "songlist.json", "songlist.txt", "slst", "slst.json", "slst.txt")
                .firstNotNullOfOrNull { name -> dir.resolve(name).takeIf { it.isFile } }

        fun findPacklistFile(dir: File): File? =
            listOf("packlist", "packlist.json", "packlist.txt")
                .firstNotNullOfOrNull { name -> dir.resolve(name).takeIf { it.isFile } }
    }
}
