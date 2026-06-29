package com.zeerqi27.etoilebridge.core

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackEngineTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun noSonglistReturnsNeedMetadata() {
        val workspace = tempDir.resolve("workspace").apply { mkdirs() }
        writeAff(workspace.resolve("2.aff"))
        workspace.resolve("base.ogg").writeBytes(byteArrayOf(1, 2, 3))

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.NeedMetadata)
        assertFalse(result.missingMetadata.requiredFields.contains("songId"))
    }

    @Test
    fun noSonglistWithManualMetadataConverts() {
        val workspace = tempDir.resolve("manual_metadata_${System.nanoTime()}").apply { mkdirs() }
        writeAff(workspace.resolve("2.aff"))
        workspace.resolve("base.ogg").writeBytes(byteArrayOf(1, 2, 3))
        workspace.resolve("1080_base.jpg").writeBytes(byteArrayOf(4, 5, 6))

        val result = EtoileBridgeConverter.convert(
            ConvertInput(
                workspace,
                tempDir.resolve("out"),
                manualMetadata = ManualMetadata(
                    songId = "afterdark",
                    title = "Afterdark",
                    artist = "Tonesphere",
                    bpmText = "128",
                    bpmBase = 128f,
                    set = "single",
                    side = 0,
                    difficulties = listOf(
                        ManualDifficultyMetadata(
                            ratingClass = 2,
                            rating = 9,
                            chartDesigner = "Manual Charter",
                            jacketDesigner = "Manual Jacket",
                        ),
                    ),
                ),
            ),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertEquals("afterdark", result.songId)
        ZipFile(result.outputFile).use { zip ->
            val project = zip.readTextEntry("afterdark/project.arcproj")
            assertTrue(project.contains("""title: "Afterdark""""))
            assertTrue(project.contains("""composer: "Tonesphere""""))
            assertTrue(project.contains("""bpmText: "128""""))
        }
    }

    @Test
    fun manualMetadataOverridesSonglistProjectFields() {
        val workspace = createCompleteWorkspace(
            songId = "auto_song",
            title = "Auto Title",
            artist = "Auto Composer",
            bpm = "100",
            bpmBase = 100.0,
        )

        val result = EtoileBridgeConverter.convert(
            ConvertInput(
                workspace,
                tempDir.resolve("out"),
                manualMetadata = ManualMetadata(
                    songId = "manual_song",
                    title = "Manual Title",
                    artist = "Manual Composer",
                    bpmText = "175",
                    bpmBase = 175.5f,
                    difficulties = listOf(
                        ManualDifficultyMetadata(
                            ratingClass = 2,
                            rating = 11,
                            ratingPlus = true,
                            difficulty = "Future 11+",
                            chartConstant = 11.7f,
                            chartDesigner = "Manual Charter",
                            jacketDesigner = "Manual Illustrator",
                        ),
                    ),
                ),
            ),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertEquals("manual_song", result.songId)
        assertEquals("manual_song.arcpkg", result.outputFile.name)
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.entryNames()
            assertTrue("manual_song/project.arcproj" in entries)
            val project = zip.readTextEntry("manual_song/project.arcproj")
            assertTrue(project.contains("""title: "Manual Title""""))
            assertTrue(project.contains("""composer: "Manual Composer""""))
            assertTrue(project.contains("baseBpm: 175.5"))
            assertTrue(project.contains("""bpmText: "175""""))
            assertTrue(project.contains("""charter: "Manual Charter""""))
            assertTrue(project.contains("""illustrator: "Manual Illustrator""""))
            assertTrue(project.contains("""difficulty: "Future 11+""""))
            assertTrue(project.contains("chartConstant: 11.7"))
        }
    }

    @Test
    fun partialSlstWithManualCompletionConverts() {
        val workspace = tempDir.resolve("partial_slst_${System.nanoTime()}").apply { mkdirs() }
        workspace.resolve("slst").writeText(
            """
            {
              "songs": [{
                "id": "partial_song",
                "difficulties": [{"ratingClass": 2}]
              }]
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )
        writeAff(workspace.resolve("2.aff"))
        workspace.resolve("base.ogg").writeBytes(byteArrayOf(1, 2, 3))
        workspace.resolve("base.jpg").writeBytes(byteArrayOf(4, 5, 6))

        val result = EtoileBridgeConverter.convert(
            ConvertInput(
                workspace,
                tempDir.resolve("out"),
                manualMetadata = ManualMetadata(
                    title = "Partial Song",
                    artist = "Manual Composer",
                    bpmText = "160",
                    bpmBase = 160f,
                    difficulties = listOf(
                        ManualDifficultyMetadata(
                            ratingClass = 2,
                            difficulty = "Future 10",
                            chartConstant = 10.3f,
                        ),
                    ),
                ),
            ),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertEquals("partial_song", result.songId)
        ZipFile(result.outputFile).use { zip ->
            val project = zip.readTextEntry("partial_song/project.arcproj")
            assertTrue(project.contains("""title: "Partial Song""""))
            assertTrue(project.contains("""composer: "Manual Composer""""))
            assertTrue(project.contains("""difficulty: "Future 10""""))
            assertTrue(project.contains("chartConstant: 10.3"))
        }
    }

    @Test
    fun defaultPackageOptionsUseEtoilebridgePublisherIdentifier() {
        val workspace = createCompleteWorkspace(songId = "test_song")

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertEquals("test_song.arcpkg", result.outputFile.name)
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.entryNames()
            assertTrue("test_song/project.arcproj" in entries)
            val index = zip.readTextEntry("index.yml")
            assertTrue(index.contains("directory: test_song") || index.contains("""directory: "test_song""""))
            assertTrue(index.contains("identifier: etoilebridge.test_song") || index.contains("""identifier: "etoilebridge.test_song""""))
            val project = zip.readTextEntry("test_song/project.arcproj")
            assertFalse(project.contains("publisherId"))
            assertFalse(project.contains("levelId"))
            assertFalse(project.contains("identifier"))
        }
    }

    @Test
    fun packageOptionsCanSetPublisherAndLevelIdentifier() {
        val workspace = createCompleteWorkspace(songId = "test_song")

        val result = EtoileBridgeConverter.convert(
            ConvertInput(
                workspace,
                tempDir.resolve("out"),
                packageOptions = PackageOptions(
                    publisherId = "tar1412",
                    levelId = "netsuijou",
                ),
            ),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertEquals("test_song.arcpkg", result.outputFile.name)
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.entryNames()
            assertTrue("test_song/project.arcproj" in entries)
            val index = zip.readTextEntry("index.yml")
            assertTrue(index.contains("directory: test_song") || index.contains("""directory: "test_song""""))
            assertTrue(index.contains("identifier: tar1412.netsuijou") || index.contains("""identifier: "tar1412.netsuijou""""))
        }
    }

    @Test
    fun explicitIdentifierOverridesPublisherAndLevelIdentifier() {
        val workspace = createCompleteWorkspace(songId = "test_song")

        val result = EtoileBridgeConverter.convert(
            ConvertInput(
                workspace,
                tempDir.resolve("out"),
                packageOptions = PackageOptions(
                    publisherId = "ignored",
                    levelId = "ignored",
                    identifier = "custom.full.identifier",
                ),
            ),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val index = zip.readTextEntry("index.yml")
            assertTrue(index.contains("directory: test_song") || index.contains("""directory: "test_song""""))
            assertTrue(index.contains("identifier: custom.full.identifier") || index.contains("""identifier: "custom.full.identifier""""))
        }
    }

    @Test
    fun lephonSideMapsToLightAndWarns() {
        val workspace = createCompleteWorkspace(songId = "lephon_song", side = 3)

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertTrue(result.warnings.any { it.contains("Lephon") && it.contains("Light") })
        ZipFile(result.outputFile).use { zip ->
            val project = zip.readTextEntry("lephon_song/project.arcproj")
            assertTrue(project.containsYamlValue("side", "light"))
            assertFalse(project.contains("side: lephon"))
        }
    }

    @Test
    fun singleLineDefaultsToNone() {
        val workspace = createCompleteWorkspace(songId = "single_line_default")

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val project = zip.readTextEntry("single_line_default/project.arcproj")
            assertTrue(project.containsYamlValue("note", ""))
            assertTrue(project.containsYamlValue("particle", ""))
            assertTrue(project.containsYamlValue("singleLine", "none"))
        }
    }

    @Test
    fun appearanceOptionsWriteSupportedSkinFields() {
        val workspace = createCompleteWorkspace(songId = "appearance_song")

        val result = EtoileBridgeConverter.convert(
            ConvertInput(
                workspace,
                tempDir.resolve("out"),
                appearanceOptions = AppearanceOptions(
                    side = ArcCreateSide.CONFLICT,
                    note = ArcCreateNote.LIGHT,
                    particle = ArcCreateParticle.MIRAI_CONFLICT,
                    accent = ArcCreateAccent.DYNAMIX,
                    track = ArcCreateTrack.ARCANA,
                    singleLine = ArcCreateSingleLine.NEO,
                ),
            ),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val project = zip.readTextEntry("appearance_song/project.arcproj")
            assertTrue(project.containsYamlValue("side", "conflict"))
            assertTrue(project.containsYamlValue("note", "light"))
            assertTrue(project.containsYamlValue("particle", "miraiconflict"))
            assertTrue(project.containsYamlValue("accent", "dynamix"))
            assertTrue(project.containsYamlValue("track", "arcana"))
            assertTrue(project.containsYamlValue("singleLine", "neo"))
        }
    }

    @Test
    fun missingAudioReturnsFailed() {
        val workspace = createCompleteWorkspace(includeAudio = false)

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Failed)
        assertTrue(result.message.contains("Missing audio"))
    }

    @Test
    fun missingAudioWithManualOverrideConverts() {
        val workspace = createCompleteWorkspace(includeAudio = false)
        val manualAudio = workspace.resolve("manual_resources").apply { mkdirs() }.resolve("selected.ogg")
            .apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val result = EtoileBridgeConverter.convert(
            ConvertInput(
                workspace,
                tempDir.resolve("out"),
                resourceOverrides = ManualResourceOverrides(audioFile = manualAudio),
            ),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val project = zip.readTextEntry("test_song/project.arcproj")
            assertTrue(project.contains("""audioPath: "selected.ogg""""))
        }
    }

    @Test
    fun missingAffReturnsFailed() {
        val workspace = tempDir.resolve("missing_aff_${System.nanoTime()}").apply { mkdirs() }
        workspace.resolve("songlist").writeText(songlistJson(), Charsets.UTF_8)
        workspace.resolve("base.ogg").writeBytes(byteArrayOf(1, 2, 3))

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Failed)
        assertTrue(result.message.contains("No standard difficulty AFF"))
    }

    @Test
    fun nonStandardAffManualMappedToFutureConverts() {
        val workspace = tempDir.resolve("manual_aff_${System.nanoTime()}").apply { mkdirs() }
        workspace.resolve("songlist").writeText(songlistJson(), Charsets.UTF_8)
        val manualAff = workspace.resolve("2_no_smoothness.aff").also { writeAff(it, "timing(0,222.00,4.00);") }
        workspace.resolve("base.ogg").writeBytes(byteArrayOf(1, 2, 3))
        workspace.resolve("base.jpg").writeBytes(byteArrayOf(4, 5, 6))

        val result = EtoileBridgeConverter.convert(
            ConvertInput(
                workspace,
                tempDir.resolve("out"),
                chartOverrides = ManualChartOverrides(adoptedAffByRatingClass = mapOf(2 to manualAff)),
            ),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertTrue(result.warnings.any { it.contains("Manual AFF mapping adopted") && it.contains("2_no_smoothness.aff") })
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.entryNames()
            assertTrue("test_song/2.aff" in entries)
            assertFalse("test_song/2_no_smoothness.aff" in entries)
        }
    }

    @Test
    fun manualAffMappingOverridesStandardAffForSameDifficulty() {
        val workspace = createCompleteWorkspace(extraAffFileNames = listOf("2_manual.aff"))
        val manualAff = workspace.resolve("2_manual.aff")

        val result = EtoileBridgeConverter.convert(
            ConvertInput(
                workspace,
                tempDir.resolve("out"),
                chartOverrides = ManualChartOverrides(adoptedAffByRatingClass = mapOf(2 to manualAff)),
            ),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertTrue(result.warnings.any { it.contains("Manual AFF mapping adopted") && it.contains("2_manual.aff") })
    }

    @Test
    fun multiSongPackReturnsUnsupportedPackStructure() {
        val workspace = tempDir.resolve("multi_song_${System.nanoTime()}").apply { mkdirs() }
        listOf("song_a", "song_b").forEach { songId ->
            val songDir = workspace.resolve(songId).apply { mkdirs() }
            writeAff(songDir.resolve("2.aff"))
            songDir.resolve("base.ogg").writeBytes(byteArrayOf(1, 2, 3))
        }

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.UnsupportedPackStructure, result.toString())
        assertEquals(listOf("song_a", "song_b"), result.candidateSongIds)
    }

    @Test
    fun missingBackgroundDoesNotBlockConversion() {
        val workspace = createCompleteWorkspace(includeBackground = false)

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertTrue(result.warnings.any { it.contains("Background not recognized") })
        ZipFile(result.outputFile).use { zip ->
            val project = zip.readTextEntry("test_song/project.arcproj")
            assertFalse(project.contains("backgroundPath: \"bg.png\""))
        }
    }

    @Test
    fun missingJacketDoesNotCrashAndWarns() {
        val workspace = createCompleteWorkspace(includeJacket = false)

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertTrue(result.warnings.any { it.contains("Jacket image not found") })
        ZipFile(result.outputFile).use { zip ->
            val project = zip.readTextEntry("test_song/project.arcproj")
            assertTrue(project.contains("jacketPath: \"\""))
        }
    }

    @Test
    fun missingJacketWithManualOverrideWritesJacketPath() {
        val workspace = createCompleteWorkspace(includeJacket = false)
        val manualJacket = workspace.resolve("manual_resources").apply { mkdirs() }.resolve("selected_jacket.jpg")
            .apply { writeBytes(byteArrayOf(4, 5, 6)) }

        val result = EtoileBridgeConverter.convert(
            ConvertInput(
                workspace,
                tempDir.resolve("out"),
                resourceOverrides = ManualResourceOverrides(jacketFile = manualJacket),
            ),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val project = zip.readTextEntry("test_song/project.arcproj")
            assertTrue(project.contains("""jacketPath: "selected_jacket.jpg""""))
        }
    }

    @Test
    fun missingBackgroundWithManualOverrideWritesBackgroundPath() {
        val workspace = createCompleteWorkspace(includeBackground = false)
        val manualBackground = workspace.resolve("manual_resources").apply { mkdirs() }.resolve("selected_bg.jpg")
            .apply { writeBytes(byteArrayOf(7, 8, 9)) }

        val result = EtoileBridgeConverter.convert(
            ConvertInput(
                workspace,
                tempDir.resolve("out"),
                resourceOverrides = ManualResourceOverrides(backgroundFile = manualBackground),
            ),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val project = zip.readTextEntry("test_song/project.arcproj")
            assertTrue(project.contains("""backgroundPath: "selected_bg.jpg""""))
        }
    }

    @Test
    fun manualSonglistOverrideResolvesMissingSonglist() {
        val workspace = tempDir.resolve("manual_songlist_${System.nanoTime()}").apply { mkdirs() }
        writeAff(workspace.resolve("2.aff"))
        workspace.resolve("base.ogg").writeBytes(byteArrayOf(1, 2, 3))
        workspace.resolve("base.jpg").writeBytes(byteArrayOf(4, 5, 6))
        val manualDir = workspace.resolve("manual_resources").apply { mkdirs() }
        val manualSonglist = manualDir.resolve("songlist.json").apply {
            writeText(songlistJson(songId = "manual_song", title = "Manual Song"), Charsets.UTF_8)
        }

        val result = EtoileBridgeConverter.convert(
            ConvertInput(
                workspace,
                tempDir.resolve("out"),
                resourceOverrides = ManualResourceOverrides(songlistFile = manualSonglist),
            ),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertEquals("manual_song", result.songId)
    }

    @Test
    fun successfulConversionOutputsSongIdArcpkg() {
        val workspace = createCompleteWorkspace()

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertEquals("test_song", result.songId)
        assertEquals("test_song.arcpkg", result.outputFile.name)
        assertTrue(result.outputFile.isFile)
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.entryNames()
            assertTrue("index.yml" in entries)
            assertTrue("test_song/project.arcproj" in entries)
            assertTrue(entries.any { it == "test_song/2.aff" }, "zip should contain at least one converted AFF")
            assertTrue(entries.any { it.startsWith("test_song/") && (it.endsWith(".ogg") || it.endsWith(".wav")) })
            assertTrue(entries.any { it.startsWith("test_song/") && it.hasImageExtension() })
            assertTrue("test_song/base.jpg" in entries)
            assertTrue("test_song/bg.png" in entries)
        }
    }

    @Test
    fun successfulConversionStillIncludesJacketPngForCompatibility() {
        val workspace = createCompleteWorkspace(jacketFileName = "jacket.png")

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.entryNames()
            assertTrue("test_song/jacket.png" in entries)
            assertTrue("test_song/bg.png" in entries)
        }
    }

    @Test
    fun successfulConversionIncludesBaseLightBackground() {
        val workspace = createCompleteWorkspace(
            backgroundFileName = "base_light.jpg",
            songBg = "base_light",
        )

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.entryNames()
            assertTrue("test_song/base.jpg" in entries)
            assertTrue("test_song/base_light.jpg" in entries)
        }
    }

    @Test
    fun successfulConversionWritesArcaeaNamedJacketAndBackgroundToProject() {
        val workspace = createCompleteWorkspace(
            songId = "JingZheShi",
            title = "Jing Zhe Shi",
            artist = "Sample Artist",
            bpm = "128",
            bpmBase = 128.0,
            jacketFileName = "1080_base.jpg",
            backgroundFileName = "jingzheshshi.jpg",
            extraImageFileNames = listOf("1080_base_256.jpg"),
            songBg = "jingzheshshi",
        )

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertEquals("JingZheShi.arcpkg", result.outputFile.name)
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.entryNames()
            assertTrue("JingZheShi/2.aff" in entries)
            assertTrue("JingZheShi/base.ogg" in entries)
            assertTrue("JingZheShi/1080_base.jpg" in entries)
            assertTrue("JingZheShi/jingzheshshi.jpg" in entries)

            val project = zip.readTextEntry("JingZheShi/project.arcproj")
            assertTrue(project.contains("""title: "Jing Zhe Shi""""))
            assertTrue(project.contains("""composer: "Sample Artist""""))
            assertTrue(project.contains("""bpmText: "128""""))
            assertTrue(project.contains("""audioPath: "base.ogg""""))
            assertTrue(project.contains("""chartPath: "2.aff""""))
            assertTrue(project.contains("""jacketPath: "1080_base.jpg""""))
            assertFalse(project.contains("jacketPath: \"\""), "jacketPath must not be empty when jacket image is in zip")
            assertTrue(project.contains("""backgroundPath: "jingzheshshi.jpg""""))
        }
    }

    @Test
    fun twoLayerUsseewaStructureUsesInnerJacketAndOuterBackgroundFallback() {
        val workspace = createUsseewaWorkspace()

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertEquals("USSEEWACantonese", result.songId)
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.entryNames()
            assertTrue("USSEEWACantonese/2.aff" in entries)
            assertTrue("USSEEWACantonese/base.ogg" in entries)
            assertTrue("USSEEWACantonese/1080_base.jpg" in entries)
            assertTrue("USSEEWACantonese/macula_conflict_a.jpg" in entries)

            val project = zip.readTextEntry("USSEEWACantonese/project.arcproj")
            assertTrue(project.contains("""chartPath: "2.aff""""))
            assertTrue(project.contains("""audioPath: "base.ogg""""))
            assertTrue(project.contains("""jacketPath: "1080_base.jpg""""))
            assertTrue(project.contains("""backgroundPath: "macula_conflict_a.jpg""""))
        }
        assertTrue(result.warnings.any { it.contains("macula_light_a") })
    }

    @Test
    fun nonStandardAffIsIgnoredAndWarned() {
        val workspace = createCompleteWorkspace(extraAffFileNames = listOf("2-extra.aff"))

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        assertTrue(result.warnings.any { it.contains("Ignored non-standard AFF file") && it.contains("2-extra.aff") })
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.entryNames()
            assertTrue("test_song/2.aff" in entries)
            assertFalse("test_song/2-extra.aff" in entries)
        }
    }

    @Test
    fun manualResourceOverridesAreWrittenToProject() {
        val workspace = createCompleteWorkspace()
        val manualDir = workspace.resolve("manual_resources").apply { mkdirs() }
        val manualAudio = manualDir.resolve("manual_audio.ogg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val manualJacket = manualDir.resolve("manual_jacket.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val manualBackground = manualDir.resolve("manual_bg.jpg").apply { writeBytes(byteArrayOf(7, 8, 9)) }

        val result = EtoileBridgeConverter.convert(
            ConvertInput(
                workspace,
                tempDir.resolve("out"),
                resourceOverrides = ManualResourceOverrides(
                    audioFile = manualAudio,
                    jacketFile = manualJacket,
                    backgroundFile = manualBackground,
                ),
            ),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.entryNames()
            assertTrue("test_song/manual_audio.ogg" in entries)
            assertTrue("test_song/manual_jacket.jpg" in entries)
            assertTrue("test_song/manual_bg.jpg" in entries)

            val project = zip.readTextEntry("test_song/project.arcproj")
            assertTrue(project.contains("""chartPath: "2.aff""""))
            assertTrue(project.contains("""audioPath: "manual_audio.ogg""""))
            assertTrue(project.contains("""jacketPath: "manual_jacket.jpg""""))
            assertTrue(project.contains("""backgroundPath: "manual_bg.jpg""""))
        }
    }

    @Test
    fun successfulConversionIncludesScenecontrolJsonWhenExportSucceeds() {
        val workspace = createCompleteWorkspace(affBody = scenecontrolAffBody())

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.entryNames()
            assertTrue("test_song/2.sc.json" in entries)
            assertNotNull(zip.getEntry("test_song/2.sc.json"))
        }
    }

    @Test
    fun scenecontrolExportFailureWarnsButDoesNotFailConversion() {
        val workspace = createCompleteWorkspace(
            affBody = """
                timing(0,100.00,4.00);
                scenecontrol(0,enwidenlanes);
            """.trimIndent(),
        )

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
        )

        assertTrue(result is ConvertResult.Success)
        assertTrue(result.outputFile.isFile)
        assertTrue(result.warnings.any { it.contains("Scenecontrol export failed") })
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.entryNames()
            assertTrue("test_song/2.aff" in entries)
            assertNull(zip.getEntry("test_song/2.sc.json"))
        }
    }

    @Test
    fun failedConversionKeepsWorkspaceWhenRequested() {
        val workspace = createCompleteWorkspace(includeAudio = false)

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
            ConvertOptions(keepWorkspaceOnFailure = true),
        )

        assertTrue(result is ConvertResult.Failed)
        assertEquals(workspace, result.workspaceDir)
        assertTrue(workspace.resolve(TempWorkspace.PROCESSED_AFF_DIR).exists())
    }

    @Test
    fun successfulConversionCleansProcessedAff() {
        val workspace = createCompleteWorkspace()

        val result = EtoileBridgeConverter.convert(
            ConvertInput(workspace, tempDir.resolve("out")),
            ConvertOptions(cleanWorkspaceOnSuccess = true),
        )

        assertTrue(result is ConvertResult.Success)
        assertTrue(!workspace.resolve(TempWorkspace.PROCESSED_AFF_DIR).exists())
    }

    private fun createCompleteWorkspace(
        songId: String = "test_song",
        title: String = "Test Song",
        artist: String = "Composer",
        bpm: String = "100",
        bpmBase: Double = 100.0,
        includeAudio: Boolean = true,
        includeJacket: Boolean = true,
        includeBackground: Boolean = true,
        affBody: String = "timing(0,100.00,4.00);",
        jacketFileName: String = "base.jpg",
        backgroundFileName: String = "bg.png",
        extraImageFileNames: List<String> = emptyList(),
        extraAffFileNames: List<String> = emptyList(),
        songBg: String = "bg",
        side: Int = 0,
    ): File {
        val workspace = tempDir.resolve("workspace_${System.nanoTime()}").apply { mkdirs() }
        workspace.resolve("songlist").writeText(
            songlistJson(
                songId = songId,
                title = title,
                artist = artist,
                bpm = bpm,
                bpmBase = bpmBase,
                bg = songBg,
                side = side,
            ),
            Charsets.UTF_8,
        )
        writeAff(workspace.resolve("2.aff"), affBody)
        if (includeAudio) workspace.resolve("base.ogg").writeBytes(byteArrayOf(1, 2, 3))
        if (includeJacket) workspace.resolve(jacketFileName).writeBytes(byteArrayOf(4, 5, 6))
        if (includeBackground) workspace.resolve(backgroundFileName).writeBytes(byteArrayOf(7, 8, 9))
        extraImageFileNames.forEach { workspace.resolve(it).writeBytes(byteArrayOf(10, 11, 12)) }
        extraAffFileNames.forEach { writeAff(workspace.resolve(it)) }
        return workspace
    }

    private fun createUsseewaWorkspace(): File {
        val workspace = tempDir.resolve("usseewa_${System.nanoTime()}").apply { mkdirs() }
        workspace.resolve("songlist.json").writeText(
            songlistJson(
                songId = "USSEEWACantonese",
                title = "USSEEWA Cantonese",
                artist = "Ado",
                bpm = "178",
                bpmBase = 178.0,
                bg = "macula_light_a",
            ).replace(
                """"bg": "macula_light_a",""",
                """"bg": "macula_light_a",
            "bg_inverse": "missing_inverse",""",
            ),
            Charsets.UTF_8,
        )
        workspace.resolve("packlist.json").writeText("""{"packs":[]}""", Charsets.UTF_8)
        workspace.resolve("macula_conflict_a.jpg").writeBytes(byteArrayOf(7, 8, 9))
        val songDir = workspace.resolve("USSEEWACantonese").apply { mkdirs() }
        writeAff(songDir.resolve("2.aff"))
        songDir.resolve("2-咖蛇精度.aff").writeText("AudioOffset:0\n-\ntiming(0,100.00,4.00);\n", Charsets.UTF_8)
        songDir.resolve("base.ogg").writeBytes(byteArrayOf(1, 2, 3))
        songDir.resolve("1080_base.jpg").writeBytes(byteArrayOf(4, 5, 6))
        songDir.resolve("1080_base_256.jpg").writeBytes(byteArrayOf(10, 11, 12))
        return workspace
    }

    private fun writeAff(file: File, body: String = "timing(0,100.00,4.00);") {
        file.writeText("AudioOffset:0\n-\n$body\n", Charsets.UTF_8)
    }

    private fun scenecontrolAffBody(): String =
        """
        timing(0,100.00,4.00);
        scenecontrol(0,enwidencamera,1000,1);
        scenecontrol(500,enwidenlanes,1000,1);
        scenecontrol(5000,trackdisplay,1000,0);
        scenecontrol(10000,trackdisplay,1000,255);
        """.trimIndent()

    private fun songlistJson(
        songId: String = "test_song",
        title: String = "Test Song",
        artist: String = "Composer",
        bpm: String = "100",
        bpmBase: Double = 100.0,
        bg: String = "bg",
        side: Int = 0,
    ): String =
        """
        {
          "songs": [{
            "id": "$songId",
            "title_localized": {"en": "$title"},
            "artist": "$artist",
            "bpm": "$bpm",
            "bpm_base": $bpmBase,
            "set": "single",
            "audioPreview": 0,
            "audioPreviewEnd": 5000,
            "side": $side,
            "bg": "$bg",
            "difficulties": [{
              "ratingClass": 2,
              "chartDesigner": "Charter",
              "jacketDesigner": "Illustrator",
              "rating": 9,
              "ratingPlus": true,
              "jacketOverride": false,
              "audioOverride": false
            }]
          }]
        }
        """.trimIndent()
}

private fun ZipFile.entryNames(): Set<String> =
    entries().asSequence().map { it.name }.toSet()

private fun ZipFile.readTextEntry(name: String): String {
    val entry = getEntry(name)
    requireNotNull(entry) { "Missing zip entry: $name" }
    return getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

private fun String.containsYamlValue(key: String, value: String): Boolean =
    if (value.isEmpty()) {
        contains("""$key: """"") || contains("$key: ''")
    } else {
        contains("$key: $value") || contains("""$key: "$value"""")
    }

private fun String.hasImageExtension(): Boolean =
    endsWith(".png", ignoreCase = true) ||
        endsWith(".jpg", ignoreCase = true) ||
        endsWith(".jpeg", ignoreCase = true)
