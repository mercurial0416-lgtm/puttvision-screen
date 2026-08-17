package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V72HardwarelessSelfTestDashboardTest {
    @Test fun dashboardRunsCurrentShotMatrixAndAllFailClosedSuites() {
        val result = V72HardwarelessSelfTestDashboard.run(1.35, 1.8)
        assertTrue(result.details.joinToString("\n"), result.passed)
        assertEquals(result.checksTotal, result.checksPassed)
        assertEquals(V76HardwarelessSoak.EXPECTED_CHECKS_PER_RUN, result.checksTotal)
        assertNull(result.failedStage)
        assertTrue(result.details.size >= 20)
        assertTrue(result.details.any { it.startsWith("TRAIN RESUME") })
        assertTrue(result.details.any { it.startsWith("TRAIN JOURNEY") })
        assertTrue(result.details.any { it.startsWith("HFR MEMORY") })
        assertTrue(result.details.any { it.startsWith("LIFECYCLE") })
        assertTrue(result.details.any { it.startsWith("TIMING DISTORTION") })
        assertTrue(result.details.any { it.startsWith("LIVE TRACK UI") })
        assertTrue(result.details.any { it.startsWith("LIVE PLAYBACK") })
        assertTrue(result.details.any { it.startsWith("UPDATE INTEGRITY") })
    }

    @Test fun compactLabelFitsHardwarelessHudBetterThanRawDiagnostics() {
        val result = V72HardwarelessSelfTestDashboard.run(1.1, -2.0)
        val label = result.shortLabel()
        assertTrue(label.startsWith("SELFTEST PASS"))
        assertTrue(label.contains("${result.checksPassed}/${result.checksTotal}"))
        assertTrue(label.length < 40)
        assertTrue(result.details.any { it.startsWith("LAN/TIME") })
        assertTrue(result.details.any { it.startsWith("PACKET BIND") })
        assertTrue(result.details.any { it.startsWith("TIMING DISTORTION") })
        assertTrue(result.details.any { it.startsWith("TRAIN JOURNEY") })
        assertTrue(result.details.any { it.startsWith("LIVE TRACK UI") })
        assertTrue(result.details.any { it.startsWith("LIVE PLAYBACK") })
        assertTrue(result.details.any { it.startsWith("UPDATE INTEGRITY") })
    }

    @Test fun runtimePublishesAndClearsAggregateReport() {
        V72HardwarelessSelfTestRuntime.clear()
        assertNull(V72HardwarelessSelfTestRuntime.snapshot())
        val report = V72HardwarelessSelfTestRuntime.run(1.4, 0.0)
        assertTrue(report.passed)
        val snapshot = V72HardwarelessSelfTestRuntime.snapshot()
        assertEquals(report.checksTotal, snapshot?.checksPassed)
        V72HardwarelessSelfTestRuntime.clear()
        assertNull(V72HardwarelessSelfTestRuntime.snapshot())
        assertNull(V68HardwarelessStereoRuntime.snapshot())
        assertNull(V69HardwarelessStereoGuardRuntime.snapshot())
        assertNull(V70HardwarelessTransportTimebaseRuntime.snapshot())
        assertNull(V71HardwarelessProvenanceRuntime.snapshot())
        assertNull(V74HardwarelessTrainingResumeRuntime.snapshot())
        assertNull(V78HardwarelessMemoryGuardRuntime.snapshot())
        assertNull(V79HardwarelessLifecycleChurnRuntime.snapshot())
        assertNull(V80HardwarelessTimingDistortionRuntime.snapshot())
        assertNull(V82HardwarelessTrainingJourneyRuntime.snapshot())
        assertNull(V83HardwarelessLiveTrackVisualRuntime.snapshot())
        assertNull(V84HardwarelessPlaybackRuntime.snapshot())
        assertNull(V97HardwarelessUpdateIntegrityRuntime.snapshot())
    }
}
