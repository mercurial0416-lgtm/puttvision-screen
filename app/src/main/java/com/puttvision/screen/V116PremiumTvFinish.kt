package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class V116PremiumPhase { ADDRESS, ROLL, CUP_APPROACH, HOLED, LIP_OUT, RESULT }

data class V116PremiumTvPlan(
    val phase: V116PremiumPhase,
    val accentColor: Int,
    val ambientAlpha: Int,
    val vignetteAlpha: Int,
    val horizonAlpha: Int,
    val ballHaloAlpha: Int,
    val cupHaloAlpha: Int,
    val resultBloomAlpha: Int,
    val particleCount: Int,
    val glassAlpha: Int,
    val refreshMs: Long
)

/**
 * Visual-only premium finish for the canonical TV surface.
 * It deliberately consumes existing simulation/presentation state without mutating measurement,
 * training, GreenPhysics or shot outcomes.
 */
object V116PremiumTvPlanner {
    fun plan(
        running: Boolean,
        progress01: Double,
        speedMps: Double,
        result: SimResult?,
        resultAgeMs: Long
    ): V116PremiumTvPlan {
        val progress = progress01.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        val speed = speedMps.takeIf { it.isFinite() }?.coerceIn(0.0, 5.0) ?: 0.0
        val age = resultAgeMs.coerceAtLeast(0L)
        val resultPulse = if (result == null) 0f else (1f - age / 2200f).coerceIn(0f, 1f)
        val speed01 = (speed / 2.1).coerceIn(0.0, 1.0).toFloat()
        val phase = when {
            result?.holed == true -> V116PremiumPhase.HOLED
            result?.lipOut == true -> V116PremiumPhase.LIP_OUT
            result != null -> V116PremiumPhase.RESULT
            running && progress >= .72 -> V116PremiumPhase.CUP_APPROACH
            running -> V116PremiumPhase.ROLL
            else -> V116PremiumPhase.ADDRESS
        }

        val accent = when (phase) {
            V116PremiumPhase.ADDRESS -> Color.rgb(132, 232, 255)
            V116PremiumPhase.ROLL -> Color.rgb(102, 226, 255)
            V116PremiumPhase.CUP_APPROACH -> Color.rgb(255, 224, 138)
            V116PremiumPhase.HOLED -> Color.rgb(137, 255, 190)
            V116PremiumPhase.LIP_OUT -> Color.rgb(255, 151, 96)
            V116PremiumPhase.RESULT -> Color.rgb(215, 230, 235)
        }

        return V116PremiumTvPlan(
            phase = phase,
            accentColor = accent,
            ambientAlpha = when (phase) {
                V116PremiumPhase.ADDRESS -> 28
                V116PremiumPhase.ROLL -> 14
                V116PremiumPhase.CUP_APPROACH -> 20
                else -> 24
            },
            vignetteAlpha = when (phase) {
                V116PremiumPhase.ROLL -> 42
                V116PremiumPhase.CUP_APPROACH -> 58
                V116PremiumPhase.HOLED, V116PremiumPhase.LIP_OUT -> 64
                else -> 52
            },
            horizonAlpha = if (running) 12 else 22,
            ballHaloAlpha = when (phase) {
                V116PremiumPhase.ROLL -> (28 + 44 * speed01).roundToInt()
                V116PremiumPhase.CUP_APPROACH -> (38 + 32 * speed01).roundToInt()
                else -> 24
            }.coerceIn(0, 78),
            cupHaloAlpha = when (phase) {
                V116PremiumPhase.CUP_APPROACH -> 108
                V116PremiumPhase.HOLED -> (150 * resultPulse).roundToInt()
                V116PremiumPhase.LIP_OUT -> (118 * resultPulse).roundToInt()
                V116PremiumPhase.RESULT -> (72 * resultPulse).roundToInt()
                V116PremiumPhase.ADDRESS -> 48
                else -> 34
            }.coerceIn(0, 170),
            resultBloomAlpha = when (phase) {
                V116PremiumPhase.HOLED -> (92 * resultPulse).roundToInt()
                V116PremiumPhase.LIP_OUT -> (68 * resultPulse).roundToInt()
                V116PremiumPhase.RESULT -> (38 * resultPulse).roundToInt()
                else -> 0
            }.coerceIn(0, 100),
            particleCount = when (phase) {
                V116PremiumPhase.HOLED -> if (resultPulse > .02f) 22 else 0
                V116PremiumPhase.LIP_OUT -> if (resultPulse > .02f) 12 else 0
                V116PremiumPhase.RESULT -> if (resultPulse > .08f) 6 else 0
                else -> 0
            },
            glassAlpha = if (running) 126 else 154,
            refreshMs = if (running || resultPulse > .01f) 16L else 240L
        )
    }

    /**
     * V117: the final premium layer follows the same thermal/HFR render tier as the base TV renderer.
     * Camera stability wins over cosmetics: PERFORMANCE lowers animation cadence and celebration work,
     * while HIGH remains pixel-for-pixel equivalent to the original V116 plan.
     */
    fun adaptForRenderTier(plan: V116PremiumTvPlan, tier: V24RenderTier): V116PremiumTvPlan {
        if (tier == V24RenderTier.HIGH) return plan
        val scale = when (tier) {
            V24RenderTier.HIGH -> 1f
            V24RenderTier.BALANCED -> .86f
            V24RenderTier.PERFORMANCE -> .68f
        }
        val particleCap = when (tier) {
            V24RenderTier.HIGH -> 22
            V24RenderTier.BALANCED -> 12
            V24RenderTier.PERFORMANCE -> 6
        }
        fun scaled(value: Int, maxValue: Int): Int =
            (value.coerceAtLeast(0) * scale).roundToInt().coerceIn(0, maxValue)

        return plan.copy(
            ambientAlpha = scaled(plan.ambientAlpha, 255),
            horizonAlpha = scaled(plan.horizonAlpha, 255),
            ballHaloAlpha = scaled(plan.ballHaloAlpha, 78),
            cupHaloAlpha = scaled(plan.cupHaloAlpha, 170),
            resultBloomAlpha = scaled(plan.resultBloomAlpha, 100),
            particleCount = plan.particleCount.coerceIn(0, particleCap),
            refreshMs = maxOf(plan.refreshMs.coerceAtLeast(16L), tier.movingFrameMs)
        )
    }
}

class V116PremiumTvFinishView(
    context: Context,
    private val engine: GameEngine
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ribbon = RectF()
    private var seenResult: SimResult? = null
    private var resultSeenAtMs = 0L
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

        val settings = engine.settings
        val state = engine.state
        val result = engine.lastResult
        val now = SystemClock.uptimeMillis()
        if (result !== seenResult) {
            seenResult = result
            resultSeenAtMs = if (result == null) 0L else now
        }
        if (now - renderTierCheckedAtMs >= 750L) {
            renderTier = V24TvQualityRuntime.snapshot(context).tier
            renderTierCheckedAtMs = now
        }
        val animated = TvInstantRollRuntime.displayPosition(state)
        val y = animated?.second ?: state?.y ?: 0.0
        val progress = if (settings.holeDistanceM.isFinite() && settings.holeDistanceM > .20) {
            (y / settings.holeDistanceM).coerceIn(0.0, 1.0)
        } else 0.0
        val speed = state?.let { hypot(it.vx, it.vy) }?.takeIf { it.isFinite() } ?: 0.0
        val age = if (resultSeenAtMs == 0L) 0L else now - resultSeenAtMs
        val running = state?.running == true || TvInstantRollRuntime.isAnimating()
        val basePlan = V116PremiumTvPlanner.plan(running, progress, speed, result, age)
        val plan = V116PremiumTvPlanner.adaptForRenderTier(basePlan, renderTier)

        drawFilmicGrade(c, plan)
        drawHorizonLight(c, plan)
        drawBallHeroHalo(c, settings, state, plan)
        drawCupHeroHalo(c, settings, plan, age)
        drawResultBloom(c, plan)
        drawResultParticles(c, settings, plan, age)
        drawVignette(c, plan)
        drawBroadcastRibbon(c, V95SoloDebriefPlanner.plan(settings, state, result), plan)

        postInvalidateDelayed(plan.refreshMs)
    }

    private fun drawFilmicGrade(c: Canvas, plan: V116PremiumTvPlan) {
        val a = plan.ambientAlpha.coerceIn(0, 255)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(
                Color.argb(a, 7, 24, 24),
                Color.argb(a / 4, 13, 42, 34),
                Color.TRANSPARENT,
                Color.argb((a * .72f).roundToInt(), 2, 10, 13)
            ),
            floatArrayOf(0f, .30f, .63f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null
    }

    private fun drawHorizonLight(c: Canvas, plan: V116PremiumTvPlan) {
        if (plan.horizonAlpha <= 0) return
        val y = height * .31f
        val r = height * .25f
        p.shader = RadialGradient(
            width * .50f,
            y,
            max(width * .60f, r),
            intArrayOf(
                Color.argb(plan.horizonAlpha, 205, 244, 226),
                Color.argb(plan.horizonAlpha / 3, 130, 205, 190),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, .48f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, width.toFloat(), height * .62f, p)
        p.shader = null
    }

    private fun drawVignette(c: Canvas, plan: V116PremiumTvPlan) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            width * .50f,
            height * .49f,
            max(width, height) * .72f,
            intArrayOf(Color.TRANSPARENT, Color.argb(plan.vignetteAlpha / 4, 0, 0, 0), Color.argb(plan.vignetteAlpha, 0, 0, 0)),
            floatArrayOf(0f, .66f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null
    }

    private fun drawBallHeroHalo(
        c: Canvas,
        settings: GreenSettings,
        state: SimState?,
        plan: V116PremiumTvPlan
    ) {
        if (plan.ballHaloAlpha <= 0) return
        val start = V26BallStartRuntime.current(settings)
        val animated = TvInstantRollRuntime.displayPosition(state)
        val x = animated?.first ?: state?.x ?: start.first
        val y = animated?.second ?: state?.y ?: start.second
        if (!x.isFinite() || !y.isFinite()) return
        val z = GreenTerrain.effectiveHeightAt(settings, x, y)
        if (!z.isFinite()) return
        val ball = V25FlagProjectionRuntime.project(x, y, z + V89VisualPhysicsPlanner.BALL_RADIUS_M) ?: return
        val radius = min(width, height) * .050f
        val accent = plan.accentColor
        p.shader = RadialGradient(
            ball.x,
            ball.y,
            radius,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(plan.ballHaloAlpha, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, .36f, 1f),
            Shader.TileMode.CLAMP
        )
        p.style = Paint.Style.FILL
        c.drawCircle(ball.x, ball.y, radius, p)
        p.shader = null
    }

    private fun drawCupHeroHalo(c: Canvas, settings: GreenSettings, plan: V116PremiumTvPlan, ageMs: Long) {
        if (plan.cupHaloAlpha <= 0) return
        val target = settings.holeDistanceM.takeIf { it.isFinite() && it >= 0.0 } ?: return
        val z = GreenTerrain.effectiveHeightAt(settings, 0.0, target)
        if (!z.isFinite()) return
        val cup = V25FlagProjectionRuntime.project(0.0, target, z + .004) ?: return
        val pulse = if (plan.phase in setOf(V116PremiumPhase.HOLED, V116PremiumPhase.LIP_OUT, V116PremiumPhase.RESULT)) {
            1f + .08f * sin((ageMs.coerceAtLeast(0L) / 95.0)).toFloat()
        } else 1f
        val radius = min(width, height) * .072f * pulse
        val accent = plan.accentColor
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cup.x,
            cup.y,
            radius,
            intArrayOf(
                Color.argb(plan.cupHaloAlpha / 2, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.argb(plan.cupHaloAlpha / 5, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, .34f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cup.x, cup.y, radius, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.5f, min(width, height) * .0018f)
        p.color = Color.argb((plan.cupHaloAlpha * .72f).roundToInt().coerceIn(0, 170), Color.red(accent), Color.green(accent), Color.blue(accent))
        c.drawCircle(cup.x, cup.y, min(width, height) * .018f * pulse, p)
        p.style = Paint.Style.FILL
    }

    private fun drawResultBloom(c: Canvas, plan: V116PremiumTvPlan) {
        if (plan.resultBloomAlpha <= 0) return
        val accent = plan.accentColor
        p.shader = RadialGradient(
            width * .50f,
            height * .50f,
            max(width, height) * .64f,
            intArrayOf(
                Color.argb(plan.resultBloomAlpha, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null
    }

    private fun drawResultParticles(c: Canvas, settings: GreenSettings, plan: V116PremiumTvPlan, ageMs: Long) {
        if (plan.particleCount <= 0) return
        val target = settings.holeDistanceM.takeIf { it.isFinite() && it >= 0.0 } ?: return
        val z = GreenTerrain.effectiveHeightAt(settings, 0.0, target)
        if (!z.isFinite()) return
        val cup = V25FlagProjectionRuntime.project(0.0, target, z + .004) ?: return
        val t = (ageMs / 2200f).coerceIn(0f, 1f)
        val accent = plan.accentColor
        val spread = min(width, height) * (.055f + .16f * t)
        p.style = Paint.Style.FILL
        for (i in 0 until plan.particleCount) {
            val angle = ((i * 137.507764f) % 360f) * (PI.toFloat() / 180f)
            val lane = .35f + ((i * 37) % 63) / 100f
            val drift = spread * lane
            val px = cup.x + cos(angle) * drift
            val py = cup.y + sin(angle) * drift - min(width, height) * .035f * t
            val fade = (1f - t).coerceIn(0f, 1f)
            val alpha = (150f * fade * (.55f + (i % 3) * .18f)).roundToInt().coerceIn(0, 170)
            p.color = Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent))
            val r = max(1.5f, min(width, height) * (.0017f + (i % 4) * .00045f))
            c.drawCircle(px, py, r, p)
        }
    }

    private fun drawBroadcastRibbon(c: Canvas, debrief: V95SoloDebriefPlan, plan: V116PremiumTvPlan) {
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * .035f
        val top = h * .875f
        val rw = w * .43f
        val rh = h * .072f
        ribbon.set(left, top, left + rw, top + rh)
        val accent = plan.accentColor

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            ribbon.left,
            0f,
            ribbon.right,
            0f,
            Color.argb(plan.glassAlpha, 3, 10, 13),
            Color.argb((plan.glassAlpha * .78f).roundToInt(), 5, 16, 18),
            Shader.TileMode.CLAMP
        )
        c.drawRoundRect(ribbon, rh * .24f, rh * .24f, p)
        p.shader = null

        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, min(width, height) * .0010f)
        p.color = Color.argb(72, Color.red(accent), Color.green(accent), Color.blue(accent))
        c.drawRoundRect(ribbon, rh * .24f, rh * .24f, p)
        p.style = Paint.Style.FILL

        val lineH = max(2f, min(width, height) * .0024f)
        p.color = Color.argb(210, Color.red(accent), Color.green(accent), Color.blue(accent))
        c.drawRoundRect(ribbon.left + rw * .035f, ribbon.top + rh * .17f, ribbon.left + rw * .045f, ribbon.bottom - rh * .17f, lineH, lineH, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(12f, w * .0062f)
        p.color = Color.WHITE
        c.drawText(debrief.headline, ribbon.left + rw * .070f, ribbon.top + rh * .43f, p)

        p.typeface = Typeface.DEFAULT
        p.textSize = max(9f, w * .0046f)
        p.color = Color.argb(190, 214, 228, 232)
        c.drawText(summary(debrief), ribbon.left + rw * .070f, ribbon.top + rh * .75f, p)

        val chipW = rw * .16f
        val chipH = rh * .54f
        val chipR = chipH * .45f
        val chipLeft = ribbon.right - chipW - rw * .035f
        val chipTop = ribbon.top + (rh - chipH) * .5f
        p.color = Color.argb(42, Color.red(accent), Color.green(accent), Color.blue(accent))
        c.drawRoundRect(chipLeft, chipTop, chipLeft + chipW, chipTop + chipH, chipR, chipR, p)
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(9f, w * .0044f)
        p.color = accent
        val chipText = when (debrief.phase) {
            V95ShotPhase.RESULT -> "${debrief.resultQualityGrade} ${debrief.resultQualityScore}"
            V95ShotPhase.CUP_APPROACH -> "CUP"
            V95ShotPhase.ROLL -> "LIVE"
            V95ShotPhase.ADDRESS -> "READY"
        }
        val tw = p.measureText(chipText)
        c.drawText(chipText, chipLeft + (chipW - tw) * .5f, chipTop + chipH * .68f, p)
    }

    private fun summary(plan: V95SoloDebriefPlan): String = when (plan.phase) {
        V95ShotPhase.ADDRESS -> "TARGET ${fmt(plan.targetDistanceM, 1)} m   •   STIMP ${fmt(plan.stimpM, 1)}   •   SLOPE ${signed(plan.sideSlopePct)} / ${signed(plan.longSlopePct)}%"
        V95ShotPhase.ROLL -> "${fmt(plan.speedMps, 2)} m/s   •   ${fmt(plan.distanceToCupM, 2)} m TO CUP"
        V95ShotPhase.CUP_APPROACH -> "${fmt(plan.distanceToCupM * 100.0, 0)} cm TO CUP   •   HOLD THE LINE"
        V95ShotPhase.RESULT -> "LEAVE ${fmt(plan.distanceToCupM * 100.0, 0)} cm   •   SIDE ${signed(plan.lateralCm)} cm   •   DEPTH ${signed(plan.longitudinalCm)} cm"
    }

    private fun signed(v: Double): String = if (v >= 0.0) "+${fmt(v, 1)}" else fmt(v, 1)

    private fun fmt(v: Double, digits: Int): String = when (digits) {
        0 -> "%.0f".format(v)
        1 -> "%.1f".format(v)
        else -> "%.2f".format(v)
    }
}
