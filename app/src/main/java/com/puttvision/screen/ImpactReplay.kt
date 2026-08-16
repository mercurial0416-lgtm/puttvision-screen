package com.puttvision.screen

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ImpactReplayTimingProvenance {
    FRAME_DURATION_COHERENT,
    UNVERIFIED
}

data class ImpactReplay(
    val frames: List<Bitmap>,
    val fps: Int,
    val impactIndex: Int,
    val sourceFrameIndices: List<Int> = emptyList(),
    val sourceImpactFrame: Int? = null,
    val timingProvenance: ImpactReplayTimingProvenance = ImpactReplayTimingProvenance.UNVERIFIED
) {
    fun relativeTimeMsAt(frameIndex: Int): Double? {
        if (fps <= 0 || frameIndex !in frames.indices) return null
        val sourceFrame = sourceFrameIndices.getOrNull(frameIndex)
        val impactFrame = sourceImpactFrame
        return if (sourceFrame != null && impactFrame != null) {
            if (timingProvenance != ImpactReplayTimingProvenance.FRAME_DURATION_COHERENT) return null
            (sourceFrame - impactFrame) * 1000.0 / fps.toDouble()
        } else {
            (frameIndex - impactIndex) * 1000.0 / fps.toDouble()
        }
    }
}

data class ImpactReplayTimingCheck(
    val coherent: Boolean,
    val expectedDurationMs: Double,
    val observedDurationMs: Long,
    val relativeError: Double
)

object ImpactReplayTimingPolicy {
    fun evaluate(
        totalFrames: Int,
        captureFps: Int,
        durationMs: Long,
        maxRelativeError: Double = 0.12
    ): ImpactReplayTimingCheck {
        if (totalFrames <= 0 || captureFps <= 0 || durationMs <= 0L || !maxRelativeError.isFinite() || maxRelativeError < 0.0) {
            return ImpactReplayTimingCheck(false, 0.0, durationMs, Double.POSITIVE_INFINITY)
        }

        val expectedDurationMs = totalFrames * 1000.0 / captureFps.toDouble()
        val relativeError = abs(durationMs.toDouble() - expectedDurationMs) / max(1.0, expectedDurationMs)
        return ImpactReplayTimingCheck(
            coherent = relativeError <= maxRelativeError,
            expectedDurationMs = expectedDurationMs,
            observedDurationMs = durationMs,
            relativeError = relativeError
        )
    }
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
            val durationMs = mmr.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            val timingCheck = ImpactReplayTimingPolicy.evaluate(total, captureFps, durationMs)
            val timingProvenance = if (timingCheck.coherent) {
                ImpactReplayTimingProvenance.FRAME_DURATION_COHERENT
            } else {
                ImpactReplayTimingProvenance.UNVERIFIED
            }
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
                val localImpact = sourceFrameIndices.indices.minByOrNull {
                    abs(sourceFrameIndices[it] - plan.sourceImpactFrame)
                } ?: 0
                ImpactReplay(
                    frames = frames,
                    fps = captureFps,
                    impactIndex = localImpact.coerceIn(0, frames.lastIndex),
                    sourceFrameIndices = sourceFrameIndices,
                    sourceImpactFrame = plan.sourceImpactFrame,
                    timingProvenance = timingProvenance
                )
            }
        } finally {
            mmr.release()
        }
    }
}
