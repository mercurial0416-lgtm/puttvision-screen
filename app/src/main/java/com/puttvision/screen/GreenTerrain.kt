package com.puttvision.screen


data class TerrainSlope(
    val sidePct: Double,
    val longPct: Double
)

/**
 * Compatibility facade for the spatial green library.
 * V12 derives every local terrain slope from GreenSurface.heightAt(), making
 * rendering and ball physics share one physically consistent scalar surface.
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
    ): TerrainSlope = GreenSurface.slopeAt(profileId, x, y, holeDistanceM)

    fun heightAt(
        profileId: Int,
        x: Double,
        y: Double,
        holeDistanceM: Double
    ): Double = GreenSurface.heightAt(profileId, x, y, holeDistanceM)
}
