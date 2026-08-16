package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class V90CinematicPhase { ADDRESS, ROLL, CUP_APPROACH, HOLED, LIP_OUT, RESULT }

data class V90CinematicPlan(
    val phase: V90CinematicPhase,
    val letterbox: Float,
    val cupFocus: Float,
    val speedLines: Float,
    val resultPulse: Float,
    val label: String
)

object V90CinematicPlanner {
    fun plan(running: Boolean, progress01: Double, speedMps: Double, result: SimResult?, resultAgeMs: Long): V90CinematicPlan {
        val progress = progress01.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        val speed = speedMps.takeIf { it.isFinite() }?.coerceIn(0.0, 5.0) ?: 0.0
        val age = resultAgeMs.coerceAtLeast(0L)
        val pulse = if (result == null) 0f else (1f - (age / 1800f).coerceIn(0f, 1f))
        val phase = when {
            result?.holed == true -> V90CinematicPhase.HOLED
            result?.lipOut == true -> V90CinematicPhase.LIP_OUT
            result != null -> V90CinematicPhase.RESULT
            running && progress >= .72 -> V90CinematicPhase.CUP_APPROACH
            running -> V90CinematicPhase.ROLL
            else -> V90CinematicPhase.ADDRESS
        }
        val normalizedSpeed = (speed / 2.0).coerceIn(0.0, 1.0).toFloat()
        return when (phase) {
            V90CinematicPhase.ADDRESS -> V90CinematicPlan(phase, .02f, .12f, 0f, 0f, "ADDRESS")
            V90CinematicPhase.ROLL -> V90CinematicPlan(phase, .055f, .08f, normalizedSpeed * .55f, 0f, "ROLL")
            V90CinematicPhase.CUP_APPROACH -> V90CinematicPlan(phase, .085f, .62f, normalizedSpeed * .28f, 0f, "CUP")
            V90CinematicPhase.HOLED -> V90CinematicPlan(phase, .115f, .95f, 0f, pulse, "HOLED")
            V90CinematicPhase.LIP_OUT -> V90CinematicPlan(phase, .10f, .86f, 0f, pulse, "LIP OUT")
            V90CinematicPhase.RESULT -> V90CinematicPlan(phase, .075f, .48f, 0f, pulse * .45f, "RESULT")
        }
    }
}

class V90ScreenGolfCinematicOverlay(context: Context, private val engine: GameEngine) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var seenResult: SimResult? = null
    private var resultSeenAtMs = 0L

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val settings = engine.settings
        val state = engine.state
        val result = engine.lastResult
        if (result !== seenResult) {
            seenResult = result
            resultSeenAtMs = if (result == null) 0L else SystemClock.uptimeMillis()
        }
        val y = state?.y ?: 0.0
        val progress = if (settings.holeDistanceM > .2) (y / settings.holeDistanceM).coerceIn(0.0, 1.0) else 0.0
        val speed = state?.let { kotlin.math.hypot(it.vx, it.vy) } ?: 0.0
        val age = if (resultSeenAtMs == 0L) 0L else SystemClock.uptimeMillis() - resultSeenAtMs
        val plan = V90CinematicPlanner.plan(state?.running == true, progress, speed, result, age)
        drawLetterbox(canvas, plan)
        drawCupFocus(canvas, settings, plan)
        drawSpeedLines(canvas, state, plan)
        drawResultFlash(canvas, plan)
        val animate = state?.running == true || (result != null && age < 1900L)
        postInvalidateDelayed(if (animate) 16L else 140L)
    }

    private fun drawLetterbox(c: Canvas, plan: V90CinematicPlan) {
        if (plan.letterbox <= .005f) return
        val bar = height * plan.letterbox
        p.shader = LinearGradient(0f, 0f, 0f, bar, Color.argb(215, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), bar * 1.7f, p)
        p.shader = LinearGradient(0f, height.toFloat(), 0f, height - bar, Color.argb(215, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, height - bar * 1.7f, width.toFloat(), height.toFloat(), p)
        p.shader = null
    }

    private fun drawCupFocus(c: Canvas, settings: GreenSettings, plan: V90CinematicPlan) {
        if (plan.cupFocus <= .02f) return
        val z = GreenTerrain.effectiveHeightAt(settings, 0.0, settings.holeDistanceM)
        val cup = V25FlagProjectionRuntime.project(0.0, settings.holeDistanceM, z + .003) ?: return
        val radius = min(width, height) * (.095f + .13f * plan.cupFocus)
        p.shader = RadialGradient(cup.x, cup.y, radius,
            intArrayOf(Color.argb((22f * plan.cupFocus).roundToInt(), 255, 255, 255), Color.TRANSPARENT, Color.argb((92f * plan.cupFocus).roundToInt(), 0, 0, 0)),
            floatArrayOf(0f, .44f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.5f, min(width, height) * .0022f)
        p.color = Color.argb((120f * plan.cupFocus).roundToInt().coerceIn(0, 130), 236, 247, 241)
        c.drawCircle(cup.x, cup.y, min(width, height) * .020f * (1f + .25f * plan.cupFocus), p)
        p.style = Paint.Style.FILL
    }

    private fun drawSpeedLines(c: Canvas, state: SimState?, plan: V90CinematicPlan) {
        if (state == null || plan.speedLines <= .03f) return
        val speed = kotlin.math.hypot(state.vx, state.vy)
        if (!speed.isFinite() || speed < .12) return
        val settings = engine.settings
        val z = GreenTerrain.effectiveHeightAt(settings, state.x, state.y)
        val ball = V25FlagProjectionRuntime.project(state.x, state.y, z + .03) ?: return
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = max(1f, min(width, height) * .0015f)
        for (i in 0 until 10) {
            val f = (i + 1f) / 10f
            val spread = width * (.05f + f * .18f)
            val yOff = height * ((i % 5) - 2) * .018f
            val alpha = (68f * plan.speedLines * (1f - f * .45f)).roundToInt().coerceIn(0, 76)
            p.color = Color.argb(alpha, 234, 246, 255)
            c.drawLine(ball.x - spread, ball.y + yOff, ball.x - spread * .32f, ball.y + yOff * .72f, p)
            c.drawLine(ball.x + spread * .32f, ball.y - yOff * .48f, ball.x + spread, ball.y - yOff, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    private fun drawResultFlash(c: Canvas, plan: V90CinematicPlan) {
        if (plan.resultPulse <= .01f) return
        val a = (85f * plan.resultPulse).roundToInt().coerceIn(0, 90)
        val color = when (plan.phase) {
            V90CinematicPhase.HOLED -> Color.argb(a, 104, 255, 190)
            V90CinematicPhase.LIP_OUT -> Color.argb(a, 255, 158, 70)
            else -> Color.argb(a / 2, 220, 235, 245)
        }
        p.shader = RadialGradient(width * .5f, height * .52f, max(width, height) * .62f,
            intArrayOf(color, Color.TRANSPARENT), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null
    }
}
