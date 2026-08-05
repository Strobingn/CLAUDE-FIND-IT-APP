package com.example.analysis

import kotlin.math.sqrt

/**
 * Groups ranked targets that lie within [epsilonMeters] of each other into "hunt zones":
 * one cellar hole, its trash pit, and a nearby foundation are usually ONE site, and the
 * field visit should be planned around the zone, not around three separate pins.
 *
 * Zones are scored by their best target so the review list stays ordered by site value.
 */
data class HuntZone(
    val id: Int,
    val centerXPercent: Float,
    val centerYPercent: Float,
    val targetCount: Int,
    val bestScore: Float,
    val dominantType: MetalDetectingTargetType,
    /** Largest pairwise target spacing inside the zone - roughly how far you walk it. */
    val spanMeters: Float,
    /** Member targets, best score first. */
    val targets: List<MetalDetectingTarget> = emptyList(),
)

object HuntZoneClusterer {

    const val DEFAULT_EPSILON_METERS = 40f

    fun cluster(
        targets: List<MetalDetectingTarget>,
        layers: TerrainDerivedLayers,
        epsilonMeters: Float = DEFAULT_EPSILON_METERS,
    ): List<HuntZone> {
        if (targets.isEmpty()) return emptyList()
        val gridWidthMeters = layers.width * layers.cellSizeMeters
        val gridHeightMeters = layers.height * layers.cellSizeMeters
        if (gridWidthMeters <= 0f || gridHeightMeters <= 0f || epsilonMeters <= 0f) return emptyList()

        val xs = FloatArray(targets.size) { targets[it].xPercent / 100f * gridWidthMeters }
        val ys = FloatArray(targets.size) { targets[it].yPercent / 100f * gridHeightMeters }

        val parent = IntArray(targets.size) { it }
        fun find(i: Int): Int {
            var root = i
            while (parent[root] != root) root = parent[root]
            var cur = i
            while (parent[cur] != root) {
                val next = parent[cur]
                parent[cur] = root
                cur = next
            }
            return root
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        val epsilonSq = epsilonMeters * epsilonMeters
        for (i in targets.indices) {
            for (j in i + 1 until targets.size) {
                val dx = xs[i] - xs[j]
                val dy = ys[i] - ys[j]
                if (dx * dx + dy * dy <= epsilonSq) union(i, j)
            }
        }

        val groups = targets.indices.groupBy { find(it) }
        return groups.values.map { members ->
            val centroidX = members.map { targets[it].xPercent }.average().toFloat()
            val centroidY = members.map { targets[it].yPercent }.average().toFloat()
            val best = members.maxBy { targets[it].score }
            var maxPairSq = 0f
            for (a in members) {
                for (b in members) {
                    if (a >= b) continue
                    val dx = xs[a] - xs[b]
                    val dy = ys[a] - ys[b]
                    val dSq = dx * dx + dy * dy
                    if (dSq > maxPairSq) maxPairSq = dSq
                }
            }
            val dominant = members
                .groupBy { targets[it].type }
                .mapValues { (_, idxs) -> idxs.sumOf { targets[it].score.toDouble() } }
                .maxWith(compareBy<Map.Entry<MetalDetectingTargetType, Double>> { it.value }.thenBy { it.key.ordinal })
                .key
            HuntZone(
                id = 0, // assigned after sorting
                centerXPercent = centroidX,
                centerYPercent = centroidY,
                targetCount = members.size,
                bestScore = targets[best].score,
                dominantType = dominant,
                spanMeters = sqrt(maxPairSq),
                targets = members.sortedByDescending { targets[it].score }.map { targets[it] },
            )
        }
            .sortedByDescending { it.bestScore }
            .mapIndexed { index, zone -> zone.copy(id = index + 1) }
    }
}
