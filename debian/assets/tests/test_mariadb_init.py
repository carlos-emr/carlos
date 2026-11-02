# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""A missing mysql schema can be initialized; existing data must be preserved."""

import contextlib
import fcntl
import importlib.machinery
import importlib.util
import io
from pathlib import Path
import shlex
import subprocess
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / "debian/assets/bin/carlos-emr-mariadb-init"
loader = importlib.machinery.SourceFileLoader("mariadb_init", str(SCRIPT))
spec = importlib.util.spec_from_loader(loader.name, loader)
init = importlib.util.module_from_spec(spec)
loader.exec_module(init)


class TestMariaDBInitialization(unittest.TestCase):
    def setUp(self):
        stack = contextlib.ExitStack()
        self.addCleanup(stack.close)
        root = Path(stack.enter_context(tempfile.TemporaryDirectory()))
        self.datadir = root / "custom-datadir"
        self.datadir.mkdir()
        self.state = root / "state"
        self.guard = root / "o19-guard"
        stack.enter_context(patch.object(init, "STATE", self.state))
        stack.enter_context(patch.object(init, "IMPORT_GUARD", str(self.guard)))
        stack.enter_context(patch.object(init.os, "geteuid", return_value=0))
        self.stderr = stack.enter_context(contextlib.redirect_stderr(io.StringIO()))
        self.calls = []
        self.service = "failed"
        self.process_rc = 1
        self.help_rc = 0
        self.init_rc = 0
        self.create_tables = True
        self.init_exception = None
        stack.enter_context(patch.object(init, "run", side_effect=self.run_command))

    def run_command(self, args, timeout=30, env=None):
        self.calls.append(args)
        out, err, rc = "", "", 0
        if args[0] == "systemctl":
            out = self.service + "\n"
        elif args[0] == "pgrep":
            rc = self.process_rc
        elif args[0] == "mariadbd":
            out, rc = f"datadir    {self.datadir}/\n", self.help_rc
        elif args[0] == str(self.guard):
            rc = 1
        elif args[0] == "timeout":
            self.assertEqual(args[:4], ["timeout", "--kill-after=10", "180", "mariadb-install-db"])
            self.assertEqual(timeout, 200)
            self.assertEqual(env["TMPDIR"], "")
            if self.init_exception:
                raise self.init_exception
            rc, err = self.init_rc, "initializer diagnostic\n"
            if self.create_tables:
                (self.datadir / "mysql").mkdir()
                (self.datadir / "mysql/db.frm").touch()
        else:
            self.fail(f"unexpected command: {args}")
        return subprocess.CompletedProcess(args, rc, out, err)

    def initialized(self):
        return [args for args in self.calls if "mariadb-install-db" in args]

    def assert_refused(self):
        self.assertEqual(init.main(), 1)
        self.assertEqual(self.initialized(), [])

    def test_initializes_empty_configured_directory_with_socket_root_and_no_test_db(self):
        self.assertEqual(init.main(), 0)
        args, = self.initialized()
        self.assertIn(f"--datadir={self.datadir}", args)
        self.assertIn("--user=mysql", args)
        self.assertIn("--auth-root-authentication-method=socket", args)
        self.assertIn("--skip-test-db", args)
        self.assertIn("--disable-log-bin", args)
        # A repeated configure must keep the initialized privilege tables.
        self.assertEqual(init.main(), 0)
        self.assertEqual(len(self.initialized()), 1)

    def test_failed_first_start_artifacts_are_kept_byte_for_byte(self):
        files = {name: name.encode() for name in (
            "ibdata1", "ib_logfile0", "ibtmp1", "undo001", "aria_log_control",
            "aria_log.00000001", "ddl_recovery.log", "binlog.000001", "binlog.index",
            "binlog.state", "binlog.000001.idx", "debian-11.8.flag")}
        for name, body in files.items():
            (self.datadir / name).write_bytes(body)
        (self.datadir / "lost+found").mkdir()
        (self.datadir / "#binlog_cache_files").mkdir()
        self.assertEqual(init.main(), 0)
        for name, body in files.items():
            self.assertEqual((self.datadir / name).read_bytes(), body)

    def test_existing_schema_refuses_initialization_even_when_mysql_is_missing(self):
        schema = self.datadir / "carlos"
        schema.mkdir()
        clinical = schema / "demographic.ibd"
        clinical.write_bytes(b"synthetic patient fixture")
        self.assert_refused()
        self.assertEqual(clinical.read_bytes(), b"synthetic patient fixture")
        self.assertIn("restore the system schema", self.stderr.getvalue())

    def test_partial_mysql_schema_is_not_reinitialized(self):
        (self.datadir / "mysql").mkdir()
        (self.datadir / "mysql/global_priv.frm").touch()
        self.assert_refused()
        self.assertIn("incomplete", self.stderr.getvalue())

    def test_version_markers_unknown_files_and_nonempty_lost_found_are_preserved(self):
        for name in ("mariadb_upgrade_info", "mysql_upgrade_info", "auto.cnf", "unknown.ibd"):
            with self.subTest(name=name):
                marker = self.datadir / name
                marker.write_text("existing database")
                self.assert_refused()
                self.assertEqual(marker.read_text(), "existing database")
                marker.unlink()
        (self.datadir / "lost+found").mkdir()
        (self.datadir / "lost+found/recovered-data").touch()
        self.assert_refused()

    def test_symlinks_in_the_directory_are_never_initialized(self):
        (self.datadir / "ibdata1").symlink_to(self.datadir / "missing-target")
        self.assert_refused()
        self.assertTrue((self.datadir / "ibdata1").is_symlink())

    def test_directory_with_whitespace_is_refused_before_vendor_initializer(self):
        self.datadir = self.datadir.rename(self.datadir.with_name("custom data directory"))
        self.assert_refused()
        self.assertIn("whitespace", self.stderr.getvalue())

    def test_running_or_starting_service_is_left_alone(self):
        for state in ("active", "activating", "deactivating", "unknown", ""):
            with self.subTest(state=state):
                self.calls.clear()
                self.service = state
                self.assertEqual(init.main(), 0)
                self.assertEqual(self.calls, [["systemctl", "is-active", "mariadb.service"]])

    def test_running_manual_server_or_failed_process_check_refuses(self):
        for rc in (0, 2):
            with self.subTest(rc=rc):
                self.process_rc = rc
                self.assert_refused()

    def test_import_or_provisioning_lock_refuses_before_probing_database(self):
        self.guard.touch()
        self.assert_refused()
        self.assertEqual(self.calls, [[str(self.guard)]])
        self.guard.unlink()
        self.calls.clear()
        with (self.state / ".finish-install.lock").open("a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            self.assert_refused()
            self.assertEqual(self.calls, [])

    def test_missing_directory_or_invalid_server_configuration_is_not_guessed(self):
        self.datadir.rmdir()
        self.assert_refused()
        self.assertFalse(self.datadir.exists())
        self.datadir.mkdir()
        self.help_rc = 1
        self.assert_refused()

    def test_failed_initializer_timeout_and_missing_executable_are_reported(self):
        self.create_tables = False
        self.init_rc = 1
        self.assertEqual(init.main(), 1)
        self.assertIn("initializer diagnostic", self.stderr.getvalue())
        self.init_exception = subprocess.TimeoutExpired("mariadb-install-db", 180)
        self.assertEqual(init.main(), 1)
        self.init_exception = FileNotFoundError("initializer missing")
        self.assertEqual(init.main(), 1)
        self.assertIn("No files were removed", self.stderr.getvalue())

    def test_initializer_success_without_privilege_tables_is_failure(self):
        self.create_tables = False
        self.assertEqual(init.main(), 1)

    def test_non_root_cannot_probe_or_initialize(self):
        with patch.object(init.os, "geteuid", return_value=1000):
            self.assertEqual(init.main(), 1)
        self.assertEqual(self.calls, [])
        self.assertFalse(self.state.exists())

    def test_postinst_starts_again_only_after_a_failed_service_was_prepared(self):
        text = (ROOT / "debian/carlos-emr.postinst").read_text()
        start = text.index('            if [ "$(systemctl is-active mariadb.service')
        block = text[start:text.index('            deadline=', start)]
        helper = self.datadir.parent / "helper"
        log = self.datadir.parent / "calls"
        helper.write_text('#!/bin/sh\nprintf "initialize\\n" >> "$REPAIR_LOG"\nexit "$REPAIR_RC"\n')
        helper.chmod(0o755)
        block = block.replace('/usr/lib/carlos-emr/carlos-emr-mariadb-init', shlex.quote(str(helper)))
        for state, rc, expected in (
            ("failed", 0, ["initialize", "reset-failed mariadb.service", "start mariadb.service"]),
            ("failed", 1, ["initialize"]), ("active", 0, []), ("inactive", 0, []),
        ):
            with self.subTest(state=state, helper_rc=rc):
                log.write_text("")
                script = f'''set -e
export REPAIR_LOG={shlex.quote(str(log))} REPAIR_RC={rc}
systemctl() {{
    if [ "$1" = is-active ]; then printf '%s\\n' {state}; return 0; fi
    printf '%s\\n' "$*" >> "$REPAIR_LOG"
}}
sd_invoke() {{ printf '%s\\n' "$*" >> "$REPAIR_LOG"; }}
{block}
'''
                result = subprocess.run(["sh", "-c", script], text=True, capture_output=True)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(log.read_text().splitlines(), expected)

    def test_postinst_retries_before_waiting_and_packages_the_helper(self):
        postinst = (ROOT / "debian/carlos-emr.postinst").read_text()
        start = postinst.index('sd_invoke start mariadb.service')
        retry = postinst.index('/usr/lib/carlos-emr/carlos-emr-mariadb-init;', start)
        wait = postinst.index('mariadb_probe_rc=0', start)
        self.assertLess(start, retry)
        self.assertLess(retry, wait)
        self.assertIn('systemctl reset-failed mariadb.service', postinst[retry:wait])
        self.assertIn('install -m 0755 debian/assets/bin/carlos-emr-mariadb-init',
                      (ROOT / "debian/rules").read_text())


if __name__ == "__main__":
    unittest.main()
