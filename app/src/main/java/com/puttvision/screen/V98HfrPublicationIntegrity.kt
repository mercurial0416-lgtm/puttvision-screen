package com.puttvision.screen

enum class V98HfrPublicationStatus {
    ACCEPTED,
    REJECTED_INTEGRITY
}

data class V98HfrPublicationDecision(
    val status: V98HfrPublicationStatus
) {
    val accepted: Boolean get() = status == V98HfrPublicationStatus.ACCEPTED
}

/**
 * Single fail-closed boundary between HFR analysis and runtime consumers.
 * A track rejected by V93 provenance validation must never be reported as a successful analysis.
 */
object V98HfrPublicationGate {
    fun publish(
        track: HfrFeatureTrack,
        nowMs: Long = System.currentTimeMillis()
    ): V98HfrPublicationDecision =
        if (V41HfrFeatureTrackRuntime.publish(track, nowMs)) {
            V98HfrPublicationDecision(V98HfrPublicationStatus.ACCEPTED)
        } else {
            V98HfrPublicationDecision(V98HfrPublicationStatus.REJECTED_INTEGRITY)
        }
}
