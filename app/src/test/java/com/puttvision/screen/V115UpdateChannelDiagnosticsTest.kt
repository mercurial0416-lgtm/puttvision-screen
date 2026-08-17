package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V115UpdateChannelDiagnosticsTest {
    @Test fun equalCodeAndNameIsCurrent() {
        val status = V115UpdateChannelDiagnostics.classify(100147, "0.7.147", 100147, "0.7.147")
        assertEquals(V115UpdateChannelState.CURRENT, status.state)
        assertTrue(status.healthy)
    }

    @Test fun newerManifestIsUpdateAvailable() {
        val status = V115UpdateChannelDiagnostics.classify(100147, "0.7.147", 100148, "0.7.148")
        assertEquals(V115UpdateChannelState.UPDATE_AVAILABLE, status.state)
        assertTrue(status.healthy)
    }

    @Test fun olderManifestIsReportedAsChannelBehind() {
        val status = V115UpdateChannelDiagnostics.classify(100148, "0.7.148", 100147, "0.7.147")
        assertEquals(V115UpdateChannelState.CHANNEL_BEHIND, status.state)
        assertFalse(status.healthy)
        assertTrue(status.summary.contains("channel behind"))
    }

    @Test fun sameCodeDifferentNameIsNotCalledCurrent() {
        val status = V115UpdateChannelDiagnostics.classify(100147, "0.7.147", 100147, "0.7.147-hotfix")
        assertEquals(V115UpdateChannelState.NAME_MISMATCH, status.state)
        assertFalse(status.healthy)
    }

    @Test fun prefixesAndWhitespaceNormalizeSafely() {
        val status = V115UpdateChannelDiagnostics.classify(
            100147,
            "  PuttVision 0.7.147  ",
            100147,
            " v0.7.147 "
        )
        assertEquals(V115UpdateChannelState.CURRENT, status.state)
        assertEquals("0.7.147", status.installedVersionName)
        assertEquals("0.7.147", status.manifestVersionName)
    }

    @Test fun blankNamesFallBackToCodes() {
        val status = V115UpdateChannelDiagnostics.classify(100147, null, 100147, "")
        assertEquals(V115UpdateChannelState.CURRENT, status.state)
        assertEquals("100147", status.installedVersionName)
        assertEquals("100147", status.manifestVersionName)
    }

    @Test fun invalidCodesFailClosed() {
        assertEquals(
            V115UpdateChannelState.INVALID,
            V115UpdateChannelDiagnostics.classify(0, "0", 100147, "0.7.147").state
        )
        assertEquals(
            V115UpdateChannelState.INVALID,
            V115UpdateChannelDiagnostics.classify(100147, "0.7.147", -1, "bad").state
        )
    }

    @Test fun versionNamesAreBounded() {
        val raw = "x".repeat(500)
        val status = V115UpdateChannelDiagnostics.classify(100147, raw, 100147, raw)
        assertEquals(V115UpdateChannelState.CURRENT, status.state)
        assertTrue(status.installedVersionName.length <= 48)
        assertTrue(status.manifestVersionName.length <= 48)
        assertTrue(status.summary.length <= 160)
    }

    @Test fun hardwarelessSuiteCoversAllStates() {
        val report = V115HardwarelessUpdateStatusSuite.run()
        assertTrue(report.passed)
        assertTrue(report.checksTotal >= 12)
        assertEquals(report.checksTotal, report.checksPassed)
    }
}
