from pathlib import Path

path = Path("app/src/main/java/com/puttvision/screen/GreenView.kt")
text = path.read_text(encoding="utf-8")
marker = "// V15 GHOST BEST TRAIL"
if marker in text:
    print("V15 ghost TV overlay already current")
    raise SystemExit(0)

needle = '''        val instantTrail = TvInstantRollRuntime.visibleTrail()
'''
if text.count(needle) != 1:
    raise SystemExit(f"ghost insertion point: expected 1 match, got {text.count(needle)}")

insert = '''        // V15 GHOST BEST TRAIL
        if (engine.gameModes.status.mode == PracticeMode.GHOST) {
            V15GhostRuntime.referenceForCurrent(settings)?.let { ghost ->
                if (ghost.trail.size >= 2) {
                    val ghostPath = Path().apply {
                        moveTo(
                            sx(ghost.trail.first().first, ghost.trail.first().second),
                            sySurface(ghost.trail.first().first, ghost.trail.first().second)
                        )
                        ghost.trail.drop(1).forEach { point ->
                            lineTo(sx(point.first, point.second), sySurface(point.first, point.second))
                        }
                    }
                    p.style = Paint.Style.STROKE
                    p.strokeCap = Paint.Cap.ROUND
                    p.strokeWidth = max(3f, w * .00215f)
                    p.pathEffect = DashPathEffect(
                        floatArrayOf(max(11f, w * .0085f), max(7f, w * .0055f)),
                        0f
                    )
                    p.color = Color.argb(150, 109, 233, 255)
                    c.drawPath(ghostPath, p)
                    p.pathEffect = null
                    p.strokeCap = Paint.Cap.BUTT
                    p.style = Paint.Style.FILL

                    val labelPoint = ghost.trail.getOrNull((ghost.trail.size * .70).toInt().coerceIn(0, ghost.trail.lastIndex))
                    if (labelPoint != null) {
                        val gx = sx(labelPoint.first, labelPoint.second)
                        val gy = sySurface(labelPoint.first, labelPoint.second)
                        p.typeface = Typeface.DEFAULT_BOLD
                        p.textSize = max(9f, w * .0068f)
                        p.color = Color.argb(210, 176, 244, 255)
                        c.drawText("GHOST · BEST ${ghost.record.strokeScore.total}", gx + w * .008f, gy - h * .010f, p)
                    }
                }
            }
        }

'''
text = text.replace(needle, insert + needle, 1)
path.write_text(text, encoding="utf-8")
print("V15 ghost TV overlay applied")
