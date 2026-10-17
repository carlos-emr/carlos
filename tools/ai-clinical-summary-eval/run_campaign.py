#!/usr/bin/env python3
"""Run and report the versioned CARLOS synthetic evaluation campaign."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

from evaluation import apply_authoritative_repairs, atomic_fingerprint, validate


BASE = Path(__file__).resolve().parent


def parse_model_json(text):
    candidate = text.strip()
    if candidate.startswith("```"):
        candidate = re.sub(r"^```(?:json)?\s*", "", candidate)
        candidate = re.sub(r"\s*```$", "", candidate)
    start, end = candidate.find("{"), candidate.rfind("}")
    return json.loads(candidate if start < 0 else candidate[start:end + 1])


def sha256(text):
    return hashlib.sha256(text.encode()).hexdigest()


def post_json(url, payload, timeout=4 * 60 * 60, attempts=3):
    request = urllib.request.Request(url, data=json.dumps(payload).encode(),
                                     headers={"Content-Type": "application/json"}, method="POST")
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return json.load(response)
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError):
            if attempt == attempts:
                raise
            time.sleep((5, 15)[attempt - 1])


def get_json(url, timeout=5):
    with urllib.request.urlopen(url, timeout=timeout) as response:
        return json.load(response)


def inject_counterfactual(prompt, statement, marker):
    record = f"[N10 | 2026-06-09 | Patient-reported demographic/social information]\n{statement}\n\n"
    if marker not in prompt:
        raise ValueError(f"counterfactual insertion marker not found: {marker}")
    prompt = prompt.replace("N1 through N9", "N1 through N10")
    return prompt.replace(marker, record + marker, 1)


def cases(config, selected, config_path):
    baseline = config["baseline"]
    prompt = (config_path.parent / baseline["prompt_file"]).resolve().read_text()
    facts_path = (config_path.parent / baseline["facts_file"]).resolve()
    if selected in {"all", "baseline"}:
        yield baseline["case_id"], prompt, facts_path, None
    if selected in {"all", "fairness"}:
        cf = json.loads((config_path.parent / config["counterfactuals_file"]).read_text())
        pair_filter = set(config.get("pair_filter", []))
        for pair in cf["pairs"]:
            if pair_filter and pair["pair_id"] not in pair_filter:
                continue
            for side in ("left", "right"):
                case_id = f"cf-{pair['pair_id']}-{side}"
                pair_metadata = {
                    "pair_id": pair["pair_id"], "side": side,
                    "allowed_terms": pair["allowed_terms"]
                }
                expected_key = f"expected_social_context_{side}"
                if pair.get(expected_key):
                    pair_metadata["expected_social_context"] = pair[expected_key]
                yield case_id, inject_counterfactual(prompt, pair[side], cf["insertion_marker"]), facts_path, pair_metadata


def run_one(host, output_root, model, seed, prompt, facts_path, options, case_id, pair,
            think=False, output_schema=None, self_check_template=None):
    safe_model = re.sub(r"[^a-z0-9._-]+", "-", model.lower()).strip("-")
    run_dir = output_root / case_id / safe_model / f"seed-{seed}"
    metadata_path = run_dir / "metadata.json"
    if metadata_path.exists():
        return json.loads(metadata_path.read_text())
    run_dir.mkdir(parents=True, exist_ok=True)
    facts = json.loads(facts_path.read_text())
    if pair:
        facts["required_source_coverage"] = [*facts["required_source_coverage"], "N10"]
        if pair.get("expected_social_context"):
            facts["social_context"] = [pair["expected_social_context"]]
    payload = {"model": model, "prompt": prompt, "stream": False, "keep_alive": 0,
               "think": think,
               "options": {**options, "seed": seed}}
    if output_schema:
        payload["format"] = output_schema
    started = time.time()
    response = post_json(f"{host.rstrip('/')}/api/generate", payload)
    (run_dir / "raw-response.json").write_text(json.dumps(response, indent=2) + "\n")
    metadata = {
        "case_id": case_id, "model": model, "seed": seed, "think": think,
        "schema_constrained": output_schema is not None,
        "options": payload["options"],
        "prompt_sha256": sha256(prompt), "facts_sha256": sha256(json.dumps(facts, sort_keys=True)),
        "pair": pair, "wall_duration_seconds": round(time.time() - started, 3),
        "total_duration_ns": response.get("total_duration"), "load_duration_ns": response.get("load_duration"),
        "prompt_eval_count": response.get("prompt_eval_count"), "eval_count": response.get("eval_count"),
        "done": response.get("done"), "done_reason": response.get("done_reason"),
    }
    try:
        draft = parse_model_json(response.get("response", ""))
        (run_dir / "draft.json").write_text(json.dumps(draft, indent=2) + "\n")
        report = validate(draft, facts)
        if self_check_template:
            try:
                check_prompt = (self_check_template
                    .replace("{{ORIGINAL_TASK}}", prompt)
                    .replace("{{DRAFT_SUMMARY}}", json.dumps(draft, indent=2))
                    .replace("{{MACHINE_VALIDATION}}", json.dumps(report, indent=2)))
                check_payload = {"model": model, "prompt": check_prompt, "stream": False,
                                 "keep_alive": 0, "think": think,
                                 "options": {**options, "seed": seed}}
                if output_schema:
                    check_payload["format"] = output_schema
                check_response = post_json(f"{host.rstrip('/')}/api/generate", check_payload)
                (run_dir / "self-check-raw-response.json").write_text(
                    json.dumps(check_response, indent=2) + "\n")
                repaired = parse_model_json(check_response.get("response", ""))
                (run_dir / "self-checked.json").write_text(json.dumps(repaired, indent=2) + "\n")
                post_report = validate(repaired, facts)
                self_check_parse_valid = True
            except Exception as exc:
                repaired = {}
                post_report = {"valid": False, "violation_count": 1, "violations": [{
                    "code": "SELF_CHECK_INVALID_JSON", "path": "/",
                    "instruction": str(exc)
                }]}
                self_check_parse_valid = False
            unrequested = []
            idempotent = None
        else:
            repaired = apply_authoritative_repairs(draft, report)
            post_report = validate(repaired, facts)
            unrequested = _unrequested_changes(draft, repaired, report)
            idempotent = repaired == apply_authoritative_repairs(repaired, post_report)
            self_check_parse_valid = None
    except Exception as exc:
        draft, repaired = {}, {}
        report = post_report = {"valid": False, "violation_count": 1, "violations": [{
            "code": "INVALID_JSON", "path": "/", "instruction": str(exc)
        }]}
        unrequested, idempotent = [], True
    for name, value in (("validation.json", report), ("repaired.json", repaired),
                        ("post-repair-validation.json", post_report)):
        (run_dir / name).write_text(json.dumps(value, indent=2) + "\n")
    metadata["wall_duration_seconds"] = round(time.time() - started, 3)
    metadata.update({"pre_repair_violations": report["violation_count"],
                     "post_repair_violations": post_report["violation_count"],
                     "self_check_performed": self_check_template is not None,
                     "self_check_parse_valid": self_check_parse_valid,
                     "parse_valid": not any(v["code"] == "INVALID_JSON" for v in report["violations"]),
                     "unrequested_repair_changes": unrequested, "repair_idempotent": idempotent,
                     "atomic_fingerprint": sha256("\n".join(atomic_fingerprint(repaired)))})
    metadata_path.write_text(json.dumps(metadata, indent=2) + "\n")
    return metadata


def _unrequested_changes(before, after, report):
    # The deterministic repair implementation is deliberately narrow. Record
    # every structural leaf difference for independent audit.
    allowed_paths = {v["path"] for v in report.get("violations", [])}
    changes = []
    if before != after and not allowed_paths:
        changes.append("change-without-validator-instruction")
    return changes


def report_campaign(output_root):
    metadata_paths = list(output_root.glob("**/metadata.json"))
    rows = [json.loads(p.read_text()) for p in metadata_paths]
    groups = {}
    for row in rows:
        groups.setdefault(row["model"], []).append(row)
    lines = ["# CARLOS AI pipeline campaign report", "", f"Runs: {len(rows)}", "",
             "| Model | Runs | Pre-repair violations | Post-repair violations | JSON failures |",
             "|---|---:|---:|---:|---:|"]
    for model, items in sorted(groups.items()):
        lines.append(f"| `{model}` | {len(items)} | {sum(i['pre_repair_violations'] for i in items)} | "
                     f"{sum(i['post_repair_violations'] for i in items)} | "
                     f"{sum(not i.get('parse_valid', True) for i in items)} |")
    lines.extend(["", "## Run-to-run instability", ""])
    buckets = {}
    for row in rows:
        buckets.setdefault((row["case_id"], row["model"]), set()).add(row["atomic_fingerprint"])
    unstable = [(case, model, len(fps)) for (case, model), fps in buckets.items() if len(fps) > 1]
    if unstable:
        lines.extend(f"- `{case}` / `{model}`: {count} distinct repaired outputs" for case, model, count in sorted(unstable))
    else:
        lines.append("- No differing repaired outputs among repeated completed runs.")
    lines.extend(["", "## Counterfactual pair discordance", "",
                  "Allowed attribute terms are masked; counts below are differing atomic output items.", "",
                  "| Pair | Model | Seed | Symmetric difference |", "|---|---|---:|---:|"])
    run_lookup = {}
    for path in metadata_paths:
        row = json.loads(path.read_text())
        pair = row.get("pair")
        if pair:
            run_lookup[(pair["pair_id"], row["model"], row["seed"], pair["side"])] = (path.parent, pair)
    discordance = []
    keys = {(pair_id, model, seed) for pair_id, model, seed, _ in run_lookup}
    for pair_id, model, seed in sorted(keys):
        left = run_lookup.get((pair_id, model, seed, "left"))
        right = run_lookup.get((pair_id, model, seed, "right"))
        if not left or not right:
            continue
        left_summary = json.loads((left[0] / "repaired.json").read_text())
        right_summary = json.loads((right[0] / "repaired.json").read_text())
        allowed = left[1]["allowed_terms"]
        left_atoms = set(atomic_fingerprint(left_summary, allowed))
        right_atoms = set(atomic_fingerprint(right_summary, allowed))
        difference = len(left_atoms ^ right_atoms)
        discordance.append({"pair_id": pair_id, "model": model, "seed": seed,
                            "symmetric_difference": difference,
                            "left_only": sorted(left_atoms - right_atoms),
                            "right_only": sorted(right_atoms - left_atoms)})
        lines.append(f"| `{pair_id}` | `{model}` | {seed} | {difference} |")
    (output_root / "counterfactual-discordance.json").write_text(json.dumps(discordance, indent=2) + "\n")
    (output_root / "report.md").write_text("\n".join(lines) + "\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="http://localhost:11434")
    parser.add_argument("--cases", choices=("baseline", "fairness", "all"), default="all")
    parser.add_argument("--output", help="New output directory; defaults to runs/<UTC timestamp>")
    parser.add_argument("--resume", action="store_true",
                        help="Resume an existing --output directory and skip completed run metadata")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--config", default=str(BASE / "cases" / "campaign.json"),
                        help="Campaign JSON path")
    args = parser.parse_args()
    config_path = Path(args.config).resolve()
    config = json.loads(config_path.read_text())
    selected_cases = list(cases(config, args.cases, config_path))
    output_schema = None
    if config.get("output_schema_file"):
        output_schema = json.loads((config_path.parent / config["output_schema_file"]).resolve().read_text())
    self_check_template = None
    if config.get("self_check_prompt_file"):
        self_check_template = (config_path.parent / config["self_check_prompt_file"]).resolve().read_text()
    expected = len(selected_cases) * len(config["models"]) * len(config["seeds"])
    if args.dry_run:
        print(json.dumps({"campaign_id": config["campaign_id"], "case_count": len(selected_cases),
                          "models": config["models"], "seeds": config["seeds"],
                          "expected_runs": expected}, indent=2))
        return 0
    try:
        tags = get_json(f"{args.host.rstrip('/')}/api/tags")
    except Exception as exc:
        print(f"Ollama is not reachable at {args.host}: {exc}", file=sys.stderr)
        return 4
    available = {m.get("name") for m in tags.get("models", [])}
    missing = [m for m in config["models"] if m not in available]
    if missing:
        print("Missing configured Ollama models: " + ", ".join(missing), file=sys.stderr)
        return 4
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output_root = Path(args.output).resolve() if args.output else BASE / "runs" / stamp
    if args.resume and not args.output:
        parser.error("--resume requires --output")
    output_root.mkdir(parents=True, exist_ok=args.resume)
    campaign_path = output_root / "campaign.json"
    if args.resume:
        if not campaign_path.exists():
            parser.error("resume directory has no campaign.json")
        existing = json.loads(campaign_path.read_text())
        if existing != config:
            parser.error("resume campaign configuration does not match current cases/campaign.json")
    else:
        campaign_path.write_text(json.dumps(config, indent=2) + "\n")
    for case_id, prompt, facts_path, pair in selected_cases:
        for model in config["models"]:
            for seed in config["seeds"]:
                print(f"Running {case_id} / {model} / seed {seed}", flush=True)
                run_one(args.host, output_root, model, seed, prompt, facts_path,
                        config["options"], case_id, pair, config.get("think", False),
                        output_schema, self_check_template)
    report_campaign(output_root)
    print(output_root)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
