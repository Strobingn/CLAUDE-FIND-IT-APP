package com.example.data.field

import com.example.geospatial.GeoSpatialLibrary
import kotlin.math.min

/** How the current GPS fix relates to configured survey boundaries. */
enum class BoundaryProximityLevel {
    INSIDE,
    NEAR_EDGE,
    OUTSIDE,
    UNKNOWN,
}

data class BoundaryProximityAlert(
    val level: BoundaryProximityLevel,
    val boundaryId: String?,
    val boundaryName: String?,
    /** Distance to nearest edge (meters); 0 when deep inside; null when unknown. */
    val distanceMeters: Double?,
    val message: String,
)

/**
 * GPS vs survey-boundary proximity for field alerts.
 *
 * Prefer containing polygons; when inside, NEAR_EDGE if the nearest edge is closer than
 * [nearEdgeMeters]. When outside all polygons, the nearest boundary edge decides NEAR_EDGE
 * vs OUTSIDE. Edge distance is haversine to vertices plus samples along each edge segment.
 */
object BoundaryProximity {
    private const val EDGE_SAMPLES = 8

    fun evaluate(
        latitude: Double,
        longitude: Double,
        boundaries: List<SurveyBoundary>,
        nearEdgeMeters: Double = 25.0,
    ): BoundaryProximityAlert {
        val usable = boundaries.filter { it.vertices.size >= 3 }
        if (usable.isEmpty()) {
            return BoundaryProximityAlert(
                level = BoundaryProximityLevel.UNKNOWN,
                boundaryId = null,
                boundaryName = null,
                distanceMeters = null,
                message = "No survey boundary defined.",
            )
        }

        val nearThreshold = nearEdgeMeters.coerceAtLeast(0.0)
        val containing = usable.filter { it.contains(latitude, longitude) }

        if (containing.isNotEmpty()) {
            // Among containing polygons, prefer the tightest (nearest edge). That surfaces
            // NEAR_EDGE against the most relevant plot when boundaries nest or overlap.
            val ranked = containing.map { boundary ->
                boundary to minDistanceToEdges(latitude, longitude, boundary)
            }
            val (boundary, edgeDist) = ranked.minBy { it.second }
            return if (edgeDist < nearThreshold) {
                BoundaryProximityAlert(
                    level = BoundaryProximityLevel.NEAR_EDGE,
                    boundaryId = boundary.id,
                    boundaryName = boundary.displayName,
                    distanceMeters = edgeDist,
                    message = "Near the edge of ${boundary.displayName} " +
                        "(${formatMeters(edgeDist)} m to boundary).",
                )
            } else {
                BoundaryProximityAlert(
                    level = BoundaryProximityLevel.INSIDE,
                    boundaryId = boundary.id,
                    boundaryName = boundary.displayName,
                    distanceMeters = 0.0,
                    message = "Inside ${boundary.displayName}.",
                )
            }
        }

        val nearest = usable
            .map { boundary -> boundary to minDistanceToEdges(latitude, longitude, boundary) }
            .minBy { it.second }
        val (boundary, edgeDist) = nearest
        return if (edgeDist <= nearThreshold) {
            BoundaryProximityAlert(
                level = BoundaryProximityLevel.NEAR_EDGE,
                boundaryId = boundary.id,
                boundaryName = boundary.displayName,
                distanceMeters = edgeDist,
                message = "Near ${boundary.displayName} " +
                    "(${formatMeters(edgeDist)} m outside edge).",
            )
        } else {
            BoundaryProximityAlert(
                level = BoundaryProximityLevel.OUTSIDE,
                boundaryId = boundary.id,
                boundaryName = boundary.displayName,
                distanceMeters = edgeDist,
                message = "Outside ${boundary.displayName} " +
                    "(${formatMeters(edgeDist)} m from nearest edge).",
            )
        }
    }

    /**
     * Minimum haversine distance from the fix to the closed ring: endpoints plus uniform
     * samples along each edge. Vertex-only would under-estimate mid-edge distance; sampling
     * is adequate for field proximity without full geodesic cross-track math.
     */
    internal fun minDistanceToEdges(
        latitude: Double,
        longitude: Double,
        boundary: SurveyBoundary,
    ): Double {
        val vertices = boundary.vertices
        if (vertices.isEmpty()) return Double.POSITIVE_INFINITY
        if (vertices.size == 1) {
            return GeoSpatialLibrary.calculateGeodesicDistance(
                latitude,
                longitude,
                vertices[0].latitude,
                vertices[0].longitude,
            )
        }

        var minDist = Double.POSITIVE_INFINITY
        for (i in vertices.indices) {
            val a = vertices[i]
            val b = vertices[(i + 1) % vertices.size]
            minDist = min(
                minDist,
                GeoSpatialLibrary.calculateGeodesicDistance(
                    latitude,
                    longitude,
                    a.latitude,
                    a.longitude,
                ),
            )
            for (sample in 1 until EDGE_SAMPLES) {
                val t = sample.toDouble() / EDGE_SAMPLES
                val sLat = a.latitude + t * (b.latitude - a.latitude)
                val sLon = a.longitude + t * (b.longitude - a.longitude)
                minDist = min(
                    minDist,
                    GeoSpatialLibrary.calculateGeodesicDistance(
                        latitude,
                        longitude,
                        sLat,
                        sLon,
                    ),
                )
            }
        }
        return minDist
    }

    private fun formatMeters(meters: Double): String =
        if (meters >= 100.0) meters.toInt().toString() else String.format("%.0f", meters)
}
