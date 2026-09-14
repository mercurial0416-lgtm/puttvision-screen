package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V51VisualPolicyRegressionTest {
    @Test fun normalizeKeepsInvalidSamplesOutOfSparklineGeometry() {
        val normalized = V51VisualPolicy.normalize(
            listOf(Double.NaN, 10.0, 20.0, Double.POSITIVE_INFINITY)
        )

        assertEquals(listOf(0.5f, 0.0f, 1.0f, 0.5f), normalized)
        assertTrue(normalized.all { it.isFinite() && it in 0.0f..1.0f })
    }

    @Test fun normalizeDropsAnAllInvalidSeries() {
        assertTrue(
            V51VisualPolicy.normalize(
                listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
            ).isEmpty()
        )
    }

    @Test fun progressAlwaysStaysInsideMeterBounds() {
        assertEquals(0.0f, V51VisualPolicy.progress(-1))
        assertEquals(0.0f, V51VisualPolicy.progress(0))
        assertEquals(0.5f, V51VisualPolicy.progress(50))
        assertEquals(1.0f, V51VisualPolicy.progress(100))
        assertEquals(1.0f, V51VisualPolicy.progress(101))
    }

    @Test fun scoreToneThresholdsStayStableAtEveryBoundary() {
        assertEquals(V51Tone.BAD, V51VisualPolicy.toneForScore(Int.MIN_VALUE))
        assertEquals(V51Tone.BAD, V51VisualPolicy.toneForScore(49))
        assertEquals(V51Tone.WARN, V51VisualPolicy.toneForScore(50))
        assertEquals(V51Tone.WARN, V51VisualPolicy.toneForScore(67))
        assertEquals(V51Tone.INFO, V51VisualPolicy.toneForScore(68))
        assertEquals(V51Tone.INFO, V51VisualPolicy.toneForScore(84))
        assertEquals(V51Tone.GOOD, V51VisualPolicy.toneForScore(85))
        assertEquals(V51Tone.GOOD, V51VisualPolicy.toneForScore(Int.MAX_VALUE))
    }
}
