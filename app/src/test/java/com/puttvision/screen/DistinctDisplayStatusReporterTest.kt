package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
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

        try {
            reporter.report(true, "TV 연결됨 · HDMI · UNITY READY")
            fail("First callback should fail")
        } catch (_: IllegalStateException) {
            // Expected: a failed delivery must not be cached as successfully reported.
        }
        reporter.report(true, "TV 연결됨 · HDMI · UNITY READY")
        reporter.report(true, "TV 연결됨 · HDMI · UNITY READY")

        assertEquals(2, attempts)
        assertEquals(listOf(true to "TV 연결됨 · HDMI · UNITY READY"), events)
    }

    @Test
    fun identicalReentrantStatusIsSuppressedWhileCallbackIsRunning() {
        var callbacks = 0
        lateinit var reporter: DistinctDisplayStatusReporter
        reporter = DistinctDisplayStatusReporter { connected, message ->
            callbacks++
            reporter.report(connected, message)
        }

        reporter.report(true, "TV 연결됨 · HDMI · UNITY READY")

        assertEquals(1, callbacks)
    }

    @Test
    fun failedOuterCallbackDoesNotClobberNewerReentrantStatus() {
        val callbacks = mutableListOf<String>()
        lateinit var reporter: DistinctDisplayStatusReporter
        reporter = DistinctDisplayStatusReporter { _, message ->
            callbacks += message
            if (message == "UNITY STARTING") {
                reporter.report(true, "UNITY READY")
                throw IllegalStateException("outer callback failed after READY")
            }
        }

        try {
            reporter.report(true, "UNITY STARTING")
            fail("Outer callback should fail")
        } catch (_: IllegalStateException) {
            // The nested READY report succeeded and must remain the cached latest state.
        }
        reporter.report(true, "UNITY READY")

        assertEquals(listOf("UNITY STARTING", "UNITY READY"), callbacks)
    }

    @Test
    fun failedOuterCallbackDoesNotUndoReentrantReset() {
        var callbacks = 0
        lateinit var reporter: DistinctDisplayStatusReporter
        reporter = DistinctDisplayStatusReporter { _, _ ->
            callbacks++
            if (callbacks == 1) {
                reporter.reset()
                throw IllegalStateException("outer callback failed after reset")
            }
        }

        try {
            reporter.report(false, "외부 TV 미검출")
            fail("Outer callback should fail")
        } catch (_: IllegalStateException) {
            // Reset is newer state than the failing report and must survive its rollback.
        }
        reporter.report(false, "외부 TV 미검출")

        assertEquals(2, callbacks)
    }
}
