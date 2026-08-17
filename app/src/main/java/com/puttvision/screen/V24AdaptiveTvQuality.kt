package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock

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
 * AUTO may receive noisy thermal/camera observations around a threshold. V122 rebuilds its whole
 * course mesh when the render tier changes, so an upgrade is intentionally slower than a safety
 * downgrade. HFR/heat protection still takes effect immediately; cosmetics return only after the
 * candidate tier has stayed healthy for a bounded hold window.
 */
object V24TvTierStabilityPlanner {
    const val AUTO_UPGRADE_HOLD_MS = 2_200L

    fun resolve(
        mode: V24TvQualityMode,
        currentTier: V24RenderTier,
        candidateTier: V24RenderTier,
        candidateStableMs: Long
    ): V24RenderTier {
        if (mode != V24TvQualityMode.AUTO) return candidateTier
        if (candidateTier == currentTier) return currentTier
        if (!isUpgrade(currentTier, candidateTier)) return candidateTier
        return if (candidateStableMs.coerceAtLeast(0L) >= AUTO_UPGRADE_HOLD_MS) candidateTier else currentTier
    }

    fun isUpgrade(currentTier: V24RenderTier, candidateTier: V24RenderTier): Boolean =
        rank(candidateTier) > rank(currentTier)

    private fun rank(tier: V24RenderTier): Int = when (tier) {
        V24RenderTier.PERFORMANCE -> 0
        V24RenderTier.BALANCED -> 1
        V24RenderTier.HIGH -> 2
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
    private val stabilityLock = Any()
    private var stableTier: V24RenderTier = V24RenderTier.HIGH
    private var pendingUpgradeTier: V24RenderTier? = null
    private var pendingUpgradeSinceMs: Long = 0L

    fun install(context: Context) {
        appContext = context.applicationContext
        val saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_MODE, V24TvQualityMode.AUTO.name)
        mode = runCatching { V24TvQualityMode.valueOf(saved ?: V24TvQualityMode.AUTO.name) }
            .getOrDefault(V24TvQualityMode.AUTO)
        synchronized(stabilityLock) {
            stableTier = V24RenderTier.HIGH
            clearPendingUpgradeLocked()
        }
    }

    fun setMode(context: Context, value: V24TvQualityMode) {
        appContext = context.applicationContext
        mode = value
        synchronized(stabilityLock) { clearPendingUpgradeLocked() }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, value.name).apply()
    }

    fun snapshot(context: Context? = appContext): V24TvQualitySnapshot {
        val capture = V21CaptureConsistencyRuntime.latest
        val frameMs = capture?.frameDurationMs
        val hfr = frameMs != null && frameMs <= 10.5
        val thermal = thermalStatus(context)
        val raw = V24TvQualityPlanner.decide(
            mode = mode,
            thermalStatus = thermal,
            highFrameRateActive = hfr,
            cameraQuality = capture?.score,
            thermalLight = thermalLight(),
            thermalSevere = thermalSevere()
        )
        val stabilized = stabilize(raw, SystemClock.elapsedRealtime())
        return V24TvQualitySnapshot(mode, stabilized.tier, thermal, hfr, capture?.score, stabilized.reason)
    }

    fun label(): String {
        val s = snapshot()
        return "${s.mode.label} · ${s.tier.label} · ${s.reason}"
    }

    private fun stabilize(raw: V24TvQualityDecision, nowMs: Long): V24TvQualityDecision = synchronized(stabilityLock) {
        val selectedMode = mode
        if (selectedMode != V24TvQualityMode.AUTO) {
            stableTier = raw.tier
            clearPendingUpgradeLocked()
            return@synchronized raw
        }

        if (raw.tier == stableTier) {
            clearPendingUpgradeLocked()
            return@synchronized raw
        }

        if (!V24TvTierStabilityPlanner.isUpgrade(stableTier, raw.tier)) {
            stableTier = raw.tier
            clearPendingUpgradeLocked()
            return@synchronized raw
        }

        if (pendingUpgradeTier != raw.tier) {
            pendingUpgradeTier = raw.tier
            pendingUpgradeSinceMs = nowMs
        }
        val stableForMs = (nowMs - pendingUpgradeSinceMs).coerceAtLeast(0L)
        val resolved = V24TvTierStabilityPlanner.resolve(selectedMode, stableTier, raw.tier, stableForMs)
        if (resolved == raw.tier) {
            stableTier = resolved
            clearPendingUpgradeLocked()
            raw
        } else {
            V24TvQualityDecision(stableTier, "${raw.reason} · 안정화 대기")
        }
    }

    private fun clearPendingUpgradeLocked() {
        pendingUpgradeTier = null
        pendingUpgradeSinceMs = 0L
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
