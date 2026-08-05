package com.example.data.export

import com.example.data.ElevationGrid
import com.example.data.NormalizedRasterBounds
import com.example.geospatial.GeoSpatialLibrary
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Writes a valid uncompressed LAS 1.2 point cloud (point format 0) from an elevation grid
 * clipped to an AOI. This is a surface-sample export for field handoff, not original pulse returns.
 */
object ClippedLasWriter {
    private const val HEADER_SIZE = 227
    private const val RECORD_LENGTH = 20
    private const val POINT_FORMAT = 0

    /**
     * Writes LAS 1.2 point records from elevation grid samples inside [normalizedBounds].
     * Header must be valid LASF.
     *
     * When [metadata] carries geographic bounds, X/Y are lon/lat (degrees). Otherwise X/Y are
     * local meters from the SW corner using [ElevationGrid.cellSizeMeters]. Z is bare-earth
     * elevation in meters. Invalid cells are skipped; the grid is strided when denser than
     * [maxPoints].
     */
    fun writeFromElevationGrid(
        grid: ElevationGrid,
        metadata: GeoSpatialLibrary.GeoSpatialMetadata,
        normalizedBounds: NormalizedRasterBounds = NormalizedRasterBounds.Full,
        maxPoints: Int = 250_000,
    ): ByteArray {
        require(maxPoints > 0) { "maxPoints must be positive" }
        val aoi = normalizedBounds.sanitized()
        val colStart = floor(aoi.left * grid.width).toInt().coerceIn(0, grid.width - 1)
        val colEndExclusive = ceil(aoi.right * grid.width).toInt().coerceIn(colStart + 1, grid.width)
        val rowStart = floor(aoi.top * grid.height).toInt().coerceIn(0, grid.height - 1)
        val rowEndExclusive = ceil(aoi.bottom * grid.height).toInt().coerceIn(rowStart + 1, grid.height)

        val colSpan = colEndExclusive - colStart
        val rowSpan = rowEndExclusive - rowStart
        var stride = 1
        while ((colSpan + stride - 1) / stride * ((rowSpan + stride - 1) / stride) > maxPoints) {
            stride++
        }

        val geoBounds = metadata.bounds
        val widthDenom = max(1, grid.width - 1).toDouble()
        val heightDenom = max(1, grid.height - 1).toDouble()
        val cell = grid.cellSizeMeters.toDouble().coerceAtLeast(0.001)

        data class Sample(val x: Double, val y: Double, val z: Double)

        val samples = ArrayList<Sample>(min(maxPoints, colSpan * rowSpan / (stride * stride) + 1))
        var row = rowStart
        while (row < rowEndExclusive) {
            var col = colStart
            while (col < colEndExclusive) {
                val index = row * grid.width + col
                if (grid.validData[index]) {
                    val z = grid.bareEarth[index].toDouble()
                    if (z.isFinite()) {
                        val (x, y) = if (geoBounds != null) {
                            val lon = geoBounds.minLon + (col / widthDenom) * (geoBounds.maxLon - geoBounds.minLon)
                            val lat = geoBounds.maxLat - (row / heightDenom) * (geoBounds.maxLat - geoBounds.minLat)
                            lon to lat
                        } else {
                            val xMeters = col * cell
                            val yMeters = (grid.height - 1 - row) * cell
                            xMeters to yMeters
                        }
                        samples.add(Sample(x, y, z))
                    }
                }
                col += stride
            }
            row += stride
        }
        require(samples.isNotEmpty()) { "No valid elevation samples in the requested AOI" }

        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY
        for (sample in samples) {
            if (sample.x < minX) minX = sample.x
            if (sample.x > maxX) maxX = sample.x
            if (sample.y < minY) minY = sample.y
            if (sample.y > maxY) maxY = sample.y
            if (sample.z < minZ) minZ = sample.z
            if (sample.z > maxZ) maxZ = sample.z
        }

        // Degrees need a fine scale; local meters use centimetre precision.
        val scaleXy = if (geoBounds != null) 1e-7 else 0.01
        val scaleZ = 0.01
        val offsetX = minX
        val offsetY = minY
        val offsetZ = minZ

        val pointCount = samples.size
        val totalSize = HEADER_SIZE + pointCount * RECORD_LENGTH
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // --- LAS 1.2 public header block ---
        buffer.put("LASF".toByteArray(Charsets.US_ASCII))
        buffer.putShort(0) // File Source ID
        buffer.putShort(0) // Global Encoding
        // Project ID GUID (16 zeros)
        repeat(16) { buffer.put(0) }
        buffer.put(1) // Version Major
        buffer.put(2) // Version Minor
        putFixedAscii(buffer, "Find It", 32) // System Identifier
        putFixedAscii(buffer, "Find It ClippedLasWriter", 32) // Generating Software
        buffer.putShort(1) // File Creation Day of Year
        buffer.putShort(2026) // File Creation Year
        buffer.putShort(HEADER_SIZE.toShort())
        buffer.putInt(HEADER_SIZE) // Offset to point data
        buffer.putInt(0) // Number of VLRs
        buffer.put(POINT_FORMAT.toByte())
        buffer.putShort(RECORD_LENGTH.toShort())
        buffer.putInt(pointCount) // Legacy number of point records
        // Number of points by return (5 x uint32); all points counted as return 1.
        buffer.putInt(pointCount)
        repeat(4) { buffer.putInt(0) }
        buffer.putDouble(scaleXy)
        buffer.putDouble(scaleXy)
        buffer.putDouble(scaleZ)
        buffer.putDouble(offsetX)
        buffer.putDouble(offsetY)
        buffer.putDouble(offsetZ)
        buffer.putDouble(maxX)
        buffer.putDouble(minX)
        buffer.putDouble(maxY)
        buffer.putDouble(minY)
        buffer.putDouble(maxZ)
        buffer.putDouble(minZ)
        check(buffer.position() == HEADER_SIZE) {
            "LAS header size mismatch: wrote ${buffer.position()} expected $HEADER_SIZE"
        }

        // --- Point records (format 0) ---
        for (sample in samples) {
            buffer.putInt(((sample.x - offsetX) / scaleXy).toInt())
            buffer.putInt(((sample.y - offsetY) / scaleXy).toInt())
            buffer.putInt(((sample.z - offsetZ) / scaleZ).toInt())
            buffer.putShort(0) // intensity
            buffer.put(0b00001001.toByte()) // return 1 of 1
            buffer.put(2) // classification: ground
            buffer.put(0) // scan angle
            buffer.put(0) // user data
            buffer.putShort(0) // point source ID
        }

        return buffer.array()
    }

    private fun putFixedAscii(buffer: ByteBuffer, value: String, length: Int) {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        val copy = min(bytes.size, length)
        buffer.put(bytes, 0, copy)
        repeat(length - copy) { buffer.put(0) }
    }
}
