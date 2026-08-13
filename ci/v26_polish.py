from pathlib import Path

# Product setup uses device-safe settings dialog.
p=Path('app/src/main/java/com/puttvision/screen/ProductSetupDialog.kt');text=p.read_text(encoding='utf-8');text2=text.replace('showV26GreenVisualDialog(context)','showV26GreenVisualSettingsDialog(context)');
if text2!=text:p.write_text(text2,encoding='utf-8');print('V26 green visual dialog polished')

# TV distance readouts use metres with two decimals everywhere.
for filename in ['app/src/main/java/com/puttvision/screen/V18OpenGlSimulator.kt','app/src/main/java/com/puttvision/screen/V17SimulatorTvView.kt']:
    p=Path(filename);text=p.read_text(encoding='utf-8');before=text
    text=text.replace('${"%.1f".format(engine.settings.holeDistanceM)} m','${"%.2f".format(engine.settings.holeDistanceM)} m')
    text=text.replace('${"%.1f".format(settings.holeDistanceM)}m','${"%.2f".format(settings.holeDistanceM)} m')
    if text!=before:p.write_text(text,encoding='utf-8');print('V26 metre precision polished:',filename)

# Multi-choice report dialog keeps its list visible; explanation lives in title/card text instead of setMessage.
p=Path('app/src/main/java/com/puttvision/screen/V26ReportBuilder.kt');text=p.read_text(encoding='utf-8');before=text
text=text.replace('.setMessage("다음 PDF 공유부터 선택한 섹션 구성으로 생성합니다. CSV는 전체 원자료를 유지합니다.")','')
if text!=before:p.write_text(text,encoding='utf-8');print('V26 report builder dialog polished')
