#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Native gate evidence must be complete, non-skipping and safe to publish."""
import importlib.util
import io
import os
import sys
from contextlib import redirect_stdout
from pathlib import Path
import tempfile
import subprocess
import unittest
from unittest import mock

from unittest.mock import patch
from git_environment import separate_checkout_environment
from defusedxml.common import DefusedXmlException

MODULE_PATH = Path(__file__).with_name('run-cds-native-tier.py')
SPEC = importlib.util.spec_from_file_location('cds_native_tier', MODULE_PATH)
NATIVE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NATIVE)


class NativeSourceTest(unittest.TestCase):
    def test_fresh_compilation_rejects_evidence_in_host_or_container_outputs_before_writing(self):
        from compiled_classes import OUTPUTS
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name in OUTPUTS:
                for container in (False, True):
                    with self.subTest(name=name, container=container):
                        output = root / ('safe' if container else 'target/' + name) / 'evidence'
                        output.mkdir(parents=True, exist_ok=True)
                        retained = output / 'retained'; retained.write_bytes(b'preserve this evidence')
                        mapped = '/build/target/' + name + '/evidence' if container else '/build/safe'
                        with patch.object(NATIVE, '__file__', str(root / 'scripts/ci/run-cds-native-tier.py')), \
                                patch.object(sys, 'argv', ['native', '--fresh-classes', '--evidence-output', str(output),
                                    '--container-workspace', '/build', '--container-evidence-output', mapped]), \
                                patch.object(NATIVE.subprocess, 'run') as execute:
                            with self.assertRaisesRegex(ValueError, 'Evidence cannot'):
                                NATIVE.main()
                            execute.assert_not_called()
                        self.assertEqual(retained.read_bytes(), b'preserve this evidence')
                        self.assertFalse((output / 'private').exists())

    def test_ci_maven_cache_is_ignored_but_untracked_source_and_tracked_edits_are_not(self):
        import release_verification as gate
        with tempfile.TemporaryDirectory() as temporary, \
                patch.dict(os.environ, separate_checkout_environment(), clear=True):
            root = Path(temporary)
            def git(*args):
                return subprocess.check_output(['git', '-c', 'user.name=Fixture',
                    '-c', 'user.email=fixture@example.invalid', *args], cwd=root, text=True).strip()
            git('init', '-q')
            (root / '.gitignore').write_bytes((gate.ROOT / '.gitignore').read_bytes())
            source = root / 'Required.java'
            source.write_text('class Required {}\n')
            git('add', '.')
            git('commit', '-qm', 'candidate')
            cache = root / '.m2-cache/repository/example/fixture.jar'
            cache.parent.mkdir(parents=True)
            cache.write_bytes(b'fake build cache')
            self.assertTrue(gate.source_identity(root)['clean'])
            untracked = root / 'Additional.java'
            untracked.write_text('class Additional {}\n')
            self.assertFalse(gate.source_identity(root)['clean'])
            untracked.unlink()
            source.write_text('class Changed {}\n')
            self.assertFalse(gate.source_identity(root)['clean'])
            # Gitignore cannot conceal an edited tracked file, even in the cache.
            git('add', '-f', str(cache.relative_to(root)))
            git('add', 'Required.java')
            git('commit', '-qm', 'tracked cache fixture')
            cache.write_bytes(b'changed tracked bytes')
            self.assertFalse(gate.source_identity(root)['clean'])


class NativeEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source, self.output = self.root / 'source', self.root / 'sanitized'
        self.source.mkdir()
        self.output.mkdir()

    def report(self, body, prefix='example'):
        path = self.source / ('TEST-' + prefix + '.Required.xml')
        path.write_text(body)
        return path

    def collect(self):
        with redirect_stdout(io.StringIO()):
            return NATIVE.collect_reports(('Required',), self.source, self.output)

    def test_success_preserves_case_evidence_and_removes_all_captured_configuration(self):
        self.report('<testsuite name="example.Required" tests="1" errors="0" failures="0" skipped="0">'
                    '<properties><property name="password" value="private"/></properties>'
                    '<testcase name="native"><system-out>private SQL</system-out></testcase>'
                    '<system-err>private diagnostic</system-err></testsuite>')
        self.assertEqual((1, []), self.collect())
        result = next(self.output.iterdir()).read_text()
        self.assertIn('name="native"', result)
        self.assertNotIn('private', result)
        self.assertNotIn('properties', result)

    def test_required_skip_and_empty_suite_cannot_pass(self):
        for count, skipped, body in ((1, 1, '<testcase name="native"><skipped/></testcase>'), (0, 0, '')):
            with self.subTest(count=count):
                self.report(f'<testsuite name="example.Required" tests="{count}" errors="0" failures="0" skipped="{skipped}">{body}</testsuite>')
                self.assertEqual((count, ['Required']), self.collect())

    def test_missing_and_ambiguous_required_reports_cannot_pass(self):
        self.assertEqual(0, self.collect()[0])
        self.assertTrue(self.collect()[1])
        body = '<testsuite name="example.Required" tests="0" errors="0" failures="0" skipped="0"/>'
        self.report(body, 'one')
        self.report(body, 'two')
        self.assertTrue(self.collect()[1])

    def test_case_counters_must_match_reported_totals(self):
        self.report('<testsuite name="example.Required" tests="1" errors="0" failures="0" skipped="0">'
                    '<testcase name="native"><skipped/></testcase></testsuite>')
        with self.assertRaises(RuntimeError):
            self.collect()

    def test_missing_counter_and_wrong_suite_binding_refuse_evidence(self):
        for body in ('<testsuite name="example.Required" tests="1" errors="0" failures="0"><testcase/></testsuite>',
                     '<testsuite name="example.Other" tests="1" errors="0" failures="0" skipped="0"><testcase/></testsuite>'):
            with self.subTest(body=body):
                self.report(body)
                with self.assertRaises(RuntimeError):
                    self.collect()

    def test_dtd_and_external_entities_are_refused_before_publication(self):
        self.report('<!DOCTYPE testsuite [<!ENTITY secret SYSTEM "file:///etc/passwd">]>'
                    '<testsuite name="example.Required" tests="1" errors="0" failures="0" skipped="0">'
                    '<testcase name="&secret;"/></testsuite>')
        with self.assertRaises(DefusedXmlException):
            self.collect()
        self.assertFalse(list(self.output.iterdir()))

    def test_flaky_rerun_is_not_clean_native_evidence(self):
        self.report('<testsuite name="example.Required" tests="1" errors="0" failures="0" skipped="0">'
                    '<testcase name="native"><flakyFailure/></testcase></testsuite>')
        self.assertEqual((1, ['Required']), self.collect())

    def test_report_symlink_is_refused(self):
        real = self.root / 'outside.xml'
        real.write_text('<testsuite/>')
        (self.source / 'TEST-example.Required.xml').symlink_to(real)
        with self.assertRaises(RuntimeError):
            self.collect()

    def test_required_spring_prefetch_proof_cannot_be_missing_or_duplicated(self):
        simple = 'CdsImportCoordinatorMariaDbIntegrationTest'
        method = 'shouldPrefetchOneImmutableBinding_withoutTransactionOrRetainedConnection'
        for names, expected in [(['unrelated'], False), ([method], True), ([method, method], False)]:
            with self.subTest(names=names):
                body = ''.join('<testcase name="' + name + '"/>' for name in names)
                path = self.source / ('TEST-example.' + simple + '.xml')
                path.write_text('<testsuite name="example.' + simple + '" tests="' + str(len(names))
                                + '" errors="0" failures="0" skipped="0">' + body + '</testsuite>')
                with redirect_stdout(io.StringIO()):
                    total, incomplete = NATIVE.collect_reports((simple,), self.source, self.output)
                self.assertEqual(len(names), total)
                self.assertEqual(expected, not incomplete)


class UpgradeEvidencePublicationTest(unittest.TestCase):
    def test_publishes_over_a_read_only_container_report_using_a_private_new_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'upgrade-on.json'
            path.write_text('{"status":"CLI_AND_RETENTION_PASSED"}\n')
            path.chmod(0o444)
            proof = {'status': 'PASSED', 'physicalSchemaAndSeedsMatched': True}
            NATIVE.publish_upgrade_proof(path, proof)
            self.assertEqual(proof, NATIVE.json.loads(path.read_text()))
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            self.assertEqual([path], list(root.iterdir()))

    def test_rename_failure_retains_partial_evidence_and_removes_pending_report(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'upgrade-on.json'
            original = '{"status":"CLI_AND_RETENTION_PASSED"}\n'
            path.write_text(original)
            with mock.patch.object(NATIVE.os, 'replace', side_effect=PermissionError('fixture')):
                with self.assertRaises(PermissionError):
                    NATIVE.publish_upgrade_proof(path, {'status': 'PASSED'})
            self.assertEqual(original, path.read_text())
            self.assertEqual([path], list(root.iterdir()))


class CoordinatedInventoryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / 'scripts/ci').mkdir(parents=True)
        self.inventory = self.root / 'scripts/ci/cds-coordinated-test-classes.json'

    def source(self, qualified):
        path = self.root / 'src/test/java' / (qualified.replace('.', '/') + '.java')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('class Fixture {}')

    def write_inventory(self, values):
        import json
        self.inventory.write_text(json.dumps(values))

    def test_real_inventory_is_covered_exactly_once_across_workflow_shards(self):
        import json
        repository = MODULE_PATH.resolve().parents[2]
        partitions = [set(NATIVE.coordinated_classes(repository, shard)) for shard in ('import', 'export', 'shared')]
        expected = json.loads((repository / 'scripts/ci/cds-coordinated-test-classes.json').read_text())
        self.assertEqual(len(expected), len(set.union(*partitions)))
        for first in range(len(partitions)):
            for second in range(first + 1, len(partitions)):
                self.assertFalse(partitions[first] & partitions[second])

    def test_real_inventory_names_every_cds_and_database_gated_test_class(self):
        # A CDS suite missing from the reviewed inventory never runs in the native lanes and its
        # MariaDB-gated cases are skipped everywhere else (review CI-5).
        import json
        import re
        repository = MODULE_PATH.resolve().parents[2]
        listed = {item.rsplit('.', 1)[-1] for item in
                  json.loads((repository / 'scripts/ci/cds-coordinated-test-classes.json').read_text())}
        required = set()
        for source in (repository / 'src/test/java').rglob('*Test.java'):
            text = source.read_text(encoding='utf-8', errors='replace')
            if source.stem.startswith('Cds') or re.search(r'cds\.(?:export\.)?test\.properties', text):
                required.add(source.stem)
        self.assertEqual(sorted(required - listed), [])

    def test_exact_source_resolution_keeps_owning_dao_and_simple_export_selectors(self):
        self.source('example.commn.dao.CdsExportPhysicalTest')
        self.source('example.cdsexport.service.ClinicalTest')
        self.write_inventory(['CdsExportPhysicalTest', 'example.cdsexport.service.ClinicalTest'])
        self.assertEqual(('example.commn.dao.CdsExportPhysicalTest', 'example.cdsexport.service.ClinicalTest'),
                         NATIVE.coordinated_classes(self.root, 'export'))

    def test_missing_ambiguous_and_wildcard_selectors_refuse_the_gate(self):
        for values in (['Missing'], ['CdsExport*'], []):
            with self.subTest(values=values):
                self.write_inventory(values)
                with self.assertRaises(ValueError):
                    NATIVE.coordinated_classes(self.root, 'export')
        self.source('one.CdsExportDuplicatedTest')
        self.source('two.CdsExportDuplicatedTest')
        self.write_inventory(['CdsExportDuplicatedTest'])
        with self.assertRaises(ValueError):
            NATIVE.coordinated_classes(self.root, 'export')

    def test_two_spellings_of_the_same_class_cannot_double_count_coverage(self):
        self.source('example.cdsexport.CdsExportFixtureTest')
        self.write_inventory(['CdsExportFixtureTest', 'example.cdsexport.CdsExportFixtureTest'])
        with self.assertRaises(ValueError):
            NATIVE.coordinated_classes(self.root, 'export')


class NativeReadinessTest(unittest.TestCase):
    def test_temporary_unix_listener_cannot_establish_native_readiness(self):
        with self.assertRaisesRegex(RuntimeError, 'final TCP listener'):
            NATIVE.wait_for_database(['mariadb'], lambda *a, **k: None, None)

    def test_final_tcp_listener_is_retried_until_it_accepts_queries(self):
        from types import SimpleNamespace
        calls = []
        def run(command, _log, **kwargs):
            calls.append(command)
            return SimpleNamespace(returncode=0 if len(calls) == 3 else 1)
        NATIVE.wait_for_database(['mariadb', '--protocol=TCP', '--host=127.0.0.1'],
                                 run, None, monotonic=lambda: 0, sleep=lambda _: None)
        self.assertEqual(len(calls), 3)
        self.assertTrue(all('--protocol=TCP' in command for command in calls))

    def test_missing_final_listener_fails_at_the_deadline(self):
        from types import SimpleNamespace
        times = iter([0, 91])
        with self.assertRaisesRegex(RuntimeError, 'did not become ready'):
            NATIVE.wait_for_database(['mariadb', '--protocol=TCP', '--host=127.0.0.1'],
                lambda *a, **k: SimpleNamespace(returncode=1), None,
                monotonic=lambda: next(times), sleep=lambda _: None)


class EvidenceDestinationArgumentTest(unittest.TestCase):
    """An invalid evidence destination is refused as a usage error before any container work."""

    def refuse(self, arguments):
        stderr = io.StringIO()
        with patch.object(sys, 'argv', ['run-cds-native-tier.py', *arguments]), \
                patch.object(sys, 'stderr', stderr), \
                self.assertRaises(SystemExit) as refusal:
            NATIVE.main()
        self.assertEqual(refusal.exception.code, 2)
        return stderr.getvalue()

    def test_symlinked_evidence_output_is_a_usage_error(self):
        with tempfile.TemporaryDirectory() as root:
            link = Path(root, 'evidence')
            link.symlink_to(root)
            self.assertIn('evidence output must not be a symlink',
                          self.refuse(['--evidence-output', str(link)]))

    def test_container_evidence_path_without_host_output_is_a_usage_error(self):
        self.assertIn('container evidence path requires an explicit host output and absolute mount path',
                      self.refuse(['--container-evidence-output', '/evidence']))

    def test_evidence_outside_target_without_shared_mount_is_a_usage_error(self):
        with tempfile.TemporaryDirectory() as root:
            self.assertIn('outside target/ requires --container-evidence-output for an explicit shared mount',
                          self.refuse(['--evidence-output', str(Path(root, 'evidence'))]))


if __name__ == '__main__':
    unittest.main()
