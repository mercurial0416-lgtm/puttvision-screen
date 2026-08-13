package com.puttvision.screen


data class TerrainSlope(
    val sidePct: Double,
    val longPct: Double
)

/**
 * Compatibility facade for the spatial green library.
 * Rendering and physics consume this same facade so built-in and user-authored surfaces stay in sync.
 */
object GreenTerrain {
    fun effectiveSlopeAt(settings: GreenSettings, x: Double, y: Double): TerrainSlope {
        val custom = V22CustomGreenRuntime.slopeAt(x, y, settings.holeDistanceM)
        val local = if (custom == null) {
            slopeAt(settings.terrainProfileId, x, y, settings.holeDistanceM)
        } else {
            TerrainSlope(0.0, 0.0)
        }
        return TerrainSlope(
            sidePct = settings.sideSlopePct + local.sidePct + (custom?.sidePct ?: 0.0),
            longPct = settings.longSlopePct + local.longPct + (custom?.longPct ?: 0.0)
        )
    }

    /** Physical elevation in metres for the exact surface GreenPhysics feels. */
    fun effectiveHeightAt(settings: GreenSettings, x: Double, y: Double): Double {
        val customHeight = V22CustomGreenRuntime.heightAt(x, y, settings.holeDistanceM)
        val profileHeight = if (customHeight == null) {
            heightAt(settings.terrainProfileId, x, y, settings.holeDistanceM)
        } else 0.0
        return profileHeight -
            0.01 * settings.sideSlopePct * x -
            0.01 * settings.longSlopePct * y +
            (customHeight ?: 0.0)
    }

    fun slopeAt(
        profileId: Int,
        x: Double,
        y: Double,
        holeDistanceM: Double
    ): TerrainSlope = GreenSurface.slopeAt(profileId, x, y, holeDistanceM)

    fun heightAt(
        profileId: Int,
        x: Double,
        y: Double,
        holeDistanceM: Double
    ): Double = GreenSurface.heightAt(profileId, x, y, holeDistanceM)
}