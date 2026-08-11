from pathlib import Path

p = Path('app/src/main/java/com/puttvision/screen/MainActivity.kt')
s = p.read_text()

def one(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'missing {label}')
    s = s.replace(old, new, 1)

one(
'''    private fun handleMeasuredShot(
        metrics: ShotMetrics,
        replay: ImpactReplay?,
        source: String
    ) {''',
'''    private fun handleMeasuredShot(
        metrics: ShotMetrics,
        replay: ImpactReplay?,
        source: String
    ): Boolean {''',
'handle return type')

one(
'''        if (!sessionActive || measurementSuspended) {
            replay?.frames?.forEach { if (!it.isRecycled) it.recycle() }
            return
        }

        val confidence = metrics.confidence''',
'''        if (!sessionActive || measurementSuspended) {
            replay?.frames?.forEach { if (!it.isRecycled) it.recycle() }
            return false
        }

        val confidence = metrics.confidence''',
'handle inactive return')

one(
'''            scheduleAutoRetry(850L)
            return
        }

        confidence?.let''',
'''            scheduleAutoRetry(850L)
            return false
        }

        confidence?.let''',
'handle low quality return')

one(
'''        overlay.invalidate()
    }

    private fun startSimulationTicker() {''',
'''        overlay.invalidate()
        return true
    }

    private fun startSimulationTicker() {''',
'handle success return')

old = '''                    handleMeasuredShot(
                        metrics =
                            result.metrics,
                        replay = replay,
                        source =
                            "PRECISION ${result.fps}fps"
                    )

                    setHfrStatus(
                        "✓ ${result.fps}fps",
                        "✓ ${result.fps}fps · ${result.analyzedFrames} frames · F${result.impactFrame}"
                    )'''
new = '''                    val accepted = handleMeasuredShot(
                        metrics =
                            result.metrics,
                        replay = replay,
                        source =
                            "PRECISION ${result.fps}fps"
                    )

                    if (accepted) {
                        setHfrStatus(
                            "✓ ${result.fps}fps",
                            "✓ ${result.fps}fps · ${result.analyzedFrames} frames · F${result.impactFrame}"
                        )
                    }'''
one(old, new, 'HFR accepted status')

one(
'''    env.addView(sliderRow(speed, seek(20, ((engine.settings.stimpMeters - 2.0) * 10).toInt().coerceIn(0, 20)) {
        engine.settings.stimpMeters = 2.0 + it / 10.0''',
'''    env.addView(sliderRow(speed, seek(12, ((engine.settings.stimpMeters - 2.4) * 10).toInt().coerceIn(0, 12)) {
        engine.settings.stimpMeters = 2.4 + it / 10.0''',
'settings green speed range')

one(
'''            sessionActive = false
            measurementSuspended = true
            showPracticeEntrance()
        }, LinearLayout.LayoutParams(0, sdp(if (compact) 40 else 46), 1f).apply { marginEnd = sdp(5) })''',
'''            sessionActive = false
            measurementSuspended = true
            if (activeSessionIsGame) showGameEntrance() else showPracticeEntrance()
        }, LinearLayout.LayoutParams(0, sdp(if (compact) 40 else 46), 1f).apply { marginEnd = sdp(5) })''',
'repeat same session target')

p.write_text(s)
