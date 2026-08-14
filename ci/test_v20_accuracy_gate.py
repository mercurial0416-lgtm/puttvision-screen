#!/usr/bin/env python3
import csv
import tempfile
import unittest
from pathlib import Path

from ci import v20_accuracy_gate as gate


class AccuracyGateTest(unittest.TestCase):
    def write_csv(self, name, fieldnames, rows):
        path = Path(self.tmp.name) / name
        with path.open('w', encoding='utf-8', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(rows)
        return path

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def fixtures(self, count=20, *, face=True, profiles=False):
        expected = []
        measured = []
        for i in range(count):
            sid = f'shot-{i:03d}'
            profile = 'phone|CAM:0|FPS:240|SIZE:1920x1080|API:36'
            expected_row = {
                'id': sid,
                'ball_speed_mps': '1.500', 'ball_tol_mps': '0.080',
                'launch_deg': '0.200', 'launch_tol_deg': '0.350',
                'face_deg': '-0.300' if face else '', 'face_tol_deg': '0.550' if face else '',
                'path_deg': '0.400', 'path_tol_deg': '0.650',
            }
            measured_row = {
                'id': sid,
                'ball_speed_mps': '1.510',
                'launch_deg': '0.220',
                'face_deg': '-0.280',
                'path_deg': '0.390',
            }
            if profiles:
                expected_row['profile_key'] = profile
                measured_row['profile_key'] = profile
            expected.append(expected_row)
            measured.append(measured_row)
        return expected, measured

    def paths(self, expected, measured, *, profiles=False):
        expected_fields = [
            'id','ball_speed_mps','ball_tol_mps','launch_deg','launch_tol_deg',
            'face_deg','face_tol_deg','path_deg','path_tol_deg'
        ]
        measured_fields = ['id','ball_speed_mps','launch_deg','face_deg','path_deg']
        if profiles:
            expected_fields.insert(1, 'profile_key')
            measured_fields.insert(1, 'profile_key')
        ep = self.write_csv('expected.csv', expected_fields, expected)
        mp = self.write_csv('measured.csv', measured_fields, measured)
        return ep, mp

    def test_twenty_complete_shots_pass(self):
        expected, measured = self.fixtures()
        ep, mp = self.paths(expected, measured)
        gate.main(ep, mp)

    def test_too_few_reference_shots_fail(self):
        expected, measured = self.fixtures(19)
        ep, mp = self.paths(expected, measured)
        with self.assertRaises(SystemExit):
            gate.main(ep, mp)

    def test_duplicate_id_fails_instead_of_silent_overwrite(self):
        expected, measured = self.fixtures()
        expected.append(dict(expected[-1]))
        ep, mp = self.paths(expected, measured)
        with self.assertRaises(SystemExit):
            gate.main(ep, mp)

    def test_non_finite_measurement_fails(self):
        expected, measured = self.fixtures()
        measured[0]['ball_speed_mps'] = 'NaN'
        ep, mp = self.paths(expected, measured)
        with self.assertRaises(SystemExit):
            gate.main(ep, mp)

    def test_each_gated_metric_needs_full_minimum_coverage(self):
        expected, measured = self.fixtures(face=False)
        ep, mp = self.paths(expected, measured)
        with self.assertRaises(SystemExit):
            gate.main(ep, mp)

    def test_outside_per_shot_tolerance_fails_release(self):
        expected, measured = self.fixtures()
        measured[7]['launch_deg'] = '0.800'
        ep, mp = self.paths(expected, measured)
        with self.assertRaises(SystemExit) as ctx:
            gate.main(ep, mp)
        self.assertEqual(2, ctx.exception.code)

    def test_profile_aware_fixture_passes_with_twenty_same_profile_shots(self):
        expected, measured = self.fixtures(profiles=True)
        ep, mp = self.paths(expected, measured, profiles=True)
        gate.main(ep, mp)

    def test_profile_aware_fixture_rejects_blank_profile(self):
        expected, measured = self.fixtures(profiles=True)
        expected[0]['profile_key'] = ''
        ep, mp = self.paths(expected, measured, profiles=True)
        with self.assertRaises(SystemExit):
            gate.main(ep, mp)

    def test_second_profile_needs_eight_complete_shots(self):
        expected, measured = self.fixtures(profiles=True)
        second = 'phone|CAM:0|FPS:120|SIZE:1920x1080|API:36'
        for i in range(gate.MIN_PROFILE_SHOTS - 1):
            expected[-1 - i]['profile_key'] = second
            measured[-1 - i]['profile_key'] = second
        ep, mp = self.paths(expected, measured, profiles=True)
        with self.assertRaises(SystemExit):
            gate.main(ep, mp)

    def test_profile_mismatch_fails_even_when_metrics_match(self):
        expected, measured = self.fixtures(profiles=True)
        measured[0]['profile_key'] = 'other|CAM:0|FPS:120|SIZE:1280x720|API:36'
        ep, mp = self.paths(expected, measured, profiles=True)
        with self.assertRaises(SystemExit) as ctx:
            gate.main(ep, mp)
        self.assertEqual(2, ctx.exception.code)

    def test_legacy_fixture_without_profile_column_remains_supported(self):
        expected, measured = self.fixtures()
        ep, mp = self.paths(expected, measured)
        gate.main(ep, mp)


if __name__ == '__main__':
    unittest.main()
