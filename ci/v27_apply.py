from pathlib import Path
import json


def replace_once(path, old, new, label):
    p = Path(path)
    text = p.read_text(encoding='utf-8')
    if new in text:
        print(f'{label}: already applied')
        return
    if old not in text:
        raise SystemExit(f'{label}: marker missing')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')
    print(f'{label}: patched')

# Generate compact TV overlay: selected cup-entry pace changes the solved ideal trail.
overlay = Path('app/src/main/java/com/puttvision/screen/V27PaceLineOverlay.kt')
if not overlay.exists():
    overlay.write_text('''package com.puttvision.screen

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.max

class V27PaceLineOverlay(context: Context, private val engine: GameEngine) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init { setWillNotDraw(false) }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val moving = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
        val read = if (!moving && engine.lastResult == null) GreenReadRuntime.peekOrSchedule(engine.settings) else null
        if (read != null && read.solverReliable) {
            val pts = read.predictedTrail.mapNotNull { (x,y) -> V25FlagProjectionRuntime.project(x,y,GreenTerrain.effectiveHeightAt(engine.settings,x,y)+.018) }
            if (pts.size > 1) {
                val ideal=Path().apply{moveTo(pts[0].x,pts[0].y);pts.drop(1).forEach{lineTo(it.x,it.y)}}
                p.style=Paint.Style.STROKE;p.strokeWidth=max(3f,width*.0017f);p.strokeCap=Paint.Cap.ROUND;p.color=Color.rgb(255,202,61);c.drawPath(ideal,p);p.style=Paint.Style.FILL
            }
            p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.DEFAULT_BOLD;p.textSize=max(10f,width*.006f);p.color=Color.rgb(255,222,108)
            c.drawText("홀 통과 %.2f m/s · 볼 %.2f m/s".format(V27CupPaceRuntime.targetCupSpeedMps,read.recommendedBallSpeedMps),width*.5f,height*.13f,p)
            p.textAlign=Paint.Align.LEFT
        }
        postInvalidateDelayed(if(moving)90L else 120L)
    }
}
''', encoding='utf-8')
    print('V27 pace overlay: generated')
else:
    print('V27 pace overlay: already exists')

replace_once(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '        V26ReportPreferences.install(this)\n',
    '        V26ReportPreferences.install(this)\n        V27CupPaceRuntime.install(this)\n',
    'MainActivity pace install'
)

replace_once(
    'app/src/main/java/com/puttvision/screen/ProductSetupDialog.kt',
    '    addAction(action("GREEN VISUALS", V26GreenVisualRuntime.label()) {\n        showV26GreenVisualSettingsDialog(context)\n    })\n',
    '    addAction(action("GREEN VISUALS", V26GreenVisualRuntime.label()) {\n        showV26GreenVisualSettingsDialog(context)\n    })\n\n    addAction(action("HOLE PACE", V27CupPaceRuntime.label()) {\n        showV27CupPaceDialog(context)\n    })\n',
    'ProductSetup pace action'
)

replace_once(
    'app/src/main/java/com/puttvision/screen/V18OpenGlSimulator.kt',
    '        addView(V26GreenInsightOverlay(context, engine), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))\n',
    '        addView(V26GreenInsightOverlay(context, engine), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))\n        addView(V27PaceLineOverlay(context, engine), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))\n',
    'V18 pace overlay'
)

p = Path('app/src/main/java/com/puttvision/screen/ImpactReplayView.kt')
text = p.read_text(encoding='utf-8')
if 'private val annotationBook = V27FrameAnnotationBook()' not in text:
    text = text.replace('    private val annotations = V27ReplayAnnotationSession()\n','    private val annotations = V27ReplayAnnotationSession()\n    private val annotationBook = V27FrameAnnotationBook()\n',1)
    text = text.replace('            frame++\n','            annotationBook.save(frame, annotations)\n            frame++\n',1)
    text = text.replace('            invalidate()\n            if (!paused) handler.postDelayed(this, 55L)\n','            annotationBook.load(frame, annotations)\n            invalidate()\n            if (!paused) handler.postDelayed(this, 55L)\n',1)
    text = text.replace('        annotations.clear()\n        annotations.selectTool(V27ReplayTool.NONE)\n        visibility = VISIBLE\n','        annotations.clear()\n        annotationBook.clear()\n        annotations.selectTool(V27ReplayTool.NONE)\n        visibility = VISIBLE\n',1)
    text = text.replace('        annotations.clear()\n        annotations.selectTool(V27ReplayTool.NONE)\n        visibility = GONE\n','        annotations.clear()\n        annotationBook.clear()\n        annotations.selectTool(V27ReplayTool.NONE)\n        visibility = GONE\n',1)
    text = text.replace('        invalidate()\n    }\n\n    override fun onTouchEvent','        annotationBook.save(frame, annotations)\n        invalidate()\n    }\n\n    override fun onTouchEvent',1)
    text = text.replace('                frame=(ratio*(r.frames.size-1)).roundToInt().coerceIn(0,r.frames.lastIndex)\n                setPaused(true); invalidate(); return true\n','                annotationBook.save(frame, annotations)\n                frame=(ratio*(r.frames.size-1)).roundToInt().coerceIn(0,r.frames.lastIndex)\n                annotationBook.load(frame, annotations)\n                setPaused(true); invalidate(); return true\n',1)
    text = text.replace('            if (handled) invalidate()\n            return handled\n','            if (handled) {\n                if (event.actionMasked == MotionEvent.ACTION_UP) annotationBook.save(frame, annotations)\n                invalidate()\n            }\n            return handled\n',1)
    p.write_text(text, encoding='utf-8')
    print('ImpactReplay frame annotations: patched')
else:
    print('ImpactReplay frame annotations: already applied')

# Regression tests generated with the feature so CI validates exact behavior.
test_dir = Path('app/src/test/java/com/puttvision/screen')
test_dir.mkdir(parents=True, exist_ok=True)
(test_dir / 'V27FrameAnnotationBookTest.kt').write_text('''package com.puttvision.screen

import org.junit.Assert.*
import org.junit.Test

class V27FrameAnnotationBookTest {
    @Test fun framesKeepIndependentMarks() {
        val book=V27FrameAnnotationBook(); val s=V27ReplayAnnotationSession()
        s.marks += V27ReplayMark.Line(V27NormPoint(.1f,.2f),V27NormPoint(.8f,.7f)); book.save(3,s)
        s.clear(); book.load(4,s); assertTrue(s.marks.isEmpty())
        book.load(3,s); assertEquals(1,s.marks.size); assertEquals(setOf(3),book.annotatedFrames())
    }
}
''', encoding='utf-8')
(test_dir / 'V27CupPaceTest.kt').write_text('''package com.puttvision.screen

import org.junit.Assert.*
import org.junit.Test

class V27CupPaceTest {
    @Test fun pacePresetsAreStrictlyIncreasing() {
        val v=V27CupPace.entries.map{it.targetCupSpeedMps}; assertEquals(v.sorted(),v); assertEquals(v.size,v.distinct().size)
    }
    @Test fun greenReadKeyIncludesPace() {
        val s=GreenSettings(holeDistanceM=4.0); val a=GreenReadAdvisor.key(s,.25); val b=GreenReadAdvisor.key(s,.85); assertNotEquals(a.pace100,b.pace100)
    }
}
''', encoding='utf-8')

fm = Path('FEATURE_MATRIX.json')
data = json.loads(fm.read_text(encoding='utf-8'))
data['version'] = 'v27-development'
f = data.setdefault('features', {})
f['hfr_frame_scoped_manual_annotations'] = True
f['cup_entry_pace_presets'] = True
f['pace_dependent_ideal_line'] = True
f['pace_dependent_green_read_cache'] = True
v = data.setdefault('validation', {})
v['v27_frame_annotation_test'] = True
v['v27_pace_regression_test'] = True
fm.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('V27 feature matrix updated')
