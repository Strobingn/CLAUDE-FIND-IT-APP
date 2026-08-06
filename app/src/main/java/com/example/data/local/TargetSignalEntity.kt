package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.DetectionSource
import com.example.data.MetalType
import com.example.data.TargetSignal
import com.example.data.VerificationOutcome

@Entity(tableName = "target_signals")
data class TargetSignalEntity(
    @PrimaryKey val id: Long,
    val gridX: Float,
    val gridY: Float,
    val metalType: String,
    val signalStrength: Float,
    val depthCm: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsAccuracyMeters: Float? = null,
    val source: String,
    val timestamp: Long,
    val updatedAtMillis: Long = timestamp,
    val lastSyncedAtMillis: Long? = null,
    val notes: String,
    val photoUris: String,
    val photoBearingsDegrees: String = "",
    val voiceNoteUris: String = "",
    val status: String,
    val outcome: String = VerificationOutcome.UNVERIFIED.name,
    val datasetKey: String? = null,
    val terrainKey: String? = null,
    val detectedFeatureType: String? = null,
    val starred: Boolean = false,
)

fun TargetSignal.toEntity() = TargetSignalEntity(
    id = id,
    gridX = gridX,
    gridY = gridY,
    metalType = metalType.name,
    signalStrength = signalStrength,
    depthCm = depthCm,
    latitude = latitude,
    longitude = longitude,
    gpsLatitude = gpsLatitude,
    gpsLongitude = gpsLongitude,
    gpsAccuracyMeters = gpsAccuracyMeters,
    source = source.name,
    timestamp = timestamp,
    updatedAtMillis = updatedAtMillis,
    lastSyncedAtMillis = lastSyncedAtMillis,
    notes = notes,
    photoUris = photoUris.joinToString("\n") { it.replace("\n", "") },
    // Blank line = no bearing recorded for that photo; kept aligned by index with photoUris, so
    // blank lines are NOT filtered out on the way back in (unlike photoUris/voiceNoteUris).
    photoBearingsDegrees = photoBearingsDegrees.joinToString("\n") { it?.toString().orEmpty() },
    voiceNoteUris = voiceNoteUris.joinToString("\n") { it.replace("\n", "") },
    status = status,
    outcome = outcome.name,
    datasetKey = datasetKey,
    terrainKey = terrainKey,
    detectedFeatureType = detectedFeatureType,
    starred = starred,
)

fun TargetSignalEntity.toDomain() = TargetSignal(
    id = id,
    gridX = gridX,
    gridY = gridY,
    metalType = enumValueOrDefault(metalType, MetalType.MANUAL_MARKER),
    signalStrength = signalStrength,
    depthCm = depthCm,
    latitude = latitude,
    longitude = longitude,
    gpsLatitude = gpsLatitude,
    gpsLongitude = gpsLongitude,
    gpsAccuracyMeters = gpsAccuracyMeters,
    source = enumValueOrDefault(source, DetectionSource.MANUAL),
    timestamp = timestamp,
    updatedAtMillis = updatedAtMillis,
    lastSyncedAtMillis = lastSyncedAtMillis,
    notes = notes,
    photoUris = photoUris.lineSequence().filter { it.isNotBlank() }.toList(),
    photoBearingsDegrees = if (photoBearingsDegrees.isEmpty()) {
        emptyList()
    } else {
        photoBearingsDegrees.split("\n").map { it.toFloatOrNull() }
    },
    voiceNoteUris = voiceNoteUris.lineSequence().filter { it.isNotBlank() }.toList(),
    status = status,
    outcome = enumValueOrDefault(outcome, VerificationOutcome.UNVERIFIED),
    datasetKey = datasetKey,
    terrainKey = terrainKey,
    detectedFeatureType = detectedFeatureType,
    starred = starred,
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback
