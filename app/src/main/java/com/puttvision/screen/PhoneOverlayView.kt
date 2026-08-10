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

        p.color = Color.argb(195, 4, 10, 7)
        c.drawRoundRect(18f, 18f, width - 18f, 92f, 18f, 18f, p)
        p.color = Color.WHITE
        p.textSize = 29f
        c.drawText(status, 38f, 64f, p)

        calibrationImagePoints.mapNotNull { mapRawToView(it, lastOverlay?.frameInfo) }.forEachIndexed { i, pt ->
            p.color = Color.rgb(255, 210, 65)
            c.drawCircle(pt.x, pt.y, 12f, p)
            p.color = Color.WHITE
            p.textSize = 24f
            c.drawText("${i + 1}", pt.x + 15f, pt.y - 9f, p)
        }

        lastOverlay?.let { ov ->
            ov.ballImage?.let { raw ->
                mapRawToView(raw, ov.frameInfo)?.let { pt ->
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 5f
                    p.color = Color.MAGENTA
                    c.drawCircle(pt.x, pt.y, 22f, p)
                    p.style = Paint.Style.FILL
                }
            }

            val heel = ov.heelImage?.let { mapRawToView(it, ov.frameInfo) }
            val toe = ov.toeImage?.let { mapRawToView(it, ov.frameInfo) }
            if (heel != null) {
                p.color = Color.rgb(255, 145, 40)
                c.drawCircle(heel.x, heel.y, 16f, p)
            }
            if (toe != null) {
                p.color = Color.rgb(50, 140, 255)
                c.drawCircle(toe.x, toe.y, 16f, p)
            }
            if (heel != null && toe != null) {
                p.strokeWidth = 5f
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
