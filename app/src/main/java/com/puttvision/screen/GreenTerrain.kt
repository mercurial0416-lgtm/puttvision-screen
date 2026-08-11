package com.puttvision.screen

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class TerrainSlope(
    val sidePct: Double,
    val longPct: Double
)

/**
 * Lightweight analytic terrain profiles for the practice-green library.
 * Values are local slope percentages sampled from the ball position so each
 * preview can produce a genuinely different break instead of a single flat
 * plane with one constant slope value.
 */
object GreenTerrain {
    fun slopeAt(
        profileId: Int,
        x: Double,
        y: Double,
        holeDistanceM: Double
    ): TerrainSlope {
        if (profileId < 0) return TerrainSlope(0.0, 0.0)

        val d = holeDistanceM.coerceAtLeast(2.0)
        val t = (y / d).coerceIn(0.0, 1.35)
        val nx = (x / (d * 0.24).coerceAtLeast(0.75)).coerceIn(-1.6, 1.6)

        return when (profileId % 6) {
            // Straight / almost-flat calibration green.
            0 -> TerrainSlope(
                sidePct = 0.12 * sin(t * PI * 2.0),
                longPct = 0.08 * cos(t * PI)
            )

            // Gentle left-to-right profile whose break grows toward the cup.
            1 -> TerrainSlope(
                sidePct = 0.45 + 0.42 * sin(t * PI),
                longPct = 0.10 * cos(t * PI * 2.0)
            )

            // Basin/contour green: ball is pulled back toward the central low area.
            2 -> TerrainSlope(
                sidePct = -0.95 * nx + 0.34 * sin(t * PI * 2.0),
                longPct = -0.72 * (t - 0.58) + 0.22 * cos(nx * PI)
            )

            // Predominantly uphill with a slight moving crown.
            3 -> TerrainSlope(
                sidePct = 0.22 * sin(t * PI * 2.0),
                longPct = -0.48 + 0.16 * cos(t * PI * 2.0)
            )

            // Uphill + right-break profile with a stronger final-third break.
            4 -> TerrainSlope(
                sidePct = 0.72 + 0.55 * sin((t - 0.12) * PI),
                longPct = -0.34 + 0.24 * cos(t * PI * 2.0) + 0.10 * nx
            )

            // Compound profile: direction and grade both evolve along the roll.
            else -> TerrainSlope(
                sidePct = -0.62 + 0.95 * sin(t * PI * 2.0) - 0.18 * nx,
                longPct = -0.26 + 0.62 * cos(t * PI * 2.0) + 0.14 * sin(nx * PI)
            )
        }
    }
}
