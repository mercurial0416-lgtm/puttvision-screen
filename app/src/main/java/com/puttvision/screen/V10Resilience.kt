package com.puttvision.screen

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import kotlin.math.abs


data class ThermalHfrDecision(
    val maxFps: Int,
    val label: String,
    val detail: String,
    val thermalStatus: Int,
    val batteryTempC: Double?
)

/**
 * Prevents long 240fps sessions from cooking the phone. The decision is sampled
 * before every precision shot, so a session can automatically move 240 -> 120 ->
 * NORMAL and later recover without restarting the app.
 */
class ThermalHfrPolicy(private val context: Context) {
    fun current(): ThermalHfrDecision {
        val power = context.getSystemService(PowerManager::class.java)
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            power?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }
        val battery = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                ?.takeIf { it > 0 }
                ?.div(10.0)
        }.getOrNull()
        return decide(status, battery)
    }

    companion object {
        fun decide(thermalStatus: Int, batteryTempC: Double?): ThermalHfrDecision {
            val critical = thermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL ||
                (batteryTempC != null && batteryTempC >= 46.0)
            val warm = thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE ||
                (batteryTempC != null && batteryTempC >= 40.5)
            val maxFps = when {
                critical -> 0
                warm -> 120
                else -> 240
            }
            val label = when (maxFps) {
                0 -> "HOT · NORMAL"
                120 -> "WARM · 120fps"
                else -> "COOL · 240fps"
            }
            val temp = batteryTempC?.let { " · ${"%.1f".format(it)}°C" }.orEmpty()
            val detail = when (maxFps) {
                0 -> "열 보호 모드$temp · 이번 샷은 일반 추적으로 측정합니다"
                120 -> "발열 보호$temp · PRECISION을 120fps로 자동 제한합니다"
                else -> "열 상태 정상$temp · 240fps 우선"
            }
            return ThermalHfrDecision(maxFps, label, detail, thermalStatus, batteryTempC)
        }
    }
}


data class AccuracyCsvReferenceRow(
    val timestampMs: Long?,
    val ball: Double?,
    val launch: Double?,
    val head: Double?,
    val face: Double?,
    val path: Double?
) {
    fun hasReference(): Boolean = ball != null || launch != null || head != null || face != null || path != null
}

data class AccuracyCsvImportResult(
    val rows: Int,
    val matched: Int,
    val timestampMatched: Int,
    val orderMatched: Int,
    val skipped: Int
) {
    fun label(): String = "CSV ${rows}행 · ${matched}샷 매칭 · 시간 $timestampMatched / 순서 $orderMatched · 스킵 $skipped"
}

/** Small permissive CSV reader for reference launch monitors / screen-golf sensors. */
object AccuracyCsvParser {
    fun parse(text: String): List<AccuracyCsvReferenceRow> {
        val lines = text.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return emptyList()
        val header = splitCsv(lines.first()).map { normalize(it) }

        fun index(vararg aliases: String): Int? {
            aliases.forEach { alias ->
                val i = header.indexOf(normalize(alias))
                if (i >= 0) return i
            }
            return null
        }

        // If an exported PuttVision CSV contains explicit ref_* columns, never
        // accidentally treat measured_* columns as reference values.
        fun refIndex(explicit: String, vararg generic: String): Int? =
            index(explicit) ?: index(*generic)

        val timeI = index("timestamp", "timestamp_ms", "time_ms", "time", "shot_time")
        val ballI = refIndex("ref_ball", "ball_speed", "ball_speed_mps", "ballspeed", "ball")
        val launchI = refIndex("ref_launch", "launch_angle", "start_angle", "start_deg", "launch", "direction")
        val headI = refIndex("ref_head", "head_speed", "club_speed", "putter_speed", "head")
        val faceI = refIndex("ref_face", "face_angle", "face")
        val pathI = refIndex("ref_path", "path_angle", "club_path", "path")

        fun value(row: List<String>, i: Int?): Double? = i?.let { idx -> row.getOrNull(idx)?.trim()?.replace("%", "")?.toDoubleOrNull() }
        fun timestamp(row: List<String>): Long? {
            val raw = timeI?.let { row.getOrNull(it) }?.trim()?.trim('"') ?: return null
            raw.toDoubleOrNull()?.let { n ->
                val ms = if (n in 1.0..9_999_999_999.0) n * 1000.0 else n
                return ms.toLong()
            }
            return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
        }

        return lines.drop(1).mapNotNull { line ->
            val row = splitCsv(line)
            val parsed = AccuracyCsvReferenceRow(
                timestampMs = timestamp(row),
                ball = value(row, ballI),
                launch = value(row, launchI),
                head = value(row, headI),
                face = value(row, faceI),
                path = value(row, pathI)
            )
            parsed.takeIf { it.hasReference() }
        }
    }

    private fun normalize(v: String): String = v.trim().trim('"').lowercase()
        .replace(" ", "_").replace("-", "_").replace("/", "_")

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { out += current.toString(); current.setLength(0) }
                else -> current.append(ch)
            }
            i++
        }
        out += current.toString()
        return out
    }
}


data class SessionRecoverySnapshot(
    val savedAtMs: Long,
    val sessionStartedAtMs: Long,
    val activeSessionIsGame: Boolean,
    val practiceEntranceMode: Int,
    val practiceCount: Int,
    val practiceDistanceM: Int,
    val practiceGreenSpeed: Double,
    val practicePatternIndex: Int,
    val practiceGreenPresetIndex: Int,
    val practiceShotsTaken: Int,
    val practicePatternShotIndex: Int,
    val gamePlayers: Int,
    val gameModeIndex: Int,
    val gameDistanceM: Int,
    val holeDistanceM: Double,
    val stimpMeters: Double,
    val sideSlopePct: Double,
    val longSlopePct: Double,
    val terrainProfileId: Int,
    val gameMode: GameModeSnapshot
)

class SessionRecoveryStore(context: Context) {
    private val prefs = context.getSharedPreferences("puttvision_session_recovery_v10", Context.MODE_PRIVATE)

    fun save(s: SessionRecoverySnapshot) {
        val g = s.gameMode
        val json = JSONObject().apply {
            put("saved", s.savedAtMs); put("started", s.sessionStartedAtMs); put("game", s.activeSessionIsGame)
            put("pem", s.practiceEntranceMode); put("pc", s.practiceCount); put("pd", s.practiceDistanceM)
            put("pgs", s.practiceGreenSpeed); put("ppi", s.practicePatternIndex); put("pgi", s.practiceGreenPresetIndex)
            put("pst", s.practiceShotsTaken); put("psi", s.practicePatternShotIndex)
            put("gp", s.gamePlayers); put("gmi", s.gameModeIndex); put("gd", s.gameDistanceM)
            put("hd", s.holeDistanceM); put("stimp", s.stimpMeters); put("ss", s.sideSlopePct)
            put("ls", s.longSlopePct); put("terrain", s.terrainProfileId)
            put("mode", JSONObject().apply {
                put("mode", g.mode.name); put("hole", g.hole); put("total", g.totalHoles); put("score", g.gameScore)
                put("streak", g.streak); put("best", g.bestStreak); put("shots", g.shots); put("made", g.made)
                put("last", g.lastPoints); put("completed", g.completed); put("players", g.playerCount)
                put("active", g.activePlayer); put("pending", g.pendingPrepare)
                put("scores", JSONArray(g.scores)); put("streaks", JSONArray(g.streaks)); put("bests", JSONArray(g.bestStreaks))
            })
        }
        prefs.edit().putString("snapshot", json.toString()).apply()
    }

    fun load(maxAgeMs: Long = 24L * 60L * 60L * 1000L): SessionRecoverySnapshot? {
        val raw = prefs.getString("snapshot", null) ?: return null
        return runCatching {
            val j = JSONObject(raw)
            val saved = j.getLong("saved")
            if (System.currentTimeMillis() - saved > maxAgeMs) {
                clear(); return@runCatching null
            }
            val gm = j.getJSONObject("mode")
            fun ints(name: String): List<Int> {
                val a = gm.optJSONArray(name) ?: JSONArray()
                return buildList { for (i in 0 until a.length()) add(a.optInt(i)) }
            }
            val mode = GameModeSnapshot(
                mode = PracticeMode.valueOf(gm.getString("mode")),
                hole = gm.optInt("hole", 1), totalHoles = gm.optInt("total", 0), gameScore = gm.optInt("score", 0),
                streak = gm.optInt("streak", 0), bestStreak = gm.optInt("best", 0), shots = gm.optInt("shots", 0),
                made = gm.optInt("made", 0), lastPoints = gm.optInt("last", 0), completed = gm.optBoolean("completed", false),
                playerCount = gm.optInt("players", 1), activePlayer = gm.optInt("active", 1), pendingPrepare = gm.optBoolean("pending", false),
                scores = ints("scores"), streaks = ints("streaks"), bestStreaks = ints("bests")
            )
            if (mode.completed) return@runCatching null
            SessionRecoverySnapshot(
                savedAtMs = saved, sessionStartedAtMs = j.optLong("started", saved), activeSessionIsGame = j.optBoolean("game"),
                practiceEntranceMode = j.optInt("pem"), practiceCount = j.optInt("pc", 10), practiceDistanceM = j.optInt("pd", 5),
                practiceGreenSpeed = j.optDouble("pgs", 2.8), practicePatternIndex = j.optInt("ppi"), practiceGreenPresetIndex = j.optInt("pgi", 2),
                practiceShotsTaken = j.optInt("pst"), practicePatternShotIndex = j.optInt("psi"), gamePlayers = j.optInt("gp", 2),
                gameModeIndex = j.optInt("gmi"), gameDistanceM = j.optInt("gd", 3), holeDistanceM = j.optDouble("hd", 5.0),
                stimpMeters = j.optDouble("stimp", 2.8), sideSlopePct = j.optDouble("ss"), longSlopePct = j.optDouble("ls"),
                terrainProfileId = j.optInt("terrain", -1), gameMode = mode
            )
        }.getOrNull()
    }

    fun clear() { prefs.edit().remove("snapshot").apply() }
}


object CrashJournal {
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val app = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                runCatching {
                    val dir = File(app.filesDir, "diagnostics").apply { mkdirs() }
                    val stack = error.stackTraceToString().take(120_000)
                    File(dir, "last_crash.txt").writeText(
                        "time=${System.currentTimeMillis()}\nthread=${thread.name}\nversion=${BuildConfig.VERSION_NAME}\n\n$stack"
                    )
                }
                previous?.uncaughtException(thread, error)
            }
            installed = true
        }
    }

    fun lastCrash(context: Context): String? = runCatching {
        File(context.filesDir, "diagnostics/last_crash.txt").takeIf { it.isFile }?.readText()?.take(120_000)
    }.getOrNull()
}

object RuntimeJanitor {
    fun cleanup(context: Context, now: Long = System.currentTimeMillis()) {
        fun prune(dir: File, maxAgeMs: Long) {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && now - file.lastModified() > maxAgeMs) runCatching { file.delete() }
            }
        }
        prune(File(context.cacheDir, "puttvision_hfr"), 6L * 60L * 60L * 1000L)
        prune(File(context.cacheDir, "updates"), 7L * 24L * 60L * 60L * 1000L)
        prune(File(context.cacheDir, "exports"), 7L * 24L * 60L * 60L * 1000L)
    }
}
