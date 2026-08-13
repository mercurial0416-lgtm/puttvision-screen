from pathlib import Path

p = Path('app/src/main/java/com/puttvision/screen/V13Hardwareless.kt')
s = p.read_text(encoding='utf-8')
old = 'val ballForward = if (i < impact) 0f else (metrics.ballSpeedMps * after * 380f)'
new = 'val ballForward = if (i < impact) 0f else (metrics.ballSpeedMps * after.toDouble() * 380.0).toFloat()'
if old not in s:
    raise RuntimeError('missing ballForward anchor')
p.write_text(s.replace(old, new, 1), encoding='utf-8')
print('V13 core numeric fix applied')
