package com.puttvision.screen

import android.content.Context
import android.os.Build
import kotlin.math.abs

/**
 * Learns a conservative per-device speed correction from HFR shots that already contain both the
 * raw observed speed and the mat/back-extrapolated impact speed. It only applies that correction
 * to non-HFR fallback measurements; HFR measurements are never corrected twice.
 */
object V16DeviceAutoCalibrationRuntime {
    private val ratios = ArrayList<Double>()
    @Volatile private var installed = false
    @Volatile private var speedScale = 1.0
    @Volatile private var sampleCount = 0
    @Volatile private var model = Build.MODEL ?: "Android"
    private var appContext: Context? = null

    @Synchronized
    fun install(context: Context) {
        appContext = context.applicationContext
        model = Build.MODEL ?: "Android"
        val prefs = context.getSharedPreferences("v16_auto_device_cal", Context.MODE_PRIVATE)
        ratios.clear()
        if (prefs.getString("model", null) == model) {
            speedScale = prefs.getFloat("speedScale", 1f).toDouble().coerceIn(.90, 1.10)
            sampleCount = prefs.getInt("count", 0).coerceAtLeast(0)
            prefs.getString("ratios", "")
                ?.split(',')
                ?.mapNotNull { it.toDoubleOrNull() }
                ?.filter { it in .90..1.10 }
                ?.takeLast(20)
                ?.let { ratios += it }
        } else {
            speedScale = 1.0
            sampleCount = 0
        }
        installed = true
    }

    private fun hasHfrEvidence(metrics: ShotMetrics): Boolean =
        metrics.estimatedMatDecelMps2 != null ||
            metrics.estimatedMatStimpM != null ||
            metrics.backswingMs != null ||
            metrics.downswingMs != null ||
            metrics.peakHeadAccelerationMps2 != null

    @Synchronized
    fun observe(metrics: ShotMetrics) {
        if (!installed || !hasHfrEvidence(metrics)) return
        val raw = metrics.rawBallSpeedMps ?: return
        val corrected = metrics.ballSpeedMps
        if (raw !in .10..6.0 || corrected !in .10..6.0) return
        if ((metrics.confidence ?: .0) < .72) return
        val ratio = corrected / raw
        // Only learn a small repeatable capture bias. Bigger gaps usually belong to mat physics,
        // bad clips, or a setup change and must not become a permanent device correction.
        if (ratio !in .90..1.10) return
        ratios += ratio
        while (ratios.size > 20) ratios.removeAt(0)
        if (ratios.size >= 5) {
            val sorted = ratios.sorted()
            val trimmed = if (sorted.size >= 10) sorted.drop(2).dropLast(2) else sorted
            speedScale = trimmed.average().coerceIn(.94, 1.06)
            sampleCount = ratios.size
            persist()
        }
    }

    fun applyFallback(metrics: ShotMetrics): ShotMetrics {
        if (!installed || sampleCount < 5 || hasHfrEvidence(metrics)) return metrics
        if (abs(speedScale - 1.0) < .003) return metrics
        val speed = (metrics.ballSpeedMps * speedScale).coerceIn(.05, 8.0)
        val smash = metrics.headSpeedMps?.takeIf { it > .05 }?.let { speed / it } ?: metrics.smash
        return metrics.copy(
            ballSpeedMps = speed,
            smash = smash,
            confidence = ((metrics.confidence ?: .55) + .025).coerceIn(.20, .96)
        )
    }

    fun statusLabel(): String = when {
        !installed -> "초기화 전"
        sampleCount < 5 -> "$model · 자동보정 $sampleCount/5"
        else -> "$model · x${"%.3f".format(speedScale)} · ${sampleCount}샷"
    }

    @Synchronized
    fun reset() {
        ratios.clear()
        speedScale = 1.0
        sampleCount = 0
        appContext?.getSharedPreferences("v16_auto_device_cal", Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
    }

    private fun persist() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences("v16_auto_device_cal", Context.MODE_PRIVATE).edit()
            .putString("model", model)
            .putFloat("speedScale", speedScale.toFloat())
            .putInt("count", sampleCount)
            .putString("ratios", ratios.joinToString(","))
            .apply()
    }
}
