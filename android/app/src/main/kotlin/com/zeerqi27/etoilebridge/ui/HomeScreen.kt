package com.zeerqi27.etoilebridge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zeerqi27.etoilebridge.core.DifficultyMapper
import com.zeerqi27.etoilebridge.model.ManualResourceKind
import com.zeerqi27.etoilebridge.model.ResponsiveLayoutRules
import com.zeerqi27.etoilebridge.model.SingleSongStateRules
import com.zeerqi27.etoilebridge.model.UiFeedbackStateRules
import com.zeerqi27.etoilebridge.model.UiAffMappingItem
import com.zeerqi27.etoilebridge.model.UiAppearanceOptions
import com.zeerqi27.etoilebridge.model.UiAppPage
import com.zeerqi27.etoilebridge.model.UiArcCreateAccent
import com.zeerqi27.etoilebridge.model.UiArcCreateParticle
import com.zeerqi27.etoilebridge.model.UiArcCreateSingleLine
import com.zeerqi27.etoilebridge.model.UiArcCreateTrack
import com.zeerqi27.etoilebridge.model.UiConvertOptions
import com.zeerqi27.etoilebridge.model.UiConvertState
import com.zeerqi27.etoilebridge.model.UiDifficultyDraft
import com.zeerqi27.etoilebridge.model.UiLanguage
import com.zeerqi27.etoilebridge.model.UiMetadataDraft
import com.zeerqi27.etoilebridge.model.UiSaveStatus
import com.zeerqi27.etoilebridge.model.UiScanStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: UiConvertState,
    onSelectInput: () -> Unit,
    onSelectZip: () -> Unit,
    onScan: () -> Unit,
    onConvert: () -> Unit,
    onSaveDownloads: () -> Unit,
    onSaveOutput: () -> Unit,
    onSelectManualResource: (ManualResourceKind) -> Unit,
    onClearManualResource: (ManualResourceKind) -> Unit,
    onClearCache: () -> Unit,
    onOptionsChange: (UiConvertOptions) -> Unit,
    onAppearanceChange: (UiAppearanceOptions) -> Unit,
    onLanguageChange: (UiLanguage) -> Unit,
    onMetadataSave: (UiMetadataDraft) -> Unit,
    onAffMappingsSave: (List<UiAffMappingItem>) -> Unit,
    onSwitchToPack: () -> Unit,
    onSwitchToCharacter: () -> Unit,
) {
    val texts = textFor(state.language)
    val busy = state.isCopying || state.isScanning || state.isConverting || state.isSaving
    var showMetadataEditor by rememberSaveable { mutableStateOf(false) }
    var showAffMapping by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val phase = UiFeedbackStateRules.singlePhase(state)
    val convertSaveRequester = remember { BringIntoViewRequester() }
    var highlightConvertSave by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val scrollState = rememberScrollState()
    val continueTarget = UiFeedbackStateRules.singleContinueTarget(state)
    val continueAction: (() -> Unit)? = if (continueTarget != null) {
        {
            scope.launch {
                delay(110)
                convertSaveRequester.bringIntoView()
                highlightConvertSave = true
                delay(650)
                highlightConvertSave = false
            }
        }
    } else {
        null
    }

    LaunchedEffect(state.scanStatus) {
        when (state.scanStatus) {
            UiScanStatus.Scanned -> snackbarHostState.showSnackbar(texts.scanComplete)
            UiScanStatus.Failed -> snackbarHostState.showSnackbar(texts.scanFailedMessage)
            else -> Unit
        }
    }
    LaunchedEffect(state.pendingOutputFile) {
        if (state.pendingOutputFile != null) snackbarHostState.showSnackbar(texts.conversionCompletePendingSave)
    }
    LaunchedEffect(state.saveStatus) {
        when (state.saveStatus) {
            UiSaveStatus.Saved -> snackbarHostState.showSnackbar(texts.saveStatusLabel(UiSaveStatus.Saved))
            UiSaveStatus.Failed -> snackbarHostState.showSnackbar(texts.saveFailedPending)
            UiSaveStatus.Canceled -> snackbarHostState.showSnackbar(texts.saveStatusLabel(UiSaveStatus.Canceled))
            else -> Unit
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(texts.operationFailed) }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            ) {
                EtoileTopBar(
                    currentPage = UiAppPage.SingleSong,
                    language = state.language,
                    busy = busy,
                    clearCacheEnabled = !state.canSave,
                    deviceSdkInt = state.deviceSdkInt,
                    deviceRelease = state.deviceRelease,
                    onPageSelected = {
                        when (it) {
                            UiAppPage.SingleSong -> Unit
                            UiAppPage.PackBundle -> onSwitchToPack()
                            UiAppPage.Character -> onSwitchToCharacter()
                        }
                    },
                    onLanguageChange = onLanguageChange,
                    onClearCache = {
                        onClearCache()
                        scope.launch { snackbarHostState.showSnackbar(texts.cacheCleared) }
                    },
                    modifier = Modifier.zIndex(2f),
                )
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                ScrollContentViewport(modifier = Modifier.fillMaxSize().zIndex(1f), scrollState = scrollState) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp)
                            .padding(top = ResponsiveLayoutRules.TopContentPaddingDp.dp, bottom = bottomInset + 32.dp),
                    ) {
                        val wide = ResponsiveLayoutRules.useTwoColumns(maxWidth.value.toInt())
                        val showResourceCard = state.inputName != null
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
                                HeroCard(state, texts, busy, onSelectInput, onSelectZip, onScan)
                                OperationStatusCard(
                                    phase = phase,
                                    title = singlePhaseTitle(state, texts),
                                    detail = singlePhaseDetail(state, texts),
                                    statusLabel = operationPhaseLabel(phase, state.language),
                                    actionLabel = continueAction?.let { continueLabel(state.language) },
                                    onAction = continueAction,
                                )
                                if (state.inputName == null) {
                                    EmptyStateCard(AppSymbols.Archive, texts.noInputTitle, texts.noInputDetail)
                                }
                                RecognitionSummaryCard(state, texts)
                                MetadataSummaryCard(state, texts, busy) { showMetadataEditor = true }
                                AnimatedVisibility(
                                    visible = showResourceCard,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically(),
                                ) {
                                    ResourceCard(state, texts, busy, onSelectManualResource, onClearManualResource)
                                }
                            }
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
                                AppearanceCard(state, texts, busy, onAppearanceChange)
                                AffMappingCard(state, texts, busy) { showAffMapping = true }
                                PreprocessCard(state, texts, busy, onOptionsChange)
                                ConvertSaveCard(
                                    state,
                                    texts,
                                    busy,
                                    onConvert,
                                    onSaveDownloads,
                                    onSaveOutput,
                                    modifier = Modifier.bringIntoViewRequester(convertSaveRequester),
                                    highlight = highlightConvertSave,
                                )
                                SingleResultCard(state, texts, onSaveDownloads, onSaveOutput, busy)
                                LocalizedFeedbackMessagesCard(state.warnings, state.errorMessage, state.errorDetails, language = state.language)
                                AdvancedInfoCard(state, texts, clipboard)
                            }
                        }
                    } else {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                            HeroCard(state, texts, busy, onSelectInput, onSelectZip, onScan)
                            OperationStatusCard(
                                phase = phase,
                                title = singlePhaseTitle(state, texts),
                                detail = singlePhaseDetail(state, texts),
                                statusLabel = operationPhaseLabel(phase, state.language),
                                actionLabel = continueAction?.let { continueLabel(state.language) },
                                onAction = continueAction,
                            )
                            if (state.inputName == null) {
                                EmptyStateCard(AppSymbols.Archive, texts.noInputTitle, texts.noInputDetail)
                            }
                            RecognitionSummaryCard(state, texts)
                            MetadataSummaryCard(state, texts, busy) { showMetadataEditor = true }
                            AnimatedVisibility(
                                visible = showResourceCard,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                ResourceCard(state, texts, busy, onSelectManualResource, onClearManualResource)
                            }
                            AppearanceCard(state, texts, busy, onAppearanceChange)
                            AffMappingCard(state, texts, busy) { showAffMapping = true }
                            PreprocessCard(state, texts, busy, onOptionsChange)
                            ConvertSaveCard(
                                state,
                                texts,
                                busy,
                                onConvert,
                                onSaveDownloads,
                                onSaveOutput,
                                modifier = Modifier.bringIntoViewRequester(convertSaveRequester),
                                highlight = highlightConvertSave,
                            )
                            SingleResultCard(state, texts, onSaveDownloads, onSaveOutput, busy)
                            LocalizedFeedbackMessagesCard(state.warnings, state.errorMessage, state.errorDetails, language = state.language)
                            AdvancedInfoCard(state, texts, clipboard)
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = bottomInset + 16.dp),
            )
        }
    }

    if (showMetadataEditor) {
        MetadataEditorDialog(
            initial = state.metadataDraft,
            texts = texts,
            onDismiss = { showMetadataEditor = false },
            onSave = {
                showMetadataEditor = false
                onMetadataSave(it)
            },
        )
    }
    if (showAffMapping) {
        AffMappingDialog(
            initial = state.affMappings,
            texts = texts,
            onDismiss = { showAffMapping = false },
            onSave = {
                showAffMapping = false
                onAffMappingsSave(it)
            },
        )
    }
}

@Composable
private fun TopBar(
    state: UiConvertState,
    texts: AppText,
    busy: Boolean,
    onLanguageChange: (UiLanguage) -> Unit,
    onClearCache: () -> Unit,
    onCacheCleared: () -> Unit,
    onSwitchToPack: () -> Unit,
    onSwitchToCharacter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageMenuOpen by rememberSaveable { mutableStateOf(false) }
    var settingsMenuOpen by rememberSaveable { mutableStateOf(false) }
    var languageMenuOpen by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(EtoileShapeTokens.TopBar),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    texts.appName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    texts.pageTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { pageMenuOpen = true }) {
                        SymbolIcon(AppSymbols.SwitchPage, contentDescription = texts.switchPage)
                    }
                    DropdownMenu(expanded = pageMenuOpen, onDismissRequest = { pageMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("${texts.pageTitle} ✓") },
                            onClick = { pageMenuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text(packText(state.language).pageTitle) },
                            onClick = {
                                pageMenuOpen = false
                                onSwitchToPack()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(characterText(state.language).pageTitle) },
                            onClick = {
                                pageMenuOpen = false
                                onSwitchToCharacter()
                            },
                        )
                    }
                }
                Box {
                    IconButton(onClick = { settingsMenuOpen = true }) {
                        SymbolIcon(AppSymbols.Settings, contentDescription = texts.settings)
                    }
                    DropdownMenu(expanded = settingsMenuOpen, onDismissRequest = { settingsMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(texts.language) },
                            leadingIcon = { SymbolIcon(AppSymbols.Language, contentDescription = null) },
                            onClick = {
                                settingsMenuOpen = false
                                languageMenuOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(texts.clearCache) },
                            leadingIcon = { SymbolIcon(AppSymbols.Delete, contentDescription = null) },
                            enabled = !busy && !state.canSave,
                            onClick = {
                                settingsMenuOpen = false
                                onClearCache()
                                onCacheCleared()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(texts.about) },
                            leadingIcon = { SymbolIcon(AppSymbols.Info, contentDescription = null) },
                            onClick = {
                                settingsMenuOpen = false
                                showAbout = true
                            },
                        )
                    }
                    DropdownMenu(expanded = languageMenuOpen, onDismissRequest = { languageMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(texts.chinese) },
                            onClick = {
                                languageMenuOpen = false
                                onLanguageChange(UiLanguage.ZhHans)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(texts.english) },
                            onClick = {
                                languageMenuOpen = false
                                onLanguageChange(UiLanguage.English)
                            },
                        )
                    }
                }
            }
        }
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(texts.appName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(texts.projectDescription)
                    Text("${texts.versionInfo}: ${texts.debugBuild}")
                    Text("${texts.device}: SDK ${state.deviceSdkInt} / Android ${state.deviceRelease}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text(texts.close) }
            },
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    symbol: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    EdgeAwareCard(
        modifier = modifier
            .animateContentSize()
            .tapFeedbackOnly(),
        shape = EtoileShapeTokens.SectionCard,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (symbol != null) {
                    SymbolIcon(symbol, contentDescription = null, color = MaterialTheme.colorScheme.primary)
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun HeroCard(
    state: UiConvertState,
    texts: AppText,
    busy: Boolean,
    onSelectInput: () -> Unit,
    onSelectZip: () -> Unit,
    onScan: () -> Unit,
) {
    EdgeAwareCard(
        modifier = Modifier
            .tapFeedbackOnly(),
        shape = EtoileShapeTokens.HeroCard,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(texts.input, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(texts.inputSummary, style = MaterialTheme.typography.bodyMedium)
            AdaptiveActionRow {
                Button(onClick = onSelectZip, enabled = !busy) { ButtonIconLabel(AppSymbols.Archive, texts.selectZip) }
                FilledTonalButton(onClick = onSelectInput, enabled = !busy) { ButtonIconLabel(AppSymbols.Folder, texts.selectFolder) }
                OutlinedButton(onClick = onScan, enabled = state.canScan && !busy) {
                    LoadingButtonIconLabel(state.isScanning, AppSymbols.Search, texts.scan)
                }
            }
            AdaptiveActionRow {
                AssistChip(onClick = {}, label = { Text(currentStatusText(state, texts)) })
                AssistChip(onClick = {}, label = { Text(texts.inputTypeLabel(state.inputType)) })
                if (state.unsupportedPackStructure) AssistChip(onClick = {}, label = { Text(texts.packDetected) })
            }
            KeyValue(texts.currentInput, state.inputName ?: texts.notSelected)
        }
    }
}

@Composable
private fun RecognitionSummaryCard(state: UiConvertState, texts: AppText) {
    SectionCard(texts.overview, symbol = AppSymbols.Article) {
        if (state.unsupportedPackStructure) {
            Text(texts.packDetected, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            Text(texts.packPageLater)
        }
        KeyValue(texts.songId, state.songId ?: state.metadataDraft.songId.ifBlank { texts.notDetected })
        KeyValue(texts.title, state.metadataDraft.title.ifBlank { texts.notDetected })
        KeyValue(texts.artist, state.metadataDraft.artist.ifBlank { texts.none })
        KeyValue(texts.difficulty, difficultySummary(state, texts))
        ResourceStatusRow(state, texts)
        if (state.missingMetadata != null) AssistChip(onClick = {}, label = { Text(texts.needMetadata) })
    }
}

@Composable
private fun ResourceStatusRow(state: UiConvertState, texts: AppText) {
    AdaptiveActionRow {
        StatusChip("${texts.audio}: ${resourceChipStatus(state.resourceStatus.audioFileName, state.resourceStatus.audioManual, texts)}")
        StatusChip("${texts.jacket}: ${resourceChipStatus(state.resourceStatus.jacketFileName, state.resourceStatus.jacketManual, texts)}")
        StatusChip("${texts.background}: ${resourceChipStatus(state.resourceStatus.backgroundFileName, state.resourceStatus.backgroundManual, texts)}")
    }
}

private fun resourceChipStatus(fileName: String?, manual: Boolean, texts: AppText): String =
    when {
        manual -> texts.manualTag
        fileName != null -> texts.identified
        else -> texts.notDetected
    }

private fun resourceSummary(state: UiConvertState, texts: AppText): String =
    listOf(
        "${texts.audio}: ${resourceChipStatus(state.resourceStatus.audioFileName, state.resourceStatus.audioManual, texts)}",
        "${texts.jacket}: ${resourceChipStatus(state.resourceStatus.jacketFileName, state.resourceStatus.jacketManual, texts)}",
        "${texts.background}: ${resourceChipStatus(state.resourceStatus.backgroundFileName, state.resourceStatus.backgroundManual, texts)}",
    ).joinToString(" · ")

@Composable
private fun StatusChip(label: String) {
    AssistChip(onClick = {}, label = { Text(label) })
}

@Composable
private fun ButtonIconLabel(symbol: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        SymbolIcon(symbol, contentDescription = null, size = 20.dp)
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LoadingButtonIconLabel(loading: Boolean, symbol: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            SymbolIcon(symbol, contentDescription = null, size = 20.dp)
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MetadataSummaryCard(
    state: UiConvertState,
    texts: AppText,
    busy: Boolean,
    onEdit: () -> Unit,
) {
    SectionCard(texts.metadata, symbol = AppSymbols.Article) {
        KeyValue(texts.publisherId, state.metadataDraft.publisherId.ifBlank { "etoilebridge" })
        KeyValue(texts.levelId, state.metadataDraft.levelId.ifBlank { state.metadataDraft.songId.ifBlank { state.songId ?: texts.notDetected } })
        KeyValue(texts.identifierPreview, identifierPreview(state.metadataDraft, state.songId))
        MissingMetadataPanel(state.missingMetadata, texts)
        FilledTonalButton(onClick = onEdit, enabled = !busy && SingleSongStateRules.showMetadataEditorEntry(state)) {
            Text(texts.editMetadata)
        }
    }
}

@Composable
private fun ResourceCard(
    state: UiConvertState,
    texts: AppText,
    busy: Boolean,
    onSelectManualResource: (ManualResourceKind) -> Unit,
    onClearManualResource: (ManualResourceKind) -> Unit,
) {
    var detailKind by rememberSaveable { mutableStateOf<ResourceImageKind?>(null) }
    val inputKey = "${state.inputName.orEmpty()}|${state.workspacePath.orEmpty()}"
    var expanded by rememberSaveable(inputKey) { mutableStateOf(false) }
    val scanned = state.scanStatus == UiScanStatus.Scanned && !state.isScanning
    LaunchedEffect(inputKey, scanned) {
        if (scanned) expanded = true
    }
    SectionCard(texts.resources, symbol = AppSymbols.Image) {
        Text(resourceSummary(state, texts), style = MaterialTheme.typography.bodyMedium)
        if (!scanned) {
            Text(
                if (state.isScanning) texts.scanStatusLabel(state.scanStatus) else texts.waitingForScan,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "resourceExpandRotation")
            TextButton(onClick = { expanded = !expanded }) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (expanded) texts.hideResources else texts.showResources)
                    SymbolIcon(AppSymbols.ExpandMore, contentDescription = null, size = 18.dp, modifier = Modifier.rotate(rotation))
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    expandVertically(animationSpec = tween(280, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                    shrinkVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AudioResourceRow(state, texts, busy, onSelectManualResource)
                    ResourceImageGrid(state, texts, busy, onSelectManualResource) { detailKind = it }
                    if (state.resourceStatus.audioFileName == null) Text(texts.audioNotDetected, color = MaterialTheme.colorScheme.error)
                    if (state.resourceStatus.jacketFileName == null) Text(texts.jacketNotDetected)
                    if (state.resourceStatus.backgroundFileName == null) Text(texts.backgroundNotDetected)
                    if (state.manualResources.songlistFileName == null && state.missingMetadata != null) Text(texts.songlistMissing)
                    HorizontalDivider()
                    ManualResourcePanel(state, state.canScan && !busy, texts, onSelectManualResource)
                }
            }
        }
    }
    detailKind?.let { kind ->
        ResourceImageDetailDialog(
            kind = kind,
            state = state,
            texts = texts,
            onDismiss = { detailKind = null },
            onReplace = {
                detailKind = null
                onSelectManualResource(kind.manualKind)
            },
            onClearManual = {
                detailKind = null
                onClearManualResource(kind.manualKind)
            },
        )
    }
}

private enum class ResourceImageKind(val manualKind: ManualResourceKind) {
    Jacket(ManualResourceKind.Jacket),
    Background(ManualResourceKind.Background),
}

@Composable
private fun AudioResourceRow(
    state: UiConvertState,
    texts: AppText,
    busy: Boolean,
    onSelectManualResource: (ManualResourceKind) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.tapFeedbackOnly(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SymbolIcon(AppSymbols.Music, contentDescription = null, color = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(texts.audio, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        state.resourceStatus.audioFileName ?: texts.notDetected,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when {
                                state.resourceStatus.audioManual -> texts.manualTag
                                state.resourceStatus.audioFileName != null -> texts.identified
                                else -> texts.notDetected
                            },
                        )
                    },
                )
            }
            OutlinedButton(onClick = { onSelectManualResource(ManualResourceKind.Audio) }, enabled = state.canScan && !busy) {
                Text(texts.selectAudio)
            }
        }
    }
}

@Composable
private fun ResourceImageGrid(
    state: UiConvertState,
    texts: AppText,
    busy: Boolean,
    onSelectManualResource: (ManualResourceKind) -> Unit,
    onOpenDetails: (ResourceImageKind) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 520.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ResourceImageCard(
                    kind = ResourceImageKind.Jacket,
                    state = state,
                    texts = texts,
                    enabled = state.canScan && !busy,
                    modifier = Modifier.weight(1f),
                    onOpenDetails = onOpenDetails,
                    onSelectManualResource = onSelectManualResource,
                )
                ResourceImageCard(
                    kind = ResourceImageKind.Background,
                    state = state,
                    texts = texts,
                    enabled = state.canScan && !busy,
                    modifier = Modifier.weight(1f),
                    onOpenDetails = onOpenDetails,
                    onSelectManualResource = onSelectManualResource,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ResourceImageCard(
                    kind = ResourceImageKind.Jacket,
                    state = state,
                    texts = texts,
                    enabled = state.canScan && !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onOpenDetails = onOpenDetails,
                    onSelectManualResource = onSelectManualResource,
                )
                ResourceImageCard(
                    kind = ResourceImageKind.Background,
                    state = state,
                    texts = texts,
                    enabled = state.canScan && !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onOpenDetails = onOpenDetails,
                    onSelectManualResource = onSelectManualResource,
                )
            }
        }
    }
}

@Composable
private fun ResourceImageCard(
    kind: ResourceImageKind,
    state: UiConvertState,
    texts: AppText,
    enabled: Boolean,
    modifier: Modifier,
    onOpenDetails: (ResourceImageKind) -> Unit,
    onSelectManualResource: (ManualResourceKind) -> Unit,
) {
    val label = kind.label(texts)
    val fileName = kind.fileName(state)
    val filePath = kind.filePath(state)
    val manual = kind.isManual(state)
    val preview = rememberImagePreview(filePath, maxWidthPx = 360, maxHeightPx = 240)
    val missing = fileName == null
    ElevatedCard(
        modifier = modifier.clickable { onOpenDetails(kind) },
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SymbolIcon(AppSymbols.Image, contentDescription = null, color = MaterialTheme.colorScheme.primary)
                Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when {
                                manual -> texts.manualTag
                                !missing -> texts.identified
                                else -> texts.notDetected
                            },
                        )
                    },
                )
            }
            if (missing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        texts.chooseManually,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { onSelectManualResource(kind.manualKind) }, enabled = enabled) {
                        Text(if (kind == ResourceImageKind.Jacket) texts.selectJacket else texts.selectBackground)
                    }
                }
            } else {
                ImagePreviewBox(
                    data = preview,
                    missing = false,
                    label = label,
                    aspectRatio = if (kind == ResourceImageKind.Jacket) 1f else 16f / 9f,
                )
                Text(
                    fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (preview.fileMissing && !missing) {
                Text(imageFileMissingText(texts), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else if (preview.decodeFailed && !missing) {
                Text(texts.imagePreviewFailed, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ImagePreviewBox(
    data: ImagePreviewData,
    missing: Boolean,
    label: String,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (data.image != null) {
            Image(
                bitmap = data.image,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SymbolIcon(
                    AppSymbols.Image,
                    contentDescription = null,
                    size = 34.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ResourceImageDetailDialog(
    kind: ResourceImageKind,
    state: UiConvertState,
    texts: AppText,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onClearManual: () -> Unit,
) {
    val label = kind.label(texts)
    val fileName = kind.fileName(state)
    val filePath = kind.filePath(state)
    val manual = kind.isManual(state)
    ImageDetailDialog(
        title = "${texts.imageDetails} - $label",
        label = label,
        fileName = fileName,
        filePath = filePath,
        manual = manual,
        texts = texts,
        onDismiss = onDismiss,
        onReplace = onReplace,
        onClearManual = if (manual) onClearManual else null,
    )
}

private fun ResourceImageKind.label(texts: AppText): String =
    when (this) {
        ResourceImageKind.Jacket -> texts.jacket
        ResourceImageKind.Background -> texts.background
    }

private fun ResourceImageKind.fileName(state: UiConvertState): String? =
    when (this) {
        ResourceImageKind.Jacket -> state.resourceStatus.jacketFileName
        ResourceImageKind.Background -> state.resourceStatus.backgroundFileName
    }

private fun ResourceImageKind.filePath(state: UiConvertState): String? =
    when (this) {
        ResourceImageKind.Jacket -> state.resourceStatus.jacketFilePath
        ResourceImageKind.Background -> state.resourceStatus.backgroundFilePath
    }

private fun ResourceImageKind.isManual(state: UiConvertState): Boolean =
    when (this) {
        ResourceImageKind.Jacket -> state.resourceStatus.jacketManual
        ResourceImageKind.Background -> state.resourceStatus.backgroundManual
    }

@Composable
private fun AppearanceCard(
    state: UiConvertState,
    texts: AppText,
    busy: Boolean,
    onAppearanceChange: (UiAppearanceOptions) -> Unit,
) {
    val options = state.appearanceOptions
    SectionCard(texts.appearance, symbol = AppSymbols.Palette) {
        if (options.sideInferredFromLephon) {
            AssistChip(onClick = {}, label = { Text(texts.lephonMappedToLight) })
        }
        ChoiceRow(
            title = texts.particle,
            values = UiArcCreateParticle.entries,
            selected = options.particle,
            label = texts::particleLabel,
            enabled = !busy,
        ) { onAppearanceChange(options.copy(particle = it)) }
        ChoiceRow(
            title = texts.accent,
            values = UiArcCreateAccent.entries,
            selected = options.accent,
            label = texts::accentLabel,
            enabled = !busy,
        ) { onAppearanceChange(options.copy(accent = it)) }
        ChoiceRow(
            title = texts.track,
            values = UiArcCreateTrack.entries,
            selected = options.track,
            label = texts::trackLabel,
            enabled = !busy,
        ) { onAppearanceChange(options.copy(track = it)) }
        ChoiceRow(
            title = texts.singleLine,
            values = UiArcCreateSingleLine.entries,
            selected = options.singleLine,
            label = texts::singleLineLabel,
            enabled = !busy,
        ) { onAppearanceChange(options.copy(singleLine = it)) }
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    values: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.Medium)
        AdaptiveActionRow {
            values.forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    enabled = enabled,
                    label = { Text(label(value)) },
                )
            }
        }
    }
}

@Composable
private fun AffMappingCard(
    state: UiConvertState,
    texts: AppText,
    busy: Boolean,
    onEdit: () -> Unit,
) {
    SectionCard(texts.affMapping, symbol = AppSymbols.AccountTree) {
        KeyValue(texts.adoptedAff, state.adoptedAffFiles.size.toString())
        KeyValue(texts.ignoredAff, state.ignoredAffFiles.size.toString())
        state.affMappings.take(4).forEach { item ->
            val mapped = item.mappedRatingClass?.let { DifficultyMapper.labelFor(it) } ?: texts.notAdopted
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.fileName, modifier = Modifier.weight(1f))
                Text(mapped)
            }
        }
        OutlinedButton(onClick = onEdit, enabled = !busy && !state.unsupportedPackStructure && state.affMappings.isNotEmpty()) {
            Text(texts.editAffMapping)
        }
    }
}

@Composable
private fun PreprocessCard(
    state: UiConvertState,
    texts: AppText,
    busy: Boolean,
    onOptionsChange: (UiConvertOptions) -> Unit,
) {
    SectionCard(texts.preprocessOptions, symbol = AppSymbols.Tune) {
        OptionPanel(options = state.options, texts = texts, onOptionsChange = onOptionsChange, enabled = !busy)
    }
}

@Composable
private fun ConvertSaveCard(
    state: UiConvertState,
    texts: AppText,
    busy: Boolean,
    onConvert: () -> Unit,
    onSaveDownloads: () -> Unit,
    onSaveOutput: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    SectionCard(texts.convertAndSave, modifier = modifier.pulseHighlight(highlight), symbol = AppSymbols.Convert) {
        AdaptiveActionRow {
            Button(onClick = onConvert, enabled = SingleSongStateRules.canStartConversion(state, busy)) {
                LoadingButtonIconLabel(state.isConverting, AppSymbols.Convert, texts.convert)
            }
            if (state.canUseMediaStoreDownloads) {
                OutlinedButton(onClick = onSaveDownloads, enabled = state.canSaveDownloads && !busy) {
                    LoadingButtonIconLabel(state.isSaving, AppSymbols.Download, texts.saveDownloads)
                }
                OutlinedButton(onClick = onSaveOutput, enabled = state.canSave && !busy) {
                    LoadingButtonIconLabel(state.isSaving, AppSymbols.SaveAs, texts.saveAs)
                }
            } else {
                Button(onClick = onSaveOutput, enabled = state.canSave && !busy) {
                    LoadingButtonIconLabel(state.isSaving, AppSymbols.SaveAs, texts.saveAs)
                }
            }
        }
        KeyValue(texts.saveStatus, texts.saveStatusLabel(state.saveStatus))
        KeyValue(texts.pendingFile, state.pendingOutputFile?.name ?: texts.none)
        KeyValue(texts.pendingFileSize, state.pendingOutputFileSize?.formatBytes() ?: texts.none)
        KeyValue(texts.savedLocation, state.savedLocation ?: texts.notSaved)
        if (state.pendingOutputFile != null && !state.canUseMediaStoreDownloads) {
            Text(texts.downloadsUnavailable, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AdvancedInfoCard(
    state: UiConvertState,
    texts: AppText,
    clipboard: ClipboardManager,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(texts.advancedInfo, symbol = AppSymbols.Terminal) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) texts.hideDetails else texts.showDetails)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                AdvancedInfoSection(texts.device) {
                    KeyValue(texts.device, "SDK ${state.deviceSdkInt} / Android ${state.deviceRelease}")
                }
                AdvancedInfoSection(texts.input) {
                    KeyValue(texts.inputType, texts.inputTypeLabel(state.inputType))
                    KeyValue(texts.currentInput, state.inputName ?: texts.notSelected)
                    KeyValue(texts.workspace, state.workspacePath ?: texts.notPrepared)
                    KeyValue(texts.projectRoot, state.projectRootPath ?: texts.notDetected)
                    KeyValue(texts.songRoot, state.songRootPath ?: texts.notDetected)
                    KeyValue(texts.extractStatus, texts.extractStatusLabel(state.extractStatus))
                    KeyValue(texts.scanStatus, texts.scanStatusLabel(state.scanStatus))
                }
                AdvancedInfoSection(texts.outputFileName) {
                    KeyValue(texts.pendingFile, state.pendingOutputFile?.name ?: texts.none)
                    KeyValue(texts.savedLocation, state.savedLocation ?: texts.notSaved)
                    KeyValue(texts.workspaceStatus, if (state.workspaceCleaned) texts.workspaceCleaned else texts.workspaceKept)
                }
                AdvancedInfoSection(texts.detectedAff) {
                    ListSection(texts.detectedAff, state.affDifficulties, texts)
                    ListSection(texts.adoptedAff, state.adoptedAffFiles, texts)
                    ListSection(texts.ignoredAff, state.ignoredAffFiles, texts)
                }
                AdvancedInfoSection(texts.logs) {
                    AdaptiveActionRow {
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(buildLogClipboardText(state, texts))) }) {
                            Text(texts.copyLogs)
                        }
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(buildScanClipboardText(state, texts))) }) {
                            Text(texts.copyScanResult)
                        }
                    }
                    LogPanel(title = texts.warnings, lines = state.warnings, texts = texts)
                    CollapsibleLogPanel(title = texts.logs, lines = state.logs, texts = texts)
                }
                if (state.errorMessage != null || state.errorDetails != null) {
                    AdvancedInfoSection(texts.errorDetails) {
                        ErrorPanel(message = state.errorMessage, details = state.errorDetails, texts = texts)
                    }
                }
            }
        }
        if (!expanded) {
            val warningCount = state.warnings.size
            val errorText = state.errorMessage?.let { "${texts.error}: $it" }
            Text(errorText ?: "${texts.warnings}: $warningCount")
        }
    }
}

@Composable
private fun ManualResourcePanel(
    state: UiConvertState,
    enabled: Boolean,
    texts: AppText,
    onSelectManualResource: (ManualResourceKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(texts.manualResources, fontWeight = FontWeight.SemiBold)
        AdaptiveActionRow {
            OutlinedButton(onClick = { onSelectManualResource(ManualResourceKind.Audio) }, enabled = enabled) {
                ButtonIconLabel(AppSymbols.Music, texts.selectAudio)
            }
            OutlinedButton(onClick = { onSelectManualResource(ManualResourceKind.Jacket) }, enabled = enabled) {
                ButtonIconLabel(AppSymbols.Image, texts.selectJacket)
            }
            OutlinedButton(onClick = { onSelectManualResource(ManualResourceKind.Background) }, enabled = enabled) {
                ButtonIconLabel(AppSymbols.Image, texts.selectBackground)
            }
            OutlinedButton(onClick = { onSelectManualResource(ManualResourceKind.Songlist) }, enabled = enabled) {
                ButtonIconLabel(AppSymbols.Article, texts.selectSonglist)
            }
            OutlinedButton(onClick = { onSelectManualResource(ManualResourceKind.Packlist) }, enabled = enabled) {
                ButtonIconLabel(AppSymbols.Article, texts.selectPacklist)
            }
        }
        AdaptiveActionRow {
            state.manualResources.audioFileName?.let { StatusChip("${texts.audio}: ${texts.manual(it)}") }
            state.manualResources.jacketFileName?.let { StatusChip("${texts.jacket}: ${texts.manual(it)}") }
            state.manualResources.backgroundFileName?.let { StatusChip("${texts.background}: ${texts.manual(it)}") }
            state.manualResources.songlistFileName?.let { StatusChip("songlist: ${texts.manual(it)}") }
            state.manualResources.packlistFileName?.let { StatusChip("packlist: ${texts.manual(it)}") }
        }
    }
}

@Composable
private fun MetadataEditorDialog(
    initial: UiMetadataDraft,
    texts: AppText,
    onDismiss: () -> Unit,
    onSave: (UiMetadataDraft) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    val errors = metadataErrors(draft, texts)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.editMetadata) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (errors.isNotEmpty()) {
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            errors.forEach { Text(it, color = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
                }
                MetadataField(texts.songId, draft.songId) { draft = draft.copy(songId = it) }
                MetadataField(texts.title, draft.title) { draft = draft.copy(title = it) }
                MetadataField(texts.artist, draft.artist) { draft = draft.copy(artist = it) }
                MetadataField(texts.bpmText, draft.bpmText) { draft = draft.copy(bpmText = it) }
                MetadataField(texts.baseBpm, draft.baseBpm) { draft = draft.copy(baseBpm = it) }
                MetadataField(texts.publisherId, draft.publisherId) { draft = draft.copy(publisherId = it) }
                MetadataField(texts.levelId, draft.levelId) { draft = draft.copy(levelId = it) }
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(texts.identifierPreview, fontWeight = FontWeight.SemiBold)
                        Text(identifierPreviewForEditor(draft, texts))
                    }
                }
                HorizontalDivider()
                draft.difficulties.forEachIndexed { index, diff ->
                    DifficultyEditor(diff, texts) { updated ->
                        draft = draft.copy(difficulties = draft.difficulties.toMutableList().also { it[index] = updated })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(draft.copy(identifierOverride = "")) }, enabled = errors.isEmpty()) { Text(texts.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(texts.cancel) } },
    )
}

private fun metadataErrors(draft: UiMetadataDraft, texts: AppText): List<String> {
    val errors = mutableListOf<String>()
    if (draft.songId.isBlank()) errors += "${texts.songId} required"
    if (draft.title.isBlank()) errors += "${texts.title} required"
    if (draft.artist.isBlank()) errors += "${texts.artist} required"
    val baseBpm = draft.baseBpm.toFloatOrNull()
    if (baseBpm == null || baseBpm <= 0f) errors += "${texts.baseBpm} required"
    if (draft.difficulties.isEmpty()) errors += texts.adoptedAff
    draft.difficulties.forEach { diff ->
        if (diff.difficulty.isBlank()) errors += "${diff.affFileName}: ${texts.difficulty} required"
        if (diff.chartConstant.toFloatOrNull() == null) errors += "${diff.affFileName}: ${texts.chartConstant} required"
    }
    return errors
}

@Composable
private fun MetadataField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun DifficultyEditor(
    diff: UiDifficultyDraft,
    texts: AppText,
    onChange: (UiDifficultyDraft) -> Unit,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${diff.affFileName} - ${DifficultyMapper.labelFor(diff.ratingClass)}", fontWeight = FontWeight.SemiBold)
            MetadataField(texts.difficulty, diff.difficulty) { onChange(diff.copy(difficulty = it)) }
            MetadataField(texts.chartConstant, diff.chartConstant) { onChange(diff.copy(chartConstant = it)) }
            MetadataField(texts.chartDesigner, diff.chartDesigner) { onChange(diff.copy(chartDesigner = it)) }
            MetadataField(texts.jacketDesigner, diff.jacketDesigner) { onChange(diff.copy(jacketDesigner = it)) }
        }
    }
}

@Composable
private fun AffMappingDialog(
    initial: List<UiAffMappingItem>,
    texts: AppText,
    onDismiss: () -> Unit,
    onSave: (List<UiAffMappingItem>) -> Unit,
) {
    var mappings by remember { mutableStateOf(initial) }
    fun select(index: Int, ratingClass: Int?) {
        mappings = mappings.mapIndexed { itemIndex, item ->
            when {
                itemIndex == index -> item.copy(
                    mappedRatingClass = ratingClass,
                    adopted = ratingClass != null,
                    manual = true,
                    conflict = false,
                )
                ratingClass != null && item.mappedRatingClass == ratingClass -> item.copy(
                    mappedRatingClass = null,
                    adopted = false,
                    conflict = false,
                )
                else -> item.copy(conflict = false)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.editAffMapping) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                mappings.forEachIndexed { index, item ->
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.fileName, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                MappingChip(texts.notAdopted, item.mappedRatingClass == null) { select(index, null) }
                                (0..4).forEach { ratingClass ->
                                    MappingChip(DifficultyMapper.labelFor(ratingClass), item.mappedRatingClass == ratingClass) {
                                        select(index, ratingClass)
                                    }
                                }
                            }
                            if (item.manual) Text(texts.manualMapping, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(mappings) }) { Text(texts.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(texts.cancel) } },
    )
}

@Composable
private fun MappingChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
internal fun KeyValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, modifier = Modifier.width(132.dp), fontWeight = FontWeight.Medium)
        Text(value, modifier = Modifier.weight(1f), softWrap = true)
    }
}

@Composable
internal fun AdvancedInfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
internal fun ListSection(title: String, lines: List<String>, texts: AppText) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        if (lines.isEmpty()) {
            Text(texts.none)
        } else {
            lines.forEach { Text("- $it") }
        }
    }
}

@Composable
private fun CollapsibleLogPanel(title: String, lines: List<String>, texts: AppText) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(title, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) texts.close else "${lines.size}")
            }
        }
        if (expanded) {
            if (lines.isEmpty()) Text(texts.none) else lines.takeLast(80).forEach { Text(it) }
        }
    }
}

@Composable
private fun ErrorPanel(message: String?, details: String?, texts: AppText) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(texts.error, fontWeight = FontWeight.SemiBold)
        Text(message ?: texts.none)
        if (!details.isNullOrBlank()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) texts.hideErrorDetails else texts.showErrorDetails)
            }
            if (expanded) {
                Text(texts.errorDetails, fontWeight = FontWeight.Medium)
                details.lines().filter { it.isNotBlank() }.take(80).forEach {
                    Text(it, softWrap = true, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

internal fun Long.formatBytes(): String = "$this bytes"

private fun currentStatusText(state: UiConvertState, texts: AppText): String =
    when {
        state.saveStatus == UiSaveStatus.Saved -> texts.saveStatusLabel(state.saveStatus)
        state.pendingOutputFile != null -> texts.saveStatusLabel(UiSaveStatus.Pending)
        state.unsupportedPackStructure -> texts.packDetected
        state.canConvert -> texts.convertible
        state.missingMetadata != null -> texts.needMetadata
        state.inputName == null -> texts.notSelected
        state.isScanning -> texts.scanStatusLabel(state.scanStatus)
        else -> texts.scanStatusLabel(state.scanStatus)
    }

private fun singlePhaseTitle(state: UiConvertState, texts: AppText): String =
    when {
        state.isCopying && state.inputType == com.zeerqi27.etoilebridge.model.UiInputType.Zip -> texts.extracting
        state.isCopying -> texts.copyingInput
        state.isScanning -> texts.scanningInput
        state.isConverting -> texts.converting
        state.isSaving -> texts.saving
        state.saveStatus == UiSaveStatus.Saved -> texts.saveStatusLabel(UiSaveStatus.Saved)
        state.pendingOutputFile != null -> texts.conversionCompletePendingSave
        state.errorMessage != null || state.scanStatus == UiScanStatus.Failed -> texts.operationFailed
        state.canConvert -> texts.convertible
        state.inputName == null -> texts.noInputTitle
        else -> currentStatusText(state, texts)
    }

private fun singlePhaseDetail(state: UiConvertState, texts: AppText): String =
    when {
        state.errorMessage != null -> state.errorMessage
        state.pendingOutputFile != null -> state.pendingOutputFile.name
        state.saveStatus == UiSaveStatus.Saved -> state.savedLocation ?: texts.savedLocation
        state.canConvert -> texts.conversionReadyDetail
        state.inputName == null -> texts.noInputPhaseDetail
        state.missingMetadata != null -> texts.needMetadata
        else -> state.inputName ?: texts.notSelected
    }

private fun continueLabel(language: UiLanguage): String =
    textFor(language).continueAction

private fun difficultySummary(state: UiConvertState, texts: AppText): String =
    state.metadataDraft.difficulties.takeIf { it.isNotEmpty() }
        ?.joinToString { it.difficulty.ifBlank { DifficultyMapper.labelFor(it.ratingClass) } }
        ?: texts.none

private fun identifierPreview(draft: UiMetadataDraft, stateSongId: String?): String {
    val publisher = draft.publisherId.trim().ifBlank { "etoilebridge" }
    val level = draft.levelId.trim().ifBlank { draft.songId.trim().ifBlank { stateSongId.orEmpty() } }
    return if (level.isBlank()) publisher else "$publisher.$level"
}

private fun identifierPreviewForEditor(draft: UiMetadataDraft, texts: AppText): String {
    val publisher = draft.publisherId.trim()
    val level = draft.levelId.trim()
    return when {
        publisher.isBlank() -> texts.enterPublisherId
        level.isBlank() -> texts.enterLevelId
        else -> "$publisher.$level"
    }
}

private fun buildLogClipboardText(state: UiConvertState, texts: AppText): String =
    buildString {
        appendLine("${texts.inputType}: ${texts.inputTypeLabel(state.inputType)}")
        appendLine("${texts.currentInput}: ${state.inputName ?: texts.notSelected}")
        appendLine("${texts.projectRoot}: ${state.projectRootPath ?: texts.notDetected}")
        appendLine("${texts.songRoot}: ${state.songRootPath ?: texts.notDetected}")
        appendLine("${texts.songId}: ${state.songId ?: state.metadataDraft.songId.ifBlank { texts.notDetected }}")
        appendLine("${texts.adoptedAff}:")
        appendList(state.adoptedAffFiles, texts)
        appendLine("${texts.ignoredAff}:")
        appendList(state.ignoredAffFiles, texts)
        appendLine("${texts.audio}: ${state.resourceStatus.audioFileName ?: texts.notDetected}")
        appendLine("${texts.jacket}: ${state.resourceStatus.jacketFileName ?: texts.notDetected}")
        appendLine("${texts.background}: ${state.resourceStatus.backgroundFileName ?: texts.notDetected}")
        appendLine("${texts.identifierPreview}: ${identifierPreview(state.metadataDraft, state.songId)}")
        appendLine("${texts.warnings}:")
        appendList(state.warnings, texts)
        appendLine("${texts.logs}:")
        appendList(state.logs, texts)
        appendLine("${texts.error}: ${state.errorMessage ?: texts.none}")
        state.errorDetails?.takeIf { it.isNotBlank() }?.let {
            appendLine("${texts.errorDetails}:")
            appendLine(it)
        }
    }

private fun buildScanClipboardText(state: UiConvertState, texts: AppText): String =
    buildString {
        appendLine("${texts.inputType}: ${texts.inputTypeLabel(state.inputType)}")
        appendLine("${texts.currentInput}: ${state.inputName ?: texts.notSelected}")
        appendLine("${texts.projectRoot}: ${state.projectRootPath ?: texts.notDetected}")
        appendLine("${texts.songRoot}: ${state.songRootPath ?: texts.notDetected}")
        appendLine("${texts.songId}: ${state.songId ?: state.metadataDraft.songId.ifBlank { texts.notDetected }}")
        appendLine("${texts.adoptedAff}:")
        appendList(state.adoptedAffFiles, texts)
        appendLine("${texts.ignoredAff}:")
        appendList(state.ignoredAffFiles, texts)
        appendLine("${texts.audio}: ${state.resourceStatus.audioFileName ?: texts.notDetected}")
        appendLine("${texts.jacket}: ${state.resourceStatus.jacketFileName ?: texts.notDetected}")
        appendLine("${texts.background}: ${state.resourceStatus.backgroundFileName ?: texts.backgroundNotDetected}")
        appendLine("${texts.identifierPreview}: ${identifierPreview(state.metadataDraft, state.songId)}")
        appendLine("${texts.warnings}:")
        appendList(state.warnings, texts)
        appendLine("${texts.error}: ${state.errorMessage ?: texts.none}")
    }

private fun StringBuilder.appendList(values: List<String>, texts: AppText) {
    if (values.isEmpty()) appendLine("- ${texts.none}") else values.forEach { appendLine("- $it") }
}
