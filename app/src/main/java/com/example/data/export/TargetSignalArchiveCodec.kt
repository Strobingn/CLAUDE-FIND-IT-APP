package com.example.data.export

import com.example.data.DetectionSource
import com.example.data.MetalType
import com.example.data.TargetSignal
import com.example.data.VerificationOutcome
import java.net.URLDecoder
import java.net.URLEncoder

/** Result of merging an imported archive's target signals into the local find list. */
data class ArchiveImportSummary(
    val projectName: String,
    val imported: Int,
    val updated: Int,
    val keptLocal: Int,
    /** Signals whose local and incoming copies both changed since being shared — not auto-merged. */
    val needsReview: List<TargetSignal>,
) {
    val hasConflicts: Boolean get() = needsReview.isNotEmpty()
}

/**
 * Lossless [TargetSignal] snapshot for the portable project archive — distinct from the
 * human/GIS-facing CSV/GPX/KML/GeoJSON exports in the same archive, which drop fields (photo and
 * voice-note URIs, per-photo bearing, dataset/terrain keys, outcome) that matter for merging
 * finds back into another device's local database via [com.example.data.field.SyncConflictResolver].
 */
object TargetSignalArchiveCodec {
    const val ENTRIES_PATH = "entities/target_signals.tsv"
    private const val HEADER = "FINDIT_TARGET_SIGNALS_V1"
    private const val FIELD_COUNT = 22

    fun encode(signals: List<TargetSignal>): ProjectArchiveFile {
        val text = buildString {
            appendLine(HEADER)
            for (signal in signals) {
                appendLine(
                    listOf(
                        signal.id.toString(),
                        signal.gridX.toString(),
                        signal.gridY.toString(),
                        signal.metalType.name,
                        signal.signalStrength.toString(),
                        signal.depthCm?.toString().orEmpty(),
                        signal.latitude?.toString().orEmpty(),
                        signal.longitude?.toString().orEmpty(),
                        signal.gpsLatitude?.toString().orEmpty(),
                        signal.gpsLongitude?.toString().orEmpty(),
                        signal.gpsAccuracyMeters?.toString().orEmpty(),
                        signal.source.name,
                        signal.timestamp.toString(),
                        enc(signal.notes),
                        signal.photoUris.joinToString(",") { enc(it) },
                        // Aligned by index with photoUris; "-" marks a photo with no bearing.
                        signal.photoBearingsDegrees.joinToString(",") { it?.toString() ?: "-" },
                        signal.voiceNoteUris.joinToString(",") { enc(it) },
                        enc(signal.status),
                        signal.outcome.name,
                        signal.datasetKey?.let { enc(it) }.orEmpty(),
                        signal.terrainKey?.let { enc(it) }.orEmpty(),
                        signal.detectedFeatureType?.let { enc(it) }.orEmpty(),
                    ).joinToString("\t") + "\t" + signal.starred,
                )
            }
        }
        return ProjectArchiveFile(ENTRIES_PATH, text.toByteArray(Charsets.UTF_8))
    }

    fun decode(bytes: ByteArray): List<TargetSignal> {
        val lines = bytes.toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }
        if (lines.firstOrNull() != HEADER) return emptyList()
        return lines.drop(1).mapNotNull(::parseRow)
    }

    private fun parseRow(line: String): TargetSignal? {
        val fields = line.split("\t")
        if (fields.size < FIELD_COUNT) return null
        return try {
            TargetSignal(
                id = fields[0].toLong(),
                gridX = fields[1].toFloat(),
                gridY = fields[2].toFloat(),
                metalType = MetalType.valueOf(fields[3]),
                signalStrength = fields[4].toFloat(),
                depthCm = fields[5].toIntOrNull(),
                latitude = fields[6].toDoubleOrNull(),
                longitude = fields[7].toDoubleOrNull(),
                gpsLatitude = fields[8].toDoubleOrNull(),
                gpsLongitude = fields[9].toDoubleOrNull(),
                gpsAccuracyMeters = fields[10].toFloatOrNull(),
                source = DetectionSource.valueOf(fields[11]),
                timestamp = fields[12].toLong(),
                notes = dec(fields[13]),
                photoUris = fields[14].split(",").filter { it.isNotBlank() }.map { dec(it) },
                photoBearingsDegrees = if (fields[15].isEmpty()) {
                    emptyList()
                } else {
                    fields[15].split(",").map { if (it == "-") null else it.toFloatOrNull() }
                },
                voiceNoteUris = fields[16].split(",").filter { it.isNotBlank() }.map { dec(it) },
                status = dec(fields[17]),
                outcome = VerificationOutcome.valueOf(fields[18]),
                datasetKey = fields[19].takeIf { it.isNotBlank() }?.let { dec(it) },
                terrainKey = fields[20].takeIf { it.isNotBlank() }?.let { dec(it) },
                detectedFeatureType = fields[21].takeIf { it.isNotBlank() }?.let { dec(it) },
                starred = fields.getOrNull(22)?.toBooleanStrictOrNull() ?: false,
            )
        } catch (malformed: Exception) {
            null
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun dec(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
}
