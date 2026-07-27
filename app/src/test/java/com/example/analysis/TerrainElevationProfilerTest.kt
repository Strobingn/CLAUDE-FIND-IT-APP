package com.example.analysis

import com.example.data.ElevationGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainElevationProfilerTest {
    @Test
    fun profileSamplesGridCellsAndReportsDistanceAndElevationChanges() {
        val grid = ElevationGrid(
            width = 3,
            height = 1,
            bareEarth = floatArrayOf(100f, 102f, 101f),
            canopySpikes = FloatArray(3),
            cellSizeMeters = 2f,
        )

        val profile = TerrainElevationProfiler.sample(
            grid = grid,
            startXPercent = 0f,
            startYPercent = 0f,
            endXPercent = 100f,
            endYPercent = 0f,
            vegetationFilter = 1f,
        )

        assertEquals(4f, profile.horizontalDistanceMeters, 0.001f)
        assertEquals(2f, profile.ascentMeters, 0.001f)
        assertEquals(1f, profile.descentMeters, 0.001f)
        assertEquals(100f, profile.minimumElevationMeters, 0.001f)
        assertEquals(102f, profile.maximumElevationMeters, 0.001f)
        assertEquals(3, profile.samples.size)
        assertTrue(profile.samples.all { it.valid })
    }
}
