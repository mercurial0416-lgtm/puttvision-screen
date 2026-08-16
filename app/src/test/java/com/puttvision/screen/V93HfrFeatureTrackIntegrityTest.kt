package com.puttvision.screen

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V93HfrFeatureTrackIntegrityTest {
    @After
    fun tearDown() {
        V41HfrFeatureTrackRuntime.clear()
    }

    @Test
    fun validTrackPublishesWithExactImpactTimebase() {
        val track = validTrack()

        assertTrue(V93HfrFeatureTrackIntegrity.isValid(track))
        assertTrue(V41HfrFeatureTrackRuntime.publish(track, nowMs = 10_000L))

        assertNotNull(V41HfrFeatureTrackRuntime.latest)
        val stored = requireNotNull(V41HfrFeatureTrackRuntime.latest)
        assertEquals(50, stored.impactFrame)
        assertEquals(0.0, stored.frames.first { it.frame == 50 }.timeFromImpactMs, 0.0)
    }

    @Test
    fun missingExactImpactFrameFailsClosedAndPreservesPreviousSnapshot() {
        val good = validTrack()
        assertTrue(V41HfrFeatureTrackRuntime.publish(good, nowMs = 10_000L))

        val missingImpact = good.copy(frames = good.frames.filterNot { it.frame == good.impactFrame })
        assertFalse(V41HfrFeatureTrackRuntime.publish(missingImpact, nowMs = 10_100L))

        assertEquals(good.frames, V41HfrFeatureTrackRuntime.latest?.frames)
        assertEquals(10_000L, V41HfrFeatureTrackRuntime.latestStoredAtMs)
    }

    @Test
    fun nonMonotonicSourceFramesFailClosed() {
        val good = validTrack()
        val frames = good.frames.toMutableList().apply {
            val tmp = this[4]
            this[4] = this[5]
            this[5] = tmp
        }

        assertFalse(V93HfrFeatureTrackIntegrity.isValid(good.copy(frames = frames)))
    }

    @Test
    fun forgedRelativeTimeFailsClosed() {
        val good = validTrack()
        val frames = good.frames.map {
            if (it.frame == 52) it.copy(timeFromImpactMs = 99.0) else it
        }

        assertFalse(V93HfrFeatureTrackIntegrity.isValid(good.copy(frames = frames)))
    }

    @Test
    fun pixelCoordinatesRequireSourceFrameShape() {
        val good = validTrack()
        val frames = good.frames.mapIndexed { index, frame ->
            if (index == 0) frame.copy(ballXpx = 120.0, ballYpx = 240.0) else frame
        }

        assertFalse(V93HfrFeatureTrackIntegrity.isValid(good.copy(frames = frames)))
    }

    @Test
    fun outOfBoundsPixelCoordinatesFailClosed() {
        val good = validTrack(imageWidthPx = 1920, imageHeightPx = 1080)
        val frames = good.frames.mapIndexed { index, frame ->
            if (index == 0) frame.copy(ballXpx = 1920.0, ballYpx = 500.0) else frame
        }

        assertFalse(V93HfrFeatureTrackIntegrity.isValid(good.copy(frames = frames)))
    }

    @Test
    fun longTrackCompactionKeepsImpactAndBothTemporalSides() {
        val longTrack = trackForRange(0..80, impactFrame = 50)

        assertTrue(V41HfrFeatureTrackRuntime.publish(longTrack, nowMs = 10_000L))

        val stored = requireNotNull(V41HfrFeatureTrackRuntime.latest)
        assertEquals(32, stored.frames.size)
        assertTrue(stored.frames.first().frame < stored.impactFrame)
        assertTrue(stored.frames.last().frame > stored.impactFrame)
        assertTrue(stored.frames.any { it.frame == stored.impactFrame })
        assertEquals(0.0, stored.frames.first { it.frame == 50 }.timeFromImpactMs, 0.0)
    }

    @Test
    fun malformedFrameOutsideCompactWindowStillFailsClosed() {
        val longTrack = trackForRange(0..80, impactFrame = 50)
        val forged = longTrack.copy(
            frames = longTrack.frames.map {
                if (it.frame == 80) it.copy(timeFromImpactMs = 999.0) else it
            }
        )

        assertFalse(V41HfrFeatureTrackRuntime.publish(forged, nowMs = 10_000L))
        assertEquals(null, V41HfrFeatureTrackRuntime.latest)
    }

    private fun validTrack(
        imageWidthPx: Int? = null,
        imageHeightPx: Int? = null
    ): HfrFeatureTrack = trackForRange(
        range = 44..56,
        impactFrame = 50,
        imageWidthPx = imageWidthPx,
        imageHeightPx = imageHeightPx
    )

    private fun trackForRange(
        range: IntRange,
        impactFrame: Int,
        imageWidthPx: Int? = null,
        imageHeightPx: Int? = null
    ): HfrFeatureTrack = HfrFeatureTrack(
        fps = 240,
        impactFrame = impactFrame,
        frames = range.map { frame ->
            HfrFeatureFrame(
                frame = frame,
                timeFromImpactMs = (frame - impactFrame) * 1000.0 / 240.0,
                ballXcm = (frame - range.first) * 0.1,
                ballYcm = (frame - range.first) * 0.2,
                heelXcm = (frame - range.first) * 0.1 - 1.0,
                heelYcm = (frame - range.first) * 0.2 - 0.5,
                toeXcm = (frame - range.first) * 0.1 + 1.0,
                toeYcm = (frame - range.first) * 0.2 + 0.5,
                markerAngleDeg = 0.2
            )
        },
        imageWidthPx = imageWidthPx,
        imageHeightPx = imageHeightPx
    )
}
