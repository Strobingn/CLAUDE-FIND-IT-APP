package com.example.data.local

import com.example.analysis.MetalDetectingTarget
import com.example.analysis.MetalDetectingTargetType
import com.example.geospatial.GeoSpatialLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The database snapshot is the only durable record of a dataset's ranked targets — the derived
 * layer cache lives in the cache directory, which Android may purge. These cover the round trip
 * that lets the workspace restore targets from it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalyzedDatasetSnapshotTest {
    private val metadata = GeoSpatialLibrary.GeoSpatialMetadata(
        siteName = "Test ridge",
        bounds = GeoSpatialLibrary.GeographicBounds(
            minLat = 41.42,
            maxLat = 41.44,
            minLon = -74.05,
            maxLon = -74.03,
        ),
    )

    private fun target(
        type: MetalDetectingTargetType = MetalDetectingTargetType.entries.first(),
        xPercent: Float = 25f,
        yPercent: Float = 60f,
        score: Float = 0.82f,
    ) = MetalDetectingTarget(
        type = type,
        xPercent = xPercent,
        yPercent = yPercent,
        score = score,
        radiusMeters = 6f,
        evidence = listOf("Flat interior: 82%"),
    )

    @Test
    fun aSnapshotRoundTripsEveryFieldTheMapNeeds() {
        val entity = buildAnalyzedDatasetEntity(
            datasetKey = "abc123",
            displayName = "Test ridge",
            metadata = metadata,
            targets = listOf(target()),
        )

        val restored = entity.parseTargets()

        assertEquals(1, restored.size)
        assertEquals(MetalDetectingTargetType.entries.first(), restored[0].type)
        assertEquals(25f, restored[0].xPercent, 0.001f)
        assertEquals(60f, restored[0].yPercent, 0.001f)
        assertEquals(0.82f, restored[0].score, 0.001f)
        assertEquals(listOf("Flat interior: 82%"), restored[0].evidence)
    }

    @Test
    fun theSnapshotIsKeyedToItsDataset() {
        val entity = buildAnalyzedDatasetEntity(
            datasetKey = "abc123",
            displayName = "Test ridge",
            metadata = metadata,
            targets = listOf(target()),
        )

        assertEquals("abc123", entity.datasetKey)
    }

    /** A georeferenced snapshot keeps real-world coordinates, so restored targets stay locatable. */
    @Test
    fun georeferencedTargetsKeepTheirCoordinates() {
        val entity = buildAnalyzedDatasetEntity(
            datasetKey = "abc123",
            displayName = "Test ridge",
            metadata = metadata,
            targets = listOf(target()),
        )

        val restored = entity.parseTargets().single()

        assertTrue("expected a latitude", restored.latitude != null)
        assertTrue("expected a longitude", restored.longitude != null)
    }

    @Test
    fun aCorruptPayloadYieldsNoTargetsRatherThanCrashing() {
        val entity = buildAnalyzedDatasetEntity(
            datasetKey = "abc123",
            displayName = "Test ridge",
            metadata = metadata,
            targets = listOf(target()),
        ).copy(targetsJson = "not json")

        assertTrue(entity.parseTargets().isEmpty())
    }

    @Test
    fun targetsRestoreInRankOrderWhenSortedByScore() {
        val entity = buildAnalyzedDatasetEntity(
            datasetKey = "abc123",
            displayName = "Test ridge",
            metadata = metadata,
            targets = listOf(target(score = 0.4f), target(score = 0.9f), target(score = 0.6f)),
        )

        val ranked = entity.parseTargets().sortedByDescending { it.score }.map { it.score }

        assertEquals(listOf(0.9f, 0.6f, 0.4f), ranked)
    }
}
