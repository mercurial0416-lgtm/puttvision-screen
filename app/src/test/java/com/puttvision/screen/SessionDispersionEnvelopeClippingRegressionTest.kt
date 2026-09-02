package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDispersionEnvelopeClippingRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun displayFittedEnvelopeDisclosesViewportCompression() {
        val script = asset("v194_dispersion_envelope.gd")

        assertTrue(script.contains("const V194_FIT_CLIPPED_THRESHOLD := 0.9999"))
        assertTrue(script.contains("var view_clipped := scale < V194_FIT_CLIPPED_THRESHOLD"))
        assertTrue(script.contains("\"viewClipped\": view_clipped"))
        assertTrue(script.contains("\"presentationScale\": scale"))
        assertTrue(script.contains("VIEW CLIPPED"))
        assertTrue(script.contains("_v194_spread_readout(spread, bool(geometry[\"viewClipped\"]))"))
    }

    @Test
    fun displayedCentimetersRemainMeasuredTruthRatherThanPresentationScale() {
        val script = asset("v194_dispersion_envelope.gd")

        assertTrue(script.contains("return \"GROUP ±%.0f / ±%.0f CM%s\" % [spread.x, spread.y, suffix]"))
        assertTrue(script.contains("fitted.append(local_point * scale)"))
        assertFalse(script.contains("spread *= scale"))
        assertFalse(script.contains("spread.x * scale"))
        assertFalse(script.contains("spread.y * scale"))
        assertFalse(script.contains("_v179_samples[index] ="))
    }

    @Test
    fun fixRemainsPresentationOnlyAndDoesNotTouchAuthoritativePhysics() {
        val script = asset("v194_dispersion_envelope.gd")

        assertTrue(script.contains("Presentation-only session grouping envelope"))
        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
        assertFalse(script.contains("predictedTrail] ="))
        assertFalse(script.contains("actualTrail] ="))
    }
}
