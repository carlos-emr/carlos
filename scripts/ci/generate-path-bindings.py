#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Propose coverage owners over all pinned scopes; only reviewed bindings are written."""
import argparse
import copy
import importlib.util
import json
from pathlib import Path
import re

from release_verification import DEFAULT_PLAN, ROOT, load_plan

spec = importlib.util.spec_from_file_location("release_scope", Path(__file__).with_name("release-scope-audit.py"))
scope = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scope)


def generate_bindings(plan, root=ROOT, head="HEAD"):
    inventory = scope.inventory(plan, root, head)
    paths = sorted({row["path"] for row in inventory["changes"] if row["production"]})
    tests = list((root / "src/test/java").rglob("*.java"))
    by_name = {}
    scopes = {}
    for row in inventory["changes"]:
        scopes.setdefault(row["path"], set()).add(row["scope"])
    for test in tests:
        names = set(re.findall(r"\b[A-Za-z_$][\w$]*\b", test.read_text()))
        names.add(re.sub(r"(?:Unit|Integration|Endpoint)?Tests?$", "", test.stem))
        for name in names:
            by_name.setdefault(name, set()).add(str(test.relative_to(root)))
    candidates = {}
    for path in paths:
        stem = Path(path).stem
        matches = []
        if path.endswith(".java"):
            interface = stem.removesuffix("Impl")
            matches = by_name.get(stem, set()) | by_name.get(interface, set())
        candidates[path] = {"candidateTests": sorted(matches),
                            "scopes": sorted(scopes[path]),
                            "deleted": not (root / path).is_file()}
    return {"schemaVersion": 1, "head": inventory["head"], "candidates": candidates,
            "unbound": inventory["unownedProductionPaths"], "ready": inventory["ready"]}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path, default=DEFAULT_PLAN)
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--reviewed", type=Path, help="Explicit reviewed pathBindings object")
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args(argv)
    if args.write and not args.reviewed:
        parser.error("--write requires an explicit --reviewed binding file; candidates are not coverage")
    plan = load_plan(args.plan)
    original = copy.deepcopy(plan)
    if args.reviewed:
        proposed = json.loads(args.reviewed.read_text())
        if not isinstance(proposed, dict):
            parser.error("reviewed bindings must be an object")
        plan.setdefault("pathBindings", {}).update(proposed)
        scope.inventory(plan)  # Reject missing tests, checks, workstreams and rationales before writing.
    result = generate_bindings(plan, head=args.head)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    if args.write and plan != original:
        args.plan.write_text(json.dumps(plan, indent=2) + "\n")
    print(json.dumps({"paths": len(result["candidates"]), "unbound": len(result["unbound"]), "ready": result["ready"]}))
    return 0 if result["ready"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
