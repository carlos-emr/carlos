# SPDX-License-Identifier: AGPL-3.0-only
"""Behavioral provisioning tests; no host services or databases touched."""
import contextlib
from pathlib import Path
import subprocess
import sys
import tempfile
import types
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'debian/assets'))
from carlos_ctl import provision as p

class RecoveryFailures(unittest.TestCase):
    def setUp(self):
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        self.tmp = self.stack.enter_context(tempfile.TemporaryDirectory())
        self.marker = Path(self.tmp, 'incomplete')
        self.marker.write_text('reason=test failure\nreset_admin=false\ndemo_data=false\n')
        props = Path(self.tmp, 'properties')
        props.touch()
        for name, value in [('MARKER', str(self.marker)), ('SEED_SENTINEL', str(Path(self.tmp, 'seed'))), ('PROPERTIES', str(props)), ('LOCK', str(Path(self.tmp, 'lock')))]:
            self.stack.enter_context(patch.object(p, name, value))
        # The repair holds its lock for the life of the process; in-process tests
        # re-acquire it, so release each one rather than leaking descriptors.
        self.addCleanup(self._release_lock)
        self.mocks = {}
        for owner, name, value in [(p, 'need_root', None), (p, '_wait_for_db', True),
                (p.config, 'load', types.SimpleNamespace(db_name='test', bind_ip='127.0.0.1')),
                (p.config, 'cmd_init_config', 0),
                (p.config, 'apply_nginx', 0),
                (p.dbops, 'cmd_db_apply_settings', 0), (p.dbops, 'cmd_db_users', 0),
                (p, '_table_count', 431), (p.dbops, 'run_flyway', 0),
                (p.dbops, 'cmd_db_migrate', 0), (p.dbops, 'cmd_bootstrap_admin', 0),
                (p.dbops, 'cmd_demo_data', 0), (p, '_report_drugref_seed', None),
                (p, '_o19_import_running', None),
                (p.util, 'reset_emr_start_limit', None),
                (p, 'run', subprocess.CompletedProcess([], 0, '', ''))]:
            self.mocks[name] = self.stack.enter_context(patch.object(owner, name, return_value=value))

    def _release_lock(self):
        if p._LOCK_HANDLE is not None:
            p._LOCK_HANDLE.close()
            p._LOCK_HANDLE = None

    def run_recovery(self, argv=None):
        # Each call stands in for a separate process, which would hold its own
        # provisioning lock and drop it on exit. Release the previous call's so
        # a test that invokes the command twice does not contend with itself —
        # the external hold in the concurrency tests is a different descriptor
        # and is deliberately left in place.
        self._release_lock()
        try:
            return p.cmd_finish_install(argv or [])
        except SystemExit as e:
            return e.code

    def test_success_clears_marker(self):
        self.assertEqual(self.run_recovery(), 0)
        self.assertFalse(self.marker.exists())

    def test_front_door_recovery_precedes_completion(self):
        def apply(bind_ip, **kwargs):
            self.assertTrue(self.marker.exists())
            self.assertEqual(bind_ip, '127.0.0.1')
            self.assertEqual(kwargs, {'start_if_inactive': True})
            return 0
        self.mocks['apply_nginx'].side_effect = apply
        self.assertEqual(self.run_recovery(), 0)
        self.mocks['apply_nginx'].assert_called_once()
        self.assertFalse(self.marker.exists())

    def test_failed_front_door_recovery_keeps_marker_and_does_not_start_emr(self):
        for failure in (1, SystemExit(1)):
            with self.subTest(failure=failure):
                self.mocks['apply_nginx'].return_value = 1
                self.mocks['apply_nginx'].side_effect = failure if isinstance(failure, SystemExit) else None
                self.assertNotEqual(self.run_recovery(), 0)
                self.assertTrue(self.marker.exists())
                self.mocks['run'].assert_not_called()

    def test_requested_demo_failure_must_not_be_success(self):
        self.marker.write_text('reset_admin=false\ndemo_data=true\n')
        self.mocks['cmd_demo_data'].side_effect = SystemExit(1)
        rc = self.run_recovery()
        self.assertTrue(rc != 0 and self.marker.exists(), f'rc={rc}, marker={self.marker.exists()}')

    def test_settings_failure_must_not_be_success(self):
        self.mocks['cmd_db_apply_settings'].return_value = 1
        rc = self.run_recovery()
        self.assertTrue(rc != 0 and self.marker.exists(), f'rc={rc}, marker={self.marker.exists()}')

    def test_manual_migration_failure_must_not_start_application(self):
        self.mocks['run_flyway'].return_value = 1
        self.mocks['cmd_db_migrate'].return_value = 1
        rc = self.run_recovery()
        self.assertTrue(rc != 0 and self.marker.exists(), f'rc={rc}, marker={self.marker.exists()}, service_calls={self.mocks["run"].call_args_list}')

    def test_boot_refuses_migration_of_populated_invalid_schema(self):
        self.mocks['run_flyway'].return_value = 1
        self.assertNotEqual(self.run_recovery(['--boot']), 0)
        self.mocks['cmd_db_migrate'].assert_not_called()
        self.assertTrue(self.marker.exists())

    def test_marker_delete_error_is_not_hidden(self):
        with patch.object(p.os, 'unlink', side_effect=PermissionError('injected read-only marker')):
            with self.assertRaises(PermissionError):
                p.clear()

    def test_fail_closed_checks_service_stop_failure(self):
        self.mocks['run'].return_value = subprocess.CompletedProcess([], 1, '', 'injected stop failure')
        with self.assertRaises((SystemExit, RuntimeError, OSError)):
            p._fail_closed()

    def test_partial_drugref_seed_is_reported(self):
        # The actual function was replaced in setUp; use its saved implementation.
        with patch.object(p.util, 'DRUGREF_PROPERTIES', p.PROPERTIES), patch.object(p.dbops, 'db_root', return_value=subprocess.CompletedProcess([], 0, '17\t0\n', '')), patch.object(p, 'warn') as warn:
            original_drugref_report()
            self.assertTrue(warn.called, '17 tables without a checked completion marker were treated as seeded')

    def test_boot_without_marker_does_no_work(self):
        self.marker.unlink()
        self.assertEqual(self.run_recovery(['--boot']), 0)
        self.mocks['_wait_for_db'].assert_not_called()
        self.mocks['cmd_bootstrap_admin'].assert_not_called()
        self.mocks['run'].assert_not_called()

    def test_old_package_without_marker_records_failed_manual_repair(self):
        self.marker.unlink()
        self.mocks['_wait_for_db'].return_value = False
        with patch.object(p.util, 'which', return_value=None):
            self.assertNotEqual(self.run_recovery(), 0)
        self.assertIn('reset_admin=true', self.marker.read_text())
        self.assertIn('demo_data=false', self.marker.read_text())

    def test_required_verbs_handle_return_codes_and_system_exit(self):
        for verb in ['cmd_init_config', 'cmd_db_apply_settings', 'cmd_db_users', 'cmd_demo_data']:
            for failure in [1, SystemExit(1)]:
                with self.subTest(verb=verb, failure=failure):
                    self.marker.write_text('reset_admin=false\ndemo_data=true\n')
                    mock = self.mocks[verb]
                    mock.return_value = 1 if failure == 1 else None
                    mock.side_effect = failure if isinstance(failure, SystemExit) else None
                    self.mocks['run'].reset_mock()
                    self.assertNotEqual(self.run_recovery(), 0)
                    self.assertTrue(self.marker.exists())
                    self.mocks['run'].assert_not_called()
                    mock.side_effect = None
                    mock.return_value = 0

    def test_fresh_schema_migration_failure_preserves_marker(self):
        self.mocks['_table_count'].return_value = 0
        self.mocks['run_flyway'].return_value = 1
        self.assertNotEqual(self.run_recovery(), 0)
        self.assertTrue(self.marker.exists())
        self.mocks['cmd_bootstrap_admin'].assert_not_called()
        self.mocks['run'].assert_not_called()

    def test_could_not_count_is_not_an_empty_schema(self):
        self.mocks['_table_count'].return_value = None
        self.assertNotEqual(self.run_recovery(), 0)
        self.assertTrue(self.marker.exists())
        self.mocks['run_flyway'].assert_not_called()

    def systemctl(self, argv, **kwargs):
        output = {'is-active': 'inactive', 'is-enabled': 'disabled'}.get(argv[1], '')
        return subprocess.CompletedProcess(argv, 0, output, '')

    def test_bootstrap_failure_checks_containment_and_preserves_marker(self):
        for failure in [1, SystemExit(1)]:
            with self.subTest(failure=failure):
                self.marker.write_text('reset_admin=true\ndemo_data=false\n')
                mock = self.mocks['cmd_bootstrap_admin']
                mock.return_value = 1
                mock.side_effect = failure if isinstance(failure, SystemExit) else None
                self.mocks['run'].side_effect = self.systemctl
                self.assertNotEqual(self.run_recovery(['--boot']), 0)
                self.assertTrue(self.marker.exists())
                self.assertTrue(Path(p.SEED_SENTINEL).exists())
                calls = [call.args[0] for call in self.mocks['run'].call_args_list]
                self.assertIn(['systemctl', 'is-active', 'carlos-emr.service'], calls)
                self.assertIn(['systemctl', 'is-enabled', 'carlos-emr.service'], calls)
                self.assertFalse(any(argv[1] == 'start' for argv in calls))

    def test_still_active_or_enabled_is_not_successful_containment(self):
        for active, enabled in [('active', 'disabled'), ('inactive', 'enabled'), ('unknown', 'unknown')]:
            def systemctl(argv, **kwargs):
                value = {'is-active': active, 'is-enabled': enabled}.get(argv[1], '')
                return subprocess.CompletedProcess(argv, 0, value, '')
            with self.subTest(active=active, enabled=enabled):
                self.mocks['run'].side_effect = systemctl
                with self.assertRaises(SystemExit):
                    p._fail_closed()

    def test_failed_reenable_keeps_both_markers(self):
        self.marker.write_text('reset_admin=true\ndemo_data=false\n')
        Path(p.SEED_SENTINEL).touch()
        self.mocks['run'].side_effect = self.systemctl
        self.assertNotEqual(self.run_recovery(), 0)
        self.assertTrue(self.marker.exists())
        self.assertTrue(Path(p.SEED_SENTINEL).exists())

    def test_boot_recovery_queues_start_for_previously_disabled_service(self):
        self.marker.write_text('reset_admin=true\ndemo_data=false\n')
        Path(p.SEED_SENTINEL).touch()
        def systemctl(argv, **kwargs):
            return subprocess.CompletedProcess(argv, 0, 'enabled' if argv[1] == 'is-enabled' else '', '')
        self.mocks['run'].side_effect = systemctl
        self.assertEqual(self.run_recovery(['--boot']), 0)
        self.assertFalse(self.marker.exists())
        self.assertFalse(Path(p.SEED_SENTINEL).exists())
        self.mocks['run'].assert_any_call(['systemctl', 'start', '--no-block', 'carlos-emr.service'])

    def test_failed_start_restores_marker_and_requested_options(self):
        self.marker.write_text('reset_admin=false\ndemo_data=true\n')
        self.mocks['run'].return_value = subprocess.CompletedProcess([], 1, '', '')
        self.assertNotEqual(self.run_recovery(), 0)
        self.assertIn('demo_data=true', self.marker.read_text())
        self.assertIn('application server did not start', self.marker.read_text())

    def test_drugref_diagnoses_empty_partial_complete_and_query_failure(self):
        for rc, output, message in [(0, '0\t0', 'empty or missing'),
                (0, '17\t0', 'Back it up'), (0, '18\t1', None),
                (1, '', 'could not verify'), (0, 'invalid', 'could not verify')]:
            with self.subTest(rc=rc, output=output), patch.object(p.util, 'DRUGREF_PROPERTIES', p.PROPERTIES), \
                    patch.object(p.dbops, 'db_root', return_value=subprocess.CompletedProcess([], rc, output, '')), \
                    patch.object(p, 'warn') as warning:
                original_drugref_report()
                if message:
                    self.assertIn(message, warning.call_args.args[0])
                else:
                    warning.assert_not_called()

    def test_failed_record_prevents_untracked_manual_provisioning(self):
        self.marker.unlink()
        with patch.object(p.util, 'which', return_value=None), \
                patch.object(p, '_record', side_effect=PermissionError('injected write failure')):
            self.assertNotEqual(self.run_recovery(), 0)
        self.mocks['_wait_for_db'].assert_not_called()
        self.mocks['run'].assert_not_called()

    def test_failed_guard_removal_keeps_recovery_pending(self):
        self.marker.write_text('reset_admin=true\ndemo_data=false\n')
        Path(p.SEED_SENTINEL).touch()
        self.mocks['run'].return_value = subprocess.CompletedProcess([], 0, 'enabled', '')
        with patch.object(p.os, 'unlink', side_effect=PermissionError('injected guard failure')):
            self.assertNotEqual(self.run_recovery(), 0)
        self.assertTrue(self.marker.exists())
        self.assertTrue(Path(p.SEED_SENTINEL).exists())
        self.assertFalse(any(call.args[0][1] == 'start' for call in self.mocks['run'].call_args_list))

    def test_failed_guard_persistence_is_reported_even_if_service_is_stopped(self):
        self.mocks['run'].side_effect = self.systemctl
        with patch('builtins.open', side_effect=PermissionError('injected write failure')):
            with self.assertRaises(SystemExit):
                p._fail_closed()

    def test_unwritable_guard_masks_the_unit_it_cannot_otherwise_block(self):
        # .seed-credential-live is what stops a start (ConditionPathExists in the
        # unit); `disable` alone does not. With /var refusing the sentinel, the
        # mask is the only remaining guard, so it must be taken.
        self.mocks['run'].side_effect = self.systemctl
        with patch('builtins.open', side_effect=PermissionError('injected write failure')):
            with self.assertRaises(SystemExit):
                p._fail_closed()
        calls = [call.args[0] for call in self.mocks['run'].call_args_list]
        self.assertIn(['systemctl', 'mask', 'carlos-emr.service'], calls)

    def test_masked_unit_is_unmasked_once_the_credential_is_replaced(self):
        # The mask has no sentinel to key its removal from, so recovery has to
        # recognize it: `systemctl enable` fails outright on a masked unit, and
        # an EMR left masked would never come back.
        self.marker.write_text('reset_admin=true\ndemo_data=false\n')
        # is-enabled is asked twice: once to read the unit's install state, and
        # once to verify it after `enable` — which must then read 'enabled'.
        states = {'is-enabled': ['masked', 'enabled']}

        def systemctl(argv, **kwargs):
            queue = states.get(argv[1])
            value = queue.pop(0) if queue else ''
            return subprocess.CompletedProcess(argv, 0, value, '')

        self.mocks['run'].side_effect = systemctl
        self.assertEqual(self.run_recovery(), 0)
        calls = [call.args[0] for call in self.mocks['run'].call_args_list]
        self.assertIn(['systemctl', 'unmask', 'carlos-emr.service'], calls)
        self.assertIn(['systemctl', 'enable', 'carlos-emr.service'], calls)
        self.assertFalse(self.marker.exists())

    def test_import_in_progress_refuses_to_provision_and_keeps_the_marker(self):
        # carlos-ctl import-o19 owns the clinical database while it copies. A
        # migration, a grants rewrite or a MariaDB restart under it is
        # unrecoverable, so this verb must refuse before touching any of them.
        self.mocks['_o19_import_running'].return_value = 'an OSCAR 19 import is in progress'
        self.assertNotEqual(self.run_recovery(), 0)
        self.assertTrue(self.marker.exists())
        self.mocks['_wait_for_db'].assert_not_called()
        self.mocks['cmd_db_apply_settings'].assert_not_called()
        self.mocks['cmd_db_users'].assert_not_called()
        self.mocks['run_flyway'].assert_not_called()
        self.mocks['run'].assert_not_called()

    def test_import_in_progress_leaves_boot_recovery_pending_without_failing(self):
        # At boot this is not a failure of the boot: the import is resumed by an
        # operator. The marker has to survive so the boot after it completes
        # finishes the install.
        self.mocks['_o19_import_running'].return_value = 'an OSCAR 19 import is in progress'
        self.assertEqual(self.run_recovery(['--boot']), 0)
        self.assertTrue(self.marker.exists())
        self.mocks['_wait_for_db'].assert_not_called()
        self.mocks['run'].assert_not_called()

    def test_missing_import_guard_fails_closed_rather_than_assuming_no_import(self):
        # A guard that is not installed is a broken unpack, not an absent
        # import — the same posture carlos-emr.postinst takes. Present but not
        # executable is the same thing, and must not raise out of the predicate.
        for access, side_effect in [(False, None), (True, PermissionError('injected'))]:
            with self.subTest(access=access):
                with patch.object(p.os, 'access', return_value=access), \
                        patch.object(p, 'run', side_effect=side_effect) as runner:
                    if side_effect is None:
                        runner.return_value = subprocess.CompletedProcess([], 0, '', '')
                    reason = original_o19_check()
                self.assertIsNotNone(reason)
                self.assertIn('carlos-emr-o19-guard', reason)

    def test_disabled_unit_is_re_enabled_even_with_no_sentinel_to_notice(self):
        # The postinst writes the sentinel best-effort: a failed write with a
        # successful disable leaves the unit down with nothing to key recovery
        # from. Clearing the marker there would strand the EMR permanently.
        self.marker.write_text('reset_admin=true\ndemo_data=false\n')
        states = {'is-enabled': ['disabled', 'enabled']}

        def systemctl(argv, **kwargs):
            queue = states.get(argv[1])
            value = queue.pop(0) if queue else ''
            return subprocess.CompletedProcess(argv, 0, value, '')

        self.mocks['run'].side_effect = systemctl
        self.assertEqual(self.run_recovery(['--boot']), 0)
        calls = [call.args[0] for call in self.mocks['run'].call_args_list]
        self.assertIn(['systemctl', 'enable', 'carlos-emr.service'], calls)
        self.assertIn(['systemctl', 'start', '--no-block', 'carlos-emr.service'], calls)
        self.assertFalse(self.marker.exists())

    def test_boot_start_resets_the_limiter_the_broken_window_may_have_burned(self):
        self.marker.write_text('reset_admin=true\ndemo_data=false\n')
        Path(p.SEED_SENTINEL).touch()
        self.mocks['run'].side_effect = lambda argv, **kw: subprocess.CompletedProcess(
            argv, 0, 'enabled' if argv[1] == 'is-enabled' else '', '')
        self.assertEqual(self.run_recovery(['--boot']), 0)
        self.mocks['reset_emr_start_limit'].assert_called()

    def _hold_lock(self):
        import fcntl
        held = open(p.LOCK, 'w')
        self.addCleanup(held.close)
        fcntl.flock(held, fcntl.LOCK_EX | fcntl.LOCK_NB)

    def test_concurrent_repair_is_refused_rather_than_racing_bootstrap_admin(self):
        self._hold_lock()
        self.assertNotEqual(self.run_recovery(), 0)
        # Refused before any provisioning: the other run owns all of it.
        self.mocks['_wait_for_db'].assert_not_called()
        self.mocks['cmd_bootstrap_admin'].assert_not_called()
        self.mocks['run'].assert_not_called()
        self.assertTrue(self.marker.exists())

    def test_boot_yields_the_lock_to_the_run_that_holds_it(self):
        # carlos-emr.postinst takes this same lock with flock(1), so an
        # unattended upgrade at boot can legitimately hold it. That is not a
        # failure of the boot: the other run is doing the work, and failing the
        # unit would only put a red unit on a host being provisioned correctly.
        self._hold_lock()
        self.assertEqual(self.run_recovery(['--boot']), 0)
        self.mocks['_wait_for_db'].assert_not_called()
        self.mocks['cmd_bootstrap_admin'].assert_not_called()
        self.mocks['run'].assert_not_called()
        # And the marker survives, so whichever run finishes clears it.
        self.assertTrue(self.marker.exists())

# Saved before setUp patches them; the tests that exercise the real functions
# (rather than the command that calls them) use these.
original_drugref_report = p._report_drugref_seed
original_o19_check = p._o19_import_running
if __name__ == '__main__':
    unittest.main(verbosity=2)
