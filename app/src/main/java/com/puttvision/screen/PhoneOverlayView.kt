package com.puttvision.screen

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class PhoneOverlayView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init { isClickable = false; isFocusable = false }

    var status: String = "자동 캘리브레이션 대기"
    var calibrationImagePoints: List<PointF> = emptyList()
    var lastOverlay: VisionOverlay? = null

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val d = resources.displayMetrics.density
        drawSafeFrame(c, d)          // 11
        drawStrikeZone(c, d)         // 12
        drawArGreenRead(c, d)        // 16
        drawCameraDock(c, d)         // 17
        drawTracking(c, d)           // 13-15
        drawCalibrationBadge(c, d)
        drawStatus(c, d)             // 18
    }

    /** Improvement 11: camera safe-area brackets replace the noisy full-screen graph-paper grid. */
    private fun drawSafeFrame(c: Canvas, d: Float) {
        val insetX = width * .055f; val insetY = height * .075f
        val arm = minOf(width, height) * .055f
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.SQUARE; p.strokeWidth = 1.4f * d
        p.color = Color.argb(105, 78, 209, 121)
        fun corner(x: Float, y: Float, sx: Float, sy: Float) {
            c.drawLine(x, y, x + arm * sx, y, p); c.drawLine(x, y, x, y + arm * sy, p)
        }
        corner(insetX, insetY, 1f, 1f); corner(width-insetX, insetY, -1f, 1f)
        corner(insetX, height-insetY, 1f, -1f); corner(width-insetX, height-insetY, -1f, -1f)
        p.strokeWidth = .65f * d; p.color = Color.argb(42, 78, 209, 121)
        c.drawLine(width*.5f, insetY, width*.5f, height-insetY, p)
        c.drawLine(insetX, height*.66f, width-insetX, height*.66f, p)
        p.style = Paint.Style.FILL; p.strokeCap = Paint.Cap.BUTT
    }

    /** Improvement 12: a strike-zone reticle makes ball placement obvious before calibration/tracking locks. */
    private fun drawStrikeZone(c: Canvas, d: Float) {
        val cx=width*.5f; val cy=height*.66f; val r=16f*d
        p.style=Paint.Style.STROKE;p.strokeWidth=1.2f*d;p.color=Color.argb(115,236,241,247)
        c.drawCircle(cx,cy,r,p)
        p.color=Color.argb(62,236,241,247);c.drawCircle(cx,cy,r*1.75f,p)
        c.drawLine(cx-r*1.3f,cy,cx-r*.45f,cy,p);c.drawLine(cx+r*.45f,cy,cx+r*1.3f,cy,p)
        c.drawLine(cx,cy-r*1.3f,cx,cy-r*.45f,p);c.drawLine(cx,cy+r*.45f,cx,cy+r*1.3f,p)
        p.style=Paint.Style.FILL;p.typeface=Typeface.DEFAULT_BOLD;p.textSize=5.8f*d;p.color=Color.argb(130,236,241,247);p.textAlign=Paint.Align.CENTER
        c.drawText("BALL ZONE",cx,cy+r*2.35f,p);p.textAlign=Paint.Align.LEFT
    }

    /**
     * AR green read: keep the shared solver path intact, but make the advice actionable at address.
     * The phone now exposes side/cup count, pace and the visual break apex instead of speed alone.
     */
    private fun drawArGreenRead(c: Canvas, d: Float) {
        val frame = lastOverlay?.frameInfo ?: return
        if (calibrationImagePoints.size != 4) return
        val settings = V26ProductSettingsRuntime.settings
        val snap = V33ArGreenReadRuntime.snapshot(settings, calibrationImagePoints, frame) ?: return
        val read = GreenReadRuntime.peekOrSchedule(settings)
        val points = snap.imagePoints.mapNotNull { mapRawToView(it, frame) }
        if (points.size < 2) return
        val path=Path().apply{moveTo(points.first().x,points.first().y);for(i in 1 until points.size)lineTo(points[i].x,points[i].y)}
        p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND
        p.color=Color.argb(100,0,0,0);p.strokeWidth=10f*d;c.drawPath(path,p)
        p.color=Color.argb(85,255,211,64);p.strokeWidth=6.4f*d;c.drawPath(path,p)
        p.color=Color.rgb(255,211,64);p.strokeWidth=2.7f*d;c.drawPath(path,p)
        p.style=Paint.Style.FILL
        points.forEachIndexed{i,pt->if(i>0&&i<points.lastIndex&&i%max(1,points.size/7)==0){p.color=Color.argb(225,255,229,125);c.drawCircle(pt.x,pt.y,2.6f*d,p)}}
        val start=points.first();val cup=points.last()
        p.color=Color.argb(220,6,10,13);c.drawCircle(start.x,start.y,8f*d,p);p.color=Color.rgb(255,211,64);c.drawCircle(start.x,start.y,4.2f*d,p)
        p.style=Paint.Style.STROKE;p.strokeWidth=2.2f*d;p.color=Color.WHITE;c.drawCircle(cup.x,cup.y,9f*d,p);p.style=Paint.Style.FILL;p.color=Pv.amber;c.drawCircle(cup.x,cup.y,3f*d,p)

        arBreakApex(points)?.let { apex ->
            val rr = 5.2f*d
            p.style=Paint.Style.FILL
            p.color=Color.argb(225,6,10,13)
            c.drawCircle(apex.x,apex.y,rr+3f*d,p)
            p.style=Paint.Style.STROKE;p.strokeWidth=1.8f*d;p.color=Pv.amber
            c.drawPath(Path().apply {
                moveTo(apex.x, apex.y-rr)
                lineTo(apex.x+rr, apex.y)
                lineTo(apex.x, apex.y+rr)
                lineTo(apex.x-rr, apex.y)
                close()
            },p)
            p.style=Paint.Style.FILL;p.textSize=5.4f*d;p.typeface=Typeface.DEFAULT_BOLD;p.color=Pv.amber
            c.drawText("APEX",apex.x+8f*d,apex.y-7f*d,p)
        }

        val cupAdvice = when {
            read == null -> "라인 계산 중"
            read.cupCount < .08 -> "센터"
            else -> "${read.aimSideLabel.removePrefix("홀 ")} ${"%.1f".format(read.cupCount)}컵"
        }
        val title = "AR READ  ·  $cupAdvice"
        val pace = read?.paceHint?.take(18) ?: "기준 페이스"
        val subtitle = "BALL %.2f → CUP %.2f m/s  ·  %s".format(snap.ballSpeedMps,snap.cupSpeedMps,pace)
        p.typeface=Typeface.DEFAULT_BOLD;p.textSize=7f*d
        val textW=maxOf(p.measureText(title),p.measureText(subtitle))
        val left=((width-textW)/2f-10f*d).coerceAtLeast(138f*d);val right=(left+textW+20f*d).coerceAtMost(width-10f*d)
        if(right>left){
            val top=10f*d
            p.color=Color.argb(220,6,10,13);c.drawRoundRect(RectF(left,top,right,top+36f*d),12f*d,12f*d,p)
            p.color=Pv.amber;p.textSize=7f*d;c.drawText(title,left+10f*d,top+14f*d,p)
            p.color=Pv.textMid;p.textSize=5.8f*d;c.drawText(subtitle,left+10f*d,top+28f*d,p)
        }
    }

    private fun arBreakApex(points: List<PointF>): PointF? {
        if (points.size < 3) return null
        val start = points.first(); val end = points.last()
        val dx = end.x - start.x; val dy = end.y - start.y
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (!length.isFinite() || length < 1f) return null
        var best: PointF? = null
        var bestDistance = 0f
        for (i in 1 until points.lastIndex) {
            val point = points[i]
            val distance = abs(dy * point.x - dx * point.y + end.x * start.y - end.y * start.x) / length
            if (distance > bestDistance) { bestDistance = distance; best = point }
        }
        return best?.takeIf { bestDistance >= 2f }
    }

    /** Improvement 17: one compact instrument dock replaces static 240fps/FHD claims with live frame and mode state. */
    private fun drawCameraDock(c: Canvas, d: Float) {
        val left=10f*d;val top=9f*d;val ww=124f*d;val hh=52f*d
        p.shader=LinearGradient(left,top,left+ww,top+hh,intArrayOf(Color.argb(235,6,10,13),Color.argb(220,12,21,20)),null,Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(left,top,left+ww,top+hh),12f*d,12f*d,p);p.shader=null
        p.color=Pv.primary;c.drawRoundRect(RectF(left,top,left+3f*d,top+hh),2f*d,2f*d,p)
        p.typeface=Typeface.DEFAULT_BOLD;p.textSize=9.4f*d;p.color=Pv.textHi;c.drawText("HFR CAMERA",left+10f*d,top+16f*d,p)
        val frame=lastOverlay?.frameInfo
        p.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);p.textSize=6.8f*d;p.color=Pv.textMid
        val frameLabel=frame?.let{"${it.width}×${it.height} · R${it.rotationDegrees}"}?:"FRAME WAIT"
        c.drawText(frameLabel,left+10f*d,top+30f*d,p)
        p.typeface=Typeface.DEFAULT_BOLD;p.textSize=6.2f*d;p.color=if(calibrationImagePoints.size>=4)Pv.primary else Pv.amber
        c.drawText(if(calibrationImagePoints.size>=4)"● AUTO TRACK LOCK" else "● AUTO CAL",left+10f*d,top+44f*d,p)
    }

    private fun drawCalibrationBadge(c:Canvas,d:Float){
        val calibrated=calibrationImagePoints.size>=4;val right=width-10f*d;val top=9f*d;val ww=if(calibrated)92f*d else 106f*d
        p.color=Color.argb(210,6,10,13);c.drawRoundRect(RectF(right-ww,top,right,top+25f*d),12f*d,12f*d,p)
        p.color=if(calibrated)Pv.primary else Pv.amber;c.drawCircle(right-ww+12f*d,top+12.5f*d,3.6f*d,p)
        p.textSize=7.6f*d;p.typeface=Typeface.DEFAULT_BOLD;c.drawText(if(calibrated)"CALIBRATED" else "CALIBRATING",right-ww+21f*d,top+16f*d,p)
    }

    /** Improvements 13–15: marker map, ball lock and putter-face direction become distinct tracking layers. */
    private fun drawTracking(c:Canvas,d:Float){
        val mappedMarkers=calibrationImagePoints.mapNotNull{mapRawToView(it,lastOverlay?.frameInfo)}
        if(mappedMarkers.size==4){
            p.style=Paint.Style.STROKE;p.strokeWidth=1f*d;p.color=Color.argb(90,255,214,58)
            val quad=Path().apply{moveTo(mappedMarkers[0].x,mappedMarkers[0].y);lineTo(mappedMarkers[1].x,mappedMarkers[1].y);lineTo(mappedMarkers[3].x,mappedMarkers[3].y);lineTo(mappedMarkers[2].x,mappedMarkers[2].y);close()};c.drawPath(quad,p);p.style=Paint.Style.FILL
        }
        mappedMarkers.forEachIndexed{i,pt->
            p.color=Color.argb(210,7,10,12);c.drawCircle(pt.x,pt.y,9f*d,p);p.style=Paint.Style.STROKE;p.strokeWidth=1.4f*d;p.color=Pv.amber;c.drawCircle(pt.x,pt.y,7f*d,p);p.style=Paint.Style.FILL
            p.color=Pv.amber;p.typeface=Typeface.DEFAULT_BOLD;p.textSize=6.4f*d;p.textAlign=Paint.Align.CENTER;c.drawText((i+1).toString(),pt.x,pt.y+2.2f*d,p);p.textAlign=Paint.Align.LEFT
        }
        lastOverlay?.let{ov->
            ov.ballImage?.let{raw->mapRawToView(raw,ov.frameInfo)?.let{pt->
                // Improvement 14: lock ring + crosshair around detected ball.
                p.style=Paint.Style.STROKE;p.strokeWidth=1.2f*d;p.color=Color.argb(130,255,255,255);c.drawCircle(pt.x,pt.y,15f*d,p)
                p.strokeWidth=2.3f*d;p.color=Color.WHITE;c.drawCircle(pt.x,pt.y,9f*d,p)
                c.drawLine(pt.x-16f*d,pt.y,pt.x-11f*d,pt.y,p);c.drawLine(pt.x+11f*d,pt.y,pt.x+16f*d,pt.y,p)
                c.drawLine(pt.x,pt.y-16f*d,pt.x,pt.y-11f*d,p);c.drawLine(pt.x,pt.y+11f*d,pt.x,pt.y+16f*d,p)
                p.style=Paint.Style.FILL;p.typeface=Typeface.DEFAULT_BOLD;p.textSize=5.6f*d;p.color=Pv.primary;c.drawText("BALL LOCK",pt.x+13f*d,pt.y-12f*d,p)
            }}
            val heel=ov.heelImage?.let{mapRawToView(it,ov.frameInfo)};val toe=ov.toeImage?.let{mapRawToView(it,ov.frameInfo)}
            if(heel!=null&&toe!=null){
                // Improvement 15: heel-toe bar plus perpendicular face-direction arrow.
                p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeWidth=5.2f*d;p.color=Color.argb(95,0,0,0);c.drawLine(heel.x,heel.y,toe.x,toe.y,p)
                p.strokeWidth=2.2f*d;p.color=Color.WHITE;c.drawLine(heel.x,heel.y,toe.x,toe.y,p);p.style=Paint.Style.FILL
                p.color=Color.rgb(255,145,40);c.drawCircle(heel.x,heel.y,5.4f*d,p);p.color=Pv.info;c.drawCircle(toe.x,toe.y,5.4f*d,p)
                val mx=(heel.x+toe.x)/2f;val my=(heel.y+toe.y)/2f;val dx=toe.x-heel.x;val dy=toe.y-heel.y;val len=hypot(dx.toDouble(),dy.toDouble()).toFloat().coerceAtLeast(1f);val nx=-dy/len;val ny=dx/len;val al=24f*d
                p.style=Paint.Style.STROKE;p.strokeWidth=1.8f*d;p.color=Pv.primary;c.drawLine(mx,my,mx+nx*al,my+ny*al,p)
                val ex=mx+nx*al;val ey=my+ny*al;p.style=Paint.Style.FILL;p.color=Pv.primary;c.drawPath(Path().apply{moveTo(ex,ey);lineTo(ex-nx*7f*d+ny*4f*d,ey-ny*7f*d-nx*4f*d);lineTo(ex-nx*7f*d-ny*4f*d,ey-ny*7f*d+nx*4f*d);close()},p)
            }
        }
        p.strokeCap=Paint.Cap.BUTT;p.style=Paint.Style.FILL
    }

    /** Improvement 18: bottom status uses semantic colour and a stronger alert banner when action is needed. */
    private fun drawStatus(c:Canvas,d:Float){
        val safe=if(status.length>46)status.take(45)+"…" else status;val tone=V51VisualPolicy.toneForStatus(status);val accent=v51ToneColor(tone)
        p.textSize=7.2f*d;p.typeface=Typeface.DEFAULT_BOLD;val textW=p.measureText(safe);val cx=width/2f;val bottom=height-10f*d
        val left=(cx-textW/2f-16f*d).coerceAtLeast(116f*d);val right=(cx+textW/2f+16f*d).coerceAtMost(width-10f*d);if(right<=left)return
        p.color=Color.argb(220,6,10,13);c.drawRoundRect(RectF(left,bottom-24f*d,right,bottom),12f*d,12f*d,p)
        p.color=accent;c.drawCircle(left+10f*d,bottom-12f*d,3.2f*d,p);p.color=Pv.textHi;c.drawText(safe,left+18f*d,bottom-8f*d,p)
    }

    private fun mapRawToView(raw:PointF,frame:FrameInfo?):PointF?{
        frame?:return null;val iw=frame.width.toFloat();val ih=frame.height.toFloat();val rx:Float;val ry:Float;val rw:Float;val rh:Float
        when((frame.rotationDegrees%360+360)%360){90->{rx=ih-raw.y;ry=raw.x;rw=ih;rh=iw};180->{rx=iw-raw.x;ry=ih-raw.y;rw=iw;rh=ih};270->{rx=raw.y;ry=iw-raw.x;rw=ih;rh=iw};else->{rx=raw.x;ry=raw.y;rw=iw;rh=ih}}
        if(width<=0||height<=0||rw<=0f||rh<=0f)return null;val scale=max(width/rw,height/rh);val dx=(width-rw*scale)/2f;val dy=(height-rh*scale)/2f;return PointF(dx+rx*scale,dy+ry*scale)
    }
}
