package com.zeerqi27.etoilebridge.ui

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

@Immutable
data class ImagePreviewData(
    val file: File?,
    val image: ImageBitmap?,
    val width: Int?,
    val height: Int?,
    val fileSizeBytes: Long?,
    val decodeFailed: Boolean,
    val fileMissing: Boolean = false,
)

data class ImagePreviewCacheKey(
    val path: String?,
    val lastModified: Long?,
    val fileSizeBytes: Long?,
    val maxWidthPx: Int,
    val maxHeightPx: Int,
)

data class SampledBitmapData(
    val file: File?,
    val bitmap: Bitmap?,
    val originalWidth: Int?,
    val originalHeight: Int?,
    val fileSizeBytes: Long?,
)

@Composable
fun rememberImagePreview(
    filePath: String?,
    maxWidthPx: Int = 512,
    maxHeightPx: Int = 512,
): ImagePreviewData =
    remember(imagePreviewCacheKey(filePath, maxWidthPx, maxHeightPx)) {
        loadImagePreview(filePath, maxWidthPx, maxHeightPx)
    }

fun imagePreviewCacheKey(
    filePath: String?,
    maxWidthPx: Int = 512,
    maxHeightPx: Int = 512,
): ImagePreviewCacheKey {
    val file = filePath?.let(::File)
    return ImagePreviewCacheKey(
        path = filePath,
        lastModified = file?.takeIf { it.isFile }?.lastModified(),
        fileSizeBytes = file?.takeIf { it.isFile }?.length(),
        maxWidthPx = maxWidthPx,
        maxHeightPx = maxHeightPx,
    )
}

fun readImageDimensions(filePath: String?): Pair<Int, Int>? {
    val file = filePath?.let(::File)?.takeIf { it.isFile } ?: return null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    return options.outWidth
        .takeIf { it > 0 }
        ?.let { width -> width to options.outHeight }
        ?.takeIf { it.second > 0 }
}

@Composable
fun rememberCroppedImagePreview(
    filePath: String?,
    centerX: Float,
    centerY: Float,
    cropSize: Float,
    outputSizePx: Int = 256,
): ImageBitmap? {
    val sampled = rememberSampledBitmap(filePath, maxWidthPx = 768, maxHeightPx = 768)
    return remember(
        sampled.bitmap,
        (centerX * 1000).roundToInt(),
        (centerY * 1000).roundToInt(),
        (cropSize * 1000).roundToInt(),
        outputSizePx,
    ) {
        createCroppedImagePreview(sampled.bitmap, centerX, centerY, cropSize, outputSizePx)
    }
}

@Composable
fun rememberSampledBitmap(
    filePath: String?,
    maxWidthPx: Int = 1024,
    maxHeightPx: Int = 1024,
): SampledBitmapData =
    remember(imagePreviewCacheKey(filePath, maxWidthPx, maxHeightPx)) {
        loadSampledBitmap(filePath, maxWidthPx, maxHeightPx)
    }

private fun loadImagePreview(
    filePath: String?,
    maxWidthPx: Int,
    maxHeightPx: Int,
): ImagePreviewData {
    if (filePath.isNullOrBlank()) {
        return ImagePreviewData(null, null, null, null, null, decodeFailed = false)
    }
    val rawFile = File(filePath)
    val file = rawFile.takeIf { it.isFile }
        ?: return ImagePreviewData(rawFile, null, null, null, null, decodeFailed = false, fileMissing = true)
    val bounds = readImageDimensions(file.absolutePath)
    if (bounds == null) {
        return ImagePreviewData(file, null, null, null, file.length(), decodeFailed = true)
    }
    val (width, height) = bounds
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(width, height, maxWidthPx, maxHeightPx)
    }
    val bitmap = runCatching {
        BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
    }.getOrNull()
    return ImagePreviewData(
        file = file,
        image = bitmap,
        width = width,
        height = height,
        fileSizeBytes = file.length(),
        decodeFailed = bitmap == null,
    )
}

private fun loadSampledBitmap(
    filePath: String?,
    maxWidthPx: Int,
    maxHeightPx: Int,
): SampledBitmapData {
    if (filePath.isNullOrBlank()) return SampledBitmapData(null, null, null, null, null)
    val rawFile = File(filePath)
    val file = rawFile.takeIf { it.isFile } ?: return SampledBitmapData(rawFile, null, null, null, null)
    val bounds = readImageDimensions(file.absolutePath) ?: return SampledBitmapData(file, null, null, null, file.length())
    val (width, height) = bounds
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(width, height, maxWidthPx, maxHeightPx)
    }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
    return SampledBitmapData(file, decoded, width, height, file.length())
}

private fun createCroppedImagePreview(
    decoded: Bitmap?,
    centerX: Float,
    centerY: Float,
    cropSize: Float,
    outputSizePx: Int,
): ImageBitmap? {
    decoded ?: return null
    return runCatching {
        val minSide = minOf(decoded.width, decoded.height)
        val size = (minSide * cropSize.coerceIn(0.05f, 1f)).roundToInt().coerceAtLeast(1)
        val left = ((decoded.width * centerX.coerceIn(0f, 1f)) - (size / 2f))
            .roundToInt()
            .coerceIn(0, (decoded.width - size).coerceAtLeast(0))
        val top = ((decoded.height * centerY.coerceIn(0f, 1f)) - (size / 2f))
            .roundToInt()
            .coerceIn(0, (decoded.height - size).coerceAtLeast(0))
        val crop = Bitmap.createBitmap(decoded, left, top, size.coerceAtMost(decoded.width), size.coerceAtMost(decoded.height))
        val scaled = Bitmap.createScaledBitmap(crop, outputSizePx, outputSizePx, true)
        scaled.asImageBitmap()
    }.getOrNull()
}

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    var sample = 1
    val safeReqWidth = max(1, reqWidth)
    val safeReqHeight = max(1, reqHeight)
    while (width / sample > safeReqWidth * 2 || height / sample > safeReqHeight * 2) {
        sample *= 2
    }
    return sample
}
