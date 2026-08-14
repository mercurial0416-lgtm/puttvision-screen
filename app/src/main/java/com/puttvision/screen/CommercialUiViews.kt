package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Presentation-only commercial surfaces. */
class CommercialHomeBackdropView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        p.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(Color.rgb(4, 7, 10), Color.rgb(7, 16, 18), Color.rgb(8, 27, 19)),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p); p.shader = null

        // Improvement 4: layered atmospheric horizon instead of a flat backdrop.
        val horizon = h * .35f
        fun ridge(base: Float, amp: Float, color: Int, phase: Float) {
            val path = Path().apply {
                moveTo(0f, h)
                lineTo(0f, base)
                for (i in 0..18) {
                    val x = w * i / 18f
                    val y = base - amp * (0.45f + 0.55f * sin(i * .78f + phase))
                    lineTo(x, y)
                }
                lineTo(w, h); close()
            }
            p.color = color; c.drawPath(path, p)
        }
        ridge(horizon + h * .028f, h * .055f, Color.rgb(16, 40, 34), .5f)
        ridge(horizon + h * .052f, h * .038f, Color.rgb(12, 54, 36), 1.7f)
        p.color = Color.rgb(11, 37, 26)
        repeat(28) { i ->
            val x = w * i / 27f
            val r = w * (.007f + (i % 4) * .0018f)
            c.drawCircle(x, horizon + h * .045f - r * .55f, r, p)
        }

        val green = Path().apply {
            moveTo(w * .08f, h)
            lineTo(w * .31f, horizon)
            lineTo(w * .69f, horizon)
            lineTo(w * .97f, h)
            close()
        }
        p.shader = LinearGradient(0f, horizon, 0f, h,
            intArrayOf(Color.rgb(47, 113, 62), Color.rgb(24, 76, 43), Color.rgb(10, 42, 28)),
            floatArrayOf(0f, .50f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(green, p); p.shader = null

        // Improvement 5: perspective mowing stripes and a disciplined centre corridor.
        val save = c.save(); c.clipPath(green)
        repeat(9) { band ->
            val t0 = band / 9f; val t1 = (band + 1) / 9f
            val y0 = horizon + (h - horizon) * t0
            val y1 = horizon + (h - horizon) * t1
            p.color = if (band % 2 == 0) Color.argb(26, 210, 255, 220) else Color.argb(20, 0, 20, 7)
            c.drawRect(0f, y0, w, y1, p)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, w * .00075f)
        p.color = Color.argb(38, 166, 239, 194)
        for (i in -6..6) c.drawLine(w * .50f + i * w * .034f, horizon, w * .50f + i * w * .108f, h, p)
        p.color = Color.argb(76, 207, 255, 222)
        p.strokeWidth = max(1.5f, w * .001f)
        c.drawLine(w * .50f, horizon, w * .50f, h, p)
        repeat(7) { idx ->
            val t = (idx + 1) / 8f
            val y = h - (h - horizon) * t * t
            c.drawLine(w * (.08f + .22f * t), y, w * (.97f - .22f * t), y, p)
        }
        p.style = Paint.Style.FILL; c.restoreToCount(save)

        // Improvement 6: cup and ball gain depth, target ring and contact shadows.
        val cupX = w * .61f; val cupY = h * .48f
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.5f, w * .0011f)
        p.color = Color.argb(95, 246, 190, 74)
        c.drawCircle(cupX, cupY, w * .019f, p)
        p.color = Color.argb(45, 246, 190, 74)
        c.drawCircle(cupX, cupY, w * .032f, p)
        p.style = Paint.Style.FILL
        p.color = Color.argb(110, 0, 0, 0)
        c.drawOval(RectF(cupX - w * .013f, cupY - h * .003f, cupX + w * .013f, cupY + h * .006f), p)
        p.color = Color.argb(235, 242, 246, 247)
        c.drawRect(cupX - 1.3f, cupY - h * .13f, cupX + 1.3f, cupY, p)
        p.color = Pv.amber
        c.drawPath(Path().apply {
            moveTo(cupX + 1.5f, cupY - h * .13f)
            lineTo(cupX + w * .055f, cupY - h * .111f)
            lineTo(cupX + 1.5f, cupY - h * .086f); close()
        }, p)

        val ballX = w * .38f; val ballY = h * .72f; val ballR = max(7f, w * .010f)
        p.shader = RadialGradient(ballX, ballY + ballR * 1.3f, ballR * 2.8f,
            intArrayOf(Color.argb(105, 0, 0, 0), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawCircle(ballX, ballY + ballR * .9f, ballR * 2.8f, p); p.shader = null
        p.color = Color.WHITE; c.drawCircle(ballX, ballY, ballR, p)
        p.color = Color.argb(115, 220, 235, 240); c.drawCircle(ballX - ballR * .30f, ballY - ballR * .27f, ballR * .20f, p)

        p.shader = RadialGradient(w * .62f, h * .42f, w * .34f,
            intArrayOf(Color.argb(38, 95, 224, 158), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawCircle(w * .62f, h * .42f, w * .34f, p); p.shader = null
        p.shader = LinearGradient(0f, h * .55f, 0f, h, Color.TRANSPARENT, Color.argb(208, 4, 6, 8), Shader.TileMode.CLAMP)
        c.drawRect(0f, h * .55f, w, h, p); p.shader = null
    }
}

class CommercialImpactPreviewView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val d = resources.displayMetrics.density; val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val card = RectF(0f,0f,w,h)
        p.shader = LinearGradient(0f,0f,w,h,intArrayOf(Color.rgb(8,12,16),Color.rgb(12,20,20)),null,Shader.TileMode.CLAMP)
        c.drawRoundRect(card,13f*d,13f*d,p);p.shader=null
        p.style=Paint.Style.STROKE;p.strokeWidth=d;p.color=Color.argb(150,50,63,72)
        c.drawRoundRect(RectF(d/2,d/2,w-d/2,h-d/2),13f*d,13f*d,p);p.style=Paint.Style.FILL

        p.typeface=Typeface.DEFAULT_BOLD;p.textSize=7.8f*d;p.color=Pv.textHi;c.drawText("IMPACT WINDOW",9f*d,15f*d,p)
        p.color=Pv.primary;c.drawCircle(w-11f*d,11.5f*d,2.6f*d,p)
        val graphL=9f*d;val graphR=w-9f*d;val graphTop=25f*d;val graphBottom=h-16f*d;val mid=(graphTop+graphBottom)/2f

        // Improvement 7: real chart grammar — minor timing grid plus filled energy envelope.
        p.style=Paint.Style.STROKE;p.strokeWidth=.55f*d;p.color=Color.argb(58,119,137,148)
        repeat(5){i->val x=graphL+(graphR-graphL)*i/4f;c.drawLine(x,graphTop,x,graphBottom,p)}
        repeat(3){i->val y=graphTop+(graphBottom-graphTop)*i/2f;c.drawLine(graphL,y,graphR,y,p)}
        val line=Path();val fill=Path();var first=true
        var x=graphL
        while(x<=graphR){
            val t=(x-graphL)/max(1f,graphR-graphL)
            val envelope=(1f-abs(t-.5f)/.15f).coerceIn(0f,1f)
            val signal=sin(t*31f)*.075f+sin(t*53f)*.03f+envelope*.43f
            val y=mid-signal*(graphBottom-graphTop)
            if(first){line.moveTo(x,y);fill.moveTo(x,mid);fill.lineTo(x,y);first=false}else{line.lineTo(x,y);fill.lineTo(x,y)}
            x+=2f*d
        }
        fill.lineTo(graphR,mid);fill.close()
        p.style=Paint.Style.FILL;p.shader=LinearGradient(0f,graphTop,0f,graphBottom,Color.argb(100,78,209,121),Color.TRANSPARENT,Shader.TileMode.CLAMP)
        c.drawPath(fill,p);p.shader=null
        p.style=Paint.Style.STROKE;p.strokeWidth=1.35f*d;p.color=Pv.primary;c.drawPath(line,p)

        // Improvement 8: impact peak gets a glow, phase marker and explicit zero-time label.
        val impactX=(graphL+graphR)/2f
        p.shader=RadialGradient(impactX,mid,18f*d,intArrayOf(Color.argb(100,246,190,74),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
        p.style=Paint.Style.FILL;c.drawCircle(impactX,mid,18f*d,p);p.shader=null
        p.style=Paint.Style.STROKE;p.strokeWidth=1.1f*d;p.color=Pv.amber;c.drawLine(impactX,graphTop,impactX,graphBottom,p)
        p.style=Paint.Style.FILL;p.color=Pv.amber;c.drawCircle(impactX,mid,3.3f*d,p)
        p.typeface=Typeface.DEFAULT_BOLD;p.textSize=5.8f*d;p.textAlign=Paint.Align.CENTER;c.drawText("0 ms · CONTACT",impactX,graphTop-3.5f*d,p)
        p.typeface=Typeface.DEFAULT;p.color=Pv.textLo
        p.textAlign=Paint.Align.LEFT;c.drawText("−100",graphL,h-5f*d,p);p.textAlign=Paint.Align.CENTER;c.drawText("IMPACT",impactX,h-5f*d,p);p.textAlign=Paint.Align.RIGHT;c.drawText("+100",graphR,h-5f*d,p);p.textAlign=Paint.Align.LEFT
    }
}

class CommercialModeVisualView(context: Context, private val game: Boolean) : View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    init { isClickable=false;isFocusable=false;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(c:Canvas){
        super.onDraw(c);val w=width.toFloat();val h=height.toFloat();if(w<=0f||h<=0f)return
        p.shader=LinearGradient(0f,0f,w,h,if(game)intArrayOf(Color.rgb(29,20,10),Color.rgb(10,12,14))else intArrayOf(Color.rgb(9,31,21),Color.rgb(8,12,14)),null,Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f,0f,w,h),28f,28f,p);p.shader=null
        val accent=if(game)Pv.amber else Pv.primary
        // Improvement 9: target corridor, concentric landing rings and mini flag make mode cards read as gameplay, not decoration.
        p.style=Paint.Style.STROKE;p.strokeWidth=max(1.2f,w*.0025f);p.color=Color.argb(60,Color.red(accent),Color.green(accent),Color.blue(accent))
        val tx=w*.70f;val ty=h*.29f
        repeat(3){i->c.drawCircle(tx,ty,w*(.035f+i*.027f),p)}
        val route=Path().apply{moveTo(w*.18f,h*.78f);cubicTo(w*.34f,h*.65f,w*.48f,h*.56f,tx,ty)}
        p.strokeWidth=max(2f,w*.004f);p.color=Color.argb(195,Color.red(accent),Color.green(accent),Color.blue(accent));c.drawPath(route,p)
        p.pathEffect=android.graphics.DashPathEffect(floatArrayOf(w*.018f,w*.012f),0f);p.color=Color.argb(90,255,255,255);p.strokeWidth=max(1f,w*.002f)
        c.drawLine(w*.50f,h*.88f,w*.50f,h*.15f,p);p.pathEffect=null;p.style=Paint.Style.FILL
        p.color=Color.WHITE;c.drawCircle(w*.18f,h*.78f,max(6f,w*.018f),p);p.color=accent;c.drawCircle(tx,ty,max(4f,w*.012f),p)
        p.color=Color.argb(230,236,242,240);c.drawRect(tx-1f,ty-h*.11f,tx+1f,ty,p)
        p.color=accent;c.drawPath(Path().apply{moveTo(tx+1f,ty-h*.11f);lineTo(tx+w*.045f,ty-h*.095f);lineTo(tx+1f,ty-h*.075f);close()},p)
    }
}

class CommercialSetupDiagramView(context: Context):View(context){
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    init{isClickable=false;isFocusable=false;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
    override fun onDraw(c:Canvas){
        super.onDraw(c);val w=width.toFloat();val h=height.toFloat();if(w<=0f||h<=0f)return
        p.color=Color.rgb(9,14,17);c.drawRoundRect(RectF(0f,0f,w,h),24f,24f,p)
        val mat=RectF(w*.20f,h*.25f,w*.80f,h*.83f)
        // Improvement 10: camera FOV cone + numbered corners + centre axis turns setup art into an actionable diagram.
        val camX=w*.5f;val camY=h*.085f
        p.color=Color.argb(42,96,178,255);c.drawPath(Path().apply{moveTo(camX,camY+h*.045f);lineTo(mat.left,mat.top);lineTo(mat.right,mat.top);close()},p)
        p.style=Paint.Style.STROKE;p.strokeWidth=2f;p.color=Color.argb(125,96,178,255);c.drawLine(camX,camY+h*.045f,mat.left,mat.top,p);c.drawLine(camX,camY+h*.045f,mat.right,mat.top,p)
        p.color=Color.argb(140,78,209,121);c.drawRoundRect(mat,16f,16f,p);p.color=Color.argb(90,105,126,139);c.drawLine(mat.centerX(),mat.top,mat.centerX(),mat.bottom,p);p.style=Paint.Style.FILL
        val pts=listOf(mat.left+14f to mat.top+14f,mat.right-14f to mat.top+14f,mat.left+14f to mat.bottom-14f,mat.right-14f to mat.bottom-14f)
        val labels=listOf("1","2","3","4");val rr=max(8f,w*.021f)
        pts.forEachIndexed{i,(x,y)->p.color=Pv.primary;c.drawCircle(x,y,rr,p);p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.DEFAULT_BOLD;p.textSize=max(9f,w*.018f);p.color=Pv.primaryInk;c.drawText(labels[i],x,y+p.textSize*.34f,p)}
        p.textAlign=Paint.Align.LEFT;p.color=Color.WHITE;c.drawCircle(mat.centerX(),mat.bottom-mat.height()*.14f,rr*.85f,p)
        p.color=Pv.info;c.drawRoundRect(RectF(w*.42f,h*.055f,w*.58f,h*.125f),9f,9f,p)
        p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.DEFAULT_BOLD;p.textSize=max(11f,w*.025f);p.color=Pv.textHi;c.drawText("PHONE CAMERA",w*.5f,h*.18f,p)
        p.textSize=max(9f,w*.019f);p.typeface=Typeface.DEFAULT;p.color=Pv.textMid;c.drawText("1→2→4→3 · BALL INSIDE SAFE FRAME",w*.5f,h*.94f,p);p.textAlign=Paint.Align.LEFT
    }
}
