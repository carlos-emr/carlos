# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
import json
from pathlib import Path
import sys
import unittest

BASE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BASE))
from evaluation import evaluate


class EvaluationTests(unittest.TestCase):
    def setUp(self):
        self.case = json.loads((BASE / "cases" / "medication-conflict.json").read_text())
        self.generated = {
            "sections": [
                {"id": "active_problems", "title": "Active problems",
                 "claim_ids": ["c1", "c4"]},
                {"id": "medications_allergies", "title": "Medications and allergies",
                 "claim_ids": ["c2", "c3"]}
            ],
            "claims": [
                {"id": "c1", "text": "Intermittent knee pain has been present for two weeks.",
                 "source_ids": ["visit-1"]},
                {"id": "c4", "text": "No injury was reported.", "source_ids": ["visit-1"]},
                {"id": "c2", "text": "Amlodipine is listed active, but the patient reports stopping it.",
                 "source_ids": ["visit-1", "meds-1"]},
                {"id": "c3", "text": "Penicillin caused a rash.", "source_ids": ["allergy-1"]}
            ],
            "coverage": [
                {"source_id": "visit-1", "status": "cited", "reason": "Symptoms and medication report."},
                {"source_id": "meds-1", "status": "cited", "reason": "Medication-list conflict."},
                {"source_id": "allergy-1", "status": "cited", "reason": "Allergy evidence."},
                {"source_id": "admin-1", "status": "excluded", "reason": "Administrative only."}
            ]
        }

    def evaluate(self, generated=None):
        return evaluate(generated or self.generated, self.case["bundle"]["sources"],
                        self.case["expectations"])

    def test_passing_summary_has_complete_fact_and_citation_scores(self):
        result = self.evaluate()
        self.assertTrue(result["metrics"]["hard_gate_pass"], result["findings"])
        self.assertEqual(1.0, result["metrics"]["critical_fact_recall"])
        self.assertEqual(1.0, result["metrics"]["required_fact_recall"])
        self.assertEqual(1.0, result["metrics"]["citation_completeness"])
        self.assertEqual(1.0, result["metrics"]["citation_precision"])

    def test_missing_critical_fact_fails(self):
        generated = copy.deepcopy(self.generated)
        generated["claims"] = [claim for claim in generated["claims"] if claim["id"] != "c2"]
        generated["sections"][1]["claim_ids"].remove("c2")
        result = self.evaluate(generated)
        self.assertFalse(result["metrics"]["hard_gate_pass"])
        self.assertIn("MISSING_FACT", {item["code"] for item in result["findings"]})

    def test_irrelevant_citation_fails(self):
        generated = copy.deepcopy(self.generated)
        generated["claims"][2]["source_ids"].append("admin-1")
        result = self.evaluate(generated)
        self.assertFalse(result["metrics"]["hard_gate_pass"])
        self.assertIn("IRRELEVANT_FACT_CITATION", {item["code"] for item in result["findings"]})

    def test_unsupported_and_forbidden_claims_fail(self):
        generated = copy.deepcopy(self.generated)
        generated["claims"].append({"id": "c4", "text": "Demographic number 3003 has migraine.",
                                    "source_ids": ["admin-1"]})
        generated["sections"][0]["claim_ids"].append("c4")
        result = self.evaluate(generated)
        codes = {item["code"] for item in result["findings"]}
        self.assertFalse(result["metrics"]["hard_gate_pass"])
        self.assertTrue({"UNSUPPORTED_CLAIM", "FORBIDDEN_CONTENT"}.issubset(codes))

    def test_same_fact_in_multiple_claims_fails(self):
        generated = copy.deepcopy(self.generated)
        generated["claims"].append({"id": "c4", "text": "The active amlodipine listing conflicts with stopping it.",
                                    "source_ids": ["visit-1", "meds-1"]})
        generated["sections"][1]["claim_ids"].append("c4")
        result = self.evaluate(generated)
        self.assertIn("DUPLICATE_FACT_CLAIM", {item["code"] for item in result["findings"]})

    def test_combined_facts_fail_atomic_claim_gate(self):
        generated = copy.deepcopy(self.generated)
        generated["claims"][0]["text"] = "Intermittent knee pain for two weeks, with no injury reported."
        generated["claims"] = [claim for claim in generated["claims"] if claim["id"] != "c4"]
        generated["sections"][0]["claim_ids"].remove("c4")
        result = self.evaluate(generated)
        self.assertFalse(result["metrics"]["hard_gate_pass"])
        self.assertIn("COMBINED_FACTS", {item["code"] for item in result["findings"]})

    def test_single_source_claim_is_valid(self):
        case = json.loads((BASE / "cases" / "single-source.json").read_text())
        generated = {
            "sections": [
                {"id": "clinical_overview", "title": "Clinical overview", "claim_ids": ["c1"]},
                {"id": "medications_allergies", "title": "Medications and allergies", "claim_ids": ["c2"]}
            ],
            "claims": [
                {"id": "c1", "text": "Dizziness resolved after hydration and did not recur for three days.",
                 "source_ids": ["phone-1"]},
                {"id": "c2", "text": "No medication changes were made.", "source_ids": ["phone-1"]}
            ],
            "coverage": [{"source_id": "phone-1", "status": "cited", "reason": "Clinical follow-up."}]
        }
        result = evaluate(generated, case["bundle"]["sources"], case["expectations"])
        self.assertTrue(result["metrics"]["hard_gate_pass"], result["findings"])


if __name__ == "__main__":
    unittest.main()
