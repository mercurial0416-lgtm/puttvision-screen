package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

enum class V51Tone { GOOD, INFO, WARN, BAD, NEUTRAL }

object V51VisualPolicy {
    fun toneForScore(score: Int): V51Tone = when {
        score >= 85 -> V51Tone.GOOD
        score >= 68 -> V51Tone.INFO
        score >= 50 -> V51Tone.WARN
        else -> V51Tone.BAD
    }

    fun toneForStatus(raw: String): V51Tone {
        val s = raw.uppercase()
        return when {
            "CALIBRATED" in s -> V51Tone.GOOD
            listOf("BLOCK", "FAIL", "ERROR", "BAD", "REJECT", "SLOW", "LOW").any(s::contains) -> V51Tone.BAD
            listOf("WARN", "WAIT", "CALIBRATING", "PARTIAL", "STALE", "PREP").any(s::contains) -> V51Tone.WARN
            listOf("GOOD", "CLEAN", "READY", "LOCK", "CONNECTED", "COMPLETE").any(s::contains) -> V51Tone.GOOD
            listOf("SYNC", "DATA", "ACTIVE", "RUNNING").any(s::contains) -> V51Tone.INFO
            else -> V51Tone.NEUTRAL
        }
    }

    fun progress(value: Int): Float = value.coerceIn(0, 100) / 100f

    fun segmentStates(blockIndex: Int, blockCount: Int, finished: Boolean): List<Int> {
        if (blockCount <= 0) return emptyList()
        return List(blockCount) { i -> if (finished || i < blockIndex) -1 else if (i == blockIndex) 0 else 1 }
    }

    fun normalize(values: List<Double>): List<Float> {
        val finite = values.filter(Double::isFinite)
        if (finite.isEmpty()) return emptyList()
        val lo = finite.minOrNull() ?: return emptyList()
        val hi = finite.maxOrNull() ?: return emptyList()
        if (hi - lo <= 1e-9) return values.map { .5f }
        return values.map { v -> if (!v.isFinite()) .5f else ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) }
    }
}

fun v51ToneColor(tone: V51Tone): Int = when (tone) {
    V51Tone.GOOD -> Pv.primary
    V51Tone.INFO -> Pv.info
    V51Tone.WARN -> Pv.amber
    V51Tone.BAD -> Pv.danger
    V51Tone.NEUTRAL -> Pv.textMid
}

fun Context.v51ElevatedPanel(padDp: Int = 12): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = android.graphics.drawable.GradientDrawable(
        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(29, 37, 47), Color.rgb(17, 23, 30))
    ).apply {
        cornerRadius = pvDp(Pv.rLg).toFloat()
        setStroke(pvDp(1), Color.rgb(48, 60, 72))
    }
    elevation = pvDp(3).toFloat()
    setPadding(pvDp(padDp), pvDp(padDp), pvDp(padDp), pvDp(padDp))
}

fun Context.v51StatusPill(label: String, tone: V51Tone): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    val accent = v51ToneColor(tone)
    background = pvRounded(Color.argb(225, 15, 21, 27), 100f, Color.argb(150, Color.red(accent), Color.green(accent), Color.blue(accent)))
    setPadding(pvDp(9), pvDp(5), pvDp(10), pvDp(5))
    addView(View(this@v51StatusPill).apply { background = pvRounded(accent, 100f) }, LinearLayout.LayoutParams(pvDp(7), pvDp(7)).apply { marginEnd = pvDp(7) })
    addView(TextView(this@v51StatusPill).apply {
        text = label; textSize = pvSp(7.7f); setTextColor(Pv.textHi); typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false; maxLines = 1
    })
}

fun Context.v51Meter(label: String, value: Int, tone: V51Tone = V51VisualPolicy.toneForScore(value), detail: String? = null): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val header = LinearLayout(this@v51Meter).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this@v51Meter).apply {
            text = label; setTextColor(Pv.textMid); textSize = pvSp(7.5f); typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this@v51Meter).apply {
            text = "$value%"; setTextColor(v51ToneColor(tone)); textSize = pvSp(8.2f); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        })
        addView(header)
        val fraction = V51VisualPolicy.progress(value)
        val track = FrameLayout(this@v51Meter).apply {
            background = pvRounded(Color.rgb(31, 39, 48), 100f)
            val fill = View(this@v51Meter).apply { background = pvRounded(v51ToneColor(tone), 100f) }
            addView(fill, FrameLayout.LayoutParams(1, pvDp(5), Gravity.START or Gravity.CENTER_VERTICAL))
            post {
                fill.layoutParams = fill.layoutParams.apply {
                    width = (this@apply.width.takeIf { it > 0 } ?: this@v51Meter.pvDp(3))
                }
                fill.layoutParams = fill.layoutParams.apply {
                    width = (this@v51Meter.resources.displayMetrics.widthPixels * 0).coerceAtLeast(0)
                }
                val target = (this.width * fraction).toInt().coerceAtLeast(if (fraction > 0f) this@v51Meter.pvDp(3) else 0)
                fill.layoutParams = fill.layoutParams.apply { width = target }
            }
        }
        addView(track, LinearLayout.LayoutParams(-1, pvDp(7)).apply { topMargin = pvDp(5) })
        detail?.let {
            addView(TextView(this@v51Meter).apply { text = it; setTextColor(Pv.textLo); textSize = pvSp(6.8f); setPadding(0, pvDp(4), 0, 0) })
        }
    }

fun Context.v51MetricCard(label: String, value: String, detail: String, tone: V51Tone = V51Tone.NEUTRAL): LinearLayout = v51ElevatedPanel(10).apply {
    addView(TextView(this@v51MetricCard).apply { text=label;textSize=pvSp(6.7f);setTextColor(Pv.textLo);typeface=Typeface.DEFAULT_BOLD;letterSpacing=.08f })
    addView(TextView(this@v51MetricCard).apply { text=value;textSize=pvSp(16f);setTextColor(v51ToneColor(tone));typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);setPadding(0,pvDp(3),0,pvDp(2)) })
    addView(TextView(this@v51MetricCard).apply { text=detail;textSize=pvSp(7f);setTextColor(Pv.textMid);maxLines=2 })
}

class V51SparklineView(context: Context) : View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    var values:List<Double> = emptyList(); set(value){field=value;invalidate()}
    var tone:V51Tone=V51Tone.GOOD; set(value){field=value;invalidate()}
    override fun onDraw(c:Canvas){
        super.onDraw(c);val n=V51VisualPolicy.normalize(values);if(n.size<2)return
        val d=resources.displayMetrics.density;val l=8f*d;val r=width-8f*d;val t=7f*d;val b=height-7f*d
        p.style=Paint.Style.STROKE;p.strokeWidth=d;p.color=Color.argb(75,120,135,148);repeat(3){i->val y=t+(b-t)*i/2f;c.drawLine(l,y,r,y,p)}
        val path=Path();n.forEachIndexed{i,v->val x=l+(r-l)*i/(n.size-1).toFloat();val y=b-(b-t)*v;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)}
        p.strokeWidth=2.2f*d;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND;p.color=v51ToneColor(tone);c.drawPath(path,p);p.style=Paint.Style.FILL;c.drawCircle(r,b-(b-t)*n.last(),3.2f*d,p)
    }
}

class V51ScoreRingView(context:Context):View(context){
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    var score:Int=0;set(value){field=value.coerceIn(0,100);invalidate()}
    var label:String="SCORE";set(value){field=value;invalidate()}
    override fun onDraw(c:Canvas){super.onDraw(c);val d=resources.displayMetrics.density;val cx=width/2f;val cy=height/2f;val radius=min(width,height)*.36f;val stroke=max(6f*d,radius*.10f);p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeWidth=stroke;p.color=Color.rgb(38,47,56);c.drawCircle(cx,cy,radius,p);p.color=v51ToneColor(V51VisualPolicy.toneForScore(score));c.drawArc(RectF(cx-radius,cy-radius,cx+radius,cy+radius),-90f,score*3.6f,false,p);p.style=Paint.Style.FILL;p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);p.textSize=radius*.62f;p.color=Pv.textHi;c.drawText(score.toString(),cx,cy+radius*.18f,p);p.typeface=Typeface.DEFAULT_BOLD;p.textSize=radius*.20f;p.color=Pv.textLo;c.drawText(label,cx,cy+radius*.58f,p);p.textAlign=Paint.Align.LEFT}
}

class V51TvPolishOverlay(context:Context,private val engine:GameEngine):View(context){
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    init{isClickable=false;isFocusable=false;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO;setWillNotDraw(false)}
    override fun onDraw(c:Canvas){super.onDraw(c);if(width<=0||height<=0)return;drawCinemaFrame(c);drawModeRail(c);drawConditionDock(c);drawConfidenceGauge(c);drawReadDock(c);drawSubsystemDots(c);drawResultRibbon(c);postInvalidateDelayed(if(engine.state?.running==true)45L else 220L)}
    private fun drawCinemaFrame(c:Canvas){val w=width.toFloat();val h=height.toFloat();p.shader=LinearGradient(0f,0f,0f,h*.18f,Color.argb(105,2,5,7),Color.TRANSPARENT,Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h*.18f,p);p.shader=LinearGradient(0f,h*.76f,0f,h,Color.TRANSPARENT,Color.argb(115,2,5,7),Shader.TileMode.CLAMP);c.drawRect(0f,h*.76f,w,h,p);p.shader=null;p.style=Paint.Style.STROKE;p.strokeWidth=max(1f,w*.0007f);p.color=Color.argb(55,210,235,220);val inset=min(w,h)*.018f;val arm=min(w,h)*.028f;listOf(0 to 0,1 to 0,0 to 1,1 to 1).forEach{(rx,ry)->val x=if(rx==0)inset else w-inset;val y=if(ry==0)inset else h-inset;c.drawLine(x,y,x+if(rx==0)arm else-arm,y,p);c.drawLine(x,y,x,y+if(ry==0)arm else-arm,p)};p.style=Paint.Style.FILL}
    private fun drawModeRail(c:Canvas){val game=engine.gameModes.status;val w=width.toFloat();val h=height.toFloat();val left=w*.022f;val top=h*.025f;val ww=w*.20f;val hh=h*.052f;p.color=Color.argb(185,7,11,14);c.drawRoundRect(RectF(left,top,left+ww,top+hh),hh*.34f,hh*.34f,p);p.color=Pv.primary;c.drawCircle(left+hh*.34f,top+hh*.50f,hh*.08f,p);p.typeface=Typeface.DEFAULT_BOLD;p.textSize=max(8f,w*.0058f);p.color=Pv.textHi;val mode=if(game.totalHoles>0)"${game.mode.label} · HOLE ${game.hole}/${game.totalHoles}" else game.mode.label;c.drawText(mode,left+hh*.52f,top+hh*.43f,p);p.textSize=max(6.5f,w*.0045f);p.color=Pv.textMid;c.drawText("P${game.activePlayer}/${game.playerCount} · SCORE ${game.gameScore}",left+hh*.52f,top+hh*.73f,p)}
    private fun drawConditionDock(c:Canvas){val s=engine.settings;val w=width.toFloat();val h=height.toFloat();val right=w*.978f;val top=h*.025f;val ww=w*.245f;val hh=h*.052f;val left=right-ww;p.color=Color.argb(185,7,11,14);c.drawRoundRect(RectF(left,top,right,top+hh),hh*.34f,hh*.34f,p);val labels=listOf("DIST ${"%.1f".format(s.holeDistanceM)}m","STIMP ${"%.1f".format(s.stimpMeters)}","SIDE ${"%+.1f".format(s.sideSlopePct)}%","LONG ${"%+.1f".format(s.longSlopePct)}%");val cw=ww/labels.size;labels.forEachIndexed{i,text->p.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);p.textSize=max(6.7f,w*.00465f);p.color=if(i==0)Pv.primary else Pv.textHi;p.textAlign=Paint.Align.CENTER;c.drawText(text,left+cw*(i+.5f),top+hh*.61f,p)};p.textAlign=Paint.Align.LEFT}
    private fun drawConfidenceGauge(c:Canvas){val shot=engine.currentShot?:return;val q=V16MetricConfidenceEstimator.estimate(shot);val value=((q.ballSpeed+q.launch+q.face+q.path)/4.0).coerceIn(0.0,1.0);val w=width.toFloat();val h=height.toFloat();val r=h*.030f;val cx=w*.952f;val cy=h*.88f;p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeWidth=max(4f,h*.006f);p.color=Color.argb(120,80,93,103);c.drawArc(RectF(cx-r,cy-r,cx+r,cy+r),145f,250f,false,p);p.color=v51ToneColor(if(value>=.75)V51Tone.GOOD else if(value>=.55)V51Tone.WARN else V51Tone.BAD);c.drawArc(RectF(cx-r,cy-r,cx+r,cy+r),145f,(250f*value).toFloat(),false,p);p.style=Paint.Style.FILL;p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.DEFAULT_BOLD;p.textSize=max(7f,w*.0048f);p.color=Pv.textHi;c.drawText("Q${(value*100).toInt()}",cx,cy+3f,p);p.textSize=max(5.5f,w*.0038f);p.color=Pv.textLo;c.drawText("SHOT",cx,cy+r*.83f,p);p.textAlign=Paint.Align.LEFT}
    private fun drawReadDock(c:Canvas){if(engine.state?.running==true||engine.lastResult!=null)return;val read=GreenReadRuntime.peekOrSchedule(engine.settings)?:return;val w=width.toFloat();val h=height.toFloat();val ww=w*.205f;val hh=h*.082f;val right=w*.978f;val left=right-ww;val top=h*.105f;p.color=Color.argb(184,7,11,14);c.drawRoundRect(RectF(left,top,right,top+hh),h*.014f,h*.014f,p);p.typeface=Typeface.DEFAULT_BOLD;p.textSize=max(6.5f,w*.0045f);p.color=Pv.textLo;c.drawText("GREEN READ",left+ww*.07f,top+hh*.25f,p);val main=when{!read.solverReliable->"RECALCULATING";read.aimSideLabel=="센터"->"CENTER";else->"${read.aimSideLabel}  ${"%.1f".format(read.cupCount)} CUP"};p.textSize=max(12f,w*.0086f);p.color=if(read.solverReliable)Pv.primary else Pv.amber;c.drawText(main,left+ww*.07f,top+hh*.58f,p);p.textSize=max(6.4f,w*.0044f);p.color=Pv.textMid;c.drawText("PACE ${"%.2f".format(read.recommendedBallSpeedMps)}m/s · ${read.paceHint}",left+ww*.07f,top+hh*.82f,p)}
    private fun drawSubsystemDots(c:Canvas){val hfr=V43HfrHealthWindow.summary();val companion=V16CompanionLinkRuntime.status();val w=width.toFloat();val h=height.toFloat();val left=w*.023f;val y=h*.105f;fun lamp(x:Float,label:String,tone:V51Tone){p.color=v51ToneColor(tone);c.drawCircle(x,y,4f*resources.displayMetrics.density,p);p.typeface=Typeface.DEFAULT_BOLD;p.textSize=max(6f,w*.0042f);p.color=Pv.textMid;c.drawText(label,x+8f*resources.displayMetrics.density,y+2f*resources.displayMetrics.density,p)};lamp(left,"HFR",if(hfr.degraded)V51Tone.BAD else if(hfr.samples==0)V51Tone.NEUTRAL else V51Tone.GOOD);lamp(left+w*.055f,"LAN",if(companion.peers>0)V51Tone.GOOD else V51Tone.NEUTRAL)}
    private fun drawResultRibbon(c:Canvas){val r=engine.lastResult?:return;val w=width.toFloat();val h=height.toFloat();val ww=w*.34f;val hh=h*.078f;val l=w*.5f-ww*.5f;val t=h*.73f;val tone=if(r.holed)V51Tone.GOOD else if(r.lipOut)V51Tone.WARN else V51Tone.NEUTRAL;val accent=v51ToneColor(tone);p.shader=LinearGradient(l,t,l+ww,t,intArrayOf(Color.argb(230,7,10,13),Color.argb(205,Color.red(accent)/5,Color.green(accent)/5,Color.blue(accent)/5)),null,Shader.TileMode.CLAMP);c.drawRoundRect(RectF(l,t,l+ww,t+hh),hh*.28f,hh*.28f,p);p.shader=null;p.color=accent;c.drawRoundRect(RectF(l,t,l+w*.004f,t+hh),hh*.20f,hh*.20f,p);val title=when{r.holed->"HOLED · PURE ROLL";r.lipOut->"LIP OUT";r.finishY<engine.settings.holeDistanceM->"SHORT";else->"RESULT"};p.typeface=Typeface.DEFAULT_BOLD;p.textSize=max(8f,w*.0056f);p.color=accent;c.drawText(title,l+ww*.065f,t+hh*.34f,p);p.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);p.textSize=max(18f,w*.0128f);p.color=Pv.textHi;c.drawText(if(r.holed)"IN" else "${"%.0f".format(r.distanceToCupM*100)} cm",l+ww*.065f,t+hh*.72f,p);p.textAlign=Paint.Align.RIGHT;p.textSize=max(9f,w*.0064f);p.color=Pv.textMid;c.drawText(engine.strokeScore?.let{"SCORE ${it.total}"}?:"ANALYZING",l+ww*.94f,t+hh*.61f,p);p.textAlign=Paint.Align.LEFT}
}
