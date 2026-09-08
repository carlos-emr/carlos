# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
import importlib.util
import json
from pathlib import Path
import unittest
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("example_agent", ROOT / "example_agent.py")
agent = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(agent)


class ExampleAgentTest(unittest.TestCase):
    def setUp(self):
        self.request = {
            "contract_version": 1, "request_id": str(uuid4()),
            "workflow": "patient-overview", "data_classification": "verified-synthetic",
            "instructions": (ROOT / "prompt.txt").read_text(),
            "output_schema": json.loads((ROOT / "output-schema.json").read_text()),
            "sources": json.loads((ROOT / "sample-input.json").read_text())["sources"],
        }

    def test_echoes_identity_and_returns_only_cited_output(self):
        original = copy.deepcopy(self.request)
        response = agent.run_agent(self.request)
        self.assertEqual(response["request_id"], self.request["request_id"])
        self.assertEqual(response["contract_version"], 1)
        self.assertEqual(response["status"], "completed")
        self.assertEqual(set(response["output"]), {"sections", "claims", "coverage"})
        self.assertEqual(len(response["output"]["claims"]), len(self.request["sources"]))
        self.assertEqual({c["source_id"] for c in response["output"]["coverage"]},
                         {s["id"] for s in self.request["sources"]})
        self.assertIn("no AI", response["output"]["sections"][0]["title"])
        self.assertEqual(original, self.request)

    def test_rejects_unknown_contracts_and_non_synthetic_data(self):
        for key, value in (("contract_version", 2), ("contract_version", True),
                           ("data_classification", "patient-data"), ("request_id", "bad-id"),
                           ("workflow", "chart-write"), ("sources", []), ("instructions", "")):
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                agent.run_agent(dict(self.request, **{key: value}))

    def test_rejects_unknown_fields(self):
        with self.assertRaises(ValueError):
            agent.run_agent(dict(self.request, session_token="not-allowed"))

    def test_rejects_duplicate_and_mixed_patient_sources(self):
        self.request["sources"].append(copy.deepcopy(self.request["sources"][0]))
        with self.assertRaises(ValueError):
            agent.run_agent(self.request)
        self.request["sources"][-1]["id"] = "other-note"
        self.request["sources"][-1]["patient_id"] = "another-patient"
        with self.assertRaises(ValueError):
            agent.run_agent(self.request)

    def test_rejects_duplicate_json_keys(self):
        with self.assertRaises(ValueError):
            json.loads('{"contract_version": 1, "contract_version": 2}',
                       object_pairs_hook=agent.unique_object)
