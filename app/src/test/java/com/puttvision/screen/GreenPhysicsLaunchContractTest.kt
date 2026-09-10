package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenPhysicsLaunchContractTest {
    private fun greenPhysicsSource(): String {
        val modulePath = "src/main/java/com/puttvision/screen/GreenPhysics.kt"
        val candidates = listOf(
            File(modulePath),
            File("app/$modulePath"),
            File("../app/$modulePath")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate $modulePath from ${File(".").absolutePath}")
    }

    @Test
    fun launchRejectsNonFiniteShotAndOriginBeforePublishingOrInitializingPhysics() {
        val source = greenPhysicsSource()
        val compact = source.replace(Regex("\\s+"), "")
        val guard =
            "!metrics.ballSpeedMps.isFinite()||!metrics.launchAngleDeg.isFinite()||" +
                "!startX.isFinite()||!startY.isFinite()"
        val guardIndex = compact.indexOf(guard)
        val publishIndex = compact.indexOf("UnityRendererBridge.publishShot(")
        val initializeIndex = compact.indexOf("V135RigidBallPhysics.initialize(")

        assertTrue("Launch must reject non-finite speed, angle, and origin values", guardIndex >= 0)
        assertTrue("Non-finite launch inputs must be rejected before renderer publication", publishIndex > guardIndex)
        assertTrue("Non-finite launch inputs must be rejected before rigid-ball initialization", initializeIndex > guardIndex)
        assertTrue(
            "Rejected launch inputs must return a stopped state rather than propagating NaN/Infinity",
            compact.contains("running=false,trail=mutableListOf(safeXtosafeY)")
        )
    }
}
