package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCalibrationCameraContractTest {
    private fun mainActivitySource(): String = sourceFile("src/main/java/com/puttvision/screen/MainActivity.kt")

    private fun buildGradleSource(): String = sourceFile("build.gradle.kts")

    private fun sourceFile(modulePath: String): String {
        val candidates = listOf(
            File(modulePath),
            File("app/$modulePath"),
            File("../app/$modulePath")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate $modulePath from ${File(".").absolutePath}")
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

    @Test
    fun legacyTargetResolutionIsConfinedToValidatedAutoCalibrationPath() {
        val source = mainActivitySource()
        val occurrences = Regex("\\.setTargetResolution\\s*\\(").findAll(source).count()

        assertEquals(
            "Do not add another deprecated target-resolution path; migrate the validated auto-calibration path instead",
            1,
            occurrences
        )
    }

    @Test
    fun cameraXBaselineStaysResolutionSelectorCapable() {
        val gradle = buildGradleSource()
        val match = Regex("val\\s+cameraX\\s*=\\s*\"(\\d+)\\.(\\d+)\\.(\\d+)\"").find(gradle)
            ?: error("Unable to find the pinned CameraX version")
        val version = match.groupValues.drop(1).map(String::toInt)
        val resolutionSelectorBaseline = listOf(1, 3, 0)

        assertTrue(
            "CameraX must remain new enough for the ResolutionSelector migration path",
            compareVersions(version, resolutionSelectorBaseline) >= 0
        )
    }

    private fun compareVersions(left: List<Int>, right: List<Int>): Int {
        for (index in 0 until maxOf(left.size, right.size)) {
            val delta = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
            if (delta != 0) return delta
        }
        return 0
    }
}
