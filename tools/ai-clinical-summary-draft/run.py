# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Generate a synthetic research artifact with local Qwen, outside CARLOS."""
import argparse
import copy
from datetime import datetime, timezone
import json
from pathlib import Path
import uuid
from urllib.error import URLError
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

from validate_artifact import require, validate, validate_generated

ROOT = Path(__file__).resolve().parent
LOCAL_MODELS = ("qwen3.5:0.8b", "qwen3.5:2b", "qwen3.5:4b", "qwen3.5:9b",
                "qwen3.5:27b", "qwen3.5:35b", "qwen3.5:122b")
MAX_RESPONSE_BYTES = 4 * 1024 * 1024


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("Ollama redirects are not permitted")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "Duplicate JSON key: " + key)
        result[key] = value
    return result


def loads_json(value):
    return json.loads(value, object_pairs_hook=unique_object)


def request_json(port, endpoint, payload):
    # Explicit numeric loopback and no proxies/redirects prevent external HTTP routing.
    opener = build_opener(ProxyHandler({}), NoRedirect())
    request = Request(f"http://127.0.0.1:{port}/api/{endpoint}",
                      data=json.dumps(payload).encode("utf-8"),
                      headers={"Content-Type": "application/json"}, method="POST")
    with opener.open(request, timeout=300) as response:
        body = response.read(MAX_RESPONSE_BYTES + 1)
    require(len(body) <= MAX_RESPONSE_BYTES, "Ollama response exceeds size limit")
    return loads_json(body)


def build_artifact(bundle, generated, model, artifact_id, timestamp, allow_empty=False):
    validate_generated(generated, bundle["sources"], allow_empty)
    artifact = {
        "schema_version": 1, "artifact_id": artifact_id, "generated_at": timestamp,
        "model": model, "workflow": "patient-overview",
        "patient_context": copy.deepcopy(bundle["patient_context"]),
        "sources": copy.deepcopy(bundle["sources"]),
        "fact_ledger": copy.deepcopy(bundle["fact_ledger"]),
        **copy.deepcopy(generated),
        "validation": [
            {"severity": "pass", "code": "reference_structure",
             "message": "Claim references, section membership and source coverage passed deterministic checks.",
             "source_ids": []},
            {"severity": "warning", "code": "clinical_review_required",
             "message": "Model prose has not been clinically verified. Citation existence does not prove support, accuracy or completeness.",
             "source_ids": []}
        ]
    }
    return validate(artifact)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", choices=LOCAL_MODELS, default="qwen3.5:4b")
    parser.add_argument("--port", type=int, default=11434)
    parser.add_argument("--input", type=Path, default=ROOT / "sample-input.json")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)
    try:
        require(1 <= args.port <= 65535, "Port must be between 1 and 65535")
        bundle = loads_json(args.input.read_text(encoding="utf-8"))
        require(isinstance(bundle, dict) and set(bundle) == {"patient_context", "sources", "fact_ledger"},
                "Input must contain patient_context, sources and fact_ledger only")
        timestamp = datetime.now(timezone.utc).isoformat()
        run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
        # Validate the immutable bundle before constructing any network request.
        empty = {"sections": [], "claims": [], "coverage": [
            {"source_id": source["id"], "status": "reviewed_not_cited",
             "reason": "Input preflight only: " + source["id"]}
            for source in bundle["sources"]
        ]}
        build_artifact(bundle, empty, args.model, run_id, timestamp, allow_empty=True)
        schema = loads_json((ROOT / "output-schema.json").read_text(encoding="utf-8"))
        prompt = (ROOT / "prompt.txt").read_text(encoding="utf-8")
        payload = {"model": args.model, "system": prompt,
                   "prompt": json.dumps({"sources": bundle["sources"]}),
                   "format": schema, "stream": False, "think": False, "keep_alive": "5m",
                   "options": {"temperature": 0, "num_ctx": 65536, "num_predict": 4096}}
        output = ROOT / "runs" / run_id
        require(output.resolve().is_relative_to(ROOT), "Runs directory must stay inside tool directory")
        if args.dry_run:
            print(json.dumps({"dry_run": True, "model": args.model,
                              "endpoint": f"http://127.0.0.1:{args.port}/api/generate",
                              "artifact": str(output / "artifact.json")}, indent=2))
            return 0
        info = request_json(args.port, "show", {"model": args.model})
        require(isinstance(info, dict) and not info.get("remote_model") and not info.get("remote_host"),
                "Cloud-backed models are not permitted")
        output.mkdir(parents=True, exist_ok=False)
        (output / "request.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        response = request_json(args.port, "generate", payload)
        (output / "response.json").write_text(json.dumps(response, indent=2) + "\n", encoding="utf-8")
        require(isinstance(response, dict) and response.get("done") is True
                and response.get("done_reason") == "stop" and response.get("model") == args.model
                and isinstance(response.get("response"), str), "Generation did not complete")
        generated = loads_json(response["response"])
        artifact = build_artifact(bundle, generated, args.model, run_id, timestamp)
        (output / "artifact.json").write_text(json.dumps(artifact, indent=2) + "\n", encoding="utf-8")
        print(f"Research artifact: {output / 'artifact.json'}")
        return 0
    except (ValueError, OSError, URLError, KeyError, TypeError) as error:
        parser.exit(1, f"Generation rejected: {error}\n")


if __name__ == "__main__":
    raise SystemExit(main())
