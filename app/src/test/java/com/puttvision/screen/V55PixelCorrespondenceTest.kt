package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V55PixelCorrespondenceTest {
    private fun track(
        width: Int? = 1920,
        height: Int? = 1080,
        pixelShift: Double = 0.0,
        corruptBallX: Double? = null
    ): HfrFeatureTrack {
        val frames = (47..54).map { frame ->
            val i = frame - 47
            HfrFeatureFrame(
                frame = frame,
                timeFromImpactMs = (frame - 50) * 1000.0 / 240.0,
                ballXcm = i * .08,
                ballYcm = i * .22,
                heelXcm = -2.0 + i * .05,
                heelYcm = -.5 + i * .10,
                toeXcm = 2.0 + i * .05,
                toeYcm = -.5 + i * .10,
                markerAngleDeg = .15,
                ballXpx = corruptBallX ?: (950.0 + i * 2.0 + pixelShift),
                ballYpx = 540.0 - i * 1.5,
                heelXpx = 900.0 + i + pixelShift,
                heelYpx = 590.0 - i,
                toeXpx = 1010.0 + i + pixelShift,
                toeYpx = 590.0 - i
            )
        }
        return HfrFeatureTrack(240, 50, frames, width, height)
    }

    @Test fun validatedPixelTrackRoundTripsWithoutLosingRawCoordinates() {
        val packet = V43FeatureTrackPacket(
            cameraId = "cam-face",
            view = V15CameraView.FACE_ON,
            capturedAtMs = 10_000L,
            sequence = 4L,
            track = track()
        )
        val raw = V55PixelFeatureTrackWire.encode("PAIR1234", packet)
        val decoded = requireNotNull(V55PixelFeatureTrackWire.decode(raw, "PAIR1234", 10_500L))

        assertEquals(1920, decoded.track.imageWidthPx)
        assertEquals(1080, decoded.track.imageHeightPx)
        assertEquals(8, decoded.track.pixelBallFrames)
        assertEquals(950.0, requireNotNull(decoded.track.frames.first().ballXpx), 1e-9)
        assertEquals(590.0, requireNotNull(decoded.track.frames.first().heelYpx), 1e-9)
        assertTrue(V55PixelFeatureTrackWire.isPixelFeatureTrack(raw))
    }

    @Test fun legacyPlanarTrackCannotSilentlyEnterStereoPixelPath() {
        val legacy = track(width = null, height = null).copy(
            frames = track().frames.map { it.copy(
                ballXpx = null, ballYpx = null,
                heelXpx = null, heelYpx = null,
                toeXpx = null, toeYpx = null
            ) }
        )
        val result = V55PixelTrackValidator.inspect(legacy, V15CameraView.TOP)
        assertFalse(result.valid)
        assertTrue(result.reason.contains("shape"))
        assertNull(result.normalized)
    }

    @Test fun outOfFramePixelIsRejectedFailClosed() {
        val bad = track(corruptBallX = 5000.0)
        val result = V55PixelTrackValidator.inspect(bad, V15CameraView.FACE_ON)
        assertFalse(result.valid)
        assertTrue(result.reason.contains("BALL pixel"))
    }

    @Test fun timedStereoMatchesAllowExplicitLocalPrimaryAndExposeV53PixelPairs() {
        val local = track(pixelShift = 0.0)
        val remote = track(pixelShift = 28.0)
        val pairs = V55StereoPixelMatcher.ballPairs(
            local = local,
            localView = V15CameraView.PRIMARY,
            remote = remote,
            remoteView = V15CameraView.TOP
        )
        assertEquals(8, pairs.size)
        assertEquals(950.0, pairs.first().localPixel.x, 1e-9)
        assertEquals(978.0, pairs.first().remotePixel.x, 1e-9)
        assertTrue(pairs.all { it.deltaMs <= 1e-9 })
    }

    @Test fun remotePrimaryPixelTrackRemainsRejected() {
        val result = V55PixelTrackValidator.inspect(track(), V15CameraView.PRIMARY)
        assertFalse(result.valid)
        assertTrue(result.reason.contains("cannot be PRIMARY"))
    }

    @Test fun wrongPairCodeCannotDecodePixelTrack() {
        val packet = V43FeatureTrackPacket(
            cameraId = "cam-top",
            view = V15CameraView.TOP,
            capturedAtMs = 20_000L,
            sequence = 2L,
            track = track()
        )
        val raw = V55PixelFeatureTrackWire.encode("PAIR1234", packet)
        assertNull(V55PixelFeatureTrackWire.decode(raw, "WRONG999", 20_300L))
    }
}
