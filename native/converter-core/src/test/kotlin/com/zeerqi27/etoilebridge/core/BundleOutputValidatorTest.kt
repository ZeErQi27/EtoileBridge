package com.zeerqi27.etoilebridge.core

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BundleOutputValidatorTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun validatesCorrectBundle() {
        val file = tempDir.resolve("valid.arcpkg")
        writeBundle(file)

        val report = BundleOutputValidator().validateBundleArcpkg(file)

        assertTrue(report.valid, report.errors.joinToString())
        assertTrue(report.packImageExists)
        assertTrue(report.levelIdentifiersMatch)
    }

    @Test
    fun failsWhenIndexIsMissing() {
        val file = tempDir.resolve("no_index.arcpkg")
        zip(file) { put("pack/pack.yml", "packName: Pack\nimagePath: pack.png\nlevelIdentifiers: []\n") }

        val report = BundleOutputValidator().validateBundleArcpkg(file)

        assertFalse(report.valid)
        assertTrue(report.errors.any { it.contains("index.yml") })
    }

    @Test
    fun failsWhenPackEntryIsMissing() {
        val file = tempDir.resolve("no_pack_entry.arcpkg")
        writeBundle(file, includePackEntry = false)

        val report = BundleOutputValidator().validateBundleArcpkg(file)

        assertFalse(report.valid)
        assertTrue(report.errors.any { it.contains("type: pack") })
    }

    @Test
    fun failsWhenPackYmlIsMissing() {
        val file = tempDir.resolve("no_pack_yml.arcpkg")
        writeBundle(file, includePackYml = false)

        val report = BundleOutputValidator().validateBundleArcpkg(file)

        assertFalse(report.valid)
        assertTrue(report.errors.any { it.contains("Pack settings file") })
    }

    @Test
    fun failsWhenPackImagePathIsMissing() {
        val file = tempDir.resolve("no_pack_image.arcpkg")
        writeBundle(file, includePackImage = false)

        val report = BundleOutputValidator().validateBundleArcpkg(file)

        assertFalse(report.valid)
        assertTrue(report.errors.any { it.contains("Pack image") })
    }

    @Test
    fun failsWhenLevelIdentifiersDoNotMatchIndexLevels() {
        val file = tempDir.resolve("mismatch.arcpkg")
        writeBundle(file, packLevelIdentifiers = listOf("pub.missing"))

        val report = BundleOutputValidator().validateBundleArcpkg(file)

        assertFalse(report.valid)
        assertTrue(report.errors.any { it.contains("missing level identifiers") || it.contains("missing from pack.yml") })
    }

    @Test
    fun failsWhenLevelSettingsFileIsMissing() {
        val file = tempDir.resolve("no_level_settings.arcpkg")
        writeBundle(file, includeLevelSettings = false)

        val report = BundleOutputValidator().validateBundleArcpkg(file)

        assertFalse(report.valid)
        assertTrue(report.errors.any { it.contains("Level settings file") })
    }

    @Test
    fun failsWhenIdentifierIsDuplicated() {
        val file = tempDir.resolve("duplicate_identifier.arcpkg")
        writeBundle(file, duplicateIdentifier = true)

        val report = BundleOutputValidator().validateBundleArcpkg(file)

        assertFalse(report.valid)
        assertTrue(report.errors.any { it.contains("Duplicate identifier") })
    }

    @Test
    fun failsWhenProjectChartsAreEmpty() {
        val file = tempDir.resolve("empty_charts.arcpkg")
        writeBundle(file, emptyCharts = true)

        val report = BundleOutputValidator().validateBundleArcpkg(file)

        assertFalse(report.valid)
        assertTrue(report.errors.any { it.contains("charts is empty") })
    }

    private fun writeBundle(
        file: File,
        includePackEntry: Boolean = true,
        includePackYml: Boolean = true,
        includePackImage: Boolean = true,
        includeLevelSettings: Boolean = true,
        duplicateIdentifier: Boolean = false,
        packLevelIdentifiers: List<String> = listOf("pub.song"),
        emptyCharts: Boolean = false,
    ) {
        zip(file) {
            put(
                "index.yml",
                buildString {
                    if (includePackEntry) {
                        appendLine("- directory: pack")
                        appendLine("  identifier: ${if (duplicateIdentifier) "pub.song" else "pub.pack"}")
                        appendLine("  settingsFile: pack.yml")
                        appendLine("  type: pack")
                    }
                    appendLine("- directory: song")
                    appendLine("  identifier: pub.song")
                    appendLine("  settingsFile: project.arcproj")
                    appendLine("  type: level")
                },
            )
            if (includePackYml) {
                put(
                    "pack/pack.yml",
                    buildString {
                        appendLine("imagePath: pack.png")
                        appendLine("levelIdentifiers:")
                        packLevelIdentifiers.forEach { appendLine("- $it") }
                        appendLine("packName: Pack")
                    },
                )
            }
            if (includePackImage) put("pack/pack.png", "image")
            if (includeLevelSettings) {
                put(
                    "song/project.arcproj",
                    if (emptyCharts) {
                        "lastOpenedChartPath: 2.aff\ncharts: []\n"
                    } else {
                        """
                        lastOpenedChartPath: 2.aff
                        charts:
                        - chartPath: 2.aff
                          audioPath: base.ogg
                          jacketPath: base.jpg
                          baseBpm: 128
                          bpmText: 128
                          title: Song
                          composer: Composer
                          difficulty: Future 9
                          difficultyColor: '#482B54FF'
                        """.trimIndent()
                    },
                )
            }
        }
    }

    private fun zip(file: File, block: ZipBuilder.() -> Unit) {
        ZipOutputStream(file.outputStream()).use { ZipBuilder(it).block() }
    }

    private class ZipBuilder(private val out: ZipOutputStream) {
        fun put(name: String, text: String) {
            out.putNextEntry(ZipEntry(name))
            out.write(text.toByteArray(Charsets.UTF_8))
            out.closeEntry()
        }
    }
}
