#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Reject resource-qualification dilution and authentication failures after valid plaintext."""
import importlib.util
import io
from pathlib import Path
import subprocess
import unittest
from unittest import mock

SPEC = importlib.util.spec_from_file_location("cds_export_resources", Path(__file__).with_name("run-cds-export-resources.py"))
RESOURCES = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RESOURCES)


def large_proof():
    size = (4 << 30) + 65536
    total = size * 5
    return dict(status="PASSED", byteLayerOnly=True, maximumHeapBytes=256 << 20, members=5,
                memberBytes=size, aggregateBytes=total, zipBytes=total + 1024,
                ciphertextBytes=total + 4096, zip64EntriesVerified=65537, independentZipReaders=2)


def disk_proof():
    return dict(status="PASSED", byteLayerOnly=True, maximumHeapBytes=256 << 20, reserveRefused=True,
                physicalEnospcRefused=True, postFailureRecoveryVerified=True, partialStageFiles=0,
                filesystemBytes=32 << 20)


class ResourceEvidenceTest(unittest.TestCase):
    def test_complete_large_and_physical_disk_evidence_pass(self):
        RESOURCES.require_resource_evidence("large", large_proof())
        RESOURCES.require_resource_evidence("disk", disk_proof())

    def test_small_rehearsal_cannot_be_reported_as_large_qualification(self):
        proof = large_proof()
        size = (1 << 20) + 65536
        proof.update(memberBytes=size, aggregateBytes=5 * size, zipBytes=5 * size + 1024,
                     ciphertextBytes=5 * size + 4096, zip64EntriesVerified=0)
        RESOURCES.require_resource_evidence("interop", proof)
        with self.assertRaises(RuntimeError):
            RESOURCES.require_resource_evidence("large", proof)

    def test_missing_each_large_evidence_field_refuses_qualification(self):
        for key in large_proof():
            with self.subTest(key=key):
                proof = large_proof()
                del proof[key]
                with self.assertRaises(RuntimeError):
                    RESOURCES.require_resource_evidence("large", proof)

    def test_ordinary_zip_and_excessive_heap_cannot_pass(self):
        for key, value in (("zip64EntriesVerified", 65535), ("maximumHeapBytes", 301 << 20),
                           ("maximumHeapBytes", True), ("members", 4), ("aggregateBytes", 0),
                           ("zipBytes", 1), ("ciphertextBytes", 1), ("independentZipReaders", 1)):
            with self.subTest(key=key, value=value):
                proof = large_proof()
                proof[key] = value
                with self.assertRaises(RuntimeError):
                    RESOURCES.require_resource_evidence("large", proof)

    def test_incomplete_disk_fault_cleanup_or_recovery_refuses_qualification(self):
        for key, value in (("reserveRefused", False), ("physicalEnospcRefused", False),
                           ("postFailureRecoveryVerified", False), ("partialStageFiles", 1),
                           ("partialStageFiles", False), ("filesystemBytes", 64 << 20)):
            with self.subTest(key=key):
                proof = disk_proof()
                proof[key] = value
                with self.assertRaises(RuntimeError):
                    RESOURCES.require_resource_evidence("disk", proof)

    def test_unbounded_interoperability_fixture_is_refused(self):
        with self.assertRaises(RuntimeError):
            RESOURCES.require_resource_evidence("interop", large_proof())

    def test_unknown_profile_is_refused(self):
        with self.assertRaises(ValueError):
            RESOURCES.require_resource_evidence("other", large_proof())


class RecipientProcessTest(unittest.TestCase):
    def run_recipient(self, decrypt_code, verify_code):
        process = mock.MagicMock()
        process.__enter__.return_value = process
        process.stdout = io.BytesIO(b"synthetic plaintext")
        process.wait.return_value = decrypt_code
        process.poll.return_value = decrypt_code
        with mock.patch.object(RESOURCES.subprocess, "Popen", return_value=process), \
             mock.patch.object(RESOURCES.subprocess, "run", return_value=subprocess.CompletedProcess([], verify_code)):
            RESOURCES.verify_openssl_recipient(["java"], Path("owned-fixture"), Path("proof.json"), io.StringIO())

    def test_valid_plaintext_cannot_hide_failed_cms_authentication(self):
        with self.assertRaises(RuntimeError):
            self.run_recipient(1, 0)

    def test_successful_decryption_cannot_hide_failed_zip_verification(self):
        with self.assertRaises(RuntimeError):
            self.run_recipient(0, 1)

    def test_both_independent_processes_must_succeed(self):
        self.run_recipient(0, 0)


if __name__ == "__main__":
    unittest.main()
