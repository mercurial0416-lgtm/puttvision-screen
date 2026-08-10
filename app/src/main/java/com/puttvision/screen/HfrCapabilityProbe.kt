package com.puttvision.screen

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range
import android.util.Size

data class HfrMode(
    val cameraId: String,
    val size: Size,
    val fpsRange: Range<Int>
) {
    val fps: Int get() = fpsRange.upper
    val label: String get() = "${size.width}×${size.height} @ ${fps}fps"
}

data class HfrCapabilities(val modes: List<HfrMode>) {
    val supports240: Boolean get() = modes.any { it.fps >= 240 }
    val supports120: Boolean get() = modes.any { it.fps >= 120 }
}

object HfrCapabilityProbe {
    fun queryBackCamera(context: Context): HfrCapabilities {
        val manager = context.getSystemService(CameraManager::class.java)
        val modes = ArrayList<HfrMode>()

        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) !=
                CameraCharacteristics.LENS_FACING_BACK
            ) continue

            val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?: intArrayOf()

            if (!caps.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO
                )
            ) continue

            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue

            for (size in map.highSpeedVideoSizes) {
                val ranges = try {
                    map.getHighSpeedVideoFpsRangesFor(size)
                } catch (_: Throwable) {
                    emptyArray()
                }

                for (range in ranges) {
                    if (range.lower == range.upper && range.upper >= 120) {
                        modes += HfrMode(id, size, range)
                    }
                }
            }
        }

        return HfrCapabilities(
            modes.distinctBy {
                "${it.cameraId}:${it.size.width}x${it.size.height}:${it.fpsRange.lower}-${it.fpsRange.upper}"
            }
        )
    }

    fun preferred(caps: HfrCapabilities): HfrMode? =
        caps.modes.sortedWith(
            compareByDescending<HfrMode> {
                when (it.fps) {
                    240 -> 1000
                    120 -> 900
                    else -> 500 + it.fps
                }
            }.thenByDescending {
                when {
                    it.size.width == 1920 && it.size.height == 1080 -> 100
                    it.size.width == 1280 && it.size.height == 720 -> 90
                    else -> it.size.width * it.size.height / 100000
                }
            }
        ).firstOrNull()
}
