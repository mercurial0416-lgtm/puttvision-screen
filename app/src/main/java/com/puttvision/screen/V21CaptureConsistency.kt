package com.puttvision.screen

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Preview
import kotlin.math.abs
import kotlin.math.max


data class V21CaptureSnapshot(
    val exposureUs: Double?,
    val iso: Int?,
    val frameDurationMs: Double?,
    val rollingShutterMs: Double?,
    val aeLocked: Boolean?,
    val awbLocked: Boolean?,
    val antibandingMode: Int?,
    val sceneFlicker: Int?,
    val score: Int,
    val confidenceMultiplier: Double,
    val hint: String,
    val updatedNs: Long
)

/**
 * Reads actual Camera2 result metadata from CameraX sessions. No extra camera is opened and no
 * capture requests are submitted from the callback, keeping CameraX in control of the session.
 */
object V21CaptureConsistencyRuntime {
    @Volatile var latest: V21CaptureSnapshot? = null
        private set

    private val exposureWindowUs = ArrayDeque<Double>(18)
    private val frameWindowMs = ArrayDeque<Double>(18)
    private val skewWindowMs = ArrayDeque<Double>(18)

    val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            observe(result)
        }
    }

    @Synchronized
    fun observe(result: CaptureResult) {
        val exposureUs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.div(1000.0)
        val frameMs = result.get(CaptureResult.SENSOR_FRAME_DURATION)?.div(1_000_000.0)
        val skewMs = result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW)?.div(1_000_000.0)
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
        val aeLock = result.get(CaptureResult.CONTROL_AE_LOCK)
        val awbLock = result.get(CaptureResult.CONTROL_AWB_LOCK)
        val band = result.get(CaptureResult.CONTROL_AE_ANTIBANDING_MODE)
        val flicker = result.get(CaptureResult.STATISTICS_SCENE_FLICKER)

        exposureUs?.let { push(exposureWindowUs, it) }
        frameMs?.let { push(frameWindowMs, it) }
        skewMs?.let { push(skewWindowMs, it) }

        val exposureStability = stability(exposureWindowUs)
        val frameStability = stability(frameWindowMs)
        val skewStability = stability(skewWindowMs)

        var score = 100.0
        // Exposure longer than ~1/180 s starts to make fast putter/ball edges less trustworthy.
        exposureUs?.let {
            score -= when {
                it <= 2800.0 -> 0.0
                it <= 5500.0 -> (it - 2800.0) / 2700.0 * 9.0
                it <= 9000.0 -> 9.0 + (it - 5500.0) / 3500.0 * 16.0
                else -> 30.0
            }
        }
        iso?.let {
            score -= when {
                it <= 800 -> 0.0
                it <= 1600 -> (it - 800) / 800.0 * 7.0
                it <= 3200 -> 7.0 + (it - 1600) / 1600.0 * 10.0
                else -> 20.0
            }
        }
        if (exposureStability < .92) score -= (0.92 - exposureStability) * 35.0
        if (frameStability < .94) score -= (0.94 - frameStability) * 28.0
        if (skewStability < .90) score -= (0.90 - skewStability) * 18.0

        if (aeLock == false) score -= 5.0
        if (awbLock == false) score -= 2.0
        if (flicker != null && flicker != CaptureResult.STATISTICS_SCENE_FLICKER_NONE) score -= 12.0

        val skewRatio = if (skewMs != null && frameMs != null && frameMs > .1) skewMs / frameMs else null
        skewRatio?.let {
            if (it > .80) score -= 12.0
            else if (it > .55) score -= 6.0
        }

        val finalScore = score.toInt().coerceIn(35, 100)
        val multiplier = when {
            finalScore >= 90 -> 1.0
            finalScore >= 80 -> .96
            finalScore >= 70 -> .90
            finalScore >= 60 -> .82
            else -> .72
        }
        val hint = when {
            flicker != null && flicker != CaptureResult.STATISTICS_SCENE_FLICKER_NONE -> "조명 플리커 감지 · 다른 조명/밝기 권장"
            exposureUs != null && exposureUs > 9000.0 -> "셔터가 느림 · 매트 조명을 더 밝게"
            iso != null && iso > 3200 -> "ISO 높음 · 노이즈 증가"
            skewRatio != null && skewRatio > .80 -> "롤링셔터 영향 큼 · FACE/PATH 신뢰도 제한"
            exposureStability < .88 -> "노출 변동 중 · 잠금 안정화 대기"
            frameStability < .90 -> "프레임 타이밍 불안정"
            aeLock == false || awbLock == false -> "카메라 3A 잠금 안정화 중"
            else -> "캡처 타이밍 안정"
        }
        latest = V21CaptureSnapshot(
            exposureUs = exposureUs,
            iso = iso,
            frameDurationMs = frameMs,
            rollingShutterMs = skewMs,
            aeLocked = aeLock,
            awbLocked = awbLock,
            antibandingMode = band,
            sceneFlicker = flicker,
            score = finalScore,
            confidenceMultiplier = multiplier,
            hint = hint,
            updatedNs = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: System.nanoTime()
        )
    }

    /** Reduces confidence only; camera metadata can never make a weak vision result look stronger. */
    fun adjust(metrics: ShotMetrics): ShotMetrics {
        val snapshot = latest ?: return metrics
        val ageNs = abs(System.nanoTime() - snapshot.updatedNs)
        if (ageNs > 3_000_000_000L) return metrics
        val base = metrics.confidence ?: .68
        val adjusted = (base * snapshot.confidenceMultiplier).coerceIn(.25, base)
        return metrics.copy(confidence = adjusted)
    }

    fun label(): String = latest?.let {
        buildString {
            append("CAM Q${it.score}")
            it.exposureUs?.let { us -> append(" · ${"%.1f".format(us / 1000.0)}ms") }
            it.rollingShutterMs?.let { skew -> append(" · skew ${"%.1f".format(skew)}ms") }
        }
    } ?: "CAM Q--"

    fun attach(builder: Preview.Builder): Preview.Builder {
        Camera2Interop.Extender(builder).setSessionCaptureCallback(captureCallback)
        return builder
    }

    @Synchronized fun reset() {
        exposureWindowUs.clear()
        frameWindowMs.clear()
        skewWindowMs.clear()
        latest = null
    }

    private fun push(window: ArrayDeque<Double>, value: Double) {
        window.addLast(value)
        while (window.size > 18) window.removeFirst()
    }

    private fun stability(values: Collection<Double>): Double {
        if (values.size < 4) return 1.0
        val avg = values.average().coerceAtLeast(.0001)
        val maxDeviation = values.maxOf { abs(it - avg) } / avg
        return (1.0 - maxDeviation).coerceIn(0.0, 1.0)
    }
}
