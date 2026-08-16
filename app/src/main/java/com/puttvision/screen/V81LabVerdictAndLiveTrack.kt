package com.puttvision.screen

import kotlin.math.atan2

/** Unified synthetic-lab verdict. This is regression health, never a physical accuracy claim. */
data class V81LabVerdict(
    val passed: Boolean,
    val selfTestPassed: Int,
    val selfTestTotal: Int,
    val historySamples: Int,
    val historyFailures: Int,
    val consecutivePasses: Int,
    val soakPassed: Boolean?,
    val failedStage: String?
) {
    fun shortLabel(): String = if (passed) {
        "LAB PASS · $selfTestPassed/$selfTestTotal · HIST ${historySamples - historyFailures}/$historySamples"
    } else {
        "LAB FAIL · ${failedStage ?: "UNKNOWN"}"
    }

    fun diagnosticsText(): String = buildString {
        append("syntheticLab=${if (passed) "PASS" else "FAIL"}\n")
        append("selfTest=$selfTestPassed/$selfTestTotal\n")
        append("historySamples=$historySamples\n")
        append("historyFailures=$historyFailures\n")
        append("consecutivePasses=$consecutivePasses\n")
        append("soak=${soakPassed?.let { if (it) "PASS" else "FAIL" } ?: "NOT_RUN"}\n")
        append("failedStage=${failedStage ?: "NONE"}\n")
        append("claim=synthetic regression only; real-device calibration and ground-truth validation required")
    }
}

object V81LabVerdictEngine {
    fun snapshot(soak: V76HardwarelessSoakReport? = null): V81LabVerdict {
        val self = V72HardwarelessSelfTestRuntime.snapshot()
        val hist = V75HardwarelessSelfTestHistoryRuntime.summary()
        val passed = self?.passed == true && hist.failures == 0 && (soak?.passed != false)
        val stage = when {
            self == null -> "SELFTEST NOT RUN"
            !self.passed -> self.failedStage ?: "SELFTEST"
            hist.failures > 0 -> "HISTORY"
            soak?.passed == false -> soak.firstFailureStage ?: "SOAK"
            else -> null
        }
        return V81LabVerdict(
            passed = passed,
            selfTestPassed = self?.checksPassed ?: 0,
            selfTestTotal = self?.checksTotal ?: 0,
            historySamples = hist.samples,
            historyFailures = hist.failures,
            consecutivePasses = hist.consecutivePasses,
            soakPassed = soak?.passed,
            failedStage = stage
        )
    }
}

/** Lightweight overlay points for a PerfectLine-style immediate replay screen. */
data class V81LiveTrackPoint(val frame: Int, val tMs: Double, val x01: Double, val y01: Double)

data class V81LivePutterPose(
    val frame: Int,
    val tMs: Double,
    val centerX01: Double,
    val centerY01: Double,
    val faceAngleDeg: Double
)

data class V81LiveTrackOverlay(
    val ball: List<V81LiveTrackPoint>,
    val putter: List<V81LivePutterPose>,
    val impactFrame: Int,
    val fps: Int,
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val ready: Boolean,
    val reason: String
)

object V81LiveTrackProjector {
    fun from(track: HfrFeatureTrack): V81LiveTrackOverlay {
        val w = track.imageWidthPx ?: return empty(track, "source width missing")
        val h = track.imageHeightPx ?: return empty(track, "source height missing")
        if (w <= 0 || h <= 0) return empty(track, "source shape invalid")

        fun nx(x: Double) = (x / w.toDouble()).coerceIn(0.0, 1.0)
        fun ny(y: Double) = (y / h.toDouble()).coerceIn(0.0, 1.0)

        val ball = track.frames.mapNotNull { f ->
            val x = f.ballXpx ?: return@mapNotNull null
            val y = f.ballYpx ?: return@mapNotNull null
            if (!x.isFinite() || !y.isFinite()) return@mapNotNull null
            V81LiveTrackPoint(f.frame, f.timeFromImpactMs, nx(x), ny(y))
        }
        val putter = track.frames.mapNotNull { f ->
            val hx = f.heelXpx ?: return@mapNotNull null
            val hy = f.heelYpx ?: return@mapNotNull null
            val tx = f.toeXpx ?: return@mapNotNull null
            val ty = f.toeYpx ?: return@mapNotNull null
            if (listOf(hx, hy, tx, ty).any { !it.isFinite() }) return@mapNotNull null
            val angle = Math.toDegrees(atan2(ty - hy, tx - hx))
            V81LivePutterPose(
                frame = f.frame,
                tMs = f.timeFromImpactMs,
                centerX01 = nx((hx + tx) / 2.0),
                centerY01 = ny((hy + ty) / 2.0),
                faceAngleDeg = angle
            )
        }
        val ready = ball.size >= 3 && putter.size >= 2
        return V81LiveTrackOverlay(
            ball = ball,
            putter = putter,
            impactFrame = track.impactFrame,
            fps = track.fps,
            sourceWidthPx = w,
            sourceHeightPx = h,
            ready = ready,
            reason = if (ready) "overlay geometry ready" else "pixel correspondences insufficient"
        )
    }

    private fun empty(track: HfrFeatureTrack, reason: String) = V81LiveTrackOverlay(
        ball = emptyList(),
        putter = emptyList(),
        impactFrame = track.impactFrame,
        fps = track.fps,
        sourceWidthPx = track.imageWidthPx ?: 0,
        sourceHeightPx = track.imageHeightPx ?: 0,
        ready = false,
        reason = reason
    )
}
