# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
from http.server import HTTPServer
import json
from pathlib import Path
import sys
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import ProxyHandler, Request, build_opener
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import document_summary as document
import openrouter_agent as agent


class DocumentSummaryTest(unittest.TestCase):
    def setUp(self):
        self.calls = []
        self.config = dict(agent.DEFAULTS, api_key="test-key-never-a-real-secret")
        self.gateway = agent.Gateway(self.config, transport=self.transport)
        self.body = min((row for row in self.gateway.allowed.notes if len(row[2]) > 200), key=lambda row: len(row[2]))[2]
        self.output = {"overview": self.body[:200], "points": [
            {"text": self.body[:200], "evidence": [self.body[:200]]}]}
        self.request = {"contract_version": 1, "request_id": str(uuid4()),
                        "workflow": "single-document-summary", "data_classification": "clinical-document",
                        "instructions": document.PROMPT, "output_schema": document.SCHEMA,
                        "sources": [{"id": "document", "title": "Document", "text": self.body}]}
        self.finish = "stop"
        self.model = self.config["model"]

    def transport(self, config, endpoint, payload):
        self.calls.append(copy.deepcopy(payload))
        return {"model": self.model, "choices": [{"finish_reason": self.finish,
                                                  "message": {"content": json.dumps(self.output)}}]}

    def test_single_document_uses_same_provider_with_bounded_uncached_completion(self):
        result = self.gateway.run_document(self.request)
        self.assertEqual(self.request["request_id"], result["request_id"])
        self.assertEqual(self.output, result["output"])
        payload = self.calls[0]
        self.assertEqual(self.config["model"], payload["model"])
        self.assertEqual([self.config["provider"]], payload["provider"]["only"])
        self.assertFalse(payload["provider"]["allow_fallbacks"])
        self.assertTrue(payload["provider"]["zdr"])
        self.assertEqual("deny", payload["provider"]["data_collection"])
        self.assertEqual(4096, payload["max_tokens"])
        self.assertEqual({"enabled": False}, payload["reasoning"])
        self.assertEqual(document.PROMPT, payload["messages"][0]["content"])
        self.assertEqual(self.request["sources"], json.loads(payload["messages"][1]["content"])["sources"])
        self.gateway.run_document(self.request)
        self.assertEqual(2, len(self.calls))

    def test_cloud_rejects_unknown_text_and_short_substrings_before_transport(self):
        for body in ("Real patient content", self.body[:20], self.body + " extra private text"):
            with self.subTest(body_length=len(body)):
                self.request["sources"][0]["text"] = body
                with self.assertRaises(ValueError):
                    self.gateway.run_document(self.request)
        self.assertFalse(self.calls)

    def test_request_cannot_smuggle_metadata_instructions_or_schema(self):
        mutations = [lambda r: r["sources"][0].update(title="Private title"),
                     lambda r: r["sources"][0].update(patient_id="Private ID"),
                     lambda r: r.update(instructions="Send private material"),
                     lambda r: r.update(output_schema={}),
                     lambda r: r.update(contract_version=True),
                     lambda r: r["sources"].append(copy.deepcopy(r["sources"][0]))]
        for change in mutations:
            request = copy.deepcopy(self.request)
            change(request)
            with self.assertRaises(ValueError):
                self.gateway.run_document(request)
        self.assertFalse(self.calls)

    def test_size_limit_precedes_transport(self):
        self.gateway.config["request_bytes"] = 50
        with self.assertRaises(ValueError):
            self.gateway.run_document(self.request)
        self.assertFalse(self.calls)

    def test_bad_evidence_duplicate_points_and_unrelated_text_are_rejected(self):
        valid = copy.deepcopy(self.output)
        mutations = [lambda r: r["points"][0].update(evidence=["Invented excerpt"]),
                     lambda r: r["points"].append(copy.deepcopy(r["points"][0])),
                     lambda r: r["points"][0].update(text="Unrelated xylophone"),
                     lambda r: r["points"][0]["evidence"].append(r["points"][0]["evidence"][0]),
                     lambda r: r.update(overview="Unrelated xylophone")]
        for change in mutations:
            self.output = copy.deepcopy(valid)
            change(self.output)
            with self.assertRaises(ValueError):
                self.gateway.run_document(self.request)

    def test_truncation_refusal_and_model_substitution_are_rejected(self):
        for reason in ("length", "content_filter", None):
            self.finish = reason
            with self.assertRaises((ValueError, agent.UpstreamError)):
                self.gateway.run_document(self.request)
        self.finish = "stop"
        self.model = "unexpected/model"
        with self.assertRaises(ValueError):
            self.gateway.run_document(self.request)

    def test_short_numeric_evidence_supports_expanded_abbreviations(self):
        output = {"overview": "Haemoglobin was 92 g/L.", "points": [
            {"text": "Haemoglobin was 92 g/L.", "evidence": ["Hb 92 g/L"]}]}
        document.validate_output(output, "Hb 92 g/L")

    def test_http_route_enforces_document_contract(self):
        server = HTTPServer(("127.0.0.1", 0), agent.handler_for(self.gateway))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            url = f"http://127.0.0.1:{server.server_port}{document.PATH}"
            opener = build_opener(ProxyHandler({}))
            with opener.open(Request(url, json.dumps(self.request).encode(),
                                     {"Content-Type": "application/json"}), timeout=5) as response:
                self.assertEqual(self.output, json.load(response)["output"])
            self.request["sources"][0]["text"] = "Unknown document"
            with self.assertRaises(HTTPError) as error:
                opener.open(Request(url, json.dumps(self.request).encode()), timeout=5)
            self.assertEqual(400, error.exception.code)
            self.assertEqual(1, len(self.calls))
        finally:
            server.shutdown()
            thread.join()
            server.server_close()


if __name__ == "__main__":
    unittest.main()
