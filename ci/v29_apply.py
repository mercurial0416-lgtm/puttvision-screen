from pathlib import Path
import json

def patch(path, old, new, label):
    p=Path(path); text=p.read_text(encoding='utf-8')
    if new in text: return
    if old not in text: raise SystemExit(label+': marker missing')
    p.write_text(text.replace(old,new,1),encoding='utf-8')

patch('app/src/main/java/com/puttvision/screen/V18OpenGlSimulator.kt','        addView(V26GreenInsightOverlay(context, engine), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))\n','        addView(V26GreenInsightOverlay(context, engine), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))\n        addView(V27PaceLineOverlay(context, engine), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))\n','V18')
p=Path('app/src/main/java/com/puttvision/screen/ImpactReplayView.kt'); t=p.read_text(encoding='utf-8')
if 'private val annotationBook = V27FrameAnnotationBook()' not in t:
    t=t.replace('    private val annotations = V27ReplayAnnotationSession()\n','    private val annotations = V27ReplayAnnotationSession()\n    private val annotationBook = V27FrameAnnotationBook()\n',1)
    t=t.replace('            frame++\n','            annotationBook.save(frame, annotations)\n            frame++\n',1)
    t=t.replace('            invalidate()\n            if (!paused) handler.postDelayed(this, 55L)\n','            annotationBook.load(frame, annotations)\n            invalidate()\n            if (!paused) handler.postDelayed(this, 55L)\n',1)
    t=t.replace('        annotations.clear()\n        annotations.selectTool(V27ReplayTool.NONE)\n        visibility = VISIBLE\n','        annotations.clear()\n        annotationBook.clear()\n        annotations.selectTool(V27ReplayTool.NONE)\n        visibility = VISIBLE\n',1)
    t=t.replace('        annotations.clear()\n        annotations.selectTool(V27ReplayTool.NONE)\n        visibility = GONE\n','        annotations.clear()\n        annotationBook.clear()\n        annotations.selectTool(V27ReplayTool.NONE)\n        visibility = GONE\n',1)
    t=t.replace('        invalidate()\n    }\n\n    override fun onTouchEvent','        annotationBook.save(frame, annotations)\n        invalidate()\n    }\n\n    override fun onTouchEvent',1)
    t=t.replace('                frame=(ratio*(r.frames.size-1)).roundToInt().coerceIn(0,r.frames.lastIndex)\n                setPaused(true); invalidate(); return true\n','                annotationBook.save(frame, annotations)\n                frame=(ratio*(r.frames.size-1)).roundToInt().coerceIn(0,r.frames.lastIndex)\n                annotationBook.load(frame, annotations)\n                setPaused(true); invalidate(); return true\n',1)
    t=t.replace('            if (handled) invalidate()\n            return handled\n','            if (handled) {\n                if (event.actionMasked == MotionEvent.ACTION_UP) annotationBook.save(frame, annotations)\n                invalidate()\n            }\n            return handled\n',1)
    p.write_text(t,encoding='utf-8')
fm=Path('FEATURE_MATRIX.json'); d=json.loads(fm.read_text(encoding='utf-8')); d['version']='v29-development'; f=d.setdefault('features',{}); f['pace_dependent_tv_ideal_trail']=True; f['hfr_frame_scoped_manual_annotations']=True; d.setdefault('validation',{})['v29_frame_annotation_test']=True; fm.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
