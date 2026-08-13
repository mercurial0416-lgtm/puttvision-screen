package com.puttvision.screen

class GameEngine {

    val settings = GreenSettings()
    val gameModes = GameModeEngine(settings)

    private val physics = GreenPhysics()

    @Volatile
    var currentShot: ShotMetrics? = null
        private set

    @Volatile
    var state: SimState? = null
        private set

    @Volatile
    var lastResult: SimResult? = null
        private set

    @Volatile
    var strokeScore: StrokeScore? = null
        private set

    @Volatile
    var coachFeedback: CoachFeedback? = null
        private set

    @Volatile
    var performanceSnapshot: V15PerformanceSnapshot? = null
        private set

    @Volatile
    var metricConfidence: V16MetricConfidence? = null
        private set

    @Volatile
    var personalCoachSnapshot: V16PersonalCoachSnapshot? = null
        private set

    @Volatile
    var dailyTrainingPlan: V16DailyTrainingPlan = V16TrainingPlanner.build(null)
        private set

    @Volatile
    var putterFitRecommendation: V15PutterFitRecommendation? = null
        private set

    @Volatile
    var ghostComparison: V15GhostComparison? = null
        private set

    @Volatile
    var recentRecords: List<ShotRecord> = emptyList()
        private set

    @Volatile
    var latestRecord: ShotRecord? = null
        private set

    @Volatile
    var matStimpEstimateM: Double? = null
        private set

    var onRecordFinalized: ((ShotRecord) -> Unit)? = null

    fun seedHistory(records: List<ShotRecord>) {
        recentRecords = records.takeLast(120)
        V15GhostRuntime.seed(records.takeLast(240))
        V15PutterFitRuntime.update(records)
        putterFitRecommendation = V15PutterFitRuntime.latest
        V16Runtime.update(records)
        personalCoachSnapshot = V16Runtime.personalCoach
        dailyTrainingPlan = V16Runtime.trainingPlan
    }

    @Synchronized
    fun launch(metrics: ShotMetrics) {
        // A secondary phone publishes its own measurement; a host consumes all recent companion
        // measurements through the existing confidence-weighted V15 fusion path.
        V16CompanionLinkRuntime.publishIfCompanion(metrics)
        val effectiveMetrics = V15CompanionRuntime.fusePrimary(metrics)
        currentShot = effectiveMetrics
        metricConfidence = V16MetricConfidenceEstimator.estimate(effectiveMetrics)
        lastResult = null
        latestRecord = null
        ghostComparison = null

        effectiveMetrics.estimatedMatStimpM?.let { estimate ->
            matStimpEstimateM =
                matStimpEstimateM?.let { old -> old * 0.78 + estimate * 0.22 } ?: estimate
        }

        performanceSnapshot = V15PerformanceAnalyzer.analyze(effectiveMetrics, recentRecords)
        strokeScore = StrokeScorer.score(
            metrics = effectiveMetrics,
            result = null,
            recent = recentRecords
        )

        coachFeedback = CoachEngine.diagnose(
            metrics = effectiveMetrics,
            score = strokeScore!!,
            recent = recentRecords
        )

        V15AutoFlowRuntime.rolling()
        state = physics.launch(effectiveMetrics, settings)
    }

    @Synchronized
    fun step(dt: Double): SimResult? {
        val s = state ?: return null
        if (!s.running) return lastResult

        val r = physics.step(s, settings, dt)
        if (r != null && lastResult == null) {
            lastResult = r
            val metrics = currentShot
            if (metrics != null) {
                val modeAtShot = gameModes.status.mode
                val targetDistance = settings.holeDistanceM
                val stimp = settings.stimpMeters
                val sideSlope = settings.sideSlopePct
                val longSlope = settings.longSlopePct

                val finalScore = StrokeScorer.score(metrics = metrics, result = r, recent = recentRecords)
                strokeScore = finalScore
                performanceSnapshot = V15PerformanceAnalyzer.analyze(metrics, recentRecords)
                metricConfidence = V16MetricConfidenceEstimator.estimate(metrics)
                coachFeedback = CoachEngine.diagnose(metrics = metrics, score = finalScore, recent = recentRecords)

                val record = ShotRecord(
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

                latestRecord = record
                ghostComparison = V15GhostRuntime.compare(record, s.trail)
                V15GhostRuntime.consider(record)
                recentRecords = (recentRecords + record).takeLast(120)
                V15PutterFitRuntime.update(recentRecords)
                putterFitRecommendation = V15PutterFitRuntime.latest
                V16Runtime.update(recentRecords)
                personalCoachSnapshot = V16Runtime.personalCoach
                dailyTrainingPlan = V16Runtime.trainingPlan
                onRecordFinalized?.invoke(record)
                gameModes.onResult(r)
                V15AutoFlowRuntime.result()
            }
        }
        return r
    }

    @Synchronized
    fun resetSimulation() {
        state = null
        currentShot = null
        lastResult = null
        strokeScore = null
        coachFeedback = null
        performanceSnapshot = null
        metricConfidence = null
        ghostComparison = null
        latestRecord = null
        V15AutoFlowRuntime.rearm()
    }
}
