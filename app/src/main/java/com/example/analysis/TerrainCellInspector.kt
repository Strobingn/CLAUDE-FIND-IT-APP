package com.example.analysis

import com.example.data.ElevationGrid
import com.example.geospatial.GeoSpatialLibrary
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class TerrainCellInspection(
    val column: Int,
    val row: Int,
    val xPercent: Float,
    val yPercent: Float,
    val valid: Boolean,
    val elevationMeters: Float,
    val bareEarthMeters: Float,
    val canopyHeightMeters: Float,
    val slopeDegrees: Float,
    val aspectDegrees: Float?,
    val curvaturePerMeter: Float,
    val localReliefMeters: Float,
    val ruggednessMeters: Float,
    val depressionDepthMeters: Float,
    val positiveOpennessDegrees: Float,
    val negativeOpennessDegrees: Float,
    val linearityResponse: Float,
    val neighborhoodRadiusMeters: Float,
    val validNeighborhoodCells: Int,
    val cellSizeMeters: Float,
    val latitude: Double?,
    val longitude: Double?,
)

/**
 * Resolves a displayed map position to one exact source raster cell and computes local metrics
 * directly from that cell's neighborhood. No bitmap resampling or screen-color approximation is
 * involved, so the reported values remain stable at every viewport zoom.
 */
object TerrainCellInspector {
    fun inspect(
        grid: ElevationGrid,
        metadata: GeoSpatialLibrary.GeoSpatialMetadata,
        xPercent: Float,
        yPercent: Float,
        vegetationFilter: Float,
        featureScaleMeters: Float,
    ): TerrainCellInspection {
        val column = ((xPercent.coerceIn(0f, 100f) / 100f) * (grid.width - 1))
            .roundToInt()
            .coerceIn(0, grid.width - 1)
        val row = ((yPercent.coerceIn(0f, 100f) / 100f) * (grid.height - 1))
            .roundToInt()
            .coerceIn(0, grid.height - 1)
        val index = row * grid.width + column
        val center = grid.getElevationAt(column, row, vegetationFilter)
        val cell = grid.cellSizeMeters.coerceAtLeast(0.001f)

        fun validAt(x: Int, y: Int): Boolean {
            val boundedX = x.coerceIn(0, grid.width - 1)
            val boundedY = y.coerceIn(0, grid.height - 1)
            return grid.validData[boundedY * grid.width + boundedX]
        }

        fun elevationAt(x: Int, y: Int): Float {
            val boundedX = x.coerceIn(0, grid.width - 1)
            val boundedY = y.coerceIn(0, grid.height - 1)
            return if (validAt(boundedX, boundedY)) {
                grid.getElevationAt(boundedX, boundedY, vegetationFilter)
            } else {
                center
            }
        }

        val z00 = elevationAt(column - 1, row - 1)
        val z01 = elevationAt(column, row - 1)
        val z02 = elevationAt(column + 1, row - 1)
        val z10 = elevationAt(column - 1, row)
        val z12 = elevationAt(column + 1, row)
        val z20 = elevationAt(column - 1, row + 1)
        val z21 = elevationAt(column, row + 1)
        val z22 = elevationAt(column + 1, row + 1)
        val dx = ((z02 + 2f * z12 + z22) - (z00 + 2f * z10 + z20)) / (8f * cell)
        val dy = ((z20 + 2f * z21 + z22) - (z00 + 2f * z01 + z02)) / (8f * cell)
        val slope = Math.toDegrees(atan(sqrt(dx * dx + dy * dy)).toDouble()).toFloat()
        val aspect = if (slope < 0.01f) {
            null
        } else {
            ((Math.toDegrees(atan2(dx, -dy).toDouble()).toFloat() + 360f) % 360f)
        }
        val curvature = (
            elevationAt(column - 1, row) + elevationAt(column + 1, row) +
                elevationAt(column, row - 1) + elevationAt(column, row + 1) -
                4f * center
            ) / (cell * cell)

        val maximumRadius = max(1, min(grid.width, grid.height) / 4)
        val radiusCells = (featureScaleMeters.coerceAtLeast(cell) / cell / 2f)
            .roundToInt()
            .coerceIn(1, min(12, maximumRadius))
        var sum = 0.0
        var squareSum = 0.0
        var validCount = 0
        var rimSum = 0.0
        var rimCount = 0
        for (y in row - radiusCells..row + radiusCells) {
            for (x in column - radiusCells..column + radiusCells) {
                if (x !in 0 until grid.width || y !in 0 until grid.height || !validAt(x, y)) continue
                val value = elevationAt(x, y).toDouble()
                sum += value
                squareSum += value * value
                validCount++
                if (abs(x - column) == radiusCells || abs(y - row) == radiusCells) {
                    rimSum += value
                    rimCount++
                }
            }
        }
        val mean = if (validCount > 0) sum / validCount else center.toDouble()
        val variance = if (validCount > 0) {
            max(0.0, squareSum / validCount - mean * mean)
        } else {
            0.0
        }
        val rimMean = if (rimCount > 0) rimSum / rimCount else center.toDouble()

        val dxx = abs(elevationAt(column - 1, row) - 2f * center + elevationAt(column + 1, row))
        val dyy = abs(elevationAt(column, row - 1) - 2f * center + elevationAt(column, row + 1))
        val d45 = abs(elevationAt(column - 1, row - 1) - 2f * center + elevationAt(column + 1, row + 1))
        val d135 = abs(elevationAt(column + 1, row - 1) - 2f * center + elevationAt(column - 1, row + 1))
        val directionalResponses = listOf(dxx, dyy, d45, d135).sortedDescending()
        val linearity = (directionalResponses[0] - directionalResponses[1] * 0.45f)
            .coerceAtLeast(0f) / cell

        val (positiveOpenness, negativeOpenness) = opennessAt(
            center = center,
            column = column,
            row = row,
            radiusCells = radiusCells,
            cellSizeMeters = cell,
            elevationAt = ::elevationAt,
        )
        val exactXPercent = if (grid.width == 1) 0f else column * 100f / (grid.width - 1)
        val exactYPercent = if (grid.height == 1) 0f else row * 100f / (grid.height - 1)
        val coordinate = GeoSpatialLibrary.gridToGeographic(exactXPercent, exactYPercent, metadata)

        return TerrainCellInspection(
            column = column,
            row = row,
            xPercent = exactXPercent,
            yPercent = exactYPercent,
            valid = grid.validData[index],
            elevationMeters = center,
            bareEarthMeters = grid.bareEarth[index],
            canopyHeightMeters = grid.canopySpikes[index].coerceAtLeast(0f),
            slopeDegrees = slope,
            aspectDegrees = aspect,
            curvaturePerMeter = curvature,
            localReliefMeters = (center - mean).toFloat(),
            ruggednessMeters = sqrt(variance).toFloat(),
            depressionDepthMeters = (rimMean - center).toFloat().coerceAtLeast(0f),
            positiveOpennessDegrees = positiveOpenness,
            negativeOpennessDegrees = negativeOpenness,
            linearityResponse = linearity,
            neighborhoodRadiusMeters = radiusCells * cell,
            validNeighborhoodCells = validCount,
            cellSizeMeters = cell,
            latitude = coordinate?.first,
            longitude = coordinate?.second,
        )
    }

    private fun opennessAt(
        center: Float,
        column: Int,
        row: Int,
        radiusCells: Int,
        cellSizeMeters: Float,
        elevationAt: (Int, Int) -> Float,
    ): Pair<Float, Float> {
        val directions = arrayOf(
            -1 to -1,
            0 to -1,
            1 to -1,
            1 to 0,
            1 to 1,
            0 to 1,
            -1 to 1,
            -1 to 0,
        )
        var positiveSum = 0.0
        var negativeSum = 0.0
        for ((stepX, stepY) in directions) {
            var highestAngle = -Math.PI / 2.0
            var deepestAngle = -Math.PI / 2.0
            for (step in 1..radiusCells) {
                val horizontalCells = sqrt((stepX * stepX + stepY * stepY).toDouble()) * step
                val distance = horizontalCells * cellSizeMeters
                val elevation = elevationAt(column + stepX * step, row + stepY * step)
                highestAngle = max(highestAngle, atan2((elevation - center).toDouble(), distance))
                deepestAngle = max(deepestAngle, atan2((center - elevation).toDouble(), distance))
            }
            positiveSum += 90.0 - Math.toDegrees(highestAngle)
            negativeSum += 90.0 - Math.toDegrees(deepestAngle)
        }
        return (positiveSum / directions.size).toFloat().coerceIn(0f, 180f) to
            (negativeSum / directions.size).toFloat().coerceIn(0f, 180f)
    }
}
