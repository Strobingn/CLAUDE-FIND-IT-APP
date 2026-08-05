package com.example.data

import com.example.geospatial.GeoSpatialLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSurfaceSamplerTest {

    private val metadata = GeoSpatialLibrary.SITES_METADATA[0]
    private val bounds = requireNotNull(metadata.bounds)

    @Test
    fun returnsNullWhenNotGeoreferenced() {
        val grid = flatGrid(11, 11, 10f)
        val local = GeoSpatialLibrary.localGrid("local", 11, 11)

        assertNull(
            SiteSurfaceSampler.sample(
                grid = grid,
                metadata = local,
                latitude = 43.12,
                longitude = -124.40,
            ),
        )
    }

    @Test
    fun returnsNullWhenOutsideMappedArea() {
        val grid = flatGrid(11, 11, 10f)

        assertNull(
            SiteSurfaceSampler.sample(
                grid = grid,
                metadata = metadata,
                latitude = bounds.minLat - 1.0,
                longitude = bounds.minLon - 1.0,
            ),
        )
    }

    @Test
    fun flatNeighborhoodIsFlatBucketAndZeroRelative() {
        val grid = flatGrid(21, 21, 42f)
        val (lat, lon) = requireNotNull(GeoSpatialLibrary.gridToGeographic(50f, 50f, metadata))

        val sample = requireNotNull(
            SiteSurfaceSampler.sample(
                grid = grid,
                metadata = metadata,
                latitude = lat,
                longitude = lon,
                neighborhoodRadiusCells = 3,
            ),
        )

        assertTrue(sample.cellValid)
        assertEquals(42f, sample.surfaceElevationMeters!!, 1e-4f)
        assertEquals(0f, sample.relativeToLocalMeanMeters, 1e-4f)
        assertEquals("flat", sample.localSlopeBucket)
        assertTrue(sample.disclaimer.contains("Not buried-object depth", ignoreCase = true))
    }

    @Test
    fun highSpotIsPositiveRelativeToLocalMean() {
        val width = 21
        val height = 21
        val elevations = FloatArray(width * height) { 10f }
        val center = (height / 2) * width + (width / 2)
        elevations[center] = 12f
        val grid = ElevationGrid(
            width = width,
            height = height,
            bareEarth = elevations,
            canopySpikes = FloatArray(width * height),
            cellSizeMeters = 1f,
        )
        val (lat, lon) = requireNotNull(GeoSpatialLibrary.gridToGeographic(50f, 50f, metadata))

        val sample = requireNotNull(
            SiteSurfaceSampler.sample(
                grid = grid,
                metadata = metadata,
                latitude = lat,
                longitude = lon,
                neighborhoodRadiusCells = 3,
            ),
        )

        assertTrue(sample.relativeToLocalMeanMeters > 0f)
        assertEquals(12f, sample.surfaceElevationMeters!!, 1e-4f)
    }

    @Test
    fun steepRangeProducesSteepBucket() {
        // Large elev swing over a short horizontal span → steep.
        val width = 15
        val height = 15
        val elevations = FloatArray(width * height) { colRow ->
            val col = colRow % width
            col * 2f // 0..28 m across 14 m → ratio >> 0.15
        }
        val grid = ElevationGrid(
            width = width,
            height = height,
            bareEarth = elevations,
            canopySpikes = FloatArray(width * height),
            cellSizeMeters = 1f,
        )
        val (lat, lon) = requireNotNull(GeoSpatialLibrary.gridToGeographic(50f, 50f, metadata))

        val sample = requireNotNull(
            SiteSurfaceSampler.sample(
                grid = grid,
                metadata = metadata,
                latitude = lat,
                longitude = lon,
                neighborhoodRadiusCells = 3,
            ),
        )

        assertEquals("steep", sample.localSlopeBucket)
    }

    @Test
    fun invalidCenterCellStillReportsNeighborhoodWhenPossible() {
        val width = 11
        val height = 11
        val elevations = FloatArray(width * height) { 20f }
        val valid = BooleanArray(width * height) { true }
        val centerCol = width / 2
        val centerRow = height / 2
        valid[centerRow * width + centerCol] = false
        val grid = ElevationGrid(
            width = width,
            height = height,
            bareEarth = elevations,
            canopySpikes = FloatArray(width * height),
            cellSizeMeters = 1f,
            validData = valid,
        )
        val (lat, lon) = requireNotNull(GeoSpatialLibrary.gridToGeographic(50f, 50f, metadata))

        val sample = requireNotNull(
            SiteSurfaceSampler.sample(
                grid = grid,
                metadata = metadata,
                latitude = lat,
                longitude = lon,
                neighborhoodRadiusCells = 2,
            ),
        )

        assertFalse(sample.cellValid)
        assertNull(sample.surfaceElevationMeters)
        assertEquals(0f, sample.relativeToLocalMeanMeters, 1e-4f)
        assertNotNull(sample.localSlopeBucket)
    }

    private fun flatGrid(width: Int, height: Int, elevation: Float): ElevationGrid =
        ElevationGrid(
            width = width,
            height = height,
            bareEarth = FloatArray(width * height) { elevation },
            canopySpikes = FloatArray(width * height),
            cellSizeMeters = 1f,
        )
}
