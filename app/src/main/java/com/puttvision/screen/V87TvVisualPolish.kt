package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Presentation-only TV polish layer.
 * Adds depth, simulator framing and compact state cues without touching physics/measurement.
 */
class V87TvVisualPolishView(
    context: Context,
    private val engine: GameEngine
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (width <= 0 || height <= 0) return
        drawEdgeDepth(c)
        drawSafeCorners(c)
        drawPreShotRail(c)
        drawTargetAtmosphere(c)
        drawResultGlow(c)
        postInvalidateDelayed(if (engine.state?.running == true) 33L else 90L)
    }

    private fun drawEdgeDepth(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.shader = LinearGradient(0f, 0f, 0f, h * .16f,
            Color.argb(132, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h * .17f, p)
        p.shader = LinearGradient(0f, h * .72f, 0f, h,
            Color.TRANSPARENT, Color.argb(150, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, h * .70f, w, h, p)
        p.shader = RadialGradient(w * .5f, h * .52f, w * .68f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(80, 0, 0, 0)),
            floatArrayOf(0f, .68f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }

    private fun drawSafeCorners(c: Canvas) {
        if (ProductSessionRuntime.tvCalibrationGuide) return
        val w = width.toFloat(); val h = height.toFloat()
        val inset = min(w, h) * .022f
        val arm = min(w, h) * .028f
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.4f, min(w, h) * .0012f)
        p.color = Color.argb(52, 255, 255, 255)
        fun corner(x: Float, y: Float, sx: Float, sy: Float) {
            c.drawLine(x, y, x + arm * sx, y, p)
            c.drawLine(x, y, x, y + arm * sy, p)
        }
        corner(inset, inset, 1f, 1f)
        corner(w - inset, inset, -1f, 1f)
        corner(inset, h - inset, 1f, -1f)
        corner(w - inset, h - inset, -1f, -1f)
        p.style = Paint.Style.FILL
    }

    private fun drawPreShotRail(c: Canvas) {
        val running = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
        if (running || engine.lastResult != null) return
        val w = width.toFloat(); val h = height.toFloat()
        val settings = engine.settings
        val read = GreenReadRuntime.peekOrSchedule(settings)
        val left = w * .035f; val top = h * .845f
        val railW = w * .245f; val railH = h * .068f
        box.set(left, top, left + railW, top + railH)
        p.color = Color.argb(164, 4, 9, 12)
        c.drawRoundRect(box, railH * .34f, railH * .34f, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(8f, w * .0058f)
        p.color = Color.argb(170, 230, 238, 240)
        c.drawText("ADDRESS", left + railW * .055f, top + railH * .34f, p)
        p.textSize = max(13f, w * .0092f)
        p.color = Color.WHITE
        c.drawText("${"%.1f".format(settings.holeDistanceM)}m", left + railW * .055f, top + railH * .72f, p)

        val reliable = read?.solverReliable == true
        val status = if (reliable) "AIM READY" else "READING GREEN"
        val accent = if (reliable) Color.rgb(101, 226, 255) else Color.rgb(246, 190, 74)
        p.textAlign = Paint.Align.RIGHT
        p.textSize = max(8f, w * .0058f)
        p.color = accent
        c.drawText(status, box.right - railW * .055f, top + railH * .34f, p)
        p.textSize = max(10f, w * .0071f)
        p.color = Color.argb(230, 239, 244, 245)
        val pace = read?.recommendedBallSpeedMps?.let { "${"%.2f".format(it)} m/s" } ?: "--"
        c.drawText(pace, box.right - railW * .055f, top + railH * .72f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawTargetAtmosphere(c: Canvas) {
        val running = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
        if (running || engine.lastResult != null) return
        val settings = engine.settings
        val read = GreenReadRuntime.peekOrSchedule(settings)
        if (read?.solverReliable != true) return
        val y = settings.holeDistanceM
        val x = read.aimOffsetCm / 100.0
        val z = GreenTerrain.effectiveHeightAt(settings, x, y) + .035
        val pt = V25FlagProjectionRuntime.project(x, y, z) ?: return
        val w = width.toFloat(); val h = height.toFloat()
        val cx = pt.x.coerceIn(w * .10f, w * .90f)
        val cy = pt.y.coerceIn(h * .18f, h * .78f)
        val now = SystemClock.uptimeMillis() / 1000f
        val pulse = .5f + .5f * sin(now * 2.2f)
        val r = min(w, h) * (.050f + pulse * .008f)
        p.shader = RadialGradient(cx, cy, r,
            intArrayOf(Color.argb((38 + pulse * 28).toInt(), 87, 220, 255), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null

        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.5f, min(w, h) * .0013f)
        p.color = Color.argb((100 + pulse * 70).toInt(), 112, 226, 255)
        c.drawCircle(cx, cy, r * .62f, p)
        p.style = Paint.Style.FILL
    }

    private fun drawResultGlow(c: Canvas) {
        val result = engine.lastResult ?: return
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w * .5f; val cy = h * .48f
        val now = SystemClock.uptimeMillis() / 1000f
        val pulse = .5f + .5f * sin(now * 2.6f)
        val base = if (result.holed) intArrayOf(246,190,74) else intArrayOf(78,209,121)
        p.shader = RadialGradient(cx, cy, w * .34f,
            intArrayOf(Color.argb((22 + pulse * 18).toInt(), base[0], base[1], base[2]), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, w * .34f, p)
        p.shader = null

        if (result.holed) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(2f, w * .0016f)
            p.color = Color.argb((90 + pulse * 90).toInt(), 246, 190, 74)
            val radius = w * (.060f + pulse * .012f)
            c.drawCircle(cx, cy, radius, p)
            p.style = Paint.Style.FILL
        }
    }
}
