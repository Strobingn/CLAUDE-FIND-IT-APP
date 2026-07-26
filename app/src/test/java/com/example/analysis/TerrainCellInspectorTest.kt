package com.example.analysis

import com.example.data.ElevationGrid
import com.example.geospatial.GeoSpatialLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainCellInspectorTest {
    @Test
    fun exactCellMetricsAreStableForAPlanarSurface() {
        val width = 5
        val height = 5
        val elevations = FloatArray(width * height) { index ->
            val x = index % width
            val y = index / width
            100f + x + y * 2f
        }
        val grid = ElevationGrid(
            width = width,
            height = height,
            bareEarth = elevations,
            canopySpikes = FloatArray(width * height),
            cellSizeMeters = 1f,
        )

        val result = TerrainCellInspector.inspect(
            grid = grid,
            metadata = GeoSpatialLibrary.localGrid("test", width, height),
            xPercent = 50f,
            yPercent = 50f,
            vegetationFilter = 1f,
            featureScaleMeters = 4f,
        )

        assertEquals(2, result.column)
        assertEquals(2, result.row)
        assertEquals(106f, result.elevationMeters, 0.0001f)
        assertEquals(65.905f, result.slopeDegrees, 0.01f)
        assertEquals(0f, result.curvaturePerMeter, 0.0001f)
        assertEquals(0f, result.localReliefMeters, 0.0001f)
        assertTrue(result.valid)
        assertTrue(result.validNeighborhoodCells > 1)
    }

    @Test
    fun noDataCellIsReportedWithoutInventingGeographicCoordinates() {
        val valid = BooleanArray(9) { true }.also { it[4] = false }
        val grid = ElevationGrid(
            width = 3,
            height = 3,
            bareEarth = FloatArray(9) { it.toFloat() },
            canopySpikes = FloatArray(9),
            validData = valid,
        )

        val result = TerrainCellInspector.inspect(
            grid = grid,
            metadata = GeoSpatialLibrary.localGrid("local", 3, 3),
            xPercent = 50f,
            yPercent = 50f,
            vegetationFilter = 1f,
            featureScaleMeters = 6f,
        )

        assertFalse(result.valid)
        assertEquals(null, result.latitude)
        assertEquals(null, result.longitude)
    }
}
