package com.zeerqi27.etoilebridge.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeerqi27.etoilebridge.core.ArcpkgBundleMerger
import com.zeerqi27.etoilebridge.core.BundleConvertResult
import com.zeerqi27.etoilebridge.core.BundleChartOverride
import com.zeerqi27.etoilebridge.core.BundleEntryOverride
import com.zeerqi27.etoilebridge.core.BundleInput
import com.zeerqi27.etoilebridge.core.BundleMetadataStatus
import com.zeerqi27.etoilebridge.core.BundleOptions
import com.zeerqi27.etoilebridge.core.BundleOutputValidator
import com.zeerqi27.etoilebridge.core.ConvertOptions
import com.zeerqi27.etoilebridge.core.PackBundleConverter
import com.zeerqi27.etoilebridge.core.PackBundleScanner
import com.zeerqi27.etoilebridge.file.AndroidFileBridge
import com.zeerqi27.etoilebridge.file.SaveResult
import com.zeerqi27.etoilebridge.file.SafTreeCopier
import com.zeerqi27.etoilebridge.file.ZipArchiveExtractor
import com.zeerqi27.etoilebridge.model.UiArcpkgSourceReport
import com.zeerqi27.etoilebridge.model.UiConvertOptions
import com.zeerqi27.etoilebridge.model.UiLanguage
import com.zeerqi27.etoilebridge.model.UiPackChartEntry
import com.zeerqi27.etoilebridge.model.UiPackConvertState
import com.zeerqi27.etoilebridge.model.UiPackEntry
import com.zeerqi27.etoilebridge.model.UiPackMode
import com.zeerqi27.etoilebridge.model.UiSaveStatus
import com.zeerqi27.etoilebridge.model.UiScanStatus
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PackConverterViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("etoilebridge_ui", Application.MODE_PRIVATE)
    private val _state = MutableStateFlow(baseState(loadLanguage()))
    val state: StateFlow<UiPackConvertState> = _state.asStateFlow()

    private var runDir: File? = null
    private var projectRoot: File? = null
    private var pendingOutputFile: File? = null
    private var packImageOverride: File? = null
    private var packImageCandidate: File? = null
    private var existingPackFile: File? = null
    private var existingPackAddRoot: File? = null

    private val crashGuard = CoroutineExceptionHandler { _, throwable ->
        showError("操作失败", throwable)
    }

    fun updateLanguage(language: UiLanguage) {
        prefs.edit().putString(PREF_LANGUAGE, language.name).apply()
        _state.update { it.copy(language = language) }
    }

    fun updateMode(mode: UiPackMode) {
        _state.update { it.copy(mode = mode, errorMessage = null, errorDetails = null) }
    }

    fun updatePublisherId(value: String) {
        _state.update {
            val publisher = value.ifBlank { "etoilebridge" }.safeId("etoilebridge")
            it.copy(publisherId = publisher).withDefaultOutputNameIfNeeded()
        }
    }

    fun updatePackName(value: String) {
        _state.update { it.copy(packName = value) }
    }

    fun updatePackId(value: String) {
        _state.update {
            it.copy(packId = value.safeId("EtoileBridgePack")).withDefaultOutputNameIfNeeded()
        }
    }

    fun updateOutputFileName(value: String) {
        _state.update {
            it.copy(
                outputFileName = value.ifBlank { defaultOutputFileName(it.publisherId, it.packId) },
                outputFileNameManual = true,
            )
        }
    }

    fun updateIncludeOnlyConvertible(value: Boolean) {
        _state.update { it.copy(includeOnlyConvertible = value).recalculatePackState() }
    }

    fun updateOptions(options: UiConvertOptions) {
        _state.update { it.copy(options = options) }
    }

    fun updateEntry(updated: UiPackEntry) {
        _state.update { current ->
            current.copy(
                entries = current.entries.map { if (it.key == updated.key) updated.normalizedForUi() else it },
                pendingOutputFile = null,
                pendingOutputFileSize = null,
                bundleValidationPassed = null,
                bundleValidationSummary = emptyList(),
                bundleValidationErrors = emptyList(),
                saveStatus = UiSaveStatus.NotSaved,
                savedLocation = null,
                savedFileSize = null,
                canSave = false,
                canSaveDownloads = false,
            ).recalculatePackState()
        }
    }

    fun onPackImageSelected(uri: Uri) = guardedLaunch {
        val currentRun = runDir ?: run {
            setError("请先选择并扫描曲包，再选择曲包封面。")
            return@guardedLaunch
        }
        val target = withContext(Dispatchers.IO) {
            val name = AndroidFileBridge.displayName(app, uri).sanitizeFileName()
            val dir = currentRun.resolve("manual_resources").apply { mkdirs() }
            val file = dir.resolve("pack_image_$name")
            AndroidFileBridge.copyUriToFile(app, uri, file)
            file
        }
        packImageOverride = target
        _state.update {
            it.copy(
                packImageFileName = target.name,
                packImageFilePath = target.absolutePath,
                packImageManual = true,
                logs = it.logs + "Manual pack image selected: ${target.name}",
            )
        }
    }

    fun clearPackImageOverride() {
        packImageOverride = null
        val candidate = packImageCandidate
        _state.update {
            it.copy(
                packImageFileName = candidate?.name,
                packImageFilePath = candidate?.absolutePath,
                packImageManual = false,
                logs = it.logs + "Cleared manual pack image.",
            )
        }
    }

    fun onOfficialTreeSelected(uri: Uri) = guardedLaunch {
        prepareFolderInput(uri, UiPackMode.OfficialArcaeaPack, "Copying official pack folder.")
        scan()
    }

    fun onOfficialZipSelected(uri: Uri) = guardedLaunch {
        prepareZipInput(uri, UiPackMode.OfficialArcaeaPack, "Copying official pack ZIP.")
        scan()
    }

    fun onArcpkgTreeSelected(uri: Uri) = guardedLaunch {
        prepareFolderInput(uri, UiPackMode.ArcpkgBundle, "Copying arcpkg folder.")
        scan()
    }

    fun onArcpkgFilesSelected(uris: List<Uri>) = guardedLaunch {
        if (pendingOutputFile != null) {
            setError("请先保存当前曲包，再选择新的输入。")
            return@guardedLaunch
        }
        resetForInput(UiPackMode.ArcpkgBundle, "${uris.size} arcpkg")
        _state.update { it.copy(isCopying = true, logs = listOf("Copying selected arcpkg files.")) }
        val root = withContext(Dispatchers.IO) {
            createFreshRun()
            val input = requireNotNull(runDir).resolve("input").apply { mkdirs() }
            uris.forEach { uri ->
                val name = AndroidFileBridge.displayName(app, uri).sanitizeFileName()
                AndroidFileBridge.copyUriToFile(app, uri, input.resolve(name))
            }
            input
        }
        projectRoot = root
        _state.update {
            it.copy(
                isCopying = false,
                workspacePath = root.absolutePath,
                projectRootPath = root.absolutePath,
                canScan = true,
                logs = it.logs + "Prepared arcpkg files workspace: ${root.absolutePath}",
            )
        }
        scan()
    }

    fun onArcpkgZipSelected(uri: Uri) = guardedLaunch {
        prepareZipInput(uri, UiPackMode.ArcpkgBundle, "Copying arcpkg ZIP.")
        scan()
    }

    fun onExistingPackSelected(uri: Uri) = guardedLaunch {
        if (pendingOutputFile != null) {
            setError("Please save the current pack before selecting a new input.")
            return@guardedLaunch
        }
        val name = AndroidFileBridge.displayName(app, uri).sanitizeFileName()
        resetForInput(UiPackMode.ExistingPackEdit, name)
        _state.update { it.copy(isCopying = true, logs = listOf("Copying existing pack arcpkg.")) }
        val root = withContext(Dispatchers.IO) {
            createFreshRun()
            val input = requireNotNull(runDir).resolve("input").apply { mkdirs() }
            val baseDir = input.resolve("base").apply { mkdirs() }
            val addDir = input.resolve("add").apply { mkdirs() }
            val baseFile = baseDir.resolve(name.ifBlank { "base.arcpkg" })
            AndroidFileBridge.copyUriToFile(app, uri, baseFile)
            existingPackFile = baseFile
            existingPackAddRoot = addDir
            input
        }
        projectRoot = root
        _state.update {
            it.copy(
                isCopying = false,
                workspacePath = root.absolutePath,
                projectRootPath = root.absolutePath,
                canScan = true,
                logs = it.logs + "Prepared existing pack workspace: ${root.absolutePath}",
            )
        }
        scan()
    }

    fun onExistingPackAddFilesSelected(uris: List<Uri>) = guardedLaunch {
        val addRoot = existingPackAddRoot ?: run {
            setError("Select an existing pack before adding arcpkg files.")
            return@guardedLaunch
        }
        _state.update { it.copy(isCopying = true, logs = it.logs + "Copying arcpkg files to add.") }
        withContext(Dispatchers.IO) {
            uris.forEach { uri ->
                val name = AndroidFileBridge.displayName(app, uri).sanitizeFileName()
                AndroidFileBridge.copyUriToFile(app, uri, addRoot.resolve(uniqueAddFileName(addRoot, name)))
            }
        }
        _state.update {
            it.copy(
                isCopying = false,
                canScan = true,
                logs = it.logs + "Prepared ${uris.size} added arcpkg files.",
            )
        }
        scan()
    }

    fun onExistingPackAddFolderSelected(uri: Uri) = guardedLaunch {
        val addRoot = existingPackAddRoot ?: run {
            setError("Select an existing pack before adding an arcpkg folder.")
            return@guardedLaunch
        }
        val name = AndroidFileBridge.displayName(app, uri).sanitizeFileName().ifBlank { "folder" }
        _state.update { it.copy(isCopying = true, logs = it.logs + "Copying arcpkg folder to add.") }
        val target = withContext(Dispatchers.IO) {
            val target = addRoot.resolve("${System.currentTimeMillis()}_$name")
            SafTreeCopier(app).copyTree(uri, target)
            target
        }
        _state.update {
            it.copy(
                isCopying = false,
                canScan = true,
                logs = it.logs + "Prepared added arcpkg folder: ${target.absolutePath}",
            )
        }
        scan()
    }

    fun scan() = guardedLaunch {
        val root = projectRoot ?: run {
            setError("没有可扫描的输入。")
            return@guardedLaunch
        }
        _state.update { it.copy(isScanning = true, scanStatus = UiScanStatus.Scanning, warnings = emptyList(), errorMessage = null, errorDetails = null) }
        when (state.value.mode) {
            UiPackMode.OfficialArcaeaPack -> {
                val result = withContext(Dispatchers.IO) { PackBundleScanner().scanOfficialPack(root) }
                _state.update {
                    val imageFile = packImageOverride ?: result.packImageFile
                    packImageCandidate = result.packImageFile
                    val newPackId = (result.packIdCandidate ?: it.packId.ifBlank { defaultPackIdFromInput() }).safeId("EtoileBridgePack")
                    it.copy(
                        isScanning = false,
                        scanStatus = UiScanStatus.Scanned,
                        projectRootPath = result.projectRoot.absolutePath,
                        packName = result.packNameCandidate ?: it.packName.ifBlank { defaultPackNameFromInput() },
                        packId = newPackId,
                        outputFileName = if (it.outputFileNameManual) it.outputFileName else defaultOutputFileName(it.publisherId, newPackId),
                        packImageFileName = imageFile?.name,
                        packImageFilePath = imageFile?.absolutePath,
                        packImageManual = packImageOverride != null,
                        entries = result.entries.map { entry -> entry.toUi() },
                        sourceReports = emptyList(),
                        warnings = result.warnings + result.entries.flatMap { entry -> entry.warnings },
                        logs = it.logs + result.logs + "Scanned official pack: ${result.entries.size} songs.",
                        canPack = result.canConvertAny,
                    )
                }
            }
            UiPackMode.ArcpkgBundle -> {
                val result = withContext(Dispatchers.IO) { ArcpkgBundleMerger().scan(root) }
                val candidateImage = if (packImageOverride == null) {
                    withContext(Dispatchers.IO) { extractPackImageCandidate(result.packImageCandidate) }
                } else {
                    null
                }
                if (candidateImage != null) packImageCandidate = candidateImage
                val imageFile = packImageOverride ?: candidateImage ?: packImageCandidate
                _state.update {
                    val newPackId = it.packId.ifBlank { defaultPackIdFromInput() }.safeId("EtoileBridgePack")
                    it.copy(
                        isScanning = false,
                        scanStatus = UiScanStatus.Scanned,
                        packName = result.packNameCandidate ?: it.packName.ifBlank { defaultPackNameFromInput() },
                        packId = newPackId,
                        outputFileName = if (it.outputFileNameManual) it.outputFileName else defaultOutputFileName(it.publisherId, newPackId),
                        packImageFileName = imageFile?.name,
                        packImageFilePath = imageFile?.absolutePath,
                        packImageManual = packImageOverride != null,
                        entries = result.levelEntries.map { level ->
                            val levelId = level.identifier.substringAfterLast('.', level.directory).safeId(level.directory)
                            val canUseLevel = level.failureReason == null && level.charts.isNotEmpty()
                            UiPackEntry(
                                key = level.key,
                                songId = level.directory,
                                title = level.title.orEmpty(),
                                artist = level.artist.orEmpty(),
                                difficultySummary = level.difficultySummary,
                                charts = level.charts.map { chart ->
                                    UiPackChartEntry(
                                        ratingClass = chart.ratingClass,
                                        chartPath = chart.chartPath,
                                        difficultyText = chart.difficulty.orEmpty(),
                                        chartConstantText = chart.chartConstant?.toString().orEmpty(),
                                        charter = chart.charter.orEmpty(),
                                        illustrator = chart.illustrator.orEmpty(),
                                        enabled = chart.enabled,
                                        canConvert = chart.canConvert,
                                        warningCount = chart.warnings.size,
                                        warnings = chart.warnings,
                                        failureReason = chart.failureReason,
                                    )
                                },
                                levelId = levelId,
                                originalLevelId = levelId,
                                audio = null,
                                jacket = null,
                                background = null,
                                metadataStatus = if (level.charts.isEmpty()) "Unable to read ArcCreate charts" else "ArcCreate project",
                                enabled = canUseLevel,
                                canConvert = canUseLevel,
                                warningCount = level.warnings.size,
                                warnings = level.warnings,
                                failureReason = level.failureReason,
                            )
                        },
                        sourceReports = result.sourceFiles.map {
                            UiArcpkgSourceReport(it.sourceFile.name, it.readable, it.levelCount, it.failureReason)
                        },
                        warnings = result.warnings,
                        logs = it.logs + result.logs + "Scanned arcpkg collection: ${result.validLevelCount} levels.",
                        canPack = result.levelEntries.any { level -> level.failureReason == null && level.charts.isNotEmpty() },
                    )
                }
            }
            UiPackMode.ExistingPackEdit -> {
                val base = existingPackFile ?: run {
                    setError("No existing pack selected.")
                    return@guardedLaunch
                }
                val addRoot = existingPackAddRoot
                val result = withContext(Dispatchers.IO) { ArcpkgBundleMerger().scanExistingPack(base, addRoot) }
                val candidateImage = if (packImageOverride == null) {
                    withContext(Dispatchers.IO) { extractPackImageCandidate(result.packImageCandidate) }
                } else {
                    null
                }
                if (candidateImage != null) packImageCandidate = candidateImage
                val imageFile = packImageOverride ?: candidateImage ?: packImageCandidate
                _state.update {
                    val packEntry = result.basePackEntry
                    val packId = result.packIdCandidate?.safeId("EtoileBridgePack")
                        ?: it.packId.ifBlank { defaultPackIdFromInput() }.safeId("EtoileBridgePack")
                    val publisher = packEntry?.identifier
                        ?.removeSuffix(".pack")
                        ?.substringBeforeLast('.', it.publisherId)
                        ?.takeIf { value -> value.isNotBlank() }
                        ?: it.publisherId
                    it.copy(
                        isScanning = false,
                        scanStatus = UiScanStatus.Scanned,
                        publisherId = publisher.safeId("etoilebridge"),
                        packName = result.packNameCandidate ?: it.packName.ifBlank { defaultPackNameFromInput() },
                        packId = packId,
                        outputFileName = if (it.outputFileNameManual) it.outputFileName else defaultOutputFileName(publisher, packId),
                        packImageFileName = imageFile?.name,
                        packImageFilePath = imageFile?.absolutePath,
                        packImageManual = packImageOverride != null,
                        entries = (result.existingLevels + result.addedLevels).map { level -> level.toUiPackEntry() },
                        sourceReports = result.sourceFiles.map { report ->
                            UiArcpkgSourceReport(report.sourceFile.name, report.readable, report.levelCount, report.failureReason)
                        },
                        existingLevelCount = result.existingLevelCount,
                        addedLevelCount = result.addedLevelCount,
                        finalLevelCount = result.finalLevelCount,
                        renamedConflictCount = result.renamedConflictCount,
                        warnings = result.warnings,
                        logs = it.logs + result.logs + "Scanned existing pack edit: ${result.existingLevelCount} existing, ${result.addedLevelCount} added.",
                        canPack = result.canRebuild,
                    ).recalculatePackState()
                }
            }
        }
    }

    fun pack() = guardedLaunch {
        val root = projectRoot ?: run {
            setError("没有可打包的输入。")
            return@guardedLaunch
        }
        val currentRun = runDir ?: return@guardedLaunch
        pendingOutputFile = null
        _state.update {
            it.copy(
                isPacking = true,
                pendingOutputFile = null,
                pendingOutputFileSize = null,
                bundleValidationPassed = null,
                bundleValidationSummary = emptyList(),
                bundleValidationErrors = emptyList(),
                saveStatus = UiSaveStatus.NotSaved,
                savedLocation = null,
                savedFileSize = null,
                workspaceCleaned = false,
                errorMessage = null,
                errorDetails = null,
                logs = it.logs + "Starting bundle packing.",
            )
        }
        val output = currentRun.resolve("output").apply { mkdirs() }.resolve(state.value.outputFileName.ensureArcpkgExtension())
        val result = withContext(Dispatchers.IO) {
            when (state.value.mode) {
                UiPackMode.OfficialArcaeaPack -> PackBundleConverter().convertOfficialPack(
                    BundleInput(
                        workspaceDir = root,
                        outputFile = output,
                        options = BundleOptions(
                            publisherId = state.value.publisherId,
                            outputFileName = state.value.outputFileName.ensureArcpkgExtension(),
                            packName = state.value.packName.ifBlank { defaultPackNameFromInput() },
                            packId = state.value.packId.ifBlank { defaultPackIdFromInput() },
                            packImageFile = packImageOverride ?: packImageCandidate,
                            includeOnlyConvertible = state.value.includeOnlyConvertible,
                            convertOptions = state.value.options.toCoreOptions(),
                            entryOverrides = state.value.toEntryOverrides(),
                        ),
                    )
                )
                UiPackMode.ArcpkgBundle -> ArcpkgBundleMerger().merge(
                    root,
                    output,
                    BundleOptions(
                        publisherId = state.value.publisherId,
                        outputFileName = state.value.outputFileName.ensureArcpkgExtension(),
                        packName = state.value.packName.ifBlank { defaultPackNameFromInput() },
                        packId = state.value.packId.ifBlank { defaultPackIdFromInput() },
                        packImageFile = packImageOverride ?: packImageCandidate,
                        includeOnlyConvertible = state.value.includeOnlyConvertible,
                        entryOverrides = state.value.toEntryOverrides(),
                        convertOptions = ConvertOptions(
                            enableDeleteDesignantLine = false,
                            enableFixZeroDurationArcTap = false,
                            enableFixReversedArcTime = false,
                            enableExpandArcResolution = false,
                        ),
                    ),
                )
                UiPackMode.ExistingPackEdit -> ArcpkgBundleMerger().editExistingPack(
                    basePack = existingPackFile ?: error("No existing pack selected."),
                    addInput = existingPackAddRoot,
                    outputFile = output,
                    options = BundleOptions(
                        publisherId = state.value.publisherId,
                        outputFileName = state.value.outputFileName.ensureArcpkgExtension(),
                        packName = state.value.packName.ifBlank { defaultPackNameFromInput() },
                        packId = state.value.packId.ifBlank { defaultPackIdFromInput() },
                        packImageFile = packImageOverride ?: packImageCandidate,
                        includeOnlyConvertible = state.value.includeOnlyConvertible,
                        entryOverrides = state.value.toEntryOverrides(),
                        convertOptions = ConvertOptions(
                            enableDeleteDesignantLine = false,
                            enableFixZeroDurationArcTap = false,
                            enableFixReversedArcTime = false,
                            enableExpandArcResolution = false,
                        ),
                    ),
                )
            }
        }
        when (result) {
            is BundleConvertResult.Success -> {
                val validation = withContext(Dispatchers.IO) {
                    BundleOutputValidator().validateBundleArcpkg(result.outputFile)
                }
                pendingOutputFile = result.outputFile
                _state.update {
                    it.copy(
                        isPacking = false,
                        pendingOutputFile = result.outputFile,
                        pendingOutputFileSize = result.outputFile.length(),
                        bundleValidationPassed = validation.valid,
                        bundleValidationSummary = validation.summaryLines(),
                        bundleValidationErrors = validation.errors,
                        saveStatus = UiSaveStatus.Pending,
                        warnings = result.warnings + validation.warnings + validation.errors,
                        logs = it.logs + result.logs + validation.summaryLines() + validation.logs + "Packed bundle to cache: ${result.outputFile.absolutePath}",
                        errorMessage = if (validation.valid) null else "曲包结构验证失败",
                        errorDetails = if (validation.valid) null else validation.errors.joinToString("\n"),
                        canSave = validation.valid,
                        canSaveDownloads = validation.valid && canUseDownloads(),
                    )
                }
            }
            is BundleConvertResult.Failed -> {
                _state.update {
                    it.copy(
                        isPacking = false,
                        warnings = result.warnings,
                        logs = it.logs + result.logs,
                        errorMessage = result.message,
                        errorDetails = result.cause?.stackTraceToString(),
                        bundleValidationPassed = false,
                        bundleValidationSummary = emptyList(),
                        bundleValidationErrors = result.warnings,
                        canSave = false,
                        canSaveDownloads = false,
                    )
                }
            }
        }
    }

    fun saveOutputToDownloads() {
        val file = pendingOutputFile ?: return
        if (state.value.bundleValidationPassed != true) {
            setError("曲包结构验证未通过，不能保存。")
            return
        }
        if (!canUseDownloads()) {
            _state.update {
                it.copy(
                    saveStatus = UiSaveStatus.Failed,
                    errorMessage = "当前系统请使用另存为保存文件",
                    logs = it.logs + "Downloads unavailable on SDK ${Build.VERSION.SDK_INT}; pending output is kept.",
                )
            }
            return
        }
        savePending("Downloads") { AndroidFileBridge.saveToDownloads(app, file, suggestedOutputFileName()) }
    }

    fun saveOutputTo(uri: Uri) {
        val file = pendingOutputFile ?: return
        if (state.value.bundleValidationPassed != true) {
            setError("曲包结构验证未通过，不能保存。")
            return
        }
        savePending("SAF") { AndroidFileBridge.writeFileToUri(app, file, uri) }
    }

    fun onSaveAsCanceled() {
        if (pendingOutputFile == null) return
        _state.update {
            it.copy(
                saveStatus = UiSaveStatus.Canceled,
                errorMessage = "用户取消保存",
                errorDetails = null,
                canSave = it.bundleValidationPassed == true,
                canSaveDownloads = it.bundleValidationPassed == true && canUseDownloads(),
                logs = it.logs + "User canceled pack save; pending output is kept.",
            )
        }
    }

    fun suggestedOutputFileName(): String =
        state.value.outputFileName.ensureArcpkgExtension()

    fun clearCache() = guardedLaunch {
        if (pendingOutputFile != null) {
            setError("请先保存当前曲包，再清理缓存。")
            return@guardedLaunch
        }
        withContext(Dispatchers.IO) { cacheRoot().deleteRecursively() }
        runDir = null
        projectRoot = null
        existingPackFile = null
        existingPackAddRoot = null
        pendingOutputFile = null
        _state.update {
            baseState(it.language).copy(logs = it.logs + "Cleared pack cache.")
        }
    }

    fun reportExternalError(message: String, throwable: Throwable) {
        showError(message, throwable)
    }

    private suspend fun prepareFolderInput(uri: Uri, mode: UiPackMode, logLine: String) {
        if (pendingOutputFile != null) {
            setError("请先保存当前曲包，再选择新的输入。")
            return
        }
        val name = AndroidFileBridge.displayName(app, uri)
        resetForInput(mode, name)
        _state.update { it.copy(isCopying = true, logs = listOf(logLine)) }
        val root = withContext(Dispatchers.IO) {
            createFreshRun()
            val input = requireNotNull(runDir).resolve("input")
            SafTreeCopier(app).copyTree(uri, input)
            input
        }
        projectRoot = root
        _state.update {
            it.copy(
                isCopying = false,
                workspacePath = root.absolutePath,
                projectRootPath = root.absolutePath,
                canScan = true,
                logs = it.logs + "Prepared folder workspace: ${root.absolutePath}",
            )
        }
    }

    private suspend fun prepareZipInput(uri: Uri, mode: UiPackMode, logLine: String) {
        if (pendingOutputFile != null) {
            setError("请先保存当前曲包，再选择新的输入。")
            return
        }
        val name = AndroidFileBridge.displayName(app, uri)
        resetForInput(mode, name)
        _state.update { it.copy(isCopying = true, logs = listOf(logLine, "Extracting zip.")) }
        val root = withContext(Dispatchers.IO) {
            createFreshRun()
            val currentRun = requireNotNull(runDir)
            val sourceZip = currentRun.resolve("source.zip")
            AndroidFileBridge.copyUriToFile(app, uri, sourceZip)
            val archiveDir = currentRun.resolve("archive")
            ZipArchiveExtractor().extract(sourceZip, archiveDir)
        }
        projectRoot = root
        _state.update {
            it.copy(
                isCopying = false,
                workspacePath = root.absolutePath,
                projectRootPath = root.absolutePath,
                canScan = true,
                logs = it.logs + "Prepared zip workspace: ${root.absolutePath}",
            )
        }
    }

    private fun savePending(method: String, block: suspend () -> SaveResult) = guardedLaunch {
        val file = pendingOutputFile ?: return@guardedLaunch
        _state.update { it.copy(isSaving = true, saveStatus = UiSaveStatus.Saving, errorMessage = null, errorDetails = null) }
        val result = withContext(Dispatchers.IO) { block() }
        val currentRun = runDir
        withContext(Dispatchers.IO) { currentRun?.deleteRecursively() }
        runDir = null
        projectRoot = null
        existingPackFile = null
        existingPackAddRoot = null
        pendingOutputFile = null
        _state.update {
            it.copy(
                isSaving = false,
                pendingOutputFile = null,
                pendingOutputFileSize = null,
                bundleValidationPassed = null,
                bundleValidationSummary = emptyList(),
                bundleValidationErrors = emptyList(),
                saveStatus = UiSaveStatus.Saved,
                savedLocation = result.location,
                savedFileSize = result.writtenBytes,
                workspaceCleaned = true,
                canSave = false,
                canSaveDownloads = false,
                logs = it.logs + "Saved pack via $method: ${result.location}" + "Cleaned pack workspace.",
            )
        }
        file
    }

    private fun resetForInput(mode: UiPackMode, inputName: String?) {
        pendingOutputFile = null
        projectRoot = null
        packImageOverride = null
        packImageCandidate = null
        existingPackFile = null
        existingPackAddRoot = null
        _state.update {
            val packName = inputName?.substringBeforeLast('.') ?: "EtoileBridge Pack"
            val packId = packName.safeId("EtoileBridgePack")
            baseState(it.language).copy(
                mode = mode,
                inputName = inputName,
                publisherId = it.publisherId,
                outputFileName = defaultOutputFileName(it.publisherId, packId),
                outputFileNameManual = false,
                packName = packName,
                packId = packId,
                includeOnlyConvertible = it.includeOnlyConvertible,
                options = it.options,
            )
        }
    }

    private fun createFreshRun() {
        val root = cacheRoot().apply { mkdirs() }
        runDir = root.resolve(System.currentTimeMillis().toString()).apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun cacheRoot(): File = app.cacheDir.resolve("etoilebridge_pack")

    private fun guardedLaunch(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.Main + crashGuard) {
            try {
                block()
            } catch (error: Exception) {
                showError(error.message ?: "操作失败", error)
            }
        }
    }

    private fun showError(message: String, throwable: Throwable) {
        _state.update {
            it.copy(
                isCopying = false,
                isScanning = false,
                isPacking = false,
                isSaving = false,
                errorMessage = message,
                errorDetails = throwable.stackTraceToString(),
                logs = it.logs + "Operation failed: ${throwable.message}",
            )
        }
    }

    private fun setError(message: String) {
        _state.update { it.copy(errorMessage = message, logs = it.logs + message) }
    }

    private fun canUseDownloads(): Boolean =
        AndroidFileBridge.canUseMediaStoreDownloads(Build.VERSION.SDK_INT)

    private fun extractPackImageCandidate(candidate: com.zeerqi27.etoilebridge.core.ArcpkgPackImageCandidate?): File? {
        val currentRun = runDir ?: return null
        if (candidate == null) return null
        return runCatching {
            val output = currentRun.resolve("pack_image_candidate").apply { mkdirs() }.resolve("pack.png")
            ZipFile(candidate.sourceFile).use { zip ->
                val entry = zip.getEntry(candidate.zipEntryPath) ?: return@runCatching null
                zip.getInputStream(entry).use { input ->
                    output.outputStream().use { input.copyTo(it) }
                }
            }
            output
        }.getOrNull()
    }

    private fun defaultPackNameFromInput(): String =
        state.value.inputName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "EtoileBridge Pack"

    private fun defaultPackIdFromInput(): String =
        defaultPackNameFromInput().safeId("EtoileBridgePack")

    private fun defaultOutputFileName(publisherId: String, packId: String): String =
        "${publisherId.safeId("etoilebridge")}.${packId.safeId("EtoileBridgePack")}.arcpkg"
            .sanitizeFileName()

    private fun UiPackConvertState.withDefaultOutputNameIfNeeded(): UiPackConvertState =
        if (outputFileNameManual) this else copy(outputFileName = defaultOutputFileName(publisherId, packId))

    private fun baseState(language: UiLanguage): UiPackConvertState =
        UiPackConvertState(
            language = language,
            deviceSdkInt = Build.VERSION.SDK_INT,
            deviceRelease = Build.VERSION.RELEASE.orEmpty(),
            canUseMediaStoreDownloads = canUseDownloads(),
        )

    private fun loadLanguage(): UiLanguage {
        val saved = prefs.getString(PREF_LANGUAGE, null)
        if (saved != null) return runCatching { UiLanguage.valueOf(saved) }.getOrNull() ?: defaultLanguage()
        return defaultLanguage()
    }

    private fun defaultLanguage(): UiLanguage =
        if (Locale.getDefault().language.equals("zh", ignoreCase = true)) UiLanguage.ZhHans else UiLanguage.English

    private fun UiConvertOptions.toCoreOptions(): ConvertOptions =
        ConvertOptions(
            enableDeleteDesignantLine = enableDeleteDesignantLine,
            enableFixZeroDurationArcTap = enableFixZeroDurationArcTap,
            enableFixReversedArcTime = enableFixReversedArcTime,
            enableExpandArcResolution = enableExpandArcResolution,
            keepWorkspaceOnFailure = true,
            cleanWorkspaceOnSuccess = true,
        )

    private fun com.zeerqi27.etoilebridge.core.BundleEntry.toUi(): UiPackEntry =
        UiPackEntry(
            key = key,
            songId = songId,
            title = title.orEmpty(),
            artist = artist.orEmpty(),
            difficultySummary = difficultySummary,
            charts = charts.map { chart ->
                UiPackChartEntry(
                    ratingClass = chart.ratingClass,
                    chartPath = chart.chartPath,
                    difficultyText = chart.difficulty.orEmpty(),
                    chartConstantText = chart.chartConstant?.toString().orEmpty(),
                    charter = chart.charter.orEmpty(),
                    illustrator = chart.illustrator.orEmpty(),
                    enabled = chart.enabled,
                    canConvert = chart.canConvert,
                    warningCount = chart.warnings.size,
                    warnings = chart.warnings,
                    failureReason = chart.failureReason,
                )
            },
            levelId = songId,
            enabled = canConvert,
            audio = audioFile?.name,
            jacket = jacketFile?.name,
            background = backgroundFile?.name,
            metadataStatus = when (metadataStatus) {
                BundleMetadataStatus.Resolved -> "OK"
                BundleMetadataStatus.NeedMetadata -> "Need metadata"
                BundleMetadataStatus.Unknown -> "Unknown"
            },
            canConvert = canConvert,
            warningCount = warnings.size,
            warnings = warnings,
            failureReason = failureReason,
        )

    private fun com.zeerqi27.etoilebridge.core.ArcpkgLevelEntry.toUiPackEntry(): UiPackEntry {
        val levelId = identifier.substringAfterLast('.', directory).safeId(directory)
        val canUseLevel = failureReason == null && charts.isNotEmpty()
        return UiPackEntry(
            key = key,
            songId = directory,
            title = title.orEmpty(),
            artist = artist.orEmpty(),
            difficultySummary = difficultySummary,
            charts = charts.map { chart ->
                UiPackChartEntry(
                    ratingClass = chart.ratingClass,
                    chartPath = chart.chartPath,
                    difficultyText = chart.difficulty.orEmpty(),
                    chartConstantText = chart.chartConstant?.toString().orEmpty(),
                    charter = chart.charter.orEmpty(),
                    illustrator = chart.illustrator.orEmpty(),
                    enabled = chart.enabled,
                    canConvert = chart.canConvert,
                    warningCount = chart.warnings.size,
                    warnings = chart.warnings,
                    failureReason = chart.failureReason,
                )
            },
            levelId = levelId,
            originalLevelId = levelId,
            audio = null,
            jacket = null,
            background = null,
            metadataStatus = if (charts.isEmpty()) "Unable to read ArcCreate charts" else "ArcCreate project",
            enabled = canUseLevel,
            canConvert = canUseLevel,
            warningCount = warnings.size,
            warnings = warnings,
            failureReason = failureReason,
        )
    }

    private fun UiPackEntry.normalizedForUi(): UiPackEntry =
        copy(
            levelId = levelId.safeId(songId),
            charts = charts.map { it.copy(chartConstantText = it.chartConstantText.trim()) },
            enabled = enabled && canBeEnabled,
        )

    private fun UiPackConvertState.recalculatePackState(): UiPackConvertState {
        val enabled = entries.filter { it.enabled }
        val canPackNow = enabled.any { it.effectiveCanPack } &&
            (includeOnlyConvertible || enabled.all { it.effectiveCanPack })
        return copy(canPack = canPackNow)
    }

    private fun UiPackConvertState.toEntryOverrides(): Map<String, BundleEntryOverride> =
        entries.associate { entry ->
            val arcpkgMode = mode == UiPackMode.ArcpkgBundle || mode == UiPackMode.ExistingPackEdit
            val chartOverrides = entry.charts.mapNotNull { chart ->
                val override = if (arcpkgMode) {
                    val changed = chart.enabled != chart.originalEnabled ||
                        chart.difficultyText != chart.originalDifficultyText ||
                        chart.chartConstantText != chart.originalChartConstantText ||
                        chart.charter != chart.originalCharter ||
                        chart.illustrator != chart.originalIllustrator
                    if (!changed) {
                        null
                    } else {
                        BundleChartOverride(
                            enabled = chart.enabled,
                            difficulty = chart.difficultyText.takeIf { it.isNotBlank() && it != chart.originalDifficultyText },
                            chartConstant = chart.chartConstantText.toFloatOrNull()
                                ?.takeIf { chart.chartConstantText != chart.originalChartConstantText },
                            charter = chart.charter.takeIf { it.isNotBlank() && it != chart.originalCharter },
                            illustrator = chart.illustrator.takeIf { it.isNotBlank() && it != chart.originalIllustrator },
                        )
                    }
                } else {
                    BundleChartOverride(
                        enabled = chart.enabled,
                        difficulty = chart.difficultyText.takeIf { it.isNotBlank() },
                        chartConstant = chart.chartConstantText.toFloatOrNull(),
                        charter = chart.charter.takeIf { it.isNotBlank() },
                        illustrator = chart.illustrator.takeIf { it.isNotBlank() },
                    )
                }
                override?.let { chart.ratingClass to it }
            }.toMap()

            entry.key to BundleEntryOverride(
                enabled = entry.enabled,
                title = if (arcpkgMode) entry.title.takeIf { it.isNotBlank() && it != entry.originalTitle } else entry.title.takeIf { it.isNotBlank() },
                artist = if (arcpkgMode) entry.artist.takeIf { it.isNotBlank() && it != entry.originalArtist } else entry.artist.takeIf { it.isNotBlank() },
                levelId = if (arcpkgMode) {
                    entry.levelId.takeIf { it.isNotBlank() && it != entry.originalLevelId }
                } else {
                    entry.levelId.takeIf { it.isNotBlank() } ?: entry.songId
                },
                chartOverrides = chartOverrides,
            )
        }

    private fun String.ensureArcpkgExtension(): String =
        if (endsWith(".arcpkg", ignoreCase = true)) this else "$this.arcpkg"

    private fun String.sanitizeFileName(): String =
        replace(Regex("""[\\/:*?"<>|]"""), "_")

    private fun uniqueAddFileName(dir: File, requestedName: String): String {
        val clean = requestedName.sanitizeFileName().ifBlank { "input.arcpkg" }
        if (!dir.resolve(clean).exists()) return clean
        val base = clean.substringBeforeLast('.', clean)
        val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
        var index = 2
        while (true) {
            val candidate = if (ext.isBlank()) "${base}_$index" else "${base}_$index.$ext"
            if (!dir.resolve(candidate).exists()) return candidate
            index++
        }
    }

    private fun String.safeId(fallback: String): String =
        replace(Regex("""[^\w.-]+"""), "_")
            .trim('_', '.', '-')
            .ifBlank { fallback }

    companion object {
        private const val PREF_LANGUAGE = "language"
    }
}
