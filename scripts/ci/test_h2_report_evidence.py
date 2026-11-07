# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from junit_evidence import EvidenceError

SPEC = importlib.util.spec_from_file_location('h2_reports', Path(__file__).with_name('verify-h2-reports.py'))
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class H2ReportEvidenceTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        plan = self.root / 'docs/testing/release-2026.08-verification.json'
        plan.parent.mkdir(parents=True)
        plan.write_text(json.dumps({'checks': [{'id': 'java-all', 'minimumCases': 2,
            'routedSkips': {'example.Native': {'check': 'native', 'cases': ['required']}}}]}))
        self.reports = self.root / 'h2'
        self.permission = self.root / 'permission'
        self.reports.mkdir()
        self.permission.mkdir()
        self.addCleanup(patch.stopall)
        patch.object(GATE, 'ROOT', self.root).start()
        self.report(self.reports, 'example.Native', [('required', 'skipped')])
        for row in GATE.PERMISSION_CASES:
            self.report(self.reports, row['classname'], [(row['name'], 'skipped')], row['container'])
            self.report(self.permission, row['classname'], [(row['name'], 'pass')], row['container'])

    def report(self, directory, family, cases, container=''):
        from xml.etree.ElementTree import Element, SubElement, tostring
        root = Element('testsuite', name=family, tests=str(len(cases)),
                       failures=str(sum(outcome == 'failure' for _, outcome in cases)), errors='0',
                       skipped=str(sum(outcome == 'skipped' for _, outcome in cases)))
        for name, outcome in cases:
            case = SubElement(root, 'testcase', classname=container or family, name=name)
            if outcome != 'pass':
                SubElement(case, outcome)
        (directory / f'TEST-{family}.xml').write_bytes(tostring(root))

    def test_exact_routed_skip_requires_a_passing_permission_case(self):
        self.assertTrue(GATE.verify(self.reports, self.permission)['ready'])
        for outcome in ('skipped', 'failure'):
            self.report(self.permission, GATE.PERMISSION_CLASS, [(GATE.PERMISSION_CASE, outcome)])
            with self.assertRaises(EvidenceError):
                GATE.verify(self.reports, self.permission)

    def test_another_passing_case_cannot_replace_permission_regression(self):
        self.report(self.permission, GATE.PERMISSION_CLASS, [('unrelated', 'pass')])
        with self.assertRaisesRegex(EvidenceError, 'replaced'):
            GATE.verify(self.reports, self.permission)

    def test_unexpected_skips_and_failed_first_attempts_cannot_pass(self):
        for family, case, outcome in [('example.Native', 'unrouted', 'skipped'),
                                      ('example.Native', 'required', 'failure')]:
            self.report(self.reports, family, [(case, outcome)])
            with self.assertRaises(EvidenceError):
                GATE.verify(self.reports, self.permission)

    def test_missing_permission_report_is_not_success(self):
        for row in GATE.PERMISSION_CASES:
            report = self.permission / f"TEST-{row['classname']}.xml"
            contents = report.read_bytes()
            report.unlink()
            with self.assertRaises(EvidenceError):
                GATE.verify(self.reports, self.permission)
            report.write_bytes(contents)
