# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
from pathlib import Path
import tempfile
import unittest
from junit_evidence import EvidenceError

spec = importlib.util.spec_from_file_location('first_login', Path(__file__).with_name('run-installed-first-login.py'))
probe = importlib.util.module_from_spec(spec); spec.loader.exec_module(probe)


class InstallationStageTest(unittest.TestCase):
    def test_private_complete_installer_file_is_required(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'initial.txt'
            path.write_text('  user: FAKE\n password: FAKE-ONLY\n PIN: 1234\n')
            path.chmod(0o600)
            self.assertEqual(probe.credentials_user(path), 'FAKE')
            path.chmod(0o644)
            with self.assertRaises(EvidenceError): probe.credentials_user(path)
            path.chmod(0o600)
            path.write_text('user: FAKE\npassword: FAKE-ONLY\n')
            with self.assertRaises(EvidenceError): probe.credentials_user(path)
            path.write_text('user: FAKE\nuser: FAKE\npassword: FAKE-ONLY\nPIN: 1234\n')
            with self.assertRaises(EvidenceError): probe.credentials_user(path)
            link = Path(directory) / 'link'; link.symlink_to(path)
            with self.assertRaises(EvidenceError): probe.credentials_user(link)

    def test_success_requires_real_password_change_reset_clear_and_preserved_identity(self):
        before = [1, 'FAKE', '999998', 'FAKE-OLD-HASH', 'FAKE-PIN-HASH', 1, None]
        after = [1, 'FAKE', '999998', 'FAKE-NEW-HASH', 'FAKE-PIN-HASH', 0, '2026-11-06']
        self.assertTrue(all(probe.restoration(before, after).values()))
        for index, value in [(0, 2), (1, 'OTHER'), (2, '999999'), (3, 'FAKE-OLD-HASH'),
                             (4, 'OTHER-PIN'), (5, 1), (6, None)]:
            invalid = list(after); invalid[index] = value
            self.assertFalse(all(probe.restoration(before, invalid).values()))
