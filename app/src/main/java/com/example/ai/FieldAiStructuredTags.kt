package com.example.ai

import com.example.data.MetalType
import com.example.data.VerificationOutcome

/**
 * Parsers for optional machine lines emitted by pack-3 Field AI features
 * ([NAV_TARGET], [VIZ_MODE], [METAL_TYPE], [OUTCOME], [STATUS], [NOTES]).
 * Style matches Field AI copilot lighting recommendations (case-insensitive, flexible spacing).
 */
object FieldAiStructuredTags {

    private val navTargetPattern = Regex(
        """NAV_TARGET\s+id\s*=\s*([0-9]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val vizModePattern = Regex(
        """VIZ_MODE\s*=\s*([0-9]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val metalTypePattern = Regex(
        """METAL_TYPE\s*=\s*(.+)""",
        RegexOption.IGNORE_CASE,
    )
    private val outcomePattern = Regex(
        """OUTCOME\s*=\s*(.+)""",
        RegexOption.IGNORE_CASE,
    )
    private val statusPattern = Regex(
        """STATUS\s*=\s*(.+)""",
        RegexOption.IGNORE_CASE,
    )
    private val notesPattern = Regex(
        """NOTES\s*=\s*(.+)""",
        RegexOption.IGNORE_CASE,
    )

    /** Ordered unique signal ids from `NAV_TARGET id=<long>` lines. */
    fun parseNavTargetIds(text: String): List<Long> {
        val seen = LinkedHashSet<Long>()
        navTargetPattern.findAll(text).forEach { match ->
            match.groupValues.getOrNull(1)?.toLongOrNull()?.let { seen.add(it) }
        }
        return seen.toList()
    }

    /** First `VIZ_MODE=<0-8>` value, or null if missing/out of range. */
    fun parseVizMode(text: String): Int? {
        val raw = vizModePattern.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        return raw.takeIf { it in 0..8 }
    }

    fun parseMetalTypeSuggestion(text: String): String? =
        firstTagValue(metalTypePattern, text)

    fun parseOutcomeSuggestion(text: String): String? =
        firstTagValue(outcomePattern, text)

    fun parseStatusSuggestion(text: String): String? =
        firstTagValue(statusPattern, text)

    fun parseNotesSuggestion(text: String): String? =
        firstTagValue(notesPattern, text)

    private fun firstTagValue(pattern: Regex, text: String): String? {
        val raw = pattern.find(text)?.groupValues?.getOrNull(1) ?: return null
        val cleaned = raw
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?.trimEnd('\r')
            ?.takeWhile { it != '\n' }
            ?.trim()
            .orEmpty()
        return cleaned.takeIf { it.isNotBlank() }
    }
}

/** Structured find-catalog suggestions parsed from free-form AI text. */
data class FieldAiFindSuggestions(
    val metalTypeLabel: String?,
    val outcomeLabel: String?,
    val statusLabel: String?,
    val notes: String?,
)

/** Collect METAL_TYPE / OUTCOME / STATUS / NOTES tags from [text]. */
fun parseFindSuggestions(text: String): FieldAiFindSuggestions = FieldAiFindSuggestions(
    metalTypeLabel = FieldAiStructuredTags.parseMetalTypeSuggestion(text),
    outcomeLabel = FieldAiStructuredTags.parseOutcomeSuggestion(text),
    statusLabel = FieldAiStructuredTags.parseStatusSuggestion(text),
    notes = FieldAiStructuredTags.parseNotesSuggestion(text),
)

/** Map free-text metal suggestion to [MetalType] when possible; null if unknown. */
fun resolveMetalType(label: String): MetalType? {
    val trimmed = label.trim()
    if (trimmed.isEmpty()) return null

    MetalType.entries.firstOrNull { it.label.equals(trimmed, ignoreCase = true) }?.let { return it }

    val normalized = trimmed.lowercase()
    return when {
        normalized.contains("gold") -> MetalType.GOLD
        normalized.contains("silver") -> MetalType.SILVER
        normalized.contains("bronze") || normalized.contains("copper") -> MetalType.BRONZE
        normalized.contains("iron") ||
            normalized.contains("nail") ||
            normalized.contains("spike") -> MetalType.IRON
        normalized.contains("ai target") ||
            normalized.contains("magnetic") ||
            normalized.contains("anomaly") -> MetalType.MAGNETIC_ANOMALY
        normalized.contains("manual") -> MetalType.MANUAL_MARKER
        else -> null
    }
}

/** Map free-text outcome to [VerificationOutcome] when possible; null if unknown. */
fun resolveOutcome(label: String): VerificationOutcome? {
    val trimmed = label.trim()
    if (trimmed.isEmpty()) return null

    VerificationOutcome.entries
        .firstOrNull { it.label.equals(trimmed, ignoreCase = true) }
        ?.let { return it }

    val normalized = trimmed.lowercase()
    return when {
        normalized.contains("false positive") ||
            normalized.contains("rejected") ||
            normalized.contains("reject") -> VerificationOutcome.REJECTED_FALSE_POSITIVE
        normalized.contains("inconclusive") ||
            normalized.contains("uncertain") -> VerificationOutcome.INCONCLUSIVE
        normalized.contains("confirmed") ||
            normalized.contains("real feature") ||
            normalized == "confirm" ||
            normalized.startsWith("confirm ") -> VerificationOutcome.CONFIRMED_FEATURE
        normalized.contains("unverified") ||
            normalized.contains("not yet") -> VerificationOutcome.UNVERIFIED
        else -> null
    }
}
