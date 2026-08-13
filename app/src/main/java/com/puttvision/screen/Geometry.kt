package com.puttvision.screen

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

data class FrameInfo(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int
)

data class CalibrationDiagnostics(
    val pointCount: Int,
    val reprojectionRmsPx: Double?,
    val maxReprojectionErrorPx: Double?,
    val lensK1: Double?,
    val lensApplied: Boolean
)

data class LensDistortionModel(
    val centerX: Double,
    val centerY: Double,
    val scale: Double,
    val k1: Double
) {
    /** Observed pixel -> corrected pinhole pixel. */
    fun undistort(p: PointF): PointF {
        val s = scale.coerceAtLeast(1.0)
        val x = (p.x - centerX) / s
        val y = (p.y - centerY) / s
        val r2 = x * x + y * y
        val f = 1.0 + k1 * r2
        return PointF(
            (centerX + x * f * s).toFloat(),
            (centerY + y * f * s).toFloat()
        )
    }

    /** Corrected pinhole pixel -> observed pixel, solved iteratively. */
    fun distort(p: PointF): PointF {
        var x = p.x.toDouble()
        var y = p.y.toDouble()
        repeat(6) {
            val u = undistort(PointF(x.toFloat(), y.toFloat()))
            x += p.x - u.x
            y += p.y - u.y
        }
        return PointF(x.toFloat(), y.toFloat())
    }
}

class Homography private constructor(
    private val h: DoubleArray,
    private val lens: LensDistortionModel? = null,
    val diagnostics: CalibrationDiagnostics = CalibrationDiagnostics(4, null, null, null, false)
) {
    private val inv: DoubleArray? by lazy { invert3x3(matrix3()) }

    fun map(p: PointF): PointF {
        val q = lens?.undistort(p) ?: p
        return project(h, q)
    }

    /** World/real coordinate -> observed camera pixel. Used by SIM CAMERA and diagnostics. */
    fun inverseMap(p: PointF): PointF? {
        val inverse = inv ?: return null
        val undistorted = projectMatrix(inverse, p) ?: return null
        return lens?.distort(undistorted) ?: undistorted
    }

    private fun matrix3(): DoubleArray = doubleArrayOf(
        h[0], h[1], h[2],
        h[3], h[4], h[5],
        h[6], h[7], 1.0
    )

    companion object {
        fun fromFourPoints(src: List<PointF>, dst: List<PointF>): Homography? {
            if (src.size != 4 || dst.size != 4) return null
            val solved = solveLeastSquares(src, dst) ?: return null
            return Homography(
                solved,
                null,
                CalibrationDiagnostics(4, null, null, null, false)
            )
        }

        /**
         * Over-determined fit. With >=6 points a small bounded radial-lens search is
         * performed and the selected model reports a real pixel reprojection residual.
         */
        fun fromPoints(src: List<PointF>, dst: List<PointF>, frame: FrameInfo): Homography? {
            if (src.size != dst.size || src.size < 4) return null
            if (src.size == 4) return fromFourPoints(src, dst)

            val centerX = frame.width * .5
            val centerY = frame.height * .5
            val scale = max(frame.width, frame.height) * .5
            val candidates = if (src.size >= 6) {
                buildList {
                    var k = -.18
                    while (k <= .1801) {
                        add(k)
                        k += .02
                    }
                }
            } else listOf(0.0)

            data class Candidate(
                val h: DoubleArray,
                val lens: LensDistortionModel?,
                val rms: Double,
                val maxErr: Double,
                val score: Double
            )

            var best: Candidate? = null
            for (k1 in candidates) {
                val model = if (abs(k1) >= .0001) LensDistortionModel(centerX, centerY, scale, k1) else null
                val corrected = if (model != null) src.map(model::undistort) else src
                val solved = solveLeastSquares(corrected, dst) ?: continue
                val inverse = invert3x3(doubleArrayOf(
                    solved[0], solved[1], solved[2],
                    solved[3], solved[4], solved[5],
                    solved[6], solved[7], 1.0
                )) ?: continue
                var sum2 = 0.0
                var maxErr = 0.0
                var count = 0
                for (i in src.indices) {
                    val predictedUndistorted = projectMatrix(inverse, dst[i]) ?: continue
                    val predicted = model?.distort(predictedUndistorted) ?: predictedUndistorted
                    val e = hypot(
                        (predicted.x - src[i].x).toDouble(),
                        (predicted.y - src[i].y).toDouble()
                    )
                    if (!e.isFinite()) continue
                    sum2 += e * e
                    maxErr = max(maxErr, e)
                    count++
                }
                if (count < 4) continue
                val rms = sqrt(sum2 / count)
                // Tiny regularization keeps a lens coefficient from winning on noise alone.
                val score = rms + abs(k1) * .42
                val c = Candidate(solved, model, rms, maxErr, score)
                if (best == null || c.score < best!!.score) best = c
            }
            val selected = best ?: return null
            return Homography(
                selected.h,
                selected.lens,
                CalibrationDiagnostics(
                    pointCount = src.size,
                    reprojectionRmsPx = selected.rms,
                    maxReprojectionErrorPx = selected.maxErr,
                    lensK1 = selected.lens?.k1,
                    lensApplied = selected.lens != null
                )
            )
        }

        private fun solveLeastSquares(src: List<PointF>, dst: List<PointF>): DoubleArray? {
            if (src.size != dst.size || src.size < 4) return null
            val ata = Array(8) { DoubleArray(8) }
            val atb = DoubleArray(8)

            fun accumulate(row: DoubleArray, value: Double) {
                for (r in 0 until 8) {
                    atb[r] += row[r] * value
                    for (c in 0 until 8) ata[r][c] += row[r] * row[c]
                }
            }

            for (i in src.indices) {
                val x = src[i].x.toDouble()
                val y = src[i].y.toDouble()
                val u = dst[i].x.toDouble()
                val v = dst[i].y.toDouble()
                accumulate(doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y), u)
                accumulate(doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y), v)
            }
            return solve8(ata, atb)
        }

        private fun solve8(aRaw: Array<DoubleArray>, bRaw: DoubleArray): DoubleArray? {
            val a = Array(8) { r -> DoubleArray(9) { c -> if (c < 8) aRaw[r][c] else bRaw[r] } }
            for (col in 0 until 8) {
                var pivot = col
                for (row in col + 1 until 8) if (abs(a[row][col]) > abs(a[pivot][col])) pivot = row
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
            return DoubleArray(8) { a[it][8] }
        }

        private fun project(h: DoubleArray, p: PointF): PointF {
            val x = p.x.toDouble()
            val y = p.y.toDouble()
            val den = h[6] * x + h[7] * y + 1.0
            if (abs(den) < 1e-10) return PointF(Float.NaN, Float.NaN)
            return PointF(
                ((h[0] * x + h[1] * y + h[2]) / den).toFloat(),
                ((h[3] * x + h[4] * y + h[5]) / den).toFloat()
            )
        }

        private fun projectMatrix(m: DoubleArray, p: PointF): PointF? {
            val x = p.x.toDouble()
            val y = p.y.toDouble()
            val den = m[6] * x + m[7] * y + m[8]
            if (abs(den) < 1e-10) return null
            val px = (m[0] * x + m[1] * y + m[2]) / den
            val py = (m[3] * x + m[4] * y + m[5]) / den
            if (!px.isFinite() || !py.isFinite()) return null
            return PointF(px.toFloat(), py.toFloat())
        }

        private fun invert3x3(m: DoubleArray): DoubleArray? {
            if (m.size != 9) return null
            val a = m[0]; val b = m[1]; val c = m[2]
            val d = m[3]; val e = m[4]; val f = m[5]
            val g = m[6]; val h = m[7]; val i = m[8]
            val A = e * i - f * h
            val B = -(d * i - f * g)
            val C = d * h - e * g
            val D = -(b * i - c * h)
            val E = a * i - c * g
            val F = -(a * h - b * g)
            val G = b * f - c * e
            val H = -(a * f - c * d)
            val I = a * e - b * d
            val det = a * A + b * B + c * C
            if (abs(det) < 1e-12) return null
            return doubleArrayOf(A, D, G, B, E, H, C, F, I).map { it / det }.toDoubleArray()
        }
    }
}

fun normalizeFaceAngle(deg: Double): Double {
    var x = deg
    while (x > 90.0) x -= 180.0
    while (x < -90.0) x += 180.0
    return x
}
