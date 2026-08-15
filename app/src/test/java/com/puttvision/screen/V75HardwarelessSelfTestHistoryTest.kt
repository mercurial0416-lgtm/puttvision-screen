package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V75HardwarelessSelfTestHistoryTest {
    private fun report(passed: Boolean, stage: String? = null) = V72HardwarelessSelfTestReport(
        passed = passed,
        checksPassed = if (passed) 41 else 40,
        checksTotal = 41,
        failedStage = stage,
        details = emptyList()
    )

    @Test fun cleanHistoryHasNoWarning() {
        V75HardwarelessSelfTestHistoryRuntime.reset()
        repeat(4) { V75HardwarelessSelfTestHistoryRuntime.record(report(true), 1_000L + it) }
        val summary = V75HardwarelessSelfTestHistoryRuntime.summary()
        assertEquals(4, summary.samples)
        assertEquals(0, summary.failures)
        assertEquals(4, summary.consecutivePasses)
        assertNull(summary.warningLabel())
    }

    @Test fun oneShotFailureRemainsLatchedAcrossLaterPasses() {
        V75HardwarelessSelfTestHistoryRuntime.reset()
        V75HardwarelessSelfTestHistoryRuntime.record(report(false, "TRAIN RESUME"), 1_000L)
        repeat(3) { V75HardwarelessSelfTestHistoryRuntime.record(report(true), 1_100L + it) }
        val summary = V75HardwarelessSelfTestHistoryRuntime.summary()
        assertEquals(4, summary.samples)
        assertEquals(1, summary.failures)
        assertEquals(3, summary.consecutivePasses)
        assertEquals("TRAIN RESUME", summary.lastFailureStage)
        assertEquals(3, summary.lastFailureSamplesAgo)
        assertTrue(summary.warningLabel()?.contains("TRAIN RESUME") == true)
    }

    @Test fun historyIsBoundedAndOldFailureAgesOut() {
        V75HardwarelessSelfTestHistoryRuntime.reset()
        V75HardwarelessSelfTestHistoryRuntime.record(report(false, "LAN/TIME"), 1L)
        repeat(V75HardwarelessSelfTestHistoryRuntime.MAX_SAMPLES) {
            V75HardwarelessSelfTestHistoryRuntime.record(report(true), 2L + it)
        }
        val summary = V75HardwarelessSelfTestHistoryRuntime.summary()
        assertEquals(V75HardwarelessSelfTestHistoryRuntime.MAX_SAMPLES, summary.samples)
        assertEquals(V75HardwarelessSelfTestHistoryRuntime.MAX_SAMPLES, summary.consecutivePasses)
        assertEquals(0, summary.failures)
        assertNull(summary.lastFailureStage)
        assertNull(summary.warningLabel())
        assertEquals(V75HardwarelessSelfTestHistoryRuntime.MAX_SAMPLES, V75HardwarelessSelfTestHistoryRuntime.sizeForTest())
    }

    @Test fun resetClearsLatchedFailure() {
        V75HardwarelessSelfTestHistoryRuntime.reset()
        V75HardwarelessSelfTestHistoryRuntime.record(report(false, "STEREO CURRENT"), 1L)
        assertTrue(V75HardwarelessSelfTestHistoryRuntime.summary().warningLabel() != null)
        V75HardwarelessSelfTestHistoryRuntime.reset()
        assertEquals(0, V75HardwarelessSelfTestHistoryRuntime.summary().samples)
    }
}
