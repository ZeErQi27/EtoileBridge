package com.zeerqi27.etoilebridge.flutter.worker

import com.zeerqi27.etoilebridge.core.ChartConverter
import com.zeerqi27.etoilebridge.core.ConvertedChart
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

internal class EtoileLiteChartConverter(private val executable: File) : ChartConverter {
    init {
        require(executable.isFile) { "Etoile.Lite adapter is missing: $executable" }
    }

    override fun convert(source: File): ConvertedChart {
        val scratch = Files.createTempDirectory(source.parentFile.toPath(), "lite-").toFile()
        try {
            val aff = scratch.resolve("chart.aff")
            val scenecontrol = scratch.resolve("chart.sc.json")
            val diagnostics = scratch.resolve("diagnostics.txt")
            val process = ProcessBuilder(executable.absolutePath, source.absolutePath, aff.absolutePath, scenecontrol.absolutePath)
                .redirectErrorStream(true)
                .redirectOutput(diagnostics)
                .start()
            try {
                check(process.waitFor(30, TimeUnit.SECONDS)) { "Etoile.Lite timed out." }
                check(process.exitValue() == 0) {
                    diagnostics.readText(Charsets.UTF_8).trim().take(1600)
                        .ifBlank { "Etoile.Lite exited with code ${process.exitValue()}." }
                }
                check(aff.isFile && aff.length() > 0 && scenecontrol.isFile) { "Etoile.Lite did not produce its chart output." }
                return ConvertedChart(aff.readText(Charsets.UTF_8), scenecontrol.readText(Charsets.UTF_8).ifBlank { null })
            } finally {
                if (process.isAlive) {
                    process.destroyForcibly()
                    process.waitFor(5, TimeUnit.SECONDS)
                }
            }
        } finally {
            scratch.deleteRecursively()
        }
    }
}
