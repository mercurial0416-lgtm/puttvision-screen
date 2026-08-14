package com.puttvision.screen

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil


data class V43StorageDecision(
    val ok: Boolean,
    val usableBytes: Long,
    val deletedFiles: Int,
    val remainingFiles: Int,
    val remainingBytes: Long
) {
    val label: String
        get() = if (ok) {
            "HFR 저장공간 OK · ${(usableBytes / (1024L * 1024L))}MB · 정리 ${deletedFiles}개"
        } else {
            "HFR 저장공간 부족 · ${(usableBytes / (1024L * 1024L))}MB"
        }
}

/** Keeps short-lived HFR recordings bounded and refuses precision capture before disk exhaustion. */
object V43HfrStorageGuard {
    const val MIN_FREE_BYTES = 192L * 1024L * 1024L
    const val MAX_CACHE_BYTES = 96L * 1024L * 1024L
    const val MAX_CACHE_FILES = 12
    const val MAX_AGE_MS = 30L * 60L * 1000L

    fun prepare(dir: File, nowMs: Long = System.currentTimeMillis(), usableBytesOverride: Long? = null): V43StorageDecision {
        dir.mkdirs()
        var deleted = 0
        val initial = dir.listFiles()?.filter { it.isFile }?.toMutableList() ?: mutableListOf()

        initial.filter { nowMs - it.lastModified() > MAX_AGE_MS }
            .forEach { if (runCatching { it.delete() }.getOrDefault(false)) deleted++ }

        var remaining = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.toMutableList()
            ?: mutableListOf()
        var bytes = remaining.sumOf { it.length().coerceAtLeast(0L) }

        while (remaining.size > MAX_CACHE_FILES || bytes > MAX_CACHE_BYTES) {
            val victim = remaining.lastOrNull() ?: break
            val size = victim.length().coerceAtLeast(0L)
            if (runCatching { victim.delete() }.getOrDefault(false)) {
                deleted++
                remaining.removeAt(remaining.lastIndex)
                bytes = (bytes - size).coerceAtLeast(0L)
            } else {
                break
            }
        }

        val usable = usableBytesOverride ?: dir.usableSpace
        return V43StorageDecision(
            ok = usable >= MIN_FREE_BYTES,
            usableBytes = usable,
            deletedFiles = deleted,
            remainingFiles = remaining.size,
            remainingBytes = bytes
        )
    }
}

/** Collision-proof names even when two capture starts share the same wall-clock millisecond. */
object V43CaptureFileNamer {
    private val sequence = AtomicLong(0L)

    fun create(dir: File, fps: Int, nowMs: Long = System.currentTimeMillis()): File {
        val seq = sequence.incrementAndGet()
        return File(dir, "shot_${nowMs}_${seq}_${fps.coerceAtLeast(0)}fps.mp4")
    }
}

/** Stops hammering CameraX/storage after repeated finalize/bind failures, then self-recovers. */
class V43HfrFailureCircuit(
    private val failureLimit: Int = 3,
    private val cooldownMs: Long = 60_000L
) {
    private var failures = 0
    private var blockedUntilMs = 0L

    @Synchronized
    fun allow(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (blockedUntilMs <= 0L) return true
        if (nowMs >= blockedUntilMs) {
            failures = 0
            blockedUntilMs = 0L
            return true
        }
        return false
    }

    @Synchronized
    fun recordFailure(nowMs: Long = System.currentTimeMillis()) {
        failures++
        if (failures >= failureLimit) blockedUntilMs = nowMs + cooldownMs
    }

    @Synchronized
    fun recordSuccess() {
        failures = 0
        blockedUntilMs = 0L
    }

    @Synchronized
    fun remainingMs(nowMs: Long = System.currentTimeMillis()): Long =
        (blockedUntilMs - nowMs).coerceAtLeast(0L)

    @Synchronized
    fun failureCount(): Int = failures
}


data class V43HfrHealthSummary(
    val samples: Int,
    val medianTotalMs: Long,
    val p95TotalMs: Long,
    val p95CalibrationMs: Long,
    val fastMarkerlessPct: Int,
    val slowSamples: Int
) {
    val degraded: Boolean get() = samples >= 5 && (p95TotalMs >= 4_500L || p95CalibrationMs >= 1_400L)
    val label: String
        get() = if (samples == 0) "HFR HEALTH · 데이터 없음"
        else "HFR HEALTH · n=$samples · P50 ${medianTotalMs}ms · P95 ${p95TotalMs}ms · CAL95 ${p95CalibrationMs}ms · FAST ${fastMarkerlessPct}%${if (degraded) " · SLOW" else ""}"
}

/** Sliding numeric-only window for spotting thermal/performance degradation across a long session. */
object V43HfrHealthWindow {
    private const val MAX_SAMPLES = 24
    private val values = ArrayDeque<V42HfrAnalysisHealth>()

    @Synchronized
    fun publish(value: V42HfrAnalysisHealth) {
        values.addLast(value)
        while (values.size > MAX_SAMPLES) values.removeFirst()
    }

    @Synchronized
    fun summary(): V43HfrHealthSummary {
        val snapshot = values.toList()
        if (snapshot.isEmpty()) return V43HfrHealthSummary(0, 0L, 0L, 0L, 0, 0)
        val totals = snapshot.map { it.totalAnalysisMs.coerceAtLeast(0L) }
        val calibration = snapshot.map { it.calibrationMs.coerceAtLeast(0L) }
        val fast = snapshot.count { it.calibrationMode == "MARKERLESS_FAST" }
        val slow = snapshot.count { it.totalAnalysisMs >= 4_500L }
        return V43HfrHealthSummary(
            samples = snapshot.size,
            medianTotalMs = percentile(totals, .50),
            p95TotalMs = percentile(totals, .95),
            p95CalibrationMs = percentile(calibration, .95),
            fastMarkerlessPct = ((fast * 100.0) / snapshot.size).toInt(),
            slowSamples = slow
        )
    }

    @Synchronized
    fun clear() = values.clear()

    private fun percentile(values: List<Long>, p: Double): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val rank = ceil(p.coerceIn(0.0, 1.0) * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}


data class V43CompanionPacket(
    val measurement: V15CameraMeasurement,
    val sequence: Long?
)

/** Optional sequence field keeps V28 peers compatible while newer peers reject replay/out-of-order packets. */
object V43CompanionWire {
    fun encodeMeasurement(code: String, m: V15CameraMeasurement, offsetMs: Long, sequence: Long): String =
        JSONObject().apply {
            put("pv", V28CompanionProtocol.VERSION)
            put("type", "measurement")
            put("code", code)
            put("seq", sequence)
            put("capturedAtMs", m.receivedAtMs + offsetMs)
            put("payload", V15CompanionWire.encode(m))
        }.toString()

    fun decodeMeasurement(raw: String, expectedCode: String, nowMs: Long = System.currentTimeMillis()): V43CompanionPacket? =
        runCatching {
            val j = JSONObject(raw)
            require(j.optInt("pv") == V28CompanionProtocol.VERSION && j.optString("type") == "measurement")
            require(j.optString("code") == expectedCode)
            val captured = j.getLong("capturedAtMs")
            require(nowMs - captured in -300L..2200L)
            val measurement = (V15CompanionWire.decode(j.getString("payload")) ?: error("payload"))
                .copy(receivedAtMs = captured)
            V43CompanionPacket(
                measurement = measurement,
                sequence = if (j.has("seq")) j.getLong("seq") else null
            )
        }.getOrNull()
}

class V43CompanionSequenceGate {
    private val latest = ConcurrentHashMap<String, Long>()

    fun accept(cameraId: String, sequence: Long?): Boolean {
        if (sequence == null) return true
        if (sequence < 0L) return false
        synchronized(latest) {
            val previous = latest[cameraId]
            if (previous != null && sequence <= previous) return false
            latest[cameraId] = sequence
            return true
        }
    }

    fun clear() = latest.clear()
}


data class V43FeatureTrackPacket(
    val cameraId: String,
    val view: V15CameraView,
    val capturedAtMs: Long,
    val sequence: Long,
    val track: HfrFeatureTrack
)

object V43FeatureTrackWire {
    const val MAX_FRAMES = 32

    fun encode(code: String, packet: V43FeatureTrackPacket): String = JSONObject().apply {
        put("pv", V28CompanionProtocol.VERSION)
        put("type", "feature_track")
        put("code", code)
        put("camera", packet.cameraId)
        put("view", packet.view.name)
        put("capturedAtMs", packet.capturedAtMs)
        put("seq", packet.sequence)
        put("fps", packet.track.fps)
        put("impact", packet.track.impactFrame)
        put("frames", JSONArray().apply {
            packet.track.frames.take(MAX_FRAMES).forEach { f ->
                put(JSONObject().apply {
                    put("f", f.frame)
                    put("t", f.timeFromImpactMs)
                    f.ballXcm?.let { put("bx", it) }; f.ballYcm?.let { put("by", it) }
                    f.heelXcm?.let { put("hx", it) }; f.heelYcm?.let { put("hy", it) }
                    f.toeXcm?.let { put("tx", it) }; f.toeYcm?.let { put("ty", it) }
                    f.markerAngleDeg?.let { put("a", it) }
                })
            }
        })
    }.toString()

    fun isFeatureTrack(raw: String): Boolean = runCatching {
        val j = JSONObject(raw)
        j.optInt("pv") == V28CompanionProtocol.VERSION && j.optString("type") == "feature_track"
    }.getOrDefault(false)

    fun decode(raw: String, expectedCode: String, nowMs: Long = System.currentTimeMillis()): V43FeatureTrackPacket? =
        runCatching {
            val j = JSONObject(raw)
            require(j.optInt("pv") == V28CompanionProtocol.VERSION && j.optString("type") == "feature_track")
            require(j.optString("code") == expectedCode)
            val captured = j.getLong("capturedAtMs")
            require(nowMs - captured in -300L..2200L)
            val arr = j.getJSONArray("frames")
            require(arr.length() in 1..MAX_FRAMES)
            fun value(o: JSONObject, key: String): Double? = o.optDouble(key, Double.NaN).takeIf { it.isFinite() }
            val frames = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(HfrFeatureFrame(
                        frame = o.getInt("f"),
                        timeFromImpactMs = o.getDouble("t"),
                        ballXcm = value(o, "bx"), ballYcm = value(o, "by"),
                        heelXcm = value(o, "hx"), heelYcm = value(o, "hy"),
                        toeXcm = value(o, "tx"), toeYcm = value(o, "ty"),
                        markerAngleDeg = value(o, "a")
                    ))
                }
            }
            V43FeatureTrackPacket(
                cameraId = j.getString("camera"),
                view = runCatching { V15CameraView.valueOf(j.getString("view")) }.getOrDefault(V15CameraView.PRIMARY),
                capturedAtMs = captured,
                sequence = j.getLong("seq"),
                track = HfrFeatureTrack(j.getInt("fps"), j.getInt("impact"), frames)
            )
        }.getOrNull()
}

/** Host-side compact feature geometry only; no bitmaps/video cross the network. */
object V43RemoteFeatureTrackRuntime {
    private val tracks = ConcurrentHashMap<String, V43FeatureTrackPacket>()

    fun publish(packet: V43FeatureTrackPacket) {
        tracks[packet.cameraId] = packet.copy(track = packet.track.copy(frames = packet.track.frames.take(V43FeatureTrackWire.MAX_FRAMES)))
    }

    fun fresh(nowMs: Long = System.currentTimeMillis(), maxAgeMs: Long = 2200L): List<V43FeatureTrackPacket> =
        tracks.values.filter { nowMs - it.capturedAtMs in 0L..maxAgeMs }.sortedByDescending { it.capturedAtMs }

    fun clear() = tracks.clear()
}


data class V43CompanionSyncHealth(
    val ageMs: Long,
    val rttMs: Long?,
    val fresh: Boolean,
    val label: String
)

object V43CompanionSyncHealthPolicy {
    const val STALE_AFTER_MS = 45_000L

    fun evaluate(sync: V28ClockSync?, lastSyncAtMs: Long, nowMs: Long = System.currentTimeMillis()): V43CompanionSyncHealth {
        if (sync == null || lastSyncAtMs <= 0L) return V43CompanionSyncHealth(Long.MAX_VALUE, null, false, "SYNC 없음")
        val age = nowMs - lastSyncAtMs
        val fresh = age in 0L..STALE_AFTER_MS && V42CompanionSyncPolicy.acceptable(sync)
        val ageSec = if (age < 0L) 0L else age / 1000L
        val label = if (fresh) "SYNC ${ageSec}s · ${sync.rttMs}ms" else "SYNC 오래됨 · ${ageSec}s · ${sync.rttMs}ms"
        return V43CompanionSyncHealth(age, sync.rttMs, fresh, label)
    }
}
