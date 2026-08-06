package com.example.ai

import com.example.data.historicmap.MapFeatureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricMapFeatureExtractorTest {
    @Test
    fun parsesWellFormedFeatureLines() {
        val text = """
            Sure, here's what I found:
            FEATURE|ROAD|0.1,0.2;0.3,0.4;0.5,0.6|Old wagon trace along the ridge
            FEATURE|STRUCTURE|0.55,0.6;0.58,0.6;0.58,0.63;0.55,0.63|Rectangular foundation outline
        """.trimIndent()

        val proposals = HistoricMapFeatureExtractor.parse(text)

        assertEquals(2, proposals.size)
        assertEquals(MapFeatureType.ROAD, proposals[0].type)
        assertEquals(listOf(0.1f to 0.2f, 0.3f to 0.4f, 0.5f to 0.6f), proposals[0].normalizedPoints)
        assertEquals("Old wagon trace along the ridge", proposals[0].description)
        assertEquals(MapFeatureType.STRUCTURE, proposals[1].type)
        assertEquals(4, proposals[1].normalizedPoints.size)
    }

    @Test
    fun replyOfNoneProducesNoProposals() {
        assertTrue(HistoricMapFeatureExtractor.parse("NONE").isEmpty())
    }

    @Test
    fun missingDescriptionFallsBackToAGenericLabel() {
        val proposals = HistoricMapFeatureExtractor.parse("FEATURE|WALL|0.1,0.1;0.2,0.2")

        assertEquals(1, proposals.size)
        assertEquals("AI-suggested wall", proposals.single().description)
    }

    @Test
    fun unknownTypeIsRejectedRatherThanGuessed() {
        assertTrue(HistoricMapFeatureExtractor.parse("FEATURE|RIVER|0.1,0.1;0.2,0.2|A river").isEmpty())
    }

    @Test
    fun singlePointFeatureIsRejected() {
        assertTrue(HistoricMapFeatureExtractor.parse("FEATURE|ROAD|0.1,0.1|Just one point").isEmpty())
    }

    @Test
    fun outOfRangeCoordinatesAreRejected() {
        assertTrue(HistoricMapFeatureExtractor.parse("FEATURE|ROAD|1.5,0.1;0.2,0.2|Bad coordinate").isEmpty())
    }

    @Test
    fun malformedLinesAreSkippedWithoutThrowing() {
        val text = """
            FEATURE|ROAD
            FEATURE|WALL|not-a-point;also-not|desc
            FEATURE|BOUNDARY|0.1,0.1;0.9,0.9|Field boundary
        """.trimIndent()

        val proposals = HistoricMapFeatureExtractor.parse(text)

        assertEquals(1, proposals.size)
        assertEquals(MapFeatureType.BOUNDARY, proposals.single().type)
    }
}
