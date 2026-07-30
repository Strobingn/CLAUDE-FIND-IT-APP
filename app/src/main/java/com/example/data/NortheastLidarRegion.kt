package com.example.data

/**
 * Search areas the public LiDAR picker offers as a starting point.
 *
 * Coverage itself is not limited to these states — the USGS 3DEP query behind the picker is
 * national. These are seed extents so a search can begin from a named place instead of requiring
 * coordinates, and so the New-York-only ITS index is consulted only where it actually has data.
 *
 * Bounds are generous WGS84 state extents rounded outward; they are meant to be narrowed by the
 * user before downloading, not used as precise borders.
 */
enum class NortheastLidarRegion(
    val displayName: String,
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
) {
    NEW_YORK("New York", -79.77, 40.47, -71.84, 45.03),
    PENNSYLVANIA("Pennsylvania", -80.53, 39.70, -74.67, 42.28),
    MASSACHUSETTS("Massachusetts", -73.52, 41.18, -69.84, 42.90),
    CONNECTICUT("Connecticut", -73.75, 40.96, -71.76, 42.06),
    NEW_HAMPSHIRE("New Hampshire", -72.57, 42.68, -70.59, 45.32),
    RHODE_ISLAND("Rhode Island", -71.90, 41.13, -71.10, 42.03),
    ;

    fun contains(longitude: Double, latitude: Double): Boolean =
        longitude in west..east && latitude in south..north

    /** True when any part of the query box overlaps this region. */
    fun intersects(west: Double, south: Double, east: Double, north: Double): Boolean =
        west <= this.east && east >= this.west && south <= this.north && north >= this.south

    companion object {
        fun containing(longitude: Double, latitude: Double): NortheastLidarRegion? =
            entries.firstOrNull { it.contains(longitude, latitude) }
    }
}
