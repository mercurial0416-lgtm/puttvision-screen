from pathlib import Path

main = Path("app/src/main/java/com/puttvision/screen/MainActivity.kt")
text = main.read_text(encoding="utf-8")
changed = False

if "V22CustomGreenRuntime.install(this)" not in text:
    marker = "        V20ProductPreferences.install(this)\n"
    if marker not in text:
        raise SystemExit("V22 runtime install marker missing")
    text = text.replace(
        marker,
        marker + "        V22CustomGreenRuntime.install(this)\n        V22AudioRuntime.install(this)\n",
        1,
    )
    changed = True
    print("V22 custom green/audio runtime install wired")

legacy = '''        actions.addView(pvButton("같은 조건 다시", PvButtonStyle.GHOST, textSp = if (compact) 7.8f else 9f, radiusDp = Pv.rLg) {
            sessionActive = false
            measurementSuspended = true
            if (activeSessionIsGame) showGameEntrance() else showPracticeEntrance()
        }, LinearLayout.LayoutParams(0, sdp(if (compact) 40 else 46), 1f).apply { marginEnd = sdp(5) })
        actions.addView(pvButton("추천 훈련 시작", PvButtonStyle.PRIMARY, textSp = if (compact) 8f else 9.2f, radiusDp = Pv.rLg) {
            applyAutoCoachPlan(report.plan)
        }, LinearLayout.LayoutParams(0, sdp(if (compact) 40 else 46), 1f).apply { marginStart = sdp(5) })'''

v22 = '''        actions.addView(pvButton("같은 조건 다시", PvButtonStyle.GHOST, textSp = if (compact) 7.2f else 8.3f, radiusDp = Pv.rLg) {
            sessionActive = false
            measurementSuspended = true
            if (activeSessionIsGame) showGameEntrance() else showPracticeEntrance()
        }, LinearLayout.LayoutParams(0, sdp(if (compact) 40 else 46), 1f).apply { marginEnd = sdp(4) })
        actions.addView(pvButton("PDF · CSV 공유", PvButtonStyle.GHOST, textSp = if (compact) 7.2f else 8.3f, radiusDp = Pv.rLg) {
            V22ReportExporter.share(this, statsRepository.recent(120))
        }, LinearLayout.LayoutParams(0, sdp(if (compact) 40 else 46), 1f).apply { marginStart = sdp(4); marginEnd = sdp(4) })
        actions.addView(pvButton("추천 훈련 시작", PvButtonStyle.PRIMARY, textSp = if (compact) 7.2f else 8.3f, radiusDp = Pv.rLg) {
            applyAutoCoachPlan(report.plan)
        }, LinearLayout.LayoutParams(0, sdp(if (compact) 40 else 46), 1f).apply { marginStart = sdp(4) })'''

if v22 not in text:
    count = text.count(legacy)
    if count != 1:
        raise SystemExit(f"V22 report action: expected 1 legacy block, got {count}")
    text = text.replace(legacy, v22, 1)
    changed = True
    print("V22 report share action wired")

if changed:
    main.write_text(text, encoding="utf-8")
else:
    print("V22 MainActivity already current")

# Preserve camera thermal headroom: render the 3D TV fast only while the ball/camera is moving.
gl = Path("app/src/main/java/com/puttvision/screen/V18OpenGlSimulator.kt")
gl_text = gl.read_text(encoding="utf-8")
gl_changed = False
if "import android.os.Handler" not in gl_text:
    gl_text = gl_text.replace(
        "import android.opengl.Matrix\n",
        "import android.opengl.Matrix\nimport android.os.Handler\nimport android.os.Looper\n",
        1,
    )
    gl_changed = True

legacy_view = '''private class V18PuttingGlView(
    context: Context,
    engine: GameEngine
) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setRenderer(V18PuttingRenderer(engine))
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }
}'''

throttled_view = '''private class V18PuttingGlView(
    context: Context,
    private val engine: GameEngine
) : GLSurfaceView(context) {
    private val renderHandler = Handler(Looper.getMainLooper())
    private var loopRunning = false
    private val renderTick = object : Runnable {
        override fun run() {
            if (!loopRunning || !isAttachedToWindow) return
            requestRender()
            val moving = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
            val delay = when {
                moving -> 16L
                engine.lastResult == null -> 66L
                else -> 180L
            }
            renderHandler.postDelayed(this, delay)
        }
    }

    init {
        setEGLContextClientVersion(2)
        setRenderer(V18PuttingRenderer(engine))
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loopRunning = true
        renderHandler.removeCallbacks(renderTick)
        renderHandler.post(renderTick)
    }

    override fun onDetachedFromWindow() {
        loopRunning = false
        renderHandler.removeCallbacks(renderTick)
        super.onDetachedFromWindow()
    }
}'''

if throttled_view not in gl_text:
    count = gl_text.count(legacy_view)
    if count != 1:
        raise SystemExit(f"V22 GL throttle: expected 1 legacy view, got {count}")
    gl_text = gl_text.replace(legacy_view, throttled_view, 1)
    gl_changed = True
    print("V22 adaptive OpenGL render cadence wired")

if gl_changed:
    gl.write_text(gl_text, encoding="utf-8")
else:
    print("V22 OpenGL cadence already current")
