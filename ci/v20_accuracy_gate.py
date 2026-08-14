#!/usr/bin/env python3
import csv
import math
import sys
from collections import defaultdict
from pathlib import Path

MIN_EXPECTED_SHOTS = 20
MIN_METRIC_SAMPLES = 20
MIN_PROFILE_SHOTS = 8
METRICS = (
    ('ball', 'ball_speed_mps', 'ball_tol_mps', 0.08),
    ('launch', 'launch_deg', 'launch_tol_deg', 0.35),
    ('face', 'face_deg', 'face_tol_deg', 0.55),
    ('path', 'path_deg', 'path_tol_deg', 0.65),
)


def number(row, key, default=None, *, label='value'):
    value = (row.get(key) or '').strip()
    if value == '':
        return default
    try:
        parsed = float(value)
    except ValueError as exc:
        raise SystemExit(f'accuracy gate: invalid {label} {key}={value!r}') from exc
    if not math.isfinite(parsed):
        raise SystemExit(f'accuracy gate: non-finite {label} {key}={value!r}')
    return parsed


def load(path):
    rows = {}
    with Path(path).open(encoding='utf-8-sig', newline='') as f:
        reader = csv.DictReader(f)
        if not reader.fieldnames or 'id' not in reader.fieldnames:
            raise SystemExit(f'accuracy gate: {path} missing id column')
        for line_no, row in enumerate(reader, start=2):
            shot_id = (row.get('id') or '').strip()
            if not shot_id:
                continue
            if shot_id in rows:
                raise SystemExit(f'accuracy gate: duplicate id {shot_id!r} in {path} line {line_no}')
            rows[shot_id] = row
    return rows, set(reader.fieldnames or [])


def validate_metric_coverage(rows, minimum, scope):
    coverage = {metric: 0 for metric, *_ in METRICS}
    for shot_id, row in rows.items():
        for metric, key, tolerance_key, default_tolerance in METRICS:
            expected_value = number(row, key, label=f'expected[{shot_id}]')
            if expected_value is None:
                continue
            tolerance = number(
                row,
                tolerance_key,
                default_tolerance,
                label=f'tolerance[{shot_id}]',
            )
            if tolerance is None or tolerance <= 0.0:
                raise SystemExit(
                    f'accuracy gate: tolerance must be > 0 for {shot_id} {metric}; got {tolerance}'
                )
            coverage[metric] += 1

    missing = [f'{metric}={count}' for metric, count in coverage.items() if count < minimum]
    if missing:
        raise SystemExit(
            f'accuracy gate: insufficient {scope} coverage; need '
            f'{minimum} finite samples per metric, got ' + ', '.join(missing)
        )


def validate_expected(expected, profile_aware=False):
    if len(expected) < MIN_EXPECTED_SHOTS:
        raise SystemExit(
            f'accuracy gate: expected fixture needs at least {MIN_EXPECTED_SHOTS} unique shots; got {len(expected)}'
        )

    validate_metric_coverage(expected, MIN_METRIC_SAMPLES, 'reference')

    if not profile_aware:
        return

    profiles = defaultdict(dict)
    for shot_id, row in expected.items():
        profile = (row.get('profile_key') or '').strip()
        if not profile:
            raise SystemExit(f'accuracy gate: expected[{shot_id}] missing profile_key')
        profiles[profile][shot_id] = row

    for profile, rows in sorted(profiles.items()):
        if len(rows) < MIN_PROFILE_SHOTS:
            raise SystemExit(
                f'accuracy gate: capture profile {profile!r} needs at least '
                f'{MIN_PROFILE_SHOTS} shots; got {len(rows)}'
            )
        validate_metric_coverage(rows, MIN_PROFILE_SHOTS, f'profile {profile!r}')


def main(expected_path, measured_path):
    expected, expected_fields = load(expected_path)
    measured, measured_fields = load(measured_path)
    profile_aware = 'profile_key' in expected_fields
    validate_expected(expected, profile_aware=profile_aware)

    if profile_aware and 'profile_key' not in measured_fields:
        raise SystemExit('accuracy gate: measured fixture missing profile_key column')

    failed = []
    totals = {metric: [] for metric, *_ in METRICS}
    profile_totals = defaultdict(lambda: {metric: [] for metric, *_ in METRICS})
    for shot_id, e in expected.items():
        m = measured.get(shot_id)
        if m is None:
            failed.append((shot_id, 'missing measurement'))
            continue

        profile = (e.get('profile_key') or '').strip() if profile_aware else ''
        if profile_aware:
            measured_profile = (m.get('profile_key') or '').strip()
            if measured_profile != profile:
                failed.append((shot_id, f'profile mismatch expected={profile!r} measured={measured_profile!r}'))
                continue

        reasons = []
        for metric, key, tolerance_key, default_tolerance in METRICS:
            ev = number(e, key, label=f'expected[{shot_id}]')
            if ev is None:
                continue
            tolerance = number(e, tolerance_key, default_tolerance, label=f'tolerance[{shot_id}]')
            mv = number(m, key, label=f'measured[{shot_id}]')
            if mv is None:
                reasons.append(f'{metric}=missing')
                continue
            error = abs(mv - ev)
            totals[metric].append(error)
            if profile_aware:
                profile_totals[profile][metric].append(error)
            if error > tolerance:
                reasons.append(f'{metric} err {error:.3f}>{tolerance:.3f}')
        if reasons:
            failed.append((shot_id, ', '.join(reasons)))

    measured_only = sorted(set(measured) - set(expected))
    if measured_only:
        print(f'accuracy gate: ignoring {len(measured_only)} measured-only rows')

    print(f'accuracy gate: {len(expected)-len(failed)}/{len(expected)} passed')
    for metric, errors in totals.items():
        if errors:
            print(f'  {metric} MAE={sum(errors)/len(errors):.4f} n={len(errors)}')
    if profile_aware:
        for profile, metrics in sorted(profile_totals.items()):
            summary = []
            for metric, errors in metrics.items():
                if errors:
                    summary.append(f'{metric}={sum(errors)/len(errors):.4f} n={len(errors)}')
            if summary:
                print(f'  profile {profile}: ' + ' · '.join(summary))
    for shot_id, reason in failed:
        print(f'FAIL {shot_id}: {reason}')
    if failed:
        raise SystemExit(2)


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('usage: v20_accuracy_gate.py EXPECTED.csv MEASURED.csv')
    main(sys.argv[1], sys.argv[2])
