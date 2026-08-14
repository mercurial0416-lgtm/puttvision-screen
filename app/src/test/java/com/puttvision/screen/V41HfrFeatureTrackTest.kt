package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class V41HfrFeatureTrackTest {
    @Test fun runtimeKeepsOnlyCompactNumericTrackAndClears() {
        V41HfrFeatureTrackRuntime.clear()
        val frames = (0 until 40).map { i ->
            HfrFeatureFrame(
                frame = 100 + i,
                timeFromImpactMs = (i - 20) * 4.166,
                ballXcm = i.toDouble(),
                ballYcm = i.toDouble() + 1.0,
                heelXcm = if (i % 2 == 0) i.toDouble() else null,
                heelYcm = if (i % 2 == 0) i.toDouble() else null,
                toeXcm = if (i % 2 == 0) i.toDouble() + 2.0 else null,
                toeYcm = if (i % 2 == 0) i.toDouble() else null,
                markerAngleDeg = null
            )
        }
        V41HfrFeatureTrackRuntime.publish(HfrFeatureTrack(240, 120, frames))
        val latest = requireNotNull(V41HfrFeatureTrackRuntime.latest)
        assertEquals(32, latest.frames.size)
        assertEquals(32, latest.ballFrames)
        assertEquals(16, latest.putterFrames)
        V41HfrFeatureTrackRuntime.clear()
        assertNull(V41HfrFeatureTrackRuntime.latest)
    }
}
