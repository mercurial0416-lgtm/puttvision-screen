package com.puttvision.screen

import android.content.Context

/**
 * Temporary compile-time bridge while the product runs in solo-only mode.
 * These symbols deliberately perform no networking and expose no online UI.
 * They can be removed when the remaining call sites are refactored, or replaced
 * from Git history if online multiplayer is brought back for a commercial build.
 */
object V33OnlineOutbox {
    fun install(context: Context) = Unit
    fun pendingCount(): Int = 0
    fun activeMatchIdOrNull(): String? = null
    fun onlineSeedOrNull(): Long? = null
    fun refreshSession() = Unit
    fun onRecord(record: ShotRecord) = Unit
    fun flush() = Unit
}
