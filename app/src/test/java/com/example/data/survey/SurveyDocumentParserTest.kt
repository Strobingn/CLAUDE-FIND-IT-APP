package com.example.data.survey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyDocumentParserTest {
    @Test
    fun parsesGpxWaypointsTracksAndElevation() {
        val layer = SurveyDocumentParser.parse(
            """
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <wpt lat="41.4301" lon="-74.0402"><ele>112.5</ele><name>Cellar</name></wpt>
              <trk><name>Walk</name><trkseg>
                <trkpt lat="41.4301" lon="-74.0402"/>
                <trkpt lat="41.4305" lon="-74.0408"/>
              </trkseg></trk>
            </gpx>
            """.trimIndent(),
            "field.gpx",
        )

        assertEquals(SurveyFormat.GPX, layer.format)
        assertEquals(2, layer.features.size)
        assertEquals(SurveyGeometryType.POINT, layer.features[0].geometryType)
        assertEquals(112.5, layer.features[0].coordinates.single().elevationMeters!!, 0.0001)
        assertEquals(SurveyGeometryType.LINE, layer.features[1].geometryType)
        assertEquals(2, layer.features[1].coordinates.size)
    }

    @Test
    fun parsesKmlPointLineAndPolygonUsingLongitudeLatitudeOrder() {
        val layer = SurveyDocumentParser.parse(
            """
            <kml xmlns="http://www.opengis.net/kml/2.2"><Document>
              <Placemark><name>Point</name><Point><coordinates>-74.04,41.43,0</coordinates></Point></Placemark>
              <Placemark><LineString><coordinates>-74.04,41.43 -74.05,41.44</coordinates></LineString></Placemark>
              <Placemark><Polygon><outerBoundaryIs><LinearRing><coordinates>
                -74.04,41.43 -74.05,41.43 -74.05,41.44 -74.04,41.43
              </coordinates></LinearRing></outerBoundaryIs></Polygon></Placemark>
            </Document></kml>
            """.trimIndent(),
            "survey.kml",
        )

        assertEquals(listOf(
            SurveyGeometryType.POINT,
            SurveyGeometryType.LINE,
            SurveyGeometryType.POLYGON,
        ), layer.features.map { it.geometryType })
        assertEquals(-74.04, layer.features.first().coordinates.first().longitude, 0.0001)
        assertEquals(41.43, layer.features.first().coordinates.first().latitude, 0.0001)
    }

    @Test
    fun rejectsExternalEntityDocuments() {
        val result = runCatching {
            SurveyDocumentParser.parse(
                """<!DOCTYPE gpx [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><gpx>&xxe;</gpx>""",
            )
        }
        assertTrue(result.isFailure)
    }
}
