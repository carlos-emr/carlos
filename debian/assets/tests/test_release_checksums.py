# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Execute the workflow's checksum generator, including its failure path."""
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[3]


def generator():
    workflow = (ROOT / ".github/workflows/deb-packages.yml").read_text()
    block = workflow.split("          cd release-assets\n", 1)[1].split("          ls -la", 1)[0]
    return "set -euo pipefail\n" + textwrap.dedent(block)


class TestPublishedChecksums(unittest.TestCase):
    def test_published_names_verify_and_corruption_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            names = ["carlos-emr_2026.08.0~alpha14_amd64.deb",
                     "carlos-emr-drugref_2026.08.0~rc1_all.deb",
                     "carlos-emr-eform-renderer_2026.08.0_all.deb"]
            for name in names:
                (root / name).write_bytes((name + " fixture bytes").encode())
            subprocess.run(["bash", "-c", generator()], cwd=root, check=True, capture_output=True)
            for name in names:
                published = name.replace("~", ".")
                (root / name).rename(root / published)
                checksum = root / (name + ".sha256")
                result = subprocess.run(["sha256sum", "-c", checksum.name], cwd=root, capture_output=True)
                self.assertEqual(result.returncode, 0, result.stderr)
                (root / published).write_bytes(b"corrupted")
                self.assertNotEqual(subprocess.run(["sha256sum", "-c", checksum.name], cwd=root,
                                                  capture_output=True).returncode, 0)

    def test_failed_hash_aborts_without_writing_a_checksum(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "carlos-emr_1_amd64.deb").write_bytes(b"fixture")
            stub = root / "sha256sum"
            stub.write_text("#!/bin/sh\nexit 42\n")
            stub.chmod(0o755)
            result = subprocess.run(["bash", "-c", generator()], cwd=root,
                                    env={**os.environ, "PATH": str(root) + os.pathsep + os.environ["PATH"]},
                                    capture_output=True)
            self.assertNotEqual(result.returncode, 0, "hash failure was silently accepted")
            self.assertEqual(list(root.glob("*.sha256")), [])
