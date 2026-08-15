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
        assertEquals(34, result.checksTotal)
        assertEquals(34, result.checksPassed)
        assertNull(result.failedStage)
        assertEquals(13, result.details.size)
    }

    @Test
    fun compactLabelFitsHardwarelessHudBetterThanRawDiagnostics() {
        val result = V72HardwarelessSelfTestDashboard.run(1.1, -2.0)
        val label = result.shortLabel()
        assertTrue(label.startsWith("SELFTEST PASS"))
        assertTrue(label.length < 40)
        assertTrue(result.details.any { it.startsWith("LAN/TIME") })
        assertTrue(result.details.any { it.startsWith("PACKET BIND") })
    }

    @Test
    fun runtimePublishesAndClearsAggregateReport() {
        V72HardwarelessSelfTestRuntime.clear()
        assertNull(V72HardwarelessSelfTestRuntime.snapshot())
        val report = V72HardwarelessSelfTestRuntime.run(1.4, 0.0)
        assertTrue(report.passed)
        assertEquals(34, V72HardwarelessSelfTestRuntime.snapshot()?.checksPassed)
        V72HardwarelessSelfTestRuntime.clear()
        assertNull(V72HardwarelessSelfTestRuntime.snapshot())
        assertNull(V68HardwarelessStereoRuntime.snapshot())
        assertNull(V69HardwarelessStereoGuardRuntime.snapshot())
        assertNull(V70HardwarelessTransportTimebaseRuntime.snapshot())
        assertNull(V71HardwarelessProvenanceRuntime.snapshot())
    }
}
