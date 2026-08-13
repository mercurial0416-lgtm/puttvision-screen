from pathlib import Path

p=Path('app/src/main/java/com/puttvision/screen/MainActivity.kt'); t=p.read_text(encoding='utf-8')
if 'V27CupPaceRuntime.install(this)' not in t:
    old='        V26ReportPreferences.install(this)\n        voiceCoach = HandsFreeVoiceCoach(this)'
    if t.count(old)!=1: raise SystemExit('MainActivity V27 install marker')
    t=t.replace(old,'        V26ReportPreferences.install(this)\n        V27CupPaceRuntime.install(this)\n        voiceCoach = HandsFreeVoiceCoach(this)',1)
    p.write_text(t,encoding='utf-8'); print('MainActivity: V27 installed')
else: print('MainActivity: current')

p=Path('app/src/main/java/com/puttvision/screen/V26GreenVisuals.kt'); t=p.read_text(encoding='utf-8')
if '컵 %.2f m/s' not in t:
    t=t.replace('val bw=width*.105f','val bw=width*.152f',1)
    old='c.drawText("권장 %.2f m/s".format(read.recommendedBallSpeedMps),left+bw*.5f,top+bh*.66f,p)'
    if t.count(old)!=1: raise SystemExit('V26 pace label marker')
    t=t.replace(old,'c.drawText("권장 %.2f m/s · 컵 %.2f m/s".format(read.recommendedBallSpeedMps,V27CupPaceRuntime.targetCupSpeedMps),left+bw*.5f,top+bh*.66f,p)',1)
    p.write_text(t,encoding='utf-8'); print('V26 TV pace label: patched')
else: print('V26 TV pace label: current')
