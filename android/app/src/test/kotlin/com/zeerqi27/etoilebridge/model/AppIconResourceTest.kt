package com.zeerqi27.etoilebridge.model

import java.io.File
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppIconResourceTest {
    @Test
    fun adaptiveIconResourcesExistAndManifestReferencesThem() {
        assertTrue(projectFile("src/main/res/drawable-nodpi/ic_launcher_background.png").isFile)
        assertTrue(projectFile("src/main/res/drawable-nodpi/ic_launcher_foreground.png").isFile)
        assertTrue(projectFile("src/main/res/drawable-nodpi/ic_launcher_monochrome.png").isFile)
        assertTrue(projectFile("src/main/res/drawable-nodpi/ic_splash_icon.png").isFile)
        assertEquals(432 to 432, pngSize(projectFile("src/main/res/drawable-nodpi/ic_launcher_foreground.png")))
        assertEquals(432 to 432, pngSize(projectFile("src/main/res/drawable-nodpi/ic_launcher_monochrome.png")))
        assertEquals(432 to 432, pngSize(projectFile("src/main/res/drawable-nodpi/ic_splash_icon.png")))
        assertTrue(alphaBounds(projectFile("src/main/res/drawable-nodpi/ic_launcher_foreground.png")).maxSide <= 230)
        assertTrue(alphaBounds(projectFile("src/main/res/drawable-nodpi/ic_launcher_monochrome.png")).maxSide <= 230)
        assertTrue(alphaBounds(projectFile("src/main/res/drawable-nodpi/ic_splash_icon.png")).maxSide <= 180)
        assertTrue(projectFile("src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText().contains("adaptive-icon"))
        assertTrue(projectFile("src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml").readText().contains("adaptive-icon"))

        val manifest = projectFile("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:icon="@mipmap/ic_launcher""""))
        assertTrue(manifest.contains("""android:roundIcon="@mipmap/ic_launcher_round""""))
    }

    private fun pngSize(file: File): Pair<Int, Int> {
        val bytes = file.readBytes()
        require(bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        val width = ByteBuffer.wrap(bytes, 16, 4).order(ByteOrder.BIG_ENDIAN).int
        val height = ByteBuffer.wrap(bytes, 20, 4).order(ByteOrder.BIG_ENDIAN).int
        return width to height
    }

    private fun alphaBounds(file: File): Bounds {
        val png = readRgbaPng(file)
        var minX = png.width
        var minY = png.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until png.height) {
            for (x in 0 until png.width) {
                val alpha = png.alphaAt(x, y)
                if (alpha > 8) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        return if (maxX < 0) Bounds(0, 0) else Bounds(maxX - minX + 1, maxY - minY + 1)
    }

    private data class Bounds(val width: Int, val height: Int) {
        val maxSide: Int get() = maxOf(width, height)
    }

    private data class DecodedPng(
        val width: Int,
        val height: Int,
        val bytesPerPixel: Int,
        val alphaOffset: Int?,
        val pixels: ByteArray,
    ) {
        fun alphaAt(x: Int, y: Int): Int {
            val offset = alphaOffset ?: return 255
            return pixels[(y * width + x) * bytesPerPixel + offset].toInt() and 0xff
        }
    }

    private fun readRgbaPng(file: File): DecodedPng {
        val input = DataInputStream(ByteArrayInputStream(file.readBytes()))
        val signature = ByteArray(8)
        input.readFully(signature)
        require(signature.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = 0
        val idat = mutableListOf<ByteArray>()
        while (input.available() > 0) {
            val length = input.readInt()
            val typeBytes = ByteArray(4)
            input.readFully(typeBytes)
            val type = typeBytes.toString(Charsets.US_ASCII)
            val data = ByteArray(length)
            input.readFully(data)
            input.readInt()
            when (type) {
                "IHDR" -> {
                    val header = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                    width = header.int
                    height = header.int
                    bitDepth = header.get().toInt() and 0xff
                    colorType = header.get().toInt() and 0xff
                }
                "IDAT" -> idat += data
                "IEND" -> break
            }
        }
        require(bitDepth == 8) { "Only 8-bit PNG icons are supported by this test." }
        val bytesPerPixel = when (colorType) {
            6 -> 4
            4 -> 2
            2 -> 3
            else -> error("Unsupported PNG color type for icon test: $colorType")
        }
        val alphaOffset = when (colorType) {
            6 -> 3
            4 -> 1
            else -> null
        }
        val inflated = InflaterInputStream(ByteArrayInputStream(idat.flattenBytes())).readBytes()
        val stride = width * bytesPerPixel
        val pixels = ByteArray(height * stride)
        var sourceOffset = 0
        for (y in 0 until height) {
            val filter = inflated[sourceOffset++].toInt() and 0xff
            val row = ByteArray(stride)
            inflated.copyInto(row, 0, sourceOffset, sourceOffset + stride)
            sourceOffset += stride
            val previousOffset = (y - 1) * stride
            for (x in 0 until stride) {
                val left = if (x >= bytesPerPixel) row[x - bytesPerPixel].toInt() and 0xff else 0
                val up = if (y > 0) pixels[previousOffset + x].toInt() and 0xff else 0
                val upLeft = if (y > 0 && x >= bytesPerPixel) pixels[previousOffset + x - bytesPerPixel].toInt() and 0xff else 0
                val value = row[x].toInt() and 0xff
                row[x] = when (filter) {
                    0 -> value
                    1 -> value + left
                    2 -> value + up
                    3 -> value + ((left + up) / 2)
                    4 -> value + paeth(left, up, upLeft)
                    else -> error("Unsupported PNG filter: $filter")
                }.toByte()
            }
            row.copyInto(pixels, y * stride)
        }
        return DecodedPng(width, height, bytesPerPixel, alphaOffset, pixels)
    }

    private fun List<ByteArray>.flattenBytes(): ByteArray {
        val output = ByteArray(sumOf { it.size })
        var offset = 0
        forEach {
            it.copyInto(output, offset)
            offset += it.size
        }
        return output
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }

    private fun projectFile(path: String): File =
        sequenceOf(File(path), File("app", path))
            .firstOrNull { it.exists() }
            ?: File(path)
}
