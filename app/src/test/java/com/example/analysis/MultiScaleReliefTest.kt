package com.example.analysis

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A single detrending radius only resolves features near its own size. These pin the behaviour that
 * makes the multi-scale layer worth having: a small hollow and a broad platform both survive, and
 * no single scale dominates the combination.
 */
class MultiScaleReliefTest {
    private val width = 64
    private val height = 64
    private val cell = 1f

    private fun flat(value: Float = 100f) = FloatArray(width * height) { value }

    private fun index(x: Int, y: Int) = y * width + x

    private fun relief(elevation: FloatArray) =
        TerrainIntelligenceEngine.multiScaleLocalRelief(elevation, width, height, cell)

    @Test
    fun flatGroundProducesNoRelief() {
        val result = relief(flat())

        assertTrue(result.all { abs(it) < 1e-3f })
    }

    /** A cellar-sized hollow is the small end of the range the scales are chosen to bracket. */
    @Test
    fun aSmallHollowReadsNegative() {
        val elevation = flat()
        for (y in 30..33) for (x in 30..33) elevation[index(x, y)] = 97f

        val result = relief(elevation)

        assertTrue("hollow should read negative", result[index(31, 31)] < -0.5f)
    }

    /** A platform-sized rise is the large end, and a single 8 m window would flatten it. */
    @Test
    fun aBroadPlatformReadsPositive() {
        val elevation = flat()
        for (y in 20..44) for (x in 20..44) elevation[index(x, y)] = 103f

        val result = relief(elevation)

        assertTrue("platform should read positive", result[index(32, 32)] > 0.5f)
    }

    /** Both must survive the same pass — that is the whole point of combining scales. */
    @Test
    fun aSmallHollowInsideABroadPlatformBothSurvive() {
        val elevation = flat()
        for (y in 16..48) for (x in 16..48) elevation[index(x, y)] = 103f
        for (y in 31..34) for (x in 31..34) elevation[index(x, y)] = 100f

        val result = relief(elevation)

        assertTrue("platform edge should still read positive", result[index(20, 20)] > 0f)
        assertTrue("hollow should read negative against the platform", result[index(32, 32)] < 0f)
    }

    @Test
    fun signIsPreservedSoMoundsAndHollowsStayDistinguishable() {
        val elevation = flat()
        for (y in 18..22) for (x in 18..22) elevation[index(x, y)] = 104f
        for (y in 42..46) for (x in 42..46) elevation[index(x, y)] = 96f

        val result = relief(elevation)

        assertTrue(result[index(20, 20)] > 0f)
        assertTrue(result[index(44, 44)] < 0f)
    }

    @Test
    fun outputIsFiniteEverywhere() {
        val elevation = FloatArray(width * height) { (it % 17).toFloat() }

        assertTrue(relief(elevation).all(Float::isFinite))
    }

    @Test
    fun theLayerMatchesTheGridSize() {
        assertEquals(width * height, relief(flat()).size)
    }

    /** A degenerate cell size must not divide by zero or blow the radius up. */
    @Test
    fun aZeroCellSizeIsHandled() {
        val result = TerrainIntelligenceEngine.multiScaleLocalRelief(flat(), width, height, 0f)

        assertEquals(width * height, result.size)
        assertTrue(result.all(Float::isFinite))
    }

    @Test
    fun anEmptyGridReturnsAnEmptyLayer() {
        assertEquals(0, TerrainIntelligenceEngine.multiScaleLocalRelief(FloatArray(0), 0, 0, cell).size)
    }
}
