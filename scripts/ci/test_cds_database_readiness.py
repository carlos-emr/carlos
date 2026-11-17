# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""An initialization-only socket must never qualify a disposable database."""
from types import SimpleNamespace
import unittest
import subprocess
import ast
import io
from pathlib import Path
from unittest.mock import Mock

from cds_database_readiness import database_client, wait_for_database


class OwnedDatabaseReadinessTest(unittest.TestCase):
    def test_socket_success_during_initialization_cannot_end_the_wait(self):
        client = database_client(['docker', '--host=unix:///private/docker.sock'],
                                 'owned-database', '/private/client config.cnf')
        attempts, sleeps = [], []

        def execute(argv, *, check, timeout=None):
            self.assertFalse(check)
            attempts.append(argv)
            # A socket query would succeed throughout initialization, but TCP
            # becomes available only after the final daemon takes over.
            status = 0 if '--protocol=TCP' not in argv else int(len(attempts) < 3)
            return SimpleNamespace(returncode=status)

        wait_for_database(client, execute, monotonic=lambda: 0, sleep=sleeps.append)
        self.assertEqual(len(attempts), 3)
        self.assertEqual(sleeps, [1, 1])
        self.assertTrue(all('--host=127.0.0.1' in argv for argv in attempts))
        self.assertTrue(all('--defaults-extra-file=/private/client config.cnf' in argv for argv in attempts))

    def test_final_listener_timeout_remains_a_failure(self):
        client = database_client(['docker'], 'owned-database', '/private/client.cnf')
        clock = [0]
        calls = []
        def execute(argv, *, check, timeout=None):
            calls.append(argv)
            clock[0] += 46
            return SimpleNamespace(returncode=1)
        with self.assertRaisesRegex(RuntimeError, 'startup timeout'):
            wait_for_database(client, execute, monotonic=lambda: clock[0], sleep=lambda _: None)
        self.assertEqual(len(calls), 2)

    def test_socket_only_caller_is_rejected_before_any_probe(self):
        def execute(*args, **kwargs):
            self.fail('Socket-only readiness must fail before querying the temporary server')
        with self.assertRaisesRegex(RuntimeError, 'final TCP listener'):
            wait_for_database(['docker', 'exec', 'owned-database', 'mariadb'], execute)

    def test_probe_execution_errors_are_not_reported_as_readiness(self):
        client = database_client(['docker'], 'owned-database', '/private/client.cnf')
        def execute(*args, **kwargs):
            raise OSError('FAKE Docker transport failure')
        with self.assertRaisesRegex(OSError, 'FAKE Docker transport failure'):
            wait_for_database(client, execute)

    def test_stalled_probe_is_bounded_by_the_remaining_startup_budget(self):
        client = database_client(['docker'], 'owned-database', '/private/client.cnf')
        clock = iter([0, 87])
        calls = []
        def execute(argv, *, check, timeout=None):
            calls.append((argv, timeout))
            self.assertIsNotNone(timeout, "A stalled probe has no deadline")
            raise subprocess.TimeoutExpired(argv, timeout)
        with self.assertRaisesRegex(RuntimeError, 'startup timeout') as caught:
            wait_for_database(client, execute, monotonic=lambda: next(clock), sleep=lambda _: None)
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0][1], 3)
        self.assertIsInstance(caught.exception.__cause__, subprocess.TimeoutExpired)

    def test_no_probe_starts_after_the_deadline(self):
        client = database_client(['docker'], 'owned-database', '/private/client.cnf')
        clock = iter([0, 90])
        def execute(*args, **kwargs):
            self.fail('No probe may start with an exhausted startup budget')
        with self.assertRaisesRegex(RuntimeError, 'startup timeout'):
            wait_for_database(client, execute, monotonic=lambda: next(clock))

    def test_late_probe_success_does_not_qualify_startup(self):
        client = database_client(['docker'], 'owned-database', '/private/client.cnf')
        clock = iter([0, 89, 91])
        def execute(argv, *, check, timeout=None):
            self.assertEqual(timeout, 1)
            return SimpleNamespace(returncode=0)
        with self.assertRaisesRegex(RuntimeError, 'startup timeout'):
            wait_for_database(client, execute, monotonic=lambda: next(clock))

    def test_both_ci_adapters_forward_the_probe_timeout_to_the_subprocess(self):
        for filename, function in (('run-cds-performance-ci.py', 'execute'),
                                   ('run-cds-export-installed-ci.py', 'execute'),
                                   ('run-cds-native-tier.py', 'run')):
            with self.subTest(adapter=filename):
                tree = ast.parse(Path(__file__).with_name(filename).read_text())
                execute = next(node for node in ast.walk(tree)
                               if isinstance(node, ast.FunctionDef) and node.name == function)
                process = Mock(return_value=SimpleNamespace(returncode=0))
                namespace = {'subprocess': SimpleNamespace(run=process, PIPE=-1), 'log': io.StringIO()}
                exec(compile(ast.Module(body=[execute], type_ignores=[]), filename, 'exec'), namespace)
                args = [['docker', 'exec', 'owned-database', 'mariadb']]
                if function == 'run':
                    args.append(namespace['log'])
                namespace[function](*args, check=False, timeout=3)
                self.assertEqual(process.call_args.kwargs['timeout'], 3)
