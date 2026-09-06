package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastHudTelemetryTruthRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun broadcastHudRejectsMalformedAndNonFiniteScalarTelemetryBeforeFormatting() {
        val source = asset("v174_broadcast_hud.gd")
        val helper = source.substringAfter("func _v174_snapshot_float")
            .substringBefore("func _update_hud")

        assertTrue(helper.contains("raw_type != TYPE_INT and raw_type != TYPE_FLOAT"))
        assertTrue(helper.contains("if not is_finite(value):"))
        assertTrue(helper.contains("{\"valid\": false, \"value\": 0.0}"))
        assertTrue(helper.contains("{\"valid\": true, \"value\": value}"))
    }

    @Test
    fun breakAndGradeFailClosedInsteadOfPretendingMissingDataIsStraightAndLevel() {
        val source = asset("v174_broadcast_hud.gd")
        val update = source.substringAfter("func _update_hud")

        assertTrue(update.contains("_v174_snapshot_float(s, \"sideSlope\")"))
        assertTrue(update.contains("_v174_snapshot_float(s, \"longSlope\")"))
        assertTrue(update.contains("_v174_break_value.text = \"BREAK  --\""))
        assertTrue(update.contains("_v174_grade_value.text = \"GRADE  --\""))
        assertTrue(update.contains("slope_label.text = \"SLOPE DATA UNAVAILABLE\""))
        assertFalse(update.contains("float(s.get(\"sideSlope\", 0.0))"))
        assertFalse(update.contains("float(s.get(\"longSlope\", 0.0))"))
    }

    @Test
    fun distanceStimpAndLiveSpeedDoNotManufacturePlausibleNumbers() {
        val source = asset("v174_broadcast_hud.gd")
        val update = source.substringAfter("func _update_hud")

        assertTrue(update.contains("_v174_snapshot_float(s, \"distanceToCup\")"))
        assertTrue(update.contains("_v174_snapshot_float(s, \"stimp\")"))
        assertTrue(update.contains("_v174_snapshot_float(s, \"speed\")"))
        assertTrue(update.contains("_v174_remaining_label.text = \"-- m\""))
        assertTrue(update.contains("stimp_label.text = \"-- m\""))
        assertTrue(update.contains("if running and bool(speed_sample.get(\"valid\", false)):"))
        assertTrue(update.contains("speed_label.text = \"-- m/s\""))
        assertFalse(update.contains("running and is_finite(speed)"))
        assertFalse(update.contains("float(s.get(\"stimp\", 2.8))"))
    }

    @Test
    fun liveSpeedUsesRawSnapshotTruthInsteadOfInheritedCoercedArgument() {
        val source = asset("v174_broadcast_hud.gd")
        val update = source.substringAfter("func _update_hud")

        val rawSample = update.indexOf("var speed_sample := _v174_snapshot_float(s, \"speed\")")
        val formatting = update.indexOf("speed_label.text = \"%.2f m/s\"")
        assertTrue(rawSample >= 0)
        assertTrue(formatting > rawSample)
        assertTrue(update.contains("float(speed_sample.get(\"value\", speed))"))
    }

    @Test
    fun validTelemetryKeepsExistingCommercialReadSemanticsAndPhysicsBoundary() {
        val source = asset("v174_broadcast_hud.gd")

        assertTrue(source.contains("return \"STRAIGHT\""))
        assertTrue(source.contains("return \"LEFT\" if side < 0.0 else \"RIGHT\""))
        assertTrue(source.contains("return \"DOWNHILL\" if long_slope > 0.0 else \"UPHILL\""))
        assertFalse(source.contains("GreenTerrain("))
        assertFalse(source.contains("GreenReadAdvisor("))
        assertFalse(source.contains("score ="))
    }
}
