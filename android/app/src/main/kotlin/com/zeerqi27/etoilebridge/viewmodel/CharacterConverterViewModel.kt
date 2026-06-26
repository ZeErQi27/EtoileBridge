package com.zeerqi27.etoilebridge.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeerqi27.etoilebridge.core.CharacterPackageBuilder
import com.zeerqi27.etoilebridge.core.CharacterPackageInput
import com.zeerqi27.etoilebridge.core.CharacterPackageLoader
import com.zeerqi27.etoilebridge.core.CharacterPackageOptions
import com.zeerqi27.etoilebridge.core.CharacterPackageResult
import com.zeerqi27.etoilebridge.core.splitCharacterIdentifier
import com.zeerqi27.etoilebridge.file.AndroidFileBridge
import com.zeerqi27.etoilebridge.file.CacheCleaner
import com.zeerqi27.etoilebridge.file.SaveResult
import com.zeerqi27.etoilebridge.file.ZipArchiveExtractor
import com.zeerqi27.etoilebridge.model.CharacterStateRules
import com.zeerqi27.etoilebridge.model.UiCharacterInputType
import com.zeerqi27.etoilebridge.model.UiCharacterState
import com.zeerqi27.etoilebridge.model.UiLanguage
import com.zeerqi27.etoilebridge.model.UiSaveStatus
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CharacterConverterViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("etoilebridge_ui", Application.MODE_PRIVATE)
    private val _state = MutableStateFlow(baseState(loadLanguage()))
    val state: StateFlow<UiCharacterState> = _state.asStateFlow()

    private var runDir: File? = null
    private var imageFile: File? = null
    private var iconFile: File? = null
    private var pendingOutputFile: File? = null

    private val crashGuard = CoroutineExceptionHandler { _, throwable ->
        showError(throwable.message ?: "Character operation failed.", throwable)
    }

    fun updateLanguage(language: UiLanguage) {
        prefs.edit().putString(PREF_LANGUAGE, language.name).apply()
        _state.update { it.copy(language = language) }
    }

    fun onPngSelected(uri: Uri) = guardedLaunch {
        if (pendingOutputFile != null) {
            setError("Save the current character package before selecting a new input.")
            return@guardedLaunch
        }
        val displayName = AndroidFileBridge.displayName(app, uri)
        if (!displayName.endsWith(".png", ignoreCase = true)) {
            resetForInput(UiCharacterInputType.None, displayName)
            setError("Only PNG character images are supported in this page.")
            return@guardedLaunch
        }
        resetForInput(UiCharacterInputType.Png, displayName)
        _state.update { it.copy(isCopying = true, logs = listOf("Copying character PNG.")) }
        val copied = withContext(Dispatchers.IO) {
            createFreshRun()
            val inputDir = requireNotNull(runDir).resolve("input").apply { mkdirs() }
            val file = inputDir.resolve(displayName.sanitizeFileName("character.png").ensurePngExtension())
            AndroidFileBridge.copyUriToFile(app, uri, file)
            file
        }
        imageFile = copied
        val id = copied.nameWithoutExtension.safeId("character")
        val icon = withContext(Dispatchers.IO) {
            val generated = requireNotNull(runDir).resolve("generated").apply { mkdirs() }.resolve("${id}_icon.png")
            generateIcon(copied, generated, state.value.cropCenterX, state.value.cropCenterY, state.value.cropSize)
            generated
        }
        iconFile = icon
        val hasAlpha = withContext(Dispatchers.IO) { imageHasAlpha(copied) }
        _state.update { current ->
            val publisher = current.publisherId.safePublisherId()
            current.copy(
                isCopying = false,
                inputType = UiCharacterInputType.Png,
                inputName = displayName,
                workspacePath = runDir?.absolutePath,
                publisherId = publisher,
                characterId = id,
                directory = id,
                defaultName = copied.nameWithoutExtension,
                identifier = "$publisher.$id",
                outputFileName = CharacterStateRules.defaultOutputFileName(publisher, id).sanitizeFileName("etoilebridge.$id.arcpkg"),
                outputFileNameManual = false,
                imageFileName = copied.name,
                imageFilePath = copied.absolutePath,
                iconFileName = icon.name,
                iconFilePath = icon.absolutePath,
                imageHasAlpha = hasAlpha,
                warnings = if (hasAlpha == false) listOf("Character image may not have a transparent background.") else emptyList(),
                logs = current.logs + "Imported character PNG: ${copied.name}" + "Generated icon: ${icon.name}",
            )
        }
    }

    fun onCharacterArcpkgSelected(uri: Uri) = guardedLaunch {
        if (pendingOutputFile != null) {
            setError("Save the current character package before selecting a new input.")
            return@guardedLaunch
        }
        val displayName = AndroidFileBridge.displayName(app, uri)
        resetForInput(UiCharacterInputType.Arcpkg, displayName)
        _state.update { it.copy(isCopying = true, isScanning = true, logs = listOf("Importing character arcpkg.")) }
        val loadResult = withContext(Dispatchers.IO) {
            createFreshRun()
            val currentRun = requireNotNull(runDir)
            val sourceZip = currentRun.resolve("source.zip")
            AndroidFileBridge.copyUriToFile(app, uri, sourceZip)
            val archiveDir = currentRun.resolve("archive")
            ZipArchiveExtractor().extract(sourceZip, archiveDir)
            CharacterPackageLoader().loadExistingCharacterPackage(archiveDir)
        }
        if (loadResult.errors.isNotEmpty()) {
            _state.update {
                it.copy(
                    isCopying = false,
                    isScanning = false,
                    errorMessage = loadResult.errors.joinToString("; "),
                    warnings = loadResult.warnings,
                    logs = it.logs + "Character arcpkg import failed: ${loadResult.errors.joinToString("; ")}",
                )
            }
            return@guardedLaunch
        }
        val character = loadResult.character
        if (character == null || loadResult.entry == null) {
            setError("No editable character package was found.")
            _state.update { it.copy(isCopying = false, isScanning = false) }
            return@guardedLaunch
        }
        imageFile = loadResult.imageFile
        iconFile = loadResult.iconFile
        if (imageFile != null && iconFile == null) {
            iconFile = withContext(Dispatchers.IO) {
                val generated = requireNotNull(runDir).resolve("generated").apply { mkdirs() }
                    .resolve("${loadResult.directory ?: "character"}_icon.png")
                generateIcon(requireNotNull(imageFile), generated, state.value.cropCenterX, state.value.cropCenterY, state.value.cropSize)
                generated
            }
        }
        val (publisher, id) = splitCharacterIdentifier(
            loadResult.identifier.orEmpty(),
            fallbackPublisher = "etoilebridge",
            fallbackCharacterId = (loadResult.directory ?: displayName.substringBeforeLast('.')).safeId("character"),
        )
        val safeId = id.safeId("character")
        val safePublisher = publisher.safePublisherId()
        _state.update { current ->
            current.copy(
                isCopying = false,
                isScanning = false,
                inputType = UiCharacterInputType.Arcpkg,
                inputName = displayName,
                workspacePath = runDir?.absolutePath,
                publisherId = safePublisher,
                characterId = safeId,
                directory = (loadResult.directory ?: safeId).safeId(safeId),
                outputFileName = displayName.takeIf { it.endsWith(".arcpkg", ignoreCase = true) }
                    ?.sanitizeFileName(CharacterStateRules.defaultOutputFileName(safePublisher, safeId))
                    ?: CharacterStateRules.defaultOutputFileName(safePublisher, safeId),
                outputFileNameManual = displayName.endsWith(".arcpkg", ignoreCase = true),
                defaultName = character.name["default"].orEmpty().ifBlank { safeId },
                zhCnName = character.name["zh-cn"].orEmpty(),
                identifier = "$safePublisher.$safeId",
                sourceIdentifier = loadResult.identifier,
                sourceDirectory = loadResult.directory,
                imageFileName = imageFile?.name ?: character.imagePath,
                imageFilePath = imageFile?.absolutePath,
                iconFileName = iconFile?.name ?: character.iconPath,
                iconFilePath = iconFile?.absolutePath,
                imageHasAlpha = imageFile?.let { imageHasAlpha(it) },
                x = character.x,
                y = character.y,
                scale = if (character.scale == 0f) 1f else character.scale,
                warnings = loadResult.warnings,
                logs = current.logs + "Imported character arcpkg: ${loadResult.identifier}",
            )
        }
    }

    fun updatePublisherId(value: String) {
        _state.update { current ->
            current.withIdentity(
                publisherId = value.safePublisherId(),
                characterId = current.characterId,
                outputManual = current.outputFileNameManual,
            )
        }
    }

    fun updateCharacterId(value: String) {
        _state.update { current ->
            current.withIdentity(
                publisherId = current.publisherId,
                characterId = value.safeId("character"),
                outputManual = current.outputFileNameManual,
            )
        }
    }

    fun updateOutputFileName(value: String) {
        _state.update { it.copy(outputFileName = value.ensureArcpkgExtension(), outputFileNameManual = true) }
    }

    fun updateDefaultName(value: String) {
        _state.update { it.copy(defaultName = value) }
    }

    fun updateZhCnName(value: String) {
        _state.update { it.copy(zhCnName = value) }
    }

    fun updateCropCenterX(value: Float) {
        _state.update { it.copy(cropCenterX = value.coerceIn(0f, 1f)) }
    }

    fun updateCropCenterY(value: Float) {
        _state.update { it.copy(cropCenterY = value.coerceIn(0f, 1f)) }
    }

    fun updateCropSize(value: Float) {
        _state.update { it.copy(cropSize = value.coerceIn(0.05f, 1f)) }
    }

    fun updateX(value: Float) {
        _state.update { it.copy(x = value) }
    }

    fun updateY(value: Float) {
        _state.update { it.copy(y = value) }
    }

    fun updateScale(value: Float) {
        _state.update { it.copy(scale = value.coerceIn(0.1f, 3f)) }
    }

    fun resetCrop() {
        _state.update { it.copy(cropCenterX = 0.5f, cropCenterY = 0.35f, cropSize = 0.35f) }
    }

    fun resetPosition() {
        _state.update { it.copy(x = 0f, y = 0f, scale = 1f) }
    }

    fun centerPosition() {
        _state.update { it.copy(x = 0f, y = 0f) }
    }

    fun fitHeightPosition() {
        _state.update { it.copy(x = 0f, y = 0f, scale = 0.7f) }
    }

    fun fitWidthPosition() {
        _state.update { it.copy(x = 0f, y = 0f, scale = 0.55f) }
    }

    fun sampleDefaultPosition() {
        _state.update { it.copy(x = 300f, y = 100f, scale = 0.7f) }
    }

    fun regenerateIcon() = guardedLaunch {
        val image = imageFile ?: run {
            setError("Select a character PNG before generating an icon.")
            return@guardedLaunch
        }
        val icon = withContext(Dispatchers.IO) {
            val output = requireNotNull(runDir).resolve("generated").apply { mkdirs() }
                .resolve("${state.value.characterId.safeId("character")}_icon.png")
            generateIcon(image, output, state.value.cropCenterX, state.value.cropCenterY, state.value.cropSize)
            output
        }
        iconFile = icon
        _state.update {
            it.copy(
                iconFileName = icon.name,
                iconFilePath = icon.absolutePath,
                logs = it.logs + "Generated icon: ${icon.name}",
            )
        }
    }

    fun buildPackage() = guardedLaunch {
        val image = imageFile ?: run {
            setError("Character image is missing.")
            return@guardedLaunch
        }
        val icon = iconFile ?: run {
            setError("Character icon is missing.")
            return@guardedLaunch
        }
        pendingOutputFile = null
        _state.update {
            it.copy(
                isBuilding = true,
                pendingOutputFile = null,
                pendingOutputFileSize = null,
                validationPassed = null,
                validationSummary = emptyList(),
                validationErrors = emptyList(),
                saveStatus = UiSaveStatus.NotSaved,
                savedLocation = null,
                savedFileSize = null,
                workspaceCleaned = false,
                errorMessage = null,
                errorDetails = null,
                logs = it.logs + "Building character package.",
            )
        }
        val output = requireNotNull(runDir).resolve("output").apply { mkdirs() }
            .resolve(state.value.outputFileName.ensureArcpkgExtension().sanitizeFileName("character.arcpkg"))
        val result = withContext(Dispatchers.IO) {
            CharacterPackageBuilder().build(
                CharacterPackageInput(
                    imageFile = image,
                    iconFile = icon,
                    outputFile = output,
                    options = CharacterPackageOptions(
                        publisherId = state.value.publisherId,
                        characterId = state.value.characterId,
                        directory = state.value.directory,
                        defaultName = state.value.defaultName,
                        zhCnName = state.value.zhCnName.takeIf { it.isNotBlank() },
                        imageFileName = "${state.value.characterId.safeId("character")}.png",
                        iconFileName = "${state.value.characterId.safeId("character")}_icon.png",
                        x = state.value.x,
                        y = state.value.y,
                        scale = state.value.scale,
                    ),
                )
            )
        }
        when (result) {
            is CharacterPackageResult.Success -> {
                pendingOutputFile = result.outputFile
                _state.update {
                    it.copy(
                        isBuilding = false,
                        pendingOutputFile = result.outputFile,
                        pendingOutputFileSize = result.outputFile.length(),
                        validationPassed = result.validation.valid,
                        validationSummary = result.validation.summaryLines(),
                        validationErrors = result.validation.errors,
                        saveStatus = UiSaveStatus.Pending,
                        warnings = result.warnings,
                        logs = it.logs + result.logs,
                        errorMessage = null,
                        errorDetails = null,
                    )
                }
            }
            is CharacterPackageResult.Failed -> {
                _state.update {
                    it.copy(
                        isBuilding = false,
                        validationPassed = false,
                        validationSummary = emptyList(),
                        validationErrors = result.warnings,
                        warnings = result.warnings,
                        logs = it.logs + result.logs,
                        errorMessage = result.message,
                        errorDetails = result.cause?.stackTraceToString(),
                    )
                }
            }
        }
    }

    fun saveOutputToDownloads() {
        val file = pendingOutputFile ?: return
        if (state.value.validationPassed != true) {
            setError("Character package validation has not passed.")
            return
        }
        if (!canUseDownloads()) {
            _state.update {
                it.copy(
                    saveStatus = UiSaveStatus.Failed,
                    errorMessage = "Current Android version requires Save As.",
                    logs = it.logs + "Downloads unavailable on SDK ${Build.VERSION.SDK_INT}; pending output is kept.",
                )
            }
            return
        }
        savePending("Downloads") { AndroidFileBridge.saveToDownloads(app, file, suggestedOutputFileName()) }
    }

    fun saveOutputTo(uri: Uri) {
        val file = pendingOutputFile ?: return
        if (state.value.validationPassed != true) {
            setError("Character package validation has not passed.")
            return
        }
        savePending("SAF") { AndroidFileBridge.writeFileToUri(app, file, uri) }
    }

    fun onSaveAsCanceled() {
        if (pendingOutputFile == null) return
        _state.update {
            it.copy(
                saveStatus = UiSaveStatus.Canceled,
                errorMessage = "User canceled save.",
                errorDetails = null,
                logs = it.logs + "User canceled character save; pending output is kept.",
            )
        }
    }

    fun suggestedOutputFileName(): String = state.value.outputFileName.ensureArcpkgExtension()

    fun clearCache() = guardedLaunch {
        if (pendingOutputFile != null) {
            setError("Save the current character package before clearing cache.")
            return@guardedLaunch
        }
        withContext(Dispatchers.IO) { cacheRoot().deleteRecursively() }
        runDir = null
        imageFile = null
        iconFile = null
        pendingOutputFile = null
        _state.update { baseState(it.language).copy(logs = it.logs + "Cleared character cache.") }
    }

    fun reportExternalError(message: String, throwable: Throwable) {
        showError(message, throwable)
    }

    private fun savePending(method: String, block: suspend () -> SaveResult) = guardedLaunch {
        _state.update { it.copy(isSaving = true, saveStatus = UiSaveStatus.Saving, errorMessage = null, errorDetails = null) }
        val result = withContext(Dispatchers.IO) { block() }
        val currentRun = runDir
        withContext(Dispatchers.IO) { currentRun?.deleteRecursively() }
        runDir = null
        imageFile = null
        iconFile = null
        pendingOutputFile = null
        _state.update {
            it.copy(
                isSaving = false,
                pendingOutputFile = null,
                pendingOutputFileSize = null,
                saveStatus = UiSaveStatus.Saved,
                savedLocation = result.location,
                savedFileSize = result.writtenBytes,
                workspaceCleaned = true,
                logs = it.logs + "Saved character via $method: ${result.location}" + "Cleaned character workspace.",
            )
        }
    }

    private fun resetForInput(type: UiCharacterInputType, inputName: String?) {
        pendingOutputFile = null
        imageFile = null
        iconFile = null
        _state.update {
            baseState(it.language).copy(
                inputType = type,
                inputName = inputName,
                publisherId = it.publisherId,
                characterId = it.characterId,
                directory = it.characterId,
                defaultName = it.defaultName,
                zhCnName = it.zhCnName,
                outputFileName = CharacterStateRules.defaultOutputFileName(it.publisherId, it.characterId),
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

    private fun cacheRoot(): File = app.cacheDir.resolve("etoilebridge_character")

    private fun guardedLaunch(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.Main + crashGuard) {
            try {
                block()
            } catch (error: Exception) {
                showError(error.message ?: "Character operation failed.", error)
            }
        }
    }

    private fun showError(message: String, throwable: Throwable) {
        _state.update {
            it.copy(
                isCopying = false,
                isScanning = false,
                isBuilding = false,
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

    private fun baseState(language: UiLanguage): UiCharacterState =
        UiCharacterState(
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

    private fun UiCharacterState.withIdentity(
        publisherId: String,
        characterId: String,
        outputManual: Boolean,
    ): UiCharacterState {
        val safePublisher = publisherId.safePublisherId()
        val safeCharacter = characterId.safeId("character")
        return copy(
            publisherId = safePublisher,
            characterId = safeCharacter,
            directory = safeCharacter,
            identifier = "$safePublisher.$safeCharacter",
            outputFileName = if (outputManual) outputFileName else CharacterStateRules.defaultOutputFileName(safePublisher, safeCharacter)
                .sanitizeFileName("etoilebridge.$safeCharacter.arcpkg"),
        )
    }
}

private fun imageHasAlpha(file: File): Boolean? {
    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
    return runCatching { BitmapFactory.decodeFile(file.absolutePath, options)?.hasAlpha() }.getOrNull()
}

private fun generateIcon(
    source: File,
    output: File,
    centerX: Float,
    centerY: Float,
    cropSize: Float,
    iconSize: Int = 256,
) {
    val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: error("Unable to decode character image.")
    try {
        val size = (min(bitmap.width, bitmap.height) * cropSize.coerceIn(0.05f, 1f)).roundToInt().coerceAtLeast(1)
        val cx = (bitmap.width * centerX.coerceIn(0f, 1f)).roundToInt()
        val cy = (bitmap.height * centerY.coerceIn(0f, 1f)).roundToInt()
        val left = (cx - size / 2).coerceIn(0, max(0, bitmap.width - size))
        val top = (cy - size / 2).coerceIn(0, max(0, bitmap.height - size))
        val cropped = Bitmap.createBitmap(bitmap, left, top, min(size, bitmap.width - left), min(size, bitmap.height - top))
        try {
            val scaled = Bitmap.createScaledBitmap(cropped, iconSize, iconSize, true)
            try {
                output.parentFile?.mkdirs()
                output.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                scaled.recycle()
            }
        } finally {
            cropped.recycle()
        }
    } finally {
        bitmap.recycle()
    }
}

private fun String.safePublisherId(): String =
    split('.')
        .joinToString(".") { it.safeId("etoilebridge") }
        .ifBlank { "etoilebridge" }

private fun String.safeId(fallback: String): String =
    replace(Regex("""[^\w.-]+"""), "_")
        .trim('.', '_', '-', ' ')
        .ifBlank { fallback }

private fun String.sanitizeFileName(fallback: String): String =
    replace(Regex("""[\\/:*?"<>|]+"""), "_")
        .trim()
        .ifBlank { fallback }

private fun String.ensureArcpkgExtension(): String =
    if (endsWith(".arcpkg", ignoreCase = true)) this else "$this.arcpkg"

private fun String.ensurePngExtension(): String =
    if (endsWith(".png", ignoreCase = true)) this else "$this.png"

private const val PREF_LANGUAGE = "language"
