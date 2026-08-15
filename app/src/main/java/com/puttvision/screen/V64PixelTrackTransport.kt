package com.puttvision.screen

import org.json.JSONObject

/**
 * Bridges V55 raw-pixel feature tracks into the existing local companion LAN transport.
 *
 * New clients send the legacy planar packet first for backward compatibility, then a newer-sequence
 * pixel packet when the source track passes V55 validation. New servers accept both. Older servers
 * keep the planar packet and simply ignore the unknown pixel message.
 */
data class V64EncodedTrackLine(
    val sequence: Long,
    val pixelTrack: Boolean,
    val raw: String
)

data class V64DecodedTrackLine(
    val packet: V43FeatureTrackPacket,
    val pixelTrack: Boolean,
    val timingEvidence: V70RemoteTimingEvidence? = null
)

object V64PixelTrackTransport {
    private const val TIME_SOURCE = "timeSource"
    private const val TIME_UNCERTAINTY_MS = "timeUncertaintyMs"

    fun canSendPixels(track: HfrFeatureTrack, view: V15CameraView): Boolean =
        V55PixelTrackValidator.inspect(track, view).valid

    fun encodeLines(
        code: String,
        cameraId: String,
        view: V15CameraView,
        track: HfrFeatureTrack,
        capturedAtMs: Long,
        legacySequence: Long,
        pixelSequence: Long?
    ): List<V64EncodedTrackLine> {
        require(code.isNotBlank())
        require(cameraId.isNotBlank())
        require(capturedAtMs > 0L)
        require(legacySequence >= 0L)
        val legacyPacket = V43FeatureTrackPacket(
            cameraId = cameraId,
            view = view,
            capturedAtMs = capturedAtMs,
            sequence = legacySequence,
            track = track
        )
        val lines = ArrayList<V64EncodedTrackLine>(2)
        lines += V64EncodedTrackLine(
            sequence = legacySequence,
            pixelTrack = false,
            raw = V43FeatureTrackWire.encode(code, legacyPacket)
        )
        if (pixelSequence != null && canSendPixels(track, view)) {
            require(pixelSequence > legacySequence)
            val pixelPacket = legacyPacket.copy(sequence = pixelSequence)
            var raw = V55PixelFeatureTrackWire.encode(code, pixelPacket)

            // Only bind timing evidence when this exact feature track is the latest V41 product.
            // If a caller sends an arbitrary/stale track, omit evidence so V70 fails closed.
            val latest = V41HfrFeatureTrackRuntime.latest
            val source = V41HfrFeatureTrackRuntime.latestTimeSource
            val uncertainty = V41HfrFeatureTrackRuntime.latestTimeUncertaintyMs
            if (latest == track && source.isNotBlank() && source != "NONE" && uncertainty >= 0L) {
                raw = JSONObject(raw).apply {
                    put(TIME_SOURCE, source)
                    put(TIME_UNCERTAINTY_MS, uncertainty)
                }.toString()
            }
            lines += V64EncodedTrackLine(
                sequence = pixelSequence,
                pixelTrack = true,
                raw = raw
            )
        }
        return lines
    }

    fun decodeLine(
        raw: String,
        expectedCode: String,
        nowMs: Long = System.currentTimeMillis()
    ): V64DecodedTrackLine? {
        if (V55PixelFeatureTrackWire.isPixelFeatureTrack(raw)) {
            val packet = V55PixelFeatureTrackWire.decode(raw, expectedCode, nowMs) ?: return null
            val timing = decodeTimingEvidence(raw, packet, nowMs)
            timing?.let { V70RemoteTimingRuntime.publish(it) }
            return V64DecodedTrackLine(packet, pixelTrack = true, timingEvidence = timing)
        }
        if (V43FeatureTrackWire.isFeatureTrack(raw)) {
            val packet = V50FeatureTrackWire.decode(raw, expectedCode, nowMs) ?: return null
            return V64DecodedTrackLine(packet, pixelTrack = false, timingEvidence = null)
        }
        return null
    }

    private fun decodeTimingEvidence(
        raw: String,
        packet: V43FeatureTrackPacket,
        nowMs: Long
    ): V70RemoteTimingEvidence? = runCatching {
        val j = JSONObject(raw)
        if (!j.has(TIME_SOURCE) || !j.has(TIME_UNCERTAINTY_MS)) return@runCatching null
        val source = j.getString(TIME_SOURCE).trim()
        val uncertainty = j.getLong(TIME_UNCERTAINTY_MS)
        if (source.isEmpty() || uncertainty !in 0L..10_000L) return@runCatching null
        V70RemoteTimingEvidence(
            cameraId = packet.cameraId,
            sequence = packet.sequence,
            eventAtMs = packet.capturedAtMs,
            receivedAtMs = nowMs,
            timeSource = source,
            uncertaintyMs = uncertainty,
            pixelTrack = true
        )
    }.getOrNull()
}
