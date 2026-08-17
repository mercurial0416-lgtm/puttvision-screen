package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class V118PaceWindowPhase { ADDRESS, APPROACH, RESULT_NEAR, HIDDEN }

data class V118CupPaceWindowPlan(
    val phase: V118PaceWindowPhase,
    val targetDistanceM: Double,
    val ringRadiiM: List<Double>,
    val segments: Int,
    val ringAlpha: Int,
    val labelAlpha: Int,
    val leaveMarkerAlpha: Int,
    val refreshMs: Long
)

/**
 * Visual-only SOLO aid: projected leave-distance rings around the cup.
 * It never changes GreenPhysics, measured metrics, training scores, or shot outcomes.
 */
object V118CupPaceWindowPlanner {
    private val RINGS = listOf(0.15, 0.30, 0.60)

    fun plan(
        targetDistanceM: Double,
        running: Boolean,
        speedMps: Double,
        distanceToCupM: Double,
        hasResult: Boolean,
        renderTier: V24RenderTier
    ): V118CupPaceWindowPlan {
        val target = targetDistanceM.takeIf { it.isFinite() && it in 0.20..33.0 }
            ?: return hidden(renderTier)
        val speed = speedMps.takeIf { it.isFinite() && it >= 0.0 } ?: Double.POSITIVE_INFINITY
        val distance = distanceToCupM.takeIf { it.isFinite() && it >= 0.0 } ?: Double.POSITIVE_INFINITY

        val phase = when {
            hasResult && distance <= 0.75 -> V118PaceWindowPhase.RESULT_NEAR
            hasResult -> V118PaceWindowPhase.HIDDEN
            running && distance <= 1.25 && speed <= 1.60 -> V118PaceWindowPhase.APPROACH
            running -> V118PaceWindowPhase.HIDDEN
            else -> V118PaceWindowPhase.ADDRESS
        }

        if (phase == V118PaceWindowPhase.HIDDEN) return hidden(renderTier)

        val segments = when (renderTier) {
            V24RenderTier.HIGH -> 14
            V24RenderTier.BALANCED -> 10
            V24RenderTier.PERFORMANCE -> 8
        }
        val tierScale = when (renderTier) {
            V24RenderTier.HIGH -> 1.0
            V24RenderTier.BALANCED -> 0.82
            V24RenderTier.PERFORMANCE -> 0.62
        }
        val baseAlpha = when (phase) {
            V118PaceWindowPhase.ADDRESS -> 62
            V118PaceWindowPhase.APPROACH -> 104
            V118PaceWindowPhase.RESULT_NEAR -> 86
            V118PaceWindowPhase.HIDDEN -> 0
        }
        val labelBase = when (phase) {
            V118PaceWindowPhase.ADDRESS -> 116
            V118PaceWindowPhase.APPROACH -> 154
            V118PaceWindowPhase.RESULT_NEAR -> 136
            V118PaceWindowPhase.HIDDEN -> 0
        }
        return V118CupPaceWindowPlan(
            phase = phase,
            targetDistanceM = target,
            ringRadiiM = RINGS,
            segments = segments,
            ringAlpha = (baseAlpha * tierScale).toInt().coerceIn(0, 180),
            labelAlpha = (labelBase * tierScale).toInt().coerceIn(0, 200),
            leaveMarkerAlpha = if (phase == V118PaceWindowPhase.RESULT_NEAR) {
                (190 * tierScale).toInt().coerceIn(0, 220)
            } else 0,
            refreshMs = when (phase) {
                V118PaceWindowPhase.APPROACH -> when (renderTier) {
                    V24RenderTier.HIGH -> 33L
                    V24RenderTier.BALANCED -> 50L
                    V24RenderTier.PERFORMANCE -> 66L
                }
                V118PaceWindowPhase.RESULT_NEAR -> 140L
                V118PaceWindowPhase.ADDRESS -> 260L
                V118PaceWindowPhase.HIDDEN -> 320L
            }
        )
    }

    private fun hidden(tier: V24RenderTier) = V118CupPaceWindowPlan(
        phase = V118PaceWindowPhase.HIDDEN,
        targetDistanceM = 0.0,
        ringRadiiM = emptyList(),
        segments = when (tier) {
            V24RenderTier.HIGH -> 14
            V24RenderTier.BALANCED -> 10
            V24RenderTier.PERFORMANCE -> 8
        },
        ringAlpha = 0,
        labelAlpha = 0,
        leaveMarkerAlpha = 0,
        refreshMs = 320L
    )
}

class V118CupPaceWindowView(
    context: Context,
    private val engine: GameEngine
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var renderTier = V24TvQualityRuntime.snapshot(context).tier
    private var renderTierCheckedAtMs = SystemClock.uptimeMillis()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (width <= 0 || height <= 0) return

        val now = SystemClock.uptimeMillis()
        if (now - renderTierCheckedAtMs >= 900L) {
            renderTier = V24TvQualityRuntime.snapshot(context).tier
            renderTierCheckedAtMs = now
        }

        val settings = engine.settings
        val state = engine.state
        val result = engine.lastResult
        val target = settings.holeDistanceM
        val x = state?.x?.takeIf { it.isFinite() } ?: 0.0
        val y = state?.y?.takeIf { it.isFinite() } ?: 0.0
        val speed = state?.let { hypot(it.vx, it.vy) }?.takeIf { it.isFinite() } ?: 0.0
        val liveDistance = if (target.isFinite()) hypot(x, target - y) else Double.POSITIVE_INFINITY
        val distance = result?.distanceToCupM?.takeIf { it.isFinite() && it >= 0.0 } ?: liveDistance
        val running = state?.running == true || TvInstantRollRuntime.isAnimating()

        val plan = V118CupPaceWindowPlanner.plan(
            targetDistanceM = target,
            running = running,
            speedMps = speed,
            distanceToCupM = distance,
            hasResult = result != null,
            renderTier = renderTier
        )

        if (plan.phase != V118PaceWindowPhase.HIDDEN) {
            drawRings(c, settings, plan)
            drawLabel(c, settings, plan)
            if (result != null && plan.leaveMarkerAlpha > 0) drawLeaveMarker(c, settings, result, plan)
        }
        postInvalidateDelayed(plan.refreshMs)
    }

    private fun drawRings(c: Canvas, settings: GreenSettings, plan: V118CupPaceWindowPlan) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.2f, min(width, height) * .00135f)
        val accent = when (plan.phase) {
            V118PaceWindowPhase.APPROACH -> Color.rgb(255, 222, 128)
            V118PaceWindowPhase.RESULT_NEAR -> Color.rgb(170, 236, 214)
            else -> Color.rgb(140, 225, 255)
        }

        plan.ringRadiiM.forEachIndexed { index, radiusM ->
            path.reset()
            var points = 0
            for (i in 0..plan.segments) {
                val angle = 2.0 * PI * i / plan.segments.toDouble()
                val wx = cos(angle) * radiusM
                val wy = plan.targetDistanceM + sin(angle) * radiusM
                val z = GreenTerrain.effectiveHeightAt(settings, wx, wy)
                if (!z.isFinite()) continue
                val projected = V25FlagProjectionRuntime.project(wx, wy, z + .002) ?: continue
                if (points == 0) path.moveTo(projected.x, projected.y) else path.lineTo(projected.x, projected.y)
                points++
            }
            if (points >= 4) {
                val alphaScale = 1.0 - index * .18
                p.color = Color.argb((plan.ringAlpha * alphaScale).toInt().coerceIn(0, 180), Color.red(accent), Color.green(accent), Color.blue(accent))
                c.drawPath(path, p)
            }
        }
        p.style = Paint.Style.FILL
    }

    private fun drawLabel(c: Canvas, settings: GreenSettings, plan: V118CupPaceWindowPlan) {
        if (plan.labelAlpha <= 0) return
        val z = GreenTerrain.effectiveHeightAt(settings, 0.0, plan.targetDistanceM)
        if (!z.isFinite()) return
        val cup = V25FlagProjectionRuntime.project(0.0, plan.targetDistanceM, z + .004) ?: return
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = max(10f, width * .0060f)
        p.color = Color.argb(plan.labelAlpha, 225, 244, 245)
        val label = when (plan.phase) {
            V118PaceWindowPhase.ADDRESS -> "PACE WINDOW  15 · 30 · 60 cm"
            V118PaceWindowPhase.APPROACH -> "CUP SPEED ZONE"
            V118PaceWindowPhase.RESULT_NEAR -> "LEAVE ZONE"
            V118PaceWindowPhase.HIDDEN -> ""
        }
        c.drawText(label, cup.x, cup.y - max(14f, height * .032f), p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    private fun drawLeaveMarker(c: Canvas, settings: GreenSettings, result: SimResult, plan: V118CupPaceWindowPlan) {
        val x = result.finishX.takeIf { it.isFinite() } ?: return
        val y = result.finishY.takeIf { it.isFinite() } ?: return
        if (kotlin.math.abs(x) > 8.0 || y !in -2.0..40.0) return
        val z = GreenTerrain.effectiveHeightAt(settings, x, y)
        if (!z.isFinite()) return
        val point = V25FlagProjectionRuntime.project(x, y, z + .004) ?: return
        val r = max(4f, min(width, height) * .008f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.5f, min(width, height) * .0018f)
        p.color = Color.argb(plan.leaveMarkerAlpha, 240, 250, 248)
        c.drawCircle(point.x, point.y, r, p)
        p.style = Paint.Style.FILL
        p.color = Color.argb((plan.leaveMarkerAlpha * .72).toInt().coerceIn(0, 220), 240, 250, 248)
        c.drawCircle(point.x, point.y, max(2f, r * .24f), p)
    }
}
