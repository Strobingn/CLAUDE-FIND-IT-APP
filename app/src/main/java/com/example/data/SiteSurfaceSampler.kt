package com.example.data

import com.example.geospatial.GeoSpatialLibrary
import kotlin.math.roundToInt

/**
 * Relative surface elevation under a georeferenced find.
 *
 * This is **not** metal depth, buried-object depth, or dig depth. It only describes how the
 * bare-earth raster sits relative to its local neighborhood so field notes can capture
 * "high spot / low bowl / flat shelf" context.
 */
data class SurfaceZSample(
    /** Absolute Z if the grid carries real elevations; null means relative-only context. */
    val surfaceElevationMeters: Float?,
    /** Find Z minus local mean of valid bare-earth cells in the neighborhood. */
    val relativeToLocalMeanMeters: Float,
    /** "flat" | "gentle" | "steep" | "unknown" from neighborhood elev range / horizontal span. */
    val localSlopeBucket: String,
    val cellValid: Boolean,
    val disclaimer: String = "Relative surface context only. Not buried-object depth.",
)

/**
 * Samples bare-earth surface context under a lat/lon fix.
 * Returns null when the terrain is not georeferenced or the fix falls outside the mapped grid.
 */
object SiteSurfaceSampler {
    private const val FLAT_RATIO = 0.05f
    private const val GENTLE_RATIO = 0.15f

    fun sample(
        grid: ElevationGrid,
        metadata: GeoSpatialLibrary.GeoSpatialMetadata,
        latitude: Double,
        longitude: Double,
        neighborhoodRadiusCells: Int = 3,
    ): SurfaceZSample? {
        val (xPct, yPct) = GeoSpatialLibrary.geographicToGrid(latitude, longitude, metadata)
            ?: return null

        val col = ((xPct.coerceIn(0f, 100f) / 100f) * (grid.width - 1))
            .roundToInt()
            .coerceIn(0, grid.width - 1)
        val row = ((yPct.coerceIn(0f, 100f) / 100f) * (grid.height - 1))
            .roundToInt()
            .coerceIn(0, grid.height - 1)
        val centerIndex = row * grid.width + col
        val centerElev = grid.bareEarth[centerIndex]
        val cellValid = grid.validData[centerIndex] && centerElev.isFinite()

        val radius = neighborhoodRadiusCells.coerceAtLeast(0)
        var sum = 0.0
        var count = 0
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        for (r in (row - radius)..(row + radius)) {
            if (r !in 0 until grid.height) continue
            val rowBase = r * grid.width
            for (c in (col - radius)..(col + radius)) {
                if (c !in 0 until grid.width) continue
                val index = rowBase + c
                if (!grid.validData[index]) continue
                val elev = grid.bareEarth[index]
                if (!elev.isFinite()) continue
                sum += elev
                count++
                if (elev < minZ) minZ = elev
                if (elev > maxZ) maxZ = elev
            }
        }

        if (count == 0) {
            return SurfaceZSample(
                surfaceElevationMeters = null,
                relativeToLocalMeanMeters = 0f,
                localSlopeBucket = "unknown",
                cellValid = false,
            )
        }

        val localMean = (sum / count).toFloat()
        val relative = if (cellValid) centerElev - localMean else 0f
        val slopeBucket = slopeBucket(
            elevRange = maxZ - minZ,
            radiusCells = radius,
            cellSizeMeters = grid.cellSizeMeters,
            validCount = count,
        )

        // Absolute Z is only meaningful when the grid is georeferenced (real-world LiDAR/DEM).
        // Local synthetic rasters still report relative-to-mean context only.
        val absoluteZ = when {
            !cellValid -> null
            metadata.isGeoreferenced -> centerElev
            else -> null
        }

        return SurfaceZSample(
            surfaceElevationMeters = absoluteZ,
            relativeToLocalMeanMeters = relative,
            localSlopeBucket = slopeBucket,
            cellValid = cellValid,
        )
    }

    /**
     * Slope proxy: elevation range across the neighborhood divided by horizontal span
     * `2 * radius * cellSize`. Buckets are coarse field labels, not survey-grade grades.
     */
    private fun slopeBucket(
        elevRange: Float,
        radiusCells: Int,
        cellSizeMeters: Float,
        validCount: Int,
    ): String {
        if (radiusCells <= 0 || validCount < 2 || !elevRange.isFinite()) return "unknown"
        val cell = cellSizeMeters.coerceAtLeast(0.001f)
        val run = 2f * radiusCells * cell
        if (run <= 0f) return "unknown"
        val ratio = elevRange / run
        return when {
            ratio < FLAT_RATIO -> "flat"
            ratio < GENTLE_RATIO -> "gentle"
            else -> "steep"
        }
    }
}
