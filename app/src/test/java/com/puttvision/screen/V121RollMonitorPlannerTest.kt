package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V121RollMonitorPlannerTest {
    @Test fun idleStateDoesNotRenderMonitor() {
        assertNull(
            V121RollMonitorPlanner.plan(
                running = false,
                holeDistanceM = 5.0,
                ballX = 0.0,
                ballY = 0.0,
                vx = 0.0,
                vy = 0.0,
                tier = V24RenderTier.HIGH
            )
        )
    }

    @Test fun normalRollReportsBoundedProgressAndPace() {
        val plan = V121RollMonitorPlanner.plan(true, 5.0, .12, 2.5, .05, .72, V24RenderTier.HIGH)!!
        assertEquals(V121PaceBand.ON_PACE, plan.paceBand)
        assertTrue(plan.progress01 in 0.0..1.0)
        assertTrue(plan.speed01 in 0.0..1.0)
        assertTrue(plan.remaining01 in 0.0..1.0)
        assertTrue(plan.lateralLabel.startsWith("RIGHT"))
    }

    @Test fun cupWindowWinsNearCupAtControlledSpeed() {
        val plan = V121RollMonitorPlanner.plan(true, 5.0, .03, 4.55, .02, .55, V24RenderTier.BALANCED)!!
        assertEquals(V121PaceBand.CUP_WINDOW, plan.paceBand)
        assertEquals("CUP WINDOW", plan.paceLabel)
        assertTrue(plan.remainingM <= .75)
    }

    @Test fun hotAndDyingBandsStayDeterministic() {
        val hot = V121RollMonitorPlanner.plan(true, 5.0, 0.0, 2.0, 0.0, 2.4, V24RenderTier.HIGH)!!
        val dying = V121RollMonitorPlanner.plan(true, 5.0, 0.0, 2.0, 0.0, .10, V24RenderTier.HIGH)!!
        assertEquals(V121PaceBand.HOT, hot.paceBand)
        assertEquals(V121PaceBand.DYING, dying.paceBand)
    }

    @Test fun centerNoiseBelowOneCentimeterIsCollapsed() {
        val plan = V121RollMonitorPlanner.plan(true, 5.0, .009, 2.0, 0.0, .5, V24RenderTier.HIGH)!!
        assertEquals("CENTER", plan.lateralLabel)
    }

    @Test fun malformedCoordinatesAndTargetFailClosed() {
        assertNull(V121RollMonitorPlanner.plan(true, Double.NaN, 0.0, 0.0, 0.0, .5, V24RenderTier.HIGH))
        assertNull(V121RollMonitorPlanner.plan(true, 5.0, Double.NaN, 0.0, 0.0, .5, V24RenderTier.HIGH))
        assertNull(V121RollMonitorPlanner.plan(true, 5.0, 0.0, Double.POSITIVE_INFINITY, 0.0, .5, V24RenderTier.HIGH))
        assertNull(V121RollMonitorPlanner.plan(true, 5.0, 25.0, 2.0, 0.0, .5, V24RenderTier.HIGH))
    }

    @Test fun speedAndVisualBudgetsAreBounded() {
        val high = V121RollMonitorPlanner.plan(true, 5.0, 0.0, 1.0, 0.0, 99.0, V24RenderTier.HIGH)!!
        val perf = V121RollMonitorPlanner.plan(true, 5.0, 0.0, 1.0, 0.0, .8, V24RenderTier.PERFORMANCE)!!
        assertEquals(5.0, high.speedMps, 0.0001)
        assertTrue(high.panelAlpha in 0..255)
        assertTrue(high.accentAlpha in 0..255)
        assertTrue(perf.refreshMs in 16L..50L)
        assertTrue(perf.panelAlpha <= high.panelAlpha)
    }

    @Test fun remainingDistanceNeverEscapesDisplayBudget() {
        val plan = V121RollMonitorPlanner.plan(true, 33.0, 0.0, -2.0, 0.0, .5, V24RenderTier.HIGH)!!
        assertTrue(plan.remainingM in 0.0..40.0)
        assertTrue(plan.remaining01 in 0.0..1.0)
    }
}
