package com.example.data.field

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityAlerterTest {

    @Test
    fun `fires once on entry then stays quiet while inside`() {
        val alerter = ProximityAlerter(enterMeters = 15f, exitMeters = 30f)
        assertFalse(alerter.offer(80f))
        assertFalse(alerter.offer(16f))
        assertTrue(alerter.offer(14f)) // entry edge
        assertFalse(alerter.offer(10f))
        assertFalse(alerter.offer(14.9f))
    }

    @Test
    fun `jitter across the entry line does not re-fire`() {
        val alerter = ProximityAlerter(enterMeters = 15f, exitMeters = 30f)
        assertTrue(alerter.offer(12f))
        assertFalse(alerter.offer(18f)) // outside enter but inside exit band
        assertFalse(alerter.offer(12f))
        assertFalse(alerter.offer(29f))
        assertFalse(alerter.offer(12f))
    }

    @Test
    fun `re-arms after leaving past the exit threshold`() {
        val alerter = ProximityAlerter(enterMeters = 15f, exitMeters = 30f)
        assertTrue(alerter.offer(10f))
        assertFalse(alerter.offer(31f)) // leaves, no event on exit
        assertTrue(alerter.isInside.not())
        assertTrue(alerter.offer(10f)) // re-entry fires again
    }

    @Test
    fun `reset disarms immediately`() {
        val alerter = ProximityAlerter()
        assertTrue(alerter.offer(5f))
        alerter.reset()
        assertFalse(alerter.isInside)
        assertTrue(alerter.offer(5f))
    }
}
