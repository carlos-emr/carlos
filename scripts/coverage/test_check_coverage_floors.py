# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('floors', Path(__file__).with_name('check-coverage-floors.py'))
floors = importlib.util.module_from_spec(spec)
spec.loader.exec_module(floors)


class CoverageFloorsTest(unittest.TestCase):
    def test_partial_instructions_branches_and_refusals_cannot_hide_behind_a_line_hit(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / 'jacoco.xml'
            report.write_text('<report><package name="example"><sourcefile name="Guard.java">'
                              '<line nr="1" ci="2" mi="2" cb="1" mb="1"/></sourcefile></package></report>')
            path = 'src/main/java/example/Guard.java'
            diff = 'diff --git a/x b/x\n+++ b/' + path + '\n@@ -1 +1 @@\n+guard\n'
            policy = {'schemaVersion': 1, 'baselineCommit': 'test-baseline', 'files': {
                path: {'lines': 100, 'instructions': 100, 'branches': 100, 'requiredRefusalLines': [1]}}}
            result = floors.check_floors(report, policy, diff)
            self.assertFalse(result['ready'])
            self.assertEqual({row['reason'] for row in result['problems']},
                             {'instructions', 'branches', 'uncovered-refusal'})
            report.write_text(report.read_text().replace('mi="2"', 'mi="0"').replace('mb="1"', 'mb="0"'))
            self.assertTrue(floors.check_floors(report, policy, diff)['ready'])

    def test_unmapped_or_unreviewed_executable_source_is_not_excluded(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / 'jacoco.xml'
            report.write_text('<report/>')
            path = 'src/main/java/example/Guard.java'
            diff = 'diff --git a/x b/x\n+++ b/' + path + '\n@@ -0,0 +1 @@\n+guard\n'
            policy = {'schemaVersion': 1, 'baselineCommit': 'test', 'files': {path: {
                'lines': 50, 'instructions': 50, 'branches': 50}}}
            self.assertFalse(floors.check_floors(report, policy, diff)['ready'])
            policy['exclusions'] = {path: {'reason': 'cannot cover', 'tests': ['some-test']}}
            with self.assertRaisesRegex(ValueError, 'Executable'):
                floors.check_floors(report, policy, diff)

    def test_invalid_thresholds_and_refusal_lines_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / 'jacoco.xml'
            report.write_text('<report><package name="example"><sourcefile name="Guard.java">'
                             '<line nr="1" ci="1" mi="0" cb="0" mb="0"/></sourcefile></package></report>')
            path = 'src/main/java/example/Guard.java'
            diff = 'diff --git a/x b/x\n+++ b/' + path + '\n@@ -0,0 +1 @@\n+guard\n'
            for metric in ('lines', 'instructions', 'branches'):
                for invalid in (float('nan'), float('inf'), -1, 101, True, '100', None):
                    with self.subTest(metric=metric, invalid=invalid):
                        row = dict.fromkeys(('lines', 'instructions', 'branches'), 100)
                        if invalid is None: row.pop(metric)
                        else: row[metric] = invalid
                        with self.assertRaisesRegex(ValueError, 'finite percentages'):
                            floors.check_floors(report, {'schemaVersion': 1, 'baselineCommit': 'base', 'files': {path: row}}, diff)
            for invalid in (True, 0, -1, 1.5, '1', None):
                with self.subTest(refusal=invalid):
                    row = {'lines': 100, 'instructions': 100, 'branches': 100, 'requiredRefusalLines': [invalid]}
                    with self.assertRaisesRegex(ValueError, 'positive source line'):
                        floors.check_floors(report, {'schemaVersion': 1, 'baselineCommit': 'base', 'files': {path: row}}, diff)

    def test_missing_and_stale_floors_cannot_qualify_a_comparison(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / 'jacoco.xml'
            report.write_text('<report><package name="example"><sourcefile name="Guard.java">'
                             '<line nr="1" ci="1" mi="0" cb="0" mb="0"/></sourcefile></package></report>')
            path = 'src/main/java/example/Guard.java'
            diff = 'diff --git a/x b/x\n+++ b/' + path + '\n@@ -0,0 +1 @@\n+guard\n'
            stale = 'src/main/java/example/Unchanged.java'
            policy = {'schemaVersion': 1, 'baselineCommit': 'base', 'files': {
                stale: {'lines': 100, 'instructions': 100, 'branches': 100}}}
            result = floors.check_floors(report, policy, diff)
            self.assertFalse(result['ready'])
            self.assertEqual(result['problems'], [{'path': path, 'reason': 'missing-floor'},
                {'path': stale, 'reason': 'floor-outside-comparison'}])
