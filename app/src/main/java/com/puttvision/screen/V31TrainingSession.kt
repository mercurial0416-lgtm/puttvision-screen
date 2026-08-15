package com.puttvision.screen

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.max

object V31TrainingRules {
    fun launchOk(angleDeg: Double) = kotlin.math.abs(angleDeg) <= .70
    fun weaknessOk(score: Int, holed: Boolean, remainingM: Double) = score >= 80 && (holed || remainingM <= .50)
    fun distanceOk(remainingM: Double) = remainingM <= .35
    fun pressureOk(holed: Boolean) = holed
}

data class V31TrainingProgress(
    val running: Boolean,
    val finished: Boolean,
    val blockIndex: Int,
    val blockCount: Int,
    val shotInBlock: Int,
    val shotsInBlock: Int,
    val successesInBlock: Int,
    val totalShots: Int,
    val totalSuccesses: Int,
    val streak: Int,
    val blockTitle: String,
    val targetDistanceM: Double,
    val summary: String,
    val paused: Boolean = false,
    val completionPct: Int = 0,
    val blockSuccessPct: Int = 0,
    val estimatedRemainingMinutes: Int = 0,
    val lastCompletedSummary: String? = null
)

data class V49TrainingCompletion(
    val title: String,
    val totalShots: Int,
    val totalSuccesses: Int,
    val successPct: Int,
    val weakestBlockTitle: String?,
    val completedAtMs: Long
) {
    val label: String
        get() = "$title · 성공 $totalSuccesses/$totalShots ($successPct%)" +
            (weakestBlockTitle?.let { " · 재훈련 $it" } ?: "")
}

object V31TrainingSessionRuntime {
    private data class OriginalSettings(val distance: Double, val side: Double, val long: Double, val terrain: Int)
    private data class BlockResult(val block: V16TrainingBlock, val attempts: Int, val successes: Int)

    @Volatile private var engine: GameEngine? = null
    @Volatile private var context: Context? = null
    @Volatile private var plan: V16DailyTrainingPlan? = null
    @Volatile private var blockIndex = 0
    @Volatile private var shotInBlock = 0
    @Volatile private var successesInBlock = 0
    @Volatile private var totalShots = 0
    @Volatile private var totalSuccesses = 0
    @Volatile private var streak = 0
    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var finished = false
    @Volatile private var startedAtMs = 0L
    @Volatile private var pausedAtMs = 0L
    @Volatile private var pausedAccumulatedMs = 0L
    @Volatile private var lastCompletion: V49TrainingCompletion? = null
    private var original: OriginalSettings? = null
    private var restored = false
    private val blockResults = ArrayList<BlockResult>()
    private var lastWeakestBlock: V16TrainingBlock? = null

    @Synchronized
    fun bind(value: GameEngine) { engine = value; restore() }

    @Synchronized
    fun install(value: Context) { context = value.applicationContext; loadLastCompletion(); restore() }

    @Synchronized
    fun start(value: V16DailyTrainingPlan): Boolean {
        val e = engine ?: return false
        if (value.blocks.isEmpty()) return false
        if (running) stop(true)
        plan = value
        original = OriginalSettings(e.settings.holeDistanceM, e.settings.sideSlopePct, e.settings.longSlopePct, e.settings.terrainProfileId)
        blockIndex = 0
        shotInBlock = 0
        successesInBlock = 0
        totalShots = 0
        totalSuccesses = 0
        streak = 0
        running = true
        paused = false
        finished = false
        startedAtMs = System.currentTimeMillis()
        pausedAtMs = 0L
        pausedAccumulatedMs = 0L
        blockResults.clear()
        applyCurrentTarget()
        save()
        return true
    }

    @Synchronized
    fun pause(): Boolean {
        if (!running || paused) return false
        paused = true
        pausedAtMs = System.currentTimeMillis()
        save()
        return true
    }

    @Synchronized
    fun resume(): Boolean {
        if (!running || !paused) return false
        val now = System.currentTimeMillis()
        if (pausedAtMs > 0L) pausedAccumulatedMs += (now - pausedAtMs).coerceAtLeast(0L)
        pausedAtMs = 0L
        paused = false
        save()
        return true
    }

    @Synchronized
    fun skipCurrentBlock(): Boolean {
        if (!running) return false
        val p = plan ?: return false
        val block = p.blocks.getOrNull(blockIndex) ?: return false
        blockResults += BlockResult(block, shotInBlock, successesInBlock)
        return advanceBlock(p)
    }

    @Synchronized
    fun restartCurrentBlock(): Boolean {
        if (!running) return false
        val p = plan ?: return false
        if (p.blocks.getOrNull(blockIndex) == null) return false
        shotInBlock = 0
        successesInBlock = 0
        streak = 0
        applyCurrentTarget()
        save()
        return true
    }

    @Synchronized
    fun retryWeakestBlock(): Boolean {
        val block = lastWeakestBlock ?: return false
        val retryPlan = V16DailyTrainingPlan(
            title = "약점 재훈련 · ${block.title}",
            estimatedMinutes = max(3, ceil(block.shots * 0.35).toInt()),
            blocks = listOf(block),
            reason = "직전 세션에서 성공률이 가장 낮았던 블록 재훈련"
        )
        return start(retryPlan)
    }

    @Synchronized
    fun stop(restore: Boolean = true) {
        running = false
        paused = false
        finished = false
        if (restore) restoreSettings()
        clearCurrent()
    }

    @Synchronized
    fun onRecord(record: ShotRecord) {
        if (!running || paused) return
        val p = plan ?: return
        val block = p.blocks.getOrNull(blockIndex) ?: return
        val success = evaluate(blockIndex, record)
        shotInBlock++
        totalShots++
        if (success) {
            successesInBlock++
            totalSuccesses++
            streak++
        } else streak = 0
        val earlyPressurePass = blockIndex == p.blocks.lastIndex && streak >= 3
        if (shotInBlock >= block.shots || earlyPressurePass) {
            blockResults += BlockResult(block, shotInBlock, successesInBlock)
            if (!advanceBlock(p)) return
        }
        applyCurrentTarget()
        save()
    }

    fun progress(): V31TrainingProgress {
        val p = plan
        val maxIndex = max(0, (p?.blocks?.size ?: 1) - 1)
        val block = p?.blocks?.getOrNull(blockIndex.coerceAtMost(maxIndex))
        val target = engine?.settings?.holeDistanceM ?: block?.distanceM ?: 0.0
        val scheduled = p?.blocks?.sumOf { it.shots } ?: 0
        val beforeBlock = p?.blocks?.take(blockIndex.coerceAtLeast(0))?.sumOf { it.shots } ?: 0
        val completedEquivalent = (beforeBlock + shotInBlock).coerceIn(0, scheduled.coerceAtLeast(0))
        val completion = if (scheduled <= 0) 0 else (completedEquivalent * 100.0 / scheduled).toInt().coerceIn(0, 100)
        val blockRate = if (shotInBlock <= 0) 0 else (successesInBlock * 100.0 / shotInBlock).toInt().coerceIn(0, 100)
        val effectiveElapsed = effectiveElapsedMs()
        val perShotMs = if (totalShots >= 2 && effectiveElapsed > 0L) (effectiveElapsed.toDouble() / totalShots).coerceIn(8_000.0, 90_000.0) else 24_000.0
        val remainingShots = (scheduled - completedEquivalent).coerceAtLeast(0)
        val eta = if (!running || paused || remainingShots == 0) 0 else ceil(remainingShots * perShotMs / 60_000.0).toInt().coerceAtLeast(1)
        val summary = when {
            finished -> "완료 · 성공 $totalSuccesses/$totalShots"
            paused && block != null -> "일시정지 · ${blockIndex + 1}/${p!!.blocks.size}"
            running && block != null -> "${blockIndex + 1}/${p!!.blocks.size} · ${shotInBlock}/${block.shots}구"
            else -> lastCompletion?.label ?: "대기"
        }
        return V31TrainingProgress(running, finished, blockIndex, p?.blocks?.size ?: 0, shotInBlock, block?.shots ?: 0, successesInBlock, totalShots, totalSuccesses, streak, block?.title ?: "--", target, summary, paused, if (finished) 100 else completion, blockRate, eta, lastCompletion?.label)
    }

    fun lastCompleted(): V49TrainingCompletion? = lastCompletion

    private fun evaluate(index: Int, record: ShotRecord): Boolean = when (index) {
        0 -> V31TrainingRules.launchOk(record.metrics.launchAngleDeg)
        1 -> V31TrainingRules.weaknessOk(record.strokeScore.total, record.result?.holed == true, record.result?.distanceToCupM ?: 9.0)
        2 -> V31TrainingRules.distanceOk(record.result?.distanceToCupM ?: 9.0)
        else -> V31TrainingRules.pressureOk(record.result?.holed == true)
    }

    private fun advanceBlock(p: V16DailyTrainingPlan): Boolean {
        blockIndex++
        shotInBlock = 0
        successesInBlock = 0
        streak = 0
        if (blockIndex >= p.blocks.size) {
            finishSession(p)
            return false
        }
        applyCurrentTarget()
        save()
        return true
    }

    private fun finishSession(p: V16DailyTrainingPlan) {
        running = false
        paused = false
        finished = true
        val weakest = blockResults.filter { it.attempts > 0 }.minWithOrNull(compareBy<BlockResult> { it.successes.toDouble() / it.attempts }.thenByDescending { it.attempts })
        lastWeakestBlock = weakest?.block
        val pct = if (totalShots <= 0) 0 else (totalSuccesses * 100.0 / totalShots).toInt().coerceIn(0, 100)
        lastCompletion = V49TrainingCompletion(p.title, totalShots, totalSuccesses, pct, weakest?.block?.title, System.currentTimeMillis())
        saveLastCompletion(weakest?.block)
        restoreSettings()
        clearCurrent()
    }

    private fun applyCurrentTarget() {
        val e = engine ?: return
        val p = plan ?: return
        val b = p.blocks.getOrNull(blockIndex) ?: return
        e.settings.holeDistanceM = if (b.title.contains("랜덤")) randomDistance(b.distanceM, shotInBlock) else b.distanceM
        e.settings.sideSlopePct = b.sideSlopePct
        e.settings.longSlopePct = b.longSlopePct
        e.settings.terrainProfileId = -1
        GreenReadRuntime.clearRuntimeCache()
    }

    private fun randomDistance(base: Double, shot: Int): Double {
        val f = doubleArrayOf(.70, .90, 1.10, .80, 1.00, 1.20)
        return (base * f[shot % f.size]).coerceIn(1.5, 8.0)
    }

    private fun restoreSettings() {
        val e = engine ?: return
        val s = original ?: return
        e.settings.holeDistanceM = s.distance
        e.settings.sideSlopePct = s.side
        e.settings.longSlopePct = s.long
        e.settings.terrainProfileId = s.terrain
        GreenReadRuntime.clearRuntimeCache()
        original = null
    }

    private fun effectiveElapsedMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (startedAtMs <= 0L) return 0L
        val currentPause = if (paused && pausedAtMs > 0L) (nowMs - pausedAtMs).coerceAtLeast(0L) else 0L
        return (nowMs - startedAtMs - pausedAccumulatedMs - currentPause).coerceAtLeast(0L)
    }

    private fun prefs() = context?.getSharedPreferences("v31_training_session", Context.MODE_PRIVATE)

    private fun save() {
        val p = plan ?: return
        val s = original ?: return
        val blocks = JSONArray()
        p.blocks.forEach { b -> blocks.put(JSONObject().put("t", b.title).put("n", b.shots).put("d", b.distanceM).put("s", b.sideSlopePct).put("l", b.longSlopePct).put("r", b.successRule)) }
        val results = JSONArray()
        blockResults.forEachIndexed { index, r -> results.put(JSONObject().put("i", index).put("a", r.attempts).put("s", r.successes)) }
        val j = JSONObject().put("ts", System.currentTimeMillis()).put("bi", blockIndex).put("si", shotInBlock).put("sb", successesInBlock).put("tsn", totalShots).put("tss", totalSuccesses).put("st", streak).put("paused", paused).put("started", startedAtMs).put("pat", pausedAtMs).put("pam", pausedAccumulatedMs).put("p", JSONObject().put("t", p.title).put("m", p.estimatedMinutes).put("r", p.reason).put("b", blocks)).put("o", JSONObject().put("d", s.distance).put("s", s.side).put("l", s.long).put("t", s.terrain)).put("br", results)
        prefs()?.edit()?.putString("state", j.toString())?.apply()
    }

    private fun clearCurrent() { prefs()?.edit()?.remove("state")?.apply() }

    private fun saveLastCompletion(weakest: V16TrainingBlock?) {
        val c = lastCompletion ?: return
        val j = JSONObject().put("title", c.title).put("shots", c.totalShots).put("success", c.totalSuccesses).put("pct", c.successPct).put("weak", c.weakestBlockTitle ?: "").put("at", c.completedAtMs)
        weakest?.let { b -> j.put("wb", JSONObject().put("t", b.title).put("n", b.shots).put("d", b.distanceM).put("s", b.sideSlopePct).put("l", b.longSlopePct).put("r", b.successRule)) }
        prefs()?.edit()?.putString("last_complete", j.toString())?.apply()
    }

    private fun loadLastCompletion() {
        val raw = prefs()?.getString("last_complete", null) ?: return
        runCatching {
            val j = JSONObject(raw)
            lastCompletion = V49TrainingCompletion(j.getString("title"), j.getInt("shots"), j.getInt("success"), j.getInt("pct"), j.optString("weak").takeIf { it.isNotBlank() }, j.getLong("at"))
            if (j.has("wb")) {
                val b = j.getJSONObject("wb")
                lastWeakestBlock = V16TrainingBlock(b.getString("t"), b.getInt("n"), b.getDouble("d"), b.getDouble("s"), b.getDouble("l"), b.getString("r"))
            }
        }
    }

    private fun restore() {
        if (restored || engine == null || context == null) return
        restored = true
        val raw = prefs()?.getString("state", null) ?: return
        val parsed = runCatching {
            val now = System.currentTimeMillis()
            val j = JSONObject(raw)
            val savedAt = j.getLong("ts")
            val pj = j.getJSONObject("p")
            val a = pj.getJSONArray("b")
            val blocks = (0 until a.length()).map { i -> a.getJSONObject(i).let { b -> V16TrainingBlock(b.getString("t"), b.getInt("n"), b.getDouble("d"), b.getDouble("s"), b.getDouble("l"), b.getString("r")) } }
            require(blocks.isNotEmpty() && blocks.all { it.shots in 1..100 && it.distanceM.isFinite() && it.distanceM in 1.0..20.0 && it.sideSlopePct.isFinite() && it.longSlopePct.isFinite() })
            val restoredPlan = V16DailyTrainingPlan(pj.getString("t"), pj.getInt("m"), blocks, pj.getString("r"))
            val o = j.getJSONObject("o")
            val restoredOriginal = OriginalSettings(o.getDouble("d"), o.getDouble("s"), o.getDouble("l"), o.getInt("t"))
            require(restoredOriginal.distance.isFinite() && restoredOriginal.side.isFinite() && restoredOriginal.long.isFinite())
            val bi = j.getInt("bi")
            require(bi in blocks.indices)
            val si = j.getInt("si")
            val sb = j.getInt("sb")
            val tsn = j.getInt("tsn")
            val tss = j.getInt("tss")
            val st = j.getInt("st")
            val isPaused = j.optBoolean("paused", false)
            val started = j.optLong("started", now)
            val pat = j.optLong("pat", 0L)
            val pam = j.optLong("pam", 0L)
            val restoredResults = ArrayList<BlockResult>()
            val persistedResults = j.optJSONArray("br") ?: JSONArray()
            for (i in 0 until persistedResults.length()) {
                val r = persistedResults.getJSONObject(i)
                val resultIndex = r.getInt("i")
                require(resultIndex in blocks.indices)
                require(resultIndex == i)
                restoredResults += BlockResult(blocks[resultIndex], r.getInt("a"), r.getInt("s"))
            }
            val integrity = V73TrainingResumeIntegrity.evaluate(V73TrainingResumeState(blocks.map { it.shots }, bi, si, sb, tsn, tss, st, isPaused, savedAt, started, pat, pam, restoredResults.mapIndexed { index, result -> V73TrainingBlockResultState(index, result.attempts, result.successes) }), now)
            require(integrity.valid) { integrity.reason }
            arrayOf(restoredPlan, restoredOriginal, bi, si, sb, tsn, tss, st, isPaused, started, pat, pam, restoredResults)
        }.getOrNull()
        if (parsed == null) {
            blockResults.clear()
            clearCurrent()
            return
        }
        @Suppress("UNCHECKED_CAST")
        plan = parsed[0] as V16DailyTrainingPlan
        original = parsed[1] as OriginalSettings
        blockIndex = parsed[2] as Int
        shotInBlock = parsed[3] as Int
        successesInBlock = parsed[4] as Int
        totalShots = parsed[5] as Int
        totalSuccesses = parsed[6] as Int
        streak = parsed[7] as Int
        paused = parsed[8] as Boolean
        startedAtMs = parsed[9] as Long
        pausedAtMs = parsed[10] as Long
        pausedAccumulatedMs = parsed[11] as Long
        blockResults.clear()
        blockResults.addAll(parsed[12] as List<BlockResult>)
        running = true
        finished = false
        applyCurrentTarget()
        save()
    }
}
