package com.zeerqi27.etoilebridge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material3.Switch
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
import com.zeerqi27.etoilebridge.model.PackStateRules
import com.zeerqi27.etoilebridge.model.ResponsiveLayoutRules
import com.zeerqi27.etoilebridge.model.UiFeedbackStateRules
import com.zeerqi27.etoilebridge.model.UiAppPage
import com.zeerqi27.etoilebridge.model.UiConvertOptions
import com.zeerqi27.etoilebridge.model.UiLanguage
import com.zeerqi27.etoilebridge.model.UiOperationPhase
import com.zeerqi27.etoilebridge.model.UiPackChartEntry
import com.zeerqi27.etoilebridge.model.UiPackConvertState
import com.zeerqi27.etoilebridge.model.UiPackEntry
import com.zeerqi27.etoilebridge.model.UiPackMode
import com.zeerqi27.etoilebridge.model.UiSaveStatus
import com.zeerqi27.etoilebridge.model.UiScanStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PackHomeScreen(
    state: UiPackConvertState,
    onSwitchToSingle: () -> Unit,
    onSwitchToCharacter: () -> Unit,
    onModeChange: (UiPackMode) -> Unit,
    onSelectOfficialZip: () -> Unit,
    onSelectOfficialFolder: () -> Unit,
    onSelectArcpkgFiles: () -> Unit,
    onSelectArcpkgFolder: () -> Unit,
    onSelectArcpkgZip: () -> Unit,
    onSelectExistingPack: () -> Unit,
    onSelectExistingAddFiles: () -> Unit,
    onSelectExistingAddFolder: () -> Unit,
    onScan: () -> Unit,
    onPack: () -> Unit,
    onSaveDownloads: () -> Unit,
    onSaveOutput: () -> Unit,
    onPublisherChange: (String) -> Unit,
    onPackNameChange: (String) -> Unit,
    onPackIdChange: (String) -> Unit,
    onSelectPackImage: () -> Unit,
    onClearPackImage: () -> Unit,
    onOutputFileNameChange: (String) -> Unit,
    onIncludeOnlyConvertibleChange: (Boolean) -> Unit,
    onOptionsChange: (UiConvertOptions) -> Unit,
    onEntryChange: (UiPackEntry) -> Unit,
    onLanguageChange: (UiLanguage) -> Unit,
    onClearCache: () -> Unit,
) {
    val texts = textFor(state.language)
    val packTexts = packText(state.language)
    val busy = state.isCopying || state.isScanning || state.isPacking || state.isSaving
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val phase = UiFeedbackStateRules.packPhase(state)
    val packSaveRequester = remember { BringIntoViewRequester() }
    var highlightPackSave by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val scrollState = rememberScrollState()
    val continueTarget = UiFeedbackStateRules.packContinueTarget(state)
    val continueAction: (() -> Unit)? = if (continueTarget != null) {
        {
            scope.launch {
                packSaveRequester.bringIntoView()
                highlightPackSave = true
                delay(650)
                highlightPackSave = false
            }
        }
    } else {
        null
    }

    LaunchedEffect(state.scanStatus) {
        when (state.scanStatus) {
            UiScanStatus.Scanned -> snackbarHostState.showSnackbar("扫描完成")
            UiScanStatus.Failed -> snackbarHostState.showSnackbar("扫描失败")
            else -> Unit
        }
    }
    LaunchedEffect(state.pendingOutputFile) {
        if (state.pendingOutputFile != null) snackbarHostState.showSnackbar("打包完成，等待保存")
    }
    LaunchedEffect(state.bundleValidationPassed) {
        when (state.bundleValidationPassed) {
            true -> snackbarHostState.showSnackbar(packTexts.bundleValidationPassed)
            false -> snackbarHostState.showSnackbar(packTexts.bundleValidationFailed)
            null -> Unit
        }
    }
    LaunchedEffect(state.saveStatus) {
        when (state.saveStatus) {
            UiSaveStatus.Saved -> snackbarHostState.showSnackbar("已保存")
            UiSaveStatus.Failed -> snackbarHostState.showSnackbar("保存失败，文件仍处于待保存状态")
            UiSaveStatus.Canceled -> snackbarHostState.showSnackbar(texts.saveStatusLabel(UiSaveStatus.Canceled))
            else -> Unit
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar("操作失败") }
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
                    currentPage = UiAppPage.PackBundle,
                    language = state.language,
                    busy = busy,
                    clearCacheEnabled = state.pendingOutputFile == null,
                    deviceSdkInt = state.deviceSdkInt,
                    deviceRelease = state.deviceRelease,
                    onPageSelected = {
                        when (it) {
                            UiAppPage.SingleSong -> onSwitchToSingle()
                            UiAppPage.PackBundle -> Unit
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
                        val twoColumns = ResponsiveLayoutRules.useTwoColumns(maxWidth.value.toInt())
                        if (twoColumns) {
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(0.9f)) {
                                PackInputAndSettingsColumnLocalized(
                                    state = state,
                                    busy = busy,
                                    phase = phase,
                                    continueAction = continueAction,
                                    onModeChange = onModeChange,
                                    onSelectOfficialZip = onSelectOfficialZip,
                                    onSelectOfficialFolder = onSelectOfficialFolder,
                                    onSelectArcpkgFiles = onSelectArcpkgFiles,
                                    onSelectArcpkgFolder = onSelectArcpkgFolder,
                                    onSelectArcpkgZip = onSelectArcpkgZip,
                                    onSelectExistingPack = onSelectExistingPack,
                                    onSelectExistingAddFiles = onSelectExistingAddFiles,
                                    onSelectExistingAddFolder = onSelectExistingAddFolder,
                                    onScan = onScan,
                                    onPublisherChange = onPublisherChange,
                                    onPackNameChange = onPackNameChange,
                                    onPackIdChange = onPackIdChange,
                                    onSelectPackImage = onSelectPackImage,
                                    onClearPackImage = onClearPackImage,
                                    onOutputFileNameChange = onOutputFileNameChange,
                                    onIncludeOnlyConvertibleChange = onIncludeOnlyConvertibleChange,
                                    onOptionsChange = onOptionsChange,
                                    onPack = onPack,
                                    onSaveDownloads = onSaveDownloads,
                                    onSaveOutput = onSaveOutput,
                                    packSaveRequester = packSaveRequester,
                                    highlightPackSave = highlightPackSave,
                                )
                            }
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1.1f)) {
                                PackEntriesAndFeedbackColumnLocalized(
                                    state = state,
                                    busy = busy,
                                    onEntryChange = onEntryChange,
                                    onSaveDownloads = onSaveDownloads,
                                    onSaveOutput = onSaveOutput,
                                    clipboard = clipboard,
                                )
                            }
                        }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                            PackInputAndSettingsColumnLocalized(
                                state = state,
                                busy = busy,
                                phase = phase,
                                continueAction = continueAction,
                                onModeChange = onModeChange,
                                onSelectOfficialZip = onSelectOfficialZip,
                                onSelectOfficialFolder = onSelectOfficialFolder,
                                onSelectArcpkgFiles = onSelectArcpkgFiles,
                                onSelectArcpkgFolder = onSelectArcpkgFolder,
                                onSelectArcpkgZip = onSelectArcpkgZip,
                                onSelectExistingPack = onSelectExistingPack,
                                onSelectExistingAddFiles = onSelectExistingAddFiles,
                                onSelectExistingAddFolder = onSelectExistingAddFolder,
                                onScan = onScan,
                                onPublisherChange = onPublisherChange,
                                onPackNameChange = onPackNameChange,
                                onPackIdChange = onPackIdChange,
                                onSelectPackImage = onSelectPackImage,
                                onClearPackImage = onClearPackImage,
                                onOutputFileNameChange = onOutputFileNameChange,
                                onIncludeOnlyConvertibleChange = onIncludeOnlyConvertibleChange,
                                onOptionsChange = onOptionsChange,
                                onPack = onPack,
                                onSaveDownloads = onSaveDownloads,
                                onSaveOutput = onSaveOutput,
                                packSaveRequester = packSaveRequester,
                                highlightPackSave = highlightPackSave,
                            )
                            PackEntriesAndFeedbackColumnLocalized(
                                state = state,
                                busy = busy,
                                onEntryChange = onEntryChange,
                                onSaveDownloads = onSaveDownloads,
                                onSaveOutput = onSaveOutput,
                                clipboard = clipboard,
                            )
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = bottomInset + 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PackInputAndSettingsColumn(
    state: UiPackConvertState,
    busy: Boolean,
    phase: UiOperationPhase,
    continueAction: (() -> Unit)?,
    onModeChange: (UiPackMode) -> Unit,
    onSelectOfficialZip: () -> Unit,
    onSelectOfficialFolder: () -> Unit,
    onSelectArcpkgFiles: () -> Unit,
    onSelectArcpkgFolder: () -> Unit,
    onSelectArcpkgZip: () -> Unit,
    onSelectExistingPack: () -> Unit,
    onSelectExistingAddFiles: () -> Unit,
    onSelectExistingAddFolder: () -> Unit,
    onScan: () -> Unit,
    onPublisherChange: (String) -> Unit,
    onPackNameChange: (String) -> Unit,
    onPackIdChange: (String) -> Unit,
    onSelectPackImage: () -> Unit,
    onClearPackImage: () -> Unit,
    onOutputFileNameChange: (String) -> Unit,
    onIncludeOnlyConvertibleChange: (Boolean) -> Unit,
    onOptionsChange: (UiConvertOptions) -> Unit,
    onPack: () -> Unit,
    onSaveDownloads: () -> Unit,
    onSaveOutput: () -> Unit,
    packSaveRequester: BringIntoViewRequester,
    highlightPackSave: Boolean,
) {
    PackHeroCard(
        state = state,
        busy = busy,
        onModeChange = onModeChange,
        onSelectOfficialZip = onSelectOfficialZip,
        onSelectOfficialFolder = onSelectOfficialFolder,
        onSelectArcpkgFiles = onSelectArcpkgFiles,
        onSelectArcpkgFolder = onSelectArcpkgFolder,
        onSelectArcpkgZip = onSelectArcpkgZip,
        onSelectExistingPack = onSelectExistingPack,
        onSelectExistingAddFiles = onSelectExistingAddFiles,
        onSelectExistingAddFolder = onSelectExistingAddFolder,
        onScan = onScan,
    )
    OperationStatusCard(
        phase = phase,
        title = packPhaseTitle(state),
        detail = packPhaseDetail(state),
        statusLabel = operationPhaseLabel(phase, state.language),
        actionLabel = continueAction?.let { continueLabel(state.language) },
        onAction = continueAction,
    )
    if (state.inputName == null) {
        EmptyStateCard(
            if (state.mode == UiPackMode.OfficialArcaeaPack) AppSymbols.Archive else AppSymbols.Folder,
            if (state.mode == UiPackMode.OfficialArcaeaPack) "选择官方曲包 ZIP 或文件夹" else "选择多个 arcpkg 或 arcpkg 文件夹",
            "扫描后会显示曲目、可打包数量和结构验证结果。",
        )
    }
    PackSettingsCard(
        state = state,
        busy = busy,
        onPublisherChange = onPublisherChange,
        onPackNameChange = onPackNameChange,
        onPackIdChange = onPackIdChange,
        onSelectPackImage = onSelectPackImage,
        onClearPackImage = onClearPackImage,
        onOutputFileNameChange = onOutputFileNameChange,
        onIncludeOnlyConvertibleChange = onIncludeOnlyConvertibleChange,
        onOptionsChange = onOptionsChange,
    )
    PackSaveCard(
        state,
        busy,
        onPack,
        onSaveDownloads,
        onSaveOutput,
        modifier = Modifier.bringIntoViewRequester(packSaveRequester),
        highlight = highlightPackSave,
    )
}

@Composable
private fun PackEntriesAndFeedbackColumn(
    state: UiPackConvertState,
    busy: Boolean,
    onEntryChange: (UiPackEntry) -> Unit,
    onSaveDownloads: () -> Unit,
    onSaveOutput: () -> Unit,
    clipboard: ClipboardManager,
) {
    if (state.scanStatus == UiScanStatus.Scanned && state.entries.isEmpty()) {
        EmptyStateCard(AppSymbols.Warning, "没有可打包项目", "请检查输入结构，或切换模式重新选择。")
    }
    PackEntriesCard(state, busy, onEntryChange)
    PackResultCard(state, onSaveDownloads, onSaveOutput, busy)
    FeedbackMessagesCard(state.warnings, state.errorMessage, state.errorDetails, state.bundleValidationErrors)
    PackAdvancedInfoCard(state, clipboard)
}

@Composable
private fun PackAdvancedInfoCard(state: UiPackConvertState, clipboard: ClipboardManager) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val texts = textFor(state.language)
    val packTexts = packText(state.language)
    ElevatedCard(
        modifier = Modifier
            .animateContentSize()
            .tapFeedbackOnly(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) texts.hideDetails else texts.showDetails)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    AdvancedInfoSection(packTexts.deviceInfo) {
                        KeyValue("Device", "SDK ${state.deviceSdkInt} / Android ${state.deviceRelease}")
                    }
                    AdvancedInfoSection(packTexts.inputInfo) {
                        KeyValue("mode", state.mode.name)
                        KeyValue("input", state.inputName ?: packTexts.none)
                        KeyValue("workspace", state.workspacePath ?: packTexts.none)
                        KeyValue("projectRoot", state.projectRootPath ?: packTexts.none)
                        KeyValue("scanStatus", state.scanStatus.name)
                    }
                    AdvancedInfoSection(packTexts.outputInfo) {
                        KeyValue("pendingFile", state.pendingOutputFile?.name ?: packTexts.none)
                        KeyValue("savedLocation", state.savedLocation ?: texts.notSaved)
                        KeyValue("workspace", if (state.workspaceCleaned) packTexts.cleaned else packTexts.kept)
                    }
                    AdvancedInfoSection(packTexts.logs) {
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(packClipboardText(state))) }) {
                            Text(packTexts.copyLogsAndScan)
                        }
                        LogPanel(packTexts.warning, state.warnings.map { localizeKnownMessage(it, state.language) }, texts)
                        CollapsiblePackLogs(packTexts.logs, state.logs.map { localizeKnownMessage(it, state.language) })
                    }
                    if (state.bundleValidationSummary.isNotEmpty() || state.bundleValidationErrors.isNotEmpty()) {
                        AdvancedInfoSection(packTexts.validator) {
                            state.bundleValidationSummary.forEach { Text(localizeKnownMessage(it, state.language), softWrap = true) }
                            state.bundleValidationErrors.forEach {
                                Text(localizeKnownMessage(it, state.language), color = MaterialTheme.colorScheme.error, softWrap = true)
                            }
                        }
                    }
                    if (state.errorMessage != null || state.errorDetails != null) {
                        AdvancedInfoSection(texts.errorDetails) {
                            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, softWrap = true) }
                            state.errorDetails?.let {
                                Text(it, maxLines = 12, overflow = TextOverflow.Ellipsis, softWrap = true)
                            }
                        }
                    }
                }
            }
            if (!expanded) {
                Text("${texts.warnings}: ${state.warnings.size}")
            }
        }
    }
}

@Composable
private fun PackTopBar(
    texts: AppText,
    state: UiPackConvertState,
    busy: Boolean,
    onSwitchToSingle: () -> Unit,
    onSwitchToCharacter: () -> Unit,
    onLanguageChange: (UiLanguage) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageMenuOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var languageOpen by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(EtoileShapeTokens.TopBar),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(texts.appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(packPageTitle(state.language), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Row {
                Box {
                    IconButton(onClick = { pageMenuOpen = true }) {
                        SymbolIcon(AppSymbols.SwitchPage, contentDescription = texts.switchPage)
                    }
                    DropdownMenu(pageMenuOpen, onDismissRequest = { pageMenuOpen = false }) {
                        DropdownMenuItem(text = { Text(texts.pageTitle) }, onClick = { pageMenuOpen = false; onSwitchToSingle() })
                        DropdownMenuItem(text = { Text(characterText(state.language).pageTitle) }, onClick = { pageMenuOpen = false; onSwitchToCharacter() })
                        DropdownMenuItem(text = { Text("${packPageTitle(state.language)} ✓") }, onClick = { pageMenuOpen = false })
                    }
                }
                Box {
                    IconButton(onClick = { settingsOpen = true }) {
                        SymbolIcon(AppSymbols.Settings, contentDescription = texts.settings)
                    }
                    DropdownMenu(settingsOpen, onDismissRequest = { settingsOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(texts.language) },
                            leadingIcon = { SymbolIcon(AppSymbols.Language, contentDescription = null) },
                            onClick = { settingsOpen = false; languageOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text(texts.clearCache) },
                            leadingIcon = { SymbolIcon(AppSymbols.Delete, contentDescription = null) },
                            enabled = !busy && state.pendingOutputFile == null,
                            onClick = { settingsOpen = false; onClearCache() },
                        )
                        DropdownMenuItem(
                            text = { Text(texts.about) },
                            leadingIcon = { SymbolIcon(AppSymbols.Info, contentDescription = null) },
                            onClick = { settingsOpen = false },
                        )
                    }
                    DropdownMenu(languageOpen, onDismissRequest = { languageOpen = false }) {
                        DropdownMenuItem(text = { Text(texts.chinese) }, onClick = { languageOpen = false; onLanguageChange(UiLanguage.ZhHans) })
                        DropdownMenuItem(text = { Text(texts.english) }, onClick = { languageOpen = false; onLanguageChange(UiLanguage.English) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PackHeroCard(
    state: UiPackConvertState,
    busy: Boolean,
    onModeChange: (UiPackMode) -> Unit,
    onSelectOfficialZip: () -> Unit,
    onSelectOfficialFolder: () -> Unit,
    onSelectArcpkgFiles: () -> Unit,
    onSelectArcpkgFolder: () -> Unit,
    onSelectArcpkgZip: () -> Unit,
    onSelectExistingPack: () -> Unit,
    onSelectExistingAddFiles: () -> Unit,
    onSelectExistingAddFolder: () -> Unit,
    onScan: () -> Unit,
) {
    val strings = packText(state.language)
    EdgeAwareCard(
        modifier = Modifier.tapFeedbackOnly(),
        shape = EtoileShapeTokens.HeroCard,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(strings.pageTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            AdaptiveActionRow {
                FilterChip(
                    selected = state.mode == UiPackMode.OfficialArcaeaPack,
                    onClick = { onModeChange(UiPackMode.OfficialArcaeaPack) },
                    enabled = !busy,
                    label = { Text(strings.officialMode) },
                )
                FilterChip(
                    selected = state.mode == UiPackMode.ArcpkgBundle,
                    onClick = { onModeChange(UiPackMode.ArcpkgBundle) },
                    enabled = !busy,
                    label = { Text(strings.arcpkgMode) },
                )
                FilterChip(
                    selected = state.mode == UiPackMode.ExistingPackEdit,
                    onClick = { onModeChange(UiPackMode.ExistingPackEdit) },
                    enabled = !busy,
                    label = { Text(strings.existingPackMode) },
                )
            }
            AdaptiveActionRow {
                when (state.mode) {
                    UiPackMode.OfficialArcaeaPack -> {
                        Button(onClick = onSelectOfficialZip, enabled = !busy) { PackButtonIcon(AppSymbols.Archive, strings.selectOfficialZip) }
                        FilledTonalButton(onClick = onSelectOfficialFolder, enabled = !busy) { PackButtonIcon(AppSymbols.Folder, strings.selectOfficialFolder) }
                    }
                    UiPackMode.ArcpkgBundle -> {
                        Button(onClick = onSelectArcpkgFiles, enabled = !busy) { PackButtonIcon(AppSymbols.Article, strings.selectArcpkgs) }
                        FilledTonalButton(onClick = onSelectArcpkgFolder, enabled = !busy) { PackButtonIcon(AppSymbols.Folder, strings.selectArcpkgFolder) }
                        OutlinedButton(onClick = onSelectArcpkgZip, enabled = !busy) { PackButtonIcon(AppSymbols.Archive, strings.selectArcpkgZip) }
                    }
                    UiPackMode.ExistingPackEdit -> {
                        Button(onClick = onSelectExistingPack, enabled = !busy) { PackButtonIcon(AppSymbols.Archive, strings.selectExistingPack) }
                        FilledTonalButton(onClick = onSelectExistingAddFiles, enabled = !busy) { PackButtonIcon(AppSymbols.Article, strings.selectArcpkgToAdd) }
                        OutlinedButton(onClick = onSelectExistingAddFolder, enabled = !busy) { PackButtonIcon(AppSymbols.Folder, strings.selectArcpkgFolderToAdd) }
                    }
                }
                OutlinedButton(onClick = onScan, enabled = state.canScan && !busy) {
                    PackLoadingButtonIcon(state.isScanning, AppSymbols.Search, strings.scan)
                }
            }
            AdaptiveActionRow {
                AssistChip(onClick = {}, label = { Text(state.inputName ?: strings.notSelected) })
                AssistChip(onClick = {}, label = { Text(scanStatusText(state)) })
                AssistChip(onClick = {}, label = { Text(strings.packableCount(state.entries.count { it.canConvert }, state.entries.size)) })
            }
        }
    }
}

@Composable
private fun PackSettingsCard(
    state: UiPackConvertState,
    busy: Boolean,
    onPublisherChange: (String) -> Unit,
    onPackNameChange: (String) -> Unit,
    onPackIdChange: (String) -> Unit,
    onSelectPackImage: () -> Unit,
    onClearPackImage: () -> Unit,
    onOutputFileNameChange: (String) -> Unit,
    onIncludeOnlyConvertibleChange: (Boolean) -> Unit,
    onOptionsChange: (UiConvertOptions) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .animateContentSize()
            .tapFeedbackOnly(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(AppSymbols.Tune, "曲包设置")
            OutlinedTextField(state.outputFileName, onOutputFileNameChange, label = { Text("输出文件名") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.packName, onPackNameChange, label = { Text("曲包名称") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.publisherId, onPublisherChange, label = { Text("发布者 ID") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.packId, onPackIdChange, label = { Text("曲包识别码") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("曲包 identifier 预览：${state.publisherId}.${state.packId}.pack", color = MaterialTheme.colorScheme.primary)
            Text(
                "Level identifier：每首歌默认 ${state.publisherId}.songId，可在曲目列表中逐曲调整。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PackImageSelector(state, busy, onSelectPackImage, onClearPackImage)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(checked = state.includeOnlyConvertible, onCheckedChange = onIncludeOnlyConvertibleChange, enabled = !busy)
                Text("只打包可转换项目")
            }
            if (!state.includeOnlyConvertible && state.entries.any { !it.canConvert }) {
                Text(
                    "当前有不可打包项目。关闭“只打包可转换项目”时需要先修复它们。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (PackStateRules.showPreprocessingOptions(state)) {
                OptionPanel(state.options, textFor(state.language), onOptionsChange, enabled = !busy)
            } else {
                AssistChip(onClick = {}, label = { Text("多个 arcpkg 打包模式不需要 AFF 预处理") })
            }
        }
    }
}

@Composable
private fun PackImageSelector(
    state: UiPackConvertState,
    busy: Boolean,
    onSelectPackImage: () -> Unit,
    onClearPackImage: () -> Unit,
) {
    val preview = rememberImagePreview(state.packImageFilePath, maxWidthPx = 320, maxHeightPx = 320)
    var showDetails by rememberSaveable { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier.tapFeedbackOnly(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .aspectRatio(1f)
                    .clickable { showDetails = true },
                contentAlignment = Alignment.Center,
            ) {
                val image = preview.image
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = "曲包封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    SymbolIcon(AppSymbols.Image, contentDescription = null, size = 36.dp, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("曲包封面", fontWeight = FontWeight.SemiBold)
                Text(
                    state.packImageFileName ?: "未识别，可手动选择",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when {
                                state.packImageManual -> "手动指定"
                                state.packImageFileName != null -> "已识别"
                                else -> "未识别"
                            }
                        )
                    },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onSelectPackImage, enabled = !busy) {
                    Text(if (state.packImageFileName == null) "选择封面" else "更换封面")
                }
                if (state.packImageManual) {
                    TextButton(onClick = onClearPackImage, enabled = !busy) { Text("清除") }
                }
            }
        }
    }
    if (showDetails) {
        val texts = textFor(state.language)
        val packImageLabel = if (state.language == UiLanguage.English) "Pack image" else "曲包封面"
        ImageDetailDialog(
            title = "${texts.imageDetails} - $packImageLabel",
            label = packImageLabel,
            fileName = state.packImageFileName,
            filePath = state.packImageFilePath,
            manual = state.packImageManual,
            texts = texts,
            onDismiss = { showDetails = false },
            onReplace = {
                showDetails = false
                onSelectPackImage()
            },
            onClearManual = if (state.packImageManual) {
                {
                    showDetails = false
                    onClearPackImage()
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun PackEntriesCard(
    state: UiPackConvertState,
    busy: Boolean,
    onEntryChange: (UiPackEntry) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .animateContentSize()
            .tapFeedbackOnly(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(AppSymbols.AccountTree, "曲目列表")
            if (state.entries.isEmpty()) {
                Text("扫描后会显示曲目 / level 列表。")
            } else {
                PackEntryStats(state)
                val duplicateLevelIds = state.entries
                    .filter { it.enabled }
                    .groupBy { it.levelId.ifBlank { it.songId } }
                    .filterValues { it.size > 1 }
                    .keys
                if (duplicateLevelIds.isNotEmpty()) {
                    Text(
                        "levelId 冲突：${duplicateLevelIds.joinToString()}。导出时会自动追加后缀，但建议先调整。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.entries.forEach { PackEntryRow(state, it, busy, onEntryChange) }
            }
            if (state.sourceReports.isNotEmpty()) {
                HorizontalDivider()
                Text("arcpkg 来源", fontWeight = FontWeight.SemiBold)
                state.sourceReports.forEach {
                    Text("${it.sourceName}: ${if (it.readable) "${it.levelCount} levels" else it.failureReason}")
                }
            }
        }
    }
}

@Composable
private fun PackEntryStats(state: UiPackConvertState) {
    val total = state.entries.size
    val enabled = state.entries.count { it.enabled }
    val packable = state.entries.count { it.effectiveCanPack }
    val skipped = state.entries.count { !it.enabled }
    val warnings = state.entries.count { it.warningCount > 0 }
    val failed = state.entries.count { !it.canConvert && !it.metadataStatus.contains("metadata", ignoreCase = true) }
    AdaptiveActionRow {
        AssistChip(onClick = {}, label = { Text("总数 $total") })
        AssistChip(onClick = {}, label = { Text("已启用 $enabled") })
        AssistChip(onClick = {}, label = { Text("可打包 $packable") })
        AssistChip(onClick = {}, label = { Text("跳过 $skipped") })
        AssistChip(onClick = {}, label = { Text("warning $warnings") })
        AssistChip(onClick = {}, label = { Text("failed $failed") })
    }
    if (enabled == 0 && total > 0) {
        Text("没有可打包项目。请启用至少一个可转换项目。", color = MaterialTheme.colorScheme.error)
    }
    if (!state.includeOnlyConvertible && state.entries.any { it.enabled && !it.canConvert }) {
        Text("存在已启用但不可转换的项目，请修复或开启“只打包可转换项目”。", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun PackEntryRow(
    state: UiPackConvertState,
    entry: UiPackEntry,
    busy: Boolean,
    onEntryChange: (UiPackEntry) -> Unit,
) {
    var expanded by rememberSaveable(entry.key) { mutableStateOf(false) }
    val container = when {
        !entry.enabled -> MaterialTheme.colorScheme.surfaceContainerLowest
        entry.canConvert -> MaterialTheme.colorScheme.surfaceContainerLow
        entry.metadataStatus.contains("metadata", ignoreCase = true) -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    ElevatedCard(
        modifier = Modifier
            .animateContentSize()
            .tapFeedbackOnly(),
        colors = CardDefaults.elevatedCardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.songId, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AssistChip(onClick = {}, label = { Text(entry.statusLabel()) })
            }
            Text(entry.title.ifBlank { "曲名未识别" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.artist.isNotBlank()) Text(entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "charts ${entry.charts.count { it.enabled }} / ${entry.charts.size}: ${entry.difficultySummary.ifBlank { "-" }}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AdaptiveActionRow(horizontalSpacing = 6.dp, verticalSpacing = 6.dp) {
                AssistChip(onClick = {}, label = { Text("音频 ${if (entry.audio != null) "OK" else "-"}") })
                AssistChip(onClick = {}, label = { Text("曲绘 ${if (entry.jacket != null) "OK" else "-"}") })
                AssistChip(onClick = {}, label = { Text("背景 ${if (entry.background != null) "OK" else "-"}") })
                AssistChip(onClick = {}, label = { Text("warning ${entry.warningCount}") })
            }
            entry.failureReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = entry.enabled,
                    onCheckedChange = { onEntryChange(entry.copy(enabled = it)) },
                    enabled = !busy && entry.canBeEnabled,
                )
                Text(if (entry.enabled) "打包此项目" else "已跳过")
                val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "packEntryExpandRotation")
                TextButton(onClick = { expanded = !expanded }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (expanded) "收起" else "编辑 / 详情")
                        SymbolIcon(AppSymbols.ExpandMore, contentDescription = null, size = 18.dp, modifier = Modifier.rotate(rotation))
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                PackEntryEditor(
                    state = state,
                    entry = entry,
                    busy = busy,
                    onEntryChange = onEntryChange,
                )
            }
        }
    }
}

@Composable
private fun PackEntryEditor(
    state: UiPackConvertState,
    entry: UiPackEntry,
    busy: Boolean,
    onEntryChange: (UiPackEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        KeyValue("songId", entry.songId)
        OutlinedTextField(
            value = entry.title,
            onValueChange = { onEntryChange(entry.copy(title = it)) },
            label = { Text("曲名") },
            enabled = !busy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = entry.artist,
            onValueChange = { onEntryChange(entry.copy(artist = it)) },
            label = { Text("曲师") },
            enabled = !busy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = entry.levelId,
                onValueChange = { onEntryChange(entry.copy(levelId = it)) },
                label = { Text("谱面识别码 levelId") },
                enabled = !busy,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            "level identifier 预览：${state.publisherId}.${entry.levelId.ifBlank { entry.songId }}",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
        AdaptiveActionRow(horizontalSpacing = 6.dp, verticalSpacing = 6.dp) {
            AssistChip(onClick = {}, label = { Text("AFF ${entry.difficultySummary.ifBlank { "-" }}") })
            AssistChip(onClick = {}, label = { Text("音频 ${entry.audio ?: "-"}") })
            AssistChip(onClick = {}, label = { Text("曲绘 ${entry.jacket ?: "-"}") })
            AssistChip(onClick = {}, label = { Text("背景 ${entry.background ?: "-"}") })
            AssistChip(onClick = {}, label = { Text("metadata ${entry.metadataStatus}") })
        }
        Text("Charts", fontWeight = FontWeight.SemiBold)
        entry.charts.forEach { chart ->
            PackChartEditor(
                chart = chart,
                busy = busy,
                onChartChange = { updatedChart ->
                    onEntryChange(
                        entry.copy(
                            charts = entry.charts.map {
                                if (it.ratingClass == updatedChart.ratingClass) updatedChart else it
                            },
                            difficultySummary = entry.charts.map {
                                if (it.ratingClass == updatedChart.ratingClass) updatedChart else it
                            }.filter { it.enabled }.joinToString(" · ") { it.difficultyText.ifBlank { it.chartPath } },
                        )
                    )
                },
            )
        }
        if (entry.warnings.isNotEmpty()) {
            Text("warnings", fontWeight = FontWeight.SemiBold)
            entry.warnings.take(6).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
        entry.failureReason?.let {
            Text("失败原因：$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PackChartEditor(
    chart: UiPackChartEntry,
    busy: Boolean,
    onChartChange: (UiPackChartEntry) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .animateContentSize()
            .tapFeedbackOnly(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(chart.chartPath, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                AssistChip(onClick = {}, label = { Text(if (chart.canConvert) "chart OK" else "chart failed") })
                Switch(
                    checked = chart.enabled,
                    onCheckedChange = { onChartChange(chart.copy(enabled = it)) },
                    enabled = !busy && chart.canConvert,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = chart.difficultyText,
                    onValueChange = { onChartChange(chart.copy(difficultyText = it)) },
                    label = { Text("难度") },
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = chart.chartConstantText,
                    onValueChange = { onChartChange(chart.copy(chartConstantText = it)) },
                    label = { Text("谱面定数") },
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = chart.charter,
                    onValueChange = { onChartChange(chart.copy(charter = it)) },
                    label = { Text("谱师") },
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = chart.illustrator,
                    onValueChange = { onChartChange(chart.copy(illustrator = it)) },
                    label = { Text("曲绘设计") },
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            chart.failureReason?.let { Text("chart 失败：$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            chart.warnings.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun PackSaveCard(
    state: UiPackConvertState,
    busy: Boolean,
    onPack: () -> Unit,
    onSaveDownloads: () -> Unit,
    onSaveOutput: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    ElevatedCard(
        modifier = modifier
            .animateContentSize()
            .pulseHighlight(highlight)
            .tapFeedbackOnly(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(AppSymbols.Convert, "打包与保存")
            AdaptiveActionRow {
                Button(onClick = onPack, enabled = PackStateRules.canStartPacking(state, busy)) {
                    PackLoadingButtonIcon(state.isPacking, AppSymbols.Convert, "开始打包")
                }
                if (state.canUseMediaStoreDownloads) {
                    OutlinedButton(onClick = onSaveDownloads, enabled = state.canSaveDownloads && !busy) {
                        PackLoadingButtonIcon(state.isSaving, AppSymbols.Download, "保存到 Downloads")
                    }
                }
                OutlinedButton(onClick = onSaveOutput, enabled = state.canSave && !busy) {
                    PackLoadingButtonIcon(state.isSaving, AppSymbols.SaveAs, "另存为")
                }
            }
            KeyValue("保存状态", state.saveStatus.name)
            KeyValue("待保存文件", state.pendingOutputFile?.name ?: "无")
            KeyValue("文件大小", state.pendingOutputFileSize?.formatBytes() ?: "无")
            KeyValue("最终位置", state.savedLocation ?: "未保存")
            state.bundleValidationPassed?.let { passed ->
                AssistChip(
                    onClick = {},
                    label = { Text(if (passed) "曲包结构验证通过" else "曲包结构验证失败") },
                )
            }
            state.bundleValidationSummary.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (state.bundleValidationErrors.isNotEmpty()) {
                Text("结构异常：", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                state.bundleValidationErrors.take(8).forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            if (state.pendingOutputFile != null && state.saveStatus == UiSaveStatus.Pending) Text("曲包已生成，等待保存。")
        }
    }
}

@Composable
private fun PackAdvancedCard(state: UiPackConvertState, clipboard: ClipboardManager) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier
            .animateContentSize()
            .tapFeedbackOnly(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起高级信息" else "高级信息") }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                KeyValue("workspace", state.workspacePath ?: "无")
                KeyValue("projectRoot", state.projectRootPath ?: "无")
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(packClipboardText(state))) }) {
                    Text("复制日志 / 扫描结果")
                }
                LogPanel("warnings", state.warnings, textFor(state.language))
                CollapsiblePackLogs("logs", state.logs)
                state.errorMessage?.let { Text("error: $it", color = MaterialTheme.colorScheme.error) }
                state.errorDetails?.let { Text(it, maxLines = 12, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun SectionTitle(symbol: String, title: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SymbolIcon(symbol, contentDescription = null, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CollapsiblePackLogs(title: String, lines: List<String>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text("$title (${lines.size})") }
    AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            lines.takeLast(80).forEach { Text(it) }
        }
    }
}

@Composable
private fun PackButtonIcon(symbol: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        SymbolIcon(symbol, contentDescription = null, size = 20.dp)
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PackLoadingButtonIcon(loading: Boolean, symbol: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            SymbolIcon(symbol, contentDescription = null, size = 20.dp)
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PackInputAndSettingsColumnLocalized(
    state: UiPackConvertState,
    busy: Boolean,
    phase: UiOperationPhase,
    continueAction: (() -> Unit)?,
    onModeChange: (UiPackMode) -> Unit,
    onSelectOfficialZip: () -> Unit,
    onSelectOfficialFolder: () -> Unit,
    onSelectArcpkgFiles: () -> Unit,
    onSelectArcpkgFolder: () -> Unit,
    onSelectArcpkgZip: () -> Unit,
    onSelectExistingPack: () -> Unit,
    onSelectExistingAddFiles: () -> Unit,
    onSelectExistingAddFolder: () -> Unit,
    onScan: () -> Unit,
    onPublisherChange: (String) -> Unit,
    onPackNameChange: (String) -> Unit,
    onPackIdChange: (String) -> Unit,
    onSelectPackImage: () -> Unit,
    onClearPackImage: () -> Unit,
    onOutputFileNameChange: (String) -> Unit,
    onIncludeOnlyConvertibleChange: (Boolean) -> Unit,
    onOptionsChange: (UiConvertOptions) -> Unit,
    onPack: () -> Unit,
    onSaveDownloads: () -> Unit,
    onSaveOutput: () -> Unit,
    packSaveRequester: BringIntoViewRequester,
    highlightPackSave: Boolean,
) {
    val p = packText(state.language)
    PackHeroCard(
        state = state,
        busy = busy,
        strings = p,
        onModeChange = onModeChange,
        onSelectOfficialZip = onSelectOfficialZip,
        onSelectOfficialFolder = onSelectOfficialFolder,
        onSelectArcpkgFiles = onSelectArcpkgFiles,
        onSelectArcpkgFolder = onSelectArcpkgFolder,
        onSelectArcpkgZip = onSelectArcpkgZip,
        onSelectExistingPack = onSelectExistingPack,
        onSelectExistingAddFiles = onSelectExistingAddFiles,
        onSelectExistingAddFolder = onSelectExistingAddFolder,
        onScan = onScan,
    )
    OperationStatusCard(
        phase = phase,
        title = packPhaseTitle(state),
        detail = packPhaseDetail(state),
        statusLabel = operationPhaseLabel(phase, state.language),
        actionLabel = continueAction?.let { continueLabel(state.language) },
        onAction = continueAction,
    )
    if (state.inputName == null) {
        EmptyStateCard(
            when (state.mode) {
                UiPackMode.OfficialArcaeaPack -> AppSymbols.Archive
                UiPackMode.ArcpkgBundle -> AppSymbols.Folder
                UiPackMode.ExistingPackEdit -> AppSymbols.Archive
            },
            p.modeStartTitle(state.mode),
            p.emptyDetail,
        )
    }
    PackSettingsCard(
        state = state,
        busy = busy,
        strings = p,
        onPublisherChange = onPublisherChange,
        onPackNameChange = onPackNameChange,
        onPackIdChange = onPackIdChange,
        onSelectPackImage = onSelectPackImage,
        onClearPackImage = onClearPackImage,
        onOutputFileNameChange = onOutputFileNameChange,
        onIncludeOnlyConvertibleChange = onIncludeOnlyConvertibleChange,
        onOptionsChange = onOptionsChange,
    )
    PackSaveCard(
        state = state,
        busy = busy,
        strings = p,
        onPack = onPack,
        onSaveDownloads = onSaveDownloads,
        onSaveOutput = onSaveOutput,
        modifier = Modifier.bringIntoViewRequester(packSaveRequester),
        highlight = highlightPackSave,
    )
}

@Composable
private fun PackEntriesAndFeedbackColumnLocalized(
    state: UiPackConvertState,
    busy: Boolean,
    onEntryChange: (UiPackEntry) -> Unit,
    onSaveDownloads: () -> Unit,
    onSaveOutput: () -> Unit,
    clipboard: ClipboardManager,
) {
    val p = packText(state.language)
    if (state.scanStatus == UiScanStatus.Scanned && state.entries.isEmpty()) {
        EmptyStateCard(AppSymbols.Warning, p.noBundleableItems, p.noBundleableItemsDetail)
    }
    PackEntriesCard(state, busy, p, onEntryChange)
    PackResultCard(state, p, onSaveDownloads, onSaveOutput, busy)
    LocalizedFeedbackMessagesCard(
        warnings = state.warnings,
        errorMessage = state.errorMessage,
        errorDetails = state.errorDetails,
        validatorErrors = state.bundleValidationErrors,
        language = state.language,
    )
    PackAdvancedInfoCard(state, clipboard)
}

@Composable
private fun PackHeroCard(
    state: UiPackConvertState,
    busy: Boolean,
    strings: PackUiText,
    onModeChange: (UiPackMode) -> Unit,
    onSelectOfficialZip: () -> Unit,
    onSelectOfficialFolder: () -> Unit,
    onSelectArcpkgFiles: () -> Unit,
    onSelectArcpkgFolder: () -> Unit,
    onSelectArcpkgZip: () -> Unit,
    onSelectExistingPack: () -> Unit,
    onSelectExistingAddFiles: () -> Unit,
    onSelectExistingAddFolder: () -> Unit,
    onScan: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.tapFeedbackOnly(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(strings.pageTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            AdaptiveActionRow {
                FilterChip(selected = state.mode == UiPackMode.OfficialArcaeaPack, onClick = { onModeChange(UiPackMode.OfficialArcaeaPack) }, enabled = !busy, label = { Text(strings.officialMode) })
                FilterChip(selected = state.mode == UiPackMode.ArcpkgBundle, onClick = { onModeChange(UiPackMode.ArcpkgBundle) }, enabled = !busy, label = { Text(strings.arcpkgMode) })
                FilterChip(selected = state.mode == UiPackMode.ExistingPackEdit, onClick = { onModeChange(UiPackMode.ExistingPackEdit) }, enabled = !busy, label = { Text(strings.existingPackMode) })
            }
            AdaptiveActionRow {
                when (state.mode) {
                    UiPackMode.OfficialArcaeaPack -> {
                        Button(onClick = onSelectOfficialZip, enabled = !busy) { PackButtonIcon(AppSymbols.Archive, strings.selectOfficialZip) }
                        FilledTonalButton(onClick = onSelectOfficialFolder, enabled = !busy) { PackButtonIcon(AppSymbols.Folder, strings.selectOfficialFolder) }
                    }
                    UiPackMode.ArcpkgBundle -> {
                        Button(onClick = onSelectArcpkgFiles, enabled = !busy) { PackButtonIcon(AppSymbols.Article, strings.selectArcpkgs) }
                        FilledTonalButton(onClick = onSelectArcpkgFolder, enabled = !busy) { PackButtonIcon(AppSymbols.Folder, strings.selectArcpkgFolder) }
                        OutlinedButton(onClick = onSelectArcpkgZip, enabled = !busy) { PackButtonIcon(AppSymbols.Archive, strings.selectArcpkgZip) }
                    }
                    UiPackMode.ExistingPackEdit -> {
                        Button(onClick = onSelectExistingPack, enabled = !busy) { PackButtonIcon(AppSymbols.Archive, strings.selectExistingPack) }
                        FilledTonalButton(onClick = onSelectExistingAddFiles, enabled = !busy) { PackButtonIcon(AppSymbols.Article, strings.selectArcpkgToAdd) }
                        OutlinedButton(onClick = onSelectExistingAddFolder, enabled = !busy) { PackButtonIcon(AppSymbols.Folder, strings.selectArcpkgFolderToAdd) }
                    }
                }
                OutlinedButton(onClick = onScan, enabled = state.canScan && !busy) {
                    PackLoadingButtonIcon(state.isScanning, AppSymbols.Search, strings.scan)
                }
            }
            AdaptiveActionRow {
                AssistChip(onClick = {}, label = { Text(state.inputName ?: strings.notSelected) })
                AssistChip(onClick = {}, label = { Text(strings.scanStatusLabel(state.scanStatus)) })
                AssistChip(onClick = {}, label = { Text(strings.packableCount(state.entries.count { it.canConvert }, state.entries.size)) })
                if (state.mode == UiPackMode.ExistingPackEdit) {
                    AssistChip(onClick = {}, label = { Text("${strings.existingLevels}: ${state.existingLevelCount}") })
                    AssistChip(onClick = {}, label = { Text("${strings.addedLevels}: ${state.addedLevelCount}") })
                    AssistChip(onClick = {}, label = { Text("${strings.finalLevels}: ${state.finalLevelCount}") })
                    AssistChip(onClick = {}, label = { Text("${strings.renamedConflicts}: ${state.renamedConflictCount}") })
                }
            }
        }
    }
}

@Composable
private fun PackSettingsCard(
    state: UiPackConvertState,
    busy: Boolean,
    strings: PackUiText,
    onPublisherChange: (String) -> Unit,
    onPackNameChange: (String) -> Unit,
    onPackIdChange: (String) -> Unit,
    onSelectPackImage: () -> Unit,
    onClearPackImage: () -> Unit,
    onOutputFileNameChange: (String) -> Unit,
    onIncludeOnlyConvertibleChange: (Boolean) -> Unit,
    onOptionsChange: (UiConvertOptions) -> Unit,
) {
    EdgeAwareCard(modifier = Modifier.animateContentSize().tapFeedbackOnly()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(AppSymbols.Tune, strings.packSettings)
            OutlinedTextField(state.outputFileName, onOutputFileNameChange, label = { Text(strings.outputFilename) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.packName, onPackNameChange, label = { Text(strings.packName) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.publisherId, onPublisherChange, label = { Text(strings.publisherId) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.packId, onPackIdChange, label = { Text(strings.packId) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("${strings.packIdentifierPreview}: ${state.publisherId}.${state.packId}.pack", color = MaterialTheme.colorScheme.primary)
            Text(strings.levelIdentifierRule, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PackImageSelector(state, busy, strings, onSelectPackImage, onClearPackImage)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(checked = state.includeOnlyConvertible, onCheckedChange = onIncludeOnlyConvertibleChange, enabled = !busy)
                Text(strings.includeConvertibleOnly)
            }
            if (!state.includeOnlyConvertible && state.entries.any { !it.canConvert }) Text(strings.includeModeError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (PackStateRules.showPreprocessingOptions(state)) {
                OptionPanel(state.options, textFor(state.language), onOptionsChange, enabled = !busy)
            } else {
                AssistChip(onClick = {}, label = { Text(strings.noPreprocessInArcpkgMode) })
            }
        }
    }
}

@Composable
private fun PackImageSelector(
    state: UiPackConvertState,
    busy: Boolean,
    strings: PackUiText,
    onSelectPackImage: () -> Unit,
    onClearPackImage: () -> Unit,
) {
    val preview = rememberImagePreview(state.packImageFilePath, maxWidthPx = 320, maxHeightPx = 320)
    var showDetails by rememberSaveable { mutableStateOf(false) }
    ElevatedCard(modifier = Modifier.tapFeedbackOnly(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(88.dp).clickable { showDetails = true }, contentAlignment = Alignment.Center) {
                preview.image?.let { Image(bitmap = it, contentDescription = strings.packCover, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    ?: SymbolIcon(AppSymbols.Image, contentDescription = null, size = 36.dp, color = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.packCover, fontWeight = FontWeight.SemiBold)
                Text(state.packImageFileName ?: strings.notDetectedCanChoose, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AssistChip(onClick = {}, label = { Text(if (state.packImageManual) strings.manual else if (state.packImageFileName != null) strings.identified else strings.notSelected) })
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onSelectPackImage, enabled = !busy) { Text(if (state.packImageFileName == null) strings.selectCover else strings.replaceCover) }
                if (state.packImageManual) TextButton(onClick = onClearPackImage, enabled = !busy) { Text(strings.clearCover) }
            }
        }
    }
    if (showDetails) {
        val texts = textFor(state.language)
        ImageDetailDialog(
            title = "${texts.imageDetails} - ${strings.packCover}",
            label = strings.packCover,
            fileName = state.packImageFileName,
            filePath = state.packImageFilePath,
            manual = state.packImageManual,
            texts = texts,
            onDismiss = { showDetails = false },
            onReplace = { showDetails = false; onSelectPackImage() },
            onClearManual = if (state.packImageManual) ({ showDetails = false; onClearPackImage() }) else null,
        )
    }
}

@Composable
private fun PackSaveCard(
    state: UiPackConvertState,
    busy: Boolean,
    strings: PackUiText,
    onPack: () -> Unit,
    onSaveDownloads: () -> Unit,
    onSaveOutput: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    EdgeAwareCard(modifier = modifier.pulseHighlight(highlight).tapFeedbackOnly()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(AppSymbols.Convert, strings.bundleAndSave)
            AdaptiveActionRow {
                val actionLabel = if (state.mode == UiPackMode.ExistingPackEdit) strings.rebuildPack else strings.startBundling
                Button(onClick = onPack, enabled = PackStateRules.canStartPacking(state, busy)) { PackLoadingButtonIcon(state.isPacking, AppSymbols.Convert, actionLabel) }
                if (state.canUseMediaStoreDownloads) OutlinedButton(onClick = onSaveDownloads, enabled = state.canSaveDownloads && !busy) { PackLoadingButtonIcon(state.isSaving, AppSymbols.Download, textFor(state.language).saveDownloads) }
                OutlinedButton(onClick = onSaveOutput, enabled = state.canSave && !busy) { PackLoadingButtonIcon(state.isSaving, AppSymbols.SaveAs, textFor(state.language).saveAs) }
            }
            KeyValue(strings.saveStatus, state.saveStatus.name)
            KeyValue(strings.pendingFile, state.pendingOutputFile?.name ?: strings.none)
            KeyValue(strings.fileSize, state.pendingOutputFileSize?.formatBytes() ?: strings.none)
            KeyValue(strings.finalLocation, state.savedLocation ?: textFor(state.language).notSaved)
            state.bundleValidationPassed?.let { AssistChip(onClick = {}, label = { Text(if (it) strings.bundleValidationPassed else strings.bundleValidationFailed) }) }
            state.bundleValidationSummary.forEach { Text(localizeKnownMessage(it, state.language), style = MaterialTheme.typography.bodySmall) }
            if (state.bundleValidationErrors.isNotEmpty()) {
                Text(strings.structureErrors, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                state.bundleValidationErrors.take(8).forEach { Text("- ${localizeKnownMessage(it, state.language)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            if (state.pendingOutputFile != null && state.saveStatus == UiSaveStatus.Pending) Text(strings.pendingSave)
        }
    }
}

@Composable
private fun PackEntriesCard(
    state: UiPackConvertState,
    busy: Boolean,
    strings: PackUiText,
    onEntryChange: (UiPackEntry) -> Unit,
) {
    EdgeAwareCard(modifier = Modifier.animateContentSize().tapFeedbackOnly()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(AppSymbols.AccountTree, strings.levelList)
            if (state.entries.isEmpty()) Text(strings.emptyDetail) else {
                PackEntryStats(state, strings)
                state.entries.forEach { PackEntryRow(state, it, busy, strings, onEntryChange) }
            }
            if (state.sourceReports.isNotEmpty()) {
                HorizontalDivider()
                Text(strings.sources, fontWeight = FontWeight.SemiBold)
                state.sourceReports.forEach { Text("${it.sourceName}: ${if (it.readable) "${it.levelCount} levels" else localizeKnownMessage(it.failureReason.orEmpty(), state.language)}") }
            }
        }
    }
}

@Composable
private fun PackEntryStats(state: UiPackConvertState, strings: PackUiText) {
    val total = state.entries.size
    val enabled = state.entries.count { it.enabled }
    val packable = state.entries.count { it.effectiveCanPack }
    val skipped = state.entries.count { !it.enabled }
    val warnings = state.entries.count { it.warningCount > 0 }
    val failed = state.entries.count { !it.canConvert && !it.metadataStatus.contains("metadata", ignoreCase = true) }
    AdaptiveActionRow {
        AssistChip(onClick = {}, label = { Text("${strings.total} $total") })
        AssistChip(onClick = {}, label = { Text("${strings.enabled} $enabled") })
        AssistChip(onClick = {}, label = { Text("${strings.packable} $packable") })
        AssistChip(onClick = {}, label = { Text("${strings.skipped} $skipped") })
        AssistChip(onClick = {}, label = { Text("${strings.warning} $warnings") })
        AssistChip(onClick = {}, label = { Text("${strings.failed} $failed") })
        if (state.mode == UiPackMode.ExistingPackEdit) {
            AssistChip(onClick = {}, label = { Text("${strings.existingLevels} ${state.existingLevelCount}") })
            AssistChip(onClick = {}, label = { Text("${strings.addedLevels} ${state.addedLevelCount}") })
            AssistChip(onClick = {}, label = { Text("${strings.finalLevels} ${state.finalLevelCount}") })
            AssistChip(onClick = {}, label = { Text("${strings.renamedConflicts} ${state.renamedConflictCount}") })
        }
    }
    if (enabled == 0 && total > 0) Text(strings.noEnabledItems, color = MaterialTheme.colorScheme.error)
    if (!state.includeOnlyConvertible && state.entries.any { it.enabled && !it.canConvert }) Text(strings.enabledFailedHint, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun PackEntryRow(
    state: UiPackConvertState,
    entry: UiPackEntry,
    busy: Boolean,
    strings: PackUiText,
    onEntryChange: (UiPackEntry) -> Unit,
) {
    var expanded by rememberSaveable(entry.key) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .tapFeedbackOnly(),
        shape = RoundedCornerShape(EtoileShapeTokens.InnerCard),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.songId, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AssistChip(onClick = {}, label = { Text(packEntryStatusText(entry, strings)) })
            }
            Text(entry.title.ifBlank { "-" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.artist.isNotBlank()) Text(entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("charts ${entry.charts.count { it.enabled }} / ${entry.charts.size}: ${entry.difficultySummary.ifBlank { "-" }}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            AdaptiveActionRow(horizontalSpacing = 6.dp, verticalSpacing = 6.dp) {
                AssistChip(onClick = {}, label = { Text("${textFor(state.language).audio} ${if (entry.audio != null) "OK" else "-"}") })
                AssistChip(onClick = {}, label = { Text("${textFor(state.language).jacket} ${if (entry.jacket != null) "OK" else "-"}") })
                AssistChip(onClick = {}, label = { Text("${textFor(state.language).background} ${if (entry.background != null) "OK" else "-"}") })
                AssistChip(onClick = {}, label = { Text("${strings.warning} ${entry.warningCount}") })
            }
            entry.failureReason?.let { Text(localizeKnownMessage(it, state.language), color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = entry.enabled, onCheckedChange = { onEntryChange(entry.copy(enabled = it)) }, enabled = !busy && entry.canBeEnabled)
                Text(if (entry.enabled) strings.enabled else strings.skipped)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) textFor(state.language).hideDetails else textFor(state.language).showDetails) }
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                PackEntryEditor(state, entry, busy, strings, onEntryChange)
            }
        }
    }
}

@Composable
private fun PackEntryEditor(
    state: UiPackConvertState,
    entry: UiPackEntry,
    busy: Boolean,
    strings: PackUiText,
    onEntryChange: (UiPackEntry) -> Unit,
) {
    val texts = textFor(state.language)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        KeyValue("songId", entry.songId)
        OutlinedTextField(entry.title, { onEntryChange(entry.copy(title = it)) }, label = { Text(texts.title) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(entry.artist, { onEntryChange(entry.copy(artist = it)) }, label = { Text(texts.artist) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(entry.levelId, { onEntryChange(entry.copy(levelId = it)) }, label = { Text(texts.levelId) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("${texts.identifierPreview}: ${state.publisherId}.${entry.levelId.ifBlank { entry.songId }}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        Text("Charts", fontWeight = FontWeight.SemiBold)
        entry.charts.forEach { chart ->
            PackChartEditor(chart, busy, state.language) { updatedChart ->
                val updatedCharts = entry.charts.map { if (it.ratingClass == updatedChart.ratingClass) updatedChart else it }
                onEntryChange(entry.copy(charts = updatedCharts, difficultySummary = updatedCharts.filter { it.enabled }.joinToString(" · ") { it.difficultyText.ifBlank { it.chartPath } }))
            }
        }
        if (entry.warnings.isNotEmpty()) {
            Text(strings.warning, fontWeight = FontWeight.SemiBold)
            entry.warnings.take(6).forEach { Text("- ${localizeKnownMessage(it, state.language)}", style = MaterialTheme.typography.bodySmall) }
        }
        entry.failureReason?.let { Text("${strings.failed}: ${localizeKnownMessage(it, state.language)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun PackChartEditor(chart: UiPackChartEntry, busy: Boolean, language: UiLanguage, onChartChange: (UiPackChartEntry) -> Unit) {
    val texts = textFor(language)
    ElevatedCard(modifier = Modifier.animateContentSize().tapFeedbackOnly(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(chart.chartPath, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Switch(checked = chart.enabled, onCheckedChange = { onChartChange(chart.copy(enabled = it)) }, enabled = !busy && chart.canConvert)
            }
            OutlinedTextField(chart.difficultyText, { onChartChange(chart.copy(difficultyText = it)) }, label = { Text(texts.difficulty) }, enabled = !busy && chart.enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(chart.chartConstantText, { onChartChange(chart.copy(chartConstantText = it)) }, label = { Text(texts.chartConstant) }, enabled = !busy && chart.enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(chart.charter, { onChartChange(chart.copy(charter = it)) }, label = { Text(texts.chartDesigner) }, enabled = !busy && chart.enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(chart.illustrator, { onChartChange(chart.copy(illustrator = it)) }, label = { Text(texts.jacketDesigner) }, enabled = !busy && chart.enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
            chart.failureReason?.let { Text(localizeKnownMessage(it, language), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun PackResultCard(
    state: UiPackConvertState,
    strings: PackUiText,
    onSaveDownloads: () -> Unit,
    onSaveOutput: () -> Unit,
    busy: Boolean,
) {
    AnimatedVisibility(visible = state.pendingOutputFile != null || state.saveStatus == UiSaveStatus.Saved || state.bundleValidationPassed == false, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
    EdgeAwareCard(
        modifier = Modifier.fillMaxWidth().tapFeedbackOnly(),
        containerColor = if (state.bundleValidationPassed == false) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
    ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(AppSymbols.Download, if (state.bundleValidationPassed == false) strings.bundleValidationFailed else strings.bundleAndSave)
                KeyValue(strings.outputFilename, state.pendingOutputFile?.name ?: state.outputFileName)
                KeyValue(strings.fileSize, state.pendingOutputFileSize?.formatBytes() ?: state.savedFileSize?.formatBytes() ?: strings.none)
                KeyValue(strings.packName, state.packName)
                KeyValue("pack identifier", "${state.publisherId}.${state.packId}.pack")
                KeyValue("levels", state.entries.count { it.enabled && it.effectiveCanPack }.toString())
                KeyValue(strings.packCover, state.packImageFileName ?: strings.notSelected)
                state.bundleValidationPassed?.let { AssistChip(onClick = {}, label = { Text(if (it) strings.bundleValidationPassed else strings.bundleValidationFailed) }) }
                if (state.bundleValidationErrors.isNotEmpty()) state.bundleValidationErrors.take(6).forEach { Text("- ${localizeKnownMessage(it, state.language)}", color = MaterialTheme.colorScheme.error) }
                AdaptiveActionRow {
                    if (state.canUseMediaStoreDownloads) OutlinedButton(onClick = onSaveDownloads, enabled = UiFeedbackStateRules.canSavePack(state, busy) && state.canSaveDownloads) { FeedbackButtonIcon(AppSymbols.Download, textFor(state.language).saveDownloads) }
                    OutlinedButton(onClick = onSaveOutput, enabled = UiFeedbackStateRules.canSavePack(state, busy)) { FeedbackButtonIcon(AppSymbols.SaveAs, textFor(state.language).saveAs) }
                }
                KeyValue(strings.finalLocation, state.savedLocation ?: textFor(state.language).notSaved)
            }
        }
    }
}

private fun packEntryStatusText(entry: UiPackEntry, strings: PackUiText): String =
    when {
        !entry.enabled -> strings.skipped
        entry.canConvert -> strings.packable
        entry.metadataStatus.contains("metadata", ignoreCase = true) -> strings.warning
        else -> strings.failed
    }

private fun packPageTitle(language: UiLanguage): String =
    packText(language).pageTitle

private fun scanStatusText(state: UiPackConvertState): String =
    packText(state.language).scanStatusLabel(state.scanStatus)

private fun packPhaseTitle(state: UiPackConvertState): String {
    val english = state.language == UiLanguage.English
    return when {
        state.isCopying -> if (english) "Copying input" else "正在复制输入"
        state.isScanning -> if (english) "Scanning" else "正在扫描"
        state.isPacking -> if (english) "Bundling" else "正在打包"
        state.bundleValidationPassed == false -> packText(state.language).bundleValidationFailed
        state.isSaving -> if (english) "Saving" else "正在保存"
        state.saveStatus == UiSaveStatus.Saved -> if (english) "Saved" else "已保存"
        state.pendingOutputFile != null && state.bundleValidationPassed == true -> if (english) "Bundle generated, waiting for save" else "打包完成，等待保存"
        state.pendingOutputFile != null -> if (english) "Validating structure" else "正在验证结构"
        state.errorMessage != null || state.scanStatus == UiScanStatus.Failed -> if (english) "Operation failed" else "操作失败"
        state.canPack -> if (english) "Bundle ready" else "可打包"
        state.inputName == null -> if (english) "No input selected" else "未选择输入"
        else -> scanStatusText(state)
    }
}

private fun packPhaseDetail(state: UiPackConvertState): String {
    val english = state.language == UiLanguage.English
    val packTexts = packText(state.language)
    return when {
        state.errorMessage != null -> localizeKnownMessage(state.errorMessage, state.language)
        state.bundleValidationErrors.isNotEmpty() -> localizeKnownMessage(state.bundleValidationErrors.first(), state.language)
        state.pendingOutputFile != null -> state.pendingOutputFile.name
        state.saveStatus == UiSaveStatus.Saved -> state.savedLocation ?: if (english) "Saved" else "已保存"
        state.canPack -> if (english) {
            "${state.entries.count { it.enabled && it.effectiveCanPack }} enabled item(s) can be bundled."
        } else {
            "已启用 ${state.entries.count { it.enabled && it.effectiveCanPack }} 个可打包项目。"
        }
        state.inputName == null -> if (state.mode == UiPackMode.OfficialArcaeaPack) {
            packTexts.officialEmptyTitle
        } else {
            packTexts.arcpkgEmptyTitle
        }
        state.entries.isEmpty() && state.scanStatus == UiScanStatus.Scanned -> packTexts.noBundleableItems
        else -> state.inputName ?: packTexts.notSelected
    }
}

private fun continueLabel(language: UiLanguage): String =
    if (language == UiLanguage.English) "Continue" else "可继续"

private fun packClipboardText(state: UiPackConvertState): String =
    buildString {
        appendLine("mode: ${state.mode}")
        appendLine("input: ${state.inputName}")
        appendLine("projectRoot: ${state.projectRootPath}")
        appendLine("packName: ${state.packName}")
        appendLine("packId: ${state.packId}")
        appendLine("packIdentifier: ${state.publisherId}.${state.packId}.pack")
        appendLine("packImage: ${state.packImageFileName ?: "(none)"}")
        appendLine("entries:")
        state.entries.forEach {
            appendLine(
                "- ${it.songId} enabled=${it.enabled} canConvert=${it.canConvert} " +
                    "levelId=${it.levelId} title=${it.title} reason=${it.failureReason.orEmpty()}"
            )
        }
        appendLine("warnings:")
        state.warnings.forEach { appendLine("- $it") }
        appendLine("logs:")
        state.logs.forEach { appendLine("- $it") }
        appendLine("error: ${state.errorMessage.orEmpty()}")
    }

private fun UiPackEntry.statusLabel(): String =
    when {
        !enabled -> "已跳过"
        canConvert -> "可打包"
        metadataDraftLooksComplete -> "可尝试打包"
        metadataStatus.contains("metadata", ignoreCase = true) -> "需要补充信息"
        else -> "不可打包"
    }
