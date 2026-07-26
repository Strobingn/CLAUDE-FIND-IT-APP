package com.example.analysis

import com.example.data.ElevationGrid
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TerrainIntelligenceCacheRestoreTest {
    @Test
    fun restoreCachedNeverComputesOnMissAndRestoresAfterAnalysis() = runBlocking {
        val directory = Files.createTempDirectory("find-it-intelligence-cache-test").toFile()
        try {
            val engine = TerrainIntelligenceEngine(TerrainDerivedLayerCache(directory))
            val grid = ElevationGrid(
                width = 16,
                height = 16,
                bareEarth = FloatArray(16 * 16) { index ->
                    val x = index % 16
                    val y = index / 16
                    (x * 0.1f) + (y * 0.05f)
                },
                canopySpikes = FloatArray(16 * 16),
            )

            assertNull(engine.restoreCached(grid, "uncached"))
            assertEquals(0, directory.listFiles()?.size ?: 0)

            val analyzed = engine.analyze(grid, "cached")
            val restored = engine.restoreCached(grid, "cached")

            assertNotNull(restored)
            assertEquals(analyzed.datasetKey, restored?.datasetKey)
            assertEquals(analyzed.layers.width, restored?.layers?.width)
            assertEquals(analyzed.layers.height, restored?.layers?.height)
        } finally {
            directory.deleteRecursively()
        }
    }
}
