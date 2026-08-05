package com.example.ai

import com.example.data.MetalType
import com.example.data.VerificationOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldAiStructuredTagsTest {

    @Test
    fun parseNavTargetIdsExtractsOrderedUniqueIds() {
        val text = """
            Stop order:
            NAV_TARGET id=1001
            NAV_TARGET id=2002
            NAV_TARGET id=1001
            nav_target id=3003
        """.trimIndent()
        assertEquals(
            listOf(1001L, 2002L, 3003L),
            FieldAiStructuredTags.parseNavTargetIds(text),
        )
        assertTrue(FieldAiStructuredTags.parseNavTargetIds("no targets").isEmpty())
    }

    @Test
    fun parseVizModeAccepts0to8Only() {
        assertEquals(3, FieldAiStructuredTags.parseVizMode("try VIZ_MODE=3 next"))
        assertEquals(0, FieldAiStructuredTags.parseVizMode("VIZ_MODE=0"))
        assertEquals(8, FieldAiStructuredTags.parseVizMode("viz_mode=8"))
        assertNull(FieldAiStructuredTags.parseVizMode("VIZ_MODE=9"))
        assertNull(FieldAiStructuredTags.parseVizMode("no mode"))
    }

    @Test
    fun parseFindSuggestionsReadsAllTags() {
        val text = """
            Suggested catalog:
            METAL_TYPE=Iron Nail/Spike
            OUTCOME=Checked - false positive
            STATUS=Excavated
            NOTES=flat iron near fence line
        """.trimIndent()

        val suggestions = parseFindSuggestions(text)
        assertEquals("Iron Nail/Spike", suggestions.metalTypeLabel)
        assertEquals("Checked - false positive", suggestions.outcomeLabel)
        assertEquals("Excavated", suggestions.statusLabel)
        assertEquals("flat iron near fence line", suggestions.notes)

        val empty = parseFindSuggestions("nothing structured here")
        assertNull(empty.metalTypeLabel)
        assertNull(empty.outcomeLabel)
        assertNull(empty.statusLabel)
        assertNull(empty.notes)
    }

    @Test
    fun resolveMetalTypeMatchesLabelsAndAliases() {
        assertEquals(MetalType.IRON, resolveMetalType("Iron Nail/Spike"))
        assertEquals(MetalType.GOLD, resolveMetalType("gold ring"))
        assertEquals(MetalType.SILVER, resolveMetalType("Silver"))
        assertEquals(MetalType.BRONZE, resolveMetalType("bronze buckle"))
        assertEquals(MetalType.MAGNETIC_ANOMALY, resolveMetalType("AI target"))
        assertEquals(MetalType.MAGNETIC_ANOMALY, resolveMetalType("magnetic anomaly"))
        assertEquals(MetalType.MANUAL_MARKER, resolveMetalType("manual pin"))
        assertNull(resolveMetalType("unknown alloy"))
        assertNull(resolveMetalType("   "))
    }

    @Test
    fun resolveOutcomeMatchesLabelsAndKeywords() {
        assertEquals(
            VerificationOutcome.CONFIRMED_FEATURE,
            resolveOutcome("Confirmed real feature"),
        )
        assertEquals(
            VerificationOutcome.REJECTED_FALSE_POSITIVE,
            resolveOutcome("Checked - false positive"),
        )
        assertEquals(
            VerificationOutcome.INCONCLUSIVE,
            resolveOutcome("Checked - inconclusive"),
        )
        assertEquals(VerificationOutcome.UNVERIFIED, resolveOutcome("Not yet checked"))

        assertEquals(VerificationOutcome.CONFIRMED_FEATURE, resolveOutcome("confirmed"))
        assertEquals(VerificationOutcome.REJECTED_FALSE_POSITIVE, resolveOutcome("rejected"))
        assertEquals(VerificationOutcome.REJECTED_FALSE_POSITIVE, resolveOutcome("false positive dig"))
        assertEquals(VerificationOutcome.INCONCLUSIVE, resolveOutcome("inconclusive signal"))
        assertNull(resolveOutcome("maybe later"))
        assertNull(resolveOutcome(""))
    }
}
