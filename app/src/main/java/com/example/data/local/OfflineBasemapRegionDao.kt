package com.example.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineBasemapRegionDao {
    @Query("SELECT * FROM offline_basemap_regions WHERE terrainKey = :terrainKey ORDER BY updatedAtMillis DESC")
    fun observeByTerrainKey(terrainKey: String): Flow<List<OfflineBasemapRegionEntity>>

    @Query("SELECT * FROM offline_basemap_regions")
    suspend fun getAll(): List<OfflineBasemapRegionEntity>

    @Upsert
    suspend fun upsert(region: OfflineBasemapRegionEntity)

    @Query("DELETE FROM offline_basemap_regions WHERE id = :id")
    suspend fun deleteById(id: String)
}
