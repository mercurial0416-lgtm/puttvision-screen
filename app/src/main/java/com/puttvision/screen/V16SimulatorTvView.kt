package com.puttvision.screen

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.*

/**
 * V16 external-display renderer.
 *
 * Visual goal: a bright, immersive putting simulator first and a measurement dashboard second.
 * It intentionally avoids third-party logos/assets while keeping the sparse simulator composition:
 * full-screen green, low eye-level camera, compact top-center distance/pace HUD, red aim line,
 * minimal waiting/result chrome, and telemetry that only appears when it is useful.
 */
class V16SimulatorTvView(
    context: Context,
    private val engine: GameEngine
) : View(context) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var smoothBallX = Float.NaN
    private var smoothBallY = Float.NaN
    private var lastGeneration = -1L

    init {
        keepScreenOn = true
        isFocusable = false
        isClickable = false
        setBackgroundColor(Color.rgb(28, 76, 130))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        canvas.translate(width * ProductSessionRuntime.tvOffsetX, height * ProductSessionRuntime.tvOffsetY)
        canvas.scale(ProductSessionRuntime.tvScaleX, ProductSessionRuntime.tvScaleY, width / 2f, height / 2f)

        drawSkyAndCourse(canvas)
        drawPuttingSurface(canvas)
        drawTopSimulatorHud(canvas)
        drawContextHud(canvas)
        drawResultHud(canvas)
        if (ProductSessionRuntime.tvCalibrationGuide) drawCalibrationGuide(canvas)

        canvas.restoreToCount(save)

        when {
            engine.state?.running == true || TvInstantRollRuntime.isAnimating() -> postInvalidateOnAnimation()
            engine.lastResult == null || ProductSessionRuntime.tvCalibrationGuide -> postInvalidateDelayed(33L)
            else -> postInvalidateDelayed(180L)
        }
    }

    private fun drawSkyAndCourse(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val horizon = h * .42f

        p.shader = LinearGradient(
            0f, 0f, 0f, horizon,
            intArrayOf(
                Color.rgb(44, 116, 196),
                Color.rgb(76, 153, 222),
                Color.rgb(153, 203, 235)
            ),
            floatArrayOf(0f, .62f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, horizon, p)
        p.shader = null

        // Soft simulator-like clouds.
        p.color = Color.argb(72, 255, 255, 255)
        val cloudSpecs = arrayOf(
            floatArrayOf(.10f, .16f, .14f),
            floatArrayOf(.38f, .10f, .12f),
            floatArrayOf(.72f, .18f, .16f),
            floatArrayOf(.90f, .08f, .09f)
        )
        cloudSpecs.forEachIndexed { i, s ->
            val cx = w * s[0]
            val cy = h * s[1]
            val rw = w * s[2]
            val rh = h * (.025f + i % 2 * .006f)
            c.drawOval(RectF(cx - rw, cy - rh, cx + rw, cy + rh), p)
            c.drawOval(RectF(cx - rw * .58f, cy - rh * 1.8f, cx + rw * .25f, cy + rh * .8f), p)
        }

        // Distant clubhouse silhouette and trees give the TV the familiar outdoor-simulator depth.
        p.color = Color.rgb(59, 105, 73)
        c.drawRect(0f, horizon - h * .025f, w, horizon + h * .045f, p)

        drawClubhouse(c, w * .34f, horizon - h * .006f, w * .18f, h * .095f)

        val treeBase = horizon + h * .018f
        for (i in 0..34) {
            val x = w * i / 34f
            val r = w * (.010f + ((i * 7) % 5) * .0021f)
            val autumn = i % 11 == 4 || i % 13 == 7
            p.color = if (autumn) Color.rgb(139, 92, 62) else Color.rgb(43, 106, 61)
            c.drawCircle(x, treeBase - r * .78f, r, p)
            c.drawCircle(x - r * .58f, treeBase - r * .5f, r * .72f, p)
            c.drawCircle(x + r * .58f, treeBase - r * .48f, r * .70f, p)
            p.color = Color.rgb(76, 66, 45)
            c.drawRect(x - r * .08f, treeBase - r * .2f, x + r * .08f, treeBase + r * .45f, p)
        }

        p.shader = LinearGradient(
            0f, horizon, 0f, h,
            Color.rgb(91, 169, 73), Color.rgb(67, 139, 58), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, horizon, w, h, p)
        p.shader = null
    }

    private fun drawClubhouse(c: Canvas, cx: Float, baseline: Float, ww: Float, hh: Float) {
        p.color = Color.rgb(225, 218, 200)
        val left = cx - ww * .5f
        val right = cx + ww * .5f
        c.drawRect(left, baseline - hh * .48f, right, baseline, p)
        p.color = Color.rgb(97, 80, 69)
        val roof = Path().apply {
            moveTo(left - ww * .07f, baseline - hh * .48f)
            lineTo(cx, baseline - hh)
            lineTo(right + ww * .07f, baseline - hh * .48f)
            close()
        }
        c.drawPath(roof, p)
        p.color = Color.argb(140, 72, 91, 100)
        repeat(4) { i ->
            val x = left + ww * (.14f + i * .235f)
            c.drawRect(x, baseline - hh * .38f, x + ww * .11f, baseline - hh * .10f, p)
        }
    }

    private fun drawPuttingSurface(c: Canvas) {
        val settings = engine.settings
        val w = width.toFloat()
        val h = height.toFloat()
        val horizonY = h * .405f
        val bottomY = h * 1.02f
        val centerX = w * .50f
        val maxY = max(settings.holeDistanceM * 1.24, 3.5)
        val sideRange = max(1.35, settings.holeDistanceM * .18)
        val originHeight = GreenTerrain.effectiveHeightAt(settings, 0.0, 0.0)

        fun syBase(y: Double): Float {
            val t = (y / maxY).coerceIn(0.0, 1.0).toFloat()
            return bottomY - (bottomY - horizonY) * t
        }
        fun halfWidthAt(yPix: Float): Float {
            val t = ((yPix - horizonY) / (bottomY - horizonY)).coerceIn(0f, 1f)
            return w * (.17f + .46f * t)
        }
        fun sx(x: Double, y: Double): Float {
            val yp = syBase(y)
            return centerX + (x / sideRange).toFloat() * halfWidthAt(yp)
        }
        fun sy(x: Double, y: Double): Float {
            val z = GreenTerrain.effectiveHeightAt(settings, x, y)
            return syBase(y) - ((z - originHeight) * h * 1.16).toFloat()
        }

        val green = Path().apply {
            moveTo(w * .28f, horizonY)
            cubicTo(w * .16f, h * .60f, w * .04f, h * .87f, -w * .03f, bottomY)
            lineTo(w * 1.03f, bottomY)
            cubicTo(w * .96f, h * .87f, w * .84f, h * .60f, w * .72f, horizonY)
            close()
        }

        p.shader = LinearGradient(
            0f, horizonY, 0f, bottomY,
            intArrayOf(Color.rgb(108, 188, 75), Color.rgb(88, 171, 68), Color.rgb(66, 148, 59)),
            floatArrayOf(0f, .50f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(green, p)
        p.shader = null

        val greenSave = c.save()
        c.clipPath(green)

        // Broad mowing bands instead of a dense engineering grid.
        for (row in 0 until 10) {
            val y0 = maxY * row / 10.0
            val y1 = maxY * (row + 1) / 10.0
            p.color = if (row % 2 == 0) Color.argb(17, 245, 255, 235) else Color.argb(15, 8, 65, 24)
            val band = Path().apply {
                moveTo(sx(-sideRange, y0), sy(-sideRange, y0))
                lineTo(sx(sideRange, y0), sy(sideRange, y0))
                lineTo(sx(sideRange, y1), sy(sideRange, y1))
                lineTo(sx(-sideRange, y1), sy(-sideRange, y1))
                close()
            }
            c.drawPath(band, p)
        }

        // Sparse dotted reference line across the green: visually closer to a simulator than a CAD grid.
        p.color = Color.argb(135, 250, 255, 248)
        val refY = settings.holeDistanceM * .52
        for (i in -8..8) {
            val xM = sideRange * i / 8.0
            c.drawCircle(sx(xM, refY), sy(xM, refY), max(1.7f, w * .0014f), p)
        }

        val read = if (engine.state?.running != true && engine.lastResult == null) GreenReadRuntime.peekOrSchedule(settings) else null
        drawSlopeFlow(c, settings, read, sideRange, ::sx, ::sy)
        drawGhostTrail(c, settings, ::sx, ::sy)
        drawLiveTrails(c, ::sx, ::sy)

        c.restoreToCount(greenSave)

        drawCupAndFlag(c, settings, ::sx, ::sy)
        drawAimLineAndBall(c, settings, read, ::sx, ::sy)
    }

    private fun drawSlopeFlow(
        c: Canvas,
        settings: GreenSettings,
        read: GreenRead?,
        sideRange: Double,
        sx: (Double, Double) -> Float,
        sy: (Double, Double) -> Float
    ) {
        if (read == null || engine.state?.running == true || TvInstantRollRuntime.isAnimating()) return
        val w = width.toFloat()
        val now = (SystemClock.uptimeMillis() % 20_000L) / 1000.0
        for (row in 1..6) {
            val y = settings.holeDistanceM * row / 7.0
            for (lane in -4..4) {
                val x = sideRange * lane / 4.3
                val slope = GreenTerrain.effectiveSlopeAt(settings, x, y)
                val mag = hypot(slope.sidePct, slope.longPct)
                if (mag < .18) continue
                val ux = slope.sidePct / mag
                val uy = slope.longPct / mag
                val phase = ((now * (.38 + min(1.2, mag * .25)) + row * .19 + lane * .11) % 1.0) - .5
                val travel = .08 + min(.16, mag * .028)
                val px = x + ux * travel * phase
                val py = y + uy * travel * phase
                p.color = Color.argb(120, 245, 255, 245)
                c.drawCircle(sx(px, py), sy(px, py), max(1.5f, w * .00125f), p)
            }
        }
    }

    private fun drawGhostTrail(
        c: Canvas,
        settings: GreenSettings,
        sx: (Double, Double) -> Float,
        sy: (Double, Double) -> Float
    ) {
        if (engine.gameModes.status.mode != PracticeMode.GHOST) return
        val ghost = V15GhostRuntime.referenceForCurrent(settings) ?: return
        if (ghost.trail.size < 2) return
        val path = Path().apply {
            moveTo(sx(ghost.trail.first().first, ghost.trail.first().second), sy(ghost.trail.first().first, ghost.trail.first().second))
            ghost.trail.drop(1).forEach { lineTo(sx(it.first, it.second), sy(it.first, it.second)) }
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.pathEffect = DashPathEffect(floatArrayOf(width * .010f, width * .006f), 0f)
        p.strokeWidth = max(3f, width * .0022f)
        p.color = Color.argb(145, 117, 234, 255)
        c.drawPath(path, p)
        p.pathEffect = null
        p.style = Paint.Style.FILL
    }

    private fun drawLiveTrails(
        c: Canvas,
        sx: (Double, Double) -> Float,
        sy: (Double, Double) -> Float
    ) {
        fun drawTrail(points: List<Pair<Double, Double>>, color: Int, widthPx: Float) {
            if (points.size < 2) return
            val path = Path().apply {
                moveTo(sx(points.first().first, points.first().second), sy(points.first().first, points.first().second))
                points.drop(1).forEach { lineTo(sx(it.first, it.second), sy(it.first, it.second)) }
            }
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = widthPx
            p.color = color
            c.drawPath(path, p)
            p.style = Paint.Style.FILL
        }
        drawTrail(TvInstantRollRuntime.visibleTrail(), Color.argb(112, 255, 255, 255), max(3f, width * .0019f))
        engine.state?.trail?.let {
            drawTrail(it, Color.argb(210, 255, 214, 77), max(4f, width * .0025f))
        }
    }

    private fun drawCupAndFlag(
        c: Canvas,
        settings: GreenSettings,
        sx: (Double, Double) -> Float,
        sy: (Double, Double) -> Float
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val hx = sx(0.0, settings.holeDistanceM)
        val hy = sy(0.0, settings.holeDistanceM)
        val poleH = h * .19f

        p.color = Color.argb(90, 0, 0, 0)
        c.drawOval(RectF(hx - w * .010f, hy - h * .004f, hx + w * .010f, hy + h * .004f), p)

        p.color = Color.WHITE
        c.drawRoundRect(RectF(hx - 1.6f, hy - poleH, hx + 1.6f, hy), 1.6f, 1.6f, p)

        val flag = Path().apply {
            moveTo(hx + 2f, hy - poleH)
            lineTo(hx + w * .047f, hy - poleH + h * .023f)
            lineTo(hx + 2f, hy - poleH + h * .050f)
            close()
        }
        p.color = Color.rgb(224, 64, 52)
        c.drawPath(flag, p)
    }

    private fun drawAimLineAndBall(
        c: Canvas,
        settings: GreenSettings,
        read: GreenRead?,
        sx: (Double, Double) -> Float,
        sy: (Double, Double) -> Float
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val state = engine.state
        val display = TvInstantRollRuntime.displayPosition(state) ?: if (state != null) state.x to state.y else 0.0 to 0.0
        val tx = sx(display.first, display.second)
        val ty = sy(display.first, display.second)
        val generation = TvInstantRollRuntime.generation()
        if (!smoothBallX.isFinite() || generation != lastGeneration) {
            smoothBallX = tx
            smoothBallY = ty
            lastGeneration = generation
        } else {
            val a = if (state?.running == true || TvInstantRollRuntime.isAnimating()) .75f else .91f
            smoothBallX += (tx - smoothBallX) * a
            smoothBallY += (ty - smoothBallY) * a
        }

        val preShot = state?.running != true && !TvInstantRollRuntime.isAnimating() && engine.lastResult == null
        if (preShot) {
            val aimX = if (read?.solverReliable == true) read.aimOffsetCm / 100.0 else 0.0
            val endX = sx(aimX, settings.holeDistanceM)
            val endY = sy(aimX, settings.holeDistanceM)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = max(2.8f, w * .0018f)
            p.color = Color.argb(220, 221, 52, 46)
            c.drawLine(smoothBallX, smoothBallY - h * .012f, endX, endY, p)
            p.style = Paint.Style.FILL

            // Dotted address ring around the ball.
            val ringR = max(18f, w * .016f)
            p.color = Color.argb(225, 255, 255, 255)
            for (i in 0 until 14) {
                val ang = Math.PI * 2.0 * i / 14.0
                c.drawCircle(
                    smoothBallX + cos(ang).toFloat() * ringR,
                    smoothBallY + sin(ang).toFloat() * ringR * .48f,
                    max(1.8f, w * .00145f),
                    p
                )
            }
        }

        val progress = (display.second / settings.holeDistanceM.coerceAtLeast(.5)).coerceIn(0.0, 1.0)
        val r = (max(8f, w * .0072f) * (1.0 - progress * .27)).toFloat()
        p.color = Color.argb(62, 0, 0, 0)
        c.drawOval(RectF(smoothBallX - r * 1.45f, smoothBallY + r * .62f, smoothBallX + r * 1.45f, smoothBallY + r * 1.35f), p)
        p.shader = RadialGradient(
            smoothBallX - r * .28f, smoothBallY - r * .30f, r * 1.5f,
            intArrayOf(Color.WHITE, Color.rgb(240, 244, 238), Color.rgb(188, 196, 185)),
            floatArrayOf(0f, .68f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(smoothBallX, smoothBallY, r, p)
        p.shader = null
    }

    private fun drawTopSimulatorHud(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val settings = engine.settings
        val read = if (engine.state?.running != true && engine.lastResult == null) GreenReadRuntime.peekOrSchedule(settings) else null

        val totalW = w * .235f
        val hh = h * .066f
        val left = w * .5f - totalW * .5f
        val top = h * .030f

        p.color = Color.argb(178, 33, 35, 40)
        c.drawRoundRect(RectF(left, top, left + totalW, top + hh), hh * .34f, hh * .34f, p)

        val divider = left + totalW * .60f
        p.color = Color.argb(74, 255, 255, 255)
        c.drawRect(divider, top + hh * .18f, divider + 1f, top + hh * .82f, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = max(10f, w * .0074f)
        p.color = Color.rgb(255, 216, 83)
        c.drawText("목표 거리 ${"%.0f".format(settings.holeDistanceM)}m", left + totalW * .30f, top + hh * .39f, p)
        p.textSize = max(8f, w * .0058f)
        p.color = Color.argb(220, 255, 255, 255)
        c.drawText("PUTTER", left + totalW * .30f, top + hh * .70f, p)

        p.textSize = max(10f, w * .0072f)
        p.color = Color.rgb(118, 232, 151)
        c.drawText(read?.paceHint ?: paceLabel(settings), left + totalW * .80f, top + hh * .39f, p)
        drawBallIcon(c, left + totalW * .80f, top + hh * .69f, max(7f, w * .0054f))
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawContextHud(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val game = engine.gameModes.status
        val shot = engine.currentShot
        val running = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
        val preShot = !running && engine.lastResult == null

        // Small player / hole pill in the upper-left. No large black rail.
        val left = w * .027f
        val top = h * .032f
        val boxW = w * .175f
        val boxH = h * .071f
        p.color = Color.argb(142, 20, 27, 28)
        c.drawRoundRect(RectF(left, top, left + boxW, top + boxH), boxH * .26f, boxH * .26f, p)
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(9f, w * .0065f)
        p.color = Color.argb(235, 255, 255, 255)
        val holeLabel = if (game.totalHoles > 0) "HOLE ${game.hole}/${game.totalHoles}" else game.mode.label
        c.drawText(holeLabel, left + w * .010f, top + h * .025f, p)
        p.textSize = max(8f, w * .0058f)
        p.color = Color.argb(205, 228, 237, 229)
        c.drawText("PLAYER ${game.activePlayer}/${game.playerCount}  ·  GREEN ${"%.1f".format(engine.settings.stimpMeters)}", left + w * .010f, top + h * .052f, p)

        if (preShot) {
            drawWaitPill(c)
        }

        // Telemetry appears only after impact / during roll, and stays compact at lower-left.
        if (shot != null && !preShot) {
            val confidence = V16MetricConfidenceEstimator.estimate(shot)
            val bLeft = w * .028f
            val bBottom = h * .950f
            val bTop = bBottom - h * .105f
            val bRight = bLeft + w * .410f
            p.color = Color.argb(160, 18, 23, 23)
            c.drawRoundRect(RectF(bLeft, bTop, bRight, bBottom), h * .016f, h * .016f, p)

            val items = listOf(
                Triple("BALL", "${"%.2f".format(shot.ballSpeedMps)} m/s", confidence.ballSpeed),
                Triple("START", "${"%+.2f".format(shot.launchAngleDeg)}°", confidence.launch),
                Triple("FACE", shot.faceAngleDeg?.let { "${"%+.2f".format(it)}°" } ?: "--", confidence.face),
                Triple("PATH", shot.pathAngleDeg?.let { "${"%+.2f".format(it)}°" } ?: "--", confidence.path)
            )
            val col = (bRight - bLeft - w * .024f) / items.size
            items.forEachIndexed { i, item ->
                val x = bLeft + w * .012f + col * i
                p.typeface = Typeface.DEFAULT_BOLD
                p.textSize = max(7f, w * .0052f)
                p.color = Color.argb(170, 226, 232, 227)
                c.drawText(item.first, x, bTop + h * .029f, p)
                p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                p.textSize = max(15f, w * .011f)
                p.color = if (item.third >= .78) Color.WHITE else Color.rgb(255, 220, 112)
                c.drawText(item.second, x, bTop + h * .064f, p)
                p.typeface = Typeface.DEFAULT_BOLD
                p.textSize = max(6.5f, w * .0047f)
                p.color = Color.argb(170, 229, 235, 229)
                c.drawText("Q${(item.third * 100).roundToInt()}", x, bTop + h * .088f, p)
            }
        }
    }

    private fun drawWaitPill(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val ww = w * .082f
        val hh = h * .035f
        val left = w * .5f - ww * .5f
        val top = h * .785f
        p.color = Color.argb(146, 76, 54, 47)
        c.drawRoundRect(RectF(left, top, left + ww, top + hh), hh * .5f, hh * .5f, p)
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(8f, w * .0057f)
        p.color = Color.argb(220, 245, 213, 191)
        c.drawText("READY", left + ww * .5f, top + hh * .68f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawResultHud(c: Canvas) {
        val result = engine.lastResult ?: return
        val score = engine.strokeScore
        val coach = engine.coachFeedback
        val w = width.toFloat()
        val h = height.toFloat()
        val boxW = w * .265f
        val boxH = h * .162f
        val left = w - boxW - w * .028f
        val top = h - boxH - h * .050f

        p.color = Color.argb(190, 21, 27, 27)
        c.drawRoundRect(RectF(left, top, left + boxW, top + boxH), h * .020f, h * .020f, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(10f, w * .0073f)
        p.color = if (result.holed) Color.rgb(255, 208, 65) else Color.rgb(123, 232, 148)
        c.drawText(if (result.holed) "NICE PUTT" else if (result.lipOut) "LIP OUT" else "RESULT", left + w * .014f, top + h * .031f, p)

        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(30f, w * .023f)
        p.color = Color.WHITE
        val main = if (result.holed) "IN" else "${"%.0f".format(result.distanceToCupM * 100.0)}cm"
        c.drawText(main, left + w * .014f, top + h * .082f, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(9f, w * .0064f)
        p.color = Color.argb(225, 234, 240, 235)
        c.drawText("SCORE ${score?.total ?: 0}  ·  ${coach?.headline ?: "분석 완료"}", left + w * .014f, top + h * .116f, p)

        engine.performanceSnapshot?.let { snap ->
            p.textSize = max(7.5f, w * .0054f)
            p.color = Color.argb(180, 222, 230, 223)
            c.drawText("${snap.oneLine}  ·  ${snap.training.title}", left + w * .014f, top + h * .143f, p)
        }
    }

    private fun drawCalibrationGuide(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val inset = max(10f, min(w, h) * .020f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(4f, min(w, h) * .005f)
        p.color = Color.rgb(83, 241, 129)
        c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), inset, inset, p)
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(17f, w * .012f)
        p.color = Color.WHITE
        c.drawText("TV SAFE AREA · 초록 테두리가 모두 보이게 맞추세요", inset * 1.6f, inset * 2.6f, p)
    }

    private fun drawBallIcon(c: Canvas, cx: Float, cy: Float, r: Float) {
        p.color = Color.WHITE
        c.drawCircle(cx, cy, r, p)
        p.color = Color.argb(95, 75, 86, 82)
        repeat(5) { i ->
            val a = Math.PI * 2.0 * i / 5.0
            c.drawCircle(cx + cos(a).toFloat() * r * .42f, cy + sin(a).toFloat() * r * .42f, r * .09f, p)
        }
    }

    private fun paceLabel(settings: GreenSettings): String = when {
        settings.stimpMeters >= 3.4 -> "빠름"
        settings.stimpMeters <= 2.4 -> "느림"
        else -> "보통"
    }
}
