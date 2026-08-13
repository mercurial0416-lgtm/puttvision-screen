package com.puttvision.screen

import android.graphics.PointF

/** Projects the shared GreenRead physics path back onto the calibrated real putting mat. */
object V33ArGreenReadRuntime {
    data class Snapshot(val imagePoints:List<PointF>,val cupSpeedMps:Double,val ballSpeedMps:Double)
    private var key=""
    private var cached:Homography?=null

    @Synchronized
    fun snapshot(settings:GreenSettings,calibrationImagePoints:List<PointF>,frame:FrameInfo):Snapshot?{
        if(calibrationImagePoints.size!=4)return null
        val currentKey=buildString{append(frame.width);append('x');append(frame.height);calibrationImagePoints.forEach{append(':');append(it.x.toInt());append(',');append(it.y.toInt())}}
        val h=if(currentKey==key)cached else {
            val half=(V16MatGeometryRuntime.widthCm/2.0).toFloat();val length=V16MatGeometryRuntime.lengthCm.toFloat()
            Homography.fromPoints(calibrationImagePoints,listOf(PointF(-half,0f),PointF(half,0f),PointF(half,length),PointF(-half,length)),frame).also{cached=it;key=currentKey}
        }?:return null
        val read=GreenReadRuntime.peekOrSchedule(settings)?:return null
        if(!read.solverReliable)return null
        val pts=read.predictedTrail.mapNotNull{(xM,yM)->
            val image=h.inverseMap(PointF((xM*100.0).toFloat(),(yM*100.0).toFloat()))?:return@mapNotNull null
            if(!image.x.isFinite()||!image.y.isFinite())return@mapNotNull null
            if(image.x !in -24f..(frame.width+24f)||image.y !in -24f..(frame.height+24f))return@mapNotNull null
            image
        }
        return if(pts.size>=2)Snapshot(pts,V27CupPaceRuntime.targetCupSpeedMps,read.recommendedBallSpeedMps)else null
    }
}
