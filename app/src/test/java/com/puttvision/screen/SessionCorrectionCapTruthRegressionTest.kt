package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCorrectionCapTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    private fun truthfulCue(samples: List<Double>): String {
        if (samples.size < 3) return ""
        val sorted = samples.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) * 0.5
        }
        if (kotlin.math.abs(median) <= 3.0) return "HOLD LINE"
        val direction = if (median > 0.0) "LEFT" else "RIGHT"
        return if (kotlin.math.abs(median) > 9.0) {
            "9cm+ $direction"
        } else {
            "${kotlin.math.round(kotlin.math.abs(median)).toInt().coerceIn(3, 9)}cm $direction"
        }
    }

    @Test
    fun saturatedCorrectionDisclosesThatTheSafeCueIsCapped() {
        assertEquals("9cm+ LEFT", truthfulCue(listOf(14.0, 12.0, 18.0, 11.0, 16.0)))
        assertEquals("9cm+ RIGHT", truthfulCue(listOf(-14.0, -12.0, -18.0, -11.0, -16.0)))
        assertEquals("7cm LEFT", truthfulCue(listOf(6.0, 7.0, 8.0)))
        assertEquals("HOLD LINE", truthfulCue(listOf(-2.0, 1.0, 3.0)))
    }

    @Test
    fun presentationGuardPreservesTheExistingSafetyCapAndPaceCue() {
        val helper = asset("session_correction_cap_truth.gd")
        assertTrue(helper.contains("const LINE_CAP_CM := 9.0"))
        assertTrue(helper.contains("return \"%dcm+ %s\""))
        assertTrue(helper.contains("var pace_separator := label.text.rfind(\" · \")"))
        assertTrue(helper.contains("label.text = \"NEXT · %s%s\""))
        assertFalse(helper.contains("_v179_samples.append"))
        assertFalse(helper.contains("readLineDeltaCm"))
    }

    @Test
    fun tvSceneWiresTheTruthGuardAfterSessionDispersion() {
        val scene = asset("v143_tv.tscn")
        val dispersion = scene.indexOf("SessionDispersionEdgeTruth")
        val capTruth = scene.indexOf("SessionCorrectionCapTruth")
        assertTrue(dispersion >= 0)
        assertTrue(capTruth > dispersion)
        assertTrue(scene.contains("res://session_correction_cap_truth.gd"))
    }
}
