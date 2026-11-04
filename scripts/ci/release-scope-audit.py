#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Inventory complete Git comparisons without GitHub's compare-file cap.

Candidate test names are discovery aids. Only explicit path bindings with real
test/source references count as ownership; execution/coverage gates remain separate.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

from junit_evidence import EvidenceError
from release_verification import DEFAULT_PLAN, ROOT, load_plan, write_json


def git(root, *args):
    return subprocess.check_output(["git", *args], cwd=root)


def comparison(root, base, head):
    # Disabling rename pairing preserves both names, including deleted interfaces/resources.
    fields = git(root, "diff", "--no-renames", "--name-status", "-z", base, head).split(b"\0")
    if fields[-1] != b"" or len(fields) % 2 != 1:
        raise EvidenceError("Invalid Git change inventory")
    return [(fields[i].decode("ascii"), fields[i + 1].decode("utf-8")) for i in range(0, len(fields) - 1, 2)]


def runtime_path(path):
    return (path.startswith(("src/main/", "database/", "debian/assets/", ".devcontainer/"))
            or path.startswith("debian/") and not path.startswith(("debian/assets/tests/", "debian/tests/"))
            or path in ("pom.xml", "package.json", "package-lock.json"))


def inventory(plan, root=ROOT, head="HEAD"):
    resolved = git(root, "rev-parse", head).decode().strip()
    bindings = plan.get("pathBindings", {})
    checks = {check["id"] for check in plan["checks"]}
    workstreams = {work["id"] for work in plan["workstreams"]}
    for path, binding in bindings.items():
        if not binding.get("reason") or not binding.get("tests") or not binding.get("checks"):
            raise EvidenceError("Path binding lacks its concrete test, check or rationale")
        if set(binding["checks"]) - checks or set(binding.get("workstreams", [])) - workstreams:
            raise EvidenceError("Path binding names an unknown check/workstream")
        for test in binding["tests"]:
            file = root / test
            if Path(test).is_absolute() or ".." in Path(test).parts or not file.is_file():
                raise EvidenceError("Path binding references a missing test/source")
    candidates = {}
    for test in (root / "src/test/java").rglob("*.java"):
        stem = re.sub(r"(?:Unit|Integration|Endpoint)?Tests?$", "", test.stem)
        if stem:
            candidates.setdefault(stem, []).append(str(test.relative_to(root)))
    rows = []
    missing = set()
    for scope, base in plan["baselines"].items():
        for status, path in comparison(root, base, resolved):
            binding = bindings.get(path)
            production = runtime_path(path)
            if production and binding is None:
                missing.add(path)
            rows.append({"scope": scope, "base": base, "head": resolved, "status": status, "path": path,
                         "production": production, "ownership": binding,
                         "candidateTests": candidates.get(Path(path).stem, []) if path.endswith(".java") else []})
    main = plan["baselines"].get("currentMain")
    migrations = []
    if main:
        main_files = set(git(root, "ls-tree", "-r", "--name-only", main, "database/mysql/migration").decode().splitlines())
        release_files = set(git(root, "ls-tree", "-r", "--name-only", resolved, "database/mysql/migration").decode().splitlines())
        dispositions = plan.get("mainOnlyMigrationDispositions", {})
        for path in sorted(main_files - release_files):
            if not path.endswith(".sql"):
                continue
            migrations.append({"path": path, "sha256": hashlib.sha256(git(root, "show", main + ":" + path)).hexdigest(),
                               "disposition": dispositions.get(path)})
    unresolved = [row["path"] for row in migrations if not row["disposition"]]
    return {"schemaVersion": 1, "head": resolved, "changes": rows,
            "unownedProductionPaths": sorted(missing), "mainOnlyMigrations": migrations,
            "unresolvedMainOnlyMigrations": unresolved, "ready": not missing and not unresolved}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path, default=DEFAULT_PLAN)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--inventory-only", action="store_true", help="Generate review inventory without claiming coverage")
    args = parser.parse_args(argv)
    args.output.unlink(missing_ok=True)
    try:
        result = inventory(load_plan(args.plan))
        write_json(args.output, result)
        print(json.dumps({"changedPathsAcrossScopes": len(result["changes"]),
                          "unownedProductionPaths": len(result["unownedProductionPaths"]),
                          "unresolvedMainOnlyMigrations": len(result["unresolvedMainOnlyMigrations"]), "ready": result["ready"]}))
        return 0 if args.inventory_only or result["ready"] else 1
    except Exception as failure:
        print("Release scope refused: " + (str(failure) if isinstance(failure, EvidenceError) else type(failure).__name__), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
