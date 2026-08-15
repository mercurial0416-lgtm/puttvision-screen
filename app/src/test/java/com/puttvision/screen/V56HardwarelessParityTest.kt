package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V56HardwarelessParityTest {
    private fun read(
        cups: Double,
        side: String,
        trail: List<Pair<Double, Double>>
    ) = GreenRead(
        estimatedBreakCm = 12.0,
        aimOffsetCm = 12.0,
        cupCount = cups,
        putterHeadCount = .8,
        aimSideLabel = side,
        effectiveSideSlopePct = 1.5,
        effectiveLongSlopePct = 0.0,
        paceHint = "브레이크 중간",
        recommendedBallSpeedMps = 1.42,
        recommendedLaunchAngleDeg = 1.2,
        solverMissCm = 1.0,
        solverReliable = true,
        predictedTrail = trail
    )

    @Test fun hardwarelessPresentationUsesProductionCupAndPaceFacts() {
        val presentation = V56GreenReadPresentationBuilder.from(
            read(1.25, "홀 오른쪽", listOf(0.0 to 0.0, .03 to 1.0, 0.0 to 2.0)),
            targetCupSpeedMps = .55
        )
        assertEquals("홀 오른쪽 1.3컵", presentation.aimText)
        assertEquals("브레이크 중간", presentation.paceText)
        assertEquals(1.42, presentation.recommendedBallSpeedMps, 1e-9)
        assertEquals(.55, presentation.targetCupSpeedMps, 1e-9)
        assertEquals(1, presentation.apexIndex)
    }

    @Test fun centerReadDoesNotInventCupOffset() {
        val presentation = V56GreenReadPresentationBuilder.from(
            read(.01, "센터", listOf(0.0 to 0.0, 0.0 to 1.0, 0.0 to 2.0)),
            targetCupSpeedMps = .55
        )
        assertEquals("센터", presentation.aimText)
        assertNull(presentation.apexIndex)
    }

    @Test fun apexFinderRejectsInvisibleNoiseButKeepsRealBreak() {
        assertNull(V56GreenReadPresentationBuilder.apexIndex(
            listOf(0.0 to 0.0, .001 to 1.0, 0.0 to 2.0)
        ))
        val index = V56GreenReadPresentationBuilder.apexIndex(
            listOf(0.0 to 0.0, .02 to .6, .05 to 1.2, .03 to 1.7, 0.0 to 2.0)
        )
        assertEquals(2, index)
        assertTrue(index != null)
    }
}
