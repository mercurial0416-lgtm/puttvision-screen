package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V69HardwarelessStereoGuardSuiteTest {
    @Test fun allInjectedCaptureFaultsFailClosed() {
        val result = V69HardwarelessStereoGuardSuite.verify()
        assertTrue(result.reason, result.passed)
        assertEquals(result.checksTotal, result.checksPassed)
        assertEquals(6, result.checksTotal)
        assertTrue(result.shortLabel().contains("STEREO GUARDS PASS"))
    }

    @Test fun runtimePublishesAndClearsGuardStatus() {
        V69HardwarelessStereoGuardRuntime.clear()
        assertNull(V69HardwarelessStereoGuardRuntime.snapshot())
        val result = V69HardwarelessStereoGuardRuntime.run()
        assertTrue(result.passed)
        assertNotNull(V69HardwarelessStereoGuardRuntime.snapshot())
        V69HardwarelessStereoGuardRuntime.clear()
        assertNull(V69HardwarelessStereoGuardRuntime.snapshot())
    }
}
