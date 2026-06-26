package com.zeerqi27.etoilebridge.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeerqi27.etoilebridge.core.AppearanceOptions
import com.zeerqi27.etoilebridge.core.ArcCreateAccent
import com.zeerqi27.etoilebridge.core.ArcCreateNote
import com.zeerqi27.etoilebridge.core.ArcCreateParticle
import com.zeerqi27.etoilebridge.core.ArcCreateSide
import com.zeerqi27.etoilebridge.core.ArcCreateSingleLine
import com.zeerqi27.etoilebridge.core.ArcCreateTrack
import com.zeerqi27.etoilebridge.core.ConvertInput
import com.zeerqi27.etoilebridge.core.ConvertOptions
import com.zeerqi27.etoilebridge.core.ConvertResult
import com.zeerqi27.etoilebridge.core.DifficultyMapper
import com.zeerqi27.etoilebridge.core.EtoileBridgeConverter
import com.zeerqi27.etoilebridge.core.InputKind
import com.zeerqi27.etoilebridge.core.InputScanner
import com.zeerqi27.etoilebridge.core.ManualChartOverrides
import com.zeerqi27.etoilebridge.core.ManualDifficultyMetadata
import com.zeerqi27.etoilebridge.core.ManualMetadata
import com.zeerqi27.etoilebridge.core.ManualResourceOverrides
import com.zeerqi27.etoilebridge.core.MetadataResolution
import com.zeerqi27.etoilebridge.core.MetadataResolver
import com.zeerqi27.etoilebridge.core.MissingMetadata
import com.zeerqi27.etoilebridge.core.MissingRequiredResourceException
import com.zeerqi27.etoilebridge.core.PackageOptions
import com.zeerqi27.etoilebridge.core.PacklistParser
import com.zeerqi27.etoilebridge.core.ResourceResolver
import com.zeerqi27.etoilebridge.core.ResolvedDifficultyMetadata
import com.zeerqi27.etoilebridge.core.ResolvedSongMetadata
import com.zeerqi27.etoilebridge.core.ScannedInput
import com.zeerqi27.etoilebridge.core.Songlist
import com.zeerqi27.etoilebridge.core.SonglistParser
import com.zeerqi27.etoilebridge.file.AndroidFileBridge
import com.zeerqi27.etoilebridge.file.CacheCleaner
import com.zeerqi27.etoilebridge.file.DownloadsRequiresCreateDocumentException
import com.zeerqi27.etoilebridge.file.SafTreeCopier
import com.zeerqi27.etoilebridge.file.SaveResult
import com.zeerqi27.etoilebridge.file.ZipArchiveExtractor
import com.zeerqi27.etoilebridge.model.ManualResourceKind
import com.zeerqi27.etoilebridge.model.SaveStateTransitions
import com.zeerqi27.etoilebridge.model.UiAffMappingItem
import com.zeerqi27.etoilebridge.model.UiAppearanceOptions
import com.zeerqi27.etoilebridge.model.UiArcCreateAccent
import com.zeerqi27.etoilebridge.model.UiArcCreateNote
import com.zeerqi27.etoilebridge.model.UiArcCreateParticle
import com.zeerqi27.etoilebridge.model.UiArcCreateSide
import com.zeerqi27.etoilebridge.model.UiArcCreateSingleLine
import com.zeerqi27.etoilebridge.model.UiArcCreateTrack
import com.zeerqi27.etoilebridge.model.UiConvertOptions
import com.zeerqi27.etoilebridge.model.UiConvertState
import com.zeerqi27.etoilebridge.model.UiDifficultyDraft
import com.zeerqi27.etoilebridge.model.UiExtractStatus
import com.zeerqi27.etoilebridge.model.UiInputType
import com.zeerqi27.etoilebridge.model.UiLanguage
import com.zeerqi27.etoilebridge.model.UiManualResources
import com.zeerqi27.etoilebridge.model.UiMetadataDraft
import com.zeerqi27.etoilebridge.model.UiMissingMetadata
import com.zeerqi27.etoilebridge.model.UiResourceStatus
import com.zeerqi27.etoilebridge.model.UiSaveStatus
import com.zeerqi27.etoilebridge.model.UiScanStatus
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConverterViewModel(
    private val app: Application,
) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("etoilebridge_ui", Application.MODE_PRIVATE)
    private val _state = MutableStateFlow(baseState(loadLanguage()))
    val state: StateFlow<UiConvertState> = _state.asStateFlow()

    private var runDir: File? = null
    private var projectRoot: File? = null
    private var songRoot: File? = null
    private var pendingOutputFile: File? = null
    private var manualOverrides = ManualResourceOverrides()
    private var manualMetadata: ManualMetadata? = null
    private var manualChartOverrides = ManualChartOverrides()
    private var packageOptions = PackageOptions()
    private var appearanceOptions = AppearanceOptions()
    private var appearanceEdited = false

    private val crashGuard = CoroutineExceptionHandler { _, throwable ->
        showError("操作失败", throwable)
    }

    init {
        viewModelScope.launch(Dispatchers.IO + crashGuard) {
            CacheCleaner.cleanOlderThan(app.cacheDir)
        }
    }

    fun onInputTreeSelected(uri: Uri) = guardedLaunch {
        if (pendingOutputFile != null) {
            setError("请先保存当前包，再选择新的输入。")
            return@guardedLaunch
        }
        val name = AndroidFileBridge.displayName(app, uri)
        resetForInput(UiInputType.Folder, name)
        _state.update { it.copy(isCopying = true, logs = listOf("Copying folder to cache.")) }
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
                extractStatus = UiExtractStatus.NotExtracted,
                canScan = true,
                logs = it.logs + "Prepared folder workspace: ${root.absolutePath}",
            )
        }
        scanInternal()
    }

    fun onZipSelected(uri: Uri) = guardedLaunch {
        if (pendingOutputFile != null) {
            setError("请先保存当前包，再选择新的输入。")
            return@guardedLaunch
        }
        val name = AndroidFileBridge.displayName(app, uri)
        resetForInput(UiInputType.Zip, name)
        _state.update {
            it.copy(
                isCopying = true,
                extractStatus = UiExtractStatus.Extracting,
                logs = listOf("Copying zip to cache.", "Extracting zip."),
            )
        }
        val root = withContext(Dispatchers.IO) {
            createFreshRun()
            val currentRun = requireNotNull(runDir)
            val sourceZip = currentRun.resolve("source.zip")
            AndroidFileBridge.copyUriToFile(app, uri, sourceZip)
            val archiveDir = currentRun.resolve("archive")
            ZipArchiveExtractor().extract(sourceZip, archiveDir)
            normalizeArchiveRoot(archiveDir)
        }
        projectRoot = root
        _state.update {
            it.copy(
                isCopying = false,
                workspacePath = root.absolutePath,
                projectRootPath = root.absolutePath,
                extractStatus = UiExtractStatus.Extracted,
                canScan = true,
                logs = it.logs + "Prepared zip workspace: ${root.absolutePath}",
            )
        }
        scanInternal()
    }

    fun onManualResourceSelected(kind: ManualResourceKind, uri: Uri) = guardedLaunch {
        val currentRun = runDir
        if (currentRun == null || projectRoot == null) {
            setError("请先选择文件夹或 ZIP。")
            return@guardedLaunch
        }
        val displayName = AndroidFileBridge.displayName(app, uri)
        _state.update { it.copy(isCopying = true, errorMessage = null, errorDetails = null) }
        val target = withContext(Dispatchers.IO) {
            val manualDir = currentRun.resolve("manual_resources").apply { mkdirs() }
            val out = manualDir.resolve("${kind.name.lowercase()}_${displayName.sanitizeFileName()}")
            AndroidFileBridge.copyUriToFile(app, uri, out)
            out
        }
        manualOverrides = when (kind) {
            ManualResourceKind.Audio -> manualOverrides.copy(audioFile = target)
            ManualResourceKind.Jacket -> manualOverrides.copy(jacketFile = target)
            ManualResourceKind.Background -> manualOverrides.copy(backgroundFile = target)
            ManualResourceKind.Songlist -> manualOverrides.copy(songlistFile = target)
            ManualResourceKind.Packlist -> manualOverrides.copy(packlistFile = target)
        }
        _state.update {
            it.copy(
                isCopying = false,
                manualResources = manualOverrides.toUiManualResources(),
                logs = it.logs + "Manual ${kind.name.lowercase()} selected: ${target.name}",
            )
        }
        scanInternal()
    }

    fun clearManualResource(kind: ManualResourceKind) = guardedLaunch {
        manualOverrides = when (kind) {
            ManualResourceKind.Audio -> manualOverrides.copy(audioFile = null)
            ManualResourceKind.Jacket -> manualOverrides.copy(jacketFile = null)
            ManualResourceKind.Background -> manualOverrides.copy(backgroundFile = null)
            ManualResourceKind.Songlist -> manualOverrides.copy(songlistFile = null)
            ManualResourceKind.Packlist -> manualOverrides.copy(packlistFile = null)
        }
        _state.update {
            it.copy(
                manualResources = manualOverrides.toUiManualResources(),
                logs = it.logs + "Manual ${kind.name.lowercase()} cleared.",
                errorMessage = null,
                errorDetails = null,
            )
        }
        if (projectRoot != null) scanInternal()
    }

    fun scan() = guardedLaunch {
        scanInternal()
    }

    fun convert() = guardedLaunch {
        val root = projectRoot ?: run {
            setError("没有可扫描的输入目录。")
            return@guardedLaunch
        }
        val currentRun = runDir ?: return@guardedLaunch
        pendingOutputFile = null
        _state.update {
            it.copy(
                isConverting = true,
                canSave = false,
                canSaveDownloads = false,
                pendingOutputFile = null,
                pendingOutputFileSize = null,
                saveStatus = UiSaveStatus.NotSaved,
                savedMethod = null,
                savedFileName = null,
                savedLocation = null,
                savedFileSize = null,
                workspaceCleaned = false,
                errorMessage = null,
                errorDetails = null,
                warnings = emptyList(),
                logs = it.logs + "Starting conversion.",
            )
        }
        val result = withContext(Dispatchers.IO) {
            EtoileBridgeConverter.convert(
                input = ConvertInput(
                    workspaceDir = root,
                    outputFile = currentRun.resolve("output").apply { mkdirs() },
                    manualMetadata = manualMetadata,
                    resourceOverrides = manualOverrides,
                    chartOverrides = manualChartOverrides,
                    packageOptions = packageOptions,
                    appearanceOptions = appearanceOptions,
                ),
                options = state.value.options.toCoreOptions(),
            )
        }
        when (result) {
            is ConvertResult.Success -> {
                pendingOutputFile = result.outputFile
                _state.update {
                    it.copy(
                        isConverting = false,
                        songId = result.songId,
                        outputFileName = result.outputFile.name,
                        pendingOutputFile = result.outputFile,
                        pendingOutputFileSize = result.outputFile.length(),
                        saveStatus = UiSaveStatus.Pending,
                        warnings = result.warnings,
                        logs = it.logs + result.logs + listOf(
                            "Packed to cache: ${result.outputFile.absolutePath}",
                            "Conversion finished. Waiting for save.",
                        ),
                        errorMessage = null,
                        errorDetails = null,
                        missingMetadata = null,
                        canConvert = true,
                        canSave = true,
                        canSaveDownloads = canUseDownloads(),
                    )
                }
            }
            is ConvertResult.NeedMetadata -> {
                _state.update {
                    it.copy(
                        isConverting = false,
                        missingMetadata = result.toUiMissingMetadata(root),
                        logs = it.logs + "Conversion needs metadata.",
                        canSave = false,
                        canSaveDownloads = false,
                    )
                }
            }
            is ConvertResult.UnsupportedPackStructure -> {
                _state.update {
                    it.copy(
                        isConverting = false,
                        unsupportedPackStructure = true,
                        unsupportedPackMessage = result.message,
                        candidateSongIds = result.candidateSongIds,
                        errorMessage = "检测到曲包结构，请等待后续“曲包转换”页面支持。",
                        errorDetails = result.candidateSongIds.joinToString(prefix = "candidateSongIds: "),
                        canConvert = false,
                        canSave = false,
                        canSaveDownloads = false,
                    )
                }
            }
            is ConvertResult.Failed -> {
                _state.update {
                    it.copy(
                        isConverting = false,
                        warnings = result.warnings,
                        logs = it.logs + result.logs,
                        errorMessage = result.message,
                        errorDetails = result.cause?.stackTraceToString(),
                        canSave = false,
                        canSaveDownloads = false,
                    )
                }
            }
        }
    }

    fun saveOutputToDownloads() {
        val packageFile = pendingOutputFile ?: return
        if (!canUseDownloads()) {
            _state.update {
                SaveStateTransitions.afterSaveFailure(
                    state = it,
                    message = "当前系统请使用另存为保存文件",
                    details = null,
                    canUseDownloads = false,
                    logLine = "Downloads is unavailable on SDK ${Build.VERSION.SDK_INT}; pending output is kept.",
                )
            }
            return
        }
        val fileName = suggestedOutputFileName()
        savePendingOutput("Downloads") {
            AndroidFileBridge.saveToDownloads(app, packageFile, fileName)
        }
    }

    fun saveOutputTo(uri: Uri) {
        val packageFile = pendingOutputFile ?: return
        savePendingOutput("SAF") {
            AndroidFileBridge.writeFileToUri(app, packageFile, uri)
        }
    }

    fun onSaveAsCanceled() {
        if (pendingOutputFile == null) return
        _state.update {
            it.copy(
                saveStatus = UiSaveStatus.Canceled,
                errorMessage = "用户取消保存",
                errorDetails = null,
                canSave = true,
                canSaveDownloads = canUseDownloads(),
                logs = it.logs + "User canceled save; pending output is kept.",
            )
        }
    }

    fun clearCache() = guardedLaunch {
        if (pendingOutputFile != null) {
            setError("请先保存当前包，再清理缓存。")
            return@guardedLaunch
        }
        withContext(Dispatchers.IO) { CacheCleaner.cleanAll(app.cacheDir) }
        runDir = null
        projectRoot = null
        songRoot = null
        pendingOutputFile = null
        manualOverrides = ManualResourceOverrides()
        manualMetadata = null
        manualChartOverrides = ManualChartOverrides()
        packageOptions = PackageOptions()
        appearanceOptions = AppearanceOptions()
        appearanceEdited = false
        _state.update {
            it.copy(
                workspacePath = null,
                projectRootPath = null,
                songRootPath = null,
                pendingOutputFile = null,
                pendingOutputFileSize = null,
                saveStatus = UiSaveStatus.NotSaved,
                savedMethod = null,
                savedFileName = null,
                savedLocation = null,
                savedFileSize = null,
                workspaceCleaned = false,
                canScan = false,
                canConvert = false,
                canSave = false,
                canSaveDownloads = false,
                manualResources = UiManualResources(),
                appearanceOptions = UiAppearanceOptions(),
                metadataDraft = UiMetadataDraft(),
                affMappings = emptyList(),
                unsupportedPackStructure = false,
                unsupportedPackMessage = null,
                candidateSongIds = emptyList(),
                logs = it.logs + "Cleared EtoileBridge cache.",
            )
        }
    }

    fun updateOptions(options: UiConvertOptions) {
        _state.update { it.copy(options = options) }
    }

    fun updateAppearanceOptions(options: UiAppearanceOptions) {
        appearanceEdited = true
        appearanceOptions = options.toCoreAppearanceOptions()
        _state.update {
            it.copy(
                appearanceOptions = options,
                logs = it.logs + "ArcCreate appearance options updated.",
            )
        }
    }

    fun saveMetadataDraft(draft: UiMetadataDraft) = guardedLaunch {
        manualMetadata = draft.toManualMetadata()
        packageOptions = draft.toPackageOptions()
        _state.update {
            it.copy(
                metadataDraft = draft,
                logs = it.logs + "Manual metadata saved.",
                errorMessage = null,
                errorDetails = null,
            )
        }
        scanInternal()
    }

    fun saveAffMappings(mappings: List<UiAffMappingItem>) = guardedLaunch {
        val root = projectRoot ?: return@guardedLaunch
        val adopted = mappings
            .mapNotNull { item ->
                val ratingClass = item.mappedRatingClass ?: return@mapNotNull null
                ratingClass to root.resolve(item.filePath)
            }
            .toMap()
        manualChartOverrides = ManualChartOverrides(adoptedAffByRatingClass = adopted)
        _state.update {
            it.copy(
                affMappings = buildAffMappingItems(root, songRoot, manualChartOverrides),
                logs = it.logs + "Manual AFF mapping saved.",
                errorMessage = null,
                errorDetails = null,
            )
        }
        scanInternal()
    }

    fun updateLanguage(language: UiLanguage) {
        prefs.edit().putString(PREF_LANGUAGE, language.name).apply()
        _state.update { it.copy(language = language) }
    }

    fun reportExternalError(message: String, throwable: Throwable) {
        showError(message, throwable)
    }

    fun suggestedOutputFileName(): String =
        state.value.outputFileName ?: state.value.songId?.let { "$it.arcpkg" } ?: "song.arcpkg"

    private suspend fun scanInternal() {
        val root = projectRoot ?: run {
            setError("没有可扫描的输入目录。")
            return
        }
        _state.update {
            it.copy(
                isScanning = true,
                scanStatus = UiScanStatus.Scanning,
                errorMessage = null,
                errorDetails = null,
                warnings = emptyList(),
            )
        }
        val result = withContext(Dispatchers.IO) { scanWorkspace(root) }
        songRoot = result.songRoot
        val inferredAppearance = if (!appearanceEdited) {
            result.metadataDraft.side.toIntOrNull()?.toUiAppearanceOptions()
        } else {
            null
        }
        _state.update {
            it.copy(
                isScanning = false,
                scanStatus = if (result.errorMessage == null && result.missingMetadata == null) {
                    UiScanStatus.Scanned
                } else if (result.missingMetadata != null || result.unsupportedPackStructure) {
                    UiScanStatus.Scanned
                } else {
                    UiScanStatus.Failed
                },
                songId = result.songId,
                affDifficulties = result.affDifficulties,
                affMappings = result.affMappings,
                adoptedAffFiles = result.adoptedAffFiles,
                ignoredAffFiles = result.ignoredAffFiles,
                resourceStatus = result.resourceStatus,
                outputFileName = result.songId?.let { songId -> "$songId.arcpkg" },
                songRootPath = result.songRoot?.absolutePath,
                missingMetadata = result.missingMetadata,
                metadataDraft = result.metadataDraft,
                appearanceOptions = inferredAppearance ?: it.appearanceOptions,
                unsupportedPackStructure = result.unsupportedPackStructure,
                unsupportedPackMessage = result.unsupportedPackMessage,
                candidateSongIds = result.candidateSongIds,
                warnings = result.warnings,
                logs = it.logs + result.logs,
                errorMessage = result.errorMessage,
                errorDetails = result.errorDetails,
                canScan = true,
                canConvert = result.canConvert,
                canSave = pendingOutputFile != null,
                canSaveDownloads = pendingOutputFile != null && canUseDownloads(),
            )
        }
    }

    private fun scanWorkspace(root: File): ScanResult {
        val logs = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        return try {
            val scanned = InputScanner().scan(root)
            val ignoredAff = scanned.ignoredAffFiles.map { it.relativeToOrSelf(root) }
            ignoredAff.forEach { warnings += "Ignored non-standard AFF file: $it" }
            logs += "Scanned input as ${scanned.kind}."
            if (scanned.rootAffFiles.isEmpty() && scanned.songDirectories.isEmpty() && scanned.ignoredAffFiles.isEmpty()) {
                return ScanResult(
                    ignoredAffFiles = ignoredAff,
                    resourceStatus = UiResourceStatus(resourceFiles = root.resourceFiles() + manualResourcePaths()),
                    warnings = warnings,
                    logs = logs,
                    canConvert = false,
                    errorMessage = "未找到标准难度 AFF。",
                )
            }
            val rootSonglistFile = manualOverrides.songlistFile?.takeIf { it.isFile } ?: scanned.songlistFile
            val rootSonglist = parseSonglistForScan(rootSonglistFile, root, warnings, logs)
            val target = resolveTarget(scanned, rootSonglist)
            val resourceFiles = scanned.resourceFiles() + manualResourcePaths()
            if (target == null) {
                val isPack = scanned.candidateSongIds.size > 1
                val missing = MissingMetadata(
                    reason = "Directory structure needs song selection or metadata.",
                    requiredFields = listOf("targetSongId"),
                    candidateSongIds = scanned.candidateSongIds,
                )
                return ScanResult(
                    missingMetadata = if (isPack) null else missing.toUi(scanned, root),
                    ignoredAffFiles = ignoredAff,
                    affMappings = buildAffMappingItems(root, null, manualChartOverrides),
                    adoptedAffFiles = emptyList(),
                    resourceStatus = UiResourceStatus(resourceFiles = resourceFiles),
                    warnings = warnings,
                    logs = logs,
                    canConvert = false,
                    unsupportedPackStructure = isPack,
                    unsupportedPackMessage = if (isPack) "检测到曲包结构，请等待后续“曲包转换”页面支持。" else null,
                    candidateSongIds = scanned.candidateSongIds,
                    errorMessage = if (isPack) "检测到曲包结构，请等待后续“曲包转换”页面支持。" else "Input structure needs metadata or song selection.",
                )
            }

            val affFiles = applyUiChartOverrides(target.affFiles, root, warnings)
            val adoptedAff = affFiles.values.map { it.relativeToOrSelf(root) }.sorted()
            val affDifficulties = affFiles.toAffDifficulties()
            val affMappings = buildAffMappingItems(root, target.songDir, manualChartOverrides)
            if (affFiles.isEmpty()) {
                return ScanResult(
                    songRoot = target.songDir,
                    affMappings = affMappings,
                    ignoredAffFiles = ignoredAff,
                    adoptedAffFiles = adoptedAff,
                    resourceStatus = UiResourceStatus(resourceFiles = resourceFiles),
                    metadataDraft = buildMetadataDraft(null, target, affFiles),
                    warnings = warnings,
                    logs = logs,
                    canConvert = false,
                    errorMessage = "未找到标准难度 AFF，请在 AFF 映射中指定一个谱面。",
                )
            }
            if (manualMetadata != null && manualMetadata?.songId.isNullOrBlank()) {
                return ScanResult(
                    songRoot = target.songDir,
                    affMappings = affMappings,
                    affDifficulties = affDifficulties,
                    adoptedAffFiles = adoptedAff,
                    ignoredAffFiles = ignoredAff,
                    resourceStatus = resolveResourceStatusForScan(root, target.copy(affFiles = affFiles), resourceFiles, warnings),
                    metadataDraft = buildMetadataDraft(null, target, affFiles),
                    warnings = warnings,
                    logs = logs,
                    canConvert = false,
                    errorMessage = "songId 不能为空。",
                )
            }

            val songlistFile = selectSonglistFile(target.songDir, root, scanned)
            val songlist = if (songlistFile?.canonicalPathSafe() == rootSonglistFile?.canonicalPathSafe()) {
                rootSonglist
            } else {
                parseSonglistForScan(songlistFile, root, warnings, logs)
            }
            val packlistFile = manualOverrides.packlistFile?.takeIf { it.isFile }
                ?: InputScanner.findPacklistFile(target.songDir)
                ?: scanned.packlistFile
            val packlist = packlistFile?.let { PacklistParser().parse(it) }
            val metadata = MetadataResolver().resolve(
                songDir = target.songDir,
                affFiles = affFiles,
                requestedSongId = target.songId,
                songlist = songlist,
                packlist = packlist,
                manualMetadata = manualMetadata,
                warnings = warnings,
            )
            when (metadata) {
                is MetadataResolution.Need -> ScanResult(
                    songRoot = target.songDir,
                    missingMetadata = metadata.missingMetadata.toUi(scanned, root),
                    affDifficulties = affDifficulties,
                    affMappings = affMappings,
                    adoptedAffFiles = adoptedAff,
                    ignoredAffFiles = ignoredAff,
                    metadataDraft = buildMetadataDraftFromSonglist(songlist, target, affFiles),
                    resourceStatus = resolveResourceStatusForScan(root, target.copy(affFiles = affFiles), resourceFiles, warnings),
                    warnings = warnings,
                    logs = logs + "Metadata is missing; scanned available resources.",
                    canConvert = false,
                    errorMessage = null,
                )
                is MetadataResolution.Resolved -> {
                    val resolvedSong = ResourceResolver().resolve(
                        workspaceDir = root,
                        songDir = target.songDir,
                        affFiles = affFiles,
                        metadata = metadata.metadata,
                        warnings = warnings,
                        overrides = manualOverrides,
                    )
                    val audioFiles = resolvedSong.difficulties.map { it.audioFile }.distinctBy { it.canonicalPathSafe() }
                    val jacketFiles = resolvedSong.difficulties.mapNotNull { it.jacketFile }.distinctBy { it.canonicalPathSafe() }
                    val backgroundFiles = resolvedSong.difficulties.mapNotNull { it.backgroundFile }.distinctBy { it.canonicalPathSafe() }
                    addMissingOptionalResourceWarnings(jacketFiles.map { it.name }, backgroundFiles.map { it.name }, warnings)
                    ScanResult(
                        songId = metadata.metadata.songId,
                        songRoot = target.songDir,
                        affDifficulties = affDifficulties,
                        affMappings = affMappings,
                        adoptedAffFiles = adoptedAff,
                        ignoredAffFiles = ignoredAff,
                        metadataDraft = buildMetadataDraft(metadata.metadata, target, affFiles),
                        resourceStatus = UiResourceStatus(
                            audioFileName = audioFiles.map { it.name }.joinToStringOrNull(),
                            audioFilePath = audioFiles.firstOrNull()?.absolutePath,
                            jacketFileName = jacketFiles.map { it.name }.joinToStringOrNull(),
                            jacketFilePath = jacketFiles.firstOrNull()?.absolutePath,
                            backgroundFileName = backgroundFiles.map { it.name }.joinToStringOrNull(),
                            backgroundFilePath = backgroundFiles.firstOrNull()?.absolutePath,
                            audioManual = manualOverrides.audioFile != null,
                            jacketManual = manualOverrides.jacketFile != null,
                            backgroundManual = manualOverrides.backgroundFile != null,
                            resourceFiles = resourceFiles,
                        ),
                        warnings = warnings,
                        logs = logs + "Resolved resources with converter-core ResourceResolver.",
                        canConvert = true,
                    )
                }
            }
        } catch (error: MissingRequiredResourceException) {
            val shortMessage = when {
                error.message?.contains("audio", ignoreCase = true) == true -> "缺少音频，请手动选择音频。"
                error.message?.contains(".aff", ignoreCase = true) == true -> "未找到标准难度 AFF。"
                else -> error.message ?: "缺少必要资源。"
            }
            ScanResult(
                resourceStatus = UiResourceStatus(resourceFiles = root.resourceFiles() + manualResourcePaths()),
                warnings = warnings,
                logs = logs,
                errorMessage = shortMessage,
                errorDetails = error.stackTraceToString(),
                canConvert = false,
            )
        } catch (error: Exception) {
            ScanResult(
                resourceStatus = UiResourceStatus(resourceFiles = root.resourceFiles() + manualResourcePaths()),
                warnings = warnings,
                logs = logs,
                errorMessage = "Scan failed.",
                errorDetails = error.stackTraceToString(),
                canConvert = false,
            )
        }
    }

    private fun addMissingOptionalResourceWarnings(
        jacketNames: List<String>,
        backgroundNames: List<String>,
        warnings: MutableList<String>,
    ) {
        if (jacketNames.isEmpty() && warnings.none { it.contains("曲绘未识别") }) {
            warnings += "曲绘未识别，可手动选择。"
        }
        if (backgroundNames.isEmpty() && warnings.none { it.contains("背景未识别") }) {
            warnings += "背景未识别，可手动选择。"
        }
    }

    private fun selectSonglistFile(songDir: File, root: File, scanned: ScannedInput): File? =
        manualOverrides.songlistFile?.takeIf { it.isFile }
            ?: InputScanner.findSonglistFile(songDir)
            ?: InputScanner.findSonglistFile(root)
            ?: scanned.songlistFile

    private fun parseSonglistForScan(
        file: File?,
        root: File,
        warnings: MutableList<String>,
        logs: MutableList<String>,
    ): Songlist? {
        if (file == null) return null
        return try {
            val songlist = SonglistParser().parse(file)
            logs += "Parsed songlist/slst: ${file.relativeToOrSelf(root)}"
            songlist
        } catch (error: Exception) {
            warnings += "Songlist/slst parse failed for ${file.relativeToOrSelf(root)}: ${error.message}"
            null
        }
    }

    private fun resolveTarget(scanned: ScannedInput, songlist: Songlist?): TargetSong? {
        val songlistSongId = songlist?.songs.orEmpty()
            .firstOrNull { it.deleted != true }
            ?.id
            ?.takeIf { scanned.findSongDirectory(it) != null }
        return when (scanned.kind) {
            InputKind.SingleSong -> TargetSong(songlistSongId, scanned.workspaceDir, scanned.rootAffFiles)
            InputKind.PackFolder -> {
                val songId = songlistSongId ?: scanned.candidateSongIds.singleOrNull() ?: return null
                val matched = scanned.findSongDirectory(songId) ?: return null
                TargetSong(matched.first, matched.second, InputScanner.findAffFiles(matched.second))
            }
            InputKind.Unknown -> TargetSong(songlistSongId, scanned.workspaceDir, scanned.rootAffFiles)
                .takeIf { it.affFiles.isNotEmpty() }
        }
    }

    private fun resolveResourceStatusForScan(
        root: File,
        target: TargetSong,
        resourceFiles: List<String>,
        warnings: MutableList<String>,
    ): UiResourceStatus {
        val metadata = ResolvedSongMetadata(
            songId = target.songId ?: target.songDir.name,
            title = "",
            artist = "",
            bpmText = "",
            bpmBase = 0f,
            set = "",
            side = 0,
            bg = null,
            bgInverse = null,
            audioPreview = 0,
            audioPreviewEnd = 0,
            additionalFiles = emptyList(),
            pack = null,
            searchTags = "",
            difficulties = target.affFiles.keys.sorted().map { ratingClass ->
                ResolvedDifficultyMetadata(
                    ratingClass = ratingClass,
                    chartDesigner = "",
                    jacketDesigner = "",
                    rating = null,
                    ratingPlus = false,
                    jacketOverride = false,
                    audioOverride = false,
                    bg = null,
                    bgInverse = null,
                    title = null,
                    artist = null,
                    bpmText = null,
                    bpmBase = null,
                )
            },
        )
        return try {
            val resolvedSong = ResourceResolver().resolve(
                workspaceDir = root,
                songDir = target.songDir,
                affFiles = target.affFiles,
                metadata = metadata,
                warnings = warnings,
                overrides = manualOverrides,
            )
            val audioFiles = resolvedSong.difficulties.map { it.audioFile }.distinctBy { it.canonicalPathSafe() }
            val jacketFiles = resolvedSong.difficulties.mapNotNull { it.jacketFile }.distinctBy { it.canonicalPathSafe() }
            val backgroundFiles = resolvedSong.difficulties.mapNotNull { it.backgroundFile }.distinctBy { it.canonicalPathSafe() }
            addMissingOptionalResourceWarnings(jacketFiles.map { it.name }, backgroundFiles.map { it.name }, warnings)
            UiResourceStatus(
                audioFileName = audioFiles.map { it.name }.joinToStringOrNull(),
                audioFilePath = audioFiles.firstOrNull()?.absolutePath,
                jacketFileName = jacketFiles.map { it.name }.joinToStringOrNull(),
                jacketFilePath = jacketFiles.firstOrNull()?.absolutePath,
                backgroundFileName = backgroundFiles.map { it.name }.joinToStringOrNull(),
                backgroundFilePath = backgroundFiles.firstOrNull()?.absolutePath,
                audioManual = manualOverrides.audioFile != null,
                jacketManual = manualOverrides.jacketFile != null,
                backgroundManual = manualOverrides.backgroundFile != null,
                resourceFiles = resourceFiles,
            )
        } catch (error: MissingRequiredResourceException) {
            warnings += error.message ?: "Required resource missing."
            UiResourceStatus(resourceFiles = resourceFiles)
        }
    }

    private fun applyUiChartOverrides(
        affFiles: Map<Int, File>,
        root: File,
        warnings: MutableList<String>,
    ): Map<Int, File> {
        if (manualChartOverrides.adoptedAffByRatingClass.isEmpty()) return affFiles
        val result = affFiles.toMutableMap()
        manualChartOverrides.adoptedAffByRatingClass.forEach { (ratingClass, file) ->
            if (ratingClass !in 0..4) return@forEach
            if (file.isFile) {
                result[ratingClass] = file
            } else {
                warnings += "Manual AFF mapping file not found: ${file.relativeToOrSelf(root)}"
            }
        }
        return result.toSortedMap()
    }

    private fun buildAffMappingItems(
        root: File,
        targetSongDir: File?,
        overrides: ManualChartOverrides,
    ): List<UiAffMappingItem> {
        val searchDir = targetSongDir ?: root
        val files = searchDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("aff", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        val overrideByFile = overrides.adoptedAffByRatingClass.entries.associate { (ratingClass, file) ->
            file.canonicalPathSafe() to ratingClass
        }
        val mappedCounts = files.mapNotNull { file ->
            overrideByFile[file.canonicalPathSafe()] ?: DifficultyMapper.fromAffFile(file)?.ratingClass
        }.groupingBy { it }.eachCount()
        return files.map { file ->
            val detected = DifficultyMapper.fromAffFile(file)?.ratingClass
            val mapped = overrideByFile[file.canonicalPathSafe()] ?: detected
            UiAffMappingItem(
                filePath = file.relativeToOrSelf(root),
                fileName = file.name,
                detectedRatingClass = detected,
                mappedRatingClass = mapped,
                adopted = mapped != null,
                manual = overrideByFile.containsKey(file.canonicalPathSafe()),
                conflict = mapped != null && mappedCounts.getOrDefault(mapped, 0) > 1,
            )
        }
    }

    private fun buildMetadataDraft(
        metadata: ResolvedSongMetadata?,
        target: TargetSong,
        affFiles: Map<Int, File>,
    ): UiMetadataDraft {
        manualMetadata?.let { return it.toUiDraft(target, affFiles) }
        val inferredSongId = metadata?.songId ?: target.songId ?: target.songDir.name
        return UiMetadataDraft(
            songId = inferredSongId,
            title = metadata?.title?.takeIf { it.isNotBlank() } ?: inferredSongId,
            artist = metadata?.artist.orEmpty(),
            bpmText = metadata?.bpmText.orEmpty(),
            baseBpm = metadata?.bpmBase?.takeIf { it > 0f }?.toString().orEmpty(),
            side = metadata?.side?.toString() ?: "0",
            publisherId = packageOptions.publisherId.ifBlank { "etoilebridge" },
            levelId = packageOptions.levelId ?: inferredSongId,
            identifierOverride = packageOptions.identifier.orEmpty(),
            difficulties = affFiles.entries.sortedBy { it.key }.map { (ratingClass, file) ->
                val diff = metadata?.difficulties?.firstOrNull { it.ratingClass == ratingClass }
                UiDifficultyDraft(
                    ratingClass = ratingClass,
                    affFileName = file.name,
                    difficulty = diff?.difficulty ?: DifficultyMapper.displayName(ratingClass, diff?.rating, diff?.ratingPlus),
                    chartConstant = diff?.chartConstant?.toString().orEmpty(),
                    chartDesigner = diff?.chartDesigner.orEmpty(),
                    jacketDesigner = diff?.jacketDesigner.orEmpty(),
                )
            },
        )
    }

    private fun buildMetadataDraftFromSonglist(
        songlist: Songlist?,
        target: TargetSong,
        affFiles: Map<Int, File>,
    ): UiMetadataDraft {
        manualMetadata?.let { return it.toUiDraft(target, affFiles) }
        val song = MetadataResolver().matchSong(target.songDir, target.songId, songlist).song
        val inferredSongId = target.songId ?: song?.id ?: target.songDir.name
        return UiMetadataDraft(
            songId = inferredSongId,
            title = song.preferredTitle() ?: inferredSongId,
            artist = song?.artist ?: song?.composer.orEmpty(),
            bpmText = song?.bpmText.orEmpty(),
            baseBpm = song?.bpmBase?.takeIf { it > 0f }?.toString().orEmpty(),
            side = song?.side?.toString() ?: "0",
            publisherId = packageOptions.publisherId.ifBlank { "etoilebridge" },
            levelId = packageOptions.levelId ?: inferredSongId,
            identifierOverride = packageOptions.identifier.orEmpty(),
            difficulties = affFiles.entries.sortedBy { it.key }.map { (ratingClass, file) ->
                val source = song?.difficulties?.firstOrNull { it.ratingClass == ratingClass }
                UiDifficultyDraft(
                    ratingClass = ratingClass,
                    affFileName = file.name,
                    difficulty = source?.rating?.let { DifficultyMapper.displayName(ratingClass, it, source.ratingPlus) }
                        ?: DifficultyMapper.labelFor(ratingClass),
                    chartConstant = DifficultyMapper.chartConstant(source?.rating, source?.ratingPlus)?.toString().orEmpty(),
                    chartDesigner = source?.chartDesigner.orEmpty(),
                    jacketDesigner = source?.jacketDesigner.orEmpty(),
                )
            },
        )
    }

    private fun normalizeArchiveRoot(archiveDir: File): File {
        var current = archiveDir
        while (true) {
            val hasRootMarkers = current.resolve("songlist").isFile ||
                current.resolve("songlist.json").isFile ||
                current.resolve("songlist.txt").isFile ||
                current.resolve("slst").isFile ||
                current.resolve("slst.json").isFile ||
                current.resolve("slst.txt").isFile ||
                current.resolve("packlist").isFile ||
                current.resolve("packlist.json").isFile ||
                current.resolve("packlist.txt").isFile ||
                InputScanner.findAffFiles(current).isNotEmpty()
            if (hasRootMarkers) return current
            current.resolve("assets").resolve("songs")
                .takeIf { it.isDirectory && hasChildSongDirs(it) }
                ?.let { return it }
            val childDirs = current.listFiles()?.filter { it.isDirectory && it.name != "__MACOSX" }.orEmpty()
            val meaningfulFiles = current.listFiles()
                ?.filter { it.isFile && it.name != ".DS_Store" }
                .orEmpty()
            if (childDirs.size != 1 || meaningfulFiles.isNotEmpty()) return current
            current = childDirs.single()
        }
    }

    private fun hasChildSongDirs(dir: File): Boolean =
        dir.listFiles()?.any { it.isDirectory && InputScanner.findAffFiles(it).isNotEmpty() } == true

    private fun savePendingOutput(kind: String, save: () -> SaveResult) {
        viewModelScope.launch(crashGuard) {
            val packageFile = pendingOutputFile ?: return@launch
            _state.update {
                it.copy(
                    isSaving = true,
                    saveStatus = UiSaveStatus.Saving,
                    errorMessage = null,
                    errorDetails = null,
                    canSave = false,
                    canSaveDownloads = false,
                    logs = it.logs + "Saving ${packageFile.name} via $kind.",
                )
            }
            runCatching {
                withContext(Dispatchers.IO) { save() }
            }.onSuccess { result ->
                withContext(Dispatchers.IO) { CacheCleaner.deleteRun(runDir) }
                runDir = null
                projectRoot = null
                songRoot = null
                pendingOutputFile = null
                manualOverrides = ManualResourceOverrides()
                manualMetadata = null
                manualChartOverrides = ManualChartOverrides()
                val saveLine = if (kind == "Downloads") {
                    "Saved to Downloads: ${result.location}"
                } else {
                    "Saved via SAF: ${result.location}"
                }
                _state.update {
                    it.copy(
                        isSaving = false,
                        pendingOutputFile = null,
                        pendingOutputFileSize = null,
                        saveStatus = UiSaveStatus.Saved,
                        savedMethod = kind,
                        savedFileName = packageFile.name,
                        savedLocation = result.location,
                        savedFileSize = result.expectedBytes,
                        workspaceCleaned = true,
                        canScan = false,
                        canConvert = false,
                        canSave = false,
                        canSaveDownloads = false,
                        workspacePath = null,
                        projectRootPath = null,
                        songRootPath = null,
                        manualResources = UiManualResources(),
                        logs = it.logs + listOf(
                            saveLine,
                            "Verified saved file size: ${result.expectedBytes} bytes",
                            "Cleaned workspace",
                        ),
                    )
                }
            }.onFailure { error ->
                val message = when {
                    error is DownloadsRequiresCreateDocumentException -> "当前系统请使用另存为保存文件"
                    kind == "Downloads" -> "保存到 Downloads 失败，请改用另存为"
                    else -> "保存失败，文件仍保留在待保存状态"
                }
                _state.update {
                    SaveStateTransitions.afterSaveFailure(
                        state = it,
                        message = message,
                        details = error.stackTraceToString(),
                        canUseDownloads = canUseDownloads(),
                        logLine = "Save failed; pending output is kept: ${error.message}",
                    )
                }
            }
        }
    }

    private fun resetForInput(type: UiInputType, name: String) {
        _state.value = baseState(state.value.language).copy(
            inputType = type,
            inputName = name,
            extractStatus = if (type == UiInputType.Zip) UiExtractStatus.Extracting else UiExtractStatus.NotExtracted,
        )
    }

    private fun createFreshRun() {
        CacheCleaner.deleteRun(runDir)
        runDir = CacheCleaner.createRunDir(app.cacheDir)
        projectRoot = null
        songRoot = null
        pendingOutputFile = null
        manualOverrides = ManualResourceOverrides()
        manualMetadata = null
        manualChartOverrides = ManualChartOverrides()
        packageOptions = PackageOptions()
        appearanceOptions = AppearanceOptions()
        appearanceEdited = false
    }

    private fun guardedLaunch(block: suspend () -> Unit) {
        viewModelScope.launch(crashGuard) {
            runCatching { block() }.onFailure { showError("操作失败", it) }
        }
    }

    private fun showError(message: String, throwable: Throwable) {
        _state.update {
            it.copy(
                isCopying = false,
                isScanning = false,
                isConverting = false,
                isSaving = false,
                extractStatus = if (it.inputType == UiInputType.Zip && it.extractStatus == UiExtractStatus.Extracting) {
                    UiExtractStatus.Failed
                } else {
                    it.extractStatus
                },
                scanStatus = if (it.isScanning) UiScanStatus.Failed else it.scanStatus,
                canScan = projectRoot != null,
                canConvert = false,
                errorMessage = if (it.inputType == UiInputType.Zip && it.extractStatus == UiExtractStatus.Extracting) {
                    "ZIP 解压失败"
                } else {
                    message
                },
                errorDetails = throwable.stackTraceToString(),
                logs = it.logs + "${message}: ${throwable.message}",
            )
        }
    }

    private fun setError(message: String) {
        _state.update { it.copy(errorMessage = message, logs = it.logs + message) }
    }

    private fun MissingMetadata.toUi(scanned: ScannedInput, root: File): UiMissingMetadata =
        UiMissingMetadata(
            reason = reason,
            requiredFields = requiredFields,
            optionalFields = optionalFields,
            candidateSongIds = candidateSongIds,
            affFiles = scanned.affFiles(root),
            resourceFiles = scanned.resourceFiles() + manualResourcePaths(),
        )

    private fun ConvertResult.NeedMetadata.toUiMissingMetadata(root: File): UiMissingMetadata =
        missingMetadata.toUi(scannedInput, root)

    private fun UiConvertOptions.toCoreOptions(): ConvertOptions =
        ConvertOptions(
            enableDeleteDesignantLine = enableDeleteDesignantLine,
            enableFixZeroDurationArcTap = enableFixZeroDurationArcTap,
            enableFixReversedArcTime = enableFixReversedArcTime,
            enableExpandArcResolution = enableExpandArcResolution,
            keepWorkspaceOnFailure = true,
            cleanWorkspaceOnSuccess = true,
        )

    private fun ManualResourceOverrides.toUiManualResources(): UiManualResources =
        UiManualResources(
            audioFileName = audioFile?.name,
            jacketFileName = jacketFile?.name,
            backgroundFileName = backgroundFile?.name,
            songlistFileName = songlistFile?.name,
            packlistFileName = packlistFile?.name,
        )

    private fun UiMetadataDraft.toManualMetadata(): ManualMetadata =
        ManualMetadata(
            songId = songId.trim().takeIf { it.isNotBlank() },
            title = title.trim().takeIf { it.isNotBlank() },
            artist = artist.trim().takeIf { it.isNotBlank() },
            bpmText = bpmText.trim().takeIf { it.isNotBlank() },
            bpmBase = baseBpm.trim().toFloatOrNull(),
            side = side.trim().toIntOrNull(),
            difficulties = difficulties.map { diff ->
                ManualDifficultyMetadata(
                    ratingClass = diff.ratingClass,
                    difficulty = diff.difficulty.trim().takeIf { it.isNotBlank() },
                    chartConstant = diff.chartConstant.trim().toFloatOrNull(),
                    chartDesigner = diff.chartDesigner.trim().takeIf { it.isNotBlank() },
                    jacketDesigner = diff.jacketDesigner.trim().takeIf { it.isNotBlank() },
                )
            },
        )

    private fun ManualMetadata.toUiDraft(target: TargetSong, affFiles: Map<Int, File>): UiMetadataDraft =
        UiMetadataDraft(
            songId = songId.orEmpty(),
            title = title.orEmpty().ifBlank { songId ?: target.songDir.name },
            artist = artist.orEmpty(),
            bpmText = bpmText.orEmpty(),
            baseBpm = bpmBase?.toString().orEmpty(),
            side = side?.toString() ?: "0",
            publisherId = packageOptions.publisherId.ifBlank { "etoilebridge" },
            levelId = packageOptions.levelId ?: (songId ?: target.songDir.name),
            identifierOverride = packageOptions.identifier.orEmpty(),
            difficulties = affFiles.entries.sortedBy { it.key }.map { (ratingClass, file) ->
                val diff = difficulties.firstOrNull { it.ratingClass == ratingClass }
                UiDifficultyDraft(
                    ratingClass = ratingClass,
                    affFileName = file.name,
                    difficulty = diff?.difficulty ?: DifficultyMapper.displayName(ratingClass, diff?.rating, diff?.ratingPlus),
                    chartConstant = diff?.chartConstant?.toString().orEmpty(),
                    chartDesigner = diff?.chartDesigner.orEmpty(),
                    jacketDesigner = diff?.jacketDesigner.orEmpty(),
                )
            },
        )

    private fun UiMetadataDraft.toPackageOptions(): PackageOptions =
        PackageOptions(
            publisherId = publisherId.trim().ifBlank { "etoilebridge" },
            levelId = levelId.trim().ifBlank { songId.trim() },
            identifier = identifierOverride.trim().takeIf { it.isNotBlank() },
        )

    private fun Int.toUiAppearanceOptions(): UiAppearanceOptions =
        when (this) {
            1 -> UiAppearanceOptions(side = UiArcCreateSide.Conflict)
            2 -> UiAppearanceOptions(side = UiArcCreateSide.Colorless)
            3 -> UiAppearanceOptions(side = UiArcCreateSide.Light, sideInferredFromLephon = true)
            else -> UiAppearanceOptions(side = UiArcCreateSide.Light)
        }

    private fun UiAppearanceOptions.toCoreAppearanceOptions(): AppearanceOptions =
        AppearanceOptions(
            side = side.toCoreSide(),
            note = note.toCoreNote(),
            particle = particle.toCoreParticle(),
            accent = accent.toCoreAccent(),
            track = track.toCoreTrack(),
            singleLine = singleLine.toCoreSingleLine(),
        )

    private fun UiArcCreateSide.toCoreSide(): ArcCreateSide =
        when (this) {
            UiArcCreateSide.Light -> ArcCreateSide.LIGHT
            UiArcCreateSide.Conflict -> ArcCreateSide.CONFLICT
            UiArcCreateSide.Colorless -> ArcCreateSide.COLORLESS
        }

    private fun UiArcCreateNote.toCoreNote(): ArcCreateNote =
        when (this) {
            UiArcCreateNote.Inherit -> ArcCreateNote.INHERIT
            UiArcCreateNote.Light -> ArcCreateNote.LIGHT
            UiArcCreateNote.Conflict -> ArcCreateNote.CONFLICT
        }

    private fun UiArcCreateParticle.toCoreParticle(): ArcCreateParticle =
        when (this) {
            UiArcCreateParticle.Inherit -> ArcCreateParticle.INHERIT
            UiArcCreateParticle.Light -> ArcCreateParticle.LIGHT
            UiArcCreateParticle.Conflict -> ArcCreateParticle.CONFLICT
            UiArcCreateParticle.MiraiLight -> ArcCreateParticle.MIRAI_LIGHT
            UiArcCreateParticle.MiraiConflict -> ArcCreateParticle.MIRAI_CONFLICT
            UiArcCreateParticle.Colorless -> ArcCreateParticle.COLORLESS
        }

    private fun UiArcCreateAccent.toCoreAccent(): ArcCreateAccent =
        when (this) {
            UiArcCreateAccent.Inherit -> ArcCreateAccent.INHERIT
            UiArcCreateAccent.Light -> ArcCreateAccent.LIGHT
            UiArcCreateAccent.Conflict -> ArcCreateAccent.CONFLICT
            UiArcCreateAccent.Dynamix -> ArcCreateAccent.DYNAMIX
            UiArcCreateAccent.Colorless -> ArcCreateAccent.COLORLESS
        }

    private fun UiArcCreateTrack.toCoreTrack(): ArcCreateTrack =
        when (this) {
            UiArcCreateTrack.Inherit -> ArcCreateTrack.INHERIT
            UiArcCreateTrack.Light -> ArcCreateTrack.LIGHT
            UiArcCreateTrack.Conflict -> ArcCreateTrack.CONFLICT
            UiArcCreateTrack.Black -> ArcCreateTrack.BLACK
            UiArcCreateTrack.Nijuusei -> ArcCreateTrack.NIJUUSEI
            UiArcCreateTrack.Rei -> ArcCreateTrack.REI
            UiArcCreateTrack.DarkVs -> ArcCreateTrack.DARK_VS
            UiArcCreateTrack.Tempest -> ArcCreateTrack.TEMPEST
            UiArcCreateTrack.Finale -> ArcCreateTrack.FINALE
            UiArcCreateTrack.Pentiment -> ArcCreateTrack.PENTIMENT
            UiArcCreateTrack.Arcana -> ArcCreateTrack.ARCANA
            UiArcCreateTrack.Colorless -> ArcCreateTrack.COLORLESS
        }

    private fun UiArcCreateSingleLine.toCoreSingleLine(): ArcCreateSingleLine =
        when (this) {
            UiArcCreateSingleLine.None -> ArcCreateSingleLine.NONE
            UiArcCreateSingleLine.Light -> ArcCreateSingleLine.LIGHT
            UiArcCreateSingleLine.Conflict -> ArcCreateSingleLine.CONFLICT
            UiArcCreateSingleLine.Neo -> ArcCreateSingleLine.NEO
        }

    private fun manualResourcePaths(): List<String> =
        listOfNotNull(
            manualOverrides.audioFile,
            manualOverrides.jacketFile,
            manualOverrides.backgroundFile,
            manualOverrides.songlistFile,
            manualOverrides.packlistFile,
        ).map { "manual_resources/${it.name}" }

    private fun Map<Int, File>.toAffDifficulties(): List<String> =
        entries.sortedBy { it.key }.map { (ratingClass, file) ->
            "${file.name} - ${DifficultyMapper.labelFor(ratingClass)}"
        }

    private fun ScannedInput.affFiles(root: File): List<String> =
        (rootAffFiles.values + songDirectories.values.flatMap { InputScanner.findAffFiles(it).values })
            .map { it.relativeToOrSelf(root) }
            .distinct()
            .sorted()

    private fun ScannedInput.resourceFiles(): List<String> = workspaceDir.resourceFiles()

    private fun File.resourceFiles(): List<String> {
        val extensions = setOf("aff", "ogg", "wav", "png", "jpg", "jpeg", "json", "yml", "yaml")
        return walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                file.name in setOf("songlist", "packlist", "songlist.txt", "packlist.txt", "slst", "slst.txt") ||
                    file.name.endsWith(".sc.json", ignoreCase = true) ||
                    file.extension.lowercase() in extensions
            }
            .map { it.relativeToOrSelf(this) }
            .toList()
            .sorted()
    }

    private fun File.relativeToOrSelf(base: File): String =
        runCatching { relativeTo(base).path }.getOrElse { name }

    private fun ScannedInput.findSongDirectory(songId: String): Pair<String, File>? {
        songDirectories[songId]?.let { return songId to it }
        return songDirectories.entries.firstOrNull { it.key.equals(songId, ignoreCase = true) }
            ?.let { it.key to it.value }
    }

    private fun com.zeerqi27.etoilebridge.core.SonglistSong?.preferredTitle(): String? =
        this?.titleLocalized?.let { titles ->
            titles["en"] ?: titles["ja"] ?: titles["zh-Hans"] ?: titles["zh-Hant"] ?: titles.values.firstOrNull()
        } ?: this?.title

    private fun File.canonicalPathSafe(): String =
        runCatching { canonicalPath }.getOrElse { absolutePath }

    private fun String.sanitizeFileName(): String =
        replace(Regex("""[\\/:*?"<>|]"""), "_")

    private fun List<String>.joinToStringOrNull(): String? =
        takeIf { it.isNotEmpty() }?.joinToString()

    private fun canUseDownloads(): Boolean =
        AndroidFileBridge.canUseMediaStoreDownloads(Build.VERSION.SDK_INT)

    private fun baseState(language: UiLanguage): UiConvertState =
        UiConvertState(
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

    private data class TargetSong(
        val songId: String?,
        val songDir: File,
        val affFiles: Map<Int, File>,
    )

    private data class ScanResult(
        val songId: String? = null,
        val songRoot: File? = null,
        val affDifficulties: List<String> = emptyList(),
        val affMappings: List<UiAffMappingItem> = emptyList(),
        val adoptedAffFiles: List<String> = emptyList(),
        val ignoredAffFiles: List<String> = emptyList(),
        val resourceStatus: UiResourceStatus = UiResourceStatus(),
        val missingMetadata: UiMissingMetadata? = null,
        val metadataDraft: UiMetadataDraft = UiMetadataDraft(),
        val unsupportedPackStructure: Boolean = false,
        val unsupportedPackMessage: String? = null,
        val candidateSongIds: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val logs: List<String> = emptyList(),
        val errorMessage: String? = null,
        val errorDetails: String? = null,
        val canConvert: Boolean = false,
    )

    companion object {
        private const val PREF_LANGUAGE = "language"
    }
}
