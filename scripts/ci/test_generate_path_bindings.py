# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('generate_bindings', Path(__file__).with_name('generate-path-bindings.py'))
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)


class PathCandidatesTest(unittest.TestCase):
    def test_deleted_older_scope_is_retained_without_inventing_coverage(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            def git(*args):
                return subprocess.check_output(['git', *args], cwd=root, stderr=subprocess.DEVNULL, text=True).strip()
            def commit():
                git('add', '.')
                git('-c', 'user.name=Synthetic', '-c', 'user.email=synthetic@example.invalid', 'commit', '-qm', 'test: fixture')
                return git('rev-parse', 'HEAD')
            git('init', '-q')
            old = root / 'src/main/java/Foo.java'
            old.parent.mkdir(parents=True)
            old.write_text('class Foo {}')
            older = commit()
            old.unlink()
            current = commit()
            new = root / 'src/main/java/Bar.java'
            new.write_text('class Bar {}')
            test = root / 'src/test/java/FooUnitTest.java'
            test.parent.mkdir(parents=True)
            test.write_text('class FooUnitTest { Foo subject; }')
            commit()
            plan = {'baselines': {'olderMain': older, 'currentMain': current, 'releaseMergeBase': older},
                    'checks': [], 'workstreams': []}
            result = generator.generate_bindings(plan, root)
            self.assertTrue(result['candidates']['src/main/java/Foo.java']['deleted'])
            self.assertEqual(result['candidates']['src/main/java/Foo.java']['candidateTests'],
                             ['src/test/java/FooUnitTest.java'])
            self.assertEqual(result['candidates']['src/main/java/Bar.java']['candidateTests'], [])
            self.assertEqual(result['unbound'], ['src/main/java/Bar.java', 'src/main/java/Foo.java'])
            self.assertFalse(result['ready'])

    def test_writing_candidates_without_review_is_refused(self):
        child = subprocess.run([sys.executable, str(Path(generator.__file__)), '--write', '--output', '/tmp/unused-5700.json'],
                               capture_output=True, text=True)
        self.assertEqual(child.returncode, 2)
        self.assertIn('explicit --reviewed', child.stderr)
