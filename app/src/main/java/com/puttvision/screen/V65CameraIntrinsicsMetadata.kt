package com.puttvision.screen

import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlin.math.abs

/**
 * Camera2 intrinsic metadata, kept in the coordinate system where Android defines it.
 *
 * Android's LENS_INTRINSIC_CALIBRATION is defined in pre-correction sensor pixel coordinates.
 * PuttVision therefore does not silently scale that matrix to an HFR video frame. A caller must
 * provide an explicit sensor crop/scale mapping, and processed/distortion-corrected coordinates are
 * accepted only when the metadata demonstrates that the simple mapping is safe.
 */
data class V65RectI(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    fun valid(): Boolean = left >= 0 && top >= 0 && right > left && bottom > top
    fun contains(other: V65RectI): Boolean =
        valid() && other.valid() && other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    companion object {
        fun from(rect: Rect): V65RectI = V65RectI(rect.left, rect.top, rect.right, rect.bottom)
    }
}

data class V65SensorIntrinsicMetadata(
    val cameraId: String,
    val fxPx: Double,
    val fyPx: Double,
    val cxPx: Double,
    val cyPx: Double,
    val skewPx: Double,
    val preCorrectionActiveArray: V65RectI,
    val activeArray: V65RectI?,
    val distortion: List<Double>?,
    val sensorOrientationDeg: Int,
    val lensFacing: Int?
) {
    fun valid(): Boolean =
        cameraId.isNotBlank() &&
            fxPx.isFinite() && fyPx.isFinite() && cxPx.isFinite() && cyPx.isFinite() && skewPx.isFinite() &&
            fxPx > 1e-6 && fyPx > 1e-6 &&
            preCorrectionActiveArray.valid() &&
            (activeArray == null || activeArray.valid()) &&
            distortion?.all { it.isFinite() } != false &&
            sensorOrientationDeg in setOf(0, 90, 180, 270)
}

enum class V65FrameCoordinateSpace {
    /** Pixels are explicitly expressed in pre-correction sensor coordinates before crop/scale. */
    PRE_CORRECTION_SENSOR,
    /** Normal processed output coordinates. Safe for simple mapping only if distortion is a no-op. */
    PROCESSED_ACTIVE_ARRAY
}

data class V65SensorToFrameMapping(
    val sourceCropPx: V65RectI,
    val outputWidthPx: Int,
    val outputHeightPx: Int,
    /** Must be zero for V53 because rotating pixels also rotates the camera X/Y basis. */
    val rotationDeg: Int,
    val coordinateSpace: V65FrameCoordinateSpace
) {
    fun valid(): Boolean =
        sourceCropPx.valid() && outputWidthPx > 0 && outputHeightPx > 0 &&
            rotationDeg in setOf(0, 90, 180, 270)
}

data class V65IntrinsicBindingPolicy(
    val maxAbsSkewPx: Double = 1e-3,
    val maxAbsNoOpDistortionCoefficient: Double = 1e-7,
    val principalPointMarginPx: Double = 2.0
) {
    fun valid(): Boolean =
        maxAbsSkewPx.isFinite() && maxAbsSkewPx >= 0.0 &&
            maxAbsNoOpDistortionCoefficient.isFinite() && maxAbsNoOpDistortionCoefficient >= 0.0 &&
            principalPointMarginPx.isFinite() && principalPointMarginPx >= 0.0
}

data class V65IntrinsicBindingResult(
    val intrinsics: V53CameraIntrinsics?,
    val usableForV61: Boolean,
    val reason: String
)

object V65CameraIntrinsicBinder {
    fun bind(
        metadata: V65SensorIntrinsicMetadata?,
        mapping: V65SensorToFrameMapping?,
        policy: V65IntrinsicBindingPolicy = V65IntrinsicBindingPolicy()
    ): V65IntrinsicBindingResult {
        if (!policy.valid()) return deny("intrinsic binding policy invalid")
        if (metadata == null || !metadata.valid()) return deny("Camera2 intrinsic metadata missing or invalid")
        if (mapping == null || !mapping.valid()) return deny("explicit sensor-to-frame mapping missing or invalid")
        if (abs(metadata.skewPx) > policy.maxAbsSkewPx) {
            return deny("camera skew unsupported by V53 pinhole model")
        }
        if (mapping.rotationDeg != 0) {
            return deny("rotated frame mapping requires coupled extrinsic-axis transform")
        }

        val sourceArray = when (mapping.coordinateSpace) {
            V65FrameCoordinateSpace.PRE_CORRECTION_SENSOR -> metadata.preCorrectionActiveArray
            V65FrameCoordinateSpace.PROCESSED_ACTIVE_ARRAY -> {
                val active = metadata.activeArray ?: return deny("processed active array metadata missing")
                if (active != metadata.preCorrectionActiveArray) {
                    return deny("processed output differs from pre-correction sensor geometry")
                }
                val distortion = metadata.distortion
                if (distortion != null && distortion.any { abs(it) > policy.maxAbsNoOpDistortionCoefficient }) {
                    return deny("processed output has non-trivial lens distortion mapping")
                }
                active
            }
        }
        if (!sourceArray.contains(mapping.sourceCropPx)) return deny("frame crop outside declared sensor coordinate space")

        val crop = mapping.sourceCropPx
        val sx = mapping.outputWidthPx.toDouble() / crop.width.toDouble()
        val sy = mapping.outputHeightPx.toDouble() / crop.height.toDouble()
        if (!sx.isFinite() || !sy.isFinite() || sx <= 0.0 || sy <= 0.0) return deny("frame scale invalid")

        val bound = V53CameraIntrinsics(
            fx = metadata.fxPx * sx,
            fy = metadata.fyPx * sy,
            cx = (metadata.cxPx - crop.left) * sx,
            cy = (metadata.cyPx - crop.top) * sy
        )
        if (!bound.valid()) return deny("bound frame intrinsics invalid")
        val margin = policy.principalPointMarginPx
        if (bound.cx !in -margin..(mapping.outputWidthPx + margin) ||
            bound.cy !in -margin..(mapping.outputHeightPx + margin)
        ) return deny("principal point outside mapped frame")

        return V65IntrinsicBindingResult(
            intrinsics = bound,
            usableForV61 = true,
            reason = "Camera2 sensor intrinsics explicitly bound to frame crop/scale"
        )
    }

    private fun deny(reason: String) = V65IntrinsicBindingResult(null, false, reason)
}

/** Android adapter. Reading metadata alone never makes it usable for V61; V65CameraIntrinsicBinder must still approve a mapping. */
object V65Camera2IntrinsicMetadataReader {
    fun read(context: Context, cameraId: String): V65SensorIntrinsicMetadata? = runCatching {
        val manager = context.applicationContext.getSystemService(CameraManager::class.java) ?: return@runCatching null
        val chars = manager.getCameraCharacteristics(cameraId)
        val k = chars.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION) ?: return@runCatching null
        if (k.size < 5) return@runCatching null
        val pre = chars.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE) ?: return@runCatching null
        val active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val distortion = chars.get(CameraCharacteristics.LENS_DISTORTION)?.map { it.toDouble() }
        V65SensorIntrinsicMetadata(
            cameraId = cameraId,
            fxPx = k[0].toDouble(),
            fyPx = k[1].toDouble(),
            cxPx = k[2].toDouble(),
            cyPx = k[3].toDouble(),
            skewPx = k[4].toDouble(),
            preCorrectionActiveArray = V65RectI.from(pre),
            activeArray = active?.let(V65RectI::from),
            distortion = distortion,
            sensorOrientationDeg = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: return@runCatching null,
            lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
        ).takeIf { it.valid() }
    }.getOrNull()
}