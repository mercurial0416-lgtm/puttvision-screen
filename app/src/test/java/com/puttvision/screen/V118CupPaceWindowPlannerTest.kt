package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V118CupPaceWindowPlannerTest {
    @Test fun addressShowsThreeBoundedPaceRings() {
        val plan = V118CupPaceWindowPlanner.plan(4.0, false, 0.0, 4.0, false, V24RenderTier.HIGH)
        assertEquals(V118PaceWindowPhase.ADDRESS, plan.phase)
        assertEquals(listOf(0.15, 0.30, 0.60), plan.ringRadiiM)
        assertEquals(14, plan.segments)
        assertEquals(0, plan.leaveMarkerAlpha)
        assertTrue(plan.refreshMs >= 240L)
    }

    @Test fun slowCupApproachRaisesVisibilityAndRefreshesFaster() {
        val address = V118CupPaceWindowPlanner.plan(4.0, false, 0.0, 4.0, false, V24RenderTier.HIGH)
        val approach = V118CupPaceWindowPlanner.plan(4.0, true, 0.8, 0.7, false, V24RenderTier.HIGH)
        assertEquals(V118PaceWindowPhase.APPROACH, approach.phase)
        assertTrue(approach.ringAlpha > address.ringAlpha)
        assertTrue(approach.refreshMs < address.refreshMs)
    }

    @Test fun fastBallHidesWindowUntilCupPaceIsRelevant() {
        val plan = V118CupPaceWindowPlanner.plan(4.0, true, 2.4, 0.7, false, V24RenderTier.HIGH)
        assertEquals(V118PaceWindowPhase.HIDDEN, plan.phase)
        assertTrue(plan.ringRadiiM.isEmpty())
        assertEquals(0, plan.ringAlpha)
    }

    @Test fun nearResultShowsLeaveMarkerButFarResultStaysHidden() {
        val near = V118CupPaceWindowPlanner.plan(4.0, false, 0.0, 0.42, true, V24RenderTier.HIGH)
        val far = V118CupPaceWindowPlanner.plan(4.0, false, 0.0, 1.2, true, V24RenderTier.HIGH)
        assertEquals(V118PaceWindowPhase.RESULT_NEAR, near.phase)
        assertTrue(near.leaveMarkerAlpha > 0)
        assertEquals(V118PaceWindowPhase.HIDDEN, far.phase)
        assertEquals(0, far.leaveMarkerAlpha)
    }

    @Test fun performanceTierReducesProjectionWorkAndAlpha() {
        val high = V118CupPaceWindowPlanner.plan(4.0, true, 0.6, 0.5, false, V24RenderTier.HIGH)
        val perf = V118CupPaceWindowPlanner.plan(4.0, true, 0.6, 0.5, false, V24RenderTier.PERFORMANCE)
        assertTrue(perf.segments < high.segments)
        assertTrue(perf.ringAlpha < high.ringAlpha)
        assertTrue(perf.labelAlpha < high.labelAlpha)
        assertTrue(perf.refreshMs > high.refreshMs)
    }

    @Test fun malformedTargetDistanceFailsClosed() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.1, 80.0).forEach { target ->
            val plan = V118CupPaceWindowPlanner.plan(target, false, 0.0, 0.0, false, V24RenderTier.HIGH)
            assertEquals(V118PaceWindowPhase.HIDDEN, plan.phase)
            assertTrue(plan.ringRadiiM.isEmpty())
        }
    }

    @Test fun malformedLiveInputsDoNotAccidentallyEnableApproach() {
        val badSpeed = V118CupPaceWindowPlanner.plan(4.0, true, Double.NaN, 0.5, false, V24RenderTier.HIGH)
        val badDistance = V118CupPaceWindowPlanner.plan(4.0, true, 0.5, Double.NaN, false, V24RenderTier.HIGH)
        assertEquals(V118PaceWindowPhase.HIDDEN, badSpeed.phase)
        assertEquals(V118PaceWindowPhase.HIDDEN, badDistance.phase)
    }
}
