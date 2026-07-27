package com.example.data.local

import com.example.data.field.BreadcrumbPoint
import com.example.data.field.BreadcrumbTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreadcrumbTrackEntityTest {
    @Test
    fun persistenceRoundTripPreservesTrackAndPoints() {
        val track = BreadcrumbTrack(
            id = "trail-1",
            terrainKey = "lidar:content://north-woods.laz",
            displayName = "North woods trail",
            points = listOf(
                BreadcrumbPoint(42.1000, -73.8000, 4f, 1_000L),
                BreadcrumbPoint(42.1001, -73.8002, 6f, 8_000L),
            ),
            isRecording = true,
            createdAtMillis = 500L,
            updatedAtMillis = 8_000L,
        )

        assertEquals(track, track.toEntity().toDomain())
    }

    @Test
    fun jitterDoesNotCreateAnotherBreadcrumbPoint() {
        val first = BreadcrumbPoint(42.1000, -73.8000, 5f, 1_000L)
        val track = BreadcrumbTrack(
            id = "trail-2",
            terrainKey = "terrain-2",
            displayName = "GPS trail",
            points = listOf(first),
            isRecording = true,
            createdAtMillis = 1_000L,
            updatedAtMillis = 1_000L,
        )

        assertFalse(track.shouldAppend(first.copy(recordedAtMillis = 3_000L)))
        assertTrue(track.shouldAppend(first.copy(latitude = 42.1002, recordedAtMillis = 8_000L)))
    }
}
