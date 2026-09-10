package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDisplayDismissSafetyRegressionTest {
    private fun source(): String {
        val candidates = listOf(
            File("src/main/java/com/puttvision/screen/ExternalDisplayController.kt"),
            File("app/src/main/java/com/puttvision/screen/ExternalDisplayController.kt"),
            File("../app/src/main/java/com/puttvision/screen/ExternalDisplayController.kt")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate ExternalDisplayController.kt from ${File(".").absolutePath}")
    }

    @Test
    fun fallbackPresentationIsClearedBeforeBestEffortDismiss() {
        val source = source()
        val helperStart = source.indexOf("private fun dismissPresentationSafely()")
        val helperEnd = source.indexOf("private fun showFallback", helperStart)
        assertTrue("safe dismissal helper must exist", helperStart >= 0 && helperEnd > helperStart)

        val helper = source.substring(helperStart, helperEnd).replace(Regex("\\s+"), "")
        assertTrue(
            "presentation ownership must clear before dismiss() can fail",
            helper.contains("valcurrent=presentation?:return") &&
                helper.indexOf("presentation=null") < helper.indexOf("current.dismiss()")
        )
        assertTrue("dismiss failure must not escape HDMI/DeX callbacks", helper.contains("catch(_:Throwable){}"))

        val lifecycle = source.substring(0, helperStart) + source.substring(helperEnd)
        assertFalse(
            "controller lifecycle paths must use the safe helper instead of direct retained-presentation dismiss",
            lifecycle.contains("presentation?.dismiss()")
        )
    }
}
