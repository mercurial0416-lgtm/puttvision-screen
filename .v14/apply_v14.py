from pathlib import Path
import re

ROOT = Path('.')

def read(path): return (ROOT/path).read_text()
def write(path, text): (ROOT/path).write_text(text)
def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'MISSING {label}')
    return text.replace(old, new, 1)

def regex_rep(text, pattern, repl, label, flags=re.S):
    out, n = re.subn(pattern, repl, text, count=1, flags=flags)
    if n != 1:
        raise SystemExit(f'MISSING/AMBIG {label}: {n}')
    return out

# ---------- HFR analyzer V2 ----------
p = Path('app/src/main/java/com/puttvision/screen/HfrVideoAnalyzer.kt')
s = p.read_text()
s = rep(s,
'''    private data class Detection(\n        val ballPx: PointF?,\n        val heelPx: PointF?,\n        val toePx: PointF?\n    )''',
'''    private data class Detection(\n        val ballPx: PointF?,\n        val heelPx: PointF?,\n        val toePx: PointF?,\n        val markerAngleDeg: Double? = null\n    )''', 'hfr detection model')
s = rep(s,
'''    private data class Sample(\n        val frame: Int,\n        val ballCm: PointF?,\n        val heelCm: PointF?,\n        val toeCm: PointF?\n    )''',
'''    private data class Sample(\n        val frame: Int,\n        val ballCm: PointF?,\n        val heelCm: PointF?,\n        val toeCm: PointF?,\n        val markerAngleDeg: Double? = null\n    )''', 'hfr sample model')
s = rep(s, 'class HfrVideoAnalyzer {\n', '''class HfrVideoAnalyzer {\n\n    private val v14Vision = V14BitmapVisionTracker()\n    private val frameCache = LinkedHashMap<Int, Bitmap>()\n''', 'hfr fields')
s = rep(s, '            mmr.setDataSource(file.absolutePath)\n', '''            mmr.setDataSource(file.absolutePath)\n            clearFrameCache()\n            v14Vision.reset()\n''', 'hfr reset')
s = rep(s, '''            val samples = ArrayList<Sample>(end - start + 1)\n            var prevBall = startBall\n\n            for (frame in start..end) {''', '''            val samples = ArrayList<Sample>(end - start + 1)\n            var prevBall = startBall\n            v14Vision.reset()\n\n            for (frame in start..end) {''', 'hfr precision reset')
s = rep(s, '                samples += Sample(frame, ballCm, heelCm, toeCm)\n', '                samples += Sample(frame, ballCm, heelCm, toeCm, d.markerAngleDeg)\n', 'hfr marker sample')
s = rep(s, '''        } finally {\n            mmr.release()\n        }\n    }\n\n    private fun safeFrame(''', '''        } finally {\n            clearFrameCache()\n            mmr.release()\n        }\n    }\n\n    /** SIM CAMERA 2.0: runs the same adaptive detector and kinematics without camera hardware. */\n    fun analyzeSynthetic(\n        frames: List<Bitmap>,\n        fps: Int,\n        homography: Homography\n    ): HfrAnalysisResult? {\n        if (frames.size < 20 || fps < 60) return null\n        val tracker = V14BitmapVisionTracker()\n        var origin: PointF? = null\n        var impact = -1\n        val samples = ArrayList<Sample>(frames.size)\n        frames.forEachIndexed { index, bmp ->\n            val d = tracker.detect(bmp, wantPutter = true)\n            val ball = d.ballPx?.let(homography::map)?.takeIf { it.x.isFinite() && it.y.isFinite() }\n            val heel = d.heelPx?.let(homography::map)?.takeIf { it.x.isFinite() && it.y.isFinite() }\n            val toe = d.toePx?.let(homography::map)?.takeIf { it.x.isFinite() && it.y.isFinite() }\n            if (origin == null && ball != null && abs(ball.x) < 30f && ball.y in -20f..40f) origin = PointF(ball.x, ball.y)\n            val o = origin\n            if (impact < 0 && o != null && ball != null && hypot((ball.x-o.x).toDouble(), (ball.y-o.y).toDouble()) >= .8) impact = index\n            samples += Sample(index, ball, heel, toe, d.markerAngleDeg)\n        }\n        val o = origin ?: return null\n        if (impact < 0) return null\n        val calculated = calculate(samples, o, fps) ?: return null\n        return HfrAnalysisResult(calculated.first, fps, calculated.second, samples.size)\n    }\n\n    private fun clearFrameCache() {\n        frameCache.values.forEach { if (!it.isRecycled) it.recycle() }\n        frameCache.clear()\n    }\n\n    private fun safeFrame(''', 'hfr synthetic insertion')
# Replace safeFrame function with batched extraction.
s = regex_rep(s, r'''    private fun safeFrame\(\n        mmr: MediaMetadataRetriever,\n        index: Int\n    \): Bitmap\? =\n        try \{\n            mmr\.getFrameAtIndex\(index\)\n        \} catch \(_: Throwable\) \{\n            null\n        \}\n''', '''    private fun safeFrame(\n        mmr: MediaMetadataRetriever,\n        index: Int\n    ): Bitmap? {\n        frameCache[index]?.let { if (!it.isRecycled) return it }\n        val batch = runCatching { mmr.getFramesAtIndex(index, 6) }.getOrNull()\n        if (!batch.isNullOrEmpty()) {\n            batch.forEachIndexed { offset, bitmap -> frameCache[index + offset] = bitmap }\n            while (frameCache.size > 24) {\n                val key = frameCache.keys.firstOrNull() ?: break\n                val old = frameCache.remove(key)\n                if (old != null && !old.isRecycled) old.recycle()\n            }\n            frameCache[index]?.let { if (!it.isRecycled) return it }\n        }\n        return runCatching { mmr.getFrameAtIndex(index) }.getOrNull()\n    }\n''', 'hfr batch decoder')
# Homography from all marker points.
s = rep(s, '''        Homography.fromFourPoints(\n            layout.imagePoints,\n            layout.realPointsCm\n        )?.let {''', '''        Homography.fromPoints(\n            layout.fitImagePoints,\n            layout.fitRealPointsCm,\n            FrameInfo(bmp.width, bmp.height, 0)\n        )?.let {''', 'hfr multi marker fit')
# QR parser success body -> shared resolver.
s = regex_rep(s, r'''            \.addOnSuccessListener \{ codes ->.*?\n            \}\n            \.addOnCompleteListener''', '''            .addOnSuccessListener { codes ->\n                val observations = codes.mapNotNull { code ->\n                    val box = code.boundingBox ?: return@mapNotNull null\n                    V14QrObservation(\n                        code.rawValue,\n                        PointF(box.exactCenterX(), box.exactCenterY()),\n                        box.width().toLong() * box.height().toLong()\n                    )\n                }\n                resolved = V14MarkerResolver.resolve(observations)\n            }\n            .addOnCompleteListener''', 'hfr shared qr parser')
# Detector body -> adaptive tracker. Keep calculate signature following it.
s = regex_rep(s, r'''    private fun detect\(\n        source: Bitmap,\n        h: Homography,\n        previousBallCm: PointF\?,\n        wantPutter: Boolean\n    \): Detection \{.*?\n    \}\n\n    private fun calculate\(''', '''    private fun detect(\n        source: Bitmap,\n        h: Homography,\n        previousBallCm: PointF?,\n        wantPutter: Boolean\n    ): Detection {\n        val d = v14Vision.detect(source, wantPutter)\n        return Detection(d.ballPx, d.heelPx, d.toePx, d.markerAngleDeg)\n    }\n\n    private fun calculate(''', 'hfr adaptive detector')
# One-point speed section -> robust multi-frame fit.
s = regex_rep(s, r'''        // Ball speed at roughly 10cm\..*?        val matData = estimateMat\(samples, impactPos, fps, rawSpeed\)\n        val correctedBallSpeed = matData\.correctedImpactSpeedMps''', '''        // V14: fit multiple early post-impact centroids instead of trusting one 10cm frame.\n        val fitPoints = samples.drop(impactPos).mapNotNull { sample ->\n            val ball = sample.ballCm ?: return@mapNotNull null\n            val d = hypot((ball.x-origin.x).toDouble(), (ball.y-origin.y).toDouble())\n            if (d !in .5..19.0) return@mapNotNull null\n            V14TimedPoint(\n                (sample.frame - impactFrame).toDouble() / fps,\n                (ball.x-origin.x).toDouble(),\n                (ball.y-origin.y).toDouble()\n            )\n        }.take(28)\n        val robust = V14RobustKinematics.fit(fitPoints) ?: return null\n        val rawSpeed = robust.speedMps\n        val launchAngle = robust.launchAngleDeg\n        val matData = estimateMat(samples, impactPos, fps, rawSpeed)\n        val correctedBallSpeed = matData.correctedImpactSpeedMps''', 'hfr robust fit')
# Roll computation before uncertainty.
s = rep(s, '''        val uncertainty = MeasurementUncertaintyEstimator.forHfr(''', '''        val rollSamples = samples.drop(impactPos).mapNotNull { sample ->\n            val ball = sample.ballCm ?: return@mapNotNull null\n            val marker = sample.markerAngleDeg ?: return@mapNotNull null\n            val d = hypot((ball.x-origin.x).toDouble(), (ball.y-origin.y).toDouble())\n            V14BallRollAnalyzer.MarkerSample(sample.frame, d, marker)\n        }\n        val roll = V14BallRollAnalyzer.analyze(rollSamples, fps, correctedBallSpeed)\n\n        val uncertainty = MeasurementUncertaintyEstimator.forHfr(''', 'hfr ball roll')
s = rep(s, '''            confidence = confidence,\n            uncertainty = uncertainty\n        ) to impactFrame''', '''            confidence = confidence,\n            roll = roll,\n            uncertainty = uncertainty\n        ) to impactFrame''', 'hfr roll metric output')
p.write_text(s)

# ---------- Stats DB V4: marked ball roll persistence ----------
p = Path('app/src/main/java/com/puttvision/screen/StatsRepository.kt')
s = p.read_text()
s = rep(s, '''    val uncertaintyBasis: String?,\n\n    val scoreTotal: Int,''', '''    val uncertaintyBasis: String?,\n    val rollSpinRpm: Double?,\n    val rollSkidDistanceCm: Double?,\n    val rollStartDistanceCm: Double?,\n    val rollMarkedBall: Boolean,\n    val rollConfidence: Double?,\n\n    val scoreTotal: Int,''', 'stats roll columns')
s = rep(s, '@Database(entities = [ShotEntity::class], version = 3, exportSchema = false)', '@Database(entities = [ShotEntity::class], version = 4, exportSchema = false)', 'stats db version')
s = rep(s, '''private val MIGRATION_2_3 = object : Migration(2, 3) {\n    override fun migrate(db: SupportSQLiteDatabase) {\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyBallSpeedMps REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyLaunchDeg REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyHeadSpeedMps REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyFaceDeg REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyPathDeg REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyImpactMm REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyBasis TEXT")\n        db.execSQL("ALTER TABLE shots ADD COLUMN lipOut INTEGER NOT NULL DEFAULT 0")\n        db.execSQL("ALTER TABLE shots ADD COLUMN cupContacts INTEGER NOT NULL DEFAULT 0")\n    }\n}\n''', '''private val MIGRATION_2_3 = object : Migration(2, 3) {\n    override fun migrate(db: SupportSQLiteDatabase) {\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyBallSpeedMps REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyLaunchDeg REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyHeadSpeedMps REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyFaceDeg REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyPathDeg REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyImpactMm REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyBasis TEXT")\n        db.execSQL("ALTER TABLE shots ADD COLUMN lipOut INTEGER NOT NULL DEFAULT 0")\n        db.execSQL("ALTER TABLE shots ADD COLUMN cupContacts INTEGER NOT NULL DEFAULT 0")\n    }\n}\n\nprivate val MIGRATION_3_4 = object : Migration(3, 4) {\n    override fun migrate(db: SupportSQLiteDatabase) {\n        db.execSQL("ALTER TABLE shots ADD COLUMN rollSpinRpm REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN rollSkidDistanceCm REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN rollStartDistanceCm REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN rollMarkedBall INTEGER NOT NULL DEFAULT 0")\n        db.execSQL("ALTER TABLE shots ADD COLUMN rollConfidence REAL")\n    }\n}\n''', 'stats migration')
s = rep(s, '.addMigrations(MIGRATION_1_2, MIGRATION_2_3)', '.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)', 'stats migration register')
s = rep(s, '''            uncertaintyImpactMm = m.uncertainty?.impactMm,\n            uncertaintyBasis = m.uncertainty?.basis,\n            scoreTotal = score.total,''', '''            uncertaintyImpactMm = m.uncertainty?.impactMm,\n            uncertaintyBasis = m.uncertainty?.basis,\n            rollSpinRpm = m.roll?.spinRpm,\n            rollSkidDistanceCm = m.roll?.skidDistanceCm,\n            rollStartDistanceCm = m.roll?.rollStartDistanceCm,\n            rollMarkedBall = m.roll?.markedBall ?: false,\n            rollConfidence = m.roll?.confidence,\n            scoreTotal = score.total,''', 'stats to entity roll')
s = rep(s, '''            confidence = e.confidence,\n            uncertainty = if (e.uncertaintyBallSpeedMps != null''', '''            confidence = e.confidence,\n            roll = if (e.rollMarkedBall || e.rollSpinRpm != null || e.rollStartDistanceCm != null) {\n                BallRollMetrics(e.rollSpinRpm, e.rollSkidDistanceCm, e.rollStartDistanceCm, e.rollMarkedBall, e.rollConfidence ?: 0.0)\n            } else null,\n            uncertainty = if (e.uncertaintyBallSpeedMps != null''', 'stats to record roll')
s = rep(s, '''        putNullable("rawBall", m.rawBallSpeedMps); putNullable("matDecel", m.estimatedMatDecelMps2); putNullable("matStimp", m.estimatedMatStimpM); putNullable("confidence", m.confidence)\n        m.uncertainty?.let''', '''        putNullable("rawBall", m.rawBallSpeedMps); putNullable("matDecel", m.estimatedMatDecelMps2); putNullable("matStimp", m.estimatedMatStimpM); putNullable("confidence", m.confidence)\n        m.roll?.let { rr -> put("roll", JSONObject().apply { putNullable("rpm", rr.spinRpm); putNullable("skid", rr.skidDistanceCm); putNullable("start", rr.rollStartDistanceCm); put("marked", rr.markedBall); put("confidence", rr.confidence) }) }\n        m.uncertainty?.let''', 'stats json roll export')
s = rep(s, '''        val m = ShotMetrics(\n            ballSpeedMps = j.getDouble("ball"), launchAngleDeg = j.getDouble("launch"), headSpeedMps = j.optNullableDouble("head"), faceAngleDeg = j.optNullableDouble("face"), pathAngleDeg = j.optNullableDouble("path"), faceToPathDeg = j.optNullableDouble("f2p"), smash = j.optNullableDouble("smash"), impactOffsetMm = j.optNullableDouble("impact"), measuredAtNs = 0L,\n            backswingMs = j.optNullableDouble("backswingMs"), downswingMs = j.optNullableDouble("downswingMs"), tempoRatio = j.optNullableDouble("tempo"), backswingLengthCm = j.optNullableDouble("backswingLength"), peakHeadAccelerationMps2 = j.optNullableDouble("accel"), rawBallSpeedMps = j.optNullableDouble("rawBall"), estimatedMatDecelMps2 = j.optNullableDouble("matDecel"), estimatedMatStimpM = j.optNullableDouble("matStimp"), confidence = j.optNullableDouble("confidence"), uncertainty = uncertainty\n        )''', '''        val rollJ = j.optJSONObject("roll")\n        val roll = rollJ?.let { BallRollMetrics(it.optNullableDouble("rpm"), it.optNullableDouble("skid"), it.optNullableDouble("start"), it.optBoolean("marked", false), it.optDouble("confidence", 0.0)) }\n        val m = ShotMetrics(\n            ballSpeedMps = j.getDouble("ball"), launchAngleDeg = j.getDouble("launch"), headSpeedMps = j.optNullableDouble("head"), faceAngleDeg = j.optNullableDouble("face"), pathAngleDeg = j.optNullableDouble("path"), faceToPathDeg = j.optNullableDouble("f2p"), smash = j.optNullableDouble("smash"), impactOffsetMm = j.optNullableDouble("impact"), measuredAtNs = 0L,\n            backswingMs = j.optNullableDouble("backswingMs"), downswingMs = j.optNullableDouble("downswingMs"), tempoRatio = j.optNullableDouble("tempo"), backswingLengthCm = j.optNullableDouble("backswingLength"), peakHeadAccelerationMps2 = j.optNullableDouble("accel"), rawBallSpeedMps = j.optNullableDouble("rawBall"), estimatedMatDecelMps2 = j.optNullableDouble("matDecel"), estimatedMatStimpM = j.optNullableDouble("matStimp"), confidence = j.optNullableDouble("confidence"), roll = roll, uncertainty = uncertainty\n        )''', 'stats json roll import')
p.write_text(s)

# ---------- Accuracy Lab empirical profile plumbing ----------
p = Path('app/src/main/java/com/puttvision/screen/ProductizationV8.kt')
s = p.read_text()
# No schema change needed; profileKey already exists. Export summary now advertises empirical readiness.
s = rep(s, '''            path?.let { append(" · PATH ${"%.2f".format(it)}°") }\n        }\n    }''', '''            path?.let { append(" · PATH ${"%.2f".format(it)}°") }\n            if (m.size >= 20) append(" · LAB P95 READY")\n        }\n    }''', 'accuracy empirical label')
p.write_text(s)

# ---------- Preview / TV immediate handoff in MainActivity ----------
p = Path('app/src/main/java/com/puttvision/screen/MainActivity.kt')
s = p.read_text()
# Clear live predictor whenever a fresh arming starts.
s = rep(s, '''        measurementSuspended = false\n        stopSimulation()''', '''        measurementSuspended = false\n        stopSimulation()\n        TvInstantRollRuntime.clear()''', 'main clear instant on arm')
# HFR impact: get quick estimate and immediately launch TV prediction.
s = rep(s, '''            if (\n                impactDetector.sampleMoved()\n            ) {\n                impactDetected = true\n\n                overlay.status =\n                    "IMPACT · +700ms HFR 캡처"''', '''            val quickImpact = impactDetector.sampleImpact(homography)\n            if (quickImpact != null) {\n                impactDetected = true\n                val read = GreenReadRuntime.peekOrSchedule(engine.settings)\n                TvInstantRollRuntime.begin(engine.settings, quickImpact, read)\n\n                overlay.status =\n                    "IMPACT · TV LIVE · +700ms HFR"''', 'main instant hfr impact')
# HFR failure clears provisional roll.
s = rep(s, '''                if (result == null) {\n                    overlay.status =''', '''                if (result == null) {\n                    TvInstantRollRuntime.clear()\n                    overlay.status =''', 'main clear prediction on analysis fail')
# Low-quality rejection clears predictor.
s = rep(s, '''        if (confidence != null && confidence < rejectThreshold) {\n            replay?.frames?.forEach''', '''        if (confidence != null && confidence < rejectThreshold) {\n            TvInstantRollRuntime.clear()\n            replay?.frames?.forEach''', 'main clear prediction on low quality')
# Accuracy capture uses exact profile.
s = rep(s, '            accuracyValidationLab.capture(baseMetrics)\n', '            accuracyValidationLab.capture(baseMetrics, accuracyAutoTuner.profileKey())\n', 'main validation profile')
# Apply empirical uncertainty after auto tune.
s = rep(s, '''        val processedMetrics = if (!offlineTestMode && ::accuracyAutoTuner.isInitialized) accuracyAutoTuner.apply(baseMetrics) else baseMetrics\n        if (!offlineTestMode) calibrationShotsSinceCheck++\n        updateMetricCards(processedMetrics)\n\n        engine.launch(\n            processedMetrics\n        )\n\n        startSimulationTicker()''', '''        val tunedMetrics = if (!offlineTestMode && ::accuracyAutoTuner.isInitialized) accuracyAutoTuner.apply(baseMetrics) else baseMetrics\n        val processedMetrics = if (!offlineTestMode && ::accuracyAutoTuner.isInitialized && ::accuracyValidationLab.isInitialized) {\n            V14EmpiricalUncertainty.apply(\n                tunedMetrics,\n                accuracyValidationLab.matched(),\n                accuracyAutoTuner.profileKey(),\n                accuracyAutoTuner.current()\n            )\n        } else tunedMetrics\n        if (!offlineTestMode) calibrationShotsSinceCheck++\n        updateMetricCards(processedMetrics)\n\n        val latencyCatchup = TvInstantRollRuntime.elapsedSec()?.coerceIn(0.0, 3.0) ?: 0.0\n        engine.launch(processedMetrics)\n        if (latencyCatchup > .015) {\n            var remaining = latencyCatchup\n            var guard = 0\n            while (remaining > 0.0 && engine.lastResult == null && guard < 420) {\n                val step = minOf(.010, remaining)\n                engine.step(step)\n                remaining -= step\n                guard++\n            }\n        }\n        engine.state?.let { TvInstantRollRuntime.handoff(it.x, it.y) } ?: TvInstantRollRuntime.clear()\n\n        startSimulationTicker()''', 'main empirical and latency catchup')
# Display marked ball metrics in result text.
s = rep(s, '''      processedMetrics.uncertainty?.let { append("\\n${it.compact()}") }\n  }''', '''      processedMetrics.uncertainty?.let { append("\\n${it.compact()}") }\n      processedMetrics.roll?.takeIf { it.markedBall }?.let { rr ->\n          append("\\nROLL ")\n          rr.spinRpm?.let { append("${"%.0f".format(it)}rpm") }\n          rr.rollStartDistanceCm?.let { append(" · START ${"%.1f".format(it)}cm") }\n      }\n  }''', 'main marked ball text')
# SIM camera button inside hardwareless lab: inject after auto test button if known string.
needle = 'root.addView(pvButton("자동 시나리오 전체검사", PvButtonStyle.PRIMARY) {'
if needle in s:
    idx = s.index(needle)
    # Find prior insertion position at exact needle and prepend a button.
    insertion = '''root.addView(pvButton("SIM CAMERA 2.0 · 영상인식 검사", PvButtonStyle.SECONDARY) {\n            metricText.text = "합성 240fps 영상 · 공/퍼터 추적 분석중"\n            cameraExecutor.execute {\n                val seq = V14SyntheticCamera.build()\n                val result = runCatching { hfrAnalyzer.analyzeSynthetic(seq.frames, seq.fps, seq.homography) }.getOrNull()\n                val truth = seq.truth\n                val measured = result?.metrics\n                val pass = measured != null &&\n                    kotlin.math.abs(measured.ballSpeedMps - truth.ballSpeedMps) <= .14 &&\n                    kotlin.math.abs(measured.launchAngleDeg - truth.launchAngleDeg) <= .65 &&\n                    measured.headSpeedMps != null\n                seq.recycle()\n                runOnUiThread {\n                    metricText.text = if (pass) {\n                        "SIM CAMERA PASS · BALL ${"%.2f".format(measured!!.ballSpeedMps)} · START ${"%+.2f".format(measured.launchAngleDeg)}° · 실제 검출/피팅 경로"\n                    } else "SIM CAMERA FAIL · 영상 검출 파이프라인 확인"\n                }\n            }\n        }, LinearLayout.LayoutParams(-1, pvDp(42)).apply { bottomMargin = pvDp(6) })\n\n        '''
    s = s[:idx] + insertion + s[idx:]
else:
    raise SystemExit('MISSING main sim auto button')
p.write_text(s)

# ---------- GreenView live screen-putting presentation ----------
p = Path('app/src/main/java/com/puttvision/screen/GreenView.kt')
s = p.read_text()
# Fields for visual smoothing.
s = rep(s, '''    private var cachedReadKey: GreenReadKey? = null\n    private var cachedRead: GreenRead? = null''', '''    private var cachedReadKey: GreenReadKey? = null\n    private var cachedRead: GreenRead? = null\n    private var smoothBallX = Float.NaN\n    private var smoothBallY = Float.NaN\n    private var lastVisualGeneration = -1L\n    private var previousBallX = Float.NaN\n    private var previousBallY = Float.NaN''', 'green live fields')
# Draw predicted trail before actual trail if present.
s = rep(s, '''        // Ball trail\n        val state = engine.state''', '''        // Instant TV trail starts at physical preview impact, before HFR finalizes.\n        val instantTrail = TvInstantRollRuntime.visibleTrail()\n        if (instantTrail.size >= 2) {\n            p.color = Color.argb(145, 244, 250, 246)\n            p.strokeWidth = max(2f, w * .0015f)\n            p.style = Paint.Style.STROKE\n            val livePath = Path()\n            instantTrail.forEachIndexed { index, pt ->\n                val px = sx(pt.first)\n                val py = worldY(pt.first, pt.second)\n                if (index == 0) livePath.moveTo(px, py) else livePath.lineTo(px, py)\n            }\n            canvas.drawPath(livePath, p)\n            p.style = Paint.Style.FILL\n        }\n\n        // Ball trail\n        val state = engine.state''', 'green predicted trail')
# Replace ball draw block using state x/y. Find from if(state != null) to calibration guide.
s = regex_rep(s, r'''        if \(state != null\) \{\n            val bx = sx\(state\.x\).*?\n        \}\n\n        if \(ProductSessionRuntime\.tvCalibrationGuide\)''', '''        val displayPos = TvInstantRollRuntime.displayPosition(state)\n        if (displayPos != null) {\n            val targetX = sx(displayPos.first)\n            val targetY = worldY(displayPos.first, displayPos.second)\n            val generation = TvInstantRollRuntime.generation()\n            if (!smoothBallX.isFinite() || !smoothBallY.isFinite() || generation != lastVisualGeneration) {\n                smoothBallX = targetX\n                smoothBallY = targetY\n                lastVisualGeneration = generation\n            } else {\n                val alpha = if (state?.running == true || TvInstantRollRuntime.isActive()) .72f else .88f\n                smoothBallX += (targetX - smoothBallX) * alpha\n                smoothBallY += (targetY - smoothBallY) * alpha\n            }\n            if (previousBallX.isFinite() && previousBallY.isFinite()) {\n                p.color = Color.argb(62, 255, 255, 255)\n                p.strokeWidth = max(5f, w * .004f)\n                canvas.drawLine(previousBallX, previousBallY, smoothBallX, smoothBallY, p)\n            }\n            previousBallX = smoothBallX\n            previousBallY = smoothBallY\n            val progress = (displayPos.second / engine.settings.holeDistanceM.coerceAtLeast(.5)).coerceIn(0.0, 1.0)\n            val ballR = (max(6f, w * .0085f) * (1.0 - progress * .34)).toFloat()\n            p.color = Color.argb(58, 255, 255, 255)\n            canvas.drawCircle(smoothBallX, smoothBallY, ballR * 1.75f, p)\n            p.color = Color.WHITE\n            canvas.drawCircle(smoothBallX, smoothBallY, ballR, p)\n\n            if (state?.running == true || TvInstantRollRuntime.isActive()) {\n                val hudW = w * .215f\n                val hudH = h * .057f\n                val left = w * .5f - hudW * .5f\n                val top = safeT + h * .018f\n                p.color = Color.argb(208, 5, 10, 8)\n                canvas.drawRoundRect(RectF(left, top, left + hudW, top + hudH), hudH * .5f, hudH * .5f, p)\n                p.typeface = Typeface.DEFAULT_BOLD\n                p.textSize = max(12f, w * .009f)\n                p.color = Color.rgb(78, 209, 121)\n                val live = TvInstantRollRuntime.quickEstimate()\n                val label = if (TvInstantRollRuntime.isActive()) "● LIVE PUTT · ANALYZING" else "● LIVE PUTT"\n                canvas.drawText(label, left + hudW * .08f, top + hudH * .63f, p)\n                if (live?.ballSpeedMps != null) {\n                    p.textSize = max(9f, w * .0065f)\n                    p.color = Color.argb(190, 235, 243, 238)\n                    canvas.drawText("${"%.2f".format(live.ballSpeedMps)} m/s", left + hudW * .68f, top + hudH * .62f, p)\n                }\n            }\n        } else {\n            smoothBallX = Float.NaN\n            smoothBallY = Float.NaN\n            previousBallX = Float.NaN\n            previousBallY = Float.NaN\n        }\n\n        if (ProductSessionRuntime.tvCalibrationGuide)''', 'green live ball block')
# 60fps scheduling includes instant runtime.
s = rep(s, '''            engine.state?.running == true -> postInvalidateOnAnimation()''', '''            engine.state?.running == true || TvInstantRollRuntime.isAnimating() -> postInvalidateOnAnimation()''', 'green animation schedule')
p.write_text(s)

# ---------- TV replay becomes PiP, never hides live green ----------
p = Path('app/src/main/java/com/puttvision/screen/V13Hardwareless.kt')
s = p.read_text()
start = s.find('class TvImpactReplayView(context: Context) : View(context) {')
end = s.find('/** Deterministic non-Android-hardware smoke runner', start)
if start < 0 or end < 0: raise SystemExit('MISSING TV replay class')
new_class = r'''class TvImpactReplayView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var payload: TvImpactReplayPayload? = null
    private var frame = 0
    private var loops = 0
    private val listener: (TvImpactReplayPayload?) -> Unit = { next ->
        payload = next
        frame = 0
        loops = 0
        handler.removeCallbacks(tick)
        if (next != null) { visibility = VISIBLE; handler.post(tick) }
        else { visibility = GONE; invalidate() }
    }
    private val tick = object : Runnable {
        override fun run() {
            val data = payload ?: return
            if (data.replay.frames.isEmpty()) return
            invalidate()
            frame++
            if (frame >= data.replay.frames.size) {
                frame = 0
                loops++
                if (loops >= 2) { handler.postDelayed({ listener(null) }, 420L); return }
            }
            handler.postDelayed(this, 52L)
        }
    }
    init { visibility = GONE; setBackgroundColor(Color.TRANSPARENT); isClickable = false }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); TvImpactReplayBus.subscribe(listener) }
    override fun onDetachedFromWindow() { TvImpactReplayBus.unsubscribe(listener); handler.removeCallbacksAndMessages(null); payload = null; super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = payload ?: return
        val bmp = data.replay.frames.getOrNull(frame) ?: return
        if (bmp.isRecycled || width <= 0 || height <= 0) return
        val w = width.toFloat(); val h = height.toFloat()
        // Screen-putting style PiP: green and rolling ball remain fully visible underneath.
        val cardW = w * .285f
        val cardH = h * .285f
        val right = w * .965f
        val top = h * .105f
        val card = RectF(right - cardW, top, right, top + cardH)
        p.color = Color.argb(225, 4, 9, 8)
        canvas.drawRoundRect(card, h * .018f, h * .018f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = max(2f, w * .0012f); p.color = Color.argb(150, 78, 209, 121)
        canvas.drawRoundRect(card, h * .018f, h * .018f, p); p.style = Paint.Style.FILL
        val media = RectF(card.left + w*.009f, card.top + h*.052f, card.right - w*.009f, card.bottom - h*.067f)
        val srcAspect = bmp.width.toFloat()/bmp.height.coerceAtLeast(1)
        val dstAspect = media.width()/media.height().coerceAtLeast(1f)
        val target = if (srcAspect > dstAspect) {
            val fitH=media.width()/srcAspect; RectF(media.left,media.centerY()-fitH/2,media.right,media.centerY()+fitH/2)
        } else { val fitW=media.height()*srcAspect; RectF(media.centerX()-fitW/2,media.top,media.centerX()+fitW/2,media.bottom) }
        canvas.drawBitmap(bmp,null,target,p)
        p.typeface=Typeface.DEFAULT_BOLD; p.textSize=max(11f,w*.0074f); p.color=if(frame==data.replay.impactIndex) Color.rgb(246,190,74) else Color.WHITE
        canvas.drawText(if(frame==data.replay.impactIndex) "IMPACT" else "IMPACT REPLAY",card.left+w*.012f,card.top+h*.034f,p)
        p.textSize=max(9f,w*.0058f); p.color=Color.rgb(78,209,121)
        canvas.drawText("BALL ${"%.2f".format(data.metrics.ballSpeedMps)} · START ${"%+.2f".format(data.metrics.launchAngleDeg)}°",card.left+w*.012f,card.bottom-h*.025f,p)
    }
}

'''
s = s[:start] + new_class + s[end:]
p.write_text(s)

# ---------- V14 tests ----------
test = Path('app/src/test/java/com/puttvision/screen/V14VisionRegressionTest.kt')
test.write_text(r'''package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V14VisionRegressionTest {
    @Test fun robustFitRejectsOneBadCentroid() {
        val speed = 1.50
        val angle = Math.toRadians(2.0)
        val points = (0..14).map { i ->
            val t = i * .006
            val x = Math.sin(angle) * speed * t * 100.0
            val y = Math.cos(angle) * speed * t * 100.0
            V14TimedPoint(t, if (i == 7) x + 2.8 else x, if (i == 7) y - 2.2 else y)
        }
        val fit = V14RobustKinematics.fit(points)
        assertNotNull(fit)
        assertEquals(1.50, fit!!.speedMps, .08)
        assertEquals(2.0, fit.launchAngleDeg, .45)
    }

    @Test fun markedBallRollChannelCanFindTransition() {
        val fps = 240
        val v = 1.4
        val r = .02135
        val target = v / r
        var angle = 0.0
        val samples = (0..24).map { i ->
            val omega = target * if (i < 6) (i / 6.0) else 1.0
            angle += Math.toDegrees(omega / fps)
            V14BallRollAnalyzer.MarkerSample(i, i * .6, angle % 360.0)
        }
        val roll = V14BallRollAnalyzer.analyze(samples, fps, v)
        assertNotNull(roll)
        assertTrue(roll!!.markedBall)
        assertTrue((roll.spinRpm ?: 0.0) > 200.0)
        assertTrue((roll.rollStartDistanceCm ?: 99.0) < 10.0)
    }

    @Test fun empiricalP95RequiresRealReferenceVolume() {
        val samples = (0 until 24).map { i ->
            ValidationSample("$i", i.toLong(), 1.40 + (i%3-.0)*.001, .20, 1.0, .1, .1, .95, "P", refBall=1.42, refLaunch=.25, refHead=1.02, refFace=.15, refPath=.12)
        }
        val m = ShotMetrics(1.4,.2,1.0,.1,.1,0.0,1.4,0.0,0L)
        val out = V14EmpiricalUncertainty.apply(m,samples,"P",null)
        assertTrue(out.uncertainty?.basis?.startsWith("LAB P95") == true)
    }
}
''')

print('V14 integration patch applied')
