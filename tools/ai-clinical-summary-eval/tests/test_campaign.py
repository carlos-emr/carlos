# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import contextlib
from copy import deepcopy
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import subprocess

BASE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BASE))
import run_campaign


class CampaignTests(unittest.TestCase):
    def setUp(self):
        self.path = BASE / "campaigns" / "smoke-2b.json"
        self.config = run_campaign.load_campaign(self.path)

    def test_smoke_matrix_covers_two_cases_and_four_candidates(self):
        matrix = list(run_campaign.experiment_matrix(self.config, self.path))
        self.assertEqual(8, len(matrix))
        self.assertEqual({"single-source", "medication-conflict"},
                         {item[0]["case_id"] for item in matrix})

    def test_selection_reduces_matrix(self):
        matrix = list(run_campaign.experiment_matrix(
            self.config, self.path, {"single-source"}, {"compact-12x180"}))
        self.assertEqual(1, len(matrix))

    def test_2b_optimization_matrix_is_prompt_only_after_baseline(self):
        path = BASE / "campaigns" / "optimize-2b.json"
        config = run_campaign.load_campaign(path)
        matrix = list(run_campaign.experiment_matrix(config, path))
        self.assertEqual(8, len(matrix))
        self.assertEqual(4, len(config["candidates"]))
        settings = {(item["max_claims"], item["max_chars"], item["num_ctx"],
                     item["num_predict"]) for item in config["candidates"]}
        self.assertEqual({(10, 180, 8192, 1024)}, settings)
        self.assertNotIn("prompt_suffix", config["candidates"][0])
        combined = config["candidates"][-1]["prompt_suffix"]
        self.assertIn("Delete scheduling", combined)
        self.assertIn("one conflict claim", combined)

    def test_ledger_optimization_is_explicit_and_opt_in(self):
        path = BASE / "campaigns" / "optimize-ledger-2b.json"
        config = run_campaign.load_campaign(path)
        matrix = list(run_campaign.experiment_matrix(config, path))
        self.assertEqual(4, len(matrix))
        case, candidate, _, _ = matrix[0]
        self.assertEqual("sources_and_fact_ledger", candidate["input_mode"])
        self.assertEqual({"sources", "fact_ledger"},
                         set(run_campaign.candidate_input(case, candidate)))
        self.assertEqual({"sources"}, set(run_campaign.candidate_input(
            case, {"id": "source-only"})))

    def test_candidate_changes_prompt_and_schema_together(self):
        candidate = self.config["candidates"][2]
        prompt = run_campaign.candidate_prompt(
            (BASE.parent / "ai-clinical-summary-draft" / "prompt.txt").read_text(), candidate)
        schema = run_campaign.candidate_schema(
            json.loads((BASE.parent / "ai-clinical-summary-draft" / "output-schema.json").read_text()),
            candidate)
        self.assertIn("no more than 12 claims total", prompt)
        self.assertIn("at most 180 characters", prompt)
        self.assertEqual(12, schema["properties"]["claims"]["maxItems"])
        self.assertEqual(180, schema["properties"]["claims"]["items"]["properties"]["text"]["maxLength"])

    def test_dry_run_does_not_contact_ollama_or_create_runs(self):
        with patch.object(run_campaign, "post_json", side_effect=AssertionError("network")), \
                contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(0, run_campaign.main(["--config", str(self.path), "--dry-run"]))
        self.assertEqual(8, json.loads(output.getvalue())["experiments"])

    def test_report_selects_fastest_candidate_passing_every_run(self):
        rows = [
            {"candidate_id": "safe-slow", "wall_duration_seconds": 8,
             "case_id": "case", "clinical_fingerprint": "same",
             "finding_counts": {},
             "metrics": {"hard_gate_pass": True, "required_fact_recall": 1.0}},
            {"candidate_id": "safe-fast", "wall_duration_seconds": 4,
             "case_id": "case", "clinical_fingerprint": "same",
             "finding_counts": {},
             "metrics": {"hard_gate_pass": True, "required_fact_recall": 0.9}},
            {"candidate_id": "unsafe", "wall_duration_seconds": 1,
             "case_id": "case", "clinical_fingerprint": "same",
             "finding_counts": {"UNSUPPORTED_CLAIM": 1},
             "metrics": {"hard_gate_pass": False, "required_fact_recall": 1.0}}
        ]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run_campaign.write_report(root, rows)
            comparison = json.loads((root / "comparison.json").read_text())
            report = (root / "report.md").read_text()
        self.assertEqual("safe-fast", comparison["winner"])
        self.assertIn("UNSUPPORTED_CLAIM", report)

    def test_report_rejects_unstable_candidate(self):
        rows = [
            {"candidate_id": "unstable", "case_id": "case", "clinical_fingerprint": "a",
             "wall_duration_seconds": 1, "metrics": {"hard_gate_pass": True, "required_fact_recall": 1.0}},
            {"candidate_id": "unstable", "case_id": "case", "clinical_fingerprint": "b",
             "wall_duration_seconds": 1, "metrics": {"hard_gate_pass": True, "required_fact_recall": 1.0}}
        ]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run_campaign.write_report(root, rows)
            comparison = json.loads((root / "comparison.json").read_text())
        self.assertIsNone(comparison["winner"])

    def test_report_ranks_inference_compute_without_model_load_time(self):
        rows = [
            {"candidate_id": "compute-fast", "wall_duration_seconds": 20,
             "prompt_eval_duration_ns": 1_000_000_000, "eval_duration_ns": 2_000_000_000,
             "case_id": "case", "clinical_fingerprint": "same", "finding_counts": {},
             "metrics": {"hard_gate_pass": True, "required_fact_recall": 1.0}},
            {"candidate_id": "wall-fast", "wall_duration_seconds": 5,
             "prompt_eval_duration_ns": 2_000_000_000, "eval_duration_ns": 3_000_000_000,
             "case_id": "case", "clinical_fingerprint": "same", "finding_counts": {},
             "metrics": {"hard_gate_pass": True, "required_fact_recall": 1.0}},
        ]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run_campaign.write_report(root, rows)
            comparison = json.loads((root / "comparison.json").read_text())
        self.assertEqual("compute-fast", comparison["winner"])
        self.assertEqual(3.0, comparison["candidates"][0]["median_inference_seconds"])

    def test_report_excludes_host_only_rows_from_model_latency(self):
        rows = [
            {"candidate_id": "hybrid", "wall_duration_seconds": 0,
             "prompt_eval_duration_ns": 0, "eval_duration_ns": 0,
             "generation_skipped": True, "case_id": "host", "clinical_fingerprint": "a",
             "finding_counts": {},
             "metrics": {"hard_gate_pass": True, "required_fact_recall": 1.0}},
            {"candidate_id": "hybrid", "wall_duration_seconds": 5,
             "prompt_eval_duration_ns": 1_000_000_000,
             "eval_duration_ns": 2_000_000_000,
             "case_id": "model", "clinical_fingerprint": "b", "finding_counts": {},
             "metrics": {"hard_gate_pass": True, "required_fact_recall": 1.0}},
        ]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run_campaign.write_report(root, rows)
            candidate = json.loads((root / "comparison.json").read_text())["candidates"][0]
        self.assertEqual(1, candidate["model_calls"])
        self.assertEqual(1, candidate["host_only_runs"])
        self.assertEqual(3.0, candidate["median_inference_seconds"])
        self.assertEqual(5.0, candidate["median_wall_seconds"])

    def test_redirects_are_rejected(self):
        with self.assertRaises(ValueError):
            run_campaign.NoRedirect().redirect_request(None, None, 302, "", {}, "https://example.invalid")

    def test_json_parser_rejects_duplicate_keys(self):
        with self.assertRaisesRegex(ValueError, "Duplicate JSON key"):
            run_campaign.loads_json('{"done":true,"done":false}')

    def test_default_run_artifacts_are_ignored(self):
        repo = BASE.parents[1]
        result = subprocess.run(["git", "-c", f"safe.directory={repo}", "check-ignore",
                                 "tools/ai-clinical-summary-eval/runs/example/report.md"],
                                cwd=repo, capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_candidate_validation_enforces_stricter_experiment_limits(self):
        case, _, _, _ = next(run_campaign.experiment_matrix(
            self.config, self.path, {"single-source"}, {"compact-12x180"}))
        generated = {"sections": [], "claims": [
            {"id": "c1", "text": "x" * 181, "source_ids": ["source-1"]}], "coverage": []}
        with patch.object(run_campaign, "validate_generated"):
            with self.assertRaisesRegex(ValueError, "character limit"):
                run_campaign.validate_candidate_output(
                    generated, case, {"max_claims": 12, "max_chars": 180})

    def test_complete_artifact_validation_rejects_duplicate_coverage(self):
        case, candidate, _, _ = next(run_campaign.experiment_matrix(
            self.config, self.path, {"single-source"}, {"compact-12x180"}))
        generated = {"sections": [{"id": "clinical_overview", "title": "Clinical overview",
                                    "claim_ids": ["c1"]}],
                     "claims": [{"id": "c1", "text": "Dizziness resolved after hydration",
                                 "source_ids": ["phone-1"]}],
                     "coverage": [
                         {"source_id": "phone-1", "status": "cited", "reason": "First reason"},
                         {"source_id": "phone-1", "status": "cited", "reason": "Second reason"}]}
        with self.assertRaisesRegex(ValueError, "duplicate coverage source"):
            run_campaign.validate_candidate_output(generated, case, candidate)

    def test_host_structured_output_materializes_host_owned_references(self):
        path = BASE / "campaigns" / "optimize-host-structured-2b.json"
        config = run_campaign.load_campaign(path)
        case, candidate, _, _ = next(run_campaign.experiment_matrix(
            config, path, {"medication-conflict"}))
        ledger = run_campaign.atomic_fact_ledger(case)
        self.assertEqual(4, len(ledger))
        self.assertEqual(["ledger-knee-1", "ledger-knee-2"],
                         [item["id"] for item in ledger[:2]])
        model_output = {"claims": [{"ledger_id": item["id"], "text": item["text"]}
                                   for item in ledger]}
        generated = run_campaign.materialize_candidate_output(model_output, case, candidate)
        run_campaign.validate_candidate_output(generated, case, candidate)
        self.assertTrue(run_campaign.evaluate(
            generated, case["bundle"]["sources"], case["expectations"])["metrics"]["hard_gate_pass"])
        conflict = next(claim for claim in generated["claims"] if "Amlodipine" in claim["text"])
        self.assertEqual(["visit-1", "meds-1"], conflict["source_ids"])
        self.assertEqual({"visit-1", "meds-1", "allergy-1", "admin-1"},
                         {entry["source_id"] for entry in generated["coverage"]})
        self.assertEqual("reviewed_not_cited",
                         next(entry["status"] for entry in generated["coverage"]
                              if entry["source_id"] == "admin-1"))

    def test_evidence_grounded_output_requires_exact_quotes(self):
        path = BASE / "campaigns" / "optimize-evidence-2b.json"
        config = run_campaign.load_campaign(path)
        case, candidate, _, _ = next(run_campaign.experiment_matrix(
            config, path, {"single-source"}))
        model_output = {"claims": [{
            "text": "Dizziness resolved after hydration without recurrence for three days.",
            "section_id": "clinical_overview",
            "evidence": [{"source_id": "phone-1",
                          "quote": "dizziness resolved after hydration, with no recurrence for three days"}]
        }, {
            "text": "No medication changes were made.",
            "section_id": "medications_allergies",
            "evidence": [{"source_id": "phone-1", "quote": "No medication changes were made."}]
        }]}
        generated = run_campaign.materialize_candidate_output(model_output, case, candidate)
        run_campaign.validate_candidate_output(generated, case, candidate)
        self.assertTrue(run_campaign.evaluate(
            generated, case["bundle"]["sources"], case["expectations"])["metrics"]["hard_gate_pass"])
        model_output["claims"][0]["evidence"][0]["quote"] = "invented quotation"
        with self.assertRaisesRegex(ValueError, "exact source quotation"):
            run_campaign.materialize_candidate_output(model_output, case, candidate)

    def test_evidence_grounded_output_rejects_changed_numeric_value(self):
        path = BASE / "campaigns" / "optimize-evidence-2b.json"
        config = run_campaign.load_campaign(path)
        case, candidate, _, _ = next(run_campaign.experiment_matrix(
            config, path, {"single-source"}))
        model_output = {"claims": [{
            "text": "Dizziness resolved with no recurrence for five days.",
            "section_id": "clinical_overview",
            "evidence": [{"source_id": "phone-1",
                          "quote": "dizziness resolved after hydration, with no recurrence for three days"}]
        }]}
        with self.assertRaisesRegex(ValueError, "numeric value"):
            run_campaign.materialize_candidate_output(model_output, case, candidate)

    def test_ledger_delta_uses_host_baseline_and_hides_nonclinical_source(self):
        path = BASE / "campaigns" / "optimize-delta-2b.json"
        config = run_campaign.load_campaign(path)
        case, candidate, _, _ = next(run_campaign.experiment_matrix(
            config, path, {"medication-conflict"}))
        model_input = run_campaign.candidate_input(case, candidate)
        self.assertEqual({"visit-1", "meds-1", "allergy-1"},
                         {source["id"] for source in model_input["sources"]})
        generated = run_campaign.materialize_candidate_output({"claims": []}, case, candidate)
        run_campaign.validate_candidate_output(generated, case, candidate)
        self.assertTrue(run_campaign.evaluate(
            generated, case["bundle"]["sources"], case["expectations"])["metrics"]["hard_gate_pass"])
        self.assertEqual("reviewed_not_cited",
                         next(item["status"] for item in generated["coverage"]
                              if item["source_id"] == "admin-1"))

    def test_27b_holdouts_accept_complete_quote_grounded_deltas(self):
        path = BASE / "campaigns" / "checkpoint-27b.json"
        config = run_campaign.load_campaign(path)
        matrix = run_campaign.experiment_matrix(
            config, path, selected_candidates={"host-ledger-evidence-delta-10x180-8k"})
        outputs = {
            "holdout-observations-followup": {"claims": [
                {"text": "BP 116/68 and SpO2 98% on room air.",
                 "section_id": "results_observations", "evidence": [{
                     "source_id": "review-1",
                     "quote": "BP 116/68, RR 14, temperature 36.7 C and SpO2 98% on room air."}]},
                {"text": "Follow-up with the cardiology clinic is planned in four weeks.",
                 "section_id": "plan_follow_up", "evidence": [{
                     "source_id": "review-1",
                     "quote": "Follow-up with the cardiology clinic is planned in four weeks."}]}
            ]},
            "holdout-lab-medication": {"claims": [
                {"text": "HbA1c improved from 57 mmol/mol to 48 mmol/mol.",
                 "section_id": "results_observations", "evidence": [{
                     "source_id": "clinic-1",
                     "quote": "HbA1c is 48 mmol/mol, improved from 57 mmol/mol."}]},
                {"text": "Empagliflozin 10 mg daily was started today.",
                 "section_id": "medications_allergies", "evidence": [{
                     "source_id": "clinic-1",
                     "quote": "Empagliflozin 10 mg daily was started today."}]},
                {"text": "Repeat the renal profile in two weeks.",
                 "section_id": "plan_follow_up", "evidence": [{
                     "source_id": "clinic-1", "quote": "Repeat renal profile in two weeks."}]}
            ]},
            "holdout-anticoagulation": {"claims": [
                {"text": "INR is 2.6 today.", "section_id": "results_observations",
                 "evidence": [{"source_id": "anticoag-1", "quote": "INR is 2.6 today."}]},
                {"text": "Restart warfarin 3 mg nightly today.",
                 "section_id": "medications_allergies", "evidence": [{
                     "source_id": "anticoag-1", "quote": "Restart warfarin 3 mg nightly today."}]},
                {"text": "Follow-up with the anticoagulation service in three days.",
                 "section_id": "plan_follow_up", "evidence": [{
                     "source_id": "anticoag-1",
                     "quote": "Follow-up with the anticoagulation service in three days."}]}
            ]}
        }
        for case, candidate, _, _ in matrix:
            with self.subTest(case=case["case_id"]):
                generated = run_campaign.materialize_candidate_output(
                    outputs[case["case_id"]], case, candidate)
                run_campaign.validate_candidate_output(generated, case, candidate)
                result = run_campaign.evaluate(
                    generated, case["bundle"]["sources"], case["expectations"])
                self.assertTrue(result["metrics"]["hard_gate_pass"], result["findings"])

    def test_normalized_delta_corrects_sections_and_groups_vitals(self):
        path = BASE / "campaigns" / "optimize-normalized-delta-2b.json"
        config = run_campaign.load_campaign(path)
        case, candidate, _, _ = next(run_campaign.experiment_matrix(
            config, path, {"holdout-observations-followup"}))
        output = {"claims": [
            {"text": "HR 74", "section_id": "clinical_overview",
             "evidence": [{"source_id": "review-1", "quote": "HR 74"}]},
            {"text": "BP 116/68", "section_id": "medications_allergies",
             "evidence": [{"source_id": "review-1", "quote": "BP 116/68"}]},
            {"text": "SpO2 98% on room air", "section_id": "clinical_overview",
             "evidence": [{"source_id": "review-1", "quote": "SpO2 98% on room air"}]},
            {"text": "Follow-up with the cardiology clinic is planned in four weeks.",
             "section_id": "clinical_overview", "evidence": [{
                 "source_id": "review-1",
                 "quote": "Follow-up with the cardiology clinic is planned in four weeks."}]}
        ]}
        generated = run_campaign.materialize_candidate_output(output, case, candidate)
        run_campaign.validate_candidate_output(generated, case, candidate)
        result = run_campaign.evaluate(
            generated, case["bundle"]["sources"], case["expectations"])
        self.assertTrue(result["metrics"]["hard_gate_pass"], result["findings"])
        additions = generated["claims"][1:]
        self.assertEqual(2, len(additions))
        self.assertIn("116/68", additions[0]["text"])
        self.assertIn("98%", additions[0]["text"])

    def test_normalized_delta_places_labs_medications_and_plans(self):
        path = BASE / "campaigns" / "optimize-normalized-delta-2b.json"
        config = run_campaign.load_campaign(path)
        case, candidate, _, _ = next(run_campaign.experiment_matrix(
            config, path, {"holdout-lab-medication"}))
        rows = [
            ("HbA1c is 48 mmol/mol, improved from 57 mmol/mol.",
             "HbA1c is 48 mmol/mol, improved from 57 mmol/mol."),
            ("Empagliflozin 10 mg daily was started today.",
             "Empagliflozin 10 mg daily was started today."),
            ("Repeat renal profile in two weeks.", "Repeat renal profile in two weeks.")]
        output = {"claims": [
            {"text": text, "section_id": "clinical_overview",
             "evidence": [{"source_id": "clinic-1", "quote": quote}]}
            for text, quote in rows]}
        generated = run_campaign.materialize_candidate_output(output, case, candidate)
        result = run_campaign.evaluate(
            generated, case["bundle"]["sources"], case["expectations"])
        self.assertTrue(result["metrics"]["hard_gate_pass"], result["findings"])
        sections = {claim_id: section["id"] for section in generated["sections"]
                    for claim_id in section["claim_ids"]}
        additions = generated["claims"][2:]
        self.assertEqual(["results_observations", "medications_allergies", "plan_follow_up"],
                         [sections[claim["id"]] for claim in additions])

    def test_normalized_delta_discards_ledger_duplicates_and_unclassified_text(self):
        path = BASE / "campaigns" / "optimize-normalized-delta-2b.json"
        config = run_campaign.load_campaign(path)
        case, candidate, _, _ = next(run_campaign.experiment_matrix(
            config, path, {"holdout-anticoagulation"}))
        output = {"claims": [
            {"text": "Warfarin was held after an INR of 4.8 on 8 September.",
             "section_id": "results_observations", "evidence": [{
                 "source_id": "anticoag-1",
                 "quote": "INR was 4.8 on 8 September, so warfarin was held"}]},
            {"text": "Some unrelated prose.", "section_id": "clinical_overview",
             "evidence": [{"source_id": "anticoag-1", "quote": "INR is 2.6 today"}]}
        ]}
        generated = run_campaign.materialize_candidate_output(output, case, candidate)
        self.assertEqual(1, len(generated["claims"]))

    def test_scorer_accepts_us_orthopnea_spelling(self):
        self.assertEqual("no orthopnoea", run_campaign.evaluate.__globals__["normalized"](
            "No orthopnea"))

    def test_normalized_delta_prioritizes_named_lab_over_change_verb(self):
        self.assertEqual("results_observations",
                         run_campaign.normalized_delta_section(
                             "TSH is 9.2 mIU/L, increased from 5.1 mIU/L."))
        self.assertEqual("medications_allergies",
                         run_campaign.normalized_delta_section(
                             "Levothyroxine was increased to 100 micrograms daily."))

    def test_thyroid_validation_materializes_complete_grounded_delta(self):
        path = BASE / "campaigns" / "validation-normalized-27b.json"
        config = run_campaign.load_campaign(path)
        case, candidate, _, _ = next(run_campaign.experiment_matrix(
            config, path, {"validation-thyroid-adjustment"}))
        texts = [
            "TSH is 9.2 mIU/L, increased from 5.1 mIU/L.",
            "Levothyroxine was increased from 75 micrograms to 100 micrograms daily today.",
            "Repeat thyroid function tests in six weeks.",
            "Follow-up with the endocrinology clinic is planned in eight weeks.",
        ]
        output = {"claims": [
            {"text": text, "section_id": "clinical_overview",
             "evidence": [{"source_id": "thyroid-1", "quote": text}]}
            for text in texts]}
        generated = run_campaign.materialize_candidate_output(output, case, candidate)
        run_campaign.validate_candidate_output(generated, case, candidate)
        result = run_campaign.evaluate(
            generated, case["bundle"]["sources"], case["expectations"])
        self.assertTrue(result["metrics"]["hard_gate_pass"], result["findings"])

    def test_guided_delta_aggregates_observations_and_discards_imaging(self):
        path = BASE / "campaigns" / "optimize-delta-2b-final.json"
        config = run_campaign.load_campaign(path)
        case, candidate, _, _ = next(run_campaign.experiment_matrix(
            config, path, {"repetitive-discharge"}))
        output = {"claims": [
            {"text": "Blood pressure is 128/78 mmHg.", "section_id": "medications_allergies",
             "evidence": [{"source_id": "ward-1", "quote": "BP 128/78"}]},
            {"text": "Oxygen saturation is 97% on air.", "section_id": "medications_allergies",
             "evidence": [{"source_id": "ward-1", "quote": "SpO2 97% on air"}]},
            {"text": "Heart rate is 82.", "section_id": "medications_allergies",
             "evidence": [{"source_id": "ward-1", "quote": "HR 82"}]},
            {"text": "Arrange repeat CXR in 48 hours.", "section_id": "medications_allergies",
             "evidence": [{"source_id": "plan-1", "quote": "Arrange repeat CXR in 48 hours"}]}
        ]}
        generated = run_campaign.materialize_candidate_output(output, case, candidate)
        run_campaign.validate_candidate_output(generated, case, candidate)
        result = run_campaign.evaluate(generated, case["bundle"]["sources"], case["expectations"])
        self.assertTrue(result["metrics"]["hard_gate_pass"], result["findings"])
        self.assertEqual(7, len(generated["claims"]))
        observation = generated["claims"][-1]["text"]
        self.assertIn("128/78", observation)
        self.assertIn("97%", observation)
        self.assertIn("82", observation)

    def test_guided_delta_rejects_measurement_value_absent_from_quote(self):
        path = BASE / "campaigns" / "optimize-delta-2b-final.json"
        config = run_campaign.load_campaign(path)
        case, candidate, _, _ = next(run_campaign.experiment_matrix(
            config, path, {"repetitive-discharge"}))
        output = {"claims": [{
            "text": "Blood pressure is 190/100 mmHg.",
            "section_id": "results_observations",
            "evidence": [{"source_id": "ward-1", "quote": "BP 128/78"}]
        }]}
        with self.assertRaisesRegex(ValueError, "value is not present"):
            run_campaign.materialize_candidate_output(output, case, candidate)

    def test_guided_delta_skips_model_when_no_eligible_detail_is_missing(self):
        path = BASE / "campaigns" / "optimize-delta-2b-final.json"
        config = run_campaign.load_campaign(path)
        case, candidate, seed, repetition = next(run_campaign.experiment_matrix(
            config, path, {"medication-conflict"}))
        self.assertFalse(run_campaign.candidate_requires_generation(case, candidate))
        draft = BASE.parent / "ai-clinical-summary-draft"
        with tempfile.TemporaryDirectory() as directory, \
                patch.object(run_campaign, "post_json", side_effect=AssertionError("network")):
            metadata = run_campaign.run_one(
                11434, config["model"], 1, Path(directory), case, candidate, seed,
                repetition, (draft / "prompt.txt").read_text(),
                json.loads((draft / "output-schema.json").read_text()))
        self.assertTrue(metadata["generation_skipped"])
        self.assertTrue(metadata["metrics"]["hard_gate_pass"])
        self.assertEqual("host_no_delta", metadata["done_reason"])

    def test_guided_delta_runs_model_when_measurements_are_missing(self):
        path = BASE / "campaigns" / "optimize-delta-2b-final.json"
        config = run_campaign.load_campaign(path)
        case, candidate, _, _ = next(run_campaign.experiment_matrix(
            config, path, {"repetitive-discharge"}))
        self.assertTrue(run_campaign.candidate_requires_generation(case, candidate))

        partially_ledgered = deepcopy(case)
        partially_ledgered["bundle"]["fact_ledger"].append({
            "id": "ledger-heart-rate", "category": "Observations",
            "text": "Heart rate is 82.", "source_ids": ["ward-1"]})
        self.assertTrue(run_campaign.candidate_requires_generation(partially_ledgered, candidate))

    def test_guided_delta_schema_rejects_ledger_over_claim_limit(self):
        path = BASE / "campaigns" / "optimize-delta-2b-final.json"
        config = run_campaign.load_campaign(path)
        case, candidate, _, _ = next(run_campaign.experiment_matrix(
            config, path, {"repetitive-discharge"}))
        with self.assertRaisesRegex(ValueError, "Atomic ledger exceeds"):
            run_campaign.candidate_schema({}, dict(candidate, max_claims=1), case)

    def test_generation_timeout_is_recorded_as_a_failed_row(self):
        case, candidate, seed, repetition = next(run_campaign.experiment_matrix(
            self.config, self.path, {"single-source"}, {"compact-12x180"}))
        draft = BASE.parent / "ai-clinical-summary-draft"
        with tempfile.TemporaryDirectory() as directory, \
                patch.object(run_campaign, "post_json", side_effect=TimeoutError("timed out")):
            metadata = run_campaign.run_one(
                11434, self.config["model"], 1, Path(directory), case, candidate, seed,
                repetition, (draft / "prompt.txt").read_text(),
                json.loads((draft / "output-schema.json").read_text()))
            evaluation = json.loads(next(Path(directory).glob("**/evaluation.json")).read_text())
            request = json.loads(next(Path(directory).glob("**/request.json")).read_text())
        self.assertFalse(metadata["metrics"]["hard_gate_pass"])
        self.assertEqual("transport_error", metadata["done_reason"])
        self.assertEqual("GENERATION_ERROR", evaluation["findings"][0]["code"])
        self.assertEqual(0, request["keep_alive"])

    def test_campaign_stops_after_transport_error_to_protect_later_timings(self):
        failed = {
            "candidate_id": "compact-12x180", "case_id": "single-source",
            "wall_duration_seconds": 1.0, "done_reason": "transport_error",
            "clinical_fingerprint": "failed", "finding_counts": {"GENERATION_ERROR": 1},
            "metrics": {"hard_gate_pass": False, "required_fact_recall": 0.0}
        }
        with tempfile.TemporaryDirectory() as directory, \
                patch.object(run_campaign, "post_json", return_value={}), \
                patch.object(run_campaign, "run_one", return_value=failed) as run_one:
            output = Path(directory) / "run"
            with contextlib.redirect_stderr(io.StringIO()) as stderr, \
                    self.assertRaises(SystemExit) as stopped:
                run_campaign.main(["--config", str(self.path), "--output", str(output)])
            self.assertEqual(2, stopped.exception.code)
            self.assertIn("Ollama may continue", stderr.getvalue())
            self.assertEqual(1, run_one.call_count)
            self.assertTrue((output / "report.md").is_file())

    def test_invalid_response_is_recorded_without_aborting_campaign(self):
        case, candidate, seed, repetition = next(run_campaign.experiment_matrix(
            self.config, self.path, {"single-source"}, {"compact-12x180"}))
        draft = BASE.parent / "ai-clinical-summary-draft"
        with tempfile.TemporaryDirectory() as directory, \
                patch.object(run_campaign, "post_json", return_value=[]):
            metadata = run_campaign.run_one(
                11434, self.config["model"], 1, Path(directory), case, candidate, seed,
                repetition, (draft / "prompt.txt").read_text(),
                json.loads((draft / "output-schema.json").read_text()))
            evaluation = json.loads(next(Path(directory).glob("**/evaluation.json")).read_text())
        self.assertFalse(metadata["metrics"]["hard_gate_pass"])
        self.assertEqual("invalid_response", metadata["done_reason"])
        self.assertEqual("INVALID_DRAFT", evaluation["findings"][0]["code"])

    def test_resume_requires_identical_candidate_prompt_schema_case_and_scorer(self):
        case, candidate, seed, repetition = next(run_campaign.experiment_matrix(
            self.config, self.path, {"single-source"}, {"compact-12x180"}))
        draft = BASE.parent / "ai-clinical-summary-draft"
        prompt = (draft / "prompt.txt").read_text()
        schema = json.loads((draft / "output-schema.json").read_text())
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            with patch.object(run_campaign, "post_json", side_effect=TimeoutError("timed out")):
                original = run_campaign.run_one(
                    11434, self.config["model"], 1, output, case, candidate, seed,
                    repetition, prompt, schema)
            with patch.object(run_campaign, "post_json", side_effect=AssertionError("network")):
                resumed = run_campaign.run_one(
                    11434, self.config["model"], 1, output, case, candidate, seed,
                    repetition, prompt, schema)
            self.assertEqual(original, resumed)

            mutations = [
                (case, dict(candidate, max_claims=11), prompt, schema),
                (case, candidate, prompt + "\nChanged prompt.\n", schema),
                (case, candidate, prompt, dict(schema, title="Changed schema")),
                (dict(case, description="Changed case"), candidate, prompt, schema),
            ]
            for changed_case, changed_candidate, changed_prompt, changed_schema in mutations:
                with self.subTest(candidate=changed_candidate, prompt=changed_prompt[-20:]), \
                        self.assertRaisesRegex(ValueError, "Preserved run inputs differ"):
                    run_campaign.run_one(
                        11434, self.config["model"], 1, output, changed_case,
                        changed_candidate, seed, repetition, changed_prompt, changed_schema)
            with patch.object(run_campaign, "scorer_sha256", return_value="changed-scorer"), \
                    self.assertRaisesRegex(ValueError, "scorer_sha256"):
                run_campaign.run_one(
                    11434, self.config["model"], 1, output, case, candidate, seed,
                    repetition, prompt, schema)


if __name__ == "__main__":
    unittest.main()
