# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Generate a synthetic research artifact with local Qwen, outside CARLOS."""
import argparse
import copy
from datetime import datetime, timezone
import json
from pathlib import Path
import uuid
import host_checks
import pipeline
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
        ] + [{"severity": "warning", "code": "date_not_in_cited_sources",
              "message": "Statement " + finding["claim_id"] + " asserts " + finding["asserted"]
                         + ", which none of its cited notes carries (" + ", ".join(finding["allowed"]) + ").",
              "source_ids": finding["source_ids"]}
             for finding in host_checks.date_findings(generated, bundle["sources"])]
        + [{"severity": "warning", "code": "undocumented_medication_change",
            "message": "Statement " + finding["claim_id"] + " describes a switch or change between "
                       + " and ".join(finding["drugs"]) + ", which no note records.",
            "source_ids": finding["source_ids"]}
           for finding in host_checks.undocumented_changes(generated, bundle["sources"],
                                                           host_checks.configured_classes())]
        + [{"severity": "warning", "code": "statement_names_person_or_identifier",
            "message": "Statement " + finding["claim_id"] + " names a person or carries a patient identifier ("
                       + ", ".join(finding["terms"]) + ").", "source_ids": []}
           for finding in host_checks.name_findings(generated, bundle["sources"], bundle["patient_context"].get("label"))]
        + [{"severity": "warning", "code": "statements_restate_each_other",
            "message": "Statement " + shorter + " restates statement " + longer + " in another section.",
            "source_ids": []} for longer, shorter, _j, _c in host_checks.near_duplicates(generated)]
        + [{"severity": "warning", "code": "statements_repaired",
            "message": f"{count} statement{'' if count == 1 else 's'} {'was' if count == 1 else 'were'} rewritten by the "
                       "agent after host checks; their IDs begin with repaired-.", "source_ids": []}
           for count in [sum(1 for claim in generated["claims"] if claim["id"].startswith("repaired-"))] if count]
        + [{"severity": "warning", "code": "sources_not_cited_without_reason",
            "message": "The draft neither cites nor explains setting aside these notes; read them directly.",
            "source_ids": unexplained} for unexplained in [pipeline.unexplained_sources(generated)] if unexplained]
    }
    return validate(artifact)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", choices=LOCAL_MODELS, default="qwen3.5:2b")
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
                   "options": {"temperature": 0, "num_ctx": 16384, "num_predict": 4096}}
        output = ROOT / "runs" / run_id
        require(output.resolve().is_relative_to(ROOT), "Runs directory must stay inside tool directory")
        if args.dry_run:
            print(json.dumps({"dry_run": True, "model": args.model,
                              "endpoint": f"http://127.0.0.1:{args.port}/api/generate",
                              "artifact": str(output / "artifact.json"),
                              "passes": len(pipeline.plan(bundle["sources"], prompt, schema))}, indent=2))
            return 0
        info = request_json(args.port, "show", {"model": args.model})
        require(isinstance(info, dict) and not info.get("remote_model") and not info.get("remote_host"),
                "Cloud-backed models are not permitted")
        output.mkdir(parents=True, exist_ok=False)
        attempts = []

        def infer(sources):
            if attempts:
                info = request_json(args.port, "show", {"model": args.model})
                require(isinstance(info, dict) and not info.get("remote_model") and not info.get("remote_host"),
                        "Cloud-backed models are not permitted")
            request = copy.deepcopy(payload)
            request["prompt"] = json.dumps({"sources": sources}, ensure_ascii=False)
            request["format"] = pipeline.ollama_schema(schema, sources)
            attempt = len(attempts) + 1
            directory = output if attempt == 1 else output / f"pass-{attempt}"
            directory.mkdir(exist_ok=True)
            (directory / "request.json").write_text(json.dumps(request, indent=2) + "\n", encoding="utf-8")
            response = request_json(args.port, "generate", request)
            (directory / "response.json").write_text(json.dumps(response, indent=2) + "\n", encoding="utf-8")
            require(isinstance(response, dict), "Unreadable model response")
            attempts.append({"attempt": attempt, "source_ids": [source["id"] for source in sources],
                             **{key: response.get(key) for key in ("done_reason", "total_duration", "load_duration",
                                                                  "prompt_eval_count", "prompt_eval_duration", "eval_count", "eval_duration")}})
            (output / "timings.json").write_text(json.dumps(attempts, indent=2) + "\n", encoding="utf-8")
            print(f"Completed model pass {attempt}: {response.get('done_reason', 'incomplete')}", flush=True)
            require(isinstance(response, dict) and response.get("done") is True
                    and response.get("model") == args.model and isinstance(response.get("response"), str),
                    "Generation did not complete")
            if response.get("done_reason") == "length":
                raise pipeline.OutputLimitError()
            require(response.get("done_reason") == "stop", "Generation did not complete")
            return loads_json(response["response"])

        def validate_part(sources, generated):
            part = dict(bundle, sources=sources, fact_ledger=[])
            build_artifact(part, generated, args.model, run_id, timestamp, allow_empty=True)

        generated = pipeline.generate(bundle["sources"], prompt, schema, infer, validate_part)
        artifact = build_artifact(bundle, generated, args.model, run_id, timestamp)
        (output / "artifact.json").write_text(json.dumps(artifact, indent=2) + "\n", encoding="utf-8")
        print(f"Research artifact: {output / 'artifact.json'}")
        return 0
    except (ValueError, OSError, URLError, KeyError, TypeError) as error:
        parser.exit(1, f"Generation rejected: {error}\n")


if __name__ == "__main__":
    raise SystemExit(main())
