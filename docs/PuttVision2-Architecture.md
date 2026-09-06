# PuttVision 2 Architecture

PuttVision 2 separates measurement/physics truth from presentation instead of forcing a game engine to own the entire product.

## Final authority split

- **Android native owns truth**: camera selection, HFR capture, calibration, computer vision, shot validation/fusion, settings, persistence, app lifecycle, `GreenSurface`, `GreenTerrain`, and the V135/V136 six-DOF putting physics.
- **Unity owns presentation**: 3D green/cup/flag/ball rendering, lighting, materials, post-processing, TV camera direction, HUD, shot trail, replay and presentation effects.
- **Unity does not re-simulate product shots.** Production ball motion follows the authoritative `SimState` snapshots published by native physics.
- The small Unity-side local simulator remains only as an editor/smoke fallback so the scene can be tested before Android is attached.
- Existing Filament/Godot renderers remain available until Unity passes parity and device validation. Migration is additive first, destructive last.

## Why native physics stays authoritative

The existing native solver is already purpose-built for this product. `GreenPhysics` delegates to `V135RigidBallPhysics`, which runs microsteps up to 480 Hz and tracks translational motion, angular velocity/orientation, skid-to-roll transition, regulation-cup edge/wall/bottom contact, lip-outs, bridging and flagstick contact. V136 adds surface realism such as grain, moisture, firmness and trueness.

Replacing that with Unity PhysX or a second Unity putting model would create two physical truths and make regression testing worse. Unity therefore consumes native results instead of recreating them.

## Runtime data flow

```text
Galaxy camera
  -> Android HFR capture
  -> native vision / calibration
  -> ShotMetrics validation + fusion
  -> GreenPhysics / V135RigidBallPhysics / V136 realism
        |                         |
        | shot-static context     | authoritative presentation snapshots
        v                         v
  PuttTelemetry JSON       PuttPhysicsFrame JSON
        \                         /
         -> UnityRendererBridge ->
            Unity PuttTelemetryReceiver / PuttPhysicsFrameReceiver
              -> PuttGreenMeshPresenter
              -> PuttAuthoritativeBallPresenter
              -> HUD / replay / camera / effects
              -> external display / TV
```

Raw camera frames never cross into Unity. Unity receives compact product state only.

## Surface parity

For the 24 built-in practice profiles, Unity ports the exact `GreenSurface.heightAt(...)` formulas and applies the same global side/long slope term used by `GreenTerrain.effectiveHeightAt(...)`:

```text
visualHeight = GreenSurface.heightAt(profile, x, y, holeDistance)
             - 0.01 * sideSlopePct * x
             - 0.01 * longSlopePct * y
```

This is presentation only; native remains authoritative. User-authored/custom greens must not be approximated with the built-in formula. Their production Unity path will consume sampled native surface geometry/height data.

## Coordinate and unit contract

- Native position basis: `+X` player-right, `+Y` target-forward, `+Z` up.
- Unity position basis: `+X` player-right, `+Z` target-forward, `+Y` up.
- Native position `(x, y, z)` maps to Unity `(x, z, y)`.
- Native quaternion orientation is **not** converted by swapping components. The correct Unity rotation is the basis transform `B * Rnative * B`, where `B` maps `(x,y,z)` to `(x,z,y)`.
- Distance: metres (`m`).
- Speed: metres per second (`m/s`).
- Impact offset: millimetres (`mm`).
- Angles: degrees (`deg`).
- `launchDirectionDeg`: `0` straight, positive player-right.
- `faceAngleDeg`: `0` square, positive open/player-right.
- `pathAngleDeg`: `0` target line, positive player-right.
- `impactOffsetMm`: `0` face centre, positive toe-side.
- `confidence`: `[0, 1]`.

Every payload is versioned with `schemaVersion`.

### Shot validity flags

| Bit | Value | Measurement |
| --- | ---: | --- |
| 0 | 1 | face angle valid |
| 1 | 2 | path angle valid |
| 2 | 4 | impact offset valid |
| 3 | 8 | confidence valid |

## Android/Unity transport

The Android bridge is compile-safe before Unity is exported: it has no compile-time `UnityPlayer` dependency and resolves `UnityPlayer.UnitySendMessage(...)` by reflection only when the Unity runtime exists. Without Unity packaged, it performs one capability probe and stays a no-op.

The first transport uses two message surfaces on the stable GameObject name `PuttTelemetryReceiver`:

- `OnTelemetryJson`: one shot-static payload with measurements and green settings.
- `OnPhysicsFrameJson`: newest authoritative `SimState` presentation snapshot.

The Unity receiver intentionally drops stale queued physics frames and consumes only the newest pose. If profiling later shows JSON/`UnitySendMessage` is too expensive at the selected render cadence, replace the transport with JNI/native shared memory or a binary ring buffer **without changing the authority split**.

## Migration gates

### Gate 0 — compile-safe scaffold

- Pin Unity 6.3 LTS and URP 17.3.
- Version shot/frame protocol.
- Keep existing Android build independent of `unityLibrary`.
- Add one-click prototype scene creation.

### Gate 1 — native-authoritative renderer parity

- Publish validated shot context from native `GreenPhysics.launch`.
- Publish thread-safe V126 physics snapshots to Unity when the runtime exists.
- Present native X/Y/Z and quaternion orientation exactly in Unity.
- Generate built-in green geometry from the native-compatible height field.
- Build a real cup opening/liner rather than a painted cup marker.
- Add recorded-shot/frame replay so rendering is testable without camera hardware.

### Gate 2 — production art and broadcast presentation

- Replace prototype materials with authored PBR grass/ball/cup/flag assets.
- Add turf normal/detail maps, anisotropic grass response, contact shadowing, reflection probes and tuned URP post-processing.
- Add camera choreography, telemetry HUD, shot trail, slope visualization and result/replay states.
- Add sampled custom-green surface transport.

### Gate 3 — Android host/export

- Export Unity as Android `unityLibrary`.
- Host it from the existing app without moving camera or physics lifecycle into Unity.
- Wire external display lifecycle and renderer fallback/rollback policy.

### Gate 4 — hardware validation

- Verify HFR capture and Unity rendering can coexist on the target Galaxy device without capture starvation.
- Verify native-vs-Unity final pose and rotation parity from recorded shots.
- Verify cup/lip-out/bridge/flagstick visual parity.
- Verify external-display 60 fps frame pacing, memory and thermals.

### Gate 5 — cutover

Only after Unity passes recorded-shot regression and physical-device validation:

1. Make Unity the default TV renderer.
2. Keep a rollback renderer for one release cycle.
3. Remove legacy renderer dependencies only after field validation.

## Performance budgets

- External-display renderer target: stable 60 fps.
- Zero camera-frame copies into Unity managed memory.
- No Unity physics in the product motion path.
- No unbounded physics-frame queue; newest frame wins.
- No JSON allocation in the existing Android path when Unity is absent.
- Surface mesh rebuild only when shot/static green context changes, not every frame.
- Dynamic frame payload excludes the full trail; Unity reconstructs presentation trail from received poses.

## Repository layout

```text
app/src/main/java/com/puttvision/screen/
  GreenPhysics.kt                  # native physical authority, publishes shot context
  V126PhysicsFrameBridge.kt        # thread-safe snapshot boundary + optional Unity publish
  UnityRendererBridge.kt           # optional transport/protocol

unity/
  Packages/
  ProjectSettings/
  Assets/PuttVision/
    Editor/PuttVisionSceneBuilder.cs
    Scripts/
      Bootstrap/
      Simulation/
        PuttAuthoritativeBallPresenter.cs
        PuttGreenSurfaceMath.cs
        PuttGreenMeshPresenter.cs
        ...editor/local fallback...
      Telemetry/
        PuttTelemetry.cs
        PuttPhysicsFrame.cs
        PuttTelemetryReceiver.cs
        PuttPhysicsFrameReceiver.cs
```

The current prototype primitives are smoke-test assets, not the visual-quality target.