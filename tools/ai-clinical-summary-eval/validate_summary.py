#!/usr/bin/env python3
"""CLI wrapper for the deterministic CARLOS summary validator."""

import argparse
import json
import sys
from pathlib import Path

from evaluation import validate


BASE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("summary", nargs="?", default="-", help="Summary JSON file or - for stdin")
    parser.add_argument("--facts", default=str(BASE / "authoritative-facts.json"))
    args = parser.parse_args()
    try:
        summary = json.load(sys.stdin) if args.summary == "-" else json.loads(Path(args.summary).read_text())
        facts = json.loads(Path(args.facts).read_text())
        report = validate(summary, facts)
    except Exception as exc:
        report = {"valid": False, "violation_count": 1, "violations": [{
            "code": "INVALID_JSON", "path": "/", "instruction": f"Return valid JSON: {exc}"
        }]}
    print(json.dumps(report, indent=2))
    return 0 if report["valid"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
