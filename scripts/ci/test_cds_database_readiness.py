# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""An initialization-only socket must never qualify a disposable database."""
from types import SimpleNamespace
import unittest

from cds_database_readiness import database_client, wait_for_database


class OwnedDatabaseReadinessTest(unittest.TestCase):
    def test_socket_success_during_initialization_cannot_end_the_wait(self):
        client = database_client(['docker', '--host=unix:///private/docker.sock'],
                                 'owned-database', '/private/client config.cnf')
        attempts, sleeps = [], []

        def execute(argv, *, check):
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
        clock = iter([0, 1, 91])
        calls = []
        def execute(argv, *, check):
            calls.append(argv)
            return SimpleNamespace(returncode=1)
        with self.assertRaisesRegex(RuntimeError, 'startup timeout'):
            wait_for_database(client, execute, monotonic=lambda: next(clock), sleep=lambda _: None)
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
