package com.puttvision.screen

import org.junit.Assert.*
import org.junit.Test

class V28CompanionProtocolTest {
    @Test fun midpointClockSyncIsStable() {
        val sync = V28ClockSyncEstimator.estimate(1000L, 1120L, 1100L)
        assertEquals(70L, sync.offsetMs)
        assertEquals(100L, sync.rttMs)
    }

    @Test fun pairCodeAndTimestampAreEnforced() {
        val measurement = V15CameraMeasurement(
            "test", V15CameraView.FACE_ON,
            ShotMetrics(ballSpeedMps = 1.25, launchAngleDeg = .35, confidence = .9),
            .9, 10000L
        )
        val raw = V28CompanionProtocol.encodeMeasurement("ABCDEFGH", measurement, 70L)
        assertEquals(10070L, V28CompanionProtocol.decodeMeasurement(raw, "ABCDEFGH", 10500L)?.receivedAtMs)
        assertNull(V28CompanionProtocol.decodeMeasurement(raw, "XXXXXXXX", 10500L))
        assertNull(V28CompanionProtocol.decodeMeasurement(raw, "ABCDEFGH", 12500L))
    }
}
