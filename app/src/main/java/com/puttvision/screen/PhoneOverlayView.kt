package com.puttvision.screen

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.max

class PhoneOverlayView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = false
        isFocusable = false
    }

    var status: String = "자동 캘리브레이션 대기"
    var calibrationImagePoints: List<PointF> = emptyList()
    var lastOverlay: VisionOverlay? = null

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val d = resources.displayMetrics.density
        drawGrid(c, d)
        drawCameraBadges(c, d)
        drawCalibrationBadge(c, d)
        drawTracking(c, d)
        drawStatus(c, d)
    }

    private fun drawGrid(c: Canvas, d: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = .65f * d
        p.color = Color.argb(43, 78, 209, 121)
        for (i in 1 until 12) {
            val x = width * i / 12f
            c.drawLine(x, 0f, x, height.toFloat(), p)
        }
        for (i in 1 until 8) {
            val y = height * i / 8f
            c.drawLine(0f, y, width.toFloat(), y, p)
        }
        p.color = Color.argb(100, 78, 209, 121)
        p.strokeWidth = 1f * d
        c.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), p)
        c.drawCircle(width / 2f, height * .66f, 13f * d, p)
        p.style = Paint.Style.FILL
    }

    private fun drawCameraBadges(c: Canvas, d: Float) {
        val left = 10f * d
        val top = 9f * d
        val box = RectF(left, top, left + 112f * d, top + 50f * d)
        p.color = Color.argb(220, 6, 10, 13)
        c.drawRoundRect(box, 11f * d, 11f * d, p)

        p.color = Pv.primary
        p.textSize = 10.5f * d
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText("240fps", left + 9f * d, top + 16f * d, p)
        p.color = Pv.textMid
        p.textSize = 7.5f * d
        p.typeface = Typeface.DEFAULT
        c.drawText("FHD 1920×1080", left + 9f * d, top + 30f * d, p)

        p.color = Pv.primaryDim
        c.drawRoundRect(RectF(left + 8f * d, top + 35f * d, left + 65f * d, top + 47f * d), 6f * d, 6f * d, p)
        p.color = Pv.primary
        p.textSize = 6.5f * d
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText("HFR MODE", left + 14f * d, top + 44f * d, p)

        val right = width - 10f * d
        val badgeL = right - 72f * d
        p.color = Color.argb(210, 6, 10, 13)
        c.drawRoundRect(RectF(badgeL, top, right, top + 25f * d), 10f * d, 10f * d, p)
        p.color = Pv.textHi
        p.textSize = 7.5f * d
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText("AUTO", badgeL + 9f * d, top + 16f * d, p)
        p.color = Pv.primary
        c.drawCircle(right - 12f * d, top + 12.5f * d, 3.2f * d, p)
    }

    private fun drawCalibrationBadge(c: Canvas, d: Float) {
        val calibrated = calibrationImagePoints.size >= 4
        val left = 10f * d
        val bottom = height - 10f * d
        val w = if (calibrated) 90f * d else 104f * d
        p.color = Color.argb(215, 6, 10, 13)
        c.drawRoundRect(RectF(left, bottom - 25f * d, left + w, bottom), 11f * d, 11f * d, p)
        p.color = if (calibrated) Pv.primary else Pv.amber
        c.drawCircle(left + 11f * d, bottom - 12.5f * d, 3.6f * d, p)
        p.textSize = 8.2f * d
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText(if (calibrated) "CALIBRATED" else "CALIBRATING", left + 20f * d, bottom - 9f * d, p)
    }

    private fun drawTracking(c: Canvas, d: Float) {
        calibrationImagePoints.mapNotNull { mapRawToView(it, lastOverlay?.frameInfo) }.forEach { pt ->
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.2f * d
            p.color = Color.argb(210, 255, 214, 58)
            c.drawRect(pt.x - 6f * d, pt.y - 6f * d, pt.x + 6f * d, pt.y + 6f * d, p)
            p.style = Paint.Style.FILL
        }

        lastOverlay?.let { ov ->
            ov.ballImage?.let { raw ->
                mapRawToView(raw, ov.frameInfo)?.let { pt ->
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2.2f * d
                    p.color = Color.WHITE
                    c.drawCircle(pt.x, pt.y, 9f * d, p)
                    p.style = Paint.Style.FILL
                }
            }
            val heel = ov.heelImage?.let { mapRawToView(it, ov.frameInfo) }
            val toe = ov.toeImage?.let { mapRawToView(it, ov.frameInfo) }
            if (heel != null) {
                p.color = Color.rgb(255, 145, 40)
                c.drawCircle(heel.x, heel.y, 6f * d, p)
            }
            if (toe != null) {
                p.color = Color.rgb(63, 167, 255)
                c.drawCircle(toe.x, toe.y, 6f * d, p)
            }
            if (heel != null && toe != null) {
                p.strokeWidth = 1.8f * d
                p.color = Color.WHITE
                c.drawLine(heel.x, heel.y, toe.x, toe.y, p)
            }
        }
    }

    private fun drawStatus(c: Canvas, d: Float) {
        val safe = if (status.length > 42) status.take(41) + "…" else status
        p.textSize = 7f * d
        p.typeface = Typeface.DEFAULT_BOLD
        val textW = p.measureText(safe)
        val cx = width / 2f
        val bottom = height - 10f * d
        val left = (cx - textW / 2f - 10f * d).coerceAtLeast(112f * d)
        val right = (cx + textW / 2f + 10f * d).coerceAtMost(width - 10f * d)
        if (right <= left) return
        p.color = Color.argb(190, 6, 10, 13)
        c.drawRoundRect(RectF(left, bottom - 22f * d, right, bottom), 10f * d, 10f * d, p)
        p.color = Pv.textHi
        c.drawText(safe, left + 10f * d, bottom - 7f * d, p)
    }

    private fun mapRawToView(raw: PointF, frame: FrameInfo?): PointF? {
        frame ?: return null
        val iw = frame.width.toFloat()
        val ih = frame.height.toFloat()
        val rx: Float
        val ry: Float
        val rw: Float
        val rh: Float
        when ((frame.rotationDegrees % 360 + 360) % 360) {
            90 -> { rx = ih - raw.y; ry = raw.x; rw = ih; rh = iw }
            180 -> { rx = iw - raw.x; ry = ih - raw.y; rw = iw; rh = ih }
            270 -> { rx = raw.y; ry = iw - raw.x; rw = ih; rh = iw }
            else -> { rx = raw.x; ry = raw.y; rw = iw; rh = ih }
        }
        if (width <= 0 || height <= 0 || rw <= 0f || rh <= 0f) return null
        val scale = max(width / rw, height / rh)
        val dx = (width - rw * scale) / 2f
        val dy = (height - rh * scale) / 2f
        return PointF(dx + rx * scale, dy + ry * scale)
    }
}
