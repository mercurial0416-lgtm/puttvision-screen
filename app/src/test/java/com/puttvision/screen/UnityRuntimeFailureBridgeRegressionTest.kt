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
    fun unavailableUnityLaunchFailsClosedBeforeReturning() {
        val source = runtimeSource()
        val launchStart = source.indexOf("fun launch(context: Context, display: Display): Boolean")
        val readyStart = source.indexOf("fun isReadyOn(displayId: Int)", startIndex = launchStart)
        assertTrue("launch must exist before isReadyOn", launchStart >= 0 && readyStart > launchStart)

        val launch = source.substring(launchStart, readyStart)
        val unavailableGuard = launch.indexOf("if (!isAvailable()) {")
        val clearReady = launch.indexOf("setupComplete = false", startIndex = unavailableGuard)
        val failureState = launch.indexOf("lastFailure = \"Unity runtime unavailable\"", startIndex = clearReady)
        val clearSession = launch.indexOf("launchSessions.clear()", startIndex = failureState)
        val disableBridge = launch.indexOf("UnityRendererBridge.enabled = false", startIndex = clearSession)
        val returnFalse = launch.indexOf("return false", startIndex = disableBridge)

        assertTrue("unavailable Unity runtime must be handled explicitly", unavailableGuard >= 0)
        assertTrue("unavailable runtime must clear setupComplete", clearReady > unavailableGuard)
        assertTrue("unavailable runtime must expose a failure reason", failureState > clearReady)
        assertTrue("unavailable runtime must clear any stale launch session", clearSession > failureState)
        assertTrue("unavailable runtime must disable the renderer bridge", disableBridge > clearSession)
        assertTrue("launch may return false only after state fails closed", returnFalse > disableBridge)
    }

    @Test
    fun activeUnityReadyRequiresBridgeBeforeReportingSetupComplete() {
        val source = runtimeSource()
        val readyStart = source.indexOf("fun onUnityReady(")
        val failureStart = source.indexOf("fun onUnityFailure(", startIndex = readyStart)
        assertTrue("onUnityReady must exist before onUnityFailure", readyStart >= 0 && failureStart > readyStart)

        val ready = source.substring(readyStart, failureStart)
        val matchGuard = ready.indexOf("if (!launchSessions.matches(displayId, launchSession)) return")
        val bridgeGate = ready.indexOf("setupComplete = UnityRendererBridge.enableIfRuntimeAvailable()")
        val failureState = ready.indexOf("lastFailure = if (setupComplete) null else \"Unity renderer bridge unavailable\"")
        val failedReadyGuard = ready.indexOf("if (!setupComplete) {", startIndex = failureState)
        val clearSession = ready.indexOf("launchSessions.clearIf(displayId, launchSession)", startIndex = failedReadyGuard)

        assertTrue("stale Unity readiness must be rejected before mutating runtime state", matchGuard >= 0)
        assertTrue("Unity readiness must be gated by a usable renderer bridge", bridgeGate > matchGuard)
        assertTrue("bridge-unavailable readiness must remain observable as a failure state", failureState > bridgeGate)
        assertTrue("bridge-unavailable readiness must enter a fail-closed branch", failedReadyGuard > failureState)
        assertTrue("failed readiness must retire the launch session so duplicate callbacks stay stale", clearSession > failedReadyGuard)
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

    @Test
    fun finishingUnityClearsFailureBeforeRetiringSession() {
        val source = runtimeSource()
        val finishStart = source.indexOf("fun finishCurrent()")
        assertTrue("finishCurrent must exist", finishStart >= 0)

        val finish = source.substring(finishStart)
        val clearReady = finish.indexOf("setupComplete = false")
        val clearFailure = finish.indexOf("lastFailure = null", startIndex = clearReady)
        val clearSession = finish.indexOf("launchSessions.clear()", startIndex = clearFailure)
        val disableBridge = finish.indexOf("UnityRendererBridge.enabled = false", startIndex = clearSession)

        assertTrue("finishing Unity must clear setupComplete", clearReady >= 0)
        assertTrue("finishing Unity must clear stale failure state", clearFailure > clearReady)
        assertTrue("failure state must clear before the launch session is retired", clearSession > clearFailure)
        assertTrue("renderer bridge must still fail closed during finish", disableBridge > clearSession)
    }
}