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
            Integer.TYPE,
            java.lang.Long.TYPE,
        )
        assertEquals(Void.TYPE, ready.returnType)

        val failure = runtime.getDeclaredMethod(
            "onUnityFailure",
            Integer.TYPE,
            java.lang.Long.TYPE,
            String::class.java,
        )
        assertEquals(Void.TYPE, failure.returnType)
    }

    @Test
    fun unitySourceCallsExactKotlinCallbackContract() {
        val userDir = requireNotNull(System.getProperty("user.dir")) { "user.dir is unavailable" }
        val repoRoot = generateSequence(File(userDir).absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "unity/Assets/PuttVision/Scripts/Bootstrap/PuttVisionAndroidStatusBridge.cs").isFile }
            ?: error("Could not locate repository root from $userDir")

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
