package com.puttvision.screen

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
    val pixelTrack: Boolean
)

object V64PixelTrackTransport {
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
            lines += V64EncodedTrackLine(
                sequence = pixelSequence,
                pixelTrack = true,
                raw = V55PixelFeatureTrackWire.encode(code, pixelPacket)
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
            return V64DecodedTrackLine(packet, pixelTrack = true)
        }
        if (V43FeatureTrackWire.isFeatureTrack(raw)) {
            val packet = V50FeatureTrackWire.decode(raw, expectedCode, nowMs) ?: return null
            return V64DecodedTrackLine(packet, pixelTrack = false)
        }
        return null
    }
}