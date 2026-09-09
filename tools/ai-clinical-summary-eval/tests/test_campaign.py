# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import contextlib
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

    def test_redirects_are_rejected(self):
        with self.assertRaises(ValueError):
            run_campaign.NoRedirect().redirect_request(None, None, 302, "", {}, "https://example.invalid")

    def test_default_run_artifacts_are_ignored(self):
        repo = BASE.parents[1]
        result = subprocess.run(["git", "-c", f"safe.directory={repo}", "check-ignore",
                                 "tools/ai-clinical-summary-eval/runs/example/report.md"],
                                cwd=repo, capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_candidate_validation_enforces_stricter_experiment_limits(self):
        generated = {"sections": [], "claims": [
            {"id": "c1", "text": "x" * 181, "source_ids": ["source-1"]}], "coverage": []}
        with patch.object(run_campaign, "validate_generated"):
            with self.assertRaisesRegex(ValueError, "character limit"):
                run_campaign.validate_candidate_output(
                    generated, [], {"max_claims": 12, "max_chars": 180})

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
        self.assertFalse(metadata["metrics"]["hard_gate_pass"])
        self.assertEqual("transport_error", metadata["done_reason"])
        self.assertEqual("GENERATION_ERROR", evaluation["findings"][0]["code"])


if __name__ == "__main__":
    unittest.main()
