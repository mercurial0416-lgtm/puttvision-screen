package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HfrRecordingStartFailureRegressionTest {
    private fun controllerSource(): String {
        val modulePath = "src/main/java/com/puttvision/screen/HighSpeedCaptureController.kt"
        val candidates = listOf(File(modulePath), File("app/$modulePath"), File("../app/$modulePath"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate $modulePath from ${File(".").absolutePath}")
    }

    @Test
    fun synchronousRecordingStartFailureCleansStateAndFallsBackThroughFinalize() {
        val source = controllerSource()
        val compact = source.replace(Regex("\\s+"), "")

        assertTrue(
            "HFR recording startup must be guarded because CameraX may throw before emitting Finalize",
            compact.contains("try{recording=rec.prepareRecording(context,output).start(callbackExecutor)")
        )
        assertTrue(
            "A synchronous startup failure must clear active recording state and the temp file",
            compact.contains("catch(t:Throwable){if(activeFile==file)activeFile=nullrecording=nullfailureCircuit.recordFailure()runCatching{file.delete()}")
        )
        assertTrue(
            "Startup failures must rejoin the normal HFR fallback path via onFinalize",
            compact.contains("callbackExecutor.execute{onFinalize(null,fpsAtStart,t)}")
        )
    }
}
