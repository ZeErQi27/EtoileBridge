package com.zeerqi27.etoile_bridge

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlin.concurrent.thread

class MainActivity : FlutterActivity() {
    private var pendingFileResult: MethodChannel.Result? = null
    private var pendingRequestCode: Int? = null
    private var pendingMultipleSelection: Boolean = false
    private lateinit var singleSongBridge: AndroidSingleSongBridge
    private lateinit var packBridge: AndroidPackBridge
    private lateinit var characterBridge: AndroidCharacterBridge

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        singleSongBridge = AndroidSingleSongBridge(this)
        packBridge = AndroidPackBridge(this)
        characterBridge = AndroidCharacterBridge(this)
        singleSongBridge.cleanOldSessions()
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.zeerqi27.etoile_bridge/converter",
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "smokeTest" -> result.success(singleSongBridge.smokeTest())
                "cacheRoot" -> result.success(singleSongBridge.cacheRootPath())
                "scanSingle" -> runConverter(result) {
                    singleSongBridge.scanSingle(call.arguments as? Map<*, *> ?: emptyMap<Any, Any?>())
                }
                "saveSingle" -> runConverter(result) {
                    singleSongBridge.saveSingle(call.arguments as? Map<*, *> ?: emptyMap<Any, Any?>())
                }
                "scanPackOfficial" -> runConverter(result) {
                    packBridge.scanPackOfficial(call.arguments as? Map<*, *> ?: emptyMap<Any, Any?>())
                }
                "scanPackBundle" -> runConverter(result) {
                    packBridge.scanPackBundle(call.arguments as? Map<*, *> ?: emptyMap<Any, Any?>())
                }
                "scanPackExisting" -> runConverter(result) {
                    packBridge.scanPackExisting(call.arguments as? Map<*, *> ?: emptyMap<Any, Any?>())
                }
                "savePack" -> runConverter(result) {
                    packBridge.savePack(call.arguments as? Map<*, *> ?: emptyMap<Any, Any?>())
                }
                "scanCharacterImage" -> runConverter(result) {
                    characterBridge.scanCharacterImage(call.arguments as? Map<*, *> ?: emptyMap<Any, Any?>())
                }
                "scanCharacterPackage" -> runConverter(result) {
                    characterBridge.scanCharacterPackage(call.arguments as? Map<*, *> ?: emptyMap<Any, Any?>())
                }
                "generateCharacterIcon" -> runConverter(result) {
                    characterBridge.generateCharacterIcon(call.arguments as? Map<*, *> ?: emptyMap<Any, Any?>())
                }
                "saveCharacter" -> runConverter(result) {
                    characterBridge.saveCharacter(call.arguments as? Map<*, *> ?: emptyMap<Any, Any?>())
                }
                else -> result.notImplemented()
            }
        }
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.zeerqi27.etoile_bridge/file_dialogs",
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "pickFile" -> launchPickFile(call.arguments as? Map<*, *> ?: emptyMap<Any, Any?>(), result)
                "pickFiles" -> launchPickFiles(call.arguments as? Map<*, *> ?: emptyMap<Any, Any?>(), result)
                "pickFolder" -> launchPickFolder(result)
                "saveFile" -> launchCreateDocument(call.arguments as? Map<*, *> ?: emptyMap<Any, Any?>(), result)
                else -> result.notImplemented()
            }
        }
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.zeerqi27.etoile_bridge/theme",
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "dynamicColorScheme" -> result.success(dynamicColorScheme())
                else -> result.notImplemented()
            }
        }
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.zeerqi27.etoile_bridge/system",
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "openUrl" -> openUrl(call.arguments as? Map<*, *> ?: emptyMap<Any, Any?>(), result)
                else -> result.notImplemented()
            }
        }
    }

    private fun openUrl(args: Map<*, *>, result: MethodChannel.Result) {
        val url = args["url"]?.toString()?.trim().orEmpty()
        if (url.isBlank()) {
            result.error("invalid_url", "URL is empty.", null)
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
            result.success(null)
        } catch (error: ActivityNotFoundException) {
            result.error("no_browser", "No app can open this URL.", null)
        }
    }

    private fun runConverter(
        result: MethodChannel.Result,
        block: () -> Map<String, Any?>,
    ) {
        thread(name = "etoile-android-converter") {
            val envelope = block()
            runOnUiThread { result.success(envelope) }
        }
    }

    private fun launchPickFile(args: Map<*, *>, result: MethodChannel.Result) {
        if (!beginPending(RequestPickFile, result)) return
        val extensions = (args["extensions"] as? List<*>).orEmpty().map { it.toString().lowercase() }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeTypeFor(extensions)
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypesFor(extensions))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, RequestPickFile)
    }

    private fun launchPickFiles(args: Map<*, *>, result: MethodChannel.Result) {
        if (!beginPending(RequestPickFile, result, multiple = true)) return
        val extensions = (args["extensions"] as? List<*>).orEmpty().map { it.toString().lowercase() }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeTypeFor(extensions)
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypesFor(extensions))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, RequestPickFile)
    }

    private fun launchPickFolder(result: MethodChannel.Result) {
        if (!beginPending(RequestPickFolder, result)) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
        }
        startActivityForResult(intent, RequestPickFolder)
    }

    private fun launchCreateDocument(args: Map<*, *>, result: MethodChannel.Result) {
        if (!beginPending(RequestSaveFile, result)) return
        val suggested = args["suggestedName"]?.toString()?.takeIf { it.isNotBlank() }
            ?: "etoilebridge-level.arcpkg"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, suggested)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, RequestSaveFile)
    }

    private fun beginPending(
        requestCode: Int,
        result: MethodChannel.Result,
        multiple: Boolean = false,
    ): Boolean {
        if (pendingFileResult != null) {
            result.error("busy", "Another Android file dialog is already open.", null)
            return false
        }
        pendingRequestCode = requestCode
        pendingFileResult = result
        pendingMultipleSelection = multiple
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val pending = pendingFileResult ?: return
        if (requestCode != pendingRequestCode) return
        pendingFileResult = null
        pendingRequestCode = null
        val wantsMultiple = pendingMultipleSelection
        pendingMultipleSelection = false
        if (resultCode != Activity.RESULT_OK) {
            pending.success(if (wantsMultiple) emptyList<String>() else null)
            return
        }
        if (wantsMultiple) {
            val uris = buildList {
                data?.clipData?.let { clip ->
                    for (index in 0 until clip.itemCount) {
                        clip.getItemAt(index)?.uri?.let { add(it) }
                    }
                }
                data?.data?.let { add(it) }
            }.distinct()
            uris.forEach { persistPermission(it, data?.flags ?: 0, requestCode) }
            pending.success(uris.map { it.toString() })
            return
        }
        val uri = data?.data
        if (uri == null) {
            pending.success(null)
            return
        }
        persistPermission(uri, data.flags, requestCode)
        pending.success(uri.toString())
    }

    private fun persistPermission(uri: Uri, flags: Int, requestCode: Int) {
        val wanted = when (requestCode) {
            RequestSaveFile -> Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            RequestPickFolder -> Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            else -> Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val granted = flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val persistFlags = granted and wanted
        if (persistFlags == 0) return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, persistFlags)
        }.onFailure {
            Log.w("EtoileMainActivity", "Persistable URI permission was not granted: ${it.message}")
        }
    }

    private fun mimeTypeFor(extensions: List<String>): String =
        when {
            extensions.any { it in imageExtensions } -> "image/*"
            extensions.size == 1 && extensions.single() == "zip" -> "*/*"
            else -> "*/*"
        }

    private fun mimeTypesFor(extensions: List<String>): Array<String> =
        extensions.flatMap { extension ->
            when (extension) {
                "zip" -> listOf("application/zip", "application/x-zip-compressed", "application/x-zip", "application/octet-stream", "*/*")
                "arcpkg" -> listOf("application/octet-stream", "application/zip", "application/x-zip-compressed")
                "png" -> listOf("image/png")
                "jpg", "jpeg" -> listOf("image/jpeg")
                "webp" -> listOf("image/webp")
                else -> listOf("*/*")
            }
        }.distinct().toTypedArray()

    private fun dynamicColorScheme(): Map<String, Long>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val primary = firstAndroidColor(
            "system_accent1_600",
            "system_accent1_500",
            "system_accent1_400",
        ) ?: return null
        val secondary = firstAndroidColor(
            "system_accent2_600",
            "system_accent2_500",
            "system_accent2_400",
        )
        val tertiary = firstAndroidColor(
            "system_accent3_600",
            "system_accent3_500",
            "system_accent3_400",
        )
        return buildMap {
            put("primary", primary)
            secondary?.let { put("secondary", it) }
            tertiary?.let { put("tertiary", it) }
        }
    }

    private fun firstAndroidColor(vararg names: String): Long? {
        for (name in names) {
            val id = resources.getIdentifier(name, "color", "android")
            if (id == 0) continue
            return getColor(id).toLong() and 0xffffffffL
        }
        return null
    }

    companion object {
        private const val RequestPickFile = 4201
        private const val RequestPickFolder = 4202
        private const val RequestSaveFile = 4203
        private val imageExtensions = setOf("png", "jpg", "jpeg", "webp")
    }
}
