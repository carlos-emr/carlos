#!/usr/bin/env python3
"""Exercise the real image-pull script without contacting a container registry."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / '.github/actions/dev-container/pull-image.sh'


class PullImageTest(unittest.TestCase):
    def classify(self, scenario):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'pom.xml').write_text('<project><release>25</release></project>\n')
            docker = root / 'docker'
            docker.write_text('''#!/bin/bash
case "$1" in
 pull)
  case "$SCENARIO" in
   missing) echo 'manifest unknown' >&2; exit 1;;
   denied) echo 'access denied' >&2; exit 1;;
  esac;;
 tag) if [ "$SCENARIO" = tag-failure ]; then exit 1; fi;;
 run)
  case "$SCENARIO" in
   old-jdk) echo 'openjdk version "21.0.8"' >&2;;
   unknown-jdk) exit 1;;
   *) echo 'openjdk version "25.0.1"' >&2;;
  esac;;
esac
''')
            docker.chmod(0o700)
            output = root / 'output'
            env = {**os.environ, 'PATH': str(root) + os.pathsep + os.environ['PATH'],
                   'SCENARIO': scenario, 'IMAGE': 'fixture/dev:latest', 'GITHUB_OUTPUT': str(output)}
            subprocess.run(['bash', str(SCRIPT)], cwd=root, env=env, check=True, capture_output=True)
            return dict(line.split('=', 1) for line in output.read_text().splitlines())

    def test_matching_jdk_uses_pulled_image(self):
        self.assertEqual(self.classify('matching'), {'pulled': 'true', 'reason': 'success'})

    def test_old_jdk_builds_current_toolchain(self):
        self.assertEqual(self.classify('old-jdk'), {'pulled': 'false', 'reason': 'not-found'})

    def test_unreadable_jdk_builds_current_toolchain(self):
        self.assertEqual(self.classify('unknown-jdk'), {'pulled': 'false', 'reason': 'not-found'})

    def test_missing_image_builds_locally(self):
        self.assertEqual(self.classify('missing'), {'pulled': 'false', 'reason': 'not-found'})

    def test_registry_denial_fails(self):
        self.assertEqual(self.classify('denied'), {'pulled': 'false', 'reason': 'error'})

    def test_failed_tag_fails(self):
        self.assertEqual(self.classify('tag-failure'), {'pulled': 'false', 'reason': 'error'})


if __name__ == '__main__':
    unittest.main()
