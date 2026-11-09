# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location('floors', Path(__file__).with_name('check-coverage-floors.py'))
floors = importlib.util.module_from_spec(spec)
spec.loader.exec_module(floors)
environment_spec = importlib.util.spec_from_file_location('coverage_git_environment',
    Path(__file__).resolve().parents[1] / 'ci/git_environment.py')
git_environment = importlib.util.module_from_spec(environment_spec)
environment_spec.loader.exec_module(git_environment)


class CoverageFloorsTest(unittest.TestCase):
    def test_zero_floor_requires_review_of_the_same_source_even_after_coverage_improves(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / 'jacoco.xml'
            path = 'src/main/java/example/Guard.java'
            diff = 'diff --git a/x b/x\n+++ b/' + path + '\n@@ -0,0 +1 @@\n+guard\n'
            policy = {'schemaVersion': 1, 'baselineCommit': 'FAKE', 'files': {path: {
                'lines': 0, 'instructions': 0, 'branches': 100, 'sourceBlob': 'a' * 40}}}
            review = {'reason': 'FAKE reviewed fixture limitation', 'reviewer': 'fixture-reviewer',
                'reviewUrl': 'https://github.com/carlos-emr/carlos/pull/1#pullrequestreview-1',
                'sourceBlob': 'a' * 40}
            for hit, miss in ((0, 1), (1, 0)):
                report.write_text('<report><package name="example"><sourcefile name="Guard.java">'
                    f'<line nr="1" ci="{hit}" mi="{miss}" cb="0" mb="0"/>'
                    '</sourcefile></package></report>')
                for invalid in (None, True, {}, dict(review, reason=' '), dict(review, reviewer=''),
                                dict(review, sourceBlob='b' * 40), dict(review, reviewUrl='https://example.invalid'),
                                dict(review, reviewUrl='https://github.com/carlos-emr/carlos/issues/1')):
                    with self.subTest(hit=hit, waiver=invalid):
                        policy['zeroCoverageWaivers'] = {path: invalid}
                        self.assertEqual(floors.check_floors(report, policy, diff)['problems'],
                                         [{'path': path, 'reason': 'unreviewed-zero-floor'}])
                policy['zeroCoverageWaivers'] = {path: review}
                self.assertTrue(floors.check_floors(report, policy, diff)['ready'])
            policy['files'][path]['lines'] = 1
            self.assertIn({'path': path, 'reason': 'waiver-outside-zero-floor'},
                          floors.check_floors(report, policy, diff)['problems'])

    def test_nonexecutable_changed_lines_do_not_acquire_a_zero_coverage_waiver(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / 'jacoco.xml'
            report.write_text('<report><package name="example"><sourcefile name="Guard.java">'
                '<line nr="1" ci="0" mi="0" cb="0" mb="0"/></sourcefile></package></report>')
            path = 'src/main/java/example/Guard.java'
            policy = {'schemaVersion': 1, 'baselineCommit': 'FAKE', 'files': {path: {
                'lines': 0, 'instructions': 0, 'branches': 0}}}
            diff = 'diff --git a/x b/x\n+++ b/' + path + '\n@@ -0,0 +1 @@\n+comment\n'
            self.assertTrue(floors.check_floors(report, policy, diff)['ready'])
            policy['zeroCoverageWaivers'] = {path: {}}
            self.assertEqual(floors.check_floors(report, policy, diff)['problems'],
                             [{'path': path, 'reason': 'waiver-outside-zero-floor'}])

    def test_partial_instructions_branches_and_refusals_cannot_hide_behind_a_line_hit(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / 'jacoco.xml'
            report.write_text('<report><package name="example"><sourcefile name="Guard.java">'
                              '<line nr="1" ci="2" mi="2" cb="1" mb="1"/></sourcefile></package></report>')
            path = 'src/main/java/example/Guard.java'
            diff = 'diff --git a/x b/x\n+++ b/' + path + '\n@@ -1 +1 @@\n+guard\n'
            policy = {'schemaVersion': 1, 'baselineCommit': 'test-baseline', 'files': {
                path: {'lines': 100, 'instructions': 100, 'branches': 100, 'requiredRefusalLines': [1]}}}
            result = floors.check_floors(report, policy, diff)
            self.assertFalse(result['ready'])
            self.assertEqual({row['reason'] for row in result['problems']},
                             {'instructions', 'branches', 'uncovered-refusal'})
            report.write_text(report.read_text().replace('mi="2"', 'mi="0"').replace('mb="1"', 'mb="0"'))
            self.assertTrue(floors.check_floors(report, policy, diff)['ready'])

    def test_unmapped_or_unreviewed_executable_source_is_not_excluded(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / 'jacoco.xml'
            report.write_text('<report/>')
            path = 'src/main/java/example/Guard.java'
            diff = 'diff --git a/x b/x\n+++ b/' + path + '\n@@ -0,0 +1 @@\n+guard\n'
            policy = {'schemaVersion': 1, 'baselineCommit': 'test', 'files': {path: {
                'lines': 50, 'instructions': 50, 'branches': 50}}}
            self.assertFalse(floors.check_floors(report, policy, diff)['ready'])
            policy['exclusions'] = {path: {'reason': 'cannot cover', 'tests': ['some-test']}}
            with self.assertRaisesRegex(ValueError, 'Executable'):
                floors.check_floors(report, policy, diff)

    def test_invalid_thresholds_and_refusal_lines_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / 'jacoco.xml'
            report.write_text('<report><package name="example"><sourcefile name="Guard.java">'
                             '<line nr="1" ci="1" mi="0" cb="0" mb="0"/></sourcefile></package></report>')
            path = 'src/main/java/example/Guard.java'
            diff = 'diff --git a/x b/x\n+++ b/' + path + '\n@@ -0,0 +1 @@\n+guard\n'
            for metric in ('lines', 'instructions', 'branches'):
                for invalid in (float('nan'), float('inf'), -1, 101, True, '100', None):
                    with self.subTest(metric=metric, invalid=invalid):
                        row = dict.fromkeys(('lines', 'instructions', 'branches'), 100)
                        if invalid is None: row.pop(metric)
                        else: row[metric] = invalid
                        with self.assertRaisesRegex(ValueError, 'finite percentages'):
                            floors.check_floors(report, {'schemaVersion': 1, 'baselineCommit': 'base', 'files': {path: row}}, diff)
            for invalid in (True, 0, -1, 1.5, '1', None):
                with self.subTest(refusal=invalid):
                    row = {'lines': 100, 'instructions': 100, 'branches': 100, 'requiredRefusalLines': [invalid]}
                    with self.assertRaisesRegex(ValueError, 'positive source line'):
                        floors.check_floors(report, {'schemaVersion': 1, 'baselineCommit': 'base', 'files': {path: row}}, diff)

    def test_missing_and_stale_floors_cannot_qualify_a_comparison(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / 'jacoco.xml'
            report.write_text('<report><package name="example"><sourcefile name="Guard.java">'
                             '<line nr="1" ci="1" mi="0" cb="0" mb="0"/></sourcefile></package></report>')
            path = 'src/main/java/example/Guard.java'
            diff = 'diff --git a/x b/x\n+++ b/' + path + '\n@@ -0,0 +1 @@\n+guard\n'
            stale = 'src/main/java/example/Unchanged.java'
            policy = {'schemaVersion': 1, 'baselineCommit': 'base', 'files': {
                stale: {'lines': 100, 'instructions': 100, 'branches': 100}}}
            result = floors.check_floors(report, policy, diff)
            self.assertFalse(result['ready'])
            self.assertEqual(result['problems'], [{'path': path, 'reason': 'missing-floor'},
                {'path': stale, 'reason': 'floor-outside-comparison'}])


class CoverageFreshnessTest(unittest.TestCase):
    def setUp(self):
        environment = mock.patch.dict(os.environ, git_environment.separate_checkout_environment(), clear=True)
        environment.start()
        self.addCleanup(environment.stop)
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        old = Path.cwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, old)
        self.git('init', '-q')
        self.source = 'src/main/java/example/Guard.java'
        path = self.root / self.source
        path.parent.mkdir(parents=True)
        path.write_text('class Guard { int value = 1; }\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'base')
        self.base = self.git('rev-parse', 'HEAD')
        path.write_text('class Guard { int value = 2; }\n')
        self.git('commit', '-qam', 'candidate')
        self.policy = {'schemaVersion': 1, **floors.source_identity(self.base),
            'baselineCommit': self.git('rev-parse', 'HEAD'), 'baselineReportSha256': 'a' * 64,
            'baselineLane': 'FAKE controlled baseline', 'files': {self.source: {
                'lines': 100, 'instructions': 100, 'branches': 100,
                'sourceBlob': self.git('rev-parse', 'HEAD:' + self.source)}}}

    def git(self, *arguments):
        return subprocess.check_output(['git', '-c', 'user.name=Fixture',
            '-c', 'user.email=fixture@example.invalid', *arguments], text=True).strip()

    def test_inherited_git_locations_cannot_modify_another_repository_during_pruning(self):
        before = (self.git('rev-parse', 'HEAD'), (self.root / '.git/index').read_bytes(),
                  (self.root / self.source).read_bytes(), self.git('reflog', '--all', '--format=%H'))
        inherited = dict(os.environ, GIT_DIR=str(self.root / '.git'), GIT_WORK_TREE=str(self.root),
                         GIT_INDEX_FILE=str(self.root / '.git/index'), GIT_COMMON_DIR=str(self.root / '.git'),
                         GIT_OBJECT_DIRECTORY=str(self.root / '.git/objects'))
        child = subprocess.run([sys.executable, '-m', 'unittest',
            'test_check_coverage_floors.CoverageFreshnessTest.test_rewritten_history_retains_the_measured_tree_even_when_the_original_commit_is_gone'],
            cwd=Path(__file__).resolve().parent, env=inherited, capture_output=True, text=True, timeout=30)
        self.assertEqual(child.returncode, 0, child.stderr)
        after = (self.git('rev-parse', 'HEAD'), (self.root / '.git/index').read_bytes(),
                 (self.root / self.source).read_bytes(), self.git('reflog', '--all', '--format=%H'))
        self.assertEqual(before, after)

    def test_test_only_commits_preserve_freshness_but_dirty_and_committed_java_changes_do_not(self):
        self.assertTrue(floors.check_freshness(self.policy, self.base)['ready'])
        (self.root / 'test.txt').write_text('FAKE independent test')
        self.git('add', 'test.txt'); self.git('commit', '-qm', 'test only')
        self.assertTrue(floors.check_freshness(self.policy, self.base)['ready'])
        (self.root / self.source).write_text('class Guard { int value = 3; }\n')
        self.assertIn('dirtyJavaSources', floors.check_freshness(self.policy, self.base)['problems'])
        self.git('commit', '-qam', 'new production behavior')
        problems = floors.check_freshness(self.policy, self.base)['problems']
        self.assertTrue({'sourceTree', 'comparisonSha256', 'sourceBlob:' + self.source,
                         'baselineSourceTree'} <= set(problems))

    def test_wrong_base_or_baseline_provenance_cannot_qualify_a_current_tree(self):
        self.assertFalse(floors.check_freshness(self.policy, 'HEAD')['ready'])
        self.policy['baselineCommit'] = self.base
        self.assertIn('baselineSourceTree', floors.check_freshness(self.policy, self.base)['problems'])
        for key, value in (('baselineCommit', 'unknown'),
                           ('baselineReportSha256', ''), ('baselineLane', ' ')):
            with self.subTest(key=key):
                policy = dict(self.policy, **{key: value})
                self.assertIn(key, floors.check_freshness(policy, self.base)['problems'])

    def test_rewritten_history_retains_the_measured_tree_even_when_the_original_commit_is_gone(self):
        measured = self.policy['baselineCommit']
        self.policy['baselineSourceTree'] = self.git('rev-parse', measured + ':src/main/java')
        self.git('reset', '--hard', self.base)
        (self.root / self.source).write_text('class Guard { int value = 2; }\n')
        (self.root / 'squashed.txt').write_text('Independent rewritten merge')
        self.git('add', '.')
        self.git('commit', '-qm', 'squashed candidate')
        self.assertTrue(floors.check_freshness(self.policy, self.base)['ready'])
        self.git('reflog', 'expire', '--expire=now', '--all')
        self.git('gc', '--prune=now')
        self.assertNotEqual(subprocess.run(['git', 'cat-file', '-e', measured],
                            capture_output=True).returncode, 0)
        self.assertTrue(floors.check_freshness(self.policy, self.base)['ready'])
        for identity in (None, 'b' * 40):
            policy = dict(self.policy, baselineSourceTree=identity)
            self.assertIn('baselineSourceTree', floors.check_freshness(policy, self.base)['problems'])
        policy = dict(self.policy, comparisonSha256='b' * 64)
        self.assertIn('comparisonSha256', floors.check_freshness(policy, self.base)['problems'])
        policy = dict(self.policy, sourceTree='b' * 40)
        self.assertIn('sourceTree', floors.check_freshness(policy, self.base)['problems'])

    def test_cli_freshness_does_not_claim_that_coverage_was_evaluated(self):
        policy = self.root / 'floors.json'
        policy.write_text(json.dumps(self.policy))
        output = self.root / 'result.json'
        script = Path(floors.__file__)
        command = [__import__('sys').executable, str(script), '--freshness-only', '--floors', str(policy),
                   '--base', self.base, '--output', str(output)]
        result = subprocess.run(command, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        record = json.loads(output.read_text())
        self.assertTrue(record['ready'])
        self.assertFalse(record['coverageEvaluated'])
        policy.write_text(json.dumps(dict(self.policy, sourceTree='b' * 40)))
        self.assertEqual(subprocess.run(command, capture_output=True).returncode, 1)
        self.assertFalse(json.loads(output.read_text())['ready'])
        policy.write_text('{}')
        self.assertNotEqual(subprocess.run(command, capture_output=True).returncode, 0)
        self.assertFalse(output.exists(), 'A failed checker retained a previous success/failure record')

    def test_refreshing_identity_alone_cannot_hide_missing_or_stale_source_floors(self):
        other = 'src/main/java/example/Another.java'
        (self.root / other).write_text('class Another {}\n')
        self.git('add', other); self.git('commit', '-qm', 'additional source')
        self.policy.update(floors.source_identity(self.base), baselineCommit=self.git('rev-parse', 'HEAD'))
        self.assertIn('missingSourceFloor:' + other,
                      floors.check_freshness(self.policy, self.base)['problems'])
        self.policy['files'][other] = {'lines': 100, 'instructions': 100, 'branches': 100,
            'sourceBlob': self.git('rev-parse', 'HEAD:' + other)}
        self.assertTrue(floors.check_freshness(self.policy, self.base)['ready'])
        self.policy['files']['src/main/java/example/Absent.java'] = self.policy['files'][other]
        self.assertIn('sourceOutsideComparison:src/main/java/example/Absent.java',
                      floors.check_freshness(self.policy, self.base)['problems'])


class CoverageFixtureIsolationTest(unittest.TestCase):
    def test_inherited_git_locations_cannot_change_another_checkout_or_prune_its_objects(self):
        with tempfile.TemporaryDirectory() as temporary:
            external = Path(temporary)
            environment = git_environment.separate_checkout_environment()

            def git(*arguments):
                return subprocess.check_output(['git', '-C', str(external), '-c', 'user.name=Fixture',
                    '-c', 'user.email=fixture@example.invalid', *arguments],
                    env=environment, text=True).strip()

            git('init', '-q')
            (external / 'owned.txt').write_text('retained data')
            git('add', '.'); git('commit', '-qm', 'retained')
            head = git('rev-parse', 'HEAD')
            (external / 'owned.txt').write_text('unreachable data that must not be pruned')
            git('commit', '-qam', 'unreachable')
            unreachable = git('rev-parse', 'HEAD')
            git('reset', '--hard', head)
            index = (external / '.git/index').read_bytes()
            with mock.patch.dict(os.environ, GIT_DIR=str(external / '.git'),
                    GIT_WORK_TREE=str(external), GIT_INDEX_FILE=str(external / '.git/index')):
                case = CoverageFreshnessTest(
                    'test_rewritten_history_retains_the_measured_tree_even_when_the_original_commit_is_gone')
                result = unittest.TestResult()
                case.run(result)
                self.assertTrue(result.wasSuccessful(), (result.errors, result.failures))
            self.assertEqual(git('rev-parse', 'HEAD'), head)
            self.assertEqual((external / 'owned.txt').read_text(), 'retained data')
            self.assertEqual((external / '.git/index').read_bytes(), index)
            self.assertEqual(git('cat-file', '-t', unreachable), 'commit')
