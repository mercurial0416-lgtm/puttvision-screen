package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCalibrationCameraContractTest {
    private fun mainActivitySource(): String {
        val candidates = listOf(
            File("src/main/java/com/puttvision/screen/MainActivity.kt"),
            File("app/src/main/java/com/puttvision/screen/MainActivity.kt"),
            File("../app/src/main/java/com/puttvision/screen/MainActivity.kt")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate MainActivity.kt from ${File(".").absolutePath}")
    }

    @Test
    fun autoCalibrationAnalysisKeepsResolutionAndBackpressureContract() {
        val source = mainActivitySource()
        val compact = source.replace(Regex("\\s+"), "")

        val analysisConfig =
            "ImageAnalysis.Builder()" +
                ".setTargetResolution(Size(640,480))" +
                ".setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)" +
                ".build()"

        assertTrue(
            "Auto-calibration analysis must preserve the validated 640x480 + latest-frame contract",
            compact.contains(analysisConfig)
        )
    }
}
