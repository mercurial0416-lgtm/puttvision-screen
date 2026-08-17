package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Compact original HUD inspired by the information hierarchy of commercial screen-golf systems.
 * The course remains the hero; panels are deliberately edge-bound and low-opacity.
 */
data class V128HudPlan(
    val showReady: Boolean,
    val showLive: Boolean,
    val showShotBar: Boolean,
    val showResult: Boolean,
    val refreshMs: Long,
    val panelAlpha: Int
)

object V128HudPlanner {
    fun plan(running: Boolean, hasShot: Boolean, hasResult: Boolean, resultAgeMs: Long): V128HudPlan {
        val age = resultAgeMs.coerceAtLeast(0L)
        return V128HudPlan(
            showReady = !running && !hasResult,
            showLive = running,
            showShotBar = hasShot,
            showResult = hasResult,
            refreshMs = when {
                running -> 16L
                hasResult && age < 2400L -> 32L
                else -> 120L
            },
            panelAlpha = if (running) 182 else 205
        )
    }
}

class V128CommercialScreenGolfHudView(
    context: Context,
    private val engine: GameEngine
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var seenResult: SimResult? = null
    private var resultAtMs = 0L

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (width <= 0 || height <= 0) return

        val state = engine.state
        val result = engine.lastResult
        if (result !== seenResult) {
            seenResult = result
            resultAtMs = if (result == null) 0L else SystemClock.uptimeMillis()
        }
        val running = state?.running == true || TvInstantRollRuntime.isAnimating()
        val age = if (resultAtMs == 0L) Long.MAX_VALUE else SystemClock.uptimeMillis() - resultAtMs
        val plan = V128HudPlanner.plan(running, engine.currentShot != null, result != null, age)

        drawHolePlate(c, plan)
        drawMiniMap(c, plan)
        drawConditions(c, plan)
        if (plan.showReady) drawReady(c)
        if (plan.showLive) drawLive(c)
        if (plan.showShotBar) drawShotBar(c)
        if (plan.showResult) drawResult(c, result)
        drawBrand(c)

        postInvalidateDelayed(plan.refreshMs)
    }

    private fun drawHolePlate(c: Canvas, plan: V128HudPlan) {
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * .018f
        val top = h * .022f
        val pw = w * .208f
        val ph = h * .083f

        p.style = Paint.Style.FILL
        p.color = Color.argb(plan.panelAlpha, 20, 24, 23)
        c.drawRoundRect(left, top, left + pw, top + ph, h * .008f, h * .008f, p)
        p.color = Color.rgb(237, 177, 41)
        c.drawRect(left, top, left + w * .0045f, top + ph, p)

        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(14f, w * .009f)
        p.color = Color.WHITE
        c.drawText("PRACTICE  ·  H01", left + pw * .075f, top + ph * .38f, p)

        p.typeface = Typeface.DEFAULT
        p.textSize = max(10f, w * .0062f)
        p.color = Color.rgb(204, 211, 205)
        c.drawText("PAR 2   PUTT", left + pw * .075f, top + ph * .72f, p)

        p.textAlign = Paint.Align.RIGHT
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(17f, w * .0105f)
        p.color = Color.rgb(247, 248, 244)
        c.drawText("${fmt(targetM(), 1)}m", left + pw * .94f, top + ph * .56f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawMiniMap(c: Canvas, plan: V128HudPlan) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pw = w * .105f
        val ph = h * .255f
        val left = w - pw - w * .018f
        val top = h * .022f

        p.style = Paint.Style.FILL
        p.color = Color.argb(plan.panelAlpha - 8, 21, 25, 23)
        c.drawRoundRect(left, top, left + pw, top + ph, h * .009f, h * .009f, p)

        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(9f, w * .0055f)
        p.color = Color.rgb(230, 234, 228)
        c.drawText("GREEN", left + pw * .12f, top + ph * .10f, p)

        val mapL = left + pw * .22f
        val mapR = left + pw * .78f
        val mapT = top + ph * .17f
        val mapB = top + ph * .90f
        val cx = (mapL + mapR) * .5f
        val mw = mapR - mapL
        val mh = mapB - mapT

        path.reset()
        path.moveTo(cx - mw * .37f, mapB)
        path.cubicTo(cx - mw * .55f, mapT + mh * .72f, cx - mw * .44f, mapT + mh * .30f, cx - mw * .20f, mapT)
        path.cubicTo(cx + mw * .17f, mapT - mh * .02f, cx + mw * .52f, mapT + mh * .32f, cx + mw * .36f, mapB)
        path.close()
        p.shader = LinearGradient(mapL, mapT, mapR, mapB, Color.rgb(100, 177, 68), Color.rgb(49, 126, 44), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.shader = null

        val settings = engine.settings
        val state = engine.state
        val display = TvInstantRollRuntime.displayPosition(state)
        val bx = display?.first ?: state?.x ?: V26BallStartRuntime.current(settings).first
        val by = display?.second ?: state?.y ?: V26BallStartRuntime.current(settings).second
        val progress = (by / targetM()).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        val lateral = bx.takeIf { it.isFinite() }?.coerceIn(-2.2, 2.2)?.div(2.2) ?: 0.0
        val ballX = cx + (lateral * mw * .38).toFloat()
        val ballY = mapB - (progress * mh).toFloat()

        p.color = Color.rgb(244, 72, 58)
        c.drawCircle(cx, mapT + mh * .025f, max(3.6f, w * .0024f), p)
        p.color = Color.WHITE
        c.drawCircle(ballX, ballY, max(3.8f, w * .0026f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, w * .0008f)
        p.color = Color.argb(190, 15, 18, 16)
        c.drawCircle(ballX, ballY, max(3.8f, w * .0026f), p)
        p.style = Paint.Style.FILL
    }

    private fun drawConditions(c: Canvas, plan: V128HudPlan) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pw = w * .105f
        val left = w - pw - w * .018f
        val top = h * .292f
        val rowH = h * .047f
        val gap = h * .006f
        val settings = engine.settings
        val rows = listOf(
            "GREEN" to "${fmt(stimp(), 1)} STIMP",
            "SLOPE" to slopeLabel(settings.sideSlopePct, settings.longSlopePct),
            "TARGET" to "${fmt(targetM(), 1)} m"
        )
        rows.forEachIndexed { index, row ->
            val y = top + index * (rowH + gap)
            p.color = Color.argb(plan.panelAlpha - 14, 21, 25, 23)
            c.drawRoundRect(left, y, left + pw, y + rowH, h * .007f, h * .007f, p)
            p.typeface = Typeface.DEFAULT
            p.textSize = max(8f, w * .0048f)
            p.color = Color.rgb(168, 178, 168)
            c.drawText(row.first, left + pw * .10f, y + rowH * .38f, p)
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = max(9.5f, w * .0057f)
            p.color = Color.WHITE
            c.drawText(row.second, left + pw * .10f, y + rowH * .76f, p)
        }
    }

    private fun drawReady(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cy = h * .782f
        val radius = h * .036f
        p.style = Paint.Style.FILL
        p.color = Color.argb(175, 12, 18, 18)
        c.drawCircle(w * .5f, cy, radius * 1.18f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(2f, h * .003f)
        p.color = Color.rgb(41, 197, 224)
        c.drawCircle(w * .5f, cy, radius, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(13f, w * .008f)
        p.color = Color.WHITE
        c.drawText("READY", w * .5f, cy + radius * .20f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawLive(c: Canvas) {
        val state = engine.state ?: return
        val settings = engine.settings
        val display = TvInstantRollRuntime.displayPosition(state)
        val bx = display?.first ?: state.x
        val by = display?.second ?: state.y
        val remaining = hypot(bx, targetM() - by).takeIf { it.isFinite() }?.coerceIn(0.0, 99.9) ?: 0.0
        val speed = hypot(state.vx, state.vy).takeIf { it.isFinite() }?.coerceIn(0.0, 9.9) ?: 0.0
        val w = width.toFloat()
        val h = height.toFloat()

        val pw = w * .156f
        val ph = h * .068f
        val left = (w - pw) * .5f
        val top = h * .025f
        p.color = Color.argb(184, 18, 22, 20)
        c.drawRoundRect(left, top, left + pw, top + ph, h * .009f, h * .009f, p)
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(18f, w * .011f)
        p.color = Color.rgb(109, 237, 118)
        c.drawText("${fmt(remaining, 2)} m", left + pw * .5f, top + ph * .49f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = max(9f, w * .0054f)
        p.color = Color.rgb(215, 221, 214)
        c.drawText("BALL ${fmt(speed, 2)} m/s", left + pw * .5f, top + ph * .78f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawShotBar(c: Canvas) {
        val metrics = engine.currentShot ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * .018f
        val right = w * .745f
        val top = h * .922f
        val bottom = h * .978f
        p.color = Color.argb(198, 18, 22, 20)
        c.drawRoundRect(left, top, right, bottom, h * .008f, h * .008f, p)

        val items = listOf(
            "BALL" to "${fmt(metrics.ballSpeedMps.takeIf { it.isFinite() }?.coerceIn(0.0, 8.0) ?: 0.0, 2)} m/s",
            "ANGLE" to "${signed(metrics.launchAngleDeg.takeIf { it.isFinite() }?.coerceIn(-45.0, 45.0) ?: 0.0, 1)}°",
            "HEAD" to (metrics.headSpeedMps?.takeIf { it.isFinite() }?.coerceIn(0.0, 12.0)?.let { "${fmt(it, 2)} m/s" } ?: "--"),
            "FACE" to (metrics.faceAngleDeg?.takeIf { it.isFinite() }?.coerceIn(-45.0, 45.0)?.let { "${signed(it, 1)}°" } ?: "--")
        )
        val cellW = (right - left) / items.size
        items.forEachIndexed { index, item ->
            val x = left + cellW * index
            if (index > 0) {
                p.color = Color.argb(75, 220, 225, 219)
                c.drawRect(x, top + (bottom - top) * .19f, x + 1f, bottom - (bottom - top) * .19f, p)
            }
            p.typeface = Typeface.DEFAULT
            p.textSize = max(8f, w * .0047f)
            p.color = Color.rgb(157, 168, 158)
            c.drawText(item.first, x + cellW * .10f, top + (bottom - top) * .39f, p)
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = max(10f, w * .0059f)
            p.color = Color.WHITE
            c.drawText(item.second, x + cellW * .10f, top + (bottom - top) * .75f, p)
        }
    }

    private fun drawResult(c: Canvas, result: SimResult?) {
        result ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val pw = w * .225f
        val ph = h * .104f
        val left = (w - pw) * .5f
        val top = h * .715f
        p.color = Color.argb(218, 16, 20, 18)
        c.drawRoundRect(left, top, left + pw, top + ph, h * .012f, h * .012f, p)

        val title = when {
            result.holed -> "HOLED"
            result.lipOut -> "LIP OUT"
            else -> "STOP"
        }
        val accent = when {
            result.holed -> Color.rgb(92, 231, 106)
            result.lipOut -> Color.rgb(246, 179, 54)
            else -> Color.rgb(231, 235, 228)
        }
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(20f, w * .0125f)
        p.color = accent
        c.drawText(title, left + pw * .5f, top + ph * .43f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = max(10f, w * .006f)
        p.color = Color.rgb(219, 224, 217)
        c.drawText("LEAVE ${fmt(result.distanceToCupM.takeIf { it.isFinite() }?.coerceIn(0.0, 99.9) ?: 0.0, 2)} m", left + pw * .5f, top + ph * .73f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawBrand(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.textAlign = Paint.Align.RIGHT
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(9f, w * .0054f)
        p.color = Color.argb(165, 238, 241, 236)
        c.drawText("PUTTVISION", w * .982f, h * .973f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun targetM(): Double = engine.settings.holeDistanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0
    private fun stimp(): Double = engine.settings.stimpMeters.takeIf { it.isFinite() }?.coerceIn(1.5, 5.0) ?: 2.8

    private fun slopeLabel(sideRaw: Double, longRaw: Double): String {
        val side = sideRaw.takeIf { it.isFinite() }?.coerceIn(-8.0, 8.0) ?: 0.0
        val long = longRaw.takeIf { it.isFinite() }?.coerceIn(-8.0, 8.0) ?: 0.0
        if (abs(side) < .05 && abs(long) < .05) return "FLAT"
        val h = when {
            side > .05 -> "R"
            side < -.05 -> "L"
            else -> ""
        }
        val v = when {
            long > .05 -> "DOWN"
            long < -.05 -> "UP"
            else -> ""
        }
        return listOf(h, v).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun fmt(value: Double, digits: Int): String = "% .${digits}f".format(value).trim()
    private fun signed(value: Double, digits: Int): String = if (value >= 0.0) "+${fmt(value, digits)}" else fmt(value, digits)
}
