package com.zeerqi27.etoilebridge.core

import com.charleskorn.kaml.decodeFromStream
import com.zeerqi27.etoilebridge.core.etoile.CharacterInformation
import com.zeerqi27.etoilebridge.core.etoile.EtoileYaml
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CharacterPackageTest {
    @Test
    fun scansRealCharacterSamples() {
        val samples = File("E:/ArcpkgAPP/samples/搭档包")
        if (!samples.isDirectory) return

        val result = CharacterPackageScanner().scan(samples)

        assertTrue(result.packages.isNotEmpty())
        val loaded = result.packages.firstOrNull { it.character != null }
        assertNotNull(loaded)
        assertEquals("character.yml", loaded.settingsFile)
        assertTrue(loaded.identifier.orEmpty().isNotBlank())
        assertTrue(loaded.character?.imagePath.orEmpty().isNotBlank())
        assertTrue(loaded.character?.iconPath.orEmpty().isNotBlank())
    }

    @Test
    fun loadsExistingCharacterArcpkg() {
        val sample = File("E:/ArcpkgAPP/samples/搭档包/zeerqi27.otto.arcpkg")
        if (!sample.isFile) return

        val loaded = CharacterPackageLoader().loadExistingCharacterPackage(sample)

        assertEquals("otto", loaded.directory)
        assertEquals("zeerqi27.otto", loaded.identifier)
        assertEquals("OTTO", loaded.character?.name?.get("default"))
        assertEquals("otto.png", loaded.character?.imagePath)
        assertEquals("otto_icon.png", loaded.character?.iconPath)
        assertEquals(300f, loaded.character?.x)
        assertEquals(100f, loaded.character?.y)
        assertEquals(0.7f, loaded.character?.scale)
    }

    @Test
    fun buildsValidCharacterPackageWithChineseName() {
        val dir = createTempDir("character-builder")
        try {
            val image = dir.resolve("image.png").apply { writeBytes(PNG_BYTES) }
            val icon = dir.resolve("icon.png").apply { writeBytes(PNG_BYTES) }
            val output = dir.resolve("zeerqi27.testchar.arcpkg")

            val result = CharacterPackageBuilder().build(
                CharacterPackageInput(
                    imageFile = image,
                    iconFile = icon,
                    outputFile = output,
                    options = CharacterPackageOptions(
                        publisherId = "zeerqi27",
                        characterId = "testchar",
                        defaultName = "Test Character",
                        zhCnName = "测试搭档",
                        x = 300f,
                        y = 100f,
                        scale = 0.7f,
                    ),
                )
            )

            assertTrue(result is CharacterPackageResult.Success)
            val validation = CharacterPackageValidator().validateCharacterArcpkg(output)
            assertTrue(validation.valid, validation.errors.joinToString())
            ZipFile(output).use { zip ->
                assertNotNull(zip.getEntry("index.yml"))
                assertNotNull(zip.getEntry("testchar/character.yml"))
                assertNotNull(zip.getEntry("testchar/testchar.png"))
                assertNotNull(zip.getEntry("testchar/testchar_icon.png"))
                val character = zip.getInputStream(zip.getEntry("testchar/character.yml")).use {
                    EtoileYaml.decodeFromStream(CharacterInformation.serializer(), it)
                }
                assertEquals("测试搭档", character.name["zh-cn"])
                assertEquals(300f, character.x)
                assertEquals(100f, character.y)
                assertEquals(0.7f, character.scale)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun validatorFailsWhenIconIsMissing() {
        val dir = createTempDir("character-missing-icon")
        try {
            val image = dir.resolve("image.png").apply { writeBytes(PNG_BYTES) }
            val icon = dir.resolve("icon.png").apply { writeBytes(PNG_BYTES) }
            val output = dir.resolve("broken.arcpkg")
            CharacterPackageBuilder().build(CharacterPackageInput(image, icon, output))

            val broken = dir.resolve("broken_no_icon.arcpkg")
            java.util.zip.ZipOutputStream(broken.outputStream()).use { out ->
                ZipFile(output).use { zip ->
                    zip.entries().asSequence().filterNot { it.name.endsWith("_icon.png") }.forEach { entry ->
                        out.putNextEntry(java.util.zip.ZipEntry(entry.name))
                        zip.getInputStream(entry).use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }
            }

            val validation = CharacterPackageValidator().validateCharacterArcpkg(broken)

            assertFalse(validation.valid)
            assertTrue(validation.errors.any { it.contains("iconPath") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun splitIdentifierKeepsMultiPartPublisher() {
        assertEquals("com.example" to "otto", splitCharacterIdentifier("com.example.otto"))
        assertEquals("zeerqi27" to "otto", splitCharacterIdentifier("zeerqi27.otto"))
    }

    private companion object {
        private val PNG_BYTES = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(), 0x89.toByte(),
            0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
            0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
    }
}
