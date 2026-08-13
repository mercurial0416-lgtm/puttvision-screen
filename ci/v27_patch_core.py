from pathlib import Path
import json

def patch(path, old, new, label):
    p=Path(path); t=p.read_text(encoding='utf-8')
    if new in t:
        print(label+': current'); return
    if t.count(old)!=1:
        raise SystemExit(label+' marker')
    p.write_text(t.replace(old,new,1),encoding='utf-8'); print(label+': patched')

patch('app/src/main/java/com/puttvision/screen/GreenPhysics.kt',
'fun step(state: SimState, settings: GreenSettings, dtRaw: Double): SimResult? {',
'''fun step(
        state: SimState,
        settings: GreenSettings,
        dtRaw: Double,
        cupEnabled: Boolean = true
    ): SimResult? {''','physics signature')
patch('app/src/main/java/com/puttvision/screen/GreenPhysics.kt',
'if (closestDist <= CAPTURE_CENTER_RADIUS_M && nowSpeed <= captureSpeed) {',
'if (cupEnabled && closestDist <= CAPTURE_CENTER_RADIUS_M && nowSpeed <= captureSpeed) {','physics capture')
patch('app/src/main/java/com/puttvision/screen/GreenPhysics.kt',
'''        if (
            closestDist <= RIM_CONTACT_RADIUS_M &&''',
'''        if (
            cupEnabled &&
            closestDist <= RIM_CONTACT_RADIUS_M &&''','physics rim')
patch('app/src/main/java/com/puttvision/screen/V13Reliability.kt',
'''        key.profile, key.distance100, key.stimp100, key.side100, key.long100, key.putter100,
        key.startX100, key.startY100
    ).joinToString(":")''',
'''        key.profile, key.distance100, key.stimp100, key.side100, key.long100, key.putter100,
        key.startX100, key.startY100, key.pace100
    ).joinToString(":")''','persistent pace key')

p=Path('app/src/main/java/com/puttvision/screen/V26SettingsDialogs.kt'); t=p.read_text(encoding='utf-8')
if '홀 통과 페이스' not in t:
    t=t.replace('''    val labels = modes.map { mode ->
        val mark = if (mode == V26GreenVisualRuntime.mode) "✓" else "  "
        "$mark ${mode.label}"
    } + listOf("${if (V26GreenVisualRuntime.swingGuide) "✓" else "  "} 추천 속도 스윙가이드")''','''    V27CupPaceRuntime.install(context)
    val labels = modes.map { mode ->
        val mark = if (mode == V26GreenVisualRuntime.mode) "✓" else "  "
        "$mark ${mode.label}"
    } + listOf(
        "${if (V26GreenVisualRuntime.swingGuide) "✓" else "  "} 추천 속도 스윙가이드",
        "홀 통과 페이스 · ${V27CupPaceRuntime.label()}"
    )''')
    t=t.replace('''            if (which < modes.size) V26GreenVisualRuntime.setMode(context, modes[which])
            else V26GreenVisualRuntime.setSwingGuide(context, !V26GreenVisualRuntime.swingGuide)
            dialog.dismiss()''','''            when {
                which < modes.size -> V26GreenVisualRuntime.setMode(context, modes[which])
                which == modes.size -> V26GreenVisualRuntime.setSwingGuide(context, !V26GreenVisualRuntime.swingGuide)
                else -> { dialog.dismiss(); showV27CupPaceDialog(context); return@setItems }
            }
            dialog.dismiss()''')
    p.write_text(t,encoding='utf-8'); print('green settings: patched')
else: print('green settings: current')

p=Path('FEATURE_MATRIX.json'); d=json.loads(p.read_text(encoding='utf-8')); d['version']='v27-development'
f=d.setdefault('features',{}); f.update({'selectable_cup_entry_pace':True,'pace_changes_green_read_line_and_speed':True,'hfr_manual_line_circle_angle_annotations':True,'hfr_manual_frame_scrub':True,'hfr_annotation_undo_clear':True})
v=d.setdefault('validation',{}); v.update({'v27_pace_solver_regression_test':True,'v27_replay_geometry_test':True})
p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8'); print('matrix: v27')
