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
    private var stateVersion: Long = 0

    fun report(connected: Boolean, message: String) {
        if (lastConnected == connected && lastMessage == message) return
        val previousConnected = lastConnected
        val previousMessage = lastMessage
        val reportVersion = ++stateVersion
        lastConnected = connected
        lastMessage = message
        try {
            callback(connected, message)
        } catch (t: Throwable) {
            // A reentrant callback may have reported a newer status or reset the reporter. Do not
            // let the failed outer delivery roll that newer state back to a stale snapshot.
            if (stateVersion == reportVersion) {
                lastConnected = previousConnected
                lastMessage = previousMessage
                stateVersion++
            }
            throw t
        }
    }

    fun reset() {
        lastConnected = null
        lastMessage = null
        stateVersion++
    }
}
