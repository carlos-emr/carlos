# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Exercise each real image-layer transaction with failing APT subprocesses."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest


class CdsAptRetryTest(unittest.TestCase):
    def exercise(self, failed_updates, failed_installs, updates, installs, success):
        repository = Path(__file__).resolve().parents[2]
        selections = [('scripts/ci/cds-performance-runtime.Dockerfile', 3),
                      ('.github/workflows/package-smoke.yml', 1),
                      ('scripts/deb-docker-validation.sh', 1)]
        transactions = []
        for filename, expected_count in selections:
            source = (repository / filename).read_text()
            layers = re.findall(r'for attempt in 1 2 3; do.*?\bdone', source, re.S)
            self.assertEqual(len(layers), expected_count, filename)
            transactions.extend((filename, layer, strict)
                                for layer in layers for strict in (False, True))
        for layer, (filename, transaction, strict) in enumerate(transactions):
            with self.subTest(layer=layer), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                binary = root / 'bin'
                binary.mkdir()
                command = binary / 'apt-get'
                command.write_text('''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
path = Path(os.environ['APT_TEST_STATE'])
state = json.loads(path.read_text()) if path.exists() else {'update': 0, 'install': 0, 'calls': []}
kind = 'update' if 'update' in sys.argv else 'install'
state[kind] += 1
state['calls'].append({'kind': kind, 'arguments': sys.argv[1:]})
path.write_text(json.dumps(state))
limit = int(os.environ['APT_TEST_FAILED_UPDATES' if kind == 'update' else 'APT_TEST_FAILED_INSTALLS'])
sys.exit(100 if state[kind] <= limit else 0)
''')
                command.chmod(0o755)
                for name in ('rm', 'sleep'):
                    command = binary / name
                    command.write_text('#!/bin/sh\nexit 0\n')
                    command.chmod(0o755)
                state_path = root / 'state.json'
                environment = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ['PATH'],
                    APT_TEST_STATE=str(state_path), APT_TEST_FAILED_UPDATES=str(failed_updates),
                    APT_TEST_FAILED_INSTALLS=str(failed_installs))
                completed = subprocess.run(['sh', '-ec' if strict else '-c', transaction.replace('\\\n', ' ')],
                    env=environment, capture_output=True, text=True, timeout=10)
                state = json.loads(state_path.read_text())
                self.assertEqual(completed.returncode == 0, success, completed.stderr)
                self.assertEqual(state['update'], updates)
                self.assertEqual(state['install'], installs)
                expected = ['update'] * (updates - installs) + [
                    kind for _ in range(installs) for kind in ('update', 'install')]
                self.assertEqual([call['kind'] for call in state['calls']], expected)
                for call in state['calls']:
                    self.assertIn('Acquire::https::No-Cache=true', call['arguments'])
                    if filename.endswith('.Dockerfile'):
                        self.assertIn('Acquire::https::CaInfo=/run/secrets/ci_ca', call['arguments'])

    def test_successful_first_attempt_does_not_retry(self):
        self.exercise(0, 0, 1, 1, True)

    def test_failed_update_is_refreshed_before_installation(self):
        self.exercise(1, 0, 2, 1, True)

    def test_failed_installation_retries_the_complete_index_and_install_transaction(self):
        self.exercise(0, 1, 2, 2, True)

    def test_persistent_index_failure_refuses_package_installation(self):
        self.exercise(3, 0, 3, 0, False)

    def test_persistent_package_failure_cannot_succeed_after_retries_are_exhausted(self):
        self.exercise(0, 3, 3, 3, False)
