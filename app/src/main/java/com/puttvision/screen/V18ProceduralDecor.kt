package com.puttvision.screen

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Lightweight no-asset scenery for the TV simulator. Vertex format matches V18: pos/normal/RGBA. */
object V18ProceduralDecor {
    fun build(settings: GreenSettings): FloatArray {
        val out = ArrayList<Float>(14000)
        val hole = settings.holeDistanceM.toFloat()

        fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, c: FloatArray) {
            out += x; out += y; out += z
            out += nx; out += ny; out += nz
            out += c[0]; out += c[1]; out += c[2]; out += c[3]
        }
        fun tri(a: FloatArray, b: FloatArray, c: FloatArray, color: FloatArray, normal: FloatArray = floatArrayOf(0f, 0f, 1f)) {
            vertex(a[0],a[1],a[2],normal[0],normal[1],normal[2],color)
            vertex(b[0],b[1],b[2],normal[0],normal[1],normal[2],color)
            vertex(c[0],c[1],c[2],normal[0],normal[1],normal[2],color)
        }
        fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, color: FloatArray, normal: FloatArray) {
            tri(a,b,c,color,normal); tri(a,c,d,color,normal)
        }
        fun box(cx: Float, cy: Float, base: Float, sx: Float, sy: Float, sz: Float, color: FloatArray) {
            val x0=cx-sx/2; val x1=cx+sx/2; val y0=cy-sy/2; val y1=cy+sy/2; val z1=base+sz
            quad(floatArrayOf(x0,y0,base),floatArrayOf(x1,y0,base),floatArrayOf(x1,y0,z1),floatArrayOf(x0,y0,z1),color,floatArrayOf(0f,-1f,0f))
            quad(floatArrayOf(x1,y0,base),floatArrayOf(x1,y1,base),floatArrayOf(x1,y1,z1),floatArrayOf(x1,y0,z1),color,floatArrayOf(1f,0f,0f))
            quad(floatArrayOf(x1,y1,base),floatArrayOf(x0,y1,base),floatArrayOf(x0,y1,z1),floatArrayOf(x1,y1,z1),color,floatArrayOf(0f,1f,0f))
            quad(floatArrayOf(x0,y1,base),floatArrayOf(x0,y0,base),floatArrayOf(x0,y0,z1),floatArrayOf(x0,y1,z1),color,floatArrayOf(-1f,0f,0f))
            quad(floatArrayOf(x0,y0,z1),floatArrayOf(x1,y0,z1),floatArrayOf(x1,y1,z1),floatArrayOf(x0,y1,z1),color,floatArrayOf(0f,0f,1f))
        }
        fun tree(x: Float, y: Float, scale: Float, warm: Boolean) {
            val trunk = floatArrayOf(.27f,.18f,.10f,1f)
            val foliage = if (warm) floatArrayOf(.50f,.25f,.09f,1f) else floatArrayOf(.07f,.31f,.09f,1f)
            val base = -.035f
            box(x,y,base,.09f*scale,.09f*scale,.54f*scale,trunk)
            val z=.42f*scale
            val r=.31f*scale
            val h=.60f*scale
            // Three crossed tapered crowns give depth from every camera angle.
            repeat(3){ k ->
                val a=(Math.PI*k/3.0).toFloat()
                val dx=cos(a)*r; val dy=sin(a)*r
                val p0=floatArrayOf(x-dx,y-dy,base+z)
                val p1=floatArrayOf(x+dx,y+dy,base+z)
                val top=floatArrayOf(x,y,base+z+h)
                tri(p0,p1,top,foliage,floatArrayOf(0f,-1f,.18f))
                val p2=floatArrayOf(x-dx*.78f,y-dy*.78f,base+z+h*.28f)
                val p3=floatArrayOf(x+dx*.78f,y+dy*.78f,base+z+h*.28f)
                val top2=floatArrayOf(x,y,base+z+h*1.16f)
                tri(p2,p3,top2,foliage,floatArrayOf(0f,-1f,.18f))
            }
        }

        // Distant rolling hills behind the target line.
        val hillNear=floatArrayOf(.12f,.40f,.14f,1f)
        val hillFar=floatArrayOf(.10f,.31f,.15f,1f)
        fun hillStrip(y: Float, z: Float, color: FloatArray, phase: Float) {
            val steps=28
            for(i in 0 until steps){
                val x0=-9f+18f*i/steps; val x1=-9f+18f*(i+1)/steps
                val z0=z + sin(i*.67f+phase)*.22f + sin(i*.23f+phase*.7f)*.13f
                val z1=z + sin((i+1)*.67f+phase)*.22f + sin((i+1)*.23f+phase*.7f)*.13f
                tri(floatArrayOf(x0,y,-.06f),floatArrayOf(x1,y,-.06f),floatArrayOf(x1,y,z1),color,floatArrayOf(0f,-1f,.3f))
                tri(floatArrayOf(x0,y,-.06f),floatArrayOf(x1,y,z1),floatArrayOf(x0,y,z0),color,floatArrayOf(0f,-1f,.3f))
            }
        }
        hillStrip(hole+5.9f,.65f,hillFar,.9f)
        hillStrip(hole+4.7f,.43f,hillNear,2.4f)

        // Balanced tree lines: dense enough to feel like a course, leaving the putting lane open.
        repeat(18){ i ->
            val yy = -0.1f + (hole+4.4f) * i / 17f
            val jitter = sin(i*1.77f)*.36f
            val scale = .68f + (i%5)*.08f
            tree(-2.65f-jitter, yy, scale, i%9==3)
            if(i%2==0 || i<8) tree(2.72f+jitter*.8f, yy+.12f, scale*.95f, i%11==5)
        }

        // Small clubhouse on the horizon, intentionally generic rather than copying a branded course.
        val building=floatArrayOf(.72f,.68f,.58f,1f)
        val roof=floatArrayOf(.26f,.18f,.14f,1f)
        val glass=floatArrayOf(.18f,.29f,.32f,1f)
        val bx=-3.7f; val by=hole+3.3f
        box(bx,by,-.04f,2.15f,.62f,.72f,building)
        val roofBase=.68f
        tri(floatArrayOf(bx-1.25f,by-.38f,roofBase),floatArrayOf(bx+1.25f,by-.38f,roofBase),floatArrayOf(bx,by-.38f,1.20f),roof,floatArrayOf(0f,-1f,.45f))
        tri(floatArrayOf(bx+1.25f,by+.38f,roofBase),floatArrayOf(bx-1.25f,by+.38f,roofBase),floatArrayOf(bx,by+.38f,1.20f),roof,floatArrayOf(0f,1f,.45f))
        repeat(5){i -> box(bx-.82f+i*.41f,by-.325f,.17f,.23f,.015f,.29f,glass) }

        return out.toFloatArray()
    }
}
