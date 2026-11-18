# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
from pathlib import Path
import subprocess
import sys
import unittest
import tempfile
import json
from unittest.mock import patch
from junit_evidence import EvidenceError

SPEC = importlib.util.spec_from_file_location('browser_oracle', Path(__file__).with_name('run-browser-process-oracle.py'))
oracle = importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(oracle)


class BrowserProcessOracleTest(unittest.TestCase):
    def test_expected_interruptions_write_complete_success_evidence(self):
        class ObservedProcess:
            def __init__(self, command, **_kwargs):
                Path(command[3]).write_text(json.dumps({'node': 10, 'browser': 20, 'version': '149.0.7827.55'}))

            def wait(self, timeout=None):
                return 0

        def interrupt(command, **_kwargs):
            # Exercise main's real observation and exception handling, without
            # requiring Chromium in the evidence-adapter unit-test job.
            oracle.process_group.subprocess.Popen(command).wait()
            reason = 'timed out' if command[4] == 'timeout' else 'interrupted'
            raise oracle.process_group.ProcessGroupFailure(reason, True)

        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'result.json'
            inventory = {10: (1, 'S', 100), 20: (10, 'S', 200)}
            with patch.object(sys, 'argv', ['oracle', '--output', str(output)]), \
                    patch.object(oracle.subprocess, 'Popen', ObservedProcess), \
                    patch.object(oracle.process_group, 'run_process_group', side_effect=interrupt), \
                    patch.object(oracle.os, 'getpgid', side_effect=lambda pid: pid), \
                    patch.object(oracle, 'processes', side_effect=[inventory, {}] * 3), \
                    patch.object(oracle.gate, 'require_locked_chromium'):
                oracle.main()
            result = json.loads(output.read_text())
            self.assertEqual(len(result['assertions']), 3)
            self.assertTrue(all(result['assertions'].values()))
            observations = json.loads((output.parent / result['inputs'][0]['path']).read_text())
            self.assertIsNone(observations['failure'])
            self.assertEqual([row['mode'] for row in observations['observations']],
                             ['timeout', 'SIGTERM', 'SIGINT'])

    def test_partial_oracle_failure_retains_evidence_without_qualifying_missing_modes(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'result.json'
            failure = {'mode': 'SIGTERM', 'type': 'ProcessGroupFailure', 'quiescent': False}
            oracle.write_observations(output, [{'mode': 'timeout'}], failure)
            result = json.loads(output.read_text())
            self.assertEqual(result['assertions'], {'timeout-browser-stopped': True,
                'sigterm-browser-stopped': False, 'sigint-browser-stopped': False})
            path = output.parent / result['inputs'][0]['path']
            self.assertEqual(oracle.gate.digest(path), result['inputs'][0]['sha256'])
            self.assertEqual(json.loads(path.read_text())['failure'], failure)

    def test_surviving_or_reparented_browser_descendants_cannot_qualify(self):
        observed = {'node': 10, 'browser': 20, 'separateGroup': True,
                    'pids': {10: (1, 'S', 100), 20: (10, 'T', 200), 30: (20, 'S', 300)}}
        self.assertEqual(oracle.validate_stopped(observed, {}), 3)
        self.assertEqual(oracle.validate_stopped(observed, {30: (1, 'S', 301)}), 3)
        with self.assertRaisesRegex(EvidenceError, 'survived'):
            oracle.validate_stopped(observed, {30: (1, 'S', 300)})
        with self.assertRaisesRegex(EvidenceError, 'inventory'):
            oracle.validate_stopped({**observed, 'pids': {}}, {})

    def test_optimized_python_cannot_bypass_detached_browser_validation(self):
        program = ('import importlib.util,sys; spec=importlib.util.spec_from_file_location("oracle",sys.argv[1]); '
                   'module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module); '
                   'module.validate_stopped({"separateGroup":False}, {})')
        result = subprocess.run([sys.executable, '-O', '-c', program, str(SPEC.origin)],
                                cwd=Path(__file__).parent, capture_output=True, text=True, timeout=15)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('did not exercise a detached browser', result.stderr)
