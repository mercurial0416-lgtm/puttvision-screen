# PuttVision 2 Architecture

PuttVision 2 separates sensing/analysis from rendering instead of forcing one engine to own the entire product.

## Design goal

- **Android native owns truth**: camera selection, high-speed capture, calibration, computer vision, shot measurement, settings, persistence, and app lifecycle.
- **Unity owns presentation**: 3D green, ball/cup/flag presentation, shot replay, slope visualization, lighting, post-processing, TV output, and deterministic putting simulation.
- The legacy renderer stays available until the Unity path reaches parity. Migration is additive first, destructive last.

## Runtime data flow

```text
Galaxy camera
  -> Camera2 high-speed capture (when the device/format supports it)
  -> native vision / calibration pipeline
  -> PuttTelemetry (one immutable shot result)
  -> UnityTelemetryBridge
  -> Unity PuttTelemetryReceiver
  -> PuttBallController / replay / HUD
  -> external display / TV
```

Do **not** stream raw camera frames through the Unity bridge. Camera frames stay native. Unity receives compact shot results and, later, low-rate status/calibration state only.

## Coordinate and unit contract

The telemetry contract is deliberately small and versioned.

- Distance: metres (`m`)
- Speed: metres per second (`m/s`)
- Impact offset: millimetres (`mm`)
- Angles: degrees (`deg`)
- Unity world: `+Z` is straight toward the target, `+X` is player-right, `+Y` is up
- `launchDirectionDeg`: `0` = straight, positive = player-right
- `faceAngleDeg`: `0` = square, positive = open/player-right
- `pathAngleDeg`: `0` = target line, positive = player-right
- `impactOffsetMm`: `0` = face centre, positive = toe-side
- `confidence`: `[0, 1]`

A consumer must check `schemaVersion` before trusting a payload.

### validityFlags

Optional measurements are represented by a bit field so JSON stays cheap and Unity `JsonUtility` remains sufficient.

| Bit | Value | Measurement |
| --- | ---: | --- |
| 0 | 1 | face angle valid |
| 1 | 2 | path angle valid |
| 2 | 4 | impact offset valid |
| 3 | 8 | confidence valid |

Ball speed and launch direction are required for a shot to enter simulation.

## Integration rule

Unity is exported as an Android **library**, then hosted by the native application. The native application must remain capable of launching without Unity during migration.

The initial bridge uses `UnityPlayer.UnitySendMessage(...)` only at the shot-result boundary. If later profiling shows the message bridge is insufficient for high-rate status traffic, replace only the transport; keep `PuttTelemetry` stable.

## Migration gates

### Gate 0 — scaffold (this branch)

- Freeze telemetry schema v1.
- Add Unity-side receiver and deterministic simulation core.
- Add Android-side contract/bridge reference implementation.
- Touch no legacy runtime path.

### Gate 1 — renderer parity

- Build a URP green scene.
- Match current shot start/end coordinates and camera framing.
- Add cup interaction, ball spin, shadows, slope/debug overlays, and replay.
- Add recorded-shot replay so rendering can be tested without a camera.

### Gate 2 — Android host

- Export Unity project as `unityLibrary`.
- Host it from the existing Android app behind a feature flag.
- Keep Camera2/vision lifecycle outside Unity.
- Forward shot telemetry only after native validation.

### Gate 3 — hardware validation

- Verify supported high-speed camera modes on the target Galaxy device.
- Verify shot measurements against known test strokes.
- Verify external display lifecycle and 60 fps render target.
- Profile thermals, frame pacing, memory, and capture/render contention.

### Gate 4 — cutover

Only after the Unity path passes the same recorded-shot suite and hardware tests as the existing renderer:

1. Make Unity renderer the default.
2. Keep a rollback feature flag for one release cycle.
3. Remove the legacy renderer only after field validation.

## Performance budgets

Initial budgets; tune from device profiling rather than guesses.

- Renderer target: stable 60 fps on the external display.
- No camera-frame copies into managed Unity memory.
- No blocking work on Android main/UI thread.
- Shot-result bridge payload should remain tiny (hundreds of bytes, not frames/images).
- Simulation uses fixed-step updates and recorded telemetry for deterministic regression testing.

## Repository layout introduced by this migration

```text
docs/
  PuttVision2-Architecture.md
native-bridge/
  android/
    PuttTelemetry.kt
    UnityTelemetryBridge.kt
unity/
  README.md
  Assets/PuttVision/Scripts/
    Bootstrap/
    Simulation/
    Telemetry/
```

The `native-bridge` Kotlin files are a reference module until the actual Android host repository/module is located and wired. They intentionally do not modify the current screen renderer build.