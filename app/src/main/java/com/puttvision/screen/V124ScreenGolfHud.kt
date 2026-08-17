package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/**
 * Original PuttVision HUD using the familiar information hierarchy of Korean screen-golf systems:
 * player/hole card, right-side course map and conditions, central READY cue, and a bottom shot strip.
 * No third-party marks, character art, fonts or proprietary assets are included.
 */
data class V124HudPlan(
    val showReady: Boolean,
    val showLiveDistance: Boolean,
    val showShotStrip: Boolean,
    val showResult: Boolean,
    val miniMapAlpha: Int,
    val panelAlpha: Int,
    val refreshMs: Long
)

object V124HudPlanner {
    fun plan(running: Boolean, hasShot: Boolean, hasResult: Boolean, resultAgeMs: Long): V124HudPlan {
        val age = resultAgeMs.coerceAtLeast(0L)
        return V124HudPlan(
            showReady = !running && !hasResult,
            showLiveDistance = running,
            showShotStrip = hasShot,
            showResult = hasResult,
            miniMapAlpha = if (running) 208 else 226,
            panelAlpha = if (running) 198 else 222,
            refreshMs = when {
                running -> 20L
                hasResult && age < 2500L -> 33L
                else -> 140L
            }
        )
    }
}

class V124ScreenGolfHudView(
    context: Context,
    private val engine: GameEngine
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var seenResult: SimResult? = null
    private var resultAtMs = 0L
    private var qualityLabel = "--"
    private var qualitySampleAtMs = 0L

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (width <= 0 || height <= 0) return

        val state = engine.state
        val result = engine.lastResult
        if (result !== seenResult) {
            seenResult = result
            resultAtMs = if (result == null) 0L else SystemClock.uptimeMillis()
        }
        val running = state?.running == true || TvInstantRollRuntime.isAnimating()
        val age = if (resultAtMs == 0L) Long.MAX_VALUE else SystemClock.uptimeMillis() - resultAtMs
        val plan = V124HudPlanner.plan(running, engine.currentShot != null, result != null, age)

        drawTopLeft(c, plan)
        drawMiniMap(c, plan)
        drawRightConditions(c, plan)
        if (plan.showReady) drawReady(c)
        if (plan.showLiveDistance) drawLiveDistance(c)
        if (plan.showShotStrip) drawShotStrip(c)
        if (plan.showResult) drawResult(c, result, age)
        drawBottomBrand(c)

        postInvalidateDelayed(plan.refreshMs)
    }

    private fun drawTopLeft(c: Canvas, plan: V124HudPlan) {
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * .020f
        val top = h * .025f
        val pw = w * .255f
        val ph = h * .118f

        p.style = Paint.Style.FILL
        p.color = Color.argb(plan.panelAlpha, 43, 46, 43)
        c.drawRoundRect(left, top, left + pw, top + ph, h * .010f, h * .010f, p)

        p.color = Color.rgb(245, 185, 48)
        c.drawRoundRect(left, top, left + w * .007f, top + ph, h * .010f, h * .010f, p)

        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.color = Color.WHITE
        p.textSize = max(17f, w * .012f)
        c.drawText("PRACTICE", left + pw * .070f, top + ph * .32f, p)

        p.textSize = max(12f, w * .008f)
        p.color = Color.rgb(238, 240, 236)
        val target = V125ScreenGolfHudSafety.targetM(engine.settings.holeDistanceM)
        c.drawText("HOLE 01   PAR 2", left + pw * .070f, top + ph * .60f, p)

        p.typeface = Typeface.DEFAULT
        p.textSize = max(10f, w * .0064f)
        p.color = Color.rgb(202, 208, 198)
        c.drawText("TARGET ${fmt(target, 1)}m   ·   PUTT", left + pw * .070f, top + ph * .84f, p)
    }

    private fun drawMiniMap(c: Canvas, plan: V124HudPlan) {
        val w = width.toFloat()
        val h = height.toFloat()
        val panelW = w * .155f
        val panelH = h * .365f
        val left = w - panelW - w * .018f
        val top = h * .024f

        p.style = Paint.Style.FILL
        p.color = Color.argb(plan.miniMapAlpha, 39, 43, 40)
        c.drawRoundRect(left, top, left + panelW, top + panelH, h * .012f, h * .012f, p)

        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(11f, w * .0068f)
        p.color = Color.WHITE
        c.drawText("GREEN MAP", left + panelW * .10f, top + panelH * .09f, p)

        val mapLeft = left + panelW * .18f
        val mapTop = top + panelH * .15f
        val mapRight = left + panelW * .82f
        val mapBottom = top + panelH * .88f
        val cx = (mapLeft + mapRight) * .5f
        val mapW = mapRight - mapLeft
        val mapH = mapBottom - mapTop

        path.reset()
        path.moveTo(cx - mapW * .30f, mapBottom)
        path.cubicTo(cx - mapW * .48f, mapTop + mapH * .70f, cx - mapW * .40f, mapTop + mapH * .30f, cx - mapW * .18f, mapTop)
        path.cubicTo(cx + mapW * .16f, mapTop - mapH * .03f, cx + mapW * .43f, mapTop + mapH * .31f, cx + mapW * .34f, mapBottom)
        path.close()

        p.shader = LinearGradient(
            mapLeft, mapTop, mapRight, mapBottom,
            Color.rgb(81, 166, 63), Color.rgb(46, 126, 45), Shader.TileMode.CLAMP
        )
        c.drawPath(path, p)
        p.shader = null

        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.5f, w * .0011f)
        p.color = Color.argb(180, 213, 233, 204)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL

        val settings = engine.settings
        val target = V125ScreenGolfHudSafety.targetM(settings.holeDistanceM)
        val state = engine.state
        val display = TvInstantRollRuntime.displayPosition(state)
        val bx = display?.first ?: state?.x ?: V26BallStartRuntime.current(settings).first
        val by = display?.second ?: state?.y ?: V26BallStartRuntime.current(settings).second
        val progress = V125ScreenGolfHudSafety.normalizedProgress(by, target)
        val lateral = V125ScreenGolfHudSafety.normalizedLateral(bx)

        val ballX = cx + (lateral * mapW * .30).toFloat()
        val ballY = mapBottom - (progress * mapH).toFloat()
        p.color = Color.WHITE
        c.drawCircle(ballX, ballY, max(4.5f, w * .0032f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.4f, w * .001f)
        p.color = Color.rgb(40, 40, 38)
        c.drawCircle(ballX, ballY, max(4.5f, w * .0032f), p)
        p.style = Paint.Style.FILL

        p.color = Color.rgb(245, 70, 58)
        c.drawCircle(cx, mapTop + mapH * .035f, max(5f, w * .0035f), p)

        val read = GreenReadRuntime.peek(settings)
        if (read?.solverReliable == true && read.predictedTrail.size >= 2) {
            val hide = V20GreenReadTrainingRuntime.shouldHideSolution(engine.gameModes.status.mode, settings) && !engine.readFeedback.revealed
            if (!hide) {
                val samples = V125ScreenGolfHudSafety.trailSamples(read.predictedTrail)
                if (samples.size >= 2) {
                    path.reset()
                    samples.forEachIndexed { index, pt ->
                        val py = V125ScreenGolfHudSafety.normalizedProgress(pt.second, target)
                        val px = V125ScreenGolfHudSafety.normalizedLateral(pt.first)
                        val sx = cx + (px * mapW * .30).toFloat()
                        val sy = mapBottom - (py * mapH).toFloat()
                        if (index == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                    }
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = max(1.8f, w * .0013f)
                    p.color = Color.rgb(255, 225, 66)
                    c.drawPath(path, p)
                    p.style = Paint.Style.FILL
                }
            }
        }
    }

    private fun drawRightConditions(c: Canvas, plan: V124HudPlan) {
        val w = width.toFloat()
        val h = height.toFloat()
        val panelW = w * .155f
        val left = w - panelW - w * .018f
        val top = h * .405f
        val rowH = h * .068f
        val settings = engine.settings

        val rows = listOf(
            "거리" to "${fmt(V125ScreenGolfHudSafety.targetM(settings.holeDistanceM), 1)} m",
            "경사" to slopeLabel(settings.sideSlopePct, settings.longSlopePct),
            "그린" to "STIMP ${fmt(V125ScreenGolfHudSafety.stimpM(settings.stimpMeters), 1)}",
            "품질" to currentQualityLabel()
        )

        rows.forEachIndexed { index, pair ->
            val y = top + index * (rowH + h * .008f)
            p.color = Color.argb(plan.panelAlpha, 46, 49, 46)
            c.drawRoundRect(left, y, left + panelW, y + rowH, h * .008f, h * .008f, p)
            p.typeface = Typeface.DEFAULT
            p.textSize = max(9f, w * .0057f)
            p.color = Color.rgb(190, 198, 188)
            c.drawText(pair.first, left + panelW * .09f, y + rowH * .36f, p)
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = max(11f, w * .0068f)
            p.color = Color.WHITE
            c.drawText(pair.second, left + panelW * .09f, y + rowH * .72f, p)
        }
    }

    private fun currentQualityLabel(): String {
        val now = SystemClock.uptimeMillis()
        if (V125ScreenGolfHudSafety.qualityCacheRefreshDue(now, qualitySampleAtMs)) {
            qualityLabel = runCatching {
                V24TvQualityRuntime.snapshot(context.applicationContext).tier.label
            }.getOrDefault("--")
            qualitySampleAtMs = now
        }
        return qualityLabel
    }

    private fun drawReady(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cw = w * .090f
        val ch = h * .044f
        val left = (w - cw) * .5f
        val top = h * .765f
        p.color = Color.rgb(33, 179, 223)
        c.drawRoundRect(left, top, left + cw, top + ch, ch * .20f, ch * .20f, p)
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
        p.textSize = max(13f, w * .008f)
        p.color = Color.WHITE
        c.drawText("READY", left + cw * .5f, top + ch * .69f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawLiveDistance(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val settings = engine.settings
        val state = engine.state ?: return
        val display = TvInstantRollRuntime.displayPosition(state)
        val bx = display?.first ?: state.x
        val by = display?.second ?: state.y
        val remain = V125ScreenGolfHudSafety.liveRemainingM(bx, by, settings.holeDistanceM)
        val speed = V125ScreenGolfHudSafety.ballSpeedMps(state.vx, state.vy)

        val left = w * .655f
        val top = h * .215f
        val pw = w * .160f
        val ph = h * .102f
        p.color = Color.argb(205, 42, 47, 43)
        c.drawRoundRect(left, top, left + pw, top + ph, h * .010f, h * .010f, p)

        p.typeface = Typeface.DEFAULT
        p.textSize = max(9f, w * .0058f)
        p.color = Color.rgb(198, 206, 196)
        c.drawText("남은거리", left + pw * .08f, top + ph * .30f, p)
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(17f, w * .011f)
        p.color = Color.rgb(91, 229, 109)
        c.drawText("${fmt(remain, 2)} m", left + pw * .08f, top + ph * .63f, p)
        p.textSize = max(10f, w * .0062f)
        p.color = Color.WHITE
        c.drawText("BALL ${fmt(speed, 2)} m/s", left + pw * .08f, top + ph * .86f, p)
    }

    private fun drawShotStrip(c: Canvas) {
        val metrics = engine.currentShot ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * .018f
        val right = w * .805f
        val top = h * .895f
        val bottom = h * .975f
        p.color = Color.argb(224, 35, 39, 36)
        c.drawRoundRect(left, top, right, bottom, h * .010f, h * .010f, p)

        val cells = listOf(
            "볼스피드" to metrics.ballSpeedMps.takeIf { it.isFinite() }?.let { "${fmt(it.coerceIn(0.0, 8.0), 2)} m/s" }.orEmpty().ifBlank { "--" },
            "출발각" to metrics.launchAngleDeg.takeIf { it.isFinite() }?.let { "${signed(it.coerceIn(-45.0, 45.0), 1)}°" }.orEmpty().ifBlank { "--" },
            "헤드" to metrics.headSpeedMps?.takeIf { it.isFinite() }?.let { "${fmt(it.coerceIn(0.0, 12.0), 2)} m/s" }.orEmpty().ifBlank { "--" },
            "FACE" to metrics.faceAngleDeg?.takeIf { it.isFinite() }?.let { "${signed(it.coerceIn(-45.0, 45.0), 1)}°" }.orEmpty().ifBlank { "--" },
            "PATH" to metrics.pathAngleDeg?.takeIf { it.isFinite() }?.let { "${signed(it.coerceIn(-45.0, 45.0), 1)}°" }.orEmpty().ifBlank { "--" },
            "IMPACT" to metrics.impactOffsetMm?.takeIf { it.isFinite() }?.let { "${signed(it.coerceIn(-50.0, 50.0), 1)} mm" }.orEmpty().ifBlank { "--" }
        )
        val cellW = (right - left) / cells.size
        cells.forEachIndexed { i, pair ->
            val x = left + cellW * i
            if (i > 0) {
                p.color = Color.argb(80, 255, 255, 255)
                c.drawRect(x, top + (bottom - top) * .18f, x + 1f, bottom - (bottom - top) * .18f, p)
            }
            p.typeface = Typeface.DEFAULT
            p.textSize = max(8f, w * .0049f)
            p.color = Color.rgb(182, 190, 180)
            c.drawText(pair.first, x + cellW * .10f, top + (bottom - top) * .34f, p)
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = max(11f, w * .0066f)
            p.color = Color.WHITE
            c.drawText(pair.second, x + cellW * .10f, top + (bottom - top) * .70f, p)
        }
    }

    private fun drawResult(c: Canvas, result: SimResult?, ageMs: Long) {
        result ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val live = ageMs.coerceAtLeast(0L) < 3000L
        val distanceCm = V125ScreenGolfHudSafety.resultDistanceCm(result.distanceToCupM)
        val distanceM = distanceCm / 100.0
        val headline = when {
            result.holed -> "HOLE IN"
            result.lipOut -> "LIP OUT"
            distanceM <= .15 -> "GREAT"
            distanceM <= .45 -> "GOOD"
            else -> "FINISH"
        }
        val accent = when {
            result.holed -> Color.rgb(255, 197, 45)
            result.lipOut -> Color.rgb(242, 112, 71)
            else -> Color.rgb(77, 202, 107)
        }
        val cw = w * .280f
        val ch = h * .126f
        val left = (w - cw) * .5f
        val top = h * .695f

        p.color = Color.argb(if (live) 228 else 205, 40, 43, 40)
        c.drawRoundRect(left, top, left + cw, top + ch, h * .012f, h * .012f, p)
        p.color = accent
        c.drawRect(left, top, left + cw, top + max(4f, h * .006f), p)

        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(18f, w * .013f)
        p.color = Color.WHITE
        c.drawText(headline, left + cw * .5f, top + ch * .37f, p)
        p.textSize = max(12f, w * .0076f)
        p.color = accent
        c.drawText("남은거리 ${fmt(distanceCm, 1)} cm", left + cw * .5f, top + ch * .64f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = max(9f, w * .0055f)
        p.color = Color.rgb(210, 217, 207)
        c.drawText(if (result.holed) "정확한 페이스와 스피드" else "다음 퍼트 라인을 확인하세요", left + cw * .5f, top + ch * .84f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawBottomBrand(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(9f, w * .0056f)
        p.color = Color.argb(170, 255, 255, 255)
        c.drawText("PUTTVISION SCREEN", w * .020f, h * .865f, p)
    }

    private fun slopeLabel(side: Double, long: Double): String {
        val s = V125ScreenGolfHudSafety.slopePct(side)
        val l = V125ScreenGolfHudSafety.slopePct(long)
        if (abs(s) < .05 && abs(l) < .05) return "FLAT"
        val sideText = when {
            s > .05 -> "R ${fmt(abs(s), 1)}%"
            s < -.05 -> "L ${fmt(abs(s), 1)}%"
            else -> ""
        }
        val longText = when {
            l > .05 -> "DOWN ${fmt(abs(l), 1)}%"
            l < -.05 -> "UP ${fmt(abs(l), 1)}%"
            else -> ""
        }
        return listOf(sideText, longText).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun fmt(v: Double, n: Int): String = "% .${n}f".format(v).trim()
    private fun signed(v: Double, n: Int): String = if (v >= 0.0) "+${fmt(v, n)}" else fmt(v, n)
}
