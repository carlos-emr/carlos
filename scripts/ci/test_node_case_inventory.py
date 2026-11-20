# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import copy
from pathlib import Path
import tempfile
import unittest

from junit_evidence import EvidenceError
import node_case_inventory as inventory
from release_verification import digest


class NodeCaseInventoryTest(unittest.TestCase):
    def fixture(self, root):
        (root / 'scripts').mkdir(); (root / 'scripts/FAKE.test.js').write_text('// FAKE controlled source\n')
        first = {'file': 'scripts/FAKE.test.js', 'parents': [{'name': 'FAKE suite', 'ordinal': 1}],
                 'name': 'required', 'line': 10, 'column': 3}
        second = dict(first, name='another', line=20)
        cases = [first, copy.deepcopy(first), second]
        manifest = inventory.create({'schemaVersion': 3, 'sourceChanged': False, 'runtime': 'v22.15.0',
            'sourceFiles': {'scripts/FAKE.test.js': digest(root / 'scripts/FAKE.test.js')},
            'files': ['scripts/FAKE.test.js'], 'cases': cases}, root, digest)
        return cases, manifest

    def test_equal_counts_and_names_cannot_replace_a_required_generated_invocation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); cases, manifest = self.fixture(root)
            inventory.validate(manifest, cases, root, digest)
            changed = copy.deepcopy(cases); changed[1]['parents'][0]['ordinal'] = 99
            inventory.validate(manifest, changed, root, digest)  # Runtime ordinals are not source locations.
            changed[1]['line'] = 11
            with self.assertRaisesRegex(EvidenceError, 'omitted, replaced or added'):
                inventory.validate(manifest, changed, root, digest)
            with self.assertRaises(EvidenceError): inventory.validate(manifest, cases[1:], root, digest)

    def test_old_case_metadata_cannot_qualify_changed_test_source(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); cases, manifest = self.fixture(root)
            (root / 'scripts/FAKE.test.js').write_text('// FAKE changed assertions with the same test names\n')
            with self.assertRaisesRegex(EvidenceError, 'test source changed'):
                inventory.validate(manifest, cases, root, digest)

    def test_duplicate_invalid_or_missing_reviewed_cases_are_not_an_inventory(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); cases, manifest = self.fixture(root)
            for change in ('duplicate', 'empty', 'boolean-count'):
                broken = copy.deepcopy(manifest)
                if change == 'duplicate': broken['cases'].append(broken['cases'][0])
                elif change == 'empty': broken['cases'] = []
                else: broken['cases'][0]['count'] = True
                with self.assertRaises(EvidenceError): inventory.validate(broken, cases, root, digest)
