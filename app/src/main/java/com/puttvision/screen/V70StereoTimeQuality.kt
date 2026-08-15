package com.puttvision.screen

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max

/**
 * Measurement-facing time-quality evidence for calibrated stereo.
 *
 * Pixel geometry can be perfect while two phones are still looking at different physical shots.
 * V50 intentionally falls back to publication time when the HFR capture epoch is unavailable; that
 * fallback is useful for diagnostics but far too uncertain for calibrated 3D. V70 keeps that
 * uncertainty explicit and fails closed before a pair can be promoted to measurement validation.
 */
data class V70RemoteTimingEvidence(
    val cameraId: String,
    val sequence: Long,
    val eventAtMs: Long,
    val receivedAtMs: Long,
    val timeSource: String,
    val uncertaintyMs: Long,
    val pixelTrack: Boolean
)

data class V70StereoTimePolicy(
    val maxTimestampUncertaintyMs: Long = 80L,
    val maxReceiveAgeMs: Long = 5_000L,
    val maxEventAgeMs: Long = V55PixelFeatureTrackWire.MAX_EVENT_AGE_MS,
    val fixedPairMarginMs: Long = 35L,
    val maxPairSkewMs: Long = 220L
) {
    fun valid(): Boolean =
        maxTimestampUncertaintyMs in 1L..1_000L &&
            maxReceiveAgeMs in 100L..30_000L &&
            maxEventAgeMs in 500L..30_000L &&
            fixedPairMarginMs in 0L..500L &&
            maxPairSkewMs in 20L..2_000L
}

data class V70StereoTimeGateResult(
    val accepted: Boolean,
    val shotSkewMs: Long?,
    val allowedSkewMs: Long?,
    val reason: String
)

object V70StereoTimeQualityGate {
    fun evaluate(
        local: HfrFeatureTrackSnapshot?,
        remote: V70RemoteTimingEvidence?,
        nowMs: Long = System.currentTimeMillis(),
        policy: V70StereoTimePolicy = V70StereoTimePolicy()
    ): V70StereoTimeGateResult {
        if (!policy.valid()) return deny("stereo time policy invalid")
        val l = local ?: return deny("local HFR timing evidence missing")
        val r = remote ?: return deny("remote pixel timing evidence missing")
        if (!r.pixelTrack) return deny("remote timing evidence is not pixel-track bound")
        if (!trustedSource(l.timeSource)) return deny("local HFR timestamp source is fallback/legacy")
        if (!trustedSource(r.timeSource)) return deny("remote HFR timestamp source is fallback/legacy")
        if (l.timeUncertaintyMs !in 0L..policy.maxTimestampUncertaintyMs) {
            return deny("local HFR timestamp uncertainty too high")
        }
        if (r.uncertaintyMs !in 0L..policy.maxTimestampUncertaintyMs) {
            return deny("remote HFR timestamp uncertainty too high")
        }
        if (l.publishedAtMs <= 0L || l.storedAtMs <= 0L || r.eventAtMs <= 0L || r.receivedAtMs <= 0L) {
            return deny("stereo event timestamp invalid")
        }
        val localReceiveAge = nowMs - l.storedAtMs
        val remoteReceiveAge = nowMs - r.receivedAtMs
        if (localReceiveAge !in 0L..policy.maxReceiveAgeMs) return deny("local HFR timing evidence stale")
        if (remoteReceiveAge !in 0L..policy.maxReceiveAgeMs) return deny("remote HFR timing evidence stale")
        val localEventAge = nowMs - l.publishedAtMs
        val remoteEventAge = nowMs - r.eventAtMs
        if (localEventAge !in -V50FeatureTrackWire.MAX_FUTURE_MS..policy.maxEventAgeMs) {
            return deny("local HFR event time implausible")
        }
        if (remoteEventAge !in -V50FeatureTrackWire.MAX_FUTURE_MS..policy.maxEventAgeMs) {
            return deny("remote HFR event time implausible")
        }

        val skew = abs(l.publishedAtMs - r.eventAtMs)
        val uncertaintyBudget = l.timeUncertaintyMs + r.uncertaintyMs + policy.fixedPairMarginMs
        val allowed = max(25L, uncertaintyBudget).coerceAtMost(policy.maxPairSkewMs)
        if (skew > allowed) return V70StereoTimeGateResult(false, skew, allowed, "physical-shot time skew too large")
        return V70StereoTimeGateResult(
            true,
            skew,
            allowed,
            "stereo timing is fresh, non-fallback, and uncertainty-bounded; real-device validation still required"
        )
    }

    private fun trustedSource(source: String): Boolean {
        val normalized = source.trim().uppercase()
        if (normalized.isEmpty()) return false
        return normalized != "NONE" && normalized != "LEGACY" && !normalized.contains("FALLBACK")
    }

    private fun deny(reason: String) = V70StereoTimeGateResult(false, null, null, reason)
}

/** Latest remote timing evidence, kept separate from legacy planar readiness. */
object V70RemoteTimingRuntime {
    private val latest = ConcurrentHashMap<String, V70RemoteTimingEvidence>()

    fun publish(evidence: V70RemoteTimingEvidence) {
        if (evidence.cameraId.isBlank() || evidence.sequence < 0L) return
        latest.compute(evidence.cameraId) { _, previous ->
            if (previous == null || evidence.sequence > previous.sequence ||
                (evidence.sequence == previous.sequence && evidence.receivedAtMs > previous.receivedAtMs)
            ) evidence else previous
        }
    }

    fun latest(cameraId: String, nowMs: Long = System.currentTimeMillis(), maxAgeMs: Long = 5_000L): V70RemoteTimingEvidence? {
        val evidence = latest[cameraId] ?: return null
        val age = nowMs - evidence.receivedAtMs
        if (age !in 0L..maxAgeMs) return null
        return evidence
    }

    fun clear() = latest.clear()
}
