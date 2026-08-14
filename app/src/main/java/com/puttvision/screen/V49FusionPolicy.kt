package com.puttvision.screen

import kotlin.math.max

/** Candidate admission and view-diversity policy for local multi-phone fusion. */
object V49FusionPolicy {
    const val MAX_AGE_MS = 1_300L
    const val FUTURE_ALLOWANCE_MS = 250L

    data class Selection(
        val measurements: List<V15CameraMeasurement>,
        val droppedInvalid: Int,
        val droppedStale: Int,
        val droppedSameView: Int,
        val companionViews: Set<V15CameraView>
    )

    fun select(raw: List<V15CameraMeasurement>, nowMs: Long): Selection {
        var invalid = 0
        var stale = 0
        val admissible = raw.mapNotNull { m ->
            if (m.cameraId.isBlank() || !m.confidence.isFinite() || m.confidence !in .05..1.0) {
                invalid++
                return@mapNotNull null
            }
            val age = nowMs - m.receivedAtMs
            if (age !in -FUTURE_ALLOWANCE_MS..MAX_AGE_MS) {
                stale++
                return@mapNotNull null
            }
            m
        }.groupBy { it.cameraId }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.receivedAtMs } }

        val primary = admissible.filter { it.view == V15CameraView.PRIMARY }
            .maxByOrNull { candidateScore(it, nowMs) }
        val companions = admissible.filter { it.view != V15CameraView.PRIMARY }

        // One camera per physical view prevents two FACE_ON phones from double-voting the same evidence.
        val onePerView = companions.groupBy { it.view }.values.mapNotNull { sameView ->
            sameView.maxByOrNull { candidateScore(it, nowMs) }
        }
        val droppedSameView = companions.size - onePerView.size
        val selected = buildList {
            if (primary != null) add(primary)
            addAll(onePerView)
        }
        return Selection(
            measurements = selected,
            droppedInvalid = invalid,
            droppedStale = stale,
            droppedSameView = droppedSameView,
            companionViews = onePerView.mapTo(linkedSetOf()) { it.view }
        )
    }

    fun confidenceSupportBonus(agreeingViews: Set<V15CameraView>, acceptedFeatures: Int): Double {
        val distinct = agreeingViews.count { it != V15CameraView.PRIMARY }
        val viewBonus = when (distinct) {
            0 -> 0.0
            1 -> .025
            2 -> .060
            else -> .085
        }
        return (viewBonus + acceptedFeatures.coerceAtMost(8) * .0025).coerceAtMost(.10)
    }

    fun confidenceCeiling(companionViews: Set<V15CameraView>): Double = when (companionViews.size) {
        0 -> .98
        1 -> .94
        2 -> .975
        else -> .99
    }

    private fun candidateScore(m: V15CameraMeasurement, nowMs: Long): Double {
        val age = max(0L, nowMs - m.receivedAtMs)
        val freshness = (1.0 - age.toDouble() / MAX_AGE_MS).coerceIn(.0, 1.0)
        return m.confidence * .72 + freshness * .28
    }
}
