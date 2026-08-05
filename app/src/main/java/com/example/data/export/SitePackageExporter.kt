package com.example.data.export

import com.example.data.TargetSignal
import com.example.data.field.BreadcrumbTrack
import com.example.data.field.ExcavationLogEntry
import com.example.data.field.SurveyBoundary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inputs for a complete field handoff zip: summary, targets, digs, boundaries, trails,
 * optional annotated terrain PNG / field PDF / clipped LAS surface sample.
 */
data class SitePackageInput(
    val projectName: String,
    val terrainKey: String,
    val summary: String,
    val crsBanner: String,
    val scorecardLines: List<String>,
    val targets: List<TargetSignal>,
    val digs: List<ExcavationLogEntry>,
    val boundaries: List<SurveyBoundary>,
    val trails: List<BreadcrumbTrack>,
    val terrainPng: ByteArray?,
    val reportPdf: ByteArray?,
    val clippedLas: ByteArray? = null,
    val generatedAtMillis: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SitePackageInput) return false
        return projectName == other.projectName &&
            terrainKey == other.terrainKey &&
            summary == other.summary &&
            crsBanner == other.crsBanner &&
            scorecardLines == other.scorecardLines &&
            targets == other.targets &&
            digs == other.digs &&
            boundaries == other.boundaries &&
            trails == other.trails &&
            terrainPng.contentEquals(other.terrainPng) &&
            reportPdf.contentEquals(other.reportPdf) &&
            clippedLas.contentEquals(other.clippedLas) &&
            generatedAtMillis == other.generatedAtMillis
    }

    override fun hashCode(): Int {
        var result = projectName.hashCode()
        result = 31 * result + terrainKey.hashCode()
        result = 31 * result + summary.hashCode()
        result = 31 * result + crsBanner.hashCode()
        result = 31 * result + scorecardLines.hashCode()
        result = 31 * result + targets.hashCode()
        result = 31 * result + digs.hashCode()
        result = 31 * result + boundaries.hashCode()
        result = 31 * result + trails.hashCode()
        result = 31 * result + (terrainPng?.contentHashCode() ?: 0)
        result = 31 * result + (reportPdf?.contentHashCode() ?: 0)
        result = 31 * result + (clippedLas?.contentHashCode() ?: 0)
        result = 31 * result + generatedAtMillis.hashCode()
        return result
    }
}

/**
 * Builds a self-describing site package archive via [ProjectArchiveWriter]:
 * manifest + summary + targets (CSV/GPX/GeoJSON) + digs + boundaries + trails + optional binaries.
 */
object SitePackageExporter {
    fun build(input: SitePackageInput): ByteArray {
        val files = mutableListOf(
            ProjectArchiveFile(
                "site-summary.txt",
                buildSiteSummary(input).toByteArray(Charsets.UTF_8),
            ),
            ProjectArchiveFile("targets.csv", buildCsv(input.targets).toByteArray(Charsets.UTF_8)),
            ProjectArchiveFile("targets.gpx", buildGpx(input.targets).toByteArray(Charsets.UTF_8)),
            ProjectArchiveFile(
                "targets.geojson",
                buildGeoJson(input.targets).toByteArray(Charsets.UTF_8),
            ),
            ProjectArchiveFile("digs.csv", buildDigsCsv(input.digs).toByteArray(Charsets.UTF_8)),
            ProjectArchiveFile(
                "boundaries.geojson",
                buildBoundariesGeoJson(input.boundaries).toByteArray(Charsets.UTF_8),
            ),
            ProjectArchiveFile(
                "trails.geojson",
                buildTrailsGeoJson(input.trails).toByteArray(Charsets.UTF_8),
            ),
        )
        input.terrainPng?.takeIf { it.isNotEmpty() }?.let {
            files.add(ProjectArchiveFile("terrain-annotated.png", it))
        }
        input.reportPdf?.takeIf { it.isNotEmpty() }?.let {
            files.add(ProjectArchiveFile("field-report.pdf", it))
        }
        input.clippedLas?.takeIf { it.isNotEmpty() }?.let {
            files.add(ProjectArchiveFile("clipped-aoi.las", it))
        }
        return ProjectArchiveWriter.write(
            projectName = input.projectName,
            files = files,
            createdAtMillis = input.generatedAtMillis,
        )
    }

    internal fun buildSiteSummary(input: SitePackageInput): String = buildString {
        appendLine("Find It site package")
        appendLine("Project: ${input.projectName}")
        appendLine("Terrain key: ${input.terrainKey}")
        appendLine("Generated: ${formatTimestamp(input.generatedAtMillis)}")
        appendLine("CRS: ${input.crsBanner}")
        appendLine()
        appendLine("Summary")
        appendLine(input.summary.ifBlank { "(none)" })
        appendLine()
        appendLine("Counts")
        appendLine("Targets: ${input.targets.size}")
        appendLine("Digs: ${input.digs.size}")
        appendLine("Boundaries: ${input.boundaries.size}")
        appendLine("Trails: ${input.trails.size}")
        appendLine()
        if (input.scorecardLines.isNotEmpty()) {
            appendLine("Scorecard")
            input.scorecardLines.forEach { appendLine("- $it") }
            appendLine()
        }
        appendLine("Ethics")
        appendLine(DEFAULT_ETHICS_FOOTER)
    }

    internal fun buildDigsCsv(digs: List<ExcavationLogEntry>): String = buildString {
        append("id,targetId,notes,depth,complete,startedAtMillis,completedAtMillis,createdAtMillis,updatedAtMillis\n")
        digs.forEach { dig ->
            val notes = listOf(dig.soilNotes, dig.findsDescription)
                .filter { it.isNotBlank() }
                .joinToString(" | ")
            val fields = listOf(
                dig.id,
                dig.targetId.toString(),
                notes,
                dig.depthCentimeters?.toString().orEmpty(),
                dig.isComplete.toString(),
                dig.startedAtMillis.toString(),
                dig.completedAtMillis?.toString().orEmpty(),
                dig.createdAtMillis.toString(),
                dig.updatedAtMillis.toString(),
            )
            append(fields.joinToString(",") { csvEscape(it) }).append('\n')
        }
    }

    internal fun buildBoundariesGeoJson(boundaries: List<SurveyBoundary>): String = buildString {
        append("{\"type\":\"FeatureCollection\",\"features\":[")
        var written = 0
        boundaries.forEach { boundary ->
            if (boundary.vertices.size < 3) return@forEach
            if (written > 0) append(',')
            append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[")
            boundary.vertices.forEachIndexed { index, vertex ->
                if (index > 0) append(',')
                append('[').append(formatDecimal(vertex.longitude, 7)).append(',')
                    .append(formatDecimal(vertex.latitude, 7)).append(']')
            }
            // Close the ring when the first vertex is not already repeated.
            val first = boundary.vertices.first()
            val last = boundary.vertices.last()
            if (first.latitude != last.latitude || first.longitude != last.longitude) {
                append(",[").append(formatDecimal(first.longitude, 7)).append(',')
                    .append(formatDecimal(first.latitude, 7)).append(']')
            }
            append("]]},\"properties\":{")
            append("\"id\":\"").append(jsonEscape(boundary.id)).append("\",")
            append("\"name\":\"").append(jsonEscape(boundary.displayName)).append("\",")
            append("\"terrainKey\":\"").append(jsonEscape(boundary.terrainKey)).append("\",")
            append("\"createdAtMillis\":").append(boundary.createdAtMillis)
            append("}}")
            written++
        }
        append("]}")
    }

    internal fun buildTrailsGeoJson(trails: List<BreadcrumbTrack>): String = buildString {
        append("{\"type\":\"FeatureCollection\",\"features\":[")
        var written = 0
        trails.forEach { trail ->
            if (trail.points.size < 2) return@forEach
            if (written > 0) append(',')
            append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
            trail.points.forEachIndexed { index, point ->
                if (index > 0) append(',')
                append('[').append(formatDecimal(point.longitude, 7)).append(',')
                    .append(formatDecimal(point.latitude, 7)).append(']')
            }
            append("]},\"properties\":{")
            append("\"id\":\"").append(jsonEscape(trail.id)).append("\",")
            append("\"name\":\"").append(jsonEscape(trail.displayName)).append("\",")
            append("\"terrainKey\":\"").append(jsonEscape(trail.terrainKey)).append("\",")
            append("\"pointCount\":").append(trail.points.size).append(',')
            append("\"isRecording\":").append(trail.isRecording).append(',')
            append("\"createdAtMillis\":").append(trail.createdAtMillis).append(',')
            append("\"updatedAtMillis\":").append(trail.updatedAtMillis)
            append("}}")
            written++
        }
        append("]}")
    }

    private fun formatTimestamp(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.US).format(Date(timestamp))

    private fun formatDecimal(value: Double, places: Int): String =
        String.format(Locale.US, "%.${places}f", value)

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun jsonEscape(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u%04x".format(character.code))
                } else {
                    append(character)
                }
            }
        }
    }
}
