package com.example.data

import com.example.geospatial.GeoSpatialLibrary
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

/** A decoded source tile positioned by its authoritative geographic index bounds. */
data class MosaicTerrainTile(
    val displayName: String,
    val terrain: DemGenerator.TerrainLoadResult,
    val bounds: GeoSpatialLibrary.GeographicBounds,
)

/**
 * Produces one geographic bare-earth raster from adjacent decoded LiDAR tiles. It samples only
 * actual source cells and leaves gaps transparent/no-data; no values are fabricated between
 * tiles. Output size is bounded so a wide area remains usable on Android.
 */
object MosaicTerrainBuilder {
    private const val MAX_LONG_SIDE = 1_536

    fun build(projectName: String, tiles: List<MosaicTerrainTile>): DemGenerator.TerrainLoadResult {
        require(tiles.isNotEmpty()) { "Select at least one decoded tile" }
        val bounds = GeoSpatialLibrary.GeographicBounds(
            minLat = tiles.minOf { it.bounds.minLat },
            maxLat = tiles.maxOf { it.bounds.maxLat },
            minLon = tiles.minOf { it.bounds.minLon },
            maxLon = tiles.maxOf { it.bounds.maxLon },
        )
        require(bounds.maxLat > bounds.minLat && bounds.maxLon > bounds.minLon) {
            "Selected tiles do not have usable geographic bounds"
        }
        val widthMeters = longitudeMeters(bounds.minLon, bounds.maxLon, (bounds.minLat + bounds.maxLat) / 2.0)
        val heightMeters = latitudeMeters(bounds.minLat, bounds.maxLat)
        val desiredCellMeters = tiles.minOf { it.terrain.grid.cellSizeMeters.toDouble() }.coerceAtLeast(0.05)
        var width = max(2, (widthMeters / desiredCellMeters).roundToInt())
        var height = max(2, (heightMeters / desiredCellMeters).roundToInt())
        val longSide = max(width, height)
        if (longSide > MAX_LONG_SIDE) {
            val scale = MAX_LONG_SIDE.toDouble() / longSide
            width = max(2, (width * scale).roundToInt())
            height = max(2, (height * scale).roundToInt())
        }
        val bare = FloatArray(width * height)
        val canopy = FloatArray(width * height)
        val valid = BooleanArray(width * height)
        for (row in 0 until height) {
            val latitude = bounds.maxLat - (row.toDouble() / (height - 1)) * (bounds.maxLat - bounds.minLat)
            for (column in 0 until width) {
                val longitude = bounds.minLon + (column.toDouble() / (width - 1)) * (bounds.maxLon - bounds.minLon)
                val tile = tiles.firstOrNull { tile ->
                    latitude in tile.bounds.minLat..tile.bounds.maxLat &&
                        longitude in tile.bounds.minLon..tile.bounds.maxLon
                } ?: continue
                val grid = tile.terrain.grid
                val x = ((longitude - tile.bounds.minLon) / (tile.bounds.maxLon - tile.bounds.minLon))
                val y = ((tile.bounds.maxLat - latitude) / (tile.bounds.maxLat - tile.bounds.minLat))
                val sourceColumn = (x * (grid.width - 1)).roundToInt().coerceIn(0, grid.width - 1)
                val sourceRow = (y * (grid.height - 1)).roundToInt().coerceIn(0, grid.height - 1)
                val sourceIndex = sourceRow * grid.width + sourceColumn
                if (!grid.validData[sourceIndex]) continue
                val index = row * width + column
                bare[index] = grid.bareEarth[sourceIndex]
                canopy[index] = grid.canopySpikes[sourceIndex]
                valid[index] = true
            }
        }
        val resolution = max(widthMeters / (width - 1), heightMeters / (height - 1)).toFloat()
        val metadata = GeoSpatialLibrary.GeoSpatialMetadata(
            siteName = projectName,
            bounds = bounds,
            crs = "EPSG:4326 (WGS 84 geographic tile mosaic)",
            datum = "WGS 84",
            resolutionMeters = resolution.toDouble(),
            columns = width,
            rows = height,
        )
        return DemGenerator.TerrainLoadResult(
            grid = ElevationGrid(width, height, bare, canopy, resolution, valid),
            summary = "${tiles.size}-tile LiDAR mosaic · ${valid.count { it }} valid cells · source tiles: " +
                tiles.joinToString { it.displayName }.take(240),
            isBareEarth = tiles.all { it.terrain.isBareEarth },
            geoMetadata = metadata,
        )
    }

    private fun latitudeMeters(minLatitude: Double, maxLatitude: Double): Double =
        GeoSpatialLibrary.calculateGeodesicDistance(minLatitude, 0.0, maxLatitude, 0.0)

    private fun longitudeMeters(minLongitude: Double, maxLongitude: Double, latitude: Double): Double =
        GeoSpatialLibrary.calculateGeodesicDistance(latitude, minLongitude, latitude, maxLongitude)
}
