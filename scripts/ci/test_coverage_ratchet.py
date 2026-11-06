# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import release_verification as gate
from junit_evidence import EvidenceError

spec = importlib.util.spec_from_file_location('coverage_ratchet', Path(__file__).with_name('run-coverage-ratchet.py'))
ratchet = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ratchet)


class CoverageCommandTest(unittest.TestCase):
    def test_standalone_checker_runs_without_pythonpath_and_retains_failed_floors(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            def git(*args):
                return subprocess.check_output(['git', '-c', 'user.name=Fixture',
                    '-c', 'user.email=fixture@example.invalid', *args], cwd=root, text=True).strip()
            git('init', '-q')
            git('commit', '--allow-empty', '-qm', 'baseline')
            base = git('rev-parse', 'HEAD')
            source = 'src/main/java/example/Guard.java'
            (root / source).parent.mkdir(parents=True)
            (root / source).write_text('class Guard {}\n')
            git('add', source)
            git('commit', '-qm', 'candidate')
            policy = root / 'floors.json'
            policy.write_text(json.dumps({'schemaVersion': 1, 'base': base,
                'baselineCommit': base, 'files': {source: {
                    'lines': 100, 'instructions': 100, 'branches': 100}}}))
            report = root / 'jacoco.xml'
            with patch.dict(os.environ):
                os.environ.pop('PYTHONPATH', None)
                for covered, missed, expected in ((1, 0, True), (0, 1, False)):
                    with self.subTest(expected=expected):
                        report.write_text('<report><package name="example"><sourcefile name="Guard.java">'
                            f'<line nr="1" ci="{covered}" mi="{missed}" cb="0" mb="0"/>'
                            '</sourcefile></package></report>')
                        output = root / ('result-' + str(expected) + '.json')
                        result = ratchet.check_floors(report, policy, base, output, root)
                        self.assertIs(result['ready'], expected)
                        self.assertEqual(result, json.loads(output.read_text()))
                        if not expected:
                            self.assertEqual({problem['reason'] for problem in result['problems']},
                                             {'lines', 'instructions'})


class CoverageLaneTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.evidence = self.root / 'evidence'
        self.lane = self.evidence / 'fresh-run'
        (self.lane / 'reports').mkdir(parents=True)
        (self.lane / 'coverage').mkdir()
        self.plan = self.root / 'plan.json'
        self.plan.write_text('{}')
        self.check = {'id': 'java-all', 'kind': 'junit', 'command': ['mvn', 'test'],
                      'requireCoverage': True, 'requireClassIdentity': True, 'runtimeConstraints': {'java': '25'}}
        self.source = {'commit': 'a' * 40, 'tree': 'b' * 40, 'clean': True}
        self.identity = patch.object(gate, 'source_identity', return_value=self.source)
        self.identity.start()
        self.addCleanup(self.identity.stop)
        (self.root / 'target/classes/example').mkdir(parents=True)
        (self.root / 'target/classes/example/Guard.class').write_bytes(b'candidate-class')
        (self.lane / 'reports/TEST-example.Required.xml').write_text(
            '<testsuite name="example.Required" tests="1" failures="0" errors="0" skipped="0">'
            '<testcase classname="example.Required" name="shouldRefuse_whenUnauthorized"/></testsuite>')
        (self.lane / 'jacoco.exec').write_bytes(b'synthetic-agent-data')
        (self.lane / 'coverage/jacoco.xml').write_text(
            '<report><package name="example"/><counter type="INSTRUCTION" missed="0" covered="1"/></report>')
        (self.lane / 'classes.json').write_text(json.dumps(gate.production_class_identity(self.root)))
        self.receipt = {'checkId': 'java-all', 'source': dict(self.source), 'planSha256': gate.digest(self.plan),
            'outcome': 'PASS', 'exitCode': 0, 'command': self.check['command'], 'runtimes': {'java': '25.0.3'},
            'runId': 'fresh-run', 'profile': 'all', 'iteration': 1,
            'summary': gate.summarize(self.check, self.lane, 0, self.root),
            'inputs': [{'path': p.relative_to(self.lane).as_posix(), 'sha256': gate.digest(p)}
                       for p in gate.evidence_files(self.check, self.lane)]}
        self.write_receipt()

    def write_receipt(self):
        (self.lane / 'receipt.json').write_text(json.dumps(self.receipt))

    def resolve(self):
        return ratchet.resolve_lane(self.evidence, self.check, self.plan, self.root)

    def test_exact_successful_lane_is_selected(self):
        self.assertEqual(self.resolve(), self.lane)

    def test_missing_ambiguous_and_failed_lanes_are_refused(self):
        for key, value in [('checkId', 'another'), ('outcome', 'FAIL'), ('exitCode', 1),
                           ('cancelled', True), ('iteration', 2), ('runId', 'stale'),
                           ('profile', 'ON'), ('command', ['true']), ('runtimes', {'java': '21'})]:
            with self.subTest(key=key):
                original = dict(self.receipt)
                self.receipt[key] = value
                self.write_receipt()
                with self.assertRaises(EvidenceError): self.resolve()
                self.receipt = original
        self.write_receipt()
        (self.evidence / 'duplicate').mkdir()
        (self.evidence / 'duplicate/receipt.json').write_text(json.dumps(self.receipt))
        with self.assertRaisesRegex(EvidenceError, 'ambiguous'): self.resolve()

    def test_stale_source_plan_and_changed_or_omitted_inputs_are_refused(self):
        original = json.loads(json.dumps(self.receipt))
        self.receipt['source']['tree'] = 'd' * 40
        self.write_receipt()
        with self.assertRaises(EvidenceError): self.resolve()
        self.receipt = original
        self.write_receipt()
        self.plan.write_text('{"changed":true}')
        with self.assertRaises(EvidenceError): self.resolve()
        self.plan.write_text('{}')
        self.receipt['inputs'] = [row for row in original['inputs'] if not row['path'].startswith('reports/')]
        self.write_receipt()
        with self.assertRaisesRegex(EvidenceError, 'omitted'): self.resolve()
        self.receipt['inputs'] = original['inputs']
        self.write_receipt()
        (self.lane / 'jacoco.exec').write_bytes(b'changed')
        with self.assertRaises(EvidenceError): self.resolve()

    def test_dirty_checkout_and_wrong_class_tree_are_refused(self):
        self.source['clean'] = False
        with self.assertRaisesRegex(EvidenceError, 'clean'): self.resolve()
        self.source['clean'] = True
        value = gate.read_json(self.lane / 'classes.json')
        value['sourceTree'] = 'e' * 40
        (self.lane / 'classes.json').write_text(json.dumps(value))
        self.receipt['inputs'] = [{'path': p.relative_to(self.lane).as_posix(), 'sha256': gate.digest(p)}
                                  for p in gate.evidence_files(self.check, self.lane)]
        self.write_receipt()
        with self.assertRaisesRegex(EvidenceError, 'class identity'): self.resolve()

    def test_different_compiled_bytes_cannot_be_merged_even_at_the_same_commit(self):
        (self.root / 'target/classes/example/Guard.class').write_bytes(b'another-compilation')
        with self.assertRaisesRegex(EvidenceError, 'different production classes'): self.resolve()
