package com.puttvision.screen

import java.security.MessageDigest

object V33OnlineCourseSeed {
    fun fromMatchId(matchId: String): Long {
        val bytes = MessageDigest.getInstance("SHA-256").digest(matchId.toByteArray())
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) xor (bytes[i].toLong() and 0xffL)
        return value
    }

    fun forHole(matchSeed: Long, hole: Int): Int {
        val safeHole = hole.coerceAtLeast(1)
        val mixed = matchSeed xor (safeHole.toLong() * -7046029254386353131L)
        return (mixed xor (mixed ushr 32)).toInt()
    }
}
