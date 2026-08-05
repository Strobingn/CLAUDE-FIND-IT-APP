package com.example.data.field

import com.example.data.MetalType
import com.example.data.TargetSignal
import com.example.data.VerificationOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldSessionStatsTest {

    @Test
    fun `counts outcomes and finds top type`() {
        val signals = listOf(
            signal(outcome = VerificationOutcome.CONFIRMED_FEATURE, type = MetalType.GOLD),
            signal(outcome = VerificationOutcome.CONFIRMED_FEATURE, type = MetalType.GOLD),
            signal(outcome = VerificationOutcome.REJECTED_FALSE_POSITIVE, type = MetalType.SILVER),
            signal(outcome = VerificationOutcome.UNVERIFIED, type = MetalType.IRON),
        )
        val stats = FieldSessionStatsCalculator.compute(signals, emptyList())
        assertEquals(4, stats.totalFinds)
        assertEquals(2, stats.confirmedFinds)
        assertEquals(1, stats.rejectedFinds)
        assertEquals("Gold Coin/Ring", stats.topFindType)
        assertEquals(2f / 3f, stats.confirmRate!!, 1e-4f)
    }

    @Test
    fun `sums track distance across points`() {
        // ~111.195 m per 0.001 degree of latitude.
        val track = track(
            point(41.0000, -74.0, 0L),
            point(41.0010, -74.0, 60_000L),
            point(41.0020, -74.0, 120_000L),
        )
        val stats = FieldSessionStatsCalculator.compute(emptyList(), listOf(track))
        assertEquals(222.4, stats.distanceMeters, 2.0)
    }

    @Test
    fun `finds per hour only when the session is long enough`() {
        val shortSpan = listOf(
            signal(timestamp = 1_000_000L),
            signal(timestamp = 1_000_000L + 5 * 60_000L),
        )
        val short = FieldSessionStatsCalculator.compute(shortSpan, emptyList())
        assertNull(short.findsPerHour)

        val longSpan = listOf(
            signal(timestamp = 1_000_000L),
            signal(timestamp = 1_000_000L),
            signal(timestamp = 1_000_000L + 60 * 60_000L),
        )
        val long = FieldSessionStatsCalculator.compute(longSpan, emptyList())
        assertEquals(3.0, long.findsPerHour!!, 1e-4)
    }

    @Test
    fun `empty inputs produce a zeroed report`() {
        val stats = FieldSessionStatsCalculator.compute(emptyList(), emptyList())
        assertEquals(0, stats.totalFinds)
        assertEquals(0.0, stats.distanceMeters, 1e-6)
        assertNull(stats.activeMinutes)
        assertNull(stats.topFindType)
        assertNull(stats.confirmRate)
    }

    private fun signal(
        outcome: VerificationOutcome = VerificationOutcome.UNVERIFIED,
        type: MetalType = MetalType.GOLD,
        timestamp: Long = 1_000_000L,
    ) = TargetSignal(
        gridX = 50f,
        gridY = 50f,
        metalType = type,
        signalStrength = 80f,
        outcome = outcome,
        timestamp = timestamp,
    )

    private fun point(lat: Double, lon: Double, at: Long) =
        BreadcrumbPoint(latitude = lat, longitude = lon, accuracyMeters = 5f, recordedAtMillis = at)

    private fun track(vararg points: BreadcrumbPoint) = BreadcrumbTrack(
        id = "t1",
        terrainKey = "k",
        displayName = "Track",
        points = points.toList(),
        isRecording = false,
        createdAtMillis = points.firstOrNull()?.recordedAtMillis ?: 0L,
        updatedAtMillis = points.lastOrNull()?.recordedAtMillis ?: 0L,
    )
}
