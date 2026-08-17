package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V111GreenReadHudPlannerTest {
    @Test fun reliableReadProducesBoundedBroadcastPlan() {
        val plan = V111GreenReadHudPlanner.plan(read(), moving = false, training = false, targetCupSpeedMps = .45)
        assertTrue(plan.show)
        assertTrue(plan.aimText.startsWith("AIM"))
        assertTrue(plan.speedText.contains("BALL"))
        assertTrue(plan.breakText.startsWith("BREAK"))
        assertTrue(plan.slopeText.contains("SIDE"))
        assertTrue(plan.confidenceText.contains("READ"))
        assertTrue(plan.confidenceFraction in 0f..1f)
        assertTrue(plan.trailAlpha in 125..235)
        assertEquals(240, plan.trailSampleCap)
        assertTrue(plan.aimAlpha in 1..255)
        assertTrue(plan.secondaryAlpha in 1..255)
        assertEquals(120L, plan.refreshMs)
    }

    @Test fun movingBallSuppressesReadPanelAndTrail() {
        val plan = V111GreenReadHudPlanner.plan(read(), moving = true, training = false, targetCupSpeedMps = .45)
        assertFalse(plan.show)
        assertEquals(0, plan.aimAlpha)
        assertEquals(0, plan.trailAlpha)
        assertEquals(0, plan.trailSampleCap)
        assertEquals(90L, plan.refreshMs)
    }

    @Test fun unreliableOrMalformedReadFailsClosed() {
        assertFalse(V111GreenReadHudPlanner.plan(read(solverReliable = false), false, false, .45).show)
        assertFalse(V111GreenReadHudPlanner.plan(read(ballSpeed = Double.NaN), false, false, .45).show)
        assertFalse(V111GreenReadHudPlanner.plan(read(aimOffsetCm = Double.POSITIVE_INFINITY), false, false, .45).show)
        assertFalse(V111GreenReadHudPlanner.plan(read(cupCount = 999.0), false, false, .45).show)
        assertFalse(V111GreenReadHudPlanner.plan(read(estimatedBreakCm = Double.NaN), false, false, .45).show)
        assertFalse(V111GreenReadHudPlanner.plan(read(sideSlopePct = 99.0), false, false, .45).show)
        assertFalse(V111GreenReadHudPlanner.plan(read(longSlopePct = Double.NEGATIVE_INFINITY), false, false, .45).show)
        assertFalse(V111GreenReadHudPlanner.plan(read(launchAngleDeg = 44.0), false, false, .45).show)
        assertFalse(V111GreenReadHudPlanner.plan(read(), false, false, Double.NaN).show)
        assertFalse(V111GreenReadHudPlanner.plan(read(), false, false, 9.0).show)
    }

    @Test fun centerAimAndBreakAvoidNoisyTinyOffsets() {
        val plan = V111GreenReadHudPlanner.plan(
            read(aimOffsetCm = .4, cupCount = .03, estimatedBreakCm = .6),
            false,
            false,
            .45
        )
        assertTrue(plan.show)
        assertEquals("AIM  CENTER", plan.aimText)
        assertEquals("BREAK  CENTER", plan.breakText)
    }

    @Test fun labelsAreWhitespaceNormalizedAndBounded() {
        val plan = V111GreenReadHudPlanner.plan(
            read(
                aimSideLabel = "  홀\n\t오른쪽 아주아주아주 긴 라벨  ",
                paceHint = "  브레이크\n\t중간   천천히   "
            ),
            false,
            false,
            .45
        )
        assertTrue(plan.show)
        assertFalse(plan.aimText.contains('\n'))
        assertFalse(plan.paceText.contains('\n'))
        assertTrue(plan.paceText.length <= 22)
        assertTrue(plan.aimText.length <= 32)
    }

    @Test fun trainingUsesLowerCadenceAndSmallerTrailBudget() {
        val normal = V111GreenReadHudPlanner.plan(read(), moving = false, training = false, targetCupSpeedMps = .45)
        val training = V111GreenReadHudPlanner.plan(read(), moving = false, training = true, targetCupSpeedMps = .45)
        assertTrue(training.show)
        assertEquals(normal.aimText, training.aimText)
        assertEquals(200L, training.refreshMs)
        assertEquals(180, training.trailSampleCap)
    }

    @Test fun lockedReadReducesIdleRefreshPressure() {
        val plan = V111GreenReadHudPlanner.plan(read(solverMissCm = 1.0), false, false, .45)
        assertTrue(plan.show)
        assertEquals(160L, plan.refreshMs)
        assertTrue(plan.confidenceText.contains("LOCKED"))
        assertTrue(plan.confidenceFraction > .8f)
    }

    @Test fun weakerReadUsesLowerTrailEmphasis() {
        val locked = V111GreenReadHudPlanner.plan(read(solverMissCm = 1.0), false, false, .45)
        val weak = V111GreenReadHudPlanner.plan(read(solverMissCm = 7.5), false, false, .45)
        assertTrue(locked.trailAlpha > weak.trailAlpha)
        assertTrue(locked.confidenceFraction > weak.confidenceFraction)
    }

    @Test fun slopeAndLaunchFormattingStayCompact() {
        val plan = V111GreenReadHudPlanner.plan(
            read(sideSlopePct = 1.8, longSlopePct = -.7, launchAngleDeg = 2.2),
            false,
            false,
            .45
        )
        assertTrue(plan.slopeText.contains("SIDE +1.8%"))
        assertTrue(plan.slopeText.contains("LONG -0.7%"))
        assertTrue(plan.slopeText.contains("LAUNCH 2.2°"))
        assertTrue(plan.slopeText.length <= 42)
    }

    private fun read(
        solverReliable: Boolean = true,
        ballSpeed: Double = 1.42,
        aimOffsetCm: Double = 11.5,
        cupCount: Double = 1.06,
        estimatedBreakCm: Double = 8.0,
        sideSlopePct: Double = 1.8,
        longSlopePct: Double = .4,
        launchAngleDeg: Double = 2.0,
        solverMissCm: Double = 2.4,
        aimSideLabel: String = "홀 오른쪽",
        paceHint: String = "브레이크 중간"
    ) = GreenRead(
        estimatedBreakCm = estimatedBreakCm,
        aimOffsetCm = aimOffsetCm,
        cupCount = cupCount,
        putterHeadCount = .8,
        aimSideLabel = aimSideLabel,
        effectiveSideSlopePct = sideSlopePct,
        effectiveLongSlopePct = longSlopePct,
        paceHint = paceHint,
        recommendedBallSpeedMps = ballSpeed,
        recommendedLaunchAngleDeg = launchAngleDeg,
        solverMissCm = solverMissCm,
        solverReliable = solverReliable,
        predictedTrail = listOf(0.0 to 0.0, .1 to 1.0)
    )
}
