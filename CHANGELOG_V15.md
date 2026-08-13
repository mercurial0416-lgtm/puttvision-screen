# PuttVision V15 — Product-grade putting intelligence

## Measurement and setup
- Added a second trajectory continuity gate after pixel detection to reject stationary bright objects, impossible frame jumps, and poor motion fits.
- Added live phone-placement scoring from calibration geometry, including left/right shift, height/coverage, camera roll, and perspective guidance.
- Added a conservative markerless-mat fallback that only returns a candidate when segmentation confidence is sufficient. Metric homography still requires the real mat dimensions.
- Added a physical cup/dark-target detector for real-hole confirmation pipelines.
- Existing V14 marked-ball spin/skid/roll measurement is consumed by V15 roll-quality grading and coaching.

## Coaching and performance
- Added V15 stroke signature: arc type, face-to-path, tempo, impact bias, launch bias, consistency, speed CV, deceleration risk, and repeatability score.
- Upgraded the local coach from single-shot thresholds to prioritized diagnosis using recent-shot patterns.
- Added automatic drill selection and measurable practice targets.
- Added roll-efficiency grades and skid-specific feedback when a marked ball is available.

## Putter fitting
- Added camera-derived fitting guidance after at least 20 shots with the selected putter.
- Recommends face-balanced / slight-toe-hang / toe-hang and blade / mid-mallet / high-MOI mallet families.
- Length and lie outputs are deliberately limited to small trial deltas because phone video alone cannot replace a physical fitting measurement.

## Ghost and games
- Added a historical best-shot ghost engine grouped by target distance, terrain, and mode.
- Added ghost comparison for score, launch, ball speed, and finishing position.
- Added Dart, Curling, Battle, and Ghost modes alongside the existing practice modes.
- Existing 1–4 player turn handling is reused by the new competitive modes.

## Multi-phone cameras
- Added confidence-weighted fusion for primary, face-on, down-the-line, and top camera measurements.
- Added a compact LAN companion transport using newline-delimited JSON; no cloud backend is required.
- Recent companion measurements are fused automatically when the primary phone launches the shot.

## Hands-free flow
- Added a common READY → IMPACT → ANALYZING → ROLLING → RESULT → REARM state model shared by normal measurement and product logic.

## Validation
- Added V15 regression coverage for trajectory gating, performance analysis, putter fitting, multi-camera fusion, ghost comparison, and competitive modes.
- Added a temporary branch CI workflow that runs both consumer/developer unit-test suites and compiles both debug APK variants before merge.
