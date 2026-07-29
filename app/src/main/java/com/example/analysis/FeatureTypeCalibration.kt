package com.example.analysis

import com.example.data.TargetSignal
import com.example.data.VerificationOutcome

/**
 * Per-type detection bias derived from every field-verified [TargetSignal] the user has ever
 * logged, across every dataset - not just the one currently open. [VerifiedFeedback] already
 * re-scores a single exact spot within the dataset it was checked in; this is the complementary
 * piece that lets the *type* itself get more or less trusted over time: a type the user keeps
 * confirming in the field gets a lower effective threshold (more of it surfaces), a type mostly
 * rejected gets a higher one (fewer, stricter candidates). Only [TargetSignal]s that were logged
 * from an AI candidate carry [TargetSignal.detectedFeatureType]; manual and cloud-AI markers with
 * no recorded type are ignored here.
 */
object FeatureTypeCalibration {
    /** Largest threshold shift, in score units, calibration is ever allowed to apply. */
    private const val MAX_BIAS = 0.12f

    /** Sample size at which calibration reaches full confidence (scales in linearly below this). */
    private const val CONFIDENT_SAMPLE_SIZE = 10f

    /** Below this many verified samples for a type, its outcomes are too thin to act on. */
    private const val MIN_SAMPLES = 3

    /**
     * Returns a threshold bias per [MetalDetectingTargetType]: positive lowers the effective
     * threshold (surface more), negative raises it (surface less). Types with fewer than
     * [MIN_SAMPLES] verified outcomes are omitted rather than defaulted to zero, so callers can
     * tell "not enough data yet" apart from "genuinely neutral" if they ever need to.
     */
    fun derive(signals: List<TargetSignal>): Map<MetalDetectingTargetType, Float> {
        val confirmed = HashMap<MetalDetectingTargetType, Int>()
        val rejected = HashMap<MetalDetectingTargetType, Int>()
        for (signal in signals) {
            val type = signal.detectedFeatureType?.let { name ->
                MetalDetectingTargetType.entries.firstOrNull { it.name == name }
            } ?: continue
            when (signal.outcome) {
                VerificationOutcome.CONFIRMED_FEATURE -> confirmed.merge(type, 1, Int::plus)
                VerificationOutcome.REJECTED_FALSE_POSITIVE -> rejected.merge(type, 1, Int::plus)
                VerificationOutcome.UNVERIFIED, VerificationOutcome.INCONCLUSIVE -> Unit
            }
        }
        val types = confirmed.keys + rejected.keys
        return types.mapNotNull { type ->
            val confirmedCount = confirmed[type] ?: 0
            val rejectedCount = rejected[type] ?: 0
            val sampleSize = confirmedCount + rejectedCount
            if (sampleSize < MIN_SAMPLES) return@mapNotNull null
            val confirmRate = confirmedCount.toFloat() / sampleSize
            val confidence = (sampleSize / CONFIDENT_SAMPLE_SIZE).coerceAtMost(1f)
            val bias = ((confirmRate - 0.5f) * 2f * MAX_BIAS * confidence).coerceIn(-MAX_BIAS, MAX_BIAS)
            type to bias
        }.toMap()
    }
}
