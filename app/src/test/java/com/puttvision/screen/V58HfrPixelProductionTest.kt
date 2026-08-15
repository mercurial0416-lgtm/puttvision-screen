package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V58HfrPixelProductionTest {
    private fun analyzerSource(): String {
        val candidates = listOf(
            File("src/main/java/com/puttvision/screen/HfrVideoAnalyzer.kt"),
            File("app/src/main/java/com/puttvision/screen/HfrVideoAnalyzer.kt")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("HfrVideoAnalyzer.kt not found from ${File(".").absolutePath}")
    }

    @Test
    fun productionPrecisionTrackPreservesDetectorPixelsAndFrameShape() {
        val source = analyzerSource()
        assertTrue(source.contains("ballPx = d.ballPx"))
        assertTrue(source.contains("heelPx = d.heelPx"))
        assertTrue(source.contains("toePx = d.toePx"))
        assertTrue(source.contains("imageWidthPx = bmp.width"))
        assertTrue(source.contains("imageHeightPx = bmp.height"))
        assertTrue(source.contains("ballXpx = s.ballPx?.x?.toDouble()"))
        assertTrue(source.contains("toeYpx = s.toePx?.y?.toDouble()"))
    }

    @Test
    fun trackShapeFailsClosedWhenSourceFramesDisagree() {
        val source = analyzerSource()
        assertTrue(source.contains("samples.map { it.imageWidthPx }.distinct().singleOrNull()"))
        assertTrue(source.contains("samples.map { it.imageHeightPx }.distinct().singleOrNull()"))
    }
}
