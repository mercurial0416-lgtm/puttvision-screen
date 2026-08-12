from pathlib import Path

ROOT = Path('.')


def patch(path, old, new):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise RuntimeError(f'missing V10 fixup anchor: {path}: {old[:100]!r}')
    if text.count(old) != 1:
        raise RuntimeError(f'non unique V10 fixup anchor: {path}: {text.count(old)}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')

# Test typo in staged generator output.
path = ROOT / 'app/src/test/java/com/puttvision/screen/GameModeRecoveryRegressionTest.kt'
text = path.read_text(encoding='utf-8').replace('snap.playerScores', 'snap.scores')
path.write_text(text, encoding='utf-8')

# HFR bind failure needs a cooldown; otherwise recalibration immediately re-enters
# the same failing HFR bind and loops forever.
patch(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '    private var hfrRecordingGeneration = 0\n',
    '    private var hfrRecordingGeneration = 0\n    private var hfrRetryAfterMs = 0L\n'
)

patch(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''        if (!hfrHardwareAvailable) {\n            tracker.arm()\n\n            overlay.status =\n                "NORMAL AUTO READY · HFR 미지원"\n\n            metricText.text =\n                "HFR 미지원이라 일반 추적. 공 놓고 그냥 치면 됨."\n\n            overlay.invalidate()\n\n            return\n        }\n''',
    '''        val hfrCoolingDown = System.currentTimeMillis() < hfrRetryAfterMs\n        if (!hfrHardwareAvailable || hfrCoolingDown) {\n            tracker.arm()\n\n            overlay.status = if (hfrCoolingDown)\n                "NORMAL AUTO READY · HFR 재시도 대기"\n            else\n                "NORMAL AUTO READY · HFR 미지원"\n\n            metricText.text = if (hfrCoolingDown)\n                "HFR 파이프라인 오류 보호 · 일반 추적으로 계속 측정합니다"\n            else\n                "HFR 미지원이라 일반 추적. 공 놓고 그냥 치면 됨."\n\n            overlay.invalidate()\n            return\n        }\n'''
)

patch(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''        val thermal = thermalHfrPolicy.current()\n        if (thermal.maxFps < 120) {\n            hfrController?.close()\n            hfrController = null\n            tracker.arm()\n            setHfrStatus("THERMAL SAFE", thermal.detail)\n            overlay.status = "THERMAL SAFE · NORMAL AUTO"\n            metricText.text = thermal.detail\n            overlay.invalidate()\n            return\n        }\n\n        overlay.status =\n            "${thermal.maxFps}fps PRECISION 준비중"\n\n        metricText.text =\n            "${thermal.label} · ${thermal.maxFps}fps 상한 · 자동 녹화 준비"\n''',
    '''        val thermal = thermalHfrPolicy.current()\n        if (thermal.maxFps < 120) {\n            val switchingFromHfr = hfrController != null\n            hfrController?.close()\n            hfrController = null\n            setHfrStatus("THERMAL SAFE", thermal.detail)\n            if (switchingFromHfr) {\n                overlay.status = "THERMAL SAFE · NORMAL 전환중"\n                metricText.text = "${thermal.detail} · 카메라 파이프라인 전환중"\n                overlay.invalidate()\n                beginAutoCalibration()\n            } else {\n                tracker.arm()\n                overlay.status = "THERMAL SAFE · NORMAL AUTO"\n                metricText.text = thermal.detail\n                overlay.invalidate()\n            }\n            return\n        }\n\n        val desiredHfrCap = min(\n            thermal.maxFps,\n            deviceReport.maxHfrFps.takeIf { it >= 120 } ?: thermal.maxFps\n        ).coerceIn(120, 240)\n\n        overlay.status =\n            "${desiredHfrCap}fps PRECISION 준비중"\n\n        metricText.text =\n            "${thermal.label} · ${desiredHfrCap}fps 목표 · 자동 녹화 준비"\n'''
)

patch(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''            existing != null &&\n            existing.fps() >= 120 &&\n            existing.fps() <= thermal.maxFps\n''',
    '''            existing != null &&\n            existing.fps() == desiredHfrCap\n'''
)

patch(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '            controller.bindBest(maxFps = thermal.maxFps)\n',
    '            controller.bindBest(maxFps = desiredHfrCap)\n'
)

patch(
    'app/src/main/java/com/puttvision/screen/MainActivity.kt',
    '''        if (session == null) {\n            setHfrStatus("HFR fallback", "${thermal.label} · 현재 조건에서 HFR 바인딩 실패 · NORMAL fallback")\n            beginAutoCalibration()\n            return\n        }\n\n        setHfrStatus("HFR ${session.fps}fps", "PRECISION ${session.fps}fps 준비")\n''',
    '''        if (session == null) {\n            hfrRetryAfterMs = System.currentTimeMillis() + 60_000L\n            setHfrStatus("HFR fallback", "${thermal.label} · HFR 바인딩 실패 · 60초 NORMAL 보호")\n            beginAutoCalibration()\n            return\n        }\n\n        hfrRetryAfterMs = 0L\n        setHfrStatus("HFR ${session.fps}fps", "PRECISION ${session.fps}fps 준비")\n'''
)

print('V10 thermal fixups applied')
