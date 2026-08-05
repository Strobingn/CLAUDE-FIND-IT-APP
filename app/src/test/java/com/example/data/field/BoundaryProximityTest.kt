package com.example.data.field

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundaryProximityTest {

    /**
     * ~111 m per degree lat; a 0.01° square is roughly 1.1 km on a side.
     * Center is deep inside; points near edges / outside exercise thresholds.
     */
    private fun squareBoundary(
        id: String = "b1",
        name: String = "North field",
        centerLat: Double = 43.12,
        centerLon: Double = -124.40,
        halfDeg: Double = 0.005,
    ): SurveyBoundary = SurveyBoundary(
        id = id,
        terrainKey = "terrain-1",
        displayName = name,
        vertices = listOf(
            BoundaryVertex(centerLat - halfDeg, centerLon - halfDeg),
            BoundaryVertex(centerLat - halfDeg, centerLon + halfDeg),
            BoundaryVertex(centerLat + halfDeg, centerLon + halfDeg),
            BoundaryVertex(centerLat + halfDeg, centerLon - halfDeg),
        ),
        createdAtMillis = 1_000L,
    )

    @Test
    fun unknownWhenNoBoundaries() {
        val alert = BoundaryProximity.evaluate(43.12, -124.40, emptyList())
        assertEquals(BoundaryProximityLevel.UNKNOWN, alert.level)
        assertNull(alert.distanceMeters)
        assertNull(alert.boundaryId)
    }

    @Test
    fun deepInsideIsInsideWithZeroDistance() {
        val boundary = squareBoundary()
        val alert = BoundaryProximity.evaluate(
            latitude = 43.12,
            longitude = -124.40,
            boundaries = listOf(boundary),
            nearEdgeMeters = 25.0,
        )
        assertEquals(BoundaryProximityLevel.INSIDE, alert.level)
        assertEquals(0.0, alert.distanceMeters)
        assertEquals("b1", alert.boundaryId)
        assertEquals("North field", alert.boundaryName)
        assertTrue(alert.message.contains("Inside", ignoreCase = true))
    }

    @Test
    fun nearInteriorEdgeIsNearEdge() {
        val boundary = squareBoundary(halfDeg = 0.005)
        // ~0.0049° south of north edge → a few meters inside the north edge.
        val alert = BoundaryProximity.evaluate(
            latitude = 43.12 + 0.005 - 0.00005,
            longitude = -124.40,
            boundaries = listOf(boundary),
            nearEdgeMeters = 25.0,
        )
        assertEquals(BoundaryProximityLevel.NEAR_EDGE, alert.level)
        assertTrue(alert.distanceMeters != null && alert.distanceMeters!! < 25.0)
        assertTrue(alert.message.contains("Near", ignoreCase = true))
    }

    @Test
    fun wellOutsideIsOutside() {
        val boundary = squareBoundary(halfDeg = 0.005)
        val alert = BoundaryProximity.evaluate(
            latitude = 43.12 + 0.05,
            longitude = -124.40,
            boundaries = listOf(boundary),
            nearEdgeMeters = 25.0,
        )
        assertEquals(BoundaryProximityLevel.OUTSIDE, alert.level)
        assertTrue(requireNotNull(alert.distanceMeters) > 25.0)
        assertEquals("b1", alert.boundaryId)
    }

    @Test
    fun justOutsideIsNearEdge() {
        val boundary = squareBoundary(halfDeg = 0.005)
        // ~5–10 m north of the north edge.
        val alert = BoundaryProximity.evaluate(
            latitude = 43.12 + 0.005 + 0.00008,
            longitude = -124.40,
            boundaries = listOf(boundary),
            nearEdgeMeters = 25.0,
        )
        assertEquals(BoundaryProximityLevel.NEAR_EDGE, alert.level)
        assertTrue(requireNotNull(alert.distanceMeters) <= 25.0)
    }

    @Test
    fun prefersContainingBoundaryOverNearbyOutside() {
        val inner = squareBoundary(id = "inner", name = "Inner", halfDeg = 0.002)
        val outer = squareBoundary(id = "outer", name = "Outer", halfDeg = 0.01)
        val alert = BoundaryProximity.evaluate(
            latitude = 43.12,
            longitude = -124.40,
            boundaries = listOf(outer, inner),
            nearEdgeMeters = 25.0,
        )
        // Center is inside both; tightest containing boundary (inner) should win.
        assertEquals(BoundaryProximityLevel.INSIDE, alert.level)
        assertEquals("inner", alert.boundaryId)
    }

    @Test
    fun degenerateBoundaryDoesNotCountAsUsable() {
        val bad = squareBoundary().copy(
            vertices = listOf(
                BoundaryVertex(43.12, -124.40),
                BoundaryVertex(43.13, -124.40),
            ),
        )
        val alert = BoundaryProximity.evaluate(43.12, -124.40, listOf(bad))
        assertEquals(BoundaryProximityLevel.UNKNOWN, alert.level)
    }
}
