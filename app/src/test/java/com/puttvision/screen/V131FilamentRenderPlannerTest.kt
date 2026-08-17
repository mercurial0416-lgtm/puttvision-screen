package com.puttvision.screen

import org.junit.Assert.assertTrue
import org.junit.Test

class V131FilamentRenderPlannerTest {
    @Test
    fun highTierUsesDenserTerrainThanPerformance() {
        val high = V131RenderPlanner.plan(V24RenderTier.HIGH, 8.0)
        val low = V131RenderPlanner.plan(V24RenderTier.PERFORMANCE, 8.0)
        assertTrue(high.greenCols > low.greenCols)
        assertTrue(high.greenRows > low.greenRows)
        assertTrue(high.roughCols > low.roughCols)
        assertTrue(high.roughRows > low.roughRows)
    }

    @Test
    fun longCourseAddsLongitudinalResolution() {
        val short = V131RenderPlanner.plan(V24RenderTier.BALANCED, 5.0)
        val long = V131RenderPlanner.plan(V24RenderTier.BALANCED, 18.0)
        assertTrue(long.greenRows > short.greenRows)
        assertTrue(long.roughRows > short.roughRows)
    }
}
