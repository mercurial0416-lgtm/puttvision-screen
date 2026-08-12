package com.puttvision.screen

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class TerrainSlope(
    val sidePct: Double,
    val longPct: Double
)

/**
 * Analytic spatial terrain library used by practice greens.
 * Each profile returns the local side/long slope at the ball position, so the
 * break can evolve during the roll instead of behaving like one uniform plane.
 */
object GreenTerrain {
    fun effectiveSlopeAt(settings: GreenSettings, x: Double, y: Double): TerrainSlope {
        val local = slopeAt(settings.terrainProfileId, x, y, settings.holeDistanceM)
        return TerrainSlope(
            sidePct = settings.sideSlopePct + local.sidePct,
            longPct = settings.longSlopePct + local.longPct
        )
    }

    fun slopeAt(
        profileId: Int,
        x: Double,
        y: Double,
        holeDistanceM: Double
    ): TerrainSlope {
        if (profileId < 0) return TerrainSlope(0.0, 0.0)

        val d = holeDistanceM.coerceAtLeast(2.0)
        val t = (y / d).coerceIn(0.0, 1.35)
        val nx = (x / (d * 0.24).coerceAtLeast(0.75)).coerceIn(-1.7, 1.7)
        val p = profileId.coerceIn(0, 23)

        return when (p) {
            // EASY ---------------------------------------------------------
            0 -> TerrainSlope(
                sidePct = 0.08 * sin(t * PI * 2.0),
                longPct = 0.06 * cos(t * PI)
            )
            1 -> TerrainSlope(
                sidePct = 0.24 + 0.20 * sin(t * PI),
                longPct = 0.05 * cos(t * PI * 2.0)
            )
            2 -> TerrainSlope(
                sidePct = -0.24 - 0.20 * sin(t * PI),
                longPct = 0.05 * cos(t * PI * 2.0)
            )
            3 -> TerrainSlope(
                sidePct = 0.10 * sin(t * PI * 2.0),
                longPct = -0.22 + 0.07 * cos(t * PI * 2.0)
            )
            4 -> TerrainSlope(
                sidePct = -0.10 * sin(t * PI * 2.0),
                longPct = 0.22 + 0.07 * cos(t * PI * 2.0)
            )
            5 -> TerrainSlope(
                sidePct = -0.34 * nx + 0.11 * sin(t * PI * 2.0),
                longPct = -0.24 * (t - 0.58) + 0.08 * cos(nx * PI)
            )

            // STANDARD -----------------------------------------------------
            6 -> TerrainSlope(
                sidePct = 0.52 + 0.36 * sin((t - 0.08) * PI),
                longPct = 0.09 * cos(t * PI * 2.0)
            )
            7 -> TerrainSlope(
                sidePct = -0.52 - 0.36 * sin((t - 0.08) * PI),
                longPct = 0.09 * cos(t * PI * 2.0)
            )
            8 -> TerrainSlope(
                sidePct = 0.44 + 0.33 * sin(t * PI),
                longPct = -0.38 + 0.13 * cos(t * PI * 2.0)
            )
            9 -> TerrainSlope(
                sidePct = -0.44 - 0.33 * sin(t * PI),
                longPct = -0.38 + 0.13 * cos(t * PI * 2.0)
            )
            10 -> TerrainSlope(
                sidePct = 0.46 + 0.28 * sin(t * PI * 1.4),
                longPct = 0.38 + 0.14 * cos(t * PI * 2.0)
            )
            11 -> TerrainSlope(
                sidePct = -0.46 - 0.28 * sin(t * PI * 1.4),
                longPct = 0.38 + 0.14 * cos(t * PI * 2.0)
            )

            // ADVANCED -----------------------------------------------------
            12 -> TerrainSlope(
                sidePct = 0.82 * sin((t - 0.10) * PI * 2.0) - 0.12 * nx,
                longPct = -0.10 + 0.25 * cos(t * PI * 2.0)
            )
            13 -> TerrainSlope(
                sidePct = -0.82 * sin((t - 0.10) * PI * 2.0) + 0.12 * nx,
                longPct = -0.10 + 0.25 * cos(t * PI * 2.0)
            )
            14 -> TerrainSlope(
                sidePct = 0.72 * nx + 0.18 * sin(t * PI * 2.0),
                longPct = 0.48 * (t - 0.48) + 0.12 * cos(nx * PI)
            )
            15 -> TerrainSlope(
                sidePct = -0.78 * nx + 0.24 * sin(t * PI * 2.0),
                longPct = -0.58 * (t - 0.56) + 0.18 * cos(nx * PI)
            )
            16 -> TerrainSlope(
                sidePct = 0.12 + 1.05 * t * t + 0.12 * sin(nx * PI),
                longPct = -0.12 + 0.24 * cos(t * PI * 2.0)
            )
            17 -> TerrainSlope(
                sidePct = -0.12 - 1.05 * t * t - 0.12 * sin(nx * PI),
                longPct = -0.12 + 0.24 * cos(t * PI * 2.0)
            )

            // EXPERT -------------------------------------------------------
            18 -> TerrainSlope(
                sidePct = 0.56 * sin(t * PI * 3.0) + 0.48 * nx,
                longPct = -0.18 + 0.55 * cos(t * PI * 2.0)
            )
            19 -> TerrainSlope(
                sidePct = 1.12 * sin((t + 0.08) * PI * 2.6) - 0.20 * nx,
                longPct = 0.42 * cos(t * PI * 3.0) + 0.12 * sin(nx * PI)
            )
            20 -> TerrainSlope(
                sidePct = 0.92 + 0.62 * sin((t - 0.08) * PI) + 0.16 * nx,
                longPct = -0.72 + 0.30 * cos(t * PI * 2.0)
            )
            21 -> TerrainSlope(
                sidePct = -0.92 - 0.62 * sin((t - 0.08) * PI) - 0.16 * nx,
                longPct = 0.72 + 0.30 * cos(t * PI * 2.0)
            )
            22 -> TerrainSlope(
                sidePct = 0.82 * nx + 0.72 * sin(t * PI * 2.0),
                longPct = 0.58 * (t - 0.46) - 0.30 * cos(nx * PI)
            )
            else -> TerrainSlope(
                sidePct = -0.66 + 1.35 * sin(t * PI * 2.2) - 0.26 * nx,
                longPct = -0.34 + 0.88 * cos(t * PI * 2.0) + 0.20 * sin(nx * PI)
            )
        }
    }
}
