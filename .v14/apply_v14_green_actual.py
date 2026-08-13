from pathlib import Path

p = Path('app/src/main/java/com/puttvision/screen/GreenView.kt')
s = p.read_text()

def rep(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'MISSING GREEN {label}')
    s = s.replace(old, new, 1)

rep(
'''    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
''',
'''    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var smoothBallX = Float.NaN
    private var smoothBallY = Float.NaN
    private var lastVisualGeneration = -1L
    private var previousBallX = Float.NaN
    private var previousBallY = Float.NaN
''', 'fields')

rep(
'''            engine.state?.running == true -> postInvalidateOnAnimation()
''',
'''            engine.state?.running == true || TvInstantRollRuntime.isAnimating() -> postInvalidateOnAnimation()
''', 'animation schedule')

rep(
'''        val preShot = engine.state?.running != true && engine.lastResult == null
''',
'''        val preShot = engine.state?.running != true && !TvInstantRollRuntime.isAnimating() && engine.lastResult == null
''', 'pre-shot gate')

rep(
'''        engine.state?.trail?.takeIf { it.size >= 2 }?.let { trail ->
''',
'''        val instantTrail = TvInstantRollRuntime.visibleTrail()
        if (instantTrail.size >= 2) {
            val livePath = Path().apply {
                moveTo(sx(instantTrail.first().first, instantTrail.first().second), sySurface(instantTrail.first().first, instantTrail.first().second))
                instantTrail.drop(1).forEach { point ->
                    lineTo(sx(point.first, point.second), sySurface(point.first, point.second))
                }
            }
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = max(3f, w * .0022f)
            p.color = Color.argb(185, 238, 247, 239)
            c.drawPath(livePath, p)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        }

        engine.state?.trail?.takeIf { it.size >= 2 }?.let { trail ->
''', 'instant trail')

rep(
'''        val state = engine.state
        val bx = if (state != null) sx(state.x, state.y) else sx(0.0, 0.0)
        val by = if (state != null) sySurface(state.x, state.y) else sySurface(0.0, 0.0)
        p.color = Color.argb(72, 0, 0, 0)
        c.drawOval(RectF(bx - w * .012f, by + h * .008f, bx + w * .012f, by + h * .017f), p)
        p.color = Color.WHITE
        c.drawCircle(bx, by, max(8f, w * .0065f), p)
''',
'''        val state = engine.state
        val displayPos = TvInstantRollRuntime.displayPosition(state) ?: if (state != null) (state.x to state.y) else (0.0 to 0.0)
        val targetX = sx(displayPos.first, displayPos.second)
        val targetY = sySurface(displayPos.first, displayPos.second)
        val generation = TvInstantRollRuntime.generation()
        if (!smoothBallX.isFinite() || !smoothBallY.isFinite() || generation != lastVisualGeneration) {
            smoothBallX = targetX
            smoothBallY = targetY
            lastVisualGeneration = generation
        } else {
            val alpha = if (state?.running == true || TvInstantRollRuntime.isActive()) .72f else .88f
            smoothBallX += (targetX - smoothBallX) * alpha
            smoothBallY += (targetY - smoothBallY) * alpha
        }

        if ((state?.running == true || TvInstantRollRuntime.isAnimating()) && previousBallX.isFinite() && previousBallY.isFinite()) {
            p.color = Color.argb(62, 255, 255, 255)
            p.strokeWidth = max(5f, w * .004f)
            c.drawLine(previousBallX, previousBallY, smoothBallX, smoothBallY, p)
        }
        previousBallX = smoothBallX
        previousBallY = smoothBallY

        val progress = (displayPos.second / settings.holeDistanceM.coerceAtLeast(.5)).coerceIn(0.0, 1.0)
        val ballR = (max(7f, w * .0074f) * (1.0 - progress * .30)).toFloat()
        p.color = Color.argb(72, 0, 0, 0)
        c.drawOval(RectF(smoothBallX - ballR * 1.35f, smoothBallY + ballR * .55f, smoothBallX + ballR * 1.35f, smoothBallY + ballR * 1.35f), p)
        if (state?.running == true || TvInstantRollRuntime.isAnimating()) {
            p.color = Color.argb(55, 255, 255, 255)
            c.drawCircle(smoothBallX, smoothBallY, ballR * 1.9f, p)
        }
        p.color = Color.WHITE
        c.drawCircle(smoothBallX, smoothBallY, ballR, p)

        if (state?.running == true || TvInstantRollRuntime.isActive()) {
            val hudW = w * .245f
            val hudH = h * .054f
            val leftHud = w * .5f - hudW * .5f
            val topHud = h * .145f
            p.color = Color.argb(215, 5, 10, 8)
            c.drawRoundRect(RectF(leftHud, topHud, leftHud + hudW, topHud + hudH), hudH * .5f, hudH * .5f, p)
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = max(12f, w * .0082f)
            p.color = Pv.primary
            val live = TvInstantRollRuntime.quickEstimate()
            c.drawText(if (TvInstantRollRuntime.isActive()) "● LIVE PUTT · ANALYZING" else "● LIVE PUTT", leftHud + hudW * .07f, topHud + hudH * .64f, p)
            live?.ballSpeedMps?.let { speed ->
                p.textAlign = Paint.Align.RIGHT
                p.textSize = max(11f, w * .0074f)
                p.color = Color.WHITE
                c.drawText("${"%.2f".format(speed)} m/s", leftHud + hudW * .94f, topHud + hudH * .64f, p)
                p.textAlign = Paint.Align.LEFT
            }
        }
''', 'ball renderer')

rep(
'''    if (engine.state?.running == true || engine.lastResult != null) return
''',
'''    if (engine.state?.running == true || TvInstantRollRuntime.isAnimating() || engine.lastResult != null) return
''', 'aim readout gate')

p.write_text(s)
print('current GreenView live-roll patch applied')
