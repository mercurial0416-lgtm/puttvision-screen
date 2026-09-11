package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class V170SurfaceZonesRegressionTest {
    @Test
    fun legacyPlaneKeepsInfiniteGreenCalibration() {
        val settings = GreenSettings(terrainProfileId = -1)
        assertEquals(V170SurfaceZone.GREEN, V170SurfaceZones.zoneAt(settings, 20.0, 50.0))
    }

    @Test
    fun allTwentyFourProfilesHaveGreenFringeAndRoughZones() {
        for (profile in 0..23) {
            val settings = GreenSettings(terrainProfileId = profile)
            assertEquals("profile $profile center", V170SurfaceZone.GREEN, V170SurfaceZones.zoneAt(settings, 0.0, 14.25))
            assertEquals("profile $profile far turf", V170SurfaceZone.ROUGH, V170SurfaceZones.zoneAt(settings, 8.2, 14.25))
            assertTrue("profile $profile should expose some fringe", (-70..70).any { i ->
                V170SurfaceZones.zoneAt(settings, i / 10.0, 14.25) == V170SurfaceZone.FRINGE
            })
        }
    }

    @Test
    fun shapeFamiliesActuallyChangeTheGreenFootprint() {
        val classic = GreenSettings(terrainProfileId = 0)
        val narrow = GreenSettings(terrainProfileId = 3)
        assertEquals(V170SurfaceZone.GREEN, V170SurfaceZones.zoneAt(classic, 5.0, 14.25))
        assertTrue(V170SurfaceZones.zoneAt(narrow, 5.0, 14.25) != V170SurfaceZone.GREEN)
    }

    @Test
    fun nonFiniteCoordinatesFailClosedToRoughAndKeepResistanceFinite() {
        val settings = GreenSettings(stimpMeters = Double.NaN, terrainProfileId = 0)
        val nonFinite = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)

        nonFinite.forEach { coordinate ->
            assertEquals(
                "non-finite x must not be classified as playable green",
                V170SurfaceZone.ROUGH,
                V170SurfaceZones.zoneAt(settings, coordinate, 14.25)
            )
            assertEquals(
                "non-finite y must not be classified as playable green",
                V170SurfaceZone.ROUGH,
                V170SurfaceZones.zoneAt(settings, 0.0, coordinate)
            )
        }

        val effectiveStimp = V170SurfaceZones.effectiveStimpMeters(settings, Double.NaN, 14.25)
        assertTrue("surface resistance must stay finite", effectiveStimp.isFinite())
        assertEquals("invalid Stimp falls back before rough scaling", 2.8 * 0.28, effectiveStimp, 1e-12)
    }

    @Test
    fun slowerTurfAddsPhysicalResistanceWithoutChangingGreen() {
        fun speedAfter(x: Double): Double {
            val settings = GreenSettings(stimpMeters = 2.8, terrainProfileId = 0, trueness01 = 1.0)
            val state = SimState(x = x, y = 14.25, vx = 1.0, vy = 0.0, running = true)
            V136PhysicalRealism.applyTrueness(state, settings, 0.01)
            return hypot(state.vx, state.vy)
        }

        val green = speedAfter(0.0)
        val fringe = speedAfter(6.0)
        val rough = speedAfter(7.5)
        assertEquals("green Stimp path stays untouched", 1.0, green, 1e-12)
        assertTrue("fringe must be slower", fringe < green)
        assertTrue("rough must lose more speed than fringe", rough < fringe)
    }
}
