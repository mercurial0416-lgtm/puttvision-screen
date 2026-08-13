package com.puttvision.screen

import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.hypot

data class ResolvedMarkerLayout(
    val imagePoints: List<PointF>, // stable BL, BR, TR, TL anchors
    val realPointsCm: List<PointF>,
    val source: String,
    val fitImagePoints: List<PointF> = imagePoints,
    val fitRealPointsCm: List<PointF> = realPointsCm
)

object MarkerLayoutResolver {

    private const val WIDTH_CM = 45f
    private const val LENGTH_CM = 100f

    fun fromGenericFour(points: List<PointF>): ResolvedMarkerLayout? {
        if (points.size != 4) return null

        val cx = points.map { it.x }.average().toFloat()
        val cy = points.map { it.y }.average().toFloat()
        val ring = points.sortedBy {
            atan2((it.y - cy).toDouble(), (it.x - cx).toDouble())
        }

        fun dist(a: PointF, b: PointF): Double =
            hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

        data class Edge(val a: Int, val b: Int, val length: Double)
        val edges = List(4) { i -> Edge(i, (i + 1) % 4, dist(ring[i], ring[(i + 1) % 4])) }
        val pairA = listOf(edges[0], edges[2])
        val pairB = listOf(edges[1], edges[3])
        val widthEdges = if (pairA.map { it.length }.average() <= pairB.map { it.length }.average()) pairA else pairB
        val near = widthEdges.maxByOrNull { it.length } ?: return null
        val far = widthEdges.minByOrNull { it.length } ?: return null
        val nearPoints = listOf(ring[near.a], ring[near.b]).sortedBy { it.x }
        val farPoints = listOf(ring[far.a], ring[far.b]).sortedBy { it.x }
        if (nearPoints.size != 2 || farPoints.size != 2) return null

        val src = listOf(nearPoints[0], nearPoints[1], farPoints[1], farPoints[0])
        val dst = listOf(
            PointF(-WIDTH_CM / 2f, 0f),
            PointF(WIDTH_CM / 2f, 0f),
            PointF(WIDTH_CM / 2f, LENGTH_CM),
            PointF(-WIDTH_CM / 2f, LENGTH_CM)
        )
        return ResolvedMarkerLayout(src, dst, "GENERIC-QR-45x100")
    }
}
