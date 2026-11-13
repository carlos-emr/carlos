# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import copy
import json
from pathlib import Path
import unittest
from lifecycle_baselines import candidate_cli, required_baselines, select_baseline
from junit_evidence import EvidenceError


class LifecycleBaselineTest(unittest.TestCase):
    def document(self):
        return json.loads((Path(__file__).resolve().parents[2] / 'docs/testing/release-2026.08-lifecycle-baselines.json').read_text())

    def test_distinct_historical_migration_and_cli_predecessor_is_required(self):
        document = self.document()
        self.assertEqual(len(required_baselines(document)), 2)
        self.assertEqual(select_baseline(document)['cliVersion'], '1.3.1')
        self.assertEqual(select_baseline(document, '2026.08.0-beta5')['cliVersion'], '1.3.0')
        for invalid in (None, [], [document]):
            broken = copy.deepcopy(document); broken['additionalSupportedBaselines'] = invalid
            with self.assertRaises(EvidenceError): required_baselines(broken)
        with self.assertRaises(EvidenceError): select_baseline(document, 'unreviewed')

    def test_candidate_cli_upgrade_keeps_both_published_predecessors_unchanged(self):
        document = self.document()
        self.assertEqual(candidate_cli(document)['version'], '1.3.5')
        self.assertEqual([row['cliVersion'] for row in required_baselines(document)], ['1.3.1', '1.3.0'])
        predecessor = next(row for row in document['assets'] if row['name'].startswith('carlos-ctl_'))
        self.assertNotEqual(candidate_cli(document)['sha256'], predecessor['sha256'])

    def test_missing_stale_or_malformed_candidate_cli_cannot_qualify(self):
        document = self.document()
        for value in (None, {}, []):
            broken = copy.deepcopy(document); broken['candidateCli'] = value
            with self.assertRaises(EvidenceError): required_baselines(broken)
        for field, value in [('tag', '1.3.1'), ('version', '1.3.1'), ('name', 'carlos-ctl_1.3.1_all.deb'),
                ('sha256', 'unverified'), ('bytes', True), ('bytes', 0),
                ('url', 'https://example.invalid/unreviewed.deb')]:
            with self.subTest(field=field, value=value):
                broken = copy.deepcopy(document); broken['candidateCli'][field] = value
                with self.assertRaises(EvidenceError): candidate_cli(broken)

    def test_unpublished_candidate_needs_its_exact_source_and_actual_package_identity(self):
        document = self.document()
        row = candidate_cli(document)
        self.assertEqual(row['mode'], 'source-built')
        self.assertEqual(row['sourceRepository'], 'https://github.com/carlos-emr/carlos-ctl')
        for field, value in [('sourceCommit', '0' * 40),
                ('sourceRepository', 'https://example.invalid/companion'),
                ('mode', 'published'), ('sha256', None), ('bytes', None)]:
            with self.subTest(field=field):
                broken = copy.deepcopy(document); broken['candidateCli'][field] = value
                with self.assertRaises(EvidenceError): candidate_cli(broken)

    def test_published_candidate_remains_bound_to_its_exact_release_url(self):
        document = self.document()
        source = document['candidateCli']
        row = {name: source[name] for name in ('tag', 'version', 'name', 'bytes', 'sha256')}
        row['url'] = 'https://github.com/carlos-emr/carlos-ctl/releases/download/' + row['tag'] + '/' + row['name']
        document['candidateCli'] = row
        self.assertEqual(candidate_cli(document), row)
        row['url'] = 'https://example.invalid/candidate.deb'
        with self.assertRaises(EvidenceError): candidate_cli(document)

    def test_a_second_tag_on_the_same_commit_cannot_replace_historical_qualification(self):
        document = self.document()
        document['additionalSupportedBaselines'][0]['sourceCommit'] = document['sourceCommit']
        with self.assertRaises(EvidenceError): required_baselines(document)

    def test_incomplete_package_province_or_published_identity_cannot_qualify(self):
        document = self.document()
        for field, value in [('provinces', ['ON']), ('cliVersion', ''), ('sourceCommit', 'unknown')]:
            broken = copy.deepcopy(document); broken['additionalSupportedBaselines'][0][field] = value
            with self.assertRaises(EvidenceError): required_baselines(broken)
        broken = copy.deepcopy(document); broken['additionalSupportedBaselines'][0]['assets'].pop()
        with self.assertRaises(EvidenceError): required_baselines(broken)
        for field, value in [('sha256', 'unverified'), ('bytes', True), ('url', 'https://example.invalid/not-a-release')]:
            broken = copy.deepcopy(document); broken['additionalSupportedBaselines'][0]['assets'][0][field] = value
            with self.assertRaises(EvidenceError): required_baselines(broken)
