package com.zeerqi27.etoilebridge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun ImageDetailDialog(
    title: String,
    label: String,
    fileName: String?,
    filePath: String?,
    manual: Boolean,
    texts: AppText,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onClearManual: (() -> Unit)?,
) {
    val preview = rememberImagePreview(filePath, maxWidthPx = 1200, maxHeightPx = 900)
    var showPath by rememberSaveable(filePath) { mutableStateOf(ImageDetailLayoutRules.PathInitiallyExpanded) }

    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints {
            val widthDp = maxWidth.value.toInt()
            val sideBySide = ImageDetailLayoutRules.useSideBySide(widthDp)
            val imageHeight = ImageDetailLayoutRules.imageMaxHeightDp(widthDp).dp
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .widthIn(max = ImageDetailLayoutRules.dialogMaxWidthDp(widthDp).dp)
                    .heightIn(max = if (sideBySide) 680.dp else 760.dp),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (sideBySide) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 560.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            DetailImagePreview(
                                data = preview,
                                label = label,
                                modifier = Modifier
                                    .weight(1.35f)
                                    .height(imageHeight),
                            )
                            DetailInfoColumn(
                                label = label,
                                fileName = fileName,
                                filePath = filePath,
                                manual = manual,
                                preview = preview,
                                texts = texts,
                                showPath = showPath,
                                onTogglePath = { showPath = !showPath },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(max = 560.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 620.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            DetailImagePreview(
                                data = preview,
                                label = label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(imageHeight),
                            )
                            DetailInfoColumn(
                                label = label,
                                fileName = fileName,
                                filePath = filePath,
                                manual = manual,
                                preview = preview,
                                texts = texts,
                                showPath = showPath,
                                onTogglePath = { showPath = !showPath },
                            )
                        }
                    }
                    AdaptiveActionRow {
                        Button(onClick = onReplace) { Text(texts.changeImage) }
                        if (manual && onClearManual != null) {
                            OutlinedButton(onClick = onClearManual) { Text(texts.clearManualSelection) }
                        }
                        TextButton(onClick = onDismiss) { Text(texts.close) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailImagePreview(
    data: ImagePreviewData,
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = data.image
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SymbolIcon(AppSymbols.Image, contentDescription = null, size = 42.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DetailInfoColumn(
    label: String,
    fileName: String?,
    filePath: String?,
    manual: Boolean,
    preview: ImagePreviewData,
    texts: AppText,
    showPath: Boolean,
    onTogglePath: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailInfoSection {
            KeyValue(texts.resourceType, label)
            KeyValue(texts.savedFileName, fileName ?: texts.notDetected)
            KeyValue(texts.source, if (manual) texts.manualTag else if (fileName != null) texts.automaticSource else texts.notDetected)
            KeyValue(texts.fileSize, preview.fileSizeBytes?.formatBytes() ?: texts.none)
            KeyValue(
                texts.imageDimensions,
                if (preview.width != null && preview.height != null) "${preview.width} x ${preview.height}" else texts.none,
            )
        }
        when {
            preview.fileMissing -> Text(imageFileMissingText(texts), color = MaterialTheme.colorScheme.error)
            preview.decodeFailed && fileName != null -> Text(texts.imagePreviewFailed, color = MaterialTheme.colorScheme.error)
        }
        if (!filePath.isNullOrBlank()) {
            AdaptiveActionRow {
                TextButton(onClick = onTogglePath) {
                    Text(if (showPath) texts.hideDetailedPath else texts.showDetailedPath)
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(filePath)) }) {
                    Text(copyPathText(texts))
                }
            }
            if (showPath) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    SelectionContainer {
                        Text(
                            filePath,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = true,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoSection(content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

fun imageFileMissingText(texts: AppText): String =
    if (texts.close == "Close") "File not found" else "文件不存在"

fun copyPathText(texts: AppText): String =
    if (texts.close == "Close") "Copy path" else "复制路径"
