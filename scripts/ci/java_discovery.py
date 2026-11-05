#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Reconcile concrete annotated Java test sources with fresh Surefire families.

Counts include nested, parameterized and dynamic executions. This supplements
JUnit discovery; it is deliberately conservative about disabled source tests.
An absent family is incomplete evidence, including a disabled required family.
"""
import argparse
import json
from pathlib import Path
import re
import sys

from junit_evidence import EvidenceError, read_surefire


def expected_families(source):
    result = {}
    for path in sorted(source.rglob("*.java")):
        if not re.search(r"Tests?$", path.stem):
            continue
        text = path.read_text(encoding="utf-8")
        if re.search(r"\babstract\s+class\s+" + re.escape(path.stem) + r"\b", text):
            continue
        if not re.search(r"@(?:Test|RepeatedTest|ParameterizedTest|TestFactory|TestTemplate)\b", text):
            continue
        package = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.MULTILINE)
        if package is None:
            raise EvidenceError("Executable test source has no package")
        family = package[1] + "." + path.stem
        if family in result:
            raise EvidenceError("Duplicate source test family")
        result[family] = str(path.relative_to(source))
    if not result:
        raise EvidenceError("No executable Java test sources")
    return result


def reconcile(source, reports):
    expected = expected_families(source)
    executed = {}
    identities = set()
    failures = skips = 0
    collisions = []
    for report in sorted(reports.glob("TEST-*.xml")):
        suite = report.stem.removeprefix("TEST-")
        for case in read_surefire(report, suite, diagnostics=collisions):
            key = case.identity
            if key in identities:
                raise EvidenceError("Duplicate Java discovery evidence")
            identities.add(key)
            family = case.classname.split("$", 1)[0]
            executed[family] = executed.get(family, 0) + 1
            failures += case.outcome == "failure"
            skips += case.outcome == "skipped"
    missing = sorted(set(expected) - executed.keys())
    return {"sourceFamilies": len(expected), "executedFamilies": len(executed),
            "executedCases": len(identities), "failed": failures, "skipped": skips, "counterCollisions": collisions,
            "missing": [{"class": key, "source": expected[key]} for key in missing],
            "ready": bool(identities) and not missing and not failures and not skips}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path("src/test/java"))
    parser.add_argument("--reports", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    args.output.unlink(missing_ok=True)
    try:
        summary = reconcile(args.source, args.reports)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({**{k: v for k, v in summary.items() if k not in ("missing", "counterCollisions")},
                          "counterCollisionCount": len(summary["counterCollisions"])}))
        return 0 if summary["ready"] else 1
    except Exception as failure:
        print("Java discovery evidence refused: " + (str(failure) if isinstance(failure, EvidenceError)
                                                     else type(failure).__name__), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
