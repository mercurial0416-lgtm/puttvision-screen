package com.puttvision.screen

object V49SessionWindow {
    const val GAP_MS = 30L * 60L * 1000L
    const val LIVE_MAX_IDLE_MS = 2L * 60L * 60L * 1000L

    fun current(recordsRaw: List<ShotRecord>, nowMs: Long = System.currentTimeMillis()): List<ShotRecord> {
        val records = recordsRaw.sortedBy { it.timestampMs }
        if (records.isEmpty()) return emptyList()
        val last = records.last()
        if (last.timestampMs <= 0L || nowMs - last.timestampMs !in 0L..LIVE_MAX_IDLE_MS) return emptyList()
        var start = 0
        for (i in 1 until records.size) {
            if (records[i].timestampMs - records[i - 1].timestampMs > GAP_MS) start = i
        }
        return records.drop(start).takeLast(40)
    }
}
