from pathlib import Path
p=Path('app/src/main/java/com/puttvision/screen/ImpactReplayView.kt')
t=p.read_text(encoding='utf-8')
old='''                if (loops >= 2) {
                    stopReplay(recycleFrames = true)
                    return
                }'''
new='''                if (loops >= 2) {
                    frame = r.impactIndex.coerceIn(0, r.frames.lastIndex)
                    paused = true
                    invalidate()
                    return
                }'''
if new in t:
    print('V27 replay hold: current')
elif t.count(old)==1:
    p.write_text(t.replace(old,new,1),encoding='utf-8'); print('V27 replay hold: patched')
else:
    raise SystemExit('V27 replay hold marker')
