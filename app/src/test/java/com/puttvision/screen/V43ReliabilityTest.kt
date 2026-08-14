package com.puttvision.screen

import org.junit.Assert.*
import org.junit.Test
import java.io.RandomAccessFile
import java.nio.file.Files

class V43ReliabilityTest {
    private fun metrics() = ShotMetrics(
        ballSpeedMps = 1.25,
        launchAngleDeg = .35,
        headSpeedMps = .92,
        faceAngleDeg = .15,
        pathAngleDeg = -.10,
        faceToPathDeg = .25,
        smash = 1.36,
        impactOffsetMm = 1.2,
        measuredAtNs = 10_000_000_000L,
        confidence = .9
    )

    private fun track(frameCount: Int = 12) = HfrFeatureTrack(
        fps = 240,
        impactFrame = 50,
        frames = (0 until frameCount).map { i ->
            HfrFeatureFrame(
                frame = 44 + i,
                timeFromImpactMs = (i - 6) * 1000.0 / 240.0,
                ballXcm = i * .1,
                ballYcm = i * .2,
                heelXcm = i * .1 - 1.0,
                heelYcm = i * .2 - .5,
                toeXcm = i * .1 + 1.0,
                toeYcm = i * .2 + .5,
                markerAngleDeg = .2
            )
        }
    )

    @Test fun storageGuardRejectsLowFreeSpaceBeforeCapture() {
        val dir = Files.createTempDirectory("pv43-low").toFile()
        try {
            val decision = V43HfrStorageGuard.prepare(
                dir = dir,
                usableBytesOverride = V43HfrStorageGuard.MIN_FREE_BYTES - 1L
            )
            assertFalse(decision.ok)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun storageGuardBoundsFileCountAndBytes() {
        val dir = Files.createTempDirectory("pv43-budget").toFile()
        try {
            repeat(13) { index ->
                val file = dir.resolve("shot_$index.mp4")
                RandomAccessFile(file, "rw").use { it.setLength(10L * 1024L * 1024L) }
                file.setLastModified(10_000L + index)
            }
            val decision = V43HfrStorageGuard.prepare(
                dir = dir,
                nowMs = 20_000L,
                usableBytesOverride = 512L * 1024L * 1024L
            )
            assertTrue(decision.ok)
            assertTrue(decision.deletedFiles >= 4)
            assertTrue(decision.remainingFiles <= V43HfrStorageGuard.MAX_CACHE_FILES)
            assertTrue(decision.remainingBytes <= V43HfrStorageGuard.MAX_CACHE_BYTES)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun captureNamesStayUniqueInsideSameMillisecond() {
        val dir = Files.createTempDirectory("pv43-name").toFile()
        try {
            val a = V43CaptureFileNamer.create(dir, 240, 1234L)
            val b = V43CaptureFileNamer.create(dir, 240, 1234L)
            assertNotEquals(a.name, b.name)
            assertTrue(a.name.endsWith("240fps.mp4"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun failureCircuitFallsBackThenSelfRecovers() {
        val circuit = V43HfrFailureCircuit(failureLimit = 3, cooldownMs = 1000L)
        circuit.recordFailure(100L)
        circuit.recordFailure(200L)
        assertTrue(circuit.allow(250L))
        circuit.recordFailure(300L)
        assertFalse(circuit.allow(301L))
        assertEquals(999L, circuit.remainingMs(301L))
        assertTrue(circuit.allow(1300L))
        assertEquals(0, circuit.failureCount())
    }

    @Test fun hfrWindowReportsTailLatencyAndFastCalibrationShare() {
        V43HfrHealthWindow.clear()
        listOf(1000L, 1100L, 1200L, 1300L, 5000L).forEachIndexed { index, total ->
            V43HfrHealthWindow.publish(
                V42HfrAnalysisHealth(
                    calibrationMode = if (index < 2) "MARKERLESS_FAST" else "QR",
                    calibrationMs = if (index == 4) 1500L else 200L + index,
                    totalAnalysisMs = total,
                    fps = 240,
                    analyzedFrames = 120,
                    ballTrackFrames = 20,
                    putterTrackFrames = 18
                )
            )
        }
        val summary = V43HfrHealthWindow.summary()
        assertEquals(5, summary.samples)
        assertEquals(1200L, summary.medianTotalMs)
        assertEquals(5000L, summary.p95TotalMs)
        assertEquals(1500L, summary.p95CalibrationMs)
        assertEquals(40, summary.fastMarkerlessPct)
        assertTrue(summary.degraded)
        V43HfrHealthWindow.clear()
    }

    @Test fun sequenceGateRejectsDuplicateAndOutOfOrderPackets() {
        val gate = V43CompanionSequenceGate()
        assertTrue(gate.accept("cam", 10L))
        assertFalse(gate.accept("cam", 10L))
        assertFalse(gate.accept("cam", 9L))
        assertTrue(gate.accept("cam", 11L))
        assertTrue(gate.accept("legacy", null))
    }

    @Test fun sequencedMeasurementRoundTripPreservesServerTime() {
        val m = V15CameraMeasurement("cam", V15CameraView.FACE_ON, metrics(), .9, 10_000L)
        val raw = V43CompanionWire.encodeMeasurement("PAIR1234", m, 70L, 42L)
        val decoded = requireNotNull(V43CompanionWire.decodeMeasurement(raw, "PAIR1234", 10_500L))
        assertEquals(42L, decoded.sequence)
        assertEquals(10_070L, decoded.measurement.receivedAtMs)
        assertEquals("cam", decoded.measurement.cameraId)
        assertNull(V43CompanionWire.decodeMeasurement(raw, "WRONG123", 10_500L))
    }

    @Test fun featureTrackWireCapsPayloadAndRoundTripsGeometry() {
        val packet = V43FeatureTrackPacket(
            cameraId = "cam-top",
            view = V15CameraView.TOP,
            capturedAtMs = 20_000L,
            sequence = 7L,
            track = track(40)
        )
        val raw = V43FeatureTrackWire.encode("PAIR1234", packet)
        assertTrue(raw.length < 8192)
        val decoded = requireNotNull(V43FeatureTrackWire.decode(raw, "PAIR1234", 20_500L))
        assertEquals(7L, decoded.sequence)
        assertEquals(V15CameraView.TOP, decoded.view)
        assertEquals(32, decoded.track.frames.size)
        assertEquals(packet.track.frames.first().ballXcm, decoded.track.frames.first().ballXcm, 1e-9)
    }

    @Test fun remoteFeatureRuntimeDropsStaleTracksFromFreshView() {
        V43RemoteFeatureTrackRuntime.clear()
        V43RemoteFeatureTrackRuntime.publish(
            V43FeatureTrackPacket("cam", V15CameraView.FACE_ON, 1000L, 1L, track())
        )
        assertEquals(1, V43RemoteFeatureTrackRuntime.fresh(nowMs = 2000L).size)
        assertTrue(V43RemoteFeatureTrackRuntime.fresh(nowMs = 4000L).isEmpty())
        V43RemoteFeatureTrackRuntime.clear()
    }

    @Test fun syncHealthMakesClockAgeVisible() {
        val sync = V28ClockSync(offsetMs = 4L, rttMs = 30L)
        val fresh = V43CompanionSyncHealthPolicy.evaluate(sync, lastSyncAtMs = 10_000L, nowMs = 20_000L)
        assertTrue(fresh.fresh)
        assertTrue(fresh.label.contains("10s"))
        val stale = V43CompanionSyncHealthPolicy.evaluate(sync, lastSyncAtMs = 10_000L, nowMs = 60_001L)
        assertFalse(stale.fresh)
        assertTrue(stale.label.contains("오래됨"))
    }

    @Test fun localFeatureSnapshotExpiresInsteadOfReusingOldShot() {
        V41HfrFeatureTrackRuntime.clear()
        V41HfrFeatureTrackRuntime.publish(track(), nowMs = 5_000L)
        assertNotNull(V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 6_000L))
        assertNull(V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 7_000L))
        V41HfrFeatureTrackRuntime.clear()
    }
}
