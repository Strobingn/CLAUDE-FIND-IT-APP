package com.example.data

import com.example.data.field.BoundaryVertex
import com.example.data.field.SurveyBoundary
import com.example.geospatial.GeoSpatialLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundaryFocusMapperTest {

    private val metadata = GeoSpatialLibrary.SITES_METADATA[0]
    private val bounds = requireNotNull(metadata.bounds)

    @Test
    fun returnsNullWhenFewerThanThreeVertices() {
        val boundary = surveyBoundary(
            BoundaryVertex(bounds.minLat + 0.0001, bounds.minLon + 0.0001),
            BoundaryVertex(bounds.minLat + 0.0002, bounds.minLon + 0.0002),
        )
        assertNull(BoundaryFocusMapper.toNormalizedBounds(boundary, metadata))
    }

    @Test
    fun returnsNullWhenMetadataNotGeoreferenced() {
        val boundary = fullSiteBoundary()
        val local = GeoSpatialLibrary.localGrid("local", 100, 100)
        assertNull(BoundaryFocusMapper.toNormalizedBounds(boundary, local))
    }

    @Test
    fun mapsInteriorSquareToPaddedNormalizedBounds() {
        val latSpan = bounds.maxLat - bounds.minLat
        val lonSpan = bounds.maxLon - bounds.minLon
        // 25%–75% of the site footprint.
        val boundary = surveyBoundary(
            BoundaryVertex(bounds.minLat + 0.25 * latSpan, bounds.minLon + 0.25 * lonSpan),
            BoundaryVertex(bounds.minLat + 0.25 * latSpan, bounds.minLon + 0.75 * lonSpan),
            BoundaryVertex(bounds.minLat + 0.75 * latSpan, bounds.minLon + 0.75 * lonSpan),
            BoundaryVertex(bounds.minLat + 0.75 * latSpan, bounds.minLon + 0.25 * lonSpan),
        )

        val focus = requireNotNull(BoundaryFocusMapper.toNormalizedBounds(boundary, metadata))

        // Unpadded box is 0.25–0.75 in x; y is inverted (north = low yPct).
        // 2% pad of 0.5 span = 0.01 → 0.24–0.76 before sanitized clamp.
        assertTrue(focus.left in 0.23..0.25)
        assertTrue(focus.right in 0.75..0.77)
        assertTrue(focus.top in 0.23..0.25)
        assertTrue(focus.bottom in 0.75..0.77)
        assertTrue(focus.left < focus.right)
        assertTrue(focus.top < focus.bottom)
        // sanitized() keeps values inside [0,1]
        assertTrue(focus.left >= 0.0)
        assertTrue(focus.right <= 1.0)
    }

    @Test
    fun axisAlignedLatLonBoxReturnsMinMaxCorners() {
        val boundary = surveyBoundary(
            BoundaryVertex(10.0, -20.0),
            BoundaryVertex(12.0, -18.0),
            BoundaryVertex(11.0, -19.0),
        )

        val box = requireNotNull(BoundaryFocusMapper.axisAlignedLatLonBox(boundary))
        val (minCorner, maxCorner) = box
        assertEquals(10.0, minCorner.first, 1e-9)
        assertEquals(-20.0, minCorner.second, 1e-9)
        assertEquals(12.0, maxCorner.first, 1e-9)
        assertEquals(-18.0, maxCorner.second, 1e-9)
    }

    @Test
    fun axisAlignedLatLonBoxNullForEmptyVertices() {
        val empty = SurveyBoundary(
            id = "empty",
            terrainKey = "t",
            displayName = "Empty",
            vertices = emptyList(),
            createdAtMillis = 0L,
        )
        assertNull(BoundaryFocusMapper.axisAlignedLatLonBox(empty))
    }

    @Test
    fun fullSiteBoundaryFocusIsNearFullAfterPadAndSanitize() {
        val focus = BoundaryFocusMapper.toNormalizedBounds(fullSiteBoundary(), metadata)
        assertNotNull(focus)
        // Site corners map near 0/1; pad + sanitized keeps a valid viewport.
        assertTrue(focus!!.left < 0.05)
        assertTrue(focus.top < 0.05)
        assertTrue(focus.right > 0.95)
        assertTrue(focus.bottom > 0.95)
    }

    private fun fullSiteBoundary(): SurveyBoundary = surveyBoundary(
        BoundaryVertex(bounds.minLat, bounds.minLon),
        BoundaryVertex(bounds.minLat, bounds.maxLon),
        BoundaryVertex(bounds.maxLat, bounds.maxLon),
        BoundaryVertex(bounds.maxLat, bounds.minLon),
    )

    private fun surveyBoundary(vararg vertices: BoundaryVertex): SurveyBoundary =
        SurveyBoundary(
            id = "b1",
            terrainKey = "terrain-1",
            displayName = "Test field",
            vertices = vertices.toList(),
            createdAtMillis = 1L,
        )
}
