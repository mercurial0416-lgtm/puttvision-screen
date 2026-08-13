package com.puttvision.screen

import org.json.JSONObject
import java.security.SecureRandom

data class V28ClockSync(val offsetMs: Long, val rttMs: Long) {
    val label: String get() = when {
        rttMs <= 35 -> "SYNC 좋음 · ${rttMs}ms"
        rttMs <= 90 -> "SYNC 보통 · ${rttMs}ms"
        else -> "SYNC 느림 · ${rttMs}ms"
    }
}

object V28ClockSyncEstimator {
    fun estimate(t0: Long, serverMs: Long, t2: Long): V28ClockSync {
        val rtt = (t2 - t0).coerceAtLeast(0L)
        return V28ClockSync(serverMs - (t0 + rtt / 2L), rtt)
    }
}

object V28CompanionProtocol {
    const val VERSION = 28
    private val random = SecureRandom()
    private const val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun newSessionCode(): String = buildString {
        repeat(8) { append(alphabet[random.nextInt(alphabet.length)]) }
    }

    fun syncRequest(code: String, t0: Long): String = JSONObject().apply {
        put("pv", VERSION); put("type", "sync"); put("code", code); put("t0", t0)
    }.toString()

    fun syncAck(raw: String, expectedCode: String, serverMs: Long): String? = runCatching {
        val j = JSONObject(raw)
        require(j.optInt("pv") == VERSION && j.optString("type") == "sync")
        JSONObject().apply {
            put("pv", VERSION)
            put("type", "sync_ack")
            put("t0", j.getLong("t0"))
            put("t1", serverMs)
            put("ok", j.optString("code") == expectedCode)
        }.toString()
    }.getOrNull()

    fun parseSyncAck(raw: String, expectedT0: Long, t2: Long): V28ClockSync? = runCatching {
        val j = JSONObject(raw)
        require(j.optInt("pv") == VERSION && j.optString("type") == "sync_ack")
        require(j.optBoolean("ok", false))
        require(j.getLong("t0") == expectedT0)
        V28ClockSyncEstimator.estimate(expectedT0, j.getLong("t1"), t2)
    }.getOrNull()

    fun encodeMeasurement(code: String, m: V15CameraMeasurement, offsetMs: Long): String = JSONObject().apply {
        put("pv", VERSION)
        put("type", "measurement")
        put("code", code)
        put("capturedAtMs", m.receivedAtMs + offsetMs)
        put("payload", V15CompanionWire.encode(m))
    }.toString()

    fun decodeMeasurement(raw: String, expectedCode: String, nowMs: Long = System.currentTimeMillis()): V15CameraMeasurement? = runCatching {
        val j = JSONObject(raw)
        require(j.optInt("pv") == VERSION && j.optString("type") == "measurement")
        require(j.optString("code") == expectedCode)
        val captured = j.getLong("capturedAtMs")
        val age = nowMs - captured
        require(age in -300L..2200L)
        (V15CompanionWire.decode(j.getString("payload")) ?: error("payload")).copy(receivedAtMs = captured)
    }.getOrNull()

    fun isSync(raw: String): Boolean = runCatching {
        val j = JSONObject(raw)
        j.optInt("pv") == VERSION && j.optString("type") == "sync"
    }.getOrDefault(false)
}
