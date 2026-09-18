package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class V51StatusTonePrecedenceRegressionTest {
    @Test fun severeStatusWinsOverReadySignals() {
        assertEquals(V51Tone.BAD, V51VisualPolicy.toneForStatus("camera error ready"))
        assertEquals(V51Tone.BAD, V51VisualPolicy.toneForStatus("low confidence connected"))
    }

    @Test fun statusPrecedenceIsCaseInsensitive() {
        assertEquals(V51Tone.BAD, V51VisualPolicy.toneForStatus("Camera ERROR Ready"))
        assertEquals(V51Tone.WARN, V51VisualPolicy.toneForStatus("Wait Connected"))
        assertEquals(V51Tone.INFO, V51VisualPolicy.toneForStatus("Sync Active"))
    }

    @Test fun transitionalStatusWinsOverHealthySignals() {
        assertEquals(V51Tone.WARN, V51VisualPolicy.toneForStatus("wait connected"))
        assertEquals(V51Tone.WARN, V51VisualPolicy.toneForStatus("partial data ready"))
    }

    @Test fun calibratedStatusKeepsExplicitHealthyPriority() {
        assertEquals(V51Tone.GOOD, V51VisualPolicy.toneForStatus("calibrated"))
        assertEquals(V51Tone.GOOD, V51VisualPolicy.toneForStatus("calibrated wait"))
    }

    @Test fun runtimeAndUnknownStatusesRemainDistinct() {
        assertEquals(V51Tone.INFO, V51VisualPolicy.toneForStatus("running"))
        assertEquals(V51Tone.INFO, V51VisualPolicy.toneForStatus("sync active"))
        assertEquals(V51Tone.NEUTRAL, V51VisualPolicy.toneForStatus("idle"))
    }

    @Test fun emptyAndWhitespaceOnlyStatusesStayNeutral() {
        assertEquals(V51Tone.NEUTRAL, V51VisualPolicy.toneForStatus(""))
        assertEquals(V51Tone.NEUTRAL, V51VisualPolicy.toneForStatus("   \t\n"))
        assertEquals(V51Tone.NEUTRAL, V51VisualPolicy.toneForStatus("\r\n\t"))
        assertEquals(V51Tone.NEUTRAL, V51VisualPolicy.toneForStatus("\u2003\u2003"))
        assertEquals(V51Tone.NEUTRAL, V51VisualPolicy.toneForStatus("\u00a0\u00a0"))
        assertEquals(V51Tone.NEUTRAL, V51VisualPolicy.toneForStatus("\u3000\u3000"))
    }
}
