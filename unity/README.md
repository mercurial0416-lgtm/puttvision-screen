# PuttVision Unity Renderer

This directory is the Unity presentation project for PuttVision 2.

## Version

- Unity: **6.3 LTS** (`6000.3.0f1` baseline)
- URP: **17.3.0**
- Product motion authority: Android native `GreenPhysics` / V135 / V136

## First editor smoke run

1. Open the `unity/` directory as a Unity project.
2. Let Package Manager restore URP and the test framework.
3. If the project has no URP asset yet, create/assign one through Unity's URP project setup before judging materials. The branch intentionally avoids committing hand-written serialized URP assets before an editor has generated/validated them.
4. Run `PuttVision > Build Prototype Scene`.
5. Open `Assets/PuttVision/Scenes/PuttVisionPrototype.unity` if Unity did not keep it active.
6. Enter Play mode.
7. On the `PuttVisionBootstrap` component, use the editor context command `PuttVision/Send Test Putt` for a no-Android smoke shot.
8. Run EditMode tests under `Assets/PuttVision/Tests/Editor`.

The generated primitive scene is a functional integration target, not final art.

## Product integration

Native Android code publishes two versioned payloads to the stable GameObject name `PuttTelemetryReceiver` when `com.unity3d.player.UnityPlayer` is packaged:

- `OnTelemetryJson`: shot metrics and green context.
- `OnPhysicsFrameJson`: authoritative V126/V135/V136 ball state.

`PuttAuthoritativeBallPresenter` converts the native basis `(X right, Y forward, Z up)` to Unity `(X right, Z forward, Y up)` and converts rotation through the full basis matrix rather than swapping quaternion components.

`PuttGreenMeshPresenter` reconstructs the 24 built-in surfaces from the same height formulas used by native code and cuts an actual cup opening with an interior liner. Custom/user-authored greens require sampled native surface geometry and are intentionally not approximated.

## What must stay native

Do not move these into Unity during this migration:

- high-speed camera capture;
- camera calibration and vision;
- shot validation/fusion;
- V135/V136 putting physics;
- score/history/session truth.

The Unity-side `PuttBallController` is editor/local fallback only. Once an authoritative native frame arrives, `PuttAuthoritativeBallPresenter` disables that fallback.

## Android library export

When the Unity scene and tests are clean, export Unity as an Android library and integrate the generated `unityLibrary` module into the Android host. Keep the existing renderer path available until device-level parity, HFR coexistence, external-display frame pacing and thermals have been validated.
