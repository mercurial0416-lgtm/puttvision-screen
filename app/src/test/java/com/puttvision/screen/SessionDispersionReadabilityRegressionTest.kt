package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDispersionReadabilityRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun averagesUseSemanticDirectionsInsteadOfSignedNumbers() {
        val source = asset("address_relief_camera.gd")
        assertTrue(source.contains("CENTER 0 cm"))
        assertTrue(source.contains("R %.0f cm"))
        assertTrue(source.contains("L %.0f cm"))
        assertTrue(source.contains("CUP 0 cm"))
        assertTrue(source.contains("LONG %.0f cm"))
        assertTrue(source.contains("SHORT %.0f cm"))
        assertTrue(source.contains("_session_line_average_text(_v179_mean(0))"))
        assertTrue(source.contains("_session_pace_average_text(_v179_mean(1))"))
    }

    @Test
    fun newestRepIsEmphasizedForEveryPartialHistorySize() {
        val source = asset("address_relief_camera.gd")
        assertTrue(source.contains("var active_count := mini(_v179_samples.size(), _v179_points.size())"))
        assertTrue(source.contains("var latest := index == active_count - 1"))
        assertTrue(source.contains("SESSION_DISPERSION_RECENT_SIZE if latest else SESSION_DISPERSION_HISTORY_SIZE"))
        assertTrue(source.contains("SESSION_DISPERSION_RECENT_COLOR if latest else SESSION_DISPERSION_HISTORY_COLOR"))
        assertFalse(source.contains("index < V179_HISTORY - 1"))
    }

    @Test
    fun productionKeepsEstablishedTvBehaviorAndPreviewExercisesReadability() {
        val tvScene = asset("v143_tv.tscn")
        val liveLayer = asset("replay_timeline_camera_truth.gd")
        val greenRead = asset("green_read_direction_truth.gd")
        val depthFinish = asset("relief_depth_finish.gd")
        val previewScene = asset("v143_preview.tscn")
        assertTrue(tvScene.contains("res://replay_timeline_camera_truth.gd"))
        assertTrue(liveLayer.contains("extends \"res://green_read_direction_truth.gd\""))
        assertTrue(greenRead.contains("extends \"res://relief_depth_finish.gd\""))
        assertTrue(depthFinish.contains("extends \"res://address_relief_camera.gd\""))
        assertTrue(previewScene.contains("res://session_dispersion_readability_preview.gd"))
        val preview = asset("session_dispersion_readability_preview.gd")
        assertTrue(preview.contains("SESSION_DISPERSION_READABILITY_OK=1"))
        assertTrue(preview.contains("_v179_points[2].color != SESSION_DISPERSION_RECENT_COLOR"))
    }

    @Test
    fun presentationPolishCannotMutateAuthoritativePuttingInputs() {
        for (path in listOf("address_relief_camera.gd", "relief_depth_finish.gd", "green_read_direction_truth.gd", "replay_timeline_camera_truth.gd", "session_dispersion_readability_preview.gd")) {
            val source = asset(path)
            assertFalse(source.contains("GreenTerrain.set"))
            assertFalse(source.contains("GreenReadAdvisor.set"))
            assertFalse(source.contains("ballVelocity ="))
            assertFalse(source.contains("readLineDeltaCm ="))
            assertFalse(source.contains("paceDeltaCm ="))
        }
    }
}
