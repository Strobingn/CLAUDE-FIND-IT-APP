package com.example.ui.components

import com.example.data.DetectionSource
import com.example.data.MetalType
import com.example.data.TargetSignal
import com.example.data.export.buildCsv
import com.example.data.export.buildGeoJson
import com.example.data.export.buildGpx
import com.example.data.export.buildKml
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GisExportTest {
    @Test
    fun exportsNeverInventCoordinates() {
        val local = TargetSignal(
            id = 1,
            gridX = 20f,
            gridY = 30f,
            metalType = MetalType.MANUAL_MARKER,
            signalStrength = 0f,
            source = DetectionSource.MANUAL,
            notes = "comma, quote \" test",
        )

        val csv = buildCsv(listOf(local))
        val gpx = buildGpx(listOf(local))

        assertFalse(csv.contains("38.8977"))
        assertTrue(csv.contains("\"\",\"\",\"\",\"\",\"\",\"Manual marker\""))
        assertFalse(gpx.contains("<wpt"))
    }

    @Test
    fun gpxUsesStoredCoordinatesAndEscapesNotes() {
        val located = TargetSignal(
            id = 2,
            gridX = 50f,
            gridY = 50f,
            metalType = MetalType.MAGNETIC_ANOMALY,
            signalStrength = 42f,
            latitude = 43.12,
            longitude = -124.40,
            source = DetectionSource.MAGNETOMETER,
            notes = "rock & nail <check>",
        )

        val gpx = buildGpx(listOf(located))

        assertTrue(gpx.contains("lat=\"43.1200000\""))
        assertTrue(gpx.contains("rock &amp; nail &lt;check&gt;"))
    }

    @Test
    fun kmlAndGeoJsonUseStoredCoordinates() {
        val located = TargetSignal(
            id = 3,
            gridX = 10f,
            gridY = 20f,
            metalType = MetalType.MANUAL_MARKER,
            signalStrength = 18f,
            latitude = 38.8977,
            longitude = -77.0365,
            source = DetectionSource.MANUAL,
            notes = "foundation edge",
        )

        val kml = buildKml(listOf(located))
        val geoJson = buildGeoJson(listOf(located))

        assertTrue(kml.contains("-77.0365000,38.8977000,0"))
        assertTrue(geoJson.contains("\"coordinates\":[-77.0365000,38.8977000]"))
        assertTrue(geoJson.contains("\"type\":\"FeatureCollection\""))
    }

    @Test
    fun csvAndGeoJsonPreserveMarkerTimeGpsProvenance() {
        val marked = TargetSignal(
            id = 4,
            gridX = 10f,
            gridY = 20f,
            metalType = MetalType.MANUAL_MARKER,
            signalStrength = 0f,
            latitude = 42.1000,
            longitude = -73.8000,
            gpsLatitude = 42.1002,
            gpsLongitude = -73.8003,
            gpsAccuracyMeters = 3.8f,
            source = DetectionSource.MANUAL,
        )

        assertTrue(buildCsv(listOf(marked)).contains("42.1002000"))
        assertTrue(buildGeoJson(listOf(marked)).contains("\"gpsAccuracyMeters\":3.8"))
    }

    @Test
    fun exportsDescribeAttachedVoiceNotesWithoutExposingPrivateFilePaths() {
        val marked = TargetSignal(
            id = 5,
            gridX = 10f,
            gridY = 20f,
            metalType = MetalType.MANUAL_MARKER,
            signalStrength = 0f,
            latitude = 42.1,
            longitude = -73.8,
            source = DetectionSource.MANUAL,
            voiceNoteUris = listOf("file:///private/field-voice-notes/target-5.m4a"),
        )

        val csv = buildCsv(listOf(marked))
        val geoJson = buildGeoJson(listOf(marked))
        val gpx = buildGpx(listOf(marked))

        assertTrue(csv.contains("VoiceNoteCount"))
        assertTrue(geoJson.contains("\"voiceNoteCount\":1"))
        assertTrue(gpx.contains("Voice notes: 1"))
        assertFalse(csv.contains("field-voice-notes"))
    }
}
