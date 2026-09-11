package com.puttvision.screen

/**
 * Suppresses identical external-display status callbacks.
 *
 * Samsung/DeX can emit bursts of display-changed callbacks while the physical TV state is
 * unchanged. Keeping this tiny gate outside the Android controller makes the behavior easy to
 * verify and avoids rebuilding the same phone HUD status repeatedly during those bursts.
 */
class DistinctDisplayStatusReporter(
    private val callback: (Boolean, String) -> Unit
) {
    private var lastConnected: Boolean? = null
    private var lastMessage: String? = null

    fun report(connected: Boolean, message: String) {
        if (lastConnected == connected && lastMessage == message) return
        lastConnected = connected
        lastMessage = message
        callback(connected, message)
    }

    fun reset() {
        lastConnected = null
        lastMessage = null
    }
}
