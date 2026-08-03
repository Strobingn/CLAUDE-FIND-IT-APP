package com.example.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class CautionPenaltyTest {
    @Test
    fun cautionPenaltyIsSmallAndCapped() {
        assertEquals(0f, MetalDetectingTargetRefiner.cautionPenalty(0), 1e-4f)
        assertEquals(0.12f, MetalDetectingTargetRefiner.cautionPenalty(2), 1e-4f)
        assertEquals(0.18f, MetalDetectingTargetRefiner.cautionPenalty(99), 1e-4f)
    }
}
