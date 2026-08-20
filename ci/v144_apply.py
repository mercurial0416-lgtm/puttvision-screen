#!/usr/bin/env python3
from pathlib import Path

TARGET = Path("app/src/main/java/com/puttvision/screen/MainActivity.kt")
START = "    private fun showHardwarelessTestLab() {"
END = "    private fun openAccuracyValidationLab() {"
REPLACEMENT = '''    private fun showHardwarelessTestLab() {
        if (sessionActive) {
            toast("실제 세션을 끝낸 뒤 장비 없이 테스트를 실행하세요")
            return
        }
        startActivity(Intent(this, V144HardwarelessGodotActivity::class.java))
    }

'''

text = TARGET.read_text(encoding="utf-8")
if "V144HardwarelessGodotActivity::class.java" in text:
    print("V144 SIM LAB launcher already applied")
    raise SystemExit(0)

start = text.find(START)
if start < 0:
    raise SystemExit("V144 patch failed: showHardwarelessTestLab start marker missing")
end = text.find(END, start)
if end < 0:
    raise SystemExit("V144 patch failed: openAccuracyValidationLab end marker missing")
if text.find(START, start + 1) >= 0:
    raise SystemExit("V144 patch failed: duplicate showHardwarelessTestLab marker")

old = text[start:end]
required = [
    "V57ProductTvSurface.create(this, engine)",
    "HardwarelessScenario.entries",
    "자동 시나리오 전체검사",
    "24 GREEN 엔진 스모크",
]
missing = [needle for needle in required if needle not in old]
if missing:
    raise SystemExit(f"V144 patch refused: old lab shape changed; missing={missing}")

patched = text[:start] + REPLACEMENT + text[end:]
if patched.count("V144HardwarelessGodotActivity::class.java") != 1:
    raise SystemExit("V144 patch failed: launcher count is not exactly one")
if "private fun showHardwarelessTestLab()" not in patched:
    raise SystemExit("V144 patch failed: launcher function disappeared")

TARGET.write_text(patched, encoding="utf-8")
print(f"V144 SIM LAB launcher applied; removed {len(old.splitlines())} legacy lab lines")
