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
