package com.example.data

import org.junit.Assert.assertTrue
import org.junit.Test

class NysHistoricLazTileCatalogTest {
    @Test
    fun nationalMapLookupUsesPointBoundingBoxAndLidarDataset() {
        val url = NysHistoricLazTileCatalog().buildNationalMapUrl(
            west = -74.04,
            south = 41.43,
            east = -74.04,
            north = 41.43,
        )

        assertTrue(url.startsWith(NysHistoricLazTileCatalog.NATIONAL_MAP_PRODUCTS_URL))
        assertTrue(url.contains("Lidar+Point+Cloud+%28LPC%29"))
        assertTrue(url.contains("bbox="))
        assertTrue(url.contains("max=100"))
    }
}
