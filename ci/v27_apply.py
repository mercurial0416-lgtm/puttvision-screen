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
    text = text.replace(
        '    private val annotations = V27ReplayAnnotationSession()\n',
        '    private val annotations = V27ReplayAnnotationSession()\n    private val annotationBook = V27FrameAnnotationBook()\n', 1)
    text = text.replace(
        '            frame++\n',
        '            annotationBook.save(frame, annotations)\n            frame++\n', 1)
    text = text.replace(
        '            invalidate()\n            if (!paused) handler.postDelayed(this, 55L)\n',
        '            annotationBook.load(frame, annotations)\n            invalidate()\n            if (!paused) handler.postDelayed(this, 55L)\n', 1)
    text = text.replace(
        '        annotations.clear()\n        annotations.selectTool(V27ReplayTool.NONE)\n        visibility = VISIBLE\n',
        '        annotations.clear()\n        annotationBook.clear()\n        annotations.selectTool(V27ReplayTool.NONE)\n        visibility = VISIBLE\n', 1)
    text = text.replace(
        '        annotations.clear()\n        annotations.selectTool(V27ReplayTool.NONE)\n        visibility = GONE\n',
        '        annotations.clear()\n        annotationBook.clear()\n        annotations.selectTool(V27ReplayTool.NONE)\n        visibility = GONE\n', 1)
    text = text.replace(
        '        invalidate()\n    }\n\n    override fun onTouchEvent',
        '        annotationBook.save(frame, annotations)\n        invalidate()\n    }\n\n    override fun onTouchEvent', 1)
    text = text.replace(
        '                frame=(ratio*(r.frames.size-1)).roundToInt().coerceIn(0,r.frames.lastIndex)\n                setPaused(true); invalidate(); return true\n',
        '                annotationBook.save(frame, annotations)\n                frame=(ratio*(r.frames.size-1)).roundToInt().coerceIn(0,r.frames.lastIndex)\n                annotationBook.load(frame, annotations)\n                setPaused(true); invalidate(); return true\n', 1)
    text = text.replace(
        '            if (handled) invalidate()\n            return handled\n',
        '            if (handled) {\n                if (event.actionMasked == MotionEvent.ACTION_UP) annotationBook.save(frame, annotations)\n                invalidate()\n            }\n            return handled\n', 1)
    p.write_text(text, encoding='utf-8')
    print('ImpactReplay frame annotations: patched')
else:
    print('ImpactReplay frame annotations: already applied')

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
