package com.example.ui

import com.example.data.NormalizedRasterBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkspaceConfigTest {
    @Test
    fun aiRefineResolutionAdaptsToDeviceCapability() {
        assertEquals(768, chooseAiRefineResolution(256, false, 8))
        assertEquals(768, chooseAiRefineResolution(1_024, true, 8))
        assertEquals(768, chooseAiRefineResolution(320, false, 4))
        assertEquals(768, chooseAiRefineResolution(384, false, 6))
        assertEquals(1_024, chooseAiRefineResolution(512, false, 8))
    }

    @Test
    fun wholeTerrainRefineUsesAlreadyDecodedSource() {
        assertTrue(isEffectivelyWholeTerrain(NormalizedRasterBounds.Full))
        assertTrue(isEffectivelyWholeTerrain(NormalizedRasterBounds(0.02, 0.02, 0.98, 0.98)))
        assertTrue(!isEffectivelyWholeTerrain(NormalizedRasterBounds(0.2, 0.2, 0.8, 0.8)))
    }

    @Test
    fun activeAiPanelKeepsBuiltInQuestions() {
        assertEquals(5, AI_BUILT_IN_QUESTIONS.size)
        assertEquals(AI_BUILT_IN_QUESTIONS.size, AI_BUILT_IN_QUESTIONS.distinct().size)
        assertTrue(AI_BUILT_IN_QUESTIONS.all(String::isNotBlank))
    }

    @Test
    fun aiAnalysisDefaultsToSourceHillshade() {
        assertTrue(AiTerrainState().showSourceHillshade)
        assertTrue(AI_HISTORIC_TARGETS_DEFAULT_VISIBLE)
    }

    @Test
    fun cloudAiTargetsMapFromViewportCoordinates() {
        val targets = parseCloudMapTargets(
            "[MAP_TARGET x=50 y=25 confidence=0.8 label=possible cellar rim]",
            NormalizedRasterBounds(0.2, 0.4, 0.6, 0.8),
        )
        assertEquals(1, targets.size)
        assertEquals(40f, targets.single().xPercent, 0.001f)
        assertEquals(50f, targets.single().yPercent, 0.001f)
        assertEquals("possible cellar rim", targets.single().label)
    }
}
