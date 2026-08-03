package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainRenderPerformanceTest {
    @Test
    fun progressiveOverviewOnlyForFullFootprintAbovePreviewResolution() {
        assertTrue(
            LidarImportOptions(rasterResolution = 512).wantsProgressiveOverview(),
        )
        assertFalse(
            LidarImportOptions(rasterResolution = 256).wantsProgressiveOverview(),
        )
        assertFalse(
            LidarImportOptions(
                rasterResolution = 1_024,
                focusBounds = NormalizedRasterBounds(0.2, 0.2, 0.8, 0.8),
            ).wantsProgressiveOverview(),
        )
        assertEquals(
            LidarImportOptions.PROGRESSIVE_PREVIEW_RESOLUTION,
            LidarImportOptions(rasterResolution = 512).progressivePreviewOptions().rasterResolution,
        )
    }

    @Test
    fun hillshadeDebounceIsLongerForHeavyAnalysisModes() {
        assertEquals(0L, hillshadeDebounceMs(visualizationMode = 5, immediate = true))
        assertEquals(80L, hillshadeDebounceMs(visualizationMode = 0, immediate = false))
        assertEquals(180L, hillshadeDebounceMs(visualizationMode = 3, immediate = false))
        assertEquals(180L, hillshadeDebounceMs(visualizationMode = 4, immediate = false))
        assertEquals(180L, hillshadeDebounceMs(visualizationMode = 5, immediate = false))
    }

    @Test
    fun previewMaxSideScalesWithZoom() {
        assertEquals(320, previewMaxSideForZoom(zoom = 1f, sourceMaxSide = 1_024))
        assertEquals(512, previewMaxSideForZoom(zoom = 2f, sourceMaxSide = 1_024))
        assertEquals(1_024, previewMaxSideForZoom(zoom = 3f, sourceMaxSide = 1_024))
        assertEquals(200, previewMaxSideForZoom(zoom = 1f, sourceMaxSide = 200))
    }

    @Test
    fun gridForHillshadePreviewDownsamplesLargeRasters() {
        val source = ElevationGrid(
            width = 640,
            height = 480,
            bareEarth = FloatArray(640 * 480) { it.toFloat() },
            canopySpikes = FloatArray(640 * 480),
        )
        val preview = gridForHillshadePreview(source, maxSide = 320)
        assertTrue(preview.width <= 320)
        assertTrue(preview.height <= 320)
        assertTrue(preview.width < source.width || preview.height < source.height)
    }

    @Test
    fun overviewUsesLowerSampleBudgetThanRefinedViewport() {
        val overview = LidarRasterizer(
            minX = 0.0,
            maxX = 100.0,
            minY = 0.0,
            maxY = 100.0,
            options = LidarImportOptions(rasterResolution = 512),
            declaredPointCount = 40_000_000,
        )
        val refined = LidarRasterizer(
            minX = 0.0,
            maxX = 100.0,
            minY = 0.0,
            maxY = 100.0,
            options = LidarImportOptions(
                rasterResolution = 512,
                focusBounds = NormalizedRasterBounds(0.25, 0.25, 0.75, 0.75),
            ),
            declaredPointCount = 40_000_000,
        )

        // Overview should skip more elevation getters (higher effective stride).
        var overviewElevation = 0
        var refinedElevation = 0
        repeat(10_000) {
            if (overview.nextPointWork() == LidarPointWork.ELEVATION) overviewElevation++
            overview.skipPoint()
            if (refined.nextPointWork() == LidarPointWork.ELEVATION) refinedElevation++
            refined.skipPoint()
        }
        assertTrue(
            "overview elev=$overviewElevation refined elev=$refinedElevation",
            overviewElevation <= refinedElevation,
        )
    }

    @Test
    fun overviewEarlyOutAfterScanBudget() {
        val rasterizer = LidarRasterizer(
            minX = 0.0,
            maxX = 10.0,
            minY = 0.0,
            maxY = 10.0,
            options = LidarImportOptions(rasterResolution = 128),
            declaredPointCount = 5_000_000,
        )
        assertFalse(rasterizer.shouldStopDecoding())
        var x = 0.0
        var y = 0.0
        while (!rasterizer.shouldStopDecoding() && rasterizer.pointsDecoded < 2_000_000L) {
            when (rasterizer.nextPointWork()) {
                LidarPointWork.SKIP -> rasterizer.skipPoint()
                LidarPointWork.COVERAGE -> rasterizer.addCoveragePoint(x, y)
                LidarPointWork.ELEVATION -> rasterizer.addPoint(x, y, 10f, classification = 2)
            }
            x = (x + 0.37) % 10.0
            y = (y + 0.53) % 10.0
        }
        assertTrue(rasterizer.shouldStopDecoding())
        assertTrue(rasterizer.pointsDecoded < 5_000_000L)
    }
}
