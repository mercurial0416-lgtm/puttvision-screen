package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImpactReplaySamplePlannerTest {
    @Test fun neverExceedsRequestedFrameCap() {
        val plan = ImpactReplaySamplePlanner.plan(
            totalFrames = 1000,
            impactFrame = 500,
            captureFps = 240,
            maxFrames = 24
        )!!
        assertTrue(plan.sourceFrameIndices.size <= 24)
        assertTrue(500 in plan.sourceFrameIndices)
    }

    @Test fun clampsImpactIntoVideoRange() {
        val early = ImpactReplaySamplePlanner.plan(100, -30, 240, 24)!!
        val late = ImpactReplaySamplePlanner.plan(100, 999, 240, 24)!!
        assertEquals(0, early.sourceImpactFrame)
        assertEquals(99, late.sourceImpactFrame)
        assertTrue(0 in early.sourceFrameIndices)
        assertTrue(99 in late.sourceFrameIndices)
    }

    @Test fun shortWindowKeepsEveryFrameInsideComputedWindow() {
        val plan = ImpactReplaySamplePlanner.plan(8, 4, 30, 24)!!
        assertEquals((2..7).toList(), plan.sourceFrameIndices)
    }

    @Test fun invalidInputsFailClosed() {
        assertNull(ImpactReplaySamplePlanner.plan(0, 0, 240, 24))
        assertNull(ImpactReplaySamplePlanner.plan(100, 50, 0, 24))
        assertNull(ImpactReplaySamplePlanner.plan(100, 50, 240, 0))
        assertNotNull(ImpactReplaySamplePlanner.plan(100, 50, 240, 1))
    }

    @Test fun timingPolicyAcceptsCoherentHfrContainerTiming() {
        val check = ImpactReplayTimingPolicy.evaluate(
            totalFrames = 240,
            captureFps = 240,
            durationMs = 1_000L
        )
        assertTrue(check.coherent)
        assertEquals(1_000.0, check.expectedDurationMs, 0.001)
        assertEquals(0.0, check.relativeError, 0.0001)
    }

    @Test fun timingPolicyRejectsPlaybackTimelineThatDoesNotMatchCaptureFps() {
        val check = ImpactReplayTimingPolicy.evaluate(
            totalFrames = 240,
            captureFps = 240,
            durationMs = 4_000L
        )
        assertFalse(check.coherent)
        assertTrue(check.relativeError > 0.12)
    }

    @Test fun timingPolicyFailsClosedWhenDurationMetadataIsMissing() {
        val check = ImpactReplayTimingPolicy.evaluate(
            totalFrames = 240,
            captureFps = 240,
            durationMs = 0L
        )
        assertFalse(check.coherent)
        assertTrue(check.relativeError.isInfinite())
    }
}
