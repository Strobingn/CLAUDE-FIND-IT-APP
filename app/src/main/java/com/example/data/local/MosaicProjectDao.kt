package com.example.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MosaicProjectDao {
    @Query("SELECT * FROM mosaic_projects ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<MosaicProjectEntity>>

    @Upsert
    suspend fun upsert(project: MosaicProjectEntity)

    @Query("DELETE FROM mosaic_projects WHERE id = :id")
    suspend fun deleteById(id: String)
}
