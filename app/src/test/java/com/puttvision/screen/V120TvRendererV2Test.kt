package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V120TvRendererV2Test {
    @Test fun addressHighIsCalmDetailedAndReadReady() {
        val plan = V120TvRendererV2Planner.plan(
            running = false,
            progress01 = 0.0,
            speedMps = 0.0,
            result = null,
            resultAgeMs = 0L,
            tier = V24RenderTier.HIGH
        )
        assertEquals(V120TvPhase.ADDRESS, plan.phase)
        assertEquals(30, plan.courseSamples)
        assertEquals(44, plan.trailSamples)
        assertEquals(36, plan.cupRingSegments)
        assertTrue(plan.readLineAlpha > 0)
        assertFalse(plan.showResultCard)
        assertEquals(0, plan.celebrationCount)
        assertTrue(plan.refreshMs >= 100L)
    }

    @Test fun rollingPerformanceShedsLoadAndNeverCelebrates() {
        val plan = V120TvRendererV2Planner.plan(
            running = true,
            progress01 = .42,
            speedMps = 1.4,
            result = null,
            resultAgeMs = 0L,
            tier = V24RenderTier.PERFORMANCE
        )
        assertEquals(V120TvPhase.ROLL, plan.phase)
        assertEquals(V24RenderTier.PERFORMANCE.movingFrameMs, plan.refreshMs)
        assertEquals(14, plan.courseSamples)
        assertEquals(18, plan.trailSamples)
        assertEquals(18, plan.cupRingSegments)
        assertEquals(0, plan.readLineAlpha)
        assertEquals(0, plan.celebrationCount)
    }

    @Test fun cupApproachRaisesCupFocusWithoutExtraParticles() {
        val roll = V120TvRendererV2Planner.plan(true, .50, .8, null, 0L, V24RenderTier.HIGH)
        val cup = V120TvRendererV2Planner.plan(true, .88, .35, null, 0L, V24RenderTier.HIGH)
        assertEquals(V120TvPhase.CUP_APPROACH, cup.phase)
        assertTrue(cup.cupFocusAlpha > roll.cupFocusAlpha)
        assertEquals(0, cup.celebrationCount)
    }

    @Test fun holedCelebrationIsBoundedByQualityTier() {
        val result = SimResult(true, 0.0, 5.0, 0.0, 2.0, cupContacts = 1)
        val high = V120TvRendererV2Planner.plan(false, 1.0, 0.0, result, 100L, V24RenderTier.HIGH)
        val performance = V120TvRendererV2Planner.plan(false, 1.0, 0.0, result, 100L, V24RenderTier.PERFORMANCE)
        assertEquals(V120TvPhase.HOLED, high.phase)
        assertTrue(high.showResultCard)
        assertEquals(14, high.celebrationCount)
        assertEquals(5, performance.celebrationCount)
        assertTrue(performance.celebrationCount < high.celebrationCount)
    }

    @Test fun agedResultStopsAnimationCost() {
        val result = SimResult(false, .2, 4.8, .28, 2.0)
        val plan = V120TvRendererV2Planner.plan(false, .9, 0.0, result, 4_000L, V24RenderTier.HIGH)
        assertEquals(V120TvPhase.RESULT, plan.phase)
        assertEquals(0, plan.celebrationCount)
        assertTrue(plan.showResultCard)
        assertTrue(plan.refreshMs >= 100L)
    }

    @Test fun malformedPresentationInputsFailSafeToBounds() {
        val plan = V120TvRendererV2Planner.plan(
            running = false,
            progress01 = Double.NaN,
            speedMps = Double.POSITIVE_INFINITY,
            result = null,
            resultAgeMs = -99L,
            tier = V24RenderTier.BALANCED
        )
        assertEquals(V120TvPhase.ADDRESS, plan.phase)
        assertTrue(plan.courseSamples in 8..40)
        assertTrue(plan.trailSamples in 8..60)
        assertTrue(plan.cupRingSegments in 12..40)
        assertTrue(plan.vignetteAlpha in 0..255)
        assertTrue(plan.ballHaloAlpha in 0..255)
        assertTrue(plan.cupFocusAlpha in 0..255)
    }

    @Test fun canonicalLayerPolicyRetiresLegacyDecorativeStack() {
        assertFalse(V120TvLayerPolicy.legacyDecorativeLayersEnabled)
        assertEquals(listOf("WORLD", "RENDERER_V2", "TRAINING", "REPLAY"), V120TvLayerPolicy.canonicalLayers)
        assertFalse(V120TvLayerPolicy.canonicalLayers.any { it.contains("V89") || it.contains("V116") || it.contains("V118") })
    }
}
