package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UnityRendererTransportFailureRegressionTest {
    private fun bridgeSource(): String {
        val candidates = listOf(
            File("src/main/java/com/puttvision/screen/UnityRendererBridge.kt"),
            File("app/src/main/java/com/puttvision/screen/UnityRendererBridge.kt")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("UnityRendererBridge.kt not found from ${File(".").absolutePath}")
    }

    @Test
    fun reflectedTransportFailureDisablesBridgeBeforeReturning() {
        val source = bridgeSource()
        val sendStart = source.indexOf("private fun send(methodName: String, payload: String): Boolean")
        val resolveStart = source.indexOf("private fun resolveUnitySendMessage()", startIndex = sendStart)
        assertTrue("send must exist before resolveUnitySendMessage", sendStart >= 0 && resolveStart > sendStart)

        val send = source.substring(sendStart, resolveStart)
        val invoke = send.indexOf("method.invoke(null, RECEIVER_GAME_OBJECT, methodName, payload)")
        val failureGuard = send.indexOf("if (!delivered)", startIndex = invoke)
        val clearCachedMethod = send.indexOf("reflectedMethod.set(null)", startIndex = failureGuard)
        val disableBridge = send.indexOf("enabled = false", startIndex = failureGuard)
        val returnDelivery = send.indexOf("return delivered", startIndex = disableBridge)

        assertTrue("Unity transport invocation must remain guarded", invoke >= 0)
        assertTrue("failed reflected delivery must enter fail-closed guard", failureGuard > invoke)
        assertTrue("failed transport must discard the cached reflected Method", clearCachedMethod > failureGuard)
        assertTrue("bridge must be disabled after transport failure", disableBridge > clearCachedMethod)
        assertTrue("bridge must fail closed before send returns", returnDelivery > disableBridge)
    }

    @Test
    fun lifecycleRecoveryCanResolveAReplacementUnityMethod() {
        val source = bridgeSource()
        val enableStart = source.indexOf("fun enableIfRuntimeAvailable(): Boolean")
        val sendStart = source.indexOf("private fun send(methodName: String, payload: String): Boolean", startIndex = enableStart)
        val resolveStart = source.indexOf("private fun resolveUnitySendMessage()", startIndex = sendStart)
        assertTrue("expected Unity bridge lifecycle and resolver sections", enableStart >= 0 && sendStart > enableStart && resolveStart > sendStart)

        val enable = source.substring(enableStart, sendStart)
        val send = source.substring(sendStart, resolveStart)
        val resolve = source.substring(resolveStart)

        assertTrue("lifecycle enable must probe runtime availability", enable.contains("enabled = isUnityRuntimeAvailable()"))
        assertTrue("transport failure must clear reflected method cache", send.contains("reflectedMethod.set(null)"))
        assertTrue("resolver must cache a newly resolved UnitySendMessage method", resolve.contains("reflectedMethod.compareAndSet(null, resolved)"))
    }
}
