package com.example.data

import com.example.data.field.SurveyBoundary
import com.example.geospatial.GeoSpatialLibrary

/**
 * Maps a survey boundary polygon (lat/lon) onto normalized raster focus bounds so refine
 * re-rasterization can clip to the permitted search area.
 */
object BoundaryFocusMapper {
    private const val PAD_FRACTION = 0.02

    /**
     * Converts [boundary] vertices into a padded axis-aligned [NormalizedRasterBounds] in
     * 0–1 raster space (top-left origin, matching terrain canvas y-down percents).
     *
     * @return null when the boundary has fewer than 3 vertices or [metadata] is not georeferenced
     */
    fun toNormalizedBounds(
        boundary: SurveyBoundary,
        metadata: GeoSpatialLibrary.GeoSpatialMetadata,
    ): NormalizedRasterBounds? {
        if (boundary.vertices.size < 3) return null
        val geoBounds = metadata.bounds ?: return null
        val latRange = geoBounds.maxLat - geoBounds.minLat
        val lonRange = geoBounds.maxLon - geoBounds.minLon
        if (latRange <= 0.0 || lonRange <= 0.0) return null

        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY

        for (vertex in boundary.vertices) {
            // Match GeoSpatialLibrary.geographicToGrid percent math, but allow out-of-footprint
            // vertices so a boundary that slightly overshoots still produces usable focus bounds.
            val xPct = (vertex.longitude - geoBounds.minLon) / lonRange * 100.0
            val yPct = 100.0 - (vertex.latitude - geoBounds.minLat) / latRange * 100.0
            val nx = xPct / 100.0
            val ny = yPct / 100.0
            if (nx < minX) minX = nx
            if (nx > maxX) maxX = nx
            if (ny < minY) minY = ny
            if (ny > maxY) maxY = ny
        }

        if (!minX.isFinite() || !maxX.isFinite() || !minY.isFinite() || !maxY.isFinite()) {
            return null
        }

        val spanX = (maxX - minX).coerceAtLeast(0.001)
        val spanY = (maxY - minY).coerceAtLeast(0.001)
        val padX = spanX * PAD_FRACTION
        val padY = spanY * PAD_FRACTION

        return NormalizedRasterBounds(
            left = minX - padX,
            top = minY - padY,
            right = maxX + padX,
            bottom = maxY + padY,
        ).sanitized()
    }

    /**
     * Axis-aligned geographic box of the boundary vertices.
     *
     * @return `((minLat, minLon), (maxLat, maxLon))`, or null when there are no vertices
     */
    fun axisAlignedLatLonBox(
        boundary: SurveyBoundary,
    ): Pair<Pair<Double, Double>, Pair<Double, Double>>? {
        if (boundary.vertices.isEmpty()) return null
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        for (vertex in boundary.vertices) {
            if (vertex.latitude < minLat) minLat = vertex.latitude
            if (vertex.latitude > maxLat) maxLat = vertex.latitude
            if (vertex.longitude < minLon) minLon = vertex.longitude
            if (vertex.longitude > maxLon) maxLon = vertex.longitude
        }
        if (!minLat.isFinite() || !maxLat.isFinite() || !minLon.isFinite() || !maxLon.isFinite()) {
            return null
        }
        return (minLat to minLon) to (maxLat to maxLon)
    }
}
