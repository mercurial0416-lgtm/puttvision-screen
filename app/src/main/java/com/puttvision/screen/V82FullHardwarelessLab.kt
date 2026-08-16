package com.puttvision.screen

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** End-to-end synthetic training journey. It validates state-transition semantics without Android UI. */
data class V82TrainingJourneyResult(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val weakestBlockIndex: Int?,
    val reason: String
)

object V82HardwarelessTrainingJourney {
    private data class Block(val shots: Int)
    private data class Result(val attempts: Int, val successes: Int)

    fun verify(): V82TrainingJourneyResult {
        val blocks = listOf(Block(4), Block(5), Block(4), Block(4))
        val completed = ArrayList<Result>()
        var blockIndex = 0
        var shotInBlock = 0
        var successInBlock = 0
        var totalShots = 0
        var totalSuccesses = 0
        var paused = false

        fun shoot(success: Boolean) {
            check(!paused)
            val block = blocks[blockIndex]
            shotInBlock++
            totalShots++
            if (success) { successInBlock++; totalSuccesses++ }
            if (shotInBlock >= block.shots) {
                completed += Result(shotInBlock, successInBlock)
                blockIndex++
                shotInBlock = 0
                successInBlock = 0
            }
        }

        // block 0: 3/4
        listOf(true, true, false, true).forEach(::shoot)
        // block 1: pause after 2 shots, serialize a V73-compatible checkpoint, resume, finish 2/5
        listOf(true, false).forEach(::shoot)
        paused = true
        val checkpoint = V73TrainingResumeState(
            blockShots = blocks.map { it.shots },
            blockIndex = blockIndex,
            shotInBlock = shotInBlock,
            successesInBlock = successInBlock,
            totalShots = totalShots,
            totalSuccesses = totalSuccesses,
            streak = 0,
            paused = true,
            savedAtMs = 9_999_000L,
            startedAtMs = 9_500_000L,
            pausedAtMs = 9_998_000L,
            pausedAccumulatedMs = 20_000L,
            completedBlocks = completed.mapIndexed { i, r -> V73TrainingBlockResultState(i, r.attempts, r.successes) }
        )
        val checkpointValid = V73TrainingResumeIntegrity.evaluate(checkpoint, 10_000_000L).valid
        paused = false
        listOf(false, true, false).forEach(::shoot)
        // block 2: 4/4, block 3: 3/4
        repeat(4) { shoot(true) }
        listOf(true, true, true, false).forEach(::shoot)

        val weakest = completed.withIndex().minWithOrNull(
            compareBy<IndexedValue<Result>> { it.value.successes.toDouble() / it.value.attempts }
                .thenByDescending { it.value.attempts }
        )?.index

        val checks = listOf(
            "checkpoint accepted by production resume integrity" to checkpointValid,
            "pause freezes active block counters" to (checkpoint.blockIndex == 1 && checkpoint.shotInBlock == 2 && checkpoint.successesInBlock == 1),
            "completed block history survives checkpoint" to (checkpoint.completedBlocks.size == 1 && checkpoint.completedBlocks[0].successes == 3),
            "resume continues same block" to (completed.size >= 2 && completed[1].attempts == 5),
            "all scheduled blocks complete" to (blockIndex == blocks.size && completed.size == blocks.size),
            "whole-session shot total preserved" to (totalShots == blocks.sumOf { it.shots }),
            "whole-session success total preserved" to (totalSuccesses == completed.sumOf { it.successes }),
            "weakest block selection spans pre/post resume" to (weakest == 1),
            "finished state has no active-shot residue" to (shotInBlock == 0 && successInBlock == 0 && !paused)
        )
        val passed = checks.count { it.second }
        return V82TrainingJourneyResult(
            passed = passed == checks.size,
            checksPassed = passed,
            checksTotal = checks.size,
            weakestBlockIndex = weakest,
            reason = checks.firstOrNull { !it.second }?.first ?: "full pause/resume/block-transition/completion journey verified"
        )
    }
}

/** Portable diagnostics payload for later comparison with real-device runs. */
object V82HardwarelessDiagnosticsExport {
    const val SCHEMA = 1

    fun toJson(
        verdict: V81LabVerdict,
        soak: V76HardwarelessSoakReport? = null,
        overlay: V81LiveTrackOverlay? = null,
        generatedAtMs: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject()
            .put("schema", SCHEMA)
            .put("generatedAtMs", generatedAtMs.coerceAtLeast(0L))
            .put("scope", "synthetic-regression-only")
            .put("realDeviceAccuracyClaim", false)
            .put("lab", JSONObject()
                .put("passed", verdict.passed)
                .put("selfTestPassed", verdict.selfTestPassed)
                .put("selfTestTotal", verdict.selfTestTotal)
                .put("historySamples", verdict.historySamples)
                .put("historyFailures", verdict.historyFailures)
                .put("consecutivePasses", verdict.consecutivePasses)
                .put("failedStage", verdict.failedStage ?: JSONObject.NULL)
            )

        soak?.let {
            root.put("soak", JSONObject()
                .put("passed", it.passed)
                .put("requestedRuns", it.requestedRuns)
                .put("completedRuns", it.completedRuns)
                .put("passedRuns", it.passedRuns)
                .put("firstFailureRun", it.firstFailureRun ?: JSONObject.NULL)
                .put("firstFailureStage", it.firstFailureStage ?: JSONObject.NULL)
            )
        }
        overlay?.let {
            root.put("liveTrack", JSONObject()
                .put("ready", it.ready)
                .put("fps", it.fps)
                .put("impactFrame", it.impactFrame)
                .put("sourceWidthPx", it.sourceWidthPx)
                .put("sourceHeightPx", it.sourceHeightPx)
                .put("ballPoints", it.ball.size)
                .put("putterPoses", it.putter.size)
                .put("reason", it.reason)
            )
        }
        return root.toString()
    }
}

/** A render-friendly instant-replay slice for the uploaded-video style tracking screen. */
data class V82ReplaySlice(
    val playheadMs: Double,
    val ballTrail: List<V81LiveTrackPoint>,
    val putterGhosts: List<V81LivePutterPose>,
    val currentBall: V81LiveTrackPoint?,
    val currentPutter: V81LivePutterPose?,
    val impactReached: Boolean,
    val ready: Boolean,
    val reason: String
)

object V82LiveTrackReplay {
    fun slice(overlay: V81LiveTrackOverlay, playheadMs: Double, ghostWindowMs: Double = 80.0): V82ReplaySlice {
        if (!overlay.ready) return V82ReplaySlice(playheadMs, emptyList(), emptyList(), null, null, false, false, overlay.reason)
        if (!playheadMs.isFinite() || !ghostWindowMs.isFinite() || ghostWindowMs !in 1.0..500.0) {
            return V82ReplaySlice(playheadMs, emptyList(), emptyList(), null, null, false, false, "replay timing invalid")
        }
        val ball = overlay.ball.filter { it.tMs <= playheadMs }.sortedBy { it.tMs }
        val putter = overlay.putter.filter { it.tMs <= playheadMs && it.tMs >= playheadMs - ghostWindowMs }.sortedBy { it.tMs }
        val currentBall = overlay.ball.minByOrNull { abs(it.tMs - playheadMs) }?.takeIf { abs(it.tMs - playheadMs) <= 1000.0 / overlay.fps.coerceAtLeast(1) * 1.6 }
        val currentPutter = overlay.putter.minByOrNull { abs(it.tMs - playheadMs) }?.takeIf { abs(it.tMs - playheadMs) <= 1000.0 / overlay.fps.coerceAtLeast(1) * 1.6 }
        val impactReached = overlay.ball.any { it.frame == overlay.impactFrame && it.tMs <= playheadMs } || playheadMs >= 0.0
        return V82ReplaySlice(playheadMs, ball, putter, currentBall, currentPutter, impactReached, true, "replay slice ready")
    }

    fun timeline(overlay: V81LiveTrackOverlay): DoubleArray {
        if (!overlay.ready) return doubleArrayOf()
        val times = (overlay.ball.map { it.tMs } + overlay.putter.map { it.tMs }).filter { it.isFinite() }.distinct().sorted()
        return times.toDoubleArray()
    }
}

object V82HardwarelessTrainingJourneyRuntime {
    @Volatile private var latest: V82TrainingJourneyResult? = null
    fun run(): V82TrainingJourneyResult = V82HardwarelessTrainingJourney.verify().also { latest = it }
    fun snapshot(): V82TrainingJourneyResult? = latest
    fun clear() { latest = null }
}
