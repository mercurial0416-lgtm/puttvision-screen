package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure presentation plan for the pre-shot SOLO green-read HUD. */
data class V111GreenReadHudPlan(
    val show: Boolean,
    val aimText: String,
    val paceText: String,
    val confidenceText: String,
    val speedText: String,
    val breakText: String,
    val slopeText: String,
    val confidenceFraction: Float,
    val trailAlpha: Int,
    val trailSampleCap: Int,
    val aimAlpha: Int,
    val secondaryAlpha: Int,
    val refreshMs: Long
)

object V111GreenReadHudPlanner {
    private fun cleanLabel(raw: String, maxChars: Int): String =
        raw.replace(Regex("\\s+"), " ").trim().take(maxChars)

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
                it.recommendedLaunchAngleDeg.isFinite() && abs(it.recommendedLaunchAngleDeg) <= 15.0 &&
                it.aimOffsetCm.isFinite() && abs(it.aimOffsetCm) <= 500.0 &&
                it.estimatedBreakCm.isFinite() && abs(it.estimatedBreakCm) <= 500.0 &&
                it.cupCount.isFinite() && it.cupCount in 0.0..50.0 &&
                it.effectiveSideSlopePct.isFinite() && abs(it.effectiveSideSlopePct) <= 20.0 &&
                it.effectiveLongSlopePct.isFinite() && abs(it.effectiveLongSlopePct) <= 20.0 &&
                it.solverMissCm.isFinite() && it.solverMissCm >= 0.0
        }
        if (moving || valid == null || validCupSpeed == null) {
            return V111GreenReadHudPlan(
                show = false,
                aimText = "",
                paceText = "",
                confidenceText = "",
                speedText = "",
                breakText = "",
                slopeText = "",
                confidenceFraction = 0f,
                trailAlpha = 0,
                trailSampleCap = 0,
                aimAlpha = 0,
                secondaryAlpha = 0,
                refreshMs = if (moving) 90L else if (training) 200L else 140L
            )
        }

        val sideLabel = cleanLabel(valid.aimSideLabel, 12).ifBlank { "SIDE" }
        val paceHint = cleanLabel(valid.paceHint, 22).ifBlank { "기준 페이스" }
        val aimMagnitude = abs(valid.aimOffsetCm)
        val aimText = when {
            aimMagnitude < 1.5 -> "AIM  CENTER"
            valid.cupCount < .5 -> "AIM  $sideLabel ${aimMagnitude.toInt()}cm"
            else -> "AIM  $sideLabel %.1f컵".format(valid.cupCount.coerceAtMost(9.9))
        }
        val breakMagnitude = abs(valid.estimatedBreakCm)
        val breakText = when {
            breakMagnitude < 1.0 -> "BREAK  CENTER"
            else -> "BREAK  $sideLabel %.0fcm".format(breakMagnitude.coerceAtMost(999.0))
        }
        val miss = valid.solverMissCm.coerceIn(0.0, 99.0)
        val confidence = when {
            miss <= 2.0 -> "READ  LOCKED"
            miss <= 5.0 -> "READ  HIGH"
            else -> "READ  OK"
        }
        val confidenceFraction = ((8.0 - miss) / 8.0).coerceIn(0.0, 1.0).toFloat()
        val trailAlpha = (125 + confidenceFraction * 110f).toInt().coerceIn(125, 235)
        val trailSampleCap = if (training) 180 else 240
        return V111GreenReadHudPlan(
            show = true,
            aimText = aimText,
            paceText = paceHint,
            confidenceText = "$confidence · SOLVER %.1fcm".format(miss),
            speedText = "BALL %.2f m/s · CUP %.2f m/s".format(valid.recommendedBallSpeedMps, validCupSpeed),
            breakText = breakText,
            slopeText = "SIDE %+.1f%% · LONG %+.1f%% · LAUNCH %.1f°".format(
                valid.effectiveSideSlopePct,
                valid.effectiveLongSlopePct,
                valid.recommendedLaunchAngleDeg
            ),
            confidenceFraction = confidenceFraction,
            trailAlpha = trailAlpha,
            trailSampleCap = trailSampleCap,
            aimAlpha = 235,
            secondaryAlpha = 188,
            refreshMs = when {
                training -> 200L
                miss <= 2.0 -> 160L
                else -> 120L
            }
        )
    }
}

/**
 * Keeps the broadcast trail bounded without chopping off its cup-side endpoint.
 * Invalid world coordinates are rejected before projection so a malformed solver trail
 * cannot waste projection work or spray graphics outside the useful green envelope.
 */
object V113BroadcastTrailPlanner {
    private const val MIN_POINT_SEPARATION_M = .002

    fun sample(
        raw: List<Pair<Double, Double>>,
        holeDistanceM: Double,
        cap: Int
    ): List<Pair<Double, Double>> {
        if (!holeDistanceM.isFinite() || holeDistanceM !in 0.1..40.0 || cap < 2 || raw.isEmpty()) return emptyList()

        val maxY = max(8.0, min(45.0, holeDistanceM + 5.0))
        val maxX = max(6.0, min(30.0, holeDistanceM * 1.25 + 2.0))
        val filtered = ArrayList<Pair<Double, Double>>(min(raw.size, cap * 2))
        raw.forEach { point ->
            val x = point.first
            val y = point.second
            if (!x.isFinite() || !y.isFinite()) return@forEach
            if (abs(x) > maxX || y !in -1.0..maxY) return@forEach
            val last = filtered.lastOrNull()
            if (last != null && hypot(x - last.first, y - last.second) < MIN_POINT_SEPARATION_M) return@forEach
            filtered += x to y
        }

        if (filtered.size <= cap) return filtered
        val lastIndex = filtered.lastIndex
        return List(cap) { i ->
            val index = (i.toDouble() * lastIndex.toDouble() / (cap - 1).toDouble()).roundToInt().coerceIn(0, lastIndex)
            filtered[index]
        }
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
        drawPaceLine(c, read, hudPlan)
        drawBroadcastRead(c, hudPlan)
        postInvalidateDelayed(hudPlan.refreshMs)
    }

    private fun drawPaceLine(c: Canvas, read: GreenRead?, plan: V111GreenReadHudPlan) {
        if (!plan.show || read == null || !read.solverReliable || plan.trailSampleCap <= 0) return
        val world = V113BroadcastTrailPlanner.sample(
            raw = read.predictedTrail,
            holeDistanceM = engine.settings.holeDistanceM,
            cap = plan.trailSampleCap
        )
        val pts = world.mapNotNull { (x, y) ->
            val z = GreenTerrain.effectiveHeightAt(engine.settings, x, y)
            if (!z.isFinite()) null else V25FlagProjectionRuntime.project(x, y, z + .018)
        }
        if (pts.size <= 1) return

        val ideal = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val mainStroke = max(3f, width * .0017f)

        // Dark under-stroke keeps the read line visible on both bright and shadowed green textures.
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = mainStroke * 2.25f
        p.color = Color.argb((plan.trailAlpha * .44f).toInt().coerceIn(38, 112), 2, 8, 10)
        c.drawPath(ideal, p)

        p.strokeWidth = mainStroke
        p.color = Color.argb(plan.trailAlpha, 255, 202, 61)
        c.drawPath(ideal, p)

        // Broadcast-style origin/terminal beads make direction obvious without adding another HUD label.
        p.style = Paint.Style.FILL
        val beadR = max(3.5f, width * .0024f)
        p.color = Color.argb((plan.trailAlpha * .72f).toInt().coerceIn(80, 220), 255, 245, 214)
        c.drawCircle(pts.first().x, pts.first().y, beadR * .70f, p)
        p.color = Color.argb(plan.trailAlpha, 255, 202, 61)
        c.drawCircle(pts.last().x, pts.last().y, beadR, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.5f, mainStroke * .65f)
        p.color = Color.argb((plan.trailAlpha * .78f).toInt().coerceIn(88, 220), 10, 18, 20)
        c.drawCircle(pts.last().x, pts.last().y, beadR * 1.55f, p)
        p.style = Paint.Style.FILL
    }

    private fun drawBroadcastRead(c: Canvas, plan: V111GreenReadHudPlan) {
        if (!plan.show || width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * .055f
        val top = h * .065f
        val panelW = (w * .34f).coerceAtMost(w * .44f)
        val panelH = h * .195f
        val inset = panelW * .055f

        p.style = Paint.Style.FILL
        p.color = Color.argb(138, 5, 10, 14)
        c.drawRoundRect(left, top, left + panelW, top + panelH, h * .014f, h * .014f, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.LEFT
        p.textSize = max(13f, w * .0125f)
        p.color = Color.argb(plan.aimAlpha, 255, 220, 92)
        c.drawText(plan.aimText, left + inset, top + panelH * .20f, p)

        p.typeface = Typeface.DEFAULT
        p.textSize = max(11f, w * .0091f)
        p.color = Color.argb(plan.secondaryAlpha, 235, 244, 248)
        c.drawText(plan.speedText, left + inset, top + panelH * .39f, p)
        c.drawText(plan.breakText, left + inset, top + panelH * .55f, p)
        c.drawText(plan.slopeText, left + inset, top + panelH * .70f, p)
        c.drawText(plan.paceText, left + inset, top + panelH * .84f, p)

        val meterLeft = left + inset
        val meterRight = left + panelW - inset
        val meterTop = top + panelH * .91f
        val meterH = max(2f, h * .004f)
        p.color = Color.argb(72, 200, 214, 220)
        c.drawRoundRect(meterLeft, meterTop, meterRight, meterTop + meterH, meterH, meterH, p)
        val fillRight = meterLeft + (meterRight - meterLeft) * plan.confidenceFraction.coerceIn(0f, 1f)
        p.color = Color.argb(plan.trailAlpha, 255, 220, 92)
        c.drawRoundRect(meterLeft, meterTop, fillRight, meterTop + meterH, meterH, meterH, p)

        p.textSize = max(9f, w * .0076f)
        p.color = Color.argb((plan.secondaryAlpha * .78f).toInt().coerceIn(0, 255), 188, 211, 219)
        c.drawText(plan.confidenceText, left + inset, top + panelH * .985f, p)
        p.textAlign = Paint.Align.LEFT
    }
}
