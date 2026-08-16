package com.puttvision.screen

/**
 * Product-facing bridge between the compact HFR feature track and ImpactReplayView.
 *
 * V81 projects raw detector pixels into normalized image coordinates, V84 owns timeline
 * clamping/scrub semantics, and V83 plans the render model. Product replay should supply
 * an exact playhead derived from the extracted frame's original source-frame index.
 */
data class V85ImpactReplayLiveTrackBinding(
    val overlay: V81LiveTrackOverlay,
    val playback: V84ReplayState,
    val fps: Int
)

object V85ImpactReplayLiveTrack {
    fun bind(track: HfrFeatureTrack?): V85ImpactReplayLiveTrackBinding? {
        val source = track ?: return null
        // Replay is user-facing evidence of a measured stroke. Never normalize/clamp malformed
        // detector provenance into apparently valid geometry; share the same fail-closed gate used
        // by the HFR runtime before projecting pixels into display coordinates.
        if (!V93HfrFeatureTrackIntegrity.isValid(source)) return null
        val overlay = V81LiveTrackProjector.from(source)
        if (!overlay.ready) return null
        val playback = V84LiveTrackPlayback.initial(overlay) ?: return null
        return V85ImpactReplayLiveTrackBinding(
            overlay = overlay,
            playback = playback,
            fps = source.fps
        )
    }

    fun modelAtPlayheadMs(
        binding: V85ImpactReplayLiveTrackBinding?,
        playheadMs: Double?
    ): V83LiveTrackRenderModel? {
        val b = binding ?: return null
        val timeMs = playheadMs ?: return null
        if (!timeMs.isFinite()) return null
        val scrubbed = V84LiveTrackPlayback.scrub(b.playback, timeMs)
        return V83LiveTrackRenderPlanner.plan(b.overlay, scrubbed.playheadMs)
            .takeIf { it.ready }
    }

    /**
     * Index-based fallback retained for callers that do not have source-frame provenance.
     * Product replay should prefer modelAtPlayheadMs with ImpactReplay.relativeTimeMsAt().
     */
    fun modelAtReplayFrame(
        binding: V85ImpactReplayLiveTrackBinding?,
        replayFrame: Int,
        replayImpactIndex: Int
    ): V83LiveTrackRenderModel? {
        val b = binding ?: return null
        if (replayFrame < 0 || replayImpactIndex < 0 || b.fps <= 0) return null
        val playheadMs = (replayFrame - replayImpactIndex) * 1000.0 / b.fps.toDouble()
        return modelAtPlayheadMs(b, playheadMs)
    }
}

data class V85ImpactReplayLiveTrackSuiteResult(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val reason: String
)

object V85HardwarelessImpactReplayLiveTrackSuite {
    fun verify(): V85ImpactReplayLiveTrackSuiteResult {
        val track = HfrFeatureTrack(
            fps = 240,
            impactFrame = 100,
            imageWidthPx = 1000,
            imageHeightPx = 500,
            frames = listOf(
                HfrFeatureFrame(98, -2000.0 / 240.0, -2.0, 0.0, -1.0, 0.0, 1.0, 0.0, null, 430.0, 270.0, 380.0, 330.0, 480.0, 330.0),
                HfrFeatureFrame(99, -1000.0 / 240.0, -1.0, 0.0, -1.0, 0.0, 1.0, 0.0, null, 450.0, 260.0, 400.0, 320.0, 500.0, 320.0),
                HfrFeatureFrame(100, 0.0, 0.0, 0.0, -1.0, 0.0, 1.0, 0.0, null, 470.0, 250.0, 420.0, 310.0, 520.0, 310.0),
                HfrFeatureFrame(101, 1000.0 / 240.0, 1.0, 0.0, -1.0, 0.0, 1.0, 0.0, null, 500.0, 235.0, 445.0, 300.0, 545.0, 300.0),
                HfrFeatureFrame(102, 2000.0 / 240.0, 2.0, 0.0, -1.0, 0.0, 1.0, 0.0, null, 530.0, 220.0, 470.0, 290.0, 570.0, 290.0)
            )
        )
        val binding = V85ImpactReplayLiveTrack.bind(track)
        val before = V85ImpactReplayLiveTrack.modelAtReplayFrame(binding, replayFrame = 9, replayImpactIndex = 10)
        val impact = V85ImpactReplayLiveTrack.modelAtReplayFrame(binding, replayFrame = 10, replayImpactIndex = 10)
        val after = V85ImpactReplayLiveTrack.modelAtReplayFrame(binding, replayFrame = 11, replayImpactIndex = 10)
        val strideAfter = V85ImpactReplayLiveTrack.modelAtPlayheadMs(binding, 2000.0 / 240.0)
        val missingShape = V85ImpactReplayLiveTrack.bind(track.copy(imageWidthPx = null))
        val outOfBounds = V85ImpactReplayLiveTrack.bind(
            track.copy(frames = track.frames.mapIndexed { index, frame ->
                if (index == 2) frame.copy(ballXpx = 1001.0) else frame
            })
        )
        val invalidTiming = V85ImpactReplayLiveTrack.bind(
            track.copy(frames = track.frames.mapIndexed { index, frame ->
                if (index == 3) frame.copy(timeFromImpactMs = 99.0) else frame
            })
        )
        val checks = listOf(
            "valid HFR track binds to product replay" to (binding != null),
            "pre-impact replay stays pre-impact" to (before?.impactReached == false),
            "impact replay frame aligns to zero milliseconds" to (impact?.impactReached == true),
            "post-impact ball trail grows" to (impact != null && after != null && after.ballTrail.size >= impact.ballTrail.size),
            "source-frame playhead survives replay stride" to (after != null && strideAfter != null && strideAfter.ballTrail.size >= after.ballTrail.size),
            "V83 image-face label survives product bridge" to (impact?.imageFaceLabel?.startsWith("IMAGE FACE") == true),
            "missing source shape fails closed" to (missingShape == null),
            "out-of-frame detector provenance fails closed before normalization" to (outOfBounds == null),
            "inconsistent source-frame timing fails closed" to (invalidTiming == null),
            "invalid replay index fails closed" to (V85ImpactReplayLiveTrack.modelAtReplayFrame(binding, -1, 10) == null),
            "invalid playhead fails closed" to (V85ImpactReplayLiveTrack.modelAtPlayheadMs(binding, Double.NaN) == null)
        )
        val passed = checks.count { it.second }
        return V85ImpactReplayLiveTrackSuiteResult(
            passed = passed == checks.size,
            checksPassed = passed,
            checksTotal = checks.size,
            reason = checks.firstOrNull { !it.second }?.first ?: "impact replay live-track bridge verified"
        )
    }
}

object V85HardwarelessImpactReplayLiveTrackRuntime {
    @Volatile private var latest: V85ImpactReplayLiveTrackSuiteResult? = null
    fun run(): V85ImpactReplayLiveTrackSuiteResult = V85HardwarelessImpactReplayLiveTrackSuite.verify().also { latest = it }
    fun snapshot(): V85ImpactReplayLiveTrackSuiteResult? = latest
    fun clear() { latest = null }
}
