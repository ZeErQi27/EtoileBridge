package com.zeerqi27.etoile_bridge

import android.net.Uri
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class AndroidLiteExportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var root: File

    @Before
    fun setUp() {
        assumeTrue(Process.is64Bit())
        root = context.cacheDir.resolve("lite-export-\u66f2\u5305-\uD83D\uDE80-${UUID.randomUUID()}")
        assertTrue(root.mkdir())
    }

    @After
    fun tearDown() {
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun exportsSingleWithNativeResolutionAndScenecontrol() {
        val workspace = root.resolve("single")
        createSong(workspace)
        writeSonglist(workspace)
        val out = root.resolve("single.arcpkg")

        val result = AndroidSingleSongBridge(context).saveSingle(
            mapOf("workspace" to workspace.path, "output" to Uri.fromFile(out).toString()),
        )
        assertUsesLite(result)
        assertLiteChart(out)
    }

    @Test
    fun exportsOfficialPackTwiceWithTheSameNativeEngine() {
        val workspace = root.resolve("pack")
        createSong(workspace.resolve("litesync"))
        writeSonglist(workspace)
        val bridge = AndroidPackBridge(context)
        repeat(2) { index ->
            val out = root.resolve("pack-$index.arcpkg")
            val result = bridge.savePack(
                mapOf(
                    "mode" to "official",
                    "workspace" to workspace.path,
                    "output" to Uri.fromFile(out).toString(),
                    "request" to mapOf("packName" to "Lite Pack", "packId" to "litepack"),
                ),
            )
            assertUsesLite(result)
            assertLiteChart(out)
        }
    }

    @Test
    fun malformedChartReturnsAnErrorWithoutBreakingTheNextConversion() {
        val workspace = root.resolve("invalid").apply { mkdir() }
        val source = workspace.resolve("2.aff")
        source.writeText("AudioOffset:0\n-\ndesignant();\n")
        val failed = runCatching { AndroidEtoileLite.convert(source) }
        assertTrue(failed.exceptionOrNull()?.message.orEmpty(), failed.isFailure)
        createSong(workspace)
        assertTrue(AndroidEtoileLite.convert(source).aff.contains(",true,3.50);"))
    }

    private fun createSong(directory: File) {
        directory.mkdirs()
        instrumentation.context.assets.open("native-resolution.aff").use { fixture ->
            directory.resolve("2.aff").outputStream().use { fixture.copyTo(it) }
        }
        // These bridge tests exercise chart conversion and packaging, not the media decoder.
        directory.resolve("base.ogg").writeBytes(byteArrayOf(1))
        directory.resolve("base.jpg").writeBytes(byteArrayOf(2))
    }

    private fun writeSonglist(directory: File) {
        directory.resolve("songlist").writeText(
            """
            {"songs":[{"id":"litesync","title_localized":{"en":"Lite Sync"},"artist":"Test",
            "bpm":"120","bpm_base":120,"set":"litepack","side":0,
            "difficulties":[{"ratingClass":2,"rating":9}]}]}
            """.trimIndent(),
        )
    }

    private fun assertUsesLite(result: Map<String, Any?>) {
        assertEquals(result.toString(), true, result["ok"])
        assertTrue(result.toString(), result["logs"].toString().contains("with Etoile.Lite"))
    }

    private fun assertLiteChart(output: File) {
        ZipFile(output).use { zip ->
            fun read(suffix: String): String {
                val entry = zip.entries().asSequence().first { it.name.endsWith(suffix) }
                return zip.getInputStream(entry).bufferedReader().use { it.readText() }
            }
            val chart = read("/2.aff")
            assertTrue(chart, chart.contains("arc(1000,1001"))
            assertTrue(chart, chart.contains("arc(1800,2400,1.00,0.00,s,1.00,0.00"))
            assertTrue(chart, chart.contains(",true,2.00);"))
            assertTrue(chart, chart.contains(",true,3.50);"))
            assertFalse(chart, chart.contains("arcresolution="))
            val scene = JSONArray(read("/2.sc.json"))
            assertTrue(scene.length() > 1)
            assertEquals("versioning", scene.getJSONObject(0).getString("Type"))
            assertTrue((0 until scene.length()).any { scene.getJSONObject(it).getString("Type") == "track" })
        }
    }
}
