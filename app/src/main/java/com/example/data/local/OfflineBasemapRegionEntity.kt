package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.basemap.OfflineBasemapRegion
import com.example.data.basemap.OfflineBasemapStatus
import com.example.geospatial.GeoSpatialLibrary

@Entity(
    tableName = "offline_basemap_regions",
    indices = [Index("terrainKey")],
)
data class OfflineBasemapRegionEntity(
    @PrimaryKey val id: String,
    val terrainKey: String,
    val displayName: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val zoom: Int,
    val tileCount: Int,
    val completedTiles: Int,
    val estimatedBytes: Long,
    val storedBytes: Long,
    val status: String,
    val lastError: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

fun OfflineBasemapRegionEntity.toDomain() = OfflineBasemapRegion(
    id = id,
    terrainKey = terrainKey,
    displayName = displayName,
    bounds = GeoSpatialLibrary.GeographicBounds(minLat, maxLat, minLon, maxLon),
    zoom = zoom,
    tileCount = tileCount,
    completedTiles = completedTiles,
    estimatedBytes = estimatedBytes,
    storedBytes = storedBytes,
    status = runCatching { OfflineBasemapStatus.valueOf(status) }.getOrDefault(OfflineBasemapStatus.FAILED),
    lastError = lastError,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun OfflineBasemapRegion.toEntity() = OfflineBasemapRegionEntity(
    id = id,
    terrainKey = terrainKey,
    displayName = displayName,
    minLat = bounds.minLat,
    maxLat = bounds.maxLat,
    minLon = bounds.minLon,
    maxLon = bounds.maxLon,
    zoom = zoom,
    tileCount = tileCount,
    completedTiles = completedTiles,
    estimatedBytes = estimatedBytes,
    storedBytes = storedBytes,
    status = status.name,
    lastError = lastError,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
