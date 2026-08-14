package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V45HfrDiagnosticsTest {
    @Test fun frameCachePolicyCapsLargeHfrFrames() {
        val six1080p = V45HfrFrameCachePolicy.estimatedArgbBytes(1920, 1080, 6)
        val twentyFour1080p = V45HfrFrameCachePolicy.estimatedArgbBytes(1920, 1080, 24)
        assertFalse(V45HfrFrameCachePolicy.shouldEvict(6, six1080p))
        assertTrue(V45HfrFrameCachePolicy.shouldEvict(24, twentyFour1080p))
        assertTrue(V45HfrFrameCachePolicy.MAX_BYTES <= 48L * 1024L * 1024L)
        assertTrue(V45HfrFrameCachePolicy.BATCH_SIZE <= 4)
    }

    @Test fun failureRuntimeKeepsBoundedReasonHistoryAndClearsLatestOnSuccess() {
        V45HfrFailureRuntime.reset()
        repeat(30) { i ->
            V45HfrFailureRuntime.publish(
                V45HfrFailure(
                    reason = if (i % 3 == 0) V45HfrFailureReason.CALIBRATION else V45HfrFailureReason.BALL_ORIGIN,
                    phase = "TEST",
                    elapsedMs = i.toLong()
                )
            )
        }
        val summary = V45HfrFailureRuntime.summary()
        assertEquals(24, summary.samples)
        assertEquals(V45HfrFailureReason.BALL_ORIGIN, summary.topReason)
        assertTrue(summary.topReasonCount > 0)
        assertTrue(V45HfrFailureRuntime.latest!!.label.contains("HFR FAIL"))

        V45HfrFailureRuntime.recordSuccess()
        assertNull(V45HfrFailureRuntime.latest)
        assertEquals(24, V45HfrFailureRuntime.summary().samples)
        V45HfrFailureRuntime.reset()
    }

    @Test fun invalidEstimatedDimensionsNeverProduceNegativeBytes() {
        assertEquals(0L, V45HfrFrameCachePolicy.estimatedArgbBytes(-1, 1080, 4))
        assertEquals(0L, V45HfrFrameCachePolicy.estimatedArgbBytes(1920, -1, 4))
        assertEquals(0L, V45HfrFrameCachePolicy.estimatedArgbBytes(1920, 1080, -2))
    }
}