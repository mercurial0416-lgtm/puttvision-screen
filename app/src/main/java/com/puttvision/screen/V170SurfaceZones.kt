package com.puttvision.screen

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V170 surface zones shared by the authoritative V135 rolling solver and TV telemetry.
 *
 * The footprint math mirrors the six visual shape families introduced by V169. We intentionally
 * activate it only for the 24 practice terrain profiles; terrainProfileId < 0 preserves the legacy
 * infinite-green behavior used by historical calibration/regression cases.
 */
enum class V170SurfaceZone(val effectiveStimpScale: Double) {
    GREEN(1.0),
    FRINGE(0.58),
    ROUGH(0.28)
}

object V170SurfaceZones {
    const val GREEN_WIDTH_M = 11.8
    const val GREEN_DEPTH_M = 34.5
    const val GREEN_CENTER_Y_M = 14.25
    const val FRINGE_WIDTH_M = 13.8
    const val FRINGE_DEPTH_M = 36.0
    const val FRINGE_CENTER_Y_M = 15.0

    private data class Limits(val left: Double, val right: Double)

    fun zoneAt(settings: GreenSettings, x: Double, y: Double): V170SurfaceZone {
        val profile = settings.terrainProfileId
        if (profile < 0) return V170SurfaceZone.GREEN
        if (inside(profile, x, y, GREEN_WIDTH_M, GREEN_DEPTH_M, GREEN_CENTER_Y_M, 0.0)) {
            return V170SurfaceZone.GREEN
        }
        if (inside(profile, x, y, FRINGE_WIDTH_M, FRINGE_DEPTH_M, FRINGE_CENTER_Y_M, 0.035)) {
            return V170SurfaceZone.FRINGE
        }
        return V170SurfaceZone.ROUGH
    }

    fun effectiveStimpMeters(settings: GreenSettings, x: Double, y: Double): Double {
        val base = settings.stimpMeters.takeIf { it.isFinite() }?.coerceIn(1.0, 6.2) ?: 2.8
        val scale = zoneAt(settings, x, y).effectiveStimpScale
        return (base * scale).coerceIn(0.50, 6.2)
    }

    /** Public for renderer/CI parity tests without exposing implementation state. */
    fun insideGreen(profileId: Int, x: Double, y: Double): Boolean =
        inside(profileId, x, y, GREEN_WIDTH_M, GREEN_DEPTH_M, GREEN_CENTER_Y_M, 0.0)

    fun insideFringe(profileId: Int, x: Double, y: Double): Boolean =
        inside(profileId, x, y, FRINGE_WIDTH_M, FRINGE_DEPTH_M, FRINGE_CENTER_Y_M, 0.035)

    private fun inside(
        profileId: Int,
        x: Double,
        y: Double,
        widthM: Double,
        depthM: Double,
        centerYM: Double,
        expansion: Double
    ): Boolean {
        // Godot local z is centerY-y because renderer forward is -Z while physics forward is +Y.
        val nzRaw = (centerYM - y) / (depthM * 0.5)
        if (kotlin.math.abs(nzRaw) > 1.0 + expansion) return false
        val nz = nzRaw.coerceIn(-1.0, 1.0)
        val nx = x / (widthM * 0.5)
        val limits = shapeLimits(profileId, nz)
        return nx >= limits.left - expansion && nx <= limits.right + expansion
    }

    private fun shapeLimits(profileId: Int, normalizedZ: Double): Limits {
        val z = normalizedZ.coerceIn(-1.0, 1.0)
        val t = (z + 1.0) * 0.5
        val ellipse = sqrt(max(0.0, 1.0 - z * z))
        var center = 0.0
        var width = ellipse * 0.92
        when (Math.floorMod(profileId, 6)) {
            0 -> {
                center = 0.0
                width = ellipse * 0.92
            }
            1 -> {
                center = -0.11 + t * 0.17 + sin(t * PI) * 0.035
                width = ellipse * (0.79 + (1.0 - t) * 0.10)
            }
            2 -> {
                center = sin(t * PI * 1.25) * 0.055
                width = ellipse * (0.90 + sin(t * PI) * 0.055)
            }
            3 -> {
                center = sin(t * PI * 1.4) * 0.025
                width = ellipse * 0.78
            }
            4 -> {
                center = -0.08 + t * 0.15 - sin(t * PI * 1.15) * 0.035
                width = ellipse * (0.80 + t * 0.09)
            }
            else -> {
                center = sin((t - 0.12) * PI * 1.55) * 0.10
                width = ellipse * (0.82 + sin(t * PI * 2.0) * 0.045)
            }
        }
        width = max(0.0, width)
        return Limits(center - width, center + width)
    }
}
