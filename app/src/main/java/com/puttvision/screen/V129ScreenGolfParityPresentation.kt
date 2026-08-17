package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * V129 is a single presentation layer for the TV / mirrored phone simulator surface.
 * It intentionally keeps proprietary logos/assets out of the product while matching the
 * information hierarchy, framing, restrained chrome and state transitions expected from a
 * modern commercial screen-golf system.
 *
 * Physics, capture, HFR, calibration and result authority remain owned by GameEngine.
 */
enum class V129PresentationPhase {
    ADDRESS,
    ROLL,
    CUP_APPROACH,
    HOLED,
    LIP_OUT,
    RESULT
}

data class V129SafeFrame(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val compact: Boolean
)

data class V129PresentationPlan(
    val phase: V129PresentationPhase,
    val safe: V129SafeFrame,
    val showHolePlate: Boolean,
    val showMiniMap: Boolean,
    val showConditions: Boolean,
    val showShotMetrics: Boolean,
    val showGreenRead: Boolean,
    val showCenterStatus: Boolean,
    val showResultCard: Boolean,
    val chromeAlpha: Int,
    val refreshMs: Long,
    val resultFlash: Float,
    val cupFocus: Float
)

object V129PresentationPlanner {
    fun safeFrame(width: Int, height: Int): V129SafeFrame {
        val w = width.coerceAtLeast(1).toFloat()
        val h = height.coerceAtLeast(1).toFloat()
        val aspect = w / h
        val compact = aspect < 1.48f
        val side = if (compact) w * .026f else w * .018f
        val top = h * .022f
        val bottom = h * .024f
        return V129SafeFrame(
            left = side,
            top = top,
            right = w - side,
            bottom = h - bottom,
            compact = compact
        )
    }

    fun classify(
        running: Boolean,
        progressRaw: Double,
        distanceToCupRaw: Double?,
        result: SimResult?
    ): V129PresentationPhase {
        if (result != null) {
            return when {
                result.holed -> V129PresentationPhase.HOLED
                result.lipOut -> V129PresentationPhase.LIP_OUT
                else -> V129PresentationPhase.RESULT
            }
        }
        if (!running) return V129PresentationPhase.ADDRESS
        val progress = progressRaw.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        val toCup = distanceToCupRaw?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
        return if ((toCup != null && toCup <= .95) || progress >= .80) {
            V129PresentationPhase.CUP_APPROACH
        } else {
            V129PresentationPhase.ROLL
        }
    }

    fun plan(
        width: Int,
        height: Int,
        running: Boolean,
        progressRaw: Double,
        distanceToCupRaw: Double?,
        result: SimResult?,
        resultAgeMsRaw: Long,
        hasShot: Boolean
    ): V129PresentationPlan {
        val safe = safeFrame(width, height)
        val phase = classify(running, progressRaw, distanceToCupRaw, result)
        val age = resultAgeMsRaw.coerceAtLeast(0L)
        val flash = when (phase) {
            V129PresentationPhase.HOLED -> (1f - age / 620f).coerceIn(0f, 1f)
            V129PresentationPhase.LIP_OUT -> (1f - age / 430f).coerceIn(0f, .72f)
            else -> 0f
        }
        val cupFocus = when (phase) {
            V129PresentationPhase.CUP_APPROACH -> .72f
            V129PresentationPhase.HOLED -> (1f - age / 1800f).coerceIn(.22f, 1f)
            V129PresentationPhase.LIP_OUT -> (1f - age / 1350f).coerceIn(.16f, .78f)
            else -> 0f
        }
        val moving = phase == V129PresentationPhase.ROLL || phase == V129PresentationPhase.CUP_APPROACH
        val activeResult = result != null && age < 2800L

        return V129PresentationPlan(
            phase = phase,
            safe = safe,
            showHolePlate = phase != V129PresentationPhase.CUP_APPROACH || safe.compact.not(),
            showMiniMap = !safe.compact && phase != V129PresentationPhase.CUP_APPROACH,
            showConditions = phase == V129PresentationPhase.ADDRESS,
            showShotMetrics = hasShot && phase != V129PresentationPhase.CUP_APPROACH,
            showGreenRead = phase == V129PresentationPhase.ADDRESS || phase == V129PresentationPhase.ROLL,
            showCenterStatus = phase == V129PresentationPhase.ADDRESS || moving,
            showResultCard = result != null,
            chromeAlpha = when (phase) {
                V129PresentationPhase.ADDRESS -> 205
                V129PresentationPhase.ROLL -> 158
                V129PresentationPhase.CUP_APPROACH -> 112
                else -> 212
            },
            refreshMs = when {
                moving -> 16L
                activeResult -> 32L
                else -> 120L
            },
            resultFlash = flash,
            cupFocus = cupFocus
        )
    }
}

object V129ScreenGolfPresentationFactory {
    fun create(context: Context, engine: GameEngine): FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        // V128 remains the tested GLES course renderer and keeps V124 as its no-GLES fallback.
        addView(V128ScreenGolfWorldFactory.create(context, engine), LayoutParams(-1, -1))
        addView(V129CourseGradeView(context, engine), LayoutParams(-1, -1))
        addView(V129CommercialHudView(context, engine), LayoutParams(-1, -1))
    }
}

private abstract class V129EngineView(
    context: Context,
    protected val engine: GameEngine
) : View(context) {
    protected var seenResult: SimResult? = null
    protected var resultAtMs: Long = 0L

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    protected fun snapshotPlan(): V129PresentationPlan {
        val result = engine.lastResult
        if (result !== seenResult) {
            seenResult = result
            resultAtMs = if (result == null) 0L else SystemClock.uptimeMillis()
        }
        val settings = engine.settings
        val state = engine.state
        val running = state?.running == true || TvInstantRollRuntime.isAnimating()
        val display = TvInstantRollRuntime.displayPosition(state)
        val start = V26BallStartRuntime.current(settings)
        val bx = display?.first ?: state?.x ?: start.first
        val by = display?.second ?: state?.y ?: start.second
        val target = settings.holeDistanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0
        val progress = (by / target).takeIf { it.isFinite() } ?: 0.0
        val toCup = hypot(bx, target - by).takeIf { it.isFinite() }
        val age = if (resultAtMs == 0L) Long.MAX_VALUE else SystemClock.uptimeMillis() - resultAtMs
        return V129PresentationPlanner.plan(
            width = width,
            height = height,
            running = running,
            progressRaw = progress,
            distanceToCupRaw = toCup,
            result = result,
            resultAgeMsRaw = age,
            hasShot = engine.currentShot != null
        )
    }
}

/** Restrained broadcast grade: atmosphere, depth and cup/result focus only. */
private class V129CourseGradeView(context: Context, engine: GameEngine) : V129EngineView(context, engine) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (width <= 0 || height <= 0) return
        val plan = snapshotPlan()
        val w = width.toFloat()
        val h = height.toFloat()

        // Cooler upper sky, warmer horizon, then a subtle lower-field density grade.
        p.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.argb(48, 34, 72, 103),
                Color.argb(10, 238, 224, 184),
                Color.TRANSPARENT,
                Color.argb(24, 0, 20, 12)
            ),
            floatArrayOf(0f, .31f, .56f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // Atmospheric light source kept away from the central aiming corridor.
        p.shader = RadialGradient(
            w * .78f, h * .16f, max(w, h) * .24f,
            intArrayOf(Color.argb(40, 255, 245, 207), Color.argb(7, 255, 244, 218), Color.TRANSPARENT),
            floatArrayOf(0f, .42f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(w * .78f, h * .16f, max(w, h) * .24f, p)
        p.shader = null

        // Edge-only vignette. It frames the playfield without darkening the ball-to-cup lane.
        edgeGradient(c, 0f, 0f, w * .12f, 0f, Color.argb(92, 0, 8, 6), Color.TRANSPARENT, 0f, 0f, w * .14f, h)
        edgeGradient(c, w, 0f, w * .88f, 0f, Color.argb(78, 0, 8, 6), Color.TRANSPARENT, w * .86f, 0f, w, h)
        edgeGradient(c, 0f, h, 0f, h * .84f, Color.argb(84, 0, 9, 5), Color.TRANSPARENT, 0f, h * .82f, w, h)

        if (plan.cupFocus > 0f) {
            val alpha = (76f * plan.cupFocus).toInt().coerceIn(0, 76)
            val cx = w * .50f
            val cy = h * .45f
            p.shader = RadialGradient(
                cx, cy, h * .31f,
                intArrayOf(Color.TRANSPARENT, Color.argb(alpha / 3, 255, 247, 215), Color.argb(alpha, 0, 9, 5)),
                floatArrayOf(0f, .44f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, h * .31f, p)
            p.shader = null
        }

        if (plan.resultFlash > 0f) {
            val holed = plan.phase == V129PresentationPhase.HOLED
            p.color = if (holed) {
                Color.argb((128f * plan.resultFlash).toInt(), 255, 255, 247)
            } else {
                Color.argb((74f * plan.resultFlash).toInt(), 255, 187, 66)
            }
            c.drawRect(0f, 0f, w, h, p)
        }

        postInvalidateDelayed(plan.refreshMs)
    }

    private fun edgeGradient(
        c: Canvas,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        start: Int,
        end: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        p.shader = LinearGradient(x0, y0, x1, y1, start, end, Shader.TileMode.CLAMP)
        c.drawRect(left, top, right, bottom, p)
        p.shader = null
    }
}

private class V129CommercialHudView(context: Context, engine: GameEngine) : V129EngineView(context, engine) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (width <= 0 || height <= 0) return
        val plan = snapshotPlan()
        if (plan.showHolePlate) drawHolePlate(c, plan)
        if (plan.showMiniMap) drawMiniMap(c, plan)
        if (plan.showConditions) drawConditions(c, plan)
        if (plan.showShotMetrics) drawShotMetrics(c, plan)
        if (plan.showGreenRead) drawGreenRead(c, plan)
        if (plan.showCenterStatus) drawCenterStatus(c, plan)
        if (plan.phase == V129PresentationPhase.CUP_APPROACH) drawCupApproach(c)
        if (plan.showResultCard) drawResult(c, plan)
        drawWatermark(c, plan)
        postInvalidateDelayed(plan.refreshMs)
    }

    private fun drawHolePlate(c: Canvas, plan: V129PresentationPlan) {
        val w = width.toFloat(); val h = height.toFloat(); val s = plan.safe
        val boxW = if (s.compact) w * .30f else w * .218f
        val boxH = h * .086f
        panel(c, s.left, s.top, s.left + boxW, s.top + boxH, plan.chromeAlpha)
        p.color = Color.rgb(233, 173, 35)
        c.drawRect(s.left, s.top, s.left + w * .0042f, s.top + boxH, p)

        text(c, "PRACTICE  ·  H01", s.left + boxW * .075f, s.top + boxH * .37f, w * .0088f, Color.WHITE, true)
        text(c, "PAR 2   PUTTING", s.left + boxW * .075f, s.top + boxH * .72f, w * .0056f, Color.rgb(196, 204, 197), false)
        p.textAlign = Paint.Align.RIGHT
        text(c, "${fmt(targetM(), 1)}m", s.left + boxW * .94f, s.top + boxH * .57f, w * .0108f, Color.WHITE, true)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawMiniMap(c: Canvas, plan: V129PresentationPlan) {
        val w = width.toFloat(); val h = height.toFloat(); val s = plan.safe
        val pw = w * .102f; val ph = h * .245f
        val left = s.right - pw; val top = s.top
        panel(c, left, top, s.right, top + ph, plan.chromeAlpha - 10)
        text(c, "GREEN", left + pw * .11f, top + ph * .105f, w * .0054f, Color.rgb(220, 226, 220), true)

        val mapL = left + pw * .23f; val mapR = left + pw * .79f
        val mapT = top + ph * .18f; val mapB = top + ph * .89f
        val cx = (mapL + mapR) * .5f; val mw = mapR - mapL; val mh = mapB - mapT
        path.reset()
        path.moveTo(cx - mw * .34f, mapB)
        path.cubicTo(cx - mw * .52f, mapT + mh * .70f, cx - mw * .48f, mapT + mh * .28f, cx - mw * .19f, mapT)
        path.cubicTo(cx + mw * .21f, mapT - mh * .01f, cx + mw * .51f, mapT + mh * .29f, cx + mw * .34f, mapB)
        path.close()
        p.shader = LinearGradient(mapL, mapT, mapR, mapB, Color.rgb(91, 165, 66), Color.rgb(43, 111, 41), Shader.TileMode.CLAMP)
        c.drawPath(path, p); p.shader = null

        val pos = displayPosition()
        val progress = (pos.second / targetM()).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        val lateral = pos.first.takeIf { it.isFinite() }?.coerceIn(-2.2, 2.2)?.div(2.2) ?: 0.0
        val bx = cx + (lateral * mw * .37).toFloat(); val by = mapB - (progress * mh).toFloat()
        p.color = Color.rgb(241, 66, 53); c.drawCircle(cx, mapT + mh * .02f, max(3.5f, w * .0023f), p)
        p.color = Color.WHITE; c.drawCircle(bx, by, max(3.8f, w * .0025f), p)
    }

    private fun drawConditions(c: Canvas, plan: V129PresentationPlan) {
        val w = width.toFloat(); val h = height.toFloat(); val s = plan.safe
        val pw = if (s.compact) w * .27f else w * .102f
        val rowH = h * .044f; val gap = h * .006f
        val left = s.right - pw; val top = if (s.compact) s.top + h * .105f else s.top + h * .266f
        val settings = engine.settings
        val rows = listOf(
            "GREEN" to "${fmt(stimp(), 1)} STIMP",
            "SLOPE" to slopeLabel(settings.sideSlopePct, settings.longSlopePct),
            "TARGET" to "${fmt(targetM(), 1)} m"
        )
        rows.forEachIndexed { i, row ->
            val y = top + i * (rowH + gap)
            panel(c, left, y, s.right, y + rowH, plan.chromeAlpha - 18)
            text(c, row.first, left + pw * .09f, y + rowH * .37f, w * .0046f, Color.rgb(157, 168, 160), false)
            text(c, row.second, left + pw * .09f, y + rowH * .76f, w * .0057f, Color.WHITE, true)
        }
    }

    private fun drawShotMetrics(c: Canvas, plan: V129PresentationPlan) {
        val m = engine.currentShot ?: return
        val w = width.toFloat(); val h = height.toFloat(); val s = plan.safe
        val pw = if (s.compact) w * .62f else w * .39f; val ph = h * .059f
        val left = s.left; val top = s.bottom - ph
        panel(c, left, top, left + pw, s.bottom, plan.chromeAlpha)
        val items = listOf(
            "BALL" to "${fmt(m.ballSpeedMps.safe(0.0, 8.0), 2)} m/s",
            "ANGLE" to "${signed(m.launchAngleDeg.safe(-45.0, 45.0), 1)}°",
            "HEAD" to (m.headSpeedMps?.takeIf { it.isFinite() }?.let { "${fmt(it.coerceIn(0.0, 12.0), 2)} m/s" } ?: "--"),
            "FACE" to (m.faceAngleDeg?.takeIf { it.isFinite() }?.let { "${signed(it.coerceIn(-45.0, 45.0), 1)}°" } ?: "--")
        )
        val cell = pw / items.size
        items.forEachIndexed { i, item ->
            val x = left + cell * i
            if (i > 0) {
                p.color = Color.argb(58, 220, 228, 221)
                c.drawRect(x, top + ph * .20f, x + 1f, s.bottom - ph * .20f, p)
            }
            text(c, item.first, x + cell * .10f, top + ph * .39f, w * .0044f, Color.rgb(154, 165, 156), false)
            text(c, item.second, x + cell * .10f, top + ph * .77f, w * .0058f, Color.WHITE, true)
        }
    }

    private fun drawGreenRead(c: Canvas, plan: V129PresentationPlan) {
        val w = width.toFloat(); val h = height.toFloat(); val s = plan.safe
        val side = engine.settings.sideSlopePct.safe(-12.0, 12.0)
        val long = engine.settings.longSlopePct.safe(-12.0, 12.0)
        val pw = if (s.compact) w * .30f else w * .165f; val ph = h * .054f
        val left = s.left; val top = if (engine.currentShot == null) s.bottom - ph else s.bottom - ph - h * .071f
        panel(c, left, top, left + pw, top + ph, plan.chromeAlpha - 20)
        text(c, "GREEN READ", left + pw * .07f, top + ph * .35f, w * .0044f, Color.rgb(154, 166, 157), false)
        val dir = when {
            abs(side) < .15 -> "STRAIGHT"
            side > 0 -> "BREAK  →"
            else -> "←  BREAK"
        }
        val amount = abs(side)
        text(c, "$dir  ${fmt(amount, 1)}%   ${if (long >= 0) "DOWN" else "UP"} ${fmt(abs(long), 1)}%", left + pw * .07f, top + ph * .76f, w * .0056f, Color.WHITE, true)
    }

    private fun drawCenterStatus(c: Canvas, plan: V129PresentationPlan) {
        val w = width.toFloat(); val h = height.toFloat()
        when (plan.phase) {
            V129PresentationPhase.ADDRESS -> {
                val y = h * .80f
                p.style = Paint.Style.STROKE; p.strokeWidth = max(2f, h * .0022f); p.color = Color.argb(205, 83, 214, 231)
                c.drawCircle(w * .5f, y, h * .030f, p); p.style = Paint.Style.FILL
                p.textAlign = Paint.Align.CENTER
                text(c, "READY", w * .5f, y + h * .006f, w * .0078f, Color.WHITE, true)
                p.textAlign = Paint.Align.LEFT
            }
            V129PresentationPhase.ROLL, V129PresentationPhase.CUP_APPROACH -> {
                val pos = displayPosition(); val state = engine.state
                val remain = hypot(pos.first, targetM() - pos.second).safe(0.0, 99.9)
                val speed = if (state == null) 0.0 else hypot(state.vx, state.vy).safe(0.0, 9.9)
                val pw = w * .145f; val ph = h * .056f; val left = (w - pw) * .5f; val top = h * .026f
                panel(c, left, top, left + pw, top + ph, if (plan.phase == V129PresentationPhase.CUP_APPROACH) 128 else 174)
                p.textAlign = Paint.Align.CENTER
                text(c, "${fmt(remain, 2)} m", w * .5f, top + ph * .47f, w * .0094f, Color.rgb(124, 238, 136), true)
                text(c, "BALL ${fmt(speed, 2)} m/s", w * .5f, top + ph * .78f, w * .0048f, Color.rgb(215, 223, 216), false)
                p.textAlign = Paint.Align.LEFT
            }
            else -> Unit
        }
    }

    private fun drawCupApproach(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val cx = w * .5f; val cy = h * .46f
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1.5f, h * .0018f); p.color = Color.argb(118, 244, 247, 236)
        c.drawCircle(cx, cy, h * .032f, p)
        c.drawLine(cx - h * .052f, cy, cx - h * .036f, cy, p)
        c.drawLine(cx + h * .036f, cy, cx + h * .052f, cy, p)
        c.drawLine(cx, cy - h * .052f, cx, cy - h * .036f, p)
        p.style = Paint.Style.FILL
    }

    private fun drawResult(c: Canvas, plan: V129PresentationPlan) {
        val r = engine.lastResult ?: return
        val w = width.toFloat(); val h = height.toFloat(); val s = plan.safe
        val pw = if (s.compact) w * .50f else w * .255f; val ph = h * .116f
        val left = (w - pw) * .5f; val top = h * .70f
        panel(c, left, top, left + pw, top + ph, 222)
        val title = when (plan.phase) {
            V129PresentationPhase.HOLED -> "HOLED"
            V129PresentationPhase.LIP_OUT -> "LIP OUT"
            else -> "STOP"
        }
        val accent = when (plan.phase) {
            V129PresentationPhase.HOLED -> Color.rgb(98, 233, 113)
            V129PresentationPhase.LIP_OUT -> Color.rgb(246, 179, 54)
            else -> Color.rgb(235, 239, 233)
        }
        p.textAlign = Paint.Align.CENTER
        text(c, title, w * .5f, top + ph * .37f, w * .0128f, accent, true)
        val leaveCm = (r.distanceToCupM.safe(0.0, 99.0) * 100.0)
        val sub = if (r.holed) "CUP IN" else "LEAVE ${fmt(leaveCm, 0)} cm   ·   ${sideResult(r.finishX)}"
        text(c, sub, w * .5f, top + ph * .72f, w * .0058f, Color.rgb(214, 221, 214), false)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawWatermark(c: Canvas, plan: V129PresentationPlan) {
        val w = width.toFloat(); val h = height.toFloat(); val s = plan.safe
        p.textAlign = Paint.Align.RIGHT
        text(c, "PUTTVISION", s.right, s.bottom, w * .0046f, Color.argb(if (plan.phase == V129PresentationPhase.CUP_APPROACH) 76 else 116, 235, 240, 234), true)
        p.textAlign = Paint.Align.LEFT
    }

    private fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float, alphaRaw: Int) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(alphaRaw.coerceIn(0, 235), 15, 20, 18)
        c.drawRoundRect(l, t, r, b, max(5f, height * .007f), max(5f, height * .007f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, width * .00045f)
        p.color = Color.argb((alphaRaw * .19f).toInt().coerceIn(0, 58), 225, 232, 225)
        c.drawRoundRect(l, t, r, b, max(5f, height * .007f), max(5f, height * .007f), p)
        p.style = Paint.Style.FILL
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, sizeRaw: Float, color: Int, bold: Boolean) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = color
        p.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        p.textSize = max(9f, sizeRaw)
        c.drawText(value, x, y, p)
    }

    private fun displayPosition(): Pair<Double, Double> {
        val settings = engine.settings; val state = engine.state; val display = TvInstantRollRuntime.displayPosition(state)
        val start = V26BallStartRuntime.current(settings)
        return (display?.first ?: state?.x ?: start.first) to (display?.second ?: state?.y ?: start.second)
    }

    private fun targetM(): Double = engine.settings.holeDistanceM.safe(.5, 30.0)
    private fun stimp(): Double = engine.settings.stimpMeters.safe(1.5, 5.0)
    private fun slopeLabel(sideRaw: Double, longRaw: Double): String {
        val side = sideRaw.safe(-12.0, 12.0); val long = longRaw.safe(-12.0, 12.0)
        if (abs(side) < .1 && abs(long) < .1) return "FLAT"
        val s = if (abs(side) < .1) "" else if (side > 0) "R${fmt(abs(side), 1)}" else "L${fmt(abs(side), 1)}"
        val l = if (abs(long) < .1) "" else if (long > 0) " D${fmt(abs(long), 1)}" else " U${fmt(abs(long), 1)}"
        return (s + l).trim().ifEmpty { "FLAT" }
    }

    private fun sideResult(xRaw: Double): String {
        val x = xRaw.takeIf { it.isFinite() } ?: 0.0
        return when {
            abs(x) < .03 -> "CENTER"
            x > 0 -> "RIGHT"
            else -> "LEFT"
        }
    }

    private fun fmt(v: Double, digits: Int): String = when (digits.coerceIn(0, 3)) {
        0 -> String.format(java.util.Locale.US, "%.0f", v)
        1 -> String.format(java.util.Locale.US, "%.1f", v)
        2 -> String.format(java.util.Locale.US, "%.2f", v)
        else -> String.format(java.util.Locale.US, "%.3f", v)
    }

    private fun signed(v: Double, digits: Int): String = (if (v >= 0.0) "+" else "") + fmt(v, digits)
}

private fun Double.safe(minValue: Double, maxValue: Double): Double =
    takeIf { it.isFinite() }?.coerceIn(minValue, maxValue) ?: minValue
