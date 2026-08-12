from pathlib import Path

ROOT = Path('.')


def replace_once(path: str, old: str, new: str):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise RuntimeError(f'missing anchor in {path}: {old[:120]!r}')
    if text.count(old) != 1:
        raise RuntimeError(f'non-unique anchor in {path}: {text.count(old)}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')


# 1) Green read now returns the exact predicted physics trail used by the solver.
(ROOT / 'app/src/main/java/com/puttvision/screen/GreenReadAdvisor.kt').write_text(r'''package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.math.tan


data class GreenRead(
    val estimatedBreakCm: Double,
    val aimOffsetCm: Double,
    val cupCount: Double,
    val putterHeadCount: Double,
    val aimSideLabel: String,
    val effectiveSideSlopePct: Double,
    val effectiveLongSlopePct: Double,
    val paceHint: String,
    val recommendedBallSpeedMps: Double,
    val recommendedLaunchAngleDeg: Double,
    val solverMissCm: Double,
    val predictedTrail: List<Pair<Double, Double>>
)

object GreenReadAdvisor {
    private const val CUP_DIAMETER_CM = 10.8
    private const val STIMP_LAUNCH_MPS = 1.95072
    private val physics = GreenPhysics()

    private data class Key(
        val profile: Int, val distance100: Int, val stimp100: Int,
        val side100: Int, val long100: Int, val putter100: Int
    )
    private data class Candidate(val angleDeg: Double, val speed: Double, val result: SimResult, val objective: Double)
    private data class Trace(val result: SimResult, val trail: List<Pair<Double, Double>>)

    private val cache = object : LinkedHashMap<Key, GreenRead>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, GreenRead>?): Boolean = size > 64
    }

    @Synchronized
    fun read(settings: GreenSettings): GreenRead {
        val putterWidth = ProductRuntime.putterHeadWidthCm.coerceIn(8.0, 15.0)
        val key = Key(
            settings.terrainProfileId,
            (settings.holeDistanceM * 100.0).toInt(),
            (settings.stimpMeters * 100.0).toInt(),
            (settings.sideSlopePct * 100.0).toInt(),
            (settings.longSlopePct * 100.0).toInt(),
            (putterWidth * 100.0).toInt()
        )
        cache[key]?.let { return it }
        val solved = solve(settings, putterWidth)
        cache[key] = solved
        return solved
    }

    private fun solve(settings: GreenSettings, putterWidth: Double): GreenRead {
        val d = settings.holeDistanceM.coerceIn(0.5, 20.0)
        val stimp = settings.stimpMeters.coerceIn(1.5, 5.0)
        val flatSpeed = (STIMP_LAUNCH_MPS * sqrt(d / stimp)).coerceIn(.20, 5.0)
        val minSpeed = (flatSpeed * .45).coerceIn(.15, 4.7)
        val maxSpeed = (flatSpeed * 1.55).coerceIn(minSpeed + .05, 5.0)

        var best: Candidate? = null
        val coarseSpeedStep = (maxSpeed - minSpeed) / 14.0
        for (angleStep in -10..10) {
            val angle = angleStep * 3.0
            for (speedStep in 0..14) {
                val speed = minSpeed + coarseSpeedStep * speedStep
                val c = candidate(settings, angle, speed, flatSpeed)
                if (best == null || c.objective < best!!.objective) best = c
            }
        }

        val coarse = best ?: candidate(settings, 0.0, flatSpeed, flatSpeed)
        best = coarse
        val refineSpeedSpan = maxOf(.10, coarseSpeedStep * 1.25)
        for (ai in -6..6) {
            val angle = (coarse.angleDeg + ai * .5).coerceIn(-35.0, 35.0)
            for (si in -6..6) {
                val speed = (coarse.speed + refineSpeedSpan * si / 6.0).coerceIn(.15, 5.0)
                val c = candidate(settings, angle, speed, flatSpeed)
                if (c.objective < best!!.objective) best = c
            }
        }

        val b = best!!
        val trace = simulateTrace(settings, b.speed, b.angleDeg)
        val aimCm = tan(Math.toRadians(b.angleDeg)) * d * 100.0
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
        }
        val effectiveSide = corridor.map { it.sidePct }.average()
        val effectiveLong = corridor.map { it.longPct }.average()
        val ratio = b.speed / flatSpeed.coerceAtLeast(.1)
        val pace = when {
            ratio <= .78 -> "강한 내리막 · 매우 약하게"
            ratio <= .91 -> "내리막 · 약하게"
            ratio >= 1.22 -> "강한 오르막 · 강하게"
            ratio >= 1.08 -> "오르막 · 조금 강하게"
            abs(effectiveSide) >= 3.0 -> "브레이크 큼 · 끝까지 읽기"
            abs(effectiveSide) >= 1.6 -> "브레이크 중간"
            else -> "기준 페이스"
        }
        val side = when {
            magnitude < 1.5 -> "센터"
            aimCm < 0.0 -> "홀 왼쪽"
            else -> "홀 오른쪽"
        }
        return GreenRead(
            estimatedBreakCm = breakCm,
            aimOffsetCm = aimCm,
            cupCount = magnitude / CUP_DIAMETER_CM,
            putterHeadCount = magnitude / putterWidth,
            aimSideLabel = side,
            effectiveSideSlopePct = effectiveSide,
            effectiveLongSlopePct = effectiveLong,
            paceHint = pace,
            recommendedBallSpeedMps = b.speed,
            recommendedLaunchAngleDeg = b.angleDeg,
            solverMissCm = trace.result.distanceToCupM * 100.0,
            predictedTrail = trace.trail
        )
    }

    private fun candidate(settings: GreenSettings, angle: Double, speed: Double, flatSpeed: Double): Candidate {
        val result = simulate(settings, speed, angle)
        val regularizer = abs(angle) * .00015 + abs(speed - flatSpeed) * .00025
        val objective = if (result.holed) -1.0 + regularizer else result.distanceToCupM + regularizer
        return Candidate(angle, speed, result, objective)
    }

    private fun shot(speed: Double, angle: Double) = ShotMetrics(
        ballSpeedMps = speed,
        launchAngleDeg = angle,
        headSpeedMps = null,
        faceAngleDeg = null,
        pathAngleDeg = null,
        faceToPathDeg = null,
        smash = null,
        impactOffsetMm = null,
        measuredAtNs = 0L
    )

    private fun simulate(settings: GreenSettings, speed: Double, angle: Double): SimResult =
        simulateTrace(settings, speed, angle, keepTrail = false).result

    private fun simulateTrace(
        settings: GreenSettings,
        speed: Double,
        angle: Double,
        keepTrail: Boolean = true
    ): Trace {
        val state = physics.launch(shot(speed, angle), settings)
        var result: SimResult? = null
        repeat(900) {
            result = physics.step(state, settings, .025)
            if (result != null) return@repeat
        }
        val final = result ?: SimResult(
            holed = state.holed,
            finishX = state.x,
            finishY = state.y,
            distanceToCupM = hypot(state.x, state.y - settings.holeDistanceM),
            elapsedSec = state.elapsed
        )
        val trail = if (keepTrail) {
            state.trail.toList().ifEmpty { listOf(0.0 to 0.0, state.x to state.y) }
        } else emptyList()
        return Trace(final, trail)
    }
}
''', encoding='utf-8')


# 2) TV renderer: true physics trajectory, animated slope-flow field and an explicit AIM marker.
replace_once(
    'app/src/main/java/com/puttvision/screen/GreenView.kt',
    'import android.graphics.*\nimport android.view.View\n',
    'import android.graphics.*\nimport android.os.SystemClock\nimport android.view.View\n'
)

old_guide = r'''        val read = GreenReadAdvisor.read(settings)
        val holeY = settings.holeDistanceM
        val aimX = read.aimOffsetCm / 100.0
        val guide = Path().apply {
            moveTo(sx(0.0, 0.0), sy(0.0))
            val midY = holeY * .56
            val curveX = aimX * .55 + settings.sideSlopePct * .012
            cubicTo(
                sx(curveX * .16, holeY * .18), sy(holeY * .18),
                sx(curveX, midY), sy(midY),
                sx(aimX, holeY), sy(holeY)
            )
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = max(3f, w * .0021f)
        p.color = Color.argb(188, 238, 247, 239)
        c.drawPath(guide, p)
'''

new_guide = r'''        val holeY = settings.holeDistanceM
        val preShot = engine.state?.running != true && engine.lastResult == null
        val read = if (preShot) GreenReadAdvisor.read(settings) else null

        if (preShot && read != null) {
            // V11 slope-flow field. Each particle follows the same local slope vector
            // that GreenPhysics uses at this exact x/y location.
            val flowSave = c.save()
            c.clipPath(greenShape)
            val nowSec = (SystemClock.uptimeMillis() % 60_000L) / 1000.0
            for (row in 1..8) {
                val yM = holeY * row / 9.0
                for (lane in -4..4) {
                    val xM = gridSideRange * lane / 4.5
                    val slope = GreenTerrain.effectiveSlopeAt(settings, xM, yM)
                    val mag = hypot(slope.sidePct, slope.longPct)
                    if (mag < .14) continue
                    val ux = slope.sidePct / mag
                    val uy = slope.longPct / mag
                    val travelM = .08 + min(.18, mag * .028)
                    val speed = .42 + min(1.75, mag * .30)
                    for (particle in 0..1) {
                        var phase = (nowSec * speed + row * .173 + lane * .119 + particle * .5) % 1.0
                        if (phase < 0.0) phase += 1.0
                        val centered = phase - .5
                        val pxM = xM + ux * travelM * centered
                        val pyM = yM + uy * travelM * centered
                        val px = sx(pxM, pyM)
                        val py = sy(pyM)
                        val alpha = (70 + 150 * (1.0 - abs(centered) * 1.45).coerceIn(.0, 1.0)).toInt()
                        p.style = Paint.Style.FILL
                        p.color = Color.argb(alpha, 218, 255, 226)
                        c.drawCircle(px, py, max(1.8f, w * .00155f), p)
                    }
                }
            }
            c.restoreToCount(flowSave)

            // No decorative Bezier: this line is literally the path GreenPhysics produced
            // from the recommended launch angle and ball speed.
            read.predictedTrail.takeIf { it.size >= 2 }?.let { trail ->
                val guide = Path().apply {
                    moveTo(sx(trail.first().first, trail.first().second), sy(trail.first().second))
                    trail.drop(1).forEach { point ->
                        lineTo(sx(point.first, point.second), sy(point.second))
                    }
                }
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = max(3f, w * .00205f)
                p.pathEffect = DashPathEffect(floatArrayOf(max(8f, w * .008f), max(5f, w * .0045f)), 0f)
                p.color = Color.argb(205, 238, 247, 239)
                c.drawPath(guide, p)
                p.pathEffect = null
                p.strokeCap = Paint.Cap.BUTT
            }

            // Physical aim point at cup distance. This is the same lateral offset used
            // to calculate cup/head counts, not a decorative HUD coordinate.
            val aimX = read.aimOffsetCm / 100.0
            val ax = sx(aimX, holeY)
            val ay = sy(holeY)
            val radius = max(8f, w * .0068f)
            p.style = Paint.Style.FILL
            p.color = Color.argb(205, 5, 9, 11)
            c.drawCircle(ax, ay, radius * 1.45f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(2f, w * .0015f)
            p.color = Pv.primary
            c.drawCircle(ax, ay, radius, p)
            c.drawLine(ax - radius * .62f, ay, ax + radius * .62f, ay, p)
            c.drawLine(ax, ay - radius * .62f, ax, ay + radius * .62f, p)
            p.style = Paint.Style.FILL
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = max(8f, w * .0062f)
            p.color = Pv.primary
            val aimLabel = if (read.aimSideLabel == "센터") "AIM · CENTER" else "AIM · ${"%.1f".format(read.cupCount)} CUP"
            c.drawText(aimLabel, ax + radius * 1.65f, ay - radius * .35f, p)
        }
'''
replace_once('app/src/main/java/com/puttvision/screen/GreenView.kt', old_guide, new_guide)
replace_once(
    'app/src/main/java/com/puttvision/screen/GreenView.kt',
    '        drawAimReadout(c, read)\n',
    '        read?.let { drawAimReadout(c, it) }\n'
)

# 3) Phone/tablet can render the exact TV view without a physical TV attached.
menu_anchor = r'''    tools.addView(tool("TV CAL", "TV 화면 크기 · 위치 보정") {
        showTvCalibrationDialog(this, tvCalibrationStore)
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("BACKUP", "기록 · 설정 백업 / 복원") {
'''
menu_replacement = r'''    tools.addView(tool("TV CAL", "TV 화면 크기 · 위치 보정") {
        showTvCalibrationDialog(this, tvCalibrationStore)
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("TV PREVIEW", "TV 화면을 이 기기에서 미리보기") {
        closeThen { showTvPreview() }
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("BACKUP", "기록 · 설정 백업 / 복원") {
'''
replace_once('app/src/main/java/com/puttvision/screen/MainActivity.kt', menu_anchor, menu_replacement)

preview_anchor = r'''    private fun openAccuracyValidationLab() {
        showAccuracyValidationLab(this, accuracyValidationLab) {
            accuracyCsvImport.launch("text/*")
        }
    }
'''
preview_code = r'''    private fun showTvPreview() {
        val resumeAfter = sessionActive && !measurementSuspended
        if (resumeAfter) suspendMeasurementForOverlay()

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val tv = GreenView(this, engine)
        root.addView(tv, FrameLayout.LayoutParams(-1, -1))

        val close = pvButton("닫기", PvButtonStyle.GHOST) { dialog.dismiss() }
        root.addView(close, FrameLayout.LayoutParams(pvDp(84), pvDp(42), Gravity.TOP or Gravity.END).apply {
            topMargin = pvDp(12)
            marginEnd = pvDp(12)
        })
        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
        dialog.setOnDismissListener {
            if (resumeAfter && sessionActive) mainHandler.post { armPrecision() }
        }
        dialog.show()
    }

    private fun openAccuracyValidationLab() {
        showAccuracyValidationLab(this, accuracyValidationLab) {
            accuracyCsvImport.launch("text/*")
        }
    }
'''
replace_once('app/src/main/java/com/puttvision/screen/MainActivity.kt', preview_anchor, preview_code)

# 4) Regression coverage for the physical path contract.
test = ROOT / 'app/src/test/java/com/puttvision/screen/GreenReadTrajectoryRegressionTest.kt'
test.write_text(r'''package com.puttvision.screen

import org.junit.Assert.assertTrue
import org.junit.Test

class GreenReadTrajectoryRegressionTest {
    @Test
    fun solverReturnsPhysicalTrajectoryAndLaunch() {
        val settings = GreenSettings(
            stimpMeters = 2.8,
            holeDistanceM = 5.0,
            sideSlopePct = 0.8,
            longSlopePct = -0.3,
            terrainProfileId = 12
        )
        val read = GreenReadAdvisor.read(settings)
        assertTrue(read.recommendedBallSpeedMps > 0.1)
        assertTrue(read.recommendedLaunchAngleDeg in -35.0..35.0)
        assertTrue(read.predictedTrail.size >= 2)
        assertTrue(read.predictedTrail.first().second <= 0.05)
        assertTrue(read.solverMissCm.isFinite())
    }
}
''', encoding='utf-8')

print('V11 TV physics patch applied')
