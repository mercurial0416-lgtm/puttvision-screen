from pathlib import Path
import re

ROOT = Path('.')


def read(path):
    return (ROOT / path).read_text(encoding='utf-8')


def write(path, text):
    (ROOT / path).write_text(text, encoding='utf-8')


def replace_once(path, old, new):
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{path}: expected one anchor, found {count}: {old[:120]!r}')
    write(path, text.replace(old, new, 1))


# ---------------------------------------------------------------------------
# Thermal hysteresis: keep public stateless decide() for regression tests, but
# current() now uses the V12 state machine so FPS does not flap around 40.5 C.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/com/puttvision/screen/V10Resilience.kt'
text = read(path)
if 'import android.os.SystemClock' not in text:
    text = text.replace('import android.os.PowerManager\n', 'import android.os.PowerManager\nimport android.os.SystemClock\n', 1)
text = text.replace(
    'class ThermalHfrPolicy(private val context: Context) {\n    fun current(): ThermalHfrDecision {',
    'class ThermalHfrPolicy(private val context: Context) {\n    private val hysteresis = ThermalHfrHysteresis()\n\n    fun current(): ThermalHfrDecision {',
    1
)
text = text.replace(
    '        return decide(status, battery)\n',
    '        return hysteresis.update(decide(status, battery), SystemClock.elapsedRealtime())\n',
    1
)
write(path, text)


# ---------------------------------------------------------------------------
# Device report now identifies the exact back camera used for the tuning key.
# Validation samples persist the active device/camera/FPS/resolution/API profile.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/com/puttvision/screen/ProductizationV8.kt'
text = read(path)
text = text.replace(
    '    val memoryMb: Int,\n    val model: String,\n    val recommendation: String\n)',
    '    val memoryMb: Int,\n    val model: String,\n    val cameraId: String,\n    val recommendation: String\n)',
    1
)
text = text.replace(
    '        var bestSize = "--"\n        var hw = "UNKNOWN"\n',
    '        var bestSize = "--"\n        var hw = "UNKNOWN"\n        var selectedCameraId = "BACK"\n',
    1
)
text = text.replace(
    '            val chars = manager.getCameraCharacteristics(id)\n',
    '            selectedCameraId = id\n            val chars = manager.getCameraCharacteristics(id)\n',
    1
)
text = text.replace(
    '        return DeviceCapabilityReport(grade, maxFps, bestSize, hw, memory, "${Build.MANUFACTURER} ${Build.MODEL}", rec)\n',
    '        return DeviceCapabilityReport(grade, maxFps, bestSize, hw, memory, "${Build.MANUFACTURER} ${Build.MODEL}", selectedCameraId, rec)\n',
    1
)
text = text.replace(
    '    val confidence: Double?,\n    val refBall: Double? = null,',
    '    val confidence: Double?,\n    val profileKey: String? = null,\n    val refBall: Double? = null,',
    1
)
text = text.replace(
    '    fun capture(metrics: ShotMetrics) {\n',
    '    fun capture(metrics: ShotMetrics, profileKey: String? = null) {\n',
    1
)
text = text.replace(
    '            confidence = metrics.confidence\n        )',
    '            confidence = metrics.confidence,\n            profileKey = profileKey\n        )',
    1
)
text = text.replace(
    'out.appendLine("timestamp,measured_ball,ref_ball,ball_error_pct,measured_launch,ref_launch,launch_error_deg,measured_head,ref_head,head_error_pct,measured_face,ref_face,face_error_deg,measured_path,ref_path,path_error_deg,confidence")',
    'out.appendLine("timestamp,profile_key,measured_ball,ref_ball,ball_error_pct,measured_launch,ref_launch,launch_error_deg,measured_head,ref_head,head_error_pct,measured_face,ref_face,face_error_deg,measured_path,ref_path,path_error_deg,confidence")',
    1
)
text = text.replace(
    '                    s.timestampMs,\n                    s.measuredBall,',
    '                    s.timestampMs,\n                    s.profileKey ?: "",\n                    s.measuredBall,',
    1
)
text = text.replace(
    '                put("mb", s.measuredBall); put("ml", s.measuredLaunch)\n',
    '                put("mb", s.measuredBall); put("ml", s.measuredLaunch); put("pk", s.profileKey ?: JSONObject.NULL)\n',
    1
)
text = text.replace(
    '                        measuredHead = j.optNullableDouble("mh"), measuredFace = j.optNullableDouble("mf"), measuredPath = j.optNullableDouble("mp"), confidence = j.optNullableDouble("c"),\n',
    '                        measuredHead = j.optNullableDouble("mh"), measuredFace = j.optNullableDouble("mf"), measuredPath = j.optNullableDouble("mp"), confidence = j.optNullableDouble("c"),\n                        profileKey = j.optString("pk").takeIf { it.isNotBlank() && it != "null" },\n',
    1
)
# Backups include every V12 profile slot, while still keeping the old single model.
old_backup = '''            put("accuracyTune", JSONObject().apply {
                put("enabled", tunePrefs.getBoolean("enabled", true))
                put("model", tunePrefs.getString("model", ""))
            })'''
new_backup = '''            put("accuracyTune", JSONObject().apply {
                put("enabled", tunePrefs.getBoolean("enabled", true))
                put("model", tunePrefs.getString("model", ""))
                put("profiles", JSONObject().apply {
                    tunePrefs.all.filterKeys { it.startsWith("model_") }.forEach { (key, value) ->
                        if (value is String) put(key, value)
                    }
                })
            })'''
if old_backup not in text:
    raise RuntimeError('ProductizationV8: accuracy backup anchor missing')
text = text.replace(old_backup, new_backup, 1)
# Import side: augment any existing accuracyTune restore block if present.
needle = 'root.optJSONObject("accuracyTune")?.let { tune ->'
if needle in text and 'optJSONObject("profiles")' not in text[text.index(needle):text.index(needle)+1400]:
    pos = text.index(needle)
    close = text.find('\n        }', pos)
    if close > pos:
        block = text[pos:close]
        insert = '''
            tune.optJSONObject("profiles")?.let { profiles ->
                val editor = tunePrefs.edit()
                profiles.keys().forEach { key -> editor.putString(key, profiles.optString(key)) }
                editor.apply()
            }'''
        text = text[:close] + insert + text[close:]
write(path, text)


# ---------------------------------------------------------------------------
# V12 multi-profile Accuracy Auto Tune. Old model is migrated as fallback, then
# new samples become exact profile data once enough are collected.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/com/puttvision/screen/V9Productization.kt'
text = read(path)
start = text.index('class AccuracyAutoTuner(')
end = text.index('fun showAccuracyTuningDialog', start)
new_tuner = r'''class AccuracyAutoTuner(context: Context, private val deviceKey: String) {
    private val prefs = context.getSharedPreferences("puttvision_accuracy_tune_v1", Context.MODE_PRIVATE)
    @Volatile private var activeKey: String = AccuracyProfileKey.build(deviceKey, "BACK", 0, "NORMAL", Build.VERSION.SDK_INT)
    @Volatile private var model: AccuracyCorrectionModel? = loadProfile(activeKey) ?: loadLegacy()

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    @Synchronized
    fun selectProfile(
        fps: Int,
        resolution: String,
        cameraId: String,
        api: Int = Build.VERSION.SDK_INT
    ): String {
        val next = AccuracyProfileKey.build(deviceKey, cameraId, fps, resolution, api)
        if (next != activeKey) {
            activeKey = next
            model = loadProfile(next)
        }
        return next
    }

    fun profileKey(): String = activeKey

    fun refresh(samples: List<ValidationSample>, force: Boolean = false): AccuracyCorrectionModel? {
        val exact = samples.filter { it.profileKey == activeKey }
        val scoped = if (exact.size >= 8) exact else samples.filter { it.profileKey == activeKey || it.profileKey == null }
        val derived = AccuracyModelCalculator.derive(scoped) ?: return model
        val current = model
        if (!force && current != null && current.sampleCount == derived.sampleCount) return current
        if (derived.improvementPct >= 7.0) {
            model = derived
            persist(activeKey, derived)
        }
        return model
    }

    fun apply(metrics: ShotMetrics): ShotMetrics {
        val m = model
        return if (enabled && m != null && m.improvementPct >= 7.0) AccuracyModelCalculator.apply(m, metrics) else metrics
    }

    fun current(): AccuracyCorrectionModel? = model

    fun reset() {
        model = null
        prefs.edit().remove(AccuracyProfileKey.slot(activeKey)).apply()
    }

    fun reload() { model = loadProfile(activeKey) ?: loadLegacy() }

    fun summary(): String {
        val mode = activeKey.substringAfter("|CAM:", "BACK|FPS:0|SIZE:NORMAL|API:${Build.VERSION.SDK_INT}")
        val m = model ?: return "$mode · 기준 센서 8샷 이상 필요"
        return "$mode · ${m.sampleCount}샷 · 개선 ${"%.0f".format(m.improvementPct)}% · BALL ×${"%.3f".format(m.ballScale)} · START ${"%+.2f".format(m.launchOffsetDeg)}°"
    }

    private fun persist(profileKey: String, m: AccuracyCorrectionModel) {
        val j = JSONObject().apply {
            put("device", deviceKey)
            put("profile", profileKey)
            put("n", m.sampleCount)
            put("ball", m.ballScale)
            put("launch", m.launchOffsetDeg)
            put("head", m.headScale)
            put("face", m.faceOffsetDeg)
            put("path", m.pathOffsetDeg)
            put("improvement", m.improvementPct)
            put("updated", m.updatedAtMs)
        }
        prefs.edit().putString(AccuracyProfileKey.slot(profileKey), j.toString()).apply()
    }

    private fun loadProfile(profileKey: String): AccuracyCorrectionModel? {
        val raw = prefs.getString(AccuracyProfileKey.slot(profileKey), null) ?: return null
        return parseModel(raw, expectedProfile = profileKey)
    }

    private fun loadLegacy(): AccuracyCorrectionModel? {
        val raw = prefs.getString("model", null) ?: return null
        return parseModel(raw, expectedProfile = null)
    }

    private fun parseModel(raw: String, expectedProfile: String?): AccuracyCorrectionModel? = runCatching {
        val j = JSONObject(raw)
        if (j.optString("device") != deviceKey) return@runCatching null
        if (expectedProfile != null && j.optString("profile") != expectedProfile) return@runCatching null
        AccuracyCorrectionModel(
            sampleCount = j.getInt("n"),
            ballScale = j.getDouble("ball"),
            launchOffsetDeg = j.getDouble("launch"),
            headScale = j.getDouble("head"),
            faceOffsetDeg = j.getDouble("face"),
            pathOffsetDeg = j.getDouble("path"),
            improvementPct = j.getDouble("improvement"),
            updatedAtMs = j.optLong("updated", 0L)
        )
    }.getOrNull()
}

'''
text = text[:start] + new_tuner + text[end:]
write(path, text)


# ---------------------------------------------------------------------------
# CameraX session exposes selected resolution for the accuracy profile key.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/com/puttvision/screen/HighSpeedCaptureController.kt'
text = read(path)
text = text.replace(
    'data class ActiveHfrSession(val fps: Int, val description: String)',
    'data class ActiveHfrSession(val fps: Int, val description: String, val resolution: String)',
    1
)
text = text.replace(
    '        val preview = Preview.Builder().build().also {',
    '''        val resolution = when (quality) {
            Quality.UHD -> "3840x2160"
            Quality.FHD -> "1920x1080"
            Quality.HD -> "1280x720"
            Quality.SD -> "720x480"
            else -> quality.toString()
        }

        val preview = Preview.Builder().build().also {''',
    1
)
text = text.replace(
    '            ActiveHfrSession(chosen.upper, desc)',
    '            ActiveHfrSession(chosen.upper, desc, resolution)',
    1
)
write(path, text)


# ---------------------------------------------------------------------------
# Main activity never blocks on the inverse solver. It prefetches whenever
# settings change and speaks an exact guide only when a reliable read is ready.
# It also switches the tuning profile with the actual capture mode.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/com/puttvision/screen/MainActivity.kt'
text = read(path)
text = text.replace(
    'accuracyValidationLab.capture(metrics)',
    'accuracyValidationLab.capture(metrics, accuracyAutoTuner.profileKey())'
)
text = text.replace(
    '    private fun updateSettingLabels() {\n',
    '    private fun updateSettingLabels() {\n        GreenReadRuntime.prefetch(engine.settings)\n',
    1
)
old_voice = '''        if (::voiceCoach.isInitialized) {
            voiceCoach.speakReady(GreenReadAdvisor.read(engine.settings))
        }'''
new_voice = '''        if (::voiceCoach.isInitialized) {
            val cachedRead = GreenReadRuntime.peekOrSchedule(engine.settings)
            voiceCoach.speakReady(cachedRead?.takeIf { it.solverReliable })
        }'''
if old_voice not in text:
    raise RuntimeError('MainActivity voice green-read anchor missing')
text = text.replace(old_voice, new_voice, 1)
text = text.replace(
    '        if (!hfrHardwareAvailable || hfrCoolingDown) {\n            tracker.arm()\n',
    '        if (!hfrHardwareAvailable || hfrCoolingDown) {\n            accuracyAutoTuner.selectProfile(0, "NORMAL", deviceReport.cameraId)\n            tracker.arm()\n',
    1
)
text = text.replace(
    '        if (thermal.maxFps < 120) {\n            val switchingFromHfr = hfrController != null\n',
    '        if (thermal.maxFps < 120) {\n            accuracyAutoTuner.selectProfile(0, "NORMAL", deviceReport.cameraId)\n            val switchingFromHfr = hfrController != null\n',
    1
)
cap_anchor = '''        val desiredHfrCap = min(
            thermal.maxFps,
            deviceReport.maxHfrFps.takeIf { it >= 120 } ?: thermal.maxFps
        ).coerceIn(120, 240)
'''
if cap_anchor not in text:
    raise RuntimeError('MainActivity desired HFR cap anchor missing')
text = text.replace(cap_anchor, cap_anchor + '''        accuracyAutoTuner.selectProfile(
            desiredHfrCap,
            deviceReport.bestHfrSize,
            deviceReport.cameraId
        )
        GreenReadRuntime.prefetch(engine.settings)

''', 1)
bind_anchor = '        val session =\n            controller.bindBest(maxFps = desiredHfrCap)\n'
if bind_anchor not in text:
    raise RuntimeError('MainActivity HFR session anchor missing')
text = text.replace(bind_anchor, bind_anchor + '''
        if (session != null) {
            accuracyAutoTuner.selectProfile(session.fps, session.resolution, deviceReport.cameraId)
        }
''', 1)
if 'GreenReadAdvisor.read(' in text:
    raise RuntimeError('MainActivity still contains blocking GreenReadAdvisor.read')
write(path, text)


# ---------------------------------------------------------------------------
# TV: non-blocking read, height-projected grid/ball/trails, confidence gating,
# and render cadence that sleeps on static result screens.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/com/puttvision/screen/GreenView.kt'
text = read(path)
text = text.replace(
    '        postInvalidateOnAnimation()\n',
    '''        val dynamic = engine.state?.running == true || engine.lastResult == null || ProductSessionRuntime.tvCalibrationGuide
        if (dynamic) postInvalidateOnAnimation() else postInvalidateDelayed(200L)
''',
    1
)
old_coords = '''        fun sy(y: Double): Float {
            val t = (y / maxY).coerceIn(0.0, 1.0).toFloat()
            return bottomY - (bottomY - horizonY) * t
        }
        fun halfWidthAt(yPix: Float): Float {
            val t = ((yPix - horizonY) / (bottomY - horizonY)).coerceIn(0f, 1f)
            return w * (.175f + .265f * t)
        }
        fun sx(x: Double, y: Double): Float {
            val yp = sy(y)
            val sideRange = max(1.15, settings.holeDistanceM * .20)
            return centerX + (x / sideRange).toFloat() * halfWidthAt(yp)
        }
'''
new_coords = '''        fun syBase(y: Double): Float {
            val t = (y / maxY).coerceIn(0.0, 1.0).toFloat()
            return bottomY - (bottomY - horizonY) * t
        }
        val originHeight = GreenTerrain.heightAt(settings.terrainProfileId, 0.0, 0.0, settings.holeDistanceM)
        fun sySurface(x: Double, y: Double): Float {
            val z = GreenTerrain.heightAt(settings.terrainProfileId, x, y, settings.holeDistanceM)
            val relief = ((z - originHeight) * h * 1.35).toFloat()
            return syBase(y) - relief
        }
        fun sy(y: Double): Float = sySurface(0.0, y)
        fun halfWidthAt(yPix: Float): Float {
            val t = ((yPix - horizonY) / (bottomY - horizonY)).coerceIn(0f, 1f)
            return w * (.175f + .265f * t)
        }
        fun sx(x: Double, y: Double): Float {
            val yp = syBase(y)
            val sideRange = max(1.15, settings.holeDistanceM * .20)
            return centerX + (x / sideRange).toFloat() * halfWidthAt(yp)
        }
'''
if old_coords not in text:
    raise RuntimeError('GreenView coordinate block missing')
text = text.replace(old_coords, new_coords, 1)
# Warp horizontal distance grid along the actual height surface.
old_line = '            c.drawLine(centerX - hw, yp, centerX + hw, yp, p)\n'
new_line = '''            val contour = Path()
            for (sample in 0..24) {
                val frac = sample / 24.0 * 2.0 - 1.0
                val xM = gridSideRange * frac
                val px = sx(xM, gridY)
                val py = sySurface(xM, gridY)
                if (sample == 0) contour.moveTo(px, py) else contour.lineTo(px, py)
            }
            c.drawPath(contour, p)
'''
text = text.replace(old_line, new_line, 1)
text = text.replace('                val py = sy(yM)\n', '                val py = sySurface(xM, yM)\n', 1)
text = text.replace('        val read = if (preShot) GreenReadAdvisor.read(settings) else null\n', '        val read = if (preShot) GreenReadRuntime.peekOrSchedule(settings) else null\n', 1)
text = text.replace('                        val py = sy(pyM)\n', '                        val py = sySurface(pxM, pyM)\n', 1)
text = text.replace('            read.predictedTrail.takeIf { it.size >= 2 }?.let { trail ->', '            read.predictedTrail.takeIf { read.solverReliable && it.size >= 2 }?.let { trail ->', 1)
text = text.replace('                    moveTo(sx(trail.first().first, trail.first().second), sy(trail.first().second))\n', '                    moveTo(sx(trail.first().first, trail.first().second), sySurface(trail.first().first, trail.first().second))\n', 2)
text = text.replace('                        lineTo(sx(point.first, point.second), sy(point.second))\n', '                        lineTo(sx(point.first, point.second), sySurface(point.first, point.second))\n', 1)
text = text.replace('                trail.drop(1).forEach { point -> lineTo(sx(point.first, point.second), sy(point.second)) }\n', '                trail.drop(1).forEach { point -> lineTo(sx(point.first, point.second), sySurface(point.first, point.second)) }\n', 1)
# Aim marker is shown only when solver residual is trustworthy.
aim_start = '''            val aimX = read.aimOffsetCm / 100.0
            val ax = sx(aimX, holeY)
            val ay = sy(holeY)
            val radius = max(8f, w * .0068f)'''
if aim_start not in text:
    raise RuntimeError('GreenView aim marker anchor missing')
text = text.replace(aim_start, '''            if (read.solverReliable) {
                val aimX = read.aimOffsetCm / 100.0
                val ax = sx(aimX, holeY)
                val ay = sySurface(aimX, holeY)
                val radius = max(8f, w * .0068f)''', 1)
label_anchor = '            c.drawText(aimLabel, ax + radius * 1.65f, ay - radius * .35f, p)\n'
text = text.replace(label_anchor, label_anchor + '            }\n', 1)
text = text.replace('        val hy = sy(holeY)\n', '        val hy = sySurface(0.0, holeY)\n', 1)
text = text.replace('        val by = if (state != null) sy(state.y) else sy(0.0)\n', '        val by = if (state != null) sySurface(state.x, state.y) else sySurface(0.0, 0.0)\n', 1)
# Readout shows residual instead of pretending an unreliable solve is exact.
text = text.replace(
    '    val main = if (read.aimSideLabel == "센터") {\n        "센터"\n    } else {\n        "${read.aimSideLabel}  ${"%.1f".format(read.cupCount)}컵"\n    }',
    '''    val main = when {
        !read.solverReliable -> "추천선 재계산"
        read.aimSideLabel == "센터" -> "센터"
        else -> "${read.aimSideLabel}  ${"%.1f".format(read.cupCount)}컵"
    }''',
    1
)
text = text.replace(
    '    c.drawText("$head  ·  ${read.paceHint}  ·  ${"%.2f".format(read.recommendedBallSpeedMps)}m/s", left + pad, bottom - h * .014f, p)\n',
    '    val residual = "SOLVER ±${"%.1f".format(read.solverMissCm)}cm"\n    c.drawText("$head  ·  ${read.paceHint}  ·  ${"%.2f".format(read.recommendedBallSpeedMps)}m/s  ·  $residual", left + pad, bottom - h * .014f, p)\n',
    1
)
# Result panel uses async cache too.
text = text.replace('    val read = GreenReadAdvisor.read(engine.settings)\n', '    val read = GreenReadRuntime.peekOrSchedule(engine.settings)\n', 1)
old_result_text = '    val readText = if (read.aimSideLabel == "센터") "추천 에임 센터" else "추천 ${read.aimSideLabel} ${"%.1f".format(read.cupCount)}컵"\n'
new_result_text = '''    val readText = when {
        read == null -> "추천 에임 계산중"
        !read.solverReliable -> "추천 에임 보류 · SOLVER ±${"%.1f".format(read.solverMissCm)}cm"
        read.aimSideLabel == "센터" -> "추천 에임 센터"
        else -> "추천 ${read.aimSideLabel} ${"%.1f".format(read.cupCount)}컵"
    }
'''
if old_result_text not in text:
    raise RuntimeError('GreenView result read anchor missing')
text = text.replace(old_result_text, new_result_text, 1)
if 'GreenReadAdvisor.read(' in text:
    raise RuntimeError('GreenView still contains blocking read')
write(path, text)


# ---------------------------------------------------------------------------
# Practice preview coloration now represents elevation as well as slope vectors.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/com/puttvision/screen/PracticeGreenPreviewView.kt'
text = read(path)
text = text.replace(
    '                val s = GreenTerrain.effectiveSlopeAt(settings, realX, realY)\n                val mag = hypot(s.sidePct, s.longPct)\n                p.color = slopeColor(s.sidePct, s.longPct, mag)\n',
    '                val s = GreenTerrain.effectiveSlopeAt(settings, realX, realY)\n                val z = GreenTerrain.heightAt(styleIndex, realX, realY, holeDistanceM)\n                val mag = hypot(s.sidePct, s.longPct)\n                p.color = surfaceColor(z, s.sidePct, s.longPct, mag)\n',
    1
)
text = text.replace(
    '    private fun slopeColor(side: Double, long: Double, magnitude: Double): Int {\n',
    '    private fun surfaceColor(heightM: Double, side: Double, long: Double, magnitude: Double): Int {\n',
    1
)
text = text.replace(
    '        val r = (18 + hot * 210).toInt().coerceIn(0, 255)\n        val g = (126 + (1.0 - hot) * 82).toInt().coerceIn(0, 255)\n        val b = (52 + directional * 135 + (1.0 - hot) * 32).toInt().coerceIn(0, 255)\n',
    '        val elevation = (heightM / .025).coerceIn(-1.0, 1.0)\n        val lift = elevation * 24.0\n        val r = (18 + hot * 190 + lift).toInt().coerceIn(0, 255)\n        val g = (126 + (1.0 - hot) * 82 + lift).toInt().coerceIn(0, 255)\n        val b = (52 + directional * 120 + (1.0 - hot) * 32 + lift).toInt().coerceIn(0, 255)\n',
    1
)
write(path, text)


# ---------------------------------------------------------------------------
# Tests for new physical invariants and profile isolation.
# ---------------------------------------------------------------------------
(ROOT / 'app/src/test/java/com/puttvision/screen/GreenSurfaceV12Test.kt').write_text(r'''package com.puttvision.screen

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
''', encoding='utf-8')

(ROOT / 'app/src/test/java/com/puttvision/screen/ThermalHysteresisV12Test.kt').write_text(r'''package com.puttvision.screen

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ThermalHysteresisV12Test {
    private fun raw(fps: Int, temp: Double) = ThermalHfrDecision(
        maxFps = fps,
        label = "raw",
        detail = "raw",
        thermalStatus = PowerManager.THERMAL_STATUS_NONE,
        batteryTempC = temp
    )

    @Test fun warmThrottleIsImmediateButRecoveryWaits() {
        val h = ThermalHfrHysteresis()
        assertEquals(120, h.update(raw(120, 41.0), 1_000L).maxFps)
        assertEquals(120, h.update(raw(240, 38.0), 2_000L).maxFps)
        assertEquals(120, h.update(raw(240, 38.0), 61_000L).maxFps)
        assertEquals(240, h.update(raw(240, 38.0), 62_100L).maxFps)
    }

    @Test fun hotModeRecoversThrough120First() {
        val h = ThermalHfrHysteresis()
        assertEquals(0, h.update(raw(0, 47.0), 1_000L).maxFps)
        assertEquals(0, h.update(raw(240, 38.0), 2_000L).maxFps)
        assertEquals(120, h.update(raw(240, 38.0), 32_100L).maxFps)
        assertEquals(120, h.update(raw(240, 38.0), 33_000L).maxFps)
    }
}
''', encoding='utf-8')

(ROOT / 'app/src/test/java/com/puttvision/screen/AccuracyProfileV12Test.kt').write_text(r'''package com.puttvision.screen

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccuracyProfileV12Test {
    @Test fun captureModesGetDifferentStableKeys() {
        val a = AccuracyProfileKey.build("Samsung S25", "0", 240, "1920x1080", 36)
        val b = AccuracyProfileKey.build("Samsung S25", "0", 120, "1920x1080", 36)
        val c = AccuracyProfileKey.build("Samsung S25", "0", 240, "1280x720", 36)
        assertNotEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(AccuracyProfileKey.slot(a), AccuracyProfileKey.slot(b))
        assertTrue(AccuracyProfileKey.slot(a).startsWith("model_"))
    }
}
''', encoding='utf-8')

# Ensure expected async/runtime source exists from the staged branch.
for required in [
    'app/src/main/java/com/puttvision/screen/GreenSurface.kt',
    'app/src/main/java/com/puttvision/screen/GreenReadRuntime.kt',
    'app/src/main/java/com/puttvision/screen/V12AdaptiveSystems.kt'
]:
    if not (ROOT / required).is_file():
        raise RuntimeError(f'missing staged V12 source: {required}')

print('V12 patch applied')
