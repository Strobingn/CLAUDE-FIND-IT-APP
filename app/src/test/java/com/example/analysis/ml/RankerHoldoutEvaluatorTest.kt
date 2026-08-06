package com.example.analysis.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankerHoldoutEvaluatorTest {
    private val featureNames = listOf("rule_score", "radius_meters")

    private fun productive(xPercent: Float, yPercent: Float, jitter: Float) = RankerTrainingExample(
        features = floatArrayOf(0.75f + jitter, 6f + jitter),
        productive = true,
    ) to SpatialFoldSplitter.FoldLocation(null, null, xPercent, yPercent)

    private fun rejected(xPercent: Float, yPercent: Float, jitter: Float) = RankerTrainingExample(
        features = floatArrayOf(0.15f + jitter, 1f + jitter),
        productive = false,
    ) to SpatialFoldSplitter.FoldLocation(null, null, xPercent, yPercent)

    @Test
    fun tooFewExamplesReturnsNullRatherThanAFakeAccuracy() {
        val pairs = listOf(
            productive(5f, 5f, 0f),
            rejected(85f, 85f, 0f),
        )

        val result = RankerHoldoutEvaluator.evaluate(
            examples = pairs.map { it.first },
            locations = pairs.map { it.second },
            featureNames = featureNames,
        )

        assertNull("Fewer than 6 examples can't support a meaningful spatial holdout", result)
    }

    @Test
    fun everyExampleInOneSpatialBlockReturnsNullRatherThanAFakeAccuracy() {
        // All within grid-percent 0-9, so every location hashes to the same ~10% block —
        // there is nothing spatially separate to hold out.
        val pairs = (0 until 8).map { index ->
            if (index % 2 == 0) productive(1f + index * 0.1f, 2f, 0f) else rejected(3f, 4f + index * 0.1f, 0f)
        }

        val result = RankerHoldoutEvaluator.evaluate(
            examples = pairs.map { it.first },
            locations = pairs.map { it.second },
            featureNames = featureNames,
        )

        assertNull(result)
    }

    @Test
    fun spatiallySeparatedClustersProduceARealHeldOutAccuracy() {
        // Two well-separated clusters (grid blocks far apart) each with clean, linearly separable
        // confirmed/rejected examples, so whichever block ends up held out is still predictable
        // from a model trained only on the other one.
        val pairs = (0 until 10).flatMap { index ->
            val jitter = index * 0.01f
            listOf(
                productive(8f, 8f, jitter),
                rejected(8f, 8f, jitter),
                productive(88f, 88f, jitter),
                rejected(88f, 88f, jitter),
            )
        }

        val result = RankerHoldoutEvaluator.evaluate(
            examples = pairs.map { it.first },
            locations = pairs.map { it.second },
            featureNames = featureNames,
        )

        assertNotNull("Two spatially separated, non-empty folds should produce a real evaluation", result)
        assertNotNull(result!!.heldOutAccuracy)
        assertTrue(result.heldOutAccuracy!! in 0f..1f)
        assertTrue(
            "Cleanly separable clusters should generalize well to the held-out block",
            result.heldOutAccuracy!! >= 0.75f,
        )
        assertTrue(result.hardNegativeCount >= 0)
    }

    @Test
    fun mismatchedExampleAndLocationCountsAreRejected() {
        try {
            RankerHoldoutEvaluator.evaluate(
                examples = listOf(productive(1f, 1f, 0f).first),
                locations = emptyList(),
                featureNames = featureNames,
            )
            org.junit.Assert.fail("must require matching example/location counts")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
