package com.example.data.export

import com.example.data.DetectionSource
import com.example.data.MetalType
import com.example.data.TargetSignal
import com.example.data.VerificationOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetSignalArchiveCodecTest {
    @Test
    fun signalsRoundTripThroughTheArchiveFormat() {
        val signals = listOf(
            TargetSignal(
                id = 42L,
                gridX = 61.5f,
                gridY = 12.25f,
                metalType = MetalType.SILVER,
                signalStrength = 87f,
                depthCm = 18,
                latitude = 41.123,
                longitude = -73.987,
                gpsLatitude = 41.1231,
                gpsLongitude = -73.9871,
                gpsAccuracyMeters = 4.2f,
                source = DetectionSource.AI_ANALYSIS,
                timestamp = 1_700_000_000_000L,
                notes = "Near the old wall\twith a tab & special chars",
                photoUris = listOf("content://media/1", "content://media/2"),
                photoBearingsDegrees = listOf(287.5f, null),
                voiceNoteUris = listOf("file:///voice/1.m4a"),
                status = "Excavated",
                outcome = VerificationOutcome.CONFIRMED_FEATURE,
                datasetKey = "dataset-1",
                terrainKey = "terrain-1",
                detectedFeatureType = "CELLAR_HOLE",
                starred = true,
            ),
            TargetSignal(
                id = 7L,
                gridX = 0f,
                gridY = 0f,
                metalType = MetalType.MANUAL_MARKER,
                signalStrength = 0f,
            ),
        )

        val file = TargetSignalArchiveCodec.encode(signals)
        val decoded = TargetSignalArchiveCodec.decode(file.bytes)

        assertEquals(signals, decoded)
    }

    @Test
    fun updatedAtMillisRoundTripsDistinctFromLoggedTimestamp() {
        val signal = TargetSignal(
            gridX = 1f,
            gridY = 1f,
            metalType = MetalType.GOLD,
            signalStrength = 50f,
            timestamp = 1_000L,
            updatedAtMillis = 5_000L,
        )

        val decoded = TargetSignalArchiveCodec.decode(TargetSignalArchiveCodec.encode(listOf(signal)).bytes)

        assertEquals(1_000L, decoded.single().timestamp)
        assertEquals(5_000L, decoded.single().updatedAtMillis)
    }

    @Test
    fun decodingAnArchiveWrittenBeforeUpdatedAtMillisExistedFallsBackToTimestamp() {
        // 23 tab-separated fields (22 named + starred), no trailing updatedAtMillis column —
        // the exact shape an archive from before this field existed would have.
        val bytes = (
            "FINDIT_TARGET_SIGNALS_V1\n" +
                "1\t1.0\t1.0\tGOLD\t50.0\t\t\t\t\t\t\tMANUAL\t9_000\t\t\t\t\tLogged\tUNVERIFIED\t\t\t\tfalse\n"
            ).replace("9_000", "9000").toByteArray()

        val decoded = TargetSignalArchiveCodec.decode(bytes)

        assertEquals(9000L, decoded.single().timestamp)
        assertEquals(9000L, decoded.single().updatedAtMillis)
    }

    @Test
    fun decodeReturnsEmptyForWrongHeaderOrGarbage() {
        assertTrue(TargetSignalArchiveCodec.decode("not the right header".toByteArray()).isEmpty())
        assertTrue(TargetSignalArchiveCodec.decode(byteArrayOf(1, 2, 3)).isEmpty())
    }

    @Test
    fun decodeSkipsMalformedRowsWithoutThrowing() {
        val bytes = "FINDIT_TARGET_SIGNALS_V1\nnot\tenough\tcolumns\n".toByteArray()

        assertTrue(TargetSignalArchiveCodec.decode(bytes).isEmpty())
    }

    @Test
    fun archiveImportSummaryReportsConflictsSeparately() {
        val summary = ArchiveImportSummary(
            projectName = "Test",
            imported = 1,
            updated = 2,
            keptLocal = 3,
            needsReview = listOf(
                TargetSignal(gridX = 1f, gridY = 1f, metalType = MetalType.GOLD, signalStrength = 50f),
            ),
        )

        assertTrue(summary.hasConflicts)
        assertEquals(1, summary.needsReview.size)
    }
}
