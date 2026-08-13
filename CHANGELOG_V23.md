# PuttVision V23 — markerless-first normal calibration

## Normal 30/60 fps setup
- Saved physical mat width/length plus the visible mat outline is now the default calibration path.
- Reads YUV_420_888 directly without allocating a full-frame Bitmap.
- Detects green and dark-neutral mat candidates, segments connected components, and fits a real perspective quadrilateral instead of an axis-aligned box.
- Boundary fitting uses deterministic RANSAC-style inlier selection plus least-squares refinement to reject side-edge contamination and sparse background noise.

## Conservative acceptance
- Requires four stable frames, markerless confidence >= 0.80, acceptable frame quality, and acceptable placement geometry before calibration can complete.
- Full-width floor patches, small disconnected clutter, weak components, implausible perspective, and unstable corner motion are rejected.
- A weak markerless result does not get promoted simply to avoid QR.

## QR policy
- QR is no longer required for the normal path when the mat is confidently visible.
- Existing PV1/generic QR calibration remains available as a precision/recovery assist for difficult mats, occlusion, or lighting.
- Markerless and QR from the same ImageProxy are frame-idempotent, preventing a delayed QR callback from overwriting a markerless result from the exact same frame.

## Geometry consistency
- Markerless corners are passed downstream in the same BL / BR / TR / TL ordering as their metric mat coordinates.
- The same homography path continues to feed normal tracking and downstream calibration quality checks.

## Validation
- Synthetic perspective-mat regression with sparse holes/noise passes.
- Full-width floor and disconnected-clutter rejection tests pass.
- Consumer and developer unit/regression suites pass.
- Consumer and developer debug APK assembly pass.
- Real Galaxy S25 mat, lighting, and camera-position validation is still required before claiming real-device markerless accuracy.