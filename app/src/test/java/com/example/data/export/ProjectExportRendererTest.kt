package com.example.data.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.example.data.DetectionSource
import com.example.data.MetalType
import com.example.data.TargetSignal
import com.example.geospatial.GeoSpatialLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectExportRendererTest {
    private val terrain = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.rgb(110, 92, 78))
    }

    private val snapshot = ProjectExportSnapshot(
        projectName = "Test Woods",
        terrainKey = "lidar:test",
        summary = "Full-footprint terrain test",
        metadata = GeoSpatialLibrary.GeoSpatialMetadata(
            siteName = "Test Woods",
            bounds = null,
            crs = "Local grid (CRS unavailable)",
            resolutionMeters = 1.5,
            columns = 160,
            rows = 120,
        ),
        terrainBitmap = terrain,
        visualizationLabel = "Local relief",
        targets = listOf(
            TargetSignal(
                id = 7,
                gridX = 50f,
                gridY = 40f,
                metalType = MetalType.MANUAL_MARKER,
                signalStrength = 0f,
                source = DetectionSource.MANUAL,
                terrainKey = "lidar:test",
            ),
        ),
        surveyLayers = emptyList(),
        generatedAtMillis = 1_700_000_000_000L,
    )

    @Test
    fun annotatedTerrainPreservesWholeRasterAndAddsReportBands() {
        val rendered = ProjectExportRenderer.renderAnnotatedTerrain(snapshot)

        assertTrue(rendered.width >= terrain.width)
        assertTrue(rendered.width >= 1200)
        assertTrue(rendered.height > terrain.height)
    }

    @Test
    fun annotatedTerrainProducesValidPng() {
        val bytes = ProjectExportRenderer.renderAnnotatedTerrain(snapshot).toPngBytesForTest()

        assertTrue(bytes.take(8).toByteArray().contentEquals(PNG_SIGNATURE))
    }

    @Test
    fun comparisonExportContainsBothFullLayers() {
        val right = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GRAY)
        }

        val bytes = ProjectExportRenderer.renderComparisonPng(
            left = terrain,
            leftLabel = "Local relief",
            right = right,
            rightLabel = "Slope",
            projectName = "Test Woods",
        )

        assertTrue(bytes.take(8).toByteArray().contentEquals(PNG_SIGNATURE))
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(1_200, decoded.width)
        assertTrue(decoded.height >= 730)
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }

    private fun Bitmap.toPngBytesForTest(): ByteArray =
        java.io.ByteArrayOutputStream().use { output ->
            assertTrue(compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
}
