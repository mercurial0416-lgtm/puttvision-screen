package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V78HardwarelessMemoryGuardTest {
    @Test
    fun memoryGuardSuitePassesAllSevenChecks() {
        val report = V78HardwarelessMemoryGuardSuite.run()
        assertTrue(report.reason, report.passed)
        assertEquals(V78HardwarelessMemoryGuardSuite.CHECKS_TOTAL, report.checksTotal)
        assertEquals(report.checksTotal, report.checksPassed)
    }

    @Test
    fun runtimePublishesAndClearsMemoryGuardReport() {
        V78HardwarelessMemoryGuardRuntime.clear()
        assertNull(V78HardwarelessMemoryGuardRuntime.snapshot())
        val report = V78HardwarelessMemoryGuardRuntime.run()
        assertTrue(report.passed)
        assertEquals(7, V78HardwarelessMemoryGuardRuntime.snapshot()?.checksPassed)
        V78HardwarelessMemoryGuardRuntime.clear()
        assertNull(V78HardwarelessMemoryGuardRuntime.snapshot())
    }
}
