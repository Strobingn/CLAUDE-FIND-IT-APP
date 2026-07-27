package com.example.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BreadcrumbTrackDao {
    @Query("SELECT * FROM breadcrumb_tracks WHERE terrainKey = :terrainKey ORDER BY updatedAtMillis DESC")
    fun observeByTerrainKey(terrainKey: String): Flow<List<BreadcrumbTrackEntity>>

    @Upsert
    suspend fun upsert(track: BreadcrumbTrackEntity)

    @Query("DELETE FROM breadcrumb_tracks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM breadcrumb_tracks WHERE terrainKey = :terrainKey")
    suspend fun deleteByTerrainKey(terrainKey: String)
}
