# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from junit_evidence import EvidenceError

SPEC = importlib.util.spec_from_file_location('ci_fixtures', Path(__file__).with_name('run-ci-browser-fixtures.py'))
fixtures = importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(fixtures)


class CiBrowserFixturesTest(unittest.TestCase):
    def fixture(self):
        return {'id': 'FAKE-browser-check', 'source': 'scripts/FAKE-browser-check.js',
            'command': ['node', 'scripts/FAKE-browser-check.js'], 'sourceSha256': 'a' * 64,
            'minimumAssertions': 3}

    def record(self):
        return {'schemaVersion': 1, 'source': '/FAKE/source.js', 'sourceBefore': 'a' * 64,
            'sourceAfter': 'a' * 64, 'exitCode': 0, 'assertions': 3,
            'launches': [{'family': 'chromium', 'version': '149.0.7827.55', 'disconnected': True}]}

    def validate(self, row=None, record=None, engines=None):
        return fixtures.validate_proofs(row or self.fixture(), [record or self.record()], engines or [],
            '149.0.7827.55', Path('/FAKE/source.js'))

    def test_ci_command_omission_extra_selection_and_source_substitution_are_refused(self):
        row = self.fixture(); contract = {'schemaVersion': 1, 'fixtures': [row]}
        workflow = '    run: node scripts/FAKE-browser-check.js\n'
        contract['workflowSha256'] = hashlib.sha256(workflow.encode()).hexdigest()
        self.assertEqual(fixtures.validate_contract(contract, workflow), [row])
        for changed in ('', workflow + '    run: node scripts/NEW-browser-check.js\n'):
            with self.assertRaises(EvidenceError): fixtures.validate_contract(contract, changed)
            rebound = copy.deepcopy(contract); rebound['workflowSha256'] = hashlib.sha256(changed.encode()).hexdigest()
            with self.assertRaises(EvidenceError): fixtures.validate_contract(rebound, changed)
        for key, value in (('source', 'scripts/OTHER-browser-check.js'), ('id', '../escape'),
                           ('minimumAssertions', 0), ('minimumAssertions', True), ('requiredCases', ['FAKE'])):
            bad = copy.deepcopy(contract); bad['fixtures'][0][key] = value
            with self.subTest(key=key), self.assertRaises(EvidenceError):
                fixtures.validate_contract(bad, workflow)
        with self.assertRaises(EvidenceError):
            fixtures.validate_contract({'schemaVersion': 1, 'fixtures': [row, row]}, workflow)

    def test_no_browser_wrong_browser_unclosed_browser_or_missing_assertions_cannot_pass(self):
        self.assertEqual(self.validate()['assertions'], 3)
        for key, value in (('assertions', 0), ('assertions', 2), ('assertions', True), ('launches', [])):
            bad = self.record(); bad[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(EvidenceError): self.validate(record=bad)
        for key, value in (('family', 'firefox'), ('version', '154.0.0.1'), ('disconnected', False)):
            bad = self.record(); bad['launches'][0][key] = value
            with self.assertRaises(EvidenceError): self.validate(record=bad)

    def test_successful_exit_cannot_hide_changed_or_unbound_fixture_source(self):
        for key, value in (('source', '/OTHER/source.js'), ('sourceBefore', 'b' * 64),
                           ('sourceAfter', 'b' * 64), ('exitCode', 1), ('exitCode', False)):
            bad = self.record(); bad[key] = value
            with self.subTest(key=key), self.assertRaises(EvidenceError): self.validate(record=bad)
        with self.assertRaises(EvidenceError):
            fixtures.validate_proofs(self.fixture(), [], [], '149.0.7827.55', Path('/FAKE/source.js'))

    def test_missing_replaced_skipped_or_duplicate_node_browser_cases_are_refused(self):
        row = self.fixture(); row['requiredCases'] = ['first', 'second']
        engine = {'schemaVersion': 1, 'summary': {'success': True, 'counts': {'tests': 2, 'passed': 2,
            'failed': 0, 'cancelled': 0, 'skipped': 0, 'todo': 0}}, 'cases': [
                {'name': name, 'file': '/FAKE/source.js', 'outcome': 'PASS'} for name in row['requiredCases']]}
        self.assertEqual(self.validate(row=row, engines=[engine])['nodeCases'], 2)
        with self.assertRaises(EvidenceError): self.validate(row=row)
        for version in (None, True, 2):
            bad = copy.deepcopy(engine); bad['schemaVersion'] = version
            with self.subTest(version=version), self.assertRaises(EvidenceError):
                self.validate(row=row, engines=[bad])
        single = copy.deepcopy(engine)
        single['cases'] = single['cases'][:1]
        single['summary']['counts'].update(tests=1, passed=True)
        with self.assertRaises(EvidenceError):
            self.validate(row={**row, 'requiredCases': ['first']}, engines=[single])
        for truncate_counts in (False, True):
            missing = copy.deepcopy(engine)
            missing['cases'] = missing['cases'][:1]
            if truncate_counts:
                missing['summary']['counts'].update(tests=1, passed=1)
            with self.subTest(truncate_counts=truncate_counts), self.assertRaises(EvidenceError):
                self.validate(row=row, engines=[missing])
        for field in ('failed', 'cancelled', 'skipped', 'todo'):
            bad = copy.deepcopy(engine); bad['summary']['counts'][field] = 1
            with self.assertRaises(EvidenceError): self.validate(row=row, engines=[bad])
        for mutation in ('replacement', 'duplicate', 'foreign-file', 'skip'):
            bad = copy.deepcopy(engine)
            if mutation == 'replacement': bad['cases'][1]['name'] = 'unrelated'
            elif mutation == 'duplicate': bad['cases'][1]['name'] = 'first'
            elif mutation == 'foreign-file': bad['cases'][1]['file'] = '/OTHER/source.js'
            else: bad['cases'][1]['outcome'] = 'SKIP'
            with self.assertRaises(EvidenceError): self.validate(row=row, engines=[bad])

    def test_private_target_output_preserves_source_and_symlink_guards(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            source = root / 'source'; source.mkdir(mode=0o700)
            target = source / 'target' / 'run'; target.mkdir(parents=True, mode=0o700)
            outside = root / 'outside'; outside.mkdir(mode=0o700)
            with patch.object(fixtures.gate, 'ROOT', source):
                for directory in (target, outside):
                    self.assertEqual(fixtures.validate_output(directory / 'result.json'), directory)
                for directory in (source, source / 'target-other'):
                    directory.mkdir(mode=0o700, exist_ok=True)
                    with self.assertRaises(EvidenceError): fixtures.validate_output(directory / 'result.json')
                link = target / 'linked'; link.symlink_to(outside, target_is_directory=True)
                with self.assertRaises(EvidenceError): fixtures.validate_output(link / 'result.json')
                result = target / 'result.json'; result.write_text('existing')
                with self.assertRaises(EvidenceError): fixtures.validate_output(result)
                result.unlink(); result.symlink_to(outside / 'missing')
                with self.assertRaises(EvidenceError): fixtures.validate_output(result)
                outside.chmod(0o755)
                with self.assertRaises(EvidenceError): fixtures.validate_output(outside / 'result.json')

    def test_default_headless_executable_is_bound_and_mutation_is_refused(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            configured = root / 'configured'; configured.write_bytes(b'FAKE configured executable')
            default = root / 'headless'; default.write_bytes(b'FAKE default executable')
            (root / 'browser-selection.json').write_text(json.dumps({'defaultHeadless': str(default)}))
            with patch.object(fixtures, 'run_process_group', return_value=subprocess.CompletedProcess([], 0)):
                identities = fixtures.browser_identities(configured, root)
            self.assertEqual(identities['defaultHeadless']['path'], str(default))
            self.assertNotEqual(identities['configured']['sha256'], identities['defaultHeadless']['sha256'])
            fixtures.verify_browser_identities(identities)
            for path in (default, configured):
                before = path.read_bytes(); path.write_bytes(b'FAKE changed executable')
                with self.assertRaisesRegex(EvidenceError, 'executable changed'):
                    fixtures.verify_browser_identities(identities)
                path.write_bytes(before)

    def test_assertion_observer_preserves_provider_arguments_identity_results_and_failures(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve(); package = root / 'node_modules/playwright'; package.mkdir(parents=True)
            (package / 'index.js').write_text("const E=require('node:events');module.exports={};"
                "for(const family of ['chromium','firefox','webkit'])module.exports[family]={async launch(options){"
                "if(options.marker!=='FAKE')throw Error('arguments changed');"
                "const browser=new E();browser.identity='FAKE-original';browser.version=()=> '149.0.7827.55';"
                "browser.close=async()=>browser.emit('disconnected');return browser;}};")
            for name, assertion, expected_code, expected_assertions in (
                    ('pass', "assert.equal(browser.identity,'FAKE-original');assert.equal(2,2);", 0, 2),
                    ('failure', "assert.equal(1,2);", 1, 1), ('empty', '', 0, 0)):
                source = root / (name + '.js'); proofs = root / (name + '-proofs'); proofs.mkdir()
                source.write_text("const assert=require('node:assert/strict');const {chromium}=require('playwright');"
                    "(async()=>{const browser=await chromium.launch({marker:'FAKE'});try{" + assertion
                    + "}finally{await browser.close();}})().catch(()=>{process.exitCode=1;});")
                result = subprocess.run(['node', '--require', str(Path(__file__).with_name('observe-fixture-browser.cjs')),
                    str(source)], env={**os.environ, 'CARLOS_FIXTURE_ROOT': str(root), 'CARLOS_FIXTURE_SOURCE': str(source),
                    'CARLOS_FIXTURE_PROOFS': str(proofs)}, capture_output=True, text=True, timeout=15)
                self.assertEqual(result.returncode, expected_code)
                records = [json.loads(path.read_text()) for path in proofs.glob('*-browser.json')]
                self.assertEqual(len(records), 1); self.assertEqual(records[0]['assertions'], expected_assertions)
                self.assertTrue(records[0]['launches'][0]['disconnected'])
                row = self.fixture(); row.update(sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(), minimumAssertions=2)
                if name == 'pass':
                    self.assertEqual(fixtures.validate_proofs(row, records, [], '149.0.7827.55', source)['assertions'], 2)
                else:
                    with self.assertRaises(EvidenceError):
                        fixtures.validate_proofs(row, records, [], '149.0.7827.55', source)

    def test_pinned_contract_covers_all_current_ci_browser_commands(self):
        root = Path(__file__).resolve().parents[2]
        contract = json.loads((root / 'docs/testing/release-2026.08-ci-browser-fixtures.json').read_text())
        selected = fixtures.validate_contract(contract, (root / '.github/workflows/script-regressions.yml').read_text())
        self.assertEqual(len(selected), 16)
        for row in selected:
            self.assertEqual(hashlib.sha256((root / row['source']).read_bytes()).hexdigest(), row['sourceSha256'])
        plan = fixtures.gate.load_plan()
        for mutation in ('remove', 'external', 'missing-result', 'wrong-runtime'):
            bad = copy.deepcopy(plan)
            check = next(row for row in bad['checks'] if row['id'] == 'fixture-ci-browser')
            if mutation == 'remove': bad['checks'].remove(check)
            elif mutation == 'external': check['kind'] = 'external'
            elif mutation == 'missing-result': check.pop('resultContract')
            else: check['runtimeConstraints']['node'] = '20'
            with tempfile.TemporaryDirectory() as temporary:
                path = Path(temporary) / 'plan.json'; path.write_text(json.dumps(bad))
                with self.subTest(mutation=mutation), self.assertRaisesRegex(EvidenceError, 'CI browser fixture'):
                    fixtures.gate.load_plan(path)
