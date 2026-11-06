# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
from pathlib import Path
import subprocess
import sys
import unittest
from junit_evidence import EvidenceError

SPEC = importlib.util.spec_from_file_location('browser_oracle', Path(__file__).with_name('run-browser-process-oracle.py'))
oracle = importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(oracle)


class BrowserProcessOracleTest(unittest.TestCase):
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
