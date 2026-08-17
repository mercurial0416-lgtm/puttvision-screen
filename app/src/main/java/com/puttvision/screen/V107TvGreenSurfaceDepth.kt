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
        return V107GreenSurfacePlan(
            targetDistanceM = target,
            visibleLengthM = visible,
            halfWidthM = 3.4,
            stripeWidthM = stripeWidth,
            stripeCount = stripes,
            stripeAlpha = if (running) 6 else 10,
            edgeAlpha = if (running) 12 else 18,
            centerGuideAlpha = if (running) 7 else 12,
            horizonAlpha = if (running) 16 else 24,
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
        drawCenterDepthGuide(canvas, plan)
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
        val settings = engine.settings
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, min(width, height) * .0012f)
        paint.color = Color.argb(plan.edgeAlpha, 224, 247, 227)
        paint.shader = null

        for (side in listOf(-1.0, 1.0)) {
            path.reset()
            var started = false
            val segments = 24
            for (i in 0..segments) {
                val y = plan.visibleLengthM * i / segments.toDouble()
                val x = side * plan.halfWidthM
                val z = GreenTerrain.effectiveHeightAt(settings, x, y)
                if (!z.isFinite()) continue
                val sp = V25FlagProjectionRuntime.project(x, y, z + .003) ?: continue
                if (!started) {
                    path.moveTo(sp.x, sp.y)
                    started = true
                } else path.lineTo(sp.x, sp.y)
            }
            if (started) canvas.drawPath(path, paint)
        }
    }

    private fun drawCenterDepthGuide(canvas: Canvas, plan: V107GreenSurfacePlan) {
        val settings = engine.settings
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, min(width, height) * .0008f)
        paint.color = Color.argb(plan.centerGuideAlpha, 255, 255, 255)
        paint.shader = null
        path.reset()
        var started = false
        val segments = 28
        for (i in 0..segments) {
            val y = plan.visibleLengthM * i / segments.toDouble()
            val z = GreenTerrain.effectiveHeightAt(settings, 0.0, y)
            if (!z.isFinite()) continue
            val sp = V25FlagProjectionRuntime.project(0.0, y, z + .004) ?: continue
            if (!started) {
                path.moveTo(sp.x, sp.y)
                started = true
            } else path.lineTo(sp.x, sp.y)
        }
        if (started) canvas.drawPath(path, paint)
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
