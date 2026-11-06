# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
from pathlib import Path
import tempfile
import unittest

from installed_package_state import stored_files, version_allowed, document_preservation
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

    def test_comparison_uses_control_versions_instead_of_normalized_asset_filenames(self):
        self.assertTrue(version_allowed('upgrade', '2026.08.0~beta6', '2026.08.0~beta7+5700'))
        self.assertFalse(version_allowed('upgrade', '2026.08.0.beta6', '2026.08.0~beta7+5700'))
        self.assertTrue(version_allowed('upgrade', '2026.08.0.beta6', '2026.08.0.beta7+5700'))
        self.assertTrue(version_allowed('reinstall', '2026.08.0.beta7+5700', '2026.08.0.beta7+5700'))
        self.assertFalse(version_allowed('reinstall', '2026.08.0.beta6', '2026.08.0.beta7+5700'))
        for value in ('', '--help', '1\n', None):
            self.assertFalse(version_allowed('upgrade', '1', value))
        with self.assertRaises(EvidenceError): version_allowed('unknown', '1', '2')

    def test_only_exact_managed_stock_bytes_may_change_with_preserved_metadata(self):
        path = 'file:carlos/eform/images/editControl2.js'; member = 'WEB-INF/eform-assets/editControl2.js'
        old = {path: [4, 0o640, 100, 101, 'a' * 64]}
        new = {path: [5, 0o640, 100, 101, 'b' * 64]}
        first = {member: {'bytes': 4, 'sha256': 'a' * 64}}
        second = {member: {'bytes': 5, 'sha256': 'b' * 64}}
        result = document_preservation(old, new, first, second)
        self.assertTrue(result['preserved']); self.assertEqual(len(result['managedAssetUpdates']), 1)
        for index, value in [(0, 6), (1, 0o600), (2, 200), (3, 201), (4, 'c' * 64)]:
            invalid = {path: list(new[path])}; invalid[path][index] = value
            self.assertFalse(document_preservation(old, invalid, first, second)['preserved'])
        self.assertFalse(document_preservation(old, new, {}, second)['preserved'])
        customized = {path: [4, 0o640, 100, 101, 'c' * 64]}
        self.assertFalse(document_preservation(customized, new, first, second)['preserved'])

    def test_managed_asset_rule_does_not_hide_added_removed_or_changed_clinical_files(self):
        old = {'file:document/FAKE.pdf': [4, 0o640, 100, 101, 'a' * 64]}
        self.assertTrue(document_preservation(old, old, {}, {})['preserved'])
        changed = {'file:document/FAKE.pdf': [4, 0o640, 100, 101, 'b' * 64]}
        self.assertFalse(document_preservation(old, changed, {}, {})['preserved'])
        self.assertFalse(document_preservation(old, {}, {}, {})['preserved'])
        self.assertFalse(document_preservation({}, old, {}, {})['preserved'])

    def test_same_asset_basename_elsewhere_and_seeded_templates_are_not_exempt(self):
        for path in ('file:carlos/eform/images/custom/editControl2.js', 'file:carlos/eform/images/blank.rtl'):
            old = {path: [4, 0o640, 100, 101, 'a' * 64]}; new = {path: [5, 0o640, 100, 101, 'b' * 64]}
            entries = {'WEB-INF/eform-assets/editControl2.js': {'bytes': 5, 'sha256': 'b' * 64}}
            self.assertFalse(document_preservation(old, new, entries, entries)['preserved'])
