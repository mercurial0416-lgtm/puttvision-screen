from pathlib import Path

# V18 OpenGL: dynamic mesh + render cadence from V24 policy.
gl = Path("app/src/main/java/com/puttvision/screen/V18OpenGlSimulator.kt")
text = gl.read_text(encoding="utf-8")
changed = False

if "private val appContext = context.applicationContext" not in text:
    marker = '''private class V18PuttingGlView(
    context: Context,
    private val engine: GameEngine
) : GLSurfaceView(context) {
    private val renderHandler = Handler(Looper.getMainLooper())'''
    replacement = '''private class V18PuttingGlView(
    context: Context,
    private val engine: GameEngine
) : GLSurfaceView(context) {
    private val appContext = context.applicationContext
    private val renderHandler = Handler(Looper.getMainLooper())'''
    if text.count(marker) != 1:
        raise SystemExit("V24 GL context insertion point missing")
    text = text.replace(marker, replacement, 1)
    changed = True

legacy_delay = '''            val moving = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
            val delay = when {
                moving -> 16L
                engine.lastResult == null -> 66L
                else -> 180L
            }'''
quality_delay = '''            val moving = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
            val tier = V24TvQualityRuntime.snapshot(appContext).tier
            val delay = when {
                moving -> tier.movingFrameMs
                engine.lastResult == null -> tier.idleFrameMs
                else -> max(180L, tier.idleFrameMs * 2L)
            }'''
if quality_delay not in text:
    if text.count(legacy_delay) != 1:
        raise SystemExit("V24 GL cadence block missing")
    text = text.replace(legacy_delay, quality_delay, 1)
    changed = True

if "setRenderer(V18PuttingRenderer(appContext, engine))" not in text:
    old = "setRenderer(V18PuttingRenderer(engine))"
    if text.count(old) != 1:
        raise SystemExit("V24 renderer constructor call missing")
    text = text.replace(old, "setRenderer(V18PuttingRenderer(appContext, engine))", 1)
    changed = True

legacy_key = '''private data class V18TerrainKey(
    val profile: Int,
    val distance100: Int,
    val side100: Int,
    val long100: Int
)'''
quality_key = '''private data class V18TerrainKey(
    val profile: Int,
    val distance100: Int,
    val side100: Int,
    val long100: Int,
    val qualityTier: V24RenderTier
)'''
if quality_key not in text:
    if text.count(legacy_key) != 1:
        raise SystemExit("V24 terrain key block missing")
    text = text.replace(legacy_key, quality_key, 1)
    changed = True

legacy_renderer = '''private class V18PuttingRenderer(
    private val engine: GameEngine
) : GLSurfaceView.Renderer {'''
quality_renderer = '''private class V18PuttingRenderer(
    private val context: Context,
    private val engine: GameEngine
) : GLSurfaceView.Renderer {'''
if quality_renderer not in text:
    if text.count(legacy_renderer) != 1:
        raise SystemExit("V24 renderer class block missing")
    text = text.replace(legacy_renderer, quality_renderer, 1)
    changed = True

legacy_ensure = '''    private fun ensureTerrain(settings: GreenSettings) {
        val key = V18TerrainKey(
            settings.terrainProfileId,
            (settings.holeDistanceM * 100).toInt(),
            (settings.sideSlopePct * 100).toInt(),
            (settings.longSlopePct * 100).toInt()
        )
        if (key == terrainKey) return
        terrainKey = key
        terrainMesh = buildTerrain(settings)
        roughMesh = buildRough(settings)
        decorMesh = V18Mesh(V18ProceduralDecor.build(settings))
    }'''
quality_ensure = '''    private fun ensureTerrain(settings: GreenSettings) {
        val tier = V24TvQualityRuntime.snapshot(context).tier
        val key = V18TerrainKey(
            settings.terrainProfileId,
            (settings.holeDistanceM * 100).toInt(),
            (settings.sideSlopePct * 100).toInt(),
            (settings.longSlopePct * 100).toInt(),
            tier
        )
        if (key == terrainKey) return
        terrainKey = key
        terrainMesh = buildTerrain(settings, tier)
        roughMesh = buildRough(settings)
        decorMesh = V18Mesh(V18ProceduralDecor.build(settings))
    }'''
if quality_ensure not in text:
    if text.count(legacy_ensure) != 1:
        raise SystemExit("V24 ensureTerrain block missing")
    text = text.replace(legacy_ensure, quality_ensure, 1)
    changed = True

if "private fun buildTerrain(settings: GreenSettings, tier: V24RenderTier): V18Mesh" not in text:
    old = '''    private fun buildTerrain(settings: GreenSettings): V18Mesh {
        val distance = max(3.5, settings.holeDistanceM * 1.32)
        val halfWidth = max(1.45, settings.holeDistanceM * .20)
        val cols = 28
        val rows = 72'''
    new = '''    private fun buildTerrain(settings: GreenSettings, tier: V24RenderTier): V18Mesh {
        val distance = max(3.5, settings.holeDistanceM * 1.32)
        val halfWidth = max(1.45, settings.holeDistanceM * .20)
        val cols = tier.terrainCols
        val rows = tier.terrainRows'''
    if text.count(old) != 1:
        raise SystemExit("V24 terrain mesh resolution block missing")
    text = text.replace(old, new, 1)
    changed = True

if changed:
    gl.write_text(text, encoding="utf-8")
    print("V24 adaptive GL quality wired")
else:
    print("V24 OpenGL already current")

# Startup persistence.
main = Path("app/src/main/java/com/puttvision/screen/MainActivity.kt")
text = main.read_text(encoding="utf-8")
if "V24TvQualityRuntime.install(this)" not in text:
    marker = "        V22AudioRuntime.install(this)\n"
    if text.count(marker) != 1:
        raise SystemExit("V24 runtime install marker missing")
    main.write_text(text.replace(marker, marker + "        V24TvQualityRuntime.install(this)\n", 1), encoding="utf-8")
    print("V24 runtime startup wired")
else:
    print("V24 runtime startup already current")

# Product setup card.
setup = Path("app/src/main/java/com/puttvision/screen/ProductSetupDialog.kt")
text = setup.read_text(encoding="utf-8")
quality_action = '''    addAction(action("TV 3D QUALITY", V24TvQualityRuntime.label()) {
        showV24TvQualityDialog(context)
    })

'''
if quality_action not in text:
    marker = '''    addAction(action("PUTTING AUDIO", if (V22AudioRuntime.enabled) "공 타격 · 롤 · 컵 사운드 ON" else "사운드 OFF") {'''
    if text.count(marker) != 1:
        raise SystemExit("V24 ProductSetup insertion marker missing")
    text = text.replace(marker, quality_action + marker, 1)
    setup.write_text(text, encoding="utf-8")
    print("V24 product setting wired")
else:
    print("V24 product setting already current")
