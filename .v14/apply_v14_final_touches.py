from pathlib import Path


def rep(path, old, new, label):
    p=Path(path); s=p.read_text()
    if old not in s: raise SystemExit(f'MISSING {label}')
    p.write_text(s.replace(old,new,1))

# NORMAL analyzer emits an immediate, provisional impact event too.
p=Path('app/src/main/java/com/puttvision/screen/ShotVisionAnalyzer.kt')
s=p.read_text()
s=s.replace(
'''    private val baselineMarkerPoints: List<PointF> = emptyList(),
    private val onCalibrationDrift: (CalibrationDriftSnapshot) -> Unit = {},
    private val onShotReady: (ShotMetrics) -> Unit
) : ImageAnalysis.Analyzer {''',
'''    private val baselineMarkerPoints: List<PointF> = emptyList(),
    private val onCalibrationDrift: (CalibrationDriftSnapshot) -> Unit = {},
    private val onImpactDetected: (QuickImpactEstimate) -> Unit = {},
    private val onShotReady: (ShotMetrics) -> Unit
) : ImageAnalysis.Analyzer {''',1)
s=s.replace(
'''    private var putterReadiness = 0.0
    private val driftWatchdog''',
'''    private var putterReadiness = 0.0
    private var impactNotified = false
    private var previousMappedBall: PointF? = null
    private var previousMappedNs: Long = 0L
    private val driftWatchdog''',1)
s=s.replace(
'''            val t = image.imageInfo.timestamp

            ball?.let {
                val p = homography.map(it)
                if (p.x.isFinite() && p.y.isFinite()) tracker.addBall(BallSample(p, t))
            }
''',
'''            val t = image.imageInfo.timestamp

            if (tracker.isArmed() && !tracker.hasImpact() && impactNotified) {
                impactNotified = false
                previousMappedBall = null
                previousMappedNs = 0L
            }

            ball?.let {
                val mapped = homography.map(it)
                if (mapped.x.isFinite() && mapped.y.isFinite()) {
                    val beforeImpact = tracker.hasImpact()
                    tracker.addBall(BallSample(mapped, t))
                    if (!beforeImpact && tracker.hasImpact() && !impactNotified) {
                        val old = previousMappedBall
                        val oldNs = previousMappedNs
                        var speed: Double? = null
                        var angle: Double? = null
                        if (old != null && oldNs > 0L) {
                            val dt = (t - oldNs) / 1_000_000_000.0
                            if (dt in .006..0.100) {
                                val dx = (mapped.x - old.x).toDouble()
                                val dy = (mapped.y - old.y).toDouble()
                                val v = (hypot(dx, dy) / 100.0) / dt
                                val a = Math.toDegrees(kotlin.math.atan2(dx, dy))
                                if (v in .16..5.2 && abs(a) <= 22.0) {
                                    speed = v
                                    angle = a
                                }
                            }
                        }
                        impactNotified = true
                        onImpactDetected(QuickImpactEstimate(speed, angle, if (speed != null) .55 else .34, t))
                    }
                    previousMappedBall = PointF(mapped.x, mapped.y)
                    previousMappedNs = t
                }
            }
''',1)
p.write_text(s)

# Wire NORMAL immediate impact into the same TV prediction runtime.
p=Path('app/src/main/java/com/puttvision/screen/MainActivity.kt')
s=p.read_text()
anchor='''                onCalibrationDrift = { drift ->
                    runOnUiThread { handleCalibrationDrift(drift) }
                },
                onShotReady = { metrics ->'''
replacement='''                onCalibrationDrift = { drift ->
                    runOnUiThread { handleCalibrationDrift(drift) }
                },
                onImpactDetected = { quick ->
                    runOnUiThread {
                        if (!sessionActive || measurementSuspended || offlineTestMode) return@runOnUiThread
                        if (!TvInstantRollRuntime.isActive()) {
                            TvInstantRollRuntime.begin(
                                engine.settings,
                                quick,
                                GreenReadRuntime.peekOrSchedule(engine.settings)
                            )
                            overlay.status = "IMPACT · TV LIVE · NORMAL 분석"
                        }
                    }
                },
                onShotReady = { metrics ->'''
if anchor not in s: raise SystemExit('MISSING main normal analyzer anchor')
s=s.replace(anchor,replacement,1)
p.write_text(s)

# Stop computing predictive physics after the simulated ball has finished.
p=Path('app/src/main/java/com/puttvision/screen/V14VisionSystems.kt')
s=p.read_text()
old='''        val state = physics.launch(metrics, copied)
        val out = ArrayList<TvLivePoint>(600)
        var t = 0.0
        out += TvLivePoint(0.0, 0.0, 0.0)
        repeat(750) {
            val r = physics.step(state, copied, .012)
            t += .012
            if (it % 1 == 0) out += TvLivePoint(t, state.x, state.y)
            if (r != null) return@repeat
        }
'''
new='''        val state = physics.launch(metrics, copied)
        val out = ArrayList<TvLivePoint>(600)
        var t = 0.0
        out += TvLivePoint(0.0, 0.0, 0.0)
        var stepIndex = 0
        while (stepIndex < 750) {
            val r = physics.step(state, copied, .012)
            t += .012
            out += TvLivePoint(t, state.x, state.y)
            stepIndex++
            if (r != null) break
        }
'''
if old not in s: raise SystemExit('MISSING live prediction loop')
s=s.replace(old,new,1)
p.write_text(s)

print('V14 final touches applied')
