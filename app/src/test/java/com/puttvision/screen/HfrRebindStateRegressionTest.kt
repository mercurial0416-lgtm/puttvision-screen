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
        val expected =
            "stability.release()provider.unbindAll()" +
                "recorder=nullvideoCapture=nullselectedFps=0" +
                "valselector=CameraSelector.DEFAULT_BACK_CAMERA"

        assertTrue(
            "Unbinding CameraX must invalidate the previous HFR recorder before capability probing can return early",
            compact.contains(expected)
        )
    }
}
