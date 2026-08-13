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

# V18 stage: the SAM-like studio is result-only and therefore safe to overlay without polluting address mode.
stage = Path("app/src/main/java/com/puttvision/screen/V18OpenGlSimulator.kt")
text = stage.read_text(encoding="utf-8")
if "V19StrokeStudioOverlay(context, engine)" not in text:
    old = "        addView(hud, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))\n"
    new = old + "        addView(V19StrokeStudioOverlay(context, engine), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))\n"
    if old not in text:
        raise SystemExit("V18 overlay insertion point missing")
    stage.write_text(text.replace(old, new, 1), encoding="utf-8")
    print("V18 TV stroke studio overlay wired")

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
