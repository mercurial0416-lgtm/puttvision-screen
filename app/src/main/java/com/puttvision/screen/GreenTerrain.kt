package com.puttvision.screen


data class TerrainSlope(
    val sidePct: Double,
    val longPct: Double
)

/**
 * Compatibility facade for the spatial green library.
 * V13 exposes both effective height and effective slope, including the explicit
 * base plane from GreenSettings. Rendering and physics therefore consume the
 * same complete surface instead of showing only the profile residual height.
 */
object GreenTerrain {
    fun effectiveSlopeAt(settings: GreenSettings, x: Double, y: Double): TerrainSlope {
        val local = slopeAt(settings.terrainProfileId, x, y, settings.holeDistanceM)
        return TerrainSlope(
            sidePct = settings.sideSlopePct + local.sidePct,
            longPct = settings.longSlopePct + local.longPct
        )
    }

    /** Physical elevation in metres for the exact surface GreenPhysics feels. */
    fun effectiveHeightAt(settings: GreenSettings, x: Double, y: Double): Double =
        heightAt(settings.terrainProfileId, x, y, settings.holeDistanceM) -
            0.01 * settings.sideSlopePct * x -
            0.01 * settings.longSlopePct * y

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
