package com.puttvision.screen

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.camera.view.PreviewView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class PreviewImpactDetector(private val preview: PreviewView) {
    private var baseline: PointF? = null
    private var previous: PointF? = null
    private var previousNs: Long = 0L
    private var stable = 0

    fun reset() {
        baseline = null
        previous = null
        previousNs = 0L
        stable = 0
    }

    /** Legacy boolean wrapper. */
    fun sampleMoved(): Boolean = sampleImpact(null) != null

    /**
     * Cheap preview-only estimate used to launch TV motion immediately. Final numbers always
     * come from HFR/NORMAL analysis; this estimate is only the latency bridge.
     */
    fun sampleImpact(homography: Homography?): QuickImpactEstimate? {
        val bmp = preview.bitmap ?: return null
        val previewWidth = bmp.width
        val nowNs = System.nanoTime()
        val now = try {
            detectBall(bmp)
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        } ?: return null

        if (baseline == null) {
            previous?.let {
                val d = hypot((now.x - it.x).toDouble(), (now.y - it.y).toDouble())
                stable = if (d < 8.0) stable + 1 else 0
                if (stable >= 3) baseline = PointF(now.x, now.y)
            }
            previous = now
            previousNs = nowNs
            return null
        }

        val old = previous
        val oldNs = previousNs
        previous = now
        previousNs = nowNs
        val b = baseline!!
        val moved = hypot((now.x - b.x).toDouble(), (now.y - b.y).toDouble())
        if (moved < max(12.0, previewWidth * 0.012)) return null

        var speed: Double? = null
        var angle: Double? = null
        var confidence = .34
        if (old != null && oldNs > 0L && homography != null) {
            val dt = (nowNs - oldNs) / 1_000_000_000.0
            if (dt in .006..0.120) {
                val a = homography.map(old)
                val c = homography.map(now)
                if (a.x.isFinite() && a.y.isFinite() && c.x.isFinite() && c.y.isFinite()) {
                    val dx = (c.x - a.x).toDouble()
                    val dy = (c.y - a.y).toDouble()
                    val measured = (hypot(dx, dy) / 100.0) / dt
                    val launch = Math.toDegrees(atan2(dx, dy))
                    if (measured in .18..5.2 && abs(launch) <= 22.0) {
                        speed = measured
                        angle = launch
                        confidence = (.48 + min(.34, moved / max(25.0, previewWidth * .045) * .24)).coerceIn(.45, .82)
                    }
                }
            }
        }
        return QuickImpactEstimate(speed, angle, confidence, nowNs)
    }

    private fun detectBall(src: Bitmap): PointF? {
        val maxW = 480
        val scale = if (src.width > maxW) maxW.toFloat() / src.width else 1f
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt(),
            (src.height * scale).toInt(),
            false
        ) else src

        try {
            val w = bmp.width
            val h = bmp.height
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            val lumSamples = ArrayList<Int>()
            var sy = h / 3
            while (sy < h) {
                var sx = 0
                while (sx < w) {
                    val c = pixels[sy * w + sx]
                    lumSamples += (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255)) / 3
                    sx += max(4, w / 28)
                }
                sy += max(4, h / 20)
            }
            lumSamples.sort()
            val median = lumSamples.getOrNull(lumSamples.size / 2) ?: 120
            val threshold = max(182, min(230, median + 55))

            var best: PointF? = null
            var bestScore = 0.0
            var y = (h * 0.30).toInt()
            while (y < h - 7) {
                var x = 7
                while (x < w - 7) {
                    val c = pixels[y * w + x]
                    val r = (c shr 16) and 255
                    val g = (c shr 8) and 255
                    val b = c and 255
                    if (min(r, min(g, b)) > threshold && abs(r - g) < 38 && abs(g - b) < 38) {
                        var white = 0
                        var total = 0
                        for (oy in -5..5 step 2) for (ox in -5..5 step 2) {
                            if (ox * ox + oy * oy <= 25) {
                                val cc = pixels[(y + oy) * w + (x + ox)]
                                val rr = (cc shr 16) and 255
                                val gg = (cc shr 8) and 255
                                val bb = cc and 255
                                if (min(rr, min(gg, bb)) > threshold - 18 && abs(rr - gg) < 50 && abs(gg - bb) < 50) white++
                                total++
                            }
                        }
                        var score = white.toDouble() / max(1, total)
                        previous?.let { prev ->
                            val px = prev.x * scale
                            val py = prev.y * scale
                            score += max(0.0, 1.0 - hypot((x - px).toDouble(), (y - py).toDouble()) / 120.0) * .65
                        }
                        if (score > .43 && score > bestScore) {
                            bestScore = score
                            best = PointF(x / scale, y / scale)
                        }
                    }
                    x += 3
                }
                y += 3
            }
            return best
        } finally {
            if (bmp !== src && !bmp.isRecycled) bmp.recycle()
        }
    }
}
