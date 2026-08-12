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
        recentRecords = records.takeLast(40)
    }

    @Synchronized
    fun launch(metrics: ShotMetrics) {
        currentShot = metrics
        lastResult = null
        latestRecord = null

        metrics.estimatedMatStimpM?.let { estimate ->
            matStimpEstimateM =
                matStimpEstimateM?.let { old -> old * 0.78 + estimate * 0.22 } ?: estimate
        }

        strokeScore = StrokeScorer.score(
            metrics = metrics,
            result = null,
            recent = recentRecords
        )

        coachFeedback = CoachEngine.diagnose(
            metrics = metrics,
            score = strokeScore!!,
            recent = recentRecords
        )

        state = physics.launch(metrics, settings)
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
                recentRecords = (recentRecords + record).takeLast(40)
                onRecordFinalized?.invoke(record)
                gameModes.onResult(r)
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
        latestRecord = null
    }
}
