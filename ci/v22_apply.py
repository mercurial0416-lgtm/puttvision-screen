from pathlib import Path

main = Path("app/src/main/java/com/puttvision/screen/MainActivity.kt")
text = main.read_text(encoding="utf-8")
changed = False

if "V22CustomGreenRuntime.install(this)" not in text:
    marker = "        V20ProductPreferences.install(this)\n"
    if marker not in text:
        raise SystemExit("V22 runtime install marker missing")
    text = text.replace(
        marker,
        marker + "        V22CustomGreenRuntime.install(this)\n        V22AudioRuntime.install(this)\n",
        1,
    )
    changed = True
    print("V22 custom green/audio runtime install wired")

legacy = '''        actions.addView(pvButton("같은 조건 다시", PvButtonStyle.GHOST, textSp = if (compact) 7.8f else 9f, radiusDp = Pv.rLg) {
            sessionActive = false
            measurementSuspended = true
            if (activeSessionIsGame) showGameEntrance() else showPracticeEntrance()
        }, LinearLayout.LayoutParams(0, sdp(if (compact) 40 else 46), 1f).apply { marginEnd = sdp(5) })
        actions.addView(pvButton("추천 훈련 시작", PvButtonStyle.PRIMARY, textSp = if (compact) 8f else 9.2f, radiusDp = Pv.rLg) {
            applyAutoCoachPlan(report.plan)
        }, LinearLayout.LayoutParams(0, sdp(if (compact) 40 else 46), 1f).apply { marginStart = sdp(5) })'''

v22 = '''        actions.addView(pvButton("같은 조건 다시", PvButtonStyle.GHOST, textSp = if (compact) 7.2f else 8.3f, radiusDp = Pv.rLg) {
            sessionActive = false
            measurementSuspended = true
            if (activeSessionIsGame) showGameEntrance() else showPracticeEntrance()
        }, LinearLayout.LayoutParams(0, sdp(if (compact) 40 else 46), 1f).apply { marginEnd = sdp(4) })
        actions.addView(pvButton("PDF · CSV 공유", PvButtonStyle.GHOST, textSp = if (compact) 7.2f else 8.3f, radiusDp = Pv.rLg) {
            V22ReportExporter.share(this, statsRepository.recent(120))
        }, LinearLayout.LayoutParams(0, sdp(if (compact) 40 else 46), 1f).apply { marginStart = sdp(4); marginEnd = sdp(4) })
        actions.addView(pvButton("추천 훈련 시작", PvButtonStyle.PRIMARY, textSp = if (compact) 7.2f else 8.3f, radiusDp = Pv.rLg) {
            applyAutoCoachPlan(report.plan)
        }, LinearLayout.LayoutParams(0, sdp(if (compact) 40 else 46), 1f).apply { marginStart = sdp(4) })'''

if v22 not in text:
    count = text.count(legacy)
    if count != 1:
        raise SystemExit(f"V22 report action: expected 1 legacy block, got {count}")
    text = text.replace(legacy, v22, 1)
    changed = True
    print("V22 report share action wired")

if changed:
    main.write_text(text, encoding="utf-8")
else:
    print("V22 MainActivity already current")
