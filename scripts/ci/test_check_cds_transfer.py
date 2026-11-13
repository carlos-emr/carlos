#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""The documented inventory reproduction must not accept skipped or missing suites."""
import importlib.util
import io
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

MODULE_PATH = Path(__file__).with_name('check-cds-transfer.py')
SPEC = importlib.util.spec_from_file_location('check_cds_transfer', MODULE_PATH)
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


def report(directory, simple, skipped=0):
    cases = ''.join('<testcase name="case%d"><skipped/></testcase>' % i if i < skipped
                    else '<testcase name="case%d"/>' % i for i in range(2))
    (directory / ('TEST-io.example.' + simple + '.xml')).write_text(
        '<testsuite name="io.example.%s" tests="2" errors="0" failures="0" skipped="%d">%s</testsuite>'
        % (simple, skipped, cases))


class InventoryReproductionTest(unittest.TestCase):
    def run_check(self, skipped_suite=None, maven_code=0, missing=False):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repo = root / 'repo'
            (repo / 'scripts/ci').mkdir(parents=True)
            selectors = ['FirstCdsSuiteTest', 'SecondCdsSuiteTest']
            (repo / 'scripts/ci/cds-coordinated-test-classes.json').write_text(str(selectors).replace("'", '"'))
            properties = {}
            for name in ('CDS_TEST_PROPERTIES', 'CDS_LEDGER_TEST_PROPERTIES', 'CDS_EXPORT_TEST_PROPERTIES'):
                path = root / (name + '.properties'); path.write_text('ownedDisposableDatabase=true\n')
                properties[name] = str(path)
            documents = root / 'documents'; documents.mkdir()
            properties['CDS_EXPORT_TEST_DOCUMENT_ROOT'] = str(documents)
            commands = []

            def maven(command, cwd):
                commands.append(command)
                reports = repo / 'target/surefire-reports'
                reports.mkdir(parents=True)
                for simple in selectors:
                    if missing and simple == 'SecondCdsSuiteTest':
                        continue
                    report(reports, simple, 1 if simple == skipped_suite else 0)
                return mock.Mock(returncode=maven_code)

            with mock.patch.object(CHECK, '__file__', str(repo / 'scripts/ci/check-cds-transfer.py')), \
                    mock.patch.object(CHECK, 'native_tier', lambda: NATIVE), \
                    mock.patch.object(CHECK.subprocess, 'run', side_effect=maven), \
                    mock.patch.dict(os.environ, properties), \
                    mock.patch.object(sys, 'argv', ['check']), redirect_stdout(io.StringIO()):
                code = CHECK.main()
            return code, commands

    def test_complete_reports_pass_and_clear_inherited_tag_filters(self):
        code, commands = self.run_check()
        self.assertEqual(0, code)
        self.assertIn('-Dgroups=', commands[0])
        self.assertIn('-DexcludedGroups=', commands[0])
        self.assertTrue(any(arg.startswith('-Dcds.export.test.documentRoot=') for arg in commands[0]))

    def test_a_skipped_suite_fails_even_when_maven_succeeds(self):
        self.assertEqual(1, self.run_check(skipped_suite='SecondCdsSuiteTest')[0])

    def test_a_missing_suite_or_a_maven_failure_fails(self):
        self.assertEqual(1, self.run_check(missing=True)[0])
        self.assertEqual(1, self.run_check(maven_code=1)[0])


NATIVE_SPEC = importlib.util.spec_from_file_location('cds_native_tier_for_check', Path(__file__).with_name('run-cds-native-tier.py'))
NATIVE = importlib.util.module_from_spec(NATIVE_SPEC)
NATIVE_SPEC.loader.exec_module(NATIVE)

if __name__ == '__main__':
    unittest.main()
