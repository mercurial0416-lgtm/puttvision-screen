package com.puttvision.screen

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import kotlin.math.max
import kotlin.math.min

data class ImpactReplay(
    val frames: List<Bitmap>,
    val fps: Int,
    val impactIndex: Int
)

object ImpactReplayExtractor {

    fun extract(
        file: File,
        impactFrame: Int,
        captureFps: Int,
        maxFrames: Int = 24
    ): ImpactReplay? {
        if (android.os.Build.VERSION.SDK_INT < 28) return null

        val mmr = MediaMetadataRetriever()

        return try {
            mmr.setDataSource(file.absolutePath)

            val total = mmr.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT
            )?.toIntOrNull() ?: return null

            val before = (captureFps * 0.085).toInt()
            val after = (captureFps * 0.115).toInt()

            val start = max(0, impactFrame - before)
            val end = min(total - 1, impactFrame + after)
            val count = end - start + 1
            val stride = max(1, count / maxFrames)

            val frames = ArrayList<Bitmap>()
            var localImpact = 0
            var i = start

            while (i <= end) {
                val bmp = try {
                    mmr.getFrameAtIndex(i)
                } catch (_: Throwable) {
                    null
                }

                if (bmp != null) {
                    val maxWidth = 720
                    val displayFrame =
                        if (bmp.width > maxWidth) {
                            val scale = maxWidth.toFloat() / bmp.width
                            val scaled = Bitmap.createScaledBitmap(
                                bmp,
                                maxWidth,
                                (bmp.height * scale).toInt().coerceAtLeast(1),
                                false
                            )
                            if (scaled !== bmp && !bmp.isRecycled) bmp.recycle()
                            scaled
                        } else bmp

                    if (i <= impactFrame) localImpact = frames.size
                    frames += displayFrame
                }

                i += stride
            }

            if (frames.size < 4) {
                frames.forEach { if (!it.isRecycled) it.recycle() }
                null
            } else ImpactReplay(
                frames = frames,
                fps = captureFps,
                impactIndex = localImpact.coerceIn(0, frames.lastIndex)
            )
        } finally {
            mmr.release()
        }
    }
}
