package com.puttvision.screen

/**
 * Product-facing bridge between the compact HFR feature track and ImpactReplayView.
 *
 * V81 projects raw detector pixels into normalized image coordinates, V84 owns timeline
 * clamping/scrub semantics, and V83 plans the render model. The replay view only needs to
 * supply its current extracted-frame index and impact-frame index.
 */
data class V85ImpactReplayLiveTrackBinding(
    val overlay: V81LiveTrackOverlay,
    val playback: V84ReplayState,
    val fps: Int
)

object V85ImpactReplayLiveTrack {
    fun bind(track: HfrFeatureTrack?): V85ImpactReplayLiveTrackBinding? {
        val source = track ?: return null
        if (source.fps <= 0) return null
        val overlay = V81LiveTrackProjector.from(source)
        if (!overlay.ready) return null
        val playback = V84LiveTrackPlayback.initial(overlay) ?: return null
        return V85ImpactReplayLiveTrackBinding(
            overlay = overlay,
            playback = playback,
            fps = source.fps
        )
    }

    fun modelAtReplayFrame(
        binding: V85ImpactReplayLiveTrackBinding?,
        replayFrame: Int,
        replayImpactIndex: Int
    ): V83LiveTrackRenderModel? {
        val b = binding ?: return null
        if (replayFrame < 0 || replayImpactIndex < 0 || b.fps <= 0) return null
        val playheadMs = (replayFrame - replayImpactIndex) * 1000.0 / b.fps.toDouble()
        val scrubbed = V84LiveTrackPlayback.scrub(b.playback, playheadMs)
        return V83LiveTrackRenderPlanner.plan(b.overlay, scrubbed.playheadMs)
            .takeIf { it.ready }
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
                HfrFeatureFrame(99, -1000.0 / 240.0, -1.0, 0.0, -1.0, 0.0, 1.0, 0.0, null, 450.0, 260.0, 400.0, 320.0, 500.0, 320.0),
                HfrFeatureFrame(100, 0.0, 0.0, 0.0, -1.0, 0.0, 1.0, 0.0, null, 470.0, 250.0, 420.0, 310.0, 520.0, 310.0),
                HfrFeatureFrame(101, 1000.0 / 240.0, 1.0, 0.0, -1.0, 0.0, 1.0, 0.0, null, 500.0, 235.0, 445.0, 300.0, 545.0, 300.0)
            )
        )
        val binding = V85ImpactReplayLiveTrack.bind(track)
        val before = V85ImpactReplayLiveTrack.modelAtReplayFrame(binding, replayFrame = 9, replayImpactIndex = 10)
        val impact = V85ImpactReplayLiveTrack.modelAtReplayFrame(binding, replayFrame = 10, replayImpactIndex = 10)
        val after = V85ImpactReplayLiveTrack.modelAtReplayFrame(binding, replayFrame = 11, replayImpactIndex = 10)
        val missingShape = V85ImpactReplayLiveTrack.bind(track.copy(imageWidthPx = null))
        val checks = listOf(
            "valid HFR track binds to product replay" to (binding != null),
            "pre-impact replay stays pre-impact" to (before?.impactReached == false),
            "impact replay frame aligns to zero milliseconds" to (impact?.impactReached == true),
            "post-impact ball trail grows" to (impact != null && after != null && after.ballTrail.size >= impact.ballTrail.size),
            "V83 image-face label survives product bridge" to (impact?.imageFaceLabel?.startsWith("IMAGE FACE") == true),
            "missing source shape fails closed" to (missingShape == null),
            "invalid replay index fails closed" to (V85ImpactReplayLiveTrack.modelAtReplayFrame(binding, -1, 10) == null)
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
