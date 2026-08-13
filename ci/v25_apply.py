from pathlib import Path
import json

# V18: publish exact GL camera matrix and draw flag-side live distance/elevation HUD.
gl = Path("app/src/main/java/com/puttvision/screen/V18OpenGlSimulator.kt")
text = gl.read_text(encoding="utf-8")
changed = False

marker = "    private var aspect = 16f / 9f\n"
insert = marker + "    private var viewportWidth = 0\n    private var viewportHeight = 0\n"
if "private var viewportWidth = 0" not in text:
    if text.count(marker) != 1: raise SystemExit("V25 viewport field marker missing")
    text = text.replace(marker, insert, 1); changed = True

old = '''    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.perspectiveM(projection, 0, 43f, aspect, .05f, 45f)
    }'''
new = '''    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.perspectiveM(projection, 0, 43f, aspect, .05f, 45f)
    }'''
if new not in text:
    if text.count(old) != 1: raise SystemExit("V25 onSurfaceChanged marker missing")
    text = text.replace(old, new, 1); changed = True

old = "        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)\n        GLES20.glUseProgram(program)"
new = "        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)\n        V25FlagProjectionRuntime.publish(mvp, viewportWidth, viewportHeight)\n        GLES20.glUseProgram(program)"
if "V25FlagProjectionRuntime.publish" not in text:
    if text.count(old) != 1: raise SystemExit("V25 MVP publish marker missing")
    text = text.replace(old, new, 1); changed = True

old = '''        drawTopPill(c)
        drawMode(c)
        drawTelemetry(c)'''
new = '''        drawTopPill(c)
        drawMode(c)
        drawFlagInfo(c)
        drawTelemetry(c)'''
if "drawFlagInfo(c)" not in text:
    if text.count(old) != 1: raise SystemExit("V25 HUD draw marker missing")
    text = text.replace(old, new, 1); changed = True

flag_fn = r'''    private fun drawFlagInfo(c: Canvas) {
        val anchor = V25FlagProjectionRuntime.projectFlag(engine.settings) ?: return
        val info = V25FlagInfoRuntime.current(engine)
        val w = width.toFloat(); val h = height.toFloat()
        if (anchor.x < -w * .02f || anchor.x > w * 1.02f || anchor.y < 0f || anchor.y > h) return
        val bw = w * .145f; val bh = h * .073f
        val left = (anchor.x + w * .016f).coerceIn(w * .012f, w - bw - w * .012f)
        val top = (anchor.y - bh * .52f).coerceIn(h * .10f, h - bh - h * .08f)
        p.color = Color.argb(188, 12, 18, 17)
        c.drawRoundRect(RectF(left, top, left + bw, top + bh), bh * .23f, bh * .23f, p)
        p.color = Color.argb(205, 255, 255, 255)
        c.drawRect(anchor.x, anchor.y - 1f, left, anchor.y + 1f, p)
        p.textAlign = Paint.Align.LEFT; p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(9f, w * .0063f); p.color = Color.WHITE
        c.drawText(info.distanceLabel, left + bw * .08f, top + bh * .40f, p)
        p.textSize = max(8f, w * .0055f)
        p.color = when {
            info.heightDeltaM > .005 -> Color.rgb(255, 214, 94)
            info.heightDeltaM < -.005 -> Color.rgb(124, 214, 255)
            else -> Color.argb(205, 230, 236, 230)
        }
        c.drawText(info.heightLabel, left + bw * .08f, top + bh * .76f, p)
    }

'''
if "private fun drawFlagInfo(c: Canvas)" not in text:
    marker = "    private fun drawTelemetry(c: Canvas) {\n"
    if text.count(marker) != 1: raise SystemExit("V25 drawFlagInfo insertion marker missing")
    text = text.replace(marker, flag_fn + marker, 1); changed = True

old = 'c.drawText(if(r.holed)"IN" else "${"%.0f".format(r.distanceToCupM*100)} cm",w*.5f,t+hh*.68f,p)'
new = 'c.drawText(if(r.holed)"IN" else "${"%.2f".format(r.distanceToCupM)} m",w*.5f,t+hh*.68f,p)'
if old in text:
    text = text.replace(old, new, 1); changed = True

if changed:
    gl.write_text(text, encoding="utf-8")
    print("V25 V18 flag HUD wired")
else:
    print("V25 V18 already current")

# V17 fallback: same information, anchored to its projected flag pole midpoint.
v17 = Path("app/src/main/java/com/puttvision/screen/V17SimulatorTvView.kt")
text = v17.read_text(encoding="utf-8")
changed = False
marker = '''        p.color = Color.rgb(224, 64, 52)
        c.drawPath(flag, p)
    }
'''
insert = '''        p.color = Color.rgb(224, 64, 52)
        c.drawPath(flag, p)

        val info = V25FlagInfoRuntime.current(engine)
        val bw = w * .145f
        val bh = h * .073f
        val anchorY = hy - poleH * .52f
        val left = (hx + w * .016f).coerceIn(w * .012f, w - bw - w * .012f)
        val top = (anchorY - bh * .50f).coerceIn(h * .10f, h - bh - h * .08f)
        p.color = Color.argb(188, 12, 18, 17)
        c.drawRoundRect(RectF(left, top, left + bw, top + bh), bh * .23f, bh * .23f, p)
        p.color = Color.argb(205, 255, 255, 255)
        c.drawRect(hx, anchorY - 1f, left, anchorY + 1f, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(9f, w * .0063f)
        p.color = Color.WHITE
        c.drawText(info.distanceLabel, left + bw * .08f, top + bh * .40f, p)
        p.textSize = max(8f, w * .0055f)
        p.color = when {
            info.heightDeltaM > .005 -> Color.rgb(255, 214, 94)
            info.heightDeltaM < -.005 -> Color.rgb(124, 214, 255)
            else -> Color.argb(205, 230, 236, 230)
        }
        c.drawText(info.heightLabel, left + bw * .08f, top + bh * .76f, p)
    }
'''
if "val info = V25FlagInfoRuntime.current(engine)" not in text:
    if text.count(marker) != 1: raise SystemExit("V25 V17 flag marker missing")
    text = text.replace(marker, insert, 1); changed = True

old = 'val main = if (result.holed) "IN" else "${"%.0f".format(result.distanceToCupM * 100.0)}cm"'
new = 'val main = if (result.holed) "IN" else "${"%.2f".format(result.distanceToCupM)} m"'
if old in text:
    text = text.replace(old, new, 1); changed = True

if changed:
    v17.write_text(text, encoding="utf-8")
    print("V25 V17 flag HUD wired")
else:
    print("V25 V17 already current")

# Feature matrix stays honest about real-device validation.
fm = Path("FEATURE_MATRIX.json")
data = json.loads(fm.read_text(encoding="utf-8"))
data["version"] = "v25-development"
f = data.setdefault("features", {})
f["adaptive_tv_3d_quality"] = True
f["flag_side_live_remaining_distance_m"] = True
f["flag_side_live_height_delta_m"] = True
f["tv_distance_units_fixed_to_meters"] = True
f["flag_info_tracks_opengl_camera"] = True
v = data.setdefault("validation", {})
v["v25_flag_unit_regression_test"] = True
fm.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("V25 feature matrix updated")
