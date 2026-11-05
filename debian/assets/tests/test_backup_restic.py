# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Real restic glob/restore/retention contracts, using only synthetic temp files.

The exclusion argv comes from the shipped backup invocation. This supplements
the maintainer-script agreement tests; it does not certify an installed DB drill.
"""
import json
import os
from pathlib import Path
import shutil
import re
import shlex
import subprocess
import tempfile
import unittest

from test_packaging import backup_excludes
from test_packaging import POSTRM


class ResticBackupContractTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(shutil.which("restic"), "Install restic: the real-engine contract must not silently skip")
        temporary = tempfile.TemporaryDirectory(prefix="carlos-restic-contract-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.conf = self.root / "config"
        self.imports = self.root / "o19-import"
        self.docs = self.root / "documents"
        self.environment = {**os.environ, "RESTIC_REPOSITORY": str(self.root / "repository"),
                            "RESTIC_PASSWORD": "synthetic-disposable-restic-test-only",
                            "RESTIC_CACHE_DIR": str(self.root / "cache")}
        # No inherited real repository credentials or cloud identities reach restic.
        self.environment = {k: v for k, v in self.environment.items()
                            if not k.startswith(("AWS_", "AZURE_", "GOOGLE_", "B2_", "RCLONE_"))}
        for key in ("RESTIC_PASSWORD_FILE", "RESTIC_PASSWORD_COMMAND", "RESTIC_REPOSITORY_FILE"):
            self.environment.pop(key, None)
        self.allowed = {
            self.docs / "synthetic-Écho.pdf": b"synthetic document content\n",
            self.imports / "ledger.ndjson": b'{"syntheticMigration": "complete"}\n',
            self.imports / "snapshots/deep/source.sql": b"synthetic immutable SQL snapshot\n",
            self.imports / "snapshots/deep/db-baseline.json": b'{"synthetic":true}\n',
        }
        self.secrets = [self.conf / "tls/private-key.pem", self.conf / "initial-admin.txt"]
        for parent in (self.imports, self.imports / "copies/older/nested"):
            self.secrets += [parent / name for name in ("admin-credentials.txt", "admin-credentials.txt.old",
                "o19-derived-carlos.properties", "o19-derived-carlos.properties.old", "source.properties",
                "source.properties.backup", "oscar.properties")]
        for bundle in ("bundle", "bundle-assess", "bundle-sources", "bundle-assess-sources"):
            self.secrets.append(self.imports / bundle / "nested/password.properties")
        self.secrets.append(self.imports / ".lock")
        for path, content in self.allowed.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
        for index, path in enumerate(self.secrets):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(f"SYNTHETIC-CREDENTIAL-CANARY-{index}")
        self.restic("init")

    def restic(self, *args, success=True):
        result = subprocess.run(["restic", "--no-cache", *map(str, args)], env=self.environment,
                                capture_output=True, text=True, timeout=60)
        if success:
            self.assertEqual(result.returncode, 0, result.stderr)
        return result

    def backup(self, patterns=None):
        patterns = backup_excludes() if patterns is None else patterns
        arguments = ["backup", "--tag", "files", "--host", "synthetic-carlos"]
        for pattern in patterns:
            expanded = pattern.replace("${CONF_DIR}", str(self.conf)).replace("${O19_DIR}", str(self.imports))
            self.assertNotIn("${", expanded)
            arguments += ["--exclude", expanded]
        return self.restic(*arguments, self.conf, self.imports, self.docs)

    def snapshot_files(self):
        records = [json.loads(line) for line in self.restic("ls", "latest", "--json").stdout.splitlines()]
        return {record["path"] for record in records if record.get("type") == "file"}

    def test_actual_exclusion_globs_exclude_every_secret_and_restore_exact_allowed_bytes(self):
        self.backup()
        self.assertEqual(self.snapshot_files(), {str(path) for path in self.allowed})
        destination = self.root / "restored"
        # Restore the owned subtree, without trying to chown host ancestors (/tmp).
        self.restic("restore", "latest:" + str(self.root), "--target", destination, "--verify")
        restored = {str(path.relative_to(destination)): path.read_bytes()
                    for path in destination.rglob("*") if path.is_file()}
        expected = {str(path.relative_to(self.root)): data for path, data in self.allowed.items()}
        self.assertEqual(restored, expected)
        self.assertFalse(any(b"SYNTHETIC-CREDENTIAL-CANARY" in value for value in restored.values()))

    def test_fault_control_proves_missing_credential_exclusions_are_detectable(self):
        patterns = [pattern for pattern in backup_excludes() if "admin-credentials.txt" not in pattern]
        self.backup(patterns)
        files = self.snapshot_files()
        self.assertIn(str(self.imports / "admin-credentials.txt"), files)
        self.assertIn(str(self.imports / "copies/older/nested/admin-credentials.txt.old"), files)
        self.assertNotEqual(files, {str(path) for path in self.allowed})

    def test_retention_keeps_latest_complete_snapshot_and_remains_restorable(self):
        self.backup()
        document = self.docs / "synthetic-Écho.pdf"
        document.write_bytes(b"synthetic revised document\n")
        self.backup()
        snapshots = json.loads(self.restic("snapshots", "--json").stdout)
        self.assertEqual(len(snapshots), 2)
        self.restic("forget", "--tag", "files", "--host", "synthetic-carlos", "--keep-last", "1")
        self.restic("prune")
        retained = json.loads(self.restic("snapshots", "--json").stdout)
        self.assertEqual(len(retained), 1)
        self.assertEqual(self.restic("dump", "latest", str(document)).stdout, "synthetic revised document\n")
        self.assertEqual(self.snapshot_files(), {str(path) for path in self.allowed})

    def test_missing_input_and_wrong_repository_password_are_nonzero(self):
        self.assertNotEqual(self.restic("backup", self.root / "does-not-exist", success=False).returncode, 0)
        self.environment["RESTIC_PASSWORD"] = "different-synthetic-password"
        self.assertNotEqual(self.restic("snapshots", "--json", success=False).returncode, 0)


class PurgeTraversalContractTest(unittest.TestCase):
    def test_real_shipped_find_predicate_never_follows_outside_symlinks(self):
        text = POSTRM.read_text(encoding="utf-8").replace("\\\n", " ")
        match = re.search(r'find -P "\$\{STATE\}/o19-import" -type f\s+(.+?)\s+-exec shred', text, re.S)
        self.assertIsNotNone(match, "The shipped purge traversal must retain an explicit no-follow contract")
        predicate = shlex.split(match[1])
        with tempfile.TemporaryDirectory(prefix="carlos-purge-contract-") as temporary:
            root = Path(temporary)
            imports = root / "o19-import"
            imports.mkdir()
            outside = root / "outside"
            outside.mkdir()
            canary = outside / "admin-credentials.txt"
            canary.write_text("SYNTHETIC-OUTSIDE-CANARY")
            owned = imports / "nested/deep/source.properties.backup"
            owned.parent.mkdir(parents=True)
            owned.write_text("SYNTHETIC-OWNED-CREDENTIAL")
            ledger = imports / "ledger.ndjson"
            ledger.write_text("SYNTHETIC-LEDGER")
            (imports / "linked-directory").symlink_to(outside, target_is_directory=True)
            (imports / "admin-credentials.txt.symlink").symlink_to(canary)
            selected = subprocess.run(["find", "-P", str(imports), "-type", "f", *predicate, "-print0"],
                                      capture_output=True, check=True).stdout.split(b"\0")
            self.assertEqual({Path(os.fsdecode(path)) for path in selected if path}, {owned})
            # The actual fallback action acts only on files selected by that same predicate.
            subprocess.run(["find", "-P", str(imports), "-type", "f", *predicate, "-delete"], check=True)
            self.assertFalse(owned.exists())
            self.assertEqual(canary.read_text(), "SYNTHETIC-OUTSIDE-CANARY")
            self.assertEqual(ledger.read_text(), "SYNTHETIC-LEDGER")
            self.assertTrue((imports / "admin-credentials.txt.symlink").is_symlink())


if __name__ == "__main__":
    unittest.main()
