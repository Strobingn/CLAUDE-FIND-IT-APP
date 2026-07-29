package com.example.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonViewportTest {
    @Test
    fun fitScaleKeepsTheWholeLayerVisibleInAHalfWidthPane() {
        // A wide raster in a tall narrow comparison pane: contain-fit must be driven by width, and
        // the scaled image must fit entirely inside the pane rather than overflowing it.
        val fit = comparisonFitScale(
            viewportWidth = 400f,
            viewportHeight = 1000f,
            imageWidth = 2000f,
            imageHeight = 1000f,
        )

        assertEquals(0.2f, fit, 1e-5f)
        assertTrue("scaled width must fit the pane", 2000f * fit <= 400f + 1e-3f)
        assertTrue("scaled height must fit the pane", 1000f * fit <= 1000f + 1e-3f)
    }

    @Test
    fun atRestTheImageIsFullyVisibleSoPanningIsPinned() {
        val start = ComparisonViewport(zoom = 1f, pan = Offset.Zero)

        val result = applyComparisonGesture(
            current = start,
            centroid = Offset(200f, 500f),
            panChange = Offset(300f, 300f),
            zoomChange = 1f,
            viewportWidth = 400f,
            viewportHeight = 1000f,
            sourceWidth = 2000f,
            sourceHeight = 1000f,
        )

        // Nothing overflows at 1x, so there is nothing to pan into.
        assertEquals(1f, result.zoom, 1e-5f)
        assertEquals(0f, result.pan.x, 1e-4f)
        assertEquals(0f, result.pan.y, 1e-4f)
    }

    @Test
    fun whenTheLayerIsLetterboxedTheShortAxisStaysPinned() {
        // Contain-fit on a 2:1 raster in a 400x1000 pane is width-driven, so at 4x the image is
        // 1600x800 - still shorter than the pane. There is no vertical overflow to pan into.
        val result = applyComparisonGesture(
            current = ComparisonViewport(zoom = 4f, pan = Offset.Zero),
            centroid = Offset(200f, 500f),
            panChange = Offset(0f, 400f),
            zoomChange = 1f,
            viewportWidth = 400f,
            viewportHeight = 1000f,
            sourceWidth = 2000f,
            sourceHeight = 1000f,
        )

        assertEquals(0f, result.pan.y, 1e-4f)
    }

    @Test
    fun zoomKeepsThePointUnderTheCentroidFixed() {
        val paneWidth = 400f
        val paneHeight = 1000f
        val sourceWidth = 2000f
        val sourceHeight = 1000f
        // 6x overflows the pane on both axes (2400x1200), so anchoring is observable in both.
        val start = ComparisonViewport(zoom = 6f, pan = Offset.Zero)
        val centroid = Offset(120f, 400f)

        fun imageFractionUnder(state: ComparisonViewport): Offset {
            val fit = comparisonFitScale(paneWidth, paneHeight, sourceWidth, sourceHeight)
            val width = sourceWidth * fit * state.zoom
            val height = sourceHeight * fit * state.zoom
            val left = (paneWidth - width) * 0.5f + state.pan.x
            val top = (paneHeight - height) * 0.5f + state.pan.y
            return Offset((centroid.x - left) / width, (centroid.y - top) / height)
        }

        val before = imageFractionUnder(start)
        val zoomed = applyComparisonGesture(
            current = start,
            centroid = centroid,
            panChange = Offset.Zero,
            zoomChange = 1.5f,
            viewportWidth = paneWidth,
            viewportHeight = paneHeight,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
        )
        val after = imageFractionUnder(zoomed)

        assertEquals(9f, zoomed.zoom, 1e-4f)
        assertEquals("zoom must stay anchored horizontally", before.x, after.x, 1e-3f)
        assertEquals("zoom must stay anchored vertically", before.y, after.y, 1e-3f)
    }

    @Test
    fun panIsClampedToTheOverflowSoTheRasterCannotLeaveThePane() {
        // 8x gives 3200x1600 against a 400x1000 pane, so there is real overflow on both axes.
        val start = ComparisonViewport(zoom = 8f, pan = Offset.Zero)

        val result = applyComparisonGesture(
            current = start,
            centroid = Offset(200f, 500f),
            panChange = Offset(99_999f, 99_999f),
            zoomChange = 1f,
            viewportWidth = 400f,
            viewportHeight = 1000f,
            sourceWidth = 2000f,
            sourceHeight = 1000f,
        )

        val fit = comparisonFitScale(400f, 1000f, 2000f, 1000f)
        val maxPanX = (2000f * fit * 8f - 400f) * 0.5f
        val maxPanY = (1000f * fit * 8f - 1000f) * 0.5f

        assertTrue("both axes must overflow for this to test clamping", maxPanX > 0f && maxPanY > 0f)
        assertEquals(maxPanX, result.pan.x, 1e-3f)
        assertEquals(maxPanY, result.pan.y, 1e-3f)
    }

    @Test
    fun zoomIsClampedToTheSupportedRange() {
        val viewport = ComparisonViewport(zoom = 1f, pan = Offset.Zero)

        val zoomedOut = applyComparisonGesture(
            current = viewport,
            centroid = Offset(200f, 500f),
            panChange = Offset.Zero,
            zoomChange = 0.01f,
            viewportWidth = 400f,
            viewportHeight = 1000f,
            sourceWidth = 2000f,
            sourceHeight = 1000f,
        )
        val zoomedIn = applyComparisonGesture(
            current = ComparisonViewport(zoom = 12f, pan = Offset.Zero),
            centroid = Offset(200f, 500f),
            panChange = Offset.Zero,
            zoomChange = 10f,
            viewportWidth = 400f,
            viewportHeight = 1000f,
            sourceWidth = 2000f,
            sourceHeight = 1000f,
        )

        assertEquals(1f, zoomedOut.zoom, 1e-5f)
        assertEquals(16f, zoomedIn.zoom, 1e-5f)
    }
}
