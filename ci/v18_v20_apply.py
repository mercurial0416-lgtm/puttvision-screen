from pathlib import Path


def replace_all(path: Path, old: str, new: str, label: str, minimum: int = 1):
    text = path.read_text(encoding="utf-8")
    if new in text and old not in text:
        print(f"{label}: already current")
        return False
    count = text.count(old)
    if count < minimum:
        raise SystemExit(f"{label}: expected at least {minimum} legacy match, got {count}")
    path.write_text(text.replace(old, new), encoding="utf-8")
    print(f"{label}: replaced {count}")
    return True


# MainActivity: local preview and hardwareless lab must render the exact same 3D stage as the TV.
main = Path("app/src/main/java/com/puttvision/screen/MainActivity.kt")
text = main.read_text(encoding="utf-8")
changed = False
if "V20ProductPreferences.install(this)" not in text:
    marker = "        V16DeviceAutoCalibrationRuntime.install(this)\n"
    if marker not in text:
        raise SystemExit("MainActivity V20 preference insertion point missing")
    text = text.replace(marker, marker + "        V20ProductPreferences.install(this)\n", 1)
    changed = True
count = text.count("V17SimulatorTvView(this, engine)")
if count:
    text = text.replace("V17SimulatorTvView(this, engine)", "V18SimulatorFactory.create(this, engine)")
    changed = True
    print(f"MainActivity local V18 stage: replaced {count}")
if changed:
    main.write_text(text, encoding="utf-8")

# V18 stage: SAM-like result overlay plus procedural scenery so the real 3D path is not visually sparse.
stage = Path("app/src/main/java/com/puttvision/screen/V18OpenGlSimulator.kt")
text = stage.read_text(encoding="utf-8")
stage_changed = False
if "V19StrokeStudioOverlay(context, engine)" not in text:
    old = "        addView(hud, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))\n"
    new = old + "        addView(V19StrokeStudioOverlay(context, engine), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))\n"
    if old not in text:
        raise SystemExit("V18 overlay insertion point missing")
    text = text.replace(old, new, 1)
    stage_changed = True
    print("V18 TV stroke studio overlay wired")

if "private var decorMesh: V18Mesh? = null" not in text:
    old = "    private var roughMesh: V18Mesh? = null\n"
    if old not in text:
        raise SystemExit("V18 decor field insertion point missing")
    text = text.replace(old, old + "    private var decorMesh: V18Mesh? = null\n", 1)
    stage_changed = True

if "decorMesh?.let { draw(it, GLES20.GL_TRIANGLES) }" not in text:
    old = "        roughMesh?.let { draw(it, GLES20.GL_TRIANGLES) }\n        terrainMesh?.let { draw(it, GLES20.GL_TRIANGLES) }\n"
    new = "        roughMesh?.let { draw(it, GLES20.GL_TRIANGLES) }\n        decorMesh?.let { draw(it, GLES20.GL_TRIANGLES) }\n        terrainMesh?.let { draw(it, GLES20.GL_TRIANGLES) }\n"
    if old not in text:
        raise SystemExit("V18 decor draw insertion point missing")
    text = text.replace(old, new, 1)
    stage_changed = True

if "decorMesh = V18Mesh(V18ProceduralDecor.build(settings))" not in text:
    old = "        terrainMesh = buildTerrain(settings)\n        roughMesh = buildRough(settings)\n"
    new = old + "        decorMesh = V18Mesh(V18ProceduralDecor.build(settings))\n"
    if old not in text:
        raise SystemExit("V18 decor build insertion point missing")
    text = text.replace(old, new, 1)
    stage_changed = True

legacy_ball = '''    private fun drawBall(settings: GreenSettings) {
        val state = engine.state
        val display = TvInstantRollRuntime.displayPosition(state) ?: if (state != null) state.x to state.y else 0.0 to 0.0
        val z = GreenTerrain.effectiveHeightAt(settings, display.first, display.second).toFloat() + .023f
        draw(sphere(display.first.toFloat(), display.second.toFloat(), z, .043f), GLES20.GL_TRIANGLES)
    }'''
shadow_ball = '''    private fun drawBall(settings: GreenSettings) {
        val state = engine.state
        val display = TvInstantRollRuntime.displayPosition(state) ?: if (state != null) state.x to state.y else 0.0 to 0.0
        val ground = GreenTerrain.effectiveHeightAt(settings, display.first, display.second).toFloat()
        // Soft contact shadow anchors the ball to the 3D green instead of making it look pasted on.
        draw(
            circle(display.first.toFloat() + .012f, display.second.toFloat() + .010f, ground + .003f, .052f,
                floatArrayOf(.015f, .020f, .015f, .22f), 24),
            GLES20.GL_TRIANGLES
        )
        draw(sphere(display.first.toFloat(), display.second.toFloat(), ground + .043f, .043f), GLES20.GL_TRIANGLES)
    }'''
if shadow_ball not in text:
    if text.count(legacy_ball) != 1:
        raise SystemExit(f"V18 ball shadow: expected 1 legacy match, got {text.count(legacy_ball)}")
    text = text.replace(legacy_ball, shadow_ball, 1)
    stage_changed = True

if stage_changed:
    stage.write_text(text, encoding="utf-8")
    print("V18 procedural 3D scenery wired")

# Phone HFR replay: add current / ideal / best corridor on the impact frame.
replay = Path("app/src/main/java/com/puttvision/screen/ImpactReplayView.kt")
text = replay.read_text(encoding="utf-8")
if "drawV19StudioComparison(canvas, media)" not in text:
    old = "            drawBestShotComparison(canvas, media)\n"
    if old not in text:
        raise SystemExit("ImpactReplay V19 call insertion point missing")
    text = text.replace(old, old + "            drawV19StudioComparison(canvas, media)\n", 1)

if "private fun drawV19StudioComparison" not in text:
    marker = "    override fun onDetachedFromWindow() {\n"
    if marker not in text:
        raise SystemExit("ImpactReplay V19 method insertion point missing")
    method = r'''    private fun drawV19StudioComparison(canvas: Canvas, media: RectF) {
        val model = V19StrokeStudioRuntime.latest ?: return
        if (model.current.size < 2 || model.ideal.size < 2) return
        val box = RectF(
            media.right - media.width() * .34f,
            media.top + media.height() * .06f,
            media.right - media.width() * .025f,
            media.bottom - media.height() * .08f
        )
        paint.color = Color.argb(126, 4, 8, 10)
        canvas.drawRoundRect(box, max(9f, width * .006f), max(9f, width * .006f), paint)

        fun trace(points: List<V19StrokeNode>, color: Int, stroke: Float) {
            if (points.size < 2) return
            val xMax = max(4.0, points.maxOf { kotlin.math.abs(it.xCm) } + 1.0)
            val yMin = points.minOf { it.yCm }
            val yMax = points.maxOf { it.yCm }.coerceAtLeast(yMin + 1.0)
            fun sx(x: Double) = box.centerX() + (x / xMax).toFloat() * box.width() * .44f
            fun sy(y: Double) = box.bottom - box.height() * .08f - ((y - yMin) / (yMax - yMin)).toFloat() * box.height() * .74f
            val path = Path().apply {
                moveTo(sx(points.first().xCm), sy(points.first().yCm))
                points.drop(1).forEach { lineTo(sx(it.xCm), sy(it.yCm)) }
            }
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = stroke
            paint.color = color
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
        }

        paint.color = Color.argb(42, 246, 190, 74)
        val corridor = (model.corridorCm / 5.0).toFloat().coerceIn(.03f, .22f) * box.width()
        canvas.drawRect(box.centerX() - corridor, box.top + box.height() * .12f, box.centerX() + corridor, box.bottom - box.height() * .06f, paint)
        trace(model.ghost, Color.argb(180, 86, 167, 255), max(2f, width * .0012f))
        trace(model.ideal, Color.argb(220, 246, 190, 74), max(2f, width * .0014f))
        trace(model.current, Color.argb(235, 78, 209, 121), max(3f, width * .0019f))

        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = max(8f, width * .0068f)
        paint.color = Color.WHITE
        canvas.drawText("STROKE Q${model.quality}", box.left + box.width() * .06f, box.top + box.height() * .09f, paint)
        paint.textSize = max(6.5f, width * .0055f)
        paint.color = Color.argb(210, 220, 228, 221)
        canvas.drawText("GREEN CURRENT  ·  GOLD IDEAL  ·  BLUE BEST", box.left + box.width() * .06f, box.bottom - box.height() * .025f, paint)
    }

'''
    text = text.replace(marker, method + marker, 1)

replay.write_text(text, encoding="utf-8")
print("V19 HFR replay comparison wired")
