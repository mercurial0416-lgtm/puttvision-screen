package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hardwareless end-to-end stereo self-check.
 *
 * A deterministic world-space BALL trajectory is projected into two synthetic cameras and then
 * reconstructed through the production V67 -> V55 -> V60 -> V63 path. This is intentionally not
 * a replacement for real-device validation; it proves the no-hardware lab still exercises the
 * production stereo gates and geometry instead of a separate hand-written approximation.
 */
data class V68StereoSelfCheckResult(
    val passed: Boolean,
    val expectedSpeedMps: Double,
    val reconstructedSpeedMps: Double?,
    val expectedDirectionDeg: Double,
    val reconstructedDirectionDeg: Double?,
    val speedErrorMps: Double?,
    val directionErrorDeg: Double?,
    val sampleCount: Int,
    val reason: String
) {
    fun shortLabel(): String = if (passed) {
        "STEREO PIPE PASS · ${"%.2f".format(reconstructedSpeedMps ?: 0.0)}m/s · ${"%+.2f".format(reconstructedDirectionDeg ?: 0.0)}°"
    } else {
        "STEREO PIPE FAIL · $reason"
    }
}

object V68HardwarelessStereoSelfCheck {
    private const val FPS = 240
    private const val WIDTH = 1920
    private const val HEIGHT = 1080
    private const val NOW_MS = 100_500L
    private const val CALIBRATED_AT_MS = 100_000L
    private val identity = doubleArrayOf(
        1.0, 0.0, 0.0,
        0.0, 1.0, 0.0,
        0.0, 0.0, 1.0
    )
    private val intrinsics = V53CameraIntrinsics(1200.0, 1200.0, 960.0, 540.0)

    fun verify(metrics: ShotMetrics): V68StereoSelfCheckResult = runCatching {
        val speed = metrics.ballSpeedMps
        val direction = metrics.launchAngleDeg
        require(speed.isFinite() && speed > 0.01)
        require(direction.isFinite())

        val first = calibration(-0.15)
        val second = calibration(0.15)
        val firstSignature = signature("hardwareless-left")
        val secondSignature = signature("hardwareless-right")
        val profile = V59StereoCalibrationProfile(
            pairId = "hardwareless-pair",
            rigRevisionId = "hardwareless-rig-v1",
            first = V59BoundCameraCalibration(firstSignature, first),
            second = V59BoundCameraCalibration(secondSignature, second),
            calibratedAtMs = CALIBRATED_AT_MS,
            acceptedObservationCount = 5
        )
        val rad = Math.toRadians(direction)
        val vx = speed * sin(rad)
        val vy = speed * cos(rad)
        val firstTrack = track(first, vx, vy)
        val secondTrack = track(second, vx, vy)

        val result = V63StereoBallTrajectoryReconstructor.reconstruct(
            localTrack = firstTrack,
            localView = V15CameraView.PRIMARY,
            remoteTrack = secondTrack,
            remoteView = V15CameraView.FACE_ON,
            profile = profile,
            currentFirst = firstSignature,
            currentSecond = secondSignature,
            activePairId = "hardwareless-pair",
            activeRigRevisionId = "hardwareless-rig-v1",
            nowMs = NOW_MS
        )
        val reconstructedSpeed = result.horizontalSpeedMps
        val reconstructedDirection = result.startDirectionDeg
        val speedError = reconstructedSpeed?.let { abs(it - speed) }
        val directionError = reconstructedDirection?.let { angularErrorDeg(it, direction) }
        val passed = result.usableForMeasurementValidation &&
            reconstructedSpeed != null && reconstructedDirection != null &&
            speedError != null && speedError <= 0.005 &&
            directionError != null && directionError <= 0.05
        V68StereoSelfCheckResult(
            passed = passed,
            expectedSpeedMps = speed,
            reconstructedSpeedMps = reconstructedSpeed,
            expectedDirectionDeg = direction,
            reconstructedDirectionDeg = reconstructedDirection,
            speedErrorMps = speedError,
            directionErrorDeg = directionError,
            sampleCount = result.samples.size,
            reason = if (passed) "production stereo path reconstructed synthetic truth" else result.reason
        )
    }.getOrElse { error ->
        V68StereoSelfCheckResult(
            passed = false,
            expectedSpeedMps = metrics.ballSpeedMps,
            reconstructedSpeedMps = null,
            expectedDirectionDeg = metrics.launchAngleDeg,
            reconstructedDirectionDeg = null,
            speedErrorMps = null,
            directionErrorDeg = null,
            sampleCount = 0,
            reason = error.message ?: "self-check exception"
        )
    }

    private fun signature(cameraId: String) = V59CaptureSignature(
        cameraId = cameraId,
        widthPx = WIDTH,
        heightPx = HEIGHT,
        fps = FPS,
        sensorOrientationDeg = 90,
        lensFacing = "BACK",
        captureMode = "HFR"
    )

    private fun calibration(x: Double) = V53CameraCalibration(
        intrinsics = intrinsics,
        extrinsics = V53CameraExtrinsics(identity.copyOf(), V53Vec3(x, 0.0, -1.5)),
        rmsReprojectionPx = 0.15,
        calibratedAtMs = CALIBRATED_AT_MS
    )

    private fun track(calibration: V53CameraCalibration, vx: Double, vy: Double): HfrFeatureTrack {
        val impactFrame = 10
        val frames = (0 until 7).map { i ->
            val timeMs = i * (1000.0 / FPS)
            val seconds = timeMs / 1000.0
            val point = V53Vec3(
                x = 0.01 + vx * seconds,
                y = 0.18 + vy * seconds,
                z = 0.021
            )
            val pixel = requireNotNull(V53StereoProjection.project(calibration, point))
            HfrFeatureFrame(
                frame = impactFrame + i,
                timeFromImpactMs = timeMs,
                ballXcm = point.x * 100.0,
                ballYcm = point.y * 100.0,
                heelXcm = null,
                heelYcm = null,
                toeXcm = null,
                toeYcm = null,
                markerAngleDeg = null,
                ballXpx = pixel.x,
                ballYpx = pixel.y
            )
        }
        return HfrFeatureTrack(
            fps = FPS,
            impactFrame = impactFrame,
            frames = frames,
            imageWidthPx = WIDTH,
            imageHeightPx = HEIGHT
        )
    }

    private fun angularErrorDeg(actual: Double, expected: Double): Double {
        var delta = (actual - expected) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return abs(delta)
    }
}

object V68HardwarelessStereoRuntime {
    @Volatile private var latest: V68StereoSelfCheckResult? = null

    fun run(metrics: ShotMetrics): V68StereoSelfCheckResult =
        V68HardwarelessStereoSelfCheck.verify(metrics).also { latest = it }

    fun snapshot(): V68StereoSelfCheckResult? = latest

    fun clear() {
        latest = null
    }
}
