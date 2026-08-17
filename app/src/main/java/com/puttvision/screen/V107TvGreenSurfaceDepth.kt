package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Visual-only projected green treatment. It reads the existing terrain/projection state and never
 * mutates GreenPhysics or measured shot metrics.
 */
data class V107GreenSurfacePlan(
    val targetDistanceM: Double,
    val visibleLengthM: Double,
    val halfWidthM: Double,
    val stripeWidthM: Double,
    val stripeCount: Int,
    val stripeAlpha: Int,
    val edgeAlpha: Int,
    val centerGuideAlpha: Int,
    val horizonAlpha: Int,
    val distanceTickSpacingM: Double,
    val distanceTickCount: Int,
    val distanceTickAlpha: Int,
    val majorTickEvery: Int,
    val majorTickHalfWidthM: Double,
    val distanceLabelEvery: Int,
    val distanceLabelCount: Int,
    val distanceLabelAlpha: Int,
    val targetGuideAlpha: Int,
    val cupWindowHalfLengthM: Double,
    val cupWindowHalfWidthM: Double,
    val cupWindowAlpha: Int,
    val laneOffsetM: Double,
    val laneGuideAlpha: Int,
    val refreshMs: Long
)

object V107TvGreenSurfacePlanner {
    fun plan(holeDistanceM: Double, running: Boolean): V107GreenSurfacePlan {
        val target = holeDistanceM.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val visible = (target + 3.0).coerceIn(4.0, 33.0)
        val stripeWidth = when {
            visible <= 8.0 -> .65
            visible <= 16.0 -> .85
            else -> 1.05
        }
        val stripes = ceil(visible / stripeWidth).toInt().coerceIn(5, 32)
        val tickSpacing = when {
            visible <= 8.0 -> 1.0
            visible <= 18.0 -> 2.0
            else -> 3.0
        }
        val ticks = (visible / tickSpacing).toInt().coerceIn(1, 16)
        val majorEvery = when {
            ticks <= 6 -> 2
            ticks <= 12 -> 3
            else -> 4
        }
        val labelEvery = majorEvery
        val labels = (ticks / labelEvery).coerceIn(0, 6)
        val halfWidth = 3.4
        return V107GreenSurfacePlan(
            targetDistanceM = target,
            visibleLengthM = visible,
            halfWidthM = halfWidth,
            stripeWidthM = stripeWidth,
            stripeCount = stripes,
            stripeAlpha = if (running) 6 else 10,
            edgeAlpha = if (running) 12 else 18,
            centerGuideAlpha = if (running) 7 else 12,
            horizonAlpha = if (running) 16 else 24,
            distanceTickSpacingM = tickSpacing,
            distanceTickCount = ticks,
            distanceTickAlpha = if (running) 5 else 9,
            majorTickEvery = majorEvery,
            majorTickHalfWidthM = min(.90, halfWidth * .28),
            distanceLabelEvery = labelEvery,
            distanceLabelCount = labels,
            distanceLabelAlpha = if (running) 0 else 42,
            targetGuideAlpha = if (target > 0.0) if (running) 20 else 48 else 0,
            cupWindowHalfLengthM = .60,
            cupWindowHalfWidthM = .72,
            cupWindowAlpha = if (target > 0.0) if (running) 10 else 24 else 0,
            laneOffsetM = min(1.15, halfWidth * .34),
            laneGuideAlpha = if (running) 4 else 8,
            refreshMs = if (running) 66L else 240L
        )
    }
}

class V107TvGreenSurfaceDepthView(
    context: Context,
    private val engine: GameEngine
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val state = engine.state
        val running = state?.running == true || TvInstantRollRuntime.isAnimating()
        val plan = V107TvGreenSurfacePlanner.plan(engine.settings.holeDistanceM, running)

        drawMowingBands(canvas, plan)
        drawGreenEdges(canvas, plan)
        drawLaneGuides(canvas, plan)
        drawCenterDepthGuide(canvas, plan)
        drawDistanceTicks(canvas, plan)
        drawCupWindow(canvas, plan)
        drawTargetGuide(canvas, plan)
        drawHorizonHaze(canvas, plan)

        postInvalidateDelayed(plan.refreshMs)
    }

    private fun drawMowingBands(canvas: Canvas, plan: V107GreenSurfacePlan) {
        val settings = engine.settings
        for (i in 0 until plan.stripeCount) {
            if (i % 2 != 0) continue
            val y0 = i * plan.stripeWidthM
            val y1 = min(plan.visibleLengthM, y0 + plan.stripeWidthM)
            if (y1 <= y0) continue

            val z00 = GreenTerrain.effectiveHeightAt(settings, -plan.halfWidthM, y0)
            val z10 = GreenTerrain.effectiveHeightAt(settings, plan.halfWidthM, y0)
            val z11 = GreenTerrain.effectiveHeightAt(settings, plan.halfWidthM, y1)
            val z01 = GreenTerrain.effectiveHeightAt(settings, -plan.halfWidthM, y1)
            if (!listOf(z00, z10, z11, z01).all { it.isFinite() }) continue

            val p00 = V25FlagProjectionRuntime.project(-plan.halfWidthM, y0, z00 + .001) ?: continue
            val p10 = V25FlagProjectionRuntime.project(plan.halfWidthM, y0, z10 + .001) ?: continue
            val p11 = V25FlagProjectionRuntime.project(plan.halfWidthM, y1, z11 + .001) ?: continue
            val p01 = V25FlagProjectionRuntime.project(-plan.halfWidthM, y1, z01 + .001) ?: continue

            path.reset()
            path.moveTo(p00.x, p00.y)
            path.lineTo(p10.x, p10.y)
            path.lineTo(p11.x, p11.y)
            path.lineTo(p01.x, p01.y)
            path.close()
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = Color.argb(plan.stripeAlpha, 235, 255, 236)
            canvas.drawPath(path, paint)
        }
    }

    private fun drawGreenEdges(canvas: Canvas, plan: V107GreenSurfacePlan) {
        drawDepthGuideAtX(canvas, plan, -plan.halfWidthM, plan.edgeAlpha, .0012f)
        drawDepthGuideAtX(canvas, plan, plan.halfWidthM, plan.edgeAlpha, .0012f)
    }

    private fun drawLaneGuides(canvas: Canvas, plan: V107GreenSurfacePlan) {
        if (plan.laneGuideAlpha <= 0) return
        drawDepthGuideAtX(canvas, plan, -plan.laneOffsetM, plan.laneGuideAlpha, .00065f)
        drawDepthGuideAtX(canvas, plan, plan.laneOffsetM, plan.laneGuideAlpha, .00065f)
    }

    private fun drawCenterDepthGuide(canvas: Canvas, plan: V107GreenSurfacePlan) {
        drawDepthGuideAtX(canvas, plan, 0.0, plan.centerGuideAlpha, .0008f)
    }

    private fun drawDepthGuideAtX(
        canvas: Canvas,
        plan: V107GreenSurfacePlan,
        x: Double,
        alpha: Int,
        strokeScale: Float
    ) {
        val settings = engine.settings
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, min(width, height) * strokeScale)
        paint.color = Color.argb(alpha.coerceIn(0, 255), 255, 255, 255)
        paint.shader = null
        path.reset()
        var started = false
        val segments = 24
        for (i in 0..segments) {
            val y = plan.visibleLengthM * i / segments.toDouble()
            val z = GreenTerrain.effectiveHeightAt(settings, x, y)
            if (!z.isFinite()) continue
            val sp = V25FlagProjectionRuntime.project(x, y, z + .004) ?: continue
            if (!started) {
                path.moveTo(sp.x, sp.y)
                started = true
            } else path.lineTo(sp.x, sp.y)
        }
        if (started) canvas.drawPath(path, paint)
    }

    private fun drawDistanceTicks(canvas: Canvas, plan: V107GreenSurfacePlan) {
        val settings = engine.settings
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, min(width, height) * .0009f)
        paint.shader = null

        var labelsDrawn = 0
        for (i in 1..plan.distanceTickCount) {
            val y = i * plan.distanceTickSpacingM
            if (y <= 0.0 || y > plan.visibleLengthM) continue
            val major = i % plan.majorTickEvery == 0
            val halfTickWidthM = if (major) plan.majorTickHalfWidthM else min(.55, plan.halfWidthM * .18)
            val alpha = if (major) (plan.distanceTickAlpha * 2).coerceAtMost(24) else plan.distanceTickAlpha
            val zl = GreenTerrain.effectiveHeightAt(settings, -halfTickWidthM, y)
            val zr = GreenTerrain.effectiveHeightAt(settings, halfTickWidthM, y)
            if (!zl.isFinite() || !zr.isFinite()) continue
            val left = V25FlagProjectionRuntime.project(-halfTickWidthM, y, zl + .005) ?: continue
            val right = V25FlagProjectionRuntime.project(halfTickWidthM, y, zr + .005) ?: continue
            paint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawLine(left.x, left.y, right.x, right.y, paint)

            if (major && plan.distanceLabelAlpha > 0 && labelsDrawn < plan.distanceLabelCount) {
                drawDistanceLabel(canvas, y, right.x, right.y, plan.distanceLabelAlpha)
                labelsDrawn++
            }
        }
    }

    private fun drawDistanceLabel(canvas: Canvas, distanceM: Double, x: Float, y: Float, alpha: Int) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.argb(alpha.coerceIn(0, 255), 255, 255, 255)
        paint.textSize = max(10f, min(width, height) * .014f)
        paint.isFakeBoldText = true
        val text = if (distanceM < 10.0) String.format("%.0fm", distanceM) else "${distanceM.roundToInt()}m"
        canvas.drawText(text, x + paint.textSize * .38f, y + paint.textSize * .32f, paint)
        paint.isFakeBoldText = false
    }

    private fun drawTargetGuide(canvas: Canvas, plan: V107GreenSurfacePlan) {
        if (plan.targetGuideAlpha <= 0 || plan.targetDistanceM <= 0.0 || plan.targetDistanceM > plan.visibleLengthM) return
        val settings = engine.settings
        val halfWidth = min(1.15, plan.halfWidthM * .40)
        val y = plan.targetDistanceM
        val zl = GreenTerrain.effectiveHeightAt(settings, -halfWidth, y)
        val zr = GreenTerrain.effectiveHeightAt(settings, halfWidth, y)
        if (!zl.isFinite() || !zr.isFinite()) return
        val left = V25FlagProjectionRuntime.project(-halfWidth, y, zl + .009) ?: return
        val right = V25FlagProjectionRuntime.project(halfWidth, y, zr + .009) ?: return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.5f, min(width, height) * .0015f)
        paint.color = Color.argb(plan.targetGuideAlpha, 255, 244, 186)
        paint.shader = null
        canvas.drawLine(left.x, left.y, right.x, right.y, paint)
    }

    private fun drawCupWindow(canvas: Canvas, plan: V107GreenSurfacePlan) {
        if (plan.cupWindowAlpha <= 0 || plan.targetDistanceM <= 0.0) return
        val settings = engine.settings
        val y0 = max(0.0, plan.targetDistanceM - plan.cupWindowHalfLengthM)
        val y1 = min(plan.visibleLengthM, plan.targetDistanceM + plan.cupWindowHalfLengthM)
        if (y1 <= y0) return
        val x0 = -plan.cupWindowHalfWidthM
        val x1 = plan.cupWindowHalfWidthM
        val z00 = GreenTerrain.effectiveHeightAt(settings, x0, y0)
        val z10 = GreenTerrain.effectiveHeightAt(settings, x1, y0)
        val z11 = GreenTerrain.effectiveHeightAt(settings, x1, y1)
        val z01 = GreenTerrain.effectiveHeightAt(settings, x0, y1)
        if (!listOf(z00, z10, z11, z01).all { it.isFinite() }) return
        val p00 = V25FlagProjectionRuntime.project(x0, y0, z00 + .006) ?: return
        val p10 = V25FlagProjectionRuntime.project(x1, y0, z10 + .006) ?: return
        val p11 = V25FlagProjectionRuntime.project(x1, y1, z11 + .006) ?: return
        val p01 = V25FlagProjectionRuntime.project(x0, y1, z01 + .006) ?: return
        path.reset()
        path.moveTo(p00.x, p00.y)
        path.lineTo(p10.x, p10.y)
        path.lineTo(p11.x, p11.y)
        path.lineTo(p01.x, p01.y)
        path.close()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, min(width, height) * .001f)
        paint.color = Color.argb(plan.cupWindowAlpha, 255, 244, 186)
        paint.shader = null
        canvas.drawPath(path, paint)
    }

    private fun drawHorizonHaze(canvas: Canvas, plan: V107GreenSurfacePlan) {
        val top = height * .12f
        val bottom = height * .46f
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            top,
            0f,
            bottom,
            Color.argb(plan.horizonAlpha, 225, 238, 235),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, top, width.toFloat(), bottom, paint)
        paint.shader = null
    }
}
