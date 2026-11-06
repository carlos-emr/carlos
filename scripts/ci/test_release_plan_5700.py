# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import release_verification as gate
from junit_evidence import EvidenceError


class RequiredExecutionsTest(unittest.TestCase):
    def test_removing_one_manual_province_is_rejected(self):
        plan = gate.read_json(gate.DEFAULT_PLAN)
        check = next(c for c in plan['checks'] if c['id'] == 'manual-legacy-encounter-editor-isolation')
        check['profiles'] = ['ON']
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'plan.json'
            path.write_text(json.dumps(plan))
            with self.assertRaisesRegex(EvidenceError, 'Manual browser'):
                gate.load_plan(path)

    def test_jdbc_parameterized_routes_require_both_native_invocations(self):
        plan = gate.load_plan()
        checks = {check['id']: check for check in plan['checks']}
        source = checks['java-all']['routedSkips']['io.github.carlos_emr.carlos.utility.JdbcConnectorMariaDbIntegrationTest']
        self.assertEqual(source['check'], 'java-native-generated')
        self.assertEqual(len(source['cases']), 16)
        for case in source['cases']:
            self.assertEqual([row['name'] for row in case['destinations']],
                             [case['name'] + '[1]', case['name'] + '[2]'])
        self.assertIn('JdbcConnectorMariaDbIntegrationTest', ' '.join(checks['java-native-generated']['command']))

    def test_locked_chromium_rejects_missing_and_wrong_browser_metadata(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'scripts').mkdir()
            (root / 'scripts/playwright-suite.json').write_text(json.dumps({'checks': [
                {'name': 'required', 'provinces': ['ON']}]}))
            (root / 'package-lock.json').write_text(json.dumps({'packages': {
                'node_modules/playwright-core': {'version': '1.61.1'}}}))
            installed = root / 'node_modules/playwright-core'
            installed.mkdir(parents=True)
            (installed / 'package.json').write_text('{"version":"1.61.1"}')
            (installed / 'browsers.json').write_text(json.dumps({'browsers': [
                {'name': 'chromium', 'browserVersion': '154.0.0.1'}]}))
            report = root / 'browser.xml'
            for version in ('', '148.0.0.1', '154.0.0.1,148.0.0.1', '154.0.0.1'):
                report.write_text('<testsuites><testsuite><properties><property name="browserVersion" value="' +
                                  version + '"/></properties><testcase name="required"/></testsuite></testsuites>')
                check = {'selectedProfile': 'ON', 'requireLockedChromium': True}
                if version == '154.0.0.1':
                    self.assertTrue(gate.browser_results(report, check, root)['ready'])
                else:
                    with self.assertRaisesRegex(EvidenceError, 'Chromium'):
                        gate.browser_results(report, check, root)


class PythonPackageDiscoveryTest(unittest.TestCase):
    def test_top_level_runs_package_initialization_and_retains_real_cases(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            package = root / 'example'
            package.mkdir()
            (package / '__init__.py').write_text('import builtins\nbuiltins.release_package_initialized=True\n')
            (package / 'test_required.py').write_text(
                'import unittest,builtins\nclass Required(unittest.TestCase):\n'
                ' def test_initialized(self): self.assertTrue(builtins.release_package_initialized)\n')
            output = root / 'python.json'
            command = [sys.executable, str(gate.ROOT / 'scripts/ci/run-python-release-tests.py'),
                       '--start', str(package), '--top-level', str(root), '--output', str(output)]
            child = subprocess.run(command, capture_output=True)
            self.assertEqual(child.returncode, 0, child.stderr)
            result = json.loads(output.read_text())
            self.assertEqual(result['tests'], 1)
            self.assertEqual(result['cases'][0]['id'], 'example.test_required.Required.test_initialized')
            self.assertTrue(result['ready'])
