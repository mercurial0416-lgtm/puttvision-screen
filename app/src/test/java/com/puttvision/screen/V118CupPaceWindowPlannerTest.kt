package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V118CupPaceWindowPlannerTest {
    @Test fun addressShowsThreeBoundedPaceRingsWithoutFakeActiveZone() {
        val plan = V118CupPaceWindowPlanner.plan(4.0, false, 0.0, 4.0, false, V24RenderTier.HIGH)
        assertEquals(V118PaceWindowPhase.ADDRESS, plan.phase)
        assertEquals(listOf(0.15, 0.30, 0.60), plan.ringRadiiM)
        assertEquals(14, plan.segments)
        assertEquals(-1, plan.activeRingIndex)
        assertEquals(0, plan.activeRingAlpha)
        assertEquals("PACE WINDOW  15 · 30 · 60 cm", plan.zoneLabel)
        assertEquals(0, plan.leaveMarkerAlpha)
        assertTrue(plan.refreshMs >= 240L)
    }

    @Test fun slowCupApproachRaisesVisibilityAndRefreshesFaster() {
        val address = V118CupPaceWindowPlanner.plan(4.0, false, 0.0, 4.0, false, V24RenderTier.HIGH)
        val approach = V118CupPaceWindowPlanner.plan(4.0, true, 0.8, 0.7, false, V24RenderTier.HIGH)
        assertEquals(V118PaceWindowPhase.APPROACH, approach.phase)
        assertTrue(approach.ringAlpha > address.ringAlpha)
        assertTrue(approach.refreshMs < address.refreshMs)
        assertEquals(-1, approach.activeRingIndex)
        assertEquals("CUP SPEED ZONE", approach.zoneLabel)
    }

    @Test fun activeZoneStepsFromSixtyToThirtyToFifteenCentimeters() {
        val outer = V118CupPaceWindowPlanner.plan(4.0, true, 0.8, 0.55, false, V24RenderTier.HIGH)
        val middle = V118CupPaceWindowPlanner.plan(4.0, true, 0.8, 0.28, false, V24RenderTier.HIGH)
        val inner = V118CupPaceWindowPlanner.plan(4.0, true, 0.8, 0.12, false, V24RenderTier.HIGH)
        assertEquals(2, outer.activeRingIndex)
        assertEquals(1, middle.activeRingIndex)
        assertEquals(0, inner.activeRingIndex)
        assertEquals("CUP SPEED · 60 CM ZONE", outer.zoneLabel)
        assertEquals("CUP SPEED · 30 CM ZONE", middle.zoneLabel)
        assertEquals("CUP SPEED · 15 CM ZONE", inner.zoneLabel)
        assertTrue(inner.activeRingAlpha > inner.ringAlpha)
        assertTrue(inner.activeRingStrokeScale > 1f)
    }

    @Test fun exactRingBoundariesStayInsideTheirExpectedZone() {
        assertEquals(0, V118CupPaceWindowPlanner.activeRingIndex(0.15))
        assertEquals(1, V118CupPaceWindowPlanner.activeRingIndex(0.30))
        assertEquals(2, V118CupPaceWindowPlanner.activeRingIndex(0.60))
        assertEquals(-1, V118CupPaceWindowPlanner.activeRingIndex(0.60001))
    }

    @Test fun malformedZoneDistanceFailsClosed() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.01).forEach { distance ->
            assertEquals(-1, V118CupPaceWindowPlanner.activeRingIndex(distance))
        }
    }

    @Test fun fastBallHidesWindowUntilCupPaceIsRelevant() {
        val plan = V118CupPaceWindowPlanner.plan(4.0, true, 2.4, 0.7, false, V24RenderTier.HIGH)
        assertEquals(V118PaceWindowPhase.HIDDEN, plan.phase)
        assertTrue(plan.ringRadiiM.isEmpty())
        assertEquals(-1, plan.activeRingIndex)
        assertEquals(0, plan.ringAlpha)
        assertEquals(0, plan.activeRingAlpha)
    }

    @Test fun nearResultShowsLeaveMarkerAndHighlightsContainingRing() {
        val near = V118CupPaceWindowPlanner.plan(4.0, false, 0.0, 0.42, true, V24RenderTier.HIGH)
        val tapIn = V118CupPaceWindowPlanner.plan(4.0, false, 0.0, 0.11, true, V24RenderTier.HIGH)
        val far = V118CupPaceWindowPlanner.plan(4.0, false, 0.0, 1.2, true, V24RenderTier.HIGH)
        assertEquals(V118PaceWindowPhase.RESULT_NEAR, near.phase)
        assertEquals(2, near.activeRingIndex)
        assertEquals("LEAVE · 60 CM ZONE", near.zoneLabel)
        assertTrue(near.leaveMarkerAlpha > 0)
        assertEquals(0, tapIn.activeRingIndex)
        assertEquals("LEAVE · 15 CM ZONE", tapIn.zoneLabel)
        assertEquals(V118PaceWindowPhase.HIDDEN, far.phase)
        assertEquals(0, far.leaveMarkerAlpha)
    }

    @Test fun seventyFiveCentimeterResultKeepsLeaveContextWithoutInventingRing() {
        val plan = V118CupPaceWindowPlanner.plan(4.0, false, 0.0, 0.72, true, V24RenderTier.HIGH)
        assertEquals(V118PaceWindowPhase.RESULT_NEAR, plan.phase)
        assertEquals(-1, plan.activeRingIndex)
        assertEquals("LEAVE ZONE", plan.zoneLabel)
        assertTrue(plan.leaveMarkerAlpha > 0)
    }

    @Test fun fartherApproachUsesSlowerRefreshUntilBallEntersOuterRing() {
        val far = V118CupPaceWindowPlanner.plan(4.0, true, 0.8, 0.90, false, V24RenderTier.HIGH)
        val inside = V118CupPaceWindowPlanner.plan(4.0, true, 0.8, 0.55, false, V24RenderTier.HIGH)
        assertEquals(V118PaceWindowPhase.APPROACH, far.phase)
        assertEquals(V118PaceWindowPhase.APPROACH, inside.phase)
        assertTrue(far.refreshMs > inside.refreshMs)
    }

    @Test fun performanceTierReducesProjectionWorkAlphaAndActiveEmphasis() {
        val high = V118CupPaceWindowPlanner.plan(4.0, true, 0.6, 0.5, false, V24RenderTier.HIGH)
        val perf = V118CupPaceWindowPlanner.plan(4.0, true, 0.6, 0.5, false, V24RenderTier.PERFORMANCE)
        assertTrue(perf.segments < high.segments)
        assertTrue(perf.ringAlpha < high.ringAlpha)
        assertTrue(perf.activeRingAlpha < high.activeRingAlpha)
        assertTrue(perf.activeRingStrokeScale < high.activeRingStrokeScale)
        assertTrue(perf.labelAlpha < high.labelAlpha)
        assertTrue(perf.refreshMs > high.refreshMs)
    }

    @Test fun malformedTargetDistanceFailsClosed() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.1, 80.0).forEach { target ->
            val plan = V118CupPaceWindowPlanner.plan(target, false, 0.0, 0.0, false, V24RenderTier.HIGH)
            assertEquals(V118PaceWindowPhase.HIDDEN, plan.phase)
            assertTrue(plan.ringRadiiM.isEmpty())
            assertEquals(-1, plan.activeRingIndex)
            assertTrue(plan.zoneLabel.isBlank())
        }
    }

    @Test fun malformedLiveInputsDoNotAccidentallyEnableApproach() {
        val badSpeed = V118CupPaceWindowPlanner.plan(4.0, true, Double.NaN, 0.5, false, V24RenderTier.HIGH)
        val badDistance = V118CupPaceWindowPlanner.plan(4.0, true, 0.5, Double.NaN, false, V24RenderTier.HIGH)
        assertEquals(V118PaceWindowPhase.HIDDEN, badSpeed.phase)
        assertEquals(V118PaceWindowPhase.HIDDEN, badDistance.phase)
        assertEquals(-1, badSpeed.activeRingIndex)
        assertEquals(-1, badDistance.activeRingIndex)
    }
}
