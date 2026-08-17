package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/** Pure presentation plan for the pre-shot SOLO green-read HUD. */
data class V111GreenReadHudPlan(
    val show: Boolean,
    val aimText: String,
    val paceText: String,
    val confidenceText: String,
    val speedText: String,
    val aimAlpha: Int,
    val secondaryAlpha: Int,
    val refreshMs: Long
)

object V111GreenReadHudPlanner {
    fun plan(
        read: GreenRead?,
        moving: Boolean,
        training: Boolean,
        targetCupSpeedMps: Double = V27CupPaceRuntime.targetCupSpeedMps
    ): V111GreenReadHudPlan {
        val validCupSpeed = targetCupSpeedMps.takeIf { it.isFinite() && it in 0.0..5.0 }
        val valid = read?.takeIf {
            it.solverReliable && validCupSpeed != null &&
                it.recommendedBallSpeedMps.isFinite() && it.recommendedBallSpeedMps in 0.0..5.0 &&
                it.aimOffsetCm.isFinite() && abs(it.aimOffsetCm) <= 500.0 &&
                it.cupCount.isFinite() && it.cupCount in 0.0..50.0 &&
                it.solverMissCm.isFinite() && it.solverMissCm >= 0.0
        }
        if (moving || valid == null || validCupSpeed == null) {
            return V111GreenReadHudPlan(
                show = false,
                aimText = "",
                paceText = "",
                confidenceText = "",
                speedText = "",
                aimAlpha = 0,
                secondaryAlpha = 0,
                refreshMs = if (moving) 90L else if (training) 180L else 120L
            )
        }

        val aimMagnitude = abs(valid.aimOffsetCm)
        val aimText = when {
            aimMagnitude < 1.5 -> "AIM  CENTER"
            valid.cupCount < .5 -> "AIM  ${valid.aimSideLabel} ${aimMagnitude.toInt()}cm"
            else -> "AIM  ${valid.aimSideLabel} %.1f컵".format(valid.cupCount.coerceAtMost(9.9))
        }
        val miss = valid.solverMissCm.coerceIn(0.0, 99.0)
        val confidence = when {
            miss <= 2.0 -> "READ  LOCKED"
            miss <= 5.0 -> "READ  HIGH"
            else -> "READ  OK"
        }
        return V111GreenReadHudPlan(
            show = true,
            aimText = aimText,
            paceText = valid.paceHint.take(24).ifBlank { "기준 페이스" },
            confidenceText = "$confidence · SOLVER %.1fcm".format(miss),
            speedText = "BALL %.2f m/s · CUP %.2f m/s".format(valid.recommendedBallSpeedMps, validCupSpeed),
            aimAlpha = 235,
            secondaryAlpha = 188,
            refreshMs = if (training) 180L else 120L
        )
    }
}

/** Lightweight TV overlay for pace-aware ideal line. Training status is rendered by V31TrainingTvOverlay. */
class V27PaceLineOverlay(context: Context, private val engine: GameEngine) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init { setWillNotDraw(false) }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val moving = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
        val read = if (!moving && engine.lastResult == null) GreenReadRuntime.peekOrSchedule(engine.settings) else null
        val training = V31TrainingSessionRuntime.progress().running
        val hudPlan = V111GreenReadHudPlanner.plan(read, moving, training)
        drawPaceLine(c, read)
        drawBroadcastRead(c, hudPlan)
        postInvalidateDelayed(hudPlan.refreshMs)
    }

    private fun drawPaceLine(c: Canvas, read: GreenRead?) {
        if (read == null || !read.solverReliable) return
        val pts = read.predictedTrail.mapNotNull { (x, y) ->
            if (!x.isFinite() || !y.isFinite()) null else V25FlagProjectionRuntime.project(
                x,
                y,
                GreenTerrain.effectiveHeightAt(engine.settings, x, y) + .018
            )
        }.take(240)
        if (pts.size > 1) {
            val ideal = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(3f, width * .0017f)
            p.strokeCap = Paint.Cap.ROUND
            p.color = Color.rgb(255, 202, 61)
            c.drawPath(ideal, p)
            p.style = Paint.Style.FILL
        }
    }

    private fun drawBroadcastRead(c: Canvas, plan: V111GreenReadHudPlan) {
        if (!plan.show || width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * .055f
        val top = h * .075f
        val panelW = (w * .30f).coerceAtMost(w * .42f)
        val panelH = h * .145f

        p.style = Paint.Style.FILL
        p.color = Color.argb(132, 5, 10, 14)
        c.drawRoundRect(left, top, left + panelW, top + panelH, h * .014f, h * .014f, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.LEFT
        p.textSize = max(13f, w * .0125f)
        p.color = Color.argb(plan.aimAlpha, 255, 220, 92)
        c.drawText(plan.aimText, left + panelW * .06f, top + panelH * .28f, p)

        p.typeface = Typeface.DEFAULT
        p.textSize = max(11f, w * .0093f)
        p.color = Color.argb(plan.secondaryAlpha, 235, 244, 248)
        c.drawText(plan.speedText, left + panelW * .06f, top + panelH * .53f, p)
        c.drawText(plan.paceText, left + panelW * .06f, top + panelH * .73f, p)

        p.textSize = max(10f, w * .0082f)
        p.color = Color.argb((plan.secondaryAlpha * .78f).toInt().coerceIn(0, 255), 188, 211, 219)
        c.drawText(plan.confidenceText, left + panelW * .06f, top + panelH * .91f, p)
        p.textAlign = Paint.Align.LEFT
    }
}
