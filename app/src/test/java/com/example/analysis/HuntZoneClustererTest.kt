package com.example.analysis

import java.util.EnumMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuntZoneClustererTest {

    @Test
    fun `separated clusters form separate zones ordered by best score`() {
        val targets = listOf(
            target(MetalDetectingTargetType.CELLAR_HOLE, 80f, 80f, 0.9f),
            target(MetalDetectingTargetType.CELLAR_HOLE, 10f, 10f, 0.8f),
            target(MetalDetectingTargetType.CELLAR_HOLE, 15f, 12f, 0.7f),
            target(MetalDetectingTargetType.FOUNDATION, 12f, 18f, 0.6f),
            target(MetalDetectingTargetType.TRASH_PIT, 85f, 82f, 0.5f),
        )
        val zones = HuntZoneClusterer.cluster(targets, layers())
        assertEquals(2, zones.size)

        val first = zones[0] // cluster B holds the 0.9 target, so it ranks first
        assertEquals(1, first.id)
        assertEquals(2, first.targetCount)
        assertEquals(MetalDetectingTargetType.CELLAR_HOLE, first.dominantType)
        assertEquals(82.5f, first.centerXPercent, 0.01f)
        assertEquals(81f, first.centerYPercent, 0.01f)

        val second = zones[1]
        assertEquals(2, second.id)
        assertEquals(3, second.targetCount)
        assertEquals(MetalDetectingTargetType.CELLAR_HOLE, second.dominantType) // 0.8 + 0.7 beats 0.6
        assertEquals(12.333f, second.centerXPercent, 0.01f)
        assertEquals(13.333f, second.centerYPercent, 0.01f)
        assertTrue(second.spanMeters > 8f) // (10,10)-(12,18) diagonal
    }

    @Test
    fun `chain of nearby targets merges into one zone`() {
        val targets = listOf(
            target(MetalDetectingTargetType.ROAD_TRAIL, 10f, 10f, 0.6f),
            target(MetalDetectingTargetType.ROAD_TRAIL, 40f, 10f, 0.5f),
            target(MetalDetectingTargetType.ROAD_TRAIL, 70f, 10f, 0.4f), // 60 m from first, 30 m from second
        )
        val zones = HuntZoneClusterer.cluster(targets, layers(), epsilonMeters = 40f)
        assertEquals(1, zones.size)
        assertEquals(3, zones[0].targetCount)
        assertEquals(60f, zones[0].spanMeters, 0.01f)
    }

    @Test
    fun `empty and singleton inputs`() {
        assertTrue(HuntZoneClusterer.cluster(emptyList(), layers()).isEmpty())
        val zones = HuntZoneClusterer.cluster(
            listOf(target(MetalDetectingTargetType.OLD_HOMESITE, 50f, 50f, 0.7f)),
            layers(),
        )
        assertEquals(1, zones.size)
        assertEquals(0f, zones[0].spanMeters, 0.001f)
        assertEquals(MetalDetectingTargetType.OLD_HOMESITE, zones[0].dominantType)
    }

    private fun target(type: MetalDetectingTargetType, x: Float, y: Float, score: Float) =
        MetalDetectingTarget(
            type = type,
            xPercent = x,
            yPercent = y,
            score = score,
            radiusMeters = 5f,
            evidence = emptyList(),
        )

    /** 100x100 m grid at 1 m cells so 1 percent == 1 meter in both axes. */
    private fun layers(): TerrainDerivedLayers {
        val size = 100 * 100
        val values = EnumMap<TerrainDerivedLayer, FloatArray>(TerrainDerivedLayer::class.java).apply {
            put(TerrainDerivedLayer.SLOPE, FloatArray(size))
        }
        return TerrainDerivedLayers(100, 100, 1f, values)
    }
}
