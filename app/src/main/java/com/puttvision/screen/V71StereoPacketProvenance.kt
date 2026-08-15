package com.puttvision.screen

import kotlin.math.abs

/**
 * Binds companion packet metadata to the exact calibrated camera pair before raw tracks are handed
 * to V63. This closes the gap where cameraId/event metadata could be discarded and a structurally
 * valid track from another phone or another shot could be paired accidentally.
 *
 * The time thresholds are same-shot safety windows, not stereo accuracy claims.
 */
data class V71StereoPacketPolicy(
    val maxEventSkewMs: Long = 40L,
    val maxReceiveAgeMs: Long = 3_000L
) {
    fun valid(): Boolean = maxEventSkewMs in 0L..500L && maxReceiveAgeMs in 100L..20_000L
}

data class V71StereoPacketBindingResult(
    val bound: Boolean,
    val eventSkewMs: Long?,
    val reason: String
)

object V71StereoPacketProvenanceGate {
    fun evaluate(
        localPacket: V43FeatureTrackPacket?,
        remotePacket: V43FeatureTrackPacket?,
        currentFirst: V59CaptureSignature,
        currentSecond: V59CaptureSignature,
        expectedLocalView: V15CameraView,
        expectedRemoteView: V15CameraView,
        nowMs: Long,
        policy: V71StereoPacketPolicy = V71StereoPacketPolicy()
    ): V71StereoPacketBindingResult {
        if (!policy.valid()) return deny("packet provenance policy invalid")
        if (!currentFirst.valid() || !currentSecond.valid()) return deny("active capture signature invalid")
        val local = localPacket ?: return deny("local feature packet missing")
        val remote = remotePacket ?: return deny("remote feature packet missing")
        if (local.cameraId != currentFirst.cameraId) return deny("local camera id does not match active calibration")
        if (remote.cameraId != currentSecond.cameraId) return deny("remote camera id does not match active calibration")
        if (local.cameraId == remote.cameraId) return deny("stereo packets resolve to the same camera id")
        if (local.view != expectedLocalView) return deny("local camera view mismatch")
        if (remote.view != expectedRemoteView) return deny("remote camera view mismatch")
        if (local.sequence < 0L || remote.sequence < 0L) return deny("packet sequence invalid")
        if (!freshEvent(local.capturedAtMs, nowMs) || !freshEvent(remote.capturedAtMs, nowMs)) {
            return deny("shot event timestamp stale or future")
        }
        if (!freshReceive(local.receivedAtMs, nowMs, policy.maxReceiveAgeMs) ||
            !freshReceive(remote.receivedAtMs, nowMs, policy.maxReceiveAgeMs)
        ) {
            return deny("packet receive age stale or future")
        }
        val skew = abs(local.capturedAtMs - remote.capturedAtMs)
        if (skew > policy.maxEventSkewMs) return deny("packet shot-event skew exceeds same-shot window", skew)
        return V71StereoPacketBindingResult(
            bound = true,
            eventSkewMs = skew,
            reason = "camera identity, view and same-shot packet provenance bound"
        )
    }

    private fun freshEvent(eventMs: Long, nowMs: Long): Boolean =
        eventMs > 0L && nowMs - eventMs in -V50FeatureTrackWire.MAX_FUTURE_MS..V50FeatureTrackWire.MAX_EVENT_AGE_MS

    private fun freshReceive(receivedAtMs: Long, nowMs: Long, maxAgeMs: Long): Boolean =
        receivedAtMs > 0L && nowMs - receivedAtMs in -500L..maxAgeMs

    private fun deny(reason: String, skew: Long? = null) =
        V71StereoPacketBindingResult(false, skew, reason)
}

/**
 * Measurement-validation wrapper that preserves packet provenance all the way into V63.
 * Existing bare-track V63 stays available for deterministic geometry tests, while runtime packet
 * consumers can use this fail-closed entry point and cannot accidentally throw metadata away.
 */
object V71StereoPacketBallReconstructor {
    fun reconstruct(
        localPacket: V43FeatureTrackPacket?,
        remotePacket: V43FeatureTrackPacket?,
        localView: V15CameraView,
        remoteView: V15CameraView,
        profile: V59StereoCalibrationProfile?,
        currentFirst: V59CaptureSignature,
        currentSecond: V59CaptureSignature,
        activePairId: String,
        activeRigRevisionId: String,
        nowMs: Long,
        packetPolicy: V71StereoPacketPolicy = V71StereoPacketPolicy(),
        ballPolicy: V63StereoBallPolicy = V63StereoBallPolicy()
    ): V63StereoBallTrajectory {
        val binding = V71StereoPacketProvenanceGate.evaluate(
            localPacket = localPacket,
            remotePacket = remotePacket,
            currentFirst = currentFirst,
            currentSecond = currentSecond,
            expectedLocalView = localView,
            expectedRemoteView = remoteView,
            nowMs = nowMs,
            policy = packetPolicy
        )
        if (!binding.bound) {
            return V63StereoBallTrajectory(
                usableForMeasurementValidation = false,
                samples = emptyList(),
                horizontalSpeedMps = null,
                startDirectionDeg = null,
                verticalSpeedMps = null,
                verticalSpreadM = null,
                reason = "V71 gate: ${binding.reason}"
            )
        }
        return V63StereoBallTrajectoryReconstructor.reconstruct(
            localTrack = requireNotNull(localPacket).track,
            localView = localView,
            remoteTrack = requireNotNull(remotePacket).track,
            remoteView = remoteView,
            profile = profile,
            currentFirst = currentFirst,
            currentSecond = currentSecond,
            activePairId = activePairId,
            activeRigRevisionId = activeRigRevisionId,
            nowMs = nowMs,
            policy = ballPolicy
        )
    }
}

data class V71HardwarelessProvenanceSuiteResult(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val reason: String
) {
    fun shortLabel(): String = if (passed) {
        "PACKET BIND PASS · $checksPassed/$checksTotal"
    } else {
        "PACKET BIND FAIL · $checksPassed/$checksTotal · $reason"
    }
}

object V71HardwarelessProvenanceSuite {
    private const val NOW_MS = 500_000L

    fun verify(): V71HardwarelessProvenanceSuiteResult {
        val first = signature("primary-cam")
        val second = signature("face-cam")
        val local = packet("primary-cam", V15CameraView.PRIMARY, NOW_MS - 120L, NOW_MS - 30L, 10L)
        val remote = packet("face-cam", V15CameraView.FACE_ON, NOW_MS - 112L, NOW_MS - 25L, 11L)
        fun bound(l: V43FeatureTrackPacket? = local, r: V43FeatureTrackPacket? = remote) =
            V71StereoPacketProvenanceGate.evaluate(
                l, r, first, second, V15CameraView.PRIMARY, V15CameraView.FACE_ON, NOW_MS
            ).bound

        val checks = listOf(
            "baseline packet pair accepted" to bound(),
            "wrong local camera rejected" to !bound(local.copy(cameraId = "other-cam"), remote),
            "wrong remote camera rejected" to !bound(local, remote.copy(cameraId = "other-cam")),
            "wrong remote view rejected" to !bound(local, remote.copy(view = V15CameraView.PRIMARY)),
            "same camera id rejected" to !V71StereoPacketProvenanceGate.evaluate(
                local,
                remote.copy(cameraId = local.cameraId),
                first,
                signature(local.cameraId),
                V15CameraView.PRIMARY,
                V15CameraView.FACE_ON,
                NOW_MS
            ).bound,
            "different shot event rejected" to !bound(local, remote.copy(capturedAtMs = local.capturedAtMs + 100L)),
            "stale receive rejected" to !bound(local, remote.copy(receivedAtMs = NOW_MS - 3_001L)),
            "future event rejected" to !bound(local, remote.copy(capturedAtMs = NOW_MS + V50FeatureTrackWire.MAX_FUTURE_MS + 1L))
        )
        val passed = checks.count { it.second }
        val failure = checks.firstOrNull { !it.second }?.first
        return V71HardwarelessProvenanceSuiteResult(
            passed = passed == checks.size,
            checksPassed = passed,
            checksTotal = checks.size,
            reason = failure ?: "packet identity and same-shot provenance guards verified"
        )
    }

    private fun signature(cameraId: String) = V59CaptureSignature(
        cameraId = cameraId,
        widthPx = 1920,
        heightPx = 1080,
        fps = 240,
        sensorOrientationDeg = 90,
        lensFacing = "BACK",
        captureMode = "HFR"
    )

    private fun packet(
        cameraId: String,
        view: V15CameraView,
        capturedAtMs: Long,
        receivedAtMs: Long,
        sequence: Long
    ) = V43FeatureTrackPacket(
        cameraId = cameraId,
        view = view,
        capturedAtMs = capturedAtMs,
        sequence = sequence,
        track = HfrFeatureTrack(
            fps = 240,
            impactFrame = 0,
            frames = emptyList(),
            imageWidthPx = 1920,
            imageHeightPx = 1080
        ),
        receivedAtMs = receivedAtMs
    )
}

object V71HardwarelessProvenanceRuntime {
    @Volatile private var latest: V71HardwarelessProvenanceSuiteResult? = null

    fun run(): V71HardwarelessProvenanceSuiteResult =
        V71HardwarelessProvenanceSuite.verify().also { latest = it }

    fun snapshot(): V71HardwarelessProvenanceSuiteResult? = latest

    fun clear() {
        latest = null
    }
}
