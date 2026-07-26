package com.example.data.basemap

import com.example.geospatial.GeoSpatialLibrary

enum class OfflineBasemapStatus {
    PLANNED,
    DOWNLOADING,
    READY,
    FAILED,
    CANCELED,
}

data class OfflineBasemapRegion(
    val id: String,
    val terrainKey: String,
    val displayName: String,
    val bounds: GeoSpatialLibrary.GeographicBounds,
    val zoom: Int,
    val tileCount: Int,
    val completedTiles: Int,
    val estimatedBytes: Long,
    val storedBytes: Long,
    val status: OfflineBasemapStatus,
    val lastError: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
