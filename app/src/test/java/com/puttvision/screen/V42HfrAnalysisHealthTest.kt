package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V42HfrAnalysisHealthTest {
    @Test fun fastMarkerlessNeedsVeryHighConfidence() {
        assertFalse(V42HfrCalibrationPolicy.canUseFastMarkerless(.899))
        assertTrue(V42HfrCalibrationPolicy.canUseFastMarkerless(.90))
    }

    @Test fun fallbackKeepsExistingSafetyThreshold() {
        assertFalse(V42HfrCalibrationPolicy.canUseFallbackMarkerless(.739))
        assertTrue(V42HfrCalibrationPolicy.canUseFallbackMarkerless(.74))
    }

    @Test fun nonFiniteConfidenceIsNeverAccepted() {
        assertFalse(V42HfrCalibrationPolicy.canUseFastMarkerless(Double.NaN))
        assertFalse(V42HfrCalibrationPolicy.canUseFallbackMarkerless(Double.POSITIVE_INFINITY))
    }

    @Test fun healthLabelCarriesLatencyAndTrackCoverage() {
        val health = V42HfrAnalysisHealth(
            calibrationMode = "MARKERLESS_FAST",
            calibrationMs = 82L,
            totalAnalysisMs = 640L,
            fps = 240,
            analyzedFrames = 96,
            ballTrackFrames = 30,
            putterTrackFrames = 24
        )
        assertTrue(health.label.contains("MARKERLESS_FAST"))
        assertTrue(health.label.contains("CAL 82ms"))
        assertTrue(health.label.contains("TOTAL 640ms"))
        assertTrue(health.label.contains("TRACK 30/24"))
    }

    @Test fun perShotClearDoesNotEraseLongSessionWindow() {
        V42HfrAnalysisHealthRuntime.resetHistory()
        repeat(5) { i ->
            V42HfrAnalysisHealthRuntime.publish(
                V42HfrAnalysisHealth(
                    calibrationMode = "QR",
                    calibrationMs = 100L + i,
                    totalAnalysisMs = 600L + i,
                    fps = 240,
                    analyzedFrames = 80,
                    ballTrackFrames = 28,
                    putterTrackFrames = 20
                )
            )
            V42HfrAnalysisHealthRuntime.clear()
            assertNull(V42HfrAnalysisHealthRuntime.latest)
        }
        assertEquals(5, V43HfrHealthWindow.summary().samples)
        V42HfrAnalysisHealthRuntime.resetHistory()
        assertEquals(0, V43HfrHealthWindow.summary().samples)
    }
}