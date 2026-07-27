package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.field.BreadcrumbTrack
import com.example.data.field.breadcrumbPointsFromStorage
import com.example.data.field.pointsToStorage

@Entity(
    tableName = "breadcrumb_tracks",
    indices = [Index("terrainKey"), Index(value = ["terrainKey", "isRecording"])],
)
data class BreadcrumbTrackEntity(
    @PrimaryKey val id: String,
    val terrainKey: String,
    val displayName: String,
    val pointsJson: String,
    val isRecording: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

fun BreadcrumbTrack.toEntity() = BreadcrumbTrackEntity(
    id = id,
    terrainKey = terrainKey,
    displayName = displayName,
    pointsJson = pointsToStorage(),
    isRecording = isRecording,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun BreadcrumbTrackEntity.toDomain() = BreadcrumbTrack(
    id = id,
    terrainKey = terrainKey,
    displayName = displayName,
    points = breadcrumbPointsFromStorage(pointsJson),
    isRecording = isRecording,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
