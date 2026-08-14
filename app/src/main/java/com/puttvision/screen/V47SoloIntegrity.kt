package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * V47 is a boundary layer: it does not change the measurement model. It prevents malformed,
 * stale, cross-profile or impossible values from contaminating simulation, history and coaching.
 */
data class V47ShotGuardReport(
    val accepted: Boolean,
    val metrics: ShotMetrics?,
    val sanitizedFields: List<String>,
    val rejectReason: String? = null
) {
    val qualityScore: Int
        get() = when {
            !accepted -> 0
            sanitizedFields.isEmpty() -> 100
            sanitizedFields.size <= 2 -> 88
            sanitizedFields.size <= 5 -> 72
            else -> 55
        }
}

object V47ShotGuard {
    fun normalize(raw: ShotMetrics): V47ShotGuardReport {
        if (!raw.ballSpeedMps.isFinite()) return reject("BALL_SPEED_NON_FINITE")
        if (raw.ballSpeedMps !in .08..8.0) return reject("BALL_SPEED_RANGE")
        if (!raw.launchAngleDeg.isFinite()) return reject("START_NON_FINITE")
        if (abs(raw.launchAngleDeg) > 18.0) return reject("START_RANGE")

        val changed = ArrayList<String>()
        fun optionalRange(name: String, value: Double?, min: Double, max: Double): Double? {
            if (value == null) return null
            if (!value.isFinite() || value !in min..max) {
                changed += name
                return null
            }
            return value
        }
        fun optionalAbs(name: String, value: Double?, maxAbs: Double): Double? {
            if (value == null) return null
            if (!value.isFinite() || abs(value) > maxAbs) {
                changed += name
                return null
            }
            return value
        }

        val head = optionalRange("HEAD_SPEED", raw.headSpeedMps, .02, 5.0)
        val face = optionalAbs("FACE", raw.faceAngleDeg, 25.0)
        val path = optionalAbs("PATH", raw.pathAngleDeg, 25.0)
        val impact = optionalAbs("IMPACT", raw.impactOffsetMm, 80.0)
        val backswing = optionalRange("BACKSWING_MS", raw.backswingMs, 50.0, 3_000.0)
        val downswing = optionalRange("DOWNSWING_MS", raw.downswingMs, 20.0, 2_000.0)
        val tempo = optionalRange("TEMPO", raw.tempoRatio, .50, 5.0)
        val length = optionalRange("BACKSWING_LENGTH", raw.backswingLengthCm, 1.0, 150.0)
        val acceleration = optionalRange("ACCELERATION", raw.peakHeadAccelerationMps2, .0, 100.0)
        val rawBall = optionalRange("RAW_BALL_SPEED", raw.rawBallSpeedMps, .05, 10.0)
        val decel = optionalRange("MAT_DECEL", raw.estimatedMatDecelMps2, .01, 10.0)
        val stimp = optionalRange("MAT_STIMP", raw.estimatedMatStimpM, .5, 10.0)

        val confidence = when {
            raw.confidence == null -> null
            !raw.confidence.isFinite() -> {
                changed += "CONFIDENCE"
                null
            }
            else -> {
                val clamped = raw.confidence.coerceIn(.15, .99)
                if (clamped != raw.confidence) changed += "CONFIDENCE"
                clamped
            }
        }

        val faceToPath = if (face != null && path != null) face - path else null
        if (raw.faceToPathDeg != faceToPath && (raw.faceToPathDeg != null || faceToPath != null)) changed += "FACE_TO_PATH"
        val smash = if (head != null && head > .03) (raw.ballSpeedMps / head).coerceIn(.05, 3.0) else null
        if (raw.smash != smash && (raw.smash != null || smash != null)) changed += "SMASH"

        return V47ShotGuardReport(
            accepted = true,
            metrics = raw.copy(
                headSpeedMps = head,
                faceAngleDeg = face,
                pathAngleDeg = path,
                faceToPathDeg = faceToPath,
                smash = smash,
                impactOffsetMm = impact,
                backswingMs = backswing,
                downswingMs = downswing,
                tempoRatio = tempo,
                backswingLengthCm = length,
                peakHeadAccelerationMps2 = acceleration,
                rawBallSpeedMps = rawBall,
                estimatedMatDecelMps2 = decel,
                estimatedMatStimpM = stimp,
                confidence = confidence
            ),
            sanitizedFields = changed.distinct()
        )
    }

    private fun reject(reason: String) = V47ShotGuardReport(false, null, emptyList(), reason)
}


data class V47RecordGuardReport(
    val record: ShotRecord,
    val normalizedFields: List<String>
)

object V47RecordGuard {
    fun normalize(raw: ShotRecord, profileIdRaw: String, nowMs: Long = System.currentTimeMillis()): V47RecordGuardReport {
        val changed = ArrayList<String>()
        val profileId = profileIdRaw.ifBlank { "owner" }
        val shot = V47ShotGuard.normalize(raw.metrics)
        val metrics = shot.metrics ?: raw.metrics
        changed += shot.sanitizedFields

        fun finiteOr(name: String, value: Double, fallback: Double): Double {
            if (!value.isFinite()) {
                changed += name
                return fallback
            }
            return value
        }

        val target = finiteOr("TARGET_DISTANCE", raw.targetDistanceM, .0).coerceIn(.0, 50.0).also {
            if (it != raw.targetDistanceM) changed += "TARGET_DISTANCE"
        }
        val stimp = finiteOr("GREEN_STIMP", raw.stimpMeters, 2.8).coerceIn(1.5, 5.0).also {
            if (it != raw.stimpMeters) changed += "GREEN_STIMP"
        }
        val side = finiteOr("SIDE_SLOPE", raw.sideSlopePct, .0).coerceIn(-10.0, 10.0).also {
            if (it != raw.sideSlopePct) changed += "SIDE_SLOPE"
        }
        val long = finiteOr("LONG_SLOPE", raw.longSlopePct, .0).coerceIn(-10.0, 10.0).also {
            if (it != raw.longSlopePct) changed += "LONG_SLOPE"
        }
        val terrain = raw.terrainProfileId.takeIf { it in -1..23 } ?: -1.also { changed += "TERRAIN" }
        val profile = raw.userProfileId.ifBlank {
            changed += "PROFILE"
            profileId
        }
        val timestamp = when {
            raw.timestampMs <= 0L || raw.timestampMs > nowMs + V47HistoryGuard.FUTURE_ALLOWANCE_MS -> {
                changed += "TIMESTAMP"
                nowMs
            }
            else -> raw.timestampMs
        }

        val result = raw.result?.let { r ->
            if (!r.finishX.isFinite() || !r.finishY.isFinite() || !r.distanceToCupM.isFinite() || !r.elapsedSec.isFinite()) {
                changed += "RESULT"
                null
            } else {
                val normalized = r.copy(
                    distanceToCupM = r.distanceToCupM.coerceAtLeast(.0),
                    elapsedSec = r.elapsedSec.coerceIn(.0, 20.5),
                    lipOut = r.lipOut && !r.holed,
                    cupContacts = r.cupContacts.coerceIn(0, 20)
                )
                if (normalized != r) changed += "RESULT"
                normalized
            }
        }
        val score = normalizeScore(raw.strokeScore).also {
            if (it != raw.strokeScore) changed += "STROKE_SCORE"
        }

        return V47RecordGuardReport(
            raw.copy(
                metrics = metrics,
                result = result,
                strokeScore = score,
                targetDistanceM = target,
                stimpMeters = stimp,
                sideSlopePct = side,
                longSlopePct = long,
                terrainProfileId = terrain,
                userProfileId = profile,
                timestampMs = timestamp
            ),
            changed.distinct()
        )
    }

    private fun normalizeScore(s: StrokeScore) = StrokeScore(
        total = s.total.coerceIn(0, 100),
        face = s.face.coerceIn(0, 100),
        path = s.path.coerceIn(0, 100),
        tempo = s.tempo.coerceIn(0, 100),
        impact = s.impact.coerceIn(0, 100),
        distance = s.distance.coerceIn(0, 100),
        consistency = s.consistency.coerceIn(0, 100)
    )
}


data class V47HistoryReport(
    val records: List<ShotRecord>,
    val inputCount: Int,
    val droppedProfile: Int,
    val droppedTimestamp: Int,
    val droppedShot: Int,
    val droppedDuplicates: Int,
    val normalizedRecords: Int
) {
    val droppedTotal: Int get() = droppedProfile + droppedTimestamp + droppedShot + droppedDuplicates
    val cleanRatio: Double get() = if (inputCount <= 0) 1.0 else (records.size.toDouble() / inputCount).coerceIn(.0, 1.0)
    val label: String get() = "HISTORY · ${records.size}/$inputCount · DROP $droppedTotal · FIX $normalizedRecords"
}

object V47HistoryGuard {
    const val MAX_RECORDS = 120
    const val FUTURE_ALLOWANCE_MS = 5L * 60L * 1000L

    fun prepare(
        recordsRaw: List<ShotRecord>,
        profileIdRaw: String,
        nowMs: Long = System.currentTimeMillis()
    ): V47HistoryReport {
        val profileId = profileIdRaw.ifBlank { "owner" }
        var droppedProfile = 0
        var droppedTimestamp = 0
        var droppedShot = 0
        var droppedDuplicates = 0
        var normalizedRecords = 0
        val prepared = ArrayList<ShotRecord>()
        val duplicateKeys = HashSet<String>()

        recordsRaw.sortedBy { it.timestampMs }.forEach { raw ->
            val rawProfile = raw.userProfileId.ifBlank { "owner" }
            if (rawProfile != profileId) {
                droppedProfile++
                return@forEach
            }
            if (raw.timestampMs <= 0L || raw.timestampMs > nowMs + FUTURE_ALLOWANCE_MS) {
                droppedTimestamp++
                return@forEach
            }
            val shot = V47ShotGuard.normalize(raw.metrics)
            if (!shot.accepted || shot.metrics == null) {
                droppedShot++
                return@forEach
            }
            val normalized = V47RecordGuard.normalize(raw.copy(metrics = shot.metrics), profileId, nowMs)
            val record = normalized.record
            val key = duplicateKey(record)
            if (!duplicateKeys.add(key)) {
                droppedDuplicates++
                return@forEach
            }
            if (normalized.normalizedFields.isNotEmpty()) normalizedRecords++
            prepared += record
        }

        return V47HistoryReport(
            records = prepared.takeLast(MAX_RECORDS),
            inputCount = recordsRaw.size,
            droppedProfile = droppedProfile,
            droppedTimestamp = droppedTimestamp,
            droppedShot = droppedShot,
            droppedDuplicates = droppedDuplicates,
            normalizedRecords = normalizedRecords
        )
    }

    private fun duplicateKey(r: ShotRecord): String = buildString {
        append(r.timestampMs).append('|')
        append(r.userProfileId).append('|')
        append(r.metrics.ballSpeedMps.toBits()).append('|')
        append(r.metrics.launchAngleDeg.toBits()).append('|')
        append(r.targetDistanceM.toBits())
    }
}


data class V47HealthSection(
    val name: String,
    val score: Int,
    val status: String,
    val detail: String,
    val optional: Boolean = false
)

data class V47SoloHealthInput(
    val shot: V47ShotGuardReport?,
    val history: V47HistoryReport?,
    val hfr: V43HfrHealthSummary,
    val hfrFailures: V45HfrFailureSummary,
    val companion: V16CompanionUiStatus,
    val stereo: V44StereoReadiness
)

data class V47SoloHealthSnapshot(
    val score: Int,
    val grade: String,
    val insufficientData: Boolean,
    val sections: List<V47HealthSection>,
    val topIssue: String,
    val nextActions: List<String>
) {
    val shortLabel: String
        get() = if (insufficientData) "DATA · $score/100 · $topIssue" else "$grade · $score/100 · $topIssue"
}

object V47SoloHealthEngine {
    fun build(input: V47SoloHealthInput): V47SoloHealthSnapshot {
        val history = input.history
        val cleanRecords = history?.records.orEmpty()
        val shotSection = when (val shot = input.shot) {
            null -> V47HealthSection("SHOT", 55, "WAIT", "이번 실행의 검증 샷 없음")
            else -> when {
                !shot.accepted -> V47HealthSection("SHOT", 0, "BLOCK", shot.rejectReason ?: "측정값 거부")
                shot.sanitizedFields.isEmpty() -> V47HealthSection("SHOT", 100, "CLEAN", "필수/선택 측정값 범위 정상")
                else -> V47HealthSection("SHOT", shot.qualityScore, "FIXED", shot.sanitizedFields.joinToString(", "))
            }
        }
        val historySection = when {
            history == null -> V47HealthSection("HISTORY", 50, "WAIT", "기록 검증 전")
            history.records.size < 8 -> V47HealthSection("HISTORY", 55, "DATA", "유효 ${history.records.size}구 · 8구 이상 필요")
            history.cleanRatio < .70 -> V47HealthSection("HISTORY", 45, "DIRTY", history.label)
            history.droppedTotal > 0 || history.normalizedRecords > 0 -> V47HealthSection("HISTORY", 78, "CLEANED", history.label)
            else -> V47HealthSection("HISTORY", 96, "CLEAN", history.label)
        }
        val hfrSection = when {
            input.hfr.samples == 0 -> V47HealthSection("HFR", 60, "DATA", "장시간 분석 표본 없음")
            input.hfr.degraded -> V47HealthSection("HFR", 42, "SLOW", input.hfr.label)
            input.hfr.p95TotalMs <= 2_500L && input.hfr.p95CalibrationMs <= 900L -> V47HealthSection("HFR", 94, "GOOD", input.hfr.label)
            else -> V47HealthSection("HFR", 76, "WATCH", input.hfr.label)
        }
        val failureRatio = if (input.hfrFailures.samples > 0) input.hfrFailures.topReasonCount.toDouble() / input.hfrFailures.samples else .0
        val failureSection = when {
            input.hfrFailures.samples == 0 -> V47HealthSection("HFR FAIL", 96, "CLEAN", "최근 실패 기록 없음")
            failureRatio >= .50 -> V47HealthSection("HFR FAIL", 48, "REPEAT", input.hfrFailures.label)
            else -> V47HealthSection("HFR FAIL", 72, "WATCH", input.hfrFailures.label)
        }
        val companion = input.companion
        val companionSection = when (companion.role) {
            V16CompanionRole.OFF -> V47HealthSection("MULTI PHONE", 85, "OFF", "솔플에서는 선택 기능", optional = true)
            V16CompanionRole.HOST -> {
                val total = companion.received + companion.rejected
                val rejectRatio = if (total <= 0L) .0 else companion.rejected.toDouble() / total
                when {
                    companion.peers <= 0 -> V47HealthSection("MULTI PHONE", 64, "WAIT", "메인폰 대기 · 연결 0대", optional = true)
                    rejectRatio >= .20 -> V47HealthSection("MULTI PHONE", 48, "REJECT", "거부 ${companion.rejected}/$total", optional = true)
                    else -> V47HealthSection("MULTI PHONE", 92, "GOOD", companion.label, optional = true)
                }
            }
            V16CompanionRole.COMPANION -> {
                val sync = companion.syncLabel.orEmpty()
                val bad = sync.contains("STALE", true) || sync.contains("BAD", true) || sync.contains("오래", true) || sync.contains("불량", true)
                if (bad) V47HealthSection("MULTI PHONE", 48, "SYNC", sync, optional = true)
                else V47HealthSection("MULTI PHONE", 88, "SYNC", sync.ifBlank { companion.label }, optional = true)
            }
        }
        val stereoSection = if (companion.role == V16CompanionRole.OFF) {
            V47HealthSection("STEREO", 80, "OFF", "멀티폰 사용 시 평가", optional = true)
        } else if (input.stereo.ready) {
            V47HealthSection("STEREO", (85 + input.stereo.score / 7).coerceAtMost(100), "TRACK READY", input.stereo.reason, optional = true)
        } else {
            V47HealthSection("STEREO", input.stereo.score.coerceIn(25, 75), "PREP", input.stereo.reason, optional = true)
        }

        val faceCoverage = coverage(cleanRecords) { it.metrics.faceAngleDeg != null }
        val pathCoverage = coverage(cleanRecords) { it.metrics.pathAngleDeg != null }
        val impactCoverage = coverage(cleanRecords) { it.metrics.impactOffsetMm != null }
        val coverageAvg = listOf(faceCoverage, pathCoverage, impactCoverage).average()
        val coverageSection = when {
            cleanRecords.size < 8 -> V47HealthSection("DATA COVERAGE", 50, "DATA", "유효 ${cleanRecords.size}구 · FACE/PATH/IMPACT 표본 부족")
            coverageAvg >= .80 -> V47HealthSection("DATA COVERAGE", 96, "GOOD", coverageLabel(faceCoverage, pathCoverage, impactCoverage))
            coverageAvg >= .55 -> V47HealthSection("DATA COVERAGE", 72, "PARTIAL", coverageLabel(faceCoverage, pathCoverage, impactCoverage))
            else -> V47HealthSection("DATA COVERAGE", 45, "LOW", coverageLabel(faceCoverage, pathCoverage, impactCoverage))
        }

        val sections = listOf(shotSection, historySection, hfrSection, failureSection, companionSection, stereoSection, coverageSection)
        val required = sections.filterNot { it.optional }
        val overall = if (required.isEmpty()) 0 else required.map { it.score }.average().roundToInt().coerceIn(0, 100)
        val insufficient = cleanRecords.size < 8 || input.shot == null
        val grade = when {
            overall >= 90 -> "A"
            overall >= 80 -> "B"
            overall >= 68 -> "C"
            overall >= 55 -> "D"
            else -> "E"
        }

        val actions = ArrayList<String>()
        if (input.shot?.accepted == false) actions += "이번 샷이 차단됨 · 카메라/보정/검출 상태부터 확인"
        if (cleanRecords.size < 8) actions += "유효 샷을 ${8 - cleanRecords.size}구 더 확보해 기준선을 만드세요"
        if (history != null && history.cleanRatio < .85) actions += "가져온 기록에 비정상/다른 프로필 데이터가 많음 · 백업 원본 확인"
        if (input.hfr.degraded) actions += "HFR P95 지연 큼 · 기기 온도/240fps 지속 부하를 확인"
        if (failureRatio >= .50) actions += "HFR ${input.hfrFailures.topReason?.name ?: "FAIL"} 실패가 반복됨 · 해당 단계 재점검"
        if (coverageAvg < .55 && cleanRecords.size >= 8) actions += "FACE/PATH/IMPACT 커버리지 낮음 · 퍼터 마커/카메라 시점을 조정"
        if (companion.role != V16CompanionRole.OFF && companionSection.score < 65) actions += "멀티폰 패킷/SYNC 품질 확인"
        if (companion.role != V16CompanionRole.OFF && !input.stereo.ready) actions += "STEREO PREP · ${input.stereo.reason}"
        if (actions.isEmpty()) actions += "현재 소프트웨어 무결성은 안정적 · 실기 기준장비 검증을 계속 누적"

        val worst = required.minByOrNull { it.score }
        val issue = worst?.let { "${it.name} ${it.status}" } ?: "데이터 없음"
        return V47SoloHealthSnapshot(overall, grade, insufficient, sections, issue, actions.distinct().take(3))
    }

    private fun coverage(records: List<ShotRecord>, present: (ShotRecord) -> Boolean): Double =
        if (records.isEmpty()) .0 else records.count(present).toDouble() / records.size

    private fun coverageLabel(face: Double, path: Double, impact: Double): String =
        "FACE ${(face * 100).roundToInt()}% · PATH ${(path * 100).roundToInt()}% · IMPACT ${(impact * 100).roundToInt()}%"
}

object V47SoloIntegrityRuntime {
    @Volatile var latestShot: V47ShotGuardReport? = null
        private set
    @Volatile var latestHistory: V47HistoryReport? = null
        private set

    fun recordShot(value: V47ShotGuardReport) {
        latestShot = value
    }

    fun recordHistory(value: V47HistoryReport) {
        latestHistory = value
    }

    fun health(nowMs: Long = System.currentTimeMillis()): V47SoloHealthSnapshot = V47SoloHealthEngine.build(
        V47SoloHealthInput(
            shot = latestShot,
            history = latestHistory,
            hfr = V43HfrHealthWindow.summary(),
            hfrFailures = V45HfrFailureRuntime.summary(),
            companion = V16CompanionLinkRuntime.status(),
            stereo = V44StereoPrepRuntime.snapshot(nowMs)
        )
    )
}
