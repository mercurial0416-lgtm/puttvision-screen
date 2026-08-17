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
import android.widget.FrameLayout
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class V120TvPhase { ADDRESS, ROLL, CUP_APPROACH, HOLED, LIP_OUT, RESULT }

data class V120TvRenderPlan(
    val phase: V120TvPhase,
    val refreshMs: Long,
    val courseSamples: Int,
    val trailSamples: Int,
    val cupRingSegments: Int,
    val vignetteAlpha: Int,
    val ambientAlpha: Int,
    val ballHaloAlpha: Int,
    val cupFocusAlpha: Int,
    val readLineAlpha: Int,
    val hudAlpha: Int,
    val showResultCard: Boolean,
    val celebrationCount: Int
)

/** Pure, bounded policy for the V2 renderer. It never changes simulation or measurement state. */
object V120TvRendererV2Planner {
    fun plan(
        running: Boolean,
        progress01: Double,
        speedMps: Double,
        result: SimResult?,
        resultAgeMs: Long,
        tier: V24RenderTier
    ): V120TvRenderPlan {
        val progress = progress01.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        val speed = speedMps.takeIf { it.isFinite() }?.coerceIn(0.0, 5.0) ?: 0.0
        val age = resultAgeMs.coerceAtLeast(0L)
        val resultAlive = result != null && age < 2200L
        val phase = when {
            result?.holed == true -> V120TvPhase.HOLED
            result?.lipOut == true -> V120TvPhase.LIP_OUT
            result != null -> V120TvPhase.RESULT
            running && progress >= .74 -> V120TvPhase.CUP_APPROACH
            running -> V120TvPhase.ROLL
            else -> V120TvPhase.ADDRESS
        }
        val courseSamples = when (tier) {
            V24RenderTier.HIGH -> 30
            V24RenderTier.BALANCED -> 22
            V24RenderTier.PERFORMANCE -> 14
        }
        val trailSamples = when (tier) {
            V24RenderTier.HIGH -> 44
            V24RenderTier.BALANCED -> 30
            V24RenderTier.PERFORMANCE -> 18
        }
        val cupSegments = when (tier) {
            V24RenderTier.HIGH -> 36
            V24RenderTier.BALANCED -> 26
            V24RenderTier.PERFORMANCE -> 18
        }
        val speed01 = (speed / 2.0).coerceIn(0.0, 1.0)
        val pulse = if (resultAlive) (1.0 - age / 2200.0).coerceIn(0.0, 1.0) else 0.0
        return V120TvRenderPlan(
            phase = phase,
            refreshMs = when {
                running -> tier.movingFrameMs
                resultAlive -> 33L
                else -> max(180L, tier.idleFrameMs * 2L)
            },
            courseSamples = courseSamples,
            trailSamples = trailSamples,
            cupRingSegments = cupSegments,
            vignetteAlpha = when (phase) {
                V120TvPhase.ROLL -> 42
                V120TvPhase.CUP_APPROACH -> 54
                V120TvPhase.HOLED, V120TvPhase.LIP_OUT -> 58
                else -> 46
            },
            ambientAlpha = if (running) 10 else 18,
            ballHaloAlpha = when (phase) {
                V120TvPhase.ROLL -> (20 + speed01 * 36.0).roundToInt()
                V120TvPhase.CUP_APPROACH -> (28 + speed01 * 28.0).roundToInt()
                else -> 18
            }.coerceIn(0, 60),
            cupFocusAlpha = when (phase) {
                V120TvPhase.CUP_APPROACH -> 106
                V120TvPhase.HOLED -> (150 * pulse).roundToInt()
                V120TvPhase.LIP_OUT -> (112 * pulse).roundToInt()
                V120TvPhase.RESULT -> (72 * pulse).roundToInt()
                V120TvPhase.ADDRESS -> 44
                V120TvPhase.ROLL -> 28
            }.coerceIn(0, 160),
            readLineAlpha = if (running) 0 else 110,
            hudAlpha = if (running) 176 else 205,
            showResultCard = result != null,
            celebrationCount = when {
                !resultAlive -> 0
                phase == V120TvPhase.HOLED -> when (tier) {
                    V24RenderTier.HIGH -> 14
                    V24RenderTier.BALANCED -> 9
                    V24RenderTier.PERFORMANCE -> 5
                }
                phase == V120TvPhase.LIP_OUT -> when (tier) {
                    V24RenderTier.HIGH -> 7
                    V24RenderTier.BALANCED -> 5
                    V24RenderTier.PERFORMANCE -> 3
                }
                else -> 0
            }
        )
    }
}

/** Canonical V2 surface intentionally keeps only world + one visual language + functional overlays. */
object V120TvLayerPolicy {
    val canonicalLayers = listOf("WORLD", "RENDERER_V2", "TRAINING", "REPLAY")
    const val legacyDecorativeLayersEnabled = false
}

/**
 * Reuses the proven V18 OpenGL world but removes its legacy 2D HUD stack. The older files stay in
 * the repository for rollback; they are simply no longer mounted on the canonical TV surface.
 */
object V120WorldOnlyFactory {
    fun create(context: Context, engine: GameEngine): View {
        val world = V18SimulatorFactory.create(context, engine)
        if (world is V18SimulatorStage) {
            while (world.childCount > 1) world.removeViewAt(world.childCount - 1)
        }
        return world
    }
}

/**
 * PuttVision TV renderer V2. One presentation layer owns course framing, ball/cup focus, read line,
 * live trail and the minimal broadcast HUD. Measurement, HFR, calibration and GreenPhysics remain
 * read-only inputs.
 */
class V120TvRendererV2View(
    context: Context,
    private val engine: GameEngine
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var seenResult: SimResult? = null
    private var resultSeenAtMs = 0L

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
        if (result !== seenResult) {
            seenResult = result
            resultSeenAtMs = if (result == null) 0L else SystemClock.uptimeMillis()
        }

        val animated = TvInstantRollRuntime.displayPosition(state)
        val ballX = animated?.first ?: state?.x ?: V26BallStartRuntime.current(settings).first
        val ballY = animated?.second ?: state?.y ?: V26BallStartRuntime.current(settings).second
        val target = settings.holeDistanceM.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val progress = if (target > .20 && ballY.isFinite()) (ballY / target).coerceIn(0.0, 1.0) else 0.0
        val speed = state?.let { hypot(it.vx, it.vy) }?.takeIf { it.isFinite() } ?: 0.0
        val running = state?.running == true || TvInstantRollRuntime.isAnimating()
        val age = if (resultSeenAtMs == 0L) Long.MAX_VALUE else SystemClock.uptimeMillis() - resultSeenAtMs
        val tier = V24TvQualityRuntime.snapshot(context.applicationContext).tier
        val plan = V120TvRendererV2Planner.plan(running, progress, speed, result, age, tier)

        drawWorldGrade(c, plan)
        drawCourseFrame(c, settings, plan)
        drawCupPaceZones(c, settings, plan)
        drawReadLine(c, settings, plan)
        drawLiveTrail(c, settings, state, plan)
        drawBallFocus(c, settings, ballX, ballY, plan)
        drawCupFocus(c, settings, plan)
        drawCelebration(c, settings, plan, age)
        drawHud(c, settings, state, result, plan)
        drawVignette(c, plan)

        postInvalidateDelayed(plan.refreshMs)
    }

    private fun drawWorldGrade(c: Canvas, plan: V120TvRenderPlan) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(
                Color.argb(plan.ambientAlpha, 7, 20, 24),
                Color.TRANSPARENT,
                Color.argb((plan.ambientAlpha * .8f).roundToInt(), 2, 12, 10)
            ),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null
    }

    private fun drawCourseFrame(c: Canvas, settings: GreenSettings, plan: V120TvRenderPlan) {
        val target = settings.holeDistanceM.takeIf { it.isFinite() && it > 0.0 } ?: return
        val visible = (target + 2.5).coerceIn(4.0, 28.0)
        val halfWidth = 3.25
        val samples = plan.courseSamples.coerceAtLeast(8)

        // Subtle projected fringe edges replace the old pile of mowing/debug guide overlays.
        listOf(-halfWidth to Color.argb(74, 112, 178, 105), halfWidth to Color.argb(74, 112, 178, 105)).forEach { (x, color) ->
            path.reset()
            var started = false
            for (i in 0..samples) {
                val y = visible * i / samples.toDouble()
                val z = GreenTerrain.effectiveHeightAt(settings, x, y)
                if (!z.isFinite()) continue
                val sp = V25FlagProjectionRuntime.project(x, y, z + .006) ?: continue
                if (!started) { path.moveTo(sp.x, sp.y); started = true } else path.lineTo(sp.x, sp.y)
            }
            if (started) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(1.2f, min(width, height) * .0014f)
                p.color = color
                p.shader = null
                c.drawPath(path, p)
            }
        }

        // Sparse depth cuts read as course scale instead of a debug grid.
        val spacing = when {
            visible <= 8.0 -> 2.0
            visible <= 16.0 -> 3.0
            else -> 4.0
        }
        val cuts = ceil(visible / spacing).toInt().coerceIn(1, 7)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, min(width, height) * .0008f)
        for (i in 1..cuts) {
            val y = i * spacing
            if (y >= visible) break
            val leftZ = GreenTerrain.effectiveHeightAt(settings, -1.1, y)
            val rightZ = GreenTerrain.effectiveHeightAt(settings, 1.1, y)
            if (!leftZ.isFinite() || !rightZ.isFinite()) continue
            val l = V25FlagProjectionRuntime.project(-1.1, y, leftZ + .007) ?: continue
            val r = V25FlagProjectionRuntime.project(1.1, y, rightZ + .007) ?: continue
            p.color = Color.argb(if (plan.phase == V120TvPhase.ADDRESS) 28 else 14, 223, 244, 226)
            c.drawLine(l.x, l.y, r.x, r.y, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun drawCupPaceZones(c: Canvas, settings: GreenSettings, plan: V120TvRenderPlan) {
        val target = settings.holeDistanceM.takeIf { it.isFinite() && it > 0.0 } ?: return
        val rings = doubleArrayOf(.15, .30, .60)
        val alphas = intArrayOf(88, 54, 30)
        rings.forEachIndexed { index, radius ->
            path.reset()
            var started = false
            for (i in 0..plan.cupRingSegments) {
                val a = 2.0 * PI * i / plan.cupRingSegments.toDouble()
                val x = cos(a) * radius
                val y = target + sin(a) * radius
                val z = GreenTerrain.effectiveHeightAt(settings, x, y)
                if (!z.isFinite()) continue
                val sp = V25FlagProjectionRuntime.project(x, y, z + .009) ?: continue
                if (!started) { path.moveTo(sp.x, sp.y); started = true } else path.lineTo(sp.x, sp.y)
            }
            if (started) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(1.1f, min(width, height) * if (index == 0) .0015f else .0010f)
                p.color = when (index) {
                    0 -> Color.argb(alphas[index], 255, 219, 118)
                    1 -> Color.argb(alphas[index], 188, 236, 205)
                    else -> Color.argb(alphas[index], 220, 239, 230)
                }
                c.drawPath(path, p)
            }
        }
        p.style = Paint.Style.FILL
    }

    private fun drawReadLine(c: Canvas, settings: GreenSettings, plan: V120TvRenderPlan) {
        if (plan.readLineAlpha <= 0) return
        val hidden = V20GreenReadTrainingRuntime.shouldHideSolution(engine.gameModes.status.mode, settings) && !engine.readFeedback.revealed
        if (hidden) return
        val read = GreenReadRuntime.peekOrSchedule(settings) ?: return
        if (!read.solverReliable || read.predictedTrail.size < 2) return
        val maxPoints = min(plan.trailSamples, read.predictedTrail.size)
        val step = max(1, read.predictedTrail.size / maxPoints.coerceAtLeast(1))
        path.reset()
        var started = false
        var drawn = 0
        var i = 0
        while (i < read.predictedTrail.size && drawn < maxPoints) {
            val pt = read.predictedTrail[i]
            val z = GreenTerrain.effectiveHeightAt(settings, pt.first, pt.second)
            if (z.isFinite()) {
                val sp = V25FlagProjectionRuntime.project(pt.first, pt.second, z + .012)
                if (sp != null) {
                    if (!started) { path.moveTo(sp.x, sp.y); started = true } else path.lineTo(sp.x, sp.y)
                    drawn++
                }
            }
            i += step
        }
        if (started) {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = max(2.0f, min(width, height) * .0021f)
            p.color = Color.argb(plan.readLineAlpha, 116, 232, 255)
            c.drawPath(path, p)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        }
    }

    private fun drawLiveTrail(c: Canvas, settings: GreenSettings, state: SimState?, plan: V120TvRenderPlan) {
        val trail = state?.trail ?: return
        if (trail.size < 2) return
        val keep = min(plan.trailSamples, trail.size)
        val startIndex = (trail.size - keep).coerceAtLeast(0)
        path.reset()
        var started = false
        for (i in startIndex until trail.size) {
            val pt = trail[i]
            if (!pt.first.isFinite() || !pt.second.isFinite()) continue
            val z = GreenTerrain.effectiveHeightAt(settings, pt.first, pt.second)
            if (!z.isFinite()) continue
            val sp = V25FlagProjectionRuntime.project(pt.first, pt.second, z + .014) ?: continue
            if (!started) { path.moveTo(sp.x, sp.y); started = true } else path.lineTo(sp.x, sp.y)
        }
        if (started) {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = max(2.2f, min(width, height) * .0025f)
            p.color = Color.argb(if (plan.phase == V120TvPhase.CUP_APPROACH) 190 else 150, 245, 250, 252)
            c.drawPath(path, p)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        }
    }

    private fun drawBallFocus(c: Canvas, settings: GreenSettings, x: Double, y: Double, plan: V120TvRenderPlan) {
        if (plan.ballHaloAlpha <= 0 || !x.isFinite() || !y.isFinite()) return
        val z = GreenTerrain.effectiveHeightAt(settings, x, y)
        if (!z.isFinite()) return
        val sp = V25FlagProjectionRuntime.project(x, y, z + .024) ?: return
        val r = min(width, height) * .042f
        p.shader = RadialGradient(
            sp.x, sp.y, r,
            intArrayOf(Color.argb(plan.ballHaloAlpha, 225, 247, 255), Color.argb(plan.ballHaloAlpha / 4, 134, 227, 255), Color.TRANSPARENT),
            floatArrayOf(0f, .32f, 1f), Shader.TileMode.CLAMP
        )
        p.style = Paint.Style.FILL
        c.drawCircle(sp.x, sp.y, r, p)
        p.shader = null
    }

    private fun drawCupFocus(c: Canvas, settings: GreenSettings, plan: V120TvRenderPlan) {
        if (plan.cupFocusAlpha <= 0) return
        val target = settings.holeDistanceM.takeIf { it.isFinite() && it > 0.0 } ?: return
        val z = GreenTerrain.effectiveHeightAt(settings, 0.0, target)
        if (!z.isFinite()) return
        val cup = V25FlagProjectionRuntime.project(0.0, target, z + .006) ?: return
        val r = min(width, height) * if (plan.phase == V120TvPhase.CUP_APPROACH) .080f else .060f
        val color = accentColor(plan.phase)
        p.shader = RadialGradient(
            cup.x, cup.y, r,
            intArrayOf(Color.argb(plan.cupFocusAlpha / 2, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cup.x, cup.y, r, p)
        p.shader = null
    }

    private fun drawCelebration(c: Canvas, settings: GreenSettings, plan: V120TvRenderPlan, ageMs: Long) {
        if (plan.celebrationCount <= 0 || ageMs == Long.MAX_VALUE) return
        val target = settings.holeDistanceM.takeIf { it.isFinite() && it > 0.0 } ?: return
        val z = GreenTerrain.effectiveHeightAt(settings, 0.0, target)
        if (!z.isFinite()) return
        val cup = V25FlagProjectionRuntime.project(0.0, target, z + .008) ?: return
        val t = (ageMs / 2200f).coerceIn(0f, 1f)
        val color = accentColor(plan.phase)
        val spread = min(width, height) * (.025f + .12f * t)
        for (i in 0 until plan.celebrationCount) {
            val a = ((i * 137.5f) % 360f) * (PI.toFloat() / 180f)
            val lane = .35f + (i % 5) * .13f
            val px = cup.x + cos(a) * spread * lane
            val py = cup.y + sin(a) * spread * lane - min(width, height) * .025f * t
            p.color = Color.argb((125 * (1f - t)).roundToInt().coerceIn(0, 125), Color.red(color), Color.green(color), Color.blue(color))
            c.drawCircle(px, py, max(1.4f, min(width, height) * .0021f), p)
        }
    }

    private fun drawHud(c: Canvas, settings: GreenSettings, state: SimState?, result: SimResult?, plan: V120TvRenderPlan) {
        drawTopStatus(c, settings, plan)
        if (plan.showResultCard) drawResultCard(c, settings, state, result, plan) else drawBottomRibbon(c, settings, state, plan)
    }

    private fun drawTopStatus(c: Canvas, settings: GreenSettings, plan: V120TvRenderPlan) {
        val margin = width * .026f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(13f, width * .0090f)
        p.color = Color.argb(plan.hudAlpha, 245, 249, 248)
        c.drawText("PUTTVISION", margin, height * .060f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = max(10f, width * .0062f)
        p.color = Color.argb((plan.hudAlpha * .72f).roundToInt(), 215, 228, 226)
        val target = settings.holeDistanceM.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val stimp = settings.stimpMeters.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        c.drawText("${fmt(target, 1)} m   ·   STIMP ${fmt(stimp, 1)}", margin, height * .086f, p)
    }

    private fun drawBottomRibbon(c: Canvas, settings: GreenSettings, state: SimState?, plan: V120TvRenderPlan) {
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * .026f
        val top = h * .875f
        val rw = w * .56f
        val rh = h * .075f
        rect.set(left, top, left + rw, top + rh)
        p.style = Paint.Style.FILL
        p.color = Color.argb(if (plan.phase == V120TvPhase.ROLL) 145 else 174, 4, 10, 12)
        c.drawRoundRect(rect, rh * .23f, rh * .23f, p)
        val accent = accentColor(plan.phase)
        p.color = accent
        c.drawRoundRect(left, top, left + max(3f, w * .003f), top + rh, rh * .2f, rh * .2f, p)

        val metrics = engine.currentShot
        val line1 = when (plan.phase) {
            V120TvPhase.ADDRESS -> {
                val read = if (!V20GreenReadTrainingRuntime.shouldHideSolution(engine.gameModes.status.mode, settings) || engine.readFeedback.revealed) GreenReadRuntime.peekOrSchedule(settings) else null
                if (read != null && read.solverReliable) "READ  ${read.aimSideLabel}  ·  ${fmt(read.recommendedBallSpeedMps, 2)} m/s" else "READY  ·  READ THE GREEN"
            }
            V120TvPhase.ROLL, V120TvPhase.CUP_APPROACH -> {
                val speed = state?.let { hypot(it.vx, it.vy) }?.takeIf { it.isFinite() } ?: 0.0
                val remain = state?.let { hypot(it.x, settings.holeDistanceM - it.y) }?.takeIf { it.isFinite() } ?: 0.0
                "LIVE  ${fmt(speed, 2)} m/s   ·   CUP ${fmt(remain, 2)} m"
            }
            else -> "SHOT COMPLETE"
        }
        val line2 = metrics?.let {
            val face = it.faceAngleDeg?.takeIf(Double::isFinite)?.let { v -> "FACE ${signed(v, 1)}°" }
            val pathText = it.pathAngleDeg?.takeIf(Double::isFinite)?.let { v -> "PATH ${signed(v, 1)}°" }
            val ball = it.ballSpeedMps.takeIf(Double::isFinite)?.let { v -> "BALL ${fmt(v, 2)}" }
            listOfNotNull(face, pathText, ball).joinToString("   ·   ")
        }.orEmpty().ifBlank { "TARGET ${fmt(settings.holeDistanceM, 1)} m" }

        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(12f, w * .0080f)
        p.color = Color.WHITE
        c.drawText(line1, left + rw * .040f, top + rh * .42f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = max(9f, w * .0055f)
        p.color = Color.argb(185, 215, 226, 228)
        c.drawText(line2, left + rw * .040f, top + rh * .75f, p)
    }

    private fun drawResultCard(c: Canvas, settings: GreenSettings, state: SimState?, result: SimResult?, plan: V120TvRenderPlan) {
        val debrief = V95SoloDebriefPlanner.plan(settings, state, result)
        val w = width.toFloat()
        val h = height.toFloat()
        val cw = w * .34f
        val ch = h * .135f
        val left = (w - cw) * .5f
        val top = h * .765f
        rect.set(left, top, left + cw, top + ch)
        p.style = Paint.Style.FILL
        p.color = Color.argb(198, 3, 9, 11)
        c.drawRoundRect(rect, ch * .14f, ch * .14f, p)
        val accent = accentColor(plan.phase)
        p.color = accent
        c.drawRoundRect(left, top, left + cw, top + max(3f, ch * .035f), ch * .02f, ch * .02f, p)

        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(18f, w * .014f)
        p.color = Color.WHITE
        c.drawText(debrief.headline, left + cw * .5f, top + ch * .34f, p)
        p.textSize = max(11f, w * .0070f)
        p.color = accent
        c.drawText("QUALITY ${debrief.resultQualityGrade}  ${debrief.resultQualityScore}", left + cw * .5f, top + ch * .57f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = max(9f, w * .0055f)
        p.color = Color.argb(205, 219, 230, 232)
        c.drawText("LEAVE ${fmt(debrief.distanceToCupM * 100.0, 1)} cm   ·   SIDE ${signed(debrief.lateralCm, 1)} cm   ·   DEPTH ${signed(debrief.longitudinalCm, 1)} cm", left + cw * .5f, top + ch * .80f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawVignette(c: Canvas, plan: V120TvRenderPlan) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            width * .50f, height * .50f, max(width, height) * .72f,
            intArrayOf(Color.TRANSPARENT, Color.argb(plan.vignetteAlpha / 5, 0, 0, 0), Color.argb(plan.vignetteAlpha, 0, 0, 0)),
            floatArrayOf(0f, .68f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null
    }

    private fun accentColor(phase: V120TvPhase): Int = when (phase) {
        V120TvPhase.ADDRESS -> Color.rgb(126, 226, 247)
        V120TvPhase.ROLL -> Color.rgb(104, 224, 246)
        V120TvPhase.CUP_APPROACH -> Color.rgb(249, 211, 112)
        V120TvPhase.HOLED -> Color.rgb(112, 240, 170)
        V120TvPhase.LIP_OUT -> Color.rgb(255, 145, 92)
        V120TvPhase.RESULT -> Color.rgb(203, 222, 226)
    }

    private fun fmt(value: Double, digits: Int): String {
        val v = value.takeIf { it.isFinite() } ?: 0.0
        return if (digits <= 1) "%.1f".format(v) else "%.2f".format(v)
    }

    private fun signed(value: Double, digits: Int): String {
        val v = value.takeIf { it.isFinite() } ?: 0.0
        val body = if (digits <= 1) "%.1f".format(abs(v)) else "%.2f".format(abs(v))
        return if (v >= 0.0) "+$body" else "-$body"
    }
}
