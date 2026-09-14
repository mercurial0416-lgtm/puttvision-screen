package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UnityTvRuntimeLaunchResilienceRegressionTest {
    private fun runtimeSource(): String {
        val candidates = listOf(
            File("src/main/java/com/puttvision/screen/UnityTvRuntime.kt"),
            File("app/src/main/java/com/puttvision/screen/UnityTvRuntime.kt")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("UnityTvRuntime.kt not found from ${File(".").absolutePath}")
    }

    @Test
    fun eagerBridgeFailureCannotTurnSuccessfulActivityLaunchIntoLaunchFailure() {
        val source = runtimeSource()
        val launchGuard = source.indexOf("val launchFailure = runCatching {")
        val startActivity = source.indexOf("context.startActivity(intent, options.toBundle())", launchGuard)
        val criticalBoundary = source.indexOf("}.exceptionOrNull()", startActivity)
        val launchFailureHandling = source.indexOf("if (launchFailure != null)", criticalBoundary)
        val deferredBridge = source.indexOf(
            "runCatching { UnityRendererBridge.enableIfRuntimeAvailable() }",
            launchFailureHandling
        )
        val successfulReturn = source.indexOf("return true", deferredBridge)

        assertTrue("Unity Activity launch must retain an explicit failure boundary", launchGuard >= 0)
        assertTrue("startActivity must remain inside the critical launch boundary", startActivity > launchGuard)
        assertTrue("critical launch boundary must end immediately after startActivity", criticalBoundary > startActivity)
        assertTrue("launch failures must be handled before best-effort bridge activation", launchFailureHandling > criticalBoundary)
        assertTrue("eager renderer bridge activation must be isolated from launch failure", deferredBridge > launchFailureHandling)
        assertTrue("successful Activity launch must remain successful after best-effort bridge activation", successfulReturn > deferredBridge)
    }

    @Test
    fun unityReadyStillOwnsAuthoritativeBridgeActivationRetry() {
        val source = runtimeSource()
        val readyCallback = source.indexOf("fun onUnityReady(displayId: Int, launchSession: Long)")
        val retry = source.indexOf(
            "setupComplete = UnityRendererBridge.enableIfRuntimeAvailable()",
            readyCallback
        )

        assertTrue("Unity readiness callback must exist", readyCallback >= 0)
        assertTrue("onUnityReady must retry bridge activation authoritatively", retry > readyCallback)
    }
}
