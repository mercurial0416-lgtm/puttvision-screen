package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V79HardwarelessLifecycleChurnTest {
    @Test fun clearThenRerunRepopulatesEveryStatefulHardwarelessRuntime() {
        val report = V79HardwarelessLifecycleChurnRuntime.run()
        assertTrue(report.reason, report.passed)
        assertEquals(7, report.checksTotal)
        assertEquals(7, report.checksPassed)
        assertTrue(report.reason.contains("clean"))
    }

    @Test fun runtimeClearDropsOnlyItsOwnAggregateSnapshot() {
        val report = V79HardwarelessLifecycleChurnRuntime.run()
        assertTrue(report.passed)
        assertTrue(V79HardwarelessLifecycleChurnRuntime.snapshot() != null)
        V79HardwarelessLifecycleChurnRuntime.clear()
        assertNull(V79HardwarelessLifecycleChurnRuntime.snapshot())
        assertTrue(V68HardwarelessStereoRuntime.snapshot() != null)
        assertTrue(V78HardwarelessMemoryGuardRuntime.snapshot() != null)
    }
}
