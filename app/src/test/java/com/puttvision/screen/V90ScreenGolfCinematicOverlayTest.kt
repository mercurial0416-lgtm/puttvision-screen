package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V90ScreenGolfCinematicOverlayTest {
    @Test
    fun addressIsCalmAndNeutral() {
        val p = V90CinematicPlanner.plan(false, 0.0, 0.0, null, 0L)
        assertEquals(V90CinematicPhase.ADDRESS, p.phase)
        assertEquals(0f, p.speedLines, 1e-6f)
        assertEquals(0f, p.resultPulse, 1e-6f)
    }

    @Test
    fun rollingBallGetsSpeedDrivenCinematicMotion() {
        val slow = V90CinematicPlanner.plan(true, .25, .35, null, 0L)
        val fast = V90CinematicPlanner.plan(true, .25, 1.8, null, 0L)
        assertEquals(V90CinematicPhase.ROLL, slow.phase)
        assertEquals(V90CinematicPhase.ROLL, fast.phase)
        assertTrue(fast.speedLines > slow.speedLines)
    }

    @Test
    fun lateRollTransitionsToCupApproach() {
        val p = V90CinematicPlanner.plan(true, .82, .8, null, 0L)
        assertEquals(V90CinematicPhase.CUP_APPROACH, p.phase)
        assertTrue(p.cupFocus > .5f)
        assertTrue(p.letterbox > .05f)
    }

    @Test
    fun holedAndLipOutGetDistinctResultPhases() {
        val holed = SimResult(true, 0.0, 5.0, 0.0, 2.0, false, 0)
        val lip = SimResult(false, .05, 5.0, .05, 2.0, true, 1)
        val hp = V90CinematicPlanner.plan(false, 1.0, 0.0, holed, 100L)
        val lp = V90CinematicPlanner.plan(false, 1.0, 0.0, lip, 100L)
        assertEquals(V90CinematicPhase.HOLED, hp.phase)
        assertEquals(V90CinematicPhase.LIP_OUT, lp.phase)
        assertTrue(hp.resultPulse > 0f)
        assertTrue(lp.resultPulse > 0f)
        assertTrue(hp.cupFocus > lp.cupFocus)
    }

    @Test
    fun resultPulseDecaysAndInvalidInputsFailSafe() {
        val result = SimResult(false, .2, 4.8, .2, 2.0, false, 0)
        val early = V90CinematicPlanner.plan(false, Double.NaN, Double.NaN, result, 50L)
        val late = V90CinematicPlanner.plan(false, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, result, 1700L)
        assertEquals(V90CinematicPhase.RESULT, early.phase)
        assertTrue(early.resultPulse > late.resultPulse)
        assertTrue(early.letterbox.isFinite())
        assertTrue(early.cupFocus.isFinite())
        assertTrue(early.speedLines.isFinite())
    }
}
