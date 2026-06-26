package com.zeerqi27.etoilebridge.core

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourceResolverTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `base jpg is recognized as jacket when it is the only image`() {
        val result = resolveWithFiles("base.jpg")

        assertEquals("base.jpg", result.difficulties.single().jacketFile?.name)
        assertEquals(null, result.difficulties.single().backgroundFile)
    }

    @Test
    fun `base jpg is jacket and bg png is background`() {
        val result = resolveWithFiles("base.jpg", "bg.png")
        val difficulty = result.difficulties.single()

        assertEquals("base.jpg", difficulty.jacketFile?.name)
        assertEquals("bg.png", difficulty.backgroundFile?.name)
    }

    @Test
    fun `base jpg is jacket and base light jpg is background`() {
        val result = resolveWithFiles("base.jpg", "base_light.jpg")
        val difficulty = result.difficulties.single()

        assertEquals("base.jpg", difficulty.jacketFile?.name)
        assertEquals("base_light.jpg", difficulty.backgroundFile?.name)
    }

    @Test
    fun `songlist bg stem resolves base light jpg`() {
        val result = resolveWithFiles("base.jpg", "base_light.jpg", songBg = "base_light")
        val difficulty = result.difficulties.single()

        assertEquals("base.jpg", difficulty.jacketFile?.name)
        assertEquals("base_light.jpg", difficulty.backgroundFile?.name)
    }

    @Test
    fun `jacket override exists and wins over base jpg`() {
        val result = resolveWithFiles(
            "base.jpg",
            "1080_2.jpg",
            difficulty = difficulty(jacketOverride = true),
        )
        val difficulty = result.difficulties.single()

        assertEquals("1080_2.jpg", difficulty.jacketFile?.name)
    }

    @Test
    fun `missing jacket override warns and falls back to base jpg`() {
        val warnings = mutableListOf<String>()
        val result = resolveWithFiles(
            "base.jpg",
            difficulty = difficulty(jacketOverride = true),
            warnings = warnings,
        )
        val difficulty = result.difficulties.single()

        assertEquals("base.jpg", difficulty.jacketFile?.name)
        assertTrue(warnings.any { it.contains("Jacket override image not found") })
    }

    @Test
    fun `only 1080 base jpg is recognized as jacket`() {
        val result = resolveWithFiles("1080_base.jpg")
        val difficulty = result.difficulties.single()

        assertEquals("1080_base.jpg", difficulty.jacketFile?.name)
        assertEquals(null, difficulty.backgroundFile)
    }

    @Test
    fun `1080 base jpg wins over 1080 base 256 jpg`() {
        val result = resolveWithFiles("1080_base_256.jpg", "1080_base.jpg")
        val difficulty = result.difficulties.single()

        assertEquals("1080_base.jpg", difficulty.jacketFile?.name)
    }

    @Test
    fun `only 1080 base 256 jpg is used as jacket fallback`() {
        val result = resolveWithFiles("1080_base_256.jpg")
        val difficulty = result.difficulties.single()

        assertEquals("1080_base_256.jpg", difficulty.jacketFile?.name)
    }

    @Test
    fun `base 256 jpg is not used as background fallback`() {
        val result = resolveWithFiles("base.jpg", "base_256.jpg", songBg = "missing_background")
        val difficulty = result.difficulties.single()

        assertEquals("base.jpg", difficulty.jacketFile?.name)
        assertEquals(null, difficulty.backgroundFile)
    }

    @Test
    fun `songlist bg stem resolves jingzheshshi jpg as background`() {
        val result = resolveWithFiles("1080_base.jpg", "jingzheshshi.jpg", songBg = "jingzheshshi")
        val difficulty = result.difficulties.single()

        assertEquals("1080_base.jpg", difficulty.jacketFile?.name)
        assertEquals("jingzheshshi.jpg", difficulty.backgroundFile?.name)
    }

    @Test
    fun `1080 base jpg is not recognized as background`() {
        val result = resolveWithFiles("1080_base.jpg")
        val difficulty = result.difficulties.single()

        assertEquals("1080_base.jpg", difficulty.jacketFile?.name)
        assertEquals(null, difficulty.backgroundFile)
    }

    @Test
    fun `jingzheshshi jpg does not steal jacket from 1080 base jpg`() {
        val result = resolveWithFiles("jingzheshshi.jpg", "1080_base.jpg", songBg = "jingzheshshi")
        val difficulty = result.difficulties.single()

        assertEquals("1080_base.jpg", difficulty.jacketFile?.name)
        assertEquals("jingzheshshi.jpg", difficulty.backgroundFile?.name)
    }

    @Test
    fun `project root image falls back as background when song bg is missing`() {
        val result = resolveWithFiles(
            "1080_base.jpg",
            songBg = "macula_light_a",
            projectRootFiles = listOf("macula_conflict_a.jpg"),
        )
        val difficulty = result.difficulties.single()

        assertEquals("1080_base.jpg", difficulty.jacketFile?.name)
        assertEquals("macula_conflict_a.jpg", difficulty.backgroundFile?.name)
    }

    @Test
    fun `manual background override wins over automatic background`() {
        val manual = tempDir.resolve("manual_bg.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val result = resolveWithFiles(
            "1080_base.jpg",
            "bg.jpg",
            overrides = ManualResourceOverrides(backgroundFile = manual),
        )

        assertEquals("manual_bg.jpg", result.difficulties.single().backgroundFile?.name)
    }

    @Test
    fun `manual jacket override wins over automatic jacket`() {
        val manual = tempDir.resolve("manual_jacket.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val result = resolveWithFiles(
            "1080_base.jpg",
            overrides = ManualResourceOverrides(jacketFile = manual),
        )

        assertEquals("manual_jacket.jpg", result.difficulties.single().jacketFile?.name)
    }

    @Test
    fun `manual audio override wins over automatic audio`() {
        val manual = tempDir.resolve("manual_audio.ogg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val result = resolveWithFiles(
            "1080_base.jpg",
            overrides = ManualResourceOverrides(audioFile = manual),
        )

        assertEquals("manual_audio.ogg", result.difficulties.single().audioFile.name)
    }

    private fun resolveWithFiles(
        vararg fileNames: String,
        songBg: String? = null,
        projectRootFiles: List<String> = emptyList(),
        difficulty: ResolvedDifficultyMetadata = difficulty(),
        warnings: MutableList<String> = mutableListOf(),
        overrides: ManualResourceOverrides? = null,
    ): ResolvedSong {
        val workspace = tempDir.resolve("workspace_${System.nanoTime()}").apply { mkdirs() }
        val songDir = workspace.resolve("song").apply { mkdirs() }
        val aff = songDir.resolve("2.aff").apply {
            writeText("AudioOffset:0\n-\ntiming(0,100.00,4.00);\n", Charsets.UTF_8)
        }
        songDir.resolve("base.ogg").writeBytes(byteArrayOf(1, 2, 3))
        fileNames.forEach { name -> songDir.resolve(name).writeBytes(byteArrayOf(4, 5, 6)) }
        projectRootFiles.forEach { name -> workspace.resolve(name).writeBytes(byteArrayOf(7, 8, 9)) }

        return ResourceResolver().resolve(
            workspaceDir = workspace,
            songDir = songDir,
            affFiles = mapOf(2 to aff),
            metadata = metadata(songBg = songBg, difficulty = difficulty),
            warnings = warnings,
            overrides = overrides,
        )
    }

    private fun metadata(
        songBg: String?,
        difficulty: ResolvedDifficultyMetadata,
    ): ResolvedSongMetadata = ResolvedSongMetadata(
        songId = "test_song",
        title = "Test Song",
        artist = "Composer",
        bpmText = "100",
        bpmBase = 100f,
        set = "single",
        side = 0,
        bg = songBg,
        bgInverse = null,
        audioPreview = 0,
        audioPreviewEnd = 5000,
        additionalFiles = emptyList(),
        pack = null,
        searchTags = "",
        difficulties = listOf(difficulty),
    )

    private fun difficulty(jacketOverride: Boolean = false): ResolvedDifficultyMetadata =
        ResolvedDifficultyMetadata(
            ratingClass = 2,
            chartDesigner = "Charter",
            jacketDesigner = "Illustrator",
            rating = 9,
            ratingPlus = false,
            jacketOverride = jacketOverride,
            audioOverride = false,
            bg = null,
            bgInverse = null,
            title = null,
            artist = null,
            bpmText = null,
            bpmBase = null,
        )
}
