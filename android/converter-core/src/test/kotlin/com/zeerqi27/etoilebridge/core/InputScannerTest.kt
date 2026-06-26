package com.zeerqi27.etoilebridge.core

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputScannerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun detectsSingleSongFolder() {
        tempDir.resolve("2.aff").writeText("AudioOffset:0\n-\ntiming(0,100.00,4.00);")

        val scanned = InputScanner().scan(tempDir)

        assertEquals(InputKind.SingleSong, scanned.kind)
        assertTrue(scanned.rootAffFiles.containsKey(2))
    }

    @Test
    fun detectsPackFolder() {
        tempDir.resolve("songlist").writeText("""{"songs":[]}""")
        val songDir = tempDir.resolve("song_a").apply { mkdirs() }
        songDir.resolve("0.aff").writeText("AudioOffset:0\n-\ntiming(0,100.00,4.00);")

        val scanned = InputScanner().scan(tempDir)

        assertEquals(InputKind.PackFolder, scanned.kind)
        assertEquals(songDir, scanned.songDirectories["song_a"])
    }

    @Test
    fun detectsSonglistJsonAndPacklistJson() {
        tempDir.resolve("songlist.json").writeText("""{"songs":[]}""")
        tempDir.resolve("packlist.json").writeText("""{"packs":[]}""")
        val songDir = tempDir.resolve("song_a").apply { mkdirs() }
        songDir.resolve("2.aff").writeText("AudioOffset:0\n-\ntiming(0,100.00,4.00);")

        val scanned = InputScanner().scan(tempDir)

        assertEquals(InputKind.PackFolder, scanned.kind)
        assertEquals("songlist.json", scanned.songlistFile?.name)
        assertEquals("packlist.json", scanned.packlistFile?.name)
    }

    @Test
    fun detectsSonglistTxt() {
        tempDir.resolve("songlist.txt").writeText("""{"id":"song_a"}""")
        val songDir = tempDir.resolve("song_a").apply { mkdirs() }
        songDir.resolve("2.aff").writeText("AudioOffset:0\n-\ntiming(0,100.00,4.00);")

        val scanned = InputScanner().scan(tempDir)

        assertEquals(InputKind.PackFolder, scanned.kind)
        assertEquals("songlist.txt", scanned.songlistFile?.name)
    }

    @Test
    fun detectsSlstAsSonglist() {
        tempDir.resolve("slst").writeText("""{"id":"song_a"}""")
        val songDir = tempDir.resolve("song_a").apply { mkdirs() }
        songDir.resolve("2.aff").writeText("AudioOffset:0\n-\ntiming(0,100.00,4.00);")

        val scanned = InputScanner().scan(tempDir)

        assertEquals(InputKind.PackFolder, scanned.kind)
        assertEquals("slst", scanned.songlistFile?.name)
    }

    @Test
    fun detectsChildSongFolderWithoutSonglist() {
        val songDir = tempDir.resolve("song_a").apply { mkdirs() }
        songDir.resolve("2.aff").writeText("AudioOffset:0\n-\ntiming(0,100.00,4.00);")

        val scanned = InputScanner().scan(tempDir)

        assertEquals(InputKind.PackFolder, scanned.kind)
        assertEquals(songDir, scanned.songDirectories["song_a"])
    }

    @Test
    fun recordsIgnoredNonStandardAffFiles() {
        tempDir.resolve("2.aff").writeText("AudioOffset:0\n-\ntiming(0,100.00,4.00);")
        tempDir.resolve("2-extra.aff").writeText("AudioOffset:0\n-\ntiming(0,100.00,4.00);")

        val scanned = InputScanner().scan(tempDir)

        assertEquals(listOf("2-extra.aff"), scanned.ignoredAffFiles.map { it.name })
    }
}
