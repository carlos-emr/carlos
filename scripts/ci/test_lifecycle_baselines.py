# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import copy
import json
from pathlib import Path
import unittest
from lifecycle_baselines import required_baselines, select_baseline
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
