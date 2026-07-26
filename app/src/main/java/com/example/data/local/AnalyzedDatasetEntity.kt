package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.analysis.MetalDetectingTarget
import com.example.analysis.MetalDetectingTargetType
import com.example.geospatial.GeoSpatialLibrary
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted snapshot of one dataset's detected targets, keyed by the same dataset signature used
 * by [com.example.analysis.TerrainDerivedLayerCache]. This is what makes multi-dataset candidate
 * comparison possible: without saving past results somewhere, there is nothing to compare a new
 * analysis against once the app moves on to a different import.
 */
@Entity(tableName = "analyzed_datasets")
data class AnalyzedDatasetEntity(
    @PrimaryKey val datasetKey: String,
    val displayName: String,
    val analyzedAtMillis: Long,
    val width: Int,
    val height: Int,
    val cellSizeMeters: Float,
    val siteName: String,
    val crs: String,
    val boundsJson: String?,
    val targetsJson: String,
)

/** One target as stored in a snapshot, including its real-world coordinate when known. */
data class SavedTarget(
    val type: MetalDetectingTargetType,
    val xPercent: Float,
    val yPercent: Float,
    val latitude: Double?,
    val longitude: Double?,
    val score: Float,
    val radiusMeters: Float,
    val evidence: List<String>,
)

fun buildAnalyzedDatasetEntity(
    datasetKey: String,
    displayName: String,
    metadata: GeoSpatialLibrary.GeoSpatialMetadata,
    targets: List<MetalDetectingTarget>,
): AnalyzedDatasetEntity {
    val targetsArray = JSONArray()
    targets.forEach { target ->
        val coordinate = GeoSpatialLibrary.gridToGeographic(target.xPercent, target.yPercent, metadata)
        val entry = JSONObject()
            .put("type", target.type.name)
            .put("xPercent", target.xPercent.toDouble())
            .put("yPercent", target.yPercent.toDouble())
            .put("score", target.score.toDouble())
            .put("radiusMeters", target.radiusMeters.toDouble())
            .put("evidence", JSONArray(target.evidence))
        if (coordinate != null) {
            entry.put("latitude", coordinate.first)
            entry.put("longitude", coordinate.second)
        }
        targetsArray.put(entry)
    }
    val boundsJson = metadata.bounds?.let { bounds ->
        JSONObject()
            .put("minLat", bounds.minLat)
            .put("maxLat", bounds.maxLat)
            .put("minLon", bounds.minLon)
            .put("maxLon", bounds.maxLon)
            .toString()
    }
    return AnalyzedDatasetEntity(
        datasetKey = datasetKey,
        displayName = displayName,
        analyzedAtMillis = System.currentTimeMillis(),
        width = metadata.columns,
        height = metadata.rows,
        cellSizeMeters = metadata.resolutionMeters.toFloat(),
        siteName = metadata.siteName,
        crs = metadata.crs,
        boundsJson = boundsJson,
        targetsJson = targetsArray.toString(),
    )
}

fun AnalyzedDatasetEntity.parseTargets(): List<SavedTarget> {
    val array = runCatching { JSONArray(targetsJson) }.getOrNull() ?: return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val type = runCatching { MetalDetectingTargetType.valueOf(entry.optString("type")) }.getOrNull() ?: continue
            val evidence = entry.optJSONArray("evidence")?.let { evidenceArray ->
                (0 until evidenceArray.length()).mapNotNull { evidenceArray.optString(it, null) }
            }.orEmpty()
            add(
                SavedTarget(
                    type = type,
                    xPercent = entry.optDouble("xPercent", 50.0).toFloat(),
                    yPercent = entry.optDouble("yPercent", 50.0).toFloat(),
                    latitude = if (entry.has("latitude")) entry.optDouble("latitude") else null,
                    longitude = if (entry.has("longitude")) entry.optDouble("longitude") else null,
                    score = entry.optDouble("score", 0.0).toFloat(),
                    radiusMeters = entry.optDouble("radiusMeters", 5.0).toFloat(),
                    evidence = evidence,
                ),
            )
        }
    }
}
