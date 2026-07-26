package com.example.geospatial

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BasemapTileRepositoryTest {
    @Test
    fun offlinePlanReportsBoundedTileCountAndMissingSizeBeforeDownload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.filesDir.resolve("offline_usgs_topo_tiles").deleteRecursively()
        val repository = BasemapTileRepository(context)
        val bounds = GeoSpatialLibrary.GeographicBounds(
            minLat = 41.42,
            maxLat = 41.44,
            minLon = -74.05,
            maxLon = -74.02,
        )

        val plan = repository.planOfflineRegion(bounds, maxTiles = 32, maxZoom = 17)

        assertTrue(plan.tileCount in 1..32)
        assertEquals(plan.tileCount, plan.missingTiles)
        assertEquals(0, plan.cachedTiles)
        assertTrue(plan.estimatedDownloadBytes > 0L)
    }
}
