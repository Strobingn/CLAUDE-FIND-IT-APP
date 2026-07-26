package com.example.analysis

import com.example.data.local.AnalyzedDatasetEntity
import com.example.data.local.SavedTarget
import com.example.data.local.parseTargets
import com.example.geospatial.GeoSpatialLibrary

/** One candidate matched between two datasets, or found in only one of them. */
data class DatasetComparisonMatch(
    val fromFirst: SavedTarget?,
    val fromSecond: SavedTarget?,
    val distanceMeters: Double?,
)

data class DatasetComparisonResult(
    val firstName: String,
    val secondName: String,
    val agreements: List<DatasetComparisonMatch>,
    val uniqueToFirst: List<SavedTarget>,
    val uniqueToSecond: List<SavedTarget>,
    val bothGeoreferenced: Boolean,
)

/**
 * Cross-references two previously-analyzed datasets' saved targets by real-world distance (not
 * grid-relative position, which is meaningless across datasets with different extents or
 * resolutions). Candidates that agree across independently-analyzed datasets covering the same
 * ground are stronger evidence than either alone; this never invents a score, it only reports
 * which candidates coincide.
 */
object DatasetComparison {
    /** Real-world proximity, in meters, for two candidates to be considered "the same location". */
    const val MATCH_DISTANCE_METERS = 15.0

    fun compare(first: AnalyzedDatasetEntity, second: AnalyzedDatasetEntity): DatasetComparisonResult {
        val firstTargets = first.parseTargets()
        val secondTargets = second.parseTargets()
        val bothGeoreferenced = firstTargets.any { it.latitude != null } && secondTargets.any { it.latitude != null }

        val agreements = ArrayList<DatasetComparisonMatch>()
        val usedSecond = BooleanArray(secondTargets.size)

        for (candidate in firstTargets) {
            if (candidate.latitude == null || candidate.longitude == null) continue
            var bestIndex = -1
            var bestDistance = Double.MAX_VALUE
            secondTargets.forEachIndexed { index, other ->
                if (usedSecond[index] || other.latitude == null || other.longitude == null) return@forEachIndexed
                val distance = GeoSpatialLibrary.calculateGeodesicDistance(
                    candidate.latitude, candidate.longitude, other.latitude, other.longitude,
                )
                if (distance <= MATCH_DISTANCE_METERS && distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }
            if (bestIndex >= 0) {
                usedSecond[bestIndex] = true
                agreements += DatasetComparisonMatch(candidate, secondTargets[bestIndex], bestDistance)
            }
        }

        val matchedFirst = agreements.mapNotNull { it.fromFirst }.toSet()
        val uniqueToFirst = firstTargets.filter { it !in matchedFirst }
        val uniqueToSecond = secondTargets.filterIndexed { index, _ -> !usedSecond[index] }

        return DatasetComparisonResult(
            firstName = first.displayName,
            secondName = second.displayName,
            agreements = agreements,
            uniqueToFirst = uniqueToFirst,
            uniqueToSecond = uniqueToSecond,
            bothGeoreferenced = bothGeoreferenced,
        )
    }
}
