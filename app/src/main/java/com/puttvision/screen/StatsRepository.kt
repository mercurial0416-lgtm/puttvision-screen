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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
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
    val userProfileId: String = ProductSessionRuntime.userProfileId,
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
        Index(value = ["putterProfileName"]),
        Index(value = ["userProfileId"])
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
    val userProfileId: String,

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
    val uncertaintyBallSpeedMps: Double?,
    val uncertaintyLaunchDeg: Double?,
    val uncertaintyHeadSpeedMps: Double?,
    val uncertaintyFaceDeg: Double?,
    val uncertaintyPathDeg: Double?,
    val uncertaintyImpactMm: Double?,
    val uncertaintyBasis: String?,

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
    val elapsedSec: Double?,
    val lipOut: Boolean,
    val cupContacts: Int
)

@Dao
interface ShotDao {
    @Query("SELECT * FROM shots ORDER BY timestampMs ASC, id ASC")
    fun allRaw(): List<ShotEntity>

    @Insert
    fun insert(entity: ShotEntity)

    @Insert
    fun insertAll(entities: List<ShotEntity>)

    @Query("SELECT COUNT(*) FROM shots")
    fun count(): Int

    @Query("DELETE FROM shots WHERE userProfileId = :profileId")
    fun clearProfile(profileId: String)

    @Query("DELETE FROM shots")
    fun clearAll()
}

@Database(entities = [ShotEntity::class], version = 3, exportSchema = false)
abstract class PuttVisionDatabase : RoomDatabase() {
    abstract fun shotDao(): ShotDao
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shots ADD COLUMN userProfileId TEXT NOT NULL DEFAULT 'owner'")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shots_userProfileId ON shots(userProfileId)")
    }
 }

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyBallSpeedMps REAL")
        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyLaunchDeg REAL")
        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyHeadSpeedMps REAL")
        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyFaceDeg REAL")
        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyPathDeg REAL")
        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyImpactMm REAL")
        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyBasis TEXT")
        db.execSQL("ALTER TABLE shots ADD COLUMN lipOut INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE shots ADD COLUMN cupContacts INTEGER NOT NULL DEFAULT 0")
    }
}

class StatsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val legacyPrefs = appContext.getSharedPreferences("puttvision_stats_v04", Context.MODE_PRIVATE)
    private val io = Executors.newSingleThreadExecutor()
    private val loaded = AtomicBoolean(false)
    private val lock = Any()
    @Volatile private var cache: List<ShotRecord> = emptyList()
    @Volatile private var onLoaded: (() -> Unit)? = null

    private val db = Room.databaseBuilder(
        appContext,
        PuttVisionDatabase::class.java,
        "puttvision_stats_room.db"
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()
    private val dao = db.shotDao()

    init {
        io.execute {
            migrateLegacyOnce()
            cache = dao.allRaw().map(::toRecord)
            loaded.set(true)
            onLoaded?.invoke()
        }
    }

    fun setOnLoaded(listener: () -> Unit) {
        onLoaded = listener
        if (loaded.get()) listener()
    }

    fun isLoaded(): Boolean = loaded.get()

    fun all(): List<ShotRecord> {
        val id = ProductSessionRuntime.userProfileId
        return cache.filter { it.userProfileId == id }
    }

    fun allProfiles(): List<ShotRecord> = cache.toList()

    fun recent(count: Int = 30): List<ShotRecord> =
        all().takeLast(count.coerceAtLeast(1))

    fun between(startMs: Long, endMs: Long): List<ShotRecord> =
        all().filter { it.timestampMs >= startMs && it.timestampMs < endMs }

    fun add(record: ShotRecord) {
        val normalized = if (record.userProfileId.isBlank()) record.copy(userProfileId = ProductSessionRuntime.userProfileId) else record
        synchronized(lock) { cache = cache + normalized }
        io.execute { dao.insert(toEntity(normalized)) }
    }

    /** Clears only the currently selected user profile. */
    fun clear() {
        val id = ProductSessionRuntime.userProfileId
        synchronized(lock) { cache = cache.filterNot { it.userProfileId == id } }
        io.execute { dao.clearProfile(id) }
    }

    fun clearAllProfiles() {
        synchronized(lock) { cache = emptyList() }
        io.execute { dao.clearAll() }
    }

    fun summary(): StatsSummary = summaryOf(all())
    fun summary(records: List<ShotRecord>): StatsSummary = summaryOf(records)

    fun exportJson(): JSONArray = JSONArray().apply {
        allProfiles().forEach { r -> put(recordToJson(r)) }
    }

    fun importJson(array: JSONArray, replace: Boolean) {
        val restored = ArrayList<ShotRecord>()
        for (i in 0 until array.length()) {
            val j = array.optJSONObject(i) ?: continue
            jsonToRecord(j)?.let(restored::add)
        }
        synchronized(lock) {
            cache = if (replace) restored else (cache + restored)
        }
        io.execute {
            if (replace) dao.clearAll()
            if (restored.isNotEmpty()) dao.insertAll(restored.map(::toEntity))
        }
    }

    private fun summaryOf(records: List<ShotRecord>): StatsSummary {
        if (records.isEmpty()) {
            return StatsSummary(0, 0, 0.0, 0.0, 0.0, 0.0, null, null, null)
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
            userProfileId = r.userProfileId.ifBlank { "owner" },
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
            uncertaintyBallSpeedMps = m.uncertainty?.ballSpeedMps,
            uncertaintyLaunchDeg = m.uncertainty?.launchDeg,
            uncertaintyHeadSpeedMps = m.uncertainty?.headSpeedMps,
            uncertaintyFaceDeg = m.uncertainty?.faceDeg,
            uncertaintyPathDeg = m.uncertainty?.pathDeg,
            uncertaintyImpactMm = m.uncertainty?.impactMm,
            uncertaintyBasis = m.uncertainty?.basis,
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
            elapsedSec = result?.elapsedSec,
            lipOut = result?.lipOut ?: false,
            cupContacts = result?.cupContacts ?: 0
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
            confidence = e.confidence,
            uncertainty = if (e.uncertaintyBallSpeedMps != null && e.uncertaintyLaunchDeg != null) {
                MeasurementUncertainty(
                    ballSpeedMps = e.uncertaintyBallSpeedMps,
                    launchDeg = e.uncertaintyLaunchDeg,
                    headSpeedMps = e.uncertaintyHeadSpeedMps,
                    faceDeg = e.uncertaintyFaceDeg,
                    pathDeg = e.uncertaintyPathDeg,
                    impactMm = e.uncertaintyImpactMm,
                    basis = e.uncertaintyBasis ?: "RESTORED"
                )
            } else null
        )
        val result = if (e.hasResult) {
            SimResult(
                e.holed,
                e.finishX ?: 0.0,
                e.finishY ?: 0.0,
                e.distanceToCupM ?: 0.0,
                e.elapsedSec ?: 0.0,
                e.lipOut,
                e.cupContacts
            )
        } else null
        return ShotRecord(
            metrics = metrics,
            result = result,
            strokeScore = StrokeScore(e.scoreTotal, e.scoreFace, e.scorePath, e.scoreTempo, e.scoreImpact, e.scoreDistance, e.scoreConsistency),
            mode = runCatching { PracticeMode.valueOf(e.mode) }.getOrDefault(PracticeMode.PRACTICE),
            targetDistanceM = e.targetDistanceM,
            stimpMeters = e.stimpMeters,
            sideSlopePct = e.sideSlopePct,
            longSlopePct = e.longSlopePct,
            terrainProfileId = e.terrainProfileId,
            putterProfileName = e.putterProfileName,
            putterHeadWidthCm = e.putterHeadWidthCm,
            physicalMatStimpM = e.physicalMatStimpM,
            userProfileId = e.userProfileId.ifBlank { "owner" },
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
                    SimResult(p[9].toBoolean(), p[10].toDouble(), p[11].toDouble(), p[12].toDouble(), 0.0)
                } else null
                val total = p[8].toInt()
                out += ShotRecord(
                    metrics = metrics,
                    result = result,
                    strokeScore = StrokeScore(total, total, total, total, total, total, total),
                    mode = PracticeMode.valueOf(p[1]),
                    targetDistanceM = p.getOrNull(13)?.toDoubleOrNull() ?: 0.0,
                    stimpMeters = p.getOrNull(14)?.toDoubleOrNull() ?: 2.8,
                    sideSlopePct = p.getOrNull(15)?.toDoubleOrNull() ?: 0.0,
                    longSlopePct = p.getOrNull(16)?.toDoubleOrNull() ?: 0.0,
                    userProfileId = "owner",
                    timestampMs = p[0].toLong()
                )
            } catch (_: Throwable) { }
        }
        return out
    }

    private fun recordToJson(r: ShotRecord): JSONObject = JSONObject().apply {
        put("t", r.timestampMs); put("profile", r.userProfileId); put("mode", r.mode.name)
        put("distance", r.targetDistanceM); put("stimp", r.stimpMeters); put("side", r.sideSlopePct); put("long", r.longSlopePct); put("terrain", r.terrainProfileId)
        putNullable("putter", r.putterProfileName); putNullable("putterWidth", r.putterHeadWidthCm); putNullable("mat", r.physicalMatStimpM)
        val m = r.metrics
        put("ball", m.ballSpeedMps); put("launch", m.launchAngleDeg); putNullable("head", m.headSpeedMps); putNullable("face", m.faceAngleDeg); putNullable("path", m.pathAngleDeg)
        putNullable("f2p", m.faceToPathDeg); putNullable("smash", m.smash); putNullable("impact", m.impactOffsetMm); putNullable("tempo", m.tempoRatio)
        putNullable("backswingMs", m.backswingMs); putNullable("downswingMs", m.downswingMs); putNullable("backswingLength", m.backswingLengthCm); putNullable("accel", m.peakHeadAccelerationMps2)
        putNullable("rawBall", m.rawBallSpeedMps); putNullable("matDecel", m.estimatedMatDecelMps2); putNullable("matStimp", m.estimatedMatStimpM); putNullable("confidence", m.confidence)
        m.uncertainty?.let { u ->
            put("uncertainty", JSONObject().apply {
                put("ball", u.ballSpeedMps); put("launch", u.launchDeg); putNullable("head", u.headSpeedMps); putNullable("face", u.faceDeg); putNullable("path", u.pathDeg); putNullable("impact", u.impactMm); put("basis", u.basis)
            })
        }
        val s = r.strokeScore
        put("score", JSONObject().apply { put("total", s.total); put("face", s.face); put("path", s.path); put("tempo", s.tempo); put("impact", s.impact); put("distance", s.distance); put("consistency", s.consistency) })
        r.result?.let { result -> put("result", JSONObject().apply { put("holed", result.holed); put("x", result.finishX); put("y", result.finishY); put("cup", result.distanceToCupM); put("elapsed", result.elapsedSec); put("lipOut", result.lipOut); put("contacts", result.cupContacts) }) }
    }

    private fun jsonToRecord(j: JSONObject): ShotRecord? = runCatching {
        val uj = j.optJSONObject("uncertainty")
        val uncertainty = uj?.let {
            MeasurementUncertainty(
                ballSpeedMps = it.optDouble("ball", 0.0),
                launchDeg = it.optDouble("launch", 0.0),
                headSpeedMps = it.optNullableDouble("head"),
                faceDeg = it.optNullableDouble("face"),
                pathDeg = it.optNullableDouble("path"),
                impactMm = it.optNullableDouble("impact"),
                basis = it.optString("basis", "RESTORED")
            )
        }
        val m = ShotMetrics(
            ballSpeedMps = j.getDouble("ball"), launchAngleDeg = j.getDouble("launch"), headSpeedMps = j.optNullableDouble("head"), faceAngleDeg = j.optNullableDouble("face"), pathAngleDeg = j.optNullableDouble("path"), faceToPathDeg = j.optNullableDouble("f2p"), smash = j.optNullableDouble("smash"), impactOffsetMm = j.optNullableDouble("impact"), measuredAtNs = 0L,
            backswingMs = j.optNullableDouble("backswingMs"), downswingMs = j.optNullableDouble("downswingMs"), tempoRatio = j.optNullableDouble("tempo"), backswingLengthCm = j.optNullableDouble("backswingLength"), peakHeadAccelerationMps2 = j.optNullableDouble("accel"), rawBallSpeedMps = j.optNullableDouble("rawBall"), estimatedMatDecelMps2 = j.optNullableDouble("matDecel"), estimatedMatStimpM = j.optNullableDouble("matStimp"), confidence = j.optNullableDouble("confidence"), uncertainty = uncertainty
        )
        val sj = j.optJSONObject("score") ?: JSONObject()
        val total = sj.optInt("total", 0)
        val score = StrokeScore(total, sj.optInt("face", total), sj.optInt("path", total), sj.optInt("tempo", total), sj.optInt("impact", total), sj.optInt("distance", total), sj.optInt("consistency", total))
        val rj = j.optJSONObject("result")
        val result = rj?.let { SimResult(it.optBoolean("holed", false), it.optDouble("x", 0.0), it.optDouble("y", 0.0), it.optDouble("cup", 0.0), it.optDouble("elapsed", 0.0), it.optBoolean("lipOut", false), it.optInt("contacts", 0)) }
        ShotRecord(
            metrics = m, result = result, strokeScore = score,
            mode = runCatching { PracticeMode.valueOf(j.optString("mode", PracticeMode.PRACTICE.name)) }.getOrDefault(PracticeMode.PRACTICE),
            targetDistanceM = j.optDouble("distance", 0.0), stimpMeters = j.optDouble("stimp", 2.8), sideSlopePct = j.optDouble("side", 0.0), longSlopePct = j.optDouble("long", 0.0), terrainProfileId = j.optInt("terrain", -1),
            putterProfileName = j.optNullableString("putter"), putterHeadWidthCm = j.optNullableDouble("putterWidth"), physicalMatStimpM = j.optNullableDouble("mat"),
            userProfileId = j.optString("profile", "owner").ifBlank { "owner" }, timestampMs = j.optLong("t", System.currentTimeMillis())
        )
    }.getOrNull()

    fun close() {
        io.execute { runCatching { db.close() } }
        io.shutdown()
    }
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.optNullableDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return optDouble(key).takeIf { it.isFinite() }
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}
