package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.mosaic.MosaicProject
import com.example.data.mosaic.mosaicTilesFromManifest
import com.example.data.mosaic.tilesToManifest

@Entity(tableName = "mosaic_projects")
data class MosaicProjectEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val tileManifest: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

fun MosaicProject.toEntity() = MosaicProjectEntity(
    id = id,
    displayName = displayName,
    tileManifest = tilesToManifest(),
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun MosaicProjectEntity.toDomain() = MosaicProject(
    id = id,
    displayName = displayName,
    tiles = mosaicTilesFromManifest(tileManifest),
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
