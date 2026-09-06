package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseAtmosphereDepthRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun atmosphereAddsSoftSunAndHorizonDepthToExistingSkyOnly() {
        val source = asset("course_atmosphere_depth.gd")
        assertTrue(source.contains("V161GradientSkyShell"))
        assertTrue(source.contains("float horizon_band"))
        assertTrue(source.contains("float sun_core"))
        assertTrue(source.contains("float sun_bloom"))
        assertTrue(source.contains("warm_haze"))
        assertTrue(source.contains("upper_depth"))
    }

    @Test
    fun atmosphereFailsClosedIfInheritedSkyContractChanges() {
        val source = asset("course_atmosphere_depth.gd")
        assertTrue(source.contains("if not source.contains(anchor):"))
        assertTrue(source.contains("if not patched.contains(ATMOSPHERE_MARKER)"))
        assertTrue(source.contains("return\n    material.shader.code = patched"))
    }

    @Test
    fun atmosphereStaysForwardMobileSafeAndPresentationOnly() {
        val source = asset("course_atmosphere_depth.gd")
        val project = asset("project.godot")
        assertTrue(source.contains("call_deferred(\"_install_course_atmosphere\")"))
        assertTrue(source.contains("set_process(false)"))
        assertFalse(source.contains("func _process("))
        assertFalse(source.contains("func _physics_process("))
        assertFalse(source.contains("FogVolume"))
        assertFalse(source.contains("GPUParticles"))
        assertFalse(source.contains("GreenTerrain"))
        assertFalse(source.contains("GreenReadAdvisor"))
        assertFalse(source.contains("score ="))
        assertTrue(project.contains("renderer/rendering_method=\"mobile\""))
    }

    @Test
    fun atmosphereIsWiredIntoProductionAndPreview() {
        val tv = asset("v143_tv.tscn")
        val preview = asset("v143_preview.tscn")
        assertTrue(tv.contains("res://course_atmosphere_depth.gd"))
        assertTrue(preview.contains("res://course_atmosphere_depth.gd"))
        assertTrue(tv.contains("CourseAtmosphereDepth"))
        assertTrue(preview.contains("CourseAtmosphereDepth"))
    }
}
