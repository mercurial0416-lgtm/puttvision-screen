package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V70HardwarelessTransportTimebaseTest {
    @Test
    fun hardwarelessSuiteCoversTransportFreshnessAndSequenceGuards() {
        val result = V70HardwarelessTransportTimebase.verify()
        assertTrue(result.reason, result.passed)
        assertEquals(10, result.checksTotal)
        assertEquals(10, result.checksPassed)
    }

    @Test
    fun runtimePublishesAndClearsVisibleStatus() {
        V70HardwarelessTransportTimebaseRuntime.clear()
        assertNull(V70HardwarelessTransportTimebaseRuntime.snapshot())
        val result = V70HardwarelessTransportTimebaseRuntime.run()
        assertTrue(result.passed)
        assertTrue(V70HardwarelessTransportTimebaseRuntime.snapshot()?.shortLabel()?.startsWith("LAN/TIME PASS") == true)
        V70HardwarelessTransportTimebaseRuntime.clear()
        assertNull(V70HardwarelessTransportTimebaseRuntime.snapshot())
    }

    @Test
    fun sequenceGateStillFailsClosedOnReplay() {
        val gate = V43CompanionSequenceGate()
        assertTrue(gate.accept("cam", 7L))
        assertFalse(gate.accept("cam", 7L))
        assertFalse(gate.accept("cam", 6L))
        assertTrue(gate.accept("cam", 8L))
    }
}
