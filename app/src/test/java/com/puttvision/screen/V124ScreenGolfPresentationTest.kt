package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V124ScreenGolfPresentationTest {
    @Test fun highWorldUsesPremiumDetailBudget() {
        val high = V124WorldPlanner.plan(V24RenderTier.HIGH, 5.0)
        val perf = V124WorldPlanner.plan(V24RenderTier.PERFORMANCE, 5.0)
        assertTrue(high.cols > perf.cols)
        assertTrue(high.rows > perf.rows)
        assertTrue(high.treeCount > perf.treeCount)
        assertTrue(high.mountainSegments > perf.mountainSegments)
        assertTrue(high.movingFrameMs < perf.movingFrameMs)
    }

    @Test fun malformedDistanceFallsBackToBoundedWorld() {
        val plan = V124WorldPlanner.plan(V24RenderTier.BALANCED, Double.NaN)
        assertTrue(plan.cols in 8..48)
        assertTrue(plan.rows in 20..120)
        assertTrue(plan.treeCount in 4..32)
        assertTrue(plan.fogFarM in 20f..60f)
    }

    @Test fun addressHudShowsReadyWithoutResult() {
        val plan = V124HudPlanner.plan(running = false, hasShot = false, hasResult = false, resultAgeMs = 0L)
        assertTrue(plan.showReady)
        assertFalse(plan.showLiveDistance)
        assertFalse(plan.showShotStrip)
        assertFalse(plan.showResult)
    }

    @Test fun liveHudPrioritizesRemainingDistanceAndShotStrip() {
        val plan = V124HudPlanner.plan(running = true, hasShot = true, hasResult = false, resultAgeMs = 0L)
        assertFalse(plan.showReady)
        assertTrue(plan.showLiveDistance)
        assertTrue(plan.showShotStrip)
        assertFalse(plan.showResult)
        assertTrue(plan.refreshMs <= 33L)
    }

    @Test fun resultHudKeepsResultAndShotDataVisible() {
        val plan = V124HudPlanner.plan(running = false, hasShot = true, hasResult = true, resultAgeMs = 200L)
        assertFalse(plan.showReady)
        assertFalse(plan.showLiveDistance)
        assertTrue(plan.showShotStrip)
        assertTrue(plan.showResult)
        assertEquals(33L, plan.refreshMs)
    }

    @Test fun oldResultStopsFastAnimationCadence() {
        val plan = V124HudPlanner.plan(running = false, hasShot = true, hasResult = true, resultAgeMs = 4000L)
        assertTrue(plan.showResult)
        assertTrue(plan.refreshMs >= 100L)
    }
}
