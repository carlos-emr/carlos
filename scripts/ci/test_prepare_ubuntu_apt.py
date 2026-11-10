# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name('prepare-ubuntu-apt.py')
SPEC = importlib.util.spec_from_file_location('prepare_ubuntu_apt', SCRIPT)
MIRRORS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MIRRORS)


class UbuntuAptTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / 'sources.list.d').mkdir()

    def test_runner_mirror_list_uses_https_for_every_candidate(self):
        path = self.root / 'apt-mirrors.txt'
        path.write_bytes(b'http://azure.archive.ubuntu.com/ubuntu\tpriority:1\n'
                         b'https://archive.ubuntu.com/ubuntu\tpriority:2\n')
        self.assertEqual(MIRRORS.prepare(self.root), [path])
        self.assertEqual(path.read_bytes(), b'https://archive.ubuntu.com/ubuntu\tpriority:1\n'
                         b'https://archive.ubuntu.com/ubuntu\tpriority:2\n')

    def test_deb822_keeps_suite_components_and_signature_key(self):
        path = self.root / 'sources.list.d/ubuntu.sources'
        original = (b'Types: deb\nURIs: http://archive.ubuntu.com/ubuntu/\n'
                    b'Suites: noble noble-updates\nComponents: main universe\n'
                    b'Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg\n')
        path.write_bytes(original)
        MIRRORS.prepare(self.root)
        self.assertEqual(path.read_bytes(), original.replace(b'http:', b'https:'))

    def test_security_and_legacy_list_urls_keep_their_repository_paths(self):
        path = self.root / 'sources.list'
        path.write_bytes(b'deb http://security.ubuntu.com/ubuntu noble-security main\n'
                         b'deb https://azure.archive.ubuntu.com/ubuntu noble main\n')
        MIRRORS.prepare(self.root)
        self.assertEqual(path.read_bytes(), b'deb https://security.ubuntu.com/ubuntu noble-security main\n'
                         b'deb https://archive.ubuntu.com/ubuntu noble main\n')

    def test_unrelated_hosts_and_lookalike_domains_remain_unchanged(self):
        path = self.root / 'sources.list.d/other.list'
        original = (b'deb https://packages.microsoft.com/ubuntu/24.04/prod noble main\n'
                    b'deb http://azure.archive.ubuntu.com.example/ubuntu noble main\n'
                    b'deb http://archive.ubuntu.com/ubuntu-other noble main\n')
        path.write_bytes(original)
        self.assertEqual(MIRRORS.prepare(self.root), [])
        self.assertEqual(path.read_bytes(), original)

    def test_repeated_preparation_is_idempotent_and_missing_files_are_not_created(self):
        path = self.root / 'sources.list'
        path.write_text('deb http://archive.ubuntu.com/ubuntu noble main\n')
        self.assertEqual(len(MIRRORS.prepare(self.root)), 1)
        self.assertEqual(MIRRORS.prepare(self.root), [])
        self.assertFalse((self.root / 'apt-mirrors.txt').exists())

    def test_cli_prepares_only_the_selected_apt_directory(self):
        path = self.root / 'sources.list'
        path.write_text('deb http://archive.ubuntu.com/ubuntu noble main\n')
        result = subprocess.run([sys.executable, str(SCRIPT), '--apt-root', str(self.root)],
                                capture_output=True, text=True, check=True, timeout=10)
        self.assertIn('mirrors: 1', result.stdout)
        self.assertEqual(path.read_text(), 'deb https://archive.ubuntu.com/ubuntu noble main\n')
