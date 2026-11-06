# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
from pathlib import Path
import tempfile
import unittest

from installed_package_state import stored_files, version_allowed
from junit_evidence import EvidenceError


class PackagePreservationTest(unittest.TestCase):
    def test_equal_row_counts_or_file_counts_cannot_hide_replaced_bytes_or_permissions(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            file = root / 'FAKE.txt'; file.write_bytes(b'FAKE-before')
            first = stored_files(root)
            self.assertEqual(first, stored_files(root))
            file.write_bytes(b'FAKE-after!')
            self.assertNotEqual(first, stored_files(root))
            file.write_bytes(b'FAKE-before'); file.chmod(0o400)
            self.assertNotEqual(first, stored_files(root))
            empty = root / 'empty'; empty.mkdir()
            self.assertIn('directory:empty', stored_files(root))

    def test_outside_symlinks_and_replaced_directory_roots_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); base = root / 'store'; base.mkdir()
            outside = root / 'outside'; outside.mkdir()
            (outside / 'FAKE.txt').write_text('FAKE-outside')
            link = base / 'link'; link.symlink_to(outside, target_is_directory=True)
            with self.assertRaises(EvidenceError): stored_files(base)
            link.unlink(); link.symlink_to(outside / 'FAKE.txt')
            with self.assertRaises((OSError, EvidenceError)): stored_files(base)
            root_link = root / 'root-link'; root_link.symlink_to(base, target_is_directory=True)
            with self.assertRaises(EvidenceError): stored_files(root_link)

    def test_debian_tilde_version_is_not_an_upgrade_from_published_dot_version(self):
        self.assertFalse(version_allowed('upgrade', '2026.08.0.beta6', '2026.08.0~beta7+5700'))
        self.assertTrue(version_allowed('upgrade', '2026.08.0.beta6', '2026.08.0.beta7+5700'))
        self.assertTrue(version_allowed('reinstall', '2026.08.0.beta7+5700', '2026.08.0.beta7+5700'))
        self.assertFalse(version_allowed('reinstall', '2026.08.0.beta6', '2026.08.0.beta7+5700'))
        for value in ('', '--help', '1\n', None):
            self.assertFalse(version_allowed('upgrade', '1', value))
        with self.assertRaises(EvidenceError): version_allowed('unknown', '1', '2')
