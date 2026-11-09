# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Exercise the shell's actual goal order, fresh-output refusals and exit propagation."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

import release_verification as gate


class PermissionLaneShellTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / 'scripts/ci').mkdir(parents=True)
        self.script = self.root / 'scripts/check-ci-permission-test.sh'
        shutil.copyfile(gate.ROOT / 'scripts/check-ci-permission-test.sh', self.script)
        shutil.copyfile(gate.ROOT / 'scripts/ci/permission-cases.json',
                        self.root / 'scripts/ci/permission-cases.json')
        binary = self.root / 'bin'
        binary.mkdir()
        fake_maven = binary / 'mvn'
        fake_maven.write_text('#!/usr/bin/env python3\nimport json,os,sys\n'
            'from pathlib import Path\n'
            'path=Path(os.environ["CAPTURE"])\n'
            'calls=json.loads(path.read_text()) if path.exists() else []\n'
            'calls.append(sys.argv[1:])\n'
            'path.write_text(json.dumps(calls))\n'
            'sys.exit(int(os.environ.get("FAKE_MAVEN_EXIT","0")))\n')
        fake_maven.chmod(0o700)
        self.capture = self.root / 'argv.json'
        self.environment = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ['PATH'],
                                CAPTURE=str(self.capture))
        for name in ('CARLOS_PERMISSION_REPORTS', 'CARLOS_PERMISSION_EXECUTION', 'CARLOS_PERMISSION_COVERAGE'):
            self.environment.pop(name, None)
        self.environment.pop('FAKE_MAVEN_EXIT', None)

    def run_script(self, **environment):
        return subprocess.run(['bash', str(self.script)], env={**self.environment, **environment},
                              capture_output=True, text=True, timeout=15)

    def test_real_shell_prepares_agent_before_tests_and_reports_the_same_execution(self):
        paths = {'CARLOS_PERMISSION_REPORTS': str(self.root / 'private-reports'),
                 'CARLOS_PERMISSION_EXECUTION': str(self.root / 'private.exec'),
                 'CARLOS_PERMISSION_COVERAGE': str(self.root / 'private-coverage')}
        result = self.run_script(**paths)
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = json.loads(self.capture.read_text())
        self.assertEqual(len(calls), 2)
        self.assertIn('test-compile', calls[0])
        self.assertNotIn('surefire:test', calls[0])
        arguments = calls[1]
        self.assertLess(arguments.index('jacoco:prepare-agent'), arguments.index('surefire:test'))
        self.assertLess(arguments.index('surefire:test'), arguments.index('jacoco:report'))
        self.assertIn('-Dtest.reportsDirectory=' + paths['CARLOS_PERMISSION_REPORTS'], arguments)
        for key in ('-Djacoco.destFile=', '-Djacoco.dataFile='):
            self.assertIn(key + paths['CARLOS_PERMISSION_EXECUTION'], arguments)
        self.assertIn('-Dtest.coverageDirectory=' + paths['CARLOS_PERMISSION_COVERAGE'], arguments)
        cases = gate.read_json(gate.ROOT / 'scripts/ci/permission-cases.json')
        self.assertEqual(next(value.removeprefix('-Dtest=') for value in arguments if value.startswith('-Dtest=')).split(','),
                         [row['classname'] + '#' + row['name'] for row in cases])

    def test_every_existing_or_dangling_output_is_refused_before_maven(self):
        for name in ('CARLOS_PERMISSION_REPORTS', 'CARLOS_PERMISSION_EXECUTION', 'CARLOS_PERMISSION_COVERAGE'):
            for linked in (False, True):
                path = self.root / 'stale-output'
                if linked:
                    path.symlink_to('missing-target')
                else:
                    path.write_text('previous evidence')
                try:
                    result = self.run_script(**{name: str(path)})
                    self.assertNotEqual(result.returncode, 0)
                    self.assertFalse(self.capture.exists())
                finally:
                    path.unlink()

    def test_relative_outputs_are_refused_before_maven(self):
        for name in ('CARLOS_PERMISSION_REPORTS', 'CARLOS_PERMISSION_EXECUTION', 'CARLOS_PERMISSION_COVERAGE'):
            result = self.run_script(**{name: 'relative-output'})
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(self.capture.exists())

    def test_maven_failure_is_returned_without_converting_it_to_success(self):
        result = self.run_script(FAKE_MAVEN_EXIT='37')
        self.assertEqual(result.returncode, 37)
        calls = json.loads(self.capture.read_text())
        self.assertEqual(len(calls), 1)
        self.assertIn('test-compile', calls[0])


if __name__ == '__main__':
    unittest.main()
