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
from release_verification import DEFAULT_PLAN, ROOT, digest, load_plan, read_json, write_json
from migration_integrity import compare_shared, sql_objects
from lifecycle_baselines import required_baselines
from git_environment import separate_checkout_environment


def git(root, *args):
    return subprocess.check_output(["git", *args], cwd=root, env=separate_checkout_environment())


def comparison(root, base, head):
    # Disabling rename pairing preserves both names, including deleted interfaces/resources.
    fields = git(root, "diff", "--no-renames", "--name-status", "-z", base, head).split(b"\0")
    if fields[-1] != b"" or len(fields) % 2 != 1:
        raise EvidenceError("Invalid Git change inventory")
    return [(fields[i].decode("ascii"), fields[i + 1].decode("utf-8")) for i in range(0, len(fields) - 1, 2)]


def runtime_path(path):
    return (path.startswith(("src/main/", "database/", ".devcontainer/", "local_repo/"))
            or path.startswith("debian/") and not path.startswith(("debian/assets/tests/", "debian/tests/"))
            or path in ("pom.xml", "package.json", "package-lock.json"))


def inventory(plan, root=ROOT, head="HEAD"):
    resolved = git(root, "rev-parse", head).decode().strip()
    bindings = plan.get("pathBindings", {})
    checks = {check["id"] for check in plan["checks"]}
    workstreams = {work["id"] for work in plan["workstreams"]}
    for path, binding in bindings.items():
        if not binding.get("reason") or not binding.get("tests") or not binding.get("checks") or not binding.get("workstreams"):
            raise EvidenceError("Path binding lacks its concrete test, check, workstream or rationale")
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
    if not isinstance(main, str) or not main.strip():
        raise EvidenceError("Scope inventory requires the currentMain baseline")
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
    unresolved = [row["path"] for row in migrations if not (
        isinstance(row["disposition"], dict)
        and row["disposition"].get("sha256") == row["sha256"]
        and isinstance(row["disposition"].get("reason"), str)
        and row["disposition"]["reason"].strip())]
    published = {}
    manifest = plan.get('lifecycleBaselineManifest')
    if manifest:
        if manifest != 'docs/testing/release-2026.08-lifecycle-baselines.json':
            raise EvidenceError('Unexpected published lifecycle baseline manifest')
        baselines = required_baselines(read_json(root / manifest))
        if baselines[0]['sourceCommit'] != main:
            raise EvidenceError('Published main source and scope baseline disagree')
        for baseline in baselines:
            minimum = len(sql_objects(root, baseline['sourceCommit']))
            if not minimum:
                raise EvidenceError('Published migration inventory is empty')
            published[baseline['tag']] = {'sourceCommit': baseline['sourceCommit'],
                **compare_shared(root, baseline['sourceCommit'], resolved, minimum=minimum)}
    else:
        published['currentMain'] = {'sourceCommit': main, **compare_shared(root, main, resolved)}
    return {"schemaVersion": 1, "head": resolved, "changes": rows,
            "unownedProductionPaths": sorted(missing), "mainOnlyMigrations": migrations,
            "unresolvedMainOnlyMigrations": unresolved, 'publishedMigrationIntegrity': published,
            "ready": not missing and not unresolved and all(row['ready'] for row in published.values())}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path, default=DEFAULT_PLAN)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--result-output", type=Path,
                        help="Write required command assertions and hash the underlying inventory")
    parser.add_argument("--inventory-only", action="store_true", help="Generate review inventory without claiming coverage")
    args = parser.parse_args(argv)
    args.output.unlink(missing_ok=True)
    try:
        result = inventory(load_plan(args.plan))
        write_json(args.output, result)
        if args.result_output:
            if args.inventory_only:
                raise EvidenceError('Inventory-only discovery cannot produce qualification assertions')
            try:
                name = args.output.resolve().relative_to(args.result_output.parent.resolve()).as_posix()
            except ValueError as failure:
                raise EvidenceError('Scope inputs must be retained beside the command result') from failure
            write_json(args.result_output, {'schemaVersion': 1, 'assertions': {
                'complete-production-ownership': not result['unownedProductionPaths'],
                'main-only-migration-dispositions': not result['unresolvedMainOnlyMigrations'],
                'published-migrations-byte-identical': all(row['ready'] for row in result['publishedMigrationIntegrity'].values())},
                'inputs': [{'path': name, 'sha256': digest(args.output)}]})
        print(json.dumps({"changedPathsAcrossScopes": len(result["changes"]),
                          "unownedProductionPaths": len(result["unownedProductionPaths"]),
                          "unresolvedMainOnlyMigrations": len(result["unresolvedMainOnlyMigrations"]), "ready": result["ready"]}))
        return 0 if args.inventory_only or result["ready"] else 1
    except Exception as failure:
        print("Release scope refused: " + (str(failure) if isinstance(failure, EvidenceError) else type(failure).__name__), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
