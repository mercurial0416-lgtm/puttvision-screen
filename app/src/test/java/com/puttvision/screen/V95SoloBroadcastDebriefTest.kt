package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V95SoloBroadcastDebriefTest {
    private val settings = GreenSettings(stimpMeters = 2.8, holeDistanceM = 5.0, sideSlopePct = 1.2, longSlopePct = -0.8)

    @Test fun addressUsesLowRefreshRateAndGreenContext() {
        val plan = V95SoloDebriefPlanner.plan(settings, null, null)
        assertEquals(V95ShotPhase.ADDRESS, plan.phase)
        assertEquals("READY", plan.headline)
        assertEquals(240L, plan.refreshMs)
        assertEquals(2.8, plan.stimpM, 0.0001)
        assertEquals(1.2, plan.sideSlopePct, 0.0001)
        assertEquals(-0.8, plan.longSlopePct, 0.0001)
        assertEquals(5.0, plan.targetDistanceM, 0.0001)
        assertEquals(0f, plan.progress01, 0.0001f)
        assertEquals(0, plan.resultQualityScore)
        assertEquals("D", plan.resultQualityGrade)
    }

    @Test fun rollingReportsLiveSpeedDistanceAndFastRefresh() {
        val state = SimState(x = .12, y = 2.0, vx = .3, vy = .4, running = true, trail = mutableListOf(0.0 to 0.0, .1 to 1.0))
        val plan = V95SoloDebriefPlanner.plan(settings, state, null)
        assertEquals(V95ShotPhase.ROLL, plan.phase)
        assertEquals("ROLLING", plan.headline)
        assertEquals(.5, plan.speedMps, 0.0001)
        assertTrue(plan.distanceToCupM > 3.0)
        assertTrue(plan.progress01 in 0f..1f)
        assertEquals(2, plan.trailSamples)
        assertEquals(33L, plan.refreshMs)
    }

    @Test fun closeBallEntersCupApproachWindow() {
        val state = SimState(x = .02, y = 4.5, vx = .05, vy = .18, running = true)
        val plan = V95SoloDebriefPlanner.plan(settings, state, null)
        assertEquals(V95ShotPhase.CUP_APPROACH, plan.phase)
        assertEquals("CUP APPROACH", plan.headline)
        assertEquals(33L, plan.refreshMs)
    }

    @Test fun holedResultWinsOverAllOtherLabels() {
        val result = SimResult(holed = true, finishX = 0.0, finishY = 5.0, distanceToCupM = 0.0, elapsedSec = 2.3, cupContacts = 1)
        val plan = V95SoloDebriefPlanner.plan(settings, null, result)
        assertEquals(V95ShotPhase.RESULT, plan.phase)
        assertEquals("HOLED", plan.headline)
        assertEquals(1f, plan.progress01, 0.0001f)
        assertEquals(1, plan.cupContacts)
        assertEquals(100, plan.resultQualityScore)
        assertEquals("S", plan.resultQualityGrade)
        assertEquals(120L, plan.refreshMs)
    }

    @Test fun lipOutIsNeverCollapsedIntoGenericMiss() {
        val result = SimResult(holed = false, finishX = .2, finishY = 5.1, distanceToCupM = .22, elapsedSec = 2.0, lipOut = true, cupContacts = 2)
        val plan = V95SoloDebriefPlanner.plan(settings, null, result)
        assertEquals("LIP OUT", plan.headline)
        assertEquals(2, plan.cupContacts)
        assertEquals(83, plan.resultQualityScore)
        assertEquals("A", plan.resultQualityGrade)
    }

    @Test fun resultClassifiesShortLongLeftRightByDominantLeaveAxis() {
        assertEquals("SHORT", V95SoloDebriefPlanner.plan(settings, null,
            SimResult(false, .02, 4.6, .40, 2.0)).headline)
        assertEquals("LONG", V95SoloDebriefPlanner.plan(settings, null,
            SimResult(false, .02, 5.4, .40, 2.0)).headline)
        assertEquals("LEFT", V95SoloDebriefPlanner.plan(settings, null,
            SimResult(false, -.4, 5.05, .40, 2.0)).headline)
        assertEquals("RIGHT", V95SoloDebriefPlanner.plan(settings, null,
            SimResult(false, .4, 5.05, .40, 2.0)).headline)
    }

    @Test fun nearMissGetsTapInLabelBeforeAxisClassification() {
        val result = SimResult(false, .05, 4.93, .09, 2.0)
        val plan = V95SoloDebriefPlanner.plan(settings, null, result)
        assertEquals("TAP-IN", plan.headline)
        assertEquals(92, plan.resultQualityScore)
        assertEquals("A+", plan.resultQualityGrade)
    }

    @Test fun resultQualityIsOutcomeOnlyBoundedAndMonotonicByLeave() {
        val close = V114SoloResultQuality.score(SimResult(false, 0.0, 5.0, .08, 2.0), .08)
        val medium = V114SoloResultQuality.score(SimResult(false, 0.0, 5.0, .50, 2.0), .50)
        val far = V114SoloResultQuality.score(SimResult(false, 0.0, 5.0, 2.0, 2.0), 2.0)
        assertTrue(close > medium)
        assertTrue(medium > far)
        assertTrue(close in 0..99)
        assertTrue(far in 0..99)
        assertEquals(100, V114SoloResultQuality.score(SimResult(true, 0.0, 5.0, 0.0, 2.0), 0.0))
        assertEquals(0, V114SoloResultQuality.score(SimResult(false, 0.0, 5.0, Double.NaN, 2.0), Double.NaN))
    }

    @Test fun presentationCountersAndEnvironmentValuesStayBounded() {
        val state = SimState(running = true, cupContacts = 999, trail = MutableList(900) { it.toDouble() to 0.0 })
        val plan = V95SoloDebriefPlanner.plan(
            GreenSettings(stimpMeters = 1000.0, holeDistanceM = 5.0, sideSlopePct = 500.0, longSlopePct = -500.0),
            state,
            null
        )
        assertEquals(99, plan.cupContacts)
        assertEquals(500, plan.trailSamples)
        assertEquals(9.9, plan.stimpM, 0.0001)
        assertEquals(99.0, plan.sideSlopePct, 0.0001)
        assertEquals(-99.0, plan.longSlopePct, 0.0001)
    }

    @Test fun nonFiniteRuntimeValuesFailSafeToFiniteNeutralPlan() {
        val badSettings = GreenSettings(
            stimpMeters = Double.NaN,
            holeDistanceM = Double.NaN,
            sideSlopePct = Double.POSITIVE_INFINITY,
            longSlopePct = Double.NaN
        )
        val state = SimState(
            x = Double.NaN,
            y = Double.NaN,
            vx = Double.NaN,
            vy = Double.POSITIVE_INFINITY,
            running = true
        )
        val plan = V95SoloDebriefPlanner.plan(badSettings, state, null)
        assertTrue(plan.speedMps.isFinite())
        assertTrue(plan.targetDistanceM.isFinite())
        assertTrue(plan.distanceToCupM.isFinite())
        assertTrue(plan.progress01.isFinite())
        assertTrue(plan.progress01 in 0f..1f)
        assertTrue(plan.lateralCm.isFinite())
        assertTrue(plan.longitudinalCm.isFinite())
        assertTrue(plan.stimpM.isFinite())
        assertTrue(plan.sideSlopePct.isFinite())
        assertTrue(plan.longSlopePct.isFinite())
        assertTrue(plan.resultQualityScore in 0..100)
    }

    @Test fun zeroTargetNeverProducesInvalidProgressGeometry() {
        val planAtCup = V95SoloDebriefPlanner.plan(
            GreenSettings(holeDistanceM = 0.0),
            SimState(x = 0.0, y = 0.0, running = false),
            null
        )
        assertEquals(1f, planAtCup.progress01, 0.0001f)

        val planAway = V95SoloDebriefPlanner.plan(
            GreenSettings(holeDistanceM = 0.0),
            SimState(x = .2, y = 0.0, running = true),
            null
        )
        assertEquals(0f, planAway.progress01, 0.0001f)
    }
}
