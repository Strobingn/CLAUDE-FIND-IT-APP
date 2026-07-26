package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.survey.SurveyDocumentParser
import com.example.data.survey.SurveyFormat
import com.example.data.survey.SurveyLayer

@Entity(
    tableName = "survey_layers",
    indices = [Index("terrainKey")],
)
data class SurveyLayerEntity(
    @PrimaryKey val id: String,
    val terrainKey: String,
    val displayName: String,
    val format: String,
    val sourceXml: String,
    val importedAtMillis: Long,
)

fun SurveyLayer.toEntity(activeTerrainKey: String) = SurveyLayerEntity(
    id = id,
    terrainKey = activeTerrainKey,
    displayName = displayName,
    format = format.name,
    sourceXml = sourceXml,
    importedAtMillis = importedAtMillis,
)

fun SurveyLayerEntity.toDomain(): SurveyLayer? = runCatching {
    SurveyDocumentParser.parse(sourceXml, displayName).copy(
        id = id,
        terrainKey = terrainKey,
        format = SurveyFormat.valueOf(format),
        importedAtMillis = importedAtMillis,
    )
}.getOrNull()
