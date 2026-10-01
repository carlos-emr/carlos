#!/usr/bin/env python3
"""Exercise the restore drill's archive/keyring gate with fake database replies."""
# Copyright (c) 2026 CARLOS Contributors.
# SPDX-License-Identifier: GPL-2.0-or-later
import pathlib
import subprocess
import unittest


class ArchiveBackupKeyringTest(unittest.TestCase):
    def check_gate(self, mode, keyring=False):
        source = pathlib.Path('debian/assets/bin/carlos-emr-backup').read_text()
        start = source.index('    local archived archive_table\n')
        end = source.index('    if ! restic snapshots --tag binlog', start)
        script = r'''
set -eu
MYSQL_ARGS=()
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
             'archive-keyring/outbound-email-archive.keyring' if keyring else ''],
            capture_output=True, text=True, check=False)

    def test_missing_legacy_table_and_zero_rows_pass(self):
        for mode in ('absent', 'zero'):
            with self.subTest(mode=mode):
                self.assertEqual(self.check_gate(mode).returncode, 0)

    def test_database_errors_fail_even_with_keyring_snapshot(self):
        for mode in ('show_error', 'count_error', 'invalid'):
            for present in (False, True):
                with self.subTest(mode=mode, keyring=present):
                    self.assertEqual(self.check_gate(mode, present).returncode, 1)

    def test_archived_rows_require_keyring_snapshot(self):
        self.assertEqual(self.check_gate('rows').returncode, 1)
        self.assertEqual(self.check_gate('rows', True).returncode, 0)


if __name__ == '__main__':
    unittest.main()
