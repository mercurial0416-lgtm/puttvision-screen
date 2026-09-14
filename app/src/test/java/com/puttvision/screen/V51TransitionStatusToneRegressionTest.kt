package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class V51TransitionStatusToneRegressionTest {
    @Test fun calibrationInProgressWinsOverReadySignal() {
        assertEquals(V51Tone.WARN, V51VisualPolicy.toneForStatus("calibrating ready"))
    }

    @Test fun staleDataWinsOverCompletionSignal() {
        assertEquals(V51Tone.WARN, V51VisualPolicy.toneForStatus("stale data complete"))
    }

    @Test fun preparationWinsOverConnectedSignal() {
        assertEquals(V51Tone.WARN, V51VisualPolicy.toneForStatus("prep connected"))
    }
}
