package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.ElevationGrid
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Clusters high-scoring disturbance cells into discrete anomaly regions.
 *
 * Uses connected-component labeling on the disturbance score grid
 * (the same analysis used by visualization mode 5).
 */
object AnomalyClusterer {

    private const val TAG = "AnomalyClusterer"

    /**
     * Find anomaly regions in the elevation grid.
     *
     * @param grid The elevation grid to analyze.
     * @param hillshadeBitmap The rendered terrain bitmap for cropping region previews.
     * @param scoreThreshold Minimum disturbance score (0–1) to consider a cell part of a cluster.
     * @param minClusterCells Minimum number of cells for a cluster to be reported.
     * @param maxRegions Maximum number of regions to return (top by mean score).
     * @param sensitivity Analysis sensitivity multiplier (same as UI control).
     * @param featureScaleMeters Feature scale in meters (same as UI control).
     */
    fun findAnomalyRegions(
        grid: ElevationGrid,
        hillshadeBitmap: Bitmap?,
        scoreThreshold: Float = 0.35f,
        minClusterCells: Int = 12,
        maxRegions: Int = 8,
        sensitivity: Float = 1.2f,
        featureScaleMeters: Float = 6f,
    ): List<AnomalyRegion> {
        val width = grid.width
        val height = grid.height
        val cellDistance = grid.cellSizeMeters.coerceAtLeast(0.001f)

        // Compute per-cell elevations (bare earth, full vegetation filter for ground analysis).
        val elevations = FloatArray(width * height)
        for (index in elevations.indices) {
            elevations[index] = grid.bareEarth[index]
        }

        // Compute analysis radius from feature scale.
        val analysisRadius = (featureScaleMeters.coerceAtLeast(cellDistance) / cellDistance)
            .toInt()
            .coerceIn(1, max(1, min(width, height) / 4))

        // Local statistics (residual + roughness).
        val local = localStatistics(elevations, width, height, analysisRadius)

        // Curvature.
        val curvature = curvature(elevations, width, height, cellDistance)

        val residualScale = robustMagnitudeScale(local.residual)
        val roughnessScale = robustMagnitudeScale(local.roughness)
        val curvatureScale = robustMagnitudeScale(curvature)

        // Compute disturbance scores (same formula as ElevationGrid mode 5).
        val scores = FloatArray(width * height)
        for (index in scores.indices) {
            if (!grid.validData[index]) continue
            val residual = abs(local.residual[index]) / residualScale
            val roughness = local.roughness[index] / roughnessScale
            val bend = abs(curvature[index]) / curvatureScale
            scores[index] = ((residual * 0.58f + bend * 0.27f + roughness * 0.15f) *
                sensitivity.coerceIn(0.4f, 2.5f)).coerceIn(0f, 1f)
        }

        // Connected-component labeling.
        val labels = IntArray(width * height) { -1 }
        var nextLabel = 0
        val clusters = mutableListOf<MutableList<Int>>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (scores[index] < scoreThreshold || labels[index] >= 0) continue

                // BFS flood fill.
                val cluster = mutableListOf<Int>()
                val queue = ArrayDeque<Int>()
                queue.addLast(index)
                labels[index] = nextLabel

                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    cluster.add(current)
                    val cx = current % width
                    val cy = current / width

                    for ((dx, dy) in arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                        val nx = cx + dx
                        val ny = cy + dy
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                        val ni = ny * width + nx
                        if (labels[ni] >= 0 || scores[ni] < scoreThreshold) continue
                        labels[ni] = nextLabel
                        queue.addLast(ni)
                    }
                }
                clusters.add(cluster)
                nextLabel++
            }
        }

        Log.d(TAG, "Found ${clusters.size} raw clusters, filtering to min $minClusterCells cells")

        // Build AnomalyRegion for qualifying clusters.
        val regions = clusters
            .filter { it.size >= minClusterCells }
            .map { cluster ->
                var sumScore = 0f
                var minCol = width
                var maxCol = 0
                var minRow = height
                var maxRow = 0
                var sumCol = 0L
                var sumRow = 0L

                for (index in cluster) {
                    val col = index % width
                    val row = index / width
                    sumScore += scores[index]
                    sumCol += col
                    sumRow += row
                    if (col < minCol) minCol = col
                    if (col > maxCol) maxCol = col
                    if (row < minRow) minRow = row
                    if (row > maxRow) maxRow = row
                }

                val centerCol = (sumCol / cluster.size).toInt()
                val centerRow = (sumRow / cluster.size).toInt()
                val meanScore = sumScore / cluster.size

                // Crop with context padding.
                val padCols = max(4, (maxCol - minCol) / 3)
                val padRows = max(4, (maxRow - minRow) / 3)
                val cropLeft = (minCol - padCols).coerceAtLeast(0)
                val cropTop = (minRow - padRows).coerceAtLeast(0)
                val cropRight = (maxCol + padCols).coerceAtMost(width - 1)
                val cropBottom = (maxRow + padRows).coerceAtMost(height - 1)
                val cropWidth = cropRight - cropLeft + 1
                val cropHeight = cropBottom - cropTop + 1

                val croppedBitmap = if (hillshadeBitmap != null &&
                    cropLeft + cropWidth <= hillshadeBitmap.width &&
                    cropTop + cropHeight <= hillshadeBitmap.height &&
                    cropWidth > 0 && cropHeight > 0
                ) {
                    runCatching {
                        Bitmap.createBitmap(hillshadeBitmap, cropLeft, cropTop, cropWidth, cropHeight)
                    }.getOrNull()
                } else {
                    null
                }

                AnomalyRegion(
                    centerCol = centerCol,
                    centerRow = centerRow,
                    boundsLeft = minCol,
                    boundsTop = minRow,
                    boundsRight = maxCol,
                    boundsBottom = maxRow,
                    meanScore = meanScore,
                    cellCount = cluster.size,
                    croppedBitmap = croppedBitmap,
                )
            }
            .sortedByDescending { it.meanScore }
            .take(maxRegions)

        Log.d(TAG, "Returning ${regions.size} anomaly regions")
        return regions
    }

    // ---- Analysis helpers (mirrors ElevationGrid logic for standalone use) ----

    private data class LocalStats(val residual: FloatArray, val roughness: FloatArray)

    private fun localStatistics(
        elevations: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
    ): LocalStats {
        val stride = width + 1
        val sum = DoubleArray((width + 1) * (height + 1))
        val squareSum = DoubleArray(sum.size)
        for (y in 0 until height) {
            var rowSum = 0.0
            var rowSquareSum = 0.0
            for (x in 0 until width) {
                val value = elevations[y * width + x].toDouble()
                rowSum += value
                rowSquareSum += value * value
                sum[(y + 1) * stride + x + 1] = sum[y * stride + x + 1] + rowSum
                squareSum[(y + 1) * stride + x + 1] = squareSum[y * stride + x + 1] + rowSquareSum
            }
        }
        val residual = FloatArray(elevations.size)
        val roughness = FloatArray(elevations.size)
        for (y in 0 until height) {
            val y0 = (y - radius).coerceAtLeast(0)
            val y1 = (y + radius).coerceAtMost(height - 1)
            for (x in 0 until width) {
                val x0 = (x - radius).coerceAtLeast(0)
                val x1 = (x + radius).coerceAtMost(width - 1)
                val count = (x1 - x0 + 1) * (y1 - y0 + 1)
                val localSum = rectSum(sum, stride, x0, y0, x1, y1)
                val localSqSum = rectSum(squareSum, stride, x0, y0, x1, y1)
                val mean = localSum / count
                val variance = max(0.0, localSqSum / count - mean * mean)
                val index = y * width + x
                residual[index] = (elevations[index] - mean).toFloat()
                roughness[index] = sqrt(variance).toFloat()
            }
        }
        return LocalStats(residual, roughness)
    }

    private fun curvature(
        elevations: FloatArray,
        width: Int,
        height: Int,
        cellDistance: Float,
    ): FloatArray {
        val output = FloatArray(elevations.size)
        val divisor = cellDistance * cellDistance
        fun idx(x: Int, y: Int) = y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val center = elevations[idx(x, y)]
                output[y * width + x] = (
                    elevations[idx(x - 1, y)] + elevations[idx(x + 1, y)] +
                        elevations[idx(x, y - 1)] + elevations[idx(x, y + 1)] - 4f * center
                    ) / divisor
            }
        }
        return output
    }

    private fun robustMagnitudeScale(values: FloatArray): Float {
        var magnitudeSum = 0.0
        var maximum = 0f
        for (value in values) {
            val magnitude = abs(value)
            magnitudeSum += magnitude
            if (magnitude > maximum) maximum = magnitude
        }
        val mean = (magnitudeSum / values.size.coerceAtLeast(1)).toFloat()
        return max(max(mean * 4.5f, maximum * 0.08f), 1e-5f)
    }

    private fun rectSum(
        integral: DoubleArray,
        stride: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ): Double = integral[(y1 + 1) * stride + x1 + 1] - integral[y0 * stride + x1 + 1] -
        integral[(y1 + 1) * stride + x0] + integral[y0 * stride + x0]
}
