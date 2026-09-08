package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnityBridgeAbiContractTest {
    @Test
    fun kotlinCallbacksKeepJvmAbiExpectedByUnity() {
        val runtime = Class.forName("com.puttvision.screen.UnityTvRuntime")

        val ready = runtime.getDeclaredMethod(
            "onUnityReady",
            Int::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
        )
        assertEquals(Void.TYPE, ready.returnType)

        val failure = runtime.getDeclaredMethod(
            "onUnityFailure",
            Int::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            String::class.java,
        )
        assertEquals(Void.TYPE, failure.returnType)
    }

    @Test
    fun unitySourceCallsExactKotlinCallbackContract() {
        val repoRoot = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "unity/Assets/PuttVision/Scripts/Bootstrap/PuttVisionAndroidStatusBridge.cs").isFile }
            ?: error("Could not locate repository root from ${System.getProperty("user.dir")}")

        val bridge = File(
            repoRoot,
            "unity/Assets/PuttVision/Scripts/Bootstrap/PuttVisionAndroidStatusBridge.cs",
        ).readText()

        assertTrue(
            "Unity ready callback must forward displayId and launchSession in ABI order",
            bridge.contains("runtime.CallStatic(\"onUnityReady\", displayId, launchSession);"),
        )
        assertTrue(
            "Unity failure callback must forward displayId, launchSession, then message in ABI order",
            bridge.contains("runtime.CallStatic(\"onUnityFailure\", displayId, launchSession, message ?? \"unknown\");"),
        )
        assertTrue(
            "Unity launch session extra must remain aligned with Android",
            bridge.contains("private const string LaunchSessionExtra = \"pv_launch_session\";"),
        )
    }
}
