package com.zeerqi27.etoilebridge.file

import java.io.File
import java.util.UUID

object CacheCleaner {
    private const val CACHE_ROOT_NAME = "etoilebridge"
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun cacheRoot(cacheDir: File): File =
        cacheDir.resolve(CACHE_ROOT_NAME).apply { mkdirs() }

    fun createRunDir(cacheDir: File): File {
        val runId = "${System.currentTimeMillis()}-${UUID.randomUUID()}"
        return cacheRoot(cacheDir).resolve(runId).apply { mkdirs() }
    }

    fun cleanOlderThan(cacheDir: File, maxAgeMillis: Long = DAY_MILLIS) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        cacheRoot(cacheDir).listFiles()
            ?.filter { it.isDirectory && it.lastModified() < cutoff }
            .orEmpty()
            .forEach { it.deleteRecursively() }
    }

    fun cleanAll(cacheDir: File) {
        cacheRoot(cacheDir).deleteRecursively()
    }

    fun deleteRun(runDir: File?) {
        runDir?.deleteRecursively()
    }
}
