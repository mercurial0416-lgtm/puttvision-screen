package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V107TvGreenSurfaceDepthTest {
    @Test
    fun normalTargetProducesBoundedBroadcastTexturePlan() {
        val plan = V107TvGreenSurfacePlanner.plan(5.0, running = false)
        assertEquals(5.0, plan.targetDistanceM, 0.0001)
        assertEquals(8.0, plan.visibleLengthM, 0.0001)
        assertTrue(plan.stripeCount in 5..32)
        assertTrue(plan.stripeAlpha in 1..32)
        assertTrue(plan.edgeAlpha > plan.stripeAlpha)
        assertTrue(plan.centerGuideAlpha > 0)
        assertEquals(240L, plan.refreshMs)
    }

    @Test
    fun movingBallReducesCosmeticLoadAndRefreshesSmoothly() {
        val idle = V107TvGreenSurfacePlanner.plan(12.0, running = false)
        val moving = V107TvGreenSurfacePlanner.plan(12.0, running = true)
        assertTrue(moving.stripeAlpha < idle.stripeAlpha)
        assertTrue(moving.edgeAlpha < idle.edgeAlpha)
        assertTrue(moving.horizonAlpha < idle.horizonAlpha)
        assertTrue(moving.refreshMs < idle.refreshMs)
    }

    @Test
    fun nonFiniteAndNegativeTargetsFailSafe() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -3.0).forEach { bad ->
            val plan = V107TvGreenSurfacePlanner.plan(bad, running = false)
            assertEquals(0.0, plan.targetDistanceM, 0.0001)
            assertTrue(plan.visibleLengthM.isFinite())
            assertTrue(plan.visibleLengthM in 4.0..33.0)
            assertTrue(plan.stripeWidthM > 0.0)
            assertTrue(plan.stripeCount in 5..32)
        }
    }

    @Test
    fun extremelyLongTargetCannotExplodeOverlayWork() {
        val plan = V107TvGreenSurfacePlanner.plan(10_000.0, running = false)
        assertEquals(33.0, plan.visibleLengthM, 0.0001)
        assertTrue(plan.stripeCount <= 32)
        assertTrue(plan.halfWidthM in 3.0..4.0)
    }

    @Test
    fun stripeDensityAdaptsToVisibleDepth() {
        val near = V107TvGreenSurfacePlanner.plan(3.0, running = false)
        val mid = V107TvGreenSurfacePlanner.plan(10.0, running = false)
        val far = V107TvGreenSurfacePlanner.plan(25.0, running = false)
        assertEquals(.65, near.stripeWidthM, 0.0001)
        assertEquals(.85, mid.stripeWidthM, 0.0001)
        assertEquals(1.05, far.stripeWidthM, 0.0001)
    }
}
