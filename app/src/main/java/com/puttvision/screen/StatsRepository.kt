package com.puttvision.screen

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
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
    val terrainProfileId: Int = -1,
    val putterProfileName: String? = null,
    val putterHeadWidthCm: Double? = null,
    val physicalMatStimpM: Double? = null,
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

@Entity(
    tableName = "shots",
    indices = [
        Index(value = ["timestampMs"]),
        Index(value = ["mode"]),
        Index(value = ["putterProfileName"])
    ]
)
data class ShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val mode: String,
    val targetDistanceM: Double,
    val stimpMeters: Double,
    val sideSlopePct: Double,
    val longSlopePct: Double,
    val terrainProfileId: Int,
    val putterProfileName: String?,
    val putterHeadWidthCm: Double?,
    val physicalMatStimpM: Double?,

    val ballSpeedMps: Double,
    val launchAngleDeg: Double,
    val headSpeedMps: Double?,
    val faceAngleDeg: Double?,
    val pathAngleDeg: Double?,
    val faceToPathDeg: Double?,
    val smash: Double?,
    val impactOffsetMm: Double?,
    val tempoRatio: Double?,
    val backswingMs: Double?,
    val downswingMs: Double?,
    val backswingLengthCm: Double?,
    val peakHeadAccelerationMps2: Double?,
    val rawBallSpeedMps: Double?,
    val estimatedMatDecelMps2: Double?,
    val estimatedMatStimpM: Double?,
    val confidence: Double?,

    val scoreTotal: Int,
    val scoreFace: Int,
    val scorePath: Int,
    val scoreTempo: Int,
    val scoreImpact: Int,
    val scoreDistance: Int,
    val scoreConsistency: Int,

    val hasResult: Boolean,
    val holed: Boolean,
    val finishX: Double?,
    val finishY: Double?,
    val distanceToCupM: Double?,
    val elapsedSec: Double?
)

@Dao
interface ShotDao {
    @Query("SELECT * FROM shots ORDER BY timestampMs ASC, id ASC")
    fun all(): List<ShotEntity>

    @Query("SELECT * FROM shots ORDER BY timestampMs DESC, id DESC LIMIT :count")
    fun recent(count: Int): List<ShotEntity>

    @Query("SELECT * FROM shots WHERE timestampMs >= :startMs AND timestampMs < :endMs ORDER BY timestampMs ASC")
    fun between(startMs: Long, endMs: Long): List<ShotEntity>

    @Insert
    fun insert(entity: ShotEntity)

    @Insert
    fun insertAll(entities: List<ShotEntity>)

    @Query("SELECT COUNT(*) FROM shots")
    fun count(): Int

    @Query("DELETE FROM shots")
    fun clear()
}

@Database(entities = [ShotEntity::class], version = 1, exportSchema = false)
abstract class PuttVisionDatabase : RoomDatabase() {
    abstract fun shotDao(): ShotDao
}

class StatsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val legacyPrefs =
        appContext.getSharedPreferences("puttvision_stats_v04", Context.MODE_PRIVATE)

    private val db = Room.databaseBuilder(
        appContext,
        PuttVisionDatabase::class.java,
        "puttvision_stats_room.db"
    )
        .allowMainThreadQueries()
        .fallbackToDestructiveMigration()
        .build()

    private val dao = db.shotDao()

    init {
        migrateLegacyOnce()
    }

    fun all(): List<ShotRecord> = dao.all().map(::toRecord)

    fun recent(count: Int = 30): List<ShotRecord> =
        dao.recent(count.coerceAtLeast(1)).asReversed().map(::toRecord)

    fun between(startMs: Long, endMs: Long): List<ShotRecord> =
        dao.between(startMs, endMs).map(::toRecord)

    fun add(record: ShotRecord) {
        dao.insert(toEntity(record))
    }

    fun clear() {
        dao.clear()
        legacyPrefs.edit().remove("records").apply()
    }

    fun summary(): StatsSummary = summaryOf(all())

    fun summary(records: List<ShotRecord>): StatsSummary = summaryOf(records)

    private fun summaryOf(records: List<ShotRecord>): StatsSummary {
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

    private fun toEntity(r: ShotRecord): ShotEntity {
        val m = r.metrics
        val result = r.result
        val score = r.strokeScore
        return ShotEntity(
            timestampMs = r.timestampMs,
            mode = r.mode.name,
            targetDistanceM = r.targetDistanceM,
            stimpMeters = r.stimpMeters,
            sideSlopePct = r.sideSlopePct,
            longSlopePct = r.longSlopePct,
            terrainProfileId = r.terrainProfileId,
            putterProfileName = r.putterProfileName,
            putterHeadWidthCm = r.putterHeadWidthCm,
            physicalMatStimpM = r.physicalMatStimpM,
            ballSpeedMps = m.ballSpeedMps,
            launchAngleDeg = m.launchAngleDeg,
            headSpeedMps = m.headSpeedMps,
            faceAngleDeg = m.faceAngleDeg,
            pathAngleDeg = m.pathAngleDeg,
            faceToPathDeg = m.faceToPathDeg,
            smash = m.smash,
            impactOffsetMm = m.impactOffsetMm,
            tempoRatio = m.tempoRatio,
            backswingMs = m.backswingMs,
            downswingMs = m.downswingMs,
            backswingLengthCm = m.backswingLengthCm,
            peakHeadAccelerationMps2 = m.peakHeadAccelerationMps2,
            rawBallSpeedMps = m.rawBallSpeedMps,
            estimatedMatDecelMps2 = m.estimatedMatDecelMps2,
            estimatedMatStimpM = m.estimatedMatStimpM,
            confidence = m.confidence,
            scoreTotal = score.total,
            scoreFace = score.face,
            scorePath = score.path,
            scoreTempo = score.tempo,
            scoreImpact = score.impact,
            scoreDistance = score.distance,
            scoreConsistency = score.consistency,
            hasResult = result != null,
            holed = result?.holed ?: false,
            finishX = result?.finishX,
            finishY = result?.finishY,
            distanceToCupM = result?.distanceToCupM,
            elapsedSec = result?.elapsedSec
        )
    }

    private fun toRecord(e: ShotEntity): ShotRecord {
        val metrics = ShotMetrics(
            ballSpeedMps = e.ballSpeedMps,
            launchAngleDeg = e.launchAngleDeg,
            headSpeedMps = e.headSpeedMps,
            faceAngleDeg = e.faceAngleDeg,
            pathAngleDeg = e.pathAngleDeg,
            faceToPathDeg = e.faceToPathDeg,
            smash = e.smash,
            impactOffsetMm = e.impactOffsetMm,
            measuredAtNs = 0L,
            backswingMs = e.backswingMs,
            downswingMs = e.downswingMs,
            tempoRatio = e.tempoRatio,
            backswingLengthCm = e.backswingLengthCm,
            peakHeadAccelerationMps2 = e.peakHeadAccelerationMps2,
            rawBallSpeedMps = e.rawBallSpeedMps,
            estimatedMatDecelMps2 = e.estimatedMatDecelMps2,
            estimatedMatStimpM = e.estimatedMatStimpM,
            confidence = e.confidence
        )
        val result = if (e.hasResult) {
            SimResult(
                holed = e.holed,
                finishX = e.finishX ?: 0.0,
                finishY = e.finishY ?: 0.0,
                distanceToCupM = e.distanceToCupM ?: 0.0,
                elapsedSec = e.elapsedSec ?: 0.0
            )
        } else null
        return ShotRecord(
            metrics = metrics,
            result = result,
            strokeScore = StrokeScore(
                total = e.scoreTotal,
                face = e.scoreFace,
                path = e.scorePath,
                tempo = e.scoreTempo,
                impact = e.scoreImpact,
                distance = e.scoreDistance,
                consistency = e.scoreConsistency
            ),
            mode = runCatching { PracticeMode.valueOf(e.mode) }.getOrDefault(PracticeMode.PRACTICE),
            targetDistanceM = e.targetDistanceM,
            stimpMeters = e.stimpMeters,
            sideSlopePct = e.sideSlopePct,
            longSlopePct = e.longSlopePct,
            terrainProfileId = e.terrainProfileId,
            putterProfileName = e.putterProfileName,
            putterHeadWidthCm = e.putterHeadWidthCm,
            physicalMatStimpM = e.physicalMatStimpM,
            timestampMs = e.timestampMs
        )
    }

    private fun migrateLegacyOnce() {
        if (legacyPrefs.getBoolean("room_migrated_v1", false)) return
        if (dao.count() == 0) {
            val payload = legacyPrefs.getString("records", null)
            if (!payload.isNullOrBlank()) {
                val imported = parseLegacy(payload)
                if (imported.isNotEmpty()) dao.insertAll(imported.map(::toEntity))
            }
        }
        legacyPrefs.edit().putBoolean("room_migrated_v1", true).apply()
    }

    private fun parseLegacy(payload: String): List<ShotRecord> {
        val out = ArrayList<ShotRecord>()
        for (line in payload.lineSequence()) {
            val p = line.split("|")
            if (p.size < 13) continue
            try {
                val metrics = ShotMetrics(
                    ballSpeedMps = p[2].toDouble(),
                    launchAngleDeg = p[3].toDouble(),
                    headSpeedMps = p.getOrNull(17)?.toDoubleOrNull(),
                    faceAngleDeg = p[4].toDoubleOrNull(),
                    pathAngleDeg = p[5].toDoubleOrNull(),
                    faceToPathDeg = p.getOrNull(18)?.toDoubleOrNull(),
                    smash = p.getOrNull(19)?.toDoubleOrNull(),
                    impactOffsetMm = p[7].toDoubleOrNull(),
                    measuredAtNs = 0L,
                    tempoRatio = p[6].toDoubleOrNull(),
                    backswingMs = p.getOrNull(21)?.toDoubleOrNull(),
                    downswingMs = p.getOrNull(22)?.toDoubleOrNull(),
                    backswingLengthCm = p.getOrNull(23)?.toDoubleOrNull(),
                    peakHeadAccelerationMps2 = p.getOrNull(24)?.toDoubleOrNull(),
                    rawBallSpeedMps = p.getOrNull(25)?.toDoubleOrNull(),
                    estimatedMatDecelMps2 = p.getOrNull(26)?.toDoubleOrNull(),
                    estimatedMatStimpM = p.getOrNull(27)?.toDoubleOrNull(),
                    confidence = p.getOrNull(20)?.toDoubleOrNull()
                )
                val result = if (p[10].isNotBlank() && p[11].isNotBlank() && p[12].isNotBlank()) {
                    SimResult(
                        holed = p[9].toBoolean(),
                        finishX = p[10].toDouble(),
                        finishY = p[11].toDouble(),
                        distanceToCupM = p[12].toDouble(),
                        elapsedSec = 0.0
                    )
                } else null
                val total = p[8].toInt()
                out += ShotRecord(
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
        return out
    }
}
