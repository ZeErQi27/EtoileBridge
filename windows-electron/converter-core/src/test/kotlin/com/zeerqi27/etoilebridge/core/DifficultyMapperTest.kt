package com.zeerqi27.etoilebridge.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DifficultyMapperTest {
    @Test
    fun mapsAffFilesToOfficialDifficultyNames() {
        val expected = listOf("Past", "Present", "Future", "Beyond", "Eternal")
        expected.forEachIndexed { ratingClass, label ->
            val info = DifficultyMapper.fromAffFile(File("$ratingClass.aff"))
            assertEquals(ratingClass, info?.ratingClass)
            assertEquals(label, info?.label)
        }
    }

    @Test
    fun formatsRatingPlus() {
        assertEquals("Future 9+", DifficultyMapper.displayName(2, 9, true))
    }
}
