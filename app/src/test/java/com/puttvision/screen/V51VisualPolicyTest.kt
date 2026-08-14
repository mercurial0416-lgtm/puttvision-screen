package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V51VisualPolicyTest {
    @Test fun scoreTonesHaveStableThresholds() {
        assertEquals(V51Tone.BAD, V51VisualPolicy.toneForScore(49))
        assertEquals(V51Tone.WARN, V51VisualPolicy.toneForScore(50))
        assertEquals(V51Tone.INFO, V51VisualPolicy.toneForScore(68))
        assertEquals(V51Tone.GOOD, V51VisualPolicy.toneForScore(85))
    }

    @Test fun semanticStatusDoesNotMarkCalibratedAsWarning() {
        assertEquals(V51Tone.GOOD, V51VisualPolicy.toneForStatus("CALIBRATED"))
        assertEquals(V51Tone.WARN, V51VisualPolicy.toneForStatus("CALIBRATING"))
        assertEquals(V51Tone.BAD, V51VisualPolicy.toneForStatus("HFR SLOW"))
        assertEquals(V51Tone.GOOD, V51VisualPolicy.toneForStatus("CONNECTED"))
    }

    @Test fun progressAlwaysStaysInsideTrack() {
        assertEquals(0f, V51VisualPolicy.progress(-40), .0001f)
        assertEquals(.52f, V51VisualPolicy.progress(52), .0001f)
        assertEquals(1f, V51VisualPolicy.progress(140), .0001f)
    }

    @Test fun trainingSegmentsExposePastCurrentAndFuture() {
        assertEquals(listOf(-1, 0, 1, 1), V51VisualPolicy.segmentStates(1, 4, false))
        assertEquals(listOf(-1, -1, -1, -1), V51VisualPolicy.segmentStates(2, 4, true))
        assertTrue(V51VisualPolicy.segmentStates(0, 0, false).isEmpty())
    }

    @Test fun sparklineNormalizationHandlesFlatAndNonFiniteInput() {
        assertEquals(listOf(.5f, .5f, .5f), V51VisualPolicy.normalize(listOf(82.0, 82.0, 82.0)))
        val n = V51VisualPolicy.normalize(listOf(60.0, Double.NaN, 100.0))
        assertEquals(0f, n[0], .0001f)
        assertEquals(.5f, n[1], .0001f)
        assertEquals(1f, n[2], .0001f)
    }
}
