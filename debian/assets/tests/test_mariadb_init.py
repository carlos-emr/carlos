# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU Affero General Public License version 3 as
# published by the Free Software Foundation.
#
# This program is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
# FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.
#
# CARLOS EMR Project
# https://github.com/carlos-emr/carlos
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
from types import SimpleNamespace
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
        self.runtime = root / "runtime"
        self.runtime.mkdir()
        self.guard = root / "o19-guard"
        self.guard.write_text("#!/bin/sh\nexit 0\n")
        self.guard.chmod(0o755)
        self.properties = root / "carlos.properties"
        self.skel = root / "skel.properties"
        self.properties.write_bytes(b"# untouched package skeleton\n")
        self.skel.write_bytes(self.properties.read_bytes())
        self.wsrep_files = (root / "wsrep-new-cluster", root / "wsrep-start-position")
        stack.enter_context(patch.object(init, "STATE", self.state))
        stack.enter_context(patch.object(init, "RUNTIME", self.runtime))
        stack.enter_context(patch.object(init, "IMPORT_GUARD", str(self.guard)))
        stack.enter_context(patch.object(init, "PROPERTIES", self.properties))
        stack.enter_context(patch.object(init, "SKEL", self.skel))
        stack.enter_context(patch.object(init, "WSREP_ENV_FILES", frozenset(map(str, self.wsrep_files))))
        self.mysql_account = SimpleNamespace(pw_dir="/nonexistent", pw_uid=997, pw_gid=997, pw_name="mysql")
        stack.enter_context(patch.object(init.system_accounts, "getpwnam", return_value=self.mysql_account))
        stack.enter_context(patch.object(init.os, "geteuid", return_value=0))
        self.stderr = stack.enter_context(contextlib.redirect_stderr(io.StringIO()))
        self.calls = []
        self.invocations = []
        self.service = "failed"
        self.service_properties = {
            "User": "mysql", "Group": "mysql", "ProtectHome": "yes", "SetLoginEnvironment": "yes",
            "ExecStart": "{ path=/usr/sbin/mariadbd ; argv[]=/usr/sbin/mariadbd $MYSQLD_OPTS "
                         "$_WSREP_NEW_CLUSTER $_WSREP_START_POSITION ; ignore_errors=no ; }",
            "ExecStartEx": "{ path=/usr/sbin/mariadbd ; argv[]=/usr/sbin/mariadbd $MYSQLD_OPTS "
                           "$_WSREP_NEW_CLUSTER $_WSREP_START_POSITION ; flags= ; }",
            "Environment": "", "EnvironmentFiles": "", "PassEnvironment": "", "UnsetEnvironment": "",
            "RootDirectory": "", "RootImage": "", "WorkingDirectory": "", "BindPaths": "",
            "BindReadOnlyPaths": "", "TemporaryFileSystem": "", "PAMName": "",
            "PrivateTmp": "no", "PrivateUsers": "no", "DynamicUser": "no", "InaccessiblePaths": "",
            "MountImages": "", "ExtensionImages": "", "ExtensionDirectories": "",
        }
        self.metadata_rc = 0
        self.metadata_stdout = None
        self.manager_environment = ""
        self.manager_rc = 0
        self.guard_rc = 0
        self.process_rc = 1
        self.help_rc = 0
        self.timezone_rc = 0
        self.timezone_sql = "INSERT INTO time_zone_name (Name, Time_zone_id) VALUES ('Etc/UTC', 1);\n"
        self.timezone_exception = None
        self.timezone_files = []
        self.timezone_snapshots = []
        self.init_rc = 0
        self.create_tables = True
        self.init_exception = None
        stack.enter_context(patch.object(init, "run", side_effect=self.run_command))

    def run_command(self, args, timeout=30, env=None, cwd=None):
        self.calls.append(args)
        self.invocations.append((args, env, cwd))
        out, err, rc = "", "", 0
        if args[0] == "systemctl":
            if args[1] == "is-active":
                out = self.service + "\n"
            elif args[1] == "show":
                self.assertEqual(args[2], "mariadb.service")
                self.assertTrue(args[3].startswith("--property="))
                out = ("\n".join(f"{key}={value}" for key, value in self.service_properties.items()) + "\n"
                       if self.metadata_stdout is None else self.metadata_stdout)
                rc = self.metadata_rc
            elif args[1] == "show-environment":
                out, rc = self.manager_environment, self.manager_rc
            else:
                self.fail(f"unexpected systemctl call: {args}")
        elif args[0] == "pgrep":
            rc = self.process_rc
        elif args[0] == "runuser" and args[-3:] == ["mariadbd", "--verbose", "--help"]:
            out, rc = f"datadir    {self.datadir}/\n", self.help_rc
        elif args[0] == "runuser" and args[-4:] == ["mariadb-tzinfo-to-sql", "--no-defaults",
                                                  "--skip-write-binlog", "/usr/share/zoneinfo"]:
            self.assertEqual(timeout, 60)
            self.assertEqual(env, {"PATH": "/usr/sbin:/usr/bin:/sbin:/bin", "LC_ALL": "C"})
            self.assertEqual(cwd, "/")
            if self.timezone_exception:
                raise self.timezone_exception
            out, err, rc = self.timezone_sql, "timezone diagnostic\n", self.timezone_rc
        elif args[0] == str(self.guard):
            rc = self.guard_rc
        elif args[0] == "timeout":
            self.assertEqual(args[:3], ["timeout", "--kill-after=10", "180"])
            self.assertEqual(timeout, 200)
            self.assertEqual(env, {"PATH": "/usr/sbin:/usr/bin:/sbin:/bin", "LC_ALL": "C"})
            self.assertEqual(cwd, "/")
            extra, = [arg.split("=", 1)[1] for arg in args if arg.startswith("--extra-file=")]
            sql = Path(extra)
            self.timezone_files.append(sql)
            self.timezone_snapshots.append((sql, sql.read_text(), sql.stat().st_mode & 0o777))
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

    def timezone_preparations(self):
        return [args for args in self.calls if "mariadb-tzinfo-to-sql" in args]

    def assert_refused(self, argv=None):
        self.assertEqual(init.main(argv), 1)
        self.assertEqual(self.initialized(), [])
        self.assertEqual(self.timezone_preparations(), [])

    def test_initializes_empty_configured_directory_with_socket_root_and_no_test_db(self):
        self.assertEqual(init.main(), 0)
        args, = self.initialized()
        self.assertIn(f"--datadir={self.datadir}", args)
        self.assertIn("--user=mysql", args)
        self.assertIn("--auth-root-authentication-method=socket", args)
        self.assertIn("--skip-test-db", args)
        self.assertIn("--disable-log-bin", args)
        self.assertIn("--default-time-zone=SYSTEM", args)
        # A repeated configure must keep the initialized privilege tables.
        self.assertEqual(init.main(), 2)
        self.assertEqual(len(self.initialized()), 1)
        self.assertEqual(len(self.timezone_preparations()), 1)

    def test_timezone_generation_failure_empty_output_or_timeout_does_not_initialize(self):
        for rc, output, exception in (
            (1, self.timezone_sql, None),
            (0, " \n\t", None),
            (0, "", subprocess.TimeoutExpired("mariadb-tzinfo-to-sql", 60)),
        ):
            with self.subTest(returncode=rc, output=output, exception=exception):
                self.calls.clear()
                self.timezone_rc, self.timezone_sql, self.timezone_exception = rc, output, exception
                self.assertEqual(init.main(), 1)
                self.assertEqual(len(self.timezone_preparations()), 1)
                self.assertEqual(self.initialized(), [])
                self.assertEqual(list(self.datadir.iterdir()), [])
                self.assertEqual(list(self.runtime.iterdir()), [])

    def test_bootstrap_reads_complete_public_timezone_sql_and_tempfile_is_removed(self):
        self.assertEqual(init.main(), 0)
        sql, contents, mode = self.timezone_snapshots[0]
        self.assertEqual(sql.parent, self.runtime)
        self.assertEqual(contents, "USE mysql;\n" + self.timezone_sql)
        self.assertEqual(mode, 0o644)
        self.assertFalse(sql.exists())
        self.assertEqual(list(self.runtime.iterdir()), [])

    def test_timezone_preparation_is_skipped_for_an_already_initialized_database(self):
        (self.datadir / "mysql").mkdir()
        (self.datadir / "mysql/db.frm").touch()
        self.assertEqual(init.main(), 2)
        self.assertEqual(self.timezone_preparations(), [])
        self.assertEqual(self.initialized(), [])
        self.assertEqual(list(self.runtime.iterdir()), [])

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
                self.assertEqual(init.main(), 2)
                self.assertEqual(self.calls, [[str(self.guard)], ["systemctl", "is-active", "mariadb.service"]])

    def test_running_manual_server_or_failed_process_check_refuses(self):
        for rc in (0, 2):
            with self.subTest(rc=rc):
                self.process_rc = rc
                self.assert_refused()

    def test_import_or_provisioning_lock_refuses_before_probing_database(self):
        self.guard_rc = 1
        self.assert_refused()
        self.assertEqual(self.calls, [[str(self.guard)]])
        self.guard_rc = 0
        self.calls.clear()
        with (self.state / ".finish-install.lock").open("a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            self.assert_refused()
            self.assertEqual(self.calls, [])

    def test_missing_or_nonexecutable_import_guard_refuses_initialization(self):
        self.guard.chmod(0o644)
        self.assert_refused()
        self.assertEqual(self.calls, [])
        self.guard.unlink()
        self.assert_refused()
        self.assertEqual(self.calls, [])

    def test_retained_configuration_refuses_a_blank_or_missing_volume(self):
        self.properties.write_bytes(b"# existing site\ndb_username=carlos\n")
        self.assert_refused()
        self.assertFalse((self.datadir / "mysql").exists())
        self.assertIn("volume is mounted", self.stderr.getvalue())

    def test_missing_or_symlinked_site_configuration_is_not_an_untouched_site(self):
        for path in (self.properties, self.skel):
            with self.subTest(path=path.name):
                original = path.read_bytes()
                path.unlink()
                self.assert_refused()
                target = path.with_suffix(".target")
                target.write_bytes(original)
                path.symlink_to(target)
                self.assert_refused()
                path.unlink()
                path.write_bytes(original)

    def test_retained_clinical_document_refuses_even_when_properties_match_skeleton(self):
        documents = self.state / "CarlosDocument" / "carlos"
        documents.mkdir(parents=True)
        clinical = documents / "clinical-fixture.pdf"
        clinical.write_bytes(b"synthetic document")
        self.assert_refused()
        self.assert_refused(["--accept-empty-datadir"])
        self.assertEqual(clinical.read_bytes(), b"synthetic document")

    def test_unreadable_document_scan_always_fails_closed(self):
        documents = self.state / "CarlosDocument"
        documents.mkdir(parents=True)

        def unreadable_walk(top, *, followlinks, onerror):
            self.assertEqual(Path(top), documents)
            self.assertFalse(followlinks)
            self.assertTrue(callable(onerror))
            onerror(PermissionError("retained document directory is unreadable"))
            self.fail("a document-scan error must be propagated")

        with patch.object(init.os, "walk", side_effect=unreadable_walk) as walk:
            self.assert_refused()
            self.assertIn("unreadable", self.stderr.getvalue())
            self.assertFalse((self.datadir / "mysql").exists())
            walk.reset_mock()
            self.assert_refused(["--accept-empty-datadir"])
            walk.assert_called_once()

    def test_empty_document_directories_are_allowed_but_symlinks_are_not(self):
        documents = self.state / "CarlosDocument"
        nested = documents / "carlos" / "empty"
        nested.mkdir(parents=True)
        link = nested / "missing-document"
        link.symlink_to(self.datadir / "missing-target")
        self.assert_refused()
        self.assert_refused(["--accept-empty-datadir"])
        link.unlink()
        self.assertEqual(init.main(), 0)

    def test_symlinked_document_store_is_not_an_untouched_site(self):
        self.state.mkdir()
        target = self.datadir.parent / "empty-documents"
        target.mkdir()
        (self.state / "CarlosDocument").symlink_to(target, target_is_directory=True)
        self.assert_refused()
        self.assert_refused(["--accept-empty-datadir"])

    def test_explicit_acceptance_allows_missing_configuration_without_documents(self):
        self.properties.unlink()
        self.assert_refused()
        self.assertEqual(init.main(["--accept-empty-datadir"]), 0)

    def test_explicit_acceptance_allows_different_configuration_without_documents(self):
        self.properties.write_bytes(b"# existing configured site\ndb_username=carlos\n")
        self.assert_refused()
        self.assertEqual(init.main(["--accept-empty-datadir"]), 0)

    def test_explicit_acceptance_refuses_configured_site_with_retained_documents(self):
        self.properties.write_bytes(b"# existing configured site\ndb_username=carlos\n")
        documents = self.state / "CarlosDocument"
        documents.mkdir(parents=True)
        clinical = documents / "fixture.pdf"
        clinical.write_bytes(b"synthetic retained document")
        self.assert_refused()
        self.assert_refused(["--accept-empty-datadir"])
        self.assertEqual(clinical.read_bytes(), b"synthetic retained document")

    def test_explicit_acceptance_does_not_bypass_existing_data(self):
        (self.datadir / "clinical_fixture").mkdir()
        self.assert_refused(["--accept-empty-datadir"])

    def test_explicit_acceptance_does_not_bypass_import_or_service_guards(self):
        self.guard_rc = 1
        self.assert_refused(["--accept-empty-datadir"])
        self.guard_rc = 0
        self.service_properties["User"] = "root"
        self.assert_refused(["--accept-empty-datadir"])
        self.service_properties["User"] = "mysql"
        self.process_rc = 0
        self.assert_refused(["--accept-empty-datadir"])

    def test_mysql_commands_do_not_use_the_root_callers_option_context(self):
        root_environment = {
            "HOME": "/root", "MYSQL_HOME": "/root", "MARIADB_HOME": "/root",
            "MYSQLD_OPTS": "--datadir=/root/wrong-db", "LD_PRELOAD": "/root/libfake.so",
            "LD_LIBRARY_PATH": "/root/lib", "PATH": "/root/bin", "TMPDIR": "/root/tmp",
        }
        with patch.dict(init.os.environ, root_environment):
            self.assertEqual(init.main(), 0)
        prefix = ["runuser", "-u", "mysql", "-g", "mysql", "--", "env", "-i",
                  "PATH=/usr/sbin:/usr/bin:/sbin:/bin", "HOME=/nonexistent",
                  "USER=mysql", "LOGNAME=mysql", "LC_ALL=C", "TMPDIR="]
        commands = [(args, env, cwd) for args, env, cwd in self.invocations
                    if args[0] in ("runuser", "timeout")]
        self.assertEqual(len(commands), 3)
        probe, timezone, bootstrap = commands
        self.assertEqual(probe[0], prefix + ["mariadbd", "--verbose", "--help"])
        self.assertEqual(timezone[0], prefix + ["mariadb-tzinfo-to-sql", "--no-defaults",
                                              "--skip-write-binlog", "/usr/share/zoneinfo"])
        self.assertEqual(bootstrap[0][3:3 + len(prefix)], prefix)
        for _, env, cwd in commands:
            self.assertEqual(env, {"PATH": "/usr/sbin:/usr/bin:/sbin:/bin", "LC_ALL": "C"})
            self.assertEqual(cwd, "/")

    def test_service_manager_options_cannot_redirect_probe_or_bootstrap(self):
        for variable in ("MYSQLD_OPTS", "MYSQL_HOME", "MARIADB_HOME", "_WSREP_NEW_CLUSTER",
                         "_WSREP_START_POSITION", "MYSQL_GROUP_SUFFIX", "MARIADB_GROUP_SUFFIX"):
            with self.subTest(variable=variable):
                self.manager_environment = f"{variable}=--datadir=/srv/wrong-db\n"
                self.assert_refused()
                self.assertFalse(any(args[0] == "runuser" for args in self.calls))

    def test_empty_option_variables_and_unrelated_manager_environment_are_supported(self):
        self.service_properties["Environment"] = 'MYSQLD_OPTS= _WSREP_NEW_CLUSTER=""'
        self.manager_environment = "UNRELATED=value\nHOME=/root\nMYSQLD_OPTS=\n_WSREP_START_POSITION=\n"
        self.assertEqual(init.main(), 0)

    def test_unit_environment_custom_argv_and_root_context_refuse_recovery(self):
        cases = (
            ("Environment", "MYSQLD_OPTS=--datadir=/srv/wrong-db"),
            ("Environment", "HOME=/root"),
            ("Environment", 'MYSQLD_OPTS="unterminated'),
            ("ExecStart", "{ path=/usr/sbin/mariadbd ; argv[]=/usr/sbin/mariadbd --datadir=/srv/wrong-db ; }"),
            ("ExecStart", '{ path=/usr/sbin/mariadbd ; argv[]=/usr/sbin/mariadbd "unterminated ; }'),
            ("ExecStart", "{ path=/root/mariadbd ; argv[]=/root/mariadbd ; }"),
            ("ExecStartEx", self.service_properties["ExecStartEx"].replace("flags= ;", "flags=privileged ;")),
            ("User", "root"), ("Group", "root"), ("ProtectHome", "no"),
            ("SetLoginEnvironment", "no"), ("WorkingDirectory", "/root"),
            ("RootDirectory", "/srv/chroot"), ("RootImage", "/srv/root.img"),
            ("BindPaths", "/srv/data:/var/lib/mariadb"),
            ("BindReadOnlyPaths", "/srv/options:/etc/mysql"),
            ("TemporaryFileSystem", "/var/lib/mariadb"), ("PAMName", "custom"),
            ("PassEnvironment", "MYSQLD_OPTS"), ("UnsetEnvironment", "MYSQL_HOME"),
            ("PrivateTmp", "yes"), ("PrivateUsers", "yes"), ("DynamicUser", "yes"),
            ("InaccessiblePaths", "/var/lib/mariadb"), ("MountImages", "/srv/database.raw:/var/lib/mariadb"),
            ("ExtensionImages", "/srv/overrides.raw"), ("ExtensionDirectories", "/srv/overrides"),
        )
        for key, value in cases:
            with self.subTest(property=key, value=value):
                original = self.service_properties[key]
                self.service_properties[key] = value
                self.assert_refused()
                self.service_properties[key] = original

    def test_unqueryable_or_incomplete_service_configuration_refuses_recovery(self):
        self.metadata_rc = 1
        self.assert_refused()
        self.metadata_rc = 0
        self.metadata_stdout = "User=mysql\nGroup=mysql\n"
        self.assert_refused()
        self.metadata_stdout = None
        self.manager_rc = 1
        self.assert_refused()

    def test_mysql_home_hidden_by_protect_home_is_not_used_for_probe(self):
        for home in ("/root", "/home/mysql", "/run/user/997", "relative-home"):
            with self.subTest(home=home):
                self.mysql_account.pw_dir = home
                self.assert_refused()

    def test_optional_wsrep_files_may_be_missing_or_contain_only_empty_options(self):
        self.service_properties["EnvironmentFiles"] = " ".join(
            f"{path} (ignore_errors=yes)" for path in self.wsrep_files)
        self.wsrep_files[0].write_text("# stock wsrep file\n\n_WSREP_NEW_CLUSTER=\n_WSREP_START_POSITION=''\n")
        # The other stock optional file is deliberately absent.
        self.assertEqual(init.main(), 0)

    def test_nonempty_or_unknown_wsrep_settings_refuse_recovery(self):
        path = self.wsrep_files[0]
        self.service_properties["EnvironmentFiles"] = f"{path} (ignore_errors=yes)"
        for content in ("_WSREP_NEW_CLUSTER=--wsrep-new-cluster\n", "MYSQLD_OPTS=--datadir=/srv/other\n",
                        "export _WSREP_NEW_CLUSTER=\n", "not-an-assignment\n", "#" * 4097):
            with self.subTest(content=content[:45]):
                path.write_text(content)
                self.assert_refused()

    def test_unknown_required_symlinked_or_invalid_envfiles_refuse_recovery(self):
        path = self.wsrep_files[0]
        for description in (f"{path} (ignore_errors=no)", "/etc/custom-mariadb.env (ignore_errors=yes)",
                            str(path)):
            with self.subTest(description=description):
                self.service_properties["EnvironmentFiles"] = description
                self.assert_refused()
        self.service_properties["EnvironmentFiles"] = f"{path} (ignore_errors=yes)"
        path.symlink_to(self.wsrep_files[1])
        self.assert_refused()
        path.unlink()
        path.mkdir()
        self.assert_refused()

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
        self.assertEqual(len(self.timezone_files), 3)
        self.assertTrue(all(not path.exists() for path in self.timezone_files))
        self.assertEqual(list(self.runtime.iterdir()), [])

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
            ("failed", 1, ["initialize"]), ("failed", 2, ["initialize"]),
            ("active", 0, []), ("inactive", 0, []),
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
