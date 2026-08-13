from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "app/src/main/java/com/puttvision/screen"


def replace_private_fun(text: str, name: str, replacement: str) -> str:
    marker = f"    private fun {name}("
    start = text.find(marker)
    if start < 0:
        raise SystemExit(f"missing function: {name}")
    nxt = text.find("\n    private fun ", start + len(marker))
    if nxt < 0:
        raise SystemExit(f"cannot locate end of function: {name}")
    return text[:start] + replacement.rstrip() + "\n\n" + text[nxt + 1:]


# Generate V17 from the validated V16 renderer, then make the simulator view cleaner and more game-like.
v16_path = PKG / "V16SimulatorTvView.kt"
v17_path = PKG / "V17SimulatorTvView.kt"
v17 = v16_path.read_text(encoding="utf-8")
v17 = v17.replace("V16 external-display renderer.", "V17 simulator-first external-display renderer.", 1)
v17 = v17.replace("class V16SimulatorTvView(", "class V17SimulatorTvView(", 1)

# Lower camera / brighter screen-golf perspective.
repls = {
    "val horizon = h * .42f": "val horizon = h * .35f",
    "val horizonY = h * .405f": "val horizonY = h * .345f",
    "val projectionBottomY = h * .74f": "val projectionBottomY = h * .815f",
    "val greenBottomY = h * 1.02f": "val greenBottomY = h * 1.045f",
    "val maxY = max(settings.holeDistanceM * 1.24, 3.5)": "val maxY = max(settings.holeDistanceM * 1.18, 3.5)",
    "val sideRange = max(1.35, settings.holeDistanceM * .18)": "val sideRange = max(1.12, settings.holeDistanceM * .145)",
    "moveTo(w * .28f, horizonY)": "moveTo(w * .32f, horizonY)",
    "w * .72f, horizonY": "w * .68f, horizonY",
    "Color.rgb(108, 188, 75), Color.rgb(88, 171, 68), Color.rgb(66, 148, 59)": "Color.rgb(119, 194, 82), Color.rgb(91, 171, 67), Color.rgb(61, 139, 54)",
}
for old, new in repls.items():
    if old not in v17:
        raise SystemExit(f"V17 renderer replacement missing: {old}")
    v17 = v17.replace(old, new, 1)

v17 = replace_private_fun(v17, "drawAimLineAndBall", r'''    private fun drawAimLineAndBall(
        c: Canvas,
        settings: GreenSettings,
        read: GreenRead?,
        sx: (Double, Double) -> Float,
        sy: (Double, Double) -> Float
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val state = engine.state
        val display = TvInstantRollRuntime.displayPosition(state)
            ?: if (state != null) state.x to state.y else 0.0 to 0.0
        val tx = sx(display.first, display.second)
        val ty = sy(display.first, display.second)
        val generation = TvInstantRollRuntime.generation()

        if (!smoothBallX.isFinite() || generation != lastGeneration) {
            smoothBallX = tx
            smoothBallY = ty
            lastGeneration = generation
        } else {
            val a = if (state?.running == true || TvInstantRollRuntime.isAnimating()) .78f else .92f
            smoothBallX += (tx - smoothBallX) * a
            smoothBallY += (ty - smoothBallY) * a
        }

        val preShot = state?.running != true && !TvInstantRollRuntime.isAnimating() && engine.lastResult == null
        if (preShot) {
            val aimX = if (read?.solverReliable == true) read.aimOffsetCm / 100.0 else 0.0
            val endX = sx(aimX, settings.holeDistanceM)
            val endY = sy(aimX, settings.holeDistanceM)

            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = max(4.8f, w * .0030f)
            p.color = Color.argb(62, 0, 0, 0)
            c.drawLine(smoothBallX, smoothBallY - h * .012f, endX, endY, p)
            p.strokeWidth = max(2.2f, w * .00145f)
            p.color = Color.argb(210, 229, 54, 48)
            c.drawLine(smoothBallX, smoothBallY - h * .012f, endX, endY, p)
            p.style = Paint.Style.FILL

            val targetR = max(4.5f, w * .0033f)
            p.color = Color.argb(220, 229, 54, 48)
            c.drawCircle(endX, endY, targetR, p)
            p.color = Color.WHITE
            c.drawCircle(endX, endY, targetR * .42f, p)
        }

        val progress = (display.second / settings.holeDistanceM.coerceAtLeast(.5)).coerceIn(0.0, 1.0)
        val r = (max(11f, w * .0093f) * (1.0 - progress * .31)).toFloat()
        p.color = Color.argb(68, 0, 0, 0)
        c.drawOval(RectF(smoothBallX - r * 1.50f, smoothBallY + r * .62f, smoothBallX + r * 1.50f, smoothBallY + r * 1.32f), p)
        p.shader = RadialGradient(
            smoothBallX - r * .31f, smoothBallY - r * .34f, r * 1.55f,
            intArrayOf(Color.WHITE, Color.rgb(246, 248, 243), Color.rgb(187, 194, 184)),
            floatArrayOf(0f, .70f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(smoothBallX, smoothBallY, r, p)
        p.shader = null
    }''')

v17 = replace_private_fun(v17, "drawTopSimulatorHud", r'''    private fun drawTopSimulatorHud(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val settings = engine.settings
        val totalW = w * .178f
        val hh = h * .056f
        val left = w * .5f - totalW * .5f
        val top = h * .025f

        p.color = Color.argb(158, 13, 18, 18)
        c.drawRoundRect(RectF(left, top, left + totalW, top + hh), hh * .50f, hh * .50f, p)
        val split = left + totalW * .57f
        p.color = Color.argb(55, 255, 255, 255)
        c.drawRect(split, top + hh * .20f, split + 1f, top + hh * .80f, p)

        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(8f, w * .0053f)
        p.color = Color.argb(170, 229, 235, 229)
        c.drawText("DISTANCE", left + totalW * .285f, top + hh * .35f, p)
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = max(16f, w * .0115f)
        p.color = Color.WHITE
        c.drawText("${"%.1f".format(settings.holeDistanceM)}m", left + totalW * .285f, top + hh * .76f, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(8f, w * .0052f)
        p.color = Color.argb(170, 229, 235, 229)
        c.drawText("GREEN", left + totalW * .785f, top + hh * .35f, p)
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = max(14f, w * .0098f)
        p.color = Color.rgb(123, 235, 153)
        c.drawText("%.1f".format(settings.stimpMeters), left + totalW * .785f, top + hh * .74f, p)
        p.textAlign = Paint.Align.LEFT
    }''')

v17 = replace_private_fun(v17, "drawContextHud", r'''    private fun drawContextHud(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val game = engine.gameModes.status
        val shot = engine.currentShot
        val running = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
        val preShot = !running && engine.lastResult == null

        if (game.totalHoles > 0 || game.playerCount > 1) {
            val left = w * .025f
            val top = h * .030f
            val bw = w * .135f
            val bh = h * .045f
            p.color = Color.argb(118, 13, 18, 18)
            c.drawRoundRect(RectF(left, top, left + bw, top + bh), bh * .5f, bh * .5f, p)
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = max(8f, w * .0055f)
            p.color = Color.WHITE
            val hole = if (game.totalHoles > 0) "HOLE ${game.hole}/${game.totalHoles}" else game.mode.label
            c.drawText("$hole   ·   P${game.activePlayer}/${game.playerCount}", left + w * .010f, top + bh * .66f, p)
        }

        if (preShot) drawWaitPill(c)

        if (shot != null && !preShot) {
            val left = w * .030f
            val bottom = h * .952f
            val top = bottom - h * .064f
            val right = left + w * .325f
            p.color = Color.argb(132, 12, 17, 17)
            c.drawRoundRect(RectF(left, top, right, bottom), h * .018f, h * .018f, p)

            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = max(8f, w * .0056f)
            p.color = Color.argb(165, 230, 236, 230)
            c.drawText("BALL", left + w * .012f, top + h * .026f, p)
            c.drawText("START", left + w * .108f, top + h * .026f, p)
            c.drawText("FACE", left + w * .214f, top + h * .026f, p)

            p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            p.textSize = max(14f, w * .0093f)
            p.color = Color.WHITE
            c.drawText("%.2f".format(shot.ballSpeedMps), left + w * .012f, top + h * .052f, p)
            c.drawText("%+.2f°".format(shot.launchAngleDeg), left + w * .108f, top + h * .052f, p)
            p.color = Color.rgb(125, 236, 155)
            c.drawText(shot.faceAngleDeg?.let { "%+.2f°".format(it) } ?: "--", left + w * .214f, top + h * .052f, p)
        }
    }''')

v17 = replace_private_fun(v17, "drawWaitPill", r'''    private fun drawWaitPill(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val ww = w * .070f
        val hh = h * .031f
        val left = w * .5f - ww * .5f
        val top = h * .875f
        p.color = Color.argb(120, 12, 17, 17)
        c.drawRoundRect(RectF(left, top, left + ww, top + hh), hh * .5f, hh * .5f, p)
        p.color = Color.rgb(108, 229, 140)
        c.drawCircle(left + ww * .22f, top + hh * .50f, max(3f, w * .0020f), p)
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(8f, w * .0053f)
        p.color = Color.WHITE
        c.drawText("READY", left + ww * .58f, top + hh * .68f, p)
        p.textAlign = Paint.Align.LEFT
    }''')

v17 = replace_private_fun(v17, "drawResultHud", r'''    private fun drawResultHud(c: Canvas) {
        val result = engine.lastResult ?: return
        val settings = engine.settings
        val score = engine.strokeScore
        val w = width.toFloat()
        val h = height.toFloat()
        val boxW = w * .205f
        val boxH = h * .112f
        val left = w * .5f - boxW * .5f
        val top = h * .755f

        p.color = Color.argb(168, 12, 17, 17)
        c.drawRoundRect(RectF(left, top, left + boxW, top + boxH), h * .022f, h * .022f, p)

        val longMiss = result.finishY - settings.holeDistanceM
        val sideMiss = result.finishX
        val missLabel = when {
            result.holed -> "NICE PUTT"
            result.lipOut -> "LIP OUT"
            abs(longMiss) >= abs(sideMiss) && longMiss < 0 -> "SHORT"
            abs(longMiss) >= abs(sideMiss) -> "LONG"
            sideMiss < 0 -> "LEFT"
            else -> "RIGHT"
        }

        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(9f, w * .0062f)
        p.color = if (result.holed) Color.rgb(255, 214, 83) else Color.rgb(124, 235, 154)
        c.drawText(missLabel, left + boxW * .5f, top + boxH * .27f, p)

        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = max(27f, w * .0195f)
        p.color = Color.WHITE
        val main = if (result.holed) "IN" else "${"%.0f".format(result.distanceToCupM * 100.0)}cm"
        c.drawText(main, left + boxW * .5f, top + boxH * .66f, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(7f, w * .0049f)
        p.color = Color.argb(180, 228, 235, 229)
        c.drawText("SCORE ${score?.total ?: 0}", left + boxW * .5f, top + boxH * .88f, p)
        p.textAlign = Paint.Align.LEFT
    }''')

# Keep the moving shot trail subtle; the aim line should be the strong red pre-shot element.
v17 = v17.replace(
    "drawTrail(it, Color.argb(210, 255, 214, 77), max(4f, width * .0025f))",
    "drawTrail(it, Color.argb(178, 255, 224, 105), max(3f, width * .0018f))",
    1,
)
v17_path.write_text(v17, encoding="utf-8")

# Both the real external TV and every local-TV preview must render the same screen.
ext_path = PKG / "ExternalDisplayController.kt"
ext = ext_path.read_text(encoding="utf-8")
if "V16SimulatorTvView(context, engine)" not in ext:
    raise SystemExit("ExternalDisplayController V16 renderer hook missing")
ext = ext.replace("V16SimulatorTvView(context, engine)", "V17SimulatorTvView(context, engine)", 1)
ext = ext.replace("V16 SIM UI", "V17 SIM UI")
ext_path.write_text(ext, encoding="utf-8")

main_path = PKG / "MainActivity.kt"
main = main_path.read_text(encoding="utf-8")
if main.count("GreenView(this, engine)") != 2:
    raise SystemExit(f"expected exactly 2 legacy local GreenView previews, got {main.count('GreenView(this, engine)')}")
main = main.replace("GreenView(this, engine)", "V17SimulatorTvView(this, engine)")

# Hardwareless lab: start on a clean full-screen TV preview. LAB / X buttons expose controls only when needed.
start = main.find("    private fun showHardwarelessTestLab()")
if start < 0:
    raise SystemExit("showHardwarelessTestLab missing")
end = main.find("\n    private fun ", start + 20)
if end < 0:
    raise SystemExit("showHardwarelessTestLab end missing")
section = main[start:end]
section = section.replace("Color.argb(238, 4, 8, 10)", "Color.argb(214, 4, 8, 10)", 1)
section = section.replace("CAMERA MOCK ●  ·  TV LOCAL ●", "CAMERA MOCK ●  ·  TV V17 LOCAL ●", 1)
old_scroll = '''        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(controls, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, FrameLayout.LayoutParams(pvDp(if (compactLandscape) 250 else 300), -1, Gravity.END).apply {
            topMargin = pvDp(8)
            bottomMargin = pvDp(8)
            marginEnd = pvDp(8)
        })

        dialog.setContentView(root)'''
new_scroll = '''        val scroll = ScrollView(this).apply {
            isFillViewport = true
            visibility = View.GONE
            addView(controls, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, FrameLayout.LayoutParams(pvDp(if (compactLandscape) 190 else 220), -1, Gravity.END).apply {
            topMargin = pvDp(48)
            bottomMargin = pvDp(8)
            marginEnd = pvDp(8)
        })

        val labToggle = pvButton("LAB", PvButtonStyle.GHOST, textSp = 6.5f) {
            scroll.visibility = if (scroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        root.addView(labToggle, FrameLayout.LayoutParams(pvDp(58), pvDp(32), Gravity.TOP or Gravity.END).apply {
            topMargin = pvDp(8)
            marginEnd = pvDp(48)
        })
        root.addView(pvButton("×", PvButtonStyle.GHOST, textSp = 8f) { dialog.dismiss() }, FrameLayout.LayoutParams(pvDp(34), pvDp(32), Gravity.TOP or Gravity.END).apply {
            topMargin = pvDp(8)
            marginEnd = pvDp(8)
        })

        dialog.setContentView(root)'''
if old_scroll not in section:
    raise SystemExit("hardwareless lab scroll block changed; refusing blind patch")
section = section.replace(old_scroll, new_scroll, 1)
main = main[:start] + section + main[end:]
main_path.write_text(main, encoding="utf-8")

print("V17 simulator UI generated and wired")
