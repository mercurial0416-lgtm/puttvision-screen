package com.puttvision.screen

import android.graphics.Bitmap
import android.graphics.PointF
import android.media.MediaMetadataRetriever
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class HfrAnalysisResult(
    val metrics: ShotMetrics,
    val fps: Int,
    val impactFrame: Int,
    val analyzedFrames: Int
)

class HfrVideoAnalyzer {

    private data class Detection(
        val ballPx: PointF?,
        val heelPx: PointF?,
        val toePx: PointF?
    )

    private data class Sample(
        val frame: Int,
        val ballCm: PointF?,
        val heelCm: PointF?,
        val toeCm: PointF?
    )

    private data class HeadFrame(
        val frame: Int,
        val center: PointF,
        val heel: PointF,
        val toe: PointF
    )

    private data class TempoData(
        val backswingMs: Double?,
        val downswingMs: Double?,
        val ratio: Double?,
        val backswingLengthCm: Double?,
        val peakAcceleration: Double?
    )

    private data class MatData(
        val rawBallSpeedMps: Double,
        val correctedImpactSpeedMps: Double,
        val decelMps2: Double?,
        val stimpM: Double?
    )

    fun analyze(
        file: File,
        requestedFps: Int,
        onProgress: (String) -> Unit = {}
    ): HfrAnalysisResult? {
        if (!file.exists() || file.length() == 0L) return null
        if (android.os.Build.VERSION.SDK_INT < 28) return null

        val mmr = MediaMetadataRetriever()

        try {
            mmr.setDataSource(file.absolutePath)

            val frameCount = mmr.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT
            )?.toIntOrNull() ?: return null

            val captureFps = mmr.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
            )?.toFloatOrNull()?.toInt()

            val fps = (captureFps ?: requestedFps).coerceAtLeast(30)

            onProgress("${fps}fps / ${frameCount}f · HFR 좌표 보정")

            val homography = findVideoHomography(mmr, frameCount) ?: return null

            // Coarse scan at ~40 samples/sec to find launch.
            val coarseStep = max(1, fps / 40)
            var origin: PointF? = null
            var previous: PointF? = null
            var coarseImpact = -1
            var i = 0

            while (i < frameCount) {
                val bmp = safeFrame(mmr, i)

                if (bmp != null) {
                    val d = detect(bmp, homography, previous, false)
                    val ball = d.ballPx?.let { homography.map(it) }

                    if (ball != null && ball.x.isFinite() && ball.y.isFinite()) {
                        if (origin == null && abs(ball.x) < 30f && ball.y in -20f..40f) {
                            origin = PointF(ball.x, ball.y)
                        }

                        if (origin != null) {
                            val dist = hypot(
                                (ball.x - origin!!.x).toDouble(),
                                (ball.y - origin!!.y).toDouble()
                            )

                            if (dist >= 1.0) {
                                coarseImpact = i
                                break
                            }
                        }

                        previous = ball
                    }
                }

                i += coarseStep
            }

            val startBall = origin ?: return null
            if (coarseImpact < 0) return null

            // v0.4 keeps substantially more pre-impact frames so the full
            // backswing / transition / downswing can be reconstructed.
            val preFrames = max(30, (fps * 0.90).toInt())
            val postFrames = max(45, (fps * 0.70).toInt())
            val start = max(0, coarseImpact - preFrames)
            val end = min(frameCount - 1, coarseImpact + postFrames)

            onProgress("스트로크+임팩트 정밀 분석 ${start}~${end}f")

            val samples = ArrayList<Sample>(end - start + 1)
            var prevBall = startBall

            for (frame in start..end) {
                val bmp = safeFrame(mmr, frame) ?: continue
                val d = detect(bmp, homography, prevBall, true)

                val ballCm = d.ballPx?.let { homography.map(it) }?.takeIf {
                    it.x.isFinite() && it.y.isFinite()
                }

                val heelCm = d.heelPx?.let { homography.map(it) }?.takeIf {
                    it.x.isFinite() && it.y.isFinite()
                }

                val toeCm = d.toePx?.let { homography.map(it) }?.takeIf {
                    it.x.isFinite() && it.y.isFinite()
                }

                if (ballCm != null) prevBall = ballCm
                samples += Sample(frame, ballCm, heelCm, toeCm)
            }

            val calculated = calculate(samples, startBall, fps) ?: return null

            return HfrAnalysisResult(
                metrics = calculated.first,
                fps = fps,
                impactFrame = calculated.second,
                analyzedFrames = samples.size
            )
        } finally {
            mmr.release()
        }
    }

    private fun safeFrame(
        mmr: MediaMetadataRetriever,
        index: Int
    ): Bitmap? =
        try {
            mmr.getFrameAtIndex(index)
        } catch (_: Throwable) {
            null
        }


private fun findVideoHomography(
    mmr: MediaMetadataRetriever,
    frameCount: Int
): Homography? {
    val indices = listOf(
        0,
        min(2, frameCount - 1),
        min(8, frameCount - 1),
        min(16, frameCount - 1)
    ).distinct()

    for (i in indices) {
        val bmp =
            safeFrame(
                mmr,
                i
            ) ?: continue

        val layout =
            scanMarkerLayoutBlocking(
                bmp
            ) ?: continue

        Homography.fromFourPoints(
            layout.imagePoints,
            layout.realPointsCm
        )?.let {
            return it
        }
    }

    return null
}

private fun scanMarkerLayoutBlocking(
    bitmap: Bitmap
): ResolvedMarkerLayout? {
    val scanner =
        BarcodeScanning.getClient()

    val latch =
        CountDownLatch(1)

    var resolved:
        ResolvedMarkerLayout? = null

    try {
        scanner.process(
            InputImage.fromBitmap(
                bitmap,
                0
            )
        )
            .addOnSuccessListener { codes ->
                val pv =
                    HashMap<
                        String,
                        Pair<PointF, PointF>
                        >()

                val generic =
                    ArrayList<
                        Pair<PointF, Long>
                        >()

                for (code in codes) {
                    val box =
                        code.boundingBox
                            ?: continue

                    val center =
                        PointF(
                            box.exactCenterX(),
                            box.exactCenterY()
                        )

                    val area =
                        box.width()
                            .toLong() *
                            box.height()
                            .toLong()

                    generic +=
                        center to area

                    val text =
                        code.rawValue
                            ?: continue

                    val parts =
                        text.split("|")

                    if (
                        parts.size == 4 &&
                        parts[0] == "PV1" &&
                        parts[1] in setOf(
                            "BL",
                            "BR",
                            "TR",
                            "TL"
                        )
                    ) {
                        val x =
                            parts[2]
                                .toFloatOrNull()

                        val y =
                            parts[3]
                                .toFloatOrNull()

                        if (
                            x != null &&
                            y != null
                        ) {
                            pv[parts[1]] =
                                center to
                                    PointF(
                                        x,
                                        y
                                    )
                        }
                    }
                }

                val roles =
                    listOf(
                        "BL",
                        "BR",
                        "TR",
                        "TL"
                    )

                resolved =
                    if (
                        roles.all {
                            pv.containsKey(
                                it
                            )
                        }
                    ) {
                        ResolvedMarkerLayout(
                            imagePoints =
                                roles.map {
                                    pv.getValue(
                                        it
                                    ).first
                                },
                            realPointsCm =
                                roles.map {
                                    pv.getValue(
                                        it
                                    ).second
                                },
                            source =
                                "PV1"
                        )
                    } else {
                        val centers =
                            generic
                                .sortedByDescending {
                                    it.second
                                }
                                .take(4)
                                .map {
                                    it.first
                                }

                        if (
                            centers.size == 4
                        ) {
                            MarkerLayoutResolver
                                .fromGenericFour(
                                    centers
                                )
                        } else {
                            null
                        }
                    }
            }
            .addOnCompleteListener {
                latch.countDown()
            }

        latch.await(
            1500,
            TimeUnit.MILLISECONDS
        )
    } finally {
        scanner.close()
    }

    return resolved
}

    private fun detect(
        source: Bitmap,
        h: Homography,
        previousBallCm: PointF?,
        wantPutter: Boolean
    ): Detection {
        val maxWidth = 960
        val scale =
            if (source.width > maxWidth) maxWidth.toFloat() / source.width else 1f

        val bmp =
            if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt(),
                    (source.height * scale).toInt(),
                    false
                )
            } else source

        val width = bmp.width
        val height = bmp.height
        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)

        fun originalPoint(x: Float, y: Float): PointF =
            PointF(x / scale, y / scale)

        var bestBall: PointF? = null
        var bestBallScore = -1.0

        var y = 6

        while (y < height - 6) {
            var x = 6

            while (x < width - 6) {
                val c = pixels[y * width + x]
                val r = (c shr 16) and 255
                val g = (c shr 8) and 255
                val b = c and 255

                if (
                    min(r, min(g, b)) > 205 &&
                    abs(r - g) < 30 &&
                    abs(g - b) < 30
                ) {
                    var hit = 0
                    var total = 0
                    var oy = -5

                    while (oy <= 5) {
                        var ox = -5

                        while (ox <= 5) {
                            if (ox * ox + oy * oy <= 25) {
                                val cc = pixels[(y + oy) * width + (x + ox)]
                                val rr = (cc shr 16) and 255
                                val gg = (cc shr 8) and 255
                                val bb = cc and 255

                                if (
                                    min(rr, min(gg, bb)) > 190 &&
                                    abs(rr - gg) < 42 &&
                                    abs(gg - bb) < 42
                                ) {
                                    hit++
                                }

                                total++
                            }

                            ox += 2
                        }

                        oy += 2
                    }

                    val ratio = hit.toDouble() / max(1, total)

                    if (ratio > 0.42) {
                        val imagePoint = originalPoint(x.toFloat(), y.toFloat())
                        val real = h.map(imagePoint)

                        if (real.x.isFinite() && real.y.isFinite()) {
                            var score = ratio

                            if (previousBallCm != null) {
                                val distance = hypot(
                                    (real.x - previousBallCm.x).toDouble(),
                                    (real.y - previousBallCm.y).toDouble()
                                )

                                score += max(
                                    0.0,
                                    1.0 - distance / 35.0
                                ) * 1.7
                            } else if (
                                abs(real.x) < 30 &&
                                real.y in -20f..55f
                            ) {
                                score += 1.0
                            }

                            if (score > bestBallScore) {
                                bestBallScore = score
                                bestBall = imagePoint
                            }
                        }
                    }
                }

                x += 3
            }

            y += 3
        }

        if (!wantPutter) {
            return Detection(bestBall, null, null)
        }

        fun colorCentroid(orange: Boolean): PointF? {
            var sumX = 0.0
            var sumY = 0.0
            var count = 0

            var yy = 0

            while (yy < height) {
                var xx = 0

                while (xx < width) {
                    val c = pixels[yy * width + xx]
                    val r = (c shr 16) and 255
                    val g = (c shr 8) and 255
                    val b = c and 255

                    val match =
                        if (orange) {
                            r > 155 &&
                                g in 55..205 &&
                                b < 135 &&
                                r > g * 1.10 &&
                                g > b * 1.02
                        } else {
                            b > 135 &&
                                r < 160 &&
                                b > r * 1.12 &&
                                b > g * 1.02
                        }

                    if (match) {
                        sumX += xx
                        sumY += yy
                        count++
                    }

                    xx += 3
                }

                yy += 3
            }

            if (count < 5) return null

            return originalPoint(
                (sumX / count).toFloat(),
                (sumY / count).toFloat()
            )
        }

        return Detection(
            ballPx = bestBall,
            heelPx = colorCentroid(true),
            toePx = colorCentroid(false)
        )
    }

    private fun calculate(
        samples: List<Sample>,
        origin: PointF,
        fps: Int
    ): Pair<ShotMetrics, Int>? {
        if (samples.size < 8) return null

        var impactPos = -1

        for (i in samples.indices) {
            val ball = samples[i].ballCm ?: continue
            val moved = hypot(
                (ball.x - origin.x).toDouble(),
                (ball.y - origin.y).toDouble()
            )

            if (moved >= 0.8) {
                impactPos = i
                break
            }
        }

        if (impactPos < 0) return null

        val impactFrame = samples[impactPos].frame

        // Ball speed at roughly 10cm. Then estimate mat deceleration from
        // subsequent high-speed positions and back-extrapolate to impact.
        var launchSample: Sample? = null

        for (i in impactPos until samples.size) {
            val ball = samples[i].ballCm ?: continue
            val d = hypot(
                (ball.x - origin.x).toDouble(),
                (ball.y - origin.y).toDouble()
            )

            if (d >= 10.0) {
                launchSample = samples[i]
                break
            }
        }

        val target = launchSample ?: return null
        val targetBall = target.ballCm ?: return null
        val frameDelta =
            (target.frame - impactFrame).coerceAtLeast(1)

        val dt = frameDelta.toDouble() / fps
        val dx = (targetBall.x - origin.x).toDouble()
        val dy = (targetBall.y - origin.y).toDouble()
        val rawSpeed = (hypot(dx, dy) / 100.0) / dt
        val launchAngle = Math.toDegrees(atan2(dx, dy))

        val matData = estimateMat(samples, impactPos, fps, rawSpeed)
        val correctedBallSpeed = matData.correctedImpactSpeedMps

        val head = samples
            .take(impactPos + 1)
            .mapNotNull { s ->
                val heel = s.heelCm ?: return@mapNotNull null
                val toe = s.toeCm ?: return@mapNotNull null

                HeadFrame(
                    frame = s.frame,
                    center = PointF(
                        (heel.x + toe.x) / 2f,
                        (heel.y + toe.y) / 2f
                    ),
                    heel = heel,
                    toe = toe
                )
            }

        val tempo = analyzeTempo(head, impactFrame, fps)

        // Last ~40ms immediately before impact.
        val impactHeadFrames =
            head.filter { it.frame <= impactFrame }
                .takeLast(max(5, (fps * 0.040).toInt()))

        var headSpeed: Double? = null
        var pathAngle: Double? = null
        var faceAngle: Double? = null
        var impactCenter: PointF? = null

        if (impactHeadFrames.size >= 2) {
            val a = impactHeadFrames.first()
            val b = impactHeadFrames.last()
            val hdt =
                (b.frame - a.frame).coerceAtLeast(1).toDouble() / fps

            val hdx = (b.center.x - a.center.x).toDouble()
            val hdy = (b.center.y - a.center.y).toDouble()

            headSpeed =
                (hypot(hdx, hdy) / 100.0) / hdt

            pathAngle =
                Math.toDegrees(atan2(hdx, hdy))

            val fx = (b.toe.x - b.heel.x).toDouble()
            val fy = (b.toe.y - b.heel.y).toDouble()

            faceAngle =
                normalizeFaceAngle(
                    Math.toDegrees(atan2(fy, fx))
                )

            impactCenter = b.center
        }

        val faceToPath =
            if (faceAngle != null && pathAngle != null) {
                faceAngle - pathAngle
            } else null

        val smash =
            if (headSpeed != null && headSpeed > 0.05) {
                correctedBallSpeed / headSpeed
            } else null

        val impactOffsetMm =
            impactCenter?.let {
                (origin.x - it.x) * 10.0
            }

        val ballDetected =
            samples.count { it.ballCm != null }.toDouble() / samples.size

        val headDetected =
            samples.take(impactPos + 1)
                .count { it.heelCm != null && it.toeCm != null }
                .toDouble() /
                max(1, impactPos + 1)

        val confidence =
            (
                0.35 +
                    ballDetected.coerceIn(0.0, 1.0) * 0.35 +
                    headDetected.coerceIn(0.0, 1.0) * 0.25 +
                    (if (matData.decelMps2 != null) 0.05 else 0.0)
                ).coerceIn(0.0, 1.0)

        return ShotMetrics(
            ballSpeedMps = correctedBallSpeed,
            launchAngleDeg = launchAngle,
            headSpeedMps = headSpeed,
            faceAngleDeg = faceAngle,
            pathAngleDeg = pathAngle,
            faceToPathDeg = faceToPath,
            smash = smash,
            impactOffsetMm = impactOffsetMm,
            measuredAtNs = System.nanoTime(),
            backswingMs = tempo.backswingMs,
            downswingMs = tempo.downswingMs,
            tempoRatio = tempo.ratio,
            backswingLengthCm = tempo.backswingLengthCm,
            peakHeadAccelerationMps2 = tempo.peakAcceleration,
            rawBallSpeedMps = matData.rawBallSpeedMps,
            estimatedMatDecelMps2 = matData.decelMps2,
            estimatedMatStimpM = matData.stimpM,
            confidence = confidence
        ) to impactFrame
    }

    private fun analyzeTempo(
        head: List<HeadFrame>,
        impactFrame: Int,
        fps: Int
    ): TempoData {
        val pre = head.filter { it.frame <= impactFrame }
        if (pre.size < 8) {
            return TempoData(null, null, null, null, null)
        }

        // Target direction is +Y, so backswing typically reaches the smallest Y.
        val transitionIndex =
            pre.indices.minByOrNull { pre[it].center.y } ?: return TempoData(
                null, null, null, null, null
            )

        if (transitionIndex <= 1 || transitionIndex >= pre.lastIndex) {
            return TempoData(null, null, null, null, null)
        }

        // Find motion start by scanning backward from transition and locating
        // the last mostly-stationary segment before the head begins moving.
        var startIndex = 0
        val speedThresholdMps = 0.045

        for (i in transitionIndex - 1 downTo 1) {
            val a = pre[i - 1]
            val b = pre[i]
            val dt = (b.frame - a.frame).coerceAtLeast(1).toDouble() / fps
            val speed =
                hypot(
                    (b.center.x - a.center.x).toDouble(),
                    (b.center.y - a.center.y).toDouble()
                ) / 100.0 / dt

            if (speed < speedThresholdMps) {
                startIndex = i
                break
            }
        }

        val start = pre[startIndex]
        val transition = pre[transitionIndex]
        val impact = pre.last()

        val backswingMs =
            (transition.frame - start.frame).coerceAtLeast(1) *
                1000.0 / fps

        val downswingMs =
            (impact.frame - transition.frame).coerceAtLeast(1) *
                1000.0 / fps

        val ratio =
            if (downswingMs > 0.0) {
                backswingMs / downswingMs
            } else null

        val length =
            hypot(
                (transition.center.x - start.center.x).toDouble(),
                (transition.center.y - start.center.y).toDouble()
            )

        // Peak acceleration from consecutive head-center velocities.
        val velocities = ArrayList<Pair<Int, Double>>()

        for (i in 1 until pre.size) {
            val a = pre[i - 1]
            val b = pre[i]
            val dt =
                (b.frame - a.frame).coerceAtLeast(1).toDouble() / fps

            val speed =
                hypot(
                    (b.center.x - a.center.x).toDouble(),
                    (b.center.y - a.center.y).toDouble()
                ) / 100.0 / dt

            if (speed in 0.0..5.0) {
                velocities += b.frame to speed
            }
        }

        var peakAcceleration: Double? = null

        for (i in 1 until velocities.size) {
            val a = velocities[i - 1]
            val b = velocities[i]
            val dt =
                (b.first - a.first).coerceAtLeast(1).toDouble() / fps

            val acceleration = abs(b.second - a.second) / dt

            if (acceleration in 0.0..80.0) {
                peakAcceleration =
                    max(peakAcceleration ?: 0.0, acceleration)
            }
        }

        return TempoData(
            backswingMs = backswingMs,
            downswingMs = downswingMs,
            ratio = ratio,
            backswingLengthCm = length,
            peakAcceleration = peakAcceleration
        )
    }

    private fun estimateMat(
        samples: List<Sample>,
        impactPos: Int,
        fps: Int,
        rawSpeed: Double
    ): MatData {
        val velocities = ArrayList<Pair<Double, Double>>()
        var previous: Sample? = null

        for (i in impactPos until samples.size) {
            val current = samples[i]
            val point = current.ballCm ?: continue
            val prev = previous

            if (prev != null && prev.ballCm != null) {
                val dtFrames =
                    (current.frame - prev.frame).coerceAtLeast(1)

                val dt = dtFrames.toDouble() / fps
                val dM =
                    hypot(
                        (point.x - prev.ballCm.x).toDouble(),
                        (point.y - prev.ballCm.y).toDouble()
                    ) / 100.0

                val speed = dM / dt
                val timeFromImpact =
                    (current.frame - samples[impactPos].frame)
                        .coerceAtLeast(0).toDouble() / fps

                if (speed in 0.08..5.0 && timeFromImpact <= 0.55) {
                    velocities += timeFromImpact to speed
                }
            }

            previous = current
        }

        if (velocities.size < 5) {
            return MatData(
                rawBallSpeedMps = rawSpeed,
                correctedImpactSpeedMps = rawSpeed,
                decelMps2 = null,
                stimpM = null
            )
        }

        // Linear regression: v(t) = intercept + slope*t.
        val meanT = velocities.map { it.first }.average()
        val meanV = velocities.map { it.second }.average()

        var num = 0.0
        var den = 0.0

        for ((t, v) in velocities) {
            num += (t - meanT) * (v - meanV)
            den += (t - meanT) * (t - meanT)
        }

        if (den <= 1e-9) {
            return MatData(rawSpeed, rawSpeed, null, null)
        }

        val slope = num / den
        val decel = -slope

        if (decel !in 0.08..6.0) {
            return MatData(rawSpeed, rawSpeed, null, null)
        }

        // Back-extrapolate from the ~10cm measurement point to impact.
        val corrected =
            sqrt(
                (rawSpeed * rawSpeed + 2.0 * decel * 0.10)
                    .coerceAtLeast(rawSpeed * rawSpeed)
            )

        // Stimpmeter-equivalent diagnostic for the PHYSICAL mat only.
        val stimpLaunchMps = 1.95072
        val stimp =
            (stimpLaunchMps * stimpLaunchMps / (2.0 * decel))
                .coerceIn(0.5, 10.0)

        return MatData(
            rawBallSpeedMps = rawSpeed,
            correctedImpactSpeedMps = corrected,
            decelMps2 = decel,
            stimpM = stimp
        )
    }
}
