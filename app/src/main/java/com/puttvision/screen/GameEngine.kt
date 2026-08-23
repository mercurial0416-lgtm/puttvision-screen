package com.puttvision.screen

object V26ProductSettingsRuntime {
    @Volatile var settings: GreenSettings = GreenSettings()
}

class GameEngine {

    val settings = GreenSettings().also { V26ProductSettingsRuntime.settings = it }
    val gameModes = GameModeEngine(settings)

    private val physics = GreenPhysics()

    // GreenPhysics owns and mutates this object only while GameEngine is synchronized. Presentation
    // threads never receive it directly; they consume the deep snapshot published through state.
    private var physicsState: SimState? = null

    // LAB-only one-shot start override. Normal gameplay never sets this, so its launch path remains
    // exactly V26BallStartRuntime.current(settings). The value is consumed by the next accepted shot.
    private var nextLabStartOverride: Pair<Double, Double>? = null

    @Volatile var currentShot: ShotMetrics? = null
        private set
    @Volatile var state: SimState? = null
        private set
    @Volatile var lastResult: SimResult? = null
        private set
    @Volatile var strokeScore: StrokeScore? = null
        private set
    @Volatile var coachFeedback: CoachFeedback? = null
        private set
    @Volatile var performanceSnapshot: V15PerformanceSnapshot? = null
        private set
    @Volatile var metricConfidence: V16MetricConfidence? = null
        private set
    @Volatile var personalCoachSnapshot: V16PersonalCoachSnapshot? = null
        private set
    @Volatile var adaptiveCoachSnapshot: V46AdaptiveCoachSnapshot? = null
        private set
    @Volatile var dailyTrainingPlan: V16DailyTrainingPlan = V16TrainingPlanner.build(null)
        private set
    @Volatile var putterFitRecommendation: V15PutterFitRecommendation? = null
        private set
    @Volatile var ghostComparison: V15GhostComparison? = null
        private set
    @Volatile var strokeStudio: V19StrokeStudioModel? = null
        private set
    @Volatile var readFeedback: V20ReadFeedback = V20ReadFeedback(active = false, revealed = false)
        private set
    @Volatile var performanceCompare: V20PerformanceReport = V20PerformanceRuntime.report
        private set
    @Volatile var recentRecords: List<ShotRecord> = emptyList()
        private set
    @Volatile var latestRecord: ShotRecord? = null
        private set
    @Volatile var matStimpEstimateM: Double? = null
        private set
    @Volatile var virtualStartAtShot: Pair<Double, Double> = 0.0 to 0.0
        private set

    var onRecordFinalized: ((ShotRecord) -> Unit)? = null

    init {
        V31TrainingSessionRuntime.bind(this)
    }

    /**
     * Places only the next synthetic LAB shot at a deterministic virtual coordinate.
     * This is intentionally not a gameplay assist: regular camera/sensor shots never call it.
     */
    @Synchronized
    fun setNextLabShotStart(x: Double, y: Double) {
        if (!x.isFinite() || !y.isFinite()) {
            nextLabStartOverride = null
            return
        }
        nextLabStartOverride = x to y
    }

    fun seedHistory(records: List<ShotRecord>) {
        val prepared = V47HistoryGuard.prepare(records, ProductSessionRuntime.userProfileId)
        V47SoloIntegrityRuntime.recordHistory(prepared)
        val clean = prepared.records
        recentRecords = clean.takeLast(120)
        V15GhostRuntime.seed(clean.takeLast(240))
        V15PutterFitRuntime.update(recentRecords)
        V16PutterFit2Runtime.update(recentRecords)
        putterFitRecommendation = V15PutterFitRuntime.latest
        V16Runtime.update(recentRecords)
        personalCoachSnapshot = V16Runtime.personalCoach
        V46AdaptiveCoachRuntime.update(recentRecords)
        adaptiveCoachSnapshot = V46AdaptiveCoachRuntime.snapshot
        dailyTrainingPlan = V46AdaptiveTrainingPlan.adapt(V16Runtime.trainingPlan, adaptiveCoachSnapshot)
        V20PerformanceRuntime.update(recentRecords)
        performanceCompare = V20PerformanceRuntime.report
    }

    @Synchronized
    fun launch(metrics: ShotMetrics) {
        val deviceAdjusted = V16DeviceAutoCalibrationRuntime.applyFallback(metrics)
        val primaryGuard = V47ShotGuard.normalize(deviceAdjusted)
        if (!primaryGuard.accepted || primaryGuard.metrics == null) {
            rejectShot(primaryGuard)
            return
        }

        val cleanPrimary = primaryGuard.metrics
        V16CompanionLinkRuntime.publishIfCompanion(cleanPrimary)
        val fused = V21CaptureConsistencyRuntime.adjust(
            V37FeatureFusionRuntime.fusePrimary(cleanPrimary)
        )
        val rawFinalGuard = V47ShotGuard.normalize(fused)
        val finalGuard = rawFinalGuard.copy(
            sanitizedFields = (primaryGuard.sanitizedFields + rawFinalGuard.sanitizedFields).distinct()
        )
        V47SoloIntegrityRuntime.recordShot(finalGuard)
        val effectiveMetrics = finalGuard.metrics
        if (!finalGuard.accepted || effectiveMetrics == null) {
            rejectShot(finalGuard)
            return
        }

        currentShot = effectiveMetrics
        metricConfidence = V16MetricConfidenceEstimator.estimate(effectiveMetrics)
        lastResult = null
        latestRecord = null
        ghostComparison = null

        V19StrokeStudioRuntime.update(effectiveMetrics, recentRecords)
        strokeStudio = V19StrokeStudioRuntime.latest
        V20GreenReadTrainingRuntime.prepare(gameModes.status.mode, settings)
        V20GreenReadTrainingRuntime.commit(effectiveMetrics, gameModes.status.mode, settings)
        readFeedback = V20GreenReadTrainingRuntime.feedback

        effectiveMetrics.estimatedMatStimpM?.let { estimate ->
            matStimpEstimateM = matStimpEstimateM?.let { old -> old * 0.78 + estimate * 0.22 } ?: estimate
        }

        performanceSnapshot = V15PerformanceAnalyzer.analyze(effectiveMetrics, recentRecords)
        strokeScore = StrokeScorer.score(effectiveMetrics, null, recentRecords)
        coachFeedback = CoachEngine.diagnose(effectiveMetrics, strokeScore!!, recentRecords)
        V15AutoFlowRuntime.rolling()
        V22AudioRuntime.launch(effectiveMetrics.ballSpeedMps)
        virtualStartAtShot = nextLabStartOverride?.also { nextLabStartOverride = null }
            ?: V26BallStartRuntime.current(settings)
        val launched = physics.launch(effectiveMetrics, settings, virtualStartAtShot.first, virtualStartAtShot.second)
        physicsState = launched
        publishPhysicsFrame(launched)
    }

    @Synchronized
    fun step(dt: Double): SimResult? {
        val s = physicsState ?: return null
        if (!s.running) {
            publishPhysicsFrame(s)
            return lastResult
        }

        // Normal 60 Hz frames remain exactly one GreenPhysics step. If the main thread stalls, split
        // the missed wall-clock interval into the physics engine's supported <=25 ms slices instead
        // of silently discarding time through GreenPhysics' per-step dt clamp.
        var remaining = dt.takeIf { it.isFinite() && it > 0.0 }?.coerceAtMost(0.20) ?: 0.016
        if (remaining < 0.001) remaining = 0.001
        var r: SimResult? = null
        while (remaining >= 0.001 && r == null) {
            val slice = minOf(0.025, remaining)
            r = physics.step(s, settings, slice)
            publishPhysicsFrame(s)
            remaining -= slice
        }

        if (r != null && lastResult == null) {
            lastResult = r
            V22AudioRuntime.result(r)
            val metrics = currentShot
            if (metrics != null) {
                val modeAtShot = gameModes.status.mode
                val targetDistance = kotlin.math.hypot(
                    virtualStartAtShot.first,
                    settings.holeDistanceM - virtualStartAtShot.second
                )
                val stimp = settings.stimpMeters
                val sideSlope = settings.sideSlopePct
                val longSlope = settings.longSlopePct

                val finalScore = StrokeScorer.score(metrics, r, recentRecords)
                strokeScore = finalScore
                performanceSnapshot = V15PerformanceAnalyzer.analyze(metrics, recentRecords)
                metricConfidence = V16MetricConfidenceEstimator.estimate(metrics)
                coachFeedback = CoachEngine.diagnose(metrics, finalScore, recentRecords)
                V16DeviceAutoCalibrationRuntime.observe(metrics)
                V20GreenReadTrainingRuntime.reveal(settings)
                readFeedback = V20GreenReadTrainingRuntime.feedback

                val rawRecord = ShotRecord(
                    metrics = metrics,
                    result = r,
                    strokeScore = finalScore,
                    mode = modeAtShot,
                    targetDistanceM = targetDistance,
                    stimpMeters = stimp,
                    sideSlopePct = sideSlope,
                    longSlopePct = longSlope,
                    terrainProfileId = settings.terrainProfileId,
                    putterProfileName = ProductRuntime.putterProfileName,
                    putterHeadWidthCm = ProductRuntime.putterHeadWidthCm,
                    physicalMatStimpM = ProductRuntime.physicalMatStimpM,
                    userProfileId = ProductSessionRuntime.userProfileId
                )
                val record = V47RecordGuard.normalize(rawRecord, ProductSessionRuntime.userProfileId).record

                latestRecord = record
                val originShot = kotlin.math.abs(virtualStartAtShot.first) < .005 && kotlin.math.abs(virtualStartAtShot.second) < .005
                ghostComparison = if (originShot) V15GhostRuntime.compare(record, s.trail) else null
                if (originShot) V15GhostRuntime.consider(record)
                val history = V47HistoryGuard.prepare(recentRecords + record, ProductSessionRuntime.userProfileId)
                V47SoloIntegrityRuntime.recordHistory(history)
                recentRecords = history.records
                V15PutterFitRuntime.update(recentRecords)
                V16PutterFit2Runtime.update(recentRecords)
                putterFitRecommendation = V15PutterFitRuntime.latest
                V16Runtime.update(recentRecords)
                personalCoachSnapshot = V16Runtime.personalCoach
                V46AdaptiveCoachRuntime.update(recentRecords)
                adaptiveCoachSnapshot = V46AdaptiveCoachRuntime.snapshot
                dailyTrainingPlan = V46AdaptiveTrainingPlan.adapt(V16Runtime.trainingPlan, adaptiveCoachSnapshot)
                V20PerformanceRuntime.update(recentRecords)
                performanceCompare = V20PerformanceRuntime.report
                V31TrainingSessionRuntime.onRecord(record)
                onRecordFinalized?.invoke(record)
                record.result?.let(gameModes::onResult)
                V15AutoFlowRuntime.result()
            }
        }
        return r
    }

    @Synchronized
    fun resetSimulation() {
        physicsState = null
        state = null
        currentShot = null
        lastResult = null
        strokeScore = null
        coachFeedback = null
        performanceSnapshot = null
        metricConfidence = null
        ghostComparison = null
        latestRecord = null
        virtualStartAtShot = 0.0 to 0.0
        nextLabStartOverride = null
        V19StrokeStudioRuntime.clear()
        strokeStudio = null
        V20GreenReadTrainingRuntime.prepare(gameModes.status.mode, settings)
        readFeedback = V20GreenReadTrainingRuntime.feedback
        V15AutoFlowRuntime.rearm()
    }

    private fun rejectShot(report: V47ShotGuardReport) {
        V47SoloIntegrityRuntime.recordShot(report)
        physicsState = null
        state = null
        currentShot = null
        lastResult = null
        strokeScore = null
        coachFeedback = null
        performanceSnapshot = null
        metricConfidence = null
        ghostComparison = null
        latestRecord = null
        nextLabStartOverride = null
        V15AutoFlowRuntime.rearm()
    }

    private fun publishPhysicsFrame(source: SimState?) {
        // Volatile publication of a NEW object gives the GL/UI thread a happens-before edge for every
        // scalar and the copied trail, while GreenPhysics keeps mutating its private authoritative state.
        state = V126PhysicsFrameBridge.snapshot(source)
    }
}
