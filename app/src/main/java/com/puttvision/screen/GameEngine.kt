package com.puttvision.screen

object V26ProductSettingsRuntime {
    @Volatile var settings: GreenSettings = GreenSettings()
}

class GameEngine {

    val settings = GreenSettings().also { V26ProductSettingsRuntime.settings = it }
    val gameModes = GameModeEngine(settings)

    private val physics = GreenPhysics()

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

    fun seedHistory(records: List<ShotRecord>) {
        recentRecords = records.takeLast(120)
        V15GhostRuntime.seed(records.takeLast(240))
        V15PutterFitRuntime.update(records)
        V16PutterFit2Runtime.update(records)
        putterFitRecommendation = V15PutterFitRuntime.latest
        V16Runtime.update(records)
        personalCoachSnapshot = V16Runtime.personalCoach
        dailyTrainingPlan = V16Runtime.trainingPlan
        V20PerformanceRuntime.update(records)
        performanceCompare = V20PerformanceRuntime.report
    }

    @Synchronized
    fun launch(metrics: ShotMetrics) {
        val deviceAdjusted = V16DeviceAutoCalibrationRuntime.applyFallback(metrics)
        V16CompanionLinkRuntime.publishIfCompanion(deviceAdjusted)
        val effectiveMetrics = V21CaptureConsistencyRuntime.adjust(
            V15CompanionRuntime.fusePrimary(deviceAdjusted)
        )
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
        virtualStartAtShot = V26BallStartRuntime.current(settings)
        state = physics.launch(effectiveMetrics, settings, virtualStartAtShot.first, virtualStartAtShot.second)
    }

    @Synchronized
    fun step(dt: Double): SimResult? {
        val s = state ?: return null
        if (!s.running) return lastResult

        val r = physics.step(s, settings, dt)
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
                val originShot = kotlin.math.abs(virtualStartAtShot.first) < .005 && kotlin.math.abs(virtualStartAtShot.second) < .005
                ghostComparison = if (originShot) V15GhostRuntime.compare(record, s.trail) else null
                if (originShot) V15GhostRuntime.consider(record)
                recentRecords = (recentRecords + record).takeLast(120)
                V15PutterFitRuntime.update(recentRecords)
                V16PutterFit2Runtime.update(recentRecords)
                putterFitRecommendation = V15PutterFitRuntime.latest
                V16Runtime.update(recentRecords)
                personalCoachSnapshot = V16Runtime.personalCoach
                dailyTrainingPlan = V16Runtime.trainingPlan
                V20PerformanceRuntime.update(recentRecords)
                performanceCompare = V20PerformanceRuntime.report
                V31TrainingSessionRuntime.onRecord(record)
                V33OnlineOutbox.onRecord(record)
                V34WeeklyRuntime.onRecord(record)
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
        virtualStartAtShot = 0.0 to 0.0
        V19StrokeStudioRuntime.clear()
        strokeStudio = null
        V20GreenReadTrainingRuntime.prepare(gameModes.status.mode, settings)
        readFeedback = V20GreenReadTrainingRuntime.feedback
        V15AutoFlowRuntime.rearm()
    }
}
