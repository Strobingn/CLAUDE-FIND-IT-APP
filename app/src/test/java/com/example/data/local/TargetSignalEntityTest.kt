package com.example.data.local

import com.example.data.DetectionSource
import com.example.data.MetalType
import com.example.data.TargetSignal
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetSignalEntityTest {
    @Test
    fun cloudAiSourceSurvivesPersistenceMapping() {
        val signal = TargetSignal(
            gridX = 25f,
            gridY = 75f,
            metalType = MetalType.MAGNETIC_ANOMALY,
            signalStrength = 82f,
            source = DetectionSource.CLOUD_AI,
            terrainKey = "lidar:file:///cloud.laz",
        )
        assertEquals(DetectionSource.CLOUD_AI, signal.toEntity().toDomain().source)
    }

    @Test
    fun photoUrisSurviveDatabaseMapping() {
        val signal = TargetSignal(
            id = 42,
            gridX = 12f,
            gridY = 34f,
            metalType = MetalType.MANUAL_MARKER,
            signalStrength = 0f,
            source = DetectionSource.MANUAL,
            datasetKey = "analysis-42",
            terrainKey = "lidar:file:///terrain-42.laz",
            gpsLatitude = 42.1831,
            gpsLongitude = -73.8142,
            gpsAccuracyMeters = 4.5f,
            photoUris = listOf(
                "content://media/picker/first",
                "content://media/picker/second",
            ),
            voiceNoteUris = listOf("file:///private/field-note.m4a"),
        )

        val restored = signal.toEntity().toDomain()

        assertEquals(signal.photoUris, restored.photoUris)
        assertEquals(signal.voiceNoteUris, restored.voiceNoteUris)
        assertEquals(signal.datasetKey, restored.datasetKey)
        assertEquals(signal.terrainKey, restored.terrainKey)
        assertEquals(signal.gpsLatitude, restored.gpsLatitude)
        assertEquals(signal.gpsLongitude, restored.gpsLongitude)
        assertEquals(signal.gpsAccuracyMeters, restored.gpsAccuracyMeters)
    }

    @Test
    fun detectedFeatureTypeSurvivesDatabaseMapping() {
        val signal = TargetSignal(
            gridX = 5f,
            gridY = 6f,
            metalType = MetalType.MAGNETIC_ANOMALY,
            signalStrength = 40f,
            source = DetectionSource.AI_ANALYSIS,
            datasetKey = "analysis-99",
            detectedFeatureType = "FOUNDATION",
        )

        assertEquals("FOUNDATION", signal.toEntity().toDomain().detectedFeatureType)
    }

    @Test
    fun databaseMappingDropsBlankPhotoEntries() {
        val entity = TargetSignal(
            gridX = 0f,
            gridY = 0f,
            metalType = MetalType.MANUAL_MARKER,
            signalStrength = 0f,
        ).toEntity().copy(photoUris = "content://one\n\ncontent://two\n")

        assertEquals(listOf("content://one", "content://two"), entity.toDomain().photoUris)
    }
}
