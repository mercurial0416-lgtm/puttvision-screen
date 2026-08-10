package com.puttvision.screen

import android.graphics.PointF
import kotlin.math.abs

data class FrameInfo(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int
)

class Homography private constructor(private val h: DoubleArray) {
    fun map(p: PointF): PointF {
        val x = p.x.toDouble()
        val y = p.y.toDouble()
        val den = h[6] * x + h[7] * y + 1.0
        if (abs(den) < 1e-10) return PointF(Float.NaN, Float.NaN)
        return PointF(
            ((h[0] * x + h[1] * y + h[2]) / den).toFloat(),
            ((h[3] * x + h[4] * y + h[5]) / den).toFloat()
        )
    }

    companion object {
        fun fromFourPoints(src: List<PointF>, dst: List<PointF>): Homography? {
            if (src.size != 4 || dst.size != 4) return null
            val a = Array(8) { DoubleArray(9) }

            for (i in 0 until 4) {
                val x = src[i].x.toDouble()
                val y = src[i].y.toDouble()
                val u = dst[i].x.toDouble()
                val v = dst[i].y.toDouble()

                val r0 = i * 2
                val r1 = r0 + 1

                a[r0][0] = x
                a[r0][1] = y
                a[r0][2] = 1.0
                a[r0][6] = -u * x
                a[r0][7] = -u * y
                a[r0][8] = u

                a[r1][3] = x
                a[r1][4] = y
                a[r1][5] = 1.0
                a[r1][6] = -v * x
                a[r1][7] = -v * y
                a[r1][8] = v
            }

            for (col in 0 until 8) {
                var pivot = col
                for (row in col + 1 until 8) {
                    if (abs(a[row][col]) > abs(a[pivot][col])) pivot = row
                }
                if (abs(a[pivot][col]) < 1e-10) return null

                val tmp = a[col]
                a[col] = a[pivot]
                a[pivot] = tmp

                val div = a[col][col]
                for (j in col until 9) a[col][j] /= div

                for (row in 0 until 8) {
                    if (row == col) continue
                    val f = a[row][col]
                    for (j in col until 9) a[row][j] -= f * a[col][j]
                }
            }

            return Homography(DoubleArray(8) { a[it][8] })
        }
    }
}

fun normalizeFaceAngle(deg: Double): Double {
    var x = deg
    while (x > 90.0) x -= 180.0
    while (x < -90.0) x += 180.0
    return x
}
