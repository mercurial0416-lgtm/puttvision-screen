package com.puttvision.screen

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.camera.view.PreviewView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class PreviewImpactDetector(private val preview: PreviewView) {
    private var baseline: PointF? = null
    private var previous: PointF? = null
    private var stable = 0

    fun reset() {
        baseline = null
        previous = null
        stable = 0
    }

    fun sampleMoved(): Boolean {
        val bmp = preview.bitmap ?: return false
        val previewWidth = bmp.width
        val now = try {
            detectBall(bmp)
        } finally {
            // PreviewView.bitmap creates a snapshot. Polling at ~38Hz without recycling it
            // quickly exhausts the bitmap heap on long sessions.
            if (!bmp.isRecycled) bmp.recycle()
        } ?: return false

        if (baseline == null) {
            previous?.let {
                val d = hypot((now.x - it.x).toDouble(), (now.y - it.y).toDouble())
                stable = if (d < 8.0) stable + 1 else 0
                if (stable >= 3) baseline = PointF(now.x, now.y)
            }
            previous = now
            return false
        }

        previous = now
        val b = baseline!!
        val d = hypot((now.x - b.x).toDouble(), (now.y - b.y).toDouble())
        return d >= max(12.0, previewWidth * 0.012)
    }

    private fun detectBall(src: Bitmap): PointF? {
        val maxW = 480
        val scale = if (src.width > maxW) maxW.toFloat() / src.width else 1f
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                src,
                (src.width * scale).toInt(),
                (src.height * scale).toInt(),
                false
            )
        } else src

        try {
            val w = bmp.width
            val h = bmp.height
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)

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

                    if (min(r, min(g, b)) > 205 &&
                        abs(r - g) < 32 && abs(g - b) < 32
                    ) {
                        var white = 0
                        var total = 0

                        var oy = -5
                        while (oy <= 5) {
                            var ox = -5
                            while (ox <= 5) {
                                if (ox * ox + oy * oy <= 25) {
                                    val cc = pixels[(y + oy) * w + (x + ox)]
                                    val rr = (cc shr 16) and 255
                                    val gg = (cc shr 8) and 255
                                    val bb = cc and 255
                                    if (min(rr, min(gg, bb)) > 190 &&
                                        abs(rr - gg) < 42 && abs(gg - bb) < 42
                                    ) white++
                                    total++
                                }
                                ox += 2
                            }
                            oy += 2
                        }

                        val score = white.toDouble() / max(1, total)
                        if (score > 0.43 && score > bestScore) {
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
