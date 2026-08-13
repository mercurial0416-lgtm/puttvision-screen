package com.puttvision.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A time stamped world-space point used by both NORMAL and HFR measurement. */
data class V14TimedPoint(
    val tSec: Double,
    val xCm: Double,
    val yCm: Double
)

data class V14Kinematics(
    val speedMps: Double,
    val launchAngleDeg: Double,
    val rmsCm: Double,
    val sampleCount: Int
)

/**
 * Robust multi-frame velocity fit. A single bad centroid no longer owns BALL/START.
 * IRLS with a Huber-like weight is intentionally small and deterministic for phone CPUs.
 */
object V14RobustKinematics {
    fun fit(pointsRaw: List<V14TimedPoint>): V14Kinematics? {
        val points = pointsRaw
            .filter { it.tSec.isFinite() && it.xCm.isFinite() && it.yCm.isFinite() }
            .sortedBy { it.tSec }
            .distinctBy { (it.tSec * 1_000_000.0).toLong() }
        if (points.size < 4) return null
        val t0 = points.first().tSec
        val shifted = points.map { it.copy(tSec = it.tSec - t0) }
        if ((shifted.last().tSec - shifted.first().tSec) < .008) return null

        var weights = DoubleArray(shifted.size) { 1.0 }
        var xFit = linear(shifted.map { it.tSec }, shifted.map { it.xCm }, weights) ?: return null
        var yFit = linear(shifted.map { it.tSec }, shifted.map { it.yCm }, weights) ?: return null

        repeat(3) {
            val residuals = shifted.mapIndexed { i, p ->
                val rx = p.xCm - (xFit.first + xFit.second * p.tSec)
                val ry = p.yCm - (yFit.first + yFit.second * p.tSec)
                i to hypot(rx, ry)
            }
            val scale = robustScale(residuals.map { it.second }).coerceAtLeast(.035)
            weights = DoubleArray(shifted.size) { i ->
                val r = residuals[i].second / (scale * 1.75)
                if (r <= 1.0) 1.0 else 1.0 / r
            }
            xFit = linear(shifted.map { it.tSec }, shifted.map { it.xCm }, weights) ?: xFit
            yFit = linear(shifted.map { it.tSec }, shifted.map { it.yCm }, weights) ?: yFit
        }

        val vxCm = xFit.second
        val vyCm = yFit.second
        val speed = hypot(vxCm, vyCm) / 100.0
        if (!speed.isFinite() || speed !in .05..7.0) return null
        val angle = Math.toDegrees(atan2(vxCm, vyCm))
        val rms = sqrt(shifted.map { p ->
            val dx = p.xCm - (xFit.first + xFit.second * p.tSec)
            val dy = p.yCm - (yFit.first + yFit.second * p.tSec)
            dx * dx + dy * dy
        }.average())
        return V14Kinematics(speed, angle, rms, shifted.size)
    }

    private fun linear(x: List<Double>, y: List<Double>, w: DoubleArray): Pair<Double, Double>? {
        if (x.size != y.size || x.size != w.size || x.size < 2) return null
        var sw = 0.0
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var sxy = 0.0
        for (i in x.indices) {
            val wi = w[i].coerceAtLeast(.001)
            sw += wi
            sx += wi * x[i]
            sy += wi * y[i]
            sxx += wi * x[i] * x[i]
            sxy += wi * x[i] * y[i]
        }
        val den = sw * sxx - sx * sx
        if (abs(den) < 1e-10) return null
        val slope = (sw * sxy - sx * sy) / den
        val intercept = (sy - slope * sx) / sw
        return intercept to slope
    }

    private fun robustScale(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val med = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) * .5
        val dev = sorted.map { abs(it - med) }.sorted()
        val mad = if (dev.size % 2 == 1) dev[dev.size / 2]
        else (dev[dev.size / 2 - 1] + dev[dev.size / 2]) * .5
        return max(mad * 1.4826, med * .25)
    }
}

data class V14Detection(
    val ballPx: PointF?,
    val heelPx: PointF?,
    val toePx: PointF?,
    val markerAngleDeg: Double?,
    val ballScore: Double,
    val headScore: Double
)

/**
 * Adaptive ROI tracker shared by real HFR clips and SIM CAMERA 2.0.
 * It predicts the next ball centroid and only falls back to a wide scan when tracking is lost.
 */
class V14BitmapVisionTracker {
    private var lastBall: PointF? = null
    private var ballVx = 0f
    private var ballVy = 0f
    private var lastHeel: PointF? = null
    private var lastToe: PointF? = null

    fun reset() {
        lastBall = null
        ballVx = 0f
        ballVy = 0f
        lastHeel = null
        lastToe = null
    }

    fun detect(source: Bitmap, wantPutter: Boolean): V14Detection {
        val maxWidth = 960
        val scale = if (source.width > maxWidth) maxWidth.toFloat() / source.width else 1f
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            false
        ) else source
        try {
            val w = bmp.width
            val h = bmp.height
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            val threshold = adaptiveWhiteThreshold(pixels, w, h)
            val prior = lastBall?.let { PointF((it.x + ballVx) * scale, (it.y + ballVy) * scale) }
            val previousScaled = lastBall?.let { PointF(it.x * scale, it.y * scale) }
            val ballCandidate = findBall(pixels, w, h, threshold, prior, previousScaled)
            val ball = ballCandidate?.first?.let { PointF(it.x / scale, it.y / scale) }
            if (ball != null) {
                lastBall?.let { old ->
                    ballVx = ballVx * .48f + (ball.x - old.x) * .52f
                    ballVy = ballVy * .48f + (ball.y - old.y) * .52f
                }
                lastBall = ball
            } else {
                ballVx *= .55f
                ballVy *= .55f
            }

            var heel: PointF? = null
            var toe: PointF? = null
            var headScore = 0.0
            if (wantPutter) {
                val center = ball?.let { PointF(it.x * scale, it.y * scale) }
                    ?: previousScaled
                    ?: PointF(w * .5f, h * .72f)
                val orange = findColor(pixels, w, h, center, lastHeel?.let { PointF(it.x * scale, it.y * scale) }, true)
                val blue = findColor(pixels, w, h, center, lastToe?.let { PointF(it.x * scale, it.y * scale) }, false)
                heel = orange?.first?.let { PointF(it.x / scale, it.y / scale) }
                toe = blue?.first?.let { PointF(it.x / scale, it.y / scale) }
                if (heel != null) lastHeel = heel
                if (toe != null) lastToe = toe
                headScore = listOfNotNull(orange?.second, blue?.second).takeIf { it.isNotEmpty() }?.average() ?: 0.0
            }
            val marker = if (ballCandidate != null) detectMarkedBallAngle(
                pixels, w, h, ballCandidate.first.x.toInt(), ballCandidate.first.y.toInt(), threshold
            ) else null
            return V14Detection(
                ballPx = ball,
                heelPx = heel,
                toePx = toe,
                markerAngleDeg = marker,
                ballScore = ballCandidate?.second ?: 0.0,
                headScore = headScore
            )
        } finally {
            if (bmp !== source && !bmp.isRecycled) bmp.recycle()
        }
    }

    private fun adaptiveWhiteThreshold(pixels: IntArray, w: Int, h: Int): Int {
        val sample = ArrayList<Int>(800)
        var y = max(0, h / 5)
        val sy = max(4, h / 24)
        val sx = max(4, w / 32)
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = pixels[y * w + x]
                val r = c shr 16 and 255
                val g = c shr 8 and 255
                val b = c and 255
                sample += (r + g + b) / 3
                x += sx
            }
            y += sy
        }
        if (sample.isEmpty()) return 198
        sample.sort()
        val p90 = sample[(sample.lastIndex * .90).toInt()]
        val median = sample[sample.size / 2]
        return max(178, min(232, max(p90 + 16, median + 54)))
    }

    private fun findBall(
        pixels: IntArray,
        w: Int,
        h: Int,
        threshold: Int,
        predicted: PointF?,
        previous: PointF?
    ): Pair<PointF, Double>? {
        fun search(cx: Float?, cy: Float?, radius: Int, step: Int): Pair<PointF, Double>? {
            val x0 = if (cx == null) 6 else max(6, (cx - radius).toInt())
            val x1 = if (cx == null) w - 7 else min(w - 7, (cx + radius).toInt())
            val y0 = if (cy == null) max(6, (h * .18f).toInt()) else max(6, (cy - radius).toInt())
            val y1 = if (cy == null) h - 7 else min(h - 7, (cy + radius).toInt())
            var best: PointF? = null
            var bestScore = -1.0
            var y = y0
            while (y <= y1) {
                var x = x0
                while (x <= x1) {
                    val c = pixels[y * w + x]
                    val r = c shr 16 and 255
                    val g = c shr 8 and 255
                    val b = c and 255
                    val minRgb = min(r, min(g, b))
                    if (minRgb >= threshold && max(r, max(g, b)) - minRgb <= 48) {
                        var hit = 0
                        var edge = 0
                        var total = 0
                        for (oy in -6..6 step 3) for (ox in -6..6 step 3) {
                            if (ox * ox + oy * oy > 40) continue
                            val cc = pixels[(y + oy) * w + (x + ox)]
                            val rr = cc shr 16 and 255
                            val gg = cc shr 8 and 255
                            val bb = cc and 255
                            if (min(rr, min(gg, bb)) >= threshold - 18 && max(rr, max(gg, bb)) - min(rr, min(gg, bb)) <= 58) hit++
                            if (abs(rr - gg) + abs(gg - bb) < 60) edge++
                            total++
                        }
                        val fill = hit.toDouble() / max(1, total)
                        if (fill >= .42) {
                            var score = fill * 1.4 + edge.toDouble() / max(1, total) * .25
                            predicted?.let { score += max(0.0, 1.0 - hypot((x - it.x).toDouble(), (y - it.y).toDouble()) / max(60.0, radius.toDouble())) * 1.8 }
                            previous?.let { score += max(0.0, 1.0 - hypot((x - it.x).toDouble(), (y - it.y).toDouble()) / 210.0) * .55 }
                            if (score > bestScore) {
                                bestScore = score
                                best = PointF(x.toFloat(), y.toFloat())
                            }
                        }
                    }
                    x += step
                }
                y += step
            }
            return best?.let { it to bestScore }
        }
        val local = predicted?.let { search(it.x, it.y, 125, 2) }
        if (local != null && local.second > 1.05) return local
        return search(null, null, max(w, h), 3)
    }

    private fun findColor(
        pixels: IntArray,
        w: Int,
        h: Int,
        fallbackCenter: PointF,
        previous: PointF?,
        orange: Boolean
    ): Pair<PointF, Double>? {
        val center = previous ?: fallbackCenter
        val radius = if (previous != null) min(w, h) * .24f else min(w, h) * .40f
        val minX = max(0, (center.x - radius).toInt())
        val maxX = min(w - 1, (center.x + radius).toInt())
        val minY = max(0, (center.y - radius).toInt())
        val maxY = min(h - 1, (center.y + radius).toInt())
        var sx = 0.0
        var sy = 0.0
        var count = 0
        var totalStrength = 0.0
        var y = minY
        while (y <= maxY) {
            var x = minX
            while (x <= maxX) {
                val c = pixels[y * w + x]
                val r = c shr 16 and 255
                val g = c shr 8 and 255
                val b = c and 255
                val strength = if (orange) {
                    if (r > 140 && g > 45 && b < 155 && r > g * 1.08 && g > b * .92) (r - b).toDouble() else 0.0
                } else {
                    if (b > 125 && r < 175 && b > r * 1.10 && b > g * 1.01) (b - r).toDouble() else 0.0
                }
                if (strength > 12.0) {
                    sx += x * strength
                    sy += y * strength
                    totalStrength += strength
                    count++
                }
                x += 3
            }
            y += 3
        }
        if (count < 5 || totalStrength <= 1.0) return null
        return PointF((sx / totalStrength).toFloat(), (sy / totalStrength).toFloat()) to min(1.0, count / 90.0)
    }

    /** Detects an optional dark dot/line printed on the white ball. */
    private fun detectMarkedBallAngle(pixels: IntArray, w: Int, h: Int, cx: Int, cy: Int, whiteThreshold: Int): Double? {
        val radius = 15
        var sx = 0.0
        var sy = 0.0
        var weight = 0.0
        var darkCount = 0
        for (oy in -radius..radius) for (ox in -radius..radius) {
            val r2 = ox * ox + oy * oy
            if (r2 < 16 || r2 > radius * radius) continue
            val x = cx + ox
            val y = cy + oy
            if (x !in 0 until w || y !in 0 until h) continue
            val c = pixels[y * w + x]
            val rr = c shr 16 and 255
            val gg = c shr 8 and 255
            val bb = c and 255
            val lum = (rr + gg + bb) / 3
            // Dark detail must sit inside the otherwise bright ball neighborhood.
            if (lum < whiteThreshold - 62) {
                val wt = (whiteThreshold - lum).toDouble()
                sx += ox * wt
                sy += oy * wt
                weight += wt
                darkCount++
            }
        }
        if (darkCount < 3 || weight < 150.0) return null
        val dx = sx / weight
        val dy = sy / weight
        if (hypot(dx, dy) < 2.2) return null
        return Math.toDegrees(atan2(dy, dx))
    }
}

data class BallRollMetrics(
    val spinRpm: Double?,
    val skidDistanceCm: Double?,
    val rollStartDistanceCm: Double?,
    val markedBall: Boolean,
    val confidence: Double
)

object V14BallRollAnalyzer {
    data class MarkerSample(val frame: Int, val distanceCm: Double, val angleDeg: Double)

    fun analyze(samplesRaw: List<MarkerSample>, fps: Int, speedMps: Double): BallRollMetrics? {
        if (samplesRaw.size < 6 || fps < 60 || speedMps <= .08) return null
        val samples = samplesRaw.sortedBy { it.frame }
        val unwrapped = ArrayList<Pair<MarkerSample, Double>>()
        var previousRaw = samples.first().angleDeg
        var angle = previousRaw
        unwrapped += samples.first() to angle
        for (s in samples.drop(1)) {
            var d = s.angleDeg - previousRaw
            while (d > 180.0) d -= 360.0
            while (d < -180.0) d += 360.0
            angle += d
            previousRaw = s.angleDeg
            unwrapped += s to angle
        }
        val omega = ArrayList<Pair<Double, Double>>() // distance, rad/s
        for (i in 1 until unwrapped.size) {
            val a = unwrapped[i - 1]
            val b = unwrapped[i]
            val dt = (b.first.frame - a.first.frame).toDouble() / fps
            if (dt <= 0.0 || dt > .05) continue
            val w = Math.toRadians(b.second - a.second) / dt
            if (w.isFinite() && abs(w) < 900.0) omega += b.first.distanceCm to abs(w)
        }
        if (omega.size < 4) return null
        val radiusM = .02135
        val targetOmega = speedMps / radiusM
        var stable = 0
        var rollDistance: Double? = null
        omega.forEach { (distance, w) ->
            val ratio = w / targetOmega
            if (ratio in .72..1.40) stable++ else stable = max(0, stable - 1)
            if (rollDistance == null && stable >= 3) rollDistance = distance
        }
        val tail = omega.takeLast(max(3, omega.size / 3)).map { it.second }.sorted()
        val medianOmega = tail[tail.size / 2]
        val rpm = medianOmega * 60.0 / (2.0 * PI)
        val confidence = (omega.size / 12.0).coerceIn(.35, 1.0) * if (rollDistance != null) 1.0 else .72
        return BallRollMetrics(
            spinRpm = rpm.takeIf { it in 5.0..2500.0 },
            skidDistanceCm = rollDistance,
            rollStartDistanceCm = rollDistance,
            markedBall = true,
            confidence = confidence.coerceIn(0.0, 1.0)
        )
    }
}

/** Converts Accuracy Lab residuals into a profile-specific empirical P95 error bound. */
object V14EmpiricalUncertainty {
    fun apply(
        metrics: ShotMetrics,
        samples: List<ValidationSample>,
        profileKey: String,
        model: AccuracyCorrectionModel?
    ): ShotMetrics {
        val matched = samples.filter {
            (it.profileKey == profileKey || it.profileKey == null) &&
                (it.refBall != null || it.refLaunch != null || it.refHead != null || it.refFace != null || it.refPath != null)
        }
        if (matched.size < 20) return metrics

        val ball = matched.mapNotNull { s -> s.refBall?.let { abs(s.measuredBall * (model?.ballScale ?: 1.0) - it) } }
        val launch = matched.mapNotNull { s -> s.refLaunch?.let { abs(s.measuredLaunch + (model?.launchOffsetDeg ?: 0.0) - it) } }
        val head = matched.mapNotNull { s ->
            val m = s.measuredHead
            val r = s.refHead
            if (m != null && r != null) abs(m * (model?.headScale ?: 1.0) - r) else null
        }
        val face = matched.mapNotNull { s ->
            val m = s.measuredFace
            val r = s.refFace
            if (m != null && r != null) abs(m + (model?.faceOffsetDeg ?: 0.0) - r) else null
        }
        val path = matched.mapNotNull { s ->
            val m = s.measuredPath
            val r = s.refPath
            if (m != null && r != null) abs(m + (model?.pathOffsetDeg ?: 0.0) - r) else null
        }
        val old = metrics.uncertainty
        val n = matched.size
        val empirical = MeasurementUncertainty(
            ballSpeedMps = p95(ball)?.coerceAtLeast(.008) ?: old?.ballSpeedMps ?: .08,
            launchDeg = p95(launch)?.coerceAtLeast(.035) ?: old?.launchDeg ?: .9,
            headSpeedMps = p95(head) ?: old?.headSpeedMps,
            faceDeg = p95(face) ?: old?.faceDeg,
            pathDeg = p95(path) ?: old?.pathDeg,
            impactMm = old?.impactMm,
            basis = "LAB P95 · N=$n"
        )
        return metrics.copy(uncertainty = empirical)
    }

    private fun p95(values: List<Double>): Double? {
        if (values.size < 12) return null
        val s = values.filter { it.isFinite() }.sorted()
        if (s.size < 12) return null
        val index = (ceil(s.size * .95).toInt() - 1).coerceIn(0, s.lastIndex)
        return s[index]
    }
}

data class QuickImpactEstimate(
    val ballSpeedMps: Double?,
    val launchAngleDeg: Double?,
    val confidence: Double,
    val detectedAtNs: Long = System.nanoTime()
)

data class TvLivePoint(val tSec: Double, val x: Double, val y: Double)

/**
 * Starts a temporary physical roll on the TV at preview-impact time, before the HFR file
 * has finished recording/analysis. The measured shot later catches up and blends into it.
 */
object TvInstantRollRuntime {
    @Volatile private var startMs: Long = 0L
    @Volatile private var points: List<TvLivePoint> = emptyList()
    @Volatile private var estimate: QuickImpactEstimate? = null
    @Volatile private var generation: Long = 0L
    @Volatile private var handoffStartMs: Long = 0L
    @Volatile private var handoffDx = 0.0
    @Volatile private var handoffDy = 0.0

    fun begin(settings: GreenSettings, quick: QuickImpactEstimate?, fallback: GreenRead?) {
        val speed = quick?.ballSpeedMps?.takeIf { it in .20..4.8 }
            ?: fallback?.recommendedBallSpeedMps?.takeIf { it in .20..4.8 }
            ?: (.70 + settings.holeDistanceM * .18).coerceIn(.65, 3.4)
        val angle = quick?.launchAngleDeg?.takeIf { abs(it) <= 18.0 }
            ?: fallback?.recommendedLaunchAngleDeg?.takeIf { abs(it) <= 18.0 }
            ?: 0.0
        estimate = QuickImpactEstimate(speed, angle, quick?.confidence ?: .38)
        val physics = GreenPhysics()
        val copied = settings.copy()
        val metrics = ShotMetrics(
            ballSpeedMps = speed,
            launchAngleDeg = angle,
            headSpeedMps = null,
            faceAngleDeg = null,
            pathAngleDeg = null,
            faceToPathDeg = null,
            smash = null,
            impactOffsetMm = null,
            measuredAtNs = System.nanoTime(),
            confidence = quick?.confidence
        )
        val state = physics.launch(metrics, copied)
        val out = ArrayList<TvLivePoint>(600)
        var t = 0.0
        out += TvLivePoint(0.0, 0.0, 0.0)
        repeat(750) {
            val r = physics.step(state, copied, .012)
            t += .012
            if (it % 1 == 0) out += TvLivePoint(t, state.x, state.y)
            if (r != null) return@repeat
        }
        points = out
        startMs = SystemClock.uptimeMillis()
        handoffStartMs = 0L
        handoffDx = 0.0
        handoffDy = 0.0
        generation++
    }

    fun generation(): Long = generation
    fun isActive(): Boolean = startMs > 0L && points.isNotEmpty()
    fun quickEstimate(): QuickImpactEstimate? = estimate
    fun elapsedSec(): Double? = startMs.takeIf { it > 0L }?.let { (SystemClock.uptimeMillis() - it) / 1000.0 }

    fun predictedPosition(nowMs: Long = SystemClock.uptimeMillis()): Pair<Double, Double>? {
        if (!isActive()) return null
        val t = ((nowMs - startMs) / 1000.0).coerceAtLeast(0.0)
        return interpolate(t)
    }

    fun visibleTrail(nowMs: Long = SystemClock.uptimeMillis()): List<Pair<Double, Double>> {
        if (!isActive()) return emptyList()
        val t = ((nowMs - startMs) / 1000.0).coerceAtLeast(0.0)
        return points.asSequence().filter { it.tSec <= t }.map { it.x to it.y }.toList().takeLast(180)
    }

    fun handoff(actualX: Double, actualY: Double) {
        val predicted = predictedPosition()
        if (predicted != null) {
            handoffDx = predicted.first - actualX
            handoffDy = predicted.second - actualY
            handoffStartMs = SystemClock.uptimeMillis()
        }
        startMs = 0L
        points = emptyList()
    }

    fun displayPosition(actual: SimState?): Pair<Double, Double>? {
        if (actual == null) return predictedPosition()
        val hs = handoffStartMs
        if (hs <= 0L) return actual.x to actual.y
        val t = ((SystemClock.uptimeMillis() - hs) / 220.0).coerceIn(0.0, 1.0)
        val eased = 1.0 - (1.0 - t) * (1.0 - t)
        if (t >= 1.0) {
            handoffStartMs = 0L
            handoffDx = 0.0
            handoffDy = 0.0
            return actual.x to actual.y
        }
        val remain = 1.0 - eased
        return actual.x + handoffDx * remain to actual.y + handoffDy * remain
    }

    fun isAnimating(): Boolean = isActive() || handoffStartMs > 0L

    fun clear() {
        startMs = 0L
        points = emptyList()
        estimate = null
        handoffStartMs = 0L
        handoffDx = 0.0
        handoffDy = 0.0
    }

    private fun interpolate(t: Double): Pair<Double, Double>? {
        val list = points
        if (list.isEmpty()) return null
        if (t <= 0.0) return list.first().x to list.first().y
        if (t >= list.last().tSec) return list.last().x to list.last().y
        var lo = 0
        var hi = list.lastIndex
        while (lo + 1 < hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].tSec <= t) lo = mid else hi = mid
        }
        val a = list[lo]
        val b = list[hi]
        val f = ((t - a.tSec) / (b.tSec - a.tSec).coerceAtLeast(1e-6)).coerceIn(0.0, 1.0)
        return (a.x + (b.x - a.x) * f) to (a.y + (b.y - a.y) * f)
    }
}

data class V14SyntheticSequence(
    val frames: List<Bitmap>,
    val fps: Int,
    val homography: Homography,
    val truth: ShotMetrics
) {
    fun recycle() = frames.forEach { if (!it.isRecycled) it.recycle() }
}

/** Synthetic camera clip with the same white-ball + orange/blue putter markers as real HFR. */
object V14SyntheticCamera {
    fun build(
        speedMps: Double = 1.42,
        launchDeg: Double = 1.35,
        faceDeg: Double = .70,
        pathDeg: Double = .45,
        fps: Int = 240
    ): V14SyntheticSequence {
        val width = 960
        val height = 540
        val imageCorners = listOf(
            PointF(170f, 470f), PointF(790f, 470f), PointF(700f, 80f), PointF(260f, 80f)
        )
        val realCorners = listOf(
            PointF(-22.5f, 0f), PointF(22.5f, 0f), PointF(22.5f, 100f), PointF(-22.5f, 100f)
        )
        val h = Homography.fromFourPoints(imageCorners, realCorners) ?: error("synthetic homography")
        val truth = ShotMetrics(
            ballSpeedMps = speedMps,
            launchAngleDeg = launchDeg,
            headSpeedMps = speedMps / 1.50,
            faceAngleDeg = faceDeg,
            pathAngleDeg = pathDeg,
            faceToPathDeg = faceDeg - pathDeg,
            smash = 1.50,
            impactOffsetMm = .4,
            measuredAtNs = 0L,
            confidence = .99
        )
        val frames = ArrayList<Bitmap>(96)
        val impact = 44
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val angle = Math.toRadians(launchDeg)
        val path = Math.toRadians(pathDeg)
        val face = Math.toRadians(faceDeg)
        for (i in 0 until 96) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap)
            c.drawColor(Color.rgb(16, 58, 34))
            p.color = Color.argb(45, 220, 255, 225)
            p.strokeWidth = 2f
            for (lane in -4..4) {
                val a = h.inverseMap(PointF((lane * 5).toFloat(), 0f))
                val b = h.inverseMap(PointF((lane * 5).toFloat(), 100f))
                if (a != null && b != null) c.drawLine(a.x, a.y, b.x, b.y, p)
            }
            val t = (i - impact).toDouble() / fps
            val ballForwardCm = if (t <= 0.0) 0.0 else speedMps * t * 100.0
            val ballWorld = PointF(
                (sin(angle) * ballForwardCm).toFloat(),
                (cos(angle) * ballForwardCm).toFloat()
            )
            val ballPx = h.inverseMap(ballWorld) ?: PointF(width * .5f, height * .75f)

            // Putter approaches through impact. The two color markers encode face angle.
            val headT = ((i - (impact - 28)).coerceIn(0, 28)) / 28.0
            val headCenterY = -14.0 + 14.0 * headT
            val headCenterX = sin(path) * (headCenterY + 14.0) * .20
            val half = 5.6
            val hx = cos(face) * half
            val hy = sin(face) * half
            val heel = h.inverseMap(PointF((headCenterX - hx).toFloat(), (headCenterY - hy).toFloat()))
            val toe = h.inverseMap(PointF((headCenterX + hx).toFloat(), (headCenterY + hy).toFloat()))
            if (heel != null) {
                p.color = Color.rgb(235, 126, 40)
                c.drawCircle(heel.x, heel.y, 10f, p)
            }
            if (toe != null) {
                p.color = Color.rgb(55, 122, 244)
                c.drawCircle(toe.x, toe.y, 10f, p)
            }

            p.color = Color.WHITE
            c.drawCircle(ballPx.x, ballPx.y, 12f, p)
            // Rotating dark dot: optional marked-ball spin channel.
            if (i >= impact) {
                val distanceM = ballForwardCm / 100.0
                val rollOmega = speedMps / .02135
                val theta = rollOmega * max(0.0, t - .035) * .82
                p.color = Color.rgb(38, 38, 38)
                c.drawCircle(
                    ballPx.x + cos(theta).toFloat() * 7.5f,
                    ballPx.y + sin(theta).toFloat() * 7.5f,
                    3.4f,
                    p
                )
            }
            frames += bitmap
        }
        return V14SyntheticSequence(frames, fps, h, truth)
    }
}

data class V14QrObservation(val text: String?, val center: PointF, val area: Long)

/** Supports legacy four PV1 corners and optional PV2 over-determined calibration points. */
object V14MarkerResolver {
    private data class Marker(val id: String, val image: PointF, val real: PointF)

    fun resolve(observations: List<V14QrObservation>): ResolvedMarkerLayout? {
        val pv2 = observations.mapNotNull { o -> parse(o, "PV2") }
        if (pv2.size >= 4) {
            val unique = pv2.distinctBy { it.id }
            if (unique.size >= 4) {
                val anchors = chooseAnchors(unique) ?: return null
                return ResolvedMarkerLayout(
                    imagePoints = anchors.map { it.image },
                    realPointsCm = anchors.map { it.real },
                    source = if (unique.size >= 6) "PV2-${unique.size}PT" else "PV2-4PT",
                    fitImagePoints = unique.map { it.image },
                    fitRealPointsCm = unique.map { it.real }
                )
            }
        }
        val pv1 = observations.mapNotNull { o -> parse(o, "PV1") }.associateBy { it.id }
        val roles = listOf("BL", "BR", "TR", "TL")
        if (roles.all { pv1.containsKey(it) }) {
            val markers = roles.map { pv1.getValue(it) }
            return ResolvedMarkerLayout(markers.map { it.image }, markers.map { it.real }, "PV1")
        }
        val generic = observations.sortedByDescending { it.area }.take(4).map { it.center }
        return if (generic.size == 4) MarkerLayoutResolver.fromGenericFour(generic) else null
    }

    private fun parse(o: V14QrObservation, prefix: String): Marker? {
        val parts = o.text?.split('|') ?: return null
        if (parts.size != 4 || parts[0] != prefix) return null
        val x = parts[2].toFloatOrNull() ?: return null
        val y = parts[3].toFloatOrNull() ?: return null
        return Marker(parts[1], o.center, PointF(x, y))
    }

    private fun chooseAnchors(markers: List<Marker>): List<Marker>? {
        val byId = markers.associateBy { it.id }
        val named = listOf("BL", "BR", "TR", "TL").mapNotNull(byId::get)
        if (named.size == 4) return named
        val minX = markers.minOf { it.real.x }
        val maxX = markers.maxOf { it.real.x }
        val minY = markers.minOf { it.real.y }
        val maxY = markers.maxOf { it.real.y }
        fun closest(x: Float, y: Float) = markers.minByOrNull { hypot((it.real.x - x).toDouble(), (it.real.y - y).toDouble()) }
        return listOfNotNull(closest(minX, minY), closest(maxX, minY), closest(maxX, maxY), closest(minX, maxY)).distinctBy { it.id }.takeIf { it.size == 4 }
    }
}
