# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Score a synthetic full-record artifact against the closed labelled fixture, without model calls."""
import argparse
import json
from pathlib import Path
import sys

from validate_artifact import validate

EVAL = Path(__file__).resolve().parent.parent / "ai-clinical-summary-eval"
sys.path.insert(0, str(EVAL))
from evaluation import evaluate  # noqa: E402


def score(artifact, case):
    validate(artifact)
    if artifact["sources"] != case["bundle"]["sources"] or artifact["patient_context"] != case["bundle"]["patient_context"]:
        raise ValueError("Artifact does not match the full-record fixture")
    result = evaluate(artifact, artifact["sources"], case["expectations"])
    # This comprehensive fixture requires every labelled fact, stricter than the historical
    # highlight experiments' 90% recall threshold. A missing noncritical fact still fails.
    result["metrics"]["full_record_pass"] = (result["metrics"]["hard_gate_pass"]
                                                   and result["metrics"]["required_fact_recall"] == 1.0)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", type=Path)
    args = parser.parse_args()
    try:
        result = score(json.loads(args.artifact.read_text()), json.loads((EVAL / "cases/full-record.json").read_text()))
        output = args.artifact.with_name("quality-report.json")
        output.write_text(json.dumps(result, indent=2) + "\n")
        print(json.dumps(result["metrics"], indent=2))
        print(f"Quality report: {output}")
        return 0 if result["metrics"]["full_record_pass"] else 1
    except (ValueError, OSError, KeyError) as error:
        parser.exit(2, f"Scoring rejected: {error}\n")


if __name__ == "__main__":
    raise SystemExit(main())
