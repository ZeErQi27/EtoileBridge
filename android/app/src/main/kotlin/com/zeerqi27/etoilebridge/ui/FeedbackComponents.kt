package com.zeerqi27.etoilebridge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeerqi27.etoilebridge.model.UiConvertState
import com.zeerqi27.etoilebridge.model.UiFeedbackStateRules
import com.zeerqi27.etoilebridge.model.UiLanguage
import com.zeerqi27.etoilebridge.model.UiOperationPhase
import com.zeerqi27.etoilebridge.model.UiPackConvertState
import com.zeerqi27.etoilebridge.model.UiSaveStatus

@Composable
fun OperationStatusCard(
    phase: UiOperationPhase,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    statusLabel: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val busy = phase in setOf(
        UiOperationPhase.Copying,
        UiOperationPhase.Extracting,
        UiOperationPhase.Scanning,
        UiOperationPhase.Converting,
        UiOperationPhase.Validating,
        UiOperationPhase.Saving,
    )
    EdgeAwareCard(
        modifier = modifier
            .tapFeedbackOnly(),
        containerColor = when (phase) {
            UiOperationPhase.Failed -> MaterialTheme.colorScheme.errorContainer
            UiOperationPhase.Saved,
            UiOperationPhase.Ready -> MaterialTheme.colorScheme.secondaryContainer
            UiOperationPhase.PendingSave -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crossfade(targetState = busy, label = "operation-icon") { isBusy ->
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    SymbolIcon(symbolForPhase(phase), contentDescription = null, color = colorForPhase(phase))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Crossfade(targetState = title, label = "operation-title") { value ->
                    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Crossfade(targetState = detail, label = "operation-detail") { value ->
                    Text(value, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (actionLabel != null && onAction != null) {
                AssistChip(onClick = onAction, label = { Text(actionLabel) })
            } else {
                AssistChip(onClick = {}, label = { Text(statusLabel ?: phaseLabel(phase)) })
            }
        }
    }
}

@Composable
fun SingleResultCard(
    state: UiConvertState,
    texts: AppText,
    onSaveDownloads: () -> Unit,
    onSaveOutput: () -> Unit,
    busy: Boolean,
) {
    AnimatedVisibility(
        visible = state.pendingOutputFile != null || state.saveStatus == UiSaveStatus.Saved || state.saveStatus == UiSaveStatus.Failed,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        EdgeAwareCard(
            modifier = Modifier
                .tapFeedbackOnly(),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    AppSymbols.Download,
                    if (state.saveStatus == UiSaveStatus.Saved) texts.saveStatusLabel(state.saveStatus) else texts.saveStatusLabel(UiSaveStatus.Pending),
                )
                KeyValue(texts.pendingFile, state.pendingOutputFile?.name ?: state.savedFileName ?: texts.none)
                KeyValue(texts.pendingFileSize, state.pendingOutputFileSize?.formatBytes() ?: state.savedFileSize?.formatBytes() ?: texts.none)
                KeyValue(texts.identifierPreview, state.metadataDraft.identifierOverride.ifBlank { defaultSingleIdentifier(state) })
                KeyValue("directory", state.songId ?: state.metadataDraft.songId.ifBlank { texts.notDetected })
                KeyValue(texts.savedMethod, state.savedMethod ?: texts.notSaved)
                KeyValue(texts.savedLocation, state.savedLocation ?: texts.notSaved)
                AdaptiveActionRow {
                    if (state.canUseMediaStoreDownloads) {
                        OutlinedButton(
                            onClick = onSaveDownloads,
                            enabled = UiFeedbackStateRules.canSaveSingle(state, busy) && state.canSaveDownloads,
                        ) {
                            FeedbackButtonIcon(AppSymbols.Download, texts.saveDownloads)
                        }
                    }
                    OutlinedButton(onClick = onSaveOutput, enabled = UiFeedbackStateRules.canSaveSingle(state, busy)) {
                        FeedbackButtonIcon(AppSymbols.SaveAs, texts.saveAs)
                    }
                }
            }
        }
    }
}

@Composable
fun PackResultCard(
    state: UiPackConvertState,
    onSaveDownloads: () -> Unit,
    onSaveOutput: () -> Unit,
    busy: Boolean,
) {
    AnimatedVisibility(
        visible = state.pendingOutputFile != null || state.saveStatus == UiSaveStatus.Saved || state.bundleValidationPassed == false,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        EdgeAwareCard(
            modifier = Modifier
                .tapFeedbackOnly(),
            containerColor = if (state.bundleValidationPassed == false) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    AppSymbols.Download,
                    if (state.bundleValidationPassed == false) "曲包结构验证失败" else "曲包结果",
                )
                KeyValue("输出文件", state.pendingOutputFile?.name ?: state.outputFileName)
                KeyValue("文件大小", state.pendingOutputFileSize?.formatBytes() ?: state.savedFileSize?.formatBytes() ?: "无")
                KeyValue("packName", state.packName)
                KeyValue("pack identifier", "${state.publisherId}.${state.packId}.pack")
                KeyValue("levels", state.entries.count { it.enabled && it.effectiveCanPack }.toString())
                KeyValue("pack image", state.packImageFileName ?: "未识别")
                state.bundleValidationPassed?.let {
                    AssistChip(onClick = {}, label = { Text(if (it) "validator passed" else "validator failed") })
                }
                if (state.bundleValidationErrors.isNotEmpty()) {
                    state.bundleValidationErrors.take(6).forEach {
                        Text("• $it", color = MaterialTheme.colorScheme.error)
                    }
                }
                AdaptiveActionRow {
                    if (state.canUseMediaStoreDownloads) {
                        OutlinedButton(
                            onClick = onSaveDownloads,
                            enabled = UiFeedbackStateRules.canSavePack(state, busy) && state.canSaveDownloads,
                        ) {
                            FeedbackButtonIcon(AppSymbols.Download, "保存到 Downloads")
                        }
                    }
                    OutlinedButton(onClick = onSaveOutput, enabled = UiFeedbackStateRules.canSavePack(state, busy)) {
                        FeedbackButtonIcon(AppSymbols.SaveAs, "另存为")
                    }
                }
                KeyValue("最终位置", state.savedLocation ?: "未保存")
            }
        }
    }
}

@Composable
fun FeedbackMessagesCard(
    warnings: List<String>,
    errorMessage: String?,
    errorDetails: String?,
    validatorErrors: List<String> = emptyList(),
) {
    AnimatedVisibility(
        visible = warnings.isNotEmpty() || errorMessage != null || validatorErrors.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        EdgeAwareCard(
            modifier = Modifier
                .tapFeedbackOnly(),
            containerColor = if (errorMessage != null || validatorErrors.isNotEmpty()) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (errorMessage != null) {
                    SectionHeader(AppSymbols.Error, "错误")
                    Text(errorMessage, fontWeight = FontWeight.SemiBold)
                    CollapsibleText("错误详情", errorDetails.orEmpty())
                }
                if (validatorErrors.isNotEmpty()) {
                    SectionHeader(AppSymbols.Warning, "结构验证")
                    validatorErrors.take(8).forEach { Text("• $it") }
                }
                if (warnings.isNotEmpty()) {
                    SectionHeader(AppSymbols.Warning, "Warnings (${warnings.size})")
                    CollapsibleList(warnings)
                }
            }
        }
    }
}

@Composable
fun LocalizedFeedbackMessagesCard(
    warnings: List<String>,
    errorMessage: String?,
    errorDetails: String?,
    validatorErrors: List<String> = emptyList(),
    language: UiLanguage,
) {
    AnimatedVisibility(
        visible = warnings.isNotEmpty() || errorMessage != null || validatorErrors.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val texts = textFor(language)
        EdgeAwareCard(
            modifier = Modifier
                .tapFeedbackOnly(),
            containerColor = if (errorMessage != null || validatorErrors.isNotEmpty()) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (errorMessage != null) {
                    SectionHeader(AppSymbols.Error, texts.error)
                    Text(localizeKnownMessage(errorMessage, language), fontWeight = FontWeight.SemiBold)
                    LocalizedCollapsibleText(texts.errorDetails, errorDetails.orEmpty(), language)
                }
                if (validatorErrors.isNotEmpty()) {
                    SectionHeader(AppSymbols.Warning, if (language == UiLanguage.English) "Validator" else "结构验证")
                    validatorErrors.take(8).forEach { Text("- ${localizeKnownMessage(it, language)}") }
                }
                if (warnings.isNotEmpty()) {
                    SectionHeader(AppSymbols.Warning, "${texts.warnings} (${warnings.size})")
                    LocalizedCollapsibleList(warnings, language)
                }
            }
        }
    }
}

@Composable
private fun LocalizedCollapsibleList(lines: List<String>, language: UiLanguage) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val show = if (language == UiLanguage.English) "Show all" else "查看全部"
    val hide = if (language == UiLanguage.English) "Hide" else "收起"
    TextButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) hide else show)
    }
    AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            lines.forEach { Text("- ${localizeKnownMessage(it, language)}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun LocalizedCollapsibleText(title: String, text: String, language: UiLanguage) {
    if (text.isBlank()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val show = if (language == UiLanguage.English) "Show $title" else "显示$title"
    val hide = if (language == UiLanguage.English) "Hide $title" else "隐藏$title"
    TextButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) hide else show)
    }
    AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun EmptyStateCard(symbol: String, title: String, detail: String) {
    EdgeAwareCard(
        modifier = Modifier
            .tapFeedbackOnly(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolIcon(symbol, contentDescription = null, color = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SectionHeader(symbol: String, title: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SymbolIcon(symbol, contentDescription = null, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FeedbackButtonIcon(symbol: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        SymbolIcon(symbol, contentDescription = null, size = 20.dp)
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CollapsibleList(lines: List<String>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "收起" else "查看全部")
    }
    AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun CollapsibleText(title: String, text: String) {
    if (text.isBlank()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "隐藏$title" else "显示$title")
    }
    AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

private fun defaultSingleIdentifier(state: UiConvertState): String {
    val publisherId = state.metadataDraft.publisherId.ifBlank { "etoilebridge" }
    val levelId = state.metadataDraft.levelId.ifBlank { state.songId ?: state.metadataDraft.songId }
    return if (levelId.isBlank()) publisherId else "$publisherId.$levelId"
}

private fun symbolForPhase(phase: UiOperationPhase): String =
    when (phase) {
        UiOperationPhase.Idle -> AppSymbols.Info
        UiOperationPhase.Copying,
        UiOperationPhase.Extracting -> AppSymbols.Folder
        UiOperationPhase.Scanning -> AppSymbols.Search
        UiOperationPhase.Ready -> AppSymbols.Check
        UiOperationPhase.Converting,
        UiOperationPhase.Validating -> AppSymbols.Convert
        UiOperationPhase.PendingSave,
        UiOperationPhase.Saving -> AppSymbols.SaveAs
        UiOperationPhase.Saved -> AppSymbols.Download
        UiOperationPhase.Failed -> AppSymbols.Error
    }

fun operationPhaseLabel(phase: UiOperationPhase, language: UiLanguage): String {
    val english = language == UiLanguage.English
    return when (phase) {
        UiOperationPhase.Idle -> if (english) "Waiting" else "待开始"
        UiOperationPhase.Copying -> if (english) "Copying" else "复制中"
        UiOperationPhase.Extracting -> if (english) "Extracting" else "解压中"
        UiOperationPhase.Scanning -> if (english) "Scanning" else "扫描中"
        UiOperationPhase.Ready -> if (english) "Continue" else "可继续"
        UiOperationPhase.Converting -> if (english) "Converting" else "转换中"
        UiOperationPhase.Validating -> if (english) "Validating" else "验证中"
        UiOperationPhase.PendingSave -> if (english) "Pending save" else "待保存"
        UiOperationPhase.Saving -> if (english) "Saving" else "保存中"
        UiOperationPhase.Saved -> if (english) "Saved" else "已保存"
        UiOperationPhase.Failed -> if (english) "Failed" else "失败"
    }
}

private fun phaseLabel(phase: UiOperationPhase): String =
    operationPhaseLabel(phase, UiLanguage.ZhHans)

@Composable
private fun colorForPhase(phase: UiOperationPhase): Color =
    when (phase) {
        UiOperationPhase.Failed -> MaterialTheme.colorScheme.error
        UiOperationPhase.Saved,
        UiOperationPhase.Ready -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
