#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (c) 2026 CARLOS Contributors.
"""Exercise the restore drill's archive/keyring gate with fake database replies.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""
import pathlib
import subprocess
import unittest

BACKUP = (pathlib.Path(__file__).resolve().parents[4]
          / "debian" / "assets" / "bin" / "carlos-emr-backup")
KEYRING = "/etc/carlos-emr/archive-keyring/outbound-email-archive.keyring"


class ArchiveBackupKeyringTest(unittest.TestCase):
    def check_gate(self, mode, keyring=False, listing=None):
        source = BACKUP.read_text()
        start = source.index('    local archived archive_table\n')
        end = source.index('    if ! restic snapshots --tag binlog', start)
        script = r'''
set -euo pipefail
MYSQL_ARGS=()
CONF_DIR=/etc/carlos-emr
VERIFY_DB=synthetic_restore
files_list=$2
mariadb() {
    case "$*" in
        *SHOW*)
            case "$MODE" in
                show_error) return 1 ;;
                absent) return 0 ;;
                *) echo outboundEmailArchive ;;
            esac ;;
        *)
            case "$MODE" in
                count_error) return 1 ;;
                invalid) echo invalid ;;
                zero) echo 0 ;;
                *) echo 2 ;;
            esac ;;
    esac
}
alert() { echo "$*" >&2; }
MODE=$1
check_gate() {
''' + source[start:end] + '\n}\ncheck_gate\n'
        return subprocess.run(
            ['bash', '-c', script, 'archive-gate', mode,
             listing if listing is not None
             else ("/etc/carlos-emr/carlos.properties\n" + KEYRING + "\n" if keyring else "")],
            capture_output=True, text=True, check=False)

    def test_missing_legacy_table_and_zero_rows_pass(self):
        for mode in ('absent', 'zero'):
            with self.subTest(mode=mode):
                self.assertEqual(self.check_gate(mode).returncode, 0)

    def assert_drill_failed(self, result):
        # The gate's own alert, not an unrelated shell error, ended the run.
        self.assertEqual(result.returncode, 1)
        self.assertIn("restore drill failed", result.stderr)

    def test_database_errors_fail_even_with_keyring_snapshot(self):
        for mode in ('show_error', 'count_error', 'invalid'):
            for present in (False, True):
                with self.subTest(mode=mode, keyring=present):
                    self.assert_drill_failed(self.check_gate(mode, present))

    def test_archived_rows_require_keyring_snapshot(self):
        self.assert_drill_failed(self.check_gate('rows'))
        self.assertEqual(self.check_gate('rows', True).returncode, 0)

    def test_a_keyring_at_the_end_of_the_listing_counts(self):
        self.assertEqual(self.check_gate('rows', listing=KEYRING).returncode, 0)

    def test_leftover_lock_or_temporary_files_are_not_the_keyring(self):
        for leftover in (KEYRING + ".lock\n",
                         "/etc/carlos-emr/archive-keyring/.archive-keyring-1.tmp\n",
                         "/srv/old-archive-keyring/outbound-email-archive.keyring\n"):
            with self.subTest(leftover=leftover):
                self.assert_drill_failed(
                    self.check_gate('rows', listing=leftover))


if __name__ == '__main__':
    unittest.main()
