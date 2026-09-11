#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Deterministic agent API example. No model, inference, persistence or chart access."""

import argparse
import json
import re
from http.server import BaseHTTPRequestHandler, HTTPServer
from uuid import UUID

MAX_REQUEST_BYTES = 60000
PATH = "/v1/clinical-summary"


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


def run_agent(request):
    """Replace this function with your agent, preserving the response contract."""
    expected = {"contract_version", "request_id", "workflow", "data_classification",
                "instructions", "sources", "output_schema"}
    if not isinstance(request, dict) or set(request) != expected:
        raise ValueError("Invalid request fields")
    if type(request["contract_version"]) is not int or request["contract_version"] != 1:
        raise ValueError("Unsupported contract")
    if (request["workflow"] != "patient-overview"
            or request["data_classification"] != "verified-synthetic"):
        raise ValueError("Unsupported workflow or data classification")
    if not isinstance(request["request_id"], str):
        raise ValueError("Invalid request ID")
    UUID(request["request_id"])
    if not isinstance(request["instructions"], str) or not request["instructions"].strip():
        raise ValueError("Missing instructions")
    if not isinstance(request["output_schema"], dict):
        raise ValueError("Missing output schema")
    sources = request["sources"]
    if not isinstance(sources, list) or not 1 <= len(sources) <= 60:
        raise ValueError("Invalid sources")
    ids = set()
    patient_ids = set()
    claims = []
    for index, source in enumerate(sources):
        if not isinstance(source, dict) or set(source) != {"id", "patient_id", "title", "date", "text"}:
            raise ValueError("Invalid source fields")
        if any(not isinstance(value, str) or not value.strip() for value in source.values()):
            raise ValueError("Invalid source values")
        source_id = source["id"]
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", source_id) or source_id in ids:
            raise ValueError("Invalid or duplicate source ID")
        ids.add(source_id)
        patient_ids.add(source["patient_id"])
        claims.append({"id": f"demo-{index + 1}",
                       "text": f"The contract demonstration received {source['title']} dated {source['date']}.",
                       "source_ids": [source_id]})
    if len(patient_ids) != 1:
        raise ValueError("Mixed patient sources")
    return {
        "contract_version": 1,
        "request_id": request["request_id"],
        "status": "completed",
        "output": {
            "sections": [{"id": "clinical_overview", "title": "Clinical overview",
                          "claim_ids": [claim["id"] for claim in claims]}],
            "claims": claims,
            "coverage": [{"source_id": source["id"], "status": "cited",
                          "reason": f"Contract demonstration listed {source['id']}; no clinical analysis."}
                         for source in sources],
        },
    }


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass  # Do not log request bodies, paths, identifiers or agent output.

    def do_POST(self):
        self.connection.settimeout(10)
        if self.path != PATH:
            self.send_error(404)
            return
        try:
            lengths = self.headers.get_all("Content-Length", [])
            if len(lengths) != 1 or self.headers.get("Transfer-Encoding"):
                raise ValueError("Expected fixed-length request")
            length = int(lengths[0])
            if not 0 < length <= MAX_REQUEST_BYTES:
                raise ValueError("Invalid request size")
            raw = self.rfile.read(length)
            if len(raw) != length:
                raise ValueError("Incomplete request")
            request = json.loads(raw, object_pairs_hook=unique_object)
            response = json.dumps(run_agent(request)).encode("utf-8")
        except (ValueError, TypeError, OSError):
            self.send_error(400, "Invalid agent request")
            return
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=11435)
    args = parser.parse_args()
    if not 1 <= args.port <= 65535:
        parser.error("port must be between 1 and 65535")
    with HTTPServer(("127.0.0.1", args.port), Handler) as server:
        print(f"Contract demo (no AI): http://127.0.0.1:{args.port}{PATH}", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
