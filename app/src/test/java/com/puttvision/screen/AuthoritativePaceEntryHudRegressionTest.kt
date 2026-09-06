package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthoritativePaceEntryHudRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun paceHudReportsSolverReadinessInsteadOfInventingDistanceGradeIntent() {
        val source = asset("v185_pace_intent.gd")

        assertTrue(source.contains("get(\"_v166_solver_ready\")"))
        assertTrue(source.contains("PACE  PHYSICS READ"))
        assertTrue(source.contains("PACE  SOLVING"))
        assertFalse(source.contains("func _v185_intent("))
        assertFalse(source.contains("PACE  SOFT"))
        assertFalse(source.contains("PACE  FIRM"))
        assertFalse(source.contains("distance_term"))
        assertFalse(source.contains("slope_term"))
    }

    @Test
    fun legacyPaceBarFailsClosedInsteadOfSurvivingHotReload() {
        val source = asset("v185_pace_intent.gd")
        val refresh = source.substringAfter("func _v185_refresh_pace")
            .substringBefore("func _v183_update")

        assertTrue(refresh.contains("_v185_pace_track.visible = false"))
        assertTrue(refresh.contains("_v185_pace_track.points = PackedVector2Array()"))
        assertTrue(refresh.contains("_v185_pace_fill.visible = false"))
        assertTrue(refresh.contains("_v185_pace_fill.points = PackedVector2Array()"))
        assertTrue(refresh.contains("_v185_pace_marker.visible = false"))
    }

    @Test
    fun heuristicNumericCupEntryWindowIsRemovedInFavorOfPathDerivedGate() {
        val source = asset("v186_cup_entry.gd")
        val gate = asset("cup_entry_read_gate.gd")
        val tv = asset("v143_tv.tscn")

        assertFalse(source.contains("func _v186_entry_target("))
        assertFalse(source.contains("func _v186_entry_band("))
        assertFalse(source.contains("ENTRY %.1f–%.1f"))
        assertFalse(source.contains("CupEntryWindow\""))
        assertTrue(source.contains("_v186_hide_legacy_entry()"))
        assertTrue(gate.contains("root.call(\"_v183_path\", offset_m)"))
        assertTrue(gate.contains("_entry_badge_text(curve, geometry)"))
        assertTrue(tv.contains("res://cup_entry_read_gate.gd"))
    }

    @Test
    fun cleanupCannotWritePhysicsAdvisorAimOrScoreState() {
        val pace = asset("v185_pace_intent.gd")
        val entry = asset("v186_cup_entry.gd")
        val combined = pace + "\n" + entry

        assertFalse(combined.contains("GreenTerrain("))
        assertFalse(combined.contains("GreenReadAdvisor("))
        assertFalse(combined.contains("recommendedAimOffsetM ="))
        assertFalse(combined.contains("score ="))
        assertFalse(combined.contains("ball.position"))
    }
}
