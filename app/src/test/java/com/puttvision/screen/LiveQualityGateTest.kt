package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveQualityGateTest {
    @Test
    fun blocksBadImageEvenWhenObjectsAreVisible() {
        val bad = FrameQualitySnapshot(
            brightness = 25.0,
            contrast = 20.0,
            sharpnessScore = 30,
            motionScore = 90,
            noiseScore = 80,
            overallScore = 45,
            hint = "too dark"
        )
        val q = LiveQualityGate.build(bad, 1.0, 1.0)
        assertTrue(q.blocked)
    }

    @Test
    fun objectDetectionIsSoftSignalNotHardBlock() {
        val good = FrameQualitySnapshot(
            brightness = 120.0,
            contrast = 30.0,
            sharpnessScore = 85,
            motionScore = 90,
            noiseScore = 85,
            overallScore = 88,
            hint = "stable"
        )
        val q = LiveQualityGate.build(good, 0.0, 0.0)
        assertFalse(q.blocked)
        assertTrue(q.label.startsWith("SET"))
    }
}
