package com.example.data.export

import com.example.data.DetectionSource
import com.example.data.MetalType
import com.example.data.TargetSignal
import com.example.data.field.BoundaryVertex
import com.example.data.field.BreadcrumbPoint
import com.example.data.field.BreadcrumbTrack
import com.example.data.field.ExcavationLogEntry
import com.example.data.field.SurveyBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePackageExporterTest {
    private val target = TargetSignal(
        id = 42,
        gridX = 50f,
        gridY = 40f,
        metalType = MetalType.MANUAL_MARKER,
        signalStrength = 10f,
        latitude = 42.5,
        longitude = -74.1,
        source = DetectionSource.MANUAL,
        terrainKey = "lidar:test",
        notes = "cellar corner",
    )

    private val dig = ExcavationLogEntry(
        id = "dig-1",
        targetId = 42,
        terrainKey = "lidar:test",
        startedAtMillis = 1_000L,
        completedAtMillis = 2_000L,
        depthCentimeters = 18,
        soilNotes = "sandy loam",
        findsDescription = "button",
        findsCount = 1,
        photoUris = emptyList(),
        voiceNoteUris = emptyList(),
        createdAtMillis = 1_000L,
        updatedAtMillis = 2_000L,
    )

    private val boundary = SurveyBoundary(
        id = "b-1",
        terrainKey = "lidar:test",
        displayName = "Permit polygon",
        vertices = listOf(
            BoundaryVertex(42.50, -74.12),
            BoundaryVertex(42.51, -74.12),
            BoundaryVertex(42.51, -74.11),
            BoundaryVertex(42.50, -74.11),
        ),
        createdAtMillis = 500L,
    )

    private val trail = BreadcrumbTrack(
        id = "t-1",
        terrainKey = "lidar:test",
        displayName = "Morning sweep",
        points = listOf(
            BreadcrumbPoint(42.501, -74.115, 4f, 1_100L),
            BreadcrumbPoint(42.502, -74.114, 5f, 1_200L),
            BreadcrumbPoint(42.503, -74.113, 5f, 1_300L),
        ),
        isRecording = false,
        createdAtMillis = 1_100L,
        updatedAtMillis = 1_300L,
    )

    private fun input(
        png: ByteArray? = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
        pdf: ByteArray? = "%PDF-1.4 ethics test".toByteArray(),
        las: ByteArray? = "LASF".toByteArray(),
    ) = SitePackageInput(
        projectName = "North woods",
        terrainKey = "lidar:test",
        summary = "Field handoff package",
        crsBanner = "EPSG:4326 | WGS 84",
        scorecardLines = listOf("Ground coverage 92%", "Mean samples/cell 4.1"),
        targets = listOf(target),
        digs = listOf(dig),
        boundaries = listOf(boundary),
        trails = listOf(trail),
        terrainPng = png,
        reportPdf = pdf,
        clippedLas = las,
        generatedAtMillis = 1_700_000_000_000L,
    )

    @Test
    fun buildProducesArchiveWithManifestAndCoreFiles() {
        val archive = SitePackageExporter.build(input())
        val manifest = ProjectArchiveWriter.readManifest(archive)
        assertNotNull(manifest)
        assertEquals("North woods", manifest!!.projectName)
        assertEquals(1_700_000_000_000L, manifest.createdAtMillis)

        val paths = manifest.filePaths.toSet()
        assertTrue(paths.contains("site-summary.txt"))
        assertTrue(paths.contains("targets.csv"))
        assertTrue(paths.contains("targets.gpx"))
        assertTrue(paths.contains("targets.geojson"))
        assertTrue(paths.contains("digs.csv"))
        assertTrue(paths.contains("boundaries.geojson"))
        assertTrue(paths.contains("trails.geojson"))
        assertTrue(paths.contains("terrain-annotated.png"))
        assertTrue(paths.contains("field-report.pdf"))
        assertTrue(paths.contains("clipped-aoi.las"))

        val entries = readZipArchive(archive)
        val summary = entries["site-summary.txt"]!!.toString(Charsets.UTF_8)
        assertTrue(summary.contains("North woods"))
        assertTrue(summary.contains("lidar:test"))
        assertTrue(summary.contains("Ground coverage 92%"))
        assertTrue(summary.contains("Targets: 1"))
        assertTrue(summary.contains("Digs: 1"))
        assertTrue(summary.contains(DEFAULT_ETHICS_FOOTER.take(20)))

        val digsCsv = entries["digs.csv"]!!.toString(Charsets.UTF_8)
        assertTrue(digsCsv.contains("dig-1"))
        assertTrue(digsCsv.contains("42"))
        assertTrue(digsCsv.contains("true"))

        val boundaries = entries["boundaries.geojson"]!!.toString(Charsets.UTF_8)
        assertTrue(boundaries.contains("FeatureCollection"))
        assertTrue(boundaries.contains("Polygon"))
        assertTrue(boundaries.contains("Permit polygon"))

        val trails = entries["trails.geojson"]!!.toString(Charsets.UTF_8)
        assertTrue(trails.contains("LineString"))
        assertTrue(trails.contains("Morning sweep"))

        assertTrue(entries["targets.csv"]!!.toString(Charsets.UTF_8).contains("42"))
        assertTrue(entries["clipped-aoi.las"]!!.contentEquals("LASF".toByteArray()))
    }

    @Test
    fun optionalBinariesOmittedWhenNullOrEmpty() {
        val archive = SitePackageExporter.build(
            input(png = null, pdf = byteArrayOf(), las = null),
        )
        val manifest = ProjectArchiveWriter.readManifest(archive)!!
        val paths = manifest.filePaths.toSet()
        assertTrue(!paths.contains("terrain-annotated.png"))
        assertTrue(!paths.contains("field-report.pdf"))
        assertTrue(!paths.contains("clipped-aoi.las"))
        assertTrue(paths.contains("site-summary.txt"))
        assertTrue(paths.contains("targets.csv"))
    }

    @Test
    fun digsCsvEscapesNotesAndReportsIncomplete() {
        val openDig = dig.copy(
            id = "dig-open",
            completedAtMillis = null,
            soilNotes = "notes, with \"quotes\"",
            findsDescription = "",
            depthCentimeters = null,
        )
        val csv = SitePackageExporter.buildDigsCsv(listOf(openDig))
        assertTrue(csv.startsWith("id,targetId,notes,depth,complete,"))
        assertTrue(csv.contains("false"))
        assertTrue(csv.contains("\"notes, with \"\"quotes\"\"\""))
    }
}
