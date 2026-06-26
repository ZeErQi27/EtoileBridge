package com.zeerqi27.etoilebridge

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.zeerqi27.etoilebridge.model.ManualResourceKind
import com.zeerqi27.etoilebridge.model.UiAppPage
import com.zeerqi27.etoilebridge.ui.CharacterHomeScreen
import com.zeerqi27.etoilebridge.ui.HomeScreen
import com.zeerqi27.etoilebridge.ui.PackHomeScreen
import com.zeerqi27.etoilebridge.ui.pageSwitchDirection
import com.zeerqi27.etoilebridge.viewmodel.CharacterConverterViewModel
import com.zeerqi27.etoilebridge.viewmodel.ConverterViewModel
import com.zeerqi27.etoilebridge.viewmodel.PackConverterViewModel

private enum class TreeAction { SingleInput, OfficialPackFolder, ArcpkgFolder, ExistingPackAddFolder }
private enum class ZipAction { SingleZip, OfficialPackZip, ArcpkgZip, ExistingPack }
private enum class ArcpkgFilesAction { BundleInputs, ExistingPackAdds }
private enum class SaveAction { Single, Pack, Character }

class MainActivity : ComponentActivity() {
    private val viewModel: ConverterViewModel by viewModels()
    private val packViewModel: PackConverterViewModel by viewModels()
    private val characterViewModel: CharacterConverterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }

        var pendingTreeAction = TreeAction.SingleInput
        var pendingZipAction = ZipAction.SingleZip
        var pendingArcpkgFilesAction = ArcpkgFilesAction.BundleInputs
        var pendingSaveAction = SaveAction.Single

        val openTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    when (pendingTreeAction) {
                        TreeAction.SingleInput -> viewModel.onInputTreeSelected(uri)
                        TreeAction.OfficialPackFolder -> packViewModel.onOfficialTreeSelected(uri)
                        TreeAction.ArcpkgFolder -> packViewModel.onArcpkgTreeSelected(uri)
                        TreeAction.ExistingPackAddFolder -> packViewModel.onExistingPackAddFolderSelected(uri)
                    }
                }.onFailure {
                    if (pendingTreeAction == TreeAction.SingleInput) {
                        viewModel.reportExternalError("Unable to read selected folder.", it)
                    } else {
                        packViewModel.reportExternalError("Unable to read selected folder.", it)
                    }
                }
            }
        }
        val createDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri ->
            if (uri != null) {
                runCatching {
                    when (pendingSaveAction) {
                        SaveAction.Single -> viewModel.saveOutputTo(uri)
                        SaveAction.Pack -> packViewModel.saveOutputTo(uri)
                        SaveAction.Character -> characterViewModel.saveOutputTo(uri)
                    }
                }.onFailure {
                    when (pendingSaveAction) {
                        SaveAction.Single -> viewModel.reportExternalError("Unable to save selected output.", it)
                        SaveAction.Pack -> packViewModel.reportExternalError("Unable to save selected output.", it)
                        SaveAction.Character -> characterViewModel.reportExternalError("Unable to save selected output.", it)
                    }
                }
            } else {
                when (pendingSaveAction) {
                    SaveAction.Single -> viewModel.onSaveAsCanceled()
                    SaveAction.Pack -> packViewModel.onSaveAsCanceled()
                    SaveAction.Character -> characterViewModel.onSaveAsCanceled()
                }
            }
        }
        val openZipLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    when (pendingZipAction) {
                        ZipAction.SingleZip -> viewModel.onZipSelected(uri)
                        ZipAction.OfficialPackZip -> packViewModel.onOfficialZipSelected(uri)
                        ZipAction.ArcpkgZip -> packViewModel.onArcpkgZipSelected(uri)
                        ZipAction.ExistingPack -> packViewModel.onExistingPackSelected(uri)
                    }
                }.onFailure {
                    if (pendingZipAction == ZipAction.SingleZip) {
                        viewModel.reportExternalError("Unable to read selected ZIP.", it)
                    } else {
                        packViewModel.reportExternalError("Unable to read selected ZIP.", it)
                    }
                }
            }
        }
        val openMultipleArcpkgLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                runCatching {
                    when (pendingArcpkgFilesAction) {
                        ArcpkgFilesAction.BundleInputs -> packViewModel.onArcpkgFilesSelected(uris)
                        ArcpkgFilesAction.ExistingPackAdds -> packViewModel.onExistingPackAddFilesSelected(uris)
                    }
                }.onFailure {
                    packViewModel.reportExternalError("Unable to read selected arcpkg files.", it)
                }
            }
        }
        val openPackImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    packViewModel.onPackImageSelected(uri)
                }.onFailure {
                    packViewModel.reportExternalError("Unable to read selected pack image.", it)
                }
            }
        }
        val openCharacterPngLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    characterViewModel.onPngSelected(uri)
                }.onFailure {
                    characterViewModel.reportExternalError("Unable to read selected character PNG.", it)
                }
            }
        }
        val openCharacterArcpkgLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    characterViewModel.onCharacterArcpkgSelected(uri)
                }.onFailure {
                    characterViewModel.reportExternalError("Unable to read selected character arcpkg.", it)
                }
            }
        }
        var pendingManualKind: ManualResourceKind? = null
        val openManualResourceLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val kind = pendingManualKind
            if (uri != null && kind != null) {
                runCatching {
                    viewModel.onManualResourceSelected(kind, uri)
                }.onFailure {
                    viewModel.reportExternalError("Unable to read selected resource.", it)
                }
            }
            pendingManualKind = null
        }

        setContent {
            val state by viewModel.state.collectAsState()
            val packState by packViewModel.state.collectAsState()
            val characterState by characterViewModel.state.collectAsState()
            var page by rememberSaveable { mutableStateOf(UiAppPage.SingleSong) }
            val dark = isSystemInDarkTheme()
            val colorScheme = if (android.os.Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else {
                if (dark) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val direction = pageSwitchDirection(initialState, targetState).takeIf { it != 0 } ?: 1
                        (
                            slideInHorizontally(animationSpec = tween(durationMillis = 260)) { fullWidth -> direction * fullWidth } +
                                fadeIn(animationSpec = tween(durationMillis = 220))
                            ) togetherWith (
                            slideOutHorizontally(animationSpec = tween(durationMillis = 260)) { fullWidth -> -direction * fullWidth } +
                                fadeOut(animationSpec = tween(durationMillis = 220))
                            )
                    },
                    label = "page-switch",
                ) { currentPage ->
                    when (currentPage) {
                        UiAppPage.SingleSong -> HomeScreen(
                            state = state,
                            onSelectInput = {
                                pendingTreeAction = TreeAction.SingleInput
                                openTreeLauncher.launch(null)
                            },
                            onSelectZip = {
                                pendingZipAction = ZipAction.SingleZip
                                openZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                            },
                            onScan = viewModel::scan,
                            onConvert = viewModel::convert,
                            onSaveDownloads = viewModel::saveOutputToDownloads,
                            onSaveOutput = {
                                pendingSaveAction = SaveAction.Single
                                createDocumentLauncher.launch(viewModel.suggestedOutputFileName())
                            },
                            onSelectManualResource = { kind ->
                                pendingManualKind = kind
                                openManualResourceLauncher.launch(arrayOf("*/*"))
                            },
                            onClearManualResource = viewModel::clearManualResource,
                            onClearCache = viewModel::clearCache,
                            onOptionsChange = viewModel::updateOptions,
                            onAppearanceChange = viewModel::updateAppearanceOptions,
                            onLanguageChange = {
                                viewModel.updateLanguage(it)
                                packViewModel.updateLanguage(it)
                                characterViewModel.updateLanguage(it)
                            },
                            onMetadataSave = viewModel::saveMetadataDraft,
                            onAffMappingsSave = viewModel::saveAffMappings,
                            onSwitchToPack = { page = UiAppPage.PackBundle },
                            onSwitchToCharacter = { page = UiAppPage.Character },
                        )
                        UiAppPage.PackBundle -> PackHomeScreen(
                            state = packState,
                            onSwitchToSingle = { page = UiAppPage.SingleSong },
                            onSwitchToCharacter = { page = UiAppPage.Character },
                            onModeChange = packViewModel::updateMode,
                            onSelectOfficialZip = {
                                pendingZipAction = ZipAction.OfficialPackZip
                                openZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                            },
                            onSelectOfficialFolder = {
                                pendingTreeAction = TreeAction.OfficialPackFolder
                                openTreeLauncher.launch(null)
                            },
                            onSelectArcpkgFiles = {
                                pendingArcpkgFilesAction = ArcpkgFilesAction.BundleInputs
                                openMultipleArcpkgLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                            },
                            onSelectArcpkgFolder = {
                                pendingTreeAction = TreeAction.ArcpkgFolder
                                openTreeLauncher.launch(null)
                            },
                            onSelectArcpkgZip = {
                                pendingZipAction = ZipAction.ArcpkgZip
                                openZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                            },
                            onSelectExistingPack = {
                                pendingZipAction = ZipAction.ExistingPack
                                openZipLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
                            },
                            onSelectExistingAddFiles = {
                                pendingArcpkgFilesAction = ArcpkgFilesAction.ExistingPackAdds
                                openMultipleArcpkgLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                            },
                            onSelectExistingAddFolder = {
                                pendingTreeAction = TreeAction.ExistingPackAddFolder
                                openTreeLauncher.launch(null)
                            },
                            onScan = packViewModel::scan,
                            onPack = packViewModel::pack,
                            onSaveDownloads = packViewModel::saveOutputToDownloads,
                            onSaveOutput = {
                                pendingSaveAction = SaveAction.Pack
                                createDocumentLauncher.launch(packViewModel.suggestedOutputFileName())
                            },
                            onPublisherChange = packViewModel::updatePublisherId,
                            onPackNameChange = packViewModel::updatePackName,
                            onPackIdChange = packViewModel::updatePackId,
                            onSelectPackImage = {
                                openPackImageLauncher.launch(arrayOf("image/png", "image/jpeg", "image/*", "*/*"))
                            },
                            onClearPackImage = packViewModel::clearPackImageOverride,
                            onOutputFileNameChange = packViewModel::updateOutputFileName,
                            onIncludeOnlyConvertibleChange = packViewModel::updateIncludeOnlyConvertible,
                            onOptionsChange = packViewModel::updateOptions,
                            onEntryChange = packViewModel::updateEntry,
                            onLanguageChange = {
                                viewModel.updateLanguage(it)
                                packViewModel.updateLanguage(it)
                                characterViewModel.updateLanguage(it)
                            },
                            onClearCache = packViewModel::clearCache,
                        )
                        UiAppPage.Character -> CharacterHomeScreen(
                            state = characterState,
                            onSwitchToSingle = { page = UiAppPage.SingleSong },
                            onSwitchToPack = { page = UiAppPage.PackBundle },
                            onSelectPng = {
                                openCharacterPngLauncher.launch(arrayOf("image/png", "image/*", "*/*"))
                            },
                            onSelectArcpkg = {
                                openCharacterArcpkgLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                            },
                            onBuild = characterViewModel::buildPackage,
                            onSaveDownloads = characterViewModel::saveOutputToDownloads,
                            onSaveOutput = {
                                pendingSaveAction = SaveAction.Character
                                createDocumentLauncher.launch(characterViewModel.suggestedOutputFileName())
                            },
                            onPublisherChange = characterViewModel::updatePublisherId,
                            onCharacterIdChange = characterViewModel::updateCharacterId,
                            onDefaultNameChange = characterViewModel::updateDefaultName,
                            onZhCnNameChange = characterViewModel::updateZhCnName,
                            onOutputFileNameChange = characterViewModel::updateOutputFileName,
                            onCropCenterXChange = characterViewModel::updateCropCenterX,
                            onCropCenterYChange = characterViewModel::updateCropCenterY,
                            onCropSizeChange = characterViewModel::updateCropSize,
                            onGenerateIcon = characterViewModel::regenerateIcon,
                            onResetCrop = characterViewModel::resetCrop,
                            onXChange = characterViewModel::updateX,
                            onYChange = characterViewModel::updateY,
                            onScaleChange = characterViewModel::updateScale,
                            onResetPosition = characterViewModel::resetPosition,
                            onCenterPosition = characterViewModel::centerPosition,
                            onFitHeight = characterViewModel::fitHeightPosition,
                            onFitWidth = characterViewModel::fitWidthPosition,
                            onSampleDefault = characterViewModel::sampleDefaultPosition,
                            onLanguageChange = {
                                viewModel.updateLanguage(it)
                                packViewModel.updateLanguage(it)
                                characterViewModel.updateLanguage(it)
                            },
                            onClearCache = characterViewModel::clearCache,
                        )
                    }
                }
            }
        }
    }
}
