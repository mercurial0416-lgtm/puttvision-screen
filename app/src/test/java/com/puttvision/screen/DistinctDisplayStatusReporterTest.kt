package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Test

class DistinctDisplayStatusReporterTest {
    @Test
    fun identicalStatusIsReportedOnlyOnce() {
        val events = mutableListOf<Pair<Boolean, String>>()
        val reporter = DistinctDisplayStatusReporter { connected, message ->
            events += connected to message
        }

        reporter.report(true, "TV 연결됨 · HDMI · UNITY READY")
        reporter.report(true, "TV 연결됨 · HDMI · UNITY READY")
        reporter.report(true, "TV 연결됨 · HDMI · UNITY READY")

        assertEquals(listOf(true to "TV 연결됨 · HDMI · UNITY READY"), events)
    }

    @Test
    fun connectionOrMessageChangeStillPropagates() {
        val events = mutableListOf<Pair<Boolean, String>>()
        val reporter = DistinctDisplayStatusReporter { connected, message ->
            events += connected to message
        }

        reporter.report(true, "UNITY STARTING")
        reporter.report(true, "UNITY READY")
        reporter.report(false, "외부 TV 미검출")

        assertEquals(
            listOf(
                true to "UNITY STARTING",
                true to "UNITY READY",
                false to "외부 TV 미검출"
            ),
            events
        )
    }

    @Test
    fun resetAllowsSameStatusAfterControllerRestart() {
        val events = mutableListOf<Pair<Boolean, String>>()
        val reporter = DistinctDisplayStatusReporter { connected, message ->
            events += connected to message
        }

        reporter.report(false, "외부 TV 미검출")
        reporter.report(false, "외부 TV 미검출")
        reporter.reset()
        reporter.report(false, "외부 TV 미검출")

        assertEquals(
            listOf(
                false to "외부 TV 미검출",
                false to "외부 TV 미검출"
            ),
            events
        )
    }

    @Test
    fun failedCallbackDoesNotSuppressRetryOfSameStatus() {
        var attempts = 0
        val events = mutableListOf<Pair<Boolean, String>>()
        val reporter = DistinctDisplayStatusReporter { connected, message ->
            attempts++
            if (attempts == 1) throw IllegalStateException("HUD callback failed")
            events += connected to message
        }

        assertFailsWith<IllegalStateException> {
            reporter.report(true, "TV 연결됨 · HDMI · UNITY READY")
        }
        reporter.report(true, "TV 연결됨 · HDMI · UNITY READY")
        reporter.report(true, "TV 연결됨 · HDMI · UNITY READY")

        assertEquals(2, attempts)
        assertEquals(listOf(true to "TV 연결됨 · HDMI · UNITY READY"), events)
    }
}
