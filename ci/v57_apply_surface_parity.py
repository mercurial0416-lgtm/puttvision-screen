from pathlib import Path
import re

main_path = Path('app/src/main/java/com/puttvision/screen/MainActivity.kt')
ext_path = Path('app/src/main/java/com/puttvision/screen/ExternalDisplayController.kt')

main = main_path.read_text(encoding='utf-8')

pair = re.compile(
    r'(?m)^(?P<i>\s*)root\.addView\(V18SimulatorFactory\.create\(this, engine\), FrameLayout\.LayoutParams\(-1, -1\)\)\n'
    r'(?P=i)root\.addView\(TvImpactReplayView\(this\), FrameLayout\.LayoutParams\(-1, -1\)\)'
)

def pair_repl(match: re.Match[str]) -> str:
    i = match.group('i')
    return f'{i}root.addView(V57ProductTvSurface.create(this, engine), FrameLayout.LayoutParams(-1, -1))'

main, count = pair.subn(pair_repl, main)
if count != 2:
    raise SystemExit(f'expected exactly 2 MainActivity TV surface pairs, found {count}')

old_copy = 'CAMERA MOCK ●  ·  TV V17 LOCAL ●\\n실제 측정 파이프라인에 합성 샷을 주입합니다.'
new_copy = 'SIM CAMERA ●  ·  PRODUCT TV PARITY ●\\n실제 TV와 동일한 화면 레이어에 합성 샷을 주입합니다.'
if old_copy not in main:
    raise SystemExit('hardwareless lab helper copy not found')
main = main.replace(old_copy, new_copy, 1)
main_path.write_text(main, encoding='utf-8')

external = ext_path.read_text(encoding='utf-8')
old_block = '''        root.addView(V18SimulatorFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))
        root.addView(V51TvPolishOverlay(context, engine), FrameLayout.LayoutParams(-1, -1))
        root.addView(V27PaceLineOverlay(context, engine), FrameLayout.LayoutParams(-1, -1))
        root.addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        root.addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))'''
new_block = '''        root.addView(V57ProductTvSurface.create(context, engine), FrameLayout.LayoutParams(-1, -1))'''
if external.count(old_block) != 1:
    raise SystemExit(f'expected one external TV composition block, found {external.count(old_block)}')
external = external.replace(old_block, new_block, 1)
ext_path.write_text(external, encoding='utf-8')

print('V57 surface parity patch applied: 2 MainActivity surfaces + 1 external TV surface')
