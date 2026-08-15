package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V72HardwarelessSelfTestDashboardTest {
    @Test
    fun dashboardRunsCurrentShotMatrixAndAllFailClosedSuites() {
        val result = V72HardwarelessSelfTestDashboard.run(1.35, 1.8)
        assertTrue(result.details.joinToString("\n"), result.passed)
        assertEquals(48, result.checksTotal)
        assertEquals(48, result.checksPassed)
        assertNull(result.failedStage)
        assertEquals(15, result.details.size)
        assertTrue(result.details.any { it.startsWith("TRAIN RESUME") })
        assertTrue(result.details.any { it.startsWith("HFR MEMORY") })
    }

    @Test
    fun compactLabelFitsHardwarelessHudBetterThanRawDiagnostics() {
        val result = V72HardwarelessSelfTestDashboard.run(1.1, -2.0)
        val label = result.shortLabel()
        assertTrue(label.startsWith("SELFTEST PASS"))
        assertTrue(label.contains("48/48"))
        assertTrue(label.length < 40)
        assertTrue(result.details.any { it.startsWith("LAN/TIME") })
        assertTrue(result.details.any { it.startsWith("PACKET BIND") })
        assertTrue(result.details.any { it.startsWith("TRAIN RESUME") })
        assertTrue(result.details.any { it.startsWith("HFR MEMORY") })
    }

    @Test
    fun runtimePublishesAndClearsAggregateReport() {
        V72HardwarelessSelfTestRuntime.clear()
        assertNull(V72HardwarelessSelfTestRuntime.snapshot())
        val report = V72HardwarelessSelfTestRuntime.run(1.4, 0.0)
        assertTrue(report.passed)
        assertEquals(48, V72HardwarelessSelfTestRuntime.snapshot()?.checksPassed)
        V72HardwarelessSelfTestRuntime.clear()
        assertNull(V72HardwarelessSelfTestRuntime.snapshot())
        assertNull(V68HardwarelessStereoRuntime.snapshot())
        assertNull(V69HardwarelessStereoGuardRuntime.snapshot())
        assertNull(V70HardwarelessTransportTimebaseRuntime.snapshot())
        assertNull(V71HardwarelessProvenanceRuntime.snapshot())
        assertNull(V74HardwarelessTrainingResumeRuntime.snapshot())
        assertNull(V78HardwarelessMemoryGuardRuntime.snapshot())
    }
}
