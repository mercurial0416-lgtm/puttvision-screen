# PuttVision V18–V21

## V18 — real 3D simulator TV
- Added an OpenGL ES 2 putting stage with terrain geometry generated from the same GreenTerrain model used by physics.
- Added low address camera, ball-follow camera, near-cup tracking, and cup/result close-up framing.
- Added shaded green/rough, 3D ball, cup/flag, solution/ghost/live-path ribbons.
- Added procedural rolling hills, tree lines, a generic clubhouse, mowing depth, and a contact shadow under the ball so the asset-free 3D path does not become visually sparse.
- Real TV and local/hardwareless preview use the same V18 factory. Devices without GLES 2 support fall back to the proven V17 Canvas renderer.

## V19 — stroke studio
- Preserves real preview-camera putter-head samples around impact without changing the persisted Room schema.
- Rejects stale traces by requiring the trace timestamp to match the current shot impact.
- Compares CURRENT vs IDEAL vs BEST with a coaching corridor, path RMS, face RMS, and trace quality score.
- Shows a short result-only TV overlay and adds the same comparison to the HFR phone replay.

## V20 — green reading and performance comparison
- Added blind green-read training: hide the solver before the shot, use the player's actual launch line/speed as the answer, reveal and grade after the ball stops.
- Added OFF/AUTO/ALWAYS read modes with persistent product preference.
- Added per-putter performance ranking and baseline-vs-recent trend reports.
- Added deterministic metric regression models plus `ci/v20_accuracy_gate.py` for real-device/video CSV fixtures.
- Real S25 fixtures are intentionally not claimed as validated until controlled captures are collected.

## V21 — camera consistency hardening
- Added Camera2 CaptureResult metadata monitoring through CameraX session callbacks.
- Tracks actual exposure, ISO, frame duration, rolling-shutter skew, AE/AWB lock state, antibanding mode, and scene flicker metadata.
- Capture quality can only reduce shot confidence; it never upgrades a weak vision result.
- Added 60 Hz antibanding selection when reported as supported by the active camera, with AUTO/available fallback.
- Normal and HFR preview sessions feed the same capture-quality runtime.

## Validation before merge
- Consumer and developer unit suites must pass.
- Consumer and developer debug APKs must compile.
- Real-device accuracy gate runs automatically when both reference and measured fixtures are committed.