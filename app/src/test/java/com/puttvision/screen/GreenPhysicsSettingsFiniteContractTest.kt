package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenPhysicsSettingsFiniteContractTest {
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
    fun nonFiniteGreenSettingsAreRejectedBeforeRendererOrPhysicsUse() {
        val compact = greenPhysicsSource().replace(Regex("\\s+"), "")
        val settingsGuard = "!settingsAreFinite(settings)"
        val launchGuardIndex = compact.indexOf(settingsGuard)
        val publishIndex = compact.indexOf("UnityRendererBridge.publishShot(")
        val stepIndex = compact.indexOf("funstep(")
        val stepGuardIndex = compact.indexOf("if(!settingsAreFinite(settings))", stepIndex)
        val rigidStepIndex = compact.indexOf("V135RigidBallPhysics.step(", stepIndex)

        assertTrue("Launch must reject non-finite green settings", launchGuardIndex >= 0)
        assertTrue("Green settings must be validated before renderer publication", publishIndex > launchGuardIndex)
        assertTrue("Step must reject settings that become non-finite mid-simulation", stepGuardIndex > stepIndex)
        assertTrue("Step guard must run before rigid-ball integration", rigidStepIndex > stepGuardIndex)

        listOf(
            "settings.stimpMeters.isFinite()",
            "settings.holeDistanceM.isFinite()",
            "settings.sideSlopePct.isFinite()",
            "settings.longSlopePct.isFinite()",
            "settings.grainDirectionDeg.isFinite()",
            "settings.grainStrength01.isFinite()",
            "settings.moisture01.isFinite()",
            "settings.firmness01.isFinite()",
            "settings.trueness01.isFinite()"
        ).forEach { check ->
            assertTrue("Missing finite guard for $check", compact.contains(check))
        }
    }

    @Test
    fun terminalResultSanitizesNonFinitePositionDistanceAndElapsed() {
        val compact = greenPhysicsSource().replace(Regex("\\s+"), "")

        assertTrue(compact.contains("valfinishX=state.x.takeIf{it.isFinite()}?:0.0"))
        assertTrue(compact.contains("valfinishY=state.y.takeIf{it.isFinite()}?:0.0"))
        assertTrue(compact.contains("valcupY=settings.holeDistanceM.takeIf{it.isFinite()}?:finishY"))
        assertTrue(compact.contains("elapsedSec=state.elapsed.takeIf{it.isFinite()}?:0.0"))
    }
}
