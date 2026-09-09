package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UnityRuntimeFailureBridgeRegressionTest {
    private fun runtimeSource(): String {
        val candidates = listOf(
            File("src/main/java/com/puttvision/screen/UnityTvRuntime.kt"),
            File("app/src/main/java/com/puttvision/screen/UnityTvRuntime.kt")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("UnityTvRuntime.kt not found from ${File(".").absolutePath}")
    }

    @Test
    fun activeUnityFailureDisablesBridgeBeforeSessionIsCleared() {
        val source = runtimeSource()
        val failureStart = source.indexOf("fun onUnityFailure(")
        val finishStart = source.indexOf("fun finishCurrent()", startIndex = failureStart)
        assertTrue("onUnityFailure must exist before finishCurrent", failureStart >= 0 && finishStart > failureStart)

        val failure = source.substring(failureStart, finishStart)
        val matchGuard = failure.indexOf("if (!launchSessions.matches(displayId, launchSession)) return")
        val disableBridge = failure.indexOf("UnityRendererBridge.enabled = false")
        val clearSession = failure.indexOf("launchSessions.clearIf(displayId, launchSession)")

        assertTrue("stale Unity failures must be rejected before mutating bridge state", matchGuard >= 0)
        assertTrue("active Unity runtime failure must disable the renderer bridge", disableBridge > matchGuard)
        assertTrue("bridge must be disabled before the active launch session is cleared", clearSession > disableBridge)
    }

    @Test
    fun failedUnityLaunchDisablesBridgeBeforeSessionIsCleared() {
        val source = runtimeSource()
        val launchStart = source.indexOf("fun launch(context: Context, display: Display): Boolean")
        val readyStart = source.indexOf("fun isReadyOn(displayId: Int)", startIndex = launchStart)
        assertTrue("launch must exist before isReadyOn", launchStart >= 0 && readyStart > launchStart)

        val launch = source.substring(launchStart, readyStart)
        val catchStart = launch.indexOf("}.getOrElse { throwable ->")
        val matchGuard = launch.indexOf("if (launchSessions.matches(displayId, launchSession))", startIndex = catchStart)
        val disableBridge = launch.indexOf("UnityRendererBridge.enabled = false", startIndex = matchGuard)
        val clearSession = launch.indexOf("launchSessions.clearIf(displayId, launchSession)", startIndex = disableBridge)

        assertTrue("launch failure handler must exist", catchStart >= 0)
        assertTrue("only the active failed launch may mutate bridge state", matchGuard > catchStart)
        assertTrue("active launch failure must disable the renderer bridge", disableBridge > matchGuard)
        assertTrue("bridge must fail closed before the failed launch session is cleared", clearSession > disableBridge)
    }
}
