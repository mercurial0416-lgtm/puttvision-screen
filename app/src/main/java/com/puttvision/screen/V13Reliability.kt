package com.puttvision.screen

import android.content.Context
import android.graphics.PointF
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Per-metric one-sigma-ish measurement bounds shown to the user as ± values. */
data class MeasurementUncertainty(
    val ballSpeedMps: Double,
    val launchDeg: Double,
    val headSpeedMps: Double?,
    val faceDeg: Double?,
    val pathDeg: Double?,
    val impactMm: Double?,
    val basis: String
) {
    fun compact(): String = buildString {
        append("BALL ±${"%.02f".format(ballSpeedMps)}m/s")
        append(" · START ±${"%.02f".format(launchDeg)}°")
        headSpeedMps?.let { append(" · HEAD ±${"%.02f".format(it)}m/s") }
        faceDeg?.let { append(" · FACE ±${"%.02f".format(it)}°") }
        pathDeg?.let { append(" · PATH ±${"%.02f".format(it)}°") }
    }
}

object MeasurementUncertaintyEstimator {
    fun forHfr(
        fps: Int,
        ballDetectionRatio: Double,
        headDetectionRatio: Double,
        matDecelAvailable: Boolean,
        ballSpeedMps: Double,
        headSpeedMps: Double?,
        impactOffsetMm: Double?
    ): MeasurementUncertainty {
        val f = fps.coerceAtLeast(60).toDouble()
        val ballMiss = (1.0 - ballDetectionRatio.coerceIn(0.0, 1.0))
        val headMiss = (1.0 - headDetectionRatio.coerceIn(0.0, 1.0))
        val framePenalty = (120.0 / f).coerceIn(.35, 2.0)
        val ball = max(.012, ballSpeedMps * (.010 + ballMiss * .070) + framePenalty * .006 + if (matDecelAvailable) 0.0 else .012)
        val launch = (.055 + ballMiss * .72 + framePenalty * .035).coerceIn(.06, 1.20)
        val head = headSpeedMps?.let { max(.018, it * (.018 + headMiss * .12) + framePenalty * .008) }
        val face = if (headSpeedMps != null) (.075 + headMiss * .75 + framePenalty * .03).coerceIn(.08, 1.35) else null
        val path = if (headSpeedMps != null) (.085 + headMiss * .82 + framePenalty * .04).coerceIn(.09, 1.45) else null
        val impact = impactOffsetMm?.let { (.55 + headMiss * 3.2 + framePenalty * .22).coerceIn(.6, 5.5) }
        return MeasurementUncertainty(ball, launch, head, face, path, impact, "HFR ${fps}fps")
    }

    fun forNormal(
        ballSpeedMps: Double,
        headSpeedMps: Double?,
        faceAngleDeg: Double?,
        pathAngleDeg: Double?,
        impactOffsetMm: Double?,
        confidence: Double = .45
    ): MeasurementUncertainty {
        val q = confidence.coerceIn(.15, 1.0)
        val miss = 1.0 - q
        return MeasurementUncertainty(
            ballSpeedMps = max(.055, ballSpeedMps * (.035 + miss * .08)),
            launchDeg = (.32 + miss * .78).coerceAtMost(1.35),
            headSpeedMps = headSpeedMps?.let { max(.09, it * (.055 + miss * .11)) },
            faceDeg = faceAngleDeg?.let { (.45 + miss * .90).coerceAtMost(1.55) },
            pathDeg = pathAngleDeg?.let { (.48 + miss * .95).coerceAtMost(1.65) },
            impactMm = impactOffsetMm?.let { (1.8 + miss * 3.0).coerceAtMost(5.8) },
            basis = "NORMAL"
        )
    }

    fun synthetic(): MeasurementUncertainty = MeasurementUncertainty(
        ballSpeedMps = .02,
        launchDeg = .08,
        headSpeedMps = .03,
        faceDeg = .10,
        pathDeg = .11,
        impactMm = .7,
        basis = "SIM"
    )
}

data class CalibrationDriftSnapshot(
    val driftPx: Double,
    val coherentMarkers: Int,
    val responseScore: Double,
    val blocked: Boolean,
    val stableBadHits: Int,
    val detail: String
)

/**
 * Low-cost calibration watchdog. It does not re-decode QR payloads on every frame.
 * Instead it follows the high-frequency QR texture around each of the four points.
 * The first stable observations establish each marker's local texture bias, then
 * only coherent translation across 3+ markers can trigger a recalibration.
 */
class CalibrationDriftWatchdog(
    private val baseline: List<PointF>,
    private val blockThresholdPx: Double = 11.0,
    private val recoverThresholdPx: Double = 6.0
) {
    private var referenceDx: Double? = null
    private var referenceDy: Double? = null
    private var badHits = 0

    fun evaluateLuma(
        luma: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): CalibrationDriftSnapshot? {
        if (baseline.size != 4 || width < 160 || height < 120) return null
        val offsets = baseline.mapNotNull { point ->
            locateTextureOffset(luma, width, height, rowStride, pixelStride, point)
        }
        if (offsets.size < 3) {
            badHits = max(0, badHits - 1)
            return CalibrationDriftSnapshot(
                driftPx = 0.0,
                coherentMarkers = offsets.size,
                responseScore = offsets.map { it.third }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
                blocked = false,
                stableBadHits = badHits,
                detail = "마커 텍스처 ${offsets.size}/4 · 감시 대기"
            )
        }

        val dxs = offsets.map { it.first }
        val dys = offsets.map { it.second }
        val medianDx = median(dxs)
        val medianDy = median(dys)
        val coherent = offsets.count { hypot(it.first - medianDx, it.second - medianDy) <= 8.5 }
        if (coherent < 3) {
            badHits = max(0, badHits - 1)
            return CalibrationDriftSnapshot(0.0, coherent, offsets.map { it.third }.average(), false, badHits, "마커 이동 방향 불일치 · 감시 유지")
        }

        if (referenceDx == null || referenceDy == null) {
            referenceDx = medianDx
            referenceDy = medianDy
            badHits = 0
            return CalibrationDriftSnapshot(0.0, coherent, offsets.map { it.third }.average(), false, 0, "CAL WATCH 기준점 고정")
        }

        val drift = hypot(medianDx - referenceDx!!, medianDy - referenceDy!!)
        when {
            drift >= blockThresholdPx -> badHits++
            drift <= recoverThresholdPx -> badHits = max(0, badHits - 1)
        }
        val blocked = badHits >= 3
        return CalibrationDriftSnapshot(
            driftPx = drift,
            coherentMarkers = coherent,
            responseScore = offsets.map { it.third }.average(),
            blocked = blocked,
            stableBadHits = badHits,
            detail = if (blocked) "거치대/카메라 이동 ${"%.1f".format(drift)}px · 재보정" else "CAL WATCH ${"%.1f".format(drift)}px · ${coherent}/4"
        )
    }

    private fun locateTextureOffset(
        luma: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        center: PointF
    ): Triple<Double, Double, Double>? {
        var bestScore = 0.0
        var bestDx = 0
        var bestDy = 0
        val search = 36
        var oy = -search
        while (oy <= search) {
            var ox = -search
            while (ox <= search) {
                val cx = center.x.toInt() + ox
                val cy = center.y.toInt() + oy
                val score = textureScore(luma, width, height, rowStride, pixelStride, cx, cy)
                if (score > bestScore) {
                    bestScore = score
                    bestDx = ox
                    bestDy = oy
                }
                ox += 4
            }
            oy += 4
        }
        if (bestScore < 18.0) return null
        return Triple(bestDx.toDouble(), bestDy.toDouble(), bestScore)
    }

    private fun textureScore(
        luma: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        cx: Int,
        cy: Int
    ): Double {
        val half = 16
        if (cx - half < 0 || cy - half < 0 || cx + half >= width || cy + half >= height) return 0.0
        var sum = 0.0
        var sum2 = 0.0
        var edge = 0.0
        var count = 0
        var y = cy - half
        while (y <= cy + half) {
            var x = cx - half
            var prev = -1
            while (x <= cx + half) {
                val idx = y * rowStride + x * pixelStride
                if (idx !in luma.indices) return 0.0
                val v = luma[idx].toInt() and 0xff
                sum += v
                sum2 += v.toDouble() * v
                if (prev >= 0) edge += abs(v - prev)
                prev = v
                count++
                x += 4
            }
            y += 4
        }
        if (count < 40) return 0.0
        val mean = sum / count
        if (mean < 22.0 || mean > 236.0) return 0.0
        val variance = max(0.0, sum2 / count - mean * mean)
        val edgeMean = edge / count
        val balance = 1.0 - abs(mean - 128.0) / 128.0
        return kotlin.math.sqrt(variance) * .55 + edgeMean * .55 + balance * 8.0
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }
}

/** Persistent V13 Green Read cache. Memory cache remains the fast path. */
class GreenReadDiskCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("puttvision_green_read_v13", Context.MODE_PRIVATE)

    fun get(key: GreenReadKey): GreenRead? {
        val stable = stableKey(key)
        val raw = prefs.getString(slot(stable), null) ?: return null
        return runCatching {
            val j = JSONObject(raw)
            if (j.optString("key") != stable) return@runCatching null
            val trailArray = j.optJSONArray("trail") ?: JSONArray()
            val trail = ArrayList<Pair<Double, Double>>(trailArray.length())
            for (i in 0 until trailArray.length()) {
                val p = trailArray.optJSONArray(i) ?: continue
                if (p.length() >= 2) trail += p.optDouble(0, 0.0) to p.optDouble(1, 0.0)
            }
            GreenRead(
                estimatedBreakCm = j.getDouble("break"),
                aimOffsetCm = j.getDouble("aim"),
                cupCount = j.getDouble("cups"),
                putterHeadCount = j.getDouble("heads"),
                aimSideLabel = j.getString("side"),
                effectiveSideSlopePct = j.getDouble("sideSlope"),
                effectiveLongSlopePct = j.getDouble("longSlope"),
                paceHint = j.getString("pace"),
                recommendedBallSpeedMps = j.getDouble("speed"),
                recommendedLaunchAngleDeg = j.getDouble("angle"),
                solverMissCm = j.getDouble("miss"),
                solverReliable = j.getBoolean("reliable"),
                predictedTrail = trail
            )
        }.getOrNull()
    }

    fun put(key: GreenReadKey, read: GreenRead) {
        val stable = stableKey(key)
        val trail = JSONArray()
        val points = if (read.predictedTrail.size <= 120) read.predictedTrail else read.predictedTrail.filterIndexed { index, _ -> index % 2 == 0 }.take(120)
        points.forEach { (x, y) -> trail.put(JSONArray().put(x).put(y)) }
        val j = JSONObject().apply {
            put("key", stable)
            put("break", read.estimatedBreakCm)
            put("aim", read.aimOffsetCm)
            put("cups", read.cupCount)
            put("heads", read.putterHeadCount)
            put("side", read.aimSideLabel)
            put("sideSlope", read.effectiveSideSlopePct)
            put("longSlope", read.effectiveLongSlopePct)
            put("pace", read.paceHint)
            put("speed", read.recommendedBallSpeedMps)
            put("angle", read.recommendedLaunchAngleDeg)
            put("miss", read.solverMissCm)
            put("reliable", read.solverReliable)
            put("trail", trail)
        }
        prefs.edit().putString(slot(stable), j.toString()).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private fun stableKey(key: GreenReadKey): String = listOf(
        key.profile, key.distance100, key.stimp100, key.side100, key.long100, key.putter100,
        key.startX100, key.startY100, key.pace100, key.customGreenHash
    ).joinToString(":")

    private fun slot(stable: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray())
        return "r_" + digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
