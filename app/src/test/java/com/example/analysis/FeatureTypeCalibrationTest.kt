package com.example.analysis

import com.example.data.DetectionSource
import com.example.data.MetalType
import com.example.data.TargetSignal
import com.example.data.VerificationOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureTypeCalibrationTest {
    private fun signal(
        outcome: VerificationOutcome,
        detectedFeatureType: String?,
        datasetKey: String = "dataset-a",
    ) = TargetSignal(
        gridX = 10f,
        gridY = 10f,
        metalType = MetalType.MAGNETIC_ANOMALY,
        signalStrength = 50f,
        source = DetectionSource.AI_ANALYSIS,
        outcome = outcome,
        datasetKey = datasetKey,
        detectedFeatureType = detectedFeatureType,
    )

    @Test
    fun typesWithFewerThanMinimumSamplesAreOmitted() {
        val signals = listOf(
            signal(VerificationOutcome.CONFIRMED_FEATURE, MetalDetectingTargetType.CELLAR_HOLE.name),
            signal(VerificationOutcome.CONFIRMED_FEATURE, MetalDetectingTargetType.CELLAR_HOLE.name),
        )
        assertTrue(FeatureTypeCalibration.derive(signals).isEmpty())
    }

    @Test
    fun mostlyConfirmedTypeGetsPositiveBias() {
        val signals = List(4) { signal(VerificationOutcome.CONFIRMED_FEATURE, MetalDetectingTargetType.FOUNDATION.name) } +
            listOf(signal(VerificationOutcome.REJECTED_FALSE_POSITIVE, MetalDetectingTargetType.FOUNDATION.name))

        val bias = FeatureTypeCalibration.derive(signals)[MetalDetectingTargetType.FOUNDATION]

        assertTrue("Mostly-confirmed type should get a positive (more permissive) bias", (bias ?: 0f) > 0f)
    }

    @Test
    fun mostlyRejectedTypeGetsNegativeBias() {
        val signals = List(4) { signal(VerificationOutcome.REJECTED_FALSE_POSITIVE, MetalDetectingTargetType.STONE_WALL.name) } +
            listOf(signal(VerificationOutcome.CONFIRMED_FEATURE, MetalDetectingTargetType.STONE_WALL.name))

        val bias = FeatureTypeCalibration.derive(signals)[MetalDetectingTargetType.STONE_WALL]

        assertTrue("Mostly-rejected type should get a negative (stricter) bias", (bias ?: 0f) < 0f)
    }

    @Test
    fun unverifiedAndUntypedSignalsAreIgnored() {
        val signals = listOf(
            signal(VerificationOutcome.UNVERIFIED, MetalDetectingTargetType.ROAD_TRAIL.name),
            signal(VerificationOutcome.INCONCLUSIVE, MetalDetectingTargetType.ROAD_TRAIL.name),
            signal(VerificationOutcome.CONFIRMED_FEATURE, null),
            signal(VerificationOutcome.CONFIRMED_FEATURE, "NOT_A_REAL_TYPE"),
        )
        assertTrue(FeatureTypeCalibration.derive(signals).isEmpty())
    }

    @Test
    fun biasNeverExceedsMaximumMagnitude() {
        val signals = List(50) { signal(VerificationOutcome.CONFIRMED_FEATURE, MetalDetectingTargetType.OLD_HOMESITE.name) }

        val bias = FeatureTypeCalibration.derive(signals).getValue(MetalDetectingTargetType.OLD_HOMESITE)

        assertTrue(bias <= 0.12f)
        assertEquals(0.12f, bias, 1e-6f)
    }
}
