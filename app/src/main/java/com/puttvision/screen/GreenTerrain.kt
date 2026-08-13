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
        if (custom != null) return custom
        val local = slopeAt(settings.terrainProfileId, x, y, settings.holeDistanceM)
        return TerrainSlope(
            sidePct = settings.sideSlopePct + local.sidePct,
            longPct = settings.longSlopePct + local.longPct
        )
    }

    /** Physical elevation in metres for the exact surface GreenPhysics feels. */
    fun effectiveHeightAt(settings: GreenSettings, x: Double, y: Double): Double {
        val customHeight = V22CustomGreenRuntime.heightAt(x, y, settings.holeDistanceM)
        if (customHeight != null) return customHeight
        return heightAt(settings.terrainProfileId, x, y, settings.holeDistanceM) -
            0.01 * settings.sideSlopePct * x -
            0.01 * settings.longSlopePct * y
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