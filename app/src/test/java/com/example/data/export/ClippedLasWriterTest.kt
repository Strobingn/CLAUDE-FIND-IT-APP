package com.example.data.export

import com.example.data.ElevationGrid
import com.example.data.NormalizedRasterBounds
import com.example.geospatial.GeoSpatialLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ClippedLasWriterTest {
    private fun tinyGrid(
        width: Int = 4,
        height: Int = 3,
        invalidIndex: Int? = null,
    ): ElevationGrid {
        val size = width * height
        val bare = FloatArray(size) { index -> 10f + index }
        val canopy = FloatArray(size)
        val valid = BooleanArray(size) { true }
        if (invalidIndex != null) valid[invalidIndex] = false
        return ElevationGrid(
            width = width,
            height = height,
            bareEarth = bare,
            canopySpikes = canopy,
            cellSizeMeters = 2f,
            validData = valid,
        )
    }

    private fun metadataWithBounds() = GeoSpatialLibrary.GeoSpatialMetadata(
        siteName = "Clip test",
        bounds = GeoSpatialLibrary.GeographicBounds(42.0, 42.01, -74.02, -74.01),
        crs = "EPSG:4326",
        datum = "WGS 84",
        resolutionMeters = 2.0,
        columns = 4,
        rows = 3,
    )

    @Test
    fun writesValidLasfHeaderAndPointsFromGeoreferencedGrid() {
        val bytes = ClippedLasWriter.writeFromElevationGrid(
            grid = tinyGrid(),
            metadata = metadataWithBounds(),
        )

        assertTrue(bytes.size > 227)
        assertEquals('L'.code.toByte(), bytes[0])
        assertEquals('A'.code.toByte(), bytes[1])
        assertEquals('S'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])

        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, header.get(24).toInt())
        assertEquals(2, header.get(25).toInt())
        assertEquals(227, header.getShort(94).toInt())
        assertEquals(227, header.getInt(96))
        assertEquals(0, header.get(104).toInt())
        assertEquals(20, header.getShort(105).toInt())
        val pointCount = header.getInt(107)
        assertTrue(pointCount > 0)
        assertEquals(227 + pointCount * 20, bytes.size)
    }

    @Test
    fun skipsInvalidCellsAndStillWritesPoints() {
        val grid = tinyGrid(invalidIndex = 0)
        val bytes = ClippedLasWriter.writeFromElevationGrid(
            grid = grid,
            metadata = metadataWithBounds(),
        )
        val pointCount = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(107)
        assertEquals(grid.width * grid.height - 1, pointCount)
    }

    @Test
    fun localGridUsesMetersWhenBoundsMissing() {
        val bytes = ClippedLasWriter.writeFromElevationGrid(
            grid = tinyGrid(width = 3, height = 3),
            metadata = GeoSpatialLibrary.localGrid("Local", columns = 3, rows = 3, resolutionMeters = 2.0),
        )
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(9, header.getInt(107))
        // Scale X should be centimetre precision for local meters.
        assertEquals(0.01, header.getDouble(131), 1e-12)
    }

    @Test
    fun normalizedBoundsAndMaxPointsSubsample() {
        val wide = tinyGrid(width = 20, height = 20)
        val half = ClippedLasWriter.writeFromElevationGrid(
            grid = wide,
            metadata = metadataWithBounds(),
            normalizedBounds = NormalizedRasterBounds(0.0, 0.0, 0.5, 0.5),
            maxPoints = 250_000,
        )
        val halfCount = ByteBuffer.wrap(half).order(ByteOrder.LITTLE_ENDIAN).getInt(107)
        assertTrue(halfCount in 1..100)

        val limited = ClippedLasWriter.writeFromElevationGrid(
            grid = wide,
            metadata = metadataWithBounds(),
            maxPoints = 16,
        )
        val limitedCount = ByteBuffer.wrap(limited).order(ByteOrder.LITTLE_ENDIAN).getInt(107)
        assertTrue(limitedCount in 1..16)
    }
}
