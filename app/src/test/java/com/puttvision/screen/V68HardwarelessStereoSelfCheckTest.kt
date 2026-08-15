package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V68HardwarelessStereoSelfCheckTest {
    @Test fun centerShotTraversesProductionStereoPath() {
        val result = V68HardwarelessStereoSelfCheck.verify(1.25, 0.0)
        assertTrue(result.reason, result.passed)
        assertTrue(result.sampleCount >= 5)
        assertNotNull(result.reconstructedSpeedMps)
        assertNotNull(result.reconstructedDirectionDeg)
        assertTrue(requireNotNull(result.speedErrorMps) <= 0.005)
        assertTrue(requireNotNull(result.directionErrorDeg) <= 0.05)
    }

    @Test fun pushAndPullDirectionsRoundTrip() {
        val push = V68HardwarelessStereoSelfCheck.verify(1.42, 2.25)
        val pull = V68HardwarelessStereoSelfCheck.verify(1.08, -2.10)
        assertTrue(push.reason, push.passed)
        assertTrue(pull.reason, pull.passed)
    }

    @Test fun invalidSyntheticTruthFailsClosed() {
        val result = V68HardwarelessStereoSelfCheck.verify(Double.NaN, 0.0)
        assertFalse(result.passed)
        assertTrue(result.sampleCount == 0)
    }

    @Test fun hardwarelessRuntimePublishesAndClearsVisibleStatus() {
        V68HardwarelessStereoRuntime.clear()
        assertTrue(V68HardwarelessStereoRuntime.snapshot() == null)
        val result = V68HardwarelessStereoRuntime.run(1.30, 1.2)
        assertTrue(result.passed)
        assertNotNull(V68HardwarelessStereoRuntime.snapshot())
        assertTrue(requireNotNull(V68HardwarelessStereoRuntime.snapshot()).shortLabel().contains("STEREO PIPE PASS"))
        V68HardwarelessStereoRuntime.clear()
        assertTrue(V68HardwarelessStereoRuntime.snapshot() == null)
    }
}
