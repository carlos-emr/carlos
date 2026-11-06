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


if __name__ == '__main__':
    unittest.main()
