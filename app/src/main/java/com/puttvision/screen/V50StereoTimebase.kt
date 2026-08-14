package com.puttvision.screen

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/**
 * Separates HFR analysis/publication time from the physical shot time used to pair LAN cameras.
 * The wall-clock impact is estimated from CameraX's recording-start callback plus the detected
 * impact frame. This is intentionally labelled ESTIMATED; it is not hardware timestamp accuracy.
 */
data class V50HfrCaptureEpoch(
    val fileName: String,
    val startedAtMs: Long,
    val fps: Int
)

data class V50ImpactEstimate(
    val impactAtMs: Long,
    val source: String,
    val uncertaintyMs: Long
)

object V50HfrCaptureClockRuntime {
    private const val MAX_CAPTURE_AGE_MS = 20_000L
    private const val MAX_FUTURE_MS = 300L

    @Volatile private var latest: V50HfrCaptureEpoch? = null

    fun onRecordingStarted(file: File, fps: Int, startedAtMs: Long = System.currentTimeMillis()) {
        if (fps !in 60..480 || startedAtMs <= 0L) return
        latest = V50HfrCaptureEpoch(file.name, startedAtMs, fps)
    }

    fun estimate(track: HfrFeatureTrack, publishedAtMs: Long = System.currentTimeMillis()): V50ImpactEstimate {
        val epoch = latest
        if (epoch != null && track.fps in 60..480 && epoch.fps == track.fps && track.impactFrame >= 0) {
            val event = epoch.startedAtMs + (track.impactFrame * 1000.0 / track.fps).toLong()
            val age = publishedAtMs - event
            if (age in -MAX_FUTURE_MS..MAX_CAPTURE_AGE_MS) {
                // CameraX Start is close to, but not guaranteed to be, sensor frame zero. Keep an
                // explicit uncertainty budget so readiness never masquerades as calibrated stereo.
                val frameMs = (1000.0 / track.fps).toLong().coerceAtLeast(2L)
                return V50ImpactEstimate(event, "CAMERAX_START+FRAME", (frameMs * 3).coerceAtLeast(12L))
            }
        }
        return V50ImpactEstimate(publishedAtMs, "PUBLISH_FALLBACK", 1_500L)
    }

    fun clear() { latest = null }

    internal fun latestForTest(): V50HfrCaptureEpoch? = latest
}

/**
 * Feature-track wire decoder that treats capturedAtMs as the shot/event time, not packet arrival.
 * Existing V43 encoding remains byte-compatible. The wider event window is bounded because HFR
 * analysis itself can legitimately take several seconds before a compact track is sent.
 */
object V50FeatureTrackWire {
    const val MAX_EVENT_AGE_MS = 15_000L
    const val MAX_FUTURE_MS = 500L

    fun decode(raw: String, expectedCode: String, nowMs: Long = System.currentTimeMillis()): V43FeatureTrackPacket? =
        runCatching {
            val j = JSONObject(raw)
            require(j.optInt("pv") == V28CompanionProtocol.VERSION && j.optString("type") == "feature_track")
            require(j.optString("code") == expectedCode)
            val captured = j.getLong("capturedAtMs")
            require(nowMs - captured in -MAX_FUTURE_MS..MAX_EVENT_AGE_MS)
            val arr = j.getJSONArray("frames")
            require(arr.length() in 1..V43FeatureTrackWire.MAX_FRAMES)
            fun value(o: JSONObject, key: String): Double? = o.optDouble(key, Double.NaN).takeIf { it.isFinite() }
            val frames = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(HfrFeatureFrame(
                        frame = o.getInt("f"),
                        timeFromImpactMs = o.getDouble("t"),
                        ballXcm = value(o, "bx"), ballYcm = value(o, "by"),
                        heelXcm = value(o, "hx"), heelYcm = value(o, "hy"),
                        toeXcm = value(o, "tx"), toeYcm = value(o, "ty"),
                        markerAngleDeg = value(o, "a")
                    ))
                }
            }
            val view = runCatching { V15CameraView.valueOf(j.getString("view")) }.getOrDefault(V15CameraView.PRIMARY)
            val rawTrack = HfrFeatureTrack(j.getInt("fps"), j.getInt("impact"), frames)
            val normalized = V44TrackValidator.normalize(rawTrack, view) ?: error("invalid feature track")
            V43FeatureTrackPacket(
                cameraId = j.getString("camera").trim().takeIf { it.isNotEmpty() } ?: error("camera"),
                view = view,
                capturedAtMs = captured,
                sequence = j.getLong("seq").also { require(it >= 0L) },
                track = normalized
            )
        }.getOrNull()
}

object V50StereoTimePolicy {
    fun usableImpactTimestamp(impactAtMs: Long, publishedAtMs: Long): Boolean =
        impactAtMs > 0L && publishedAtMs >= impactAtMs && publishedAtMs - impactAtMs <= 20_000L

    fun skewMs(localImpactAtMs: Long, remoteImpactAtMs: Long): Long = abs(localImpactAtMs - remoteImpactAtMs)
}
