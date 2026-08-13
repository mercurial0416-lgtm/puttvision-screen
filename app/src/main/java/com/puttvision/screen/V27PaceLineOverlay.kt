package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.max

/** Lightweight TV overlay for pace-aware ideal line plus executable training status. */
class V27PaceLineOverlay(context: Context, private val engine: GameEngine) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init { setWillNotDraw(false) }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val moving = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
        drawPaceLine(c, moving)
        drawTraining(c)
        val training = V31TrainingSessionRuntime.progress().running
        postInvalidateDelayed(when {
            moving -> 90L
            training -> 180L
            else -> 120L
        })
    }

    private fun drawPaceLine(c: Canvas, moving: Boolean) {
        val read = if (!moving && engine.lastResult == null) GreenReadRuntime.peekOrSchedule(engine.settings) else null
        if (read == null || !read.solverReliable) return
        val pts = read.predictedTrail.mapNotNull { (x, y) ->
            V25FlagProjectionRuntime.project(
                x,
                y,
                GreenTerrain.effectiveHeightAt(engine.settings, x, y) + .018
            )
        }
        if (pts.size > 1) {
            val ideal = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(3f, width * .0017f)
            p.strokeCap = Paint.Cap.ROUND
            p.color = Color.rgb(255, 202, 61)
            c.drawPath(ideal, p)
            p.style = Paint.Style.FILL
        }
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(10f, width * .006f)
        p.color = Color.rgb(255, 222, 108)
        c.drawText(
            "홀 통과 %.2f m/s · 볼 %.2f m/s".format(
                V27CupPaceRuntime.targetCupSpeedMps,
                read.recommendedBallSpeedMps
            ),
            width * .5f,
            height * .13f,
            p
        )
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawTraining(c: Canvas) {
        val s = V31TrainingSessionRuntime.progress()
        if (!s.running && !s.finished) return
        val w = width.toFloat()
        val h = height.toFloat()
        val bw = w * .245f
        val bh = h * .092f
        val left = w * .018f
        val top = h * .105f
        p.style = Paint.Style.FILL
        p.color = Color.argb(190, 9, 14, 16)
        c.drawRoundRect(RectF(left, top, left + bw, top + bh), bh * .18f, bh * .18f, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.LEFT
        p.textSize = max(8f, w * .0053f)
        p.color = Color.rgb(255, 211, 72)
        c.drawText(if (s.running) "15분 AI 훈련" else "15분 AI 훈련 완료", left + bw * .055f, top + bh * .29f, p)

        p.textSize = max(7.4f, w * .0046f)
        p.color = Color.WHITE
        val line = if (s.running) {
            "${s.blockIndex + 1}/${s.blockCount} · ${s.blockTitle} · ${s.shotInBlock}/${s.shotsInBlock}구 · %.2f m".format(s.targetDistanceM)
        } else s.summary
        c.drawText(line, left + bw * .055f, top + bh * .59f, p)

        p.textSize = max(7f, w * .0042f)
        p.color = Color.argb(205, 210, 224, 220)
        c.drawText("성공 ${s.totalSuccesses}/${s.totalShots} · 연속 ${s.streak}", left + bw * .055f, top + bh * .83f, p)
    }
}
