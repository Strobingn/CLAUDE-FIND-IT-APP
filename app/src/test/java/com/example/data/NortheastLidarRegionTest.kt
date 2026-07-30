package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NortheastLidarRegionTest {
    @Test
    fun everyOfferedRegionHasAWellFormedBox() {
        NortheastLidarRegion.entries.forEach { region ->
            assertTrue(region.displayName, region.west < region.east)
            assertTrue(region.displayName, region.south < region.north)
            assertTrue(region.displayName, region.west in -180.0..180.0)
            assertTrue(region.displayName, region.east in -180.0..180.0)
            assertTrue(region.displayName, region.south in -90.0..90.0)
            assertTrue(region.displayName, region.north in -90.0..90.0)
        }
    }

    @Test
    fun theSixNortheastStatesAreOffered() {
        assertEquals(
            listOf(
                "New York",
                "Pennsylvania",
                "Massachusetts",
                "Connecticut",
                "New Hampshire",
                "Rhode Island",
            ),
            NortheastLidarRegion.entries.map { it.displayName },
        )
    }

    @Test
    fun landmarksResolveToTheirOwnState() {
        // Boston, Providence, Concord NH, Hartford, Gettysburg, and the Hudson Valley.
        assertTrue(NortheastLidarRegion.MASSACHUSETTS.contains(-71.06, 42.36))
        assertTrue(NortheastLidarRegion.RHODE_ISLAND.contains(-71.41, 41.82))
        assertTrue(NortheastLidarRegion.NEW_HAMPSHIRE.contains(-71.54, 43.21))
        assertTrue(NortheastLidarRegion.CONNECTICUT.contains(-72.68, 41.76))
        assertTrue(NortheastLidarRegion.PENNSYLVANIA.contains(-77.23, 39.83))
        assertTrue(NortheastLidarRegion.NEW_YORK.contains(-74.04, 41.43))
    }

    /** The New-York-only ITS index must not be consulted for coordinates it cannot serve. */
    @Test
    fun pointsOutsideNewYorkAreNotTreatedAsNewYork() {
        assertFalse(NortheastLidarRegion.NEW_YORK.contains(-71.06, 42.36))
        assertFalse(NortheastLidarRegion.NEW_YORK.contains(-77.23, 39.83))
        assertNull(NortheastLidarRegion.containing(-122.33, 47.61))
    }

    @Test
    fun boxIntersectionCatchesPartialOverlapAndRejectsDisjointBoxes() {
        // A box straddling the Massachusetts/New York line overlaps both.
        assertTrue(NortheastLidarRegion.NEW_YORK.intersects(-73.6, 42.0, -73.4, 42.2))
        assertTrue(NortheastLidarRegion.MASSACHUSETTS.intersects(-73.6, 42.0, -73.4, 42.2))
        // Seattle overlaps neither.
        assertFalse(NortheastLidarRegion.NEW_YORK.intersects(-122.4, 47.5, -122.2, 47.7))
    }
}
