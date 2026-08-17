package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V116PremiumTvFinishTest {
    @Test fun addressPlanIsCalmAndLowFrequency() {
        val plan = V116PremiumTvPlanner.plan(
            running = false,
            progress01 = 0.0,
            speedMps = 0.0,
            result = null,
            resultAgeMs = 0L
        )
        assertEquals(V116PremiumPhase.ADDRESS, plan.phase)
        assertEquals(0, plan.particleCount)
        assertEquals(240L, plan.refreshMs)
        assertTrue(plan.ambientAlpha in 0..64)
        assertTrue(plan.vignetteAlpha in 0..96)
    }

    @Test fun rollPlanUsesLiveRefreshWithoutCelebrationParticles() {
        val plan = V116PremiumTvPlanner.plan(
            running = true,
            progress01 = .40,
            speedMps = 1.8,
            result = null,
            resultAgeMs = 0L
        )
        assertEquals(V116PremiumPhase.ROLL, plan.phase)
        assertEquals(0, plan.particleCount)
        assertEquals(16L, plan.refreshMs)
        assertTrue(plan.ballHaloAlpha > 24)
    }

    @Test fun cupApproachRaisesCupFocusWhileRemainingBounded() {
        val plan = V116PremiumTvPlanner.plan(
            running = true,
            progress01 = .90,
            speedMps = .55,
            result = null,
            resultAgeMs = 0L
        )
        assertEquals(V116PremiumPhase.CUP_APPROACH, plan.phase)
        assertTrue(plan.cupHaloAlpha >= 100)
        assertTrue(plan.ballHaloAlpha in 0..78)
        assertTrue(plan.glassAlpha in 0..255)
    }

    @Test fun holedResultGetsStrongButBoundedCelebration() {
        val result = SimResult(true, 0.0, 5.0, 0.0, 1.7)
        val plan = V116PremiumTvPlanner.plan(
            running = false,
            progress01 = 1.0,
            speedMps = 0.0,
            result = result,
            resultAgeMs = 150L
        )
        assertEquals(V116PremiumPhase.HOLED, plan.phase)
        assertEquals(22, plan.particleCount)
        assertTrue(plan.resultBloomAlpha in 1..100)
        assertTrue(plan.cupHaloAlpha in 1..170)
        assertEquals(16L, plan.refreshMs)
    }

    @Test fun oldResultStopsAnimationBudget() {
        val result = SimResult(false, .20, 5.1, .22, 1.7)
        val plan = V116PremiumTvPlanner.plan(
            running = false,
            progress01 = 1.0,
            speedMps = 0.0,
            result = result,
            resultAgeMs = 4_000L
        )
        assertEquals(V116PremiumPhase.RESULT, plan.phase)
        assertEquals(0, plan.particleCount)
        assertEquals(0, plan.resultBloomAlpha)
        assertEquals(240L, plan.refreshMs)
    }

    @Test fun malformedPresentationInputsFailSafeToFiniteBoundedPlan() {
        val plan = V116PremiumTvPlanner.plan(
            running = false,
            progress01 = Double.NaN,
            speedMps = Double.POSITIVE_INFINITY,
            result = null,
            resultAgeMs = -500L
        )
        assertEquals(V116PremiumPhase.ADDRESS, plan.phase)
        assertTrue(plan.ambientAlpha in 0..255)
        assertTrue(plan.vignetteAlpha in 0..255)
        assertTrue(plan.ballHaloAlpha in 0..78)
        assertTrue(plan.cupHaloAlpha in 0..170)
        assertTrue(plan.resultBloomAlpha in 0..100)
        assertTrue(plan.particleCount in 0..22)
    }
}
