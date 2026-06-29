package com.zeerqi27.etoilebridge.core

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BundleCoreTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun scansOfficialArcaeaPackWithMultipleSongIds() {
        val pack = createOfficialPack()

        val scan = PackBundleScanner().scanOfficialPack(pack)

        assertEquals(listOf("alpha", "beta"), scan.entries.map { it.songId })
        assertTrue(scan.entries.all { it.canConvert })
        assertTrue(scan.entries.all { it.audioFile != null })
        assertTrue(scan.entries.all { it.jacketFile != null })
        assertTrue(scan.entries.any { it.backgroundFile?.name == "alpha_bg.jpg" })
    }

    @Test
    fun officialPackConversionOutputsMultipleLevelEntries() {
        val pack = createOfficialPack()
        val out = tempDir.resolve("out")

        val result = PackBundleConverter().convertOfficialPack(
            BundleInput(
                workspaceDir = pack,
                outputFile = out,
                options = BundleOptions(publisherId = "testpub", outputFileName = "bundle.arcpkg"),
            )
        )

        assertTrue(result is BundleConvertResult.Success, result.toString())
        assertEquals(2, result.convertedCount)
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.bundleEntryNames()
            assertTrue("index.yml" in entries)
            assertTrue("alpha/project.arcproj" in entries)
            assertTrue("beta/project.arcproj" in entries)
            val scan = ArcpkgBundleMerger().scan(result.outputFile)
            val packEntry = scan.packEntries.single()
            val pack = zip.readBundleTextEntry("pack/pack.yml")
            assertEquals("pack", packEntry.directory)
            assertEquals("testpub.pack.pack", packEntry.identifier)
            assertEquals("pack.png", packEntry.imagePath)
            assertEquals("Pack", packEntry.packName)
            assertEquals(setOf("testpub.alpha", "testpub.beta"), packEntry.levelIdentifiers.toSet())
            assertTrue("pack/pack.png" in entries)
            assertTrue(pack.contains("pack.png"))
            assertTrue(pack.contains("Pack"))
            assertTrue(pack.contains("testpub.alpha"))
            assertTrue(pack.contains("testpub.beta"))
            val index = zip.readBundleTextEntry("index.yml")
            assertTrue(index.contains("testpub.alpha"))
            assertTrue(index.contains("testpub.beta"))
        }
    }

    @Test
    fun officialPackConversionOnlyPacksEnabledEntries() {
        val pack = createOfficialPack()

        val result = PackBundleConverter().convertOfficialPack(
            BundleInput(
                workspaceDir = pack,
                outputFile = tempDir.resolve("enabled_only"),
                options = BundleOptions(
                    publisherId = "testpub",
                    outputFileName = "enabled.arcpkg",
                    entryOverrides = mapOf("beta" to BundleEntryOverride(enabled = false)),
                ),
            )
        )

        assertTrue(result is BundleConvertResult.Success, result.toString())
        assertEquals(1, result.convertedCount)
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.bundleEntryNames()
            assertTrue("alpha/project.arcproj" in entries)
            assertFalse("beta/project.arcproj" in entries)
            val packEntry = ArcpkgBundleMerger().scan(result.outputFile).packEntries.single()
            assertEquals(setOf("testpub.alpha"), packEntry.levelIdentifiers.toSet())
        }
    }

    @Test
    fun officialPackConversionUsesEditedLevelIdInIndexAndPackYaml() {
        val pack = createOfficialPack()

        val result = PackBundleConverter().convertOfficialPack(
            BundleInput(
                workspaceDir = pack,
                outputFile = tempDir.resolve("level_id"),
                options = BundleOptions(
                    publisherId = "testpub",
                    outputFileName = "level_id.arcpkg",
                    entryOverrides = mapOf("alpha" to BundleEntryOverride(levelId = "alpha_custom")),
                ),
            )
        )

        assertTrue(result is BundleConvertResult.Success, result.toString())
        val scan = ArcpkgBundleMerger().scan(result.outputFile)
        assertTrue(scan.levelEntries.any { it.identifier == "testpub.alpha_custom" })
        assertTrue(scan.packEntries.single().levelIdentifiers.contains("testpub.alpha_custom"))
    }

    @Test
    fun officialPackConversionUsesEditedMetadataInProjectArcproj() {
        val pack = createOfficialPack()

        val result = PackBundleConverter().convertOfficialPack(
            BundleInput(
                workspaceDir = pack,
                outputFile = tempDir.resolve("metadata_edit"),
                options = BundleOptions(
                    publisherId = "testpub",
                    outputFileName = "metadata_edit.arcpkg",
                    entryOverrides = mapOf(
                        "alpha" to BundleEntryOverride(
                            title = "Edited Alpha",
                            artist = "Edited Composer",
                            chartOverrides = mapOf(
                                2 to BundleChartOverride(
                                    charter = "Edited Charter",
                                    illustrator = "Edited Illustrator",
                                    difficulty = "Future 10+",
                                    chartConstant = 10.7f,
                                )
                            ),
                        )
                    ),
                ),
            )
        )

        assertTrue(result is BundleConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val project = zip.readBundleTextEntry("alpha/project.arcproj")
            assertTrue(project.contains("Edited Alpha"))
            assertTrue(project.contains("Edited Composer"))
            assertTrue(project.contains("Edited Charter"))
            assertTrue(project.contains("Edited Illustrator"))
            assertTrue(project.contains("Future 10+"))
            assertTrue(project.contains("10.7"))
        }
    }

    @Test
    fun scansOfficialPackSongWithMultipleChartsSeparately() {
        val pack = createMultiDifficultyOfficialPack()

        val scan = PackBundleScanner().scanOfficialPack(pack)

        val entry = scan.entries.single()
        assertEquals("multi", entry.songId)
        assertEquals(2, entry.charts.size)
        assertEquals(listOf("2.aff", "3.aff"), entry.charts.map { it.chartPath })
        assertEquals(listOf("Future 10", "Beyond 11+"), entry.charts.map { it.difficulty })
        assertEquals(listOf(10.0f, 11.7f), entry.charts.map { it.chartConstant })
        assertEquals(listOf("Future Charter", "Beyond Charter"), entry.charts.map { it.charter })
        assertFalse(entry.difficultySummary.contains(","))
    }

    @Test
    fun officialPackConversionWritesMultipleChartsInOneProjectArcproj() {
        val pack = createMultiDifficultyOfficialPack()

        val result = PackBundleConverter().convertOfficialPack(
            BundleInput(
                workspaceDir = pack,
                outputFile = tempDir.resolve("multi_charts"),
                options = BundleOptions(publisherId = "testpub", outputFileName = "multi_charts.arcpkg"),
            )
        )

        assertTrue(result is BundleConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.bundleEntryNames()
            assertTrue("multi/project.arcproj" in entries)
            val project = zip.readBundleTextEntry("multi/project.arcproj")
            assertEquals(2, Regex("chartPath:").findAll(project).count())
            assertTrue(project.containsChartPath("2.aff"))
            assertTrue(project.containsYamlValue("difficulty", "Future 10"))
            assertTrue(project.contains("chartConstant: 10"))
            assertTrue(project.containsYamlValue("charter", "Future Charter"))
            assertTrue(project.containsChartPath("3.aff"))
            assertTrue(project.containsYamlValue("difficulty", "Beyond 11+"))
            assertTrue(project.contains("chartConstant: 11.7"))
            assertTrue(project.containsYamlValue("charter", "Beyond Charter"))

            val scan = ArcpkgBundleMerger().scan(result.outputFile)
            assertEquals(1, scan.levelEntries.size)
            assertEquals(listOf("testpub.multi"), scan.packEntries.single().levelIdentifiers)
        }
    }

    @Test
    fun officialPackConversionCanDisableOneChartInsideSong() {
        val pack = createMultiDifficultyOfficialPack()

        val result = PackBundleConverter().convertOfficialPack(
            BundleInput(
                workspaceDir = pack,
                outputFile = tempDir.resolve("one_chart"),
                options = BundleOptions(
                    publisherId = "testpub",
                    outputFileName = "one_chart.arcpkg",
                    entryOverrides = mapOf(
                        "multi" to BundleEntryOverride(
                            chartOverrides = mapOf(3 to BundleChartOverride(enabled = false)),
                        )
                    ),
                ),
            )
        )

        assertTrue(result is BundleConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val project = zip.readBundleTextEntry("multi/project.arcproj")
            assertEquals(1, Regex("chartPath:").findAll(project).count())
            assertTrue(project.containsChartPath("2.aff"))
            assertFalse(project.containsChartPath("3.aff"))
        }
    }

    @Test
    fun officialPackConversionFailsWhenNoEntriesAreEnabled() {
        val pack = createOfficialPack()

        val result = PackBundleConverter().convertOfficialPack(
            BundleInput(
                workspaceDir = pack,
                outputFile = tempDir.resolve("none"),
                options = BundleOptions(
                    entryOverrides = mapOf(
                        "alpha" to BundleEntryOverride(enabled = false),
                        "beta" to BundleEntryOverride(enabled = false),
                    ),
                ),
            )
        )

        assertTrue(result is BundleConvertResult.Failed)
        assertTrue(result.message.contains("No enabled songs"))
    }

    @Test
    fun mergeMultipleSingleArcpkgKeepsResourcesAndIndexEntries() {
        val arcpkgDir = tempDir.resolve("arcpkgs").apply { mkdirs() }
        createSingleArcpkg("alpha", arcpkgDir)
        createSingleArcpkg("beta", arcpkgDir)

        val result = ArcpkgBundleMerger().merge(arcpkgDir, tempDir.resolve("merged.arcpkg"))

        assertTrue(result is BundleConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.bundleEntryNames()
            assertTrue("alpha/project.arcproj" in entries)
            assertTrue("beta/project.arcproj" in entries)
            val scan = ArcpkgBundleMerger().scan(result.outputFile)
            val packEntry = scan.packEntries.single()
            assertTrue("${packEntry.directory}/pack.yml" in entries)
            assertTrue("${packEntry.directory}/pack.png" in entries)
            val index = zip.readBundleTextEntry("index.yml")
            val pack = zip.readBundleTextEntry("${packEntry.directory}/pack.yml")
            assertEquals("pack.png", packEntry.imagePath)
            assertEquals(setOf("etoilebridge.alpha", "etoilebridge.beta"), packEntry.levelIdentifiers.toSet())
            assertTrue(index.contains("alpha"))
            assertTrue(index.contains("beta"))
            assertTrue(pack.contains("pack.png"))
            assertTrue(pack.contains("etoilebridge.alpha"))
            assertTrue(pack.contains("etoilebridge.beta"))
        }
    }

    @Test
    fun scanArcpkgReadsChartsFromProjectArcproj() {
        val arcpkg = createMultiChartArcpkg("frozen", tempDir.resolve("multi_chart_source.arcpkg"))

        val scan = ArcpkgBundleMerger().scan(arcpkg)

        val level = scan.levelEntries.single()
        assertEquals("source.frozen", level.identifier)
        assertEquals("Frozen Title", level.title)
        assertEquals("Frozen Composer", level.artist)
        assertEquals(listOf("2.aff", "4.aff"), level.charts.map { it.chartPath })
        assertEquals(listOf("Future 10", "Eternal 10+"), level.charts.map { it.difficulty })
        assertEquals(listOf(10.6f, 10.7f), level.charts.map { it.chartConstant })
        assertEquals(listOf("Frozen Charter", "Frozen ETR Charter"), level.charts.map { it.charter })
        assertFalse(level.difficultySummary.contains("ArcCreate level"))
        assertFalse(level.charts.any { it.chartPath == "project.arcproj" })
    }

    @Test
    fun mergeArcpkgPreservesSourceProjectChartsWhenMetadataIsUnchanged() {
        val arcpkgDir = tempDir.resolve("multi_chart_merge").apply { mkdirs() }
        createMultiChartArcpkg("frozen", arcpkgDir.resolve("frozen.arcpkg"))

        val result = ArcpkgBundleMerger().merge(
            input = arcpkgDir,
            outputFile = tempDir.resolve("merged_multi_chart.arcpkg"),
            options = BundleOptions(
                publisherId = "etoilebridge",
                entryOverrides = emptyMap(),
            ),
        )

        assertTrue(result is BundleConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val project = zip.readBundleTextEntry("frozen/project.arcproj")
            assertEquals(2, Regex("chartPath:").findAll(project).count())
            assertTrue(project.containsChartPath("2.aff"))
            assertTrue(project.containsYamlValue("difficulty", "Future 10"))
            assertTrue(project.contains("chartConstant: 10.6"))
            assertTrue(project.containsYamlValue("charter", "Frozen Charter"))
            assertTrue(project.containsChartPath("4.aff"))
            assertTrue(project.containsYamlValue("difficulty", "Eternal 10+"))
            assertTrue(project.contains("chartConstant: 10.7"))
            assertTrue(project.containsYamlValue("charter", "Frozen ETR Charter"))
            assertFalse(project.contains("ArcCreate level"))
        }
        val validation = BundleOutputValidator().validateBundleArcpkg(result.outputFile)
        assertTrue(validation.valid, validation.errors.joinToString())
    }

    @Test
    fun mergeSkipsDisabledLevelAndUsesEditedLevelId() {
        val arcpkgDir = tempDir.resolve("merge_enabled").apply { mkdirs() }
        createSingleArcpkg("alpha", arcpkgDir)
        createSingleArcpkg("beta", arcpkgDir)

        val result = ArcpkgBundleMerger().merge(
            input = arcpkgDir,
            outputFile = tempDir.resolve("merge_enabled.arcpkg"),
            options = BundleOptions(
                publisherId = "testpub",
                entryOverrides = mapOf(
                    "alpha" to BundleEntryOverride(levelId = "alpha_custom"),
                    "beta" to BundleEntryOverride(enabled = false),
                ),
            ),
        )

        assertTrue(result is BundleConvertResult.Success, result.toString())
        val scan = ArcpkgBundleMerger().scan(result.outputFile)
        assertEquals(listOf("testpub.alpha_custom"), scan.levelEntries.map { it.identifier })
        assertEquals(listOf("testpub.alpha_custom"), scan.packEntries.single().levelIdentifiers)
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.bundleEntryNames()
            assertTrue("alpha/project.arcproj" in entries)
            assertFalse("beta/project.arcproj" in entries)
        }
    }

    @Test
    fun mergeRenamesDirectoryAndIdentifierConflicts() {
        val arcpkgDir = tempDir.resolve("conflicts").apply { mkdirs() }
        val first = createSingleArcpkg("same", arcpkgDir.resolve("a"))
        val second = createSingleArcpkg("same", arcpkgDir.resolve("b"))
        first.copyTo(arcpkgDir.resolve("same_a.arcpkg"))
        second.copyTo(arcpkgDir.resolve("same_b.arcpkg"))

        val result = ArcpkgBundleMerger().merge(arcpkgDir, tempDir.resolve("merged_conflict.arcpkg"))

        assertTrue(result is BundleConvertResult.Success, result.toString())
        assertTrue(result.warnings.any { it.contains("Directory conflict") })
        assertTrue(result.warnings.any { it.contains("Identifier conflict") })
        ZipFile(result.outputFile).use { zip ->
            val entries = zip.bundleEntryNames()
            assertTrue("same/project.arcproj" in entries)
            assertTrue("same_2/project.arcproj" in entries)
            val index = zip.readBundleTextEntry("index.yml")
            val packEntry = ArcpkgBundleMerger().scan(result.outputFile).packEntries.single()
            val pack = zip.readBundleTextEntry("${packEntry.directory}/pack.yml")
            assertTrue(index.contains("same_2"))
            assertTrue(index.contains("etoilebridge.same_2"))
            assertTrue(pack.contains("etoilebridge.same"))
            assertTrue(pack.contains("etoilebridge.same_2"))
        }
    }

    @Test
    fun badArcpkgDoesNotCrashScan() {
        val dir = tempDir.resolve("bad_scan").apply { mkdirs() }
        createSingleArcpkg("valid", dir)
        dir.resolve("bad.arcpkg").writeText("not a zip")

        val scan = ArcpkgBundleMerger().scan(dir)

        assertEquals(1, scan.validLevelCount)
        assertFalse(scan.sourceFiles.first { it.sourceFile.name == "bad.arcpkg" }.readable)
    }

    @Test
    fun mergeDoesNotDuplicateSourcePackEntryAndUsesSourcePackMetadataCandidate() {
        val arcpkgDir = tempDir.resolve("source_pack").apply { mkdirs() }
        createPackedArcpkgWithPackEntry(arcpkgDir.resolve("source.arcpkg"))

        val scan = ArcpkgBundleMerger().scan(arcpkgDir)
        assertEquals("Source Pack", scan.packNameCandidate)
        assertEquals(1, scan.sourceFiles.single().packEntryCount)
        assertTrue(scan.sourceFiles.single().packImageExists)

        val result = ArcpkgBundleMerger().merge(arcpkgDir, tempDir.resolve("merged_source_pack.arcpkg"))

        assertTrue(result is BundleConvertResult.Success, result.toString())
        ZipFile(result.outputFile).use { zip ->
            val scan = ArcpkgBundleMerger().scan(result.outputFile)
            val packEntry = scan.packEntries.single()
            assertEquals(1, scan.packEntries.size)
            assertEquals(1, scan.levelEntries.size)
            val pack = zip.readBundleTextEntry("${packEntry.directory}/pack.yml")
            assertEquals("Source Pack", packEntry.packName)
            assertEquals("pack.png", packEntry.imagePath)
            assertTrue(pack.contains("Source Pack"))
            assertTrue(pack.contains("etoilebridge.song"))
            assertTrue("${packEntry.directory}/pack.png" in zip.bundleEntryNames())
        }
    }

    @Test
    fun packNameWithSlashIsPreservedWhilePackIdIsSanitized() {
        val arcpkgDir = tempDir.resolve("slash_name").apply { mkdirs() }
        createSingleArcpkg("alpha", arcpkgDir)

        val result = ArcpkgBundleMerger().merge(
            input = arcpkgDir,
            outputFile = tempDir.resolve("slash_bundle.arcpkg"),
            options = BundleOptions(
                publisherId = "etoilebridge",
                packName = "vivid/stasis",
                packId = "vivid/stasis",
            ),
        )

        assertTrue(result is BundleConvertResult.Success, result.toString())
        val scan = ArcpkgBundleMerger().scan(result.outputFile)
        val packEntry = scan.packEntries.single()
        assertEquals("vivid_stasis", packEntry.directory)
        assertEquals("etoilebridge.vivid_stasis.pack", packEntry.identifier)
        assertEquals("vivid/stasis", packEntry.packName)
    }

    @Test
    fun editExistingPackAppendsSingleArcpkgAndKeepsOnePackEntry() {
        val base = tempDir.resolve("base_pack.arcpkg")
        createPackedArcpkgWithPackEntry(base)
        val addDir = tempDir.resolve("edit_add").apply { mkdirs() }
        createSingleArcpkg("extra", addDir)

        val scan = ArcpkgBundleMerger().scanExistingPack(base, addDir)
        assertEquals(1, scan.existingLevelCount)
        assertEquals(1, scan.addedLevelCount)
        assertEquals(2, scan.finalLevelCount)

        val result = ArcpkgBundleMerger().editExistingPack(
            basePack = base,
            addInput = addDir,
            outputFile = tempDir.resolve("edited_pack.arcpkg"),
            options = BundleOptions(publisherId = "testpub", outputFileName = "edited_pack.arcpkg"),
        )

        assertTrue(result is BundleConvertResult.Success, result.toString())
        val outputScan = ArcpkgBundleMerger().scan(result.outputFile)
        assertEquals(1, outputScan.packEntries.size)
        assertEquals(2, outputScan.levelEntries.size)
        assertEquals(outputScan.levelEntries.map { it.identifier }.toSet(), outputScan.packEntries.single().levelIdentifiers.toSet())
        assertTrue(BundleOutputValidator().validateBundleArcpkg(result.outputFile).valid)
    }

    @Test
    fun editExistingPackIgnoresPackEntryFromAddedPackSource() {
        val base = tempDir.resolve("base_pack_ignore.arcpkg")
        createPackedArcpkgWithPackEntry(base)
        val addPack = tempDir.resolve("added_pack_source.arcpkg")
        createPackedArcpkgWithPackEntry(addPack)

        val result = ArcpkgBundleMerger().editExistingPack(
            basePack = base,
            addInput = addPack,
            outputFile = tempDir.resolve("edited_ignored_pack.arcpkg"),
            options = BundleOptions(publisherId = "testpub", outputFileName = "edited_ignored_pack.arcpkg"),
        )

        assertTrue(result is BundleConvertResult.Success, result.toString())
        assertTrue(result.warnings.any { it.contains("Ignored pack entry from added source") })
        val outputScan = ArcpkgBundleMerger().scan(result.outputFile)
        assertEquals(1, outputScan.packEntries.size)
        assertEquals(2, outputScan.levelEntries.size)
    }

    @Test
    fun editExistingPackRenamesDirectoryAndIdentifierConflicts() {
        val base = tempDir.resolve("base_pack_conflict.arcpkg")
        createPackedArcpkgWithPackEntry(base)
        val addDir = tempDir.resolve("edit_conflict_add").apply { mkdirs() }
        createSingleArcpkg("song", addDir)

        val result = ArcpkgBundleMerger().editExistingPack(
            basePack = base,
            addInput = addDir,
            outputFile = tempDir.resolve("edited_conflict_pack.arcpkg"),
            options = BundleOptions(publisherId = "testpub", outputFileName = "edited_conflict_pack.arcpkg"),
        )

        assertTrue(result is BundleConvertResult.Success, result.toString())
        assertTrue(result.warnings.any { it.contains("Directory conflict") })
        assertTrue(result.warnings.any { it.contains("Identifier conflict") })
        val outputScan = ArcpkgBundleMerger().scan(result.outputFile)
        assertTrue(outputScan.levelEntries.any { it.directory == "song_2" })
        assertTrue(outputScan.packEntries.single().levelIdentifiers.any { it.endsWith("song_2") })
    }

    private fun createOfficialPack(): File {
        val pack = tempDir.resolve("official_pack").apply { mkdirs() }
        pack.resolve("songlist").writeText(
            """
            {"songs":[
              ${songObject("alpha", "Alpha", "alpha_bg")},
              ${songObject("beta", "Beta", "beta_bg")}
            ]}
            """.trimIndent(),
            Charsets.UTF_8,
        )
        pack.resolve("packlist").writeText("""{"packs":[{"id":"pack","name_localized":{"en":"Pack"}}]}""", Charsets.UTF_8)
        val bg = pack.resolve("bg").apply { mkdirs() }
        bg.resolve("alpha_bg.jpg").writeBytes(byteArrayOf(1))
        bg.resolve("beta_bg.jpg").writeBytes(byteArrayOf(2))
        createSongDir(pack.resolve("alpha"))
        createSongDir(pack.resolve("beta"))
        return pack
    }

    private fun createMultiDifficultyOfficialPack(): File {
        val pack = tempDir.resolve("multi_official_pack").apply { mkdirs() }
        pack.resolve("songlist").writeText(
            """
            {"songs":[{
              "id":"multi",
              "title_localized":{"en":"Multi"},
              "artist":"Composer",
              "bpm":"150",
              "bpm_base":150,
              "set":"pack",
              "audioPreview":0,
              "audioPreviewEnd":5000,
              "side":0,
              "bg":"multi_bg",
              "difficulties":[
                {
                  "ratingClass":2,
                  "chartDesigner":"Future Charter",
                  "jacketDesigner":"Future Illustrator",
                  "rating":10,
                  "ratingPlus":false
                },
                {
                  "ratingClass":3,
                  "chartDesigner":"Beyond Charter",
                  "jacketDesigner":"Beyond Illustrator",
                  "rating":11,
                  "ratingPlus":true
                }
              ]
            }]}
            """.trimIndent(),
            Charsets.UTF_8,
        )
        pack.resolve("packlist").writeText("""{"packs":[{"id":"pack","name_localized":{"en":"Pack"}}]}""", Charsets.UTF_8)
        pack.resolve("multi_bg.jpg").writeBytes(byteArrayOf(1))
        val song = pack.resolve("multi").apply { mkdirs() }
        writeAff(song.resolve("2.aff"))
        writeAff(song.resolve("3.aff"))
        song.resolve("base.ogg").writeBytes(byteArrayOf(1, 2, 3))
        song.resolve("base.jpg").writeBytes(byteArrayOf(4, 5, 6))
        return pack
    }

    private fun createSingleArcpkg(songId: String, outputDir: File): File {
        outputDir.mkdirs()
        val workspace = tempDir.resolve("workspace_${songId}_${System.nanoTime()}").apply { mkdirs() }
        workspace.resolve("songlist").writeText("""{"songs":[${songObject(songId, songId, "bg")}]}""", Charsets.UTF_8)
        createSongDir(workspace, includeSonglist = false)
        val result = EtoileBridgeConverter.convert(ConvertInput(workspace, outputDir))
        assertTrue(result is ConvertResult.Success, result.toString())
        return result.outputFile
    }

    private fun createPackedArcpkgWithPackEntry(outputFile: File) {
        val sourceDir = tempDir.resolve("source_levels_${System.nanoTime()}").apply { mkdirs() }
        createSingleArcpkg("song", sourceDir)
        val cover = tempDir.resolve("source_pack_cover.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val result = ArcpkgBundleMerger().merge(
            input = sourceDir,
            outputFile = outputFile,
            options = BundleOptions(
                publisherId = "sourcepub",
                packName = "Source Pack",
                packId = "source_pack",
                packImageFile = cover,
            ),
        )
        assertTrue(result is BundleConvertResult.Success, result.toString())
    }

    private fun createMultiChartArcpkg(songId: String, outputFile: File): File {
        outputFile.parentFile?.mkdirs()
        ZipOutputStream(outputFile.outputStream()).use { out ->
            fun put(name: String, text: String) {
                out.putNextEntry(ZipEntry(name))
                out.write(text.toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
            put(
                "index.yml",
                """
                - directory: $songId
                  identifier: source.$songId
                  settingsFile: project.arcproj
                  type: level
                """.trimIndent(),
            )
            put(
                "$songId/project.arcproj",
                """
                lastOpenedChartPath: 4.aff
                charts:
                - chartPath: 2.aff
                  audioPath: base.ogg
                  jacketPath: base.jpg
                  backgroundPath: bg.jpg
                  baseBpm: 134
                  bpmText: 134
                  title: Frozen Title
                  composer: Frozen Composer
                  charter: Frozen Charter
                  illustrator: Frozen Illustrator
                  difficulty: Future 10
                  chartConstant: 10.6
                  difficultyColor: '#482B54FF'
                - chartPath: 4.aff
                  audioPath: base.ogg
                  jacketPath: base.jpg
                  backgroundPath: bg.jpg
                  baseBpm: 134
                  bpmText: 134
                  title: Frozen Title
                  composer: Frozen Composer
                  charter: Frozen ETR Charter
                  illustrator: Frozen ETR Illustrator
                  difficulty: Eternal 10+
                  chartConstant: 10.7
                  difficultyColor: '#482B54FF'
                """.trimIndent(),
            )
            put("$songId/2.aff", "AudioOffset:0\n-\ntiming(0,100.00,4.00);\n")
            put("$songId/4.aff", "AudioOffset:0\n-\ntiming(0,100.00,4.00);\n")
            put("$songId/base.ogg", "audio")
            put("$songId/base.jpg", "jacket")
            put("$songId/bg.jpg", "background")
        }
        return outputFile
    }

    private fun createSongDir(dir: File, includeSonglist: Boolean = false) {
        dir.mkdirs()
        if (includeSonglist) dir.resolve("songlist").writeText("""{"songs":[${songObject(dir.name, dir.name, "bg")}]}""")
        writeAff(dir.resolve("2.aff"))
        dir.resolve("base.ogg").writeBytes(byteArrayOf(1, 2, 3))
        dir.resolve("base.jpg").writeBytes(byteArrayOf(4, 5, 6))
        dir.resolve("bg.jpg").writeBytes(byteArrayOf(7, 8, 9))
    }

    private fun songObject(id: String, title: String, bg: String): String =
        """
        {
          "id":"$id",
          "title_localized":{"en":"$title"},
          "artist":"Composer",
          "bpm":"128",
          "bpm_base":128,
          "set":"pack",
          "audioPreview":0,
          "audioPreviewEnd":5000,
          "side":0,
          "bg":"$bg",
          "difficulties":[{
            "ratingClass":2,
            "chartDesigner":"Charter",
            "jacketDesigner":"Illustrator",
            "rating":9,
            "ratingPlus":false
          }]
        }
        """.trimIndent()

    private fun writeAff(file: File) {
        file.writeText("AudioOffset:0\n-\ntiming(0,100.00,4.00);\n", Charsets.UTF_8)
    }
}

private fun ZipFile.bundleEntryNames(): Set<String> =
    entries().asSequence().map { it.name }.toSet()

private fun ZipFile.readBundleTextEntry(name: String): String {
    val entry = getEntry(name)
    requireNotNull(entry) { "Missing zip entry: $name" }
    return getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

private fun String.containsChartPath(path: String): Boolean =
    contains("chartPath: $path") || contains("chartPath: \"$path\"")

private fun String.containsYamlValue(key: String, value: String): Boolean =
    contains("$key: $value") || contains("$key: \"$value\"")
