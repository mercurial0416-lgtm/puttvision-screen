from pathlib import Path

p = Path('app/src/main/java/com/puttvision/screen/GreenReadAdvisor.kt')
text = p.read_text(encoding='utf-8')
old = '''        val state = physics.launch(shot(speed, angle), settings)\n        var result: SimResult? = null\n        repeat(900) {\n            result = physics.step(state, settings, .025)\n            if (result != null) return@repeat\n        }\n        val final = result ?: SimResult(\n'''
new = '''        val state = physics.launch(shot(speed, angle), settings)\n        var result: SimResult? = null\n        for (step in 0 until 900) {\n            val completed = physics.step(state, settings, .025)\n            if (completed != null) {\n                result = completed\n                break\n            }\n        }\n        val final = result ?: SimResult(\n'''
if text.count(old) != 1:
    raise RuntimeError(f'expected one solver loop, found {text.count(old)}')
p.write_text(text.replace(old, new, 1), encoding='utf-8')
print('V11 solver early termination applied')
