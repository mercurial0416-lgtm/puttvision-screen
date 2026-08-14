package com.puttvision.screen

/** Keeps long multi-phone sessions time-aligned instead of trusting one startup handshake forever. */
object V42CompanionSyncPolicy {
    const val REFRESH_INTERVAL_MS = 30_000L
    const val MAX_ACCEPTABLE_RTT_MS = 1_200L
    const val SOCKET_READ_TIMEOUT_MS = 2_500

    fun shouldRefresh(lastSyncAtMs: Long, nowMs: Long, current: V28ClockSync?): Boolean {
        if (current == null || lastSyncAtMs <= 0L) return true
        val age = nowMs - lastSyncAtMs
        return age < 0L || age >= REFRESH_INTERVAL_MS
    }

    fun acceptable(sync: V28ClockSync): Boolean =
        sync.rttMs in 0L..MAX_ACCEPTABLE_RTT_MS
}
