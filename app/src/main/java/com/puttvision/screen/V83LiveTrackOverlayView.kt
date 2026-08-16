package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Render model for the immediate HFR replay overlay.
 * Angles here are IMAGE-PLANE heel/toe orientation, not calibrated world-space face angle.
 */
data class V83LiveTrackRenderModel(
    val ready: Boolean,
    val ballTrail: List<V81LiveTrackPoint>,
    val putterGhosts: List<V81LivePutterPose>,
    val currentBall: V81LiveTrackPoint?,
    val currentPutter: V81LivePutterPose?,
    val impactReached: Boolean,
    val imageFaceLabel: String,
    val reason: String
)

object V83LiveTrackRenderPlanner {
    fun plan(overlay: V81LiveTrackOverlay, playheadMs: Double, ghostWindowMs: Double = 80.0): V83LiveTrackRenderModel {
        val slice = V82LiveTrackReplay.slice(overlay, playheadMs, ghostWindowMs)
        if (!slice.ready) return V83LiveTrackRenderModel(
            ready = false,
            ballTrail = emptyList(),
            putterGhosts = emptyList(),
            currentBall = null,
            currentPutter = null,
            impactReached = false,
            imageFaceLabel = "IMAGE FACE --",
            reason = slice.reason
        )
        val pose = slice.currentPutter
        val label = pose?.let { "IMAGE FACE ${"%+.1f".format(it.faceAngleDeg)}°" } ?: "IMAGE FACE --"
        return V83LiveTrackRenderModel(
            ready = true,
            ballTrail = slice.ballTrail,
            putterGhosts = slice.putterGhosts,
            currentBall = slice.currentBall,
            currentPutter = pose,
            impactReached = slice.impactReached,
            imageFaceLabel = label,
            reason = "render model ready"
        )
    }
}

/**
 * Thin Android renderer intended to sit over an HFR replay surface.
 * It consumes normalized 0..1 coordinates only; measurement math stays outside the View.
 */
class V83LiveTrackOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var model: V83LiveTrackRenderModel? = null
    private val viewport = RectF()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun render(value: V83LiveTrackRenderModel?) {
        model = value
        visibility = if (value?.ready == true) VISIBLE else GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val m = model?.takeIf { it.ready } ?: return
        if (width <= 0 || height <= 0) return
        viewport.set(0f, 0f, width.toFloat(), height.toFloat())

        fun x(v: Double) = (v.coerceIn(0.0, 1.0) * viewport.width()).toFloat() + viewport.left
        fun y(v: Double) = (v.coerceIn(0.0, 1.0) * viewport.height()).toFloat() + viewport.top

        // Ball history. Trail is deliberately image-space only.
        if (m.ballTrail.size >= 2) {
            val p = Path()
            val first = m.ballTrail.first()
            p.moveTo(x(first.x01), y(first.y01))
            m.ballTrail.drop(1).forEach { p.lineTo(x(it.x01), y(it.y01)) }
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = max(3f, width * .004f)
            paint.color = Color.argb(225, 76, 219, 135)
            canvas.drawPath(p, paint)
        }

        // Putter ghosts fade toward the past.
        val ghosts = m.putterGhosts
        ghosts.forEachIndexed { index, pose ->
            val alpha = (55 + 150.0 * (index + 1) / ghosts.size.coerceAtLeast(1)).toInt().coerceIn(35, 205)
            drawPutter(canvas, pose, alpha, current = false)
        }

        m.currentPutter?.let { drawPutter(canvas, it, 245, current = true) }
        m.currentBall?.let {
            paint.style = Paint.Style.FILL
            paint.color = if (m.impactReached) Color.rgb(246, 190, 74) else Color.WHITE
            canvas.drawCircle(x(it.x01), y(it.y01), max(7f, width * .010f), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(2f, width * .0022f)
            paint.color = Color.argb(220, 0, 0, 0)
            canvas.drawCircle(x(it.x01), y(it.y01), max(7f, width * .010f), paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(190, 3, 7, 10)
        val labelPad = max(10f, width * .012f)
        val labelH = max(32f, height * .075f)
        val labelW = max(155f, width * .28f)
        canvas.drawRoundRect(RectF(labelPad, labelPad, labelPad + labelW, labelPad + labelH), 12f, 12f, paint)
        paint.color = Color.WHITE
        paint.textSize = max(13f, width * .025f)
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText(m.imageFaceLabel, labelPad * 1.55f, labelPad + labelH * .62f, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
    }

    private fun drawPutter(canvas: Canvas, pose: V81LivePutterPose, alpha: Int, current: Boolean) {
        val cx = (pose.centerX01.coerceIn(0.0, 1.0) * width).toFloat()
        val cy = (pose.centerY01.coerceIn(0.0, 1.0) * height).toFloat()
        val a = Math.toRadians(pose.faceAngleDeg)
        val half = max(18f, width * .055f)
        val dx = cos(a).toFloat() * half
        val dy = sin(a).toFloat() * half
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = if (current) max(5f, width * .006f) else max(2f, width * .003f)
        paint.color = if (current) Color.argb(alpha, 246, 190, 74) else Color.argb(alpha, 86, 167, 255)
        canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, paint)
        if (current) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, max(4f, width * .005f), paint)
        }
    }
}

/** Hardwareless visual-model checks; no Android Canvas is needed in CI. */
data class V83LiveTrackVisualSuiteResult(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val reason: String
)

object V83HardwarelessLiveTrackVisualSuite {
    fun verify(): V83LiveTrackVisualSuiteResult {
        val overlay = V81LiveTrackOverlay(
            ball = listOf(
                V81LiveTrackPoint(9, -8.0, .45, .55),
                V81LiveTrackPoint(10, 0.0, .46, .53),
                V81LiveTrackPoint(11, 8.0, .48, .49)
            ),
            putter = listOf(
                V81LivePutterPose(9, -8.0, .40, .64, -2.0),
                V81LivePutterPose(10, 0.0, .42, .62, 1.5),
                V81LivePutterPose(11, 8.0, .44, .60, 2.5)
            ),
            impactFrame = 10,
            fps = 240,
            sourceWidthPx = 1920,
            sourceHeightPx = 1080,
            ready = true,
            reason = "fixture"
        )
        val before = V83LiveTrackRenderPlanner.plan(overlay, -8.0)
        val impact = V83LiveTrackRenderPlanner.plan(overlay, 0.0)
        val after = V83LiveTrackRenderPlanner.plan(overlay, 8.0)
        val bad = V83LiveTrackRenderPlanner.plan(overlay.copy(ready = false, reason = "blocked"), 0.0)
        val badTime = V83LiveTrackRenderPlanner.plan(overlay, Double.NaN)
        val checks = listOf(
            "pre-impact model ready" to before.ready,
            "impact state flips at zero" to (!before.impactReached && impact.impactReached),
            "ball trail grows monotonically" to (before.ballTrail.size <= impact.ballTrail.size && impact.ballTrail.size <= after.ballTrail.size),
            "putter ghost window stays populated" to (impact.putterGhosts.isNotEmpty() && after.putterGhosts.isNotEmpty()),
            "image-space angle is explicitly labelled" to impact.imageFaceLabel.startsWith("IMAGE FACE"),
            "unready overlay fails closed" to !bad.ready,
            "non-finite playhead fails closed" to !badTime.ready
        )
        val count = checks.count { it.second }
        return V83LiveTrackVisualSuiteResult(
            passed = count == checks.size,
            checksPassed = count,
            checksTotal = checks.size,
            reason = checks.firstOrNull { !it.second }?.first ?: "live-track replay render model verified"
        )
    }
}

object V83HardwarelessLiveTrackVisualRuntime {
    @Volatile private var latest: V83LiveTrackVisualSuiteResult? = null
    fun run(): V83LiveTrackVisualSuiteResult = V83HardwarelessLiveTrackVisualSuite.verify().also { latest = it }
    fun snapshot(): V83LiveTrackVisualSuiteResult? = latest
    fun clear() { latest = null }
}
