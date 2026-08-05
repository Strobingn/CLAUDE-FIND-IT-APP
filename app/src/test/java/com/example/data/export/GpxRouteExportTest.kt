package com.example.data.export

import com.example.data.field.FieldWaypoint
import com.example.data.field.OptimizedFieldRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxRouteExportTest {

    @Test
    fun `route export keeps walking order in rtept sequence`() {
        val route = OptimizedFieldRoute(
            waypoints = listOf(
                FieldWaypoint("a", 41.4401, -73.9901, "Cellar hole"),
                FieldWaypoint("b", 41.4402, -73.9902, "Trash pit & <privy>"),
                FieldWaypoint("c", 41.4403, -73.9903, "Wall junction"),
            ),
            totalDistanceMeters = 246.8,
        )
        val gpx = buildGpxRoute(route)

        assertEquals(3, Regex("<wpt ").findAll(gpx).count())
        assertEquals(3, Regex("<rtept ").findAll(gpx).count())
        assertTrue(gpx.contains("<rte>"))

        val firstStop = gpx.indexOf("Cellar hole")
        val secondStop = gpx.indexOf("Trash pit &amp; &lt;privy&gt;")
        val thirdStop = gpx.indexOf("Wall junction")
        assertTrue(firstStop in 1 until secondStop)
        assertTrue(secondStop < thirdStop)
        // Route name carries the total distance.
        assertTrue(gpx.contains("0.25 km"))
        // Waypoint names are numbered in walking order.
        assertTrue(gpx.contains("<name>1. Cellar hole</name>"))
    }

    @Test
    fun `empty route still emits valid document`() {
        val gpx = buildGpxRoute(OptimizedFieldRoute(emptyList(), 0.0))
        assertTrue(gpx.startsWith("<?xml"))
        assertTrue(gpx.contains("<rte>"))
        assertEquals(0, Regex("<wpt ").findAll(gpx).count())
    }
}
