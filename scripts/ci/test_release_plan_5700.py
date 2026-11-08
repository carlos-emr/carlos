# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import re
import tempfile
import unittest
import fnmatch

import release_verification as gate
from junit_evidence import EvidenceError, display_literal


class RequiredExecutionsTest(unittest.TestCase):
    def test_native_selections_and_family_floors_cover_authoritative_schema_contract(self):
        spec = importlib.util.spec_from_file_location('native_contract',
            Path(__file__).with_name('verify-mariadb-dao-reports.py'))
        contract = importlib.util.module_from_spec(spec); spec.loader.exec_module(contract)
        checks = {check['id']: check for check in gate.load_plan()['checks']}
        for mode, check_id in (('generated', 'java-native-generated'),
                              ('flyway', 'java-native-flyway'), ('bc-flyway', 'java-native-bc-flyway')):
            check = checks[check_id]
            selectors = next(value[len('-Dtest='):] for value in check['command']
                             if value.startswith('-Dtest=')).split(',')
            for family, count in contract.REQUIRED[mode].items():
                with self.subTest(mode=mode, family=family):
                    self.assertGreaterEqual(check['requiredFamilies'].get(family, 0), count)
                    self.assertTrue(any(fnmatch.fnmatchcase(family, selector) for selector in selectors),
                                    'Required native family omitted from Maven selection: ' + family)

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

    def test_browser_smoke_routes_match_the_lane_selection_and_test_sources(self):
        # The smoke suites skip in java-all and must pass in java-browser-pdf. A renamed class
        # @DisplayName (Surefire reports it as the case container), an unselected class or an
        # unrouted @Test leaves java-all with an unrouted skip or the lane with a
        # ROUTED_CASE_NOT_EXECUTED gap, and neither shows until release evidence is gathered.
        checks = {check['id']: check for check in gate.load_plan()['checks']}
        lane = checks['java-browser-pdf']
        selectors = next(value[len('-Dtest='):] for value in lane['command']
                         if value.startswith('-Dtest=')).split(',')
        routes = {name: route for name, route in checks['java-all']['routedSkips'].items()
                  if route['check'] == 'java-browser-pdf'}
        self.assertEqual({name.rsplit('.', 1)[-1] for name in routes}, set(selectors))
        self.assertGreaterEqual(lane['minimumCases'], sum(len(route['cases']) for route in routes.values()))
        # The suites read these themselves (SmokeDriverService); no Selenium system property remains.
        self.assertEqual(set(lane['requiresEnvironment']), {'CARLOS_SMOKE_CHROMIUM', 'CARLOS_SMOKE_CHROMEDRIVER'})
        self.assertFalse(any('webdriver.chrome.driver' in value for value in lane['command']))
        quoted = r'"(?:[^"\\]|\\.)*"'
        annotation = r'@[\w.]+(?:\(\s*' + quoted + r'\s*\))?'
        for name, route in routes.items():
            with self.subTest(suite=name):
                simple = name.rsplit('.', 1)[-1]
                path = (gate.ROOT / 'src/test/java' / Path(*name.split('.'))).with_suffix('.java')
                source = path.read_text(encoding='utf-8')
                header = re.search(r'((?:' + annotation + r'\s*)*)(?:public\s+|final\s+)*class\s+'
                                   + re.escape(simple) + r'\b', source)
                self.assertIsNotNone(header, 'Routed suite class declaration not found')
                labels = re.findall(r'@DisplayName\s*\(\s*(' + quoted + r')\s*\)', header[1])
                container = display_literal(labels[0]) if labels else ''
                methods = re.findall(r'@Test\b(?:\s*' + annotation + r')*\s*void\s+(\w+)\s*\(', source)
                self.assertTrue(methods)
                self.assertEqual(sorted(case['name'] for case in route['cases']), sorted(methods))
                self.assertEqual({case.get('container', '') for case in route['cases']}, {container})

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
            for version in ('', '148.0.0.1', '154.0.0.1,148.0.0.1', 'firefox 154.0.0.1', '154.0.0.1', 'chromium 154.0.0.1'):
                report.write_text('<testsuites><testsuite><properties><property name="browserVersion" value="' +
                                  version + '"/></properties><testcase name="required"/></testsuite></testsuites>')
                check = {'selectedProfile': 'ON', 'requireLockedChromium': True}
                if version in ('154.0.0.1', 'chromium 154.0.0.1'):
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
