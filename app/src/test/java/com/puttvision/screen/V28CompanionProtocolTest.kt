package com.puttvision.screen

import org.junit.Assert.*
import org.junit.Test

class V28CompanionProtocolTest {
    private fun metrics() = ShotMetrics(
        ballSpeedMps = 1.25,
        launchAngleDeg = .35,
        headSpeedMps = null,
        faceAngleDeg = null,
        pathAngleDeg = null,
        faceToPathDeg = null,
        smash = null,
        impactOffsetMm = null,
        measuredAtNs = 10_000_000_000L,
        confidence = .9
    )

    @Test fun midpointClockSyncIsStable() {
        val sync = V28ClockSyncEstimator.estimate(1000L, 1120L, 1100L)
        assertEquals(70L, sync.offsetMs)
        assertEquals(100L, sync.rttMs)
    }

    @Test fun pairCodeAndTimestampAreEnforced() {
        val measurement = V15CameraMeasurement(
            "test", V15CameraView.FACE_ON, metrics(), .9, 10000L
        )
        val raw = V28CompanionProtocol.encodeMeasurement("ABCDEFGH", measurement, 70L)
        assertEquals(10070L, V28CompanionProtocol.decodeMeasurement(raw, "ABCDEFGH", 10500L)?.receivedAtMs)
        assertNull(V28CompanionProtocol.decodeMeasurement(raw, "XXXXXXXX", 10500L))
        assertNull(V28CompanionProtocol.decodeMeasurement(raw, "ABCDEFGH", 12500L))
    }

    @Test fun wrongPairCodeGetsImmediateNegativeAck() {
        val request = V28CompanionProtocol.syncRequest("WRONG123", 1000L)
        val ack = V28CompanionProtocol.syncAck(request, "RIGHT123", 1040L)
        assertNotNull(ack)
        assertNull(V28CompanionProtocol.parseSyncAck(requireNotNull(ack), 1000L, 1080L))
    }

    @Test fun matchingPairCodeCompletesClockSync() {
        val request = V28CompanionProtocol.syncRequest("PAIR1234", 1000L)
        val ack = requireNotNull(V28CompanionProtocol.syncAck(request, "PAIR1234", 1040L))
        assertNotNull(V28CompanionProtocol.parseSyncAck(ack, 1000L, 1080L))
    }
}