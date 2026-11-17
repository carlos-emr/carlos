#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Exercise the real purge credential-removal block with nested import inputs."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class TestNestedImportCredentials(unittest.TestCase):
    def exercise(self, fail_shred=False):
        source = (ROOT / "debian/carlos-emr.postrm").read_text()
        block = source[source.index("        O19_SHREDDED=1"):source.index('        if [ "$O19_DELETE_FAILED" = 1 ]; then')]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            state = root / "state"
            inputs = state / "o19-import"
            credentials = [inputs / "bundle/backup/oscar/oscar.properties",
                           inputs / "bundle-sources/hash/source.properties",
                           inputs / "bundle-assess-sources/hash/source.properties.incoming"]
            for path in credentials:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("synthetic credential")
            export = inputs / "o19-archive-export/clinical.csv"
            export.parent.mkdir()
            export.write_text("preserved record")
            outside = root / "outside.properties"
            outside.write_text("outside input tree")
            link = inputs / "bundle-sources/hash/linked/source.properties"
            link.parent.mkdir()
            link.symlink_to(outside)
            environment = dict(os.environ, STATE=str(state))
            if fail_shred:
                binary = root / "bin"
                binary.mkdir()
                shred = binary / "shred"
                shred.write_text("#!/bin/sh\nexit 1\n")
                shred.chmod(0o755)
                environment["PATH"] = str(binary) + os.pathsep + environment["PATH"]
            cp = subprocess.run(["sh", "-ec", block + '\nprintf "%s:%s" "$O19_SHREDDED" "$O19_DELETE_FAILED"'],
                                env=environment, text=True, capture_output=True, check=True)
            self.assertEqual(cp.stdout, "0:0" if fail_shred else "1:0")
            self.assertTrue(all(not path.exists() for path in credentials))
            self.assertEqual(export.read_text(), "preserved record")
            self.assertEqual(outside.read_text(), "outside input tree")
            self.assertTrue(link.is_symlink())

    def test_nested_credentials_are_removed_without_removing_clinical_exports(self):
        self.exercise()

    def test_unlink_fallback_covers_the_same_nested_files(self):
        self.exercise(fail_shred=True)


if __name__ == "__main__":
    unittest.main()
