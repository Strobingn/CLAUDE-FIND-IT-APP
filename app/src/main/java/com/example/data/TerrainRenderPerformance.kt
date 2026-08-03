package com.example.data

import kotlin.math.max
import kotlin.math.min

/** Debounce before hillshade work. Heavy analysis modes recompute local stats/curvature. */
internal fun hillshadeDebounceMs(visualizationMode: Int, immediate: Boolean): Long = when {
    immediate -> 0L
    visualizationMode in HEAVY_HILLSHADE_MODES -> 180L
    else -> 80L
}

/** Zoom-aware max hillshade side so zoomed-out views do not shade every refined cell. */
internal fun previewMaxSideForZoom(zoom: Float, sourceMaxSide: Int): Int {
    val source = sourceMaxSide.coerceAtLeast(1)
    if (source <= 320) return source
    val safeZoom = zoom.coerceAtLeast(1f)
    return when {
        safeZoom < 1.5f -> min(source, 320)
        safeZoom < 2.5f -> min(source, 512)
        else -> source
    }
}

/** Downsamples [source] for display-only hillshade when the preview side is smaller. */
internal fun gridForHillshadePreview(source: ElevationGrid, maxSide: Int): ElevationGrid {
    val sourceMax = max(source.width, source.height)
    if (sourceMax <= maxSide) return source
    return TerrainLodPyramid.build(
        source = source,
        maxFinestDimension = maxSide.coerceAtLeast(64),
        minDimension = 32,
        maxLevels = 1,
    ).finest.grid
}

private val HEAVY_HILLSHADE_MODES = setOf(3, 4, 5)
