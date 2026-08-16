package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.PowerManager

enum class V24TvQualityMode(val label: String) {
    AUTO("자동"),
    HIGH("고화질"),
    PERFORMANCE("성능 우선")
}

enum class V24RenderTier(
    val label: String,
    val terrainCols: Int,
    val terrainRows: Int,
    val movingFrameMs: Long,
    val idleFrameMs: Long
) {
    HIGH("HIGH", 36, 96, 16L, 55L),
    BALANCED("BALANCED", 28, 72, 16L, 66L),
    PERFORMANCE("PERFORMANCE", 18, 46, 24L, 95L)
}

data class V24TvQualitySnapshot(
    val mode: V24TvQualityMode,
    val tier: V24RenderTier,
    val thermalStatus: Int?,
    val highFrameRateActive: Boolean,
    val cameraQuality: Int?,
    val reason: String
)

data class V24TvQualityDecision(
    val tier: V24RenderTier,
    val reason: String
)

/** Pure policy so thermal/HFR priority can be regression-tested without an Android PowerManager. */
object V24TvQualityPlanner {
    fun decide(
        mode: V24TvQualityMode,
        thermalStatus: Int?,
        highFrameRateActive: Boolean,
        cameraQuality: Int?,
        thermalLight: Int,
        thermalSevere: Int
    ): V24TvQualityDecision {
        // Severe heat always wins over TV cosmetics, even when HIGH was selected manually.
        if (thermalStatus != null && thermalStatus >= thermalSevere) {
            return V24TvQualityDecision(V24RenderTier.PERFORMANCE, "발열 보호")
        }

        return when (mode) {
            V24TvQualityMode.HIGH -> V24TvQualityDecision(V24RenderTier.HIGH, "사용자 고화질 고정")
            V24TvQualityMode.PERFORMANCE -> V24TvQualityDecision(V24RenderTier.PERFORMANCE, "사용자 성능 우선 고정")
            V24TvQualityMode.AUTO -> when {
                highFrameRateActive -> V24TvQualityDecision(V24RenderTier.PERFORMANCE, "HFR 카메라 우선")
                thermalStatus != null && thermalStatus >= thermalLight ->
                    V24TvQualityDecision(V24RenderTier.BALANCED, "온도 상승 · 균형")
                cameraQuality != null && cameraQuality < 72 ->
                    V24TvQualityDecision(V24RenderTier.BALANCED, "카메라 품질 우선")
                else -> V24TvQualityDecision(V24RenderTier.HIGH, "열 여유 · 고화질")
            }
        }
    }
}

/**
 * Protects camera/HFR thermal budget before TV cosmetics. AUTO may reduce graphics load when
 * the phone is hot or a high-frame-rate camera session is observed; severe heat overrides every
 * cosmetic mode because measurement/camera stability has priority over TV rendering quality.
 */
object V24TvQualityRuntime {
    private const val PREF = "puttvision_v24_tv_quality"
    private const val KEY_MODE = "mode"

    @Volatile var mode: V24TvQualityMode = V24TvQualityMode.AUTO
        private set

    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        val saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_MODE, V24TvQualityMode.AUTO.name)
        mode = runCatching { V24TvQualityMode.valueOf(saved ?: V24TvQualityMode.AUTO.name) }
            .getOrDefault(V24TvQualityMode.AUTO)
    }

    fun setMode(context: Context, value: V24TvQualityMode) {
        appContext = context.applicationContext
        mode = value
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, value.name).apply()
    }

    fun snapshot(context: Context? = appContext): V24TvQualitySnapshot {
        val capture = V21CaptureConsistencyRuntime.latest
        val frameMs = capture?.frameDurationMs
        val hfr = frameMs != null && frameMs <= 10.5
        val thermal = thermalStatus(context)
        val decision = V24TvQualityPlanner.decide(
            mode = mode,
            thermalStatus = thermal,
            highFrameRateActive = hfr,
            cameraQuality = capture?.score,
            thermalLight = thermalLight(),
            thermalSevere = thermalSevere()
        )
        return V24TvQualitySnapshot(mode, decision.tier, thermal, hfr, capture?.score, decision.reason)
    }

    fun label(): String {
        val s = snapshot()
        return "${s.mode.label} · ${s.tier.label} · ${s.reason}"
    }

    private fun thermalStatus(context: Context?): Int? {
        if (context == null || Build.VERSION.SDK_INT < 29) return null
        return runCatching {
            context.getSystemService(PowerManager::class.java)?.currentThermalStatus
        }.getOrNull()
    }

    private fun thermalLight(): Int = if (Build.VERSION.SDK_INT >= 29) PowerManager.THERMAL_STATUS_LIGHT else 1
    private fun thermalSevere(): Int = if (Build.VERSION.SDK_INT >= 29) PowerManager.THERMAL_STATUS_SEVERE else 3
}

fun showV24TvQualityDialog(context: Context) {
    val values = V24TvQualityMode.entries
    val selected = values.indexOf(V24TvQualityRuntime.mode).coerceAtLeast(0)
    AlertDialog.Builder(context)
        .setTitle("TV 3D 화질")
        .setMessage("자동은 평소 고화질을 쓰고 HFR/발열 시 TV 부담을 낮춥니다. 고화질 고정도 심한 발열에서는 카메라 안정성을 위해 일시적으로 성능 우선으로 전환됩니다.")
        .setSingleChoiceItems(values.map { it.label }.toTypedArray(), selected) { dialog, which ->
            V24TvQualityRuntime.setMode(context, values[which])
            dialog.dismiss()
        }
        .setNegativeButton("닫기", null)
        .show()
}
