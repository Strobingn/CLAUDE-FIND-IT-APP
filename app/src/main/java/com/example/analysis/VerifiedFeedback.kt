package com.example.analysis

import com.example.data.TargetSignal
import com.example.data.VerificationOutcome

/**
 * A field-verified ground-truth point for one terrain dataset: the user has physically checked
 * this grid location and confirmed either a real historic feature or a false positive. This is
 * the only kind of "feedback" the app's candidate scoring ever uses - it never invents or infers
 * verification, only reads what a user explicitly recorded.
 */
data class VerifiedFeedbackPoint(
    val xPercent: Float,
    val yPercent: Float,
    val confirmed: Boolean,
)

/** Derives verified feedback for [datasetKey] from the user's logged field finds. */
object VerifiedFeedback {
    /** Squared-distance threshold (in percent-of-grid units) for matching feedback to a candidate. */
    const val MATCH_DISTANCE_SQUARED = 64f

    fun derive(signals: List<TargetSignal>, datasetKey: String): List<VerifiedFeedbackPoint> =
        signals.mapNotNull { signal ->
            if (signal.datasetKey != datasetKey) return@mapNotNull null
            when (signal.outcome) {
                VerificationOutcome.CONFIRMED_FEATURE -> VerifiedFeedbackPoint(signal.gridX, signal.gridY, confirmed = true)
                VerificationOutcome.REJECTED_FALSE_POSITIVE -> VerifiedFeedbackPoint(signal.gridX, signal.gridY, confirmed = false)
                VerificationOutcome.UNVERIFIED, VerificationOutcome.INCONCLUSIVE -> null
            }
        }

    /**
     * Translates verified points into [TerrainFeedbackRecord]s for [TerrainIntelligenceEngine].
     * A verified point isn't labeled with which of the 12 feature types it actually is, so it is
     * applied as feedback against every type at that location: "something real is/isn't here"
     * rather than a type-specific claim the user never made.
     */
    fun toTerrainFeedbackRecords(datasetKey: String, points: List<VerifiedFeedbackPoint>): List<TerrainFeedbackRecord> =
        points.flatMap { point ->
            val rating = if (point.confirmed) TerrainFeedbackRating.CONFIRMED else TerrainFeedbackRating.REJECTED
            TerrainFeatureType.entries.map { type ->
                TerrainFeedbackRecord(
                    datasetKey = datasetKey,
                    candidateId = "verified-${type.name}-${point.xPercent}-${point.yPercent}",
                    featureType = type,
                    xPercent = point.xPercent,
                    yPercent = point.yPercent,
                    rating = rating,
                    note = "",
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
        }
}
