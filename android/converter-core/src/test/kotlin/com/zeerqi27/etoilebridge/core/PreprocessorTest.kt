package com.zeerqi27.etoilebridge.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PreprocessorTest {
    private val options = ConvertOptions()

    @Test
    fun deletesDesignantLines() {
        val result = Preprocessor().preprocess("timing(0,100,4);\ndesignant(foo);\n(1,1);\n", options)
        assertFalse(result.content.contains("designant"))
        assertEquals(1, result.stats.deletedDesignantLines)
    }

    @Test
    fun fixesZeroDurationArcTap() {
        val result = Preprocessor().preprocess(
            "arc(81643,81643,0.50,0.50,s,1.00,-0.20,0,none,true)[arctap(81643)];",
            options,
        )
        assertContains(result.content, "arc(81643,81644")
        assertContains(result.content, "[arctap(81643)]")
        assertEquals(1, result.stats.fixedZeroDurationArcTaps)
    }

    @Test
    fun fixesReversedArcTime() {
        val result = Preprocessor().preprocess(
            "arc(2000,1000,0.00,1.00,s,0.00,1.00,0,none,false);",
            options,
        )
        assertContains(result.content, "arc(1000,2000")
        assertEquals(1, result.stats.fixedReversedArcTimes)
    }

    @Test
    fun expandsArcResolutionWithinTiminggroup() {
        val result = Preprocessor().preprocess(
            """
            timinggroup(arcresolution=3.00){
            arc(0,1000,0.00,1.00,s,0.00,1.00,0,none,false);
            };
            arc(0,1000,0.00,1.00,s,0.00,1.00,0,none,false);
            """.trimIndent(),
            options,
        )
        assertContains(result.content, "timinggroup(){")
        assertContains(result.content, "arc(0,1000,0.00,1.00,s,0.00,1.00,0,none,false,3.00);")
        assertEquals(1, result.stats.expandedArcResolutionArcs)
    }
}
