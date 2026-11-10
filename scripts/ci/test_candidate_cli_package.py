# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import copy
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import candidate_cli_package as cli
from junit_evidence import EvidenceError


class CandidateCliPackageTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.package = self.root / 'carlos-ctl_1.3.4_all.deb'
        self.payload = b'synthetic package bytes for the verifier boundary'
        self.package.write_bytes(self.payload)
        self.spec = {'mode': 'source-built', 'version': '1.3.4', 'name': self.package.name,
                     'bytes': len(self.payload), 'sha256': hashlib.sha256(self.payload).hexdigest(),
                     'sourceCommit': 'a' * 40}

    def test_exact_bytes_and_control_identity_are_both_required(self):
        with patch.object(cli.subprocess, 'check_output', return_value='Package: carlos-ctl\nVersion: 1.3.4\nArchitecture: all\n'):
            self.assertEqual(cli.verify_package(self.package, self.spec), self.package)
        with patch.object(cli.subprocess, 'check_output', return_value='Package: carlos-ctl\nVersion: 1.3.2\nArchitecture: all\n'):
            with self.assertRaisesRegex(EvidenceError, 'control identity'):
                cli.verify_package(self.package, self.spec)

    def test_changed_bytes_cannot_reach_package_metadata_parsing(self):
        self.package.write_bytes(b'x' * len(self.payload))
        with patch.object(cli.subprocess, 'check_output') as metadata:
            with self.assertRaisesRegex(EvidenceError, 'SHA-256'):
                cli.verify_package(self.package, self.spec)
            metadata.assert_not_called()

    def test_missing_linked_or_wrong_length_candidate_is_refused(self):
        missing = self.root / 'missing.deb'
        with self.assertRaises(EvidenceError): cli.verify_package(missing, self.spec)
        link = self.root / 'linked.deb'; link.symlink_to(self.package)
        with self.assertRaises(EvidenceError): cli.verify_package(link, self.spec)
        self.package.write_bytes(self.payload + b'changed')
        with self.assertRaises(EvidenceError): cli.verify_package(self.package, self.spec)

    def test_wrong_or_dirty_source_never_starts_a_package_build(self):
        for head, status in [('b' * 40, ''), ('a' * 40, ' M carlos_ctl/cds_export.py')]:
            with self.subTest(head=head, status=status), patch.object(cli, 'git_text', side_effect=[head, status]), \
                    patch.object(cli.subprocess, 'run') as build:
                with self.assertRaisesRegex(EvidenceError, 'clean source pin'):
                    cli.build_package(self.root, self.root / 'output', self.spec)
                build.assert_not_called()

    def test_published_candidates_keep_the_attested_release_path(self):
        published = copy.deepcopy(self.spec); published.pop('mode')
        with patch.object(cli.subprocess, 'run') as build:
            with self.assertRaisesRegex(EvidenceError, 'attested release fetcher'):
                cli.build_package(self.root, self.root / 'output', published)
            build.assert_not_called()

    def test_checkout_that_changes_during_build_cannot_produce_a_reviewed_package(self):
        with patch.object(cli, 'git_text', side_effect=['a' * 40, '', 'carlos_ctl/cds_export.py']), \
                patch.object(cli.subprocess, 'run'), patch.object(cli, 'verify_package') as verify:
            with self.assertRaisesRegex(EvidenceError, 'changed tracked source'):
                cli.build_package(self.root, self.root / 'output', self.spec)
            verify.assert_not_called()
