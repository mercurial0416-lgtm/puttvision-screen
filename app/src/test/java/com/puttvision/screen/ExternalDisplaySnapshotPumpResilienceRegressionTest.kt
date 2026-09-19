package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDisplaySnapshotPumpResilienceRegressionTest {
    private fun controllerSource(): String {
        val candidates = listOf(
            File("src/main/java/com/puttvision/screen/ExternalDisplayController.kt"),
            File("app/src/main/java/com/puttvision/screen/ExternalDisplayController.kt")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("ExternalDisplayController.kt not found from ${File(".").absolutePath}")
    }

    @Test
    fun snapshotPublishFailureCannotTerminatePeriodicPump() {
        val source = controllerSource()
        val pumpStart = source.indexOf("private val snapshotPump = object : Runnable")
        assertTrue("snapshot pump must exist", pumpStart >= 0)

        val runStart = source.indexOf("override fun run()", startIndex = pumpStart)
        val guardedPublish = source.indexOf(
            "runCatching { V143GodotRenderBridge.publish(engine) }",
            startIndex = runStart
        )
        val reschedule = source.indexOf("handler.postDelayed(", startIndex = guardedPublish)
        val pumpEnd = source.indexOf("\n    fun start()", startIndex = reschedule)

        assertTrue("snapshot publish must be guarded against transient renderer failures", guardedPublish > runStart)
        assertTrue("pump must reschedule after guarded publication", reschedule > guardedPublish)
        assertTrue("reschedule must remain inside snapshot pump", pumpEnd > reschedule)
    }
}
