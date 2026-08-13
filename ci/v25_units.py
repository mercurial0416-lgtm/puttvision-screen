from pathlib import Path

# Keep all TV target / remaining / result distance readouts in metres with two decimals.
files = [
    Path("app/src/main/java/com/puttvision/screen/V18OpenGlSimulator.kt"),
    Path("app/src/main/java/com/puttvision/screen/V17SimulatorTvView.kt"),
]

for path in files:
    text = path.read_text(encoding="utf-8")
    before = text
    text = text.replace(
        '${"%.1f".format(engine.settings.holeDistanceM)} m',
        '${"%.2f".format(engine.settings.holeDistanceM)} m'
    )
    text = text.replace(
        '${"%.1f".format(settings.holeDistanceM)}m',
        '${"%.2f".format(settings.holeDistanceM)} m'
    )
    if text != before:
        path.write_text(text, encoding="utf-8")
        print(f"V25 metre precision updated: {path}")
    else:
        print(f"V25 metre precision already current: {path}")
