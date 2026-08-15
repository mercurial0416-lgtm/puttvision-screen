package com.puttvision.screen

/**
 * Deterministic timing-distortion checks for the no-hardware lab.
 * Reuses production V44/V70 gates; it does not invent a second timing policy or claim sensor accuracy.
 */
data class V80TimingDistortionResult(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val reason: String
)

object V80HardwarelessTimingDistortion {
    private const val FPS = 240
    private const val EVENT_MS = 500_000L
    private const val NOW_MS = EVENT_MS + 300L

    fun verify(): V80TimingDistortionResult {
        val clean = track()
        val boundedJitter = track(jitterMs = doubleArrayOf(0.0, 1.5, -1.0, 2.0, -1.5, 1.0, 0.0))
        val excessiveJitter = track(jitterMs = doubleArrayOf(0.0, 0.0, 0.0, 9.0, 0.0, 0.0, 0.0))
        val dropped = track(frameIds = intArrayOf(10, 11, 13, 14, 15, 16, 17))
        val compressedDrop = track(
            frameIds = intArrayOf(10, 11, 14, 15, 16, 17, 18),
            overrideTimesMs = doubleArrayOf(0.0, frameMs(), frameMs() * 2, frameMs() * 3, frameMs() * 4, frameMs() * 5, frameMs() * 6)
        )
        val duplicateTime = track(overrideTimesMs = doubleArrayOf(0.0, frameMs(), frameMs(), frameMs() * 3, frameMs() * 4, frameMs() * 5, frameMs() * 6))

        val local = snapshot(clean)
        fun remote(
            eventAtMs: Long = EVENT_MS + 10L,
            receivedAtMs: Long = NOW_MS - 20L,
            uncertaintyMs: Long = 12L
        ) = V70RemoteTimingEvidence(
            cameraId = "hardwareless-remote",
            sequence = 1L,
            eventAtMs = eventAtMs,
            receivedAtMs = receivedAtMs,
            timeSource = "CAMERAX_START+FRAME",
            uncertaintyMs = uncertaintyMs,
            pixelTrack = true
        )

        val goodGate = V70StereoTimeQualityGate.evaluate(local, remote(), NOW_MS)
        val checks = listOf(
            "bounded frame jitter accepted" to (V44TrackValidator.normalize(boundedJitter, V15CameraView.FACE_ON) != null),
            "excessive frame jitter rejected" to (V44TrackValidator.normalize(excessiveJitter, V15CameraView.FACE_ON) == null),
            "single dropped frame with preserved timestamp accepted" to (V44TrackValidator.normalize(dropped, V15CameraView.FACE_ON) != null),
            "multi-frame compressed timeline rejected" to (V44TrackValidator.normalize(compressedDrop, V15CameraView.FACE_ON) == null),
            "duplicate/non-monotonic timestamp rejected" to (V44TrackValidator.normalize(duplicateTime, V15CameraView.FACE_ON) == null),
            "small physical-shot skew accepted" to goodGate.accepted,
            "high timestamp uncertainty rejected" to !V70StereoTimeQualityGate.evaluate(local, remote(uncertaintyMs = 81L), NOW_MS).accepted,
            "stale packet receive delay rejected" to !V70StereoTimeQualityGate.evaluate(local, remote(receivedAtMs = NOW_MS - 5_001L), NOW_MS).accepted,
            "large inter-camera shot skew rejected" to !V70StereoTimeQualityGate.evaluate(local, remote(eventAtMs = EVENT_MS + 221L), NOW_MS).accepted
        )
        val passed = checks.count { it.second }
        return V80TimingDistortionResult(
            passed = passed == checks.size,
            checksPassed = passed,
            checksTotal = checks.size,
            reason = checks.firstOrNull { !it.second }?.first ?: "jitter, frame-drop, receive-delay and shot-skew gates verified"
        )
    }

    private fun snapshot(track: HfrFeatureTrack) = HfrFeatureTrackSnapshot(
        track = track,
        publishedAtMs = EVENT_MS,
        storedAtMs = NOW_MS - 10L,
        timeSource = "CAMERAX_START+FRAME",
        timeUncertaintyMs = 12L
    )

    private fun frameMs() = 1000.0 / FPS

    private fun track(
        frameIds: IntArray = intArrayOf(10, 11, 12, 13, 14, 15, 16),
        jitterMs: DoubleArray = DoubleArray(7),
        overrideTimesMs: DoubleArray? = null
    ): HfrFeatureTrack {
        require(frameIds.size == 7 && jitterMs.size == 7 && (overrideTimesMs == null || overrideTimesMs.size == 7))
        val frames = frameIds.indices.map { i ->
            val frame = frameIds[i]
            val expected = (frame - 10) * frameMs()
            HfrFeatureFrame(
                frame = frame,
                timeFromImpactMs = overrideTimesMs?.get(i) ?: expected + jitterMs[i],
                ballXcm = 1.0 + i * 0.3,
                ballYcm = 15.0 + i * 0.7,
                heelXcm = null,
                heelYcm = null,
                toeXcm = null,
                toeYcm = null,
                markerAngleDeg = null,
                ballXpx = 900.0 + i * 2.0,
                ballYpx = 620.0 - i * 4.0
            )
        }
        return HfrFeatureTrack(FPS, 10, frames, 1920, 1080)
    }
}

object V80HardwarelessTimingDistortionRuntime {
    @Volatile private var latest: V80TimingDistortionResult? = null
    fun run(): V80TimingDistortionResult = V80HardwarelessTimingDistortion.verify().also { latest = it }
    fun snapshot(): V80TimingDistortionResult? = latest
    fun clear() { latest = null }
}
