package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class PracticeGreenPreviewView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrow = Path()

    var styleIndex: Int = 0
        set(value) { field = value; invalidate() }
    var holeDistanceM: Double = 5.0
        set(value) { field = value.coerceIn(2.0, 15.0); invalidate() }
    var baseSideSlopePct: Double = 0.0
        set(value) { field = value; invalidate() }
    var baseLongSlopePct: Double = 0.0
        set(value) { field = value; invalidate() }

    init { isClickable = false; isFocusable = false }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        p.style = Paint.Style.FILL
        p.color = Color.rgb(8, 13, 15)
        canvas.drawRoundRect(RectF(0f, 0f, w, h), min(w, h) * .10f, min(w, h) * .10f, p)

        val pad = min(w, h) * .065f
        val r = RectF(pad, pad, w - pad, h - pad)
        val blob = buildBlob(styleIndex, r)
        canvas.save(); canvas.clipPath(blob)

        val settings = GreenSettings(
            stimpMeters = 2.8,
            holeDistanceM = holeDistanceM,
            sideSlopePct = baseSideSlopePct,
            longSlopePct = baseLongSlopePct,
            terrainProfileId = styleIndex
        )
        val cols = 9; val rows = 12
        val cellW = r.width() / cols; val cellH = r.height() / rows
        for (row in 0 until rows) {
            val yNorm = (rows - row - .5) / rows.toDouble()
            val realY = yNorm * holeDistanceM
            for (col in 0 until cols) {
                val xNorm = (col + .5) / cols.toDouble() * 2.0 - 1.0
                val realX = xNorm * max(.8, holeDistanceM * .22)
                val s = GreenTerrain.effectiveSlopeAt(settings, realX, realY)
                val z = GreenTerrain.heightAt(styleIndex, realX, realY, holeDistanceM)
                val mag = hypot(s.sidePct, s.longPct)
                p.color = surfaceColor(z, s.sidePct, s.longPct, mag)
                val l = r.left + col * cellW
                val t = r.top + row * cellH
                canvas.drawRect(l, t, l + cellW + 1f, t + cellH + 1f, p)
            }
        }

        // Actual downhill vectors. +long is toward the hole (screen up), +side is right.
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = max(1.2f, min(w, h) * .009f)
        p.color = Color.argb(175, 245, 250, 248)
        for (row in 1 until rows step 2) {
            val yNorm = (rows - row - .5) / rows.toDouble()
            val realY = yNorm * holeDistanceM
            for (col in 1 until cols step 2) {
                val xNorm = (col + .5) / cols.toDouble() * 2.0 - 1.0
                val realX = xNorm * max(.8, holeDistanceM * .22)
                val s = GreenTerrain.effectiveSlopeAt(settings, realX, realY)
                val mag = hypot(s.sidePct, s.longPct)
                if (mag < .12) continue
                val cx = r.left + (col + .5f) * cellW
                val cy = r.top + (row + .5f) * cellH
                val len = (min(w, h) * (.035 + min(3.5, mag) * .009)).toFloat()
                val dx = (s.sidePct / mag * len).toFloat()
                val dy = (-s.longPct / mag * len).toFloat()
                arrow.reset(); arrow.moveTo(cx - dx * .45f, cy - dy * .45f); arrow.lineTo(cx + dx * .45f, cy + dy * .45f)
                canvas.drawPath(arrow, p)
                val ex = cx + dx * .45f; val ey = cy + dy * .45f
                arrow.reset(); arrow.moveTo(ex, ey); arrow.lineTo(ex - dx * .22f - dy * .16f, ey - dy * .22f + dx * .16f)
                arrow.moveTo(ex, ey); arrow.lineTo(ex - dx * .22f + dy * .16f, ey - dy * .22f - dx * .16f)
                canvas.drawPath(arrow, p)
            }
        }
        canvas.restore()

        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.4f, min(w, h) * .012f)
        p.color = Color.argb(140, 92, 120, 104)
        canvas.drawPath(blob, p)
        p.style = Paint.Style.FILL

        // Start / cup anchors use the same orientation as GreenPhysics.
        p.color = Color.WHITE
        canvas.drawCircle(r.centerX(), r.bottom - r.height() * .08f, max(2.5f, min(w, h) * .018f), p)
        p.color = Color.rgb(246, 190, 74)
        canvas.drawCircle(r.centerX(), r.top + r.height() * .08f, max(2.5f, min(w, h) * .018f), p)
    }

    private fun surfaceColor(heightM: Double, side: Double, long: Double, magnitude: Double): Int {
        val hot = (magnitude / 3.8).coerceIn(0.0, 1.0)
        val directional = (abs(side) / (abs(side) + abs(long) + .01)).coerceIn(0.0, 1.0)
        val elevation = (heightM / .025).coerceIn(-1.0, 1.0)
        val lift = elevation * 24.0
        val r = (18 + hot * 190 + lift).toInt().coerceIn(0, 255)
        val g = (126 + (1.0 - hot) * 82 + lift).toInt().coerceIn(0, 255)
        val b = (52 + directional * 120 + (1.0 - hot) * 32 + lift).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun buildBlob(style: Int, r: RectF): Path = Path().apply {
        when (style % 6) {
            0 -> addOval(RectF(r.left + r.width() * .08f, r.top + r.height() * .05f, r.right - r.width() * .08f, r.bottom - r.height() * .05f), Path.Direction.CW)
            1 -> {
                moveTo(r.left + r.width() * .18f, r.top + r.height() * .08f)
                cubicTo(r.left, r.top + r.height() * .30f, r.left + r.width() * .08f, r.bottom - r.height() * .05f, r.left + r.width() * .34f, r.bottom - r.height() * .07f)
                cubicTo(r.right - r.width() * .05f, r.bottom - r.height() * .12f, r.right - r.width() * .06f, r.top + r.height() * .15f, r.left + r.width() * .18f, r.top + r.height() * .08f); close()
            }
            2 -> {
                moveTo(r.left + r.width() * .14f, r.top + r.height() * .14f)
                cubicTo(r.left, r.top + r.height() * .38f, r.left + r.width() * .06f, r.bottom - r.height() * .02f, r.left + r.width() * .36f, r.bottom - r.height() * .02f)
                cubicTo(r.right - r.width() * .06f, r.bottom, r.right, r.top + r.height() * .37f, r.right - r.width() * .10f, r.top + r.height() * .08f)
                cubicTo(r.right - r.width() * .35f, r.top, r.left + r.width() * .30f, r.top, r.left + r.width() * .14f, r.top + r.height() * .14f); close()
            }
            3 -> addOval(RectF(r.left + r.width() * .11f, r.top + r.height() * .03f, r.right - r.width() * .11f, r.bottom - r.height() * .03f), Path.Direction.CW)
            4 -> {
                moveTo(r.left + r.width() * .16f, r.top + r.height() * .08f)
                cubicTo(r.left, r.top + r.height() * .28f, r.left + r.width() * .04f, r.bottom - r.height() * .04f, r.left + r.width() * .30f, r.bottom - r.height() * .05f)
                cubicTo(r.right - r.width() * .06f, r.bottom - r.height() * .10f, r.right - r.width() * .04f, r.top + r.height() * .15f, r.left + r.width() * .46f, r.top + r.height() * .06f); close()
            }
            else -> {
                moveTo(r.left + r.width() * .20f, r.top + r.height() * .05f)
                cubicTo(r.left, r.top + r.height() * .28f, r.left + r.width() * .05f, r.bottom - r.height() * .05f, r.left + r.width() * .28f, r.bottom - r.height() * .02f)
                cubicTo(r.right - r.width() * .08f, r.bottom, r.right, r.top + r.height() * .34f, r.right - r.width() * .10f, r.top + r.height() * .07f)
                cubicTo(r.right - r.width() * .34f, r.top, r.left + r.width() * .34f, r.top, r.left + r.width() * .20f, r.top + r.height() * .05f); close()
            }
        }
    }
}
