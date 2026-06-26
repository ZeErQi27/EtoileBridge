package com.zeerqi27.etoilebridge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zeerqi27.etoilebridge.R
import com.zeerqi27.etoilebridge.model.ArcCreateResultLayoutSnapshots
import com.zeerqi27.etoilebridge.model.CharacterPreviewCoordinateMapper
import com.zeerqi27.etoilebridge.model.PreviewBounds
import com.zeerqi27.etoilebridge.model.ResponsiveLayoutRules
import com.zeerqi27.etoilebridge.model.UiAppPage
import com.zeerqi27.etoilebridge.model.UiCharacterInputType
import com.zeerqi27.etoilebridge.model.UiCharacterState
import com.zeerqi27.etoilebridge.model.UiLanguage
import com.zeerqi27.etoilebridge.model.UiOperationPhase
import com.zeerqi27.etoilebridge.model.UiSaveStatus
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun CharacterHomeScreen(
    state: UiCharacterState,
    onSwitchToSingle: () -> Unit,
    onSwitchToPack: () -> Unit,
    onSelectPng: () -> Unit,
    onSelectArcpkg: () -> Unit,
    onBuild: () -> Unit,
    onSaveDownloads: () -> Unit,
    onSaveOutput: () -> Unit,
    onPublisherChange: (String) -> Unit,
    onCharacterIdChange: (String) -> Unit,
    onDefaultNameChange: (String) -> Unit,
    onZhCnNameChange: (String) -> Unit,
    onOutputFileNameChange: (String) -> Unit,
    onCropCenterXChange: (Float) -> Unit,
    onCropCenterYChange: (Float) -> Unit,
    onCropSizeChange: (Float) -> Unit,
    onGenerateIcon: () -> Unit,
    onResetCrop: () -> Unit,
    onXChange: (Float) -> Unit,
    onYChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onResetPosition: () -> Unit,
    onCenterPosition: () -> Unit,
    onFitHeight: () -> Unit,
    onFitWidth: () -> Unit,
    onSampleDefault: () -> Unit,
    onLanguageChange: (UiLanguage) -> Unit,
    onClearCache: () -> Unit,
) {
    val texts = characterText(state.language)
    val appTexts = textFor(state.language)
    val busy = state.isCopying || state.isScanning || state.isBuilding || state.isSaving
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val phase = characterPhase(state)
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val scrollState = rememberScrollState()

    LaunchedEffect(state.inputType, state.inputName) {
        if (state.inputType == UiCharacterInputType.Png && state.inputName != null) {
            snackbarHostState.showSnackbar(texts.copiedPng)
        }
        if (state.inputType == UiCharacterInputType.Arcpkg && state.inputName != null) {
            snackbarHostState.showSnackbar(texts.importedArcpkg)
        }
    }
    LaunchedEffect(state.iconFilePath) {
        if (state.iconFilePath != null) snackbarHostState.showSnackbar(texts.iconGenerated)
    }
    LaunchedEffect(state.validationPassed) {
        when (state.validationPassed) {
            true -> snackbarHostState.showSnackbar(texts.validatorPassed)
            false -> snackbarHostState.showSnackbar(texts.validatorFailed)
            null -> Unit
        }
    }
    LaunchedEffect(state.saveStatus) {
        if (state.saveStatus == UiSaveStatus.Saved) snackbarHostState.showSnackbar(texts.saved)
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
                    currentPage = UiAppPage.Character,
                    language = state.language,
                    busy = busy,
                    clearCacheEnabled = state.pendingOutputFile == null,
                    deviceSdkInt = state.deviceSdkInt,
                    deviceRelease = state.deviceRelease,
                    onPageSelected = {
                        when (it) {
                            UiAppPage.SingleSong -> onSwitchToSingle()
                            UiAppPage.PackBundle -> onSwitchToPack()
                            UiAppPage.Character -> Unit
                        }
                    },
                    onLanguageChange = onLanguageChange,
                    onClearCache = {
                        onClearCache()
                        scope.launch { snackbarHostState.showSnackbar(appTexts.cacheCleared) }
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
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(0.9f)) {
                                    CharacterHeroCard(state, texts, busy, onSelectPng, onSelectArcpkg)
                                    OperationStatusCard(
                                        phase = phase,
                                        title = characterPhaseTitle(state, texts),
                                        detail = characterPhaseDetail(state, texts),
                                        statusLabel = operationPhaseLabel(phase, state.language),
                                    )
                                    CharacterInfoCard(
                                        state = state,
                                        texts = texts,
                                        busy = busy,
                                        onPublisherChange = onPublisherChange,
                                        onCharacterIdChange = onCharacterIdChange,
                                        onDefaultNameChange = onDefaultNameChange,
                                        onZhCnNameChange = onZhCnNameChange,
                                        onOutputFileNameChange = onOutputFileNameChange,
                                    )
                                    CharacterBuildSaveCard(state, texts, busy, onBuild, onSaveDownloads, onSaveOutput)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1.1f)) {
                                    CharacterImageCard(state, texts)
                                    CharacterIconCropCard(
                                        state = state,
                                        texts = texts,
                                        busy = busy,
                                        onCropCenterXChange = onCropCenterXChange,
                                        onCropCenterYChange = onCropCenterYChange,
                                        onCropSizeChange = onCropSizeChange,
                                        onGenerateIcon = onGenerateIcon,
                                        onResetCrop = onResetCrop,
                                    )
                                    CharacterPositionCard(
                                        state = state,
                                        texts = texts,
                                        onXChange = onXChange,
                                        onYChange = onYChange,
                                        onScaleChange = onScaleChange,
                                        onResetPosition = onResetPosition,
                                        onCenterPosition = onCenterPosition,
                                        onFitHeight = onFitHeight,
                                        onFitWidth = onFitWidth,
                                        onSampleDefault = onSampleDefault,
                                    )
                                    LocalizedFeedbackMessagesCard(state.warnings, state.errorMessage, state.errorDetails, language = state.language)
                                    CharacterAdvancedInfoCard(state, texts, clipboard)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                CharacterHeroCard(state, texts, busy, onSelectPng, onSelectArcpkg)
                                OperationStatusCard(
                                    phase = phase,
                                    title = characterPhaseTitle(state, texts),
                                    detail = characterPhaseDetail(state, texts),
                                    statusLabel = operationPhaseLabel(phase, state.language),
                                )
                                CharacterInfoCard(
                                    state = state,
                                    texts = texts,
                                    busy = busy,
                                    onPublisherChange = onPublisherChange,
                                    onCharacterIdChange = onCharacterIdChange,
                                    onDefaultNameChange = onDefaultNameChange,
                                    onZhCnNameChange = onZhCnNameChange,
                                    onOutputFileNameChange = onOutputFileNameChange,
                                )
                                CharacterImageCard(state, texts)
                                CharacterIconCropCard(
                                    state = state,
                                    texts = texts,
                                    busy = busy,
                                    onCropCenterXChange = onCropCenterXChange,
                                    onCropCenterYChange = onCropCenterYChange,
                                    onCropSizeChange = onCropSizeChange,
                                    onGenerateIcon = onGenerateIcon,
                                    onResetCrop = onResetCrop,
                                )
                                CharacterPositionCard(
                                    state = state,
                                    texts = texts,
                                    onXChange = onXChange,
                                    onYChange = onYChange,
                                    onScaleChange = onScaleChange,
                                    onResetPosition = onResetPosition,
                                    onCenterPosition = onCenterPosition,
                                    onFitHeight = onFitHeight,
                                    onFitWidth = onFitWidth,
                                    onSampleDefault = onSampleDefault,
                                )
                                CharacterBuildSaveCard(state, texts, busy, onBuild, onSaveDownloads, onSaveOutput)
                                LocalizedFeedbackMessagesCard(state.warnings, state.errorMessage, state.errorDetails, language = state.language)
                                CharacterAdvancedInfoCard(state, texts, clipboard)
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
}

@Composable
private fun CharacterTopBar(
    state: UiCharacterState,
    texts: CharacterUiText,
    appTexts: AppText,
    busy: Boolean,
    onSwitchToSingle: () -> Unit,
    onSwitchToPack: () -> Unit,
    onLanguageChange: (UiLanguage) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageMenuOpen by rememberSaveable { mutableStateOf(false) }
    var settingsMenuOpen by rememberSaveable { mutableStateOf(false) }
    var languageMenuOpen by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(EtoileShapeTokens.TopBar),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(appTexts.appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(texts.pageTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Row {
                Box {
                    IconButton(onClick = { pageMenuOpen = true }) {
                        SymbolIcon(AppSymbols.SwitchPage, contentDescription = appTexts.switchPage)
                    }
                    DropdownMenu(pageMenuOpen, onDismissRequest = { pageMenuOpen = false }) {
                        DropdownMenuItem(text = { Text(appTexts.pageTitle) }, onClick = { pageMenuOpen = false; onSwitchToSingle() })
                        DropdownMenuItem(text = { Text(packText(state.language).pageTitle) }, onClick = { pageMenuOpen = false; onSwitchToPack() })
                        DropdownMenuItem(text = { Text("${texts.pageTitle} ✓") }, onClick = { pageMenuOpen = false })
                    }
                }
                Box {
                    IconButton(onClick = { settingsMenuOpen = true }) {
                        SymbolIcon(AppSymbols.Settings, contentDescription = appTexts.settings)
                    }
                    DropdownMenu(settingsMenuOpen, onDismissRequest = { settingsMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(appTexts.language) },
                            leadingIcon = { SymbolIcon(AppSymbols.Language, contentDescription = null) },
                            onClick = { settingsMenuOpen = false; languageMenuOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text(appTexts.clearCache) },
                            leadingIcon = { SymbolIcon(AppSymbols.Delete, contentDescription = null) },
                            enabled = !busy && state.pendingOutputFile == null,
                            onClick = { settingsMenuOpen = false; onClearCache() },
                        )
                        DropdownMenuItem(
                            text = { Text(appTexts.about) },
                            leadingIcon = { SymbolIcon(AppSymbols.Info, contentDescription = null) },
                            onClick = { settingsMenuOpen = false },
                        )
                    }
                    DropdownMenu(languageMenuOpen, onDismissRequest = { languageMenuOpen = false }) {
                        DropdownMenuItem(text = { Text(appTexts.chinese) }, onClick = { languageMenuOpen = false; onLanguageChange(UiLanguage.ZhHans) })
                        DropdownMenuItem(text = { Text(appTexts.english) }, onClick = { languageMenuOpen = false; onLanguageChange(UiLanguage.English) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterSectionCard(
    title: String,
    symbol: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    EdgeAwareCard(
        modifier = modifier
            .animateContentSize()
            .tapFeedbackOnly(),
        shape = EtoileShapeTokens.SectionCard,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(symbol, title)
            content()
        }
    }
}

@Composable
private fun CharacterHeroCard(
    state: UiCharacterState,
    texts: CharacterUiText,
    busy: Boolean,
    onSelectPng: () -> Unit,
    onSelectArcpkg: () -> Unit,
) {
    EdgeAwareCard(
        modifier = Modifier
            .tapFeedbackOnly(),
        shape = EtoileShapeTokens.HeroCard,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(texts.heroTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(texts.noInputDetail, style = MaterialTheme.typography.bodyMedium)
            AdaptiveActionRow {
                Button(onClick = onSelectPng, enabled = !busy) { FeedbackButtonIcon(AppSymbols.Image, texts.selectPng) }
                FilledTonalButton(onClick = onSelectArcpkg, enabled = !busy) { FeedbackButtonIcon(AppSymbols.Archive, texts.selectArcpkg) }
            }
            AdaptiveActionRow {
                AssistChip(onClick = {}, label = { Text(characterStatusText(state, texts)) })
                AssistChip(onClick = {}, label = { Text(inputTypeText(state.inputType, texts)) })
            }
            KeyValue(texts.currentInput, state.inputName ?: texts.none)
        }
    }
}

@Composable
private fun CharacterInfoCard(
    state: UiCharacterState,
    texts: CharacterUiText,
    busy: Boolean,
    onPublisherChange: (String) -> Unit,
    onCharacterIdChange: (String) -> Unit,
    onDefaultNameChange: (String) -> Unit,
    onZhCnNameChange: (String) -> Unit,
    onOutputFileNameChange: (String) -> Unit,
) {
    CharacterSectionCard(texts.characterInfo, AppSymbols.Article) {
        OutlinedTextField(
            value = state.outputFileName,
            onValueChange = onOutputFileNameChange,
            enabled = !busy,
            label = { Text(texts.outputFileName) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.publisherId,
            onValueChange = onPublisherChange,
            enabled = !busy,
            label = { Text(texts.publisherId) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.characterId,
            onValueChange = onCharacterIdChange,
            enabled = !busy,
            label = { Text(texts.characterId) },
            modifier = Modifier.fillMaxWidth(),
        )
        KeyValue(texts.identifierPreview, state.identifier)
        KeyValue(texts.directory, state.directory)
        OutlinedTextField(
            value = state.defaultName,
            onValueChange = onDefaultNameChange,
            enabled = !busy,
            label = { Text(texts.defaultName) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.zhCnName,
            onValueChange = onZhCnNameChange,
            enabled = !busy,
            label = { Text(texts.chineseName) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CharacterImageCard(state: UiCharacterState, texts: CharacterUiText) {
    val imagePreview = rememberImagePreview(state.imageFilePath, maxWidthPx = 900, maxHeightPx = 900)
    CharacterSectionCard(texts.imageInfo, AppSymbols.Image) {
        if (imagePreview.image != null) {
            Image(
                bitmap = imagePreview.image,
                contentDescription = texts.image,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightByRatio()
                    .clip(RoundedCornerShape(EtoileShapeTokens.ImagePreview))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            EmptyStateCard(AppSymbols.Image, texts.image, if (imagePreview.fileMissing) textFor(state.language).fileDoesNotExistMessage() else texts.noInputTitle)
        }
        KeyValue(texts.image, state.imageFileName ?: texts.none)
        KeyValue(texts.fileSize, imagePreview.fileSizeBytes?.formatBytes() ?: texts.none)
        KeyValue(texts.imageSize, imagePreview.width?.let { "${it} x ${imagePreview.height}" } ?: texts.none)
        KeyValue(texts.alpha, state.imageHasAlpha?.toString() ?: texts.none)
        if (state.imageHasAlpha == false) {
            AssistChip(onClick = {}, label = { Text(texts.alphaWarning) })
        }
    }
}

@Composable
private fun CharacterIconCropCard(
    state: UiCharacterState,
    texts: CharacterUiText,
    busy: Boolean,
    onCropCenterXChange: (Float) -> Unit,
    onCropCenterYChange: (Float) -> Unit,
    onCropSizeChange: (Float) -> Unit,
    onGenerateIcon: () -> Unit,
    onResetCrop: () -> Unit,
) {
    val sourcePreview = rememberImagePreview(state.imageFilePath, maxWidthPx = 1024, maxHeightPx = 1024)
    val iconPreview = rememberImagePreview(state.iconFilePath, maxWidthPx = 320, maxHeightPx = 320)
    val liveIconPreview = rememberCroppedImagePreview(
        filePath = state.imageFilePath,
        centerX = state.cropCenterX,
        centerY = state.cropCenterY,
        cropSize = state.cropSize,
    )
    CharacterSectionCard(texts.iconCrop, AppSymbols.Tune) {
        if (sourcePreview.image == null) {
            CharacterIconPreview(iconPreview.image, texts)
            Text(
                text = if (state.iconFilePath != null) texts.iconWithoutSourceImage else texts.importImageForIconCrop,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@CharacterSectionCard
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val expanded = maxWidth >= 720.dp
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
                    CharacterCropPreviewCanvas(
                        imagePreview = sourcePreview,
                        centerX = state.cropCenterX,
                        centerY = state.cropCenterY,
                        cropSize = state.cropSize,
                        texts = texts,
                        modifier = Modifier.weight(1f),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.widthIn(max = 380.dp),
                    ) {
                        CharacterIconPreview(liveIconPreview ?: iconPreview.image, texts)
                        CharacterCropControls(
                            state = state,
                            texts = texts,
                            onCropCenterXChange = onCropCenterXChange,
                            onCropCenterYChange = onCropCenterYChange,
                            onCropSizeChange = onCropSizeChange,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    CharacterCropPreviewCanvas(
                        imagePreview = sourcePreview,
                        centerX = state.cropCenterX,
                        centerY = state.cropCenterY,
                        cropSize = state.cropSize,
                        texts = texts,
                    )
                    CharacterIconPreview(liveIconPreview ?: iconPreview.image, texts)
                    CharacterCropControls(
                        state = state,
                        texts = texts,
                        onCropCenterXChange = onCropCenterXChange,
                        onCropCenterYChange = onCropCenterYChange,
                        onCropSizeChange = onCropSizeChange,
                    )
                }
            }
        }
        AdaptiveActionRow {
            OutlinedButton(onClick = onGenerateIcon, enabled = state.imageFilePath != null && !busy) {
                FeedbackButtonIcon(AppSymbols.Image, texts.generateIcon)
            }
            TextButton(onClick = onResetCrop, enabled = !busy) { Text(texts.resetCrop) }
        }
    }
}

@Composable
private fun CharacterPositionCard(
    state: UiCharacterState,
    texts: CharacterUiText,
    onXChange: (Float) -> Unit,
    onYChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onResetPosition: () -> Unit,
    onCenterPosition: () -> Unit,
    onFitHeight: () -> Unit,
    onFitWidth: () -> Unit,
    onSampleDefault: () -> Unit,
) {
    val imagePreview = rememberImagePreview(state.imageFilePath, maxWidthPx = 720, maxHeightPx = 720)
    CharacterSectionCard(texts.positionPreview, AppSymbols.Palette) {
        Text(texts.approximateResultPreview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${texts.characterMayExceedCanvas}. ${texts.adjustIfOutsidePreview}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CharacterResultPreviewCanvas(state, imagePreview, texts)
        NumericSliderField(texts.x, state.x, -1000f..1000f, onXChange, decimals = 0)
        NumericSliderField(texts.y, state.y, -1000f..1000f, onYChange, decimals = 0)
        NumericSliderField(texts.scale, state.scale, 0.1f..3f, onScaleChange, decimals = 2)
        AdaptiveActionRow {
            TextButton(onClick = onResetPosition) { Text(texts.reset) }
            TextButton(onClick = onCenterPosition) { Text(texts.center) }
            TextButton(onClick = onFitHeight) { Text(texts.fitHeight) }
            TextButton(onClick = onFitWidth) { Text(texts.fitWidth) }
            OutlinedButton(onClick = onSampleDefault) { Text(texts.sampleDefault) }
        }
    }
}

@Composable
private fun CharacterBuildSaveCard(
    state: UiCharacterState,
    texts: CharacterUiText,
    busy: Boolean,
    onBuild: () -> Unit,
    onSaveDownloads: () -> Unit,
    onSaveOutput: () -> Unit,
) {
    CharacterSectionCard(texts.buildAndSave, AppSymbols.Convert) {
        Button(onClick = onBuild, enabled = state.canBuild && !busy) {
            FeedbackButtonIcon(AppSymbols.Convert, texts.startBuild)
        }
        state.pendingOutputFile?.let {
            KeyValue(texts.pendingFile, it.name)
            KeyValue(texts.fileSize, state.pendingOutputFileSize?.formatBytes() ?: texts.none)
        }
        state.validationPassed?.let {
            AssistChip(onClick = {}, label = { Text(if (it) texts.validatorPassed else texts.validatorFailed) })
        }
        if (state.validationErrors.isNotEmpty()) {
            state.validationErrors.take(5).forEach { Text(it, color = MaterialTheme.colorScheme.error, softWrap = true) }
        }
        KeyValue(texts.finalLocation, state.savedLocation ?: texts.notSaved)
        AdaptiveActionRow {
            if (state.canUseMediaStoreDownloads) {
                OutlinedButton(onClick = onSaveDownloads, enabled = state.canSaveDownloads && !busy) {
                    FeedbackButtonIcon(AppSymbols.Download, texts.saveDownloads)
                }
            }
            OutlinedButton(onClick = onSaveOutput, enabled = state.canSave && !busy) {
                FeedbackButtonIcon(AppSymbols.SaveAs, texts.saveAs)
            }
        }
    }
}

@Composable
private fun CharacterAdvancedInfoCard(
    state: UiCharacterState,
    texts: CharacterUiText,
    clipboard: ClipboardManager,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    CharacterSectionCard(texts.advancedInfo, AppSymbols.Terminal) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) texts.hideDetails else texts.showDetails)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                AdvancedInfoSection(texts.currentInput) {
                    KeyValue(texts.inputType, inputTypeText(state.inputType, texts))
                    KeyValue(texts.currentInput, state.inputName ?: texts.none)
                    KeyValue("workspace", state.workspacePath ?: texts.none)
                    KeyValue("source identifier", state.sourceIdentifier ?: texts.none)
                    KeyValue("source directory", state.sourceDirectory ?: texts.none)
                }
                AdvancedInfoSection(texts.indexPreview) {
                    Text(characterIndexPreview(state), softWrap = true)
                }
                AdvancedInfoSection(texts.characterYamlPreview) {
                    Text(characterYamlPreview(state), softWrap = true)
                }
                AdvancedInfoSection(texts.logs) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(characterClipboardText(state))) }) {
                        Text(texts.logs)
                    }
                    state.logs.takeLast(20).forEach { Text(it, style = MaterialTheme.typography.bodySmall, softWrap = true) }
                }
                if (state.validationSummary.isNotEmpty() || state.validationErrors.isNotEmpty()) {
                    AdvancedInfoSection("Validator") {
                        state.validationSummary.forEach { Text(it, softWrap = true) }
                        state.validationErrors.forEach { Text(it, color = MaterialTheme.colorScheme.error, softWrap = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCropControls(
    state: UiCharacterState,
    texts: CharacterUiText,
    onCropCenterXChange: (Float) -> Unit,
    onCropCenterYChange: (Float) -> Unit,
    onCropSizeChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        NumericSliderField(texts.cropCenterX, state.cropCenterX, 0f..1f, onCropCenterXChange, decimals = 2)
        NumericSliderField(texts.cropCenterY, state.cropCenterY, 0f..1f, onCropCenterYChange, decimals = 2)
        NumericSliderField(texts.cropSize, state.cropSize, 0.05f..1f, onCropSizeChange, decimals = 2)
    }
}

@Composable
private fun CharacterIconPreview(image: androidx.compose.ui.graphics.ImageBitmap?, texts: CharacterUiText) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(texts.iconPreview, fontWeight = FontWeight.Medium)
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = texts.iconPreview,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(EtoileShapeTokens.ImagePreview))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            AssistChip(onClick = {}, label = { Text(texts.iconPreview) })
        }
    }
}

@Composable
private fun CharacterCropPreviewCanvas(
    imagePreview: ImagePreviewData,
    centerX: Float,
    centerY: Float,
    cropSize: Float,
    texts: CharacterUiText,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Text(texts.cropPreview, fontWeight = FontWeight.Medium)
        val aspect = imagePreview.width?.let { width ->
            val height = imagePreview.height?.coerceAtLeast(1) ?: 1
            (width.toFloat() / height.toFloat()).coerceIn(0.45f, 1.6f)
        } ?: 1f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 420.dp)
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(EtoileShapeTokens.ImagePreview))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            imagePreview.image?.let {
                Image(
                    bitmap = it,
                    contentDescription = texts.cropPreview,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val srcWidth = imagePreview.width ?: return@Canvas
                val srcHeight = imagePreview.height ?: return@Canvas
                val display = fitRect(srcWidth, srcHeight, size.width, size.height)
                val minSide = minOf(srcWidth, srcHeight).toFloat()
                val cropPx = minSide * cropSize.coerceIn(0.05f, 1f)
                val left = ((srcWidth * centerX.coerceIn(0f, 1f)) - (cropPx / 2f)).coerceIn(0f, srcWidth - cropPx)
                val top = ((srcHeight * centerY.coerceIn(0f, 1f)) - (cropPx / 2f)).coerceIn(0f, srcHeight - cropPx)
                val cropLeft = display.left + display.width * (left / srcWidth)
                val cropTop = display.top + display.height * (top / srcHeight)
                val cropWidth = display.width * (cropPx / srcWidth)
                val cropHeight = display.height * (cropPx / srcHeight)
                val overlay = Color.Black.copy(alpha = 0.45f)
                drawRect(overlay, topLeft = Offset(display.left, display.top), size = Size(display.width, cropTop - display.top))
                drawRect(overlay, topLeft = Offset(display.left, cropTop + cropHeight), size = Size(display.width, display.top + display.height - cropTop - cropHeight))
                drawRect(overlay, topLeft = Offset(display.left, cropTop), size = Size(cropLeft - display.left, cropHeight))
                drawRect(overlay, topLeft = Offset(cropLeft + cropWidth, cropTop), size = Size(display.left + display.width - cropLeft - cropWidth, cropHeight))
                drawRect(
                    color = Color.White.copy(alpha = 0.95f),
                    topLeft = Offset(cropLeft, cropTop),
                    size = Size(cropWidth, cropHeight),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                )
            }
        }
    }
}

@Composable
private fun CharacterResultPreviewCanvas(
    state: UiCharacterState,
    imagePreview: ImagePreviewData,
    texts: CharacterUiText,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val layoutSnapshot = ArcCreateResultLayoutSnapshots.ResultScreen
        val logicalPlacement = if (imagePreview.image != null && imagePreview.width != null && imagePreview.height != null) {
            CharacterPreviewCoordinateMapper.mapLogical(
                imageWidth = imagePreview.width,
                imageHeight = imagePreview.height,
                x = state.x,
                y = state.y,
                scale = state.scale,
                layoutSnapshot = layoutSnapshot,
            )
        } else {
            null
        }
        var showDebug by rememberSaveable { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(EtoileShapeTokens.ImagePreview))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val backgroundArrow = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.ac_result_background_arrow)
                val clearGlow = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.ac_result_clear_glow)
                val jacketBackground = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.ac_result_jacket_background)
                val scoreFrame = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.ac_result_score_frame)
                val judgementTable = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.ac_result_judgement_table)
                val judgementHighlight = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.ac_result_judgement_table_highlight)
                val playRetryBackground = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.ac_result_play_retry_background)
                val playRetryFrame = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.ac_result_play_retry_frame)
                val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                val dimPanelColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.40f)
                val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                val resultBaseColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.84f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val logicalWidth = layoutSnapshot.logicalWidth
                    val logicalHeight = layoutSnapshot.logicalHeight
                    val previewScale = minOf(size.width / logicalWidth, size.height / logicalHeight)
                    val originX = (size.width - (logicalWidth * previewScale)) / 2f
                    val originY = (size.height - (logicalHeight * previewScale)) / 2f
                    fun logicalRect(rect: PreviewBounds) = Pair(
                        Offset(originX + rect.left * previewScale, originY + rect.top * previewScale),
                        Size(rect.width * previewScale, rect.height * previewScale),
                    )
                    fun drawAsset(
                        image: androidx.compose.ui.graphics.ImageBitmap,
                        rect: PreviewBounds,
                    ) {
                        val (topLeft, rectSize) = logicalRect(rect)
                        drawImage(
                            image = image,
                            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                            dstSize = IntSize(rectSize.width.roundToInt().coerceAtLeast(1), rectSize.height.roundToInt().coerceAtLeast(1)),
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                    fun roundedBlock(
                        rect: PreviewBounds,
                        color: Color,
                        radius: Float = 22f,
                    ) {
                        val (topLeft, rectSize) = logicalRect(rect)
                        drawRoundRect(
                            color = color,
                            topLeft = topLeft,
                            size = rectSize,
                            cornerRadius = CornerRadius(radius * previewScale, radius * previewScale),
                        )
                    }
                    drawRect(backgroundColor)
                    roundedBlock(PreviewBounds(0f, 0f, logicalWidth, logicalHeight), resultBaseColor, radius = 0f)
                    drawAsset(backgroundArrow, layoutSnapshot.backgroundArrowRect.rect)
                    drawAsset(clearGlow, layoutSnapshot.clearGlowRect.rect)
                    roundedBlock(PreviewBounds(0f, 0f, 520f, logicalHeight), accentColor, radius = 0f)
                    val drawPlacement = if (imagePreview.image != null && imagePreview.width != null && imagePreview.height != null) {
                        CharacterPreviewCoordinateMapper.map(
                            canvasWidth = size.width,
                            canvasHeight = size.height,
                            imageWidth = imagePreview.width,
                            imageHeight = imagePreview.height,
                            x = state.x,
                            y = state.y,
                            scale = state.scale,
                            layoutSnapshot = layoutSnapshot,
                        )
                    } else {
                        null
                    }
                    val bitmap = imagePreview.image
                    if (bitmap != null && drawPlacement != null && drawPlacement.intersectsCanvas) {
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(drawPlacement.offsetX.roundToInt(), drawPlacement.offsetY.roundToInt()),
                            dstSize = IntSize(
                                drawPlacement.width.roundToInt().coerceAtLeast(1),
                                drawPlacement.height.roundToInt().coerceAtLeast(1),
                            ),
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                    // Result UI overlays use ArcCreate textures where they exist; plain color blocks
                    // represent Unity image/color containers rather than result text.
                    drawAsset(jacketBackground, layoutSnapshot.jacketRect.rect)
                    val jacketInner = layoutSnapshot.jacketRect.rect
                    roundedBlock(
                        PreviewBounds(
                            jacketInner.left + jacketInner.width * 0.25f,
                            jacketInner.top + jacketInner.height * 0.25f,
                            jacketInner.right - jacketInner.width * 0.25f,
                            jacketInner.bottom - jacketInner.height * 0.25f,
                        ),
                        dimPanelColor,
                        radius = 10f,
                    )
                    drawAsset(scoreFrame, layoutSnapshot.bottomScoreRect.rect)
                    drawAsset(judgementTable, layoutSnapshot.resultPanelRect.rect)
                    drawAsset(judgementHighlight, layoutSnapshot.judgementHighlightRect.rect)
                    drawAsset(playRetryBackground, layoutSnapshot.playRetryRect.rect)
                    drawAsset(playRetryFrame, layoutSnapshot.playRetryHighlightRect.rect)
                    drawPlacement?.let {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.82f),
                            radius = 4f,
                            center = Offset(it.pivotX, it.pivotY),
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.72f),
                            radius = 2f,
                            center = Offset(it.pivotX, it.pivotY),
                        )
                    }
                }
                if (logicalPlacement != null && !logicalPlacement.intersectsCanvas) {
                    AssistChip(
                        onClick = {},
                        label = { Text(texts.characterOutsideCanvas) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                    )
                }
            }
            logicalPlacement?.let {
                TextButton(onClick = { showDebug = !showDebug }) {
                    Text(if (showDebug) texts.hideDetails else texts.previewDebugInfo)
                }
                AnimatedVisibility(
                    visible = showDebug,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(EtoileShapeTokens.InnerCard),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                            KeyValue(texts.sourceSize, "${imagePreview.width} x ${imagePreview.height}")
                            KeyValue(texts.drawSize, "${it.width.roundToInt()} x ${it.height.roundToInt()}")
                            KeyValue(texts.drawOffset, "${it.offsetX.roundToInt()}, ${it.offsetY.roundToInt()}")
                            KeyValue(texts.visibleBounds, "${it.visibleBounds.left.roundToInt()}, ${it.visibleBounds.top.roundToInt()}, ${it.visibleBounds.right.roundToInt()}, ${it.visibleBounds.bottom.roundToInt()}")
                            KeyValue(texts.scalePivot, "${it.pivotX.roundToInt()}, ${it.pivotY.roundToInt()}")
                            KeyValue(texts.intersectsCanvas, it.intersectsCanvas.toString())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumericSliderField(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    decimals: Int,
) {
    var text by remember(value, decimals) { mutableStateOf(formatNumber(value, decimals)) }
    var focused by remember { mutableStateOf(false) }
    val commit: () -> Unit = {
        text.toFloatOrNull()?.let {
            val clamped = it.coerceIn(range.start, range.endInclusive)
            onValueChange(clamped)
            text = formatNumber(clamped, decimals)
        } ?: run {
            text = formatNumber(value, decimals)
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 420.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    NumericValueField(text, onTextChange = { text = it }, onCommit = commit)
                }
                Slider(
                    value = value.coerceIn(range.start, range.endInclusive),
                    onValueChange = {
                        onValueChange(it)
                        if (!focused) text = formatNumber(it, decimals)
                    },
                    valueRange = range,
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(96.dp))
                Slider(
                    value = value.coerceIn(range.start, range.endInclusive),
                    onValueChange = {
                        onValueChange(it)
                        if (!focused) text = formatNumber(it, decimals)
                    },
                    valueRange = range,
                    modifier = Modifier.weight(1f).widthIn(max = 360.dp),
                )
                NumericValueField(
                    value = text,
                    onTextChange = { text = it },
                    onCommit = commit,
                    modifier = Modifier.width(104.dp),
                    onFocusChange = { focused = it },
                )
            }
        }
    }
}

@Composable
private fun NumericValueField(
    value: String,
    onTextChange: (String) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier.width(104.dp),
    onFocusChange: (Boolean) -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onTextChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = modifier.onFocusChanged {
            onFocusChange(it.isFocused)
            if (!it.isFocused) onCommit()
        },
    )
}

private data class DisplayRect(val left: Float, val top: Float, val width: Float, val height: Float)

private fun fitRect(imageWidth: Int, imageHeight: Int, boxWidth: Float, boxHeight: Float): DisplayRect {
    val imageAspect = imageWidth.toFloat() / imageHeight.coerceAtLeast(1).toFloat()
    val boxAspect = boxWidth / boxHeight.coerceAtLeast(1f)
    return if (boxAspect > imageAspect) {
        val height = boxHeight
        val width = height * imageAspect
        DisplayRect((boxWidth - width) / 2f, 0f, width, height)
    } else {
        val width = boxWidth
        val height = width / imageAspect
        DisplayRect(0f, (boxHeight - height) / 2f, width, height)
    }
}

private fun formatNumber(value: Float, decimals: Int): String =
    if (decimals == 0) value.roundToInt().toString() else "%.${decimals}f".format(value)

@Composable
private fun Modifier.heightByRatio(): Modifier = this.aspectRatio(1f)

private fun characterPhase(state: UiCharacterState): UiOperationPhase =
    when {
        state.errorMessage != null -> UiOperationPhase.Failed
        state.isCopying -> UiOperationPhase.Copying
        state.isScanning -> UiOperationPhase.Scanning
        state.isBuilding -> UiOperationPhase.Converting
        state.isSaving -> UiOperationPhase.Saving
        state.saveStatus == UiSaveStatus.Saved -> UiOperationPhase.Saved
        state.pendingOutputFile != null -> UiOperationPhase.PendingSave
        state.canBuild -> UiOperationPhase.Ready
        else -> UiOperationPhase.Idle
    }

private fun characterPhaseTitle(state: UiCharacterState, texts: CharacterUiText): String =
    when (characterPhase(state)) {
        UiOperationPhase.Ready -> texts.ready
        UiOperationPhase.Failed -> texts.failed
        UiOperationPhase.PendingSave -> texts.pendingFile
        UiOperationPhase.Saved -> texts.saved
        else -> texts.waiting
    }

private fun characterPhaseDetail(state: UiCharacterState, texts: CharacterUiText): String =
    when {
        state.errorMessage != null -> state.errorMessage
        state.pendingOutputFile != null -> state.pendingOutputFile.name
        state.canBuild -> state.identifier
        state.inputName == null -> texts.noInputDetail
        else -> texts.noInputTitle
    }

private fun characterStatusText(state: UiCharacterState, texts: CharacterUiText): String =
    when {
        state.errorMessage != null -> texts.failed
        state.canBuild -> texts.ready
        else -> texts.waiting
    }

private fun inputTypeText(type: UiCharacterInputType, texts: CharacterUiText): String =
    when (type) {
        UiCharacterInputType.None -> texts.none
        UiCharacterInputType.Png -> "PNG"
        UiCharacterInputType.Arcpkg -> "character arcpkg"
    }

private fun characterIndexPreview(state: UiCharacterState): String =
    """
    - directory: ${state.directory}
      identifier: ${state.identifier}
      settingsFile: character.yml
      version: 0
      type: character
    """.trimIndent()

private fun characterYamlPreview(state: UiCharacterState): String = buildString {
    appendLine("name:")
    appendLine("  default: \"${state.defaultName}\"")
    if (state.zhCnName.isNotBlank()) appendLine("  zh-cn: \"${state.zhCnName}\"")
    appendLine("imagePath: \"${state.characterId}.png\"")
    appendLine("iconPath: \"${state.characterId}_icon.png\"")
    appendLine("x: ${state.x}")
    appendLine("y: ${state.y}")
    appendLine("scale: ${state.scale}")
}

private fun characterClipboardText(state: UiCharacterState): String =
    buildString {
        appendLine("inputType=${state.inputType}")
        appendLine("inputName=${state.inputName}")
        appendLine("workspace=${state.workspacePath}")
        appendLine("identifier=${state.identifier}")
        appendLine("directory=${state.directory}")
        appendLine("image=${state.imageFilePath}")
        appendLine("icon=${state.iconFilePath}")
        appendLine("x=${state.x}, y=${state.y}, scale=${state.scale}")
        appendLine("warnings=${state.warnings.joinToString(" | ")}")
        appendLine("errors=${state.validationErrors.joinToString(" | ")}")
        appendLine("logs=${state.logs.joinToString(" | ")}")
    }

private fun AppText.fileDoesNotExistMessage(): String =
    imagePreviewFailed
