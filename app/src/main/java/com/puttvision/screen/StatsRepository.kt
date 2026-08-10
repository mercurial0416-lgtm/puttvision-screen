package com.puttvision.screen

import android.content.Context
import kotlin.math.abs
import kotlin.math.sqrt

data class ShotRecord(
    val metrics: ShotMetrics,
    val result: SimResult?,
    val strokeScore: StrokeScore,
    val mode: PracticeMode,
    val targetDistanceM: Double = 0.0,
    val stimpMeters: Double = 2.8,
    val sideSlopePct: Double = 0.0,
    val longSlopePct: Double = 0.0,
    val timestampMs: Long = System.currentTimeMillis()
)

data class StatsSummary(
    val shots: Int,
    val made: Int,
    val makePct: Double,
    val avgScore: Double,
    val avgLaunch: Double,
    val launchStd: Double,
    val avgFace: Double?,
    val avgPath: Double?,
    val avgDistanceErrorCm: Double?
)

class StatsRepository(context: Context) {
    private val prefs =
        context.getSharedPreferences("puttvision_stats_v04", Context.MODE_PRIVATE)

    private val records = ArrayList<ShotRecord>()

    init {
        load()
    }

    fun all(): List<ShotRecord> = records.toList()

    fun recent(count: Int = 30): List<ShotRecord> =
        records.takeLast(count)

    fun add(record: ShotRecord) {
        records += record

        while (records.size > 300) {
            records.removeAt(0)
        }

        save()
    }

    fun clear() {
        records.clear()
        prefs.edit().clear().apply()
    }

    fun summary(): StatsSummary {
        if (records.isEmpty()) {
            return StatsSummary(
                shots = 0,
                made = 0,
                makePct = 0.0,
                avgScore = 0.0,
                avgLaunch = 0.0,
                launchStd = 0.0,
                avgFace = null,
                avgPath = null,
                avgDistanceErrorCm = null
            )
        }

        val made = records.count { it.result?.holed == true }
        val launches = records.map { it.metrics.launchAngleDeg }
        val mean = launches.average()
        val variance = launches.map { (it - mean) * (it - mean) }.average()

        val faces = records.mapNotNull { it.metrics.faceAngleDeg }
        val paths = records.mapNotNull { it.metrics.pathAngleDeg }
        val errors = records.mapNotNull { it.result?.distanceToCupM }

        return StatsSummary(
            shots = records.size,
            made = made,
            makePct = made * 100.0 / records.size,
            avgScore = records.map { it.strokeScore.total }.average(),
            avgLaunch = mean,
            launchStd = sqrt(variance),
            avgFace = faces.takeIf { it.isNotEmpty() }?.average(),
            avgPath = paths.takeIf { it.isNotEmpty() }?.average(),
            avgDistanceErrorCm = errors.takeIf { it.isNotEmpty() }?.average()?.times(100.0)
        )
    }

    private fun save() {
        // Compact line format; only values needed for long-term stats/heatmap are persisted.
        val payload = records.joinToString("\n") { r ->
            val m = r.metrics
            val res = r.result

            listOf(
                r.timestampMs,
                r.mode.name,
                m.ballSpeedMps,
                m.launchAngleDeg,
                m.faceAngleDeg ?: "",
                m.pathAngleDeg ?: "",
                m.tempoRatio ?: "",
                m.impactOffsetMm ?: "",
                r.strokeScore.total,
                res?.holed ?: false,
                res?.finishX ?: "",
                res?.finishY ?: "",
                res?.distanceToCupM ?: "",
                r.targetDistanceM,
                r.stimpMeters,
                r.sideSlopePct,
                r.longSlopePct
            ).joinToString("|")
        }

        prefs.edit().putString("records", payload).apply()
    }

    private fun load() {
        val payload = prefs.getString("records", null) ?: return

        for (line in payload.lineSequence()) {
            val p = line.split("|")
            if (p.size < 13) continue

            try {
                val metrics = ShotMetrics(
                    ballSpeedMps = p[2].toDouble(),
                    launchAngleDeg = p[3].toDouble(),
                    headSpeedMps = null,
                    faceAngleDeg = p[4].toDoubleOrNull(),
                    pathAngleDeg = p[5].toDoubleOrNull(),
                    faceToPathDeg = null,
                    smash = null,
                    impactOffsetMm = p[7].toDoubleOrNull(),
                    measuredAtNs = 0L,
                    tempoRatio = p[6].toDoubleOrNull()
                )

                val result =
                    if (p[10].isNotBlank() && p[11].isNotBlank() && p[12].isNotBlank()) {
                        SimResult(
                            holed = p[9].toBoolean(),
                            finishX = p[10].toDouble(),
                            finishY = p[11].toDouble(),
                            distanceToCupM = p[12].toDouble(),
                            elapsedSec = 0.0
                        )
                    } else null

                val total = p[8].toInt()

                records += ShotRecord(
                    metrics = metrics,
                    result = result,
                    strokeScore = StrokeScore(
                        total = total,
                        face = total,
                        path = total,
                        tempo = total,
                        impact = total,
                        distance = total,
                        consistency = total
                    ),
                    mode = PracticeMode.valueOf(p[1]),
                    targetDistanceM = p.getOrNull(13)?.toDoubleOrNull() ?: 0.0,
                    stimpMeters = p.getOrNull(14)?.toDoubleOrNull() ?: 2.8,
                    sideSlopePct = p.getOrNull(15)?.toDoubleOrNull() ?: 0.0,
                    longSlopePct = p.getOrNull(16)?.toDoubleOrNull() ?: 0.0,
                    timestampMs = p[0].toLong()
                )
            } catch (_: Throwable) {
            }
        }
    }
}
