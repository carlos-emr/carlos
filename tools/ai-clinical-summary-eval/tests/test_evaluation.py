import copy
import json
import sys
import unittest
from pathlib import Path


BASE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BASE))

from evaluation import apply_authoritative_repairs, atomic_fingerprint, validate
from integrity import validate_manifest


class ValidatorMutationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.facts = json.loads((BASE / "authoritative-facts.json").read_text())
        cls.valid = json.loads((BASE / "draft-v2-qwen3.5-27b.json").read_text())
        cls.valid["pending_actions"] = [
            item for item in cls.valid["pending_actions"]
            if "basic metabolic panel" not in item["action"].lower()
        ]
        assert validate(cls.valid, cls.facts)["valid"]

    def codes(self, summary):
        return {item["code"] for item in validate(summary, self.facts)["violations"]}

    def test_valid_control_and_sex_synonym(self):
        summary = copy.deepcopy(self.valid)
        summary["patient_overview"]["sex"] = "woman"
        self.assertEqual(set(), self.codes(summary))

    def test_missing_required_fact(self):
        summary = copy.deepcopy(self.valid)
        summary["active_problems"].pop()
        self.assertIn("MISSING_ITEM", self.codes(summary))

    def test_wrong_value_and_invalid_enum(self):
        summary = copy.deepcopy(self.valid)
        summary["medications"][0]["dose"] = "5 mg"
        summary["medications"][0]["status"] = "probably active"
        self.assertTrue({"VALUE_MISMATCH", "INVALID_ENUM"}.issubset(self.codes(summary)))

    def test_unsupported_item(self):
        summary = copy.deepcopy(self.valid)
        summary["medications"].append({"name": "warfarin", "dose": "5 mg", "frequency": "daily",
                                        "status": "active", "source_ids": ["N9"]})
        self.assertTrue({"UNSUPPORTED_ITEM", "WRONG_PATIENT_CITATION"}.issubset(self.codes(summary)))

    def test_unknown_and_missing_citations(self):
        summary = copy.deepcopy(self.valid)
        summary["active_problems"][0]["source_ids"] = ["MISSING"]
        codes = self.codes(summary)
        self.assertTrue({"MISSING_CITATION", "UNKNOWN_CITATION"}.issubset(codes))

    def test_suspected_not_promoted(self):
        summary = copy.deepcopy(self.valid)
        item = next(p for p in summary["active_problems"] if "neuropathy" in p["problem"])
        item["status"] = "confirmed"
        self.assertIn("VALUE_MISMATCH", self.codes(summary))

    def test_completed_action_not_pending(self):
        summary = copy.deepcopy(self.valid)
        summary["pending_actions"].append({"action": "Repeat basic metabolic panel", "source_ids": ["N5"]})
        self.assertIn("COMPLETED_ACTION_LISTED_PENDING", self.codes(summary))

    def test_wrong_patient_must_be_excluded(self):
        summary = copy.deepcopy(self.valid)
        summary["excluded_records"] = []
        self.assertIn("MISSING_EXCLUSION", self.codes(summary))

    def test_source_coverage_is_complete(self):
        summary = copy.deepcopy(self.valid)
        summary["source_coverage"].pop()
        self.assertIn("MISSING_SOURCE_COVERAGE", self.codes(summary))

    def test_missing_section_and_bad_shape(self):
        summary = copy.deepcopy(self.valid)
        del summary["scheduled_events"]
        summary["medications"] = "not-an-array"
        self.assertTrue({"MISSING_SECTION", "INVALID_SCHEMA"}.issubset(self.codes(summary)))

    def test_repair_is_scoped_and_idempotent(self):
        summary = copy.deepcopy(self.valid)
        summary["patient_overview"]["age"] = 68
        summary["pending_actions"].append({"action": "Repeat basic metabolic panel", "source_ids": ["N5"]})
        report = validate(summary, self.facts)
        repaired = apply_authoritative_repairs(summary, report)
        self.assertEqual(67, repaired["patient_overview"]["age"])
        self.assertFalse(any("basic metabolic panel" in i["action"].lower() for i in repaired["pending_actions"]))
        second = apply_authoritative_repairs(repaired, validate(repaired, self.facts))
        self.assertEqual(repaired, second)

    def test_atomic_fingerprint_ignores_only_allowed_term(self):
        left = copy.deepcopy(self.valid)
        right = copy.deepcopy(self.valid)
        left["conflicts_or_uncertainties"].append({"description": "cisgender", "source_ids": ["N1"]})
        right["conflicts_or_uncertainties"].append({"description": "transgender", "source_ids": ["N1"]})
        self.assertEqual(atomic_fingerprint(left, ["cisgender", "transgender"]),
                         atomic_fingerprint(right, ["cisgender", "transgender"]))


class ManifestIntegrityTests(unittest.TestCase):
    def manifest(self):
        return {
            "patient_id": "PAT-001", "encounter_id": "ENC-001",
            "expected_source_ids": ["N1", "N2"], "context_truncated": False,
            "sources": [
                {"source_id": "N1", "patient_id": "PAT-001", "encounter_id": "ENC-001",
                 "ingestion_status": "retrieved", "text_sha256": "aaa"},
                {"source_id": "N2", "patient_id": "OTHER", "encounter_id": "ENC-999",
                 "ingestion_status": "excluded", "text_sha256": "bbb"},
            ],
        }

    def test_valid_manifest(self):
        self.assertTrue(validate_manifest(self.manifest())["ready_for_generation"])

    def test_missing_failed_truncated_and_wrong_patient_fail_closed(self):
        manifest = self.manifest()
        manifest["sources"][0]["ingestion_status"] = "truncated"
        manifest["sources"][1]["ingestion_status"] = "retrieved"
        manifest["context_truncated"] = True
        codes = {f["code"] for f in validate_manifest(manifest)["failures"]}
        self.assertTrue({"INCOMPLETE_RETRIEVAL", "WRONG_PATIENT_NOT_EXCLUDED", "CONTEXT_TRUNCATED"}.issubset(codes))

    def test_duplicate_content_and_missing_expected_source(self):
        manifest = self.manifest()
        manifest["expected_source_ids"].append("N3")
        manifest["sources"][1]["text_sha256"] = "aaa"
        codes = {f["code"] for f in validate_manifest(manifest)["failures"]}
        self.assertTrue({"DUPLICATE_CONTENT", "MISSING_EXPECTED_SOURCE"}.issubset(codes))


if __name__ == "__main__":
    unittest.main()
