package com.puttvision.screen

import android.content.Context
import android.hardware.camera2.CameraAccessException
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
        val cameraIds = try {
            manager.cameraIdList.toList()
        } catch (_: CameraAccessException) {
            return HfrCapabilities(emptyList())
        } catch (_: RuntimeException) {
            return HfrCapabilities(emptyList())
        }

        for (id in cameraIds) {
            val chars = try {
                manager.getCameraCharacteristics(id)
            } catch (_: CameraAccessException) {
                continue
            } catch (_: RuntimeException) {
                continue
            }
            val lensFacing = try {
                chars.get(CameraCharacteristics.LENS_FACING)
            } catch (_: RuntimeException) {
                continue
            }
            if (lensFacing != CameraCharacteristics.LENS_FACING_BACK) continue

            val caps = try {
                chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            } catch (_: RuntimeException) {
                continue
            } ?: intArrayOf()

            if (!caps.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO
                )
            ) continue

            val map = try {
                chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            } catch (_: RuntimeException) {
                continue
            } ?: continue
            val highSpeedSizes = try {
                map.highSpeedVideoSizes?.toList() ?: emptyList()
            } catch (_: RuntimeException) {
                continue
            }

            for (size in highSpeedSizes) {
                val ranges = try {
                    map.getHighSpeedVideoFpsRangesFor(size)?.toList() ?: emptyList()
                } catch (_: RuntimeException) {
                    emptyList()
                }

                for (range in ranges) {
                    if (range.lower == range.upper && range.upper >= 120) {
                        modes += HfrMode(id, size, range)
                    }
                }
            }
        }

        return HfrCapabilities(modes.distinct())
    }

    fun preferred(caps: HfrCapabilities): HfrMode? =
        caps.modes.minWithOrNull(
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
            }.thenByDescending {
                it.size.width.toLong() * it.size.height.toLong()
            }.thenBy {
                it.cameraId
            }
        )
}
