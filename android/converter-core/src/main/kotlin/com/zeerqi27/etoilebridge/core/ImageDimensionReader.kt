package com.zeerqi27.etoilebridge.core

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream

data class ImageDimension(
    val width: Int,
    val height: Int,
)

object ImageDimensionReader {
    fun read(file: File): ImageDimension? =
        when (file.extension.lowercase()) {
            "png" -> readPng(file)
            "jpg", "jpeg" -> readJpeg(file)
            else -> null
        }

    private fun readPng(file: File): ImageDimension? {
        val header = ByteArray(24)
        val read = file.inputStream().use { it.read(header) }
        if (read < header.size) return null
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        if (!header.copyOfRange(0, signature.size).contentEquals(signature)) return null
        val width = header.readIntBigEndian(16)
        val height = header.readIntBigEndian(20)
        return ImageDimension(width, height).takeIf { width > 0 && height > 0 }
    }

    private fun readJpeg(file: File): ImageDimension? {
        try {
            BufferedInputStream(file.inputStream()).use { input ->
                if (input.readByteOrThrow() != 0xFF || input.readByteOrThrow() != 0xD8) return null
                while (true) {
                    val marker = input.nextJpegMarker() ?: return null
                    if (marker in standaloneJpegMarkers) continue
                    if (marker in sofJpegMarkers) {
                        val length = input.readUnsignedShortOrThrow()
                        if (length < 7) return null
                        input.readByteOrThrow()
                        val height = input.readUnsignedShortOrThrow()
                        val width = input.readUnsignedShortOrThrow()
                        return ImageDimension(width, height).takeIf { width > 0 && height > 0 }
                    }
                    val length = input.readUnsignedShortOrThrow()
                    if (length < 2) return null
                    input.skipFully(length - 2)
                }
            }
        } catch (_: Exception) {
            return null
        }
        return null
    }

    private fun InputStream.nextJpegMarker(): Int? {
        var byte = read()
        while (byte != -1 && byte != 0xFF) {
            byte = read()
        }
        if (byte == -1) return null
        do {
            byte = read()
        } while (byte == 0xFF)
        return byte.takeIf { it != -1 }
    }

    private fun InputStream.readByteOrThrow(): Int {
        val value = read()
        if (value == -1) throw EOFException()
        return value
    }

    private fun InputStream.readUnsignedShortOrThrow(): Int =
        (readByteOrThrow() shl 8) or readByteOrThrow()

    private fun InputStream.skipFully(byteCount: Int) {
        var remaining = byteCount.toLong()
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() == -1) throw EOFException()
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun ByteArray.readIntBigEndian(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    private val standaloneJpegMarkers = setOf(0x01, 0xD0, 0xD1, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7, 0xD8, 0xD9)
    private val sofJpegMarkers = setOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)
}
