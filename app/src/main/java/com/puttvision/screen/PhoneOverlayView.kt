package com.puttvision.screen

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

class PhoneOverlayView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    var status: String = "자동 캘리브레이션 대기"
    var calibrationImagePoints: List<PointF> = emptyList()
    var lastOverlay: VisionOverlay? = null

    override fun onDraw(c: Canvas) {
        super.onDraw(c)

        val d = resources.displayMetrics.density
        val left = 12f * d
        val top = 10f * d

        // Compact instrument badge; clamp it on narrow preview panes.
        val badgeRight = min(width - left, left + 238f * d)
        p.color = Color.argb(232, 9, 12, 16)
        c.drawRoundRect(left, top, badgeRight, top + 45f * d, 13f * d, 13f * d, p)
        p.color = Pv.primary
        p.textSize = 11.5f * d
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText(if (calibrationImagePoints.size >= 4) "✓ CALIBRATED" else "● CALIBRATING", left + 10f * d, top + 17f * d, p)
        p.color = Pv.textHi
        p.textSize = 9.5f * d
        p.typeface = Typeface.DEFAULT
        val shortStatus = if (status.length > 34) status.take(33) + "…" else status
        c.drawText(shortStatus, left + 10f * d, top + 34f * d, p)

        // Fine measurement grid like the original preview.
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.75f * d
        p.color = Color.argb(45, 78, 209, 121)
        val gridTop = 4f * d
        val gridBottom = height - 4f * d
        val gridLeft = 4f * d
        val gridRight = width - 4f * d
        for (i in 1 until 12) {
            val x = gridLeft + (gridRight - gridLeft) * i / 12f
            c.drawLine(x, gridTop, x, gridBottom, p)
        }
        for (i in 1 until 8) {
            val y = gridTop + (gridBottom - gridTop) * i / 8f
            c.drawLine(gridLeft, y, gridRight, y, p)
        }

        val cx = width / 2f
        p.strokeWidth = 1.2f * d
        p.color = Color.argb(115, 78, 209, 121)
        c.drawLine(cx, 0f, cx, height.toFloat(), p)
        c.drawCircle(cx, height * 0.66f, 13f * d, p)
        p.style = Paint.Style.FILL

        calibrationImagePoints.mapNotNull { mapRawToView(it, lastOverlay?.frameInfo) }.forEachIndexed { i, pt ->
            p.color = Color.rgb(255, 216, 67)
            c.drawCircle(pt.x, pt.y, 7f * d, p)
            p.color = Color.rgb(12, 17, 18)
            p.textSize = 7.5f * d
            p.typeface = Typeface.DEFAULT_BOLD
            c.drawText("${i + 1}", pt.x - 2.5f * d, pt.y + 2.8f * d, p)
        }
        p.typeface = Typeface.DEFAULT

        lastOverlay?.let { ov ->
            ov.ballImage?.let { raw ->
                mapRawToView(raw, ov.frameInfo)?.let { pt ->
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2.5f * d
                    p.color = Color.WHITE
                    c.drawCircle(pt.x, pt.y, 10f * d, p)
                    p.style = Paint.Style.FILL
                }
            }

            val heel = ov.heelImage?.let { mapRawToView(it, ov.frameInfo) }
            val toe = ov.toeImage?.let { mapRawToView(it, ov.frameInfo) }
            if (heel != null) {
                p.color = Color.rgb(255, 145, 40)
                c.drawCircle(heel.x, heel.y, 7f * d, p)
            }
            if (toe != null) {
                p.color = Color.rgb(63, 167, 255)
                c.drawCircle(toe.x, toe.y, 7f * d, p)
            }
            if (heel != null && toe != null) {
                p.strokeWidth = 2f * d
                p.color = Color.WHITE
                c.drawLine(heel.x, heel.y, toe.x, toe.y, p)
            }
        }
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
            90 -> {
                rx = ih - raw.y
                ry = raw.x
                rw = ih
                rh = iw
            }
            180 -> {
                rx = iw - raw.x
                ry = ih - raw.y
                rw = iw
                rh = ih
            }
            270 -> {
                rx = raw.y
                ry = iw - raw.x
                rw = ih
                rh = iw
            }
            else -> {
                rx = raw.x
                ry = raw.y
                rw = iw
                rh = ih
            }
        }

        val scale = min(width / rw, height / rh)
        val dx = (width - rw * scale) / 2f
        val dy = (height - rh * scale) / 2f
        return PointF(dx + rx * scale, dy + ry * scale)
    }
}
