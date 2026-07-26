package com.example.data.local

import com.example.data.basemap.OfflineBasemapRegion
import com.example.data.basemap.OfflineBasemapStatus
import com.example.geospatial.GeoSpatialLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineBasemapRegionEntityTest {
    @Test
    fun roundTripPreservesProjectBoundsProgressAndStatus() {
        val region = OfflineBasemapRegion(
            id = "region-1",
            terrainKey = "lidar:content://tile-a",
            displayName = "North woods",
            bounds = GeoSpatialLibrary.GeographicBounds(41.1, 41.2, -74.2, -74.1),
            zoom = 16,
            tileCount = 12,
            completedTiles = 9,
            estimatedBytes = 294_912L,
            storedBytes = 210_000L,
            status = OfflineBasemapStatus.CANCELED,
            lastError = "Download canceled",
            createdAtMillis = 100L,
            updatedAtMillis = 200L,
        )

        assertEquals(region, region.toEntity().toDomain())
    }
}
