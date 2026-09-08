package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDisplayReconnectRegressionTest {
    private fun controllerSource(): String {
        val relative = "src/main/java/com/puttvision/screen/ExternalDisplayController.kt"
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate ExternalDisplayController.kt from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun removalClearsRendererAndFailureStateBeforeRefresh() {
        val source = controllerSource()
        val callbackStart = source.indexOf("override fun onDisplayRemoved(displayId: Int) {")
        val callbackEnd = source.indexOf("override fun onDisplayChanged", callbackStart)
        require(callbackStart >= 0 && callbackEnd > callbackStart) {
            "Unable to locate onDisplayRemoved callback"
        }
        val callback = source.substring(callbackStart, callbackEnd)

        val stopUnity = callback.indexOf("if (unityDisplayId == displayId) stopUnity()")
        val stopGodot = callback.indexOf("if (godotDisplayId == displayId) stopGodot()")
        val clearUnityFailure = callback.indexOf("if (unityFailedForDisplayId == displayId) unityFailedForDisplayId = null")
        val clearGodotFailure = callback.indexOf("if (godotFailedForDisplayId == displayId) godotFailedForDisplayId = null")
        val refresh = callback.lastIndexOf("refresh()")

        assertTrue("removed Unity renderer must be stopped", stopUnity >= 0)
        assertTrue("removed Godot renderer must be stopped", stopGodot >= 0)
        assertTrue("Unity failure latch must be cleared", clearUnityFailure >= 0)
        assertTrue("Godot failure latch must be cleared", clearGodotFailure >= 0)
        assertTrue("refresh must run after renderer teardown", refresh > stopUnity && refresh > stopGodot)
        assertTrue(
            "refresh must run after failure latches are cleared so a reused displayId can relaunch",
            refresh > clearUnityFailure && refresh > clearGodotFailure
        )
    }
}
