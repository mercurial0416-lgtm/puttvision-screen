package com.puttvision.screen

import org.junit.Assert.assertTrue
import org.junit.Test

class GreenSurfaceV12Test {
    @Test fun namedSurfacesKeepExpectedDirections() {
        val d = 6.0
        assertTrue(GreenSurface.slopeAt(1, 0.0, d * .5, d).sidePct > 0.15)
        assertTrue(GreenSurface.slopeAt(2, 0.0, d * .5, d).sidePct < -0.15)
        assertTrue(GreenSurface.slopeAt(3, 0.0, d * .5, d).longPct < -0.10)
        assertTrue(GreenSurface.slopeAt(4, 0.0, d * .5, d).longPct > 0.10)
    }

    @Test fun crownAndBowlAreRealHeightFields() {
        val d = 6.0
        val crownCenter = GreenSurface.heightAt(14, 0.0, d * .48, d)
        val crownEdge = GreenSurface.heightAt(14, d * .18, d * .48, d)
        val bowlCenter = GreenSurface.heightAt(15, 0.0, d * .56, d)
        val bowlEdge = GreenSurface.heightAt(15, d * .18, d * .56, d)
        assertTrue(crownCenter > crownEdge)
        assertTrue(bowlCenter < bowlEdge)
    }

    @Test fun allProfilesStayFiniteAcrossSurface() {
        for (profile in 0..23) for (iy in 0..8) for (ix in -4..4) {
            val d = 8.0
            val x = ix * .35
            val y = d * iy / 8.0
            val z = GreenSurface.heightAt(profile, x, y, d)
            val s = GreenSurface.slopeAt(profile, x, y, d)
            assertTrue("height $profile", z.isFinite())
            assertTrue("side $profile", s.sidePct.isFinite())
            assertTrue("long $profile", s.longPct.isFinite())
        }
    }
}
