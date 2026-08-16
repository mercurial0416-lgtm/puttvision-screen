package com.puttvision.screen

import java.security.MessageDigest

enum class V98HfrPublicationStatus {
    ACCEPTED,
    REJECTED_INTEGRITY
}

data class V98HfrPublicationDecision(
    val status: V98HfrPublicationStatus,
    /** Deterministic fingerprint of the exact track accepted at the publication boundary. */
    val provenanceFingerprint: String? = null
) {
    val accepted: Boolean get() = status == V98HfrPublicationStatus.ACCEPTED
}

/**
 * Stable, content-derived provenance token for accepted HFR geometry.
 * This does not claim source-video authenticity; it prevents downstream diagnostics/replay from
 * silently confusing two different compact tracks once a publication decision has been made.
 */
object V101HfrPublicationProvenance {
    fun fingerprint(track: HfrFeatureTrack): String {
        val canonical = buildString {
            append("fps=").append(track.fps)
            append("|impact=").append(track.impactFrame)
            append("|shape=").append(track.imageWidthPx ?: -1).append('x').append(track.imageHeightPx ?: -1)
            track.frames.forEach { frame ->
                append("|f=").append(frame.frame)
                append(',').append(frame.timeFromImpactMs.toBits())
                append(',').append(bits(frame.ballXcm)).append(',').append(bits(frame.ballYcm))
                append(',').append(bits(frame.heelXcm)).append(',').append(bits(frame.heelYcm))
                append(',').append(bits(frame.toeXcm)).append(',').append(bits(frame.toeYcm))
                append(',').append(bits(frame.markerAngleDeg))
                append(',').append(bits(frame.ballXpx)).append(',').append(bits(frame.ballYpx))
                append(',').append(bits(frame.heelXpx)).append(',').append(bits(frame.heelYpx))
                append(',').append(bits(frame.toeXpx)).append(',').append(bits(frame.toeYpx))
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun bits(value: Double?): String = value?.toBits()?.toString() ?: "null"
}

/**
 * Single fail-closed boundary between HFR analysis and runtime consumers.
 * A track rejected by V93 provenance validation must never be reported as a successful analysis.
 */
object V98HfrPublicationGate {
    fun publish(
        track: HfrFeatureTrack,
        nowMs: Long = System.currentTimeMillis()
    ): V98HfrPublicationDecision = synchronized(V41HfrFeatureTrackRuntime) {
        if (V41HfrFeatureTrackRuntime.publish(track, nowMs)) {
            // Runtime publication may compact long tracks around impact. Hash the exact compact track
            // while holding the same monitor as publish(), so provenance cannot drift to another shot.
            val publishedTrack = V41HfrFeatureTrackRuntime.latest
                ?: return@synchronized V98HfrPublicationDecision(V98HfrPublicationStatus.REJECTED_INTEGRITY)
            val fingerprint = V101HfrPublicationProvenance.fingerprint(publishedTrack)
            if (!V41HfrFeatureTrackRuntime.bindPublicationProvenance(publishedTrack, fingerprint)) {
                return@synchronized V98HfrPublicationDecision(V98HfrPublicationStatus.REJECTED_INTEGRITY)
            }
            V98HfrPublicationDecision(
                status = V98HfrPublicationStatus.ACCEPTED,
                provenanceFingerprint = fingerprint
            )
        } else {
            V98HfrPublicationDecision(V98HfrPublicationStatus.REJECTED_INTEGRITY)
        }
    }
}
