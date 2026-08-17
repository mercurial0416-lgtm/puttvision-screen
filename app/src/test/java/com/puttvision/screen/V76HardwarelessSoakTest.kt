package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V76HardwarelessSoakTest {
    @Test
    fun defaultSoakKeepsEverySelfTestGreenAndHistoryBounded() {
        val report = V76HardwarelessSoak.run()
        assertTrue(report.shortLabel(), report.passed)
        assertEquals(V76HardwarelessSoak.DEFAULT_RUNS, report.completedRuns)
        assertEquals(V76HardwarelessSoak.DEFAULT_RUNS, report.passedRuns)
        assertNull(report.firstFailureRun)
        assertNull(report.firstFailureStage)
        assertTrue(report.minChecksPerRun >= V76HardwarelessSoak.MIN_EXPECTED_CHECKS_PER_RUN)
        assertEquals(report.minChecksPerRun, report.maxChecksPerRun)
        assertTrue(report.maxHistorySamples <= V75HardwarelessSelfTestHistoryRuntime.MAX_SAMPLES)
        assertEquals(V75HardwarelessSelfTestHistoryRuntime.MAX_SAMPLES, report.finalHistorySamples)
        assertEquals(0, report.finalHistoryFailures)
        assertEquals(V75HardwarelessSelfTestHistoryRuntime.MAX_SAMPLES, report.finalConsecutivePasses)
    }

    @Test
    fun smallSoakReportsExactHistoryDepth() {
        val report = V76HardwarelessSoak.run(7)
        assertTrue(report.shortLabel(), report.passed)
        assertTrue(report.minChecksPerRun >= V76HardwarelessSoak.MIN_EXPECTED_CHECKS_PER_RUN)
        assertEquals(report.minChecksPerRun, report.maxChecksPerRun)
        assertEquals(7, report.finalHistorySamples)
        assertEquals(7, report.finalConsecutivePasses)
    }

    @Test(expected = IllegalArgumentException::class)
    fun soakRejectsZeroRuns() {
        V76HardwarelessSoak.run(0)
    }
}
