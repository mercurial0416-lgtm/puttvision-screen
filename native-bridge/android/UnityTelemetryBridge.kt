package com.puttvision.bridge

/**
 * Keeps the shot pipeline independent from Unity classes. The Android host wires [sender]
 * to UnityPlayer.UnitySendMessage only in the integration module that actually depends on
 * the exported unityLibrary.
 */
class UnityTelemetryBridge(
    private val sender: UnityMessageSender,
) {
    fun publish(telemetry: PuttTelemetry) {
        require(telemetry.isSimulationReady()) {
            "PuttTelemetry is not valid for simulation: ${telemetry.shotId}"
        }

        sender.send(
            RECEIVER_GAME_OBJECT,
            RECEIVER_METHOD,
            telemetry.toJson(),
        )
    }

    fun interface UnityMessageSender {
        fun send(gameObject: String, method: String, payload: String)
    }

    companion object {
        const val RECEIVER_GAME_OBJECT = "PuttTelemetryReceiver"
        const val RECEIVER_METHOD = "OnTelemetryJson"
    }
}
