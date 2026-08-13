package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

enum class V26GreenVisualMode(val label: String) { OFF("OFF"), CONTOURS("등고선"), SLOPE("경사 %"), BOTH("등고선 + 경사 %") }
object V26GreenVisualRuntime {
    private const val PREF = "puttvision_v26_green_visuals"; @Volatile var mode = V26GreenVisualMode.BOTH; private set; @Volatile var swingGuide = true; private set; @Volatile private var installed = false
    fun install(context: Context) { if (installed) return; synchronized(this) { if (installed) return; val p=context.applicationContext.getSharedPreferences(PREF,Context.MODE_PRIVATE); mode=runCatching{V26GreenVisualMode.valueOf(p.getString("mode",V26GreenVisualMode.BOTH.name)!!)}.getOrDefault(V26GreenVisualMode.BOTH); swingGuide=p.getBoolean("swing",true); installed=true } }
    fun setMode(context: Context,value:V26GreenVisualMode){install(context);mode=value;context.applicationContext.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("mode",value.name).apply()}
    fun setSwingGuide(context:Context,enabled:Boolean){install(context);swingGuide=enabled;context.applicationContext.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean("swing",enabled).apply()}
    fun label()="${mode.label} · 속도가이드 ${if(swingGuide)"ON" else "OFF"}"
}
data class V26ContourPoint(val x:Double,val y:Double,val z:Double)
data class V26ContourSegment(val a:V26ContourPoint,val b:V26ContourPoint)
object V26ContourEngine {
    fun build(settings:GreenSettings,cols:Int=12,rows:Int=22,levelCount:Int=5):List<V26ContourSegment>{
        val half=max(1.35,settings.holeDistanceM*.20);val maxY=max(3.0,settings.holeDistanceM*1.08);val xs=(0..cols).map{-half+2.0*half*it/cols};val ys=(0..rows).map{maxY*it/rows};val z=Array(rows+1){r->DoubleArray(cols+1){c->GreenTerrain.effectiveHeightAt(settings,xs[c],ys[r])}};val minZ=z.minOf{it.minOrNull()?:0.0};val maxZ=z.maxOf{it.maxOrNull()?:0.0};if(maxZ-minZ<.006)return emptyList();val levels=(1..levelCount).map{minZ+(maxZ-minZ)*it/(levelCount+1.0)};val out=ArrayList<V26ContourSegment>()
        fun edge(x1:Double,y1:Double,z1:Double,x2:Double,y2:Double,z2:Double,l:Double):V26ContourPoint?{val a=z1-l;val b=z2-l;if(a==0.0&&b==0.0)return null;if(a*b>0.0||abs(z2-z1)<1e-9)return null;val t=((l-z1)/(z2-z1)).coerceIn(0.0,1.0);return V26ContourPoint(x1+(x2-x1)*t,y1+(y2-y1)*t,l)}
        for(l in levels)for(r in 0 until rows)for(c in 0 until cols){val pts=arrayListOf<V26ContourPoint>();edge(xs[c],ys[r],z[r][c],xs[c+1],ys[r],z[r][c+1],l)?.let(pts::add);edge(xs[c+1],ys[r],z[r][c+1],xs[c+1],ys[r+1],z[r+1][c+1],l)?.let(pts::add);edge(xs[c+1],ys[r+1],z[r+1][c+1],xs[c],ys[r+1],z[r+1][c],l)?.let(pts::add);edge(xs[c],ys[r+1],z[r+1][c],xs[c],ys[r],z[r][c],l)?.let(pts::add);if(pts.size>=2){out+=V26ContourSegment(pts[0],pts[1]);if(pts.size>=4)out+=V26ContourSegment(pts[2],pts[3])}}
        return out
    }
}
class V26GreenInsightOverlay(context:Context,private val engine:GameEngine):View(context){
    private val p=Paint(Paint.ANTI_ALIAS_FLAG);private var cacheKey="";private var contours:List<V26ContourSegment> = emptyList();init{isClickable=false;isFocusable=false;setWillNotDraw(false)}
    override fun onDraw(c:Canvas){super.onDraw(c);V26GreenVisualRuntime.install(context);val moving=engine.state?.running==true||TvInstantRollRuntime.isAnimating();if(!moving&&engine.lastResult==null){if(V26GreenVisualRuntime.mode==V26GreenVisualMode.CONTOURS||V26GreenVisualRuntime.mode==V26GreenVisualMode.BOTH)drawContours(c);if(V26GreenVisualRuntime.mode==V26GreenVisualMode.SLOPE||V26GreenVisualRuntime.mode==V26GreenVisualMode.BOTH)drawSlope(c);if(V26GreenVisualRuntime.swingGuide)drawSpeed(c)};postInvalidateDelayed(if(moving)90L else 55L)}
    private fun drawContours(c:Canvas){val s=engine.settings;val key="${s.terrainProfileId}:${(s.holeDistanceM*100).toInt()}:${(s.sideSlopePct*100).toInt()}:${(s.longSlopePct*100).toInt()}:${V28CustomGreenCodec.signature(V22CustomGreenRuntime.profile)}";if(key!=cacheKey){cacheKey=key;contours=V26ContourEngine.build(s)};p.style=Paint.Style.STROKE;p.strokeWidth=max(1.2f,width*.0009f);p.color=Color.argb(92,255,255,244);contours.forEach{seg->val a=V25FlagProjectionRuntime.project(seg.a.x,seg.a.y,seg.a.z+.014)?:return@forEach;val b=V25FlagProjectionRuntime.project(seg.b.x,seg.b.y,seg.b.z+.014)?:return@forEach;c.drawLine(a.x,a.y,b.x,b.y,p)};p.style=Paint.Style.FILL}
    private fun drawSlope(c:Canvas){val s=engine.settings;val half=max(1.0,s.holeDistanceM*.16);p.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);p.textSize=max(7f,width*.0045f);for(row in 1..5)for(lane in -2..2){val x=half*lane/2.25;val y=s.holeDistanceM*row/6.0;val slope=GreenTerrain.effectiveSlopeAt(s,x,y);val mag=hypot(slope.sidePct,slope.longPct);if(mag<.20)continue;val z=GreenTerrain.effectiveHeightAt(s,x,y)+.025;val pt=V25FlagProjectionRuntime.project(x,y,z)?:continue;val ux=slope.sidePct/mag;val uy=slope.longPct/mag;val end=V25FlagProjectionRuntime.project(x+ux*.18,y+uy*.18,z)?:continue;p.color=when{mag>=3.0->Color.argb(215,255,192,82);mag>=1.5->Color.argb(200,226,244,122);else->Color.argb(185,235,244,235)};p.style=Paint.Style.STROKE;p.strokeWidth=max(1.5f,width*.0011f);c.drawLine(pt.x,pt.y,end.x,end.y,p);p.style=Paint.Style.FILL;c.drawText("%.1f%%".format(mag),pt.x+width*.004f,pt.y-height*.006f,p)}}
    private fun drawSpeed(c:Canvas){val read=GreenReadRuntime.peekOrSchedule(engine.settings)?:return;if(!read.solverReliable)return;val start=V26BallStartRuntime.current(engine.settings);val z=GreenTerrain.effectiveHeightAt(engine.settings,start.first,start.second)+.055;val pt=V25FlagProjectionRuntime.project(start.first,start.second,z)?:return;val pulse=.5f+.5f*kotlin.math.sin((SystemClock.uptimeMillis()%1400L)/1400.0*Math.PI*2.0).toFloat();p.style=Paint.Style.STROKE;p.strokeWidth=max(2f,width*.0013f);p.color=Color.argb((90+pulse*90).toInt(),255,205,72);c.drawCircle(pt.x,pt.y,width*(.009f+pulse*.004f),p);p.style=Paint.Style.FILL;val bw=width*.152f;val bh=height*.038f;val left=(pt.x-bw*.5f).coerceIn(width*.01f,width-bw-width*.01f);val top=(pt.y+height*.032f).coerceIn(height*.10f,height-bh-height*.06f);p.color=Color.argb(170,13,18,17);c.drawRoundRect(RectF(left,top,left+bw,top+bh),bh*.4f,bh*.4f,p);p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.DEFAULT_BOLD;p.textSize=max(7.5f,width*.0050f);p.color=Color.WHITE;c.drawText("권장 %.2f m/s · 컵 %.2f m/s".format(read.recommendedBallSpeedMps,V27CupPaceRuntime.targetCupSpeedMps),left+bw*.5f,top+bh*.66f,p);p.textAlign=Paint.Align.LEFT}
}
fun showV26GreenVisualDialog(context:Context){V26GreenVisualRuntime.install(context);val modes=V26GreenVisualMode.entries;val selected=modes.indexOf(V26GreenVisualRuntime.mode).coerceAtLeast(0);AlertDialog.Builder(context).setTitle("GREEN VISUALS").setSingleChoiceItems(modes.map{it.label}.toTypedArray(),selected){d,w->V26GreenVisualRuntime.setMode(context,modes[w]);d.dismiss()}.setMultiChoiceItems(arrayOf("추천 속도 스윙가이드"),booleanArrayOf(V26GreenVisualRuntime.swingGuide)){_,_,checked->V26GreenVisualRuntime.setSwingGuide(context,checked)}.setNegativeButton("닫기",null).show()}
