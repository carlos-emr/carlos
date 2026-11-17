# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import unittest

import coverage_policy as policy
import test_check_coverage_floors as fixtures

floors = fixtures.floors


class CoveragePolicyTest(unittest.TestCase):
    setUp = fixtures.CoverageFreshnessTest.setUp
    git = fixtures.CoverageFreshnessTest.git

    def migrate(self):
        path = self.root / 'coverage/policy.json'
        policy.write_json(path, self.policy)
        policy.migrate_policy(path)
        return path

    def measure(self, source, value=100):
        return {'schemaVersion': 1, **floors.source_identity(self.base),
                'baselineCommit': self.git('rev-parse', 'HEAD'),
                'baselineSourceTree': self.git('rev-parse', 'HEAD:src/main/java'),
                'baselineReportSha256': 'a' * 64, 'baselineLane': 'FAKE focused fixture run',
                'files': {source: {'lines': value, 'instructions': value, 'branches': value,
                    'sourceBlob': self.git('rev-parse', 'HEAD:' + source)}}}

    def import_source(self, manifest, source, evidence=None):
        incoming = self.root / 'incoming.json'
        policy.write_json(incoming, evidence or self.measure(source))
        policy.import_sources(manifest, incoming, [source])

    def record_path(self, manifest, source=None):
        return manifest.parent / 'floors' / ((source or self.source) + '.json')

    def test_migration_preserves_original_bytes_metrics_waivers_and_all_prior_evidence(self):
        self.policy['baselinePriorEvidence'] = {'baselineLane': 'FAKE older retained run'}
        self.policy['files'][self.source]['requiredRefusalLines'] = [1]
        self.policy['zeroCoverageWaivers'] = {self.source: {'reason': 'FAKE retained review'}}
        raw = (json.dumps(self.policy, indent=4) + '\n').encode()
        manifest = self.root / 'coverage/policy.json'
        manifest.parent.mkdir()
        manifest.write_bytes(raw)
        policy.migrate_policy(manifest)
        identity = hashlib.sha256(raw).hexdigest()
        self.assertEqual((manifest.parent / 'evidence' / (identity + '.json')).read_bytes(), raw)
        loaded = policy.load_policy(manifest)
        for key in ('files', 'zeroCoverageWaivers'):
            self.assertEqual(loaded[key], self.policy[key])
        self.assertEqual(loaded['sourceEvidence'][self.source], self.policy)
        self.assertTrue(floors.check_freshness(loaded, self.base)['ready'])

    def test_two_independent_prs_merge_without_metadata_conflicts_or_remeasuring_the_other(self):
        manifest = self.migrate()
        original_manifest = manifest.read_bytes()
        self.git('add', 'coverage'); self.git('commit', '-qm', 'one-time migration')
        common = self.git('rev-parse', 'HEAD')
        self.git('checkout', '-qb', 'first')
        (self.root / self.source).write_text('class Guard { int value = 3; }\n')
        self.git('commit', '-qam', 'first production change')
        self.import_source(manifest, self.source)
        self.git('add', 'coverage'); self.git('commit', '-qm', 'first source evidence')
        first = self.git('rev-parse', 'HEAD')
        self.git('checkout', '-qb', 'second', common)
        other = 'src/main/java/example/Independent.java'
        (self.root / other).write_text('class Independent {}\n')
        self.git('add', other); self.git('commit', '-qm', 'second production change')
        self.import_source(manifest, other)
        self.git('add', 'coverage'); self.git('commit', '-qm', 'second source evidence')
        self.assertTrue(floors.check_freshness(policy.load_policy(manifest), self.base)['ready'])
        second_tree = floors.source_identity(self.base)['sourceTree']
        self.git('merge', '--no-ff', '-qm', 'merge first PR', first)
        loaded = policy.load_policy(manifest)
        self.assertNotEqual(second_tree, floors.source_identity(self.base)['sourceTree'])
        self.assertTrue(floors.check_freshness(loaded, self.base)['ready'])
        self.assertEqual(manifest.read_bytes(), original_manifest)
        self.assertEqual(set(loaded['files']), {self.source, other})
        # A subsequent unmeasured edit still fails; another file's run cannot hide it.
        (self.root / other).write_text('class Independent { int changed; }\n')
        self.git('commit', '-qam', 'stale second source')
        self.assertIn('sourceBlob:' + other, floors.check_freshness(loaded, self.base)['problems'])

    def test_import_updates_only_selected_source_and_retains_higher_floors_and_refusals(self):
        self.policy['files'][self.source]['requiredRefusalLines'] = [1]
        manifest = self.migrate()
        original = manifest.read_bytes()
        incoming = self.measure(self.source, 50)
        incoming['files'][self.source]['requiredRefusalLines'] = [2]
        incoming['files']['src/main/java/example/Unselected.java'] = incoming['files'][self.source]
        self.import_source(manifest, self.source, incoming)
        loaded = policy.load_policy(manifest)
        self.assertEqual(loaded['files'][self.source]['lines'], 100)
        self.assertEqual(loaded['files'][self.source]['requiredRefusalLines'], [1, 2])
        self.assertEqual(set(loaded['files']), {self.source})
        self.assertEqual(manifest.read_bytes(), original)
        self.assertTrue(floors.check_freshness(loaded, self.base)['ready'])

    def test_unmeasured_missing_dirty_and_wrong_base_sources_fail_after_migration(self):
        manifest = self.migrate()
        loaded = policy.load_policy(manifest)
        self.assertFalse(floors.check_freshness(loaded, 'HEAD')['ready'])
        (self.root / self.source).write_text('class Guard { int value = 3; }\n')
        self.assertIn('dirtyJavaSources', floors.check_freshness(loaded, self.base)['problems'])
        self.git('commit', '-qam', 'unmeasured source edit')
        self.assertIn('sourceBlob:' + self.source, floors.check_freshness(loaded, self.base)['problems'])
        other = 'src/main/java/example/Missing.java'
        (self.root / other).write_text('class Missing {}\n')
        self.git('add', other); self.git('commit', '-qm', 'missing source evidence')
        self.assertIn('missingSourceFloor:' + other, floors.check_freshness(loaded, self.base)['problems'])

    def test_shifted_refusals_require_explicit_mapping_and_cover_the_new_statement(self):
        self.policy['files'][self.source]['requiredRefusalLines'] = [1]
        previous_blob = self.policy['files'][self.source]['sourceBlob']
        manifest = self.migrate()
        (self.root / self.source).write_text('// FAKE comment shifts refusal\nclass Guard { int value = 2; }\n')
        self.git('commit', '-qam', 'shift guarded statement')
        incoming = self.measure(self.source)
        incoming['files'][self.source]['requiredRefusalLines'] = [2]
        with self.assertRaisesRegex(ValueError, 'explicit refusal-line mapping'):
            self.import_source(manifest, self.source, incoming)
        for mapping in ({'fromSourceBlob': 'b' * 40, 'lines': {'1': 2}},
                        {'fromSourceBlob': previous_blob, 'lines': {}},
                        {'fromSourceBlob': previous_blob, 'lines': {'1': 1}},
                        {'fromSourceBlob': previous_blob, 'lines': {'1': True}}):
            incoming['refusalLineMappings'] = {self.source: mapping}
            with self.subTest(mapping=mapping), self.assertRaises(ValueError):
                self.import_source(manifest, self.source, incoming)
        self.assertFalse(self.record_path(manifest).exists())
        incoming['refusalLineMappings'] = {self.source: {
            'fromSourceBlob': previous_blob, 'lines': {'1': 2}}}
        self.import_source(manifest, self.source, incoming)
        loaded = policy.load_policy(manifest)
        self.assertEqual(loaded['files'][self.source]['requiredRefusalLines'], [2])
        self.assertTrue(floors.check_freshness(loaded, self.base)['ready'])
        report = self.root / 'jacoco.xml'
        report.write_text('<report><package name="example"><sourcefile name="Guard.java">'
                          '<line nr="2" ci="1" mi="0" cb="0" mb="0"/></sourcefile></package></report>')
        diff = self.git('diff', '--no-renames', '-U0', self.base, 'HEAD', '--', 'src/main/java')
        self.assertTrue(floors.check_floors(report, loaded, diff)['ready'])

    def test_source_records_preserve_freshness_after_squash_and_pruned_history(self):
        self.policy['baselineSourceTree'] = self.policy['sourceTree']
        manifest = self.migrate()
        measured = self.policy['baselineCommit']
        self.git('reset', '--hard', self.base)
        (self.root / self.source).write_text('class Guard { int value = 2; }\n')
        self.git('add', '.'); self.git('commit', '-qm', 'squashed source and policy')
        self.git('reflog', 'expire', '--expire=now', '--all')
        self.git('gc', '--prune=now')
        self.assertNotEqual(subprocess.run(['git', 'cat-file', '-e', measured], capture_output=True).returncode, 0)
        self.assertTrue(floors.check_freshness(policy.load_policy(manifest), self.base)['ready'])
        other = 'src/main/java/example/Unrelated.java'
        (self.root / other).write_text('class Unrelated {}\n')
        self.git('add', other); self.git('commit', '-qm', 'another PR source')
        self.import_source(manifest, other)
        self.assertTrue(floors.check_freshness(policy.load_policy(manifest), self.base)['ready'])

    def test_corrupt_evidence_and_misnamed_or_unbound_records_are_rejected(self):
        manifest = self.migrate()
        self.import_source(manifest, self.source)
        record_path = self.record_path(manifest)
        record = policy.read_json(record_path)
        for bad in (dict(record, path='../outside'), dict(record, path='src/main/java/other.java'),
                    dict(record, evidenceSha256='a' * 64),
                    dict(record, floor=dict(record['floor'], sourceBlob='b' * 40)),
                    dict(record, floor=dict(record['floor'], lines=0))):
            with self.subTest(record=bad):
                policy.write_json(record_path, bad)
                with self.assertRaises((ValueError, FileNotFoundError)):
                    policy.load_policy(manifest)
        policy.write_json(record_path, record)
        evidence = manifest.parent / 'evidence' / (record['evidenceSha256'] + '.json')
        evidence.write_bytes(evidence.read_bytes() + b' ')
        with self.assertRaisesRegex(ValueError, 'digest mismatch'):
            policy.load_policy(manifest)

    def test_invalid_import_does_not_partially_update_selected_sources(self):
        manifest = self.migrate()
        incoming = self.root / 'incoming.json'
        policy.write_json(incoming, self.measure(self.source))
        with self.assertRaisesRegex(ValueError, 'Selected source'):
            policy.import_sources(manifest, incoming, [self.source, 'src/main/java/zzz.java'])
        self.assertFalse(self.record_path(manifest).exists())
        policy.write_json(incoming, dict(self.measure(self.source), base='b' * 40))
        with self.assertRaisesRegex(ValueError, 'reviewed base'):
            policy.import_sources(manifest, incoming, [self.source])

    def test_exclusions_are_source_bound_and_must_remain_nonexecutable(self):
        package = 'src/main/java/example/package-info.java'
        (self.root / package).write_text('/** FAKE package docs */\npackage example;\n')
        self.git('add', package); self.git('commit', '-qm', 'package docs')
        self.policy.update(floors.source_identity(self.base), baselineCommit=self.git('rev-parse', 'HEAD'))
        self.policy['exclusions'] = {package: {'reason': 'FAKE package docs', 'tests': ['FAKE contract test']}}
        manifest = self.migrate()
        self.assertTrue(floors.check_freshness(policy.load_policy(manifest), self.base)['ready'])
        (self.root / package).write_text('/** FAKE changed docs */\npackage example;\n')
        self.git('commit', '-qam', 'changed package')
        self.assertIn('sourceBlob:' + package,
                      floors.check_freshness(policy.load_policy(manifest), self.base)['problems'])
        incoming = self.measure(self.source)
        incoming['exclusions'] = self.policy['exclusions']
        self.import_source(manifest, package, incoming)
        self.assertTrue(floors.check_freshness(policy.load_policy(manifest), self.base)['ready'])

    def test_removal_cannot_hide_a_changed_source_but_allows_a_reverted_source(self):
        other = 'src/main/java/example/Other.java'
        (self.root / other).write_text('class Other {}\n')
        self.git('add', other); self.git('commit', '-qm', 'other source')
        manifest = self.migrate()
        self.import_source(manifest, other)
        policy.write_json(self.record_path(manifest), {'schemaVersion': 1, 'kind': 'remove', 'path': self.source})
        self.assertIn('missingSourceFloor:' + self.source,
                      floors.check_freshness(policy.load_policy(manifest), self.base)['problems'])
        self.git('checkout', self.base, '--', self.source)
        self.git('commit', '-qm', 'revert source to comparison base')
        self.assertTrue(floors.check_freshness(policy.load_policy(manifest), self.base)['ready'])

    def test_exclusion_cannot_be_rebound_after_its_measured_commit_is_pruned(self):
        package = 'src/main/java/example/package-info.java'
        (self.root / package).write_text('/** FAKE package docs */\npackage example;\n')
        self.git('add', package); self.git('commit', '-qm', 'package docs')
        self.policy.update(floors.source_identity(self.base), baselineCommit=self.git('rev-parse', 'HEAD'),
                           baselineSourceTree=self.git('rev-parse', 'HEAD:src/main/java'))
        exclusions = {package: {'reason': 'FAKE package docs', 'tests': ['FAKE contract test']}}
        self.policy['exclusions'] = exclusions
        manifest = self.migrate()
        incoming = self.measure(self.source)
        incoming.update(exclusions=exclusions,
                        exclusionSourceBlobs={package: self.git('rev-parse', 'HEAD:' + package)})
        self.import_source(manifest, package, incoming)
        measured = incoming['baselineCommit']
        self.git('add', 'coverage'); self.git('commit', '-qm', 'keep source evidence')
        self.git('reset', '--soft', self.base); self.git('commit', '-qm', 'squashed candidate')
        self.git('reflog', 'expire', '--expire=now', '--all'); self.git('gc', '--prune=now')
        self.assertNotEqual(subprocess.run(['git', 'cat-file', '-e', measured], capture_output=True).returncode, 0)
        self.assertTrue(floors.check_freshness(policy.load_policy(manifest), self.base)['ready'])
        (self.root / package).write_text('/** FAKE unmeasured annotations */\npackage example;\n')
        self.git('commit', '-qam', 'unmeasured package change')
        record_path = self.record_path(manifest, package)
        record = policy.read_json(record_path)
        record['sourceBlob'] = self.git('rev-parse', 'HEAD:' + package)
        policy.write_json(record_path, record)
        with self.assertRaisesRegex(ValueError, 'immutable evidence'):
            policy.load_policy(manifest)

    def test_cli_keeps_zero_floors_refusal_and_partial_coverage_failures_separate_from_freshness(self):
        self.policy['files'][self.source].update(lines=0, requiredRefusalLines=[1])
        manifest = self.migrate()
        report = self.root / 'jacoco.xml'
        report.write_text('<report><package name="example"><sourcefile name="Guard.java">'
                          '<line nr="1" ci="1" mi="1" cb="1" mb="1"/></sourcefile></package></report>')
        output = self.root / 'output.json'
        command = [sys.executable, floors.__file__, '--floors', str(manifest), '--base', self.base,
                   '--output', str(output)]
        fresh = subprocess.run([*command, '--freshness-only'], capture_output=True, text=True)
        self.assertEqual(fresh.returncode, 0, fresh.stderr)
        self.assertFalse(policy.read_json(output)['coverageEvaluated'])
        checked = subprocess.run([*command, str(report)], capture_output=True, text=True)
        self.assertEqual(checked.returncode, 1, checked.stderr)
        result = policy.read_json(output)
        self.assertTrue(result['freshness']['ready'])
        self.assertEqual({row['reason'] for row in result['problems']},
                         {'instructions', 'branches', 'uncovered-refusal', 'unreviewed-zero-floor'})
        seed = policy.read_json(manifest)['seedEvidenceSha256']
        (manifest.parent / 'evidence' / (seed + '.json')).write_text('{}')
        self.assertNotEqual(subprocess.run([*command, '--freshness-only'], capture_output=True).returncode, 0)
        self.assertFalse(output.exists(), 'A failed load retained an earlier result')
