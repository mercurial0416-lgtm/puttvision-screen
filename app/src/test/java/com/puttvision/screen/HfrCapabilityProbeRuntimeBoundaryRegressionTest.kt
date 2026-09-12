package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HfrCapabilityProbeRuntimeBoundaryRegressionTest {
    private fun probeSource(): String {
        val modulePath = "src/main/java/com/puttvision/screen/HfrCapabilityProbe.kt"
        val candidates = listOf(
            File(modulePath),
            File("app/$modulePath"),
            File("../app/$modulePath")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate $modulePath from ${File(".").absolutePath}")
    }

    @Test
    fun vendorMetadataReadsRemainRuntimeFailClosed() {
        val source = probeSource()
        val guardedReads = listOf(
            "chars.get(CameraCharacteristics.LENS_FACING)",
            "chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)",
            "chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)",
            "map.highSpeedVideoSizes",
            "map.getHighSpeedVideoFpsRangesFor(size)"
        )

        guardedReads.forEach { read ->
            val readIndex = source.indexOf(read)
            assertTrue("Expected HFR probe read to exist: $read", readIndex >= 0)
            val tail = source.substring(readIndex, minOf(source.length, readIndex + 260))
            assertTrue(
                "HFR vendor metadata read must fall back on RuntimeException: $read",
                tail.contains("catch (_: RuntimeException)")
            )
        }
    }

    @Test
    fun nullVendorHighSpeedArraysRemainFailClosed() {
        val source = probeSource()

        assertTrue(
            "Null high-speed size arrays must degrade to no HFR modes",
            source.contains("map.highSpeedVideoSizes?.toList() ?: emptyList()")
        )
        assertTrue(
            "Null FPS-range arrays must degrade to no ranges for that size",
            source.contains("map.getHighSpeedVideoFpsRangesFor(size)?.toList() ?: emptyList()")
        )
    }

    @Test
    fun hfrProbeDoesNotSwallowFatalJvmErrors() {
        val source = probeSource()

        assertFalse(
            "HFR probing must not catch Throwable because fatal JVM errors should remain visible",
            source.contains("catch (_: Throwable)")
        )
        assertFalse(
            "HFR probing must not catch Error because fatal JVM errors should remain visible",
            source.contains("catch (_: Error)")
        )
    }

    @Test
    fun cameraEnumerationAndCharacteristicsKeepSafeFallbacks() {
        val source = probeSource()
        val cameraIdRead = source.substringAfter("val cameraIds = try {").substringBefore("for (id in cameraIds)")
        assertTrue(
            "Camera IDs must be materialized inside the guarded read so null/platform failures stay fail-closed",
            cameraIdRead.contains("manager.cameraIdList.toList()")
        )
        assertTrue(cameraIdRead.contains("catch (_: CameraAccessException)"))
        assertTrue(cameraIdRead.contains("catch (_: RuntimeException)"))
        assertTrue(cameraIdRead.contains("return HfrCapabilities(emptyList())"))

        val characteristicsRead = source.substringAfter("val chars = try {").substringBefore("val lensFacing = try {")
        assertTrue(characteristicsRead.contains("catch (_: CameraAccessException)"))
        assertTrue(characteristicsRead.contains("catch (_: RuntimeException)"))
        assertTrue(characteristicsRead.contains("continue"))
    }

    @Test
    fun hfrSizeRankingUsesOverflowSafePixelArea() {
        val source = probeSource()

        assertTrue(
            "HFR size ranking must widen dimensions before multiplying vendor-provided sizes",
            source.contains("it.size.width.toLong() * it.size.height.toLong() / 100000L")
        )
        assertFalse(
            "HFR size ranking must not multiply dimensions as Int before widening",
            source.contains("it.size.width * it.size.height / 100000")
        )
    }
}
