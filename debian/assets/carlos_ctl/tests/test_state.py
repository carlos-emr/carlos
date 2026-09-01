# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""State-ledger and P0 pristine-gate contracts for the O19 importer.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import os
import shutil
import tempfile
import unittest

from carlos_ctl import o19import, o19map_schema


class TestStateLedger(unittest.TestCase):

    def setUp(self):
        self.state_dir = tempfile.mkdtemp(prefix="o19state-test-")
        self.addCleanup(shutil.rmtree, self.state_dir)

    def test_fresh_state_is_empty(self):
        state = o19import.load_state(self.state_dir)
        self.assertEqual(state["phases"], {})
        self.assertEqual(state["accepted"], [])

    def test_mark_done_round_trips(self):
        state = o19import.load_state(self.state_dir)
        o19import.mark_done(self.state_dir, state, "stage",
                            dump_sha256="abc123")
        reloaded = o19import.load_state(self.state_dir)
        self.assertTrue(o19import.phase_done(reloaded, "stage"))
        self.assertEqual(reloaded["phases"]["stage"]["dump_sha256"],
                         "abc123")
        self.assertFalse(o19import.phase_done(reloaded, "etl"))

    def test_corrupt_state_file_resets_cleanly(self):
        os.makedirs(self.state_dir, exist_ok=True)
        with open(o19import.state_path(self.state_dir), "w") as fh:
            fh.write("{not json")
        state = o19import.load_state(self.state_dir)
        self.assertEqual(state["phases"], {})

    def test_accepted_flags_persist_in_state(self):
        state = o19import.load_state(self.state_dir)
        state["accepted"] = ["archived-forms"]
        o19import.save_state(self.state_dir, state)
        self.assertEqual(o19import.load_state(self.state_dir)["accepted"],
                         ["archived-forms"])

    def test_report_appends_sections(self):
        o19import.report_append(self.state_dir, "P0", "fine")
        o19import.report_append(self.state_dir, "P1", "also fine")
        with open(os.path.join(self.state_dir, "report.txt")) as fh:
            text = fh.read()
        self.assertIn("== P0 ==", text)
        self.assertIn("== P1 ==", text)


class TestPristineGate(unittest.TestCase):
    """The stock-initial-deploy sweep (user requirement: absolute on a
    packaged host, warning-only under --dev-target)."""

    def seeds(self):
        return dict(o19map_schema.SEED_ROW_COUNTS)

    def test_exact_seed_counts_pass(self):
        counts = self.seeds()
        counts["demographic"] = 0
        counts["appointment"] = 0
        self.assertEqual(o19import.pristine_violations(counts), [])

    def test_a_single_demo_patient_violates(self):
        counts = self.seeds()
        counts["demographic"] = 1
        v = o19import.pristine_violations(counts)
        self.assertEqual(len(v), 1)
        self.assertIn("demographic", v[0])
        self.assertIn("expected 0", v[0])

    def test_missing_seed_rows_also_violate(self):
        # fewer rows than the seed is just as non-stock as extra rows
        counts = self.seeds()
        seeded_table = next(iter(counts))
        counts[seeded_table] = 0
        if o19map_schema.SEED_ROW_COUNTS[seeded_table] == 0:
            self.skipTest("no non-zero seeded table in manifest")
        v = o19import.pristine_violations(counts)
        self.assertTrue(any(seeded_table in x for x in v))

    def test_no_accept_class_can_clear_the_gate(self):
        # the gate is not expressed as a preflight blocker at all, so the
        # accept vocabulary cannot touch it — pin the vocabulary here.
        self.assertNotIn("non-pristine", o19import.ACCEPT_CLASSES)
        self.assertNotIn("pristine", " ".join(o19import.ACCEPT_CLASSES))

    def test_provider_and_security_seeds_are_expected(self):
        # provider/security ARE seeded — the sweep must expect their seed
        # rows rather than demanding zero.
        self.assertGreater(
            o19map_schema.SEED_ROW_COUNTS.get("provider", 0), 0)
        self.assertGreater(
            o19map_schema.SEED_ROW_COUNTS.get("security", 0), 0)


class TestDiskHeadroom(unittest.TestCase):

    def test_tiny_requirement_passes(self):
        self.assertIsNone(o19import.check_disk_headroom(1024, 0))

    def test_absurd_requirement_fails_with_paths(self):
        msg = o19import.check_disk_headroom(1 << 60, 0)
        self.assertIsNotNone(msg)
        self.assertIn("insufficient disk", msg)


class TestHeadCollations(unittest.TestCase):

    def test_extracts_collations_from_dump_head(self):
        head = (b"CREATE TABLE t (a varchar(5)) "
                b"DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;\n"
                b"/*!40101 SET NAMES latin1 */; COLLATE latin1_swedish_ci")
        self.assertEqual(
            o19import.head_collations(head),
            ["latin1_swedish_ci", "utf8mb4_uca1400_ai_ci"])


if __name__ == "__main__":
    unittest.main()
