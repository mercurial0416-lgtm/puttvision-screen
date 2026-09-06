package com.puttvision.bridge

import org.json.JSONObject

/**
 * Versioned shot-result contract shared with the Unity renderer.
 *
 * Camera/vision code stays native. Publish one instance only after native validation has
 * produced a final shot result; do not use this object for raw frame transport.
 */
data class PuttTelemetry(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val shotId: String,
    val timestampNs: Long,
    val ballSpeedMps: Float,
    val launchDirectionDeg: Float,
    val faceAngleDeg: Float = 0f,
    val pathAngleDeg: Float = 0f,
    val impactOffsetMm: Float = 0f,
    val confidence: Float = 0f,
    val validityFlags: Int = 0,
) {
    fun isSimulationReady(): Boolean =
        schemaVersion == CURRENT_SCHEMA_VERSION &&
            ballSpeedMps.isFinite() &&
            ballSpeedMps >= 0f &&
            launchDirectionDeg.isFinite()

    fun toJson(): String = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("shotId", shotId)
        put("timestampNs", timestampNs)
        put("ballSpeedMps", ballSpeedMps.toDouble())
        put("launchDirectionDeg", launchDirectionDeg.toDouble())
        put("faceAngleDeg", faceAngleDeg.toDouble())
        put("pathAngleDeg", pathAngleDeg.toDouble())
        put("impactOffsetMm", impactOffsetMm.toDouble())
        put("confidence", confidence.toDouble())
        put("validityFlags", validityFlags)
    }.toString()

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        const val FACE_ANGLE_VALID = 1 shl 0
        const val PATH_ANGLE_VALID = 1 shl 1
        const val IMPACT_OFFSET_VALID = 1 shl 2
        const val CONFIDENCE_VALID = 1 shl 3
    }
}
