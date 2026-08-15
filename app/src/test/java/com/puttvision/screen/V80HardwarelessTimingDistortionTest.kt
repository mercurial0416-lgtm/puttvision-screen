package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V80HardwarelessTimingDistortionTest {
    @Test fun productionTimingGatesHandleSyntheticDistortionFailClosed() {
        val result = V80HardwarelessTimingDistortion.verify()
        assertTrue(result.reason, result.passed)
        assertEquals(9, result.checksTotal)
        assertEquals(9, result.checksPassed)
    }

    @Test fun runtimePublishesAndClears() {
        V80HardwarelessTimingDistortionRuntime.clear()
        assertNull(V80HardwarelessTimingDistortionRuntime.snapshot())
        assertTrue(V80HardwarelessTimingDistortionRuntime.run().passed)
        assertEquals(9, V80HardwarelessTimingDistortionRuntime.snapshot()?.checksPassed)
        V80HardwarelessTimingDistortionRuntime.clear()
        assertNull(V80HardwarelessTimingDistortionRuntime.snapshot())
    }
}
