package com.puttvision.screen

import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeRingBoundaryAngleWrapRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    private fun wrapPi(value: Double): Double {
        var wrapped = (value + PI) % (2.0 * PI)
        if (wrapped < 0.0) wrapped += 2.0 * PI
        return wrapped - PI
    }

    @Test
    fun boundaryBisectionKeepsOutsideAngleOnLocalArcAcrossPiSeam() {
        val production = asset("practice_ring_boundary_finish.gd")
        val scene = asset("v143_tv.tscn")
        val readSpacing = asset("read_landmark_spatial_spacing.gd")

        assertTrue(scene.contains("res://replay_timeline_camera_truth.gd"))
        assertTrue(readSpacing.contains("extends \"res://practice_ring_boundary_finish.gd\""))
        assertTrue(production.contains("inside_angle + wrapf(outside_angle - inside_angle, -PI, PI)"))

        val inside = Math.toRadians(179.0)
        val outsideRaw = Math.toRadians(-179.0)
        val outsideLocal = inside + wrapPi(outsideRaw - inside)
        val midpoint = (inside + outsideLocal) * 0.5

        // The corrected bisection stays around the nearby 180-degree edge instead of jumping to 0.
        assertTrue(abs(abs(midpoint) - PI) < Math.toRadians(2.0))
    }

    @Test
    fun seamFixRemainsPresentationOnly() {
        val production = asset("practice_ring_boundary_finish.gd")

        assertTrue(production.contains("extends \"res://replay_spatial_pacing.gd\""))
        assertTrue(!production.contains("GreenTerrain.set"))
        assertTrue(!production.contains("GreenReadAdvisor.set"))
        assertTrue(!production.contains("ballVelocity ="))
    }
}
