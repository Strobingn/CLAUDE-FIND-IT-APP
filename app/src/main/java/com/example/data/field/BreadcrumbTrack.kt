package com.example.data.field

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A recorded GPS breadcrumb, retained with the terrain project that produced it. */
data class BreadcrumbPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val recordedAtMillis: Long,
)

data class BreadcrumbTrack(
    val id: String,
    val terrainKey: String,
    val displayName: String,
    val points: List<BreadcrumbPoint>,
    val isRecording: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    /** Avoid saving GPS jitter or an unbounded stream of virtually identical points. */
    fun shouldAppend(point: BreadcrumbPoint): Boolean {
        val previous = points.lastOrNull() ?: return true
        val elapsedMillis = point.recordedAtMillis - previous.recordedAtMillis
        return elapsedMillis >= MIN_POINT_INTERVAL_MILLIS &&
            distanceMeters(previous, point) >= MIN_POINT_DISTANCE_METERS
    }

    fun withPoint(point: BreadcrumbPoint): BreadcrumbTrack =
        if (shouldAppend(point)) copy(points = points + point, updatedAtMillis = point.recordedAtMillis) else this

    companion object {
        const val MIN_POINT_INTERVAL_MILLIS = 5_000L
        const val MIN_POINT_DISTANCE_METERS = 4.0

        fun distanceMeters(first: BreadcrumbPoint, second: BreadcrumbPoint): Double {
            val latitudeRadians = Math.toRadians(second.latitude - first.latitude)
            val longitudeRadians = Math.toRadians(second.longitude - first.longitude)
            val a = sin(latitudeRadians / 2.0) * sin(latitudeRadians / 2.0) +
                cos(Math.toRadians(first.latitude)) * cos(Math.toRadians(second.latitude)) *
                sin(longitudeRadians / 2.0) * sin(longitudeRadians / 2.0)
            return 6_371_000.0 * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        }
    }
}

/**
 * Compact, locale-independent storage for Room. A small delimited codec keeps raw breadcrumbs
 * testable without Android's mocked org.json implementation and rejects malformed fixes.
 */
internal fun BreadcrumbTrack.pointsToStorage(): String = points.joinToString(";") { point ->
    "${point.latitude},${point.longitude},${point.accuracyMeters},${point.recordedAtMillis}"
}

internal fun breadcrumbPointsFromStorage(value: String): List<BreadcrumbPoint> = buildList {
    value.split(';').forEach { serializedPoint ->
        val values = serializedPoint.split(',')
        if (values.size != 4) return@forEach
        val latitude = values[0].toDoubleOrNull() ?: return@forEach
        val longitude = values[1].toDoubleOrNull() ?: return@forEach
        val accuracy = values[2].toFloatOrNull() ?: return@forEach
        val timestamp = values[3].toLongOrNull() ?: return@forEach
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
            !accuracy.isFinite() || accuracy < 0f || timestamp < 0L
        ) return@forEach
        add(BreadcrumbPoint(latitude, longitude, accuracy, timestamp))
    }
}
