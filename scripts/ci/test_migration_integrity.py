# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
from pathlib import Path
import subprocess
import tempfile
import unittest

from junit_evidence import EvidenceError
from migration_integrity import compare_shared


class SharedMigrationIntegrityTest(unittest.TestCase):
    def fixture(self, root):
        subprocess.run(['git', 'init', '-q', str(root)], check=True)
        path = root / 'database/mysql/migration/common/V1__FAKE.sql'
        path.parent.mkdir(parents=True); path.write_bytes(b'-- FAKE\r\nSELECT 1;\r\n')
        return path, self.commit(root)

    def commit(self, root):
        subprocess.run(['git', 'add', '.'], cwd=root, check=True)
        subprocess.run(['git', '-c', 'user.name=Synthetic', '-c', 'user.email=synthetic@example.invalid',
                        'commit', '-qm', 'test: synthetic migration'], cwd=root, check=True)
        return subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()

    def test_new_migrations_do_not_rewrite_shared_history(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); path, base = self.fixture(root)
            path.with_name('V2__FAKE.sql').write_text('SELECT 2;\n'); self.commit(root)
            report = compare_shared(root, base, 'HEAD', minimum=1)
            self.assertTrue(report['ready']); self.assertEqual(report['sharedFiles'], 1)
            self.assertEqual((report['mainFiles'], report['candidateFiles']), (1, 2))
            self.assertEqual(report['shared'][0]['mainSha256'], report['shared'][0]['candidateSha256'])
            with self.assertRaises(EvidenceError): compare_shared(root, base, 'HEAD', minimum=2)

    def test_same_filenames_counts_and_even_sql_semantics_cannot_hide_rewritten_published_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); path, base = self.fixture(root)
            for content in (b'-- FAKE\r\nSELECT 2;\r\n', b'-- FAKE\nSELECT 1;\n', b'-- altered comment\r\nSELECT 1;\r\n'):
                path.write_bytes(content); self.commit(root)
                report = compare_shared(root, base, 'HEAD', minimum=1)
                self.assertFalse(report['ready']); self.assertEqual(len(report['rewrittenSharedMigrations']), 1)
                self.assertEqual((report['mainFiles'], report['candidateFiles']), (1, 1))

    def test_linked_migration_and_deleted_inventory_cannot_pass_a_baseline_floor(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); path, base = self.fixture(root)
            path.unlink(); self.commit(root)
            with self.assertRaises(EvidenceError): compare_shared(root, base, 'HEAD', minimum=1)
            path.symlink_to('/FAKE/outside'); self.commit(root)
            with self.assertRaises(EvidenceError): compare_shared(root, base, 'HEAD')
