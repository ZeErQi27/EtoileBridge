package com.zeerqi27.etoilebridge.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CliArgsParserTest {
    @Test
    fun `parses required input and output`() {
        val parsed = CliArgsParser.parse(
            arrayOf("--input", "D:\\sample_song", "--output", "D:\\output"),
        )

        assertEquals("D:\\sample_song", parsed.inputDir.path)
        assertEquals("D:\\output", parsed.outputDir?.path)
        assertNull(parsed.songId)
        assertFalse(parsed.keepTemp)
        assertEquals(CliMode.ConvertSingle, parsed.mode)
        assertTrue(parsed.enableDeleteDesignantLine)
        assertTrue(parsed.enableFixZeroDurationArcTap)
        assertTrue(parsed.enableFixReversedArcTime)
        assertTrue(parsed.enableExpandArcResolution)
    }

    @Test
    fun `parses song id keep temp and disabled preprocessors`() {
        val parsed = CliArgsParser.parse(
            arrayOf(
                "--input=D:\\pack",
                "--output=D:\\output",
                "--song-id",
                "test_song",
                "--keep-temp",
                "--disable-delete-designant",
                "--disable-fix-zero-arctap",
                "--disable-fix-reversed-arc",
                "--disable-arcresolution",
            ),
        )

        assertEquals("test_song", parsed.songId)
        assertTrue(parsed.keepTemp)
        assertFalse(parsed.enableDeleteDesignantLine)
        assertFalse(parsed.enableFixZeroDurationArcTap)
        assertFalse(parsed.enableFixReversedArcTime)
        assertFalse(parsed.enableExpandArcResolution)
    }

    @Test
    fun `parses scan only without output`() {
        val parsed = CliArgsParser.parse(
            arrayOf("--scan-only", "--input", "D:\\samples"),
        )

        assertEquals("D:\\samples", parsed.inputDir.path)
        assertNull(parsed.outputDir)
        assertEquals(CliMode.ScanOnly, parsed.mode)
    }

    @Test
    fun `parses pack scan modes without output`() {
        assertEquals(
            CliMode.ScanPack,
            CliArgsParser.parse(arrayOf("--scan-pack", "--input", "D:\\packs")).mode,
        )
        assertEquals(
            CliMode.ScanArcpkgPack,
            CliArgsParser.parse(arrayOf("--scan-arcpkg-pack", "--input", "D:\\arcpkgs")).mode,
        )
    }

    @Test
    fun `parses arcpkg merge publisher id`() {
        val parsed = CliArgsParser.parse(
            arrayOf("--merge-arcpkg", "--input", "D:\\arcpkgs", "--output", "D:\\out", "--publisher-id", "pub", "--validate-output"),
        )

        assertEquals(CliMode.MergeArcpkg, parsed.mode)
        assertEquals("pub", parsed.publisherId)
        assertTrue(parsed.validateOutput)
    }

    @Test
    fun `parses official pack conversion mode`() {
        val parsed = CliArgsParser.parse(
            arrayOf("--convert-pack", "--input", "D:\\pack.zip", "--output", "D:\\out", "--validate-output"),
        )

        assertEquals(CliMode.ConvertPack, parsed.mode)
        assertTrue(parsed.validateOutput)
    }

    @Test
    fun `parses existing pack edit mode`() {
        val parsed = CliArgsParser.parse(
            arrayOf(
                "--edit-pack-arcpkg",
                "--base",
                "D:\\base.arcpkg",
                "--add",
                "D:\\more",
                "--output",
                "D:\\out",
                "--validate-output",
            ),
        )

        assertEquals(CliMode.EditPackArcpkg, parsed.mode)
        assertEquals("D:\\base.arcpkg", parsed.basePack?.path)
        assertEquals("D:\\more", parsed.addInput?.path)
        assertTrue(parsed.validateOutput)
    }

    @Test
    fun `fails when required input is missing`() {
        val error = assertFailsWith<IllegalArgumentException> {
            CliArgsParser.parse(arrayOf("--output", "D:\\output"))
        }

        assertEquals("Missing required option: --input", error.message)
    }

    @Test
    fun `fails when required output is missing`() {
        val error = assertFailsWith<IllegalArgumentException> {
            CliArgsParser.parse(arrayOf("--input", "D:\\sample_song"))
        }

        assertEquals("Missing required option: --output", error.message)
    }

    @Test
    fun `fails on unknown option`() {
        val error = assertFailsWith<IllegalArgumentException> {
            CliArgsParser.parse(arrayOf("--input", "D:\\sample_song", "--output", "D:\\output", "--bad"))
        }

        assertEquals("Unknown option: --bad", error.message)
    }

    @Test
    fun `fails when value option has no value`() {
        val error = assertFailsWith<IllegalArgumentException> {
            CliArgsParser.parse(arrayOf("--input", "D:\\sample_song", "--output"))
        }

        assertEquals("Missing value for --output", error.message)
    }
}
