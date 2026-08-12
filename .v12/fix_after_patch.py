from pathlib import Path

ROOT = Path('.')


def read(path):
    return (ROOT / path).read_text(encoding='utf-8')


def write(path, text):
    (ROOT / path).write_text(text, encoding='utf-8')


def replace_once(path, old, new):
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{path}: expected 1 anchor, found {count}: {old[:120]!r}')
    write(path, text.replace(old, new, 1))


# Remaining green-picker read becomes async and updates itself when the solver is ready.
path = 'app/src/main/java/com/puttvision/screen/MainActivity.kt'
text = read(path)
text = text.replace(
    '        val selectedRead = GreenReadAdvisor.read(selectedReadSettings)\n',
    '        val selectedRead = GreenReadRuntime.peekOrSchedule(selectedReadSettings)\n',
    1
)
old_card = r'''            addView(TextView(this@MainActivity).apply {
                val aim = if (selectedRead.aimSideLabel == "센터") {
                    "추천 에임 · 센터"
                } else {
                    "추천 에임 · ${selectedRead.aimSideLabel}  ${"%.1f".format(selectedRead.cupCount)}컵  /  ${"%.1f".format(selectedRead.putterHeadCount)}헤드"
                }
                text = "$aim\n${selectedRead.paceHint}"
                setTextColor(Pv.textMid)
                textSize = scaledSp(if (compact) 6.5f else 7.6f)
                typeface = Typeface.MONOSPACE
                includeFontPadding = false
                setPadding(0, sdp(7), 0, 0)
            })'''
new_card = r'''            addView(TextView(this@MainActivity).apply {
                fun render(read: GreenRead?) {
                    text = when {
                        read == null -> "추천 에임 계산중 · 물리 솔버 준비"
                        !read.solverReliable -> "추천선 보류 · SOLVER ±${"%.1f".format(read.solverMissCm)}cm"
                        read.aimSideLabel == "센터" -> "추천 에임 · 센터\n${read.paceHint} · SOLVER ±${"%.1f".format(read.solverMissCm)}cm"
                        else -> "추천 에임 · ${read.aimSideLabel}  ${"%.1f".format(read.cupCount)}컵  /  ${"%.1f".format(read.putterHeadCount)}헤드\n${read.paceHint} · SOLVER ±${"%.1f".format(read.solverMissCm)}cm"
                    }
                }
                render(selectedRead)
                if (selectedRead == null) {
                    GreenReadRuntime.request(selectedReadSettings) { solved ->
                        runOnUiThread { if (isAttachedToWindow) render(solved) }
                    }
                }
                setTextColor(Pv.textMid)
                textSize = scaledSp(if (compact) 6.5f else 7.6f)
                typeface = Typeface.MONOSPACE
                includeFontPadding = false
                setPadding(0, sdp(7), 0, 0)
            })'''
if old_card not in text:
    raise RuntimeError('green picker selected-read card anchor missing')
text = text.replace(old_card, new_card, 1)

old_voice = '''        if (::voiceCoach.isInitialized) {
            voiceCoach.speakReady(GreenReadAdvisor.read(engine.settings))
        }'''
new_voice = '''        if (::voiceCoach.isInitialized) {
            val cachedRead = GreenReadRuntime.peekOrSchedule(engine.settings)
            voiceCoach.speakReady(cachedRead?.takeIf { it.solverReliable })
        }'''
if old_voice in text:
    text = text.replace(old_voice, new_voice, 1)

if 'GreenReadAdvisor.read(' in text:
    lines = [f'{i+1}: {line.strip()}' for i, line in enumerate(text.splitlines()) if 'GreenReadAdvisor.read(' in line]
    raise RuntimeError('blocking GreenReadAdvisor.read remains in MainActivity: ' + ' | '.join(lines))
write(path, text)


# A cache miss must never be announced as "center". Generic READY is accurate until
# the background solver returns a trustworthy recommendation.
path = 'app/src/main/java/com/puttvision/screen/ProductSystems.kt'
old = '''        val guide = when {
            read == null || read.aimSideLabel == "센터" -> "준비 완료. 에임은 센터입니다."
            else -> "준비 완료. ${read.aimSideLabel} ${"%.1f".format(read.cupCount)}컵을 보세요."
        }'''
new = '''        val guide = when {
            read == null -> "준비 완료."
            !read.solverReliable -> "준비 완료. 추천 에임을 계산 중입니다."
            read.aimSideLabel == "센터" -> "준비 완료. 에임은 센터입니다."
            else -> "준비 완료. ${read.aimSideLabel} ${"%.1f".format(read.cupCount)}컵을 보세요."
        }'''
replace_once(path, old, new)

print('V12 async fix applied')
