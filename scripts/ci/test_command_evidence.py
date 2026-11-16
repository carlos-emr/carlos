# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import json
from pathlib import Path
import tempfile
import unittest

import release_verification as gate
from junit_evidence import EvidenceError
from command_evidence import relative_file


class CommandEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.input = self.root / "metrics.json"
        self.input.write_text('{"count": 17}')
        self.check = {"kind": "command", "id": "measured",
                      "resultContract": {"file": "result.json", "assertions": ["bounded", "restored"]}}
        self.result = {"schemaVersion": 1, "assertions": {"bounded": True, "restored": True},
                       "inputs": [{"path": "metrics.json", "sha256": gate.digest(self.input)}]}

    def summarize(self):
        (self.root / "result.json").write_text(json.dumps(self.result))
        return gate.summarize(self.check, self.root, 0)

    def test_real_inputs_are_retained_and_revalidated(self):
        self.assertTrue(self.summarize()["ready"])
        self.assertEqual({p.name for p in gate.evidence_files(self.check, self.root)},
                         {"result.json", "metrics.json"})
        self.input.write_text('{"count": 9000}')
        with self.assertRaises(EvidenceError):
            self.summarize()

    def test_missing_and_exit_zero_noop_cannot_pass(self):
        with self.assertRaises(EvidenceError):
            gate.summarize(self.check, self.root, 0)
        for value in ({}, {"ready": True}, {**self.result, "inputs": []},
                      {**self.result, "assertions": {"bounded": True}},
                      {**self.result, "assertions": {"bounded": 1, "restored": True}},
                      {**self.result, "schemaVersion": True}):
            with self.subTest(value=value):
                self.result = value
                with self.assertRaises(EvidenceError):
                    self.summarize()

    def test_negative_assertion_blocks_readiness(self):
        self.result["assertions"]["restored"] = False
        self.assertFalse(self.summarize()["ready"])

    def test_duplicate_inputs_and_parent_symlinks_are_refused(self):
        self.result["inputs"] *= 2
        with self.assertRaises(EvidenceError):
            self.summarize()
        self.result["inputs"] = [{"path": "linked/metrics.json", "sha256": gate.digest(self.input)}]
        (self.root / "linked").symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(EvidenceError):
            self.summarize()

    def test_malformed_result_does_not_prevent_failed_receipt_inventory(self):
        (self.root / "result.json").write_text('{}')
        self.assertEqual([p.name for p in gate.evidence_files(self.check, self.root)], ["result.json"])

    def test_host_ancestor_links_are_allowed_but_run_root_links_are_refused(self):
        host = self.root / 'host'
        run = host / 'runs/one'
        run.mkdir(parents=True)
        (run / 'metrics.json').write_text('{}')
        alias = self.root / 'host-alias'
        alias.symlink_to(host, target_is_directory=True)
        aliased_run = alias / 'runs/one'
        self.assertEqual(relative_file(aliased_run, 'metrics.json').read_text(), '{}')
        linked_run = host / 'runs/linked'
        linked_run.symlink_to(run, target_is_directory=True)
        with self.assertRaises(EvidenceError):
            relative_file(linked_run, 'metrics.json')
