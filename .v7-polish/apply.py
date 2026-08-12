from pathlib import Path

p = Path('app/src/main/java/com/puttvision/screen/MainActivity.kt')
s = p.read_text()
old = '''    tools.addView(tool("DEVELOPER", "ZIP 배포") {
        closeThen {
            if (wasActiveSession) showHomeMenu()
            startActivity(Intent(this, DeployActivity::class.java))
        }
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
'''
if old not in s:
    raise SystemExit('developer settings row not found')
s = s.replace(old, '', 1)
p.write_text(s)
