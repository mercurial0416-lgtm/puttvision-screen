package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V74HardwarelessTrainingResumeSuiteTest {
    @Test
    fun suitePassesAllResumeFaultInjectionCases() {
        val result = V74HardwarelessTrainingResumeSuite.verify()
        assertTrue(result.reason, result.passed)
        assertEquals(7, result.checksTotal)
        assertEquals(7, result.checksPassed)
    }

    @Test
    fun runtimePublishesAndClears() {
        V74HardwarelessTrainingResumeRuntime.clear()
        assertNull(V74HardwarelessTrainingResumeRuntime.snapshot())
        val result = V74HardwarelessTrainingResumeRuntime.run()
        assertTrue(result.passed)
        assertEquals(7, V74HardwarelessTrainingResumeRuntime.snapshot()?.checksPassed)
        V74HardwarelessTrainingResumeRuntime.clear()
        assertNull(V74HardwarelessTrainingResumeRuntime.snapshot())
    }
}
