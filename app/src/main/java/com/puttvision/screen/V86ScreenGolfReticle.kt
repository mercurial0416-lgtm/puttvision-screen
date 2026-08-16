package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

data class V86ReticlePlan(
    val visible: Boolean,
    val aimAngleDeg: Double,
    val aimOffsetM: Double,
    val assisted: Boolean,
    val label: String,
    val reason: String
)

/** Pure planner so aiming semantics can be regression-tested without Android or a camera. */
object V86ScreenGolfReticlePlanner {
    private const val MAX_ANGLE_DEG = 18.0

    fun plan(
        holeDistanceM: Double,
        recommendedAngleDeg: Double?,
        solverReliable: Boolean,
        hideSolution: Boolean,
        shotRunning: Boolean,
        hasResult: Boolean
    ): V86ReticlePlan {
        if (!holeDistanceM.isFinite() || holeDistanceM !in 0.25..30.0) {
            return V86ReticlePlan(false, 0.0, 0.0, false, "", "distance invalid")
        }
        if (shotRunning || hasResult) {
            return V86ReticlePlan(false, 0.0, 0.0, false, "", "shot active or complete")
        }
        val assisted = solverReliable && !hideSolution && recommendedAngleDeg?.isFinite() == true
        val angle = if (assisted) recommendedAngleDeg!!.coerceIn(-MAX_ANGLE_DEG, MAX_ANGLE_DEG) else 0.0
        val rawOffset = tan(Math.toRadians(angle)) * holeDistanceM
        val maxOffset = max(0.18, holeDistanceM * 0.42)
        val offset = rawOffset.coerceIn(-maxOffset, maxOffset)
        val label = if (assisted) {
            val side = when {
                abs(offset) < .005 -> "CENTER"
                offset < 0.0 -> "L ${"%.0f".format(abs(offset) * 100.0)}cm"
                else -> "R ${"%.0f".format(abs(offset) * 100.0)}cm"
            }
            "AIM ${"%+.1f".format(angle)}° · $side"
        } else {
            "TARGET · CENTER"
        }
        return V86ReticlePlan(true, angle, offset, assisted, label, if (assisted) "solver aim" else "neutral target")
    }
}

/**
 * Screen-golf style TV reticle. It is intentionally a separate product layer so the same
 * sight appears on the physical TV, phone TV preview and hardwareless preview.
 */
class V86ScreenGolfReticleView(
    context: Context,
    private val engine: GameEngine
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = RectF()

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
        val feedback = V20GreenReadTrainingRuntime.feedback
        val hideSolution = V20GreenReadTrainingRuntime.shouldHideSolution(engine.gameModes.status.mode, settings) && !feedback.revealed
        val read = GreenReadRuntime.peekOrSchedule(settings)
        val plan = V86ScreenGolfReticlePlanner.plan(
            holeDistanceM = settings.holeDistanceM,
            recommendedAngleDeg = read?.recommendedLaunchAngleDeg,
            solverReliable = read?.solverReliable == true,
            hideSolution = hideSolution,
            shotRunning = engine.state?.running == true || TvInstantRollRuntime.isAnimating(),
            hasResult = engine.lastResult != null
        )
        if (!plan.visible) {
            postInvalidateDelayed(90L)
            return
        }

        val y = settings.holeDistanceM
        val targetX = plan.aimOffsetM
        val z = GreenTerrain.effectiveHeightAt(settings, targetX, y) + .035
        val projected = V25FlagProjectionRuntime.project(targetX, y, z)
        val cx = (projected?.x ?: width * .5f).coerceIn(width * .09f, width * .91f)
        val cy = (projected?.y ?: height * .48f).coerceIn(height * .18f, height * .79f)
        drawReticle(canvas, cx, cy, plan)
        postInvalidateDelayed(70L)
    }

    private fun drawReticle(c: Canvas, cx: Float, cy: Float, plan: V86ReticlePlan) {
        val w = width.toFloat()
        val h = height.toFloat()
        val unit = min(w, h)
        val r = (unit * .028f).coerceIn(18f, 54f)
        val outer = r * 1.46f
        val arm0 = r * .38f
        val arm1 = r * 1.92f
        val shadow = max(4f, unit * .0042f)
        val stroke = max(2.2f, unit * .0022f)

        fun cross(color: Int, sw: Float) {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.SQUARE
            p.strokeWidth = sw
            p.color = color
            c.drawLine(cx - arm1, cy, cx - arm0, cy, p)
            c.drawLine(cx + arm0, cy, cx + arm1, cy, p)
            c.drawLine(cx, cy - arm1, cx, cy - arm0, p)
            c.drawLine(cx, cy + arm0, cx, cy + arm1, p)
        }

        // Heavy dark keyline keeps the sight legible on both bright green and sky.
        cross(Color.argb(185, 0, 0, 0), shadow)
        cross(Color.argb(245, 255, 255, 255), stroke)

        p.style = Paint.Style.STROKE
        p.strokeWidth = shadow
        p.color = Color.argb(170, 0, 0, 0)
        ring.set(cx - r, cy - r, cx + r, cy + r)
        c.drawOval(ring, p)
        p.strokeWidth = stroke
        p.color = if (plan.assisted) Color.rgb(87, 220, 255) else Color.WHITE
        c.drawOval(ring, p)

        // Outer cardinal + diagonal ticks mimic commercial simulator aiming sights.
        repeat(8) { i ->
            val a = Math.toRadians(i * 45.0)
            val ux = kotlin.math.cos(a).toFloat()
            val uy = kotlin.math.sin(a).toFloat()
            val inner = if (i % 2 == 0) outer * .82f else outer * .88f
            val end = if (i % 2 == 0) outer * 1.08f else outer
            p.strokeWidth = if (i % 2 == 0) stroke * 1.15f else stroke * .82f
            p.color = Color.argb(if (i % 2 == 0) 235 else 185, 255, 255, 255)
            c.drawLine(cx + ux * inner, cy + uy * inner, cx + ux * end, cy + uy * end, p)
        }

        p.style = Paint.Style.FILL
        p.color = Color.argb(225, 0, 0, 0)
        c.drawCircle(cx, cy, max(4.6f, r * .15f), p)
        p.color = if (plan.assisted) Color.rgb(87, 220, 255) else Color.WHITE
        c.drawCircle(cx, cy, max(2.6f, r * .08f), p)

        val labelW = max(w * .112f, r * 4.0f)
        val labelH = max(h * .034f, r * .78f)
        val top = (cy + outer * 1.32f).coerceAtMost(h - labelH - h * .025f)
        p.color = Color.argb(188, 4, 9, 12)
        c.drawRoundRect(RectF(cx - labelW / 2f, top, cx + labelW / 2f, top + labelH), labelH * .30f, labelH * .30f, p)
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = max(9f, min(w * .009f, labelH * .43f))
        p.color = if (plan.assisted) Color.rgb(112, 226, 255) else Color.WHITE
        c.drawText(plan.label, cx, top + labelH * .64f, p)
        p.textAlign = Paint.Align.LEFT
        p.style = Paint.Style.FILL
    }
}
