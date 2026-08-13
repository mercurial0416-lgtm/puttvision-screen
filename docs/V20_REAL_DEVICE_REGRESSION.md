# V20 real-device/video regression

The app already has an Accuracy Validation Lab for collecting matched reference values. V20 adds a CI gate so those captures can become permanent release fixtures instead of one-off manual checks.

## Fixture contract

Commit these two files when a validated device set is available:

- `validation/v20_reference.csv` — truth/reference values from the controlled capture session.
- `validation/v20_measured.csv` — values produced by the candidate build against the exact same clips/shots.

Required header:

```csv
id,ball_speed_mps,launch_deg,face_deg,path_deg,ball_tol_mps,launch_tol_deg,face_tol_deg,path_tol_deg
```

`face_deg` and `path_deg` may be blank when that reference shot cannot support the metric. Tolerance columns may be omitted/blank; defaults are 0.08 m/s, 0.35°, 0.55°, and 0.65° respectively.

## Capture set

Use the same Galaxy S25 camera position and lighting for the baseline set, then add deliberately harder subsets instead of replacing the baseline:

1. flat 1.5 m / 3 m / 5 m puts,
2. slow / normal / fast mat calibration,
3. left/right launch angles,
4. center / heel / toe strikes when a visible putter head is available,
5. bright, dim and reflective-background cases,
6. 60/120/240 fps paths where supported.

The stable baseline should never be regenerated simply because a new algorithm changed its answers. Change truth only when the capture/reference itself is proven wrong.

## CI

Run:

```bash
python3 ci/v20_accuracy_gate.py validation/v20_reference.csv validation/v20_measured.csv
```

The command exits non-zero for a missing shot or any metric outside its reference tolerance. The Kotlin `V20RegressionGate` uses the same concept inside unit tests and can be fed by the app-side validation flow.
