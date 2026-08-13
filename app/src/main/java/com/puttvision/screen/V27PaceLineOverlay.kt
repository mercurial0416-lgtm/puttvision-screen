package com.puttvision.screen

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.max

class V27PaceLineOverlay(context: Context, private val engine: GameEngine) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init { setWillNotDraw(false) }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val moving = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
        val read = if (!moving && engine.lastResult == null) GreenReadRuntime.peekOrSchedule(engine.settings) else null
        if (read != null && read.solverReliable) {
            val pts = read.predictedTrail.mapNotNull { (x,y) -> V25FlagProjectionRuntime.project(x,y,GreenTerrain.effectiveHeightAt(engine.settings,x,y)+.018) }
            if (pts.size > 1) {
                val ideal=Path().apply{moveTo(pts[0].x,pts[0].y);pts.drop(1).forEach{lineTo(it.x,it.y)}}
                p.style=Paint.Style.STROKE;p.strokeWidth=max(3f,width*.0017f);p.strokeCap=Paint.Cap.ROUND;p.color=Color.rgb(255,202,61);c.drawPath(ideal,p);p.style=Paint.Style.FILL
            }
            p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.DEFAULT_BOLD;p.textSize=max(10f,width*.006f);p.color=Color.rgb(255,222,108)
            c.drawText("홀 통과 %.2f m/s · 볼 %.2f m/s".format(V27CupPaceRuntime.targetCupSpeedMps,read.recommendedBallSpeedMps),width*.5f,height*.13f,p)
            p.textAlign=Paint.Align.LEFT
        }
        postInvalidateDelayed(if(moving)90L else 120L)
    }
}
