package com.example.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyzedDatasetDao {
    @Query("SELECT * FROM analyzed_datasets ORDER BY analyzedAtMillis DESC")
    fun observeAll(): Flow<List<AnalyzedDatasetEntity>>

    @Query("SELECT * FROM analyzed_datasets WHERE datasetKey = :datasetKey LIMIT 1")
    suspend fun getByKey(datasetKey: String): AnalyzedDatasetEntity?

    @Upsert
    suspend fun upsert(entity: AnalyzedDatasetEntity)

    @Query("DELETE FROM analyzed_datasets WHERE datasetKey = :datasetKey")
    suspend fun deleteByKey(datasetKey: String)
}
