package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDisplaySnapshotPumpRegressionTest {
    private fun controllerSource(): String {
        val modulePath = "src/main/java/com/puttvision/screen/ExternalDisplayController.kt"
        val candidates = listOf(
            File(modulePath),
            File("app/$modulePath"),
            File("../app/$modulePath")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate $modulePath from ${File(".").absolutePath}")
    }

    @Test
    fun godotSnapshotPumpRunsOnlyWithPresentationDisplay() {
        val compact = controllerSource().replace(Regex("\\s+"), "")

        assertTrue(
            "The 16 ms Godot rollback snapshot pump must stay idle without an external presentation display",
            compact.contains("if(hasPresentationDisplay){V143GodotRenderBridge.publish(engine)}")
        )
        assertTrue(
            "refresh() must derive snapshot-pump activity from the currently selected valid presentation display",
            compact.contains("hasPresentationDisplay=display!=nullif(display==null)")
        )
        assertTrue(
            "stop() must clear presentation-display state before cancelling callbacks",
            compact.contains("started=falsehasPresentationDisplay=falsehandler.removeCallbacksAndMessages(null)")
        )
    }
}
