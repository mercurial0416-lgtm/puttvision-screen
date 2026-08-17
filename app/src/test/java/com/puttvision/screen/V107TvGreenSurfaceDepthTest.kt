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
        assertTrue(plan.distanceTickCount in 1..16)
        assertEquals(1.0, plan.distanceTickSpacingM, 0.0001)
        assertTrue(plan.distanceTickAlpha > 0)
        assertTrue(plan.majorTickEvery in 2..4)
        assertTrue(plan.majorTickHalfWidthM > .55)
        assertTrue(plan.distanceLabelCount in 0..6)
        assertTrue(plan.distanceLabelAlpha > 0)
        assertTrue(plan.targetGuideAlpha > 0)
        assertTrue(plan.cupWindowHalfLengthM in .4..1.0)
        assertTrue(plan.cupWindowHalfWidthM in .5..1.0)
        assertTrue(plan.cupWindowAlpha > 0)
        assertTrue(plan.laneOffsetM in .8..1.3)
        assertTrue(plan.laneGuideAlpha > 0)
        assertEquals(240L, plan.refreshMs)
    }

    @Test
    fun movingBallReducesCosmeticLoadAndRefreshesSmoothly() {
        val idle = V107TvGreenSurfacePlanner.plan(12.0, running = false)
        val moving = V107TvGreenSurfacePlanner.plan(12.0, running = true)
        assertTrue(moving.stripeAlpha < idle.stripeAlpha)
        assertTrue(moving.edgeAlpha < idle.edgeAlpha)
        assertTrue(moving.horizonAlpha < idle.horizonAlpha)
        assertTrue(moving.distanceTickAlpha < idle.distanceTickAlpha)
        assertEquals(0, moving.distanceLabelAlpha)
        assertTrue(moving.targetGuideAlpha < idle.targetGuideAlpha)
        assertTrue(moving.cupWindowAlpha < idle.cupWindowAlpha)
        assertTrue(moving.laneGuideAlpha < idle.laneGuideAlpha)
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
            assertTrue(plan.distanceTickSpacingM > 0.0)
            assertTrue(plan.distanceTickCount in 1..16)
            assertEquals(0, plan.targetGuideAlpha)
            assertEquals(0, plan.cupWindowAlpha)
        }
    }

    @Test
    fun extremelyLongTargetCannotExplodeOverlayWork() {
        val plan = V107TvGreenSurfacePlanner.plan(10_000.0, running = false)
        assertEquals(33.0, plan.visibleLengthM, 0.0001)
        assertTrue(plan.stripeCount <= 32)
        assertTrue(plan.distanceTickCount <= 16)
        assertTrue(plan.distanceLabelCount <= 6)
        assertTrue(plan.halfWidthM in 3.0..4.0)
        assertTrue(plan.majorTickEvery in 2..4)
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

    @Test
    fun distanceTickDensityAdaptsWithoutUnboundedProjectionWork() {
        val near = V107TvGreenSurfacePlanner.plan(3.0, running = false)
        val mid = V107TvGreenSurfacePlanner.plan(10.0, running = false)
        val far = V107TvGreenSurfacePlanner.plan(25.0, running = false)
        assertEquals(1.0, near.distanceTickSpacingM, 0.0001)
        assertEquals(2.0, mid.distanceTickSpacingM, 0.0001)
        assertEquals(3.0, far.distanceTickSpacingM, 0.0001)
        assertTrue(near.distanceTickCount <= 16)
        assertTrue(mid.distanceTickCount <= 16)
        assertTrue(far.distanceTickCount <= 16)
    }

    @Test
    fun majorTickAndLabelCadenceStayBoundedAsDepthGrows() {
        val near = V107TvGreenSurfacePlanner.plan(3.0, running = false)
        val mid = V107TvGreenSurfacePlanner.plan(12.0, running = false)
        val far = V107TvGreenSurfacePlanner.plan(30.0, running = false)
        assertTrue(near.majorTickEvery <= mid.majorTickEvery)
        assertTrue(mid.majorTickEvery <= far.majorTickEvery)
        listOf(near, mid, far).forEach { plan ->
            assertEquals(plan.majorTickEvery, plan.distanceLabelEvery)
            assertTrue(plan.distanceLabelCount <= 6)
            assertTrue(plan.majorTickHalfWidthM <= plan.halfWidthM)
        }
    }

    @Test
    fun zeroTargetDoesNotPretendThereIsACupFocusZone() {
        val plan = V107TvGreenSurfacePlanner.plan(0.0, running = false)
        assertEquals(0, plan.targetGuideAlpha)
        assertEquals(0, plan.cupWindowAlpha)
        assertTrue(plan.distanceLabelCount <= 6)
    }
}
