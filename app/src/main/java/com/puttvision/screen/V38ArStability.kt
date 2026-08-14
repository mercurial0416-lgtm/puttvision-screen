package com.puttvision.screen

import kotlin.math.hypot
import kotlin.math.max

data class V38Point(val x: Float, val y: Float)
data class V38CornerDecision(
    val points: List<V38Point>,
    val stable: Boolean,
    val reacquired: Boolean,
    val stability: Double
)

/**
 * Keeps AR locked through normal 1-2px calibration jitter, but refuses to smear a real camera move.
 * Detector corner ordering is canonicalized against the current lock before motion is evaluated.
 * A large move must repeat for three frames before the new geometry is adopted.
 */
class V38CornerStabilizer {
    private var locked: List<V38Point>? = null
    private var candidate: List<V38Point>? = null
    private var candidateFrames = 0
    private var stableFrames = 0

    fun update(input: List<V38Point>, width: Int, height: Int): V38CornerDecision? {
        if (input.size != 4 || width <= 0 || height <= 0 || input.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        val diagonal = hypot(width.toDouble(), height.toDouble()).coerceAtLeast(1.0)
        val jitterPx = max(1.5, diagonal * .0025)
        val movePx = max(8.0, diagonal * .012)
        val current = locked
        if (current == null) {
            locked = input.map { it.copy() }
            stableFrames = 1
            return V38CornerDecision(locked!!, stable = false, reacquired = false, stability = .45)
        }

        val aligned = alignTo(current, input)
        val maxDelta = current.indices.maxOf { distance(current[it], aligned[it]) }
        val meanDelta = current.indices.map { distance(current[it], aligned[it]) }.average()
        if (maxDelta <= movePx) {
            candidate = null
            candidateFrames = 0
            val alpha = if (maxDelta <= jitterPx) .18f else .08f
            locked = current.indices.map { i ->
                V38Point(
                    current[i].x + (aligned[i].x - current[i].x) * alpha,
                    current[i].y + (aligned[i].y - current[i].y) * alpha
                )
            }
            stableFrames = (stableFrames + 1).coerceAtMost(30)
            val normalized = (meanDelta / movePx).coerceIn(.0, 1.0)
            val score = (.98 - normalized * .28).coerceIn(.60, .98)
            return V38CornerDecision(locked!!, stable = stableFrames >= 2, reacquired = false, stability = score)
        }

        val priorCandidate = candidate
        val candidateInput = if (priorCandidate != null) alignTo(priorCandidate, input) else aligned
        val candidateConsistent = priorCandidate != null && priorCandidate.indices.maxOf {
            distance(priorCandidate[it], candidateInput[it])
        } <= jitterPx * 2.0
        if (candidateConsistent) {
            candidate = priorCandidate!!.indices.map { i ->
                V38Point(
                    priorCandidate[i].x + (candidateInput[i].x - priorCandidate[i].x) * .35f,
                    priorCandidate[i].y + (candidateInput[i].y - priorCandidate[i].y) * .35f
                )
            }
            candidateFrames++
        } else {
            candidate = aligned.map { it.copy() }
            candidateFrames = 1
        }
        stableFrames = 0

        if (candidateFrames >= 3) {
            locked = candidate!!.map { it.copy() }
            candidate = null
            candidateFrames = 0
            stableFrames = 2
            return V38CornerDecision(locked!!, stable = true, reacquired = true, stability = .72)
        }
        return V38CornerDecision(current, stable = false, reacquired = false, stability = .20)
    }

    fun reset() {
        locked = null
        candidate = null
        candidateFrames = 0
        stableFrames = 0
    }

    private fun alignTo(reference: List<V38Point>, input: List<V38Point>): List<V38Point> {
        if (reference.size != 4 || input.size != 4) return input
        var best = input
        var bestCost = Double.POSITIVE_INFINITY
        val used = BooleanArray(4)
        val order = IntArray(4)

        fun visit(depth: Int) {
            if (depth == 4) {
                var cost = 0.0
                for (i in 0 until 4) {
                    val d = distance(reference[i], input[order[i]])
                    cost += d * d
                }
                if (cost < bestCost) {
                    bestCost = cost
                    best = order.map { input[it] }
                }
                return
            }
            for (i in 0 until 4) {
                if (used[i]) continue
                used[i] = true
                order[depth] = i
                visit(depth + 1)
                used[i] = false
            }
        }

        visit(0)
        return best
    }

    private fun distance(a: V38Point, b: V38Point): Double = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
}

/** Smooths only the same solver/read configuration; new greens and camera reacquisition snap immediately. */
class V38PathStabilizer {
    private var key: String? = null
    private var previous: List<V38Point>? = null

    fun update(next: List<V38Point>, nextKey: String, hardReset: Boolean = false): List<V38Point> {
        if (next.isEmpty()) return emptyList()
        val old = previous
        if (hardReset || key != nextKey || old == null || old.size != next.size) {
            key = nextKey
            previous = next.map { it.copy() }
            return previous!!
        }
        val alpha = .34f
        val smoothed = next.indices.map { i ->
            V38Point(
                old[i].x + (next[i].x - old[i].x) * alpha,
                old[i].y + (next[i].y - old[i].y) * alpha
            )
        }
        previous = smoothed
        return smoothed
    }

    fun reset() {
        key = null
        previous = null
    }
}
