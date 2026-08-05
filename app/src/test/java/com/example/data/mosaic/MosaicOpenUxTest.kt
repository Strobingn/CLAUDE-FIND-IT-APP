package com.example.data.mosaic

import com.example.geospatial.GeoSpatialLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MosaicOpenUxTest {
    private fun tile(name: String) = MosaicProjectTile(
        displayName = name,
        localFileName = name,
        sourceUrl = "https://example.test/$name",
        bounds = GeoSpatialLibrary.GeographicBounds(42.0, 42.1, -74.1, -74.0),
    )

    private fun project(
        state: MosaicProjectState,
        tileCount: Int = 4,
        recoveryMessage: String? = null,
        area: String? = "NYS historic 2 km box",
    ) = MosaicProject(
        id = "m-1",
        displayName = "Ridge mosaic",
        tiles = (1..tileCount).map { tile("tile-$it.laz") },
        createdAtMillis = 1L,
        updatedAtMillis = 2L,
        state = state,
        recoveryMessage = recoveryMessage,
        areaSelectionDescription = area,
    )

    @Test
    fun fullyReadyProjectOffersOpenMosaic() {
        val card = MosaicOpenUx.cardFor(
            project = project(MosaicProjectState.READY, tileCount = 3),
            readyTileCount = 3,
            totalTiles = 3,
        )
        assertEquals("Ridge mosaic", card.title)
        assertEquals("Open mosaic", card.actionLabel)
        assertTrue(card.isPrimaryActionEnabled)
        assertTrue(card.statusLine.contains("All 3"))
        assertTrue(card.detailLines.any { it.contains("Open mosaic") })
    }

    @Test
    fun partialDownloadOffersResume() {
        val card = MosaicOpenUx.cardFor(
            project = project(MosaicProjectState.DOWNLOADING, tileCount = 4),
            readyTileCount = 2,
            totalTiles = 4,
        )
        assertEquals("Resume download", card.actionLabel)
        assertTrue(card.isPrimaryActionEnabled)
        assertTrue(card.statusLine.contains("2 of 4"))
        assertTrue(card.detailLines.any { it.contains("2 of 4 source files are ready") })
    }

    @Test
    fun needsAttentionWithGapsOffersRetry() {
        val card = MosaicOpenUx.cardFor(
            project = project(
                MosaicProjectState.NEEDS_ATTENTION,
                tileCount = 5,
                recoveryMessage = "Two tiles failed checksum",
            ),
            readyTileCount = 3,
            totalTiles = 5,
        )
        assertEquals("Retry missing tiles", card.actionLabel)
        assertTrue(card.isPrimaryActionEnabled)
        assertTrue(card.statusLine.contains("Needs attention"))
        assertTrue(card.detailLines.any { it.contains("Two tiles failed checksum") })
        assertTrue(card.detailLines.any { it.contains("Area: NYS historic 2 km box") })
    }

    @Test
    fun noTilesOnDeviceDisablesNothingWhenRetryIsAvailable() {
        val card = MosaicOpenUx.cardFor(
            project = project(MosaicProjectState.DOWNLOADING, tileCount = 2),
            readyTileCount = 0,
            totalTiles = 2,
        )
        assertEquals("Retry missing tiles", card.actionLabel)
        assertTrue(card.isPrimaryActionEnabled)
        assertTrue(card.detailLines.any { it.contains("None of the source tiles") })
    }

    @Test
    fun emptyProjectDisablesPrimaryAction() {
        val empty = MosaicProject(
            id = "empty",
            displayName = "Empty",
            tiles = emptyList(),
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
            state = MosaicProjectState.READY,
        )
        val card = MosaicOpenUx.cardFor(empty, readyTileCount = 0, totalTiles = 0)
        assertFalse(card.isPrimaryActionEnabled)
        assertTrue(card.statusLine.contains("No tiles"))
    }
}
