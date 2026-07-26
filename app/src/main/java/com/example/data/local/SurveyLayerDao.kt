package com.example.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyLayerDao {
    @Query("SELECT * FROM survey_layers WHERE terrainKey = :terrainKey ORDER BY importedAtMillis DESC")
    fun observeByTerrainKey(terrainKey: String): Flow<List<SurveyLayerEntity>>

    @Upsert
    suspend fun upsert(layer: SurveyLayerEntity)

    @Query("DELETE FROM survey_layers WHERE id = :id")
    suspend fun deleteById(id: String)
}
