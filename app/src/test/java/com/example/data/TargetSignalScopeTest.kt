package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TargetSignalScopeTest {
    @Test
    fun markersOnlyBelongToTheirExactTerrainSource() {
        val first = TargetSignal(
            id = 1,
            gridX = 20f,
            gridY = 30f,
            metalType = MetalType.MANUAL_MARKER,
            signalStrength = 0f,
            terrainKey = "lidar:file:///first.laz",
        )
        val second = first.copy(id = 2, terrainKey = "lidar:file:///second.laz")
        val legacyUnscoped = first.copy(id = 3, terrainKey = null)

        assertEquals(
            listOf(first),
            targetsForTerrain(
                listOf(first, second, legacyUnscoped),
                terrainKey = "lidar:file:///first.laz",
            ),
        )
        assertEquals(
            listOf(second),
            targetsForTerrain(
                listOf(first, second, legacyUnscoped),
                terrainKey = "lidar:file:///second.laz",
            ),
        )
    }
}
