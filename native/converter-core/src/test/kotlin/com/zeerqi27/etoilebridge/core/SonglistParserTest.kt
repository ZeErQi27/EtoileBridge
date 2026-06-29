package com.zeerqi27.etoilebridge.core

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SonglistParserTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun parsesRequestedSongFields() {
        val songlist = SonglistParser().parse(
            """
            {
              "songs": [{
                "idx": 1,
                "id": "test_song",
                "title_localized": {"en": "Test Song"},
                "artist": "Composer",
                "search_title": {"en": ["test"]},
                "search_artist": {"en": ["composer"]},
                "bpm": "128",
                "bpm_base": 128.0,
                "set": "single",
                "audioPreview": 1000,
                "audioPreviewEnd": 5000,
                "side": 1,
                "bg": "bg",
                "bg_inverse": "bg_inv",
                "difficulties": [{
                  "ratingClass": 2,
                  "chartDesigner": "Charter",
                  "jacketDesigner": "Illustrator",
                  "rating": 9,
                  "ratingPlus": true,
                  "jacketOverride": false,
                  "audioOverride": false,
                  "bg": "diff_bg",
                  "bg_inverse": "diff_bg_inv"
                }]
              }]
            }
            """.trimIndent()
        )

        val song = songlist.songs.single()
        assertEquals("test_song", song.id)
        assertEquals("Test Song", song.titleLocalized["en"])
        assertEquals("Composer", song.artist)
        assertEquals("128", song.bpmText)
        assertEquals(128.0f, song.bpmBase)
        assertEquals("single", song.set)
        assertEquals("bg", song.bg)
        assertEquals("bg_inv", song.bgInverse)
        assertEquals(1000, song.audioPreview)
        assertEquals(5000, song.audioPreviewEnd)
        assertEquals(1, song.side)
        assertTrue(song.searchTitle["en"].orEmpty().contains("test"))
        assertEquals(2, song.difficulties.single().ratingClass)
        assertEquals(true, song.difficulties.single().ratingPlus)
    }

    @Test
    fun parsesSingleSongObjectWithTrailingComma() {
        val songlist = SonglistParser().parse(
            """
            {
              "id": "echoes",
              "title_localized": {"en": "Echoes of Memoria"},
              "artist": "Ludicin",
              "bpm": "175-230",
              "bpm_base": 190,
              "set": "base",
              "side": 0,
              "bg": "echoes",
              "difficulties": [{
                "ratingClass": 2,
                "chartDesigner": "Anamnesis",
                "jacketDesigner": "",
                "rating": 12
              }]
            },
            """.trimIndent()
        )

        val song = songlist.songs.single()
        assertEquals("echoes", song.id)
        assertEquals("Echoes of Memoria", song.titleLocalized["en"])
        assertEquals("echoes", song.bg)
        assertEquals(2, song.difficulties.single().ratingClass)
    }

    @Test
    fun parsesCommaSeparatedSongObjectsWithoutArrayWrapper() {
        val songlist = SonglistParser().parse(
            """
            {
              "id": "first",
              "title_localized": {"en": "First"},
              "artist": "Composer A",
              "bpm": "160",
              "bpm_base": 160,
              "difficulties": [{"ratingClass": 2, "rating": 9}]
            },
            {
              "id": "second",
              "title_localized": {"en": "Second"},
              "artist": "Composer B",
              "bpm": "180",
              "bpm_base": 180,
              "difficulties": [{"ratingClass": 3, "rating": 10}]
            },
            """.trimIndent()
        )

        assertEquals(listOf("first", "second"), songlist.songs.map { it.id })
        assertEquals("Second", songlist.songs[1].titleLocalized["en"])
        assertEquals(3, songlist.songs[1].difficulties.single().ratingClass)
    }

    @Test
    fun parsesTopLevelArrayAndGbkEncodedFile() {
        val file = tempDir.resolve("slst.txt")
        file.writeText(
            """
            [{
              "id": "gbk_song",
              "title": "中文标题",
              "composer": "作曲者",
              "bpm": "150",
              "bpm_base": 150,
              "difficulties": [{"ratingClass": 2, "rating": 9}]
            }]
            """.trimIndent(),
            Charset.forName("GBK"),
        )

        val songlist = SonglistParser().parse(file)

        val song = songlist.songs.single()
        assertEquals("gbk_song", song.id)
        assertEquals("中文标题", song.title)
        assertEquals("作曲者", song.composer)
        assertEquals(2, song.difficulties.single().ratingClass)
    }
}
