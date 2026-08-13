package com.puttvision.screen

import android.graphics.PointF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class VisionOverlay(
    val ballImage: PointF?,
    val heelImage: PointF?,
    val toeImage: PointF?,
    val frameInfo: FrameInfo
)

class ShotVisionAnalyzer(
    private val homography: Homography,
    private val tracker: ShotTracker,
    private val onOverlay: (VisionOverlay) -> Unit,
    private val onQuality: (LiveQualityGateSnapshot) -> Unit = {},
    private val baselineMarkerPoints: List<PointF> = emptyList(),
    private val onCalibrationDrift: (CalibrationDriftSnapshot) -> Unit = {},
    private val onShotReady: (ShotMetrics) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastBall: PointF? = null
    private var lastHeel: PointF? = null
    private var lastToe: PointF? = null
    private val qualityEstimator = CameraQualityEstimator()
    private var qualityFrame = 0
    private var driftFrame = 0
    private var ballReadiness = 0.0
    private var putterReadiness = 0.0
    private val driftWatchdog = baselineMarkerPoints.takeIf { it.size == 4 }?.let { CalibrationDriftWatchdog(it) }

    override fun analyze(image: ImageProxy) {
        try {
            val sampledQuality = if (++qualityFrame % 5 == 0) qualityEstimator.evaluate(image) else null
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val y = ByteArray(yPlane.buffer.remaining()).also { yPlane.buffer.get(it) }
            val u = ByteArray(uPlane.buffer.remaining()).also { uPlane.buffer.get(it) }
            val v = ByteArray(vPlane.buffer.remaining()).also { vPlane.buffer.get(it) }

            val w = image.width
            val h = image.height

            if (++driftFrame % 18 == 0) {
                driftWatchdog?.evaluateLuma(
                    luma = y,
                    width = w,
                    height = h,
                    rowStride = yPlane.rowStride,
                    pixelStride = yPlane.pixelStride
                )?.let(onCalibrationDrift)
            }

            val ball = findWhiteBall(
                y, w, h, yPlane.rowStride, yPlane.pixelStride, lastBall
            )
            if (ball != null) lastBall = ball

            val heel = findColorCentroid(
                y, u, v, w, h,
                yPlane.rowStride, yPlane.pixelStride,
                uPlane.rowStride, uPlane.pixelStride,
                vPlane.rowStride, vPlane.pixelStride,
                ball ?: lastBall,
                ColorTarget.ORANGE,
                lastHeel
            )
            if (heel != null) lastHeel = heel

            val toe = findColorCentroid(
                y, u, v, w, h,
                yPlane.rowStride, yPlane.pixelStride,
                uPlane.rowStride, uPlane.pixelStride,
                vPlane.rowStride, vPlane.pixelStride,
                ball ?: lastBall,
                ColorTarget.BLUE,
                lastToe
            )
            if (toe != null) lastToe = toe

            ballReadiness = ballReadiness * 0.82 + (if (ball != null) 1.0 else 0.0) * 0.18
            putterReadiness = putterReadiness * 0.82 + (if (heel != null && toe != null) 1.0 else 0.0) * 0.18
            sampledQuality?.let { onQuality(LiveQualityGate.build(it, ballReadiness, putterReadiness)) }

            val t = image.imageInfo.timestamp

            ball?.let {
                val p = homography.map(it)
                if (p.x.isFinite() && p.y.isFinite()) tracker.addBall(BallSample(p, t))
            }

            if (heel != null && toe != null) {
                val heelCm = homography.map(heel)
                val toeCm = homography.map(toe)
                if (heelCm.x.isFinite() && heelCm.y.isFinite() &&
                    toeCm.x.isFinite() && toeCm.y.isFinite()
                ) {
                    val center = PointF(
                        (heelCm.x + toeCm.x) / 2f,
                        (heelCm.y + toeCm.y) / 2f
                    )
                    tracker.addHead(HeadSample(center, heelCm, toeCm, t))
                }
            }

            onOverlay(
                VisionOverlay(
                    ballImage = ball,
                    heelImage = heel,
                    toeImage = toe,
                    frameInfo = FrameInfo(w, h, image.imageInfo.rotationDegrees)
                )
            )

            tracker.maybeFinalize()?.let(onShotReady)
        } finally {
            image.close()
        }
    }

    private enum class ColorTarget { ORANGE, BLUE }

    private fun findWhiteBall(
        y: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        previous: PointF?
    ): PointF? {
        val step = 2
        val sw = width / step
        val sh = height / step
        val mask = BooleanArray(sw * sh)

        fun luma(px: Int, py: Int): Int {
            val idx = py * rowStride + px * pixelStride
            return if (idx in y.indices) y[idx].toInt() and 0xff else 0
        }

        for (sy in 1 until sh - 1) {
            val py = sy * step
            for (sx in 1 until sw - 1) {
                val px = sx * step
                if (luma(px, py) >= 205) mask[sy * sw + sx] = true
            }
        }

        val visited = BooleanArray(mask.size)
        val stack = IntArray(mask.size.coerceAtMost(60000))
        var best: PointF? = null
        var bestScore = -1f

        for (sy in 1 until sh - 1) {
            for (sx in 1 until sw - 1) {
                val start = sy * sw + sx
                if (!mask[start] || visited[start]) continue

                var sp = 0
                stack[sp++] = start
                visited[start] = true

                var area = 0
                var minX = sx
                var maxX = sx
                var minY = sy
                var maxY = sy
                var sumX = 0.0
                var sumY = 0.0

                while (sp > 0) {
                    val idx = stack[--sp]
                    val cy = idx / sw
                    val cx = idx - cy * sw
                    area++
                    sumX += cx
                    sumY += cy
                    minX = min(minX, cx)
                    maxX = max(maxX, cx)
                    minY = min(minY, cy)
                    maxY = max(maxY, cy)

                    val ns = intArrayOf(
                        idx - 1, idx + 1, idx - sw, idx + sw,
                        idx - sw - 1, idx - sw + 1, idx + sw - 1, idx + sw + 1
                    )
                    for (n in ns) {
                        if (n !in mask.indices || visited[n] || !mask[n]) continue
                        val ny = n / sw
                        val nx = n - ny * sw
                        if (abs(nx - cx) > 1 || abs(ny - cy) > 1) continue
                        if (sp < stack.size) {
                            visited[n] = true
                            stack[sp++] = n
                        }
                    }
                }

                if (area !in 7..900) continue
                val bw = maxX - minX + 1
                val bh = maxY - minY + 1
                if (bw < 3 || bh < 3) continue
                val aspect = bw.toFloat() / bh.toFloat()
                if (aspect !in 0.55f..1.8f) continue
                val fill = area.toFloat() / (bw * bh).toFloat()
                if (fill !in 0.32f..0.96f) continue

                val cx = (sumX / area * step).toFloat()
                val cy = (sumY / area * step).toFloat()
                val p = PointF(cx, cy)

                var score = (1f - min(1f, abs(1f - aspect))) * 0.6f + fill * 0.4f
                previous?.let {
                    val d = hypot((cx - it.x).toDouble(), (cy - it.y).toDouble()).toFloat()
                    score += max(0f, 1f - d / 180f) * 0.8f
                }

                if (score > bestScore) {
                    bestScore = score
                    best = p
                }
            }
        }

        return if (bestScore > 0.48f) best else null
    }

    private fun findColorCentroid(
        y: ByteArray,
        u: ByteArray,
        v: ByteArray,
        width: Int,
        height: Int,
        yRow: Int,
        yPix: Int,
        uRow: Int,
        uPix: Int,
        vRow: Int,
        vPix: Int,
        roiCenter: PointF?,
        target: ColorTarget,
        previous: PointF?
    ): PointF? {
        val step = 4
        val radius = if (previous != null) 150f else 260f
        val center = previous ?: roiCenter ?: PointF(width / 2f, height * 0.68f)

        var sx = 0.0
        var sy = 0.0
        var count = 0

        val minX = max(0, (center.x - radius).toInt())
        val maxX = min(width - 1, (center.x + radius).toInt())
        val minY = max(0, (center.y - radius).toInt())
        val maxY = min(height - 1, (center.y + radius).toInt())

        var py = minY
        while (py <= maxY) {
            var px = minX
            while (px <= maxX) {
                val yi = py * yRow + px * yPix
                val ui = (py / 2) * uRow + (px / 2) * uPix
                val vi = (py / 2) * vRow + (px / 2) * vPix
                if (yi in y.indices && ui in u.indices && vi in v.indices) {
                    val yy = y[yi].toInt() and 0xff
                    val uu = u[ui].toInt() and 0xff
                    val vv = v[vi].toInt() and 0xff

                    val rgb = yuvToRgb(yy, uu, vv)
                    val match = when (target) {
                        ColorTarget.ORANGE ->
                            rgb.r > 150 && rgb.g in 55..190 && rgb.b < 125 &&
                                    rgb.r > rgb.g * 1.12 && rgb.g > rgb.b * 1.05
                        ColorTarget.BLUE ->
                            rgb.b > 130 && rgb.r < 150 &&
                                    rgb.b > rgb.r * 1.15 && rgb.b > rgb.g * 1.05
                    }

                    if (match) {
                        sx += px
                        sy += py
                        count++
                    }
                }
                px += step
            }
            py += step
        }

        if (count < 4) return null
        return PointF((sx / count).toFloat(), (sy / count).toFloat())
    }

    private data class Rgb(val r: Int, val g: Int, val b: Int)

    private fun yuvToRgb(y: Int, u: Int, v: Int): Rgb {
        val c = y - 16
        val d = u - 128
        val e = v - 128
        val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
        val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
        val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)
        return Rgb(r, g, b)
    }
}
