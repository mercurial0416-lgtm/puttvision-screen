package com.puttvision.screen

/**
 * Hardwareless integration check for the local companion transport/time path.
 *
 * This deliberately uses the production V64 encoder/decoder and V43/V50 sequence/time policies.
 * It does not open a socket: it proves that the bytes the companion would send survive the exact
 * codec and freshness gates before a real second phone is available.
 */
data class V70TransportTimebaseResult(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val reason: String
) {
    fun shortLabel(): String = if (passed) {
        "LAN/TIME PASS · $checksPassed/$checksTotal"
    } else {
        "LAN/TIME FAIL · $checksPassed/$checksTotal · $reason"
    }
}

object V70HardwarelessTransportTimebase {
    private const val CODE = "482917"
    private const val CAMERA_ID = "hardwareless-companion"
    private const val EVENT_MS = 200_000L
    private const val NOW_MS = EVENT_MS + 350L

    fun verify(): V70TransportTimebaseResult {
        val track = validPixelTrack()
        val lines = runCatching {
            V64PixelTrackTransport.encodeLines(
                code = CODE,
                cameraId = CAMERA_ID,
                view = V15CameraView.FACE_ON,
                track = track,
                capturedAtMs = EVENT_MS,
                legacySequence = 40L,
                pixelSequence = 41L
            )
        }.getOrElse {
            return V70TransportTimebaseResult(false, 0, 10, "transport encode exception")
        }
        val legacy = lines.firstOrNull { !it.pixelTrack }
        val pixel = lines.firstOrNull { it.pixelTrack }
        val decodedLegacy = legacy?.let { V64PixelTrackTransport.decodeLine(it.raw, CODE, NOW_MS) }
        val decodedPixel = pixel?.let { V64PixelTrackTransport.decodeLine(it.raw, CODE, NOW_MS) }

        val sequenceGate = V43CompanionSequenceGate()
        val seqFirst = sequenceGate.accept(CAMERA_ID, 41L)
        val seqDuplicateRejected = !sequenceGate.accept(CAMERA_ID, 41L)
        val seqOldRejected = !sequenceGate.accept(CAMERA_ID, 40L)
        val seqNewAccepted = sequenceGate.accept(CAMERA_ID, 42L)

        val checks = listOf(
            "legacy and pixel lines emitted" to (lines.size == 2 && legacy != null && pixel != null),
            "legacy line decodes" to (decodedLegacy?.pixelTrack == false && decodedLegacy.packet.cameraId == CAMERA_ID),
            "pixel line decodes with raw geometry" to (decodedPixel?.pixelTrack == true && decodedPixel.packet.track.frames.any { it.ballXpx != null && it.ballYpx != null }),
            "wrong pairing code rejected" to (pixel != null && V64PixelTrackTransport.decodeLine(pixel.raw, "000000", NOW_MS) == null),
            "stale event rejected" to (pixel != null && V64PixelTrackTransport.decodeLine(pixel.raw, CODE, EVENT_MS + V50FeatureTrackWire.MAX_EVENT_AGE_MS + 1L) == null),
            "future event rejected" to (pixel != null && V64PixelTrackTransport.decodeLine(pixel.raw, CODE, EVENT_MS - V50FeatureTrackWire.MAX_FUTURE_MS - 1L) == null),
            "sequence baseline accepted" to seqFirst,
            "duplicate sequence rejected" to seqDuplicateRejected,
            "older sequence rejected and newer accepted" to (seqOldRejected && seqNewAccepted),
            "event time policy keeps short skew bounded" to (
                V50StereoTimePolicy.usableImpactTimestamp(EVENT_MS, NOW_MS) &&
                    V50StereoTimePolicy.skewMs(EVENT_MS, EVENT_MS + 7L) == 7L &&
                    !V50StereoTimePolicy.usableImpactTimestamp(EVENT_MS, EVENT_MS + 20_001L)
                )
        )
        val passed = checks.count { it.second }
        val failure = checks.firstOrNull { !it.second }?.first
        return V70TransportTimebaseResult(
            passed = passed == checks.size,
            checksPassed = passed,
            checksTotal = checks.size,
            reason = failure ?: "transport codec, freshness and sequence guards verified"
        )
    }

    private fun validPixelTrack(): HfrFeatureTrack {
        val impact = 10
        val frames = (0 until 7).map { i ->
            HfrFeatureFrame(
                frame = impact + i,
                timeFromImpactMs = i * (1000.0 / 240.0),
                ballXcm = 1.0 + i * 0.35,
                ballYcm = 16.0 + i * 0.70,
                heelXcm = null,
                heelYcm = null,
                toeXcm = null,
                toeYcm = null,
                markerAngleDeg = null,
                ballXpx = 920.0 + i * 2.2,
                ballYpx = 610.0 - i * 4.4
            )
        }
        return HfrFeatureTrack(
            fps = 240,
            impactFrame = impact,
            frames = frames,
            imageWidthPx = 1920,
            imageHeightPx = 1080
        )
    }
}

object V70HardwarelessTransportTimebaseRuntime {
    @Volatile private var latest: V70TransportTimebaseResult? = null

    fun run(): V70TransportTimebaseResult =
        V70HardwarelessTransportTimebase.verify().also { latest = it }

    fun snapshot(): V70TransportTimebaseResult? = latest

    fun clear() {
        latest = null
    }
}
