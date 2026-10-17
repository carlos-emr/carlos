#!/usr/bin/env python3
"""Run the CARLOS synthetic-note benchmark against an Ollama model."""

import argparse
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


BASE = Path(__file__).resolve().parent


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("model", help="Exact Ollama model tag")
    parser.add_argument(
        "--host",
        default="http://localhost:11434",
        help="Ollama base URL (default: http://localhost:11434)",
    )
    parser.add_argument("--label", help="Filename label; defaults to a sanitized model tag")
    parser.add_argument("--temperature", type=float, default=0.15)
    parser.add_argument("--top-p", type=float, default=0.85)
    parser.add_argument("--presence-penalty", type=float, default=0.0)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--num-ctx", type=int, default=4096)
    parser.add_argument("--num-predict", type=int, default=2500)
    parser.add_argument(
        "--min-available-memory-gib",
        type=float,
        default=6.0,
        help=(
            "Refuse to start when the container has less available memory "
            "(default: 6 GiB)"
        ),
    )
    parser.add_argument(
        "--skip-preflight",
        action="store_true",
        help="Skip memory and loaded-model safety checks",
    )
    parser.add_argument(
        "--series",
        default="v3",
        help="Output-series label (default: v3)",
    )
    return parser.parse_args()


def safe_label(value):
    return re.sub(r"[^a-z0-9._-]+", "-", value.lower()).strip("-")


def post_json(url, payload, timeout):
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.load(response)


def get_json(url, timeout=5):
    with urllib.request.urlopen(url, timeout=timeout) as response:
        return json.load(response)


def available_memory_bytes():
    meminfo = Path("/proc/meminfo")
    if not meminfo.exists():
        return None
    for line in meminfo.read_text().splitlines():
        if line.startswith("MemAvailable:"):
            return int(line.split()[1]) * 1024
    return None


def run_preflight(args):
    if args.skip_preflight:
        print("WARNING: safety preflight skipped", file=sys.stderr)
        return

    available = available_memory_bytes()
    required = int(args.min_available_memory_gib * 1024**3)
    if available is not None and available < required:
        available_gib = available / 1024**3
        raise RuntimeError(
            f"only {available_gib:.1f} GiB memory is available; "
            f"at least {args.min_available_memory_gib:.1f} GiB is required"
        )

    try:
        running = get_json(f"{args.host.rstrip('/')}/api/ps")
    except (urllib.error.URLError, TimeoutError) as exc:
        raise RuntimeError(f"cannot query Ollama preflight status: {exc}") from exc

    loaded = [model.get("name", "unknown") for model in running.get("models", [])]
    if loaded:
        raise RuntimeError(
            "Ollama already has a model loaded: " + ", ".join(loaded)
        )

    available_text = (
        f"{available / 1024**3:.1f} GiB available"
        if available is not None
        else "available memory unknown"
    )
    print(f"Safety preflight passed: {available_text}; Ollama is idle")


def parse_model_json(text):
    candidate = text.strip()
    if candidate.startswith("```"):
        candidate = re.sub(r"^```(?:json)?\s*", "", candidate)
        candidate = re.sub(r"\s*```$", "", candidate)
    try:
        return json.loads(candidate)
    except json.JSONDecodeError:
        start = candidate.find("{")
        end = candidate.rfind("}")
        if start < 0 or end <= start:
            raise
        return json.loads(candidate[start:end + 1])


def seconds(nanoseconds):
    return round((nanoseconds or 0) / 1_000_000_000, 3)


def main():
    args = parse_args()
    try:
        run_preflight(args)
    except RuntimeError as exc:
        print(f"Safety preflight failed: {exc}", file=sys.stderr)
        return 4

    label = safe_label(args.label or args.model)
    stem = f"{args.series}-{label}"
    response_path = BASE / f"response-{stem}.json"
    draft_path = BASE / f"draft-{stem}.json"
    validation_path = BASE / f"validation-{stem}.json"
    metadata_path = BASE / f"metadata-{stem}.json"

    payload = {
        "model": args.model,
        "prompt": (BASE / "prompt-v2.txt").read_text(),
        "stream": False,
        "keep_alive": 0,
        "options": {
            "temperature": args.temperature,
            "top_p": args.top_p,
            "presence_penalty": args.presence_penalty,
            "seed": args.seed,
            "num_ctx": args.num_ctx,
            "num_predict": args.num_predict,
        },
    }

    started = time.time()
    try:
        response = post_json(
            f"{args.host.rstrip('/')}/api/generate",
            payload,
            timeout=4 * 60 * 60,
        )
    except (urllib.error.URLError, TimeoutError) as exc:
        print(f"Ollama request failed: {exc}", file=sys.stderr)
        return 1

    response_path.write_text(json.dumps(response, indent=2) + "\n")
    try:
        draft = parse_model_json(response.get("response", ""))
    except json.JSONDecodeError as exc:
        print(f"Model did not return valid JSON: {exc}", file=sys.stderr)
        return 2
    draft_path.write_text(json.dumps(draft, indent=2) + "\n")

    validation = subprocess.run(
        [sys.executable, str(BASE / "validate_summary.py"), str(draft_path)],
        check=False,
        capture_output=True,
        text=True,
    )
    validation_path.write_text(validation.stdout)

    eval_count = response.get("eval_count") or 0
    eval_duration = seconds(response.get("eval_duration"))
    metadata = {
        "model": args.model,
        "label": label,
        "options": payload["options"],
        "wall_duration_seconds": round(time.time() - started, 3),
        "ollama_total_duration_seconds": seconds(response.get("total_duration")),
        "load_duration_seconds": seconds(response.get("load_duration")),
        "prompt_eval_count": response.get("prompt_eval_count"),
        "prompt_eval_duration_seconds": seconds(response.get("prompt_eval_duration")),
        "eval_count": eval_count,
        "eval_duration_seconds": eval_duration,
        "generation_tokens_per_second": (
            round(eval_count / eval_duration, 3) if eval_duration else None
        ),
        "done": response.get("done"),
        "done_reason": response.get("done_reason"),
        "validator_exit_code": validation.returncode,
        "files": {
            "response": response_path.name,
            "draft": draft_path.name,
            "validation": validation_path.name,
        },
    }
    metadata_path.write_text(json.dumps(metadata, indent=2) + "\n")

    print(json.dumps(metadata, indent=2))
    print(validation.stdout.rstrip())
    return 0 if validation.returncode == 0 else 3


if __name__ == "__main__":
    raise SystemExit(main())
