package com.example.data

import com.example.geospatial.GeoSpatialLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MosaicTerrainBuilderTest {
    @Test
    fun adjacentTilesBecomeOneGeoreferencedTerrainWithNoDataPreserved() {
        fun tile(name: String, bounds: GeoSpatialLibrary.GeographicBounds, elevation: Float) = MosaicTerrainTile(
            displayName = name,
            bounds = bounds,
            terrain = DemGenerator.TerrainLoadResult(
                grid = ElevationGrid(
                    width = 3,
                    height = 3,
                    bareEarth = FloatArray(9) { elevation },
                    canopySpikes = FloatArray(9),
                    cellSizeMeters = 1f,
                ),
                summary = name,
                isBareEarth = true,
            ),
        )
        val mosaic = MosaicTerrainBuilder.build(
            "North woods",
            listOf(
                tile("west.laz", GeoSpatialLibrary.GeographicBounds(42.0, 42.001, -74.001, -74.0), 10f),
                tile("east.laz", GeoSpatialLibrary.GeographicBounds(42.0, 42.001, -74.0, -73.999), 20f),
            ),
        )

        assertEquals("North woods", mosaic.geoMetadata?.siteName)
        assertEquals(-74.001, mosaic.geoMetadata?.bounds?.minLon)
        assertEquals(-73.999, mosaic.geoMetadata?.bounds?.maxLon)
        assertTrue(mosaic.grid.validData.any { it })
        assertTrue(mosaic.grid.bareEarth.any { it == 10f })
        assertTrue(mosaic.grid.bareEarth.any { it == 20f })
    }
}
