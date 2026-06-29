package com.zeerqi27.etoilebridge.core

import java.io.File

object TempWorkspace {
    const val PROCESSED_AFF_DIR = "processed_aff"

    fun processedAffDir(workspaceDir: File, songId: String): File =
        workspaceDir.resolve(PROCESSED_AFF_DIR).resolve(songId)

    fun cleanProcessedAff(workspaceDir: File) {
        val dir = workspaceDir.resolve(PROCESSED_AFF_DIR)
        if (dir.exists()) dir.deleteRecursively()
    }

    fun cleanOldWorkspaces(root: File, olderThanMillis: Long = 24 * 60 * 60 * 1000L) {
        val now = System.currentTimeMillis()
        root.listFiles()
            ?.filter { it.isDirectory && now - it.lastModified() > olderThanMillis }
            ?.forEach { it.deleteRecursively() }
    }
}
