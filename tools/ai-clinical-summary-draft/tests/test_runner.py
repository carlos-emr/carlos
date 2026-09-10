# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import contextlib
import copy
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
sys.path.insert(0, str(ROOT))
import run
from validate_artifact import validate

FIXTURE = REPO / "src/main/resources/clinical/summary/synthetic-overview.json"


class RunnerTest(unittest.TestCase):
    def setUp(self):
        self.artifact = json.loads(FIXTURE.read_text(encoding="utf-8"))
        self.bundle = json.loads((ROOT / "sample-input.json").read_text(encoding="utf-8"))
        self.generated = {
            "sections": [
                {"id": "clinical_overview", "title": "Clinical overview",
                 "claim_ids": ["claim-1"]},
                {"id": "medications_allergies", "title": "Medications and allergies",
                 "claim_ids": ["claim-2", "claim-3"]},
            ],
            "claims": copy.deepcopy(self.artifact["claims"]),
            "coverage": copy.deepcopy(self.artifact["coverage"]),
        }

    def test_example_and_input_are_consistent(self):
        validate(self.artifact)
        for key, value in self.bundle.items():
            self.assertEqual(value, self.artifact[key])

    def test_dry_run_never_calls_ollama_or_creates_output(self):
        with patch.object(run, "request_json", side_effect=AssertionError("network")), io.StringIO() as output:
            with contextlib.redirect_stdout(output):
                self.assertEqual(0, run.main(["--dry-run"]))
            config = json.loads(output.getvalue())
        self.assertTrue(config["dry_run"])
        self.assertFalse(Path(config["artifact"]).parent.exists())
        self.assertEqual("http://127.0.0.1:11434/api/generate", config["endpoint"])

    def test_output_is_ignored(self):
        result = subprocess.run(["git", "-c", f"safe.directory={REPO}", "check-ignore",
                                 "tools/ai-clinical-summary-draft/runs/example/artifact.json"],
                                cwd=REPO, capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_generated_artifact_preserves_source_and_ledger(self):
        result = run.build_artifact(self.bundle, self.generated, "qwen3.5:4b",
                                    "run-1", "2026-09-08T00:00:00Z")
        for key in self.bundle:
            self.assertEqual(self.bundle[key], result[key])
        self.assertEqual("clinical_review_required", result["validation"][1]["code"])

    def test_model_cannot_supply_sources_or_validation(self):
        for field in ("sources", "validation", "patient_context", "fact_ledger"):
            generated = dict(self.generated, **{field: []})
            with self.subTest(field=field), self.assertRaises(ValueError):
                run.build_artifact(self.bundle, generated, "qwen3.5:4b", "run-1", "2026-09-08T00:00:00Z")

    def test_generated_quality_failures_are_rejected(self):
        duplicate = copy.deepcopy(self.generated)
        repeated = copy.deepcopy(duplicate["claims"][0])
        repeated["id"] = "claim-duplicate"
        duplicate["claims"].append(repeated)
        duplicate["sections"][0]["claim_ids"].append("claim-duplicate")

        ungrounded = copy.deepcopy(self.generated)
        ungrounded["claims"][0]["text"] = "Migraine with photophobia is worsening."

        bad_section = copy.deepcopy(self.generated)
        bad_section["sections"][0] = {
            "id": "patient_identity", "title": "Patient Identity", "claim_ids": ["claim-1"]}

        repeated_reason = copy.deepcopy(self.generated)
        repeated_reason["coverage"][1]["reason"] = repeated_reason["coverage"][0]["reason"]

        invalid = [duplicate, ungrounded, bad_section, repeated_reason]
        for text in ("Source note ID: imported-note-1", "Demographic number: 3003",
                     "Subject: GP contact for discharge planning", "Type: Medicine Inpatients"):
            metadata = copy.deepcopy(self.generated)
            metadata["claims"][0]["text"] = text
            invalid.append(metadata)

        for generated in invalid:
            with self.subTest(generated=generated), self.assertRaises(ValueError):
                run.build_artifact(self.bundle, generated, "qwen3.5:4b",
                                   "run-1", "2026-09-08T00:00:00Z")

    def test_contract_rejects_bad_provenance(self):
        mutations = [
            lambda a: a["claims"][0].update(source_ids=[]),
            lambda a: a["claims"][0].update(source_ids=["foreign"]),
            lambda a: a["sources"][0].update(patient_id="other-patient"),
            lambda a: a["sources"].append(copy.deepcopy(a["sources"][0])),
            lambda a: a["coverage"].pop(),
            lambda a: a["coverage"][0].update(status="excluded"),
            lambda a: a["sections"].pop(),
            lambda a: a["fact_ledger"][0].update(source_ids=[]),
            lambda a: a["validation"][0].update(severity="unknown"),
            lambda a: a["patient_context"].update(synthetic=False),
        ]
        for i, mutate in enumerate(mutations):
            artifact = copy.deepcopy(self.artifact)
            mutate(artifact)
            with self.subTest(mutation=i), self.assertRaises(ValueError):
                validate(artifact)

    def test_local_generation_round_trip_and_invalid_output(self):
        for valid in (True, False):
            with self.subTest(valid=valid), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                for name in ("sample-input.json", "prompt.txt", "output-schema.json"):
                    (root / name).write_bytes((ROOT / name).read_bytes())
                generated = copy.deepcopy(self.generated)
                if not valid:
                    generated["claims"][0]["source_ids"] = ["invented"]
                response = {"done": True, "done_reason": "stop", "model": "qwen3.5:4b",
                            "response": json.dumps(generated)}
                with patch.object(run, "ROOT", root), patch.object(run, "request_json", side_effect=[{}, response]):
                    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                        if valid:
                            self.assertEqual(0, run.main([]))
                        else:
                            with self.assertRaises(SystemExit):
                                run.main([])
                artifacts = list(root.glob("runs/*/artifact.json"))
                self.assertEqual(1 if valid else 0, len(artifacts))
                if valid:
                    validate(json.loads(artifacts[0].read_text()))

    def test_generation_request_matches_runtime_boundary_and_rejects_bad_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ("sample-input.json", "prompt.txt", "output-schema.json"):
                (root / name).write_bytes((ROOT / name).read_bytes())
            response = {"done": True, "done_reason": "stop", "model": "qwen3.5:4b",
                        "response": json.dumps(self.generated)}
            with patch.object(run, "ROOT", root), \
                    patch.object(run, "request_json", side_effect=[{}, response]) as request:
                with contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(0, run.main([]))
            payload = request.call_args_list[1].args[2]
            self.assertEqual({"sources"}, set(json.loads(payload["prompt"])))
            self.assertEqual(65536, payload["options"]["num_ctx"])
            self.assertEqual(4096, payload["options"]["num_predict"])
            self.assertEqual("5m", payload["keep_alive"])

        for mutation in ({"model": "qwen3.5:9b"}, {"done_reason": "unload"}, {"done": False}):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                for name in ("sample-input.json", "prompt.txt", "output-schema.json"):
                    (root / name).write_bytes((ROOT / name).read_bytes())
                invalid = dict(response, **mutation)
                with patch.object(run, "ROOT", root), \
                        patch.object(run, "request_json", side_effect=[{}, invalid]), \
                        contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                    run.main([])
                self.assertFalse(list(root.glob("runs/*/artifact.json")))

    def test_json_parser_rejects_duplicate_keys(self):
        with self.assertRaisesRegex(ValueError, "Duplicate JSON key"):
            run.loads_json('{"done":true,"done":false}')

    def test_cloud_model_and_redirect_are_rejected(self):
        with patch.object(run, "request_json", return_value={"remote_model": "cloud-model"}):
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                run.main([])
        with self.assertRaises(ValueError):
            run.NoRedirect().redirect_request(None, None, 302, "", {}, "https://external.invalid")

    def test_unknown_model_or_invalid_port_rejected_before_network(self):
        with patch.object(run, "request_json", side_effect=AssertionError("network")):
            for args in (["--model", "qwen3.5:cloud"], ["--port", "0"], ["--port", "65536"]):
                with self.subTest(args=args), contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                    run.main(args)


if __name__ == "__main__":
    unittest.main()
