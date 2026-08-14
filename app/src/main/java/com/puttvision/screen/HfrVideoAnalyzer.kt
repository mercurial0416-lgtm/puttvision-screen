package com.puttvision.screen

import android.graphics.Bitmap
import android.graphics.PointF
import android.media.MediaMetadataRetriever
import com.google.mlkit.vision.barcode.BarcodeScanner
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
    val analyzedFrames: Int,
    val featureTrack: HfrFeatureTrack? = null,
    val calibrationMode: String = "UNKNOWN",
    val calibrationMs: Long = 0L,
    val totalAnalysisMs: Long = 0L
)

class HfrVideoAnalyzer {

    private val v14Vision = V14BitmapVisionTracker()
    private val frameCache = LinkedHashMap<Int, Bitmap>()

    private data class Detection(
        val ballPx: PointF?,
        val heelPx: PointF?,
        val toePx: PointF?,
        val markerAngleDeg: Double? = null
    )

    private data class Sample(
        val frame: Int,
        val ballCm: PointF?,
        val heelCm: PointF?,
        val toeCm: PointF?,
        val markerAngleDeg: Double? = null
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

    private data class VideoCalibration(
        val homography: Homography,
        val mode: String
    )

    fun analyze(
        file: File,
        requestedFps: Int,
        onProgress: (String) -> Unit = {}
    ): HfrAnalysisResult? {
        V41HfrFeatureTrackRuntime.clear()
        V42HfrAnalysisHealthRuntime.clear()
        V45HfrFailureRuntime.recordSuccess()
        val analysisStartedNs = System.nanoTime()

        fun fail(
            reason: V45HfrFailureReason,
            phase: String,
            fps: Int = 0,
            frameCount: Int = 0,
            detail: String = ""
        ): HfrAnalysisResult? {
            V45HfrFailureRuntime.publish(
                V45HfrFailure(
                    reason = reason,
                    phase = phase,
                    elapsedMs = ((System.nanoTime() - analysisStartedNs) / 1_000_000L).coerceAtLeast(0L),
                    fps = fps,
                    frameCount = frameCount,
                    detail = detail.take(96)
                )
            )
            return null
        }

        if (!file.exists() || file.length() == 0L) return fail(V45HfrFailureReason.FILE_INVALID, "OPEN")
        if (android.os.Build.VERSION.SDK_INT < 28) return fail(V45HfrFailureReason.API_UNSUPPORTED, "OPEN")

        val mmr = MediaMetadataRetriever()
        var currentFps = 0
        var currentFrameCount = 0
        var phase = "OPEN"

        try {
            mmr.setDataSource(file.absolutePath)
            clearFrameCache()
            v14Vision.reset()

            phase = "METADATA"
            val frameCount = mmr.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT
            )?.toIntOrNull() ?: return fail(V45HfrFailureReason.VIDEO_METADATA, phase)
            currentFrameCount = frameCount

            val captureFps = mmr.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
            )?.toFloatOrNull()?.toInt()

            val fps = (captureFps ?: requestedFps).coerceAtLeast(30)
            currentFps = fps

            onProgress("${fps}fps / ${frameCount}f · HFR 좌표 보정")

            phase = "CALIBRATION"
            val calibrationStartedNs = System.nanoTime()
            val calibration = findVideoHomography(mmr, frameCount)
                ?: return fail(V45HfrFailureReason.CALIBRATION, phase, fps, frameCount)
            val calibrationMs = ((System.nanoTime() - calibrationStartedNs) / 1_000_000L).coerceAtLeast(0L)
            val homography = calibration.homography

            phase = "COARSE_TRACK"
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

            val startBall = origin ?: return fail(V45HfrFailureReason.BALL_ORIGIN, phase, fps, frameCount)
            if (coarseImpact < 0) return fail(V45HfrFailureReason.IMPACT_NOT_FOUND, phase, fps, frameCount)

            val preFrames = max(30, (fps * 0.90).toInt())
            val postFrames = max(45, (fps * 0.70).toInt())
            val start = max(0, coarseImpact - preFrames)
            val end = min(frameCount - 1, coarseImpact + postFrames)

            onProgress("스트로크+임팩트 정밀 분석 ${start}~${end}f")

            phase = "PRECISION_TRACK"
            val samples = ArrayList<Sample>(end - start + 1)
            var prevBall = startBall
            v14Vision.reset()

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
                samples += Sample(frame, ballCm, heelCm, toeCm, d.markerAngleDeg)
            }

            phase = "KINEMATICS"
            val calculated = calculate(samples, startBall, fps)
                ?: return fail(V45HfrFailureReason.KINEMATICS, phase, fps, frameCount, "samples=${samples.size}")
            val featureTrack = buildFeatureTrack(samples, calculated.second, fps)
            V41HfrFeatureTrackRuntime.publish(featureTrack)
            val totalAnalysisMs = ((System.nanoTime() - analysisStartedNs) / 1_000_000L).coerceAtLeast(0L)
            V42HfrAnalysisHealthRuntime.publish(
                V42HfrAnalysisHealth(
                    calibrationMode = calibration.mode,
                    calibrationMs = calibrationMs,
                    totalAnalysisMs = totalAnalysisMs,
                    fps = fps,
                    analyzedFrames = samples.size,
                    ballTrackFrames = featureTrack.ballFrames,
                    putterTrackFrames = featureTrack.putterFrames
                )
            )
            V45HfrFailureRuntime.recordSuccess()

            return HfrAnalysisResult(
                metrics = calculated.first,
                fps = fps,
                impactFrame = calculated.second,
                analyzedFrames = samples.size,
                featureTrack = featureTrack,
                calibrationMode = calibration.mode,
                calibrationMs = calibrationMs,
                totalAnalysisMs = totalAnalysisMs
            )
        } catch (t: Throwable) {
            return fail(
                V45HfrFailureReason.DECODE_EXCEPTION,
                phase,
                currentFps,
                currentFrameCount,
                t.javaClass.simpleName
            )
        } finally {
            clearFrameCache()
            runCatching { mmr.release() }
        }
    }

    /** SIM CAMERA 2.0: runs the same adaptive detector and kinematics without camera hardware. */
    fun analyzeSynthetic(
        frames: List<Bitmap>,
        fps: Int,
        homography: Homography
    ): HfrAnalysisResult? {
        if (frames.size < 20 || fps < 60) return null
        val tracker = V14BitmapVisionTracker()
        var origin: PointF? = null
        var impact = -1
        val samples = ArrayList<Sample>(frames.size)
        frames.forEachIndexed { index, bmp ->
            val d = tracker.detect(bmp, wantPutter = true)
            val ball = d.ballPx?.let(homography::map)?.takeIf { it.x.isFinite() && it.y.isFinite() }
            val heel = d.heelPx?.let(homography::map)?.takeIf { it.x.isFinite() && it.y.isFinite() }
            val toe = d.toePx?.let(homography::map)?.takeIf { it.x.isFinite() && it.y.isFinite() }
            if (origin == null && ball != null && abs(ball.x) < 30f && ball.y in -20f..40f) origin = PointF(ball.x, ball.y)
            val o = origin
            if (impact < 0 && o != null && ball != null && hypot((ball.x-o.x).toDouble(), (ball.y-o.y).toDouble()) >= .8) impact = index
            samples += Sample(index, ball, heel, toe, d.markerAngleDeg)
        }
        val o = origin ?: return null
        if (impact < 0) return null
        val calculated = calculate(samples, o, fps) ?: return null
        val featureTrack = buildFeatureTrack(samples, calculated.second, fps)
        return HfrAnalysisResult(calculated.first, fps, calculated.second, samples.size, featureTrack)
    }

    private fun buildFeatureTrack(samples: List<Sample>, impactFrame: Int, fps: Int): HfrFeatureTrack {
        val radiusFrames = max(8, (fps * .075).toInt())
        val compact = samples
            .asSequence()
            .filter { abs(it.frame - impactFrame) <= radiusFrames }
            .sortedBy { abs(it.frame - impactFrame) }
            .take(32)
            .sortedBy { it.frame }
            .map { s ->
                HfrFeatureFrame(
                    frame = s.frame,
                    timeFromImpactMs = (s.frame - impactFrame) * 1000.0 / fps,
                    ballXcm = s.ballCm?.x?.toDouble(),
                    ballYcm = s.ballCm?.y?.toDouble(),
                    heelXcm = s.heelCm?.x?.toDouble(),
                    heelYcm = s.heelCm?.y?.toDouble(),
                    toeXcm = s.toeCm?.x?.toDouble(),
                    toeYcm = s.toeCm?.y?.toDouble(),
                    markerAngleDeg = s.markerAngleDeg
                )
            }
            .toList()
        return HfrFeatureTrack(fps = fps, impactFrame = impactFrame, frames = compact)
    }

    private fun clearFrameCache() {
        frameCache.values.forEach { if (!it.isRecycled) it.recycle() }
        frameCache.clear()
    }

    private fun cacheBytes(): Long = frameCache.values.sumOf { bitmap ->
        if (bitmap.isRecycled) 0L else runCatching { bitmap.allocationByteCount.toLong() }
            .getOrDefault(bitmap.width.toLong() * bitmap.height.toLong() * 4L)
    }

    private fun putCached(index: Int, bitmap: Bitmap) {
        val previous = frameCache.put(index, bitmap)
        if (previous != null && previous !== bitmap && !previous.isRecycled) previous.recycle()
        while (V45HfrFrameCachePolicy.shouldEvict(frameCache.size, cacheBytes())) {
            val key = frameCache.keys.firstOrNull() ?: break
            val old = frameCache.remove(key)
            if (old != null && old !== bitmap && !old.isRecycled) old.recycle()
        }
    }

    private fun safeFrame(
        mmr: MediaMetadataRetriever,
        index: Int
    ): Bitmap? {
        frameCache[index]?.let { if (!it.isRecycled) return it }
        val batch = runCatching { mmr.getFramesAtIndex(index, V45HfrFrameCachePolicy.BATCH_SIZE) }.getOrNull()
        if (!batch.isNullOrEmpty()) {
            batch.forEachIndexed { offset, bitmap -> putCached(index + offset, bitmap) }
            frameCache[index]?.let { if (!it.isRecycled) return it }
        }
        val single = runCatching { mmr.getFrameAtIndex(index) }.getOrNull() ?: return null
        putCached(index, single)
        return frameCache[index]?.takeUnless { it.isRecycled }
    }

    private fun findVideoHomography(
        mmr: MediaMetadataRetriever,
        frameCount: Int
    ): VideoCalibration? {
        val indices = listOf(
            0,
            min(2, frameCount - 1),
            min(8, frameCount - 1),
            min(16, frameCount - 1)
        ).distinct()

        val scanner = BarcodeScanning.getClient()
        try {
            for ((attempt, i) in indices.withIndex()) {
                val bmp = safeFrame(mmr, i) ?: continue
                val layout = scanMarkerLayoutBlocking(scanner, bmp)
                if (layout != null) {
                    Homography.fromPoints(
                        layout.fitImagePoints,
                        layout.fitRealPointsCm,
                        FrameInfo(bmp.width, bmp.height, 0)
                    )?.let { return VideoCalibration(it, "QR") }
                }

                if (attempt == 0 && V16MatGeometryRuntime.markerlessEnabled) {
                    val detected = V15MatDetector.detect(bmp)
                    if (detected != null && V42HfrCalibrationPolicy.canUseFastMarkerless(detected.confidence)) {
                        V15MatDetector.homography(
                            detection = detected,
                            frameInfo = FrameInfo(bmp.width, bmp.height, 0),
                            matWidthCm = V16MatGeometryRuntime.widthCm,
                            matLengthCm = V16MatGeometryRuntime.lengthCm
                        )?.let { return VideoCalibration(it, "MARKERLESS_FAST") }
                    }
                }
            }
        } finally {
            scanner.close()
        }

        if (V16MatGeometryRuntime.markerlessEnabled) {
            for (i in indices) {
                val bmp = safeFrame(mmr, i) ?: continue
                val detected = V15MatDetector.detect(bmp) ?: continue
                if (!V42HfrCalibrationPolicy.canUseFallbackMarkerless(detected.confidence)) continue
                V15MatDetector.homography(
                    detection = detected,
                    frameInfo = FrameInfo(bmp.width, bmp.height, 0),
                    matWidthCm = V16MatGeometryRuntime.widthCm,
                    matLengthCm = V16MatGeometryRuntime.lengthCm
                )?.let { return VideoCalibration(it, "MARKERLESS_FALLBACK") }
            }
        }

        return null
    }

    private fun scanMarkerLayoutBlocking(
        scanner: BarcodeScanner,
        bitmap: Bitmap
    ): ResolvedMarkerLayout? {
        val latch = CountDownLatch(1)
        var resolved: ResolvedMarkerLayout? = null

        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { codes ->
                val observations = codes.mapNotNull { code ->
                    val box = code.boundingBox ?: return@mapNotNull null
                    V14QrObservation(
                        code.rawValue,
                        PointF(box.exactCenterX(), box.exactCenterY()),
                        box.width().toLong() * box.height().toLong()
                    )
                }
                resolved = V14MarkerResolver.resolve(observations)
            }
            .addOnCompleteListener { latch.countDown() }

        latch.await(V42HfrCalibrationPolicy.MARKER_SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return resolved
    }

    private fun detect(
        source: Bitmap,
        h: Homography,
        previousBallCm: PointF?,
        wantPutter: Boolean
    ): Detection {
        val d = v14Vision.detect(source, wantPutter)
        return Detection(d.ballPx, d.heelPx, d.toePx, d.markerAngleDeg)
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

        val fitPoints = samples.drop(impactPos).mapNotNull { sample ->
            val ball = sample.ballCm ?: return@mapNotNull null
            val d = hypot((ball.x-origin.x).toDouble(), (ball.y-origin.y).toDouble())
            if (d !in .5..19.0) return@mapNotNull null
            V14TimedPoint(
                (sample.frame - impactFrame).toDouble() / fps,
                (ball.x-origin.x).toDouble(),
                (ball.y-origin.y).toDouble()
            )
        }.take(28)
        val robust = V14RobustKinematics.fit(fitPoints) ?: return null
        val rawSpeed = robust.speedMps
        val launchAngle = robust.launchAngleDeg
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
            val hdt = (b.frame - a.frame).coerceAtLeast(1).toDouble() / fps
            val hdx = (b.center.x - a.center.x).toDouble()
            val hdy = (b.center.y - a.center.y).toDouble()

            headSpeed = (hypot(hdx, hdy) / 100.0) / hdt
            pathAngle = Math.toDegrees(atan2(hdx, hdy))

            val fx = (b.toe.x - b.heel.x).toDouble()
            val fy = (b.toe.y - b.heel.y).toDouble()
            faceAngle = normalizeFaceAngle(Math.toDegrees(atan2(fy, fx)))
            impactCenter = b.center
        }

        val faceToPath = if (faceAngle != null && pathAngle != null) faceAngle - pathAngle else null
        val smash = if (headSpeed != null && headSpeed > 0.05) correctedBallSpeed / headSpeed else null
        val impactOffsetMm = impactCenter?.let { (origin.x - it.x) * 10.0 }

        val ballDetected = samples.count { it.ballCm != null }.toDouble() / samples.size
        val headDetected = samples.take(impactPos + 1)
            .count { it.heelCm != null && it.toeCm != null }
            .toDouble() / max(1, impactPos + 1)

        val confidence = (
            0.35 +
                ballDetected.coerceIn(0.0, 1.0) * 0.35 +
                headDetected.coerceIn(0.0, 1.0) * 0.25 +
                (if (matData.decelMps2 != null) 0.05 else 0.0)
            ).coerceIn(0.0, 1.0)

        val rollSamples = samples.drop(impactPos).mapNotNull { sample ->
            val ball = sample.ballCm ?: return@mapNotNull null
            val marker = sample.markerAngleDeg ?: return@mapNotNull null
            val d = hypot((ball.x-origin.x).toDouble(), (ball.y-origin.y).toDouble())
            V14BallRollAnalyzer.MarkerSample(sample.frame, d, marker)
        }
        val roll = V14BallRollAnalyzer.analyze(rollSamples, fps, correctedBallSpeed)

        val uncertainty = MeasurementUncertaintyEstimator.forHfr(
            fps = fps,
            ballDetectionRatio = ballDetected,
            headDetectionRatio = headDetected,
            matDecelAvailable = matData.decelMps2 != null,
            ballSpeedMps = correctedBallSpeed,
            headSpeedMps = headSpeed,
            impactOffsetMm = impactOffsetMm
        )

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
            confidence = confidence,
            roll = roll,
            uncertainty = uncertainty
        ) to impactFrame
    }

    private fun analyzeTempo(
        head: List<HeadFrame>,
        impactFrame: Int,
        fps: Int
    ): TempoData {
        val pre = head.filter { it.frame <= impactFrame }
        if (pre.size < 8) return TempoData(null, null, null, null, null)

        val transitionIndex = pre.indices.minByOrNull { pre[it].center.y }
            ?: return TempoData(null, null, null, null, null)

        if (transitionIndex <= 1 || transitionIndex >= pre.lastIndex) {
            return TempoData(null, null, null, null, null)
        }

        var startIndex = 0
        val speedThresholdMps = 0.045

        for (i in transitionIndex - 1 downTo 1) {
            val a = pre[i - 1]
            val b = pre[i]
            val dt = (b.frame - a.frame).coerceAtLeast(1).toDouble() / fps
            val speed = hypot(
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
        val backswingMs = (transition.frame - start.frame).coerceAtLeast(1) * 1000.0 / fps
        val downswingMs = (impact.frame - transition.frame).coerceAtLeast(1) * 1000.0 / fps
        val ratio = if (downswingMs > 0.0) backswingMs / downswingMs else null
        val length = hypot(
            (transition.center.x - start.center.x).toDouble(),
            (transition.center.y - start.center.y).toDouble()
        )

        val velocities = ArrayList<Pair<Int, Double>>()
        for (i in 1 until pre.size) {
            val a = pre[i - 1]
            val b = pre[i]
            val dt = (b.frame - a.frame).coerceAtLeast(1).toDouble() / fps
            val speed = hypot(
                (b.center.x - a.center.x).toDouble(),
                (b.center.y - a.center.y).toDouble()
            ) / 100.0 / dt
            if (speed in 0.0..5.0) velocities += b.frame to speed
        }

        var peakAcceleration: Double? = null
        for (i in 1 until velocities.size) {
            val a = velocities[i - 1]
            val b = velocities[i]
            val dt = (b.first - a.first).coerceAtLeast(1).toDouble() / fps
            val acceleration = abs(b.second - a.second) / dt
            if (acceleration in 0.0..80.0) {
                peakAcceleration = max(peakAcceleration ?: 0.0, acceleration)
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
                val dtFrames = (current.frame - prev.frame).coerceAtLeast(1)
                val dt = dtFrames.toDouble() / fps
                val dM = hypot(
                    (point.x - prev.ballCm.x).toDouble(),
                    (point.y - prev.ballCm.y).toDouble()
                ) / 100.0
                val speed = dM / dt
                val timeFromImpact = (current.frame - samples[impactPos].frame)
                    .coerceAtLeast(0).toDouble() / fps

                if (speed in 0.08..5.0 && timeFromImpact <= 0.55) {
                    velocities += timeFromImpact to speed
                }
            }
            previous = current
        }

        if (velocities.size < 5) return MatData(rawSpeed, rawSpeed, null, null)

        val meanT = velocities.map { it.first }.average()
        val meanV = velocities.map { it.second }.average()
        var num = 0.0
        var den = 0.0
        for ((t, v) in velocities) {
            num += (t - meanT) * (v - meanV)
            den += (t - meanT) * (t - meanT)
        }

        if (den <= 1e-9) return MatData(rawSpeed, rawSpeed, null, null)
        val slope = num / den
        val decel = -slope
        if (decel !in 0.08..6.0) return MatData(rawSpeed, rawSpeed, null, null)

        val corrected = sqrt(
            (rawSpeed * rawSpeed + 2.0 * decel * 0.10)
                .coerceAtLeast(rawSpeed * rawSpeed)
        )
        val stimpLaunchMps = 1.95072
        val stimp = (stimpLaunchMps * stimpLaunchMps / (2.0 * decel)).coerceIn(0.5, 10.0)

        return MatData(
            rawBallSpeedMps = rawSpeed,
            correctedImpactSpeedMps = corrected,
            decelMps2 = decel,
            stimpM = stimp
        )
    }
}
