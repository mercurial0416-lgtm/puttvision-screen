package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.max

class V31TrainingTvOverlay(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val card = RectF()

    init { setWillNotDraw(false) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = V31TrainingSessionRuntime.progress()
        val refreshMs = V41TrainingTvPolicy.refreshDelayMs(s)
        if (!s.running && !s.finished) { postInvalidateDelayed(refreshMs); return }

        val layout = V41TrainingTvPolicy.layout(width, height)
        val scale=layout.scale;val left=layout.left;val top=layout.top;val cardWidth=layout.width;val cardHeight=layout.height
        card.set(left,top,left+cardWidth,top+cardHeight)
        p.style=Paint.Style.FILL
        p.shader=android.graphics.LinearGradient(left,top,left+cardWidth,top+cardHeight,intArrayOf(Color.argb(232,5,9,12),Color.argb(226,12,23,20)),null,android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRoundRect(card,18f*scale,18f*scale,p);p.shader=null
        p.style=Paint.Style.STROKE;p.strokeWidth=max(1f,1.25f*scale);p.color=Color.argb(125,92,255,190);canvas.drawRoundRect(card,18f*scale,18f*scale,p);p.style=Paint.Style.FILL

        val x=left+16f*scale
        p.typeface=Typeface.DEFAULT_BOLD;p.textSize=12.5f*scale;p.color=Pv.primary
        val header=when{s.finished->"15 MIN TRAINING · COMPLETE";s.paused->"15 MIN TRAINING · PAUSED";else->"15 MIN TRAINING · LIVE"}
        canvas.drawText(header,x,top+25f*scale,p)

        p.textSize=16f*scale;p.color=Color.WHITE;canvas.drawText(if(s.finished)s.summary else s.blockTitle,x,top+49f*scale,p)
        p.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);p.textSize=10.2f*scale;p.color=Color.rgb(205,214,220)
        val blockNo=when{s.finished&&s.blockCount>0->"BLOCK ${s.blockCount}/${s.blockCount}";s.blockCount>0->"BLOCK ${(s.blockIndex+1).coerceAtMost(s.blockCount)}/${s.blockCount}";else->"BLOCK --"}
        val shotNo=when{s.finished&&s.shotsInBlock>0->"SHOT ${s.shotsInBlock}/${s.shotsInBlock}";s.shotsInBlock>0->"SHOT ${s.shotInBlock.coerceIn(0,s.shotsInBlock)}/${s.shotsInBlock}";else->"SHOT --"}
        canvas.drawText("$blockNo   $shotNo   STREAK ${s.streak}",x,top+70f*scale,p)

        p.textSize=9.5f*scale;p.color=Pv.amber
        val etaText=if(s.estimatedRemainingMinutes>0)"ETA ${s.estimatedRemainingMinutes}m" else if(s.finished)"DONE" else "ETA --"
        canvas.drawText("PROGRESS ${s.completionPct}%   BLOCK HIT ${s.blockSuccessPct}%   $etaText",x,top+90f*scale,p)

        val barLeft=x;val barTop=top+101f*scale;val barWidth=(cardWidth-32f*scale).coerceAtLeast(20f*scale);val barHeight=7f*scale
        p.color=Color.argb(120,90,100,108);canvas.drawRoundRect(barLeft,barTop,barLeft+barWidth,barTop+barHeight,4f*scale,4f*scale,p)
        val fraction=if(s.finished)1f else V51VisualPolicy.progress(s.completionPct);p.color=Pv.primary;canvas.drawRoundRect(barLeft,barTop,barLeft+barWidth*fraction,barTop+barHeight,4f*scale,4f*scale,p)

        // Improvement 26: radial total-progress gauge + segmented block timeline gives the session shape at a glance.
        val ringCx=left+cardWidth-37f*scale;val ringCy=top+31f*scale;val rr=16f*scale
        p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeWidth=4f*scale;p.color=Color.argb(120,78,90,99);canvas.drawCircle(ringCx,ringCy,rr,p)
        p.color=Pv.primary;canvas.drawArc(RectF(ringCx-rr,ringCy-rr,ringCx+rr,ringCy+rr),-90f,360f*fraction,false,p);p.style=Paint.Style.FILL;p.strokeCap=Paint.Cap.BUTT
        p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);p.textSize=8.5f*scale;p.color=Pv.textHi;canvas.drawText("${s.completionPct}%",ringCx,ringCy+3f*scale,p);p.textAlign=Paint.Align.LEFT

        val insight=V49LiveSessionInsights.snapshot();val confidenceWarn=insight.confidenceDeltaPct?.let{it<=-8.0}==true;val hfr=V43HfrHealthWindow.summary();var warningY=top+126f*scale
        if(confidenceWarn){p.textSize=9.5f*scale;p.color=Color.rgb(255,145,90);canvas.drawText("MEASUREMENT QUALITY ↓  ${insight.confidenceLabel}",x,warningY,p);warningY+=15f*scale}
        if(hfr.degraded){p.textSize=9.5f*scale;p.color=Pv.danger;canvas.drawText("HFR SLOW · P95 ${hfr.p95TotalMs}ms · CAL95 ${hfr.p95CalibrationMs}ms",x,warningY,p)}
        else if(!confidenceWarn){p.textSize=9f*scale;p.color=Pv.textMid;canvas.drawText("SUCCESS ${s.totalSuccesses}/${s.totalShots} · TARGET ${"%.1f".format(s.targetDistanceM)}m",x,warningY,p)}

        val states=V51VisualPolicy.segmentStates(s.blockIndex,s.blockCount,s.finished)
        if(states.isNotEmpty()){
            val gap=5f*scale;val totalW=barWidth;val segW=((totalW-gap*(states.size-1))/states.size).coerceAtLeast(8f*scale);val y=top+151f*scale
            states.forEachIndexed{i,state->
                val l=barLeft+i*(segW+gap);p.color=when(state){-1->Pv.primary;0->Pv.amber;else->Color.rgb(48,58,67)}
                canvas.drawRoundRect(l,y,l+segW,y+6f*scale,3f*scale,3f*scale,p)
            }
        }
        postInvalidateDelayed(refreshMs)
    }
}
