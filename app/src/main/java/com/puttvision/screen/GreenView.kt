package com.puttvision.screen

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.*

/**
 * External TV / simulator renderer.
 * Rendering only: it reads GameEngine state but never mutates game/camera logic.
 */
class GreenView(
    context: Context,
    private val engine: GameEngine
) : View(context) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        isClickable = false
        isFocusable = false
        keepScreenOn = true
        setBackgroundColor(Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        canvas.translate(width * ProductSessionRuntime.tvOffsetX, height * ProductSessionRuntime.tvOffsetY)
        canvas.scale(
            ProductSessionRuntime.tvScaleX,
            ProductSessionRuntime.tvScaleY,
            width / 2f,
            height / 2f
        )
        drawCourse(canvas)
        drawGreen(canvas)
        drawBrandRail(canvas)
        drawShotTelemetry(canvas)
        drawResult(canvas)
        if (ProductSessionRuntime.tvCalibrationGuide) drawTvCalibrationGuide(canvas)
        canvas.restoreToCount(save)
        val dynamic = engine.state?.running == true || engine.lastResult == null || ProductSessionRuntime.tvCalibrationGuide
        if (dynamic) postInvalidateOnAnimation() else postInvalidateDelayed(200L)
    }

    private fun drawTvCalibrationGuide(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val inset = max(8f, min(w, h) * .018f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(3f, min(w, h) * .004f)
        p.color = Pv.primary
        c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), inset, inset, p)
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(18f, w * .014f)
        p.color = Color.WHITE
        c.drawText("TV SAFE AREA · 초록 테두리가 전부 보이게 맞추세요", inset * 1.6f, inset * 3.0f, p)
        val arm = min(w, h) * .055f
        p.strokeWidth = max(5f, min(w, h) * .006f)
        p.color = Pv.primary
        p.style = Paint.Style.STROKE
        listOf(
            floatArrayOf(inset, inset, inset + arm, inset, inset, inset + arm),
            floatArrayOf(w - inset, inset, w - inset - arm, inset, w - inset, inset + arm),
            floatArrayOf(inset, h - inset, inset + arm, h - inset, inset, h - inset - arm),
            floatArrayOf(w - inset, h - inset, w - inset - arm, h - inset, w - inset, h - inset - arm)
        ).forEach { a ->
            c.drawLine(a[0], a[1], a[2], a[3], p)
            c.drawLine(a[0], a[1], a[4], a[5], p)
        }
        p.style = Paint.Style.FILL
    }

    private fun drawCourse(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val horizon = h * .305f

        p.shader = LinearGradient(
            0f, 0f, 0f, horizon,
            intArrayOf(Color.rgb(12, 27, 36), Color.rgb(33, 66, 75), Color.rgb(73, 107, 108)),
            floatArrayOf(0f, .58f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, horizon, p)
        p.shader = null

        p.color = Color.argb(28, 240, 246, 247)
        repeat(6) { i ->
            val cx = w * (.10f + i * .17f)
            val cy = horizon * (.18f + (i % 2) * .14f)
            c.drawOval(RectF(cx - w * .085f, cy - h * .018f, cx + w * .085f, cy + h * .018f), p)
        }

        p.color = Color.rgb(22, 60, 39)
        val treeBase = horizon + h * .055f
        repeat(30) { i ->
            val x = w * i / 29f
            val r = w * (.012f + (i % 5) * .0022f)
            c.drawCircle(x, treeBase - r * .55f, r, p)
            c.drawRect(x - r * .08f, treeBase - r * .1f, x + r * .08f, treeBase + r * .65f, p)
        }

        p.shader = LinearGradient(
            0f, horizon, 0f, h,
            Color.rgb(43, 104, 49), Color.rgb(18, 59, 35), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, horizon, w, h, p)
        p.shader = null

        p.shader = LinearGradient(
            0f, h * .58f, 0f, h,
            Color.TRANSPARENT, Color.argb(126, 3, 7, 7), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, h * .58f, w, h, p)
        p.shader = null
    }

    private fun drawGreen(c: Canvas) {
        val settings = engine.settings
        val w = width.toFloat()
        val h = height.toFloat()
        val horizonY = h * .355f
        val bottomY = h * .955f
        val centerX = w * .54f

        val greenShape = Path().apply {
            moveTo(w * .265f, horizonY)
            lineTo(w * .815f, horizonY)
            lineTo(w * .965f, bottomY)
            lineTo(w * .075f, bottomY)
            close()
        }

        p.shader = LinearGradient(
            0f, horizonY, 0f, bottomY,
            intArrayOf(Color.rgb(78, 154, 61), Color.rgb(69, 140, 50), Color.rgb(50, 111, 43)),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(greenShape, p)
        p.shader = null

        val maxY = max(settings.holeDistanceM * 1.28, 3.0)
        fun syBase(y: Double): Float {
            val t = (y / maxY).coerceIn(0.0, 1.0).toFloat()
            return bottomY - (bottomY - horizonY) * t
        }
        val originHeight = GreenTerrain.heightAt(settings.terrainProfileId, 0.0, 0.0, settings.holeDistanceM)
        fun sySurface(x: Double, y: Double): Float {
            val z = GreenTerrain.heightAt(settings.terrainProfileId, x, y, settings.holeDistanceM)
            val relief = ((z - originHeight) * h * 1.35).toFloat()
            return syBase(y) - relief
        }
        fun sy(y: Double): Float = sySurface(0.0, y)
        fun halfWidthAt(yPix: Float): Float {
            val t = ((yPix - horizonY) / (bottomY - horizonY)).coerceIn(0f, 1f)
            return w * (.175f + .265f * t)
        }
        fun sx(x: Double, y: Double): Float {
            val yp = syBase(y)
            val sideRange = max(1.15, settings.holeDistanceM * .20)
            return centerX + (x / sideRange).toFloat() * halfWidthAt(yp)
        }

        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.BUTT

        // Perspective putting grid. Spacing adapts to the selected distance so
        // short-putt screens stay precise while long putts do not become noisy.
        val gridStepM = when {
            maxY <= 6.0 -> 0.5
            maxY <= 12.0 -> 1.0
            else -> 2.0
        }
        val gridSideRange = max(1.15, settings.holeDistanceM * .20)
        var gridY = gridStepM
        while (gridY < maxY) {
            val major = kotlin.math.abs(gridY - kotlin.math.round(gridY)) < 0.02
            val yp = sy(gridY)
            val hw = halfWidthAt(yp)
            p.strokeWidth = if (major) max(1.4f, w * .0009f) else max(.7f, w * .00045f)
            p.color = if (major) Color.argb(84, 231, 255, 235) else Color.argb(38, 183, 238, 199)
            val contour = Path()
            for (sample in 0..24) {
                val frac = sample / 24.0 * 2.0 - 1.0
                val xM = gridSideRange * frac
                val px = sx(xM, gridY)
                val py = sySurface(xM, gridY)
                if (sample == 0) contour.moveTo(px, py) else contour.lineTo(px, py)
            }
            c.drawPath(contour, p)

            if (major && gridY <= settings.holeDistanceM + 1.0) {
                p.style = Paint.Style.FILL
                p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                p.textSize = max(9f, w * .0067f)
                p.color = Color.argb(150, 235, 244, 238)
                c.drawText("${"%.0f".format(gridY)}m", centerX - hw + w * .008f, yp - h * .006f, p)
                p.style = Paint.Style.STROKE
            }
            gridY += gridStepM
        }

        for (lane in -5..5) {
            val frac = lane / 5.0
            val path = Path()
            for (step in 0..36) {
                val yM = maxY * step / 36.0
                val xM = gridSideRange * frac
                val px = sx(xM, yM)
                val py = sySurface(xM, yM)
                if (step == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            p.strokeWidth = if (lane == 0) max(1.5f, w * .0010f) else max(.7f, w * .00045f)
            p.color = if (lane == 0) Color.argb(96, 239, 255, 241) else Color.argb(38, 183, 238, 199)
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL

        val holeY = settings.holeDistanceM
        val preShot = engine.state?.running != true && engine.lastResult == null
        val read = if (preShot) GreenReadRuntime.peekOrSchedule(settings) else null

        if (preShot && read != null) {
            // V11 slope-flow field. Each particle follows the same local slope vector
            // that GreenPhysics uses at this exact x/y location.
            val flowSave = c.save()
            c.clipPath(greenShape)
            val nowSec = (SystemClock.uptimeMillis() % 60_000L) / 1000.0
            for (row in 1..8) {
                val yM = holeY * row / 9.0
                for (lane in -4..4) {
                    val xM = gridSideRange * lane / 4.5
                    val slope = GreenTerrain.effectiveSlopeAt(settings, xM, yM)
                    val mag = hypot(slope.sidePct, slope.longPct)
                    if (mag < .14) continue
                    val ux = slope.sidePct / mag
                    val uy = slope.longPct / mag
                    val travelM = .08 + min(.18, mag * .028)
                    val speed = .42 + min(1.75, mag * .30)
                    for (particle in 0..1) {
                        var phase = (nowSec * speed + row * .173 + lane * .119 + particle * .5) % 1.0
                        if (phase < 0.0) phase += 1.0
                        val centered = phase - .5
                        val pxM = xM + ux * travelM * centered
                        val pyM = yM + uy * travelM * centered
                        val px = sx(pxM, pyM)
                        val py = sySurface(pxM, pyM)
                        val alpha = (70 + 150 * (1.0 - abs(centered) * 1.45).coerceIn(.0, 1.0)).toInt()
                        p.style = Paint.Style.FILL
                        p.color = Color.argb(alpha, 218, 255, 226)
                        c.drawCircle(px, py, max(1.8f, w * .00155f), p)
                    }
                }
            }
            c.restoreToCount(flowSave)

            // No decorative Bezier: this line is literally the path GreenPhysics produced
            // from the recommended launch angle and ball speed.
            read.predictedTrail.takeIf { read.solverReliable && it.size >= 2 }?.let { trail ->
                val guide = Path().apply {
                    moveTo(sx(trail.first().first, trail.first().second), sySurface(trail.first().first, trail.first().second))
                    trail.drop(1).forEach { point ->
                        lineTo(sx(point.first, point.second), sySurface(point.first, point.second))
                    }
                }
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = max(3f, w * .00205f)
                p.pathEffect = DashPathEffect(floatArrayOf(max(8f, w * .008f), max(5f, w * .0045f)), 0f)
                p.color = Color.argb(205, 238, 247, 239)
                c.drawPath(guide, p)
                p.pathEffect = null
                p.strokeCap = Paint.Cap.BUTT
            }

            // Physical aim point at cup distance. This is the same lateral offset used
            // to calculate cup/head counts, not a decorative HUD coordinate.
            if (read.solverReliable) {
                val aimX = read.aimOffsetCm / 100.0
                val ax = sx(aimX, holeY)
                val ay = sySurface(aimX, holeY)
                val radius = max(8f, w * .0068f)
            p.style = Paint.Style.FILL
            p.color = Color.argb(205, 5, 9, 11)
            c.drawCircle(ax, ay, radius * 1.45f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(2f, w * .0015f)
            p.color = Pv.primary
            c.drawCircle(ax, ay, radius, p)
            c.drawLine(ax - radius * .62f, ay, ax + radius * .62f, ay, p)
            c.drawLine(ax, ay - radius * .62f, ax, ay + radius * .62f, p)
            p.style = Paint.Style.FILL
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = max(8f, w * .0062f)
            p.color = Pv.primary
            val aimLabel = if (read.aimSideLabel == "센터") "AIM · CENTER" else "AIM · ${"%.1f".format(read.cupCount)} CUP"
            c.drawText(aimLabel, ax + radius * 1.65f, ay - radius * .35f, p)
            }
        }

        engine.state?.trail?.takeIf { it.size >= 2 }?.let { trail ->
            val actual = Path().apply {
                moveTo(sx(trail.first().first, trail.first().second), sy(trail.first().second))
                trail.drop(1).forEach { point -> lineTo(sx(point.first, point.second), sySurface(point.first, point.second)) }
            }
            p.strokeWidth = max(4f, w * .0028f)
            p.color = Color.argb(238, 255, 210, 88)
            c.drawPath(actual, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL

        val hx = sx(0.0, holeY)
        val hy = sySurface(0.0, holeY)
        p.color = Color.argb(125, 0, 0, 0)
        c.drawOval(RectF(hx - w * .010f, hy - h * .0035f, hx + w * .010f, hy + h * .005f), p)
        p.color = Color.WHITE
        c.drawRect(hx - 1.7f, hy - h * .12f, hx + 1.7f, hy, p)
        val flag = Path().apply {
            moveTo(hx + 2f, hy - h * .12f)
            lineTo(hx + w * .035f, hy - h * .103f)
            lineTo(hx + 2f, hy - h * .082f)
            close()
        }
        p.color = Pv.amber
        c.drawPath(flag, p)

        val state = engine.state
        val bx = if (state != null) sx(state.x, state.y) else sx(0.0, 0.0)
        val by = if (state != null) sySurface(state.x, state.y) else sySurface(0.0, 0.0)
        p.color = Color.argb(72, 0, 0, 0)
        c.drawOval(RectF(bx - w * .012f, by + h * .008f, bx + w * .012f, by + h * .017f), p)
        p.color = Color.WHITE
        c.drawCircle(bx, by, max(8f, w * .0065f), p)

        read?.let { drawAimReadout(c, it) }
    }

    private fun drawAimReadout(c: Canvas, read: GreenRead) {
    // During the roll the green and actual trail are the interface. Do not cover them.
    if (engine.state?.running == true || engine.lastResult != null) return

    val w = width.toFloat()
    val h = height.toFloat()
    val right = w * .963f
    val top = h * .185f
    val widthBox = w * .245f
    val left = right - widthBox
    val bottom = top + h * .112f

    rect.set(left, top, right, bottom)
    p.color = Color.argb(216, 5, 9, 11)
    c.drawRoundRect(rect, h * .016f, h * .016f, p)
    p.style = Paint.Style.STROKE
    p.strokeWidth = 1.1f
    p.color = Color.argb(115, 72, 96, 80)
    c.drawRoundRect(rect, h * .016f, h * .016f, p)
    p.style = Paint.Style.FILL

    val pad = w * .013f
    p.typeface = Typeface.DEFAULT_BOLD
    p.textSize = max(9f, w * .0068f)
    p.color = Pv.primary
    c.drawText("GREEN READ", left + pad, top + h * .026f, p)

    val main = when {
        !read.solverReliable -> "추천선 재계산"
        read.aimSideLabel == "센터" -> "센터"
        else -> "${read.aimSideLabel}  ${"%.1f".format(read.cupCount)}컵"
    }
    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    p.textSize = max(24f, w * .018f)
    p.color = Pv.textHi
    c.drawText(main, left + pad, top + h * .068f, p)

    p.typeface = Typeface.DEFAULT_BOLD
    p.textSize = max(9f, w * .0068f)
    p.color = Pv.textMid
    val head = if (read.aimSideLabel == "센터") "헤드 0.0개" else "헤드 ${"%.1f".format(read.putterHeadCount)}개"
    val residual = "SOLVER ±${"%.1f".format(read.solverMissCm)}cm"
    c.drawText("$head  ·  ${read.paceHint}  ·  ${"%.2f".format(read.recommendedBallSpeedMps)}m/s  ·  $residual", left + pad, bottom - h * .014f, p)
}

        private fun drawBrandRail(c: Canvas) {
    val w = width.toFloat()
    val h = height.toFloat()
    val game = engine.gameModes.status
    val settings = engine.settings

    val left = w * .032f
    val top = h * .032f
    val right = w * .968f
    val bottom = h * .128f
    rect.set(left, top, right, bottom)
    p.color = Color.argb(205, 5, 9, 11)
    c.drawRoundRect(rect, h * .017f, h * .017f, p)
    p.style = Paint.Style.STROKE
    p.strokeWidth = 1.1f
    p.color = Color.argb(100, 58, 73, 78)
    c.drawRoundRect(rect, h * .017f, h * .017f, p)
    p.style = Paint.Style.FILL

    val pad = w * .015f
    p.typeface = Typeface.DEFAULT_BOLD
    p.textSize = max(17f, w * .013f)
    p.color = Pv.textHi
    c.drawText("PUTTVISION", left + pad, top + h * .039f, p)
    p.textSize = max(9f, w * .0068f)
    p.color = Pv.primary
    c.drawText("SCREEN PUTTING", left + pad, top + h * .067f, p)

    fun compactMetric(x: Float, label: String, value: String, accent: Int = Pv.textHi) {
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(8f, w * .0061f)
        p.color = Pv.textLo
        c.drawText(label, x, top + h * .027f, p)
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = max(18f, w * .014f)
        p.color = accent
        c.drawText(value, x, top + h * .064f, p)
    }

    compactMetric(left + w * .205f, "DISTANCE", "${"%.1f".format(settings.holeDistanceM)}m", Pv.primary)
    compactMetric(left + w * .335f, "GREEN", "${"%.1f".format(settings.stimpMeters)}")

    p.textAlign = Paint.Align.RIGHT
    p.typeface = Typeface.DEFAULT_BOLD
    p.textSize = max(9f, w * .0068f)
    p.color = Pv.textLo
    c.drawText("${game.mode.label}  ·  ${game.activePlayer}/${game.playerCount}", right - pad, top + h * .028f, p)
    p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    p.textSize = max(18f, w * .014f)
    p.color = Pv.textHi
    val modeValue = if (game.totalHoles > 0) "HOLE ${game.hole}  ·  ${game.gameScore}" else game.gameScore.toString()
    c.drawText(modeValue, right - pad, top + h * .065f, p)
    p.textAlign = Paint.Align.LEFT
    p.typeface = Typeface.DEFAULT
}

        private fun drawShotTelemetry(c: Canvas) {
    val shot = engine.currentShot ?: return
    val w = width.toFloat()
    val h = height.toFloat()
    val left = w * .032f
    val bottom = h * .955f
    val top = h * .855f
    val right = w * .555f

    rect.set(left, top, right, bottom)
    p.color = Color.argb(205, 5, 9, 11)
    c.drawRoundRect(rect, h * .016f, h * .016f, p)

    val items = listOf(
        Triple("BALL", "%.2f".format(shot.ballSpeedMps), "m/s"),
        Triple("START", "%+.2f".format(shot.launchAngleDeg), "°"),
        Triple("HEAD", shot.headSpeedMps?.let { "%.2f".format(it) } ?: "--", "m/s")
    )
    val pad = w * .014f
    val colW = (right - left - pad * 2f) / items.size
    items.forEachIndexed { i, item ->
        val x = left + pad + colW * i
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(8f, w * .0061f)
        p.color = Pv.textLo
        c.drawText(item.first, x, top + h * .028f, p)
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = max(23f, w * .018f)
        p.color = if (i == 0) Pv.primary else Pv.textHi
        c.drawText(item.second, x, top + h * .072f, p)
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(8f, w * .006f)
        p.color = Pv.textLo
        c.drawText(item.third, x + colW * .61f, top + h * .071f, p)
    }
}

        private fun drawResult(c: Canvas) {
    val result = engine.lastResult ?: return
    val score = engine.strokeScore
    val w = width.toFloat()
    val h = height.toFloat()
    val left = w * .685f
    val right = w * .968f
    val top = h * .785f
    val bottom = h * .955f

    rect.set(left, top, right, bottom)
    p.color = Color.argb(228, 5, 9, 11)
    c.drawRoundRect(rect, h * .020f, h * .020f, p)
    p.style = Paint.Style.STROKE
    p.strokeWidth = 1.3f
    p.color = if (result.holed) Color.argb(175, 246, 190, 74) else Color.argb(130, 78, 209, 121)
    c.drawRoundRect(rect, h * .020f, h * .020f, p)
    p.style = Paint.Style.FILL

    val x = left + w * .017f
    p.typeface = Typeface.DEFAULT_BOLD
    p.textSize = max(10f, w * .0075f)
    p.color = if (result.holed) Pv.amber else Pv.primary
    c.drawText(if (result.holed) "HOLED" else "RESULT", x, top + h * .034f, p)

    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    p.textSize = max(34f, w * .027f)
    p.color = Pv.textHi
    val main = if (result.holed) "IN" else "${"%.0f".format(result.distanceToCupM * 100.0)} cm"
    c.drawText(main, x, top + h * .093f, p)

    val side = when {
        result.finishX > .03 -> "오른쪽"
        result.finishX < -.03 -> "왼쪽"
        else -> "센터"
    }
    p.typeface = Typeface.DEFAULT_BOLD
    p.textSize = max(10f, w * .0075f)
    p.color = Pv.textMid
    c.drawText("$side  ·  ${score?.let { "SCORE ${it.total}" } ?: "분석 중"}", x, top + h * .128f, p)

    val read = GreenReadRuntime.peekOrSchedule(engine.settings)
    p.typeface = Typeface.DEFAULT
    p.textSize = max(9f, w * .0068f)
    p.color = Pv.textLo
    val readText = when {
        read == null -> "추천 에임 계산중"
        !read.solverReliable -> "추천 에임 보류 · SOLVER ±${"%.1f".format(read.solverMissCm)}cm"
        read.aimSideLabel == "센터" -> "추천 에임 센터"
        else -> "추천 ${read.aimSideLabel} ${"%.1f".format(read.cupCount)}컵"
    }
    c.drawText(readText, x, bottom - h * .014f, p)
}
}
