package com.zeerqi27.etoilebridge.core

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetadataResolverTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun songRootAndSonglistIdMatchIgnoringCaseUsesSonglistMetadata() {
        val songDir = tempDir.resolve("GetMeHigh").apply { mkdirs() }
        val songlist = SonglistParser().parse(songlistJson("getmehigh", "Get Me High", "Kobaryo"))
        val warnings = mutableListOf<String>()

        val match = MetadataResolver().matchSong(songDir, "GetMeHigh", songlist)
        val result = MetadataResolver().resolve(
            songDir = songDir,
            affFiles = mapOf(4 to songDir.resolve("4.aff")),
            requestedSongId = "GetMeHigh",
            songlist = songlist,
            packlist = null,
            manualMetadata = null,
            warnings = warnings,
        )

        assertEquals(SonglistMatchMode.IGNORE_CASE, match.mode)
        assertEquals("getmehigh", match.songlistId)
        assertTrue(warnings.any { it.contains("GetMeHigh vs getmehigh") })
        assertTrue(result is MetadataResolution.Resolved)
        assertEquals("GetMeHigh", result.metadata.songId)
        assertEquals("Get Me High", result.metadata.title)
        assertEquals("Kobaryo", result.metadata.artist)
        assertEquals("185", result.metadata.bpmText)
        assertEquals(185f, result.metadata.bpmBase)
        val diff = result.metadata.difficulties.single()
        assertEquals("Eternal 10+", diff.difficulty)
        assertEquals(10.7f, diff.chartConstant)
    }

    @Test
    fun singleSongObjectFallbackUsesMetadataWithWarning() {
        val songDir = tempDir.resolve("FolderName").apply { mkdirs() }
        val songlist = SonglistParser().parse(songlistJson("different_id", "Fallback Title", "Composer"))
        val warnings = mutableListOf<String>()

        val match = MetadataResolver().matchSong(songDir, null, songlist)
        val result = MetadataResolver().resolve(
            songDir = songDir,
            affFiles = mapOf(4 to songDir.resolve("4.aff")),
            requestedSongId = null,
            songlist = songlist,
            packlist = null,
            manualMetadata = null,
            warnings = warnings,
        )

        assertEquals(SonglistMatchMode.SINGLE_OBJECT_FALLBACK, match.mode)
        assertTrue(warnings.any { it.contains("using the only song object") })
        assertTrue(result is MetadataResolution.Resolved)
        assertEquals("Fallback Title", result.metadata.title)
        assertEquals("Composer", result.metadata.artist)
    }

    @Test
    fun multipleSongObjectsWithoutMatchNeedMetadata() {
        val songDir = tempDir.resolve("GetMeHigh").apply { mkdirs() }
        val songlist = SonglistParser().parse(
            """
            {"songs": [
              ${songObjectJson("first_song", "First", "A")},
              ${songObjectJson("second_song", "Second", "B")}
            ]}
            """.trimIndent(),
        )

        val match = MetadataResolver().matchSong(songDir, null, songlist)
        val result = MetadataResolver().resolve(
            songDir = songDir,
            affFiles = mapOf(2 to songDir.resolve("2.aff")),
            requestedSongId = null,
            songlist = songlist,
            packlist = null,
            manualMetadata = null,
        )

        assertEquals(SonglistMatchMode.NONE, match.mode)
        assertTrue(result is MetadataResolution.Need)
        assertTrue(result.missingMetadata.requiredFields.contains("title_localized"))
    }

    @Test
    fun manualMetadataOverridesIgnoreCaseMatch() {
        val songDir = tempDir.resolve("GetMeHigh").apply { mkdirs() }
        val songlist = SonglistParser().parse(songlistJson("getmehigh", "Get Me High", "Kobaryo"))

        val result = MetadataResolver().resolve(
            songDir = songDir,
            affFiles = mapOf(4 to songDir.resolve("4.aff")),
            requestedSongId = "GetMeHigh",
            songlist = songlist,
            packlist = null,
            manualMetadata = ManualMetadata(
                songId = "ManualId",
                title = "Manual Title",
                artist = "Manual Composer",
                bpmText = "200",
                bpmBase = 200f,
                difficulties = listOf(
                    ManualDifficultyMetadata(
                        ratingClass = 4,
                        difficulty = "Eternal 11",
                        chartConstant = 11.2f,
                    ),
                ),
            ),
        )

        assertTrue(result is MetadataResolution.Resolved)
        assertEquals("ManualId", result.metadata.songId)
        assertEquals("Manual Title", result.metadata.title)
        assertEquals("Manual Composer", result.metadata.artist)
        assertEquals("200", result.metadata.bpmText)
        assertEquals(200f, result.metadata.bpmBase)
        assertEquals("Eternal 11", result.metadata.difficulties.single().difficulty)
        assertEquals(11.2f, result.metadata.difficulties.single().chartConstant)
    }

    private fun songlistJson(id: String, title: String, artist: String): String =
        """{"songs": [${songObjectJson(id, title, artist)}]}"""

    private fun songObjectJson(id: String, title: String, artist: String): String =
        """
        {
          "id": "$id",
          "title_localized": {"en": "$title"},
          "artist": "$artist",
          "bpm": "185",
          "bpm_base": 185,
          "set": "single",
          "side": 0,
          "difficulties": [{
            "ratingClass": 4,
            "chartDesigner": "Charter",
            "jacketDesigner": "Illustrator",
            "rating": 10,
            "ratingPlus": true
          }]
        }
        """.trimIndent()
}
