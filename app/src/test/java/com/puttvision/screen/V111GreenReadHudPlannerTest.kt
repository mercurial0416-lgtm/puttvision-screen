package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V111GreenReadHudPlannerTest {
    @Test fun reliableReadProducesBoundedBroadcastPlan() {
        val plan = V111GreenReadHudPlanner.plan(read(), moving = false, training = false)
        assertTrue(plan.show)
        assertTrue(plan.aimText.startsWith("AIM"))
        assertTrue(plan.speedText.contains("BALL"))
        assertTrue(plan.confidenceText.contains("READ"))
        assertTrue(plan.aimAlpha in 1..255)
        assertTrue(plan.secondaryAlpha in 1..255)
        assertEquals(120L, plan.refreshMs)
    }

    @Test fun movingBallSuppressesReadPanel() {
        val plan = V111GreenReadHudPlanner.plan(read(), moving = true, training = false)
        assertFalse(plan.show)
        assertEquals(0, plan.aimAlpha)
        assertEquals(90L, plan.refreshMs)
    }

    @Test fun unreliableOrMalformedReadFailsClosed() {
        assertFalse(V111GreenReadHudPlanner.plan(read(solverReliable = false), false, false).show)
        assertFalse(V111GreenReadHudPlanner.plan(read(ballSpeed = Double.NaN), false, false).show)
        assertFalse(V111GreenReadHudPlanner.plan(read(aimOffsetCm = Double.POSITIVE_INFINITY), false, false).show)
        assertFalse(V111GreenReadHudPlanner.plan(read(cupCount = 999.0), false, false).show)
    }

    @Test fun centerAimAvoidsNoisyTinyOffset() {
        val plan = V111GreenReadHudPlanner.plan(read(aimOffsetCm = .4, cupCount = .03), false, false)
        assertTrue(plan.show)
        assertEquals("AIM  CENTER", plan.aimText)
    }

    @Test fun trainingUsesLowerCadenceWithoutChangingRead() {
        val normal = V111GreenReadHudPlanner.plan(read(), moving = false, training = false)
        val training = V111GreenReadHudPlanner.plan(read(), moving = false, training = true)
        assertTrue(training.show)
        assertEquals(normal.aimText, training.aimText)
        assertEquals(180L, training.refreshMs)
    }

    @Test fun paceTextIsBoundedForTvLayout() {
        val longHint = "x".repeat(100)
        val plan = V111GreenReadHudPlanner.plan(read(paceHint = longHint), false, false)
        assertTrue(plan.paceText.length <= 24)
    }

    private fun read(
        solverReliable: Boolean = true,
        ballSpeed: Double = 1.42,
        aimOffsetCm: Double = 11.5,
        cupCount: Double = 1.06,
        paceHint: String = "브레이크 중간"
    ) = GreenRead(
        estimatedBreakCm = 8.0,
        aimOffsetCm = aimOffsetCm,
        cupCount = cupCount,
        putterHeadCount = .8,
        aimSideLabel = "홀 오른쪽",
        effectiveSideSlopePct = 1.8,
        effectiveLongSlopePct = .4,
        paceHint = paceHint,
        recommendedBallSpeedMps = ballSpeed,
        recommendedLaunchAngleDeg = 2.0,
        solverMissCm = 2.4,
        solverReliable = solverReliable,
        predictedTrail = listOf(0.0 to 0.0, .1 to 1.0)
    )
}
