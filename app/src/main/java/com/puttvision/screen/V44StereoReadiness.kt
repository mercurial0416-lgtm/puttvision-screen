package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min


data class V44TrackValidation(
    val valid: Boolean,
    val reason: String,
    val normalized: HfrFeatureTrack? = null
)

/**
 * Boundary validation for raw feature geometry before it can participate in stereo preparation.
 * This does not claim 3D accuracy: it only makes sure both camera tracks are structurally sane.
 */
object V44TrackValidator {
    const val MIN_FPS = 60
    const val MAX_FPS = 480
    const val MIN_FRAMES = 4
    const val MAX_FRAMES = 32
    const val MAX_COORD_CM = 500.0
    const val MAX_RELATIVE_TIME_MS = 250.0

    fun inspect(track: HfrFeatureTrack, view: V15CameraView): V44TrackValidation {
        if (view == V15CameraView.PRIMARY) return V44TrackValidation(false, "보조폰 view가 PRIMARY")
        if (track.fps !in MIN_FPS..MAX_FPS) return V44TrackValidation(false, "비정상 FPS ${track.fps}")
        if (track.impactFrame < 0) return V44TrackValidation(false, "비정상 impact frame")
        if (track.frames.size !in MIN_FRAMES..MAX_FRAMES) {
            return V44TrackValidation(false, "프레임 수 ${track.frames.size}")
        }

        val sorted = track.frames.sortedBy { it.frame }
        if (sorted.map { it.frame }.distinct().size != sorted.size) {
            return V44TrackValidation(false, "중복 frame id")
        }
        if (sorted.first().frame < 0 || track.impactFrame !in sorted.first().frame..sorted.last().frame) {
            return V44TrackValidation(false, "impact frame이 track 범위 밖")
        }

        val frameMs = 1000.0 / track.fps
        var previousTime = Double.NEGATIVE_INFINITY
        for (frame in sorted) {
            if (!frame.timeFromImpactMs.isFinite() || abs(frame.timeFromImpactMs) > MAX_RELATIVE_TIME_MS) {
                return V44TrackValidation(false, "비정상 상대시간")
            }
            if (frame.timeFromImpactMs <= previousTime) {
                return V44TrackValidation(false, "시간축 역전/중복")
            }
            previousTime = frame.timeFromImpactMs

            val expectedTime = (frame.frame - track.impactFrame) * frameMs
            val timingTolerance = max(3.0, frameMs * 1.55)
            if (abs(frame.timeFromImpactMs - expectedTime) > timingTolerance) {
                return V44TrackValidation(false, "frame/time 불일치")
            }

            if (!pairValid(frame.ballXcm, frame.ballYcm)) return V44TrackValidation(false, "BALL 좌표 불완전")
            val putterValues = listOf(frame.heelXcm, frame.heelYcm, frame.toeXcm, frame.toeYcm)
            val putterPresent = putterValues.count { it != null }
            if (putterPresent != 0 && putterPresent != 4) return V44TrackValidation(false, "PUTTER 좌표 불완전")
            if (putterValues.filterNotNull().any { !coordinateValid(it) }) return V44TrackValidation(false, "PUTTER 좌표 범위 초과")
            if (frame.ballXcm?.let { !coordinateValid(it) } == true || frame.ballYcm?.let { !coordinateValid(it) } == true) {
                return V44TrackValidation(false, "BALL 좌표 범위 초과")
            }
            if (frame.markerAngleDeg?.let { !it.isFinite() || abs(it) > 360.0 } == true) {
                return V44TrackValidation(false, "marker angle 비정상")
            }
        }

        if (sorted.count { it.ballXcm != null && it.ballYcm != null } < 3) {
            return V44TrackValidation(false, "BALL track 부족")
        }

        return V44TrackValidation(true, "OK", track.copy(frames = sorted))
    }

    fun normalize(track: HfrFeatureTrack, view: V15CameraView): HfrFeatureTrack? =
        inspect(track, view).normalized

    private fun pairValid(x: Double?, y: Double?): Boolean {
        if ((x == null) != (y == null)) return false
        return x == null || (coordinateValid(x) && coordinateValid(requireNotNull(y)))
    }

    private fun coordinateValid(value: Double): Boolean = value.isFinite() && abs(value) <= MAX_COORD_CM
}


data class V44MatchedFrame(
    val local: HfrFeatureFrame,
    val remote: HfrFeatureFrame,
    val deltaMs: Double,
    val ballDeltaCm: Double?,
    val putterCenterDeltaCm: Double?
)

object V44StereoMatcher {
    fun match(local: HfrFeatureTrack, remote: HfrFeatureTrack): List<V44MatchedFrame> {
        val maxDeltaMs = min(12.0, max(6.0, 1000.0 / min(local.fps, remote.fps) * 1.35))
        val available = remote.frames.toMutableList()
        val result = ArrayList<V44MatchedFrame>()
        for (lf in local.frames.sortedBy { it.timeFromImpactMs }) {
            val candidate = available.minByOrNull { abs(it.timeFromImpactMs - lf.timeFromImpactMs) } ?: continue
            val delta = abs(candidate.timeFromImpactMs - lf.timeFromImpactMs)
            if (delta > maxDeltaMs) continue
            available.remove(candidate)
            result += V44MatchedFrame(
                local = lf,
                remote = candidate,
                deltaMs = delta,
                ballDeltaCm = distance(lf.ballXcm, lf.ballYcm, candidate.ballXcm, candidate.ballYcm),
                putterCenterDeltaCm = putterCenterDistance(lf, candidate)
            )
        }
        return result
    }

    private fun distance(ax: Double?, ay: Double?, bx: Double?, by: Double?): Double? {
        if (ax == null || ay == null || bx == null || by == null) return null
        return hypot(ax - bx, ay - by)
    }

    private fun putterCenterDistance(a: HfrFeatureFrame, b: HfrFeatureFrame): Double? {
        val ahx = a.heelXcm ?: return null
        val ahy = a.heelYcm ?: return null
        val atx = a.toeXcm ?: return null
        val aty = a.toeYcm ?: return null
        val bhx = b.heelXcm ?: return null
        val bhy = b.heelYcm ?: return null
        val btx = b.toeXcm ?: return null
        val bty = b.toeYcm ?: return null
        return hypot((ahx + atx) / 2.0 - (bhx + btx) / 2.0, (ahy + aty) / 2.0 - (bhy + bty) / 2.0)
    }
}


data class V44StereoReadiness(
    val ready: Boolean,
    val cameraId: String?,
    val view: V15CameraView?,
    val score: Int,
    val shotSkewMs: Long?,
    val matchedFrames: Int,
    val ballPairs: Int,
    val putterPairs: Int,
    val medianTimeDeltaMs: Double?,
    val medianBallDeltaCm: Double?,
    val reason: String
) {
    val shortLabel: String
        get() = when {
            ready -> "TRACK READY · ${cameraId ?: "CAM"} · $score/100"
            cameraId == null -> "TRACK WAIT · $reason"
            else -> "TRACK CHECK · $reason"
        }

    val detail: String
        get() = buildString {
            append(shortLabel)
            if (cameraId != null) append("\n카메라 $cameraId · ${view?.name ?: "--"}")
            if (shotSkewMs != null) append("\n샷 시간차 ${shotSkewMs}ms")
            append("\n매칭 $matchedFrames · BALL $ballPairs · PUTTER $putterPairs")
            medianTimeDeltaMs?.let { append("\n프레임 시간 오차 중앙값 ${"%.2f".format(it)}ms") }
            medianBallDeltaCm?.let { append(" · 평면 BALL 차이 ${"%.2f".format(it)}cm") }
            append("\n\n※ TRACK READY는 두 카메라의 시간/평면 궤적이 3D 계산 입력으로 쓸 만하다는 뜻입니다. 실제 3D triangulation 전에는 카메라 intrinsic/extrinsic 보정이 추가로 필요합니다.")
        }
}

object V44StereoReadinessEngine {
    const val MAX_SHOT_SKEW_MS = 1_800L
    const val MIN_MATCHED_FRAMES = 6
    const val MIN_BALL_PAIRS = 5
    const val MIN_PUTTER_PAIRS = 3
    const val MAX_MEDIAN_TIME_DELTA_MS = 6.5
    const val MAX_MEDIAN_BALL_DELTA_CM = 4.0

    fun best(
        local: HfrFeatureTrackSnapshot?,
        remotePackets: List<V43FeatureTrackPacket>,
        nowMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = 10_000L
    ): V44StereoReadiness {
        val localSnapshot = local ?: return empty("메인폰 HFR track 없음")
        if (localSnapshot.publishedAtMs <= 0L || nowMs - localSnapshot.publishedAtMs !in 0L..maxAgeMs) {
            return empty("메인폰 track 오래됨")
        }
        val localTrack = normalizeLocal(localSnapshot.track) ?: return empty("메인폰 track 품질 부족")
        val candidates = remotePackets.mapNotNull { packet -> candidate(localSnapshot, localTrack, packet, nowMs, maxAgeMs) }
        return candidates.maxWithOrNull(compareBy<V44StereoReadiness> { it.ready }.thenBy { it.score }.thenBy { it.matchedFrames })
            ?: empty("보조폰 HFR track 없음")
    }

    private fun candidate(
        localSnapshot: HfrFeatureTrackSnapshot,
        localTrack: HfrFeatureTrack,
        packet: V43FeatureTrackPacket,
        nowMs: Long,
        maxAgeMs: Long
    ): V44StereoReadiness? {
        val remoteTrack = V44TrackValidator.normalize(packet.track, packet.view) ?: return null
        val age = nowMs - packet.capturedAtMs
        if (age !in 0L..maxAgeMs) return null
        val skew = abs(packet.capturedAtMs - localSnapshot.publishedAtMs)
        val matches = V44StereoMatcher.match(localTrack, remoteTrack)
        val ball = matches.mapNotNull { it.ballDeltaCm }
        val putter = matches.mapNotNull { it.putterCenterDeltaCm }
        val medianTime = median(matches.map { it.deltaMs })
        val medianBall = median(ball)

        val reason = when {
            skew > MAX_SHOT_SKEW_MS -> "같은 샷 시간대 아님"
            matches.size < MIN_MATCHED_FRAMES -> "시간축 겹침 부족"
            ball.size < MIN_BALL_PAIRS -> "BALL 대응점 부족"
            putter.size < MIN_PUTTER_PAIRS -> "PUTTER 대응점 부족"
            medianTime == null || medianTime > MAX_MEDIAN_TIME_DELTA_MS -> "프레임 동기 오차 큼"
            medianBall == null || medianBall > MAX_MEDIAN_BALL_DELTA_CM -> "카메라 평면 보정 불일치"
            else -> "extrinsic 보정 후 triangulation 가능"
        }
        val ready = reason.startsWith("extrinsic")

        val timeScore = medianTime?.let { (25.0 * (1.0 - it / 12.0)).coerceIn(0.0, 25.0) } ?: 0.0
        val ballCoverage = (ball.size / 10.0).coerceIn(0.0, 1.0) * 25.0
        val putterCoverage = (putter.size / 8.0).coerceIn(0.0, 1.0) * 20.0
        val agreement = medianBall?.let { (20.0 * (1.0 - it / 8.0)).coerceIn(0.0, 20.0) } ?: 0.0
        val freshness = (10.0 * (1.0 - age.toDouble() / maxAgeMs.coerceAtLeast(1L))).coerceIn(0.0, 10.0)
        val viewBonus = when (packet.view) {
            V15CameraView.TOP -> 1.0
            V15CameraView.FACE_ON -> .98
            V15CameraView.DOWN_THE_LINE -> .96
            V15CameraView.PRIMARY -> .0
        }
        val score = ((timeScore + ballCoverage + putterCoverage + agreement + freshness) * viewBonus).toInt().coerceIn(0, 100)

        return V44StereoReadiness(
            ready = ready,
            cameraId = packet.cameraId,
            view = packet.view,
            score = score,
            shotSkewMs = skew,
            matchedFrames = matches.size,
            ballPairs = ball.size,
            putterPairs = putter.size,
            medianTimeDeltaMs = medianTime,
            medianBallDeltaCm = medianBall,
            reason = reason
        )
    }

    private fun normalizeLocal(track: HfrFeatureTrack): HfrFeatureTrack? {
        if (track.fps !in V44TrackValidator.MIN_FPS..V44TrackValidator.MAX_FPS) return null
        if (track.frames.size < V44TrackValidator.MIN_FRAMES) return null
        val sorted = track.frames.sortedBy { it.frame }
        if (sorted.map { it.frame }.distinct().size != sorted.size) return null
        if (sorted.count { it.ballXcm != null && it.ballYcm != null } < 3) return null
        return track.copy(frames = sorted)
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private fun empty(reason: String) = V44StereoReadiness(
        ready = false,
        cameraId = null,
        view = null,
        score = 0,
        shotSkewMs = null,
        matchedFrames = 0,
        ballPairs = 0,
        putterPairs = 0,
        medianTimeDeltaMs = null,
        medianBallDeltaCm = null,
        reason = reason
    )
}

object V44StereoPrepRuntime {
    fun snapshot(nowMs: Long = System.currentTimeMillis()): V44StereoReadiness {
        val published = V41HfrFeatureTrackRuntime.latestPublishedAtMs
        val local = V41HfrFeatureTrackRuntime.latest?.let { HfrFeatureTrackSnapshot(it, published) }
        val remotes = V43RemoteFeatureTrackRuntime.fresh(nowMs = nowMs, maxAgeMs = 10_000L)
        return V44StereoReadinessEngine.best(local, remotes, nowMs, 10_000L)
    }
}

fun showV44StereoPrepDialog(context: Context) {
    val snapshot = V44StereoPrepRuntime.snapshot()
    AlertDialog.Builder(context)
        .setTitle("STEREO PREP")
        .setMessage(snapshot.detail)
        .setPositiveButton("확인", null)
        .show()
}
