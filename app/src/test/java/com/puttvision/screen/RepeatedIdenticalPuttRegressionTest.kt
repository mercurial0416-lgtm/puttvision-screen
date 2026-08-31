package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatedIdenticalPuttRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun liveRollArmsACompletionSoIdenticalResultsStillCount() {
        val session = asset("v178_session_form.gd")
        assertTrue(session.contains("var _v178_completion_armed := false"))
        assertTrue(session.contains("var _v178_completed_shot_serial := 0"))
        assertTrue(session.contains("if running:\n        # Arm on the live roll"))
        assertTrue(session.contains("_v178_completion_armed = true"))
        assertTrue(session.contains("if not _v178_completion_armed and signature == _v178_last_signature:"))
        assertTrue(session.contains("_v178_completion_armed = false"))
        assertTrue(session.contains("_v178_completed_shot_serial += 1"))
    }

    @Test
    fun dispersionConsumesTheCompletionSerialInsteadOfPayloadIdentity() {
        val dispersion = asset("v179_session_dispersion.gd")
        assertTrue(dispersion.contains("var _v179_last_completion_serial := 0"))
        assertTrue(dispersion.contains("_v178_completed_shot_serial == _v179_last_completion_serial"))
        assertTrue(dispersion.contains("_v179_last_completion_serial = _v178_completed_shot_serial"))
        assertFalse(dispersion.contains("signature == _v179_last_signature"))
    }

    @Test
    fun sessionAccountingFixCannotMutateAuthoritativePuttingInputs() {
        val session = asset("v178_session_form.gd")
        val dispersion = asset("v179_session_dispersion.gd")
        for (source in listOf(session, dispersion)) {
            assertFalse(source.contains("GreenTerrain.set"))
            assertFalse(source.contains("GreenReadAdvisor.set"))
            assertFalse(source.contains("ballVelocity ="))
            assertFalse(source.contains("readLineDeltaCm ="))
            assertFalse(source.contains("paceDeltaCm ="))
        }
    }
}
