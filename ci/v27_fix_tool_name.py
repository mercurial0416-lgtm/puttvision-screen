from pathlib import Path

ann=Path('app/src/main/java/com/puttvision/screen/V27ReplayAnnotations.kt')
t=ann.read_text(encoding='utf-8')
if 'fun setTool(' in t:
    t=t.replace('fun setTool(value: V27ReplayTool)', 'fun selectTool(value: V27ReplayTool)', 1)
    ann.write_text(t,encoding='utf-8'); print('V27 annotations: renamed selectTool')
else: print('V27 annotations: current')

view=Path('app/src/main/java/com/puttvision/screen/ImpactReplayView.kt')
t=view.read_text(encoding='utf-8')
if 'annotations.setTool(' in t:
    t=t.replace('annotations.setTool(', 'annotations.selectTool(')
    view.write_text(t,encoding='utf-8'); print('V27 replay: renamed selectTool calls')
else: print('V27 replay: current')
