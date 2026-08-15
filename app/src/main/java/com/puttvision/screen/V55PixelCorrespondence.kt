package com.puttvision.screen

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pixel-correspondence transport for calibrated stereo.
 *
 * Legacy V43 planar-cm packets remain valid for readiness/diagnostics. This path is deliberately
 * separate and fail-closed: no image shape + raw detector pixels means no calibrated 3D input.
 */
data class V55PixelTrackValidation(
    val valid: Boolean,
    val reason: String,
    val normalized: HfrFeatureTrack? = null
)

object V55PixelTrackValidator {
    const val MIN_IMAGE_PX = 160
    const val MAX_IMAGE_PX = 12_000
    const val EDGE_TOLERANCE_PX = 2.0
    const val MIN_BALL_PIXEL_FRAMES = 3

    fun inspect(track: HfrFeatureTrack, view: V15CameraView): V55PixelTrackValidation {
        val planar = V44TrackValidator.normalize(track, view)
            ?: return V55PixelTrackValidation(false, "planar track invalid")
        val width = planar.imageWidthPx
        val height = planar.imageHeightPx
        if (width == null || height == null) return V55PixelTrackValidation(false, "source image shape missing")
        if (width !in MIN_IMAGE_PX..MAX_IMAGE_PX || height !in MIN_IMAGE_PX..MAX_IMAGE_PX) {
            return V55PixelTrackValidation(false, "source image shape invalid")
        }

        for (frame in planar.frames) {
            if (!pixelPairValid(frame.ballXpx, frame.ballYpx, width, height)) {
                return V55PixelTrackValidation(false, "BALL pixel pair invalid")
            }
            val putter = listOf(frame.heelXpx, frame.heelYpx, frame.toeXpx, frame.toeYpx)
            val present = putter.count { it != null }
            if (present != 0 && present != 4) return V55PixelTrackValidation(false, "PUTTER pixel set incomplete")
            if (present == 4) {
                if (!pixelPairValid(frame.heelXpx, frame.heelYpx, width, height) ||
                    !pixelPairValid(frame.toeXpx, frame.toeYpx, width, height)
                ) return V55PixelTrackValidation(false, "PUTTER pixel pair invalid")
            }
        }

        if (planar.pixelBallFrames < MIN_BALL_PIXEL_FRAMES) {
            return V55PixelTrackValidation(false, "BALL pixel correspondences insufficient")
        }
        return V55PixelTrackValidation(true, "OK", planar)
    }

    fun normalize(track: HfrFeatureTrack, view: V15CameraView): HfrFeatureTrack? = inspect(track, view).normalized

    private fun pixelPairValid(x: Double?, y: Double?, width: Int, height: Int): Boolean {
        if ((x == null) != (y == null)) return false
        if (x == null || y == null) return true
        if (!x.isFinite() || !y.isFinite()) return false
        return x >= -EDGE_TOLERANCE_PX && x <= width + EDGE_TOLERANCE_PX &&
            y >= -EDGE_TOLERANCE_PX && y <= height + EDGE_TOLERANCE_PX
    }
}

object V55PixelFeatureTrackWire {
    const val TYPE = "feature_track_pixels"
    const val MAX_FRAMES = 32
    const val MAX_EVENT_AGE_MS = 15_000L

    fun encode(code: String, packet: V43FeatureTrackPacket): String {
        val track = V55PixelTrackValidator.normalize(packet.track, packet.view)
            ?: error("pixel track is not stereo-safe")
        return JSONObject().apply {
            put("pv", V28CompanionProtocol.VERSION)
            put("type", TYPE)
            put("code", code)
            put("camera", packet.cameraId)
            put("view", packet.view.name)
            put("capturedAtMs", packet.capturedAtMs)
            put("seq", packet.sequence)
            put("fps", track.fps)
            put("impact", track.impactFrame)
            put("iw", track.imageWidthPx)
            put("ih", track.imageHeightPx)
            put("frames", JSONArray().apply {
                track.frames.take(MAX_FRAMES).forEach { f ->
                    put(JSONObject().apply {
                        put("f", f.frame); put("t", f.timeFromImpactMs)
                        f.ballXcm?.let { put("bx", it) }; f.ballYcm?.let { put("by", it) }
                        f.heelXcm?.let { put("hx", it) }; f.heelYcm?.let { put("hy", it) }
                        f.toeXcm?.let { put("tx", it) }; f.toeYcm?.let { put("ty", it) }
                        f.markerAngleDeg?.let { put("a", it) }
                        f.ballXpx?.let { put("ub", it) }; f.ballYpx?.let { put("vb", it) }
                        f.heelXpx?.let { put("uh", it) }; f.heelYpx?.let { put("vh", it) }
                        f.toeXpx?.let { put("ut", it) }; f.toeYpx?.let { put("vt", it) }
                    })
                }
            })
        }.toString()
    }

    fun isPixelFeatureTrack(raw: String): Boolean = runCatching {
        val j = JSONObject(raw)
        j.optInt("pv") == V28CompanionProtocol.VERSION && j.optString("type") == TYPE
    }.getOrDefault(false)

    fun decode(raw: String, expectedCode: String, nowMs: Long = System.currentTimeMillis()): V43FeatureTrackPacket? = runCatching {
        val j = JSONObject(raw)
        require(j.optInt("pv") == V28CompanionProtocol.VERSION && j.optString("type") == TYPE)
        require(j.optString("code") == expectedCode)
        val captured = j.getLong("capturedAtMs")
        require(nowMs - captured in -300L..MAX_EVENT_AGE_MS)
        val arr = j.getJSONArray("frames")
        require(arr.length() in 1..MAX_FRAMES)
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
                    markerAngleDeg = value(o, "a"),
                    ballXpx = value(o, "ub"), ballYpx = value(o, "vb"),
                    heelXpx = value(o, "uh"), heelYpx = value(o, "vh"),
                    toeXpx = value(o, "ut"), toeYpx = value(o, "vt")
                ))
            }
        }
        val view = V15CameraView.valueOf(j.getString("view"))
        val rawTrack = HfrFeatureTrack(
            fps = j.getInt("fps"),
            impactFrame = j.getInt("impact"),
            frames = frames,
            imageWidthPx = j.getInt("iw"),
            imageHeightPx = j.getInt("ih")
        )
        val normalized = V55PixelTrackValidator.normalize(rawTrack, view) ?: error("invalid pixel feature track")
        V43FeatureTrackPacket(
            cameraId = j.getString("camera").trim().takeIf { it.isNotEmpty() } ?: error("camera"),
            view = view,
            capturedAtMs = captured,
            sequence = j.getLong("seq").also { require(it >= 0L) },
            track = normalized,
            receivedAtMs = nowMs
        )
    }.getOrNull()
}

data class V55MatchedBallPixels(
    val localFrame: Int,
    val remoteFrame: Int,
    val deltaMs: Double,
    val localPixel: V53Pixel,
    val remotePixel: V53Pixel
)

/** Converts V44 time matches into the raw pixel pairs consumed by V53 triangulation. */
object V55StereoPixelMatcher {
    fun ballPairs(
        local: HfrFeatureTrack,
        localView: V15CameraView,
        remote: HfrFeatureTrack,
        remoteView: V15CameraView
    ): List<V55MatchedBallPixels> {
        val localTrack = V55PixelTrackValidator.normalize(local, localView) ?: return emptyList()
        val remoteTrack = V55PixelTrackValidator.normalize(remote, remoteView) ?: return emptyList()
        return V44StereoMatcher.match(localTrack, remoteTrack).mapNotNull { matched ->
            val lx = matched.local.ballXpx ?: return@mapNotNull null
            val ly = matched.local.ballYpx ?: return@mapNotNull null
            val rx = matched.remote.ballXpx ?: return@mapNotNull null
            val ry = matched.remote.ballYpx ?: return@mapNotNull null
            V55MatchedBallPixels(
                localFrame = matched.local.frame,
                remoteFrame = matched.remote.frame,
                deltaMs = matched.deltaMs,
                localPixel = V53Pixel(lx, ly),
                remotePixel = V53Pixel(rx, ry)
            )
        }
    }
}
