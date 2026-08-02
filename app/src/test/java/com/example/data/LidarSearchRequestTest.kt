package com.example.data

import com.example.geospatial.GeoSpatialLibrary
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LidarSearchRequestTest {
    @After
    fun tearDown() = LidarSearchRequest.clear()

    private fun bounds(minLat: Double = 41.42) = GeoSpatialLibrary.GeographicBounds(
        minLat = minLat,
        maxLat = 41.44,
        minLon = -74.05,
        maxLon = -74.03,
    )

    @Test
    fun aRequestIsVisibleUntilItIsConsumed() {
        LidarSearchRequest.request(bounds())

        assertEquals(bounds(), LidarSearchRequest.pending.value)
        assertEquals(bounds(), LidarSearchRequest.consume())
    }

    /**
     * The picker consumes on every recomposition that sees a request. Returning to the tab later
     * must not silently repeat a search the user already ran.
     */
    @Test
    fun aRequestIsDeliveredExactlyOnce() {
        LidarSearchRequest.request(bounds())

        assertEquals(bounds(), LidarSearchRequest.consume())
        assertNull(LidarSearchRequest.consume())
        assertNull(LidarSearchRequest.pending.value)
    }

    @Test
    fun aNewerRequestReplacesOneThatWasNeverConsumed() {
        LidarSearchRequest.request(bounds(minLat = 41.42))
        LidarSearchRequest.request(bounds(minLat = 42.10))

        assertEquals(bounds(minLat = 42.10), LidarSearchRequest.consume())
        assertNull(LidarSearchRequest.consume())
    }

    @Test
    fun consumingWithNothingPendingIsHarmless() {
        assertNull(LidarSearchRequest.consume())
    }
}
