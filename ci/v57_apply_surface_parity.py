from pathlib import Path

main_path = Path('app/src/main/java/com/puttvision/screen/MainActivity.kt')
ext_path = Path('app/src/main/java/com/puttvision/screen/ExternalDisplayController.kt')

main = main_path.read_text(encoding='utf-8')

preview_old = '''        val tv = V18SimulatorFactory.create(this, engine)
        root.addView(tv, FrameLayout.LayoutParams(-1, -1))
        root.addView(TvImpactReplayView(this), FrameLayout.LayoutParams(-1, -1))'''
preview_new = '''        root.addView(V57ProductTvSurface.create(this, engine), FrameLayout.LayoutParams(-1, -1))'''
if main.count(preview_old) != 1:
    raise SystemExit(f'expected one TV preview composition, found {main.count(preview_old)}')
main = main.replace(preview_old, preview_new, 1)

lab_old = '''        root.addView(V18SimulatorFactory.create(this, engine), FrameLayout.LayoutParams(-1, -1))
        root.addView(TvImpactReplayView(this), FrameLayout.LayoutParams(-1, -1))'''
lab_new = '''        root.addView(V57ProductTvSurface.create(this, engine), FrameLayout.LayoutParams(-1, -1))'''
if main.count(lab_old) != 1:
    raise SystemExit(f'expected one hardwareless lab composition, found {main.count(lab_old)}')
main = main.replace(lab_old, lab_new, 1)

old_copy = 'CAMERA MOCK ●  ·  TV V17 LOCAL ●\\n실제 측정 파이프라인에 합성 샷을 주입합니다.'
new_copy = 'SIM CAMERA ●  ·  PRODUCT TV PARITY ●\\n실제 TV와 동일한 화면 레이어에 합성 샷을 주입합니다.'
if main.count(old_copy) != 1:
    raise SystemExit(f'expected one hardwareless helper copy, found {main.count(old_copy)}')
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

print('V57 surface parity patch applied: preview + hardwareless lab + external TV')
