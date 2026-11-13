# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""The carlos-emr preinst's unfinished-CDS-import upgrade guard (carlos-emr/carlos#6199).

An upgrade during a CDS import could leave the EMR closed for clinical use: the new build
refuses to resume an import started by the old one, and the maintenance window stays closed
until that import releases it. The preinst refuses such an upgrade, before the debhelper
snippet stops the service, and otherwise never turns a database it cannot reach into a failed
upgrade.

These tests run the SHIPPED preinst under /bin/sh with a stub `mariadb` first on PATH (the
stub records its arguments and prints the row a test gives it). Run (from debian/assets):
    python3 -m unittest discover -v -s tests -t .
"""

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
PREINST = ROOT / "debian" / "carlos-emr.preinst"
TOOLS = ("sed", "tail", "head", "cut", "cat")


class _Host:
    """A scratch PATH holding only the tools the preinst needs, plus an optional stub
    `mariadb` that records its argv and answers with a canned row or failure."""

    def __init__(self, tmp, row=None, fail=False, mariadb=True):
        self.bin = Path(tmp) / "bin"
        self.bin.mkdir()
        for tool in TOOLS:
            found = shutil.which(tool)
            if found is None:
                raise unittest.SkipTest(f"{tool} not installed")
            os.symlink(found, self.bin / tool)
        self.args = Path(tmp) / "mariadb-args"
        self.env_file = Path(tmp) / "carlos-emr.env"
        if mariadb:
            stub = self.bin / "mariadb"
            if fail:
                body = 'echo "ERROR 2002: socket" >&2\nexit 1\n'
            else:
                body = "printf '%s\\n' \"$STUB_ROW\"\n"
            stub.write_text('#!/bin/sh\nprintf "%s\\n" "$@" > "$STUB_ARGS"\n' + body)
            stub.chmod(0o755)
        self.row = row

    def run(self, *argv, env_text=None, extra=None):
        if env_text is not None:
            self.env_file.write_text(env_text)
        environment = {
            "PATH": str(self.bin),
            "STUB_ARGS": str(self.args),
            "STUB_ROW": self.row or "",
            "CARLOS_EMR_ENV_FILE": str(self.env_file),
        }
        environment.update(extra or {})
        return subprocess.run(["/bin/sh", str(PREINST), *argv], capture_output=True,
                              text=True, env=environment)

    def database_asked(self):
        if not self.args.exists():
            return None
        lines = self.args.read_text().splitlines()
        # The database name is the first argument after the fixed client options.
        return lines[4] if len(lines) > 4 else None


class TestShippedPreinst(unittest.TestCase):

    def test_preinst_is_executable_posix_sh_with_the_guard_before_debhelper(self):
        self.assertTrue(PREINST.is_file(), PREINST)
        self.assertTrue(os.access(str(PREINST), os.X_OK), "preinst is not executable")
        text = PREINST.read_text()
        self.assertEqual(text.splitlines()[0], "#!/bin/sh")
        # The refusal must happen before the debhelper snippet stops the service.
        self.assertLess(text.index("cds_import_upgrade_guard || exit 1"),
                        text.index("#DEBHELPER#"))
        self.assertTrue(text.rstrip().endswith("exit 0"))


class TestUpgradeGuardVerdicts(unittest.TestCase):

    def _host(self, **kw):
        tmp = tempfile.mkdtemp(suffix=" with space")
        self.addCleanup(shutil.rmtree, tmp)
        return _Host(tmp, **kw)

    def test_upgrade_proceeds_when_no_import_is_unfinished(self):
        host = self._host(row="0\t0\t")
        result = host.run("upgrade", "2026.08.0")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr, "")
        self.assertEqual(host.database_asked(), "carlos")

    def test_upgrade_is_refused_while_the_maintenance_window_is_open(self):
        host = self._host(row="1\t0\t")
        result = host.run("upgrade", "2026.08.0")
        self.assertEqual(result.returncode, 1)
        self.assertIn("refusing to upgrade: a CDS import is not finished", result.stderr)
        self.assertIn("maintenance window", result.stderr)
        self.assertIn("CARLOS_ALLOW_UPGRADE_DURING_CDS_IMPORT=1", result.stderr)

    def test_upgrade_is_refused_while_imported_patients_are_held_and_names_the_runs(self):
        run_a = "0f6e1c2a-1111-4c3b-9a55-000000000001"
        run_b = "0f6e1c2a-1111-4c3b-9a55-000000000002"
        host = self._host(row=f"0\t3\t{run_a} {run_b}")
        result = host.run("upgrade", "2026.08.0")
        self.assertEqual(result.returncode, 1)
        self.assertIn("3 imported patient(s) are still held", result.stderr)
        self.assertIn(f"import run(s): {run_a} {run_b}", result.stderr)

    def test_override_skips_the_check_with_a_warning(self):
        host = self._host(row="1\t5\tx")
        result = host.run("upgrade", "2026.08.0",
                          extra={"CARLOS_ALLOW_UPGRADE_DURING_CDS_IMPORT": "1"})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("WARNING", result.stderr)
        self.assertIsNone(host.database_asked(), "the override must not query the database")

    def test_unreachable_database_continues_with_a_note(self):
        host = self._host(fail=True)
        result = host.run("upgrade", "2026.08.0")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("could not check the database", result.stderr)

    def test_unexpected_answer_continues_with_a_note(self):
        host = self._host(row="yes\tno\t")
        result = host.run("upgrade", "2026.08.0")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("unexpected answer", result.stderr)

    def test_no_mariadb_client_continues_silently(self):
        host = self._host(mariadb=False)
        result = host.run("upgrade", "2026.08.0")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr, "")

    def test_fresh_install_and_abort_upgrade_never_query_the_database(self):
        for argv in (("install",), ("install", "2026.07.0"), ("abort-upgrade", "2026.08.0")):
            host = self._host(row="1\t1\tx")
            result = host.run(*argv)
            self.assertEqual(result.returncode, 0, (argv, result.stderr))
            self.assertIsNone(host.database_asked(), argv)

    def test_database_name_is_the_last_carlos_db_name_without_quotes(self):
        host = self._host(row="0\t0\t")
        result = host.run("upgrade", "2026.08.0",
                          env_text='CARLOS_DB_NAME=first\nOTHER=1\nCARLOS_DB_NAME="clinic_db"\n')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(host.database_asked(), "clinic_db")

    def test_database_name_that_is_not_an_identifier_is_never_passed_to_the_client(self):
        host = self._host(row="1\t1\tx")
        result = host.run("upgrade", "2026.08.0",
                          env_text="CARLOS_DB_NAME=carlos;drop\n")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("not a plain identifier", result.stderr)
        self.assertIsNone(host.database_asked())

    def test_query_is_read_only_and_asks_only_the_admission_and_hold_tables(self):
        host = self._host(row="0\t0\t")
        host.run("upgrade", "2026.08.0")
        query = host.args.read_text().upper()
        for verb in ("INSERT", "UPDATE", "DELETE", "REPLACE", "ALTER", "DROP", "CREATE"):
            self.assertNotIn(verb + " ", query)
        self.assertIn("CDS_IMPORT_ADMISSION", query)
        self.assertIn("CDS_IMPORT_PATIENT", query)
        self.assertIn("--PROTOCOL=SOCKET", query)


if __name__ == "__main__":
    unittest.main()
