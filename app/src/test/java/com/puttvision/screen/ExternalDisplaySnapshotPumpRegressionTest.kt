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
            "Godot rollback snapshots must stay gated by a valid external presentation display",
            compact.contains("if(hasPresentationDisplay){V143GodotRenderBridge.publish(engine)}")
        )
        assertTrue(
            "refresh() must derive snapshot-pump activity from the currently selected valid presentation display",
            compact.contains("hasPresentationDisplay=display!=null")
        )
        assertTrue(
            "phone-only mode must use a lower snapshot-pump wake cadence while TV mode stays at 16 ms",
            compact.contains("if(hasPresentationDisplay)SNAPSHOT_ACTIVE_INTERVAL_MSelseSNAPSHOT_IDLE_INTERVAL_MS") &&
                compact.contains("SNAPSHOT_ACTIVE_INTERVAL_MS=16L") &&
                compact.contains("SNAPSHOT_IDLE_INTERVAL_MS=1000L")
        )
        assertTrue(
            "a newly attached HDMI/DeX presentation display must wake the idle pump immediately",
            compact.contains("if(!hadPresentationDisplay&&hasPresentationDisplay){handler.removeCallbacks(snapshotPump)handler.post(snapshotPump)}")
        )
        assertTrue(
            "stop() must clear presentation-display state before cancelling callbacks",
            compact.contains("started=falsehasPresentationDisplay=falsehandler.removeCallbacksAndMessages(null)")
        )
    }
}
