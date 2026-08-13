from pathlib import Path

ROOT = Path('app/src/main/java/com/puttvision/screen')

def replace(path, old, new, label):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise RuntimeError(f'{path}: missing {label}')
    p.write_text(s.replace(old, new, 1), encoding='utf-8')

replace('PracticeGreenPreviewView.kt',
'''                val z = GreenTerrain.heightAt(styleIndex, realX, realY, holeDistanceM)''',
'''                val z = GreenTerrain.effectiveHeightAt(settings, realX, realY)''',
'preview effective height')

replace('GreenView.kt',
'''        val dynamic = engine.state?.running == true || engine.lastResult == null || ProductSessionRuntime.tvCalibrationGuide\n        if (dynamic) postInvalidateOnAnimation() else postInvalidateDelayed(200L)''',
'''        when {\n            engine.state?.running == true -> postInvalidateOnAnimation()\n            ProductSessionRuntime.tvCalibrationGuide -> postInvalidateDelayed(33L)\n            engine.lastResult == null -> postInvalidateDelayed(50L)\n            else -> postInvalidateDelayed(250L)\n        }''',
'adaptive frame pacing')

replace('GreenView.kt',
'''        val originHeight = GreenTerrain.heightAt(settings.terrainProfileId, 0.0, 0.0, settings.holeDistanceM)\n        fun sySurface(x: Double, y: Double): Float {\n            val z = GreenTerrain.heightAt(settings.terrainProfileId, x, y, settings.holeDistanceM)''',
'''        val originHeight = GreenTerrain.effectiveHeightAt(settings, 0.0, 0.0)\n        fun sySurface(x: Double, y: Double): Float {\n            val z = GreenTerrain.effectiveHeightAt(settings, x, y)''',
'TV effective height')

replace('GreenView.kt',
'''        fun sx(x: Double, y: Double): Float {\n            val yp = syBase(y)\n            val sideRange = max(1.15, settings.holeDistanceM * .20)\n            return centerX + (x / sideRange).toFloat() * halfWidthAt(yp)\n        }\n\n        p.style = Paint.Style.STROKE''',
'''        fun sx(x: Double, y: Double): Float {\n            val yp = syBase(y)\n            val sideRange = max(1.15, settings.holeDistanceM * .20)\n            return centerX + (x / sideRange).toFloat() * halfWidthAt(yp)\n        }\n\n        // V13 relief mesh. Vertices are projected from the same effective height\n        // field used by GreenPhysics, so highlights/valleys are geometry-derived.\n        val meshSave = c.save()\n        c.clipPath(greenShape)\n        val meshRows = 14\n        val meshCols = 10\n        val meshSideRange = max(1.15, settings.holeDistanceM * .20)\n        for (row in 0 until meshRows) {\n            val y0 = maxY * row / meshRows.toDouble()\n            val y1 = maxY * (row + 1) / meshRows.toDouble()\n            for (col in 0 until meshCols) {\n                val x0 = -meshSideRange + meshSideRange * 2.0 * col / meshCols.toDouble()\n                val x1 = -meshSideRange + meshSideRange * 2.0 * (col + 1) / meshCols.toDouble()\n                val mx = (x0 + x1) * .5\n                val my = (y0 + y1) * .5\n                val slope = GreenTerrain.effectiveSlopeAt(settings, mx, my)\n                val z = GreenTerrain.effectiveHeightAt(settings, mx, my) - originHeight\n                val light = (-slope.sidePct * .50 + slope.longPct * .34 + z * 310.0).coerceIn(-2.5, 2.5)\n                val alpha = (12 + abs(light) * 8.0).toInt().coerceIn(12, 34)\n                p.color = if (light >= 0.0) Color.argb(alpha, 225, 255, 228) else Color.argb(alpha, 0, 12, 5)\n                val cell = Path().apply {\n                    moveTo(sx(x0, y0), sySurface(x0, y0))\n                    lineTo(sx(x1, y0), sySurface(x1, y0))\n                    lineTo(sx(x1, y1), sySurface(x1, y1))\n                    lineTo(sx(x0, y1), sySurface(x0, y1))\n                    close()\n                }\n                p.style = Paint.Style.FILL\n                c.drawPath(cell, p)\n            }\n        }\n        c.restoreToCount(meshSave)\n\n        p.style = Paint.Style.STROKE''',
'relief mesh insertion')

replace('GreenView.kt',
'''                moveTo(sx(trail.first().first, trail.first().second), sy(trail.first().second))''',
'''                moveTo(sx(trail.first().first, trail.first().second), sySurface(trail.first().first, trail.first().second))''',
'actual trail first height')

replace('GreenView.kt',
'''        c.drawText(item.third, x + colW * .61f, top + h * .071f, p)\n    }\n}''',
'''        c.drawText(item.third, x + colW * .61f, top + h * .071f, p)\n    }\n    shot.uncertainty?.let { u ->\n        p.typeface = Typeface.DEFAULT_BOLD\n        p.textSize = max(8f, w * .0058f)\n        p.color = Color.argb(165, 188, 205, 196)\n        c.drawText(u.compact(), left + pad, bottom - h * .010f, p)\n    }\n}''',
'telemetry uncertainty')

replace('GreenView.kt',
'''    c.drawText(if (result.holed) "HOLED" else "RESULT", x, top + h * .034f, p)''',
'''    c.drawText(if (result.holed) "HOLED" else if (result.lipOut) "LIP OUT" else "RESULT", x, top + h * .034f, p)''',
'lip-out result label')

print('V13 green renderer patch applied')
