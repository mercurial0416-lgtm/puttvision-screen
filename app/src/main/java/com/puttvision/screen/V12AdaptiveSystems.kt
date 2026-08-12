package com.puttvision.screen

import android.os.PowerManager
import java.security.MessageDigest

/** Stateful thermal recovery: throttle immediately, recover only after a cool hold. */
class ThermalHfrHysteresis {
    private var stableMaxFps = 240
    private var recoveryStartedAtMs = 0L

    @Synchronized
    fun update(raw: ThermalHfrDecision, nowMs: Long): ThermalHfrDecision {
        val target = raw.maxFps
        if (target < stableMaxFps) {
            stableMaxFps = target
            recoveryStartedAtMs = 0L
            return decorate(raw, false, 0L)
        }
        if (target == stableMaxFps) {
            recoveryStartedAtMs = 0L
            return decorate(raw, false, 0L)
        }

        val temp = raw.batteryTempC
        val recoveryEligible = when (stableMaxFps) {
            0 -> raw.thermalStatus < PowerManager.THERMAL_STATUS_SEVERE && (temp == null || temp <= 43.0)
            120 -> raw.thermalStatus < PowerManager.THERMAL_STATUS_MODERATE && (temp == null || temp <= 38.5)
            else -> true
        }
        if (!recoveryEligible) {
            recoveryStartedAtMs = 0L
            return decorate(raw, false, 0L)
        }

        if (recoveryStartedAtMs == 0L) recoveryStartedAtMs = nowMs
        val holdMs = if (stableMaxFps == 0) 30_000L else 60_000L
        val elapsed = (nowMs - recoveryStartedAtMs).coerceAtLeast(0L)
        if (elapsed >= holdMs) {
            stableMaxFps = if (stableMaxFps == 0 && target >= 240) 120 else target
            recoveryStartedAtMs = 0L
            return decorate(raw, false, 0L)
        }
        return decorate(raw, true, holdMs - elapsed)
    }

    private fun decorate(raw: ThermalHfrDecision, holding: Boolean, remainingMs: Long): ThermalHfrDecision {
        val temp = raw.batteryTempC?.let { " · ${"%.1f".format(it)}°C" }.orEmpty()
        val baseLabel = when (stableMaxFps) {
            0 -> "HOT · NORMAL"
            120 -> "WARM · 120fps"
            else -> "COOL · 240fps"
        }
        val detail = when {
            holding -> "$baseLabel$temp · 냉각 안정화 ${((remainingMs + 999) / 1000)}초 후 상향 재검토"
            stableMaxFps == 0 -> "열 보호 모드$temp · 일반 추적으로 측정합니다"
            stableMaxFps == 120 -> "발열 보호$temp · PRECISION을 120fps로 유지합니다"
            else -> "열 상태 안정$temp · 240fps 우선"
        }
        return raw.copy(maxFps = stableMaxFps, label = baseLabel, detail = detail)
    }
}

object AccuracyProfileKey {
    fun build(model: String, cameraId: String, fps: Int, resolution: String, api: Int): String =
        "$model|CAM:$cameraId|FPS:$fps|SIZE:$resolution|API:$api"

    fun slot(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return "model_" + digest.take(10).joinToString("") { "%02x".format(it) }
    }
}
