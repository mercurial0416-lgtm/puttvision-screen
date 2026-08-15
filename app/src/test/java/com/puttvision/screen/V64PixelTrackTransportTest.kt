package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V64PixelTrackTransportTest {
    private fun pixelTrack(): HfrFeatureTrack = HfrFeatureTrack(
        fps = 240,
        impactFrame = 10,
        imageWidthPx = 1920,
        imageHeightPx = 1080,
        frames = (0 until 6).map { i ->
            HfrFeatureFrame(
                frame = 10 + i,
                timeFromImpactMs = i * (1000.0 / 240.0),
                ballXcm = 1.0 + i,
                ballYcm = 20.0 + i,
                heelXcm = null,
                heelYcm = null,
                toeXcm = null,
                toeYcm = null,
                markerAngleDeg = null,
                ballXpx = 850.0 + i * 8.0,
                ballYpx = 700.0 + i * 4.0
            )
        }
    )

    @Test fun pixelSafeTrackSendsLegacyThenNewerPixelPacket() {
        val lines = V64PixelTrackTransport.encodeLines(
            code = "1234",
            cameraId = "cam-b",
            view = V15CameraView.FACE_ON,
            track = pixelTrack(),
            capturedAtMs = 100_000L,
            legacySequence = 40L,
            pixelSequence = 41L
        )

        assertEquals(2, lines.size)
        assertFalse(lines[0].pixelTrack)
        assertEquals(40L, lines[0].sequence)
        assertTrue(lines[1].pixelTrack)
        assertEquals(41L, lines[1].sequence)
        assertTrue(V43FeatureTrackWire.isFeatureTrack(lines[0].raw))
        assertTrue(V55PixelFeatureTrackWire.isPixelFeatureTrack(lines[1].raw))
    }

    @Test fun newServerDecoderPreservesRawPixelsFromPixelPacket() {
        val lines = V64PixelTrackTransport.encodeLines(
            "1234", "cam-b", V15CameraView.FACE_ON, pixelTrack(), 100_000L, 2L, 3L
        )
        val legacy = V64PixelTrackTransport.decodeLine(lines[0].raw, "1234", nowMs = 100_100L)
        val pixel = V64PixelTrackTransport.decodeLine(lines[1].raw, "1234", nowMs = 100_100L)

        assertTrue(legacy != null && !legacy.pixelTrack)
        assertTrue(pixel != null && pixel.pixelTrack)
        assertNull(legacy?.packet?.track?.imageWidthPx)
        assertEquals(1920, pixel?.packet?.track?.imageWidthPx)
        assertEquals(850.0, pixel?.packet?.track?.frames?.first()?.ballXpx ?: Double.NaN, 1e-9)
    }

    @Test fun legacyOnlyTrackStillUsesBackwardCompatibleSinglePacket() {
        val legacyOnly = pixelTrack().copy(
            imageWidthPx = null,
            imageHeightPx = null,
            frames = pixelTrack().frames.map { it.copy(ballXpx = null, ballYpx = null) }
        )
        assertFalse(V64PixelTrackTransport.canSendPixels(legacyOnly, V15CameraView.FACE_ON))
        val lines = V64PixelTrackTransport.encodeLines(
            "1234", "cam-b", V15CameraView.FACE_ON, legacyOnly, 100_000L, 7L, 8L
        )
        assertEquals(1, lines.size)
        assertFalse(lines.single().pixelTrack)
    }

    @Test fun wrongPairingCodeRejectsBothPacketTypes() {
        val lines = V64PixelTrackTransport.encodeLines(
            "1234", "cam-b", V15CameraView.FACE_ON, pixelTrack(), 100_000L, 12L, 13L
        )
        assertNull(V64PixelTrackTransport.decodeLine(lines[0].raw, "9999", nowMs = 100_100L))
        assertNull(V64PixelTrackTransport.decodeLine(lines[1].raw, "9999", nowMs = 100_100L))
    }
}