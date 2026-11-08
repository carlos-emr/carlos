#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Native gate evidence must be complete, non-skipping and safe to publish."""
import importlib.util
import io
from contextlib import redirect_stdout
from pathlib import Path
import tempfile
import unittest
from defusedxml.common import DefusedXmlException

MODULE_PATH = Path(__file__).with_name('run-cds-native-tier.py')
SPEC = importlib.util.spec_from_file_location('cds_native_tier', MODULE_PATH)
NATIVE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NATIVE)


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


if __name__ == '__main__':
    unittest.main()
