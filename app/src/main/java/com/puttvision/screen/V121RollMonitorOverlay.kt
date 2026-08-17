package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class V121PaceBand { CUP_WINDOW, ON_PACE, HOT, DYING }

data class V121RollMonitorPlan(
    val remainingM: Double,
    val lateralCm: Double,
    val speedMps: Double,
    val progress01: Double,
    val speed01: Double,
    val remaining01: Double,
    val paceBand: V121PaceBand,
    val paceLabel: String,
    val lateralLabel: String,
    val panelAlpha: Int,
    val accentAlpha: Int,
    val refreshMs: Long
)

/**
 * Pure visual policy for the compact SOLO roll monitor. It reads simulation state only and never
 * mutates measurement, HFR, training, GreenPhysics, or shot outcomes.
 */
object V121RollMonitorPlanner {
    fun plan(
        running: Boolean,
        holeDistanceM: Double,
        ballX: Double,
        ballY: Double,
        vx: Double,
        vy: Double,
        tier: V24RenderTier
    ): V121RollMonitorPlan? {
        if (!running) return null
        val target = holeDistanceM.takeIf { it.isFinite() && it in .20..33.0 } ?: return null
        if (!ballX.isFinite() || !ballY.isFinite() || !vx.isFinite() || !vy.isFinite()) return null
        if (abs(ballX) > 20.0 || ballY !in -2.0..40.0) return null

        val speed = hypot(vx, vy).takeIf { it.isFinite() }?.coerceIn(0.0, 5.0) ?: return null
        val remaining = hypot(ballX, target - ballY).takeIf { it.isFinite() }?.coerceIn(0.0, 40.0) ?: return null
        val progress = (ballY / target).coerceIn(0.0, 1.0)
        val speed01 = (speed / 2.0).coerceIn(0.0, 1.0)
        val remaining01 = (remaining / target).coerceIn(0.0, 1.0)
        val paceBand = when {
            remaining <= .75 && speed <= 1.0 -> V121PaceBand.CUP_WINDOW
            speed > 1.60 -> V121PaceBand.HOT
            speed < .25 -> V121PaceBand.DYING
            else -> V121PaceBand.ON_PACE
        }
        val paceLabel = when (paceBand) {
            V121PaceBand.CUP_WINDOW -> "CUP WINDOW"
            V121PaceBand.ON_PACE -> "ON PACE"
            V121PaceBand.HOT -> "HOT"
            V121PaceBand.DYING -> "DYING"
        }
        val lateralLabel = when {
            abs(ballX) < .01 -> "CENTER"
            ballX < 0.0 -> "LEFT ${formatCm(abs(ballX) * 100.0)}"
            else -> "RIGHT ${formatCm(abs(ballX) * 100.0)}"
        }
        val nearCup = remaining <= 1.25
        val panelAlpha = when (tier) {
            V24RenderTier.HIGH -> if (nearCup) 202 else 176
            V24RenderTier.BALANCED -> if (nearCup) 188 else 164
            V24RenderTier.PERFORMANCE -> if (nearCup) 172 else 148
        }
        val accentAlpha = when (paceBand) {
            V121PaceBand.CUP_WINDOW -> 235
            V121PaceBand.ON_PACE -> 205
            V121PaceBand.HOT -> 220
            V121PaceBand.DYING -> 178
        }
        return V121RollMonitorPlan(
            remainingM = remaining,
            lateralCm = ballX * 100.0,
            speedMps = speed,
            progress01 = progress,
            speed01 = speed01,
            remaining01 = remaining01,
            paceBand = paceBand,
            paceLabel = paceLabel,
            lateralLabel = lateralLabel,
            panelAlpha = panelAlpha.coerceIn(0, 255),
            accentAlpha = accentAlpha.coerceIn(0, 255),
            refreshMs = tier.movingFrameMs.coerceIn(16L, 50L)
        )
    }

    private fun formatCm(value: Double): String = "%.0f cm".format(value.coerceIn(0.0, 999.0))
}

/** Compact top-right live roll telemetry. Hidden for address/result/replay idle states. */
class V121RollMonitorOverlay(
    context: Context,
    private val engine: GameEngine
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val state = engine.state ?: return
        val running = state.running || TvInstantRollRuntime.isAnimating()
        val tier = V24TvQualityRuntime.snapshot(context.applicationContext).tier
        val plan = V121RollMonitorPlanner.plan(
            running = running,
            holeDistanceM = engine.settings.holeDistanceM,
            ballX = state.x,
            ballY = state.y,
            vx = state.vx,
            vy = state.vy,
            tier = tier
        ) ?: return

        val w = width.toFloat()
        val h = height.toFloat()
        val panelW = w * .205f
        val panelH = h * .116f
        val right = w * .974f
        val left = right - panelW
        val top = h * .052f
        rect.set(left, top, right, top + panelH)

        p.style = Paint.Style.FILL
        p.color = Color.argb(plan.panelAlpha, 3, 9, 12)
        canvas.drawRoundRect(rect, panelH * .13f, panelH * .13f, p)

        val accent = accentColor(plan.paceBand)
        p.color = Color.argb(plan.accentAlpha, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(left, top, left + max(3f, w * .0028f), top + panelH, panelH * .08f, panelH * .08f, p)

        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(11f, w * .0068f)
        p.color = Color.argb(238, 241, 247, 247)
        canvas.drawText(plan.paceLabel, left + panelW * .075f, top + panelH * .27f, p)

        p.typeface = Typeface.DEFAULT
        p.textSize = max(9f, w * .0053f)
        p.color = Color.argb(202, 214, 228, 230)
        canvas.drawText("CUP ${fmt(plan.remainingM, 2)} m   ·   ${plan.lateralLabel}", left + panelW * .075f, top + panelH * .50f, p)
        canvas.drawText("ROLL ${fmt(plan.speedMps, 2)} m/s   ·   ${(plan.progress01 * 100.0).roundToInt()}%", left + panelW * .075f, top + panelH * .70f, p)

        val barLeft = left + panelW * .075f
        val barRight = right - panelW * .075f
        val barTop = top + panelH * .82f
        val barH = max(2f, panelH * .045f)
        p.color = Color.argb(64, 210, 226, 228)
        canvas.drawRoundRect(barLeft, barTop, barRight, barTop + barH, barH * .5f, barH * .5f, p)
        val fill = (barRight - barLeft) * (1.0 - plan.remaining01).toFloat().coerceIn(0f, 1f)
        p.color = Color.argb(plan.accentAlpha, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(barLeft, barTop, barLeft + fill, barTop + barH, barH * .5f, barH * .5f, p)

        postInvalidateDelayed(plan.refreshMs)
    }

    private fun accentColor(band: V121PaceBand): Int = when (band) {
        V121PaceBand.CUP_WINDOW -> Color.rgb(246, 211, 111)
        V121PaceBand.ON_PACE -> Color.rgb(112, 232, 178)
        V121PaceBand.HOT -> Color.rgb(255, 145, 92)
        V121PaceBand.DYING -> Color.rgb(176, 198, 204)
    }

    private fun fmt(value: Double, digits: Int): String {
        val v = value.takeIf { it.isFinite() } ?: 0.0
        return if (digits <= 1) "%.1f".format(v) else "%.2f".format(v)
    }
}
