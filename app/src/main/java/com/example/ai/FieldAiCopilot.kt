package com.example.ai

import com.example.analysis.TerrainFeatureCandidate
import com.example.analysis.TerrainIntelligenceResult
import com.example.analysis.VerifiedFeedback
import com.example.data.TargetSignal
import com.example.data.VerificationOutcome
import com.example.data.field.BreadcrumbTrack
import com.example.data.field.ExcavationLogEntry
import com.example.data.field.FieldNavigation
import com.example.data.field.FindSiteClusterer
import java.util.Locale

/**
 * Ten AI-heavy field features that turn session context into specialist prompts
 * for [TerrainAiGateway] (OpenAI primary, Gemini fallback).
 */
enum class FieldAiFeature(
    val title: String,
    val shortLabel: String,
    val description: String,
    /** Prefer attaching the current terrain viewport image when available. */
    val prefersViewportImage: Boolean,
) {
    DIG_BRIEF(
        title = "Dig brief",
        shortLabel = "Dig brief",
        description = "Next-dig briefing: where, why, what to look for, risk of false positives",
        prefersViewportImage = true,
    ),
    SITE_NARRATIVE(
        title = "Site narrative",
        shortLabel = "Narrative",
        description = "Occupation / scatter story from clustered finds and field outcomes",
        prefersViewportImage = false,
    ),
    LIGHTING_ADVISOR(
        title = "Lighting advisor",
        shortLabel = "Lighting",
        description = "Recommend hillshade sun angles for earthworks; emits LIGHT_AZ / LIGHT_ALT",
        prefersViewportImage = true,
    ),
    SWEEP_PLAN(
        title = "Sweep plan",
        shortLabel = "Sweep",
        description = "Priority zones and walk order from coverage gaps + candidates",
        prefersViewportImage = true,
    ),
    FIELD_REPORT(
        title = "Field report",
        shortLabel = "Report",
        description = "Multi-section session report ready to share with partners",
        prefersViewportImage = false,
    ),
    OUTCOME_COACH(
        title = "Outcome coach",
        shortLabel = "Outcomes",
        description = "Calibrate strategy from confirmed vs false-positive outcomes",
        prefersViewportImage = false,
    ),
    FIND_INTERPRETER(
        title = "Find interpreter",
        shortLabel = "Finds AI",
        description = "Interpret notes, metal types, and status of logged finds",
        prefersViewportImage = false,
    ),
    HISTORIC_CORRELATOR(
        title = "Historic correlator",
        shortLabel = "Historic",
        description = "Correlate terrain candidates with historic homesite / road patterns",
        prefersViewportImage = true,
    ),
    ANOMALY_DEEPDIVE(
        title = "Anomaly deep-dive",
        shortLabel = "Deep-dive",
        description = "Deep analysis of top candidates with optional map markers",
        prefersViewportImage = true,
    ),
    DAY_DEBRIEF(
        title = "Day debrief",
        shortLabel = "Debrief",
        description = "End-of-day structured debrief from freeform notes + session data",
        prefersViewportImage = false,
    ),
}

/** Packed field session context for AI copilot prompts. */
data class FieldAiSessionPack(
    val terrainSummary: String,
    val terrainContext: String,
    val sunAzimuth: Float,
    val sunAltitude: Float,
    val gridWidth: Int,
    val gridHeight: Int,
    val cellSizeMeters: Float,
    val deviceLatitude: Double? = null,
    val deviceLongitude: Double? = null,
    val signals: List<TargetSignal> = emptyList(),
    val excavationLogs: List<ExcavationLogEntry> = emptyList(),
    val breadcrumbTracks: List<BreadcrumbTrack> = emptyList(),
    val localResult: TerrainIntelligenceResult? = null,
    val freeformNotes: String = "",
)

object FieldAiCopilot {

    private val lightAzPattern = Regex("""LIGHT_AZ\s*=\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
    private val lightAltPattern = Regex("""LIGHT_ALT\s*=\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    data class LightingRecommendation(val azimuth: Float, val altitude: Float)

    fun parseLightingRecommendation(text: String): LightingRecommendation? {
        val az = lightAzPattern.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: return null
        val alt = lightAltPattern.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: return null
        return LightingRecommendation(
            azimuth = ((az % 360f) + 360f) % 360f,
            altitude = alt.coerceIn(5f, 85f),
        )
    }

    fun buildUserPrompt(feature: FieldAiFeature, pack: FieldAiSessionPack): String {
        val body = when (feature) {
            FieldAiFeature.DIG_BRIEF -> digBriefPrompt(pack)
            FieldAiFeature.SITE_NARRATIVE -> siteNarrativePrompt(pack)
            FieldAiFeature.LIGHTING_ADVISOR -> lightingAdvisorPrompt(pack)
            FieldAiFeature.SWEEP_PLAN -> sweepPlanPrompt(pack)
            FieldAiFeature.FIELD_REPORT -> fieldReportPrompt(pack)
            FieldAiFeature.OUTCOME_COACH -> outcomeCoachPrompt(pack)
            FieldAiFeature.FIND_INTERPRETER -> findInterpreterPrompt(pack)
            FieldAiFeature.HISTORIC_CORRELATOR -> historicCorrelatorPrompt(pack)
            FieldAiFeature.ANOMALY_DEEPDIVE -> anomalyDeepDivePrompt(pack)
            FieldAiFeature.DAY_DEBRIEF -> dayDebriefPrompt(pack)
        }
        return body.trim()
    }

    fun buildSystemAddendum(feature: FieldAiFeature): String = when (feature) {
        FieldAiFeature.DIG_BRIEF -> """
            You are a senior metal-detecting field lead and archaeological remote-sensing specialist.
            Produce a practical dig brief. Never claim buried metal is proven from LiDAR alone.
            Use short sections with clear headings. Rank uncertainty honestly.
        """.trimIndent()
        FieldAiFeature.SITE_NARRATIVE -> """
            You are a historical landscape interpreter. Weave finds, outcomes, and terrain into a
            cautious site narrative. Separate evidence from speculation.
        """.trimIndent()
        FieldAiFeature.LIGHTING_ADVISOR -> """
            You are a LiDAR visualization specialist. Recommend sun azimuth and altitude for
            revealing earthworks on hillshade. End with exactly two machine lines:
            LIGHT_AZ=<0-360>
            LIGHT_ALT=<5-85>
        """.trimIndent()
        FieldAiFeature.SWEEP_PLAN -> """
            You are a field survey planner. Produce an efficient sweep plan that respects GPS
            coverage already done, high-value terrain candidates, and find clusters.
        """.trimIndent()
        FieldAiFeature.FIELD_REPORT -> """
            You are writing a professional field report for partners. Clear sections, no hype,
            actionable next steps, and explicit confidence language.
        """.trimIndent()
        FieldAiFeature.OUTCOME_COACH -> """
            You are a detection-strategy coach. Use verification outcomes to reduce false positives
            and reinforce productive patterns. Be specific and operational.
        """.trimIndent()
        FieldAiFeature.FIND_INTERPRETER -> """
            You are a finds catalog analyst. Interpret notes, metal types, and status without
            inventing provenience. Flag data quality gaps.
        """.trimIndent()
        FieldAiFeature.HISTORIC_CORRELATOR -> """
            You are a historic-landscape correlator. Link terrain candidates to plausible
            historic occupation/access patterns (homesites, roads, outbuildings). Mark uncertainty.
        """.trimIndent()
        FieldAiFeature.ANOMALY_DEEPDIVE -> """
            You are an archaeological remote-sensing analyst doing a deep-dive on top candidates.
            If a terrain image is attached, after the written analysis emit up to 6 map markers:
            [MAP_TARGET x=42.0 y=61.0 confidence=0.82 label=short label]
            Coordinates are 0..100 left-to-right and top-to-bottom on the attached image.
        """.trimIndent()
        FieldAiFeature.DAY_DEBRIEF -> """
            You are a field team lead writing an end-of-day debrief. Structure wins, misses,
            unfinished work, and tomorrow's priorities. Be blunt and useful.
        """.trimIndent()
    }

    // ------------------------------------------------------------------
    // Prompt builders
    // ------------------------------------------------------------------

    private fun digBriefPrompt(pack: FieldAiSessionPack): String = buildString {
        appendLine("Generate a NEXT DIG BRIEF for this session.")
        appendLine("Sections required:")
        appendLine("1) Priority dig points (grid % or lat/lon when known)")
        appendLine("2) Why each point matters (terrain evidence + finds context)")
        appendLine("3) What to look for with a metal detector / shovel test")
        appendLine("4) False-positive risks")
        appendLine("5) Suggested order and time budget (minutes)")
        appendLine()
        append(sessionFacts(pack))
    }

    private fun siteNarrativePrompt(pack: FieldAiSessionPack): String = buildString {
        appendLine("Write a SITE NARRATIVE from the finds and outcomes.")
        appendLine("Cover: site character, likely activity zones, chronology clues (if any),")
        appendLine("scatter vs nucleus, and open questions. Keep under 500 words.")
        appendLine()
        append(sessionFacts(pack))
        appendLine()
        append(findsDetail(pack.signals, limit = 40))
        appendLine()
        append(sitesSummary(pack.signals))
    }

    private fun lightingAdvisorPrompt(pack: FieldAiSessionPack): String = buildString {
        appendLine("Recommend hillshade lighting for earthwork inspection.")
        appendLine("Current sun: azimuth ${pack.sunAzimuth}°, altitude ${pack.sunAltitude}°.")
        appendLine("Explain why, then end with LIGHT_AZ= and LIGHT_ALT= lines only as the final two lines.")
        appendLine()
        append(sessionFacts(pack))
        append(candidatesBrief(pack.localResult))
    }

    private fun sweepPlanPrompt(pack: FieldAiSessionPack): String = buildString {
        appendLine("Create a SWEEP PLAN for metal-detecting this parcel.")
        appendLine("Include: zones A/B/C, order, coil-width assumptions (default 2 m),")
        appendLine("where GPS trail coverage already exists, and skip zones.")
        appendLine()
        append(sessionFacts(pack))
        append(trailSummary(pack.breadcrumbTracks))
        append(candidatesBrief(pack.localResult))
        append(sitesSummary(pack.signals))
    }

    private fun fieldReportPrompt(pack: FieldAiSessionPack): String = buildString {
        appendLine("Write a FIELD REPORT with sections:")
        appendLine("Summary · Terrain · Local AI candidates · Finds · Dig logs · Trails · Recommendations · Data gaps")
        appendLine()
        append(sessionFacts(pack))
        append(findsDetail(pack.signals, limit = 50))
        append(digsSummary(pack.excavationLogs))
        append(trailSummary(pack.breadcrumbTracks))
        append(candidatesBrief(pack.localResult))
    }

    private fun outcomeCoachPrompt(pack: FieldAiSessionPack): String = buildString {
        appendLine("Act as OUTCOME COACH. Analyze verification outcomes and coach next detections.")
        appendLine("Focus on what predicted well, what was false-positive, and how to adjust.")
        appendLine()
        append(sessionFacts(pack))
        append(outcomeBreakdown(pack.signals))
        append(findsDetail(pack.signals.filter { it.outcome != VerificationOutcome.UNVERIFIED }, limit = 40))
        append(candidatesBrief(pack.localResult))
    }

    private fun findInterpreterPrompt(pack: FieldAiSessionPack): String = buildString {
        appendLine("INTERPRET the logged finds catalog. Group by type/status, highlight notes that")
        appendLine("suggest habitation, industry, or trash, and list missing fields to fill.")
        appendLine()
        append(sessionFacts(pack))
        append(findsDetail(pack.signals, limit = 60))
        append(sitesSummary(pack.signals))
    }

    private fun historicCorrelatorPrompt(pack: FieldAiSessionPack): String = buildString {
        appendLine("HISTORIC CORRELATION: relate local terrain candidates and finds to plausible")
        appendLine("historic occupation (homesite, cellar, wagon road, outbuilding scatter, etc.).")
        appendLine("Do not invent archival proof. Rank hypotheses.")
        appendLine()
        append(sessionFacts(pack))
        append(candidatesBrief(pack.localResult, limit = 20))
        append(sitesSummary(pack.signals))
        append(findsDetail(pack.signals, limit = 30))
    }

    private fun anomalyDeepDivePrompt(pack: FieldAiSessionPack): String = buildString {
        appendLine("ANOMALY DEEP-DIVE on the strongest local candidates and the visible terrain.")
        appendLine("For each: morphology, natural vs cultural likelihood, field-check method, confidence.")
        appendLine("If an image is attached, add MAP_TARGET lines for the best 3–6 check points.")
        appendLine()
        append(sessionFacts(pack))
        append(candidatesBrief(pack.localResult, limit = 15, detailed = true))
    }

    private fun dayDebriefPrompt(pack: FieldAiSessionPack): String = buildString {
        appendLine("Write an END-OF-DAY DEBRIEF.")
        appendLine("Sections: What we did · What worked · What failed · Unfinished · Tomorrow plan · Gear/data notes")
        if (pack.freeformNotes.isNotBlank()) {
            appendLine()
            appendLine("Operator freeform notes:")
            appendLine(pack.freeformNotes.take(3_000))
        }
        appendLine()
        append(sessionFacts(pack))
        append(findsDetail(pack.signals, limit = 40))
        append(digsSummary(pack.excavationLogs))
        append(trailSummary(pack.breadcrumbTracks))
        append(outcomeBreakdown(pack.signals))
    }

    // ------------------------------------------------------------------
    // Context formatters
    // ------------------------------------------------------------------

    private fun sessionFacts(pack: FieldAiSessionPack): String = buildString {
        appendLine("--- Session facts ---")
        appendLine(pack.terrainContext.trim())
        appendLine("Terrain summary: ${pack.terrainSummary}")
        appendLine("Raster: ${pack.gridWidth}x${pack.gridHeight} @ ${pack.cellSizeMeters} m/cell")
        appendLine("Sun: az=${pack.sunAzimuth}° alt=${pack.sunAltitude}°")
        val lat = pack.deviceLatitude
        val lon = pack.deviceLongitude
        if (lat != null && lon != null) {
            appendLine(String.format(Locale.US, "Device GPS: %.6f, %.6f", lat, lon))
        } else {
            appendLine("Device GPS: unavailable")
        }
        appendLine("Logged finds: ${pack.signals.size}")
        appendLine("Starred finds: ${pack.signals.count { it.starred }}")
        appendLine("Dig logs: ${pack.excavationLogs.size}")
        appendLine("GPS trails: ${pack.breadcrumbTracks.size}")
        pack.localResult?.let {
            appendLine("Local analysis recommendation: ${it.recommendation}")
            appendLine("Local candidates: ${it.candidates.size}")
        } ?: appendLine("Local analysis: not run")
        appendLine()
    }

    private fun findsDetail(signals: List<TargetSignal>, limit: Int): String {
        if (signals.isEmpty()) return "Finds: none logged.\n"
        return buildString {
            appendLine("--- Finds (up to $limit) ---")
            signals.sortedByDescending { it.timestamp }.take(limit).forEachIndexed { i, s ->
                val geo = if (s.latitude != null && s.longitude != null) {
                    String.format(Locale.US, "%.5f,%.5f", s.latitude, s.longitude)
                } else {
                    "grid ${s.gridX.toInt()},${s.gridY.toInt()}"
                }
                append("${i + 1}. ${s.metalType.label}")
                if (s.starred) append(" ★")
                append(" · $geo · ${s.status} · ${s.outcome.label}")
                if (s.notes.isNotBlank()) append(" · notes=${s.notes.take(120)}")
                appendLine()
            }
        }
    }

    private fun sitesSummary(signals: List<TargetSignal>): String {
        val sites = FindSiteClusterer.cluster(signals)
        if (sites.isEmpty()) return "Sites: no proximity clusters.\n"
        return buildString {
            appendLine("--- Proximity sites ---")
            sites.take(12).forEachIndexed { i, site ->
                appendLine(
                    "${i + 1}. ${site.label} n=${site.signals.size} · types=${site.topTypes.take(4).joinToString(",")} · " +
                        "confirmed=${site.confirmedCount} rejected=${site.rejectedCount}",
                )
            }
        }
    }

    private fun digsSummary(logs: List<ExcavationLogEntry>): String {
        if (logs.isEmpty()) return "Dig logs: none.\n"
        return buildString {
            appendLine("--- Dig logs ---")
            logs.takeLast(20).forEach { log ->
                val notes = listOf(log.soilNotes, log.findsDescription)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .take(100)
                appendLine(
                    "target=${log.targetId} complete=${log.isComplete} " +
                        "depth=${log.depthCentimeters ?: "?"} notes=$notes",
                )
            }
        }
    }

    private fun trailSummary(tracks: List<BreadcrumbTrack>): String {
        if (tracks.isEmpty()) return "GPS trails: none.\n"
        val points = tracks.sumOf { it.points.size }
        var meters = 0.0
        tracks.forEach { track ->
            track.points.zipWithNext { a, b ->
                meters += FieldNavigation.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            }
        }
        return "GPS trails: ${tracks.size} track(s), $points points, ~${"%.0f".format(meters)} m walked.\n"
    }

    private fun outcomeBreakdown(signals: List<TargetSignal>): String {
        if (signals.isEmpty()) return "Outcomes: no finds.\n"
        val counts = VerificationOutcome.entries.associateWith { outcome ->
            signals.count { it.outcome == outcome }
        }
        return buildString {
            appendLine("--- Outcome counts ---")
            counts.forEach { (outcome, n) -> appendLine("${outcome.label}: $n") }
        }
    }

    private fun candidatesBrief(
        result: TerrainIntelligenceResult?,
        limit: Int = 12,
        detailed: Boolean = false,
    ): String {
        if (result == null) return "Local candidates: none (run local analysis first for better results).\n"
        val list = result.candidates.sortedByDescending { it.score }.take(limit)
        if (list.isEmpty()) return "Local candidates: empty list.\n"
        return buildString {
            appendLine("--- Local terrain candidates ---")
            list.forEachIndexed { i, c -> appendLine(formatCandidate(i + 1, c, detailed)) }
        }
    }

    private fun formatCandidate(index: Int, c: TerrainFeatureCandidate, detailed: Boolean): String {
        val base = String.format(
            Locale.US,
            "%d. %s score=%.3f at x=%.1f%% y=%.1f%%",
            index,
            c.type.label,
            c.score,
            c.xPercent,
            c.yPercent,
        )
        return if (detailed && c.evidence.isNotEmpty()) {
            "$base evidence=${c.evidence.joinToString(";")}"
        } else {
            base
        }
    }
}
