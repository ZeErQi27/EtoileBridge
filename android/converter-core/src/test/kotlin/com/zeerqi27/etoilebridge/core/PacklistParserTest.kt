package com.zeerqi27.etoilebridge.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PacklistParserTest {
    @Test
    fun parsesRequestedPackFields() {
        val packlist = PacklistParser().parse(
            """
            {
              "packs": [{
                "id": "single",
                "section": "arcaea",
                "name_localized": {"en": "Single"},
                "description_localized": {"en": "Desc"},
                "plus_character": -1,
                "custom_banner": false,
                "pack_parent": "root",
                "is_extend_pack": true,
                "is_active_extend_pack": false,
                "small_pack_image": true,
                "cutout_pack_image": false,
                "limitedSaleEndTime": 123
              }]
            }
            """.trimIndent()
        )

        val pack = packlist.packs.single()
        assertEquals("single", pack.id)
        assertEquals("arcaea", pack.section)
        assertEquals("Single", pack.nameLocalized["en"])
        assertEquals("Desc", pack.descriptionLocalized["en"])
        assertEquals(-1, pack.plusCharacter)
        assertEquals(false, pack.customBanner)
        assertEquals("root", pack.packParent)
    }

    @Test
    fun parsesSinglePackObjectWithoutPacksWrapper() {
        val packlist = PacklistParser().parse(
            """
            {
              "id": "rainbowhell",
              "section": "custom",
              "plus_character": -1,
              "custom_banner": false,
              "name_localized": {"en": "Rainbow Hell"},
              "description_localized": {"en": ""}
            },
            """.trimIndent()
        )

        val pack = packlist.packs.single()
        assertEquals("rainbowhell", pack.id)
        assertEquals("Rainbow Hell", pack.nameLocalized["en"])
        assertEquals(-1, pack.plusCharacter)
    }
}
