package com.puttvision.screen

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * Durable storage and projection-drift invalidation for V59/V61 stereo calibration profiles.
 *
 * A stored calibration is never returned merely because it can be decoded. It must still pass the
 * current V59 capture/pair/rig binding and, when configured, a fresh planar-target witness check.
 * Witness thresholds are operational pixel-drift gates, not physical millimetre accuracy claims.
 */
data class V62RigWitness(
    val firstSignatureKey: String,
    val secondSignatureKey: String,
    val firstLandmarksPx: List<V53Pixel>,
    val secondLandmarksPx: List<V53Pixel>,
    val capturedAtMs: Long
) {
    fun valid(): Boolean =
        firstSignatureKey.isNotBlank() && secondSignatureKey.isNotBlank() &&
            firstSignatureKey != secondSignatureKey &&
            firstLandmarksPx.size >= 4 && firstLandmarksPx.size == secondLandmarksPx.size &&
            firstLandmarksPx.all { it.valid() } && secondLandmarksPx.all { it.valid() } &&
            capturedAtMs > 0L
}

data class V62StoredStereoCalibration(
    val schemaVersion: Int = 1,
    val profile: V59StereoCalibrationProfile,
    val witness: V62RigWitness,
    val storedAtMs: Long
)

data class V62PersistencePolicy(
    val requireCurrentWitness: Boolean = true,
    val maxWitnessAgeMs: Long = 24L * 60L * 60L * 1000L,
    val maxWitnessRmsDriftPx: Double = 6.0,
    val maxWitnessPointDriftPx: Double = 12.0,
    val v59Policy: V59StereoCalibrationPolicy = V59StereoCalibrationPolicy()
) {
    fun valid(): Boolean =
        maxWitnessAgeMs > 0L &&
            maxWitnessRmsDriftPx.isFinite() && maxWitnessRmsDriftPx > 0.0 &&
            maxWitnessPointDriftPx.isFinite() && maxWitnessPointDriftPx >= maxWitnessRmsDriftPx &&
            v59Policy.valid()
}

data class V62PersistenceDecision(
    val usableForStereo: Boolean,
    val profile: V59StereoCalibrationProfile?,
    val reason: String,
    val witnessRmsDriftPx: Double? = null,
    val witnessMaxDriftPx: Double? = null
)

object V62StereoCalibrationValidator {
    fun evaluate(
        stored: V62StoredStereoCalibration?,
        currentFirst: V59CaptureSignature,
        currentSecond: V59CaptureSignature,
        activePairId: String,
        activeRigRevisionId: String,
        nowMs: Long,
        currentWitness: V62RigWitness?,
        policy: V62PersistencePolicy = V62PersistencePolicy()
    ): V62PersistenceDecision {
        if (!policy.valid()) return deny("persistence policy invalid")
        if (stored == null) return deny("stored stereo calibration missing")
        if (stored.schemaVersion != 1 || stored.storedAtMs <= 0L) return deny("stored stereo calibration envelope invalid")
        if (!stored.witness.valid()) return deny("stored rig witness invalid")

        val v59 = V59StereoCalibrationGate.evaluate(
            profile = stored.profile,
            currentFirst = currentFirst,
            currentSecond = currentSecond,
            activePairId = activePairId,
            activeRigRevisionId = activeRigRevisionId,
            nowMs = nowMs,
            policy = policy.v59Policy
        )
        if (!v59.usableForStereo) return deny("V59 gate: ${v59.reason}")

        if (stored.witness.firstSignatureKey != currentFirst.stableKey() ||
            stored.witness.secondSignatureKey != currentSecond.stableKey()
        ) return deny("stored witness capture configuration changed")

        if (currentWitness == null) {
            return if (policy.requireCurrentWitness) deny("fresh rig witness required")
            else V62PersistenceDecision(true, stored.profile, "stored profile passed without current witness")
        }
        if (!currentWitness.valid()) return deny("current rig witness invalid")
        if (currentWitness.firstSignatureKey != currentFirst.stableKey() ||
            currentWitness.secondSignatureKey != currentSecond.stableKey()
        ) return deny("current witness capture configuration changed")
        if (currentWitness.capturedAtMs - nowMs > policy.v59Policy.maxClockSkewMs) {
            return deny("current witness timestamp is in the future")
        }
        if (nowMs - currentWitness.capturedAtMs > policy.maxWitnessAgeMs) return deny("current rig witness stale")

        val drift = witnessDrift(stored.witness, currentWitness)
            ?: return deny("rig witness correspondence mismatch")
        if (drift.second > policy.maxWitnessPointDriftPx) {
            return V62PersistenceDecision(false, null, "rig witness point drift too high", drift.first, drift.second)
        }
        if (drift.first > policy.maxWitnessRmsDriftPx) {
            return V62PersistenceDecision(false, null, "rig witness RMS drift too high", drift.first, drift.second)
        }
        return V62PersistenceDecision(
            usableForStereo = true,
            profile = stored.profile,
            reason = "stored calibration matches current capture and rig witness",
            witnessRmsDriftPx = drift.first,
            witnessMaxDriftPx = drift.second
        )
    }

    private fun witnessDrift(a: V62RigWitness, b: V62RigWitness): Pair<Double, Double>? {
        if (a.firstLandmarksPx.size != b.firstLandmarksPx.size ||
            a.secondLandmarksPx.size != b.secondLandmarksPx.size ||
            a.firstLandmarksPx.size != a.secondLandmarksPx.size
        ) return null
        var squared = 0.0
        var count = 0
        var maxDrift = 0.0
        fun accumulate(first: List<V53Pixel>, second: List<V53Pixel>) {
            for (i in first.indices) {
                val dx = first[i].x - second[i].x
                val dy = first[i].y - second[i].y
                val d2 = dx * dx + dy * dy
                squared += d2
                count++
                maxDrift = maxOf(maxDrift, sqrt(d2))
            }
        }
        accumulate(a.firstLandmarksPx, b.firstLandmarksPx)
        accumulate(a.secondLandmarksPx, b.secondLandmarksPx)
        if (count == 0) return null
        val rms = sqrt(squared / count)
        if (!rms.isFinite() || !maxDrift.isFinite()) return null
        return rms to maxDrift
    }

    private fun deny(reason: String) = V62PersistenceDecision(false, null, reason)
}

object V62StereoCalibrationCodec {
    fun encode(stored: V62StoredStereoCalibration): String = JSONObject().apply {
        put("schemaVersion", stored.schemaVersion)
        put("storedAtMs", stored.storedAtMs)
        put("profile", profileToJson(stored.profile))
        put("witness", witnessToJson(stored.witness))
    }.toString()

    fun decode(raw: String): V62StoredStereoCalibration? = runCatching {
        val root = JSONObject(raw)
        V62StoredStereoCalibration(
            schemaVersion = root.getInt("schemaVersion"),
            profile = profileFromJson(root.getJSONObject("profile")),
            witness = witnessFromJson(root.getJSONObject("witness")),
            storedAtMs = root.getLong("storedAtMs")
        )
    }.getOrNull()

    private fun profileToJson(profile: V59StereoCalibrationProfile) = JSONObject().apply {
        put("schemaVersion", profile.schemaVersion)
        put("pairId", profile.pairId)
        put("rigRevisionId", profile.rigRevisionId)
        put("calibratedAtMs", profile.calibratedAtMs)
        put("acceptedObservationCount", profile.acceptedObservationCount)
        put("first", boundToJson(profile.first))
        put("second", boundToJson(profile.second))
    }

    private fun profileFromJson(json: JSONObject) = V59StereoCalibrationProfile(
        schemaVersion = json.getInt("schemaVersion"),
        pairId = json.getString("pairId"),
        rigRevisionId = json.getString("rigRevisionId"),
        first = boundFromJson(json.getJSONObject("first")),
        second = boundFromJson(json.getJSONObject("second")),
        calibratedAtMs = json.getLong("calibratedAtMs"),
        acceptedObservationCount = json.getInt("acceptedObservationCount")
    )

    private fun boundToJson(bound: V59BoundCameraCalibration) = JSONObject().apply {
        put("signature", signatureToJson(bound.signature))
        put("calibration", calibrationToJson(bound.calibration))
    }

    private fun boundFromJson(json: JSONObject) = V59BoundCameraCalibration(
        signature = signatureFromJson(json.getJSONObject("signature")),
        calibration = calibrationFromJson(json.getJSONObject("calibration"))
    )

    private fun signatureToJson(signature: V59CaptureSignature) = JSONObject().apply {
        put("cameraId", signature.cameraId)
        put("widthPx", signature.widthPx)
        put("heightPx", signature.heightPx)
        put("fps", signature.fps)
        put("sensorOrientationDeg", signature.sensorOrientationDeg)
        put("lensFacing", signature.lensFacing)
        put("captureMode", signature.captureMode)
    }

    private fun signatureFromJson(json: JSONObject) = V59CaptureSignature(
        cameraId = json.getString("cameraId"),
        widthPx = json.getInt("widthPx"),
        heightPx = json.getInt("heightPx"),
        fps = json.getInt("fps"),
        sensorOrientationDeg = json.getInt("sensorOrientationDeg"),
        lensFacing = json.getString("lensFacing"),
        captureMode = json.getString("captureMode")
    )

    private fun calibrationToJson(calibration: V53CameraCalibration) = JSONObject().apply {
        put("intrinsics", JSONObject().apply {
            put("fx", calibration.intrinsics.fx)
            put("fy", calibration.intrinsics.fy)
            put("cx", calibration.intrinsics.cx)
            put("cy", calibration.intrinsics.cy)
        })
        put("rotation", JSONArray().apply { calibration.extrinsics.rotationWorldFromCamera.forEach { put(it) } })
        put("origin", JSONObject().apply {
            put("x", calibration.extrinsics.originWorld.x)
            put("y", calibration.extrinsics.originWorld.y)
            put("z", calibration.extrinsics.originWorld.z)
        })
        put("rmsReprojectionPx", calibration.rmsReprojectionPx)
        put("calibratedAtMs", calibration.calibratedAtMs)
    }

    private fun calibrationFromJson(json: JSONObject): V53CameraCalibration {
        val intrinsics = json.getJSONObject("intrinsics")
        val rotation = json.getJSONArray("rotation")
        require(rotation.length() == 9)
        val origin = json.getJSONObject("origin")
        return V53CameraCalibration(
            intrinsics = V53CameraIntrinsics(
                fx = intrinsics.getDouble("fx"),
                fy = intrinsics.getDouble("fy"),
                cx = intrinsics.getDouble("cx"),
                cy = intrinsics.getDouble("cy")
            ),
            extrinsics = V53CameraExtrinsics(
                rotationWorldFromCamera = DoubleArray(9) { rotation.getDouble(it) },
                originWorld = V53Vec3(origin.getDouble("x"), origin.getDouble("y"), origin.getDouble("z"))
            ),
            rmsReprojectionPx = json.getDouble("rmsReprojectionPx"),
            calibratedAtMs = json.getLong("calibratedAtMs")
        )
    }

    private fun witnessToJson(witness: V62RigWitness) = JSONObject().apply {
        put("firstSignatureKey", witness.firstSignatureKey)
        put("secondSignatureKey", witness.secondSignatureKey)
        put("firstLandmarksPx", pixelsToJson(witness.firstLandmarksPx))
        put("secondLandmarksPx", pixelsToJson(witness.secondLandmarksPx))
        put("capturedAtMs", witness.capturedAtMs)
    }

    private fun witnessFromJson(json: JSONObject) = V62RigWitness(
        firstSignatureKey = json.getString("firstSignatureKey"),
        secondSignatureKey = json.getString("secondSignatureKey"),
        firstLandmarksPx = pixelsFromJson(json.getJSONArray("firstLandmarksPx")),
        secondLandmarksPx = pixelsFromJson(json.getJSONArray("secondLandmarksPx")),
        capturedAtMs = json.getLong("capturedAtMs")
    )

    private fun pixelsToJson(pixels: List<V53Pixel>) = JSONArray().apply {
        pixels.forEach { pixel -> put(JSONArray().apply { put(pixel.x); put(pixel.y) }) }
    }

    private fun pixelsFromJson(array: JSONArray): List<V53Pixel> = List(array.length()) { index ->
        val pixel = array.getJSONArray(index)
        V53Pixel(pixel.getDouble(0), pixel.getDouble(1))
    }
}

class V62StereoCalibrationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("v62_stereo_calibration", Context.MODE_PRIVATE)

    fun save(profile: V59StereoCalibrationProfile, witness: V62RigWitness, storedAtMs: Long): Boolean {
        if (!witness.valid() || storedAtMs <= 0L) return false
        if (witness.firstSignatureKey != profile.first.signature.stableKey() ||
            witness.secondSignatureKey != profile.second.signature.stableKey()
        ) return false
        val record = V62StoredStereoCalibration(profile = profile, witness = witness, storedAtMs = storedAtMs)
        val payload = V62StereoCalibrationCodec.encode(record)
        val checksum = sha256(payload)
        return prefs.edit().putString(KEY_PAYLOAD, payload).putString(KEY_SHA256, checksum).commit()
    }

    fun loadRaw(): V62StoredStereoCalibration? {
        val payload = prefs.getString(KEY_PAYLOAD, null) ?: return null
        val checksum = prefs.getString(KEY_SHA256, null) ?: return null
        if (!constantTimeEquals(checksum, sha256(payload))) {
            clear()
            return null
        }
        val decoded = V62StereoCalibrationCodec.decode(payload)
        if (decoded == null) clear()
        return decoded
    }

    fun loadValidated(
        currentFirst: V59CaptureSignature,
        currentSecond: V59CaptureSignature,
        activePairId: String,
        activeRigRevisionId: String,
        nowMs: Long,
        currentWitness: V62RigWitness?,
        policy: V62PersistencePolicy = V62PersistencePolicy()
    ): V62PersistenceDecision {
        val result = V62StereoCalibrationValidator.evaluate(
            stored = loadRaw(),
            currentFirst = currentFirst,
            currentSecond = currentSecond,
            activePairId = activePairId,
            activeRigRevisionId = activeRigRevisionId,
            nowMs = nowMs,
            currentWitness = currentWitness,
            policy = policy
        )
        // Fail closed and remove stale/mismatched calibration so it cannot silently resurrect later.
        if (!result.usableForStereo) clear()
        return result
    }

    fun clear() {
        prefs.edit().remove(KEY_PAYLOAD).remove(KEY_SHA256).commit()
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    companion object {
        private const val KEY_PAYLOAD = "profile_payload"
        private const val KEY_SHA256 = "profile_sha256"
    }
}