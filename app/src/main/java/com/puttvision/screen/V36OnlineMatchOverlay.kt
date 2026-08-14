package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.max

/** Compact broadcast-style ONLINE LEAGUE state card for the external TV. */
class V36OnlineMatchOverlay(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val card = RectF()

    init { setWillNotDraw(false) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val state = V36OnlinePresenceRuntime.snapshot() ?: return
        val me = state.me()
        val opponent = state.opponent()

        val margin = max(20f, width * .018f)
        val cardWidth = width * .34f
        val cardHeight = if (state.finished()) height * .155f else height * .125f
        card.set(width - margin - cardWidth, margin, width - margin, margin + cardHeight)

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(214, 8, 12, 18)
        canvas.drawRoundRect(card, 20f, 20f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, width * .0012f)
        paint.color = if (state.finished()) Color.rgb(255, 202, 61) else Color.rgb(73, 211, 255)
        canvas.drawRoundRect(card, 20f, 20f, paint)
        paint.style = Paint.Style.FILL

        val left = card.left + cardWidth * .055f
        val top = card.top + cardHeight * .25f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = max(15f, width * .011f)
        paint.color = Color.rgb(225, 235, 244)

        if (state.finished()) {
            val delta = me?.ratingDelta
            val result = when {
                me?.forfeited == true -> "LOSS · FORFEIT"
                opponent?.forfeited == true -> "WIN · FORFEIT"
                delta != null && delta > 0 -> "WIN"
                delta != null && delta < 0 -> "LOSS"
                else -> "DRAW / FINISHED"
            }
            canvas.drawText("ONLINE · $result", left, top, paint)
            paint.textSize = max(13f, width * .009f)
            paint.color = Color.rgb(255, 220, 118)
            val rating = if (me?.ratingBefore != null && me.ratingAfter != null) {
                val d = me.ratingDelta ?: 0
                "R${me.ratingBefore} → R${me.ratingAfter} (${if (d >= 0) "+" else ""}$d)"
            } else "서버 정산 완료"
            canvas.drawText(rating, left, top + cardHeight * .34f, paint)
        } else {
            val live = opponent?.online == true
            val name = opponent?.name ?: "OPPONENT"
            val shot = opponent?.shotNo ?: 0
            val distance = opponent?.remainingM?.let { " · %.2f m".format(it) }.orEmpty()
            canvas.drawText("ONLINE · $name", left, top, paint)
            paint.textSize = max(13f, width * .009f)
            paint.color = if (live) Color.rgb(119, 239, 164) else Color.rgb(255, 184, 84)
            canvas.drawText("${if (live) "LIVE" else "RECONNECTING"} · $shot/9$distance", left, top + cardHeight * .38f, paint)
        }

        postInvalidateDelayed(if (state.finished()) 600L else 250L)
    }
}
