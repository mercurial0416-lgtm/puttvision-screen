package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HfrRebindStateRegressionTest {
    private fun controllerSource(): String {
        val modulePath = "src/main/java/com/puttvision/screen/HighSpeedCaptureController.kt"
        val candidates = listOf(File(modulePath), File("app/$modulePath"), File("../app/$modulePath"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate $modulePath from ${File(".").absolutePath}")
    }

    @Test
    fun rebindClearsPreviousRecorderBeforeAnyCapabilityEarlyReturn() {
        val compact = controllerSource().replace(Regex("\\s+"), "")
        val unbind = compact.indexOf("provider.unbindAll()")
        val clearRecorder = compact.indexOf("recorder=null", startIndex = unbind.coerceAtLeast(0))
        val clearCapture = compact.indexOf("videoCapture=null", startIndex = clearRecorder.coerceAtLeast(0))
        val clearFps = compact.indexOf("selectedFps=0", startIndex = clearCapture.coerceAtLeast(0))
        val capabilityProbe = compact.indexOf(
            "valselector=CameraSelector.DEFAULT_BACK_CAMERA",
            startIndex = clearFps.coerceAtLeast(0)
        )

        assertTrue(
            "Unbinding CameraX must invalidate the previous HFR recorder before capability probing can return early",
            unbind >= 0 &&
                clearRecorder > unbind &&
                clearCapture > clearRecorder &&
                clearFps > clearCapture &&
                capabilityProbe > clearFps
        )
    }
}
