package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V117PremiumRenderBudgetTest {
    @Test fun highTierPreservesOriginalPremiumPlanExactly() {
        val base = V116PremiumTvPlanner.plan(
            running = true,
            progress01 = .48,
            speedMps = 1.9,
            result = null,
            resultAgeMs = 0L
        )
        assertEquals(base, V116PremiumTvPlanner.adaptForRenderTier(base, V24RenderTier.HIGH))
    }

    @Test fun performanceTierProtectsLiveCameraBudget() {
        val base = V116PremiumTvPlanner.plan(
            running = true,
            progress01 = .82,
            speedMps = 1.2,
            result = null,
            resultAgeMs = 0L
        )
        val plan = V116PremiumTvPlanner.adaptForRenderTier(base, V24RenderTier.PERFORMANCE)
        assertEquals(24L, plan.refreshMs)
        assertEquals(0, plan.particleCount)
        assertTrue(plan.ambientAlpha <= base.ambientAlpha)
        assertTrue(plan.horizonAlpha <= base.horizonAlpha)
        assertTrue(plan.ballHaloAlpha <= base.ballHaloAlpha)
        assertTrue(plan.cupHaloAlpha <= base.cupHaloAlpha)
    }

    @Test fun performanceTierCapsCelebrationWork() {
        val result = SimResult(true, 0.0, 5.0, 0.0, 1.7)
        val base = V116PremiumTvPlanner.plan(
            running = false,
            progress01 = 1.0,
            speedMps = 0.0,
            result = result,
            resultAgeMs = 150L
        )
        val plan = V116PremiumTvPlanner.adaptForRenderTier(base, V24RenderTier.PERFORMANCE)
        assertEquals(24L, plan.refreshMs)
        assertTrue(plan.particleCount in 0..6)
        assertTrue(plan.resultBloomAlpha < base.resultBloomAlpha)
        assertTrue(plan.cupHaloAlpha < base.cupHaloAlpha)
    }

    @Test fun balancedTierKeepsIntermediateCelebrationBudget() {
        val result = SimResult(true, 0.0, 5.0, 0.0, 1.7)
        val base = V116PremiumTvPlanner.plan(
            running = false,
            progress01 = 1.0,
            speedMps = 0.0,
            result = result,
            resultAgeMs = 150L
        )
        val balanced = V116PremiumTvPlanner.adaptForRenderTier(base, V24RenderTier.BALANCED)
        val performance = V116PremiumTvPlanner.adaptForRenderTier(base, V24RenderTier.PERFORMANCE)
        assertEquals(16L, balanced.refreshMs)
        assertTrue(balanced.particleCount in performance.particleCount..12)
        assertTrue(balanced.resultBloomAlpha in performance.resultBloomAlpha..base.resultBloomAlpha)
    }

    @Test fun idlePlanIsNeverAccidentallyAccelerated() {
        val base = V116PremiumTvPlanner.plan(
            running = false,
            progress01 = 0.0,
            speedMps = 0.0,
            result = null,
            resultAgeMs = 0L
        )
        assertEquals(240L, V116PremiumTvPlanner.adaptForRenderTier(base, V24RenderTier.HIGH).refreshMs)
        assertEquals(240L, V116PremiumTvPlanner.adaptForRenderTier(base, V24RenderTier.BALANCED).refreshMs)
        assertEquals(240L, V116PremiumTvPlanner.adaptForRenderTier(base, V24RenderTier.PERFORMANCE).refreshMs)
    }

    @Test fun adaptationKeepsEveryExpensiveFieldBounded() {
        val malformed = V116PremiumTvPlan(
            phase = V116PremiumPhase.HOLED,
            accentColor = 0,
            ambientAlpha = -50,
            vignetteAlpha = 999,
            horizonAlpha = 999,
            ballHaloAlpha = 999,
            cupHaloAlpha = 999,
            resultBloomAlpha = 999,
            particleCount = 999,
            glassAlpha = 999,
            refreshMs = -1L
        )
        val plan = V116PremiumTvPlanner.adaptForRenderTier(malformed, V24RenderTier.PERFORMANCE)
        assertTrue(plan.ambientAlpha in 0..255)
        assertTrue(plan.horizonAlpha in 0..255)
        assertTrue(plan.ballHaloAlpha in 0..78)
        assertTrue(plan.cupHaloAlpha in 0..170)
        assertTrue(plan.resultBloomAlpha in 0..100)
        assertTrue(plan.particleCount in 0..6)
        assertTrue(plan.refreshMs >= 24L)
    }
}
