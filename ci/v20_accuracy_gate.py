#!/usr/bin/env python3
import csv
import math
import sys
from pathlib import Path


def number(row, key, default=None):
    value = (row.get(key) or '').strip()
    if value == '':
        return default
    return float(value)


def load(path):
    with Path(path).open(encoding='utf-8-sig', newline='') as f:
        return {row['id'].strip(): row for row in csv.DictReader(f) if row.get('id', '').strip()}


def main(expected_path, measured_path):
    expected = load(expected_path)
    measured = load(measured_path)
    if not expected:
        raise SystemExit('accuracy gate: expected fixture is empty')

    failed = []
    totals = {'ball': [], 'launch': [], 'face': [], 'path': []}
    for shot_id, e in expected.items():
        m = measured.get(shot_id)
        if m is None:
            failed.append((shot_id, 'missing measurement'))
            continue

        checks = [
            ('ball', 'ball_speed_mps', number(e, 'ball_tol_mps', 0.08)),
            ('launch', 'launch_deg', number(e, 'launch_tol_deg', 0.35)),
            ('face', 'face_deg', number(e, 'face_tol_deg', 0.55)),
            ('path', 'path_deg', number(e, 'path_tol_deg', 0.65)),
        ]
        reasons = []
        for metric, key, tolerance in checks:
            ev = number(e, key)
            if ev is None:
                continue
            mv = number(m, key)
            if mv is None:
                reasons.append(f'{metric}=missing')
                continue
            error = abs(mv - ev)
            totals[metric].append(error)
            if error > tolerance:
                reasons.append(f'{metric} err {error:.3f}>{tolerance:.3f}')
        if reasons:
            failed.append((shot_id, ', '.join(reasons)))

    print(f'accuracy gate: {len(expected)-len(failed)}/{len(expected)} passed')
    for metric, errors in totals.items():
        if errors:
            print(f'  {metric} MAE={sum(errors)/len(errors):.4f}')
    for shot_id, reason in failed:
        print(f'FAIL {shot_id}: {reason}')
    if failed:
        raise SystemExit(2)


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('usage: v20_accuracy_gate.py EXPECTED.csv MEASURED.csv')
    main(sys.argv[1], sys.argv[2])
