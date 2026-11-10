# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import json
import hashlib
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from unittest.mock import patch
from release_deployment import PACKAGE_TRANSFORMS, SOURCE_ROOT

from cds_war_manifest import REQUIRED_CLASSES, REQUIRED_MIGRATION
from junit_evidence import EvidenceError
from release_deployment import attest, attest_package, local_target
from release_verification import runtime_matches


class InstalledByteBindingTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.war = self.root / "candidate.war"
        self.installed = self.root / "installed"
        members = {name: b"synthetic required class" for name in REQUIRED_CLASSES}
        members.update({REQUIRED_MIGRATION: b"-- synthetic migration", "WEB-INF/lib/synthetic.jar": b"synthetic jar",
                        "WEB-INF/classes/carlos-build.properties": b"build.number=synthetic", "images/echo.txt": "Écho 測定".encode(),
                        "WEB-INF/classes/jdbc-defaults.properties": b"untransformed Maven defaults"})
        with zipfile.ZipFile(self.war, "w") as archive:
            for name, data in members.items():
                archive.writestr(name, data)
            archive.extractall(self.installed)
        for name, source in PACKAGE_TRANSFORMS.items():
            (self.installed / name).write_bytes((SOURCE_ROOT / source).read_bytes())

    def read(self):
        return attest(self.war, self.installed, "https://127.0.0.1/carlos")

    def test_complete_installed_tree_matches_every_candidate_file(self):
        first = self.read()
        self.assertEqual(first, self.read())
        self.assertEqual(first["files"], 8)
        self.assertNotIn("synthetic required class", json.dumps(first))

    def test_changed_missing_added_and_symlinked_files_are_refused(self):
        target = self.installed / "images/echo.txt"
        original = target.read_bytes()
        target.write_bytes(b"X" * len(original))
        with self.assertRaisesRegex(EvidenceError, "bytes differ"): self.read()
        target.unlink()
        with self.assertRaises(EvidenceError): self.read()
        target.write_bytes(original)
        extra = self.installed / "unbound-extension.jar"
        extra.write_bytes(b"synthetic")
        with self.assertRaises(EvidenceError): self.read()
        extra.unlink(); target.unlink()
        outside = self.root / "outside"
        outside.write_bytes(original); target.symlink_to(outside)
        with self.assertRaises((OSError, EvidenceError)): self.read()

    def test_package_transforms_are_verified_exactly_and_neither_can_be_omitted(self):
        for name in PACKAGE_TRANSFORMS:
            with self.subTest(name=name):
                target = self.installed / name
                original = target.read_bytes()
                target.write_bytes(b"X" * len(original))
                with self.assertRaisesRegex(EvidenceError, "bytes differ"):
                    self.read()
                target.unlink()
                with self.assertRaisesRegex(EvidenceError, "missing candidate files"):
                    self.read()
                target.write_bytes(original)

    def test_unreadable_installed_directory_is_refused(self):
        def unlistable(_root, **options):
            options["onerror"](PermissionError("synthetic"))
            return iter(())
        with patch("release_deployment.os.walk", side_effect=unlistable):
            with self.assertRaisesRegex(EvidenceError, "directory cannot be read"):
                self.read()

    def test_matching_a_local_disk_does_not_attest_a_different_remote_http_target(self):
        for value in ("https://example.invalid/carlos", "https://user:synthetic@127.0.0.1/carlos", "file:///tmp/carlos"):
            self.assertFalse(local_target(value))
            with self.assertRaises(EvidenceError):
                attest(self.war, self.installed, value)

    def test_runtime_constraints_pin_qualification_without_logging_command_output(self):
        self.assertTrue(runtime_matches({"node": "22.23.4", "npm": "10.9.9", "java": "25.0.3", "python": "3.14.1"},
                                        {"node": "22", "npm": "10.9.9", "java": "25", "python": "3.14"}))
        for actual in ({"node": "24.19.0"}, {"node": None}, {}):
            self.assertFalse(runtime_matches(actual, {"node": "22"}))


class PublishedPackageBindingTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(); self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.package_root = self.root / 'package'
        control = self.package_root / 'DEBIAN'; control.mkdir(parents=True)
        control.chmod(0o755)
        (control / 'control').write_text('Package: fake-qualification\nVersion: 1.0\nArchitecture: all\n'
            'Maintainer: Fake <fake@example.invalid>\nDescription: synthetic attestation fixture\n')
        self.application = self.package_root / 'usr/share/carlos-emr/webapp/carlos'
        self.application.mkdir(parents=True)
        self.identity = self.application / 'WEB-INF/classes/carlos-build.properties'
        self.identity.parent.mkdir(parents=True)
        self.identity.write_text('build.version=FAKE\nbuild.job=deb\nbuild.number=FAKE-Debian-stamp\n')
        (self.application / 'client.txt').write_text('FAKE café Ω\n')
        self.installed = self.root / 'installed'
        shutil.copytree(self.application, self.installed)
        self.package = self.root / 'fixture.deb'

    def build(self):
        subprocess.run(['dpkg-deb', '--root-owner-group', '--build', str(self.package_root), str(self.package)],
                       check=True, capture_output=True)
        return hashlib.sha256(self.package.read_bytes()).hexdigest()

    def test_entire_published_application_including_actual_build_stamp_is_verified(self):
        digest = self.build()
        result = attest_package(self.package, digest, self.installed, 'https://127.0.0.1/carlos')
        self.assertEqual(result['packageSha256'], digest)
        self.assertEqual(result['files'], 2)
        self.assertEqual(result['buildIdentity']['build.number'], 'FAKE-Debian-stamp')
        (self.installed / 'WEB-INF/classes/carlos-build.properties').write_text('build.number=FAKE-other-stamp\n')
        with self.assertRaises(EvidenceError):
            attest_package(self.package, digest, self.installed, 'https://127.0.0.1/carlos')

    def test_missing_or_duplicate_published_build_identity_is_refused(self):
        for data in ('build.number=FAKE\n', 'build.version=FAKE\nbuild.job=deb\nbuild.number=FAKE\nbuild.number=OTHER\n'):
            self.identity.write_text(data)
            digest = self.build()
            with self.assertRaisesRegex(EvidenceError, 'identity'):
                attest_package(self.package, digest, self.installed, 'https://127.0.0.1/carlos')

    def test_wrong_pin_empty_application_and_archive_links_cannot_attest(self):
        digest = self.build()
        with self.assertRaises(EvidenceError):
            attest_package(self.package, '0' * 64, self.installed, 'https://127.0.0.1/carlos')
        link = self.application / 'link'; link.symlink_to('/FAKE-outside')
        digest = self.build()
        with self.assertRaisesRegex(EvidenceError, 'nonregular'):
            attest_package(self.package, digest, self.installed, 'https://127.0.0.1/carlos')
        shutil.rmtree(self.application)
        digest = self.build()
        with self.assertRaisesRegex(EvidenceError, 'no application'):
            attest_package(self.package, digest, self.installed, 'https://127.0.0.1/carlos')


if __name__ == "__main__":
    unittest.main()
