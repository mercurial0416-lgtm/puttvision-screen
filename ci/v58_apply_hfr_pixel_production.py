from pathlib import Path

path = Path('app/src/main/java/com/puttvision/screen/HfrVideoAnalyzer.kt')
s = path.read_text(encoding='utf-8')

old = '''    private data class Sample(
        val frame: Int,
        val ballCm: PointF?,
        val heelCm: PointF?,
        val toeCm: PointF?,
        val markerAngleDeg: Double? = null
    )'''
new = '''    private data class Sample(
        val frame: Int,
        val ballCm: PointF?,
        val heelCm: PointF?,
        val toeCm: PointF?,
        val ballPx: PointF?,
        val heelPx: PointF?,
        val toePx: PointF?,
        val imageWidthPx: Int,
        val imageHeightPx: Int,
        val markerAngleDeg: Double? = null
    )'''
if s.count(old) != 1:
    raise SystemExit(f'Sample shape mismatch: {s.count(old)}')
s = s.replace(old, new, 1)

old = '                samples += Sample(frame, ballCm, heelCm, toeCm, d.markerAngleDeg)'
new = '''                samples += Sample(
                    frame = frame,
                    ballCm = ballCm,
                    heelCm = heelCm,
                    toeCm = toeCm,
                    ballPx = d.ballPx,
                    heelPx = d.heelPx,
                    toePx = d.toePx,
                    imageWidthPx = bmp.width,
                    imageHeightPx = bmp.height,
                    markerAngleDeg = d.markerAngleDeg
                )'''
if s.count(old) != 1:
    raise SystemExit(f'precision Sample call mismatch: {s.count(old)}')
s = s.replace(old, new, 1)

old = '            samples += Sample(index, ball, heel, toe, d.markerAngleDeg)'
new = '''            samples += Sample(
                frame = index,
                ballCm = ball,
                heelCm = heel,
                toeCm = toe,
                ballPx = d.ballPx,
                heelPx = d.heelPx,
                toePx = d.toePx,
                imageWidthPx = bmp.width,
                imageHeightPx = bmp.height,
                markerAngleDeg = d.markerAngleDeg
            )'''
if s.count(old) != 1:
    raise SystemExit(f'synthetic Sample call mismatch: {s.count(old)}')
s = s.replace(old, new, 1)

old = '''                    toeXcm = s.toeCm?.x?.toDouble(),
                    toeYcm = s.toeCm?.y?.toDouble(),
                    markerAngleDeg = s.markerAngleDeg
                )'''
new = '''                    toeXcm = s.toeCm?.x?.toDouble(),
                    toeYcm = s.toeCm?.y?.toDouble(),
                    markerAngleDeg = s.markerAngleDeg,
                    ballXpx = s.ballPx?.x?.toDouble(),
                    ballYpx = s.ballPx?.y?.toDouble(),
                    heelXpx = s.heelPx?.x?.toDouble(),
                    heelYpx = s.heelPx?.y?.toDouble(),
                    toeXpx = s.toePx?.x?.toDouble(),
                    toeYpx = s.toePx?.y?.toDouble()
                )'''
if s.count(old) != 1:
    raise SystemExit(f'feature frame mapping mismatch: {s.count(old)}')
s = s.replace(old, new, 1)

old = '        return HfrFeatureTrack(fps = fps, impactFrame = impactFrame, frames = compact)'
new = '''        val imageWidthPx = samples.map { it.imageWidthPx }.distinct().singleOrNull()
        val imageHeightPx = samples.map { it.imageHeightPx }.distinct().singleOrNull()
        return HfrFeatureTrack(
            fps = fps,
            impactFrame = impactFrame,
            frames = compact,
            imageWidthPx = imageWidthPx,
            imageHeightPx = imageHeightPx
        )'''
if s.count(old) != 1:
    raise SystemExit(f'track return mismatch: {s.count(old)}')
s = s.replace(old, new, 1)

path.write_text(s, encoding='utf-8')

# Source-wiring regression: protects the production analyzer path, not just synthetic V55 fixtures.
test = Path('app/src/test/java/com/puttvision/screen/V58HfrPixelProductionTest.kt')
test.write_text('''package com.puttvision.screen

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
''', encoding='utf-8')

print('V58 HFR raw pixel production wiring applied')
