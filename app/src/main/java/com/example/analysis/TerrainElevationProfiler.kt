package com.example.analysis

import com.example.data.ElevationGrid
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

data class TerrainProfileSample(
    val distanceMeters: Float,
    val elevationMeters: Float,
    val valid: Boolean,
)

data class TerrainElevationProfile(
    val startXPercent: Float,
    val startYPercent: Float,
    val endXPercent: Float,
    val endYPercent: Float,
    val horizontalDistanceMeters: Float,
    val ascentMeters: Float,
    val descentMeters: Float,
    val minimumElevationMeters: Float,
    val maximumElevationMeters: Float,
    val samples: List<TerrainProfileSample>,
)

/** Samples the real grid cells under a selected path; no screen-pixel colors are used. */
object TerrainElevationProfiler {
    fun sample(
        grid: ElevationGrid,
        startXPercent: Float,
        startYPercent: Float,
        endXPercent: Float,
        endYPercent: Float,
        vegetationFilter: Float,
    ): TerrainElevationProfile {
        val startX = startXPercent.coerceIn(0f, 100f) / 100f * (grid.width - 1)
        val startY = startYPercent.coerceIn(0f, 100f) / 100f * (grid.height - 1)
        val endX = endXPercent.coerceIn(0f, 100f) / 100f * (grid.width - 1)
        val endY = endYPercent.coerceIn(0f, 100f) / 100f * (grid.height - 1)
        val distanceCells = hypot((endX - startX).toDouble(), (endY - startY).toDouble()).toFloat()
        val steps = ceil(maxOf(abs(endX - startX), abs(endY - startY)).toDouble()).toInt()
            .coerceIn(1, 2_048)
        val totalDistance = distanceCells * grid.cellSizeMeters.coerceAtLeast(0.001f)
        val samples = ArrayList<TerrainProfileSample>(steps + 1)
        var previousElevation: Float? = null
        var ascent = 0f
        var descent = 0f
        var minimum = Float.POSITIVE_INFINITY
        var maximum = Float.NEGATIVE_INFINITY
        for (step in 0..steps) {
            val fraction = step.toFloat() / steps
            val column = (startX + (endX - startX) * fraction).roundToInt().coerceIn(0, grid.width - 1)
            val row = (startY + (endY - startY) * fraction).roundToInt().coerceIn(0, grid.height - 1)
            val index = row * grid.width + column
            val valid = grid.validData[index]
            val elevation = grid.getElevationAt(column, row, vegetationFilter)
            if (valid) {
                minimum = minOf(minimum, elevation)
                maximum = maxOf(maximum, elevation)
                previousElevation?.let { previous ->
                    val change = elevation - previous
                    if (change > 0f) ascent += change else descent += -change
                }
                previousElevation = elevation
            }
            samples += TerrainProfileSample(
                distanceMeters = totalDistance * fraction,
                elevationMeters = elevation,
                valid = valid,
            )
        }
        if (!minimum.isFinite() || !maximum.isFinite()) {
            minimum = 0f
            maximum = 0f
        }
        return TerrainElevationProfile(
            startXPercent = startXPercent.coerceIn(0f, 100f),
            startYPercent = startYPercent.coerceIn(0f, 100f),
            endXPercent = endXPercent.coerceIn(0f, 100f),
            endYPercent = endYPercent.coerceIn(0f, 100f),
            horizontalDistanceMeters = totalDistance,
            ascentMeters = ascent,
            descentMeters = descent,
            minimumElevationMeters = minimum,
            maximumElevationMeters = maximum,
            samples = samples,
        )
    }
}
