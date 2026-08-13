from pathlib import Path
import json


def patch(path, old, new, label, count=1):
    p = Path(path); text = p.read_text(encoding='utf-8')
    if new in text:
        print(f'{label}: already current'); return
    if text.count(old) != count:
        raise SystemExit(f'{label}: marker count {text.count(old)} expected {count}')
    p.write_text(text.replace(old, new, count), encoding='utf-8')
    print(f'{label}: patched')

# Startup runtimes.
patch('app/src/main/java/com/puttvision/screen/MainActivity.kt',
'''        V24TvQualityRuntime.install(this)
        voiceCoach = HandsFreeVoiceCoach(this)''',
'''        V24TvQualityRuntime.install(this)
        V26BallStartRuntime.install(this)
        V26GreenVisualRuntime.install(this)
        V26ReportPreferences.install(this)
        voiceCoach = HandsFreeVoiceCoach(this)''', 'V26 startup')

# Product setup discovery.
patch('app/src/main/java/com/puttvision/screen/ProductSetupDialog.kt',
'''    addAction(action("GREEN READ TRAINING", "${V20GreenReadTrainingRuntime.mode.label} · 샷 후 정답 공개") {
        showV20GreenReadModeDialog(context)
    })

    addAction(action("CUSTOM GREEN", V22CustomGreenRuntime.label()) {''',
'''    addAction(action("GREEN READ TRAINING", "${V20GreenReadTrainingRuntime.mode.label} · 샷 후 정답 공개") {
        showV20GreenReadModeDialog(context)
    })

    addAction(action("GREEN VISUALS", V26GreenVisualRuntime.label()) {
        showV26GreenVisualDialog(context)
    })

    addAction(action("MOVE BALL", V26BallStartRuntime.label(engineSettingsForProductSetup())) {
        showV26MoveBallDialog(context, engineSettingsForProductSetup())
    })

    addAction(action("CUSTOM GREEN", V22CustomGreenRuntime.label()) {''', 'V26 product green actions')

# ProductSetup has no engine argument; expose current live settings through a tiny runtime below.
setup = Path('app/src/main/java/com/puttvision/screen/ProductSetupDialog.kt')
text = setup.read_text(encoding='utf-8')
text = text.replace('V26BallStartRuntime.label(engineSettingsForProductSetup())', 'V26BallStartRuntime.label(V26ProductSettingsRuntime.settings)')
text = text.replace('showV26MoveBallDialog(context, engineSettingsForProductSetup())', 'showV26MoveBallDialog(context, V26ProductSettingsRuntime.settings)')
if 'REPORT BUILDER' not in text:
    marker='''    addAction(action("PERFORMANCE COMPARE", V20PerformanceRuntime.report.headline) {
        showV20PerformanceCompareDialog(context)
    })
'''
    repl=marker+'''\n    addAction(action("REPORT BUILDER", V26ReportPreferences.summary()) {
        showV26ReportBuilderDialog(context)
    })
'''
    if text.count(marker)!=1: raise SystemExit('V26 report builder setup marker missing')
    text=text.replace(marker,repl,1)
setup.write_text(text,encoding='utf-8')

# Keep a live settings reference for setup dialogs without changing a long function signature.
game = Path('app/src/main/java/com/puttvision/screen/GameEngine.kt')
text=game.read_text(encoding='utf-8')
if 'object V26ProductSettingsRuntime' not in text:
    text=text.replace('class GameEngine {\n\n    val settings = GreenSettings()', '''object V26ProductSettingsRuntime {
    @Volatile var settings: GreenSettings = GreenSettings()
}

class GameEngine {

    val settings = GreenSettings().also { V26ProductSettingsRuntime.settings = it }''',1)
    game.write_text(text,encoding='utf-8')
    print('V26 product settings runtime: patched')

# Physics begins at virtual start.
patch('app/src/main/java/com/puttvision/screen/GreenPhysics.kt',
'''    fun launch(metrics: ShotMetrics, settings: GreenSettings): SimState {
        val a = Math.toRadians(metrics.launchAngleDeg)
        val speed = metrics.ballSpeedMps.coerceIn(0.05, 5.0)
        return SimState(
            vx = speed * sin(a),
            vy = speed * cos(a),
            running = true,
            trail = mutableListOf(0.0 to 0.0)
        )
    }''',
'''    fun launch(
        metrics: ShotMetrics,
        settings: GreenSettings,
        startX: Double = 0.0,
        startY: Double = 0.0
    ): SimState {
        val a = Math.toRadians(metrics.launchAngleDeg)
        val speed = metrics.ballSpeedMps.coerceIn(0.05, 5.0)
        return SimState(
            x = startX,
            y = startY,
            vx = speed * sin(a),
            vy = speed * cos(a),
            running = true,
            trail = mutableListOf(startX to startY)
        )
    }''','V26 physics virtual start')

# Game engine launch + record target distance + ghost safety.
game=Path('app/src/main/java/com/puttvision/screen/GameEngine.kt'); text=game.read_text(encoding='utf-8')
if 'virtualStartAtShot' not in text:
    marker='''    @Volatile var matStimpEstimateM: Double? = null
        private set
'''
    repl=marker+'''    @Volatile var virtualStartAtShot: Pair<Double, Double> = 0.0 to 0.0
        private set
'''
    if text.count(marker)!=1: raise SystemExit('V26 GameEngine field marker missing')
    text=text.replace(marker,repl,1)
if 'virtualStartAtShot = V26BallStartRuntime.current(settings)' not in text:
    old='''        V15AutoFlowRuntime.rolling()
        V22AudioRuntime.launch(effectiveMetrics.ballSpeedMps)
        state = physics.launch(effectiveMetrics, settings)'''
    new='''        V15AutoFlowRuntime.rolling()
        V22AudioRuntime.launch(effectiveMetrics.ballSpeedMps)
        virtualStartAtShot = V26BallStartRuntime.current(settings)
        state = physics.launch(effectiveMetrics, settings, virtualStartAtShot.first, virtualStartAtShot.second)'''
    if text.count(old)!=1: raise SystemExit('V26 GameEngine launch marker missing')
    text=text.replace(old,new,1)
old='''                val targetDistance = settings.holeDistanceM
                val stimp = settings.stimpMeters'''
new='''                val targetDistance = kotlin.math.hypot(
                    virtualStartAtShot.first,
                    settings.holeDistanceM - virtualStartAtShot.second
                )
                val stimp = settings.stimpMeters'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 target distance marker missing')
    text=text.replace(old,new,1)
old='''                ghostComparison = V15GhostRuntime.compare(record, s.trail)
                V15GhostRuntime.consider(record)'''
new='''                val originShot = kotlin.math.abs(virtualStartAtShot.first) < .005 && kotlin.math.abs(virtualStartAtShot.second) < .005
                ghostComparison = if (originShot) V15GhostRuntime.compare(record, s.trail) else null
                if (originShot) V15GhostRuntime.consider(record)'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 ghost safety marker missing')
    text=text.replace(old,new,1)
if 'virtualStartAtShot = 0.0 to 0.0' not in text:
    old='''        latestRecord = null
        V19StrokeStudioRuntime.clear()'''
    new='''        latestRecord = null
        virtualStartAtShot = 0.0 to 0.0
        V19StrokeStudioRuntime.clear()'''
    if text.count(old)!=1: raise SystemExit('V26 reset marker missing')
    text=text.replace(old,new,1)
game.write_text(text,encoding='utf-8'); print('V26 GameEngine: patched')

# V25 flag info uses virtual start before a shot exists.
p=Path('app/src/main/java/com/puttvision/screen/V25FlagInfo.kt'); text=p.read_text(encoding='utf-8')
old='''        val display = TvInstantRollRuntime.displayPosition(state)
        val ballX = display?.first ?: state?.x ?: 0.0
        val ballY = display?.second ?: state?.y ?: 0.0'''
new='''        val display = TvInstantRollRuntime.displayPosition(state)
        val start = V26BallStartRuntime.current(settings)
        val ballX = display?.first ?: state?.x ?: start.first
        val ballY = display?.second ?: state?.y ?: start.second'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 V25 flag start marker missing')
    text=text.replace(old,new,1);p.write_text(text,encoding='utf-8');print('V26 flag start: patched')

# Green Read key and inverse solver become start-aware.
p=Path('app/src/main/java/com/puttvision/screen/GreenReadAdvisor.kt'); text=p.read_text(encoding='utf-8')
old='''data class GreenReadKey(
    val profile: Int,
    val distance100: Int,
    val stimp100: Int,
    val side100: Int,
    val long100: Int,
    val putter100: Int
)'''
new='''data class GreenReadKey(
    val profile: Int,
    val distance100: Int,
    val stimp100: Int,
    val side100: Int,
    val long100: Int,
    val putter100: Int,
    val startX100: Int,
    val startY100: Int
)'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 GreenReadKey marker missing')
    text=text.replace(old,new,1)
old='''        val putterWidth = ProductRuntime.putterHeadWidthCm.coerceIn(8.0, 15.0)
        return GreenReadKey(
            settings.terrainProfileId,
            (settings.holeDistanceM * 100.0).toInt(),
            (settings.stimpMeters * 100.0).toInt(),
            (settings.sideSlopePct * 100.0).toInt(),
            (settings.longSlopePct * 100.0).toInt(),
            (putterWidth * 100.0).toInt()
        )'''
new='''        val putterWidth = ProductRuntime.putterHeadWidthCm.coerceIn(8.0, 15.0)
        val start = V26BallStartRuntime.current(settings)
        return GreenReadKey(
            settings.terrainProfileId,
            (settings.holeDistanceM * 100.0).toInt(),
            (settings.stimpMeters * 100.0).toInt(),
            (settings.sideSlopePct * 100.0).toInt(),
            (settings.longSlopePct * 100.0).toInt(),
            (putterWidth * 100.0).toInt(),
            (start.first * 100.0).toInt(),
            (start.second * 100.0).toInt()
        )'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 GreenRead key builder missing')
    text=text.replace(old,new,1)
old='''        val d = settings.holeDistanceM.coerceIn(0.5, 20.0)
        val stimp = settings.stimpMeters.coerceIn(1.5, 5.0)'''
new='''        val start = V26BallStartRuntime.current(settings)
        val toCupX = -start.first
        val toCupY = settings.holeDistanceM - start.second
        val d = hypot(toCupX, toCupY).coerceIn(0.5, 20.0)
        val directAngleDeg = Math.toDegrees(kotlin.math.atan2(toCupX, toCupY))
        val stimp = settings.stimpMeters.coerceIn(1.5, 5.0)'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 solver distance marker missing')
    text=text.replace(old,new,1)
old='''        for (angleStep in -10..10) {
            val angle = angleStep * 3.0'''
new='''        for (angleStep in -10..10) {
            val angle = directAngleDeg + angleStep * 3.0'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 coarse angle marker missing')
    text=text.replace(old,new,1)
text=text.replace('.coerceIn(-35.0, 35.0)', '.coerceIn(-45.0, 45.0)')
old='''        val aimCm = tan(Math.toRadians(b.angleDeg)) * d * 100.0
        val magnitude = abs(aimCm)
        val straight = simulate(settings, b.speed, 0.0)
        val breakCm = straight.finishX * 100.0

        val corridor = (1..11).map { i ->
            val y = d * i / 12.0
            val center = GreenTerrain.effectiveSlopeAt(settings, 0.0, y)
            val left = GreenTerrain.effectiveSlopeAt(settings, -0.12, y)
            val right = GreenTerrain.effectiveSlopeAt(settings, 0.12, y)
            TerrainSlope(
                center.sidePct * .60 + left.sidePct * .20 + right.sidePct * .20,
                center.longPct * .60 + left.longPct * .20 + right.longPct * .20
            )
        }'''
new='''        val aimCm = tan(Math.toRadians(b.angleDeg - directAngleDeg)) * d * 100.0
        val magnitude = abs(aimCm)
        val straight = simulate(settings, b.speed, directAngleDeg)
        val ux = toCupX / d
        val uy = toCupY / d
        val perpX = -uy
        val perpY = ux
        val breakCm = ((straight.finishX) * perpX + (straight.finishY - settings.holeDistanceM) * perpY) * 100.0

        val corridor = (1..11).map { i ->
            val t = i / 12.0
            val x = start.first + toCupX * t
            val y = start.second + toCupY * t
            val center = GreenTerrain.effectiveSlopeAt(settings, x, y)
            val left = GreenTerrain.effectiveSlopeAt(settings, x - perpX * .12, y - perpY * .12)
            val right = GreenTerrain.effectiveSlopeAt(settings, x + perpX * .12, y + perpY * .12)
            TerrainSlope(
                center.sidePct * .60 + left.sidePct * .20 + right.sidePct * .20,
                center.longPct * .60 + left.longPct * .20 + right.longPct * .20
            )
        }'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 aim/corridor marker missing')
    text=text.replace(old,new,1)
old='''        val regularizer = abs(angle) * .00015 + abs(speed - flatSpeed) * .00025'''
new='''        val start = V26BallStartRuntime.current(settings)
        val direct = Math.toDegrees(kotlin.math.atan2(-start.first, settings.holeDistanceM - start.second))
        val regularizer = abs(angle - direct) * .00015 + abs(speed - flatSpeed) * .00025'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 candidate marker missing')
    text=text.replace(old,new,1)
old='''        val state = physics.launch(shot(speed, angle), settings)'''
new='''        val start = V26BallStartRuntime.current(settings)
        val state = physics.launch(shot(speed, angle), settings, start.first, start.second)'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 simulate start marker missing')
    text=text.replace(old,new,1)
p.write_text(text,encoding='utf-8');print('V26 GreenReadAdvisor: patched')

# Persistent read cache includes virtual start.
p=Path('app/src/main/java/com/puttvision/screen/V13Reliability.kt');text=p.read_text(encoding='utf-8')
old='''        key.profile, key.distance100, key.stimp100, key.side100, key.long100, key.putter100
    ).joinToString(":")'''
new='''        key.profile, key.distance100, key.stimp100, key.side100, key.long100, key.putter100,
        key.startX100, key.startY100
    ).joinToString(":")'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 disk cache key marker missing')
    text=text.replace(old,new,1);p.write_text(text,encoding='utf-8');print('V26 GreenReadDiskCache: patched')

# OpenGL stage overlay + moved idle camera/ball/aim fallback.
p=Path('app/src/main/java/com/puttvision/screen/V18OpenGlSimulator.kt');text=p.read_text(encoding='utf-8')
old='''        addView(hud, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(V19StrokeStudioOverlay(context, engine), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))'''
new='''        addView(hud, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(V26GreenInsightOverlay(context, engine), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(V19StrokeStudioOverlay(context, engine), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 V18 overlay marker missing')
    text=text.replace(old,new,1)
old='''        val anim = TvInstantRollRuntime.displayPosition(state)
        val bx = anim?.first ?: state?.x ?: 0.0
        val by = anim?.second ?: state?.y ?: 0.0'''
new='''        val anim = TvInstantRollRuntime.displayPosition(state)
        val virtualStart = V26BallStartRuntime.current(settings)
        val bx = anim?.first ?: state?.x ?: virtualStart.first
        val by = anim?.second ?: state?.y ?: virtualStart.second'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 V18 camera start marker missing')
    text=text.replace(old,new,1)
old='''            else -> {
                desiredEye = floatArrayOf(0f, -1.58f, .70f)
                desiredTarget = floatArrayOf(0f, min(2.35, settings.holeDistanceM * .48).toFloat(), .035f)
            }'''
new='''            else -> {
                val remaining = (settings.holeDistanceM - virtualStart.second).coerceAtLeast(.5)
                desiredEye = floatArrayOf(virtualStart.first.toFloat(), (virtualStart.second - 1.58).toFloat(), .70f)
                desiredTarget = floatArrayOf((virtualStart.first * .45).toFloat(), (virtualStart.second + min(2.35, remaining * .48)).toFloat(), .035f)
            }'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 V18 idle camera marker missing')
    text=text.replace(old,new,1)
old='''            val pts = if (read.predictedTrail.size >= 2) read.predictedTrail else listOf(0.0 to 0.0, read.aimOffsetCm / 100.0 to settings.holeDistanceM)'''
new='''            val start = V26BallStartRuntime.current(settings)
            val aimDy = settings.holeDistanceM - start.second
            val aimX = start.first + kotlin.math.tan(Math.toRadians(read.recommendedLaunchAngleDeg)) * aimDy
            val pts = if (read.predictedTrail.size >= 2) read.predictedTrail else listOf(start, aimX to settings.holeDistanceM)'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 V18 aim fallback marker missing')
    text=text.replace(old,new,1)
old='''        val state = engine.state
        val display = TvInstantRollRuntime.displayPosition(state) ?: if (state != null) state.x to state.y else 0.0 to 0.0'''
new='''        val state = engine.state
        val start = V26BallStartRuntime.current(settings)
        val display = TvInstantRollRuntime.displayPosition(state) ?: if (state != null) state.x to state.y else start'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 V18 ball marker missing')
    text=text.replace(old,new,1)
p.write_text(text,encoding='utf-8');print('V26 V18 simulator: patched')

# Canvas fallback uses moved ball and absolute solved launch angle.
p=Path('app/src/main/java/com/puttvision/screen/V17SimulatorTvView.kt');text=p.read_text(encoding='utf-8')
old='''        val state = engine.state
        val display = TvInstantRollRuntime.displayPosition(state)
            ?: if (state != null) state.x to state.y else 0.0 to 0.0'''
new='''        val state = engine.state
        val start = V26BallStartRuntime.current(settings)
        val display = TvInstantRollRuntime.displayPosition(state)
            ?: if (state != null) state.x to state.y else start'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 V17 ball marker missing')
    text=text.replace(old,new,1)
old='''            val aimX = if (read?.solverReliable == true) read.aimOffsetCm / 100.0 else 0.0
            val endX = sx(aimX, settings.holeDistanceM)'''
new='''            val aimX = if (read?.solverReliable == true) {
                start.first + kotlin.math.tan(Math.toRadians(read.recommendedLaunchAngleDeg)) * (settings.holeDistanceM - start.second)
            } else 0.0
            val endX = sx(aimX, settings.holeDistanceM)'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 V17 aim marker missing')
    text=text.replace(old,new,1)
p.write_text(text,encoding='utf-8');print('V26 V17 fallback: patched')

# Report builder toggles fixed PDF sections and moves cup error to metres.
p=Path('app/src/main/java/com/puttvision/screen/V22ReportExporter.kt');text=p.read_text(encoding='utf-8')
if 'val reportTiles = V26ReportPreferences.snapshot()' not in text:
    marker='''    private fun writePdf(file: File, records: List<ShotRecord>) {
        val doc = PdfDocument()'''
    repl='''    private fun writePdf(file: File, records: List<ShotRecord>) {
        val reportTiles = V26ReportPreferences.snapshot()
        val doc = PdfDocument()'''
    if text.count(marker)!=1: raise SystemExit('V26 report tiles marker missing')
    text=text.replace(marker,repl,1)
old='''        paint.color = Color.rgb(242, 247, 244)
        canvas.drawRoundRect(65f, 135f, 1175f, 380f, 24f, 24f, paint)'''
new='''        if (V26ReportTile.OVERVIEW in reportTiles) {
        paint.color = Color.rgb(242, 247, 244)
        canvas.drawRoundRect(65f, 135f, 1175f, 380f, 24f, 24f, paint)'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 report overview start missing')
    text=text.replace(old,new,1)
old='''        y = 435f

        text("COACH / TREND"'''
new='''        y = 435f
        } else { y = 165f }

        if (V26ReportTile.COACH_TREND in reportTiles) {
        text("COACH / TREND"'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 report overview close missing')
    text=text.replace(old,new,1)
old='''        if (compare.putters.isNotEmpty()) {'''
new='''        }

        if (V26ReportTile.PUTTER_RANKING in reportTiles && compare.putters.isNotEmpty()) {'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 report putter marker missing')
    text=text.replace(old,new,1)
old='''        text("SHOT DETAIL", 70f, y, 20f, Color.rgb(34, 153, 84), true); y += 36f'''
new='''        if (V26ReportTile.SHOT_DETAIL in reportTiles) {
        text("SHOT DETAIL", 70f, y, 20f, Color.rgb(34, 153, 84), true); y += 36f'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 report detail start missing')
    text=text.replace(old,new,1)
old='''            y += 35f
        }

        doc.finishPage(page)'''
new='''            y += 35f
        }
        }

        doc.finishPage(page)'''
if new not in text:
    if text.count(old)!=1: raise SystemExit('V26 report detail close missing')
    text=text.replace(old,new,1)
text=text.replace('val cupError = records.mapNotNull { it.result?.distanceToCupM?.times(100.0) }','val cupError = records.mapNotNull { it.result?.distanceToCupM }')
text=text.replace('"CUP ERROR ${cupError.takeIf { it.isNotEmpty() }?.average()?.let { "%.0fcm".format(it) } ?: "--"}"','"CUP ERROR ${cupError.takeIf { it.isNotEmpty() }?.average()?.let { "%.2f m".format(it) } ?: "--"}"')
text=text.replace('if (it.holed) "IN" else "%.0fcm".format(it.distanceToCupM * 100.0)','if (it.holed) "IN" else "%.2fm".format(it.distanceToCupM)')
text=text.replace('cup_error_cm','cup_error_m')
text=text.replace('r.result?.distanceToCupM?.times(100.0)?.toString()','r.result?.distanceToCupM?.toString()')
p.write_text(text,encoding='utf-8');print('V26 report exporter: patched')

# Builder copy accurately says PDF sections (CSV remains raw data table).
p=Path('app/src/main/java/com/puttvision/screen/V26ReportBuilder.kt');text=p.read_text(encoding='utf-8').replace('다음 PDF/CSV 공유부터 선택한 섹션 구성으로 생성합니다.','다음 PDF 공유부터 선택한 섹션 구성으로 생성합니다. CSV는 전체 원자료를 유지합니다.')
p.write_text(text,encoding='utf-8')

# Feature matrix.
fm=Path('FEATURE_MATRIX.json');data=json.loads(fm.read_text(encoding='utf-8'));data['version']='v26-development';f=data.setdefault('features',{});f['green_contour_overlay']=True;f['live_slope_percentage_overlay']=True;f['recommended_speed_swing_guide']=True;f['move_ball_shared_physics_and_green_read']=True;f['custom_report_builder']=True;f['report_distance_units_meters']=True;v=data.setdefault('validation',{});v['v26_contour_regression_test']=True;v['v26_virtual_start_physics_test']=True;fm.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print('V26 feature matrix: updated')
