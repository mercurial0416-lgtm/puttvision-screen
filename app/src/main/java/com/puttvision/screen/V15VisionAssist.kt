package com.puttvision.screen

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


data class V15SetupAdvice(
    val score: Int,
    val horizontalShiftPct: Double,
    val verticalShiftPct: Double,
    val rollDeg: Double,
    val perspectiveSkewPct: Double,
    val coveragePct: Double,
    val ready: Boolean,
    val primaryHint: String,
    val allHints: List<String>
)

/**
 * Turns the four calibration points into plain-language phone placement instructions.
 * It is independent from QR payload type, so PV1/PV2/generic-four-code layouts all work.
 */
object V15SetupAssistant {
    fun evaluate(frameWidth: Int, frameHeight: Int, imagePointsRaw: List<PointF>): V15SetupAdvice? {
        if (frameWidth <= 0 || frameHeight <= 0 || imagePointsRaw.size < 4) return null
        val points = orderCorners(imagePointsRaw.take(4))
        val tl = points[0]
        val tr = points[1]
        val br = points[2]
        val bl = points[3]
        val cx = points.map { it.x.toDouble() }.average()
        val cy = points.map { it.y.toDouble() }.average()
        val shiftX = (cx / frameWidth - .5) * 100.0
        val shiftY = (cy / frameHeight - .57) * 100.0
        val topW = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble()).coerceAtLeast(1.0)
        val bottomW = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble()).coerceAtLeast(1.0)
        val perspective = (topW / bottomW - 1.0) * 100.0
        val roll = Math.toDegrees(atan2((tr.y - tl.y).toDouble(), (tr.x - tl.x).toDouble()))
        val minX = points.minOf { it.x.toDouble() }
        val maxX = points.maxOf { it.x.toDouble() }
        val minY = points.minOf { it.y.toDouble() }
        val maxY = points.maxOf { it.y.toDouble() }
        val coverage = (((maxX - minX) * (maxY - minY)) / (frameWidth.toDouble() * frameHeight) * 100.0)
            .coerceIn(0.0, 100.0)

        val hints = ArrayList<String>()
        if (abs(shiftX) > 4.0) hints += if (shiftX > 0) "폰을 오른쪽으로 ${abs(shiftX).roundToInt()}% 이동" else "폰을 왼쪽으로 ${abs(shiftX).roundToInt()}% 이동"
        if (shiftY < -8.0) hints += "폰을 매트 쪽으로 조금 낮추기"
        if (shiftY > 8.0) hints += "폰을 조금 높이거나 뒤로 빼기"
        if (abs(roll) > 2.5) hints += if (roll > 0) "폰을 반시계 방향으로 ${"%.1f".format(abs(roll))}° 회전" else "폰을 시계 방향으로 ${"%.1f".format(abs(roll))}° 회전"
        if (perspective < -48.0) hints += "카메라 각도가 너무 낮음 · 위에서 더 내려다보게 조정"
        if (perspective > 15.0) hints += "마커 원근이 역전됨 · 폰 방향 확인"
        if (coverage < 18.0) hints += "매트가 너무 작게 보임 · 폰을 가까이 이동"
        if (coverage > 74.0) hints += "마커가 화면 끝에 가까움 · 폰을 조금 뒤로 이동"

        var score = 100.0
        score -= (abs(shiftX) * 1.35).coerceAtMost(22.0)
        score -= (abs(shiftY) * .85).coerceAtMost(16.0)
        score -= (abs(roll) * 3.0).coerceAtMost(20.0)
        if (perspective !in -48.0..15.0) score -= 15.0
        if (coverage !in 18.0..74.0) score -= 18.0
        val finalScore = score.roundToInt().coerceIn(0, 100)
        val ready = finalScore >= 82 && hints.none { it.contains("역전") }
        val primary = if (ready) "설치 양호 · 그대로 퍼팅 가능" else hints.firstOrNull() ?: "카메라 위치를 조금 조정"
        return V15SetupAdvice(finalScore, shiftX, shiftY, roll, perspective, coverage, ready, primary, hints)
    }

    private fun orderCorners(points: List<PointF>): List<PointF> {
        val centerX = points.map { it.x }.average().toFloat()
        val centerY = points.map { it.y }.average().toFloat()
        val top = points.filter { it.y <= centerY }.sortedBy { it.x }.toMutableList()
        val bottom = points.filter { it.y > centerY }.sortedBy { it.x }.toMutableList()
        if (top.size == 2 && bottom.size == 2) return listOf(top[0], top[1], bottom[1], bottom[0])
        val sorted = points.sortedBy { atan2((it.y - centerY).toDouble(), (it.x - centerX).toDouble()) }
        val tl = sorted.minByOrNull { it.x + it.y } ?: points[0]
        val br = sorted.maxByOrNull { it.x + it.y } ?: points[2]
        val remaining = points.filter { it !== tl && it !== br }
        val tr = remaining.minByOrNull { it.y - it.x } ?: remaining.first()
        val bl = remaining.firstOrNull { it !== tr } ?: remaining.last()
        return listOf(tl, tr, br, bl)
    }
}


data class V15MatDetection(
    val boundsPx: RectF,
    val cornersPx: List<PointF>,
    val confidence: Double,
    val source: String,
    val hint: String
)

/**
 * Markerless mat fallback. It intentionally refuses weak detections rather than inventing scale.
 * The result becomes metric only when the caller supplies the real mat width/length.
 */
object V15MatDetector {
    fun detect(source: Bitmap): V15MatDetection? {
        if (source.width < 120 || source.height < 120) return null
        val scale = if (source.width > 720) 720f / source.width else 1f
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
            val xs = ArrayList<Int>()
            val ys = ArrayList<Int>()
            var y = (h * .08).toInt()
            while (y < h) {
                var x = 0
                while (x < w) {
                    val c = pixels[y * w + x]
                    val r = c shr 16 and 255
                    val g = c shr 8 and 255
                    val b = c and 255
                    val mx = max(r, max(g, b))
                    val mn = min(r, min(g, b))
                    val avg = (r + g + b) / 3
                    val greenMat = g >= 42 && g > r * 1.04 && g > b * 1.02 && (g - min(r, b)) >= 8
                    val darkNeutralMat = avg in 24..108 && mx - mn <= 30
                    if (greenMat || darkNeutralMat) {
                        xs += x
                        ys += y
                    }
                    x += 4
                }
                y += 4
            }
            val minSamples = (w * h / 16 * .025).toInt().coerceAtLeast(180)
            if (xs.size < minSamples) return null
            xs.sort(); ys.sort()
            fun q(list: List<Int>, p: Double): Int = list[(list.lastIndex * p).roundToInt().coerceIn(0, list.lastIndex)]
            val left = q(xs, .05)
            val right = q(xs, .95)
            val top = q(ys, .04)
            val bottom = q(ys, .97)
            val bw = (right - left).coerceAtLeast(1)
            val bh = (bottom - top).coerceAtLeast(1)
            if (bw < w * .12 || bh < h * .24) return null

            var inside = 0
            var matching = 0
            var yy = top
            while (yy <= bottom) {
                var xx = left
                while (xx <= right) {
                    inside++
                    val c = pixels[yy * w + xx]
                    val r = c shr 16 and 255
                    val g = c shr 8 and 255
                    val b = c and 255
                    val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val avg = (r + g + b) / 3
                    if ((g >= 42 && g > r * 1.04 && g > b * 1.02 && g - min(r, b) >= 8) || (avg in 24..108 && mx - mn <= 30)) matching++
                    xx += 6
                }
                yy += 6
            }
            val density = matching.toDouble() / inside.coerceAtLeast(1)
            val aspect = bh.toDouble() / bw
            var confidence = .38 + density * .38
            if (aspect in .65..5.5) confidence += .12
            if (bottom > h * .72) confidence += .08
            confidence = confidence.coerceIn(0.0, .96)
            if (confidence < .58) return null
            val inv = 1f / scale
            val bounds = RectF(left * inv, top * inv, right * inv, bottom * inv)
            val corners = listOf(
                PointF(bounds.left, bounds.top), PointF(bounds.right, bounds.top),
                PointF(bounds.right, bounds.bottom), PointF(bounds.left, bounds.bottom)
            )
            return V15MatDetection(
                bounds,
                corners,
                confidence,
                "MARKERLESS_MAT",
                if (confidence >= .78) "매트 자동 인식 완료" else "매트 후보 감지 · QR 1회 보정 권장"
            )
        } finally {
            if (bmp !== source && !bmp.isRecycled) bmp.recycle()
        }
    }

    fun homography(
        detection: V15MatDetection,
        frameInfo: FrameInfo,
        matWidthCm: Double,
        matLengthCm: Double
    ): Homography? {
        if (detection.confidence < .62 || matWidthCm !in 20.0..300.0 || matLengthCm !in 50.0..1000.0) return null
        val c = detection.cornersPx
        if (c.size != 4) return null
        // detection is TL,TR,BR,BL. Geometry expects metric X across the mat and Y toward the hole.
        val image = listOf(c[3], c[2], c[1], c[0]) // BL,BR,TR,TL
        val half = (matWidthCm / 2.0).toFloat()
        val length = matLengthCm.toFloat()
        val real = listOf(
            PointF(-half, 0f), PointF(half, 0f), PointF(half, length), PointF(-half, length)
        )
        return Homography.fromPoints(image, real, frameInfo)
    }
}


data class V15CupDetection(
    val centerPx: PointF,
    val radiusPx: Float,
    val confidence: Double,
    val darkness: Double,
    val roundness: Double
)

/** Detects the physical cup/dark target in a bounded ROI; useful for real-hole confirmation. */
object V15HoleCupDetector {
    fun detect(source: Bitmap, expected: PointF? = null, searchRadiusPx: Int? = null): V15CupDetection? {
        if (source.width < 80 || source.height < 80) return null
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val radius = searchRadiusPx ?: min(w, h) / 3
        val x0 = if (expected == null) 12 else max(12, expected.x.toInt() - radius)
        val x1 = if (expected == null) w - 13 else min(w - 13, expected.x.toInt() + radius)
        val y0 = if (expected == null) 12 else max(12, expected.y.toInt() - radius)
        val y1 = if (expected == null) h - 13 else min(h - 13, expected.y.toInt() + radius)
        var best: V15CupDetection? = null
        var y = y0
        while (y <= y1) {
            var x = x0
            while (x <= x1) {
                val centerLuma = luma(pixels[y * w + x])
                if (centerLuma < 92) {
                    for (r in intArrayOf(6, 9, 12, 16, 21)) {
                        if (x - r < 1 || x + r >= w - 1 || y - r < 1 || y + r >= h - 1) continue
                        var darkInside = 0
                        var insideCount = 0
                        var ring = 0.0
                        var ringSq = 0.0
                        var ringCount = 0
                        for (oy in -r..r step max(1, r / 4)) {
                            for (ox in -r..r step max(1, r / 4)) {
                                val d = hypot(ox.toDouble(), oy.toDouble())
                                val lum = luma(pixels[(y + oy) * w + (x + ox)])
                                if (d <= r * .62) {
                                    insideCount++
                                    if (lum < 105) darkInside++
                                } else if (d in r * .82..r * 1.08) {
                                    ring += lum
                                    ringSq += lum * lum
                                    ringCount++
                                }
                            }
                        }
                        if (insideCount < 5 || ringCount < 6) continue
                        val darkness = darkInside.toDouble() / insideCount
                        val ringMean = ring / ringCount
                        val ringVar = max(0.0, ringSq / ringCount - ringMean * ringMean)
                        val roundness = (1.0 - min(1.0, ringVar / 1600.0)).coerceIn(0.0, 1.0)
                        val contrast = ((ringMean - centerLuma) / 90.0).coerceIn(0.0, 1.0)
                        val proximity = expected?.let {
                            (1.0 - hypot((x - it.x).toDouble(), (y - it.y).toDouble()) / radius.coerceAtLeast(1)).coerceIn(0.0, 1.0)
                        } ?: .55
                        val confidence = (darkness * .38 + roundness * .24 + contrast * .28 + proximity * .10).coerceIn(0.0, 1.0)
                        if (confidence >= .60 && (best == null || confidence > best!!.confidence)) {
                            best = V15CupDetection(PointF(x.toFloat(), y.toFloat()), r.toFloat(), confidence, darkness, roundness)
                        }
                    }
                }
                x += 4
            }
            y += 4
        }
        return best
    }

    private fun luma(c: Int): Double {
        val r = c shr 16 and 255
        val g = c shr 8 and 255
        val b = c and 255
        return r * .2126 + g * .7152 + b * .0722
    }
}


data class V15TrajectoryVerdict(
    val accepted: Boolean,
    val score: Double,
    val reason: String
)

/**
 * A second gate after pixel detection. It rejects stationary bright objects and single-frame jumps
 * before they are allowed to become a launch measurement.
 */
object V15TrajectoryGate {
    fun validate(pointsRaw: List<V14TimedPoint>): V15TrajectoryVerdict {
        val points = pointsRaw.filter { it.tSec.isFinite() && it.xCm.isFinite() && it.yCm.isFinite() }.sortedBy { it.tSec }
        if (points.size < 4) return V15TrajectoryVerdict(false, 0.0, "추적점 부족")
        val duration = points.last().tSec - points.first().tSec
        if (duration <= .006) return V15TrajectoryVerdict(false, .1, "시간 간격 부족")
        val steps = points.zipWithNext().map { (a, b) -> hypot(b.xCm - a.xCm, b.yCm - a.yCm) }
        val total = steps.sum()
        if (total < .8) return V15TrajectoryVerdict(false, .15, "정지 물체")
        if (steps.maxOrNull() ?: 0.0 > 35.0) return V15TrajectoryVerdict(false, .2, "단일 프레임 점프")
        val forward = points.zipWithNext().count { (a, b) -> b.yCm >= a.yCm - .8 }
        val forwardRatio = forward.toDouble() / (points.size - 1)
        val fit = V14RobustKinematics.fit(points)
        if (fit == null) return V15TrajectoryVerdict(false, .35, "속도 모델 불일치")
        val rmsScore = (1.0 - fit.rmsCm / 3.0).coerceIn(0.0, 1.0)
        val score = (forwardRatio * .42 + rmsScore * .38 + min(1.0, points.size / 10.0) * .20).coerceIn(0.0, 1.0)
        return V15TrajectoryVerdict(score >= .58, score, if (score >= .58) "연속 궤적 확인" else "궤적 연속성 낮음")
    }
}
