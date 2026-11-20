package com.zeerqi27.etoile_bridge

import android.os.Process
import com.zeerqi27.etoilebridge.core.ChartConverter
import com.zeerqi27.etoilebridge.core.ConvertedChart
import java.io.File
import java.util.UUID

internal object AndroidEtoileLite : ChartConverter {
    private val library by lazy { System.loadLibrary("etoile_lite_jni") }

    // The .NET 10 Android runtime packs cover arm64 and x64, not 32-bit ARM.
    fun forCurrentProcess(): ChartConverter? = if (Process.is64Bit()) this else null

    override fun convert(source: File): ConvertedChart {
        try {
            library
        } catch (error: UnsatisfiedLinkError) {
            throw IllegalStateException("Could not load the bundled Etoile.Lite engine.", error)
        }
        val directory = source.absoluteFile.parentFile.resolve("lite-${UUID.randomUUID()}")
        check(directory.mkdir()) { "Could not create the Etoile.Lite working directory." }
        try {
            val aff = directory.resolve("chart.aff")
            val scene = directory.resolve("chart.sc.json")
            val error = ByteArray(4096)
            val result = nativeConvert(source.nativePath(), aff.nativePath(), scene.nativePath(), error)
            check(result == 0) {
                error.decodeToString(endIndex = error.indexOf(0).takeIf { it >= 0 } ?: error.size)
                    .ifBlank { "Etoile.Lite could not convert ${source.name}." }
            }
            check(aff.isFile && aff.length() > 0 && scene.isFile) { "Etoile.Lite did not produce its chart output." }
            return ConvertedChart(aff.readText(), scene.readText().ifBlank { null })
        } finally {
            directory.deleteRecursively()
        }
    }

    // JNI's GetStringUTFChars uses modified UTF-8, which changes supplementary characters in paths.
    private fun File.nativePath(): ByteArray = absolutePath.encodeToByteArray() + 0.toByte()

    private external fun nativeConvert(input: ByteArray, aff: ByteArray, scene: ByteArray, error: ByteArray): Int
}
