#!/usr/bin/env python3
"""Check release/development base-image parity without building containers."""
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().with_name('check-release-base-image.sh')
BASE = 'tomcat:11.0.24-jdk25-temurin@sha256:' + 'a' * 64
OTHER_BASE = 'tomcat:11.0.24-jdk25-temurin@sha256:' + 'b' * 64


class ReleaseBaseImageTest(unittest.TestCase):
    def check_bases(self, dev, release):
        """Run the real guard against isolated Dockerfile fixtures."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for path, base in (('.devcontainer/development/Dockerfile', dev),
                               ('.github/release-build/Dockerfile', release)):
                dockerfile = root / path
                dockerfile.parent.mkdir(parents=True)
                dockerfile.write_text(f'# Fixture\nFROM {base}\n')
            return subprocess.run(['bash', str(SCRIPT)], cwd=root,
                                  capture_output=True, text=True)

    def test_matching_digest_pins_pass(self):
        result = self.check_bases(BASE, BASE)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f'Release build base image: {BASE}', result.stdout)

    def test_different_digest_pins_fail(self):
        result = self.check_bases(BASE, OTHER_BASE)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('::error::', result.stdout)
        self.assertIn('update them together', result.stdout)

    def test_same_digest_with_different_base_reference_fails(self):
        result = self.check_bases(BASE, BASE.replace('jdk25', 'jdk21'))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('::error::', result.stdout)

    def test_missing_digest_pins_fail_with_clear_diagnostic(self):
        unpinned = 'tomcat:11.0.24-jdk25-temurin'
        for dev, release in ((unpinned, BASE), (BASE, unpinned),
                             (unpinned, unpinned)):
            with self.subTest(dev=dev, release=release):
                result = self.check_bases(dev, release)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('::error::', result.stdout)
                self.assertIn('update them together', result.stdout)


if __name__ == '__main__':
    unittest.main()
