package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDisplayPowerStateRegressionTest {
    private fun controllerSource(): String {
        val relative = "src/main/java/com/puttvision/screen/ExternalDisplayController.kt"
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate ExternalDisplayController.kt from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun poweredOffPresentationDisplaysAreNotLaunchTargets() {
        val source = controllerSource()

        assertTrue(source.contains("it.isValid && it.state != Display.STATE_OFF"))
        assertFalse(source.contains(".filter { it.isValid }\n"))
    }

    @Test
    fun ambiguousNonOffStatesRemainEligible() {
        val source = controllerSource()

        assertFalse(source.contains("Display.STATE_UNKNOWN"))
        assertFalse(source.contains("Display.STATE_DOZE"))
        assertFalse(source.contains("Display.STATE_DOZE_SUSPEND"))
    }
}
