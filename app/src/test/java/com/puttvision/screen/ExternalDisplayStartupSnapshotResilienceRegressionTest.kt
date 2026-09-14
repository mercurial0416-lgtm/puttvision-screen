package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDisplayStartupSnapshotResilienceRegressionTest {
    private fun controllerSource(): String {
        val candidates = listOf(
            File("src/main/java/com/puttvision/screen/ExternalDisplayController.kt"),
            File("app/src/main/java/com/puttvision/screen/ExternalDisplayController.kt")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("ExternalDisplayController.kt not found from ${File(".").absolutePath}")
    }

    @Test
    fun controllerStartCannotBeStrandedByWarmupSnapshotFailure() {
        val source = controllerSource()
        val start = source.indexOf("fun start() {")
        val started = source.indexOf("started = true", start)
        val guardedPublish = source.indexOf(
            "runCatching { V143GodotRenderBridge.publish(engine) }",
            started
        )
        val postPump = source.indexOf("handler.post(snapshotPump)", guardedPublish)
        val registerListener = source.indexOf("dm.registerDisplayListener(this, handler)", postPump)
        val refresh = source.indexOf("refresh()", registerListener)

        assertTrue("controller start must exist", start >= 0)
        assertTrue("controller must claim started state before warm-up", started > start)
        assertTrue("warm-up snapshot publication must be exception-isolated", guardedPublish > started)
        assertTrue("snapshot pump must still be registered after warm-up", postPump > guardedPublish)
        assertTrue("display listener must still register after warm-up", registerListener > postPump)
        assertTrue("initial refresh must still run after listener registration", refresh > registerListener)
    }

    @Test
    fun godotLaunchCannotBeBlockedByWarmupSnapshotFailure() {
        val source = controllerSource()
        val launch = source.indexOf("private fun launchGodot(display: Display, unityReason: String? = null)")
        val clearFailure = source.indexOf("V143GodotRuntime.lastFailure = null", launch)
        val guardedPublish = source.indexOf(
            "runCatching { V143GodotRenderBridge.publish(engine) }",
            clearFailure
        )
        val generation = source.indexOf("val launchGeneration = ++godotLaunchGeneration", guardedPublish)
        val startActivity = source.indexOf("context.startActivity(intent, options.toBundle())", generation)

        assertTrue("Godot launch path must exist", launch >= 0)
        assertTrue("Godot launch state must reset before warm-up", clearFailure > launch)
        assertTrue("Godot warm-up snapshot publication must be exception-isolated", guardedPublish > clearFailure)
        assertTrue("launch generation must advance after best-effort warm-up", generation > guardedPublish)
        assertTrue("Godot Activity launch must remain reachable after warm-up", startActivity > generation)
    }
}
