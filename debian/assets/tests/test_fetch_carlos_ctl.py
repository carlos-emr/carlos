# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Execute the release dependency fetcher with an isolated GitHub fixture."""

import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
FETCH = ROOT / "debian" / "fetch-carlos-ctl.sh"


class TestFetchCarlosCtl(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.dest = self.root / "download"
        self.name = "carlos-ctl_1.1.0_all.deb"
        self.payload = b"isolated release package fixture"
        self.asset = self.root / self.name
        self.asset.write_bytes(self.payload)
        self.checksum = self.root / (self.name + ".sha256")
        self.checksum.write_text(hashlib.sha256(self.payload).hexdigest()
                                 + "  " + self.name + "\n")
        stub = self.root / "gh"
        stub.write_text("""#!/bin/sh
case "$1 $2" in
  'auth status') exit 0 ;;
  'release download')
    [ "${FIXTURE_MISSING:-0}" = 1 ] && exit 1
    while [ "$1" != --dir ]; do shift; done
    cp "$FIXTURE_ASSET" "$FIXTURE_CHECKSUM" "$2/"
    ;;
  'attestation verify')
    echo 'fixture attestation result'
    exit "${FIXTURE_ATTESTATION_RC:-0}"
    ;;
  *) exit 99 ;;
esac
""")
        stub.chmod(0o755)
        self.env = {key: value for key, value in os.environ.items()
                    if not key.startswith("CARLOS_CTL_")}
        self.env.update(PATH=str(self.root) + os.pathsep + os.environ["PATH"],
                        FIXTURE_ASSET=str(self.asset),
                        FIXTURE_CHECKSUM=str(self.checksum))

    def fetch(self, **environment):
        return subprocess.run(["sh", str(FETCH), str(self.dest), "1.1.0"],
                              env={**self.env, **environment},
                              capture_output=True, text=True)

    def test_download_returns_only_verified_package_path(self):
        result = self.fetch()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, str(self.dest / self.name) + "\n")
        self.assertEqual((self.dest / self.name).read_bytes(), self.payload)
        self.assertIn("fixture attestation result", result.stderr)

    def test_corrupt_download_is_rejected_before_attestation(self):
        self.asset.write_bytes(b"corrupt")
        result = self.fetch()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertNotIn("fixture attestation result", result.stderr)

    def test_failed_attestation_does_not_return_a_package_path(self):
        result = self.fetch(FIXTURE_ATTESTATION_RC="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")

    def test_missing_release_fails_without_using_an_existing_file(self):
        self.dest.mkdir()
        (self.dest / self.name).write_bytes(self.payload)
        result = self.fetch(FIXTURE_MISSING="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")

    def test_explicit_local_package_verifies_its_checksum(self):
        result = self.fetch(CARLOS_CTL_DEB=str(self.asset))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, str(self.dest / self.name) + "\n")
        self.asset.write_bytes(b"corrupt")
        self.assertNotEqual(self.fetch(CARLOS_CTL_DEB=str(self.asset)).returncode, 0)

    def test_local_package_without_sidecar_gets_a_checksum(self):
        self.checksum.unlink()
        result = self.fetch(CARLOS_CTL_DEB=str(self.asset))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.dest / self.checksum.name).read_text(),
                         hashlib.sha256(self.payload).hexdigest()
                         + "  " + self.name + "\n")
