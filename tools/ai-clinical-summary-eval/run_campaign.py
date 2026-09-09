#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Run resumable, synthetic-only experiments against local Ollama."""

from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import statistics
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = Path(__file__).resolve().parent
DRAFT = BASE.parent / "ai-clinical-summary-draft"
sys.path.insert(0, str(DRAFT))
from validate_artifact import validate, validate_generated  # noqa: E402
from evaluation import evaluate  # noqa: E402

MAX_RESPONSE_BYTES = 4 * 1024 * 1024
SECTION_IDS = {"clinical_overview", "active_problems", "medications_allergies",
               "results_observations", "plan_follow_up"}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("Ollama redirects are not permitted")


def sha256(value):
    if not isinstance(value, str):
        value = json.dumps(value, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def scorer_sha256():
    return sha256((BASE / "evaluation.py").read_text(encoding="utf-8")
                  + (DRAFT / "validate_artifact.py").read_text(encoding="utf-8"))


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def post_json(port, endpoint, payload, timeout):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    request = urllib.request.Request(f"http://127.0.0.1:{port}/api/{endpoint}",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}, method="POST")
    with opener.open(request, timeout=timeout) as response:
        body = response.read(MAX_RESPONSE_BYTES + 1)
    if len(body) > MAX_RESPONSE_BYTES:
        raise ValueError("Ollama response exceeds size limit")
    return json.loads(body)


def candidate_schema(base_schema, candidate):
    schema = json.loads(json.dumps(base_schema))
    schema["properties"]["claims"]["maxItems"] = candidate["max_claims"]
    schema["properties"]["claims"]["items"]["properties"]["text"]["maxLength"] = candidate["max_chars"]
    schema["properties"]["sections"]["items"]["properties"]["claim_ids"]["maxItems"] = candidate["max_claims"]
    return schema


def candidate_prompt(base_prompt, candidate):
    prompt = base_prompt.replace("no more than 20 claims total",
                                 f"no more than {candidate['max_claims']} claims total")
    prompt = prompt.replace("at most 240 characters", f"at most {candidate['max_chars']} characters")
    if prompt == base_prompt and (candidate["max_claims"] != 20 or candidate["max_chars"] != 240):
        raise ValueError("Current prompt limit text was not found")
    suffix = candidate.get("prompt_suffix", "").strip()
    return prompt + (("\n" + suffix + "\n") if suffix else "")


def validate_candidate_output(generated, sources, candidate):
    validate_generated(generated, sources)
    if len(generated["claims"]) > candidate["max_claims"]:
        raise ValueError("Draft exceeds candidate claim limit")
    if any(len(claim["text"]) > candidate["max_chars"] for claim in generated["claims"]):
        raise ValueError("Draft exceeds candidate character limit")


def failure_result(code, detail):
    return {"metrics": {"hard_gate_pass": False, "critical_fact_recall": 0.0,
                        "required_fact_recall": 0.0, "citation_completeness": 0.0,
                        "citation_precision": 0.0, "claim_count": 0,
                        "average_claim_chars": 0, "maximum_claim_chars": 0,
                        "maximum_source_copy_ratio": 0.0, "warning_count": 0,
                        "error_count": 1},
            "findings": [{"severity": "error", "code": code, "detail": detail}],
            "matched_fact_ids": []}


def finding_counts(result):
    return dict(sorted(Counter(item["code"] for item in result["findings"]).items()))


def validate_case(case):
    if set(case) != {"schema_version", "case_id", "description", "bundle", "expectations"}:
        raise ValueError("Invalid case fields")
    if case["schema_version"] != 1 or not re.fullmatch(r"[a-z0-9-]+", case["case_id"]):
        raise ValueError("Invalid case identity")
    bundle = case["bundle"]
    if set(bundle) != {"patient_context", "sources", "fact_ledger"}:
        raise ValueError("Invalid case bundle")
    coverage = [{"source_id": source["id"], "status": "reviewed_not_cited",
                 "reason": "Synthetic evaluation preflight"} for source in bundle["sources"]]
    artifact = {"schema_version": 1, "artifact_id": "evaluation-preflight",
                "generated_at": "2026-01-01T00:00:00+00:00", "model": "preflight",
                "workflow": "patient-overview", **bundle, "sections": [], "claims": [],
                "coverage": coverage, "validation": []}
    validate(artifact)
    known = {source["id"] for source in bundle["sources"]}
    expectations = case["expectations"]
    if not set(expectations.get("excluded_source_ids", [])).issubset(known):
        raise ValueError("Excluded source is unknown")
    fact_ids = set()
    for fact in expectations["facts"]:
        if (fact["id"] in fact_ids or not fact["pattern_groups"]
                or not fact["section_ids"] or not set(fact["section_ids"]).issubset(SECTION_IDS)):
            raise ValueError("Invalid or duplicate expected fact")
        fact_ids.add(fact["id"])
        required_sources = set(fact["required_source_ids"])
        allowed_sources = set(fact.get("allowed_source_ids", fact["required_source_ids"]))
        if not required_sources.issubset(known) or not required_sources.issubset(allowed_sources):
            raise ValueError("Expected fact cites an unknown source")
        for group in fact["pattern_groups"]:
            if not group:
                raise ValueError("Expected fact has an empty pattern group")
            for pattern in group:
                re.compile(pattern)
    for pattern in expectations.get("forbidden_patterns", []):
        re.compile(pattern)
    if set(expectations.get("coverage", {})) != known:
        raise ValueError("Expected coverage must account for every source")
    return case


def load_campaign(path):
    config = read_json(path)
    required = {"schema_version", "campaign_id", "model", "seeds", "repetitions",
                "timeout_seconds", "cases", "candidates"}
    if set(config) != required or config["schema_version"] != 1:
        raise ValueError("Invalid campaign configuration")
    if (not config["seeds"] or config["repetitions"] < 1
            or not 1 <= config["timeout_seconds"] <= 14400):
        raise ValueError("Campaign requires seeds and repetitions")
    candidate_ids = [item["id"] for item in config["candidates"]]
    if len(candidate_ids) != len(set(candidate_ids)):
        raise ValueError("Duplicate candidate ID")
    for candidate in config["candidates"]:
        if not set(candidate).issubset({"id", "max_claims", "max_chars", "num_ctx",
                                        "num_predict", "prompt_suffix"}):
            raise ValueError("Unknown candidate setting")
        if not (1 <= candidate["max_claims"] <= 20 and 80 <= candidate["max_chars"] <= 240
                and 2048 <= candidate["num_ctx"] <= 65536
                and 256 <= candidate["num_predict"] <= 4096):
            raise ValueError("Candidate settings exceed the runtime contract")
    return config


def experiment_matrix(config, config_path, selected_cases=None, selected_candidates=None):
    case_paths = [(config_path.parent / item).resolve() for item in config["cases"]]
    cases = [validate_case(read_json(path)) for path in case_paths]
    if selected_cases:
        cases = [case for case in cases if case["case_id"] in selected_cases]
    candidates = [item for item in config["candidates"]
                  if not selected_candidates or item["id"] in selected_candidates]
    if not cases or not candidates:
        raise ValueError("Selection produced an empty campaign")
    for case in cases:
        for candidate in candidates:
            for seed in config["seeds"]:
                for repetition in range(1, config["repetitions"] + 1):
                    yield case, candidate, seed, repetition


def run_one(port, model, timeout, output_root, case, candidate, seed, repetition,
            base_prompt, base_schema):
    run_dir = output_root / case["case_id"] / candidate["id"] / f"seed-{seed}-run-{repetition}"
    metadata_path = run_dir / "metadata.json"
    if metadata_path.exists():
        metadata = read_json(metadata_path)
        if metadata.get("scorer_sha256") != scorer_sha256():
            raise ValueError("Preserved run uses a different scorer; start a new campaign")
        if "finding_counts" not in metadata:
            result = read_json(run_dir / "evaluation.json")
            metadata["finding_counts"] = finding_counts(result)
            metadata_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        return metadata
    run_dir.mkdir(parents=True, exist_ok=True)
    prompt = candidate_prompt(base_prompt, candidate)
    schema = candidate_schema(base_schema, candidate)
    payload = {"model": model, "system": prompt,
               "prompt": json.dumps({"sources": case["bundle"]["sources"]}),
               "format": schema, "stream": False, "think": False, "keep_alive": "5m",
               "options": {"temperature": 0, "seed": seed, "num_ctx": candidate["num_ctx"],
                           "num_predict": candidate["num_predict"]}}
    (run_dir / "request.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    metadata = {"case_id": case["case_id"], "candidate_id": candidate["id"],
                "model": model, "seed": seed, "repetition": repetition,
                "prompt_sha256": sha256(prompt), "schema_sha256": sha256(schema),
                "case_sha256": sha256(case), "scorer_sha256": scorer_sha256()}
    started = time.monotonic()
    try:
        response = post_json(port, "generate", payload, timeout)
    except (OSError, urllib.error.URLError, TimeoutError) as error:
        metadata.update({"wall_duration_seconds": round(time.monotonic() - started, 3),
                         "total_duration_ns": None, "load_duration_ns": None,
                         "prompt_eval_count": None, "prompt_eval_duration_ns": None,
                         "eval_count": None, "eval_duration_ns": None,
                         "done": False, "done_reason": "transport_error"})
        result = failure_result("GENERATION_ERROR", str(error))
        (run_dir / "evaluation.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        metadata["metrics"] = result["metrics"]
        metadata["clinical_fingerprint"] = sha256(result["matched_fact_ids"])
        metadata["finding_counts"] = finding_counts(result)
        metadata_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        return metadata
    wall = round(time.monotonic() - started, 3)
    (run_dir / "response.json").write_text(json.dumps(response, indent=2) + "\n", encoding="utf-8")
    metadata.update({"wall_duration_seconds": wall,
                "total_duration_ns": response.get("total_duration"),
                "load_duration_ns": response.get("load_duration"),
                "prompt_eval_count": response.get("prompt_eval_count"),
                "prompt_eval_duration_ns": response.get("prompt_eval_duration"),
                "eval_count": response.get("eval_count"),
                "eval_duration_ns": response.get("eval_duration"),
                "done": response.get("done"), "done_reason": response.get("done_reason")})
    try:
        if (response.get("done") is not True or response.get("done_reason") != "stop"
                or response.get("model") != model):
            raise ValueError("Generation did not finish with done_reason=stop")
        generated = json.loads(response.get("response", ""))
        (run_dir / "draft.json").write_text(json.dumps(generated, indent=2) + "\n", encoding="utf-8")
        validator_error = None
        try:
            validate_candidate_output(generated, case["bundle"]["sources"], candidate)
        except (ValueError, TypeError, KeyError) as error:
            validator_error = str(error)
        result = evaluate(generated, case["bundle"]["sources"], case["expectations"])
        if validator_error:
            result["findings"].insert(0, {"severity": "error", "code": "RUNTIME_VALIDATOR_REJECTION",
                                           "detail": validator_error})
            result["metrics"]["hard_gate_pass"] = False
            result["metrics"]["error_count"] += 1
    except (ValueError, TypeError, KeyError, json.JSONDecodeError) as error:
        result = failure_result("INVALID_DRAFT", str(error))
    (run_dir / "evaluation.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    metadata["metrics"] = result["metrics"]
    metadata["clinical_fingerprint"] = sha256(result["matched_fact_ids"])
    metadata["finding_counts"] = finding_counts(result)
    metadata_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    return metadata


def write_report(output_root, rows):
    grouped = {}
    for row in rows:
        grouped.setdefault(row["candidate_id"], []).append(row)
    summaries = []

    def median_present(items, key):
        values = [item.get(key) for item in items if item.get(key) is not None]
        return round(statistics.median(values), 3) if values else None

    for candidate_id, items in sorted(grouped.items()):
        durations = [item["wall_duration_seconds"] for item in items]
        recalls = [item["metrics"]["required_fact_recall"] for item in items]
        passes = sum(item["metrics"]["hard_gate_pass"] for item in items)
        fingerprints = {}
        for item in items:
            fingerprints.setdefault(item["case_id"], set()).add(item.get("clinical_fingerprint"))
        stable = all(len(values) == 1 for values in fingerprints.values())
        summaries.append({"candidate_id": candidate_id, "runs": len(items), "passes": passes,
                          "stable": stable, "eligible": passes == len(items) and stable,
                          "minimum_fact_recall": min(recalls, default=0.0),
                          "median_wall_seconds": round(statistics.median(durations), 3) if durations else None,
                          "maximum_wall_seconds": max(durations, default=None),
                          "median_prompt_tokens": median_present(items, "prompt_eval_count"),
                          "median_output_tokens": median_present(items, "eval_count")})
    eligible = sorted((item for item in summaries if item["eligible"]),
                      key=lambda item: item["median_wall_seconds"])
    comparison = {"selection_rule": "fastest candidate passing every safety gate in every run",
                  "winner": eligible[0]["candidate_id"] if eligible else None,
                  "candidates": summaries}
    (output_root / "comparison.json").write_text(json.dumps(comparison, indent=2) + "\n", encoding="utf-8")
    lines = ["# Clinical summary optimization report", "",
             "Safety gates are applied before latency. Automated synthetic checks are not clinical validation.", "",
             "| Candidate | Runs passing | Minimum fact recall | Median seconds | Prompt tokens | Output tokens | Stable | Eligible |",
             "|---|---:|---:|---:|---:|---:|---|---|"]
    for item in summaries:
        lines.append(f"| `{item['candidate_id']}` | {item['passes']}/{item['runs']} | "
                     f"{item['minimum_fact_recall']:.3f} | {item['median_wall_seconds']:.3f} | "
                     f"{item['median_prompt_tokens'] or '-'} | {item['median_output_tokens'] or '-'} | "
                     f"{'yes' if item['stable'] else 'no'} | "
                     f"{'yes' if item['eligible'] else 'no'} |")
    lines.extend(["", "Selected: " + (f"`{eligible[0]['candidate_id']}`" if eligible else "none"),
                  "", "## Findings", "",
                  "| Candidate | Finding | Count |", "|---|---|---:|"])
    for candidate_id, items in sorted(grouped.items()):
        counts = Counter()
        for item in items:
            counts.update(item.get("finding_counts", {}))
        if not counts:
            lines.append(f"| `{candidate_id}` | none | 0 |")
        else:
            for code, count in sorted(counts.items()):
                lines.append(f"| `{candidate_id}` | `{code}` | {count} |")
    (output_root / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=BASE / "campaigns" / "smoke-2b.json")
    parser.add_argument("--port", type=int, default=11434)
    parser.add_argument("--case", action="append", dest="cases")
    parser.add_argument("--candidate", action="append", dest="candidates")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)
    try:
        if not 1 <= args.port <= 65535:
            raise ValueError("Port must be between 1 and 65535")
        config_path = args.config.resolve()
        config = load_campaign(config_path)
        matrix = list(experiment_matrix(config, config_path, set(args.cases or []),
                                        set(args.candidates or [])))
        preview = {"campaign_id": config["campaign_id"], "model": config["model"],
                   "experiments": len(matrix), "cases": sorted({row[0]["case_id"] for row in matrix}),
                   "candidates": sorted({row[1]["id"] for row in matrix})}
        if args.dry_run:
            print(json.dumps(preview, indent=2))
            return 0
        info = post_json(args.port, "show", {"model": config["model"]}, 30)
        if info.get("remote_model") or info.get("remote_host"):
            raise ValueError("Cloud-backed Ollama models are not permitted")
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
        output_root = args.output.resolve() if args.output else (BASE / "runs" / stamp).resolve()
        if args.resume and not args.output:
            raise ValueError("--resume requires --output")
        output_root.mkdir(parents=True, exist_ok=args.resume)
        campaign_copy = output_root / "campaign.json"
        if args.resume:
            if read_json(campaign_copy) != config:
                raise ValueError("Resume configuration differs from the preserved campaign")
        else:
            campaign_copy.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
        base_prompt = (DRAFT / "prompt.txt").read_text(encoding="utf-8")
        base_schema = read_json(DRAFT / "output-schema.json")
        rows = []
        for case, candidate, seed, repetition in matrix:
            print(f"Running {case['case_id']} / {candidate['id']} / seed {seed} / {repetition}", flush=True)
            rows.append(run_one(args.port, config["model"], config["timeout_seconds"], output_root,
                                case, candidate, seed, repetition, base_prompt, base_schema))
        write_report(output_root, rows)
        print(output_root)
        return 0
    except (ValueError, OSError, urllib.error.URLError, KeyError, TypeError) as error:
        parser.exit(2, f"Campaign rejected: {error}\n")


if __name__ == "__main__":
    raise SystemExit(main())
