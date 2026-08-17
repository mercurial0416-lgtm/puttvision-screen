package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V122CourseWorldTest {
    @Test fun highTierIsMateriallyRicherThanPerformance() {
        val high = V122WorldDetailPlanner.plan(V24RenderTier.HIGH, 5.0)
        val perf = V122WorldDetailPlanner.plan(V24RenderTier.PERFORMANCE, 5.0)
        assertTrue(high.greenCols > perf.greenCols)
        assertTrue(high.greenRows > perf.greenRows)
        assertTrue(high.treeCount > perf.treeCount)
        assertTrue(high.hillSegments > perf.hillSegments)
        assertTrue(high.fogFarM >= perf.fogFarM)
    }

    @Test fun malformedDistanceFallsBackToSafeFiniteBudget() {
        val plan = V122WorldDetailPlanner.plan(V24RenderTier.BALANCED, Double.NaN)
        assertEquals(28, plan.greenCols)
        assertTrue(plan.greenRows in 40..100)
        assertTrue(plan.treeCount in 0..26)
        assertTrue(plan.fogFarM in 18f..52f)
        assertTrue(plan.idleFrameMs >= 60L)
    }

    @Test fun longCourseKeepsWorldBudgetBounded() {
        val plan = V122WorldDetailPlanner.plan(V24RenderTier.HIGH, 500.0)
        assertTrue(plan.greenCols <= 40)
        assertTrue(plan.greenRows <= 110)
        assertTrue(plan.treeCount <= 26)
        assertTrue(plan.hillSegments <= 40)
        assertTrue(plan.fogFarM <= 52f)
    }

    @Test fun performanceMovingCadenceRespectsExistingThermalTier() {
        val plan = V122WorldDetailPlanner.plan(V24RenderTier.PERFORMANCE, 8.0)
        assertEquals(V24RenderTier.PERFORMANCE.movingFrameMs, plan.movingFrameMs)
        assertTrue(plan.idleFrameMs >= V24RenderTier.PERFORMANCE.idleFrameMs)
    }
}
