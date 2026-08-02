package com.example.data

import com.example.geospatial.GeoSpatialLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Carries a search box from the map to the tile picker.
 *
 * The two live on separate tabs, so the bounds cannot simply be passed down as a parameter. The
 * picker consumes a request exactly once, so coming back to the tab later does not silently re-run
 * a stale search.
 */
object LidarSearchRequest {
    private val _pending = MutableStateFlow<GeoSpatialLibrary.GeographicBounds?>(null)
    val pending: StateFlow<GeoSpatialLibrary.GeographicBounds?> = _pending.asStateFlow()

    fun request(bounds: GeoSpatialLibrary.GeographicBounds) {
        _pending.value = bounds
    }

    /** Returns the outstanding request and clears it, so it is acted on only once. */
    fun consume(): GeoSpatialLibrary.GeographicBounds? = _pending.getAndUpdate { null }

    fun clear() {
        _pending.value = null
    }
}
