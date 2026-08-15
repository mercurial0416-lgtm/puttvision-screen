# V59 stereo calibration runtime gate

V59 adds a fail-closed runtime binding layer in front of V53 triangulation.

A stored stereo profile is usable only when all of the following still match the active capture session:

- camera identity for both views
- image width and height
- capture FPS
- sensor orientation, lens facing, and capture mode
- explicit camera pair identity
- explicit physical-rig revision identity
- supported profile schema
- valid calibration timestamps and operational freshness policy
- valid V53 intrinsics/extrinsics
- calibration reprojection RMS within the existing V53 policy
- non-zero stereo baseline

The gate returns the validated V53 camera calibrations only after all checks pass. `triangulateIfReady` routes accepted profiles into V53 and returns a non-usable result without a 3D point when the gate blocks the profile.

## Important limitation

This gate does not estimate intrinsics/extrinsics and does not prove millimetre/centimetre real-device accuracy. `rigRevisionId` must be advanced by the future physical calibration workflow whenever either phone is moved. Real-device accuracy claims remain blocked until calibrated reference-device measurements exist.
