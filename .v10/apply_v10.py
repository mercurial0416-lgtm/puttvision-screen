from pathlib import Path

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        raise RuntimeError(f'missing patch anchor in {path}: {old[:120]!r}')
    if text.count(old) != 1:
        raise RuntimeError(f'non-unique patch anchor in {path}: count={text.count(old)}')
    write(path, text.replace(old, new, 1))


# ---------------------------------------------------------------------------
# V10 resilience / thermal / crash / CSV / session recovery runtime.
# ---------------------------------------------------------------------------
write('app/src/main/java/com/puttvision/screen/V10Resilience.kt', r'''package com.puttvision.screen

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
''')


# ---------------------------------------------------------------------------
# Game mode snapshot / restore.
# ---------------------------------------------------------------------------
replace_once(
    'app/src/main/java/com/puttvision/screen/GameModes.kt',
    '''data class GameStatus(\n    var mode: PracticeMode = PracticeMode.PRACTICE,\n    var hole: Int = 1,\n    var totalHoles: Int = 0,\n    var gameScore: Int = 0,\n    var streak: Int = 0,\n    var bestStreak: Int = 0,\n    var shots: Int = 0,\n    var made: Int = 0,\n    var lastPoints: Int = 0,\n    var completed: Boolean = false,\n    var playerCount: Int = 1,\n    var activePlayer: Int = 1,\n    var playerScores: List<Int> = listOf(0)\n)\n''',
    '''data class GameStatus(\n    var mode: PracticeMode = PracticeMode.PRACTICE,\n    var hole: Int = 1,\n    var totalHoles: Int = 0,\n    var gameScore: Int = 0,\n    var streak: Int = 0,\n    var bestStreak: Int = 0,\n    var shots: Int = 0,\n    var made: Int = 0,\n    var lastPoints: Int = 0,\n    var completed: Boolean = false,\n    var playerCount: Int = 1,\n    var activePlayer: Int = 1,\n    var playerScores: List<Int> = listOf(0)\n)\n\ndata class GameModeSnapshot(\n    val mode: PracticeMode,\n    val hole: Int,\n    val totalHoles: Int,\n    val gameScore: Int,\n    val streak: Int,\n    val bestStreak: Int,\n    val shots: Int,\n    val made: Int,\n    val lastPoints: Int,\n    val completed: Boolean,\n    val playerCount: Int,\n    val activePlayer: Int,\n    val pendingPrepare: Boolean,\n    val scores: List<Int>,\n    val streaks: List<Int>,\n    val bestStreaks: List<Int>\n)\n'''
)
replace_once(
    'app/src/main/java/com/puttvision/screen/GameModes.kt',
    '''    private fun syncActivePlayerState() {\n''',
    '''    fun snapshot(): GameModeSnapshot = GameModeSnapshot(\n        mode = status.mode, hole = status.hole, totalHoles = status.totalHoles, gameScore = status.gameScore,\n        streak = status.streak, bestStreak = status.bestStreak, shots = status.shots, made = status.made,\n        lastPoints = status.lastPoints, completed = status.completed, playerCount = status.playerCount,\n        activePlayer = status.activePlayer, pendingPrepare = pendingPrepare, scores = scores.toList(),\n        streaks = streaks.toList(), bestStreaks = bestStreaks.toList()\n    )\n\n    fun restore(snapshot: GameModeSnapshot) {\n        val count = snapshot.playerCount.coerceIn(1, 4)\n        fun normalized(values: List<Int>): IntArray = IntArray(count) { i -> values.getOrElse(i) { 0 } }\n        scores = normalized(snapshot.scores)\n        streaks = normalized(snapshot.streaks)\n        bestStreaks = normalized(snapshot.bestStreaks)\n        pendingPrepare = snapshot.pendingPrepare\n        status.mode = snapshot.mode\n        status.hole = snapshot.hole.coerceAtLeast(1)\n        status.totalHoles = snapshot.totalHoles.coerceAtLeast(0)\n        status.gameScore = snapshot.gameScore\n        status.streak = snapshot.streak\n        status.bestStreak = snapshot.bestStreak\n        status.shots = snapshot.shots.coerceAtLeast(0)\n        status.made = snapshot.made.coerceAtLeast(0)\n        status.lastPoints = snapshot.lastPoints\n        status.completed = snapshot.completed\n        status.playerCount = count\n        status.activePlayer = snapshot.activePlayer.coerceIn(1, count)\n        status.playerScores = scores.toList()\n    }\n\n    private fun syncActivePlayerState() {\n'''
)


# ---------------------------------------------------------------------------
# One source of truth for effective slope.
# ---------------------------------------------------------------------------
replace_once(
    'app/src/main/java/com/puttvision/screen/GreenTerrain.kt',
    '''object GreenTerrain {\n    fun slopeAt(\n''',
    '''object GreenTerrain {\n    fun effectiveSlopeAt(settings: GreenSettings, x: Double, y: Double): TerrainSlope {\n        val local = slopeAt(settings.terrainProfileId, x, y, settings.holeDistanceM)\n        return TerrainSlope(\n            sidePct = settings.sideSlopePct + local.sidePct,\n            longPct = settings.longSlopePct + local.longPct\n        )\n    }\n\n    fun slopeAt(\n'''
)
replace_once(
    'app/src/main/java/com/puttvision/screen/GreenPhysics.kt',
    '''        val localTerrain = GreenTerrain.slopeAt(\n            profileId = settings.terrainProfileId,\n            x = state.x,\n            y = state.y,\n            holeDistanceM = settings.holeDistanceM\n        )\n        val effectiveSideSlopePct = settings.sideSlopePct + localTerrain.sidePct\n        val effectiveLongSlopePct = settings.longSlopePct + localTerrain.longPct\n''',
    '''        val effectiveSlope = GreenTerrain.effectiveSlopeAt(settings, state.x, state.y)\n        val effectiveSideSlopePct = effectiveSlope.sidePct\n        val effectiveLongSlopePct = effectiveSlope.longPct\n'''
)


# ---------------------------------------------------------------------------
# Physics-backed green read solver with small LRU cache.
# ---------------------------------------------------------------------------
write('app/src/main/java/com/puttvision/screen/GreenReadAdvisor.kt', r'''package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.math.tan


data class GreenRead(
    val estimatedBreakCm: Double,
    val aimOffsetCm: Double,
    val cupCount: Double,
    val putterHeadCount: Double,
    val aimSideLabel: String,
    val effectiveSideSlopePct: Double,
    val effectiveLongSlopePct: Double,
    val paceHint: String,
    val recommendedBallSpeedMps: Double,
    val solverMissCm: Double
)

object GreenReadAdvisor {
    private const val CUP_DIAMETER_CM = 10.8
    private const val STIMP_LAUNCH_MPS = 1.95072
    private val physics = GreenPhysics()

    private data class Key(
        val profile: Int, val distance100: Int, val stimp100: Int,
        val side100: Int, val long100: Int, val putter100: Int
    )
    private data class Candidate(val angleDeg: Double, val speed: Double, val result: SimResult, val objective: Double)

    private val cache = object : LinkedHashMap<Key, GreenRead>(48, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, GreenRead>?): Boolean = size > 48
    }

    @Synchronized
    fun read(settings: GreenSettings): GreenRead {
        val putterWidth = ProductRuntime.putterHeadWidthCm.coerceIn(8.0, 15.0)
        val key = Key(
            settings.terrainProfileId,
            (settings.holeDistanceM * 100.0).toInt(),
            (settings.stimpMeters * 100.0).toInt(),
            (settings.sideSlopePct * 100.0).toInt(),
            (settings.longSlopePct * 100.0).toInt(),
            (putterWidth * 100.0).toInt()
        )
        cache[key]?.let { return it }
        val solved = solve(settings, putterWidth)
        cache[key] = solved
        return solved
    }

    private fun solve(settings: GreenSettings, putterWidth: Double): GreenRead {
        val d = settings.holeDistanceM.coerceIn(0.5, 20.0)
        val stimp = settings.stimpMeters.coerceIn(1.5, 5.0)
        val flatSpeed = (STIMP_LAUNCH_MPS * sqrt(d / stimp)).coerceIn(.20, 5.0)
        val minSpeed = (flatSpeed * .45).coerceIn(.15, 4.7)
        val maxSpeed = (flatSpeed * 1.55).coerceIn(minSpeed + .05, 5.0)

        var best: Candidate? = null
        val coarseSpeedStep = (maxSpeed - minSpeed) / 14.0
        for (angleStep in -10..10) {
            val angle = angleStep * 3.0
            for (speedStep in 0..14) {
                val speed = minSpeed + coarseSpeedStep * speedStep
                val c = candidate(settings, angle, speed, flatSpeed)
                if (best == null || c.objective < best!!.objective) best = c
            }
        }

        val coarse = best ?: candidate(settings, 0.0, flatSpeed, flatSpeed)
        best = coarse
        val refineAngleStep = .5
        val refineSpeedSpan = maxOf(.10, coarseSpeedStep * 1.25)
        for (ai in -6..6) {
            val angle = (coarse.angleDeg + ai * refineAngleStep).coerceIn(-35.0, 35.0)
            for (si in -6..6) {
                val speed = (coarse.speed + refineSpeedSpan * si / 6.0).coerceIn(.15, 5.0)
                val c = candidate(settings, angle, speed, flatSpeed)
                if (c.objective < best!!.objective) best = c
            }
        }

        val b = best!!
        val aimCm = tan(Math.toRadians(b.angleDeg)) * d * 100.0
        val magnitude = abs(aimCm)
        val straight = simulate(settings, b.speed, 0.0)
        val breakCm = straight.finishX * 100.0

        val corridor = (1..11).map { i ->
            val y = d * i / 12.0
            val center = GreenTerrain.effectiveSlopeAt(settings, 0.0, y)
            val left = GreenTerrain.effectiveSlopeAt(settings, -0.12, y)
            val right = GreenTerrain.effectiveSlopeAt(settings, 0.12, y)
            TerrainSlope(
                center.sidePct * .60 + left.sidePct * .20 + right.sidePct * .20,
                center.longPct * .60 + left.longPct * .20 + right.longPct * .20
            )
        }
        val effectiveSide = corridor.map { it.sidePct }.average()
        val effectiveLong = corridor.map { it.longPct }.average()
        val ratio = b.speed / flatSpeed.coerceAtLeast(.1)
        val pace = when {
            ratio <= .78 -> "강한 내리막 · 매우 약하게"
            ratio <= .91 -> "내리막 · 약하게"
            ratio >= 1.22 -> "강한 오르막 · 강하게"
            ratio >= 1.08 -> "오르막 · 조금 강하게"
            abs(effectiveSide) >= 3.0 -> "브레이크 큼 · 끝까지 읽기"
            abs(effectiveSide) >= 1.6 -> "브레이크 중간"
            else -> "기준 페이스"
        }
        val side = when {
            magnitude < 1.5 -> "센터"
            aimCm < 0.0 -> "홀 왼쪽"
            else -> "홀 오른쪽"
        }
        return GreenRead(
            estimatedBreakCm = breakCm,
            aimOffsetCm = aimCm,
            cupCount = magnitude / CUP_DIAMETER_CM,
            putterHeadCount = magnitude / putterWidth,
            aimSideLabel = side,
            effectiveSideSlopePct = effectiveSide,
            effectiveLongSlopePct = effectiveLong,
            paceHint = pace,
            recommendedBallSpeedMps = b.speed,
            solverMissCm = b.result.distanceToCupM * 100.0
        )
    }

    private fun candidate(settings: GreenSettings, angle: Double, speed: Double, flatSpeed: Double): Candidate {
        val result = simulate(settings, speed, angle)
        val miss = result.distanceToCupM
        val regularizer = abs(angle) * .00015 + abs(speed - flatSpeed) * .00025
        val objective = if (result.holed) -1.0 + regularizer else miss + regularizer
        return Candidate(angle, speed, result, objective)
    }

    private fun simulate(settings: GreenSettings, speed: Double, angle: Double): SimResult {
        val metrics = ShotMetrics(
            ballSpeedMps = speed,
            launchAngleDeg = angle,
            headSpeedMps = null,
            faceAngleDeg = null,
            pathAngleDeg = null,
            faceToPathDeg = null,
            smash = null,
            impactOffsetMm = null,
            measuredAtNs = 0L
        )
        val state = physics.launch(metrics, settings)
        repeat(900) {
            val result = physics.step(state, settings, .025)
            if (result != null) return result
        }
        return SimResult(
            holed = state.holed,
            finishX = state.x,
            finishY = state.y,
            distanceToCupM = hypot(state.x, state.y - settings.holeDistanceM),
            elapsedSec = state.elapsed
        )
    }
}
''')


# ---------------------------------------------------------------------------
# Practice green preview now visualizes the same effective slope field as physics.
# ---------------------------------------------------------------------------
write('app/src/main/java/com/puttvision/screen/PracticeGreenPreviewView.kt', r'''package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class PracticeGreenPreviewView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrow = Path()

    var styleIndex: Int = 0
        set(value) { field = value; invalidate() }
    var holeDistanceM: Double = 5.0
        set(value) { field = value.coerceIn(2.0, 15.0); invalidate() }
    var baseSideSlopePct: Double = 0.0
        set(value) { field = value; invalidate() }
    var baseLongSlopePct: Double = 0.0
        set(value) { field = value; invalidate() }

    init { isClickable = false; isFocusable = false }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        p.style = Paint.Style.FILL
        p.color = Color.rgb(8, 13, 15)
        canvas.drawRoundRect(RectF(0f, 0f, w, h), min(w, h) * .10f, min(w, h) * .10f, p)

        val pad = min(w, h) * .065f
        val r = RectF(pad, pad, w - pad, h - pad)
        val blob = buildBlob(styleIndex, r)
        canvas.save(); canvas.clipPath(blob)

        val settings = GreenSettings(
            stimpMeters = 2.8,
            holeDistanceM = holeDistanceM,
            sideSlopePct = baseSideSlopePct,
            longSlopePct = baseLongSlopePct,
            terrainProfileId = styleIndex
        )
        val cols = 9; val rows = 12
        val cellW = r.width() / cols; val cellH = r.height() / rows
        for (row in 0 until rows) {
            val yNorm = (rows - row - .5) / rows.toDouble()
            val realY = yNorm * holeDistanceM
            for (col in 0 until cols) {
                val xNorm = (col + .5) / cols.toDouble() * 2.0 - 1.0
                val realX = xNorm * max(.8, holeDistanceM * .22)
                val s = GreenTerrain.effectiveSlopeAt(settings, realX, realY)
                val mag = hypot(s.sidePct, s.longPct)
                p.color = slopeColor(s.sidePct, s.longPct, mag)
                val l = r.left + col * cellW
                val t = r.top + row * cellH
                canvas.drawRect(l, t, l + cellW + 1f, t + cellH + 1f, p)
            }
        }

        // Actual downhill vectors. +long is toward the hole (screen up), +side is right.
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = max(1.2f, min(w, h) * .009f)
        p.color = Color.argb(175, 245, 250, 248)
        for (row in 1 until rows step 2) {
            val yNorm = (rows - row - .5) / rows.toDouble()
            val realY = yNorm * holeDistanceM
            for (col in 1 until cols step 2) {
                val xNorm = (col + .5) / cols.toDouble() * 2.0 - 1.0
                val realX = xNorm * max(.8, holeDistanceM * .22)
                val s = GreenTerrain.effectiveSlopeAt(settings, realX, realY)
                val mag = hypot(s.sidePct, s.longPct)
                if (mag < .12) continue
                val cx = r.left + (col + .5f) * cellW
                val cy = r.top + (row + .5f) * cellH
                val len = (min(w, h) * (.035 + min(3.5, mag) * .009)).toFloat()
                val dx = (s.sidePct / mag * len).toFloat()
                val dy = (-s.longPct / mag * len).toFloat()
                arrow.reset(); arrow.moveTo(cx - dx * .45f, cy - dy * .45f); arrow.lineTo(cx + dx * .45f, cy + dy * .45f)
                canvas.drawPath(arrow, p)
                val ex = cx + dx * .45f; val ey = cy + dy * .45f
                arrow.reset(); arrow.moveTo(ex, ey); arrow.lineTo(ex - dx * .22f - dy * .16f, ey - dy * .22f + dx * .16f)
                arrow.moveTo(ex, ey); arrow.lineTo(ex - dx * .22f + dy * .16f, ey - dy * .22f - dx * .16f)
                canvas.drawPath(arrow, p)
            }
        }
        canvas.restore()

        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.4f, min(w, h) * .012f)
        p.color = Color.argb(140, 92, 120, 104)
        canvas.drawPath(blob, p)
        p.style = Paint.Style.FILL

        // Start / cup anchors use the same orientation as GreenPhysics.
        p.color = Color.WHITE
        canvas.drawCircle(r.centerX(), r.bottom - r.height() * .08f, max(2.5f, min(w, h) * .018f), p)
        p.color = Color.rgb(246, 190, 74)
        canvas.drawCircle(r.centerX(), r.top + r.height() * .08f, max(2.5f, min(w, h) * .018f), p)
    }

    private fun slopeColor(side: Double, long: Double, magnitude: Double): Int {
        val hot = (magnitude / 3.8).coerceIn(0.0, 1.0)
        val directional = (abs(side) / (abs(side) + abs(long) + .01)).coerceIn(0.0, 1.0)
        val r = (18 + hot * 210).toInt().coerceIn(0, 255)
        val g = (126 + (1.0 - hot) * 82).toInt().coerceIn(0, 255)
        val b = (52 + directional * 135 + (1.0 - hot) * 32).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun buildBlob(style: Int, r: RectF): Path = Path().apply {
        when (style % 6) {
            0 -> addOval(RectF(r.left + r.width() * .08f, r.top + r.height() * .05f, r.right - r.width() * .08f, r.bottom - r.height() * .05f), Path.Direction.CW)
            1 -> {
                moveTo(r.left + r.width() * .18f, r.top + r.height() * .08f)
                cubicTo(r.left, r.top + r.height() * .30f, r.left + r.width() * .08f, r.bottom - r.height() * .05f, r.left + r.width() * .34f, r.bottom - r.height() * .07f)
                cubicTo(r.right - r.width() * .05f, r.bottom - r.height() * .12f, r.right - r.width() * .06f, r.top + r.height() * .15f, r.left + r.width() * .18f, r.top + r.height() * .08f); close()
            }
            2 -> {
                moveTo(r.left + r.width() * .14f, r.top + r.height() * .14f)
                cubicTo(r.left, r.top + r.height() * .38f, r.left + r.width() * .06f, r.bottom - r.height() * .02f, r.left + r.width() * .36f, r.bottom - r.height() * .02f)
                cubicTo(r.right - r.width() * .06f, r.bottom, r.right, r.top + r.height() * .37f, r.right - r.width() * .10f, r.top + r.height() * .08f)
                cubicTo(r.right - r.width() * .35f, r.top, r.left + r.width() * .30f, r.top, r.left + r.width() * .14f, r.top + r.height() * .14f); close()
            }
            3 -> addOval(RectF(r.left + r.width() * .11f, r.top + r.height() * .03f, r.right - r.width() * .11f, r.bottom - r.height() * .03f), Path.Direction.CW)
            4 -> {
                moveTo(r.left + r.width() * .16f, r.top + r.height() * .08f)
                cubicTo(r.left, r.top + r.height() * .28f, r.left + r.width() * .04f, r.bottom - r.height() * .04f, r.left + r.width() * .30f, r.bottom - r.height() * .05f)
                cubicTo(r.right - r.width() * .06f, r.bottom - r.height() * .10f, r.right - r.width() * .04f, r.top + r.height() * .15f, r.left + r.width() * .46f, r.top + r.height() * .06f); close()
            }
            else -> {
                moveTo(r.left + r.width() * .20f, r.top + r.height() * .05f)
                cubicTo(r.left, r.top + r.height() * .28f, r.left + r.width() * .05f, r.bottom - r.height() * .05f, r.left + r.width() * .28f, r.bottom - r.height() * .02f)
                cubicTo(r.right - r.width() * .08f, r.bottom, r.right, r.top + r.height() * .34f, r.right - r.width() * .10f, r.top + r.height() * .07f)
                cubicTo(r.right - r.width() * .34f, r.top, r.left + r.width() * .34f, r.top, r.left + r.width() * .20f, r.top + r.height() * .05f); close()
            }
        }
    }
}
''')


# ---------------------------------------------------------------------------
# Thermal-aware HFR binding.
# ---------------------------------------------------------------------------
replace_once(
    'app/src/main/java/com/puttvision/screen/HighSpeedCaptureController.kt',
    '    fun bindBest(): ActiveHfrSession? {\n',
    '    fun bindBest(maxFps: Int = 240): ActiveHfrSession? {\n'
)
replace_once(
    'app/src/main/java/com/puttvision/screen/HighSpeedCaptureController.kt',
    '''        val ranges = info.getSupportedFrameRateRanges(builder.build())\n            .filter { it.upper >= 120 }\n\n        if (ranges.isEmpty()) {\n            status("HFR FPS range 없음")\n            return null\n        }\n''',
    '''        val cap = maxFps.coerceIn(0, 240)\n        if (cap < 120) {\n            status("THERMAL SAFE · NORMAL")\n            return null\n        }\n        val ranges = info.getSupportedFrameRateRanges(builder.build())\n            .filter { it.upper >= 120 && it.upper <= cap }\n\n        if (ranges.isEmpty()) {\n            status("${cap}fps 이하 HFR range 없음")\n            return null\n        }\n'''
)


# ---------------------------------------------------------------------------
# Accuracy Lab CSV import + button.
# ---------------------------------------------------------------------------
replace_once(
    'app/src/main/java/com/puttvision/screen/ProductizationV8.kt',
    '''    fun clear() {\n        samples.clear()\n        persist()\n    }\n\n    fun summaryText(): String {\n''',
    '''    fun clear() {\n        samples.clear()\n        persist()\n    }\n\n    fun importReferenceCsv(uri: Uri): AccuracyCsvImportResult {\n        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }\n            ?: error("CSV 파일을 열 수 없습니다")\n        val rows = AccuracyCsvParser.parse(text)\n        if (rows.isEmpty()) return AccuracyCsvImportResult(0, 0, 0, 0, 0)\n\n        val used = linkedSetOf<Int>()\n        var timeMatched = 0\n        var orderMatched = 0\n\n        fun apply(index: Int, row: AccuracyCsvReferenceRow) {\n            val old = samples[index]\n            samples[index] = old.copy(\n                refBall = row.ball ?: old.refBall,\n                refLaunch = row.launch ?: old.refLaunch,\n                refHead = row.head ?: old.refHead,\n                refFace = row.face ?: old.refFace,\n                refPath = row.path ?: old.refPath\n            )\n            used += index\n        }\n\n        // First choice: nearest timestamp within 10 seconds. This handles simultaneous\n        // exports from another launch monitor without relying on row order.\n        val deferred = ArrayList<AccuracyCsvReferenceRow>()\n        rows.forEach { row ->\n            val t = row.timestampMs\n            if (t == null) { deferred += row; return@forEach }\n            val best = samples.indices\n                .filter { it !in used }\n                .map { it to kotlin.math.abs(samples[it].timestampMs - t) }\n                .minByOrNull { it.second }\n            if (best != null && best.second <= 10_000L) {\n                apply(best.first, row); timeMatched++\n            } else deferred += row\n        }\n\n        // Fallback: align unmatched CSV rows to the most recent unmatched PuttVision shots.\n        // This is useful for sensors that export no timestamp column.\n        val remainingSamples = samples.indices.filter { it !in used }.takeLast(deferred.size)\n        val rowOffset = (deferred.size - remainingSamples.size).coerceAtLeast(0)\n        deferred.drop(rowOffset).zip(remainingSamples).forEach { (row, index) ->\n            apply(index, row); orderMatched++\n        }\n        if (used.isNotEmpty()) persist()\n        return AccuracyCsvImportResult(\n            rows = rows.size,\n            matched = used.size,\n            timestampMatched = timeMatched,\n            orderMatched = orderMatched,\n            skipped = (rows.size - used.size).coerceAtLeast(0)\n        )\n    }\n\n    fun summaryText(): String {\n'''
)
replace_once(
    'app/src/main/java/com/puttvision/screen/ProductizationV8.kt',
    'fun showAccuracyValidationLab(activity: Activity, lab: AccuracyValidationLab) {\n',
    'fun showAccuracyValidationLab(activity: Activity, lab: AccuracyValidationLab, onImportCsv: (() -> Unit)? = null) {\n'
)
# recursive reopen calls must preserve importer
text = read('app/src/main/java/com/puttvision/screen/ProductizationV8.kt')
text = text.replace('showAccuracyValidationLab(activity, lab)\n', 'showAccuracyValidationLab(activity, lab, onImportCsv)\n')
write('app/src/main/java/com/puttvision/screen/ProductizationV8.kt', text)
replace_once(
    'app/src/main/java/com/puttvision/screen/ProductizationV8.kt',
    '''    val secondary = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }\n    secondary.addView(activity.pvButton("CSV 내보내기", PvButtonStyle.GHOST) { lab.exportCsv(activity) }, LinearLayout.LayoutParams(0, activity.pvDp(38), 1f))\n    secondary.addView(activity.pvButton("검증 기록 초기화", PvButtonStyle.GHOST) { lab.clear(); refresh() }, LinearLayout.LayoutParams(0, activity.pvDp(38), 1f).apply { marginStart = activity.pvDp(6) })\n    root.addView(secondary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.pvDp(6) })\n''',
    '''    val secondary = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }\n    secondary.addView(activity.pvButton("CSV 내보내기", PvButtonStyle.GHOST) { lab.exportCsv(activity) }, LinearLayout.LayoutParams(0, activity.pvDp(38), 1f))\n    if (onImportCsv != null) {\n        secondary.addView(activity.pvButton("CSV 가져오기", PvButtonStyle.SECONDARY) { onImportCsv() }, LinearLayout.LayoutParams(0, activity.pvDp(38), 1f).apply { marginStart = activity.pvDp(6) })\n    }\n    secondary.addView(activity.pvButton("기록 초기화", PvButtonStyle.GHOST) { lab.clear(); refresh() }, LinearLayout.LayoutParams(0, activity.pvDp(38), 1f).apply { marginStart = activity.pvDp(6) })\n    root.addView(secondary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.pvDp(6) })\n'''
)


# ---------------------------------------------------------------------------
# Support ZIP: include previous crash stack trace only when user explicitly exports.
# ---------------------------------------------------------------------------
replace_once(
    'app/src/main/java/com/puttvision/screen/V9Productization.kt',
    '''            put("hfrStatus", hfrStatus)\n            put("shotCount", stats.all().size)\n        }\n''',
    '''            put("hfrStatus", hfrStatus)\n            put("lastCrashPresent", CrashJournal.lastCrash(activity) != null)\n            put("shotCount", stats.all().size)\n        }\n'''
)
replace_once(
    'app/src/main/java/com/puttvision/screen/V9Productization.kt',
    '''            textEntry("recent-shots.csv", csv)\n            textEntry("README.txt", "PuttVision support diagnostics. No camera images, GitHub token, license payload, or Android ID are included.\\n")\n''',
    '''            textEntry("recent-shots.csv", csv)\n            CrashJournal.lastCrash(activity)?.let { textEntry("last-crash.txt", it) }\n            textEntry("README.txt", "PuttVision support diagnostics. No camera images, GitHub token, license payload, or Android ID are included.\\n")\n'''
)


# ---------------------------------------------------------------------------
# TV green read exposes solved launch speed.
# ---------------------------------------------------------------------------
replace_once(
    'app/src/main/java/com/puttvision/screen/GreenView.kt',
    '''    c.drawText("$head  ·  ${read.paceHint}", left + pad, bottom - h * .014f, p)\n''',
    '''    c.drawText("$head  ·  ${read.paceHint}  ·  ${"%.2f".format(read.recommendedBallSpeedMps)}m/s", left + pad, bottom - h * .014f, p)\n'''
)


# ---------------------------------------------------------------------------
# MainActivity: thermal policy, session recovery, CSV picker, shared green preview.
# ---------------------------------------------------------------------------
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''    private lateinit var offlineLicenseManager: OfflineLicenseManager\n    private var liveQualitySnapshot: LiveQualityGateSnapshot? = null\n''',
    '''    private lateinit var offlineLicenseManager: OfflineLicenseManager\n    private lateinit var thermalHfrPolicy: ThermalHfrPolicy\n    private lateinit var sessionRecoveryStore: SessionRecoveryStore\n    private var liveQualitySnapshot: LiveQualityGateSnapshot? = null\n'''
)
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''    private val licenseImport =\n        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->\n''',
    '''    private val accuracyCsvImport =\n        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->\n            if (uri == null || !::accuracyValidationLab.isInitialized) return@registerForActivityResult\n            runCatching { accuracyValidationLab.importReferenceCsv(uri) }\n                .onSuccess { result ->\n                    accuracyAutoTuner.refresh(accuracyValidationLab.matched(), force = true)\n                    toast(result.label())\n                    showAccuracyValidationLab(this, accuracyValidationLab) { accuracyCsvImport.launch("text/*") }\n                }\n                .onFailure { error -> toast("CSV 가져오기 실패: ${error.message ?: "형식 오류"}") }\n        }\n\n    private val licenseImport =\n        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->\n'''
)
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''        super.onCreate(savedInstanceState)\n        applyImmersiveMode()\n''',
    '''        super.onCreate(savedInstanceState)\n        CrashJournal.install(this)\n        RuntimeJanitor.cleanup(this)\n        applyImmersiveMode()\n'''
)
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''        deviceReport = DeviceDiagnostics.inspect(this)\n        statsRepository = StatsRepository(this)\n''',
    '''        deviceReport = DeviceDiagnostics.inspect(this)\n        thermalHfrPolicy = ThermalHfrPolicy(this)\n        sessionRecoveryStore = SessionRecoveryStore(this)\n        statsRepository = StatsRepository(this)\n'''
)
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''        buildUi()\n\n        onBackPressedDispatcher.addCallback(\n''',
    '''        buildUi()\n        maybeOfferSessionRecovery()\n\n        onBackPressedDispatcher.addCallback(\n'''
)
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''    override fun onWindowFocusChanged(hasFocus: Boolean) {\n''',
    '''    override fun onPause() {\n        if (::sessionRecoveryStore.isInitialized && sessionActive) saveSessionRecovery()\n        super.onPause()\n    }\n\n    override fun onWindowFocusChanged(hasFocus: Boolean) {\n'''
)
# Preview instances: selected card / selected info / library cards.
text = read('app/src/main/java/com/puttvision/screen/MainActivity.kt')
text = text.replace(
    'PracticeGreenPreviewView(this@MainActivity).apply { styleIndex = selectedGreen.previewStyle }',
    'PracticeGreenPreviewView(this@MainActivity).apply { styleIndex = selectedGreen.previewStyle; holeDistanceM = practiceDistanceM.toDouble(); baseSideSlopePct = selectedGreen.sideSlopePct; baseLongSlopePct = selectedGreen.longSlopePct }'
)
text = text.replace(
    '''PracticeGreenPreviewView(this@MainActivity).apply {\n                styleIndex = selected.previewStyle\n            }''',
    '''PracticeGreenPreviewView(this@MainActivity).apply {\n                styleIndex = selected.previewStyle\n                holeDistanceM = practiceDistanceM.toDouble()\n                baseSideSlopePct = selected.sideSlopePct\n                baseLongSlopePct = selected.longSlopePct\n            }'''
)
text = text.replace(
    '''PracticeGreenPreviewView(this@MainActivity).apply {\n                        styleIndex = preset.previewStyle\n                    }''',
    '''PracticeGreenPreviewView(this@MainActivity).apply {\n                        styleIndex = preset.previewStyle\n                        holeDistanceM = practiceDistanceM.toDouble()\n                        baseSideSlopePct = preset.sideSlopePct\n                        baseLongSlopePct = preset.longSlopePct\n                    }'''
)
write('app/src/main/java/com/puttvision/screen/MainActivity.kt', text)
# Accuracy settings tool callback.
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''        showAccuracyValidationLab(this, accuracyValidationLab)\n''',
    '''        showAccuracyValidationLab(this, accuracyValidationLab) { accuracyCsvImport.launch("text/*") }\n'''
)
# Session start checkpoint.
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''        menuOverlay.visibility = View.GONE\n\n        // Measurement/recording starts only after explicit entry. If camera permission/provider\n''',
    '''        menuOverlay.visibility = View.GONE\n        saveSessionRecovery()\n\n        // Measurement/recording starts only after explicit entry. If camera permission/provider\n'''
)
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''    private fun pauseSessionForMenu() {\n        sessionActive = false\n        suspendMeasurementForOverlay()\n    }\n''',
    '''    private fun pauseSessionForMenu() {\n        if (::sessionRecoveryStore.isInitialized) sessionRecoveryStore.clear()\n        sessionActive = false\n        suspendMeasurementForOverlay()\n    }\n'''
)
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''        stopHfrRecordingOnly()\n        practiceEntranceMode = plan.entranceMode\n''',
    '''        stopHfrRecordingOnly()\n        if (::sessionRecoveryStore.isInitialized) sessionRecoveryStore.clear()\n        practiceEntranceMode = plan.entranceMode\n'''
)
# Checkpoint after every finalized simulation result.
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''                        onSessionShotFinished()\n                        showFinalShotSummary(result)\n''',
    '''                        onSessionShotFinished()\n                        checkpointSessionRecovery()\n                        showFinalShotSummary(result)\n'''
)
# Thermal HFR decision and cap-aware reuse/bind.
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''        overlay.status =\n            "240/120fps PRECISION 준비중"\n\n        metricText.text =\n            "240fps 우선 → 120fps fallback · 자동 녹화 준비"\n''',
    '''        val thermal = thermalHfrPolicy.current()\n        if (thermal.maxFps < 120) {\n            hfrController?.close()\n            hfrController = null\n            tracker.arm()\n            setHfrStatus("THERMAL SAFE", thermal.detail)\n            overlay.status = "THERMAL SAFE · NORMAL AUTO"\n            metricText.text = thermal.detail\n            overlay.invalidate()\n            return\n        }\n\n        overlay.status =\n            "${thermal.maxFps}fps PRECISION 준비중"\n\n        metricText.text =\n            "${thermal.label} · ${thermal.maxFps}fps 상한 · 자동 녹화 준비"\n'''
)
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''            existing != null &&\n            existing.fps() >= 120\n''',
    '''            existing != null &&\n            existing.fps() >= 120 &&\n            existing.fps() <= thermal.maxFps\n'''
)
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''            controller.bindBest()\n''',
    '''            controller.bindBest(maxFps = thermal.maxFps)\n'''
)
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''        if (session == null) {\n            hfrHardwareAvailable = false\n\n            setHfrStatus("HFR 오류", "HFR 바인딩 실패 · NORMAL fallback")\n\n            beginAutoCalibration()\n\n            return\n        }\n''',
    '''        if (session == null) {\n            setHfrStatus("HFR fallback", "${thermal.label} · 현재 조건에서 HFR 바인딩 실패 · NORMAL fallback")\n            beginAutoCalibration()\n            return\n        }\n'''
)
# Session recovery helper methods inserted before currentSessionRecords.
replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''    private fun currentSessionRecords(): List<ShotRecord> {\n''',
    r'''    private fun saveSessionRecovery() {
        if (!::sessionRecoveryStore.isInitialized || !sessionActive) return
        sessionRecoveryStore.save(
            SessionRecoverySnapshot(
                savedAtMs = System.currentTimeMillis(),
                sessionStartedAtMs = sessionStartedAtMs,
                activeSessionIsGame = activeSessionIsGame,
                practiceEntranceMode = practiceEntranceMode,
                practiceCount = practiceCount,
                practiceDistanceM = practiceDistanceM,
                practiceGreenSpeed = practiceGreenSpeed,
                practicePatternIndex = practicePatternIndex,
                practiceGreenPresetIndex = practiceGreenPresetIndex,
                practiceShotsTaken = practiceShotsTaken,
                practicePatternShotIndex = practicePatternShotIndex,
                gamePlayers = gamePlayers,
                gameModeIndex = gameModeIndex,
                gameDistanceM = gameDistanceM,
                holeDistanceM = engine.settings.holeDistanceM,
                stimpMeters = engine.settings.stimpMeters,
                sideSlopePct = engine.settings.sideSlopePct,
                longSlopePct = engine.settings.longSlopePct,
                terrainProfileId = engine.settings.terrainProfileId,
                gameMode = engine.gameModes.snapshot()
            )
        )
    }

    private fun checkpointSessionRecovery() {
        if (!::sessionRecoveryStore.isInitialized) return
        val completed = if (activeSessionIsGame) engine.gameModes.status.completed else practiceShotsTaken >= practiceCount
        if (completed) sessionRecoveryStore.clear() else saveSessionRecovery()
    }

    private fun maybeOfferSessionRecovery() {
        if (!::sessionRecoveryStore.isInitialized) return
        val recovered = sessionRecoveryStore.load() ?: return
        firstRunWizardShown = true
        val kind = if (recovered.activeSessionIsGame) "게임" else "연습"
        val progress = if (recovered.activeSessionIsGame) {
            val g = recovered.gameMode
            if (g.totalHoles > 0) "${g.hole}/${g.totalHoles}홀 · ${g.shots}샷" else "${g.shots}샷"
        } else {
            "${recovered.practiceShotsTaken}/${recovered.practiceCount}구"
        }
        AlertDialog.Builder(this)
            .setTitle("이전 세션 이어하기")
            .setMessage("강제 종료 전에 저장된 $kind 세션이 있습니다.\n$progress · ${"%.1f".format(recovered.holeDistanceM)}m")
            .setNegativeButton("새로 시작") { _, _ -> sessionRecoveryStore.clear() }
            .setPositiveButton("이어하기") { _, _ -> resumeRecoveredSession(recovered) }
            .show()
    }

    private fun resumeRecoveredSession(s: SessionRecoverySnapshot) {
        activeSessionIsGame = s.activeSessionIsGame
        sessionActive = true
        measurementSuspended = false
        sessionStartedAtMs = s.sessionStartedAtMs
        practiceEntranceMode = s.practiceEntranceMode.coerceIn(0, 2)
        practiceCount = s.practiceCount.coerceIn(5, 20)
        practiceDistanceM = s.practiceDistanceM.coerceIn(2, 15)
        practiceGreenSpeed = s.practiceGreenSpeed.coerceIn(2.4, 3.6)
        practicePatternIndex = s.practicePatternIndex.coerceIn(0, 3)
        practiceGreenPresetIndex = s.practiceGreenPresetIndex.coerceIn(0, practiceGreenPresets.lastIndex)
        practiceShotsTaken = s.practiceShotsTaken.coerceIn(0, practiceCount)
        practicePatternShotIndex = s.practicePatternShotIndex.coerceAtLeast(0)
        gamePlayers = s.gamePlayers.coerceIn(1, 4)
        gameModeIndex = s.gameModeIndex.coerceIn(0, 3)
        gameDistanceM = s.gameDistanceM.coerceIn(1, 15)
        engine.settings.holeDistanceM = s.holeDistanceM
        engine.settings.stimpMeters = s.stimpMeters
        engine.settings.sideSlopePct = s.sideSlopePct
        engine.settings.longSlopePct = s.longSlopePct
        engine.settings.terrainProfileId = s.terrainProfileId
        engine.gameModes.restore(s.gameMode)
        modeButton.text = "메뉴"
        metricText.text = "이전 세션 복구 · ${engine.gameModes.status.mode.label}"
        updateSettingLabels()
        menuBackAction = null
        menuOverlay.isClickable = false
        menuOverlay.visibility = View.GONE
        saveSessionRecovery()

        if (!::provider.isInitialized) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (granted) openProvider() else permission.launch(Manifest.permission.CAMERA)
        } else if (homography != null) {
            mainHandler.post { armPrecision() }
        } else {
            beginAutoCalibration()
        }
    }

    private fun currentSessionRecords(): List<ShotRecord> {
'''
)


# ---------------------------------------------------------------------------
# Regression tests.
# ---------------------------------------------------------------------------
write('app/src/test/java/com/puttvision/screen/GreenReadSolverRegressionTest.kt', r'''package com.puttvision.screen

import org.junit.Assert.assertTrue
import org.junit.Test

class GreenReadSolverRegressionTest {
    @Test fun flatGreenAimsNearCenter() {
        val read = GreenReadAdvisor.read(GreenSettings(stimpMeters = 2.8, holeDistanceM = 5.0))
        assertTrue(kotlin.math.abs(read.aimOffsetCm) < 12.0)
        assertTrue(read.recommendedBallSpeedMps in 0.3..5.0)
    }

    @Test fun rightBreakRequiresLeftAim() {
        val read = GreenReadAdvisor.read(GreenSettings(stimpMeters = 2.8, holeDistanceM = 5.0, sideSlopePct = 1.8))
        assertTrue(read.aimOffsetCm < -1.0)
    }

    @Test fun leftBreakRequiresRightAim() {
        val read = GreenReadAdvisor.read(GreenSettings(stimpMeters = 2.8, holeDistanceM = 5.0, sideSlopePct = -1.8))
        assertTrue(read.aimOffsetCm > 1.0)
    }
}
''')
write('app/src/test/java/com/puttvision/screen/ThermalPolicyRegressionTest.kt', r'''package com.puttvision.screen

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ThermalPolicyRegressionTest {
    @Test fun coolPhoneAllows240() {
        assertEquals(240, ThermalHfrPolicy.decide(PowerManager.THERMAL_STATUS_NONE, 34.0).maxFps)
    }

    @Test fun warmPhoneCaps120() {
        assertEquals(120, ThermalHfrPolicy.decide(PowerManager.THERMAL_STATUS_MODERATE, 39.0).maxFps)
        assertEquals(120, ThermalHfrPolicy.decide(PowerManager.THERMAL_STATUS_NONE, 41.0).maxFps)
    }

    @Test fun criticalPhoneFallsBackNormal() {
        assertEquals(0, ThermalHfrPolicy.decide(PowerManager.THERMAL_STATUS_CRITICAL, 44.0).maxFps)
        assertEquals(0, ThermalHfrPolicy.decide(PowerManager.THERMAL_STATUS_NONE, 46.5).maxFps)
    }
}
''')
write('app/src/test/java/com/puttvision/screen/GameModeRecoveryRegressionTest.kt', r'''package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class GameModeRecoveryRegressionTest {
    @Test fun snapshotRestoresMultiplayerState() {
        val settings = GreenSettings()
        val engine = GameModeEngine(settings)
        engine.configurePlayers(3)
        engine.setMode(PracticeMode.NINE_HOLE)
        val snap = engine.snapshot()
        val restored = GameModeEngine(GreenSettings())
        restored.restore(snap)
        assertEquals(snap.mode, restored.status.mode)
        assertEquals(3, restored.status.playerCount)
        assertEquals(snap.hole, restored.status.hole)
        assertEquals(snap.playerScores, restored.status.playerScores)
    }
}
''')

print('V10 patch applied')
