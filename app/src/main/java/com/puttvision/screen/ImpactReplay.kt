package com.puttvision.screen

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ImpactReplay(
    val frames: List<Bitmap>,
    val fps: Int,
    val impactIndex: Int,
    val sourceFrameIndices: List<Int> = emptyList(),
    val sourceImpactFrame: Int? = null
) {
    fun relativeTimeMsAt(frameIndex: Int): Double? =
        ImpactReplayExtractionIntegrity.relativeTimeMs(
            frameIndex = frameIndex,
            frameCount = frames.size,
            fps = fps,
            impactIndex = impactIndex,
            sourceFrameIndices = sourceFrameIndices,
            sourceImpactFrame = sourceImpactFrame
        )
}

data class ImpactReplaySamplePlan(
    val sourceFrameIndices: List<Int>,
    val sourceImpactFrame: Int
)

object ImpactReplaySamplePlanner {
    fun plan(
        totalFrames: Int,
        impactFrame: Int,
        captureFps: Int,
        maxFrames: Int = 24
    ): ImpactReplaySamplePlan? {
        if (totalFrames <= 0 || captureFps <= 0 || maxFrames <= 0) return null

        val safeImpact = impactFrame.coerceIn(0, totalFrames - 1)
        val before = (captureFps * 0.085).toInt().coerceAtLeast(0)
        val after = (captureFps * 0.115).toInt().coerceAtLeast(0)
        val start = max(0, safeImpact - before)
        val end = min(totalFrames - 1, safeImpact + after)
        val count = end - start + 1
        if (count <= 0) return null

        val targetCount = min(maxFrames, count)
        if (targetCount == 1) {
            return ImpactReplaySamplePlan(listOf(safeImpact), safeImpact)
        }

        val span = end - start
        val sampled = MutableList(targetCount) { slot ->
            start + (slot.toDouble() * span.toDouble() / (targetCount - 1).toDouble()).roundToInt()
        }

        if (safeImpact !in sampled) {
            val replaceAt = sampled.indices.minByOrNull { abs(sampled[it] - safeImpact) } ?: 0
            sampled[replaceAt] = safeImpact
        }

        val indices = sampled.distinct().sorted()
        return ImpactReplaySamplePlan(indices, safeImpact)
    }
}

/**
 * Keeps Impact Replay honest about which decoded source frame is the actual contact frame.
 * A neighboring decoded frame must never be promoted to IMPACT when the requested source
 * impact frame itself failed to decode, and partially-present provenance must never silently
 * fall back to ordinal replay timing.
 */
object ImpactReplayExtractionIntegrity {
    fun exactImpactIndex(
        extractedSourceFrameIndices: List<Int>,
        sourceImpactFrame: Int
    ): Int? {
        if (sourceImpactFrame < 0 || extractedSourceFrameIndices.isEmpty()) return null
        if (extractedSourceFrameIndices.zipWithNext().any { (a, b) -> b <= a }) return null
        val index = extractedSourceFrameIndices.indexOf(sourceImpactFrame)
        return index.takeIf { it >= 0 }
    }

    fun relativeTimeMs(
        frameIndex: Int,
        frameCount: Int,
        fps: Int,
        impactIndex: Int,
        sourceFrameIndices: List<Int>,
        sourceImpactFrame: Int?
    ): Double? {
        if (fps <= 0 || frameCount <= 0 || frameIndex !in 0 until frameCount) return null
        if (impactIndex !in 0 until frameCount) return null

        val hasAnyProvenance = sourceFrameIndices.isNotEmpty() || sourceImpactFrame != null
        if (!hasAnyProvenance) {
            return (frameIndex - impactIndex) * 1000.0 / fps.toDouble()
        }

        val impactFrame = sourceImpactFrame ?: return null
        if (sourceFrameIndices.size != frameCount) return null
        if (sourceFrameIndices.zipWithNext().any { (a, b) -> b <= a }) return null
        if (sourceFrameIndices.getOrNull(impactIndex) != impactFrame) return null
        val sourceFrame = sourceFrameIndices.getOrNull(frameIndex) ?: return null
        return (sourceFrame - impactFrame) * 1000.0 / fps.toDouble()
    }
}

object ImpactReplayExtractor {

    fun extract(
        file: File,
        impactFrame: Int,
        captureFps: Int,
        maxFrames: Int = 24
    ): ImpactReplay? {
        if (android.os.Build.VERSION.SDK_INT < 28) return null
        if (captureFps <= 0 || maxFrames <= 0) return null

        val mmr = MediaMetadataRetriever()

        return try {
            mmr.setDataSource(file.absolutePath)

            val total = mmr.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT
            )?.toIntOrNull() ?: return null
            val plan = ImpactReplaySamplePlanner.plan(total, impactFrame, captureFps, maxFrames) ?: return null

            val frames = ArrayList<Bitmap>(plan.sourceFrameIndices.size)
            val sourceFrameIndices = ArrayList<Int>(plan.sourceFrameIndices.size)

            for (sourceIndex in plan.sourceFrameIndices) {
                val bmp = try {
                    mmr.getFrameAtIndex(sourceIndex)
                } catch (_: Throwable) {
                    null
                } ?: continue

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

                frames += displayFrame
                sourceFrameIndices += sourceIndex
            }

            if (frames.size < 4) {
                frames.forEach { if (!it.isRecycled) it.recycle() }
                null
            } else {
                val localImpact = ImpactReplayExtractionIntegrity.exactImpactIndex(
                    extractedSourceFrameIndices = sourceFrameIndices,
                    sourceImpactFrame = plan.sourceImpactFrame
                )
                if (localImpact == null) {
                    frames.forEach { if (!it.isRecycled) it.recycle() }
                    return null
                }
                ImpactReplay(
                    frames = frames,
                    fps = captureFps,
                    impactIndex = localImpact,
                    sourceFrameIndices = sourceFrameIndices,
                    sourceImpactFrame = plan.sourceImpactFrame
                )
            }
        } finally {
            mmr.release()
        }
    }
}
