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

enum class V95ShotPhase { ADDRESS, ROLL, CUP_APPROACH, RESULT }

data class V95SoloDebriefPlan(
    val phase: V95ShotPhase,
    val headline: String,
    val speedMps: Double,
    val distanceToCupM: Double,
    val lateralCm: Double,
    val longitudinalCm: Double,
    val cupContacts: Int,
    val trailSamples: Int,
    val stimpM: Double,
    val sideSlopePct: Double,
    val longSlopePct: Double,
    val refreshMs: Long
)

object V95SoloDebriefPlanner {
    fun plan(settings: GreenSettings, state: SimState?, result: SimResult?): V95SoloDebriefPlan {
        val sx = state?.x?.takeIf { it.isFinite() } ?: 0.0
        val sy = state?.y?.takeIf { it.isFinite() } ?: 0.0
        val speed = state?.let { hypot(it.vx, it.vy) }?.takeIf { it.isFinite() } ?: 0.0
        val liveDistance = hypot(sx, settings.holeDistanceM - sy).takeIf { it.isFinite() }
            ?: settings.holeDistanceM.coerceAtLeast(0.0)
        val resultDistance = result?.distanceToCupM?.takeIf { it.isFinite() && it >= 0.0 }
        val distance = resultDistance ?: liveDistance
        val lateral = ((result?.finishX ?: sx) * 100.0).takeIf { it.isFinite() } ?: 0.0
        val longitudinal = (((result?.finishY ?: sy) - settings.holeDistanceM) * 100.0)
            .takeIf { it.isFinite() } ?: 0.0

        val phase = when {
            result != null -> V95ShotPhase.RESULT
            state?.running == true && liveDistance <= 0.75 -> V95ShotPhase.CUP_APPROACH
            state?.running == true -> V95ShotPhase.ROLL
            else -> V95ShotPhase.ADDRESS
        }

        val headline = when {
            result?.holed == true -> "HOLED"
            result?.lipOut == true -> "LIP OUT"
            result != null && result.distanceToCupM <= 0.15 -> "TAP-IN"
            result != null && abs(longitudinal) >= abs(lateral) && longitudinal < 0.0 -> "SHORT"
            result != null && abs(longitudinal) >= abs(lateral) && longitudinal > 0.0 -> "LONG"
            result != null && lateral < 0.0 -> "LEFT"
            result != null && lateral > 0.0 -> "RIGHT"
            phase == V95ShotPhase.CUP_APPROACH -> "CUP APPROACH"
            phase == V95ShotPhase.ROLL -> "ROLLING"
            else -> "READY"
        }

        val refresh = when (phase) {
            V95ShotPhase.ROLL, V95ShotPhase.CUP_APPROACH -> 33L
            V95ShotPhase.RESULT -> 120L
            V95ShotPhase.ADDRESS -> 240L
        }

        return V95SoloDebriefPlan(
            phase = phase,
            headline = headline,
            speedMps = speed.coerceAtLeast(0.0),
            distanceToCupM = distance.coerceAtLeast(0.0),
            lateralCm = lateral,
            longitudinalCm = longitudinal,
            cupContacts = (result?.cupContacts ?: state?.cupContacts ?: 0).coerceAtLeast(0),
            trailSamples = state?.trail?.size?.coerceAtLeast(0) ?: 0,
            stimpM = settings.stimpMeters.takeIf { it.isFinite() } ?: 0.0,
            sideSlopePct = settings.sideSlopePct.takeIf { it.isFinite() } ?: 0.0,
            longSlopePct = settings.longSlopePct.takeIf { it.isFinite() } ?: 0.0,
            refreshMs = refresh
        )
    }
}

/**
 * SOLO-only TV information layer. It is read-only: no measurement, physics or training state is mutated.
 * The rail deliberately changes content by shot phase so the TV stays useful without becoming a debug dump.
 */
class V95SoloBroadcastDebriefView(
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
        val plan = V95SoloDebriefPlanner.plan(engine.settings, engine.state, engine.lastResult)
        drawRail(c, plan)
        postInvalidateDelayed(plan.refreshMs)
    }

    private fun drawRail(c: Canvas, plan: V95SoloDebriefPlan) {
        val w = width.toFloat()
        val h = height.toFloat()
        val railW = w * .225f
        val railH = h * .145f
        val left = w - railW - w * .026f
        val top = h * .052f
        box.set(left, top, left + railW, top + railH)

        p.style = Paint.Style.FILL
        p.color = Color.argb(176, 3, 8, 11)
        c.drawRoundRect(box, railH * .14f, railH * .14f, p)

        val accent = when (plan.headline) {
            "HOLED" -> Color.rgb(246, 190, 74)
            "LIP OUT" -> Color.rgb(255, 129, 94)
            "CUP APPROACH" -> Color.rgb(101, 226, 255)
            else -> Color.rgb(214, 228, 232)
        }
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(10f, w * .0061f)
        p.color = accent
        c.drawText(plan.headline, left + railW * .06f, top + railH * .22f, p)

        p.typeface = Typeface.DEFAULT
        p.textSize = max(8f, w * .0047f)
        p.color = Color.argb(190, 224, 232, 235)
        c.drawText(phaseLabel(plan.phase), left + railW * .06f, top + railH * .39f, p)

        val first = when (plan.phase) {
            V95ShotPhase.ADDRESS -> "STIMP ${fmt(plan.stimpM, 1)}m"
            V95ShotPhase.ROLL, V95ShotPhase.CUP_APPROACH -> "SPEED ${fmt(plan.speedMps, 2)} m/s"
            V95ShotPhase.RESULT -> "LEAVE ${fmt(plan.distanceToCupM * 100.0, 1)} cm"
        }
        val second = when (plan.phase) {
            V95ShotPhase.ADDRESS -> "SLOPE ${signed(plan.sideSlopePct)} / ${signed(plan.longSlopePct)}%"
            V95ShotPhase.ROLL, V95ShotPhase.CUP_APPROACH -> "TO CUP ${fmt(plan.distanceToCupM, 2)} m"
            V95ShotPhase.RESULT -> "SIDE ${signed(plan.lateralCm)} cm"
        }
        val third = when (plan.phase) {
            V95ShotPhase.ADDRESS -> "TARGET ${fmt(engine.settings.holeDistanceM, 1)} m"
            V95ShotPhase.ROLL, V95ShotPhase.CUP_APPROACH -> "BREAK ${breakLabel(plan.lateralCm)}"
            V95ShotPhase.RESULT -> "DEPTH ${signed(plan.longitudinalCm)} cm"
        }
        val fourth = when (plan.phase) {
            V95ShotPhase.ADDRESS -> "SOLO READY"
            V95ShotPhase.ROLL, V95ShotPhase.CUP_APPROACH -> "TRAIL ${plan.trailSamples}  CUP ${plan.cupContacts}"
            V95ShotPhase.RESULT -> "CUP TOUCHES ${plan.cupContacts}"
        }

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(10f, w * .0058f)
        p.color = Color.WHITE
        val x = left + railW * .06f
        c.drawText(first, x, top + railH * .56f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = max(8f, w * .0048f)
        p.color = Color.argb(218, 229, 236, 238)
        c.drawText(second, x, top + railH * .70f, p)
        c.drawText(third, x, top + railH * .83f, p)
        p.color = Color.argb(150, 196, 211, 215)
        c.drawText(fourth, x, top + railH * .95f, p)

        drawProgress(c, plan, left, top, railW, railH)
    }

    private fun drawProgress(c: Canvas, plan: V95SoloDebriefPlan, left: Float, top: Float, railW: Float, railH: Float) {
        val target = engine.settings.holeDistanceM.coerceAtLeast(.01)
        val progress = (1.0 - plan.distanceToCupM / target).coerceIn(0.0, 1.0).toFloat()
        val barLeft = left + railW * .60f
        val barTop = top + railH * .18f
        val barW = railW * .32f
        val barH = max(2f, min(width, height) * .0022f)
        p.color = Color.argb(62, 255, 255, 255)
        c.drawRoundRect(barLeft, barTop, barLeft + barW, barTop + barH, barH, barH, p)
        p.color = if (plan.phase == V95ShotPhase.RESULT && plan.headline == "HOLED") {
            Color.rgb(246, 190, 74)
        } else Color.rgb(101, 226, 255)
        c.drawRoundRect(barLeft, barTop, barLeft + barW * progress, barTop + barH, barH, barH, p)
    }

    private fun phaseLabel(phase: V95ShotPhase) = when (phase) {
        V95ShotPhase.ADDRESS -> "ADDRESS"
        V95ShotPhase.ROLL -> "BALL TRACK"
        V95ShotPhase.CUP_APPROACH -> "CUP WINDOW"
        V95ShotPhase.RESULT -> "SHOT DEBRIEF"
    }

    private fun breakLabel(cm: Double): String = when {
        cm < -1.0 -> "LEFT ${fmt(abs(cm), 0)}cm"
        cm > 1.0 -> "RIGHT ${fmt(abs(cm), 0)}cm"
        else -> "CENTER"
    }

    private fun signed(v: Double): String = if (v >= 0.0) "+${fmt(v, 1)}" else fmt(v, 1)

    private fun fmt(v: Double, digits: Int): String = when (digits) {
        0 -> "%.0f".format(v)
        1 -> "%.1f".format(v)
        else -> "%.2f".format(v)
    }
}
