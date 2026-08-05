package com.example.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldAiCopilotTest {

    private val pack = FieldAiSessionPack(
        terrainSummary = "test terrain",
        terrainContext = "grid 10x10",
        sunAzimuth = 315f,
        sunAltitude = 35f,
        gridWidth = 10,
        gridHeight = 10,
        cellSizeMeters = 1f,
    )

    @Test
    fun parseLightingRecommendation_extractsAzimuthAndAltitude() {
        val text = """
            Recommend low NW light for bank shadows.
            LIGHT_AZ=315
            LIGHT_ALT=35
        """.trimIndent()

        val rec = FieldAiCopilot.parseLightingRecommendation(text)

        assertNotNull(rec)
        assertEquals(315f, rec!!.azimuth, 1e-3f)
        assertEquals(35f, rec.altitude, 1e-3f)
    }

    @Test
    fun parseLightingRecommendation_returnsNullWhenMissingLines() {
        assertNull(FieldAiCopilot.parseLightingRecommendation("no machine lines here"))
        assertNull(FieldAiCopilot.parseLightingRecommendation("LIGHT_AZ=180"))
        assertNull(FieldAiCopilot.parseLightingRecommendation("LIGHT_ALT=40"))
    }

    @Test
    fun parseLightingRecommendation_coercesAltitudeInto5to85() {
        val tooLow = FieldAiCopilot.parseLightingRecommendation(
            "LIGHT_AZ=90\nLIGHT_ALT=0",
        )
        assertNotNull(tooLow)
        assertEquals(5f, tooLow!!.altitude, 1e-3f)

        val tooHigh = FieldAiCopilot.parseLightingRecommendation(
            "LIGHT_AZ=90\nLIGHT_ALT=99",
        )
        assertNotNull(tooHigh)
        assertEquals(85f, tooHigh!!.altitude, 1e-3f)
    }

    @Test
    fun buildUserPrompt_eachFeatureIsNonBlankWithDistinctiveKeywords() {
        val expectedKeywords = mapOf(
            FieldAiFeature.DIG_BRIEF to "DIG",
            FieldAiFeature.SITE_NARRATIVE to "NARRATIVE",
            FieldAiFeature.LIGHTING_ADVISOR to "LIGHT_AZ",
            FieldAiFeature.SWEEP_PLAN to "SWEEP",
            FieldAiFeature.FIELD_REPORT to "REPORT",
            FieldAiFeature.OUTCOME_COACH to "OUTCOME",
            FieldAiFeature.FIND_INTERPRETER to "INTERPRET",
            FieldAiFeature.HISTORIC_CORRELATOR to "HISTORIC",
            FieldAiFeature.ANOMALY_DEEPDIVE to "DEEP",
            FieldAiFeature.DAY_DEBRIEF to "DEBRIEF",
        )

        for (feature in FieldAiFeature.entries) {
            val prompt = FieldAiCopilot.buildUserPrompt(feature, pack)
            assertTrue("prompt blank for $feature", prompt.isNotBlank())
            val keyword = expectedKeywords.getValue(feature)
            assertTrue(
                "prompt for $feature missing keyword '$keyword': ${prompt.take(120)}",
                prompt.contains(keyword, ignoreCase = true),
            )
        }
    }

    @Test
    fun buildSystemAddendum_lightingAdvisorMentionsLightAz() {
        val addendum = FieldAiCopilot.buildSystemAddendum(FieldAiFeature.LIGHTING_ADVISOR)
        assertFalse(addendum.isBlank())
        assertTrue(addendum.contains("LIGHT_AZ"))
    }

    @Test
    fun fieldAiFeature_hasExactlyTenEntries() {
        assertEquals(10, FieldAiFeature.entries.size)
    }
}
